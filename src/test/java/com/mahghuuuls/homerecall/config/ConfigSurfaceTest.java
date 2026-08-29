package com.mahghuuuls.homerecall.config;

import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shape of the generated configuration file: the exact category names, the exact option
 * names, their types, and the comment text the approved requirements make load-bearing.
 *
 * <p>Every option name here is a key in a file players and pack authors have already edited by the
 * time anyone notices a rename. A renamed key does not fail loudly; the option silently reverts to
 * its default and the user's setting is ignored. Nothing else in this project would catch that, so
 * this reads the annotations rather than trusting that they were typed correctly.
 *
 * <p>This reflects over {@link HomeRecallConfig} and needs no running game. It pins the annotation
 * surface, not the file: it cannot see the @Config root category, and it cannot show that Forge
 * writes what these annotations describe. Both need a real launch, and the root category is exactly
 * where an earlier version of this class let a defect through.
 *
 * <p>Names are compared as sets. Field declaration order is not guaranteed by the JVM, and Forge
 * writes each category alphabetically regardless, so asserting order would pin something that never
 * reaches the file.
 */
class ConfigSurfaceTest {

    /** The approved surface: category name to its option names, in the approved order. */
    private static final Map<String, List<String>> APPROVED = new LinkedHashMap<String, List<String>>();

    static {
        APPROVED.put("general", Arrays.asList(
                "requireRecallStone", "castTimeSeconds", "castMovementSpeed", "allowCrossDimension",
                "fallbackToWorldSpawn"));
        APPROVED.put("equipment", Arrays.asList(
                "registerRecallStone", "registerRecallStoneRecipe", "keepRecallStoneOnDeath",
                "giveRecallStoneToNewPlayers", "showInventoryButton"));
        APPROVED.put("visual", Arrays.asList("enableParticles", "enableCastHud"));
        APPROVED.put("audio", Arrays.asList("enableRecallSounds"));
        APPROVED.put("diagnostics", Arrays.asList("enableDiagnostics"));
    }

    @Test
    @DisplayName("the root category is disabled, so the five sections sit at the top level")
    void rootCategoryIsDisabled() {
        // Config.category() defaults to "general". Left alone, Forge writes every section one
        // level deeper inside a wrapper also called "general", so the real keys become
        // general.equipment.registerRecallStone. That is a different file from the one the
        // requirements describe, and every key in it is a compatibility surface. This assertion
        // exists because that defect shipped past a review that only read field annotations.
        Config config = HomeRecallConfig.class.getAnnotation(Config.class);
        assertNotNull(config, "HomeRecallConfig must carry @Config");
        assertEquals("", config.category(),
                "an empty root category is what puts the five sections at the top level");
    }

    @Test
    @DisplayName("five categories, named exactly as the requirements table says")
    void categoryNames() {
        List<String> found = new ArrayList<String>();
        for (Field field : HomeRecallConfig.class.getDeclaredFields()) {
            Config.Name name = field.getAnnotation(Config.Name.class);
            if (name != null) {
                found.add(name.value());
            }
        }
        assertEquals(new TreeSet<String>(APPROVED.keySet()), new TreeSet<String>(found),
                "the five approved category names");
        assertEquals(APPROVED.size(), found.size(), "no extra or duplicate category");
    }

    @Test
    @DisplayName("fourteen options, named exactly as the requirements table says")
    void optionNames() {
        int total = 0;
        for (Map.Entry<String, List<String>> entry : APPROVED.entrySet()) {
            List<String> found = optionNamesIn(categoryClass(entry.getKey()));
            assertEquals(new TreeSet<String>(entry.getValue()), new TreeSet<String>(found),
                    "option names in category " + entry.getKey());
            assertEquals(entry.getValue().size(), found.size(),
                    "no extra or duplicate option in category " + entry.getKey());
            total += found.size();
        }
        assertEquals(14, total, "the approved table has fourteen options");
    }

    @Test
    @DisplayName("every option carries a comment, because the generated file is the only place a player reads")
    void everyOptionIsDocumented() {
        for (String category : APPROVED.keySet()) {
            for (Field field : categoryClass(category).getDeclaredFields()) {
                Config.Comment comment = field.getAnnotation(Config.Comment.class);
                assertNotNull(comment, field.getName() + " has no comment");
                assertTrue(comment.value().length > 0, field.getName() + " has an empty comment");
            }
        }
    }

