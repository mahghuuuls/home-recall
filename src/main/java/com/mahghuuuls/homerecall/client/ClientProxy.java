package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.CommonProxy;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Start-up on a client.
 *
 * <p>Everything this mod draws, binds, or plays is reached from here and from nowhere else, so no
 * client type is loaded on a server. Empty for now; the keybinding, the HUD, the effects, and the
 * Inventory Button Bar registration all arrive here.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
}
