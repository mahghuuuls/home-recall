package com.mahghuuuls.homerecall.client.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the circle's progression: four bands, one per quarter, never out of range, never going
 * backwards. The requirement's own words are "sparse in the first quarter, visibly stronger by
 * the final quarter", and the band is the whole mechanism delivering that.
 */
class CastCircleTest {

    @Test
    @DisplayName("the four bands are the four quarters of the cast")
    void bandsAreQuarters() {
        // A 120-tick cast, the shipped default.
        assertEquals(1, CastCircle.band(0, 120));
        assertEquals(1, CastCircle.band(29, 120));
        assertEquals(2, CastCircle.band(30, 120));
        assertEquals(2, CastCircle.band(59, 120));
        assertEquals(3, CastCircle.band(60, 120));
        assertEquals(4, CastCircle.band(90, 120));
        assertEquals(4, CastCircle.band(119, 120));
    }

    @Test
    @DisplayName("the band never leaves 1 to 4, whatever the inputs")
    void bandIsClamped() {
        // The tick that completes the cast can present elapsed == duration; presentation must
        // shrug at it, not throw or invent a fifth band. Same for nonsense inputs.
        assertEquals(4, CastCircle.band(120, 120));
        assertEquals(4, CastCircle.band(999, 120));
        assertEquals(1, CastCircle.band(-5, 120));
        assertEquals(1, CastCircle.band(10, 0));
        assertEquals(1, CastCircle.band(10, -1));
    }

    @Test
    @DisplayName("intensity never decreases while a cast runs")
    void intensityIsMonotonic() {
        int previous = 1;
        for (int tick = 0; tick < 120; tick++) {
            int count = CastCircle.particlesThisTick(tick, 120);
            assertTrue(count >= previous,
                    "tick " + tick + " dropped from " + previous + " to " + count);
            assertTrue(count >= 1 && count <= 4, "tick " + tick + " spawned " + count);
            previous = count;
        }
    }

    @Test
    @DisplayName("a tick's particles are spread out on the ring, not clumped")
    void particlesAreSpreadEvenly() {
        // Asserted through the actual positions rather than the angle formula, so a formula
        // that collapsed every particle onto one spot could not certify itself. Four particles,
        // one tick: every pair must sit a meaningful distance apart on the ring.
        double[][] points = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double angle = CastCircle.angle(50, i, 4);
            points[i][0] = Math.cos(angle) * CastCircle.RADIUS;
            points[i][1] = Math.sin(angle) * CastCircle.RADIUS;
        }
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                double dx = points[a][0] - points[b][0];
                double dz = points[a][1] - points[b][1];
                assertTrue(Math.sqrt(dx * dx + dz * dz) > CastCircle.RADIUS * 0.5D,
                        "particles " + a + " and " + b + " are clumped together");
            }
        }
    }

    @Test
    @DisplayName("the ring swirls: consecutive ticks do not restack the same spots")
    void ringSwirls() {
        double first = CastCircle.angle(10, 0, 4);
        double next = CastCircle.angle(11, 0, 4);
        assertTrue(Math.abs(next - first) > 1.0E-6,
                "two consecutive ticks placed a particle on the same angle");
    }
}
