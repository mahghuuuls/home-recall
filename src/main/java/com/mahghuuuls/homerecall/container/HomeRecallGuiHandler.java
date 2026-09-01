package com.mahghuuuls.homerecall.container;

import com.mahghuuuls.homerecall.HomeRecallMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * Builds the two halves of the equipment screen. One GUI id, because the mod has one screen.
 *
 * <p>The client element is reached through the proxy rather than named here, so this class stays
 * loadable on a dedicated server.
 */
public final class HomeRecallGuiHandler implements IGuiHandler {

    /** The equipment screen's id, the only one this mod uses. */
    public static final int EQUIPMENT_GUI = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world,
                                      int x, int y, int z) {
        return id == EQUIPMENT_GUI ? new ContainerRecallEquipment(player) : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world,
                                      int x, int y, int z) {
        return id == EQUIPMENT_GUI ? HomeRecallMod.proxy.createEquipmentGui(player) : null;
    }
}
