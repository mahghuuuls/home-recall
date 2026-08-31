package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the promises {@link CancelReason} makes about itself.
 *
 * <p>The one that matters is the language file. A cause whose key is missing does not fail, throw,
 * or log; the player is shown the raw key, which is the kind of defect that reaches a release
 * because nobody happened to trigger that particular cancellation while looking.
 */
class CancelReasonTest {

    @Test
    @DisplayName("every cause with a message has real text in the language file")
    void everyMessageKeyExists() {
        Set<String> keys = LangKeys.read();
        for (CancelReason reason : CancelReason.values()) {
            String key = reason.messageKey();
            if (key != null) {
                assertTrue(keys.contains(key),
                        reason + " claims the key " + key + ", which is not in en_us.lang");
            }
        }
    }

    @Test
    @DisplayName("no cancellation key collides with a refusal key")
    void causesAndRefusalsDoNotShareKeys() {
        // They share one anti-spam window, which is keyed on the message. Two different causes
        // reusing one key would silence each other, which is the exact failure that window exists
        // to avoid.
        Set<String> seen = new HashSet<String>();
        for (RefusalReason refusal : RefusalReason.values()) {
            assertTrue(seen.add(refusal.translationKey()), "duplicate key " + refusal);
        }
        for (CancelReason reason : CancelReason.values()) {
            if (reason.messageKey() != null) {
                assertTrue(seen.add(reason.messageKey()), "duplicate key " + reason);
            }
        }
    }

    @Test
    @DisplayName("only the two causes nobody could read are silent")
    void onlySilentWhereNobodyCouldRead() {
        // Death and logout leave the player somewhere no action bar reaches: a death screen, or
        // gone. Every other cause leaves them standing in the world.
        //
        // LEFT_THE_WORLD is the one to watch. An earlier version had it silent on the grounds that
        // the End exit shows the credits. That is true only the first time: vanilla sends the
        // credits while seenCredits is false, so every later End exit drops the player straight
        // into the Overworld, where they can read perfectly well.
        assertNull(CancelReason.DIED.messageKey());
        assertNull(CancelReason.LOGGED_OUT.messageKey());
        assertNotNull(CancelReason.CHANGED_DIMENSION.messageKey(),
                "a player who walks through a portal is standing right there");
        assertNotNull(CancelReason.LEFT_THE_WORLD.messageKey(),
                "a player leaving the End after their first time sees no credits and can read");
    }
}
