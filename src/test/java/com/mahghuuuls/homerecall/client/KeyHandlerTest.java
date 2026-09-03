package com.mahghuuuls.homerecall.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the key rule as an edge, written after a held key in the target pack toggled the cast
 * every tick: with keyboard repeat events left on by another mod, press counts lie, and only
 * the down transition is trustworthy — plus the tap too quick to still be down when sampled,
 * which the queue witnesses (review B2: a pure down-edge rule dropped those silently).
 */
class KeyHandlerTest {

    @Test
    @DisplayName("the key going down is one request")
    void pressIsARequest() {
        assertTrue(KeyHandler.shouldRequest(true, true, false));
    }

    @Test
    @DisplayName("a tap that is already released when sampled still counts")
    void subTickTapIsARequest() {
        assertTrue(KeyHandler.shouldRequest(false, true, false),
                "a press and release inside one tick must not vanish");
    }

    @Test
    @DisplayName("a held key is not a second request, however long it is held or repeated")
    void holdIsNotARequest() {
        boolean before = false;
        int requests = 0;
        for (int tick = 0; tick < 100; tick++) {
            // Repeat events on: the queue reports a press every tick of the hold.
            if (KeyHandler.shouldRequest(true, true, before)) {
                requests++;
            }
            before = true;
        }
        assertEquals(1, requests, "a hundred held ticks must cost exactly one request");
    }

    @Test
    @DisplayName("release then press again is a new request")
    void releaseThenPress() {
        assertFalse(KeyHandler.shouldRequest(false, false, true), "the release asks nothing");
        assertTrue(KeyHandler.shouldRequest(true, true, false), "the next press asks again");
    }

    @Test
    @DisplayName("the tick after a quick tap asks nothing on its own")
    void tapLeavesNoEdgeBehind() {
        assertFalse(KeyHandler.shouldRequest(false, false, false),
                "with the key up and no press witnessed there is nothing to send");
    }
}
