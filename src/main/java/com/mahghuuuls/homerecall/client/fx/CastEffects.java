package com.mahghuuuls.homerecall.client.fx;

import com.mahghuuuls.homerecall.client.ClientCastState;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The local player's own cast, made visible and audible: the thickening ground circle, the start
 * chime, and the channelling hum.
 *
 * <p>A second independent consumer of {@link ClientCastState}, beside the HUD. It reads the same
 * belief every frame and decides nothing: when the belief says the cast ended — however it ended
 * — no new particle is spawned from that tick on (already-spawned portal particles live out
 * their own two-second vanilla lifetime and fall away), and the hum is stopped by handle, which
 * is what lets a cancellation fall silent in the same moment the bar reacts.
 *
 * <p>Departure and arrival are deliberately not here. They are one-shot world events other
 * players must perceive, so the server spawns them where the cast completes; this class only
 * renders the private, ongoing part.
 *
 * <p>Everything is vanilla: portal particles, portal ambience, the end-portal-frame chime. No
 * custom asset ships (REQ-038, REQ-039).
 */
@SideOnly(Side.CLIENT)
public final class CastEffects {

    /**
     * Ticks before the hum is started again. The portal-ambient sample is a single 4.97-second
     * file; at the 0.8 pitch used here it plays for about 124 ticks, so one chunk covers the
     * whole 6-second default cast and this restart only ever fires on longer configured casts.
     * Restarting no earlier than the previous chunk's end is what makes overlap impossible —
     * an earlier version restarted every 40 ticks and stacked three copies of the drone.
     */
    private static final int HUM_RESTART_TICKS = 130;

    private static boolean wasCasting;
    private static int humTicksLeft;

    /**
     * The one live hum chunk, kept so a cancellation can stop it mid-sample. A fire-and-forget
     * sound cannot be taken back, and "the channelling sound stops on the next tick" is a
     * requirement, not a nicety. The restart interval above is what guarantees there is never
     * a second live chunk this handle does not cover.
     */
    private static ISound playingHum;

    /** Other casters' hums, keyed by entity id, reaped as their casts vanish. */
    private static final Map<Integer, HumChannel> OBSERVED_HUMS =
            new HashMap<Integer, HumChannel>();

