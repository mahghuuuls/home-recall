package com.mahghuuuls.homerecall.client.render;

import com.mahghuuuls.homerecall.HomeRecallMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Third person: the held-item layer, with one substitution. When {@link CastHeldItem} answers
 * with a stone for the player being rendered, the main hand draws that stone and the real item is
 * not drawn; otherwise this behaves exactly as the vanilla layer it replaces.
 *
 * <p>It replaces {@code LayerHeldItem} in the player renderers rather than being added beside it,
 * because a second layer could only draw a stone <i>on top of</i> the real item — two things in
 * one hand. Replacement is the only way to make the real item yield, and the non-casting path
 * reproduces the vanilla body line for line so the swap is invisible outside a cast.
 *
 * <p>The off hand renders its real stack either way, matching the first-person rule.
 */
@SideOnly(Side.CLIENT)
public final class CastThirdPersonLayer extends LayerHeldItem {

    /**
     * The layer this one stands in for when it is not vanilla's own: another mod's held-item
     * layer (a subclass, or a wrapper such as Everfilling Flasks'), kept and delegated to
     * outside a cast so that mod's rendering survives. Null when the replaced layer was
     * vanilla's, whose behaviour {@code super} is.
     */
    private final LayerRenderer<EntityLivingBase> replaced;

    public CastThirdPersonLayer(RenderLivingBase<?> renderer) {
        this(renderer, null);
    }

    private CastThirdPersonLayer(RenderLivingBase<?> renderer,
                                 LayerRenderer<EntityLivingBase> replaced) {
        super(renderer);
        this.replaced = replaced;
    }

    /**
     * Swaps this layer in for the vanilla {@code LayerHeldItem} on both player renderers, the
     * default-armed and the slim-armed. Called once, at client init — after the render manager
     * exists, before any world renders.
     *
     * <p>The layer list is reached by reflection because vanilla offers add-only access. The
     * field is named by its SRG name, {@code field_177097_h} ({@code layerRenderers}); Forge
     * remaps it to the workspace name in development, so one name serves both jars. A
     * renderer with no vanilla layer to replace — another mod got there first — is left alone
     * and reported, because silently rendering nothing in third person while first person works
     * is the exact split-brain a log line is for. Always logged, not diagnostics-gated: it is an
     * install-time anomaly, not gameplay telemetry.
     */
    public static void installOn(RenderManager renderManager) {
        for (java.util.Map.Entry<String, RenderPlayer> entry
                : renderManager.getSkinMap().entrySet()) {
            RenderPlayer renderer = entry.getValue();
            try {
                List<LayerRenderer<?>> layers = ObfuscationReflectionHelper.getPrivateValue(
                        RenderLivingBase.class, renderer, "field_177097_h");
                if (!takeOver(layers, renderer)) {
                    HomeRecallMod.LOGGER.warn(
                            "The '" + entry.getKey() + "' player renderer has no held-item layer "
                                    + "this mod recognises, so casts will show no held stone in "
                                    + "third person for players drawn by it. Layers present: "
                                    + layerNames(layers));
                }
            } catch (Throwable failure) {
                // Throwable, around the whole body: the reflection, the scan over classes 360
                // other mods contributed, and the swap. A renderer some coremod reshaped should
                // cost the stone-in-hand, never the game — the same rule the no-layer case
                // above follows, and the same width Everfilling Flasks' installer uses.
                HomeRecallMod.LOGGER.warn(
                        "Could not take over the '" + entry.getKey() + "' player renderer's "
                                + "held-item layer; casts will show no held stone in third "
                                + "person for players drawn by it.", failure);
            }
        }
    }

    /**
     * The list rewrite alone, separated from the render manager so a test can hand in a list:
     * vanilla's layer replaced outright, else another mod's held-item layer wrapped. True when
     * the list now contains this layer.
     */
    static boolean takeOver(List<LayerRenderer<?>> layers, RenderPlayer renderer) {
        return replaceVanillaLayer(layers, renderer) || wrapHeldItemLayer(layers, renderer);
    }

    /** The layer this one delegates to outside a cast, or null when it stands in for vanilla's. */
    LayerRenderer<EntityLivingBase> replacedLayer() {
        return replaced;
    }

