package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Covers the half of "cancelling a non-caster is a no-op" that can be reached without a running
 * game.
 *
 * <p>Every lifecycle handler calls {@code cancel} unconditionally, which is the whole design: a
 * handler that has to check first is a handler that will one day forget. That only works if the
 * method tolerates being handed nothing.
 *
 * <p>The other half — a real player who exists but is not casting — needs a server, and is a
 * runtime check.
 */
class CancelIdempotenceTest {

    @Test
    @DisplayName("cancelling for no player at all answers false rather than failing")
    void nullPlayerIsANoOp() {
        for (CancelReason reason : CancelReason.values()) {
            assertFalse(RecallService.cancel(null, reason),
                    reason + " should report that there was nothing to cancel");
        }
    }
}
