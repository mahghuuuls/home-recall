package com.mahghuuuls.homerecall.net;

import com.mahghuuuls.homerecall.Tags;
import com.mahghuuuls.homerecall.diagnostics.Diagnostics;
import com.mahghuuuls.homerecall.equipment.PlayerRecallEquipment;
import com.mahghuuuls.homerecall.equipment.RecallEquipment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
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
    private static final int ID_EQUIPMENT_SYNC = 2;
    private static final int ID_OPEN_EQUIPMENT = 3;

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
        channel.registerMessage(EquipmentSyncMessage.Handler.class, EquipmentSyncMessage.class,
                ID_EQUIPMENT_SYNC, Side.CLIENT);
        channel.registerMessage(OpenEquipmentMessage.Handler.class, OpenEquipmentMessage.class,
                ID_OPEN_EQUIPMENT, Side.SERVER);
    }

    /** Asks the server to open the equipment screen for this client. */
    public static void sendOpenEquipment() {
        channel().sendToServer(new OpenEquipmentMessage());
    }

    /**
     * Shows one player their own equipment slot. Owner-only: nobody else draws this slot, so
     * nobody else receives it.
     */
    public static void sendEquipmentSync(EntityPlayerMP player) {
        PlayerRecallEquipment equipment = RecallEquipment.of(player);
        ItemStack stone = equipment == null ? ItemStack.EMPTY : equipment.stone();
        channel().sendTo(new EquipmentSyncMessage(stone), player);
    }

    /** Asks the server to start a recall. Client side; the server decides whether it may. */
    public static void sendRecallRequest() {
        channel().sendToServer(new RecallRequestMessage());
    }

    /**
     * Tells the caster and everyone tracking them that a cast started or ended.
     *
     * <p>Two records per full cast — a start and one end, whichever kind — and nothing per tick:
     * receivers extrapolate progress from the duration (ARC-009). The tracking set is vanilla's
     * own — whoever is close enough to see the caster's entity is close enough to see their
     * cast, and someone out of tracking range receives nothing. Someone who walks into range
     * mid-cast is caught up by {@link #sendLateCastStart}.
     *
     * <p>The recipient count is read from the tracker <em>before</em> the sends, which is the
     * same set {@code sendToAllTracking} resolves a line later. On a cross-dimension completion
     * that set is the destination's: the transfer has already moved the player, so the
     * completion reaches whoever tracks them there, while the origin's observers see the entity
     * leave tracking and their effects stop that way. The count describes the actual send.
     */
    public static void sendCastSync(EntityPlayerMP player, boolean casting, int durationTicks,
                                    boolean interrupted) {
        int observers = ((WorldServer) player.world).getEntityTracker()
                .getTrackingPlayers(player).size();
        CastSyncMessage message =
                new CastSyncMessage(player.getEntityId(), casting, durationTicks, 0, interrupted);
        channel().sendTo(message, player);
        channel().sendToAllTracking(message, player);
        Diagnostics.castSyncSent(player.getName(),
                casting ? "start" : (interrupted ? "cancel" : "complete"), 1 + observers);
    }

    /**
     * Catches up one observer who walked into tracking range while this cast was already
     * running. Without this, a player approaching mid-cast would see the caster standing in
     * silence and then vanishing — the silent escape REQ-042 exists to prevent. Carries the
     * elapsed ticks so the newcomer's circle shows the cast's real progress.
     */
    public static void sendLateCastStart(EntityPlayerMP observer, EntityPlayerMP caster,
                                         int durationTicks, int elapsedTicks) {
        channel().sendTo(new CastSyncMessage(caster.getEntityId(), true, durationTicks,
                elapsedTicks, false), observer);
        Diagnostics.castSyncSent(caster.getName(), "late-start", 1);
    }

    /**
     * Corrects one just-joined player's own belief: no cast is held for them. Sent to that
     * player alone — there is nothing to tell anyone else — and recorded under its own phase so
     * a session log's completion count is not polluted by logins.
     */
    public static void sendCastResync(EntityPlayerMP player) {
        channel().sendTo(new CastSyncMessage(player.getEntityId(), false, 0, 0, false), player);
        Diagnostics.castSyncSent(player.getName(), "resync", 1);
    }

    private static SimpleNetworkWrapper channel() {
        if (channel == null) {
            throw new IllegalStateException(
                    "Home Recall network channel was used before register() ran.");
        }
        return channel;
    }
}
