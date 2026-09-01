package com.mahghuuuls.homerecall.equipment;

import com.mahghuuuls.homerecall.item.ItemRecallStone;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * One player's Home Recall equipment: the stone slot and the new-player grant flag.
 *
 * <p>This class owns the NBT shape and nothing else does. That shape is a persistence contract
 * the moment the mod ships — a stone written by one version must be read by the next — so every
 * field name here is as fixed as a wire format, and changing one later means a migration, not an
 * edit. (ARC-002)
 *
 * <p>Deliberately not holding: cast state, which must survive nothing while this must survive
 * everything (ARC-002 keeps the two lifetimes in separate homes); and any policy about what the
 * slot's contents mean, which belongs to the recall rules that read it.
 */
public final class PlayerRecallEquipment implements INBTSerializable<NBTTagCompound> {

    /** NBT key for the stone stack. A missing key is an empty slot; nothing writes an empty. */
    private static final String NBT_STONE = "Stone";

    /** NBT key for the one-per-world new-player grant flag (set by the grant, read by it too). */
    private static final String NBT_GRANTED = "Granted";

    private ItemStack stone = ItemStack.EMPTY;
    private boolean granted;

    /**
     * Whether this stack may sit in the slot: only a Recall Stone, per REQ-028. Version 1.0 has
     * exactly one valid equipment item; a second one changes this rule and nothing else.
     */
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemRecallStone;
    }

    /** The equipped stone, or {@link ItemStack#EMPTY}. Callers must not mutate the returned stack. */
    public ItemStack stone() {
        return stone;
    }

    /**
     * Puts this stack in the slot — REPLACING whatever is there — or empties it. The accept rule
     * is enforced here as well as at the slot, because a path that bypasses the GUI must not be
     * able to store a diamond where only a stone belongs. Emptiness is deliberately not checked:
     * a caller that must not overwrite (the grant) checks it first, and one that means to
     * overwrite (the operator command) should not have to fight this method to do it.
     *
     * @return true when the stack was acceptable and stored, or the slot was emptied
     */
    public boolean setStone(ItemStack stack) {
        if (!stack.isEmpty() && !accepts(stack)) {
            return false;
        }
        stone = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        return true;
    }

    /**
     * Applies the death policy: the slot keeps its stone when the normal inventory is surviving
     * this death, or when {@code keepRecallStoneOnDeath} says so; otherwise the slot is emptied
     * and the stone handed back to join the death drops exactly once.
     *
     * <p>The decision lives here, on the class that owns the slot; the lifecycle handler that
     * calls this only reports the two inputs and places whatever comes back. The
     * inventory-survives input is genuinely an input, not structural: {@code EntityPlayerMP}'s
     * own override fires the drops event only when dropping, but the base {@code EntityPlayer}
     * fires it unconditionally, and a policy that ignored the input would strip the stone from a
     * player whose whole inventory was being kept. (REQ-030, review round 1)
     */
    public ItemStack applyDeathPolicy(boolean inventorySurvives, boolean keepOnDeath) {
        if (inventorySurvives || keepOnDeath || stone.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack dropped = stone;
        stone = ItemStack.EMPTY;
        return dropped;
    }

    /** Whether this player has already received the one-time new-player grant. */
    public boolean granted() {
        return granted;
    }

    /** Marks the grant as given. Never unset: the grant is once per player per world, ever. */
    public void markGranted() {
        granted = true;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (!stone.isEmpty()) {
            tag.setTag(NBT_STONE, stone.writeToNBT(new NBTTagCompound()));
        }
        tag.setBoolean(NBT_GRANTED, granted);
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound tag) {
        stone = tag.hasKey(NBT_STONE) ? new ItemStack(tag.getCompoundTag(NBT_STONE))
                : ItemStack.EMPTY;
        granted = tag.getBoolean(NBT_GRANTED);
    }
}
