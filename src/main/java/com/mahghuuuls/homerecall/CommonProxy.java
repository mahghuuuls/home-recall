package com.mahghuuuls.homerecall;

import com.mahghuuuls.homerecall.net.CastSyncMessage;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Start-up on a dedicated server, and the shared half of start-up on a client.
 *
 * <p>{@link com.mahghuuuls.homerecall.client.ClientProxy} extends this rather than implementing a
 * shared interface. With one implementation per side and the client half inheriting the common
 * half, an interface would be a third type with no second implementer and no boundary of its own.
 *
 * <p>The method below is the whole of the client's belief as far as common code is concerned.
 * It exists so that {@code net} can update something only a client has, without naming a client
 * type. Ignoring it is the correct answer for a server, which has no cast belief to hold.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Deliberately empty. See the class javadoc.
    }

    /**
     * Records a cast transition the server sent.
     *
     * <p>Ignored here. A server receiving its own client-bound message has nothing to do with it,
     * and silently dropping it is better than trusting it.
     */
    public void handleCastSync(CastSyncMessage message) {
        // Deliberately empty. See the method javadoc.
    }
}
