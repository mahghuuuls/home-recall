package com.mahghuuuls.homerecall.diagnostics;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.recall.RecallDestination;
import com.mahghuuuls.homerecall.recall.RefusalReason;

/**
 * Every diagnostic record the mod can write, and the one place the enabled check lives.
 *
 * <p>Off by default, and off means silent: not quieter, not summarised, nothing at all. A player
 * who never touches the option should never see a line from here.
 *
 * <p>Records are written at state transitions only. Never per tick and never per frame. A cast
 * lasting eight seconds produces a handful of lines, not one hundred and sixty, and that property
 * is checkable by counting.
 *
 * <p>These exist because most of what this mod decides is invisible. A refused recall and a broken
 * keybind produce the same observation: the player presses a key and nothing happens. A record
 * naming the reason is what separates the two. It is not proof on its own, though: a component
 * reporting its own conclusion proves only that it reached it, so a claim here is corroborated
 * against something outside the mod wherever it matters.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    private static boolean enabled() {
        return ConfigSnapshot.current().enableDiagnostics();
    }

    /** A recall request arrived and was accepted. */
    public static void recallStarted(String playerName, int durationTicks) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: recall started, {} ticks", playerName, durationTicks);
        }
    }

    /** A recall request arrived and was declined, with the reason that decided it. */
    public static void recallRefused(String playerName, RefusalReason reason) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: recall refused ({})", playerName, reason);
        }
    }

    /**
     * A destination was resolved. Names the branch as well as the position, because coordinates
     * alone cannot show which rule produced them.
     */
    public static void destinationResolved(String playerName, RecallDestination destination) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: destination resolved to {}", playerName, destination);
        }
    }

    /** No destination could be resolved, so the recall was declined before it began. */
    public static void destinationUnresolved(String playerName, boolean fallbackAllowed) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info(
                    "{}: no valid personal spawn and world-spawn fallback is {}",
                    playerName, fallbackAllowed ? "on" : "off");
        }
    }

    /**
     * How many casts were still running when the server stopped.
     *
     * <p>Logged even when the count is zero, because zero is the answer the "quit to menu
     * mid-cast" check is looking for. A record that only appears on failure cannot distinguish a
     * pass from a mod that never ran.
     */
    public static void castsDiscardedAtServerStop(int count) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("discarding {} recall(s) still running at server stop", count);
        }
    }

    /** A cast finished and the player was moved. */
    public static void recallCompleted(String playerName, RecallDestination destination) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: recall completed to {}", playerName, destination);
        }
    }
}
