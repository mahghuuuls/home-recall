package com.mahghuuuls.homerecall.recall;

/**
 * The handful of vanilla facts {@link SpawnResolver} needs, expressed without Minecraft types.
 *
 * <p>This exists so the resolution order can be evaluated as plain logic. That order is the most
 * error-prone code in the mod: Forge stores respawn points per dimension, offers no lookup for
 * which dimension a bed is in, and marks the convenient no-argument accessors deprecated because
 * they answer for the wrong dimension. Getting it wrong sends a player somewhere plausible and
 * silent.
 *
 * <p>Every method here must be a pure query. In particular {@link #worldExists(int)} must not load
 * a dimension: asking whether somewhere exists should not bring it into being.
 */
public interface SpawnLookup {

    /**
     * The dimension the player would respawn in. Forge tracks this separately from the bed
     * position, and it is the only way to learn which dimension's spawn map to read.
     */
    int spawnDimension();

    /** Whether a world exists for the given dimension id, without loading it. */
    boolean worldExists(int dimension);

    /** Whether the given dimension permits respawning, which the End does not. */
    boolean canRespawnIn(int dimension);

    /** Where a player who cannot respawn in the given dimension is sent instead. */
    int respawnDimensionFor(int dimension);

    /**
     * Resolves the player's stored spawn for this dimension into a standable position, applying
     * vanilla's own bed and forced-spawn rules.
     *
     * @return null when there is no stored spawn, or when the bed is gone or a forced position is
     *         obstructed. Those cases are indistinguishable to the caller and are handled alike.
     */
    SpawnPos resolveStoredSpawn(int dimension);

    /** The world spawn of the given dimension. Never null. */
    SpawnPos worldSpawn(int dimension);
}
