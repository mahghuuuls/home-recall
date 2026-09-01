package com.mahghuuuls.homerecall.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the other-players half of {@link ClientCastState}: independence between casts, the
 * self-expiry that caps a lost end message, and the local cast staying untouched by any of it.
 *
 * <p>Independence is the requirement's own word — one player's cast must not affect another's —
 * and it is exactly the property a shared-state bug would break invisibly with one caster.
 */
class ObservedCastTest {

    @BeforeEach
    void reset() {
        ClientCastState.clear();
    }

    @Test
    @DisplayName("an observed cast begins, advances on the shared tick, and ends by id")
    void observedLifecycle() {
        ClientCastState.beginObserved(7, 120, 0);
        assertEquals(1, ClientCastState.observed().size());

        for (int i = 0; i < 30; i++) {
            ClientCastState.tick();
        }
        assertEquals(30, ClientCastState.observed().get(0).getValue().elapsedTicks());

        ClientCastState.endObserved(7);
        assertTrue(ClientCastState.observed().isEmpty());
    }

    @Test
    @DisplayName("two observed casts are independent, and ending one leaves the other")
    void twoObserversAreIndependent() {
        ClientCastState.beginObserved(7, 120, 0);
        for (int i = 0; i < 40; i++) {
            ClientCastState.tick();
        }
        ClientCastState.beginObserved(9, 60, 0);
        for (int i = 0; i < 10; i++) {
            ClientCastState.tick();
        }

        assertEquals(2, ClientCastState.observed().size());
        int elapsed7 = -1;
        int elapsed9 = -1;
        for (java.util.Map.Entry<Integer, ClientCastState.ObservedCast> entry
                : ClientCastState.observed()) {
            if (entry.getKey() == 7) {
                elapsed7 = entry.getValue().elapsedTicks();
            } else if (entry.getKey() == 9) {
                elapsed9 = entry.getValue().elapsedTicks();
            }
        }
        assertEquals(50, elapsed7);
        assertEquals(10, elapsed9);

        ClientCastState.endObserved(7);
        assertEquals(1, ClientCastState.observed().size());
        assertEquals(9, (int) ClientCastState.observed().get(0).getKey());
    }

    @Test
    @DisplayName("the local cast and an observed cast do not touch each other")
    void localAndObservedAreIndependent() {
        ClientCastState.begin(120);
        ClientCastState.beginObserved(7, 60, 0);
        for (int i = 0; i < 20; i++) {
            ClientCastState.tick();
        }

        assertEquals(20, ClientCastState.elapsedTicks());
        assertEquals(20, ClientCastState.observed().get(0).getValue().elapsedTicks());

        ClientCastState.endObserved(7);
        assertTrue(ClientCastState.casting(), "ending an observed cast must not end the local one");

        ClientCastState.end(true);
        ClientCastState.beginObserved(9, 60, 0);
        assertFalse(ClientCastState.casting(), "an observed begin must not start a local cast");
    }

    @Test
    @DisplayName("an observed cast whose end message never arrives expires on its own")
    void lostEndExpires() {
        ClientCastState.beginObserved(7, 60, 0);
        // Duration plus the expiry slack: after the cast could not possibly still be running,
        // the entry drops even though no end was ever received. A dropped packet costs a ghost
        // circle for seconds, not for the session.
        for (int i = 0; i < 60 + 40; i++) {
            ClientCastState.tick();
            assertFalse(ClientCastState.observed().isEmpty(),
                    "expired too early, at tick " + i);
        }
        ClientCastState.tick();
        assertTrue(ClientCastState.observed().isEmpty(), "the lost-end entry never expired");
    }

    @Test
    @DisplayName("a fresh begin for the same caster replaces the old belief entirely")
    void reBeginReplaces() {
        ClientCastState.beginObserved(7, 120, 0);
        for (int i = 0; i < 100; i++) {
            ClientCastState.tick();
        }
        ClientCastState.beginObserved(7, 60, 0);

        assertEquals(1, ClientCastState.observed().size());
        assertEquals(0, ClientCastState.observed().get(0).getValue().elapsedTicks());
        assertEquals(60, ClientCastState.observed().get(0).getValue().durationTicks());
    }

    @Test
    @DisplayName("a late start joins the cast where it really is, expiry included")
    void lateStartCarriesProgress() {
        // The observer who walked into range at tick 80 of a 120-tick cast must see a circle in
        // its final-quarter density, and the entry must expire relative to the true start, not
        // relative to when this client first heard about it.
        ClientCastState.beginObserved(7, 120, 80);
        assertEquals(80, ClientCastState.observed().get(0).getValue().elapsedTicks());

        for (int i = 0; i < 40 + 40; i++) {
            ClientCastState.tick();
            assertFalse(ClientCastState.observed().isEmpty(), "expired too early at tick " + i);
        }
        ClientCastState.tick();
        assertTrue(ClientCastState.observed().isEmpty(),
                "a late-started entry must expire on the true timeline");
    }

    @Test
    @DisplayName("a round-tripped elapsed count survives the wire")
    void elapsedSurvivesTheWire() {
        // Belt to the message test's braces: the elapsed field exists for the late start alone,
        // so a message test that never asserts it non-zero would let it silently rot.
        com.mahghuuuls.homerecall.net.CastSyncMessage received =
                new com.mahghuuuls.homerecall.net.CastSyncMessage();
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        new com.mahghuuuls.homerecall.net.CastSyncMessage(7, true, 120, 80, false).toBytes(buf);
        received.fromBytes(buf);
        assertEquals(80, received.elapsedTicks());
    }

    @Test
    @DisplayName("clear forgets observed casts along with everything else")
    void clearForgetsObserved() {
        ClientCastState.beginObserved(7, 120, 0);
        ClientCastState.beginObserved(9, 120, 0);
        ClientCastState.clear();
        assertTrue(ClientCastState.observed().isEmpty());
    }
}
