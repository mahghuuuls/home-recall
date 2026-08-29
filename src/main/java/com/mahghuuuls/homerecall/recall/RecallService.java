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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
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

    private static final Map<UUID, Long> LAST_REFUSAL_AT = new HashMap<UUID, Long>();
    private static final Map<UUID, RefusalReason> LAST_REFUSAL = new HashMap<UUID, RefusalReason>();

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
     * <p>Paired with {@link #endCast}. Keeping the two together is the point: the cast entry and
     * the movement slow start and stop as one thing, so a later path that ends a cast cannot end
     * half of it. Every caller uses the pair; none of them touches the map or the slow directly.
     */
    private static void beginCast(EntityPlayerMP player, ConfigSnapshot config) {
        CastState cast = new CastState(config.castTimeTicks());
        CASTS.put(player.getUniqueID(), cast);
        LAST_REFUSAL.remove(player.getUniqueID());

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
     * Everything that stops being true when a cast ends, in one place.
     *
     * <p>Safe to call for a player who is not casting, so every lifecycle path can call it without
     * first working out whether it needs to.
     *
     * @param path what ended it, for the record
     */
    private static void endCast(EntityPlayer player, String path) {
        CASTS.remove(player.getUniqueID());
        endCastEffects(player, path);
    }

    /**
     * Everything a cast leaves behind, undone: the slow comes off and the client is told the cast
     * is over.
     *
     * <p>Separate from {@link #endCast} for one reason. The tick loop removes the cast entry
     * through its iterator, because removing it from the map while iterating would fail, so it
     * calls this for the rest. Both halves still have exactly one implementation between them.
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

    /** Discards every cast. */
    public static void clear() {
        CASTS.clear();
        LAST_REFUSAL_AT.clear();
        LAST_REFUSAL.clear();
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

        Iterator<Map.Entry<UUID, CastState>> casts = CASTS.entrySet().iterator();
        while (casts.hasNext()) {
            Map.Entry<UUID, CastState> entry = casts.next();
            EntityPlayerMP player = playerFor(entry.getKey());
            if (player == null || !player.isEntityAlive()) {
                // The player has gone, died, or been removed from the world under this cast. That
                // last one is not obvious: leaving the End through the exit portal marks the old
                // entity dead and builds a new one, so a player who is very much alive arrives
                // here too. All three end the cast silently for now; giving each a stated cause
                // and telling the player is a later slice.
                casts.remove();
                if (player != null) {
                    // The entity is about to be replaced by one with a fresh attribute map, so
                    // this changes nothing that would be observed. It is here because "every path
                    // that ends a cast removes the slow" is only worth having as a rule if it has
                    // no exceptions a later reader has to remember.
                    endCastEffects(player, "cast discarded");
                }
                continue;
            }
            if (!entry.getValue().tick()) {
                continue;
            }
            casts.remove();
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
     * Strips a leftover slow from a player as they join.
     *
     * <p>This should never find anything. The modifier is unsaved, so it cannot survive a restart,
     * and every path that ends a cast removes it. That is exactly why it is worth having: if it
     * ever does find one, the record it writes is the only evidence that an end path was added
     * without a matching removal, or that the modifier stopped being unsaved.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (CastSlowdown.remove(event.player)) {
            Diagnostics.slowdownFoundAtLogin(event.player.getName());
        }
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
     * <p>The tick loop would discard the entry on its next pass anyway, once the player can no
     * longer be found. Ending it here as well is not redundant: it keeps the cast and the slow
     * ending together, which is the invariant {@link #endCast} exists to hold. Announcing a cause
     * to a player who has already left is a separate question and is not answered here.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        endCast(event.player, "player logged out");
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
            tell(player, RefusalReason.NO_DESTINATION);
            return;
        }
        RefusalReason dimensionRefusal = dimensionRefusalFor(
                destination.dimension(), player.dimension, config.allowCrossDimension());
        if (dimensionRefusal != null) {
            // The destination moved to another dimension during the cast. Same decision as at the
            // start, made in the same place, so the two cannot drift apart.
            Diagnostics.recallRefused(player.getName(), dimensionRefusal);
            tell(player, dimensionRefusal);
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
        tell(player, reason);
        return reason;
    }

    /**
     * Shows a short action-bar message, unless the same cause was already shown recently.
     *
     * <p>A held key produces one message rather than a stream. A different cause is never
     * suppressed by an earlier one, so a player who fixes the first problem and hits the second
     * still hears about it immediately.
     */
    private static void tell(EntityPlayerMP player, RefusalReason reason) {
        UUID id = player.getUniqueID();
        long now = player.world.getTotalWorldTime() * 50L;
        Long lastAt = LAST_REFUSAL_AT.get(id);
        if (reason == LAST_REFUSAL.get(id) && lastAt != null
                && now - lastAt.longValue() < REFUSAL_QUIET_MILLIS) {
            return;
        }
        LAST_REFUSAL.put(id, reason);
        LAST_REFUSAL_AT.put(id, Long.valueOf(now));
        player.sendStatusMessage(new TextComponentTranslation(reason.translationKey()), true);
    }

    private static EntityPlayerMP playerFor(UUID id) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }
}
