package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("every message key in the language file is owned by a living constant")
    void noOrphanedKeysInTheLanguageFile() {
        // The reverse of everyMessageKeyExists. A key nothing references any more survives every
        // build silently and ships as dead translator work — exactly what Bundle 003's silencing
        // could have left behind. Keybinding keys are the one family owned outside these enums.
        Set<String> owned = new HashSet<String>();
        for (RefusalReason refusal : RefusalReason.values()) {
            if (refusal.translationKey() != null) {
                owned.add(refusal.translationKey());
            }
        }
        for (CancelReason reason : CancelReason.values()) {
            if (reason.messageKey() != null) {
                owned.add(reason.messageKey());
            }
        }
        for (String key : LangKeys.read()) {
            // Scoped to the two families these enums own. GUI titles, item names, and keybind
            // labels have owners of their own and are pinned by their own tests.
            if (key.startsWith("homerecall.refused.") || key.startsWith("homerecall.cancelled.")) {
                assertTrue(owned.contains(key),
                        key + " is in en_us.lang but no constant claims it any more");
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
            if (refusal.translationKey() != null) {
                assertTrue(seen.add(refusal.translationKey()), "duplicate key " + refusal);
            }
        }
        for (CancelReason reason : CancelReason.values()) {
            if (reason.messageKey() != null) {
                assertTrue(seen.add(reason.messageKey()), "duplicate key " + reason);
            }
        }
    }

    @Test
    @DisplayName("the fade plays exactly when the player is present to see it")
    void fadeMeansPresent() {
        // Death and logout are the only places no bar is watched: a death screen, or gone.
        assertFalse(CancelReason.DIED.fades());
        assertFalse(CancelReason.LOGGED_OUT.fades());
        assertTrue(CancelReason.CHANGED_DIMENSION.fades());
        assertTrue(CancelReason.LEFT_THE_WORLD.fades());
        assertTrue(CancelReason.MOVED.fades());
        assertTrue(CancelReason.ACTED.fades());
        assertTrue(CancelReason.DAMAGED.fades());
        assertTrue(CancelReason.CANCELLED_BY_PLAYER.fades());
        assertTrue(CancelReason.STONE_REMOVED.fades());
    }

    @Test
    @DisplayName("messages accompany only causes that are not the player's own obvious doing")
    void messagesOnlyWhereNotSelfEvident() {
        // Stepping, swinging, being hit, and pressing the key again explain themselves; the fade
        // is their whole feedback. A portal and the End exit are the two that earn a sentence.
        assertNull(CancelReason.MOVED.messageKey());
        assertNull(CancelReason.ACTED.messageKey());
        assertNull(CancelReason.DAMAGED.messageKey());
        assertNull(CancelReason.CANCELLED_BY_PLAYER.messageKey());
        assertNull(CancelReason.DIED.messageKey());
        assertNull(CancelReason.LOGGED_OUT.messageKey());
        assertNotNull(CancelReason.CHANGED_DIMENSION.messageKey());
        assertNotNull(CancelReason.LEFT_THE_WORLD.messageKey());
        // The stone vanishing from an unwatched slot is Bundle 003's recorded exception: not
        // the player's own obvious doing, so it speaks.
        assertNotNull(CancelReason.STONE_REMOVED.messageKey());
    }

    @Test
    @DisplayName("no cause both fades nowhere and speaks - an absent player cannot be told anything")
    void noMessageWithoutPresence() {
        for (CancelReason reason : CancelReason.values()) {
            if (reason.messageKey() != null) {
                assertTrue(reason.fades(),
                        reason + " has a message but no fade, which would mean telling a player"
                                + " who is not there");
            }
        }
    }
}
