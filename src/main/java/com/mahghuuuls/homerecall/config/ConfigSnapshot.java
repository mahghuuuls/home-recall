package com.mahghuuuls.homerecall.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The immutable values the rest of the mod reads.
 *
 * <p>{@link #initialize()} must be called once, at preInit, before anything reads this. That is
 * not a convention: it is when the two boot-pinned values below are captured, and Forge has read
 * the configuration file by then. Reading or rebuilding before it throws rather than quietly
 * handing back the declared defaults, because a snapshot silently reporting {@code true} for an
 * option the player set to {@code false} is the kind of failure nothing downstream can detect.
 *
 * <p>Rebuilt again when Forge posts a configuration change, which in practice means the in-game
 * configuration screen. A file edited by hand while the game is running is not picked up, and is
 * overwritten the next time that screen saves; changing the file is a between-sessions action.
 * Each correction is decided once per build, so a valid configuration says nothing at all.
 *
 * <p>This class is the only place a normalization rule lives. {@link #requireRecallStone()}
 * already accounts for {@link #registerRecallStone()}, so no caller has to remember that a stone
 * which cannot be obtained cannot be required. A rule applied at a call site is a rule that some
 * later call site will forget.
 *
 * <p><strong>Two values are pinned at boot and do not follow later edits:</strong>
 * {@link #registerRecallStone()} and {@link #registerRecallStoneRecipe()}. Both are consumed once,
 * during item registration and recipe loading, and neither can be undone afterwards. Rereading
 * them would produce a snapshot claiming the stone system is off while the item is registered and
 * the recipe is loaded, which is a worse answer than not following the edit. Because the
 * normalization reads the pinned value, {@link #requireRecallStone()} cannot flip mid-session
 * either. This is the one place the object is not a pure function of the current file; it is
 * forced by Forge's registry lifecycle rather than chosen.
 *
 * <p>Corrections are reported rather than applied to the file. Silently rewriting what the user
 * typed hides the mistake from the person who has to fix it, and leaves the stored value and the
 * effective value disagreeing.
 *
 * <p>Side-agnostic. This returns whichever side's file the calling process read. On a client
 * connected to a server, a value the server is authoritative over must arrive over the network,
 * not from here.
 */
public final class ConfigSnapshot {

    /** Shortest cast the mod will run. Below this the cast is not observable as a cast. */
    static final int MIN_CAST_SECONDS = 1;

    /** Longest cast the mod will run. Five minutes is already far past any plausible use. */
    static final int MAX_CAST_SECONDS = 300;

    /**
     * The boot values of the two options that cannot change in place. Set exactly once, by
     * {@link #initialize()}. Null before that, and every read path checks it rather than assuming.
     */
    private static volatile BootPinned pinned;

    /** Null until {@link #initialize()} runs. Deliberately not built at class load; see below. */
    private static volatile ConfigSnapshot current;

    private final boolean registerRecallStone;
    private final boolean requireRecallStone;
    private final int castTimeSeconds;
    private final boolean cancelOnDamage;
    private final boolean allowCrossDimension;
    private final boolean fallbackToWorldSpawn;
    private final boolean registerRecallStoneRecipe;
    private final boolean keepRecallStoneOnDeath;
    private final boolean giveRecallStoneToNewPlayers;
    private final boolean showInventoryButton;
    private final boolean enableParticles;
    private final boolean enableCastHud;
    private final boolean enableRecallSounds;
    private final boolean enableDiagnostics;
    private final List<String> corrections;

    private ConfigSnapshot(boolean registerRecallStone, boolean requireRecallStone,
                           int castTimeSeconds, boolean cancelOnDamage, boolean allowCrossDimension,
                           boolean fallbackToWorldSpawn, boolean registerRecallStoneRecipe,
                           boolean keepRecallStoneOnDeath, boolean giveRecallStoneToNewPlayers,
                           boolean showInventoryButton, boolean enableParticles,
                           boolean enableCastHud, boolean enableRecallSounds,
                           boolean enableDiagnostics, List<String> corrections) {
        this.registerRecallStone = registerRecallStone;
        this.requireRecallStone = requireRecallStone;
        this.castTimeSeconds = castTimeSeconds;
        this.cancelOnDamage = cancelOnDamage;
        this.allowCrossDimension = allowCrossDimension;
        this.fallbackToWorldSpawn = fallbackToWorldSpawn;
        this.registerRecallStoneRecipe = registerRecallStoneRecipe;
        this.keepRecallStoneOnDeath = keepRecallStoneOnDeath;
        this.giveRecallStoneToNewPlayers = giveRecallStoneToNewPlayers;
        this.showInventoryButton = showInventoryButton;
        this.enableParticles = enableParticles;
        this.enableCastHud = enableCastHud;
        this.enableRecallSounds = enableRecallSounds;
        this.enableDiagnostics = enableDiagnostics;
        this.corrections = corrections;
    }

    /**
     * Captures the boot-pinned values and builds the first snapshot. Call once, from preInit.
     *
     * <p>The timing is the contract. Forge reads the configuration file during mod construction,
     * before preInit, so preInit is the first moment the annotated fields hold what the player
     * wrote rather than the declared defaults. Pinning any earlier would freeze the defaults and
     * ignore the file for the rest of the session, with nothing to show for it in any log.
     *
     * <p>Calling this twice is a programming error rather than a no-op, because the second call
     * would mean something believed it was still safe to pin.
     */
    public static void initialize() {
        if (pinned != null) {
            throw new IllegalStateException(
                    "Home Recall configuration was already initialized. It pins values that are "
                            + "consumed once at start-up, so initializing twice would mean "
                            + "something read them before they were correct.");
        }
        pinned = new BootPinned(HomeRecallConfig.equipment.registerRecallStone,
                HomeRecallConfig.equipment.registerRecallStoneRecipe);
        current = build();
    }

    /** The values as they stood when the configuration was last read. */
    public static ConfigSnapshot current() {
        ConfigSnapshot snapshot = current;
        if (snapshot == null) {
            throw new IllegalStateException(
                    "Home Recall configuration was read before initialize(). Returning the "
                            + "declared defaults here would silently ignore the player's file.");
        }
        return snapshot;
    }

    /**
     * Rebuilds from {@link HomeRecallConfig} after Forge reports a configuration change. The
     * boot-pinned values are carried across rather than reread; see the class javadoc.
     */
    public static void refresh() {
        if (pinned == null) {
            throw new IllegalStateException(
                    "Home Recall configuration was refreshed before initialize(), so there are "
                            + "no boot-pinned values to carry across.");
        }
        current = build();
    }

    /** Returns the mod to its pre-initialize state. For tests only. */
    static void resetForTest() {
        pinned = null;
        current = null;
    }

    /** The one mapping from {@link HomeRecallConfig} fields to snapshot values. */
    private static ConfigSnapshot build() {
        List<String> corrections = new ArrayList<String>();

        BootPinned boot = pinned;
        boolean register = boot.registerRecallStone;
        boolean require = HomeRecallConfig.general.requireRecallStone;
        if (!register && require) {
            require = false;
            corrections.add("requireRecallStone was true while registerRecallStone was false. "
                    + "A Recall Stone that cannot be obtained cannot be required, so recall is "
                    + "an innate ability this session. Set one of the two to agree with the "
                    + "other to silence this.");
        }

        int cast = HomeRecallConfig.general.castTimeSeconds;
        if (cast < MIN_CAST_SECONDS) {
            corrections.add("castTimeSeconds was " + cast + ", below the minimum of "
                    + MIN_CAST_SECONDS + ". Using " + MIN_CAST_SECONDS + " this session.");
            cast = MIN_CAST_SECONDS;
        } else if (cast > MAX_CAST_SECONDS) {
            corrections.add("castTimeSeconds was " + cast + ", above the maximum of "
                    + MAX_CAST_SECONDS + ". Using " + MAX_CAST_SECONDS + " this session.");
            cast = MAX_CAST_SECONDS;
        }

        return new ConfigSnapshot(
                register,
                require,
                cast,
                HomeRecallConfig.general.cancelOnDamage,
                HomeRecallConfig.general.allowCrossDimension,
                HomeRecallConfig.general.fallbackToWorldSpawn,
                boot.registerRecallStoneRecipe,
                HomeRecallConfig.equipment.keepRecallStoneOnDeath,
                HomeRecallConfig.equipment.giveRecallStoneToNewPlayers,
                HomeRecallConfig.equipment.showInventoryButton,
                HomeRecallConfig.visual.enableParticles,
                HomeRecallConfig.visual.enableCastHud,
                HomeRecallConfig.audio.enableRecallSounds,
                HomeRecallConfig.diagnostics.enableDiagnostics,
                Collections.unmodifiableList(corrections));
    }

    /**
     * Whether the Recall Stone system is available to players. The item is registered either way;
     * this decides whether it can be obtained, seen, or equipped.
     *
     * <p>Pinned at boot. A later edit to the file does not change what this returns for the rest
     * of the session, because item registration has already happened.
     */
    public boolean registerRecallStone() {
        return registerRecallStone;
    }

    /**
     * Whether a player needs an equipped Recall Stone to recall. Already false whenever
     * {@link #registerRecallStone()} is false, so callers never repeat that rule.
     */
    public boolean requireRecallStone() {
        return requireRecallStone;
    }

    /** Cast length in seconds, already within {@value #MIN_CAST_SECONDS} to 300. */
    public int castTimeSeconds() {
        return castTimeSeconds;
    }

    /** Cast length in ticks, which is what the server actually counts. */
    public int castTimeTicks() {
        return castTimeSeconds * 20;
    }

    /** Whether damage that lands on a casting player breaks the channel. */
    public boolean cancelOnDamage() {
        return cancelOnDamage;
    }

    public boolean allowCrossDimension() {
        return allowCrossDimension;
    }

    public boolean fallbackToWorldSpawn() {
        return fallbackToWorldSpawn;
    }

    /**
     * Whether the crafting recipe exists. Pinned at boot: recipes are loaded once, so a later edit
     * cannot add or remove one for the rest of the session.
     */
    public boolean registerRecallStoneRecipe() {
        return registerRecallStoneRecipe;
    }

    public boolean keepRecallStoneOnDeath() {
        return keepRecallStoneOnDeath;
    }

    public boolean giveRecallStoneToNewPlayers() {
        return giveRecallStoneToNewPlayers;
    }

    public boolean showInventoryButton() {
        return showInventoryButton;
    }

    public boolean enableParticles() {
        return enableParticles;
    }

    public boolean enableCastHud() {
        return enableCastHud;
    }

    public boolean enableRecallSounds() {
        return enableRecallSounds;
    }

    public boolean enableDiagnostics() {
        return enableDiagnostics;
    }

    /**
     * One message per value this snapshot had to correct, in the order they were found. Empty when
     * the configuration was already consistent, which is the normal case and logs nothing.
     */
    public List<String> corrections() {
        return corrections;
    }

    /**
     * The two options captured on the first build. Held separately from the snapshot itself so
     * that "pinned at boot" is a property of the class rather than a convention each rebuild has
     * to remember.
     */
    private static final class BootPinned {

        private final boolean registerRecallStone;
        private final boolean registerRecallStoneRecipe;

        private BootPinned(boolean registerRecallStone, boolean registerRecallStoneRecipe) {
            this.registerRecallStone = registerRecallStone;
            this.registerRecallStoneRecipe = registerRecallStoneRecipe;
        }
    }
}
