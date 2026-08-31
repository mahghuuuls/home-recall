package com.mahghuuuls.homerecall.client.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the bar's arithmetic against plain numbers, which is the reason it has no Minecraft
 * import: a bar that fills at the wrong rate looks merely "a bit off" in play and is exact here.
 */
class CastBarTest {

    @Test
    @DisplayName("an untouched cast is empty")
    void fillAtZero() {
        assertEquals(0.0F, CastBar.fill(0, 0.0F, 160), 0.0001F);
    }

    @Test
    @DisplayName("half the ticks is half the bar")
    void fillAtHalf() {
        assertEquals(0.5F, CastBar.fill(80, 0.0F, 160), 0.0001F);
    }

    @Test
    @DisplayName("the partial tick moves the bar between whole ticks")
    void partialTickSmooths() {
        float atTick = CastBar.fill(80, 0.0F, 160);
        float between = CastBar.fill(80, 0.5F, 160);
        assertEquals(atTick + 0.5F / 160.0F, between, 0.0001F,
                "half a tick should advance the fill by half a tick's worth");
    }

    @Test
    @DisplayName("the bar clamps at full rather than overshooting")
    void fillClampsAtFull() {
        assertEquals(1.0F, CastBar.fill(160, 0.9F, 160), 0.0001F);
        assertEquals(1.0F, CastBar.fill(500, 0.0F, 160), 0.0001F);
    }

    @Test
    @DisplayName("a zero or negative duration answers empty instead of dividing by it")
    void degenerateDurationIsEmpty() {
        assertEquals(0.0F, CastBar.fill(10, 0.5F, 0), 0.0001F);
        assertEquals(0.0F, CastBar.fill(10, 0.5F, -5), 0.0001F);
    }

    @Test
    @DisplayName("the fade starts fully opaque")
    void fadeStartsOpaque() {
        assertEquals(1.0F, CastBar.fadeAlpha(0), 0.0001F);
    }

    @Test
    @DisplayName("the fade falls linearly")
    void fadeFallsLinearly() {
        assertEquals(0.5F, CastBar.fadeAlpha(CastBar.FADE_TICKS / 2), 0.0001F);
    }

    @Test
    @DisplayName("the fade is gone at its own length and stays gone")
    void fadeEndsAndStaysEnded() {
        assertEquals(0.0F, CastBar.fadeAlpha(CastBar.FADE_TICKS), 0.0001F);
        assertEquals(0.0F, CastBar.fadeAlpha(CastBar.FADE_TICKS + 100), 0.0001F);
    }

    @Test
    @DisplayName("a nonsense negative time draws nothing rather than something negative")
    void negativeFadeTimeDrawsNothing() {
        assertEquals(0.0F, CastBar.fadeAlpha(-1), 0.0001F);
    }
}
