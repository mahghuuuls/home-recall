package com.mahghuuuls.homerecall.recall;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.diagnostics.Diagnostics;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every recall cast: whether one may begin, how it advances, and what happens when it ends.
 *
 * <p>Callers ask {@link #requestRecall(EntityPlayerMP)} and read the answer. They do not check
 * eligibility, hold timers, or know how a destination is chosen.
 *
 * <p>Casts live in a map keyed by player UUID and are never written to disk. That map is static, so
 * it outlives any one server: a single-player world stops its integrated server without ending the
 * JVM, and an entry left behind would resume against the next world the player loads, with the same
 * UUID, and teleport them seconds after they log in. {@link #onServerStopping} is what prevents
 * that, and it is the reason "never written to disk" is not on its own enough.
 *
 * <p>Server-side only. The client sends a request and draws what it is told; it decides nothing, so
 * a modified client gains nothing.
 */
public final class RecallService {

    private static final Map<UUID, CastState> CASTS = new HashMap<UUID, CastState>();

    /**
     * How long the same refusal is suppressed for one player, in milliseconds. A held key produces
     * one message rather than a stream; a different cause is never suppressed by an earlier one.
     */
    private static final long REFUSAL_QUIET_MILLIS = 2000L;

    /**
     * The anti-spam rule, shared by refusals and cancellations so there is one window rather than
     * two that could drift apart.
     */
    private static final NotificationThrottle MESSAGES =
            new NotificationThrottle(REFUSAL_QUIET_MILLIS);

    private RecallService() {
    }

    /**
     * Starts a cast if the player may have one, telling them why if not.
     *
     * @return the reason it was declined, or null when a cast has begun
     */
    public static RefusalReason requestRecall(EntityPlayerMP player) {
        if (player == null) {
            return RefusalReason.NOT_ALIVE;
        }
        if (!player.isEntityAlive()) {
            return refuse(player, RefusalReason.NOT_ALIVE);
        }
        if (CASTS.containsKey(player.getUniqueID())) {
            return refuse(player, RefusalReason.ALREADY_RECALLING);
        }

        ConfigSnapshot config = ConfigSnapshot.current();

        // Resolved before the cast begins rather than at the end. A player who has nowhere to go
        // should be told immediately, not left standing through eight seconds for nothing.
        RecallDestination destination = SpawnResolver.resolve(player, config);
        if (destination == null) {
            Diagnostics.destinationUnresolved(player.getName(), config.fallbackToWorldSpawn());
            return refuse(player, RefusalReason.NO_DESTINATION);
        }
        Diagnostics.destinationResolved(player.getName(), destination);

        RefusalReason dimensionRefusal = dimensionRefusalFor(
                destination.dimension(), player.dimension, config.allowCrossDimension());
        if (dimensionRefusal != null) {
            return refuse(player, dimensionRefusal);
        }

        beginCast(player, config);
        return null;
    }

    /**
     * Everything that becomes true when a cast starts, in one place.
     *
     * <p>Paired with {@link #endCastEffects}. Keeping the two together is the point: the cast
     * entry, the movement slow, and what the client believes start and stop as one thing, so a
     * later path that ends a cast cannot end half of it.
     */
    private static void beginCast(EntityPlayerMP player, ConfigSnapshot config) {
        CastState cast = new CastState(config.castTimeTicks());
        CASTS.put(player.getUniqueID(), cast);

        // A new cast is a fresh slate for messages. Without this a refusal from the previous cast
        // could still be inside the quiet window and silence the first refusal of this one, which
        // is the moment a player most wants to be told something.
        MESSAGES.forget(player.getUniqueID());

        // Abandons an item use that was already running, without telling the item it finished. A
        // player who was eating when they pressed the key stops eating; they do not swallow.
        player.resetActiveHand();

        // The configured speed is read once, here. A cast started before the option changed keeps
        // the speed it began with.
        boolean slowed = CastSlowdown.apply(player, config.castMovementSpeed());
        Diagnostics.slowdownApplied(player.getName(), slowed, config.castMovementSpeed());

        // Told last, once everything that makes the cast real has happened. The client uses this
        // only to stop predicting actions the server is about to refuse; it decides nothing.
        HomeRecallNetwork.sendCastSync(player, true, cast.durationTicks());
        Diagnostics.recallStarted(player.getName(), cast.durationTicks());
    }

    /**
     * Everything a cast leaves behind, undone: the slow comes off and the client is told the cast
     * is over.
     *
     * <p>Separate from the cast entry itself so the tick loop and {@link #cancel} can each remove
     * that entry at the moment that suits them and share everything after it.
     */
    private static void endCastEffects(EntityPlayer player, String path) {
        if (CastSlowdown.remove(player)) {
            Diagnostics.slowdownRemoved(player.getName(), path);
        }
        if (player instanceof EntityPlayerMP) {
            // Unconditional, unlike the slow above. A player whose configured speed was 1.0 never
            // had a modifier but was still casting, and still has a client that must be told to
            // stop refusing their actions. Tying this to the slow's removal would leave exactly
            // those players unable to act until something else happened to correct them.
            HomeRecallNetwork.sendCastSync((EntityPlayerMP) player, false, 0);
        }
    }

    /**
     * Ends this player's cast, if they have one, for a stated reason.
     *
     * <p>Idempotent. Cancelling a player who is not casting is a no-op, not an error, so every
     * lifecycle handler can call it without first asking whether it needs to. That is the point:
     * a handler that has to check is a handler that will one day forget.
     *
     * @return true when a cast was actually running and has now ended
     */
    public static boolean cancel(EntityPlayerMP player, CancelReason reason) {
        if (player == null || CASTS.remove(player.getUniqueID()) == null) {
            return false;
        }
        endCancelledCast(player, reason);
        return true;
    }

    /**
     * Everything a cancellation does apart from removing the cast entry itself.
     *
     * <p>Separate only because the two callers remove that entry differently: {@link #cancel} from
     * the map, and the tick loop through its iterator, because removing from a map while iterating
     * it would fail. Everything after that point is identical and lives here, so a cause added
     * later cannot be handled properly on one path and forgotten on the other.
     */
    private static void endCancelledCast(EntityPlayerMP player, CancelReason reason) {
        endCastEffects(player, "cancelled: " + reason);
        Diagnostics.recallCancelled(player.getName(), reason);
        String message = reason.messageKey();
        if (message != null) {
            tell(player, message);
        }
    }

    /**
     * Whether this player has a cast running right now.
     *
     * <p>A membership test on a hot path: it is asked once per attack, block break, and
     * interaction, and the map is empty whenever nobody is recalling.
     *
     * <p>Answers false for a client-side player. Casts live only on the server, and on a physical
     * client both logical sides share this class, so an unguarded caller would read server state
     * from the client thread and get an answer that happens to be right in single player and is
     * meaningless in multiplayer. Refusing here rather than trusting each caller to check is what
     * makes this safe to hand to client code.
     */
    public static boolean isCasting(EntityPlayer player) {
        return player != null && !player.world.isRemote && CASTS.containsKey(player.getUniqueID());
    }

    /**
     * Whether a destination in this dimension can be reached, and if not, which of the two reasons
     * applies.
     *
     * <p>Pulled out so the choice can be tested. The two causes look identical to a player and are
     * easy to collapse into one constant, which is exactly what happened in an earlier version:
     * a player with cross-dimension recall switched <em>on</em> was told it was switched off. The
     * whole purpose of naming reasons is to keep causes apart, so this one is worth a test rather
     * than a careful reading.
     *
     * @return null when the destination is reachable
     */
    static RefusalReason dimensionRefusalFor(int destinationDimension, int playerDimension,
                                             boolean allowCrossDimension) {
        if (destinationDimension == playerDimension) {
            return null;
        }
        if (!allowCrossDimension) {
            return RefusalReason.CROSS_DIMENSION_DISABLED;
        }
        // Permitted by configuration, but the transfer needs a custom teleporter and a re-entrancy
        // guard that are not built yet. Saying so is honest; reusing the "disabled" reason is not.
        return RefusalReason.CROSS_DIMENSION_NOT_IMPLEMENTED;
    }

    /** Discards every cast and everything remembered about who was told what. */
    public static void clear() {
        CASTS.clear();
        MESSAGES.clear();
    }

    /**
     * Advances every running cast once per server tick.
     *
     * <p>Costs nothing when nobody is recalling: the map is empty and the loop does not run.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CASTS.isEmpty()) {
            return;
        }

        // Walked over a snapshot rather than the live map, because complete() below fires dismount,
        // wake and chunk-load events, any of which a listening mod could follow back into cancel()
        // for another player. A live iterator would then throw out of this loop, past the per-cast
        // catch, and take the server tick with it. One list allocation, only on ticks where
        // somebody is casting.
        //
        // Every removal goes through CASTS by key. A snapshot iterator's own remove() would take
        // the entry out of the copy and leave the real map untouched, which is not a subtle
        // failure: the cast would never end, the player would be refused every action for the rest
        // of the session, and this loop would run forever.
        for (Map.Entry<UUID, CastState> entry
                : new ArrayList<Map.Entry<UUID, CastState>>(CASTS.entrySet())) {
            UUID id = entry.getKey();

            // The snapshot can be stale: a re-entrant cancel during an earlier player's completion
            // may already have ended this one. Ticking it anyway would teleport a player who has
            // been told their recall was interrupted.
            if (CASTS.get(id) != entry.getValue()) {
                continue;
            }

            EntityPlayerMP player = playerFor(id);
            if (player == null) {
                // Nobody to end anything for and nobody to tell. Recorded anyway: this is the one
                // place whose job is to notice a cast disappearing quietly, so it is a poor place
                // for a quiet exit of its own.
                CASTS.remove(id);
                Diagnostics.castDiscardedForMissingPlayer(id);
                continue;
            }
            if (player.isDead) {
                // Removed from the world while alive. Death and logout have their own handlers and
                // have already taken their casts by now, so what reaches here is the case with no
                // event of its own: the End exit portal, which discards the player's entity and
                // builds a new one.
                CASTS.remove(id);
                endCancelledCast(player, CancelReason.LEFT_THE_WORLD);
                continue;
            }
            if (player.getHealth() <= 0.0F) {
                // At zero health but not yet removed. The death handler owns this and has usually
                // run already; reaching here means something cancelled the death without healing
                // the player. Ended under the honest cause rather than under the End exit's.
                CASTS.remove(id);
                endCancelledCast(player, CancelReason.DIED);
                continue;
            }
            if (!entry.getValue().tick()) {
                continue;
            }
            CASTS.remove(id);
            try {
                complete(player);
            } catch (Throwable failure) {
                // One player's cast must not take down the tick, and must not abandon the casts
                // that have not been visited yet.
                HomeRecallMod.LOGGER.error("Home Recall failed while completing a recall for {}",
                        player.getName(), failure);
            } finally {
                // In a finally block on purpose. A completion that throws must still give the
                // player their speed back: the cast is already out of the map by this point, so
                // nothing else would ever come along to remove it.
                //
                // Named for reaching the end rather than for teleporting, because completion can
                // also end in a refusal. Which of the two happened is recorded by complete().
                endCastEffects(player, "cast reached its end");
            }
        }
    }

    /**
     * Discards every cast when the server stops.
     *
     * <p>Not optional bookkeeping. On a single-player world the server stops but the JVM does not,
     * so without this a cast started before quitting to the menu would still be in the map when the
     * next world loads, and would complete against a player who never asked for it.
     *
     * <p>No slow is removed here, and that is deliberate rather than an omission. The modifier is
     * unsaved, so it is not written with the player and cannot come back; every player entity that
     * carries one is about to be discarded. Walking the player list to strip a modifier from
     * entities that will not exist a moment later would be code that looks like a safeguard while
     * guarding nothing. {@link #onPlayerLoggedIn} is the real backstop.
     */
    public static void onServerStopping() {
        Diagnostics.castsDiscardedAtServerStop(CASTS.size());
        clear();
    }

    /**
     * Reports what a joining player brought with them, and strips anything they should not have.
     *
     * <p>Three things, and none of them should ever find anything. A leftover slow cannot survive
     * a restart because the modifier is unsaved. A leftover cast cannot survive a logout because
     * the logout ends it. The client belief is cleared by the client itself when it loses its
     * world.
     *
     * <p>Which is exactly why all three are checked and the ordinary answer is written down. A
     * cast correctly discarded at logout and a cast that never existed produce the same
     * observation on reconnect: the player is not teleported. Only a record saying "nothing was
     * being held" can tell a working logout from a mod that never started a cast at all.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (CastSlowdown.remove(event.player)) {
            Diagnostics.slowdownFoundAtLogin(event.player.getName());
        }
        // Removed on its own line, not inside the report. This removal is load-bearing, and a
        // maintainer who reads the line as "just a log" and wraps or deletes it would silently
        // delete the cleanup with it.
        //
        // Deliberately not routed through cancel(). This is a backstop for something that should
        // not exist, not a cancellation: there is nothing to tell the player, no cause worth a
        // constant, and the slow and client sync either side of it already cover the same case.
        boolean staleCast = CASTS.remove(event.player.getUniqueID()) != null;
        Diagnostics.castStateAtLogin(event.player.getName(), staleCast);
        if (event.player instanceof EntityPlayerMP) {
            // The client belief is static and survives leaving a world, so a player who quit to
            // the menu mid-cast can arrive here still believing they are casting, with no server
            // anywhere that could tell them otherwise. The client clears that itself when it
            // loses its world; this makes the correction arrive from the authority as well, on
            // every join, so the belief cannot depend on that timing being right.
            HomeRecallNetwork.sendCastSync((EntityPlayerMP) event.player, false, 0);
        }
    }

    /**
     * Ends a departing player's cast.
     *
     * <p>The tick loop would discard the entry on its next pass anyway. Ending it here is what
     * makes the reason right: from the loop this player is indistinguishable from one whose entity
     * was removed, and they would be recorded under the wrong cause.
     *
     * <p>Everything remembered about what they have been told is dropped too. Those entries are
     * per player and nothing else would ever remove them.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            cancel((EntityPlayerMP) event.player, CancelReason.LOGGED_OUT);
        }
        MESSAGES.forget(event.player.getUniqueID());
    }

    /**
     * Ends a dying player's cast.
     *
     * <p>Before the tick loop can see them, which matters for the recorded cause rather than for
     * the outcome: the loop would discard the same cast a moment later under a cause meant for
     * something else.
     *
     * <p>No message. The player is looking at a death screen, and an action-bar line under it
     * would be shown to nobody.
     *
     * <p>Lowest priority, and a cancelled event is ignored. This event can be cancelled, and a
     * mod that cancels it means the player did not die: vanilla returns from onDeath without
     * doing anything else. Running first would end a cast for a death that was then undone,
     * leaving a player standing there mid-recall with nothing to show for it and no message,
     * because this cause deliberately has none. Going last is what lets a totem-like mod have
     * its say before this does.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntityLiving() instanceof EntityPlayerMP) {
            cancel((EntityPlayerMP) event.getEntityLiving(), CancelReason.DIED);
        }
    }

    /**
     * Ends the cast of a player who changed dimension by some route this recall did not ask for.
     *
     * <p>A portal is the ordinary case. A spectator teleport across dimensions reaches this too,
     * and is treated the same on purpose: the destination the cast resolved was chosen for where
     * the player was, and they are no longer there.
     *
     * <p>A recall completing its own cross-dimension transfer will fire this event too, once that
     * is built. It needs no exemption and should not be given one: the tick loop removes the cast
     * entry before it calls the completion, so an event raised from inside that completion finds
     * nothing to cancel and this returns false.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            cancel((EntityPlayerMP) event.player, CancelReason.CHANGED_DIMENSION);
        }
    }

    /**
     * Resolves the destination again and moves the player.
     *
     * <p>Resolved a second time on purpose. Eight seconds is long enough for a bed to be broken,
     * and the destination decided at the start may no longer exist.
     */
    private static void complete(EntityPlayerMP player) {
        ConfigSnapshot config = ConfigSnapshot.current();
        RecallDestination destination = SpawnResolver.resolve(player, config);
        if (destination == null) {
            Diagnostics.destinationUnresolved(player.getName(), config.fallbackToWorldSpawn());
            Diagnostics.recallRefused(player.getName(), RefusalReason.NO_DESTINATION);
            tell(player, RefusalReason.NO_DESTINATION.translationKey());
            return;
        }
        RefusalReason dimensionRefusal = dimensionRefusalFor(
                destination.dimension(), player.dimension, config.allowCrossDimension());
        if (dimensionRefusal != null) {
            // The destination moved to another dimension during the cast. Same decision as at the
            // start, made in the same place, so the two cannot drift apart.
            Diagnostics.recallRefused(player.getName(), dimensionRefusal);
            tell(player, dimensionRefusal.translationKey());
            return;
        }

        // A player who is riding or asleep is snapped back by the vehicle or the bed on the next
        // tick, so moving them without letting go would leave them where they were while the log
        // recorded a completed recall. Setting a position is not the same as arriving.
        if (player.isRiding()) {
            player.dismountRidingEntity();
        }
        if (player.isPlayerSleeping()) {
            // Not setting the spawn: this wakes the player, it does not make the bed theirs. That
            // distinction matters: this mod never writes respawn data.
            player.wakeUpPlayer(true, false, false);
        }

        double y = escapeCollision(player, destination);
        player.setLocationAndAngles(destination.x(), y, destination.z(),
                player.rotationYaw, player.rotationPitch);
        player.connection.setPlayerLocation(destination.x(), y, destination.z(),
                player.rotationYaw, player.rotationPitch);
        Diagnostics.recallCompleted(player.getName(), destination);
    }

    /**
     * Vanilla's own escape from a solid destination: rise until the player fits, or stop at the
     * world ceiling as vanilla does.
     *
     * <p>The destination chunk is loaded first, and that line is the whole point. An unloaded chunk
     * reports no collision boxes at all, so without it the loop finds empty space on its first look
     * and returns unchanged, dropping the player inside terrain. It only shows up at range: a
     * recall near spawn passes because those chunks are already loaded.
     */
    private static double escapeCollision(EntityPlayerMP player, RecallDestination destination) {
        WorldServer world = player.server.getWorld(destination.dimension());
        if (world == null) {
            return destination.y();
        }
        // Floor rather than vanilla's cast. Destinations are always blockX + 0.5, and a cast
        // truncates toward zero, so for a block at -1, -17, -33 and so on it would load the
        // neighbouring chunk and leave the destination unloaded. That is the same silent failure
        // this line exists to prevent, surviving on a sixteenth of negative coordinates.
        world.getChunkProvider().provideChunk(
                MathHelper.floor(destination.x()) >> 4, MathHelper.floor(destination.z()) >> 4);

        double y = destination.y();
        double width = player.width / 2.0D;
        while (y < 256.0D) {
            AxisAlignedBB box = new AxisAlignedBB(
                    destination.x() - width, y, destination.z() - width,
                    destination.x() + width, y + player.height, destination.z() + width);
            if (world.getCollisionBoxes(player, box).isEmpty()) {
                return y;
            }
            y += 1.0D;
        }
        // Solid all the way up. Vanilla leaves the player at the ceiling rather than back inside
        // the block it started from.
        return 256.0D;
    }

    /** Records the refusal, tells the player, and returns the reason so callers can pass it on. */
    private static RefusalReason refuse(EntityPlayerMP player, RefusalReason reason) {
        Diagnostics.recallRefused(player.getName(), reason);
        tell(player, reason.translationKey());
        return reason;
    }

    /**
     * Shows a short action-bar message, unless the same cause was already shown recently.
     *
     * <p>A held key produces one message rather than a stream. A different cause is never
     * suppressed by an earlier one, so a player who fixes the first problem and hits the second
     * still hears about it immediately.
     */
    private static void tell(EntityPlayerMP player, String messageKey) {
        long now = player.world.getTotalWorldTime() * 50L;
        if (!MESSAGES.allow(player.getUniqueID(), messageKey, now)) {
            return;
        }
        player.sendStatusMessage(new TextComponentTranslation(messageKey), true);
    }

    private static EntityPlayerMP playerFor(UUID id) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }
}
