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
 * <p>The client needs this for two things it cannot know on its own: its own cast bar, and other
 * players' casts. The caster's entity id names whose cast this is, which is what lets one message
 * type serve both — a receiver whose own id matches draws the bar; any other receiver renders the
 * caster's effects. The end transition's interrupted flag tells a fading cancellation from an
 * instant removal.
 *
 * <p>Carries the duration as well as the fact, so progress can be extrapolated locally without a
 * second message. The client is told nothing it could use to cheat: the server decides everything
 * either way.
 */
public final class CastSyncMessage implements IMessage {

    private int casterId;
    private boolean casting;
    private int durationTicks;
    private int elapsedTicks;
    private boolean interrupted;

    /** Required by the network layer. */
    public CastSyncMessage() {
    }

    public CastSyncMessage(int casterId, boolean casting, int durationTicks, int elapsedTicks,
                           boolean interrupted) {
        this.casterId = casterId;
        this.casting = casting;
        this.durationTicks = durationTicks;
        this.elapsedTicks = elapsedTicks;
        this.interrupted = interrupted;
    }

    /**
     * Ticks already elapsed when this start was sent. Zero for an ordinary start; nonzero only
     * for the late start sent to an observer who walked into tracking range mid-cast, whose
     * circle must show the cast's real progress rather than beginning again from empty.
     */
    public int elapsedTicks() {
        return elapsedTicks;
    }

    /**
     * The entity id of the player whose cast this is. Entity ids are per-session and per-world,
     * which is fine: the message is meaningful only to clients currently tracking that entity,
     * and both sides agree on the id for exactly as long as that is true.
     */
    public int casterId() {
        return casterId;
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
        casterId = buf.readInt();
        casting = buf.readBoolean();
        durationTicks = buf.readInt();
        elapsedTicks = buf.readInt();
        interrupted = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(casterId);
        buf.writeBoolean(casting);
        buf.writeInt(durationTicks);
        buf.writeInt(elapsedTicks);
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
