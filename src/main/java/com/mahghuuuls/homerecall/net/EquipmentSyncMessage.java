package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.HomeRecallMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * The player's own equipment slot, server to client.
 *
 * <p>Sent to the owning player alone, at the boundaries where the client's copy resets — login,
 * respawn, dimension change — and whenever the slot changes. The client uses it to draw the slot
 * in the equipment GUI; it decides nothing, because the server's copy is the only one that is
 * ever consulted for a rule.
 */
public final class EquipmentSyncMessage implements IMessage {

    private ItemStack stone = ItemStack.EMPTY;

    /** Required by the network layer. */
    public EquipmentSyncMessage() {
    }

    public EquipmentSyncMessage(ItemStack stone) {
        this.stone = stone;
    }

    /** The slot's contents. {@link ItemStack#EMPTY} for an empty slot. */
    public ItemStack stone() {
        return stone;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stone = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, stone);
    }

    /** Hands the message to the proxy, exactly as the cast sync does and for the same reason. */
    public static final class Handler implements IMessageHandler<EquipmentSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(EquipmentSyncMessage message, MessageContext ctx) {
            HomeRecallMod.proxy.handleEquipmentSync(message);
            return null;
        }
    }
}
