package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Start-up on a client.
 *
 * <p>Everything this mod draws, binds, or plays is reached from here and from nowhere else, so no
 * client type is loaded on a server.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        HomeRecallKeys.register();
        MinecraftForge.EVENT_BUS.register(KeyHandler.class);
    }
}
