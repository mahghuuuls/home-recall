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
        maybeGrant(event.player);
        resync(event.player);
    }

    /**
     * The new-player grant: one stone, once per player per world, ever — and only when the pack
     * author asked for it. Runs before the login resync so a granted stone is in the very first
     * picture the client receives.
     *
     * <p>The flag is set in the same operation that grants, so the two cannot diverge; it
     * persists on the capability, which already survives death, relog, dimension change, and
     * restart. Placement never loses the stone: the empty slot first, then the inventory, then
     * dropped at the player's feet.
     */
    private static void maybeGrant(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        PlayerRecallEquipment equipment = RecallEquipment.of(player);
        if (equipment == null) {
            // Recorded like every other skip: a silent path here would be indistinguishable
            // from the handler never running.
            com.mahghuuuls.homerecall.diagnostics.Diagnostics.stoneGrantSkipped(
                    player.getName(), "no equipment attached");
            return;
        }
        com.mahghuuuls.homerecall.config.ConfigSnapshot config =
                com.mahghuuuls.homerecall.config.ConfigSnapshot.current();
        String skip = grantSkipReason(config.giveRecallStoneToNewPlayers(),
                config.registerRecallStone(), equipment.granted());
        if (skip != null) {
            com.mahghuuuls.homerecall.diagnostics.Diagnostics.stoneGrantSkipped(
                    player.getName(), skip);
            return;
        }
        // Flag first: even if placement threw, a player must never be granted twice — a lost
        // grant is a /recallequip away, a duplicated one is an exploit.
        equipment.markGranted();
        net.minecraft.item.ItemStack stone =
                new net.minecraft.item.ItemStack(com.mahghuuuls.homerecall.item.ModItems.RECALL_STONE);
        String placement;
        if (equipment.stone().isEmpty() && equipment.setStone(stone)) {
            // Emptiness checked HERE, not by setStone: setStone stores any acceptable stack,
            // replacing what is there, and the grant overwriting an admin-placed or pre-grant
            // stone would destroy it — the exact loss REQ-044 forbids. (Review round 1.)
            placement = "the equipment slot";
        } else if (player.inventory.addItemStackToInventory(stone)) {
            placement = "their inventory";
        } else if (player.dropItem(stone, true, false) != null) {
            // dropAround=true is the at-their-feet drop; the plain toss throws it forward.
            placement = "the ground at their feet";
        } else {
            // Unreachable today: the feet-drop overload fires no cancellable toss event, and the
            // stack cannot be empty here. Kept as the honest answer if either fact ever changes,
            // because the record must never claim a drop that did not happen.
            placement = "nowhere: the drop returned nothing";
        }
        com.mahghuuuls.homerecall.diagnostics.Diagnostics.stoneGranted(
                player.getName(), placement);
    }

    /**
     * Why a login does not grant, or null when it should. Pulled out so the four-way decision is
     * testable without a login: the grant firing twice, or firing with the stone system off, is
     * invisible until a player reports it.
     */
    static String grantSkipReason(boolean optionOn, boolean stoneSystemOn, boolean alreadyGranted) {
        if (!optionOn) {
            return "the option is off";
        }
        if (!stoneSystemOn) {
            return "the stone system is off";
        }
        if (alreadyGranted) {
            return "already granted";
        }
        return null;
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
