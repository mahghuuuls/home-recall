package com.mahghuuuls.homerecall.equipment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The grant's decision table. Once-ever is the whole promise: a grant that fires twice is a free
 * item dupe, and one that fires with the stone system off hands players an item the pack author
 * hid. Both are invisible until a player reports them.
 */
class GrantDecisionTest {

    @Test
    @DisplayName("the grant fires only when on, with the system on, for a first-timer")
    void grantsExactlyWhenAllThreeHold() {
        assertNull(EquipmentLifecycle.grantSkipReason(true, true, false),
                "all conditions met: the grant must fire");
    }

    @Test
    @DisplayName("each failed condition skips, and names itself")
    void everySkipHasItsReason() {
        assertEquals("the option is off",
                EquipmentLifecycle.grantSkipReason(false, true, false));
        assertEquals("the stone system is off",
                EquipmentLifecycle.grantSkipReason(true, false, false));
        assertEquals("already granted",
                EquipmentLifecycle.grantSkipReason(true, true, true));
    }

    @Test
    @DisplayName("already-granted wins even against every option flipped on")
    void onceEverBeatsEverything() {
        // The row that matters most: however the pack author toggles options later, a player
        // who was granted stays granted.
        assertNotNull(EquipmentLifecycle.grantSkipReason(true, true, true));
    }
}
