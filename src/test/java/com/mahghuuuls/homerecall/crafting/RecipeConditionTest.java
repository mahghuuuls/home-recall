package com.mahghuuuls.homerecall.crafting;

import com.mahghuuuls.homerecall.config.ConfigTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the recipe condition's two reads through the real supplier: the recipe's own switch AND
 * the stone system's. Either off must kill the recipe — a hidden stone system whose recipe still
 * crafts hands players an item that has no screen, no button, and no purpose.
 */
class RecipeConditionTest {

    @BeforeEach
    void snapshot() {
        ConfigTestAccess.captureAndReset();
    }

    @AfterEach
    void restore() {
        ConfigTestAccess.restore();
    }

    private static boolean evaluate() {
        BooleanSupplier condition = new RecipeEnabledCondition().parse(null, null);
        return condition.getAsBoolean();
    }

    @Test
    @DisplayName("both switches on: the recipe loads")
    void bothOn() {
        ConfigTestAccess.initializeWith(true, true);
        assertTrue(evaluate());
    }

    @Test
    @DisplayName("the recipe switch alone kills the recipe")
    void recipeSwitchOff() {
        ConfigTestAccess.initializeWith(true, false);
        assertFalse(evaluate());
    }

    @Test
    @DisplayName("hiding the stone system kills the recipe too")
    void stoneSystemOff() {
        ConfigTestAccess.initializeWith(false, true);
        assertFalse(evaluate());
    }
}
