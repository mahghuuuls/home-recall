package com.mahghuuuls.homerecall.recall;

/**
 * Why a running cast ended before it could complete.
 *
 * <p>Separate from {@link RefusalReason}, which says why one never started. A player who presses
 * the key and gets nothing, and a player whose cast was quietly dropped eight seconds later, have
 * very different problems, and collapsing the two would lose exactly that distinction.
 *
 * <p>Two causes carry no message, and only two. A player who has just died is looking at a death
 * screen, and one who has logged out is not there at all; an action-bar line would be shown to
 * nobody. Every other cause leaves the player standing somewhere they can read one, so every other
 * cause has one.
 */
public enum CancelReason {

    /** The player died. No message: they are on the death screen and cannot read one. */
    DIED(null),

    /** The player disconnected. No message: nobody is there to see it. */
    LOGGED_OUT(null),

    /**
     * The player changed dimension by some route other than this recall completing.
     *
     * <p>A portal is the ordinary case. A spectator teleport across dimensions reaches this too,
     * deliberately: it is still a dimension change the recall did not ask for, and the destination
     * it resolved may no longer be reachable.
     */
    CHANGED_DIMENSION("homerecall.cancelled.changed_dimension"),

    /**
     * The player's entity was removed from the world while they were still alive.
     *
     * <p>In practice this is the End exit portal, which builds the player a new entity rather than
     * moving the one they had. It fires no dimension-change event, so the only way to notice is
     * that the entity a cast belongs to has gone.
     *
     * <p>This does carry a message, and an earlier version of this enum said it should not. The
     * reasoning then was that the player lands on the credits screen and could not read one. That
     * is true exactly once: vanilla sends the credits only while the player's {@code seenCredits}
     * flag is false, so every End exit after their first drops them straight into the Overworld,
     * able to read an action bar and with no idea their recall had gone.
     */
    LEFT_THE_WORLD("homerecall.cancelled.left_the_world");

    private final String messageKey;

    CancelReason(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * The language key for the short message shown to the player, or null when this cause happens
     * somewhere nobody could read one.
     *
     * <p>Null rather than a companion "does this one have a message" call. One accessor is one
     * thing for a caller to get wrong instead of two.
     */
    public String messageKey() {
        return messageKey;
    }
}
