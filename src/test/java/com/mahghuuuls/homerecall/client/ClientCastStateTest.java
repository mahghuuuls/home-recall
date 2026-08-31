package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.client.hud.CastBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the client cast state's transitions: begin, tick, the two kinds of end, and clear.
 *
 * <p>This sequencing was once labelled owner-observation-only, and review rejected the label: the
 * class is plain field bookkeeping, its {@code @SideOnly} annotation is inert off the game's
 * class transformer, and a wrong freeze or a fade that never ends is far easier to pin here than
 * by eye. What genuinely stays with the owner is everything drawn — position, colour, smoothness.
 *
 * <p>The state is static, as the production class is, so every test starts from {@code clear()}.
 */
class ClientCastStateTest {

    @BeforeEach
    void reset() {
        ClientCastState.clear();
    }

    @Test
    @DisplayName("a cleared state is idle: not casting, nothing fading")
    void clearedIsIdle() {
        assertFalse(ClientCastState.casting());
        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F);
    }

    @Test
    @DisplayName("beginning a cast starts the clock at zero")
    void beginStartsAtZero() {
        ClientCastState.begin(160);
        assertTrue(ClientCastState.casting());
        assertEquals(160, ClientCastState.durationTicks());
        assertEquals(0, ClientCastState.elapsedTicks());
    }

    @Test
    @DisplayName("ticks advance the clock only while casting")
    void ticksAdvanceOnlyWhileCasting() {
        ClientCastState.tick();
        ClientCastState.tick();
        ClientCastState.begin(160);
        ClientCastState.tick();
        assertEquals(1, ClientCastState.elapsedTicks(),
                "ticks before the cast began must not have counted");
    }

    @Test
    @DisplayName("an interruption freezes the fill where the clock stood")
    void interruptionFreezesTheFill() {
        ClientCastState.begin(160);
        for (int i = 0; i < 80; i++) {
            ClientCastState.tick();
        }
        ClientCastState.end(true);

        assertFalse(ClientCastState.casting());
        assertEquals(0.5F, ClientCastState.interruptedFill(), 0.0001F,
                "half the ticks were served, so the bar freezes at half");
        assertEquals(1.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F,
                "the fade starts fully opaque at the moment of interruption");
    }

    @Test
    @DisplayName("a completion removes the bar with no fade")
    void completionDoesNotFade() {
        ClientCastState.begin(160);
        for (int i = 0; i < 160; i++) {
            ClientCastState.tick();
        }
        ClientCastState.end(false);

        assertFalse(ClientCastState.casting());
        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F,
                "an uninterrupted end must draw no fade frame at all");
    }

    @Test
    @DisplayName("the fade runs its length and then stops consuming ticks")
    void fadeRunsOutAndSaturates() {
        ClientCastState.begin(160);
        ClientCastState.tick();
        ClientCastState.end(true);

        for (int i = 0; i < CastBar.FADE_TICKS; i++) {
            assertTrue(CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()) > 0.0F,
                    "still fading at tick " + i);
            ClientCastState.tick();
        }
        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F);

        // Long after, the counter must not have marched on into overflow territory.
        for (int i = 0; i < 1000; i++) {
            ClientCastState.tick();
        }
        assertEquals(CastBar.FADE_TICKS, ClientCastState.ticksSinceInterrupt(),
                "the counter saturates at the fade length rather than growing forever");
    }

    @Test
    @DisplayName("a new cast cancels a fade still in flight")
    void beginCancelsAnInFlightFade() {
        ClientCastState.begin(160);
        ClientCastState.tick();
        ClientCastState.end(true);
        ClientCastState.begin(160);

        assertTrue(ClientCastState.casting());
        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F,
                "the red remnant of the old cast must not draw under the new bar");
    }

    @Test
    @DisplayName("an interrupted end with no cast believed running starts no fade")
    void strayInterruptedEndDrawsNothing() {
        ClientCastState.end(true);
        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F,
                "there is no honest fill to freeze, so nothing may be drawn");
    }

    @Test
    @DisplayName("clearing mid-fade removes the fade too")
    void clearRemovesAFadeInFlight() {
        ClientCastState.begin(160);
        ClientCastState.tick();
        ClientCastState.end(true);
        ClientCastState.clear();

        assertEquals(0.0F, CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt()), 0.0001F,
                "a fade must not play its first frames over the next world");
    }
}
