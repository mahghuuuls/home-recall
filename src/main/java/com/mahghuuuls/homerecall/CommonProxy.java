package com.mahghuuuls.homerecall;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Start-up on a dedicated server, and the shared half of start-up on a client.
 *
 * <p>Empty for now. Configuration, the network channel, and everything else that runs the same on
 * both sides is registered by the {@code @Mod} class itself; routing side-independent work through
 * a proxy would only add a layer that forwards.
 *
 * <p>{@link com.mahghuuuls.homerecall.client.ClientProxy} extends this rather than implementing a
 * shared interface. With one implementation per side and the client half inheriting the common
 * half, an interface would be a third type with no second implementer and no boundary of its own.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Deliberately empty. See the class javadoc.
    }
}
