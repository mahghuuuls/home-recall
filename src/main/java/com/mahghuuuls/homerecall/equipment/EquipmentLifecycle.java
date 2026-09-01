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

    /**
     * Contributes the stone to the death drops when the policy says it falls.
     *
     * <p>{@code PlayerDropsEvent}, never {@code LivingDeathEvent}: the death event fires before
     * vanilla enables drop capture, so anything spawned there is a loose entity grave and corpse
     * mods never see. And {@code HIGHEST}, because this handler adds to the list those mods
     * consume later — at normal priority the two would race on registration order.
     * {@code EntityPlayerMP}'s own override fires this event only when the inventory is genuinely
     * dropping, but the base {@code EntityPlayer} fires it unconditionally, so whether the
     * inventory is surviving is read here and handed to the policy rather than assumed. If
     * another mod cancels the event, everything in it is destroyed together — the inventory was
     * emptied into the same list first, so the stone's fate stays exactly the inventory's.
     * (Feasibility FQ4; REQ-030)
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public static void onPlayerDrops(net.minecraftforge.event.entity.player.PlayerDropsEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        PlayerRecallEquipment equipment = RecallEquipment.of(player);
        if (equipment == null) {
            return;
        }
        boolean inventorySurvives = player.world.getGameRules().getBoolean("keepInventory")
                || player.isSpectator();
        boolean keepOnDeath =
                com.mahghuuuls.homerecall.config.ConfigSnapshot.current().keepRecallStoneOnDeath();
        net.minecraft.item.ItemStack falling =
                equipment.applyDeathPolicy(inventorySurvives, keepOnDeath);
        if (!falling.isEmpty()) {
            // Placed the way vanilla places the rest of the corpse: the same drop height and the
            // same 40-tick pickup delay, so the stone is not lootable ahead of the pile.
            net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                    player.world, player.posX,
                    player.posY - 0.30000001192092896D + player.getEyeHeight(),
                    player.posZ, falling);
            drop.setPickupDelay(40);
            event.getDrops().add(drop);
        }
        com.mahghuuuls.homerecall.diagnostics.Diagnostics.stoneDeathPolicy(
                player.getName(), inventorySurvives, keepOnDeath, !falling.isEmpty());
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
