package com.mahghuuuls.homerecall.container;

import com.mahghuuuls.homerecall.equipment.PlayerRecallEquipment;
import com.mahghuuuls.homerecall.equipment.RecallEquipment;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The Home Recall equipment screen's server half: one stone slot above the player inventory.
 *
 * <p>Exactly one Home Recall slot, per REQ-031 — this is deliberately not a generic accessory
 * inventory. The slot's backing store is the player's equipment capability itself, adapted
 * through a one-slot inventory whose writes go straight to the capability, so there is no second
 * copy of the stone to fall out of step with the one persistence owns.
 */
public class ContainerRecallEquipment extends Container {

    /** Index of the stone slot; the player inventory follows. */
    private static final int STONE_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 37;

    private final EntityPlayer player;

    public ContainerRecallEquipment(EntityPlayer player) {
        this.player = player;
        addSlotToContainer(new SlotRecallStone(new EquipmentInventory(player), 0, 80, 21));

        InventoryPlayer inventory = player.inventory;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inventory,
                        9 + row * 9 + col, 8 + col * 18, 50 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inventory, col, 8 + col * 18, 108));
        }
    }

    // Deliberately no onContainerClosed override. Container.clearContainer would empty the slot
    // through removeStackFromSlot — one of the two InventoryBasic paths that never call
    // markDirty — silently orphaning the capability. If a close hook is ever needed, it must
    // not clear.

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // The screen shows the player's own equipment, which is wherever they are.
        return playerIn == player;
    }

    /** Standard shift-click routing: slot to inventory, or a valid stone into the slot. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack moving = slot.getStack();
        ItemStack before = moving.copy();

        if (index == STONE_SLOT) {
            if (!mergeItemStack(moving, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            Slot stoneSlot = inventorySlots.get(STONE_SLOT);
            if (!stoneSlot.getHasStack() && stoneSlot.isItemValid(moving)) {
                // mergeItemStack respects no per-slot limit, so the move is done by hand:
                // one stone in, the rest stays where it was.
                stoneSlot.putStack(moving.splitStack(1));
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (moving.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return before;
    }

    /**
     * The one-slot adapter between the container world and the capability. Writes go to the
     * capability immediately, and on the server every write also refreshes the owner's client
     * mirror, so the slot has exactly one source of truth with two views.
     */
    private static final class EquipmentInventory extends InventoryBasic {

        private final EntityPlayer player;
        private final boolean loaded;

        EquipmentInventory(EntityPlayer player) {
            super("homerecall.equipment", false, 1);
            this.player = player;
            PlayerRecallEquipment equipment = RecallEquipment.of(player);
            if (equipment != null) {
                // Runs before `loaded` flips, so the markDirty this triggers skips the
                // write-back and the network send: loading the current truth is not a change.
                setInventorySlotContents(0, equipment.stone().copy());
            }
            loaded = true;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (!loaded) {
                return;
            }
            PlayerRecallEquipment equipment = RecallEquipment.of(player);
            if (equipment != null) {
                if (!equipment.setStone(getStackInSlot(0))) {
                    // The slot's own accept rule makes this unreachable; if it ever fires, the
                    // capability kept the old stone while the screen shows something else, and
                    // that is worth a loud line rather than a silent divergence.
                    com.mahghuuuls.homerecall.HomeRecallMod.LOGGER.warn(
                            "the equipment slot accepted a stack the capability refused for {}",
                            player.getName());
                }
                if (player instanceof EntityPlayerMP) {
                    HomeRecallNetwork.sendEquipmentSync((EntityPlayerMP) player);
                }
            }
        }
    }
}
