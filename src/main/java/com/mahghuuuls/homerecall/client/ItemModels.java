package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.item.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Binds each item to its model. Registered by {@link ClientProxy} alone, which is what keeps a
 * dedicated server from ever loading a model class it does not have.
 */
@SideOnly(Side.CLIENT)
public final class ItemModels {

    private ItemModels() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.RECALL_STONE, 0,
                new ModelResourceLocation(ModItems.RECALL_STONE.getRegistryName(), "inventory"));
    }
}
