package com.mahghuuuls.homerecall.equipment;

import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * The wiring that keeps the equipment attached, copied, and shown — and none of the policy.
 *
 * <p>What is deliberately absent matters as much as what is here (Feasibility FQ4):
 * <ul>
 * <li><b>No dimension-change copy.</b> Vanilla moves the same entity between dimensions, so the
 * capability rides along. A handler copying it would be wrong, not redundant — two writes racing
 * one truth. Only the client resync below is a dimension-change concern.</li>
 * <li><b>No {@code isWasDeath} branch in the clone.</b> The death policy (IMP-008) runs before
 * the clone; branching here too would put the same decision in two places.</li>
 * </ul>
 */
public final class EquipmentLifecycle {

    private EquipmentLifecycle() {
    }

    /** Every player gets the equipment, on both sides, from the moment the entity exists. */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(RecallEquipment.KEY, new RecallEquipment.Provider());
        }
    }

    /**
     * Carries the equipment onto the new entity vanilla builds at respawn and End-exit.
     *
     * <p>Unconditional on purpose: whatever the death policy decided has already been applied to
     * the old entity's equipment by the time this runs, so copying "what is there now" is the
     * whole job. (Feasibility FQ4)
     */
    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        PlayerRecallEquipment from = RecallEquipment.of(event.getOriginal());
        PlayerRecallEquipment to = RecallEquipment.of(event.getEntityPlayer());
        if (from != null && to != null) {
            to.deserializeNBT(from.serializeNBT());
        }
    }

    /** The client's copy is refreshed at every boundary where its world knowledge resets. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        resync(event.player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resync(event.player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resync(event.player);
    }

    private static void resync(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            HomeRecallNetwork.sendEquipmentSync((EntityPlayerMP) player);
        }
    }
}
