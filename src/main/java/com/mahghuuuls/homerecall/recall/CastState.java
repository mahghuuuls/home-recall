package com.mahghuuuls.homerecall.recall;

/**
 * One running cast.
 *
 * <p>Deliberately small: a duration and how far through it we are. Everything else about a recall
 * is decided when it starts or when it finishes, not carried along.
 *
 * <p>Never serialized. A cast must not survive a logout, and the surest way to guarantee that is
 * for there to be nowhere to write it. See ARC-002.
 */
public final class CastState {

    /**
     * How far from the anchor a caster may drift before they have moved, squared, in blocks.
     *
     * <p>0.05 blocks. A standing player's position does not change at all, so any nonzero
     * tolerance protects them; this one exists for sub-step physics nudges, and a single walking
     * step covers several times it. Squared so the check needs no square root.
     */
    private static final double MOVE_TOLERANCE_SQ = 0.05 * 0.05;

    private final int durationTicks;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;
    private int elapsedTicks;

    public CastState(int durationTicks, double anchorX, double anchorY, double anchorZ) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("cast duration must be at least one tick, was "
                    + durationTicks);
        }
        this.durationTicks = durationTicks;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    /**
     * Whether this position counts as having moved away from where the cast began.
     *
     * <p>The channel's movement rule, in one place and free of Minecraft types. It deliberately
     * cannot ask why the player moved: a step, a jump, a fall, and a mob's shove all look the
     * same from here, and all of them break the channel.
     */
    public boolean movedFrom(double x, double y, double z) {
        double dx = x - anchorX;
        double dy = y - anchorY;
        double dz = z - anchorZ;
        return dx * dx + dy * dy + dz * dz > MOVE_TOLERANCE_SQ;
    }

    /**
     * Advances one tick and reports whether the cast has now finished.
     *
     * <p>Returns true exactly once for a given cast, on the tick it completes, so a caller cannot
     * teleport a player twice by ticking again.
     */
    public boolean tick() {
        if (elapsedTicks >= durationTicks) {
            return false;
        }
        elapsedTicks++;
        return elapsedTicks >= durationTicks;
    }

    public boolean isComplete() {
        return elapsedTicks >= durationTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    /** Ticks left, never negative. */
    public int remainingTicks() {
        return Math.max(0, durationTicks - elapsedTicks);
    }
}
