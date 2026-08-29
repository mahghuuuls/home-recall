package com.mahghuuuls.homerecall.config;

import com.mahghuuuls.homerecall.Tags;
import net.minecraftforge.common.config.Config;

/**
 * Every option this mod has. Nothing reads these fields directly except
 * {@link ConfigSnapshot#refresh()}; the rest of the mod reads the snapshot, so normalization
 * happens once and no caller has to remember a rule.
 *
 * <p>Option fields must be public: Forge's ConfigManager skips every non-public field, so a
 * private option would silently keep its default. Keep anything that is not an option out of this
 * class entirely.
 *
 * <p>{@code category = ""} disables Forge's root category, which otherwise defaults to
 * {@code "general"} and wraps everything one level deeper: the five categories below would be
 * written as {@code general.general}, {@code general.equipment}, and so on. That is legal but it
 * is not the surface the requirements describe, and every key in it is a compatibility surface a
 * player has already edited by the time anyone notices. An empty root is allowed here because
 * every field below is a category object; a primitive at this level would be an error.
 */
@Config(modid = Tags.MOD_ID, name = Tags.MOD_ID, category = "")
public final class HomeRecallConfig {

    @Config.Name("general")
    @Config.Comment("How the recall itself behaves.")
    public static final General general = new General();

    @Config.Name("equipment")
    @Config.Comment("The Recall Stone, its slot, and how players obtain it.")
    public static final Equipment equipment = new Equipment();

    @Config.Name("visual")
    @Config.Comment("What a recall looks like. These change nothing about who may recall.")
    public static final Visual visual = new Visual();

    @Config.Name("audio")
    @Config.Comment("What a recall sounds like.")
    public static final Audio audio = new Audio();

    @Config.Name("diagnostics")
    @Config.Comment("Troubleshooting output. Off by default and silent when off.")
    public static final Diagnostics diagnostics = new Diagnostics();

    private HomeRecallConfig() {
    }

    public static final class General {

        @Config.Name("requireRecallStone")
        @Config.Comment({
                "Require a Recall Stone in the Home Recall slot before a player may recall.",
                "false makes recall an innate ability that needs no item.",
                "Forced to false when registerRecallStone is false, since a stone that cannot",
                "be obtained cannot be required."})
        public boolean requireRecallStone = true;

        @Config.Name("castTimeSeconds")
        @Config.Comment({
                "Seconds a player must stand through before the recall completes.",
                "They can still walk, jump, look around, and open their inventory during it,",
                "but they move slowly and cannot attack or use items.",
                "Example: 8. Values outside 1 to 300 are corrected to the nearest bound."})
        @Config.RangeInt(min = ConfigSnapshot.MIN_CAST_SECONDS, max = ConfigSnapshot.MAX_CAST_SECONDS)
        public int castTimeSeconds = 8;

        @Config.Name("castMovementSpeed")
        @Config.Comment({
                "How fast a player moves while recalling, as a fraction of their normal speed.",
                "Example: 0.2 is a fifth of normal. 1.0 turns the slow off and leaves them at",
                "full speed. 0.0 stops them completely.",
                "Values outside 0.0 to 1.0 are corrected to the nearest bound."})
        @Config.RangeDouble(min = ConfigSnapshot.MIN_CAST_SPEED, max = ConfigSnapshot.MAX_CAST_SPEED)
        public double castMovementSpeed = 0.2D;

        @Config.Name("allowCrossDimension")
        @Config.Comment({
                "Allow a recall to finish in a different dimension from where it started,",
                "for example returning from the Nether to an Overworld bed.",
                "false refuses the recall instead, and says so."})
        public boolean allowCrossDimension = true;

        @Config.Name("fallbackToWorldSpawn")
        @Config.Comment({
                "Send a player to the world spawn when they have no valid personal spawn,",
                "such as never having slept or having lost their bed.",
                "false refuses the recall instead."})
        public boolean fallbackToWorldSpawn = true;
    }

    public static final class Equipment {

        @Config.RequiresMcRestart
        @Config.Name("registerRecallStone")
        @Config.Comment({
                "Whether the Recall Stone system is available: the creative tab entry, the",
                "recipe, the inventory button, and the equipment screen.",
                "false makes recall an innate ability instead.",
                "The item itself stays registered either way. Removing a registered item from",
                "a save makes Minecraft offer to strip it, which destroys every stone players",
                "already have, and makes a dedicated server refuse the world outright. Hiding",
                "it is safe; unregistering it is not.",
                "Takes effect on next game start."})
        public boolean registerRecallStone = true;

        @Config.RequiresMcRestart
        @Config.Name("registerRecallStoneRecipe")
        @Config.Comment({
                "Whether the Recall Stone crafting recipe exists.",
                "false leaves the item craftable only by other means a pack provides.",
                "Takes effect on next game start."})
        public boolean registerRecallStoneRecipe = true;

        @Config.Name("keepRecallStoneOnDeath")
        @Config.Comment({
                "Keep the equipped Recall Stone in its slot through death.",
                "When false it follows the rest of the inventory, including into a grave."})
        public boolean keepRecallStoneOnDeath = true;

        @Config.Name("giveRecallStoneToNewPlayers")
        @Config.Comment({
                "Give each player one Recall Stone the first time they join a world.",
                "Happens once per player per world and never repeats.",
                "Off by default, so the recipe is the normal way to get one."})
        public boolean giveRecallStoneToNewPlayers = false;

        @Config.Name("showInventoryButton")
        @Config.Comment({
                "Show the Home Recall button beside the inventory panel.",
                "false hides it for players who do not want it there. Recall still works,",
                "but the equipment screen becomes unreachable."})
        public boolean showInventoryButton = true;
    }

    public static final class Visual {

        @Config.Name("enableParticles")
        @Config.Comment("Show the particle effect while a recall is being cast.")
        public boolean enableParticles = true;

        @Config.Name("enableCastHud")
        @Config.Comment("Show the progress bar while your own recall is being cast.")
        public boolean enableCastHud = true;
    }

    public static final class Audio {

        @Config.Name("enableRecallSounds")
        @Config.Comment("Play the casting, departure, and arrival sounds.")
        public boolean enableRecallSounds = true;
    }

    public static final class Diagnostics {

        @Config.Name("enableDiagnostics")
        @Config.Comment({
                "Log why each recall was allowed or refused, where it sent the player, and why",
                "any cast ended. For working out why a recall did nothing, which otherwise",
                "looks the same as the mod being broken.",
                "Off by default and writes nothing at all when off."})
        public boolean enableDiagnostics = false;
    }
}
