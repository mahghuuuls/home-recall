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
        CastState cast = new CastState(160, 0, 64, 0);

        for (int tick = 1; tick < 160; tick++) {
            assertFalse(cast.tick(), "completed early at tick " + tick);
        }
        assertTrue(cast.tick(), "must complete on tick 160");
    }

    @Test
    @DisplayName("a one-tick cast completes on its first tick")
    void shortestCast() {
        assertTrue(new CastState(1, 0, 64, 0).tick());
    }

    @Test
    @DisplayName("completion is reported exactly once, however many times it is ticked")
    void completesOnlyOnce() {
        // The failure this prevents is a double teleport: a tick loop that keeps a finished cast
        // would move the player again on the next tick.
        CastState cast = new CastState(3, 0, 64, 0);
        cast.tick();
        cast.tick();
        assertTrue(cast.tick(), "third tick completes it");

        assertFalse(cast.tick(), "a completed cast must not report completion again");
        assertFalse(cast.tick());
    }

    @Test
    @DisplayName("remaining ticks count down and stop at zero")
    void remainingCountsDown() {
        CastState cast = new CastState(3, 0, 64, 0);
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
        CastState cast = new CastState(2, 0, 64, 0);
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
                new CastState(invalid, 0, 64, 0);
                throw new AssertionError("duration " + invalid + " must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("at least one tick"));
            }
        }
    }

    @Test
    @DisplayName("standing exactly still is never having moved")
    void standingStillNeverMoves() {
        CastState cast = new CastState(120, 10.5, 64.0, -3.5);
        org.junit.jupiter.api.Assertions.assertFalse(cast.movedFrom(10.5, 64.0, -3.5));
    }

    @Test
    @DisplayName("a drift inside the tolerance does not count as moving")
    void driftInsideToleranceIsNotMoving() {
        // Sub-step physics nudges are smaller than this and a walking step is far larger, which
        // is the whole reason the tolerance has the value it has.
        CastState cast = new CastState(120, 10.5, 64.0, -3.5);
        org.junit.jupiter.api.Assertions.assertFalse(cast.movedFrom(10.5 + 0.03, 64.0, -3.5));
    }

    @Test
    @DisplayName("a step beyond the tolerance counts, in any direction")
    void stepBeyondToleranceMoves() {
        CastState cast = new CastState(120, 10.5, 64.0, -3.5);
        org.junit.jupiter.api.Assertions.assertTrue(cast.movedFrom(10.5 + 0.1, 64.0, -3.5),
                "sideways");
        org.junit.jupiter.api.Assertions.assertTrue(cast.movedFrom(10.5, 64.0 + 1.0, -3.5),
                "straight up, which is what a jump looks like from here");
        org.junit.jupiter.api.Assertions.assertTrue(cast.movedFrom(10.5, 62.0, -3.5),
                "straight down, which is what falling looks like from here");
    }

    @Test
    @DisplayName("small drifts on every axis at once still add up correctly")
    void axesCombineAsDistance() {
        // The rule is distance, not per-axis: three drifts each just under the line must trip it
        // together. A per-axis check would let a player creep diagonally without ever moving.
        CastState cast = new CastState(120, 0, 0, 0);
        org.junit.jupiter.api.Assertions.assertTrue(cast.movedFrom(0.04, 0.04, 0.04));
    }
}
