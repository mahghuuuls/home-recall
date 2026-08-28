package com.mahghuuuls.homerecall.recall;

import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Decides where a recall ends, following vanilla's own respawn order.
 *
 * <p>Modelled on {@code PlayerList.recreatePlayerEntity}, which is the only place Minecraft
 * expresses this order and which cannot be called here because it also destroys and rebuilds the
 * player entity.
 *
 * <p><strong>One step differs deliberately, and the difference matters.</strong> Vanilla starts
 * from the dimension the player is currently in, because a respawn happens wherever they died. A
 * recall starts from the player's spawn dimension, because the whole question is where their bed
 * is. That single change breaks vanilla's redirect step, which returns
 * {@code player.getSpawnDimension()} and so would hand back the value already in hand. See
 * {@link #redirectAwayFrom}.
 *
 * <p>The order, and why each step exists:
 *
 * <ol>
 *   <li>Start from the player's spawn dimension. Forge tracks this separately, and it is the only
 *       way to know which dimension's spawn map to read.</li>
 *   <li>If that world is gone, fall back to the overworld. A dimension mod can be removed.</li>
 *   <li>If that dimension forbids respawning, find one that does not. This is what stops a recall
 *       depositing someone in the End.</li>
 *   <li>Read the stored spawn <em>for that dimension</em>. Never the no-argument accessor: it
 *       answers for whichever dimension the player is standing in, which is almost never the one
 *       holding their bed.</li>
 *   <li>Fall back to the world spawn only if the configuration permits it.</li>
 * </ol>
 *
 * <p>This never writes respawn data. A player who recalls to the world spawn because their bed is
 * gone still has the same broken bed afterwards, and still gets vanilla's own message the next time
 * they die. Home Recall reads that state; it does not tidy it up.
 */
public final class SpawnResolver {

    /** Dimension id of the overworld, which is the only one guaranteed to exist. */
    static final int OVERWORLD = 0;

    private SpawnResolver() {
    }

    /**
     * Resolves where this player's recall should end, or null when there is nowhere to send them.
     *
     * <p>Null means no valid personal spawn and the world-spawn fallback switched off. The caller
     * turns that into a refusal with a stated reason.
     */
    public static RecallDestination resolve(EntityPlayerMP player, ConfigSnapshot config) {
        return resolve(new VanillaSpawnLookup(player), config.fallbackToWorldSpawn());
    }

    /**
     * The algorithm, against a lookup rather than a player.
     *
     * <p>Package-private and used by tests. The public entry point above is the one the rest of the
     * mod calls, so no caller has to know a lookup exists or which configuration value matters.
     */
    static RecallDestination resolve(SpawnLookup lookup, boolean fallbackToWorldSpawn) {
        if (lookup == null) {
            throw new NullPointerException("lookup");
        }

        int dimension = lookup.spawnDimension();
        if (!lookup.worldExists(dimension)) {
            dimension = OVERWORLD;
        } else if (!lookup.canRespawnIn(dimension)) {
            dimension = redirectAwayFrom(dimension, lookup);
        }

        SpawnPos stored = lookup.resolveStoredSpawn(dimension);
        if (stored != null) {
            return centred(dimension, stored, RecallDestination.Source.PERSONAL_SPAWN);
        }

        if (!fallbackToWorldSpawn) {
            return null;
        }
        return centred(dimension, lookup.worldSpawn(dimension),
                RecallDestination.Source.WORLD_SPAWN_FALLBACK);
    }

    /**
     * Finds somewhere respawnable when the spawn dimension is not.
     *
     * <p>The provider is asked first, because a dimension mod may have an opinion. Its answer
     * cannot simply be trusted, and this is the part that is easy to get wrong: vanilla's
     * {@code WorldProvider.getRespawnDimension} returns {@code player.getSpawnDimension()}, which
     * is exactly the value this resolver started from. Neither the Nether nor the End overrides it.
     * So on a stock game the provider hands back the same dimension it was asked about, and a
     * redirect that trusted it would be a no-op that reads like a safety net.
     *
     * <p>Falling through to the overworld is what actually keeps a player out of the End.
     */
    private static int redirectAwayFrom(int dimension, SpawnLookup lookup) {
        int redirected = lookup.respawnDimensionFor(dimension);
        if (redirected == dimension) {
            return OVERWORLD;
        }
        if (!lookup.worldExists(redirected) || !lookup.canRespawnIn(redirected)) {
            return OVERWORLD;
        }
        return redirected;
    }

    /**
     * Vanilla's own placement offset: the centre of the block horizontally, and a hair above its
     * floor. Matched rather than reinvented so a recall lands exactly where a respawn would.
     */
    private static RecallDestination centred(int dimension, SpawnPos pos,
                                             RecallDestination.Source source) {
        return new RecallDestination(dimension, pos.x() + 0.5D, pos.y() + 0.1D, pos.z() + 0.5D,
                source);
    }
}
