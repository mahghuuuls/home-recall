package com.mahghuuuls.homerecall.client.gui;

import com.mahghuuuls.homerecall.Tags;
import com.mahghuuuls.homerecall.container.ContainerRecallEquipment;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The equipment screen's client half: the background, the title, and the slot label.
 *
 * <p>The label is not decoration. An unlabelled lone slot reads as "put anything here", and this
 * one accepts exactly one item in the game — the name above it is what tells a player that
 * before their first refused drag.
 */
@SideOnly(Side.CLIENT)
public final class GuiRecallEquipment extends GuiContainer {

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(Tags.MOD_ID, "textures/gui/equipment.png");

    public GuiRecallEquipment(EntityPlayer player) {
        super(new ContainerRecallEquipment(player));
        xSize = 176;
        ySize = 132;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(BACKGROUND);
        // The custom-size draw, not drawTexturedModalRect: the plain one hardcodes a 256x256
        // texture, and against this 176x132 file it would sample the top-left corner stretched
        // 1.45x — every slot inset visibly off its real slot. (Review round 1.)
        drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0, 0, xSize, ySize, 176F, 132F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // One label, above the slot. The owner cut the separate "Home Recall" title during
        // validation: the slot's name says everything the screen holds.
        String label = I18n.format("homerecall.gui.equipment.slot");
        fontRenderer.drawString(label,
                (xSize - fontRenderer.getStringWidth(label)) / 2, 8, 0x404040);
    }
}
