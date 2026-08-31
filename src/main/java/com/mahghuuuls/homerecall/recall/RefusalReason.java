package com.mahghuuuls.homerecall.recall;

/**
 * Why the server declined to start a recall.
 *
 * <p>Every refusal has a reason, and the reason reaches both the player and the diagnostic log.
 * A recall that is refused looks exactly like a recall that never happened: the player presses a
 * key and nothing occurs. Without a named cause, a working refusal and a broken keybind are the
 * same observation.
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
    CROSS_DIMENSION_NOT_IMPLEMENTED("homerecall.refused.cross_dimension_not_implemented");

    private final String translationKey;

    RefusalReason(String translationKey) {
        this.translationKey = translationKey;
    }

    /** The language key for the short message shown to the player. */
    public String translationKey() {
        return translationKey;
    }
}
