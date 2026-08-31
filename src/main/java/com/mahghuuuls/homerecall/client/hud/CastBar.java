package com.mahghuuuls.homerecall.client.hud;

/**
 * The cast bar's timing arithmetic, free of any Minecraft class so the fill and the interrupted
 * fade are evaluable against plain numbers.
 *
 * <p>Adapted from the owner's Everfilling Flasks, MIT-licensed, same author. Deliberately a copy
 * rather than a shared dependency: the two mods must be installable independently, and a bar this
 * small is cheaper to own than to import.
 */
public final class CastBar {

    /** Ticks the interrupted look takes to fade out completely. */
    public static final int FADE_TICKS = 10;

    private CastBar() {
    }

    /**
     * Fill fraction 0 to 1 for a running cast, smooth via partial ticks, clamped at full.
     *
     * <p>A duration of zero or less answers empty rather than dividing by it. Not reachable
     * through the configuration, whose minimum cast time is one second, but arithmetic this
     * central should not have an input that detonates.
     */
    public static float fill(int progressTicks, float partialTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (progressTicks + partialTicks) / durationTicks);
    }

    /**
     * Opacity of the interrupted look: 1 at the moment of interruption, falling linearly to 0 at
     * {@link #FADE_TICKS}, and 0 forever after, which is also the idle answer.
     */
    public static float fadeAlpha(int ticksSinceInterrupt) {
        if (ticksSinceInterrupt < 0 || ticksSinceInterrupt >= FADE_TICKS) {
            return 0.0F;
        }
        return 1.0F - (float) ticksSinceInterrupt / FADE_TICKS;
    }
}
