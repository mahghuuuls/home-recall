package com.mahghuuuls.homerecall.client.render;

import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the installer's list rewrite, written against a real collision — Everfilling Flasks'
 * wrapper in the target pack. The shape of that wrapper (a plain {@code LayerRenderer} whose
 * name says "HeldItem") must keep being wrapped WITH its delegate retained, vanilla's own layer
 * must be replaced outright, an armor layer must never match, and this mod's own layer must be
 * skipped on any re-scan rather than wrapped in another of itself. (Review S1/S2.)
 */
class CastThirdPersonLayerTest {

    /** The same shape as Everfilling Flasks' wrapper: not a subclass, named for the hand. */
    private static final class DrinkAwareHeldItemLayer implements LayerRenderer<EntityLivingBase> {
        @Override
        public void doRenderLayer(EntityLivingBase entity, float a, float b, float c, float d,
                                  float e, float f, float g) {
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    /** A wrapper that hides what it is: the installer must refuse to guess. */
    private static final class QuietWrapper implements LayerRenderer<EntityLivingBase> {
        @Override
        public void doRenderLayer(EntityLivingBase entity, float a, float b, float c, float d,
                                  float e, float f, float g) {
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    @Test
    @DisplayName("vanilla's layer and any subclass of it are held-item layers")
    void vanillaAndSubclasses() {
        assertTrue(CastThirdPersonLayer.looksLikeHeldItemLayer(LayerHeldItem.class));
    }

    @Test
    @DisplayName("a wrapper that names itself a held-item layer is one")
    void namedWrappers() {
        assertTrue(CastThirdPersonLayer.looksLikeHeldItemLayer(DrinkAwareHeldItemLayer.class));
    }

    @Test
    @DisplayName("everything else is left alone, this mod's own layer included")
    void othersAreNot() {
        assertFalse(CastThirdPersonLayer.looksLikeHeldItemLayer(LayerBipedArmor.class),
                "wrapping the armor layer would hide the armor during every cast");
        assertFalse(CastThirdPersonLayer.looksLikeHeldItemLayer(QuietWrapper.class),
                "an unnamed wrapper is a warning, not a guess");
        assertFalse(CastThirdPersonLayer.looksLikeHeldItemLayer(CastThirdPersonLayer.class),
                "a re-scan must skip this mod's own layer, not nest it");
    }

    @Test
    @DisplayName("vanilla's layer is replaced outright, standing in with no delegate")
    void vanillaIsReplaced() {
        LayerHeldItem vanilla = new LayerHeldItem(null);
        List<LayerRenderer<?>> layers = new ArrayList<LayerRenderer<?>>();
        layers.add(new LayerBipedArmor(null));
        layers.add(vanilla);

        assertTrue(CastThirdPersonLayer.takeOver(layers, null));
        assertEquals(2, layers.size(), "the list must keep its shape");
        assertTrue(layers.get(0) instanceof LayerBipedArmor, "the armor layer is untouched");
        assertTrue(layers.get(1) instanceof CastThirdPersonLayer);
        assertNull(((CastThirdPersonLayer) layers.get(1)).replacedLayer(),
                "vanilla's behaviour is super, not a delegate");
    }

    @Test
    @DisplayName("another mod's held-item wrapper is wrapped, and keeps its delegate")
    void foreignWrapperIsWrappedWithDelegateKept() {
        DrinkAwareHeldItemLayer flasks = new DrinkAwareHeldItemLayer();
        List<LayerRenderer<?>> layers = new ArrayList<LayerRenderer<?>>();
        layers.add(new LayerBipedArmor(null));
        layers.add(flasks);

        assertTrue(CastThirdPersonLayer.takeOver(layers, null));
        assertTrue(layers.get(1) instanceof CastThirdPersonLayer);
        assertSame(flasks, ((CastThirdPersonLayer) layers.get(1)).replacedLayer(),
                "losing the delegate silently loses the other mod's rendering outside a cast");
    }

    @Test
    @DisplayName("with nothing recognisable the list is left exactly as found")
    void unknownListIsUntouched() {
        List<LayerRenderer<?>> layers = new ArrayList<LayerRenderer<?>>();
        layers.add(new LayerBipedArmor(null));
        layers.add(new QuietWrapper());

        assertFalse(CastThirdPersonLayer.takeOver(layers, null));
        assertTrue(layers.get(0) instanceof LayerBipedArmor);
        assertTrue(layers.get(1) instanceof QuietWrapper);
    }

    @Test
    @DisplayName("a second take-over does not nest this layer inside itself")
    void secondPassIsANoOp() {
        List<LayerRenderer<?>> layers = new ArrayList<LayerRenderer<?>>();
        layers.add(new LayerHeldItem(null));
        assertTrue(CastThirdPersonLayer.takeOver(layers, null));
        assertFalse(CastThirdPersonLayer.takeOver(layers, null),
                "the installed layer must not be recognised as something to wrap");
        assertNull(((CastThirdPersonLayer) layers.get(0)).replacedLayer());
    }
}
