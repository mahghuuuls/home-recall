package com.mahghuuuls.homerecall.diagnostics;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.config.ConfigTestAccess;
import com.mahghuuuls.homerecall.recall.CancelReason;
import com.mahghuuuls.homerecall.recall.RecallDestination;
import com.mahghuuuls.homerecall.recall.RefusalReason;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit's load-bearing test: off means silent — not quieter, nothing. Every {@code Diagnostics}
 * record method is called with diagnostics disabled and the log is watched at the root, so a
 * record escaping through any logger is seen; a single record made unconditional fails this. The
 * enabled half proves the same calls do produce output, so a gate accidentally stuck closed
 * cannot pass either. The list of calls is checked against the class by reflection, so adding a
 * record method without adding it here fails rather than silently shrinking the audit.
 * (Review S1/S2.)
 */
class DiagnosticsSilenceTest {

    /**
     * The fifteen record methods, by name, that {@link #callEveryRecord()} exercises.
     * {@code castStateAtLogin} is called on both branches (they log at different levels), which
     * is why sixteen calls cover fifteen methods.
     */
    private static final Set<String> CALLED = new HashSet<String>(Arrays.asList(
            "recallStarted", "recallRefused", "destinationResolved", "destinationUnresolved",
            "castsDiscardedAtServerStop", "recallCancelled", "castStateAtLogin",
            "castDiscardedForMissingPlayer", "recallTransferred", "castSyncSent",
            "recallStoneRecipeCondition", "stoneDeathPolicy", "stoneGranted",
            "stoneGrantSkipped", "recallCompleted"));

    private CapturingAppender appender;

    @BeforeEach
    void listenAndReset() {
        ConfigTestAccess.captureAndReset();
        appender = new CapturingAppender();
        appender.start();
        // The ROOT logger, not the mod's: additivity carries every logger's events here, so a
        // record routed through some new logger cannot slip past the silence assertion.
        ((Logger) org.apache.logging.log4j.LogManager.getRootLogger()).addAppender(appender);
    }

    @AfterEach
    void stopListeningAndRestore() {
        ((Logger) org.apache.logging.log4j.LogManager.getRootLogger()).removeAppender(appender);
        appender.stop();
        ConfigTestAccess.restore();
    }

    @Test
    @DisplayName("this test's call list is every record method Diagnostics declares")
    void theCallListIsComplete() {
        Set<String> declared = new HashSet<String>();
        for (Method method : Diagnostics.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && Modifier.isStatic(method.getModifiers())) {
                declared.add(method.getName());
            }
        }
        assertEquals(declared, CALLED,
                "a record method exists that this audit does not exercise (or one was removed)");
    }

    @Test
    @DisplayName("disabled means no output at all, across every Diagnostics record")
    void disabledIsSilent() {
        ConfigTestAccess.initializeWithDiagnostics(false);
        callEveryRecord();
        assertEquals(0, appender.captured.size(),
                "a record escaped the gate: " + appender.captured);
    }

    @Test
    @DisplayName("enabled produces one line per record, which proves the gate can open")
    void enabledSpeaks() {
        ConfigTestAccess.initializeWithDiagnostics(true);
        int made = callEveryRecord();
        assertEquals(made, appender.captured.size(),
                "each record call should produce exactly one line");
        for (String line : appender.captured) {
            assertTrue(line.length() > 0, "an empty record explains nothing");
        }
    }

    /**
     * One call to every record method in {@link #CALLED}, plus the second branch of
     * {@code castStateAtLogin}. Returns how many calls were made.
     */
    private static int callEveryRecord() {
        RecallDestination destination = new RecallDestination(0, 1.5, 64.0, -2.5,
                RecallDestination.Source.PERSONAL_SPAWN);
        Diagnostics.recallStarted("Tester", 120);
        Diagnostics.recallRefused("Tester", RefusalReason.NO_STONE);
        Diagnostics.destinationResolved("Tester", destination);
        Diagnostics.destinationUnresolved("Tester", true);
        Diagnostics.castsDiscardedAtServerStop(0);
        Diagnostics.recallCancelled("Tester", CancelReason.values()[0]);
        Diagnostics.castStateAtLogin("Tester", false);
        Diagnostics.castStateAtLogin("Tester", true);
        Diagnostics.castDiscardedForMissingPlayer(UUID.randomUUID());
        Diagnostics.recallTransferred("Tester", -1, 0);
        Diagnostics.castSyncSent("Tester", "start", 2);
        Diagnostics.recallStoneRecipeCondition(true);
        Diagnostics.stoneDeathPolicy("Tester", false, true, false);
        Diagnostics.stoneGranted("Tester", "the equipment slot");
        Diagnostics.stoneGrantSkipped("Tester", "already granted");
        Diagnostics.recallCompleted("Tester", destination);
        return 16;
    }

    /** Collects every event reaching the mod's logger, formatted, in order. */
    private static final class CapturingAppender extends AbstractAppender {

        final List<String> captured = new ArrayList<String>();

        CapturingAppender() {
            super("homerecall-test-capture", null, null);
        }

        @Override
        public void append(LogEvent event) {
            captured.add(event.getMessage().getFormattedMessage());
        }
    }
}
