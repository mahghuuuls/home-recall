package com.mahghuuuls.homerecall.recall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reads the shipped language file, so more than one test can check its keys without three copies
 * of the same parser drifting apart.
 *
 * <p>Reads the source file rather than the classpath copy on purpose: a missing key is a mistake
 * made while editing that file, and this is where it is cheapest to catch. The path is relative to
 * the module directory, which is where the test runner starts, and a missing file fails loudly
 * rather than quietly reporting an empty set of keys.
 */
final class LangKeys {

    private static final String PATH = "src/main/resources/assets/homerecall/lang/en_us.lang";

    private LangKeys() {
    }

    /**
     * Every key defined in the shipped English language file.
     *
     * <p>Fails on a duplicated key rather than merging it. Two lines defining the same key is a
     * silent defect: the file loads, the later line wins, and the earlier text simply never
     * appears. A set alone could not see it.
     */
    static Set<String> read() {
        File lang = new File(PATH);
        assertTrue(lang.isFile(), "expected the language file at " + lang.getAbsolutePath());

        Set<String> keys = new HashSet<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(lang), Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#")) {
                    String key = line.substring(0, equals).trim();
                    assertTrue(keys.add(key), key + " is defined twice in " + PATH);
                }
            }
        } catch (Exception failure) {
            fail("could not read the language file: " + failure);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // Nothing useful to do about a failed close in a test.
                }
            }
        }
        return keys;
    }
}
