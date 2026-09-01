package com.mahghuuuls.homerecall.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the equipment screen's language entries. A missing one does not fail anything at build or
 * load — the player just reads {@code homerecall.gui.equipment.title} on their screen. This test
 * is the owner the language-sweep's rescoping comment promised these keys would have.
 */
class GuiLangTest {

    @Test
    @DisplayName("the slot label has real text")
    void screenTextExists() throws Exception {
        Set<String> keys = new HashSet<String>();
        InputStream stream = GuiLangTest.class
                .getResourceAsStream("/assets/homerecall/lang/en_us.lang");
        assertNotNull(stream, "en_us.lang is not on the classpath");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String line; (line = reader.readLine()) != null; ) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                keys.add(line.substring(0, eq));
            }
        }
        assertTrue(keys.contains("homerecall.gui.equipment.slot"),
                "the slot label would render as its raw key");
    }
}
