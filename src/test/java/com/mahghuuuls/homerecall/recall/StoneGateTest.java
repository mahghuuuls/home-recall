package com.mahghuuuls.homerecall.recall;

import com.mahghuuuls.homerecall.item.ItemRecallStone;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Equipment-Gated Mode truth table, all four combinations, as IMP-011's verification plan
 * pre-committed: ignoring the requirement flag fails two of these; ignoring the slot fails the
 * other two. An inverted gate strands every player in Equipment-Gated Mode while every other
 * test stays green.
 */
class StoneGateTest {

    private static ItemStack stone;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        stone = new ItemStack(new ItemRecallStone());
    }

    @Test
    @DisplayName("requirement on: an empty slot blocks, a stone unlocks")
    void requirementOn() {
        assertTrue(RecallService.stoneGateBlocks(true, ItemStack.EMPTY),
                "no stone must mean no recall");
        assertFalse(RecallService.stoneGateBlocks(true, stone),
                "the equipped stone must unlock recall");
    }

    @Test
    @DisplayName("requirement off: the slot is ignored entirely")
    void requirementOff() {
        assertFalse(RecallService.stoneGateBlocks(false, ItemStack.EMPTY),
                "innate mode must not care about the empty slot");
        assertFalse(RecallService.stoneGateBlocks(false, stone),
                "innate mode must not care about the full slot either");
    }

    @Test
    @DisplayName("a non-stone in the slot does not unlock recall")
    void nonStoneDoesNotUnlock() {
        // Deserialization trusts disk, so the slot can theoretically hold anything; the gate
        // asks the accept rule, not emptiness, and a smuggled diamond stays locked out.
        assertTrue(RecallService.stoneGateBlocks(true, new ItemStack(Items.DIAMOND)),
                "a non-stone must not satisfy the requirement");
    }
}
