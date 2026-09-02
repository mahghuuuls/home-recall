package com.mahghuuuls.homerecall.client.render;

import com.mahghuuuls.homerecall.client.ClientCastState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one decision the render package makes: who shows a stone. The renderers themselves are
 * draw calls with no branches of their own, so this decision is the testable whole — a stone
 * shown for the wrong player, or kept showing after an end message, would otherwise only be
 * visible to someone standing in the right place at the right moment.
 */
class CastHeldItemTest {

    @AfterEach
    void clearBelief() {
        ClientCastState.clear();
    }

    @Test
    @DisplayName("the client's own player shows the stone exactly while their cast runs")
    void selfFollowsOwnCast() {
        assertFalse(CastHeldItem.showsStoneFor(true, 1), "no cast yet");
        ClientCastState.begin(120);
        assertTrue(CastHeldItem.showsStoneFor(true, 1), "cast running");
        ClientCastState.end(true);
        assertFalse(CastHeldItem.showsStoneFor(true, 1), "cast interrupted");
        ClientCastState.begin(120);
        ClientCastState.end(false);
        assertFalse(CastHeldItem.showsStoneFor(true, 1), "cast completed");
    }

    @Test
    @DisplayName("another player shows the stone exactly while their observed cast runs")
    void othersFollowTheObservedTable() {
        assertFalse(CastHeldItem.showsStoneFor(false, 42), "nothing observed yet");
        ClientCastState.beginObserved(42, 120, 0);
        assertTrue(CastHeldItem.showsStoneFor(false, 42), "observed cast running");
        assertFalse(CastHeldItem.showsStoneFor(false, 43),
                "a different player must not borrow the cast");
        ClientCastState.endObserved(42);
        assertFalse(CastHeldItem.showsStoneFor(false, 42), "observed cast ended");
    }

    @Test
    @DisplayName("the client's own cast never dresses another player")
    void ownCastIsNotEveryonesCast() {
        ClientCastState.begin(120);
        assertFalse(CastHeldItem.showsStoneFor(false, 42),
                "an observer entry must come from the wire, not from the self belief");
    }

    @Test
    @DisplayName("an observed cast whose end message was lost expires on its own")
    void lostEndExpires() {
        ClientCastState.beginObserved(42, 10, 0);
        for (int tick = 0; tick < 51; tick++) {
            ClientCastState.tick();
        }
        assertFalse(CastHeldItem.showsStoneFor(false, 42),
                "a dropped end packet must cost seconds, not a permanent stone");
    }
}
