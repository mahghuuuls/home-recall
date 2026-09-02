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

    public CastThirdPersonLayer(RenderLivingBase<?> renderer) {
        super(renderer);
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
            List<LayerRenderer<?>> layers;
            try {
                layers = ObfuscationReflectionHelper.getPrivateValue(
                        RenderLivingBase.class, renderer, "field_177097_h");
            } catch (RuntimeException failure) {
                // The same degradation as the no-layer case below, and for the same reason: a
                // coremod that reshaped the renderer should cost the stone-in-hand, not the game.
                HomeRecallMod.LOGGER.warn(
                        "Could not reach the '" + entry.getKey() + "' player renderer's layer "
                                + "list; casts will show no held stone in third person for "
                                + "players drawn by it.", failure);
                continue;
            }
            boolean replaced = false;
            for (int i = 0; i < layers.size(); i++) {
                if (layers.get(i).getClass() == LayerHeldItem.class) {
                    layers.set(i, new CastThirdPersonLayer(renderer));
                    replaced = true;
                }
            }
            if (!replaced) {
                HomeRecallMod.LOGGER.warn(
                        "The '" + entry.getKey() + "' player renderer has no vanilla held-item "
                                + "layer to replace; another mod may own it now. Casts will show "
                                + "no held stone in third person for players drawn by it.");
            }
        }
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw,
                              float headPitch, float scale) {
        ItemStack stone = entity instanceof EntityPlayer
                ? CastHeldItem.stackFor((EntityPlayer) entity) : ItemStack.EMPTY;
        if (stone.isEmpty()) {
            super.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks,
                    netHeadYaw, headPitch, scale);
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
