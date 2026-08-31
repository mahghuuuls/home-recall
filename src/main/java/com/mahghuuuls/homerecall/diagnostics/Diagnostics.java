package com.mahghuuuls.homerecall.diagnostics;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.recall.CancelReason;
import com.mahghuuuls.homerecall.recall.RecallDestination;
import com.mahghuuuls.homerecall.recall.RefusalReason;

/**
 * Every diagnostic record the mod can write, and the one place the enabled check lives.
 *
 * <p>Off by default, and off means silent: not quieter, not summarised, nothing at all. A player
 * who never touches the option should never see a line from here.
 *
 * <p>Records are written at state transitions only. Never per tick and never per frame. A cast
 * lasting six seconds produces a handful of lines, not one hundred and twenty, and that property
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

    /**
     * A running cast was ended before it could complete, and by what.
     *
     * <p>The counterpart to a refusal record. A cancelled cast and a cast that never started look
     * identical from outside: no teleport, and a player standing where they were.
     */
    public static void recallCancelled(String playerName, CancelReason reason) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: recall cancelled ({})", playerName, reason);
        }
    }

    /**
     * A player joined, and whether the server was holding a cast for them.
     *
     * <p>Written on every login, including the ordinary case of nothing to report, and that is the
     * whole point of it. "No cast survived the logout" and "no cast ever existed" produce exactly
     * the same observation from the player: they reconnect and are not teleported. A record that
     * only appeared on failure could not tell those apart, so the passing case has to say so out
     * loud.
     */
    public static void castStateAtLogin(String playerName, boolean castFound) {
        if (enabled()) {
            if (castFound) {
                HomeRecallMod.LOGGER.warn(
                        "{} logged in with a recall still held for them, which should not be "
                                + "possible: a logout ends every cast. It has been discarded.",
                        playerName);
            } else {
                HomeRecallMod.LOGGER.info("{}: logged in, no recall was being held", playerName);
            }
        }
    }

    /**
     * A cast was discarded because the player it belonged to could no longer be found at all.
     *
     * <p>Not expected: logout removes the cast while the player is still resolvable. Seeing this
     * means a player left by some route that did not.
     */
    public static void castDiscardedForMissingPlayer(java.util.UUID playerId) {
        if (enabled()) {
            HomeRecallMod.LOGGER.warn(
                    "discarded a recall for {}, who could no longer be found on the server",
                    playerId);
        }
    }

    /** A cast finished and the player was moved. */
    public static void recallCompleted(String playerName, RecallDestination destination) {
        if (enabled()) {
            HomeRecallMod.LOGGER.info("{}: recall completed to {}", playerName, destination);
        }
    }
}
