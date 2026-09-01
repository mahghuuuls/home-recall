package com.mahghuuuls.homerecall.client.fx;

/**
 * The arithmetic of the cast circle: how many particles this tick, and where on the ring.
 *
 * <p>Free of Minecraft types so the progression can be tested. The rendering half spends these
 * answers; this class only computes them.
 *
 * <p>The intensity contract comes from the requirements: sparse in the first quarter, visibly
 * stronger by the last. Four bands, one per quarter, spawning one to four particles per tick.
 * The default cast's final band is thirty ticks and 120 particles, and a whole default cast
 * spawns 300 — modest enough that several visible casts stay immaterial to frame timing.
 */
public final class CastCircle {

    /** Ring radius in blocks. Just outside the player's feet without clipping into them. */
    public static final double RADIUS = 0.9D;

    /**
     * The ring advances by this angle each tick, in radians. Deliberately irrational relative to
     * the ring's own spacing so consecutive ticks never restack particles on the same spots and
     * the circle reads as swirling rather than strobing.
     */
    static final double SWIRL_PER_TICK = 0.35D;

    private CastCircle() {
    }

    /**
     * Which quarter of the cast this tick is in, 1 to 4. Never below 1 or above 4, whatever the
     * inputs, because a presentation helper must not be able to throw a cast into a bad state.
     */
    public static int band(int elapsedTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return 1;
        }
        int quarter = (elapsedTicks * 4) / durationTicks + 1;
        return Math.max(1, Math.min(4, quarter));
    }

    /** Particles to spawn this tick: one per band, so the thickening is the progression. */
    public static int particlesThisTick(int elapsedTicks, int durationTicks) {
        return band(elapsedTicks, durationTicks);
    }

    /**
     * The ring angle for one particle this tick, in radians: the swirl's base for the tick, plus
     * even spacing for the tick's particles.
     */
    public static double angle(int elapsedTicks, int index, int count) {
        double spacing = count <= 0 ? 0.0D : (Math.PI * 2.0D) / count;
        return elapsedTicks * SWIRL_PER_TICK + index * spacing;
    }
}
