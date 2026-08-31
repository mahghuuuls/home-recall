package com.mahghuuuls.homerecall.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraftforge.common.config.Config;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the normalization rules and the shipped defaults.
 *
 * <p>These reach {@link ConfigSnapshot#build()} through {@link ConfigSnapshot#refresh()}, which is
 * the same path preInit uses, so they exercise the production mapping rather than a copy of its
 * arithmetic. What they cannot establish is that Forge parses the file into those fields
 * correctly, or that the generated file reads well; both need a running game.
 */
class ConfigSnapshotTest {

    private boolean originalRegisterStone;
    private boolean originalRegisterRecipe;
    private boolean originalRequire;
    private int originalCast;
    private boolean originalCancelOnDamage;
    private boolean originalCrossDimension;
    private boolean originalWorldSpawn;
    private boolean originalKeepOnDeath;
    private boolean originalGrant;
    private boolean originalButton;
    private boolean originalParticles;
    private boolean originalHud;
    private boolean originalSounds;
    private boolean originalDiagnostics;

    // Every option is captured and restored, not only the three these tests currently mutate.
    // Restoring a subset works until someone adds a test that touches a fourth, and then it leaks
    // into whichever test runs next.
    @BeforeEach
    void captureDefaults() {
        originalRegisterStone = HomeRecallConfig.equipment.registerRecallStone;
        originalRegisterRecipe = HomeRecallConfig.equipment.registerRecallStoneRecipe;
        originalRequire = HomeRecallConfig.general.requireRecallStone;
        originalCast = HomeRecallConfig.general.castTimeSeconds;
        originalCancelOnDamage = HomeRecallConfig.general.cancelOnDamage;
        originalCrossDimension = HomeRecallConfig.general.allowCrossDimension;
        originalWorldSpawn = HomeRecallConfig.general.fallbackToWorldSpawn;
        originalKeepOnDeath = HomeRecallConfig.equipment.keepRecallStoneOnDeath;
        originalGrant = HomeRecallConfig.equipment.giveRecallStoneToNewPlayers;
        originalButton = HomeRecallConfig.equipment.showInventoryButton;
        originalParticles = HomeRecallConfig.visual.enableParticles;
        originalHud = HomeRecallConfig.visual.enableCastHud;
        originalSounds = HomeRecallConfig.audio.enableRecallSounds;
        originalDiagnostics = HomeRecallConfig.diagnostics.enableDiagnostics;
        ConfigSnapshot.resetForTest();
    }

    @AfterEach
    void restoreDefaults() {
        HomeRecallConfig.equipment.registerRecallStone = originalRegisterStone;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = originalRegisterRecipe;
        HomeRecallConfig.general.requireRecallStone = originalRequire;
        HomeRecallConfig.general.castTimeSeconds = originalCast;
        HomeRecallConfig.general.cancelOnDamage = originalCancelOnDamage;
        HomeRecallConfig.general.allowCrossDimension = originalCrossDimension;
        HomeRecallConfig.general.fallbackToWorldSpawn = originalWorldSpawn;
        HomeRecallConfig.equipment.keepRecallStoneOnDeath = originalKeepOnDeath;
        HomeRecallConfig.equipment.giveRecallStoneToNewPlayers = originalGrant;
        HomeRecallConfig.equipment.showInventoryButton = originalButton;
        HomeRecallConfig.visual.enableParticles = originalParticles;
        HomeRecallConfig.visual.enableCastHud = originalHud;
        HomeRecallConfig.audio.enableRecallSounds = originalSounds;
        HomeRecallConfig.diagnostics.enableDiagnostics = originalDiagnostics;
        ConfigSnapshot.resetForTest();
    }

    @Test
    @DisplayName("shipped defaults survive a refresh unchanged and report no correction")
    void shippedDefaults() {
        ConfigSnapshot.initialize();
        ConfigSnapshot snapshot = ConfigSnapshot.current();

        assertTrue(snapshot.registerRecallStone());
        assertTrue(snapshot.requireRecallStone());
        assertEquals(6, snapshot.castTimeSeconds());
        assertTrue(snapshot.cancelOnDamage());
        assertTrue(snapshot.allowCrossDimension());
        assertTrue(snapshot.fallbackToWorldSpawn());
        assertTrue(snapshot.registerRecallStoneRecipe());
        assertTrue(snapshot.keepRecallStoneOnDeath());
        assertFalse(snapshot.giveRecallStoneToNewPlayers());
        assertTrue(snapshot.showInventoryButton());
        assertTrue(snapshot.enableParticles());
        assertTrue(snapshot.enableCastHud());
        assertTrue(snapshot.enableRecallSounds());
        assertFalse(snapshot.enableDiagnostics());
        assertTrue(snapshot.corrections().isEmpty(),
                "a valid configuration must log nothing");
    }

    @Test
    @DisplayName("the default cast time is 120 ticks")
    void defaultCastTicks() {
        ConfigSnapshot.initialize();
        assertEquals(120, ConfigSnapshot.current().castTimeTicks());
    }

    @Test
    @DisplayName("requiring a stone that cannot be obtained is corrected to innate")
    void requireIsForcedFalseWhenTheStoneIsUnavailable() {
        HomeRecallConfig.equipment.registerRecallStone = false;
        HomeRecallConfig.general.requireRecallStone = true;
        ConfigSnapshot.initialize();

        ConfigSnapshot snapshot = ConfigSnapshot.current();
        assertFalse(snapshot.requireRecallStone(),
                "a stone that cannot be obtained cannot be required");
        assertEquals(1, snapshot.corrections().size());
        assertTrue(snapshot.corrections().get(0).contains("requireRecallStone"),
                "the correction must name the key the user has to fix");
    }

    @Test
    @DisplayName("the two switches agreeing on false is not a correction")
    void bothFalseIsNotCorrected() {
        HomeRecallConfig.equipment.registerRecallStone = false;
        HomeRecallConfig.general.requireRecallStone = false;
        ConfigSnapshot.initialize();

        ConfigSnapshot snapshot = ConfigSnapshot.current();
        assertFalse(snapshot.requireRecallStone());
        assertTrue(snapshot.corrections().isEmpty(),
                "nothing was wrong, so nothing should be reported");
    }

    @Test
    @DisplayName("a cast time below the minimum is raised to it")
    void castTimeBelowMinimum() {
        HomeRecallConfig.general.castTimeSeconds = 0;
        ConfigSnapshot.initialize();

        ConfigSnapshot snapshot = ConfigSnapshot.current();
        assertEquals(ConfigSnapshot.MIN_CAST_SECONDS, snapshot.castTimeSeconds());
        assertEquals(1, snapshot.corrections().size());
        assertTrue(snapshot.corrections().get(0).contains("castTimeSeconds"));
    }

    @Test
    @DisplayName("a cast time above the maximum is lowered to it")
    void castTimeAboveMaximum() {
        HomeRecallConfig.general.castTimeSeconds = 99999;
        ConfigSnapshot.initialize();

        ConfigSnapshot snapshot = ConfigSnapshot.current();
        assertEquals(ConfigSnapshot.MAX_CAST_SECONDS, snapshot.castTimeSeconds());
        assertEquals(1, snapshot.corrections().size());
        assertTrue(snapshot.corrections().get(0).contains("castTimeSeconds"));
    }

    @Test
    @DisplayName("both bounds are themselves valid and are not corrected")
    void boundsAreInclusive() {
        HomeRecallConfig.general.castTimeSeconds = ConfigSnapshot.MIN_CAST_SECONDS;
        ConfigSnapshot.initialize();
        assertEquals(ConfigSnapshot.MIN_CAST_SECONDS, ConfigSnapshot.current().castTimeSeconds());
        assertTrue(ConfigSnapshot.current().corrections().isEmpty());

        HomeRecallConfig.general.castTimeSeconds = ConfigSnapshot.MAX_CAST_SECONDS;
        ConfigSnapshot.refresh();
        assertEquals(ConfigSnapshot.MAX_CAST_SECONDS, ConfigSnapshot.current().castTimeSeconds());
        assertTrue(ConfigSnapshot.current().corrections().isEmpty());
    }

    @Test
    @DisplayName("two independent problems are reported separately")
    void bothRulesReportIndependently() {
        HomeRecallConfig.equipment.registerRecallStone = false;
        HomeRecallConfig.general.requireRecallStone = true;
        HomeRecallConfig.general.castTimeSeconds = 0;
        ConfigSnapshot.initialize();

        List<String> corrections = ConfigSnapshot.current().corrections();
        assertEquals(2, corrections.size(),
                "one message per corrected value, so the user can fix each one");
    }

    @Test
    @DisplayName("the approved bounds are 1 and 300, not merely whatever the constants say")
    void boundValuesMatchTheApprovedRange() {
        // Asserting MIN against MIN would pass however the constants were changed. The approved
        // requirements fix these two numbers, so the numbers are what the test has to name.
        assertEquals(1, ConfigSnapshot.MIN_CAST_SECONDS);
        assertEquals(300, ConfigSnapshot.MAX_CAST_SECONDS);
    }

    @Test
    @DisplayName("a boot-pinned option does not follow a later edit")
    void bootPinnedValuesIgnoreLaterEdits() {
        ConfigSnapshot.initialize();
        assertTrue(ConfigSnapshot.current().registerRecallStone());
        assertTrue(ConfigSnapshot.current().registerRecallStoneRecipe());

        // Item registration and recipe loading have already happened by the time a player can edit
        // the file, so the snapshot must keep reporting what those consumers actually saw.
        HomeRecallConfig.equipment.registerRecallStone = false;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = false;
        ConfigSnapshot.refresh();

        assertTrue(ConfigSnapshot.current().registerRecallStone(),
                "registerRecallStone is consumed at boot and must not follow a later edit");
        assertTrue(ConfigSnapshot.current().registerRecallStoneRecipe(),
                "registerRecallStoneRecipe is consumed at boot and must not follow a later edit");
    }

    @Test
    @DisplayName("the two boot-pinned options are not cross-wired either")
    void pinnedOptionsAreNotCrossWired() {
        // The one-at-a-time sweep cannot reach the pinned pair, and both default true, so a swap
        // of their constructor slots would survive every default-reading test. Pinning them to
        // different values at boot is the check that swap cannot survive.
        HomeRecallConfig.equipment.registerRecallStoneRecipe = false;
        ConfigSnapshot.initialize();

        assertTrue(ConfigSnapshot.current().registerRecallStone());
        assertFalse(ConfigSnapshot.current().registerRecallStoneRecipe());
    }

    @Test
    @DisplayName("a live option does follow a later edit")
    void liveValuesFollowLaterEdits() {
        ConfigSnapshot.initialize();
        assertEquals(6, ConfigSnapshot.current().castTimeSeconds());
        assertTrue(ConfigSnapshot.current().showInventoryButton());

        HomeRecallConfig.general.castTimeSeconds = 20;
        HomeRecallConfig.equipment.showInventoryButton = false;
        ConfigSnapshot.refresh();

        assertEquals(20, ConfigSnapshot.current().castTimeSeconds(),
                "castTimeSeconds is documented as needing no restart");
        assertFalse(ConfigSnapshot.current().showInventoryButton(),
                "showInventoryButton is documented as needing no restart");
    }

    @Test
    @DisplayName("the implication reads the pinned value, so it cannot flip mid-session")
    void implicationUsesThePinnedValue() {
        ConfigSnapshot.initialize();

        // Turning the stone system off after boot must not silently make recall innate for a
        // session whose items and recipes were built while it was on.
        HomeRecallConfig.equipment.registerRecallStone = false;
        HomeRecallConfig.general.requireRecallStone = true;
        ConfigSnapshot.refresh();

        assertTrue(ConfigSnapshot.current().requireRecallStone(),
                "the implication must read the boot value, not the edited one");
        assertTrue(ConfigSnapshot.current().corrections().isEmpty(),
                "nothing was actually inconsistent at boot, so nothing should be reported");
    }

    @Test
    @DisplayName("reading before initialize fails loudly instead of returning the declared defaults")
    void readingBeforeInitializeThrows() {
        // The dangerous version of this bug is silent: a snapshot built before Forge reads the
        // file would report the declared defaults and pin them, and nothing downstream could tell.
        // Failing here is what makes that impossible rather than merely unlikely.
        try {
            ConfigSnapshot.current();
            throw new AssertionError("reading before initialize must not return a value");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("initialize"),
                    "the message must name what was not done");
        }
    }

    @Test
    @DisplayName("refreshing before initialize fails, because there is nothing pinned to carry across")
    void refreshBeforeInitializeThrows() {
        try {
            ConfigSnapshot.refresh();
            throw new AssertionError("refresh before initialize must not succeed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("initialize"));
        }
    }

    @Test
    @DisplayName("initializing twice fails, because the second pin would be too late to trust")
    void initializingTwiceThrows() {
        ConfigSnapshot.initialize();
        try {
            ConfigSnapshot.initialize();
            throw new AssertionError("initialize must be callable only once");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already initialized"));
        }
    }

    @Test
    @DisplayName("flipping one option moves exactly one accessor, so no two are cross-wired")
    void eachOptionMovesOnlyItsOwnAccessor() throws Exception {
        // The snapshot constructor takes thirteen booleans by position. A transposition among
        // them compiles, and with most defaults being true it would also pass every test that
        // only reads defaults. Flipping the options one at a time is the check a swap cannot
        // survive: the flipped option's accessor must move, and no other may. The two boot-pinned
        // options cannot be swept this way; bootPinnedValuesIgnoreLaterEdits pins them apart.
        //
        // Reflection on purpose: each option's file name, its config field, and its snapshot
        // accessor share one name, and resolving both ends from that one name also proves the
        // three still agree.
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("requireRecallStone", HomeRecallConfig.general);
        options.put("cancelOnDamage", HomeRecallConfig.general);
        options.put("allowCrossDimension", HomeRecallConfig.general);
        options.put("fallbackToWorldSpawn", HomeRecallConfig.general);
        options.put("keepRecallStoneOnDeath", HomeRecallConfig.equipment);
        options.put("giveRecallStoneToNewPlayers", HomeRecallConfig.equipment);
        options.put("showInventoryButton", HomeRecallConfig.equipment);
        options.put("enableParticles", HomeRecallConfig.visual);
        options.put("enableCastHud", HomeRecallConfig.visual);
        options.put("enableRecallSounds", HomeRecallConfig.audio);
        options.put("enableDiagnostics", HomeRecallConfig.diagnostics);

        for (String flipped : options.keySet()) {
            restoreDefaults();
            ConfigSnapshot.initialize();
            Map<String, Boolean> baseline = new LinkedHashMap<String, Boolean>();
            for (String option : options.keySet()) {
                baseline.put(option, snapshotValue(option));
            }

            Field field = optionField(options.get(flipped), flipped);
            field.setBoolean(options.get(flipped), !field.getBoolean(options.get(flipped)));
            ConfigSnapshot.refresh();

            for (String option : options.keySet()) {
                boolean now = snapshotValue(option);
                if (option.equals(flipped)) {
                    assertNotEquals(baseline.get(option), Boolean.valueOf(now),
                            flipped + " was flipped but its accessor did not move");
                } else {
                    assertEquals(baseline.get(option), Boolean.valueOf(now),
                            option + " moved when only " + flipped + " was flipped");
                }
            }
        }
    }

    /** The snapshot accessor named exactly like the option, read from the current snapshot. */
    private static boolean snapshotValue(String option) throws Exception {
        return (Boolean) ConfigSnapshot.class.getMethod(option).invoke(ConfigSnapshot.current());
    }

    /** The config field carrying this option's {@code @Config.Name} inside its category object. */
    private static Field optionField(Object category, String option) {
        for (Field field : category.getClass().getDeclaredFields()) {
            Config.Name name = field.getAnnotation(Config.Name.class);
            if (name != null && option.equals(name.value())) {
                return field;
            }
        }
        throw new AssertionError("no option " + option + " in " + category.getClass().getName());
    }

    @Test
    @DisplayName("the corrections list cannot be modified by a caller")
    void correctionsAreUnmodifiable() {
        HomeRecallConfig.general.castTimeSeconds = 0;
        ConfigSnapshot.initialize();

        try {
            ConfigSnapshot.current().corrections().add("injected");
            throw new AssertionError("corrections must not be modifiable from outside");
        } catch (UnsupportedOperationException expected) {
            // The snapshot is immutable, including its lists.
        }
    }
}
