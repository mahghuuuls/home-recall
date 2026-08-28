package com.mahghuuuls.homerecall.recall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which reason a cross-dimension destination is refused with.
 *
 * <p>Both refusals look identical from inside the game: the player presses a key and nothing
 * happens. The only thing separating them is the reason recorded, so a wrong reason is not a
 * cosmetic slip, it is the diagnostic asserting something false. An earlier version returned
 * "switched off" to a player who had switched it on, and nothing caught it.
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
    @DisplayName("cross-dimension with the setting on reports that it is not built yet")
    void permittedButUnbuiltSaysSo() {
        // The case the earlier version got wrong, and the one a player is more likely to hit,
        // because allowCrossDimension defaults to true.
        assertEquals(RefusalReason.CROSS_DIMENSION_NOT_IMPLEMENTED,
                RecallService.dimensionRefusalFor(0, -1, true));
    }

    @Test
    @DisplayName("every reason's message key actually exists in the language file")
    void everyReasonHasRealText() throws Exception {
        // Comparing a key against itself would pass with the key absent from en_us.lang, and the
        // player would then be shown the raw key. Reading the file is what makes this a check
        // rather than a restatement.
        Set<String> defined = new HashSet<String>();
        File lang = new File("src/main/resources/assets/homerecall/lang/en_us.lang");
        assertTrue(lang.isFile(), "expected the language file at " + lang.getAbsolutePath());

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(lang), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#")) {
                    defined.add(line.substring(0, equals).trim());
                }
            }
        } finally {
            reader.close();
        }

        for (RefusalReason reason : RefusalReason.values()) {
            assertTrue(defined.contains(reason.translationKey()),
                    reason + " has no text in en_us.lang: " + reason.translationKey());
        }
    }

    @Test
    @DisplayName("every refusal reason has a distinct, non-empty message key")
    void everyReasonIsDistinct() {
        // A duplicated key would make two causes show the same text, which is the same failure as
        // a duplicated constant wearing a different name.
        RefusalReason[] reasons = RefusalReason.values();
        for (int i = 0; i < reasons.length; i++) {
            String key = reasons[i].translationKey();
            if (key == null || key.isEmpty()) {
                throw new AssertionError(reasons[i] + " has no message key");
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
