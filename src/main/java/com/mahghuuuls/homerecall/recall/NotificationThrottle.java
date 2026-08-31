package com.mahghuuuls.homerecall.recall;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rule that stops one player being told the same thing over and over.
 *
 * <p>Two things must both be true for a message to be suppressed: it is the same cause as the last
 * one, and it arrived inside the quiet window. A held key produces one message rather than a
 * stream; a player who fixes the first problem and immediately hits a second still hears about the
 * second at once.
 *
 * <p>Keyed on the message as well as the time, and that is the whole design. A time-only window
 * would silence the more informative message precisely when two things go wrong together, which is
 * when a player most needs to be told which one they are looking at.
 *
 * <p>No Minecraft types and no clock of its own. The caller supplies the time, so the window can be
 * evaluated against plain numbers instead of by waiting two seconds.
 */
final class NotificationThrottle {

    private final long quietMillis;
    private final Map<UUID, String> lastMessage = new HashMap<UUID, String>();
    private final Map<UUID, Long> lastAt = new HashMap<UUID, Long>();

    NotificationThrottle(long quietMillis) {
        this.quietMillis = quietMillis;
    }

    /**
     * Whether this message should be shown, recording it when the answer is yes.
     *
     * <p>Calling this is what marks the message as shown, so a caller that asks and then decides
     * not to display it will wrongly suppress the next one. Ask only when about to show.
     *
     * @param messageKey the message itself, which is also what identifies the cause. Keyed on the
     *                   message rather than on a separate cause value, so no caller can pass a
     *                   cause and a message that do not match.
     * @param nowMillis the current time in milliseconds, on whatever clock the caller uses
     *                  consistently
     */
    boolean allow(UUID player, String messageKey, long nowMillis) {
        if (messageKey == null) {
            // A silent cancellation cause has a null key. Reaching here with one means a caller
            // skipped its null check, and the alternative to failing is a blank action bar, which
            // a player reads as the mod being broken.
            throw new NullPointerException(
                    "A message key is required. A cause with no message must not be sent.");
        }
        Long previous = lastAt.get(player);
        if (previous != null && messageKey.equals(lastMessage.get(player))
                && nowMillis - previous.longValue() < quietMillis) {
            return false;
        }
        lastMessage.put(player, messageKey);
        lastAt.put(player, Long.valueOf(nowMillis));
        return true;
    }

    /**
     * Drops everything remembered about one player.
     *
     * <p>Called when they leave. Without it these two maps grow for the life of the server process,
     * one entry per player who has ever been told anything, and nothing would ever remove them.
     */
    void forget(UUID player) {
        lastMessage.remove(player);
        lastAt.remove(player);
    }

    /** Drops everything. */
    void clear() {
        lastMessage.clear();
        lastAt.clear();
    }
}
