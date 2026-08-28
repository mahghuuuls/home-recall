package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the destination resolution order.
 *
 * <p>This is the piece most likely to be wrong in a way nobody notices. A player sent to the wrong
 * dimension's spawn arrives somewhere plausible, and the only in-game symptom is that home is not
 * where they left it. These run the real {@link SpawnResolver} against a scripted
 * {@link SpawnLookup}, so the order and the branch choices are checked directly.
 *
 * <p>What they cannot establish is that {@code VanillaSpawnLookup} answers those questions
 * correctly against a real server. That is a runtime check.
 */
class SpawnResolverTest {

    /** A scripted world, so each rule can be put under the exact condition that triggers it. */
    private static final class FakeLookup implements SpawnLookup {

        int spawnDimension;
        final Set<Integer> existingWorlds = new HashSet<Integer>();
        final Set<Integer> noRespawnDimensions = new HashSet<Integer>();
        final Map<Integer, Integer> respawnRedirects = new HashMap<Integer, Integer>();
        final Map<Integer, SpawnPos> storedSpawns = new HashMap<Integer, SpawnPos>();
        final Set<Integer> unresolvableSpawns = new HashSet<Integer>();
        final Map<Integer, SpawnPos> worldSpawns = new HashMap<Integer, SpawnPos>();
        final List<String> calls = new ArrayList<String>();

        @Override
        public int spawnDimension() {
            calls.add("spawnDimension");
            return spawnDimension;
        }

        @Override
        public boolean worldExists(int dimension) {
            return existingWorlds.contains(dimension);
        }

        @Override
        public boolean canRespawnIn(int dimension) {
            // Stricter than VanillaSpawnLookup, which reports an unloaded dimension as respawnable
            // rather than load it. Being strict here is deliberate: a resolver that dropped the
            // worldExists gate would be caught, and the divergence is unreachable through resolve()
            // because that gate runs first.
            return existingWorlds.contains(dimension) && !noRespawnDimensions.contains(dimension);
        }

        @Override
        public int respawnDimensionFor(int dimension) {
            calls.add("respawnDimensionFor:" + dimension);
            Integer redirect = respawnRedirects.get(dimension);
            return redirect == null ? 0 : redirect;
        }

        @Override
        public SpawnPos resolveStoredSpawn(int dimension) {
            calls.add("resolveStoredSpawn:" + dimension);
            return unresolvableSpawns.contains(dimension) ? null : storedSpawns.get(dimension);
        }

        @Override
        public SpawnPos worldSpawn(int dimension) {
            calls.add("worldSpawn:" + dimension);
            SpawnPos spawn = worldSpawns.get(dimension);
            return spawn == null ? new SpawnPos(0, 64, 0) : spawn;
        }
    }

    private static FakeLookup overworldOnly() {
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 0;
        lookup.existingWorlds.add(0);
        lookup.worldSpawns.put(0, new SpawnPos(8, 64, 8));
        return lookup;
    }

    @Test
    @DisplayName("a valid bed resolves to the bed, centred on the block")
    void bedSpawnWins() {
        FakeLookup lookup = overworldOnly();
        lookup.storedSpawns.put(0, new SpawnPos(100, 70, -40));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(RecallDestination.Source.PERSONAL_SPAWN, destination.source());
        assertEquals(0, destination.dimension());
        assertEquals(100.5D, destination.x(), 0.0001D);
        assertEquals(70.1D, destination.y(), 0.0001D);
        assertEquals(-39.5D, destination.z(), 0.0001D);
    }

    @Test
    @DisplayName("no bed at all falls back to world spawn")
    void neverSleptFallsBack() {
        FakeLookup lookup = overworldOnly();

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(RecallDestination.Source.WORLD_SPAWN_FALLBACK, destination.source());
        assertEquals(8.5D, destination.x(), 0.0001D);
    }

    @Test
    @DisplayName("a destroyed bed falls back, which is a different case from never having slept")
    void destroyedBedFallsBack() {
        FakeLookup lookup = overworldOnly();
        // The spawn is still stored; vanilla just cannot turn it into a standable position, which
        // is exactly what a broken bed looks like.
        lookup.storedSpawns.put(0, new SpawnPos(100, 70, -40));
        lookup.unresolvableSpawns.add(0);

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(RecallDestination.Source.WORLD_SPAWN_FALLBACK, destination.source());
        assertTrue(lookup.calls.contains("resolveStoredSpawn:0"),
                "the stored spawn must actually be attempted before falling back");
    }

    @Test
    @DisplayName("with the fallback off and no valid bed there is no destination")
    void fallbackDisabledYieldsNull() {
        FakeLookup lookup = overworldOnly();

        assertNull(SpawnResolver.resolve(lookup, false));
    }

    @Test
    @DisplayName("with the fallback off a valid bed is still used")
    void fallbackDisabledStillUsesBed() {
        FakeLookup lookup = overworldOnly();
        lookup.storedSpawns.put(0, new SpawnPos(10, 65, 10));

        RecallDestination destination = SpawnResolver.resolve(lookup, false);

        assertEquals(RecallDestination.Source.PERSONAL_SPAWN, destination.source());
    }

