package com.mahghuuuls.homerecall.recall;

/**
 * A block position, without a Minecraft type.
 *
 * <p>Exists so {@link SpawnLookup} can answer in something named and correctly sized. An
 * {@code int[]} would be equally free of Minecraft and would additionally be indexable out of
 * bounds on the completion path.
 */
public final class SpawnPos {

    private final int x;
    private final int y;
    private final int z;

    public SpawnPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
