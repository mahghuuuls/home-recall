package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins which refusals speak and which one deliberately does not.
 *
 * <p>Silence is the feature for exactly one cause, and a table nobody pins drifts: a later reason
 * added with a null key by accident would be invisible to the player and to every test that only
 * reads messages that exist.
 */
class RefusalReasonTest {

    @Test
    @DisplayName("exactly one refusal is silent: the player's own item use")
    void onlyTheItemUseRefusalIsSilent() {
        // A refusal the player could not predict deserves a sentence; being mid-bite is the one
        // cause that explains itself. Every other reason must keep speaking.
        assertNull(RefusalReason.USING_ITEM.translationKey());
        for (RefusalReason reason : RefusalReason.values()) {
            if (reason != RefusalReason.USING_ITEM) {
                assertNotNull(reason.translationKey(),
                        reason + " must have a message; only USING_ITEM is self-evident");
            }
        }
    }

}
