package com.mahghuuuls.homerecall.item;

import com.mahghuuuls.homerecall.Tags;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * The Recall Stone: the object that grants and explains the recall ability.
 *
 * <p>Deliberately inert. It has no use behavior, no durability, and no right-click — equipping it
 * in the Recall Stone Slot is its entire function, and that function lives in the equipment
 * system, not here. An item that also did something on use would blur "this unlocks the ability"
 * into "this is the ability", which the concept keeps apart. (REQ-022)
 *
 * <p>Stack size one: a player equips a stone, not a supply of them, and a slot that could hold
 * sixty-four would imply consumption that does not exist.
 */
public final class ItemRecallStone extends Item {

    public ItemRecallStone() {
        setRegistryName(Tags.MOD_ID, "recall_stone");
        setTranslationKey(Tags.MOD_ID + ".recall_stone");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }
}
