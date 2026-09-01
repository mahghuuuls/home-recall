package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.container.HomeRecallGuiHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * The client asking the server to open the equipment screen.
 *
 * <p>Empty on purpose, like the recall request: which screen, and for whom, are both decided by
 * the server from who sent it. Opening goes through the server because the container is the
 * server's — a client that opened a screen by itself would be editing slot contents the server
 * never agreed to show it.
 */
public final class OpenEquipmentMessage implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    /** Opens the screen on the server thread, for the sender alone. */
    public static final class Handler implements IMessageHandler<OpenEquipmentMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenEquipmentMessage message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    player.openGui(HomeRecallMod.INSTANCE, HomeRecallGuiHandler.EQUIPMENT_GUI,
                            player.world, 0, 0, 0);
                }
            });
            return null;
        }
    }
}
