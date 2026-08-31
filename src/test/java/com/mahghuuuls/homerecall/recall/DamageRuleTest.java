package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which landed damage belongs to the damage handler and which to the death handler.
 *
 * <p>The boundary matters because the damage event fires before health drops. A killing blow
 * taken by the damage handler would record a survivable hit for a player who is dead a moment
 * later, and would put the cancellation fade over their death screen. The rule itself is one
 * comparison; what this pins is the boundary, because either off-by-one direction produces a
 * visible wrong outcome only a played death would catch.
 */
class DamageRuleTest {

    @Test
    @DisplayName("a survivable hit breaks the channel")
    void survivableHitBreaks() {
        assertTrue(RecallService.breaksChannel(1.0F, 20.0F));
        assertTrue(RecallService.breaksChannel(19.5F, 20.0F));
        assertTrue(RecallService.breaksChannel(0.5F, 1.0F));
    }

    @Test
    @DisplayName("a killing blow is left for the death handler")
    void killingBlowIsNotTakenHere() {
        // Exactly lethal and beyond lethal both put health at or below zero, so both are deaths.
        assertFalse(RecallService.breaksChannel(20.0F, 20.0F));
        assertFalse(RecallService.breaksChannel(21.0F, 20.0F));
        assertFalse(RecallService.breaksChannel(Float.MAX_VALUE, 20.0F));
    }

    @Test
    @DisplayName("zero landed damage is not damage")
    void zeroDoesNotBreak() {
        // The event fires after armor and absorption; an amount of zero means nothing reached the
        // health bar, and a channel must not break over damage that never happened.
        assertFalse(RecallService.breaksChannel(0.0F, 20.0F));
        assertFalse(RecallService.breaksChannel(-1.0F, 20.0F));
    }
}
