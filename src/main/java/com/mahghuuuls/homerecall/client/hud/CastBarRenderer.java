package com.mahghuuuls.homerecall.client.hud;

import com.mahghuuuls.homerecall.client.ClientCastState;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The recall cast bar: boss-bar wide, centred, filling left to right while the cast runs. An
 * interruption freezes the bar at its last fill, turns it red, and fades it out over
 * {@link CastBar#FADE_TICKS} ticks; a completion, a death, or a logout simply removes it. Idle
 * draws nothing, which is what makes a refused recall visibly different from a slow one.
 *
 * <p>The geometry deliberately matches the owner's Everfilling Flasks bar, with two deliberate
 * differences, both because a player can drink a flask during a recall and the two bars must be
 * tellable apart when both are up:
 *
 * <ul>
 * <li><b>Teal fill rather than amber.</b> Which ability is casting is answerable at a glance.
 * The interrupted colour stays the same red in both mods, so "red means interrupted" reads as one
 * language across the family.</li>
 * <li><b>Nine points below the sixty-percent line rather than on it.</b> The flask bar's top sits
 * at sixty percent of the scaled height and it is five tall; nine points down leaves a four-point
 * gap between the two.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class CastBarRenderer {

    /** Vanilla boss bar width in points, so the bar lines up with the HUD's own elements. */
    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;

    /** How far below the sixty-percent line this bar sits, clearing the flask bar's five points. */
    private static final int OFFSET_BELOW = 9;

    private static final int FILL = 0x3CC8E8;
    private static final int FILL_INTERRUPTED = 0xD03030;
    private static final int BACKGROUND = 0x101010;

    private CastBarRenderer() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (!ConfigSnapshot.current().enableCastHud()) {
            return;
        }
        ScaledResolution resolution = event.getResolution();
        int left = resolution.getScaledWidth() / 2 - WIDTH / 2;
        int top = Math.round(resolution.getScaledHeight() * 0.6F) + OFFSET_BELOW;

        if (ClientCastState.casting() && ClientCastState.durationTicks() > 0) {
            float fill = CastBar.fill(ClientCastState.elapsedTicks(), event.getPartialTicks(),
                    ClientCastState.durationTicks());
            drawBar(left, top, fill, FILL, 1.0F);
        } else {
            float alpha = CastBar.fadeAlpha(ClientCastState.ticksSinceInterrupt());
            if (alpha > 0.0F) {
                drawBar(left, top, ClientCastState.interruptedFill(), FILL_INTERRUPTED, alpha);
            }
        }
    }

    private static void drawBar(int left, int top, float fill, int fillColor, float alpha) {
        int backgroundAlpha = (int) (alpha * 0xA0) << 24;
        int fillAlpha = (int) (alpha * 0xFF) << 24;
        Gui.drawRect(left, top, left + WIDTH, top + HEIGHT, backgroundAlpha | BACKGROUND);
        int filledWidth = Math.round(WIDTH * Math.min(1.0F, fill));
        if (filledWidth > 0) {
            Gui.drawRect(left, top, left + filledWidth, top + HEIGHT, fillAlpha | fillColor);
        }
        // drawRect leaves its last colour in the GL state; a following overlay would be tinted.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
