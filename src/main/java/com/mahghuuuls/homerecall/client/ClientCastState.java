package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.client.hud.CastBar;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
 * <p>Only the local player's cast for now. Nearby players' casts arrive in a later slice, which is
 * why this is a small holder rather than a field on the player.
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

    /** Advances the progress count, or the fade, by one client tick. */
    public static void tick() {
        if (casting) {
            elapsedTicks++;
        } else if (ticksSinceInterrupt < FADE_OVER) {
            ticksSinceInterrupt++;
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
    }
}