    @Test
    @DisplayName("the two boot-pinned options say a restart is required")
    void restartRequiredOptionsSaySo() {
        for (String option : Arrays.asList("registerRecallStone", "registerRecallStoneRecipe")) {
            Field field = optionField("equipment", option);
            assertTrue(commentOf(field).toLowerCase().contains("next game start"),
                    option + " must tell the user its edit needs a restart");
            assertNotNull(field.getAnnotation(Config.RequiresMcRestart.class),
                    option + " must carry @Config.RequiresMcRestart so Forge's own screen "
                            + "refuses an edit that would do nothing");
        }
    }

    @Test
    @DisplayName("registerRecallStone admits that the item stays registered")
    void registerRecallStoneExplainsWhatItReallyDoes() {
        // The option name promises more than it delivers. World safety forbids unregistering the
        // item, so the comment is the only place a reader learns what the switch actually changes.
        String comment = commentOf(optionField("equipment", "registerRecallStone")).toLowerCase();
        assertTrue(comment.contains("stays registered"),
                "the comment must say the item remains registered");
        assertTrue(comment.contains("destroys") || comment.contains("strip"),
                "the comment must say why, or the constraint reads as an arbitrary limitation");
    }

    @Test
    @DisplayName("the cast time range in the file comes from the one place that owns it")
    void castTimeRangeMatchesTheOwner() {
        Config.RangeInt range = optionField("general", "castTimeSeconds")
                .getAnnotation(Config.RangeInt.class);
        assertNotNull(range, "castTimeSeconds must declare its range for the config screen");
        assertEquals(ConfigSnapshot.MIN_CAST_SECONDS, range.min());
        assertEquals(ConfigSnapshot.MAX_CAST_SECONDS, range.max());
    }

    @Test
    @DisplayName("the cast speed range in the file comes from the one place that owns it")
    void castMovementSpeedRangeMatchesTheOwner() {
        Config.RangeDouble range = optionField("general", "castMovementSpeed")
                .getAnnotation(Config.RangeDouble.class);
        assertNotNull(range, "castMovementSpeed must declare its range for the config screen");
        assertEquals(ConfigSnapshot.MIN_CAST_SPEED, range.min());
        assertEquals(ConfigSnapshot.MAX_CAST_SPEED, range.max());
    }

    @Test
    @DisplayName("castMovementSpeed says what 1.0 does, because off is not the default")
    void castMovementSpeedExplainsHowToTurnItOff() {
        // A pack author who dislikes the slow has one way out, and the generated file is the only
        // place they will look for it. A range of 0.0 to 1.0 does not on its own say which end
        // means "leave me alone".
        String comment = commentOf(optionField("general", "castMovementSpeed")).toLowerCase();
        assertTrue(comment.contains("1.0"), "the comment must name the value that disables it");
        assertTrue(comment.contains("off") || comment.contains("full speed"),
                "the comment must say what that value actually does");
    }

    private static Class<?> categoryClass(String category) {
        for (Field field : HomeRecallConfig.class.getDeclaredFields()) {
            Config.Name name = field.getAnnotation(Config.Name.class);
            if (name != null && category.equals(name.value())) {
                return field.getType();
            }
        }
        throw new AssertionError("no category named " + category);
    }

    private static List<String> optionNamesIn(Class<?> category) {
        List<String> names = new ArrayList<String>();
        for (Field field : category.getDeclaredFields()) {
            Config.Name name = field.getAnnotation(Config.Name.class);
            if (name != null) {
                names.add(name.value());
            }
        }
        return names;
    }

    private static Field optionField(String category, String option) {
        for (Field field : categoryClass(category).getDeclaredFields()) {
            Config.Name name = field.getAnnotation(Config.Name.class);
            if (name != null && option.equals(name.value())) {
                return field;
            }
        }
        throw new AssertionError("no option " + option + " in category " + category);
    }

    private static String commentOf(Field field) {
        Config.Comment comment = field.getAnnotation(Config.Comment.class);
        assertNotNull(comment, field.getName() + " has no comment");
        StringBuilder joined = new StringBuilder();
        for (String line : comment.value()) {
            joined.append(line).append(' ');
        }
        return joined.toString();
    }
}
