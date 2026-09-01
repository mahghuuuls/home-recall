package com.mahghuuuls.homerecall.equipment;

import com.mahghuuuls.homerecall.item.ItemRecallStone;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the NBT persistence contract and the accept rule.
 *
 * <p>These need real {@code ItemStack}s, so vanilla's {@link Bootstrap} runs once. The stone
 * itself is constructed directly rather than through {@code ModItems}, whose registry-name call
 * needs a live Forge loader; the item's class is what the accept rule reads, and the class needs
 * no registry to exist.
 */
class PlayerRecallEquipmentTest {

    private static Item recallStone;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        // The real class, constructible here precisely because its registry name is assigned at
        // registration rather than in the constructor. It stays unregistered — Forge locks the
        // registry outside game start — which the accept-rule and copy tests do not mind, and
        // the round-trip test works around by carrying a registered vanilla item instead.
        recallStone = new ItemRecallStone();
    }

    @Test
    @DisplayName("an occupied slot survives the NBT round trip, grant flag included")
    void roundTripWithStack() {
        // Serialization stores the item by its registered name, and no unit test can register
        // our item (Forge locks the registry outside a launched game). A registered vanilla item
        // through the deserialize door exercises the identical shape: deserialize trusts disk,
        // exactly as it must when a world written by an older version comes back.
        NBTTagCompound written = new NBTTagCompound();
        written.setTag("Stone", new ItemStack(Items.DIAMOND).writeToNBT(new NBTTagCompound()));
        written.setBoolean("Granted", true);

        PlayerRecallEquipment restored = new PlayerRecallEquipment();
        restored.deserializeNBT(written);
        assertFalse(restored.stone().isEmpty(), "the stack did not survive");
        assertEquals(Items.DIAMOND, restored.stone().getItem());
        assertEquals(1, restored.stone().getCount());
        assertTrue(restored.granted(), "the grant flag did not survive");

        // And back out: what it writes must be what it was given, byte for byte.
        assertEquals(written, restored.serializeNBT(), "the round trip altered the shape");
    }

    @Test
    @DisplayName("an empty slot round-trips as empty, with the grant unused")
    void roundTripEmpty() {
        PlayerRecallEquipment restored = new PlayerRecallEquipment();
        restored.deserializeNBT(new PlayerRecallEquipment().serializeNBT());

        assertTrue(restored.stone().isEmpty());
        assertFalse(restored.granted());
    }

    @Test
    @DisplayName("the slot refuses everything that is not a Recall Stone")
    void acceptRule() {
        PlayerRecallEquipment equipment = new PlayerRecallEquipment();

        assertFalse(equipment.setStone(new ItemStack(Items.DIAMOND)),
                "a diamond is not equipment");
        assertTrue(equipment.stone().isEmpty(), "the refused stack must not be stored");

        assertTrue(equipment.setStone(new ItemStack(recallStone)));
        assertFalse(equipment.stone().isEmpty());

        assertTrue(equipment.setStone(ItemStack.EMPTY), "emptying is always allowed");
        assertTrue(equipment.stone().isEmpty());
    }

    @Test
    @DisplayName("the stored stone is a copy, so the caller's stack cannot mutate the slot")
    void storedStoneIsACopy() {
        PlayerRecallEquipment equipment = new PlayerRecallEquipment();
        ItemStack mine = new ItemStack(recallStone);
        equipment.setStone(mine);

        mine.setCount(0);
        assertFalse(equipment.stone().isEmpty(),
                "hollowing out the caller's stack emptied the slot too");
    }
}