    private CastEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            // Leaving a world mid-cast. The world swap stops every sound anyway, but the rarer
            // player-null frame does not, so every live chunk is stopped here rather than trusted
            // to the teardown.
            if (playingHum != null) {
                minecraft.getSoundHandler().stopSound(playingHum);
                playingHum = null;
            }
            stopAllObservedHums(minecraft);
            wasCasting = false;
            return;
        }

        boolean casting = ClientCastState.casting();
        if (casting && !wasCasting) {
            beginEffects(minecraft);
        } else if (!casting && wasCasting) {
            endEffects(minecraft);
        }
        if (!minecraft.isGamePaused()) {
            if (casting) {
                tickEffects(minecraft);
            }
            tickObservedEffects(minecraft);
        }
        wasCasting = casting;
    }

    private static void beginEffects(Minecraft minecraft) {
        // No start chime, by the owner's call during validation: the hum beginning on the very
        // next branch is the start announcement, and a chime on top of it was one sound too many.
        humTicksLeft = 0;
        playingHum = null;
    }

    private static void endEffects(Minecraft minecraft) {
        // Completion, cancellation, and the second press all land here; the circle is already
        // gone because casting() is false. The departure and arrival one-shots come from the
        // server, so ending locally in silence is correct for every cause.
        if (playingHum != null) {
            minecraft.getSoundHandler().stopSound(playingHum);
            playingHum = null;
        }
    }

    private static void tickEffects(Minecraft minecraft) {
        ConfigSnapshot config = ConfigSnapshot.current();
        EntityPlayerSP player = minecraft.player;
        int elapsed = ClientCastState.elapsedTicks();
        int duration = ClientCastState.durationTicks();

        if (config.enableParticles()) {
            int count = CastCircle.particlesThisTick(elapsed, duration);
            for (int i = 0; i < count; i++) {
                double angle = CastCircle.angle(elapsed, i, count);
                double x = player.posX + Math.cos(angle) * CastCircle.RADIUS;
                double z = player.posZ + Math.sin(angle) * CastCircle.RADIUS;
                minecraft.world.spawnParticle(EnumParticleTypes.PORTAL,
                        x, player.posY + 0.1D, z, 0.0D, 0.4D, 0.0D);
            }
        }

        if (config.enableRecallSounds()) {
            if (humTicksLeft <= 0) {
                // Defensive even though the arithmetic says the old chunk has ended: stopping a
                // finished sound is a no-op, and one live handle is the invariant everything
                // else here leans on.
                if (playingHum != null) {
                    minecraft.getSoundHandler().stopSound(playingHum);
                }
                playingHum = new PositionedSoundRecord(SoundEvents.BLOCK_PORTAL_AMBIENT,
                        SoundCategory.PLAYERS, 0.25F, 0.8F, new BlockPos(player));
                minecraft.getSoundHandler().playSound(playingHum);
                humTicksLeft = HUM_RESTART_TICKS;
            } else {
                humTicksLeft--;
            }
        } else if (playingHum != null) {
            // Sounds switched off mid-cast: silence the chunk already in the air too.
            minecraft.getSoundHandler().stopSound(playingHum);
            playingHum = null;
        }
    }

    /**
     * Renders every other visible player's cast: their circle at their feet, their hum at their
     * position. Runs whether or not the local player is casting — the two are independent, which
     * is the requirement — and derives everything from {@link ClientCastState#observed()}: a
     * caster whose end message removed them, whose entry expired, or whose entity left tracking
     * simply stops appearing here, and their hum is reaped the same tick.
     */
    private static void tickObservedEffects(Minecraft minecraft) {
        List<Map.Entry<Integer, ClientCastState.ObservedCast>> observed =
                ClientCastState.observed();
        if (observed.isEmpty() && OBSERVED_HUMS.isEmpty()) {
            return;
        }
        ConfigSnapshot config = ConfigSnapshot.current();
        if (!config.enableRecallSounds()) {
            stopAllObservedHums(minecraft);
        }

        Set<Integer> alive = new HashSet<Integer>();
        for (Map.Entry<Integer, ClientCastState.ObservedCast> entry : observed) {
            Entity caster = minecraft.world.getEntityByID(entry.getKey());
            if (!(caster instanceof EntityPlayer) || caster == minecraft.player) {
                // Not visible this tick, or somehow ourselves. The entry expires on its own; the
                // hum reap below keeps this caster silent meanwhile.
                continue;
            }
            alive.add(entry.getKey());
            ClientCastState.ObservedCast cast = entry.getValue();

            if (config.enableParticles()) {
                int count = CastCircle.particlesThisTick(
                        cast.elapsedTicks(), cast.durationTicks());
                for (int i = 0; i < count; i++) {
                    double angle = CastCircle.angle(cast.elapsedTicks(), i, count);
                    double x = caster.posX + Math.cos(angle) * CastCircle.RADIUS;
                    double z = caster.posZ + Math.sin(angle) * CastCircle.RADIUS;
                    minecraft.world.spawnParticle(EnumParticleTypes.PORTAL,
                            x, caster.posY + 0.1D, z, 0.0D, 0.4D, 0.0D);
                }
            }

            if (config.enableRecallSounds()) {
                HumChannel hum = OBSERVED_HUMS.get(entry.getKey());
                if (hum == null) {
                    hum = new HumChannel();
                    OBSERVED_HUMS.put(entry.getKey(), hum);
                }
                if (hum.ticksLeft <= 0) {
                    if (hum.handle != null) {
                        minecraft.getSoundHandler().stopSound(hum.handle);
                    }
                    hum.handle = new PositionedSoundRecord(SoundEvents.BLOCK_PORTAL_AMBIENT,
                            SoundCategory.PLAYERS, 0.25F, 0.8F, new BlockPos(caster));
                    minecraft.getSoundHandler().playSound(hum.handle);
                    hum.ticksLeft = HUM_RESTART_TICKS;
                } else {
                    hum.ticksLeft--;
                }
            }
        }

        if (!OBSERVED_HUMS.isEmpty()) {
            Iterator<Map.Entry<Integer, HumChannel>> hums =
                    OBSERVED_HUMS.entrySet().iterator();
            while (hums.hasNext()) {
                Map.Entry<Integer, HumChannel> entry = hums.next();
                if (!alive.contains(entry.getKey())) {
                    if (entry.getValue().handle != null) {
                        minecraft.getSoundHandler().stopSound(entry.getValue().handle);
                    }
                    hums.remove();
                }
            }
        }
    }

    private static void stopAllObservedHums(Minecraft minecraft) {
        if (OBSERVED_HUMS.isEmpty()) {
            return;
        }
        for (HumChannel hum : OBSERVED_HUMS.values()) {
            if (hum.handle != null) {
                minecraft.getSoundHandler().stopSound(hum.handle);
            }
        }
        OBSERVED_HUMS.clear();
    }

    /** One caster's hum: the live chunk and the ticks until it may be restarted. */
    private static final class HumChannel {
        private ISound handle;
        private int ticksLeft;
    }
}
