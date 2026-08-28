package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.recall.RecallService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * "I pressed the recall key."
 *
 * <p>Carries nothing. Every fact the decision needs is already on the server, and a payload would
 * only be something a modified client could lie about.
 */
public class RecallRequestMessage implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<RecallRequestMessage, IMessage> {

        @Override
        public IMessage onMessage(RecallRequestMessage message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            // Messages arrive on the network thread. Touching player or world state from there is
            // a race; scheduling onto the server thread is what makes the decision safe.
            player.server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // The returned reason is deliberately ignored. RecallService already told the
                    // player, and it is the only place that knows whether the same message was
                    // shown recently. Sending again here would be a second owner for one decision,
                    // and it would bypass the rate limit on every single request.
                    RecallService.requestRecall(player);
                }
            });
            return null;
        }
    }
}
