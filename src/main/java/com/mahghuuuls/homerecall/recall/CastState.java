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

    private final int durationTicks;
    private int elapsedTicks;

    public CastState(int durationTicks) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("cast duration must be at least one tick, was "
                    + durationTicks);
        }
        this.durationTicks = durationTicks;
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
