package com.mahghuuuls.homerecall.item;

import com.mahghuuuls.homerecall.Tags;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The mod's items, registered unconditionally on every start.
 *
 * <p>Unconditional is the point, not an oversight (ARC-001): configuration never touches
 * registration. A registered item removed from a save makes singleplayer offer to strip it —
 * destroying every stone players hold — and makes a dedicated server refuse the world outright.
 * {@code registerRecallStone=false} therefore hides and disables the stone elsewhere; it never
 * unregisters it.
 */
public final class ModItems {

    public static final ItemRecallStone RECALL_STONE = new ItemRecallStone();

    private ModItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Named here rather than in the constructor, so the item class stays constructible in a
        // unit test with no Forge loader behind it.
        event.getRegistry().register(RECALL_STONE.setRegistryName(Tags.MOD_ID, "recall_stone"));
    }
}
