package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.Tags;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mod's one network channel, and the single place message ids are assigned.
 *
 * <p>Ids are named constants rather than a running counter. The id is part of the wire format:
 * reordering the registration calls would silently change what an older client's packet means.
 */
public final class HomeRecallNetwork {

    private static final int ID_RECALL_REQUEST = 0;
    private static final int ID_CAST_SYNC = 1;

    private static SimpleNetworkWrapper channel;

    private HomeRecallNetwork() {
    }

    /**
     * Creates the channel and registers every message. Called once from preInit.
     *
     * <p>The channel is created here rather than in a static initializer so that calling this is
     * what brings it into existence. Built the other way, this method looks empty and deletable
     * while actually being load-bearing.
     */
    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);
        channel.registerMessage(RecallRequestMessage.Handler.class, RecallRequestMessage.class,
                ID_RECALL_REQUEST, Side.SERVER);
        channel.registerMessage(CastSyncMessage.Handler.class, CastSyncMessage.class,
                ID_CAST_SYNC, Side.CLIENT);
    }

    /** Asks the server to start a recall. Client side; the server decides whether it may. */
    public static void sendRecallRequest() {
        channel().sendToServer(new RecallRequestMessage());
    }

    /**
     * Tells one player that their cast started or ended.
     *
     * <p>Sent to the caster alone. Nearby players are a later slice, and sending to more people
     * than need it now would be a wire format to unpick later rather than extend.
     */
    public static void sendCastSync(EntityPlayerMP player, boolean casting, int durationTicks) {
        channel().sendTo(new CastSyncMessage(casting, durationTicks), player);
    }

    private static SimpleNetworkWrapper channel() {
        if (channel == null) {
            throw new IllegalStateException(
                    "Home Recall network channel was used before register() ran.");
        }
        return channel;
    }
}
