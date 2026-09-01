package com.mahghuuuls.homerecall.client;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * What this client believes its own equipment slot holds.
 *
 * <p>A mirror of the server's last sync and nothing more: the GUI draws it, no rule reads it.
 * Like the cast belief, it is static and outlives any one server, so it is cleared with the
 * world and re-established by the login resync.
 */
@SideOnly(Side.CLIENT)
public final class ClientEquipmentState {

    private static ItemStack stone = ItemStack.EMPTY;

    private ClientEquipmentState() {
    }

    public static void set(ItemStack stack) {
        stone = stack;
    }

    /** The mirrored slot contents. Draw it; never decide from it. */
    public static ItemStack stone() {
        return stone;
    }

    public static void clear() {
        stone = ItemStack.EMPTY;
    }
}
