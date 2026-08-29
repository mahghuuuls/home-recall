package com.mahghuuuls.homerecall;

import com.mahghuuuls.homerecall.net.CastSyncMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Start-up on a dedicated server, and the shared half of start-up on a client.
 *
 * <p>{@link com.mahghuuuls.homerecall.client.ClientProxy} extends this rather than implementing a
 * shared interface. With one implementation per side and the client half inheriting the common
 * half, an interface would be a third type with no second implementer and no boundary of its own.
 *
 * <p>The two methods below are the whole of the client's belief as far as common code is
 * concerned. They exist so that {@code guard} and {@code net} can ask about, and update, something
 * only a client has, without naming a client type. The answers here are the correct ones for a
 * server: it never predicts anything, and it has no cast belief to hold.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Deliberately empty. See the class javadoc.
    }

    /**
     * Whether this client believes the given player is casting.
     *
     * <p>Always false here. A dedicated server has no client belief, and server-side code that
     * wants the truth asks {@code RecallService} instead.
     */
    public boolean isLocalPlayerCasting(EntityPlayer player) {
        return false;
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
