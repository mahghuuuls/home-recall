package com.mahghuuuls.homerecall.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * The mod's keybindings, and the one place their display name is read from.
 *
 * <p>The binding is exposed so the Recall Stone tooltip can name whichever key the player has
 * actually chosen. Reading it live is the requirement; a hardcoded "R" would be wrong the moment
 * anyone rebinds, and would stay wrong with no way for the player to tell why.
 */
@SideOnly(Side.CLIENT)
public final class HomeRecallKeys {

    /** Its own category, so the binding is not buried among vanilla's. */
    private static final String CATEGORY = "key.categories.homerecall";

    private static KeyBinding recall;

    private HomeRecallKeys() {
    }

    public static void register() {
        recall = new KeyBinding("key.homerecall.recall", Keyboard.KEY_R, CATEGORY);
        ClientRegistry.registerKeyBinding(recall);
    }

    /** The recall binding. Null before {@link #register()} runs. */
    public static KeyBinding recall() {
        return recall;
    }
}