    /** Vanilla's own layer, replaced outright: the exact class, never a subclass. */
    private static boolean replaceVanillaLayer(List<LayerRenderer<?>> layers,
                                               RenderPlayer renderer) {
        boolean replaced = false;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).getClass() == LayerHeldItem.class) {
                layers.set(i, new CastThirdPersonLayer(renderer));
                replaced = true;
            }
        }
        return replaced;
    }

    /**
     * Another mod's held-item layer, wrapped rather than replaced: that mod's layer keeps drawing
     * whenever no cast is showing, and yields the main hand to the stone when one is. The first
     * such layer only — a second would mean two mods already disagree about the hand.
     *
     * <p>Found in the target pack: Everfilling Flasks wraps the vanilla layer in its own
     * {@code DrinkAwareHeldItemLayer} — not a subclass, so {@code instanceof} misses it — to hide
     * the held item while a player drinks. Wrapping that wrapper keeps the Flask logic running
     * outside a cast. During one, the delegate is skipped and the stone drawn: the delegate's
     * only job was hiding the real item, which the stone branch does anyway, and there is no
     * generic way to ask a foreign layer whether it would have drawn nothing.
     *
     * <p>A drink CAN overlap a cast — Flasks drinks on its own key, not through a vanilla item
     * use, so the channel does not break (the HUD in {@code CastBarRenderer} is laid out for
     * exactly that overlap). In that window a watcher sees the stone and the Flask together in
     * one hand, because Flasks' separate drink layer still draws. Accepted, recorded here rather
     * than denied: the overlap is brief, the two are the owner's own mods, and the alternative
     * — asking a foreign delegate what it would draw — does not exist. (Review, 2026-09-03.)
     */
    @SuppressWarnings("unchecked")
    private static boolean wrapHeldItemLayer(List<LayerRenderer<?>> layers,
                                             RenderPlayer renderer) {
        for (int i = 0; i < layers.size(); i++) {
            if (looksLikeHeldItemLayer(layers.get(i).getClass())) {
                layers.set(i, new CastThirdPersonLayer(renderer,
                        (LayerRenderer<EntityLivingBase>) layers.get(i)));
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a layer class draws the held item, as far as can be told without knowing the mod:
     * vanilla's class and its subclasses, or any class that names itself a held-item layer —
     * the convention vanilla set and other mods' wrappers follow. A naming heuristic, and
     * recorded as one; the class-identity checks run first, and the warning above names every
     * layer present when this misses too. This mod's own layer is never a match: a re-scan
     * (none exists today, but a resource-reload rebuild of the renderers is a plausible future)
     * must skip it rather than wrap it in another of itself.
     */
    static boolean looksLikeHeldItemLayer(Class<?> layerClass) {
        if (layerClass == CastThirdPersonLayer.class) {
            return false;
        }
        return LayerHeldItem.class.isAssignableFrom(layerClass)
                || layerClass.getSimpleName().contains("HeldItem");
    }

    /** The class names present, for the warning: the one fact that identifies the owner. */
    private static String layerNames(List<LayerRenderer<?>> layers) {
        StringBuilder names = new StringBuilder();
        for (LayerRenderer<?> layer : layers) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(layer.getClass().getName());
        }
        return names.toString();
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw,
                              float headPitch, float scale) {
        ItemStack stone = entity instanceof EntityPlayer
                ? CastHeldItem.stackFor((EntityPlayer) entity) : ItemStack.EMPTY;
        if (stone.isEmpty()) {
            if (replaced != null) {
                replaced.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, scale);
            } else {
                super.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, scale);
            }
            return;
        }
        // Vanilla's body with the main-hand stack replaced: the stone goes to the primary side,
        // the off hand keeps its real stack, and the child-model scaling is preserved.
        boolean rightHanded = entity.getPrimaryHand() == EnumHandSide.RIGHT;
        ItemStack inRightHand = rightHanded ? stone : entity.getHeldItemOffhand();
        ItemStack inLeftHand = rightHanded ? entity.getHeldItemOffhand() : stone;
        GlStateManager.pushMatrix();
        if (this.livingEntityRenderer.getMainModel().isChild) {
            GlStateManager.translate(0.0F, 0.75F, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
        }
        renderInHand(entity, inRightHand,
                ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, EnumHandSide.RIGHT);
        renderInHand(entity, inLeftHand,
                ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, EnumHandSide.LEFT);
        GlStateManager.popMatrix();
    }

    /**
     * One hand, exactly as vanilla's private helper draws it: the same sneak offset, the same
     * hand-relative rotations and translation, the same renderer call.
     */
    private void renderInHand(EntityLivingBase entity, ItemStack stack,
                              ItemCameraTransforms.TransformType transform, EnumHandSide side) {
        if (stack.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        if (entity.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        this.translateToHand(side);
        GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        boolean leftHand = side == EnumHandSide.LEFT;
        GlStateManager.translate((float) (leftHand ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        Minecraft.getMinecraft().getItemRenderer().renderItemSide(entity, stack, transform,
                leftHand);
        GlStateManager.popMatrix();
    }
}
