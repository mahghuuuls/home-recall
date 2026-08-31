package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.HomeRecallMod;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * A cast starting or ending, server to client.
 *
 * <p>Sent at those two transitions and nowhere else, never per tick. A six-second cast puts two
 * packets on the wire regardless of its length.
 *
 * <p>The client needs this for one reason: the cast bar. Without it the client does not know a
 * cast is running, so it has nothing to draw, no length to fill toward, and no way to tell a
 * completion from a cancellation when the bar comes down — which is what the end transition's
 * interrupted flag carries.
 *
 * <p>Carries the duration as well as the fact, so the cast bar can be drawn later without a second
 * message. The client is told nothing it could use to cheat: it already knows it pressed the key,
 * and the server decides everything either way.
 */
public final class CastSyncMessage implements IMessage {

    private boolean casting;
    private int durationTicks;
    private boolean interrupted;

    /** Required by the network layer. */
    public CastSyncMessage() {
    }

    public CastSyncMessage(boolean casting, int durationTicks, boolean interrupted) {
        this.casting = casting;
        this.durationTicks = durationTicks;
        this.interrupted = interrupted;
    }

    /** Whether a cast is now running. False means one has just ended, by any route. */
    public boolean casting() {
        return casting;
    }

    /** How long the cast runs in total. Zero on an end. */
    public int durationTicks() {
        return durationTicks;
    }

    /**
     * Whether an ended cast was interrupted rather than reaching its end.
     *
     * <p>Meaningful only when {@link #casting()} is false. The bar draws the difference: an
     * interruption freezes it and fades it out, while a completion, a death, or a logout removes
     * it at once. Carried on the wire because the client cannot tell those apart on its own; all
     * it would see is "the cast is over".
     */
    public boolean interrupted() {
        return interrupted;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        casting = buf.readBoolean();
        durationTicks = buf.readInt();
        interrupted = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(casting);
        buf.writeInt(durationTicks);
        buf.writeBoolean(interrupted);
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
