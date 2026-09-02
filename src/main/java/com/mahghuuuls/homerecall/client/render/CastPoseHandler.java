package com.mahghuuuls.homerecall.client.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Third person: while a player shows the stone, their main arm holds it up instead of hanging.
 *
 * <p>Vanilla derives the arm pose from the real held item, so a casting player with an empty hand
 * would stand arm-down with a stone floating beside their leg. This handler re-poses the main arm
 * to the vanilla item-holding pose after vanilla's derivation and before the model renders —
 * {@code RenderLivingEvent.Pre} is the one hook in that window, because {@code RenderPlayerEvent.Pre}
 * fires before {@code setModelVisibilities} overwrites whatever it set.
 *
 * <p>No restore is needed: the pose lives on the shared player model, and vanilla recomputes it
 * for every non-spectator player it renders before this event fires, so a forced pose cannot
 * leak anywhere visible. (Spectators skip the recomputation, but their layers are skipped too.)
 * The pose question is not asked here — {@link CastHeldItem} owns it; this class only translates
 * the same answer into an arm.
 */
@SideOnly(Side.CLIENT)
public final class CastPoseHandler {

    private CastPoseHandler() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<EntityLivingBase> event) {
        EntityLivingBase entity = event.getEntity();
        // Through Object: the event's renderer is typed on EntityLivingBase and RenderPlayer is
        // parameterized on players, so the direct cast is not just unchecked but rejected.
        Object renderer = event.getRenderer();
        if (!(entity instanceof AbstractClientPlayer) || !(renderer instanceof RenderPlayer)) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (CastHeldItem.stackFor(player).isEmpty()) {
            return;
        }
        ModelBiped model = ((RenderPlayer) renderer).getMainModel();
        if (player.getPrimaryHand() == EnumHandSide.RIGHT) {
            model.rightArmPose = ModelBiped.ArmPose.ITEM;
        } else {
            model.leftArmPose = ModelBiped.ArmPose.ITEM;
        }
    }
}
