package com.mahghuuuls.homerecall.container;

import com.mahghuuuls.homerecall.equipment.PlayerRecallEquipment;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The one slot that holds a Recall Stone.
 *
 * <p>The accept rule is {@link PlayerRecallEquipment#accepts} — the same rule the capability
 * enforces — asked here so the GUI refuses a diamond at the moment of the drag, shift-click
 * included, rather than silently discarding it later. One rule, two gates, one owner.
 */
public final class SlotRecallStone extends Slot {

    public SlotRecallStone(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return PlayerRecallEquipment.accepts(stack);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
