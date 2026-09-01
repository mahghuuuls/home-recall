package com.mahghuuuls.homerecall.recall;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.diagnostics.Diagnostics;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
     * <p>A press while a cast is already running is the player cancelling it, so null does not
     * mean a cast began; it means the press was accepted. A caller that wants to react to a start
     * specifically must not use this return for it.
     *
     * @return the reason it was declined, or null when the press either began a cast or cancelled
     *         the running one
     */
    public static RefusalReason requestRecall(EntityPlayerMP player) {
        if (player == null) {
            return RefusalReason.NOT_ALIVE;
        }
        if (!player.isEntityAlive()) {
            return refuse(player, RefusalReason.NOT_ALIVE);
        }
        if (CASTS.containsKey(player.getUniqueID())) {
            // The second press is the player's own cancel, not a mistake to refuse. The one
            // cancellation that is a choice gets its own cause, so the log can tell a deliberate
            // stop from an accident.
            cancel(player, CancelReason.CANCELLED_BY_PLAYER);
            return null;
        }
        if (player.isHandActive()) {
            // Mid-eat, mid-drink, mid-draw, or blocking. Refused rather than started, and after
            // the second-press branch on purpose: a cancel must always win. Silent by the owner's
            // rule — the player knows exactly what their own hands are doing — and before any
            // destination work, so a press that will be refused costs no chunk load.
            return refuse(player, RefusalReason.USING_ITEM);
        }

        ConfigSnapshot config = ConfigSnapshot.current();

        // Resolved before the cast begins rather than at the end. A player who has nowhere to go
        // should be told immediately, not left standing through six seconds for nothing.
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
     * entry, its anchor, and what the client believes start and stop as one thing, so a later
     * path that ends a cast cannot end half of it.
     */
    private static void beginCast(EntityPlayerMP player, ConfigSnapshot config) {
        // The anchor is where the channel must be waited out. Any position past the tolerance,
        // however reached, is the caster having left it.
        CastState cast = new CastState(config.castTimeTicks(), player.posX, player.posY, player.posZ);
        CASTS.put(player.getUniqueID(), cast);

        // A new cast is a fresh slate for messages. Without this a refusal from the previous cast
        // could still be inside the quiet window and silence the first refusal of this one, which
        // is the moment a player most wants to be told something.
        MESSAGES.forget(player.getUniqueID());

        // No item use can be running here: requestRecall refuses the press while one is. What
        // reaches this method is a player whose hands were free.

        // Told last, once everything that makes the cast real has happened. The client uses this
        // only to draw the bar; it decides nothing.
        HomeRecallNetwork.sendCastSync(player, true, cast.durationTicks(), false);
        Diagnostics.recallStarted(player.getName(), cast.durationTicks());
    }

    /**
     * Everything a cast leaves behind, undone: the client is told the cast is over, and how.
     *
     * <p>Separate from the cast entry itself so the tick loop and {@link #cancel} can each remove
     * that entry at the moment that suits them and share everything after it.
     */
    private static void endCastEffects(EntityPlayer player, boolean interrupted) {
        if (player instanceof EntityPlayerMP) {
            // The one effect a cast leaves on the client is its bar, and this is what removes or
            // fades it. Which of the two rides on the wire, because the client cannot tell a
            // completion from a cancellation on its own.
            HomeRecallNetwork.sendCastSync((EntityPlayerMP) player, false, 0, interrupted);
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
        // Two independent questions. The fade plays for every cause the player is present to see;
        // a message accompanies only causes that are not their own obvious doing. Splitting them
        // is what lets a self-evident break fade in silence.
        endCastEffects(player, reason.fades());
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
     * Whether a destination in this dimension can be reached.
     *
     * <p>Pulled out so the choice can be tested. This once told two causes apart — "switched off"
     * and "permitted but not built" — after an earlier version conflated them and told a player
     * with cross-dimension recall switched <em>on</em> that it was off. The transfer is built now
     * (IMP-004), so the second cause is gone and only the configured refusal remains; the shape
     * survives because the decision is still made in one place for the start and the completion.
     *
     * @return null when the destination is reachable
     */
    static RefusalReason dimensionRefusalFor(int destinationDimension, int playerDimension,
                                             boolean allowCrossDimension) {
        if (destinationDimension == playerDimension || allowCrossDimension) {
            return null;
        }
        return RefusalReason.CROSS_DIMENSION_DISABLED;
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
            if (entry.getValue().movedFrom(player.posX, player.posY, player.posZ)) {
                // The channel's own rule: leaving the anchor breaks it, whoever or whatever did
                // the moving. Checked before the tick so a final step cannot land the teleport.
                CASTS.remove(id);
                endCancelledCast(player, CancelReason.MOVED);
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
                // In a finally block on purpose. A completion that throws must still take the
                // client's bar down: the cast is already out of the map by this point, so nothing
                // else would ever come along to tell them.
                //
                // Sent as a completion rather than a cancellation even when complete() refused,
                // because either way the channel was stood through to its end. Which of the two
                // happened is recorded by complete().
                endCastEffects(player, false);
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
     * <p>Nothing else needs undoing here. Every entity that carried a cast is about to be
     * discarded with its world, and {@link #onPlayerLoggedIn} backstops the map itself.
     */
    public static void onServerStopping() {
        Diagnostics.castsDiscardedAtServerStop(CASTS.size());
        clear();
    }

    /**
     * Reports what a joining player brought with them, and strips anything they should not have.
     *
     * <p>Neither check should ever find anything. A leftover cast cannot survive a logout because
     * the logout ends it, and the client clears its own belief when it loses its world.
     *
     * <p>Which is exactly why both are checked and the ordinary answer is written down. A
     * cast correctly discarded at logout and a cast that never existed produce the same
     * observation on reconnect: the player is not teleported. Only a record saying "nothing was
     * being held" can tell a working logout from a mod that never started a cast at all.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Removed on its own line, not inside the report. This removal is load-bearing, and a
        // maintainer who reads the line as "just a log" and wraps or deletes it would silently
        // delete the cleanup with it.
        //
        // Deliberately not routed through cancel(). This is a backstop for something that should
        // not exist, not a cancellation: there is nothing to tell the player, no cause worth a
        // constant, and the client resync just below already covers the same case.
        boolean staleCast = CASTS.remove(event.player.getUniqueID()) != null;
        Diagnostics.castStateAtLogin(event.player.getName(), staleCast);
        if (event.player instanceof EntityPlayerMP) {
            // The client belief is static and survives leaving a world, so a player who quit to
            // the menu mid-cast can arrive here still believing they are casting, with no server
            // anywhere that could tell them otherwise. The client clears that itself when it
            // loses its world; this makes the correction arrive from the authority as well, on
            // every join, so the belief cannot depend on that timing being right. Sent to the
            // joiner alone: nobody else holds a belief about them yet.
            HomeRecallNetwork.sendCastResync((EntityPlayerMP) event.player);
        }
    }

    /**
     * Catches up a player who walks into tracking range of someone already casting.
     *
     * <p>The transition sends reach only whoever is tracking the caster at that moment; without
     * this, an observer approaching mid-cast would see the caster standing in silence and then
     * vanishing with a burst — the unreadable escape REQ-042 forbids. Fired by vanilla's own
     * tracker, so "close enough to see the player" and "close enough to see their cast" stay the
     * same rule.
     */
    @SubscribeEvent
    public static void onStartTracking(
            net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityPlayerMP)
                || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP caster = (EntityPlayerMP) event.getTarget();
        CastState cast = CASTS.get(caster.getUniqueID());
        if (cast != null) {
            HomeRecallNetwork.sendLateCastStart((EntityPlayerMP) event.getEntityPlayer(),
                    caster, cast.durationTicks(), cast.elapsedTicks());
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
     * <p>Lowest priority. This event can be cancelled, and a mod that cancels it means the player
     * did not die: vanilla returns from onDeath without doing anything else. Running first would
     * end a cast for a death that was then undone, leaving a player standing there mid-recall with
     * nothing to show for it and no message, because this cause deliberately has none. Going last
     * is what lets a totem-like mod have its say before this does; the bus then never delivers the
     * cancelled event here at all, so the isCanceled check is a statement of intent, not the
     * mechanism.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntityLiving() instanceof EntityPlayerMP) {
            cancel((EntityPlayerMP) event.getEntityLiving(), CancelReason.DIED);
        }
    }

    /**
     * Ends the cast of a player who took damage that landed and survived it.
     *
     * <p>Lowest priority, for the same reason as the death handler above: another mod that cancels
     * or absorbs the harm means the player was not hurt, and a cast must not break over damage
     * that never happened. What actually delivers that guarantee is the priority alone; the bus
     * never hands a cancelled event to a listener that has not asked for cancelled events, so the
     * isCanceled check below can never see true and exists only as a statement of intent. This
     * event fires after armor and absorption, so the amount here is what actually reached the
     * health bar; anything above zero breaks the channel when the option is on.
     *
     * <p>A blow that kills is deliberately not taken here. This event fires before health drops,
     * so on a killing blow this handler would run first and record a survivable hit for a player
     * who is dead a moment later, putting the fade over their death screen. The death handler owns
     * that case. The one thing this trades away: a lethal blow something then undoes — a totem,
     * which restores health without ever firing a death event — leaves the channel running. A
     * death a mod cancels without healing is different: health sits at zero and the tick loop's
     * own health check ends the cast as the death it nearly was.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDamaged(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        // Two float comparisons before anything else; almost every damage event on the server is
        // for somebody who is not casting, and most of the rest are filtered here too.
        if (!breaksChannel(event.getAmount(), player.getHealth())) {
            return;
        }
        if (isCasting(player) && ConfigSnapshot.current().cancelOnDamage()) {
            cancel(player, CancelReason.DAMAGED);
        }
    }

    /**
     * Whether a landed amount, against this much remaining health, is the damage handler's to act
     * on: some harm, but not the killing blow, which belongs to the death handler.
     */
    static boolean breaksChannel(float amount, float health) {
        return amount > 0.0F && amount < health;
    }

    /**
     * Ends the cast of a player who changed dimension by some route this recall did not ask for.
     *
     * <p>A portal is the ordinary case. A spectator teleport across dimensions reaches this too,
     * and is treated the same on purpose: the destination the cast resolved was chosen for where
     * the player was, and they are no longer there.
     *
     * <p>A recall completing its own cross-dimension transfer fires this event too, from the last
     * line of vanilla's transfer. It needs no exemption and has none: the tick loop removes the
     * cast entry before it calls the completion, so the event raised from inside that completion
     * finds nothing to cancel and this returns false. (ARC-004 as amended)
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
     * <p>Resolved a second time on purpose. Six seconds is long enough for a bed to be broken,
     * and the destination decided at the start may no longer exist.
     */
    private static void complete(EntityPlayerMP player) {
        ConfigSnapshot config = ConfigSnapshot.current();
        RecallDestination destination = SpawnResolver.resolve(player, config);
        if (destination == null) {
            Diagnostics.destinationUnresolved(player.getName(), config.fallbackToWorldSpawn());
            // Through refuse() rather than a bare tell, so this path shares the record-and-speak
            // rule — including the null-key guard — instead of carrying its own copy of it.
            refuse(player, RefusalReason.NO_DESTINATION);
            return;
        }
        RefusalReason dimensionRefusal = dimensionRefusalFor(
                destination.dimension(), player.dimension, config.allowCrossDimension());
        if (dimensionRefusal != null) {
            // The destination moved to another dimension during the cast. Same decision as at the
            // start, made in the same place, so the two cannot drift apart.
            refuse(player, dimensionRefusal);
            return;
        }
        if (player.server.getWorld(destination.dimension()) == null) {
            // Forge can fail to initialize a dimension and returns null rather than throwing.
            // Without this check the transfer below would write the new dimension id onto the
            // player and then blow up, leaving them in the old world with player data pointing at
            // the broken one — which is persisted, and greets them again at their next login. A
            // destination whose world cannot exist is a destination they do not have.
            refuse(player, RefusalReason.NO_DESTINATION);
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

        // Departure, at the origin, before anything moves: a compact portal-particle puff and the
        // enderman pop. Server-spawned one-shots so bystanders at the origin see the leaving too;
        // nothing here repeats per tick (ARC-009).
        if (config.enableParticles()) {
            ((WorldServer) player.world).spawnParticle(EnumParticleTypes.PORTAL,
                    player.posX, player.posY + 1.0D, player.posZ, 40, 0.4D, 0.7D, 0.4D, 0.05D);
        }
        if (config.enableRecallSounds()) {
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 1.0F);
        }

        boolean crossedDimensions = destination.dimension() != player.dimension;
        double y = escapeCollision(player, destination);
        if (crossedDimensions) {
            // Through the player's own changeDimension, never PlayerList directly. The player
            // entry point is the one that resets the client-sync fields — skip it and the client
            // rebuilds its player on respawn with the experience bar at zero and nothing ever
            // resending it — and it grants the movement-check immunity window and asks other mods
            // first via EntityTravelToDimensionEvent. Vanilla fires the dimension-change event as
            // the transfer's last line, where its cancel finds this cast already out of the map
            // and does nothing: that ordering is the re-entrancy protection (ARC-004 as amended),
            // and the teleporter is what keeps a portal from being built at the bed.
            int from = player.dimension;
            player.changeDimension(destination.dimension(),
                    new RecallTeleporter(destination.x(), y, destination.z()));
            if (player.dimension != destination.dimension()) {
                // Another mod vetoed the travel event. The recall honestly did not happen, and
                // this is a cross-mod interference worth a line even with diagnostics off.
                HomeRecallMod.LOGGER.warn(
                        "another mod prevented Home Recall from moving {} to dimension {}",
                        player.getName(), destination.dimension());
                return;
            }
            Diagnostics.recallTransferred(player.getName(), from, destination.dimension());
        } else {
            player.setLocationAndAngles(destination.x(), y, destination.z(),
                    player.rotationYaw, player.rotationPitch);
            player.connection.setPlayerLocation(destination.x(), y, destination.z(),
                    player.rotationYaw, player.rotationPitch);
        }

        // Arrival, at the destination, after placement: a smaller puff and the portal-travel
        // whoosh — the owner's pick for the sound of every finished recall. On a cross-dimension
        // arrival vanilla has already played the whoosh to the traveller from inside
        // changeDimension, so there they are excluded and only bystanders hear this copy; the
        // same-dimension path includes everyone. Either way the player hears it exactly once.
        WorldServer destinationWorld = player.server.getWorld(destination.dimension());
        if (config.enableParticles()) {
            destinationWorld.spawnParticle(EnumParticleTypes.PORTAL,
                    destination.x(), y + 1.0D, destination.z(), 20, 0.3D, 0.6D, 0.3D, 0.05D);
        }
        if (config.enableRecallSounds()) {
            destinationWorld.playSound(crossedDimensions ? player : null,
                    destination.x(), y, destination.z(),
                    SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.4F, 1.0F);
        }
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

    /**
     * Records the refusal, tells the player when the reason speaks, and returns the reason so
     * callers can pass it on. A reason with no key is recorded and shown to nobody — the log is
     * the only place a silent refusal exists, and that record is what separates it from a broken
     * keybind.
     */
    private static RefusalReason refuse(EntityPlayerMP player, RefusalReason reason) {
        Diagnostics.recallRefused(player.getName(), reason);
        if (reason.translationKey() != null) {
            tell(player, reason.translationKey());
        }
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