    @Test
    @DisplayName("the spawn dimension is what the stored spawn is read for")
    void readsTheSpawnDimension() {
        // This checks that the dimension chosen at the top is the one carried into the spawn read,
        // and that no other dimension is consulted. It does NOT guard against the no-argument
        // vanilla accessors: that hazard lives in VanillaSpawnLookup, which this fake replaces, and
        // SpawnLookup has no concept of where the player is standing, so the resolver could not
        // commit it. Only a runtime check covers that.
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 0;
        lookup.existingWorlds.add(0);
        lookup.existingWorlds.add(-1);
        lookup.storedSpawns.put(0, new SpawnPos(200, 68, 200));
        lookup.worldSpawns.put(-1, new SpawnPos(0, 64, 0));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(0, destination.dimension());
        assertEquals(200.5D, destination.x(), 0.0001D);
        assertTrue(lookup.calls.contains("resolveStoredSpawn:0"));
        assertTrue(!lookup.calls.contains("resolveStoredSpawn:-1"),
                "no dimension other than the spawn dimension may be consulted");
    }

    @Test
    @DisplayName("vanilla's redirect answers with the dimension we started from, so it is not trusted")
    void selfReferentialRedirectFallsToOverworld() {
        // This is what really happens on a stock game. WorldProvider.getRespawnDimension returns
        // player.getSpawnDimension(), which is exactly the value the resolver started from, and
        // neither the Nether nor the End overrides it. An earlier version trusted that answer, so
        // the End branch was a no-op that read like a safety net, and a test scripted with a
        // redirect no real provider produces certified it as working.
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 1;
        lookup.existingWorlds.add(1);
        lookup.existingWorlds.add(0);
        lookup.noRespawnDimensions.add(1);
        lookup.respawnRedirects.put(1, 1);
        lookup.worldSpawns.put(0, new SpawnPos(4, 64, 4));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(0, destination.dimension(),
                "a self-referential redirect must not leave the player in the End");
    }

    @Test
    @DisplayName("a redirect to a dimension that also forbids respawning falls to the overworld")
    void redirectToAnotherNonRespawnableDimensionFallsToOverworld() {
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 1;
        lookup.existingWorlds.add(1);
        lookup.existingWorlds.add(2);
        lookup.existingWorlds.add(0);
        lookup.noRespawnDimensions.add(1);
        lookup.noRespawnDimensions.add(2);
        lookup.respawnRedirects.put(1, 2);
        lookup.worldSpawns.put(0, new SpawnPos(4, 64, 4));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(0, destination.dimension(),
                "one hop is not enough if it lands somewhere equally unusable");
    }

    @Test
    @DisplayName("a redirect a mod actually provides is honoured")
    void usefulRedirectIsHonoured() {
        // The redirect target is deliberately not the overworld. Pointing it at 0 would make the
        // assertion pass whether the redirect was honoured or ignored, because falling through
        // gives the same answer.
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 1;
        lookup.existingWorlds.add(1);
        lookup.existingWorlds.add(5);
        lookup.existingWorlds.add(0);
        lookup.noRespawnDimensions.add(1);
        lookup.respawnRedirects.put(1, 5);
        lookup.worldSpawns.put(5, new SpawnPos(4, 64, 4));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(5, destination.dimension(),
                "a redirect a mod really provides must be followed, not overridden");
        assertTrue(lookup.calls.contains("respawnDimensionFor:1"));
    }

    @Test
    @DisplayName("a removed dimension falls back to the overworld rather than failing")
    void missingWorldFallsBackToOverworld() {
        // A pack can drop a dimension mod between sessions, leaving a spawn dimension that no
        // longer exists.
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 7;
        lookup.existingWorlds.add(0);
        lookup.worldSpawns.put(0, new SpawnPos(1, 64, 1));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(0, destination.dimension());
        assertEquals(RecallDestination.Source.WORLD_SPAWN_FALLBACK, destination.source());
    }

    @Test
    @DisplayName("a redirect that also points nowhere still lands in the overworld")
    void redirectToMissingWorldStillLandsSomewhere() {
        FakeLookup lookup = new FakeLookup();
        lookup.spawnDimension = 1;
        lookup.existingWorlds.add(1);
        lookup.existingWorlds.add(0);
        lookup.noRespawnDimensions.add(1);
        lookup.respawnRedirects.put(1, 99);
        lookup.worldSpawns.put(0, new SpawnPos(2, 64, 2));

        RecallDestination destination = SpawnResolver.resolve(lookup, true);

        assertEquals(0, destination.dimension(),
                "a player must always end up somewhere that exists");
    }

    @Test
    @DisplayName("a valid bed is read once, not twice")
    void storedSpawnIsReadOnce() {
        // Each read costs a getBedLocation and, on the real lookup, can touch the destination
        // world. Reading it twice per resolve was a real cost of an earlier shape that asked
        // whether a spawn existed and then asked for it again.
        FakeLookup lookup = overworldOnly();
        lookup.storedSpawns.put(0, new SpawnPos(1, 2, 3));

        SpawnResolver.resolve(lookup, true);

        int reads = 0;
        for (String call : lookup.calls) {
            if (call.equals("resolveStoredSpawn:0")) {
                reads++;
            }
        }
        assertEquals(1, reads, "the stored spawn should be read exactly once per resolve");
    }

    @Test
    @DisplayName("a valid bed means the world spawn is never asked for")
    void worldSpawnIsNotConsultedWhenABedExists() {
        FakeLookup lookup = overworldOnly();
        lookup.storedSpawns.put(0, new SpawnPos(1, 2, 3));

        SpawnResolver.resolve(lookup, true);

        assertTrue(!lookup.calls.contains("worldSpawn:0"),
                "the fallback must not be evaluated when it is not needed");
    }
}
