package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.Tags;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/**
 * The mod's one network channel, and the single place message ids are assigned.
 *
 * <p>No message is registered yet. The channel exists so later work has one place to add to rather
 * than each feature creating its own.
 *
 * <p>When messages arrive, give each one a named constant for its id rather than a running
 * counter. The id is part of the wire format: reordering registration calls would silently change
 * what an older client's packet means.
 */
public final class HomeRecallNetwork {

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
    }

    /** The channel. Null before {@link #register()}, which is a programming error rather than a state to handle. */
    static SimpleNetworkWrapper channel() {
        if (channel == null) {
            throw new IllegalStateException(
                    "Home Recall network channel was used before register() ran.");
        }
        return channel;
    }
}
