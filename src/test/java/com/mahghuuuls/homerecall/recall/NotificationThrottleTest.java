package com.mahghuuuls.homerecall.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the anti-spam window.
 *
 * <p>Two rules, and the second is the one that gets lost. A repeat of the same cause inside the
 * window is suppressed; a <em>different</em> cause never is. A window keyed on time alone would
 * pass a careless reading of the first rule while silencing the more useful message exactly when
 * two things go wrong at once.
 *
 * <p>The clock is a parameter, so these run in microseconds rather than by waiting two seconds.
 * What they cannot establish is that the caller feeds a sane clock, or that the message reaches the
 * action bar rather than chat. Both are runtime checks.
 */
class NotificationThrottleTest {

    private static final long WINDOW = 2000L;

    private final UUID alice = UUID.nameUUIDFromBytes("alice".getBytes());
    private final UUID bob = UUID.nameUUIDFromBytes("bob".getBytes());

    @Test
    @DisplayName("the first message of any cause is always shown")
    void firstIsAlwaysShown() {
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        assertTrue(throttle.allow(alice, RefusalReason.NO_DESTINATION.translationKey(), 0L));
    }

    @Test
    @DisplayName("the same cause inside the window is suppressed")
    void repeatInsideWindowSuppressed() {
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);

        // A held key at twenty ticks a second. Every one of these is the same cause.
        for (long t = 50L; t < WINDOW; t += 50L) {
            assertFalse(throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), t),
                    "a repeat at " + t + "ms should still be inside the " + WINDOW + "ms window");
        }
    }

    @Test
    @DisplayName("the same cause once the window has passed is shown again")
    void repeatAfterWindowShown() {
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);

        assertFalse(throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), WINDOW - 1L));
        assertTrue(throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), WINDOW),
                "the window is exclusive at its own length");
    }

    @Test
    @DisplayName("a different cause is never suppressed, however soon it arrives")
    void differentCauseNeverSuppressed() {
        // The rule a time-only window would break. A player who is told they cannot recall right
        // now and then, one tick later, that they have nowhere to go must hear the second thing.
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);

        assertTrue(throttle.allow(alice, RefusalReason.NO_DESTINATION.translationKey(), 1L));
    }

    @Test
    @DisplayName("switching cause and back still suppresses only a genuine repeat")
    void causeSwitchResetsTheWindow() {
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);
        throttle.allow(alice, RefusalReason.NO_DESTINATION.translationKey(), 10L);

        // Back to the first cause. It is no longer the last one shown, so it is not a repeat.
        assertTrue(throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 20L));
    }

    @Test
    @DisplayName("a cancellation is never silenced by a refusal that came just before")
    void differentEnumsAreDifferentCauses() {
        // Both enums go through the same throttle. A cancellation must not be silenced by a
        // refusal that happened to be the last thing this player was told.
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);

        assertTrue(throttle.allow(alice, CancelReason.CHANGED_DIMENSION.messageKey(), 1L));
    }

    @Test
    @DisplayName("one player's messages never suppress another's")
    void playersAreIndependent() {
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);

        assertTrue(throttle.allow(bob, RefusalReason.NOT_ALIVE.translationKey(), 1L));
    }

    @Test
    @DisplayName("a null message key is refused rather than silently swallowed")
    void nullKeyFailsLoudly() {
        // A silent cause has a null key. A caller that forgot to check would otherwise put a
        // blank line in front of a player, which reads as the mod being broken.
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        try {
            throttle.allow(alice, null, 0L);
            org.junit.jupiter.api.Assertions.fail("a null key should not be accepted");
        } catch (NullPointerException expected) {
            // The contract is that callers pass a real key.
        }
    }

    @Test
    @DisplayName("forgetting a player clears their window rather than leaving it behind")
    void forgetClearsTheWindow() {
        // Called when a player leaves. Two things ride on it: the maps do not grow for the life of
        // the server, and a player who rejoins is not silenced by something they were told before.
        NotificationThrottle throttle = new NotificationThrottle(WINDOW);
        throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 0L);
        throttle.forget(alice);

        assertTrue(throttle.allow(alice, RefusalReason.NOT_ALIVE.translationKey(), 1L));
    }
}
