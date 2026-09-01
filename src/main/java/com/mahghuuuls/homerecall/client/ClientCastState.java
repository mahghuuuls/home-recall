package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.client.hud.CastBar;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What this client believes about its own cast.
 *
 * <p>Three things live here, and only one of them came from the server. The belief — whether a
 * cast is running and how long it is — is a mirror of what the server last said and may be
 * briefly wrong. The elapsed count is the client's own clock, generated locally because the
 * transition-only rule forbids per-tick progress on the wire; {@link ClientCastLifecycle} owns
 * when it may advance. The fade bookkeeping is pure presentation. None of it may be treated as
 * authority: it exists so the cast bar has something to draw, and for nothing else.
 *
 * <p>Progress is counted here, on the client's own ticks, rather than synchronized. The server
 * says "a cast started, this long" and "it ended, this way", per the transition-only rule; two
 * clocks ticking at the same twenty per second stay close enough for a progress bar, and drift
 * cannot accumulate past one cast because every cast starts the count again.
 *
 * <p>Other players' casts live here too, keyed by their entity id, with the same shape: begin,
 * a locally extrapolated clock, end. They carry no fade bookkeeping — the red fade is the HUD's
 * private language with its own player, and observers just see the effects stop. An observed
 * cast whose end message never arrives expires on its own a moment after the duration runs out,
 * so a dropped packet costs a ghost circle for two seconds, not forever.
 */
@SideOnly(Side.CLIENT)
public final class ClientCastState {

    /**
     * Exactly the fade's length, which {@code fadeAlpha} already answers zero for, so the idle
     * state draws nothing without a flag of its own. No slack is needed: the comparison there is
     * inclusive.
     */
    private static final int FADE_OVER = CastBar.FADE_TICKS;

    private static boolean casting;
    private static int durationTicks;
    private static int elapsedTicks;

    private static float interruptedFill;
    private static int ticksSinceInterrupt = FADE_OVER;

    /** Other players' running casts, keyed by entity id. Empty whenever nobody visible casts. */
    private static final Map<Integer, ObservedCast> OBSERVED =
            new HashMap<Integer, ObservedCast>();

    private ClientCastState() {
    }

    /** Records that the server started a cast of this length. */
    public static void begin(int duration) {
        casting = true;
        durationTicks = duration;
        elapsedTicks = 0;
        ticksSinceInterrupt = FADE_OVER;
    }

    /**
     * Records that the server ended the cast, one way or the other.
     *
     * <p>An interruption freezes the fill where it stands and starts the fade. Anything else,
     * completion included, simply removes the bar; which of the two happened arrives on the wire,
     * because the client has no way to tell them apart on its own.
     */
    public static void end(boolean interrupted) {
        if (interrupted && casting) {
            // The casting check guards a corner nothing currently reaches: an "interrupted" end
            // arriving with no cast believed running would otherwise freeze a stale fill and fade
            // it over whatever the player is doing now. If a path like that ever appears, showing
            // nothing is right and starting a fade is not.
            interruptedFill = CastBar.fill(elapsedTicks, 0.0F, durationTicks);
            ticksSinceInterrupt = 0;
        } else {
            ticksSinceInterrupt = FADE_OVER;
        }
        casting = false;
        durationTicks = 0;
        elapsedTicks = 0;
    }

    /** Advances the progress count, or the fade, by one client tick — observed casts included. */
    public static void tick() {
        if (casting) {
            elapsedTicks++;
        } else if (ticksSinceInterrupt < FADE_OVER) {
            ticksSinceInterrupt++;
        }
        if (!OBSERVED.isEmpty()) {
            Iterator<ObservedCast> observed = OBSERVED.values().iterator();
            while (observed.hasNext()) {
                if (observed.next().tickAndExpire()) {
                    observed.remove();
                }
            }
        }
    }

    /**
     * Records that another player's cast started, already this far along. Zero for a start seen
     * from the beginning; a late start for an observer who just walked into range carries the
     * real progress, so their circle joins the cast where it actually is. Replaces any earlier
     * belief about the same caster.
     */
    public static void beginObserved(int casterId, int duration, int elapsed) {
        OBSERVED.put(casterId, new ObservedCast(duration, elapsed));
    }

    /** Records that another player's cast ended, however it ended. Unknown ids are a no-op. */
    public static void endObserved(int casterId) {
        OBSERVED.remove(casterId);
    }

    /**
     * A snapshot of the observed casts, safe to iterate while messages mutate the real map.
     * Values are live entries, so the elapsed count read from one is current.
     */
    public static List<Map.Entry<Integer, ObservedCast>> observed() {
        if (OBSERVED.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<Map.Entry<Integer, ObservedCast>>(OBSERVED.entrySet());
    }

    /**
     * One other player's running cast: a duration from the wire and a locally extrapolated
     * clock, nothing else.
     */
    public static final class ObservedCast {

        /**
         * How long past its duration an observed cast survives without an end message before it
         * is dropped anyway. Long enough that an end arriving marginally late still finds its
         * entry; short enough that a lost packet costs seconds of ghost circle, not a session.
         */
        private static final int EXPIRE_SLACK_TICKS = 40;

        private final int durationTicks;
        private int elapsedTicks;

        ObservedCast(int durationTicks, int elapsedTicks) {
            this.durationTicks = durationTicks;
            this.elapsedTicks = elapsedTicks;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public int elapsedTicks() {
            return elapsedTicks;
        }

        /** Advances one tick; true when this entry has outlived any believable cast. */
        boolean tickAndExpire() {
            elapsedTicks++;
            return elapsedTicks > durationTicks + EXPIRE_SLACK_TICKS;
        }
    }

    /** Whether this client currently believes it is casting. */
    public static boolean casting() {
        return casting;
    }

    /** The cast's total length in ticks, or zero when not casting. */
    public static int durationTicks() {
        return durationTicks;
    }

    /** Whole ticks elapsed since the cast began. The renderer adds the partial tick itself. */
    public static int elapsedTicks() {
        return elapsedTicks;
    }

    /** The fill the bar was frozen at when the cast was interrupted. */
    public static float interruptedFill() {
        return interruptedFill;
    }

    /** Ticks since the interruption, at and past {@link CastBar#FADE_TICKS} once faded out. */
    public static int ticksSinceInterrupt() {
        return ticksSinceInterrupt;
    }

    /**
     * Forgets everything, fade included.
     *
     * <p>Called when leaving a world. The state is static and the client outlives any one server,
     * so a belief left behind would put a cast bar for a cast that no longer exists on the next
     * world they join, filling toward a completion no server will ever send.
     */
    public static void clear() {
        casting = false;
        durationTicks = 0;
        elapsedTicks = 0;
        ticksSinceInterrupt = FADE_OVER;
        OBSERVED.clear();
    }
}
