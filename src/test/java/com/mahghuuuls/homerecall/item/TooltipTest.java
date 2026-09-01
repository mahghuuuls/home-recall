package com.mahghuuuls.homerecall.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the tooltip's one rule with a wrong answer: claiming the stone is required when recall is
 * innate (REQ-034). The line text itself is the language file's business; which line is asked
 * for is this class's.
 */
class TooltipTest {

    @Test
    @DisplayName("the ability line follows the mode")
    void abilityLineFollowsMode() {
        assertEquals("homerecall.tooltip.recall_stone.required",
                ItemRecallStone.abilityLineKey(true));
        assertEquals("homerecall.tooltip.recall_stone.innate",
                ItemRecallStone.abilityLineKey(false));
    }
}
