package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.HomeRecallMod;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * A cast starting or ending, server to client.
 *
 * <p>Sent at those two transitions and nowhere else, never per tick. An eight-second cast puts two
 * packets on the wire regardless of its length.
 *
 * <p>The client needs this for one reason: without it, it does not know a cast is running, so it
 * predicts every action the server is about to refuse. The player then watches a block appear and
 * vanish, an eating animation that never completes, and a stack count that drops and comes back.
 * Nothing is actually wrong in any of those cases, which is precisely what makes them look like
 * bugs.
 *
 * <p>Carries the duration as well as the fact, so the cast bar can be drawn later without a second
 * message. The client is told nothing it could use to cheat: it already knows it pressed the key,
 * and the server decides everything either way.
 */
public final class CastSyncMessage implements IMessage {

    private boolean casting;
    private int durationTicks;

    /** Required by the network layer. */
    public CastSyncMessage() {
    }

    public CastSyncMessage(boolean casting, int durationTicks) {
        this.casting = casting;
        this.durationTicks = durationTicks;
    }

    /** Whether a cast is now running. False means one has just ended, by any route. */
    public boolean casting() {
        return casting;
    }

    /** How long the cast runs in total. Zero on an end. */
    public int durationTicks() {
        return durationTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        casting = buf.readBoolean();
        durationTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(casting);
        buf.writeInt(durationTicks);
    }

    /**
     * Hands the message to the proxy.
     *
     * <p>Through the proxy rather than straight into the client state, so this class stays loadable
     * on a dedicated server. That is the whole reason. The message is registered client-bound, so
     * no handler is installed on a server channel and the common implementation is unreachable
     * there.
     */
    public static final class Handler implements IMessageHandler<CastSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(CastSyncMessage message, MessageContext ctx) {
            HomeRecallMod.proxy.handleCastSync(message);
            return null;
        }
    }
}
