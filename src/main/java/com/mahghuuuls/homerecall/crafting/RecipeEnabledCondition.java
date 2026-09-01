package com.mahghuuuls.homerecall.crafting;

import com.google.gson.JsonObject;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.diagnostics.Diagnostics;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.function.BooleanSupplier;

/**
 * The recipe condition behind {@code registerRecallStoneRecipe} — and, since IMP-012, behind
 * {@code registerRecallStone} too: a hidden stone system disables its recipe as surely as the
 * recipe's own switch.
 *
 * <p>Recipes load once, at start, so this is evaluated once and a change to the option waits for
 * the next start — which is why the snapshot's boot-pinned accessor is the right source and the
 * config comment says a restart is required.
 *
 * <p>The answer is recorded when diagnostics are on. Without that record, "recipe correctly
 * disabled" and "recipe silently broken" produce the same observation: a craft that yields
 * nothing.
 */
public final class RecipeEnabledCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                // Both reads inside this one supplier, per IMP-006's recorded note: Forge
                // short-circuits a condition list on the first false, so a second condition
                // would silently suppress the diagnostic below. A hidden stone system disables
                // its recipe as surely as the recipe's own switch. (REQ-024)
                ConfigSnapshot config = ConfigSnapshot.current();
                boolean enabled = config.registerRecallStoneRecipe()
                        && config.registerRecallStone();
                Diagnostics.recallStoneRecipeCondition(enabled);
                return enabled;
            }
        };
    }
}
