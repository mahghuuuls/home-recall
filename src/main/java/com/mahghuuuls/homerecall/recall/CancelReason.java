package com.mahghuuuls.homerecall.recall;

/**
 * Why a running cast ended before it could complete.
 *
 * <p>Separate from {@link RefusalReason}, which says why one never started. A player who presses
 * the key and gets nothing, and a player whose channel quietly broke, have very different
 * problems, and collapsing the two would lose exactly that distinction.
 *
 * <p>Each cause answers two independent questions, and they are independent on purpose.
 * {@link #fades()} asks "was the player present to watch the bar" — every cause but death and
 * logout, where nobody is looking. {@link #messageKey()} asks "was this not their own obvious
 * doing" — a player who stepped, swung, got hit, or pressed the key again needs no sentence about
 * it, while a portal or the End exit is worth a line. The two rules were once one; splitting them
 * is what lets a self-evident cause fade in silence.
 */
public enum CancelReason {

    /** The player died. No fade and no message: they are looking at a death screen. */
    DIED(false, null),

    /** The player disconnected. No fade and no message: nobody is there to see either. */
    LOGGED_OUT(false, null),

    /**
     * The player changed dimension by some route other than this recall completing. A portal is
     * the ordinary case; a spectator teleport across dimensions reaches this too, deliberately.
     * Kept as a message: entering a portal is not obviously "the thing that ate my recall".
     */
    CHANGED_DIMENSION(true, "homerecall.cancelled.changed_dimension"),

    /**
     * The player's entity was removed from the world while they were still alive — in practice,
     * the End exit portal, which fires no dimension-change event; only the tick loop can notice
     * it. Kept as a message for the same reason as the portal.
     */
    LEFT_THE_WORLD(true, "homerecall.cancelled.left_the_world"),

    /** The player moved away from the anchor, by any means. Fade only: they know they moved. */
    MOVED(true, null),

    /** The player did something and it happened. Fade only: they watched themselves do it. */
    ACTED(true, null),

    /** The player took damage that landed. Fade only: the hit itself is the message. */
    DAMAGED(true, null),

    /** The player pressed the key again. Fade only: it worked, and the bar fading says so. */
    CANCELLED_BY_PLAYER(true, null),

    /**
     * The Recall Stone left the slot mid-cast with the requirement on. Keeps its message by
     * Bundle 003's own carve-out: an item vanishing from a slot the player is not looking at is
     * not self-evident the way their own step or swing is.
     */
    STONE_REMOVED(true, "homerecall.cancelled.stone_removed");

    private final boolean fades;
    private final String messageKey;

    CancelReason(boolean fades, String messageKey) {
        this.fades = fades;
        this.messageKey = messageKey;
    }

    /**
     * Whether the bar freezes and fades for this cause, which is the rule "the player was present
     * to see it". False only for death and logout.
     */
    public boolean fades() {
        return fades;
    }

    /**
     * The language key for a short message, or null for a cause that is the player's own obvious
     * doing — or that nobody is present to read.
     */
    public String messageKey() {
        return messageKey;
    }
}
