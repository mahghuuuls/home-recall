package com.mahghuuuls.homerecall.recall;

/**
 * Why the server declined to start a recall.
 *
 * <p>Every refusal has a reason, and the reason always reaches the diagnostic log. Most also
 * reach the player: a refusal the player could not predict deserves a sentence. One does not —
 * a press during their own item use is refused in silence, because they are mid-bite and know
 * exactly why nothing happened. The log still records it either way; a recall that is refused
 * looks exactly like a broken keybind from outside, and the named cause is what tells them apart.
 */
public enum RefusalReason {

    /** The player is dead, or otherwise not in a state that can be moved. */
    NOT_ALIVE("homerecall.refused.not_alive"),

    /** No personal spawn, and the world-spawn fallback is switched off. */
    NO_DESTINATION("homerecall.refused.no_destination"),

    /** The destination is in another dimension and cross-dimension recall is switched off. */
    CROSS_DIMENSION_DISABLED("homerecall.refused.cross_dimension_disabled"),

    /**
     * The destination is in another dimension, cross-dimension recall is permitted, and the
     * transfer is not built yet.
     *
     * <p>Separate from CROSS_DIMENSION_DISABLED on purpose. Telling a player their
     * configuration is off when they switched it on is a false diagnostic, and telling those two
     * causes apart is the entire reason this enum exists.
     */
    CROSS_DIMENSION_NOT_IMPLEMENTED("homerecall.refused.cross_dimension_not_implemented"),

    /**
     * The player is in the middle of using an item: eating, drinking, drawing a bow, or holding
     * a shield up. (In 1.12 those are the only vanilla uses; swords do not block.)
     *
     * <p>The one silent refusal, by the owner's rule: the player is mid-bite and knows exactly
     * why nothing happened, so a message would explain the self-evident. They finish and press
     * again. Null key rather than a special case at the call site, so the "does it speak" rule
     * lives here with its reason, the same shape the cancellations use.
     */
    USING_ITEM(null);

    private final String translationKey;

    RefusalReason(String translationKey) {
        this.translationKey = translationKey;
    }

    /**
     * The language key for the short message shown to the player, or null for the refusal that
     * deliberately says nothing.
     */
    public String translationKey() {
        return translationKey;
    }
}
