package com.mahghuuuls.homerecall.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.common.crafting.IConditionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the recipe data the game loads: the shape, the ingredients, the result, the condition
 * wiring, and the language entry. All of it is data Minecraft parses at start with no compiler in
 * the loop — a typo in any of these files fails silently as "crafting yields nothing" or a raw
 * key on screen, in game, on someone else's machine.
 */
class RecipeDataTest {

    @Test
    @DisplayName("the recipe is a pearl flanked by two diamonds, ringed by six stone")
    void recipeShape() {
        JsonObject recipe = readJson("/assets/homerecall/recipes/recall_stone.json");

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("SSS", pattern.get(0).getAsString());
        assertEquals("DED", pattern.get(1).getAsString());
        assertEquals("SSS", pattern.get(2).getAsString());

        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:stone",
                key.getAsJsonObject("S").get("item").getAsString());
        // minecraft:stone has subtypes (granite and friends), and Forge REFUSES a subtyped
        // ingredient without explicit data — refuses silently, dropping the whole recipe at load
        // with only a log line. Review round 1 caught exactly that: this recipe was dead in every
        // configuration until this field existed, so this assertion is what keeps it alive.
        assertEquals(0, key.getAsJsonObject("S").get("data").getAsInt(),
                "stone needs data 0 or the recipe is silently dropped");
        // Diamond and pearl have no subtypes, so no data field — the same rule from the other
        // side: adding one to a subtype-free item would be harmless, omitting one from a subtyped
        // item is fatal, and the assertions pin the fatal direction.
        assertEquals("minecraft:diamond",
                key.getAsJsonObject("D").get("item").getAsString());
        assertEquals("minecraft:ender_pearl",
                key.getAsJsonObject("E").get("item").getAsString());

        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("homerecall:recall_stone", result.get("item").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    @DisplayName("the recipe's condition names a factory that exists and is one")
    void conditionIsWired() throws Exception {
        JsonObject recipe = readJson("/assets/homerecall/recipes/recall_stone.json");
        String conditionType = recipe.getAsJsonArray("conditions")
                .get(0).getAsJsonObject().get("type").getAsString();
        assertEquals("homerecall:recipe_enabled", conditionType);

        JsonObject factories = readJson("/assets/homerecall/recipes/_factories.json");
        String factoryClass = factories.getAsJsonObject("conditions")
                .get("recipe_enabled").getAsString();
        // The class name in _factories.json reaches the game as a string; only loading it here
        // proves the string and the class cannot drift apart.
        Class<?> factory = Class.forName(factoryClass);
        assertTrue(IConditionFactory.class.isAssignableFrom(factory),
                factoryClass + " must implement IConditionFactory");
        // Forge instantiates the factory with newInstance(); a private or parameterized
        // constructor would pass the type check above and still drop the recipe at load.
        assertNotNull(factory.newInstance());
    }

    @Test
    @DisplayName("the item has a display name in the language file")
    void itemHasAName() throws Exception {
        // Read directly rather than through the recall package's LangKeys, which is deliberately
        // package-private; a duplicate five-line read beats widening that boundary for a test.
        InputStream stream = RecipeDataTest.class
                .getResourceAsStream("/assets/homerecall/lang/en_us.lang");
        assertNotNull(stream, "en_us.lang is not on the classpath");
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        boolean found = false;
        for (String line; (line = reader.readLine()) != null; ) {
            if (line.startsWith("item.homerecall.recall_stone.name=")) {
                found = true;
            }
        }
        assertTrue(found, "a missing entry shows the player the raw key");
    }

    private static JsonObject readJson(String resourcePath) {
        InputStream stream = RecipeDataTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, resourcePath + " is not on the classpath");
        return new JsonParser()
                .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
