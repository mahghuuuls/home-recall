package com.mahghuuuls.homerecall.recall;

/**
 * Where a recall will put the player, and which rule decided it.
 *
 * <p>The branch is carried because a position alone cannot be checked. A player arriving near the
 * world spawn looks identical whether the fallback fired or whether their bed simply happens to be
 * there, so a diagnostic record naming coordinates proves nothing about the decision. Naming the
 * branch is what makes the record evidence.
 *
 * <p>Coordinates are the exact placement the player will be moved to, already offset to the centre
 * of the block. Vanilla's suffocation escape is applied afterwards, by whoever performs the move,
 * because it needs the destination world.
 */
public final class RecallDestination {

    /** Which rule produced the destination. */
    public enum Source {

        /** The player's own bed or forced spawn, resolved and still valid. */
        PERSONAL_SPAWN,

        /** No valid personal spawn existed, and the world-spawn fallback was permitted. */
        WORLD_SPAWN_FALLBACK
    }

    private final int dimension;
    private final double x;
    private final double y;
    private final double z;
    private final Source source;

    public RecallDestination(int dimension, double x, double y, double z, Source source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.source = source;
    }

    public int dimension() {
        return dimension;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Source source() {
        return source;
    }

    @Override
    public String toString() {
        return "dimension " + dimension + " at " + String.format("%.1f, %.1f, %.1f", x, y, z)
                + " via " + source;
    }
}
