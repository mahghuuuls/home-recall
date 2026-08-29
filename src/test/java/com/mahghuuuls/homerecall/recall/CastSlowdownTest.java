package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the one piece of the slowdown that can be wrong without failing.
 *
 * <p>Applying and removing the modifier needs a live player entity, so it is checked at runtime,
 * not here. What is checked here is the conversion from "a fraction of normal speed" to a vanilla
 * operation-2 amount, because getting its sign wrong makes a recalling player <em>faster</em> and
 * nothing anywhere reports it: no exception, no log line, and a cast that still completes on time.
 *
 * <p>These establish the arithmetic only. They say nothing about whether the modifier is actually
 * unsaved, whether it is removed on every end path, or whether a player feels slow.
 */
class CastSlowdownTest {

    @Test
    @DisplayName("the default fifth of normal speed is a four-fifths reduction")
    void defaultFraction() {
        // Operation 2 multiplies the total by (1 + amount), so a fifth of normal speed is -0.8,
        // not 0.2. The positive value is the failure this whole class exists for.
        assertEquals(-0.8D, CastSlowdown.modifierAmount(0.2D), 0.0001D);
    }

    @Test
    @DisplayName("a full stop is a complete reduction")
    void fullStop() {
        assertEquals(-1.0D, CastSlowdown.modifierAmount(0.0D), 0.0001D);
    }

    @Test
    @DisplayName("normal speed is no change at all")
    void normalSpeed() {
        assertEquals(0.0D, CastSlowdown.modifierAmount(1.0D), 0.0001D);
    }

    @Test
    @DisplayName("every fraction the config allows produces a slow, never a speed boost")
    void noAllowedFractionEverSpeedsThePlayerUp() {
        // Walks the whole configurable range rather than the three values above, because a wrong
        // conversion could be correct at the endpoints and wrong between them.
        for (int step = 0; step <= 100; step++) {
            double fraction = step / 100.0D;
            double amount = CastSlowdown.modifierAmount(fraction);
            assertTrue(amount <= 0.0D,
                    "a fraction of " + fraction + " produced " + amount
                            + ", which would make a recalling player faster than normal");
            assertEquals(fraction, 1.0D + amount, 0.0001D,
                    "the amount must reproduce the requested fraction of normal speed");
        }
    }
}
