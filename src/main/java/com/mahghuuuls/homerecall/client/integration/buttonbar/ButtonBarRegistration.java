package com.mahghuuuls.homerecall.client.integration.buttonbar;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.Tags;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import com.mahghuuuls.inventorybuttonbar.api.ButtonSpec;
import com.mahghuuuls.inventorybuttonbar.api.InventoryButtonBar;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The one class in this mod that names an Inventory Button Bar type. (ARC-005)
 *
 * <p><b>Named inner classes only — no lambdas, no method references.</b> This is a load-time
 * safety rule, not style: javac compiles a lambda to an {@code invokedynamic} whose bootstrap can
 * hoist the functional interface's mention into the enclosing class's constant pool, which would
 * let a dedicated server trip over a client-side type without this file changing how it reads.
 * A named inner class keeps every cross-mod mention inside this {@code @SideOnly} boundary.
 *
 * <p>Registration failure is logged and swallowed. Losing the button costs a convenience — the
 * screen still opens with {@code /recallequip open} — and must never stop the game.
 */
@SideOnly(Side.CLIENT)
public final class ButtonBarRegistration {

    private ButtonBarRegistration() {
    }

    /** Called once from {@link com.mahghuuuls.homerecall.client.ClientProxy}. */
    public static void register() {
        try {
            InventoryButtonBar.register(ButtonSpec.builder(Tags.MOD_ID + ":equipment")
                    .icon(new ResourceLocation(Tags.MOD_ID, "textures/items/recall_stone.png"))
                    .tooltip(new EquipmentTooltip())
                    .onClick(new OpenEquipmentScreen())
                    .visibleWhen(new ButtonVisibility())
                    .build());
        } catch (Throwable failure) {
            // Throwable on purpose: a missing class surfaces as NoClassDefFoundError, which a
            // catch (Exception) would let kill the game over a button.
            HomeRecallMod.LOGGER.error(
                    "Home Recall could not add its Inventory Button Bar button; the equipment "
                            + "screen is still available through /recallequip open", failure);
        }
    }

    /** The button names what it opens. */
    private static final class EquipmentTooltip implements Supplier<List<String>> {
        @Override
        public List<String> get() {
            return Collections.singletonList(I18n.format("homerecall.gui.equipment.button"));
        }
    }

    /** Clicking asks the server to open the screen; the server decides, as with everything. */
    private static final class OpenEquipmentScreen implements Runnable {
        @Override
        public void run() {
            HomeRecallNetwork.sendOpenEquipment();
        }
    }

    /**
     * Visible only while both switches say so: the player's own {@code showInventoryButton},
     * and {@code registerRecallStone} — a hidden stone system should not advertise its screen.
     * Visibility is the whole mechanism; the API has no unregister and needs none.
     */
    private static final class ButtonVisibility implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            ConfigSnapshot config = ConfigSnapshot.current();
            return config.showInventoryButton() && config.registerRecallStone();
        }
    }
}
