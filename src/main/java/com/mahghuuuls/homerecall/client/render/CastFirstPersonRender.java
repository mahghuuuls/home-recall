package com.mahghuuuls.homerecall.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * First person: while this client's own cast runs, the main hand draws a Recall Stone instead of
 * whatever is really held.
 *
 * <p>The vanilla render for the hand is cancelled and re-issued through the same public
 * {@code renderItemInFirstPerson} overload the cancelled call would have used, with every timing
 * value taken from the event and only the stack substituted. That keeps the arm position, swing,
 * and equip animation exactly vanilla's, and it is why the real item is back the instant the cast
 * ends: nothing was changed anywhere, so the next frame simply renders reality again. (REQ-050)
 *
 * <p>The off hand is left alone — the stone is a main-hand statement, and hiding the off hand
 * would be display lying about a slot the cast says nothing about.
 */
@SideOnly(Side.CLIENT)
public final class CastFirstPersonRender {

    private CastFirstPersonRender() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderSpecificHandEvent event) {
        if (event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack stone = CastHeldItem.stackFor(player);
        if (stone.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        minecraft.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                event.getInterpolatedPitch(), EnumHand.MAIN_HAND, event.getSwingProgress(),
                stone, event.getEquipProgress());
    }
}
