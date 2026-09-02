package com.mahghuuuls.homerecall.config;

/**
 * The one sanctioned door for tests outside this package that need a snapshot built from chosen
 * values. Wraps the same capture-restore discipline {@code ConfigSnapshotTest} follows, so a test
 * in another package cannot leak config state into its neighbours.
 */
public final class ConfigTestAccess {

    private static boolean savedRegisterStone;
    private static boolean savedRegisterRecipe;
    private static boolean savedDiagnostics;

    private ConfigTestAccess() {
    }

    /** Capture the fields this helper mutates, and reset the snapshot. Call in @BeforeEach. */
    public static void captureAndReset() {
        savedRegisterStone = HomeRecallConfig.equipment.registerRecallStone;
        savedRegisterRecipe = HomeRecallConfig.equipment.registerRecallStoneRecipe;
        savedDiagnostics = HomeRecallConfig.diagnostics.enableDiagnostics;
        ConfigSnapshot.resetForTest();
    }

    /** Build a snapshot with the two boot-pinned stone switches set as given. */
    public static void initializeWith(boolean registerRecallStone,
                                      boolean registerRecallStoneRecipe) {
        HomeRecallConfig.equipment.registerRecallStone = registerRecallStone;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = registerRecallStoneRecipe;
        // Reset first, same as the diagnostics door: the two doors must chain in either order
        // without one of them tripping over a snapshot the other already built.
        ConfigSnapshot.resetForTest();
        ConfigSnapshot.initialize();
    }

    /** Build a snapshot with the diagnostics switch set as given, everything else as it stands. */
    public static void initializeWithDiagnostics(boolean enableDiagnostics) {
        HomeRecallConfig.diagnostics.enableDiagnostics = enableDiagnostics;
        ConfigSnapshot.resetForTest();
        ConfigSnapshot.initialize();
    }

    /** Restore the captured fields and reset again. Call in @AfterEach. */
    public static void restore() {
        HomeRecallConfig.equipment.registerRecallStone = savedRegisterStone;
        HomeRecallConfig.equipment.registerRecallStoneRecipe = savedRegisterRecipe;
        HomeRecallConfig.diagnostics.enableDiagnostics = savedDiagnostics;
        ConfigSnapshot.resetForTest();
    }
}
