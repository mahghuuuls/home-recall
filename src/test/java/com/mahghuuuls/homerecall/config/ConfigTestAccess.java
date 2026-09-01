package com.mahghuuuls.homerecall.config;

/**
 * The one sanctioned door for tests outside this package that need a snapshot built from chosen
 * values. Wraps the same capture-restore discipline {@code ConfigSnapshotTest} follows, so a test
 * in another package cannot leak config state into its neighbours.
 */
public final class ConfigTestAccess {

    private static boolean savedRegisterStone;
    private static boolean savedRegisterRecipe;

    private ConfigTestAccess() {
    }

    /** Capture the fields this helper mutates, and reset the snapshot. Call in @BeforeEach. */
    public static void captureAndReset() {
        savedRegisterStone = HomeRecallConfig.equipment.registerRecallStone;
        savedRegisterRecipe = HomeRecallConfig.equipment.registerRecallStoneRecipe;
        ConfigSnapshot.resetForTest();
    }

    /** Build a snapshot with the two boot-pinned stone switches set as given. */
    public static void initializeWith(boolean registerRecallStone,
                                      boolean registerRecallStoneRecipe) {
        HomeRecallConfig.equipment.registerRecallStone = registerRecallStone;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = registerRecallStoneRecipe;
        ConfigSnapshot.initialize();
    }

    /** Restore the captured fields and reset again. Call in @AfterEach. */
    public static void restore() {
        HomeRecallConfig.equipment.registerRecallStone = savedRegisterStone;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = savedRegisterRecipe;
        ConfigSnapshot.resetForTest();
    }
}
