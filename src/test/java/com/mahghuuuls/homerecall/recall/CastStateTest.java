package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cast timer.
 *
 * <p>Small, but the arithmetic decides when a player is moved, and the "completes exactly once"
 * property is what stops a mistimed tick loop teleporting someone twice.
 */
class CastStateTest {

    @Test
    @DisplayName("the default cast completes on tick 160 and not before")
    void completesAtTheConfiguredTick() {
        CastState cast = new CastState(160);

        for (int tick = 1; tick < 160; tick++) {
            assertFalse(cast.tick(), "completed early at tick " + tick);
        }
        assertTrue(cast.tick(), "must complete on tick 160");
    }

    @Test
    @DisplayName("a one-tick cast completes on its first tick")
    void shortestCast() {
        assertTrue(new CastState(1).tick());
    }

    @Test
    @DisplayName("completion is reported exactly once, however many times it is ticked")
    void completesOnlyOnce() {
        // The failure this prevents is a double teleport: a tick loop that keeps a finished cast
        // would move the player again on the next tick.
        CastState cast = new CastState(3);
        cast.tick();
        cast.tick();
        assertTrue(cast.tick(), "third tick completes it");

        assertFalse(cast.tick(), "a completed cast must not report completion again");
        assertFalse(cast.tick());
    }

    @Test
    @DisplayName("remaining ticks count down and stop at zero")
    void remainingCountsDown() {
        CastState cast = new CastState(3);
        assertEquals(3, cast.remainingTicks());
        cast.tick();
        assertEquals(2, cast.remainingTicks());
        cast.tick();
        cast.tick();
        assertEquals(0, cast.remainingTicks());
        cast.tick();
        assertEquals(0, cast.remainingTicks(), "never negative");
    }

    @Test
    @DisplayName("elapsed never runs past the duration")
    void elapsedIsBounded() {
        CastState cast = new CastState(2);
        for (int i = 0; i < 10; i++) {
            cast.tick();
        }
        assertEquals(2, cast.elapsedTicks());
        assertTrue(cast.isComplete());
    }

    @Test
    @DisplayName("a zero or negative duration is rejected rather than completing instantly")
    void invalidDurationIsRejected() {
        // A cast of zero ticks would teleport on the same tick as the request, which is not a cast
        // at all. The configuration clamp already prevents it; this makes the class say so too.
        for (int invalid : new int[] {0, -1, -160}) {
            try {
                new CastState(invalid);
                throw new AssertionError("duration " + invalid + " must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("at least one tick"));
            }
        }
    }
}
