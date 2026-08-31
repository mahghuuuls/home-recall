package com.mahghuuuls.homerecall.recall;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which reason a cross-dimension destination is refused with.
 *
 * <p>A refusal and a permitted transfer look identical at the moment of the decision: the same
 * method answers both. A wrong answer here either strands a player who should have travelled or
 * tells them their configuration is off when they switched it on — an earlier version did the
 * second, and nothing caught it.
 */
class DimensionRefusalTest {

    @Test
    @DisplayName("a destination in the same dimension is not refused")
    void sameDimensionIsFine() {
        assertNull(RecallService.dimensionRefusalFor(0, 0, true));
        assertNull(RecallService.dimensionRefusalFor(0, 0, false));
        assertNull(RecallService.dimensionRefusalFor(-1, -1, true));
    }

    @Test
    @DisplayName("cross-dimension with the setting off reports that it is off")
    void disabledReportsDisabled() {
        assertEquals(RefusalReason.CROSS_DIMENSION_DISABLED,
                RecallService.dimensionRefusalFor(0, -1, false));
    }

    @Test
    @DisplayName("cross-dimension with the setting on is permitted, in both directions")
    void permittedTravels() {
        // Until IMP-004 this case refused with "not built yet". The transfer exists now, so the
        // permitted answer is null — and the default configuration allows it, so this is the
        // branch nearly every real cross-dimension recall takes.
        assertNull(RecallService.dimensionRefusalFor(0, -1, true));
        assertNull(RecallService.dimensionRefusalFor(-1, 0, true));
        assertNull(RecallService.dimensionRefusalFor(0, 1, true));
    }

    @Test
    @DisplayName("every reason's message key actually exists in the language file")
    void everyReasonHasRealText() {
        // Comparing a key against itself would pass with the key absent from en_us.lang, and the
        // player would then be shown the raw key. Reading the file is what makes this a check
        // rather than a restatement.
        Set<String> defined = LangKeys.read();

        for (RefusalReason reason : RefusalReason.values()) {
            // The silent refusal has no key on purpose; which reasons may be silent is pinned by
            // RefusalReasonTest, not here.
            if (reason.translationKey() != null) {
                assertTrue(defined.contains(reason.translationKey()),
                        reason + " has no text in en_us.lang: " + reason.translationKey());
            }
        }
    }

    @Test
    @DisplayName("every refusal reason that speaks has a distinct, non-empty message key")
    void everyReasonIsDistinct() {
        // A duplicated key would make two causes show the same text, which is the same failure as
        // a duplicated constant wearing a different name. Null means deliberately silent and is
        // pinned by RefusalReasonTest; empty is always a mistake.
        RefusalReason[] reasons = RefusalReason.values();
        for (int i = 0; i < reasons.length; i++) {
            String key = reasons[i].translationKey();
            if (key == null) {
                continue;
            }
            if (key.isEmpty()) {
                throw new AssertionError(reasons[i] + " has an empty message key");
            }
            for (int j = i + 1; j < reasons.length; j++) {
                if (key.equals(reasons[j].translationKey())) {
                    throw new AssertionError(
                            reasons[i] + " and " + reasons[j] + " share a message key");
                }
            }
        }
    }
}
