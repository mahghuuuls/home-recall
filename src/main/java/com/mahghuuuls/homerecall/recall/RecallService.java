package com.mahghuuuls.homerecall.recall;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.diagnostics.Diagnostics;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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

        CastState cast = new CastState(config.castTimeTicks());
        CASTS.put(player.getUniqueID(), cast);
        LAST_REFUSAL.remove(player.getUniqueID());
        Diagnostics.recallStarted(player.getName(), cast.durationTicks());
        return null;
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
                // The player left or died between ticks. Cancellation has its own handling in a
                // later slice; discarding the orphaned cast here keeps the map from growing.
                casts.remove();
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
            }
        }
    }

    /**
     * Discards every cast when the server stops.
     *
     * <p>Not optional bookkeeping. On a single-player world the server stops but the JVM does not,
     * so without this a cast started before quitting to the menu would still be in the map when the
     * next world loads, and would complete against a player who never asked for it.
     */
    public static void onServerStopping() {
        Diagnostics.castsDiscardedAtServerStop(CASTS.size());
        clear();
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
            // distinction is the whole of REQ-013.
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
