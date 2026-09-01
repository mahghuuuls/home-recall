package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.CommonProxy;
import com.mahghuuuls.homerecall.client.fx.CastEffects;
import com.mahghuuuls.homerecall.client.hud.CastBarRenderer;
import com.mahghuuuls.homerecall.net.CastSyncMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Start-up on a client.
 *
 * <p>Everything this mod draws, binds, or plays is reached from here and from nowhere else, so no
 * client type is loaded on a server.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        HomeRecallKeys.register();
        MinecraftForge.EVENT_BUS.register(KeyHandler.class);
        MinecraftForge.EVENT_BUS.register(ClientCastLifecycle.class);
        MinecraftForge.EVENT_BUS.register(CastBarRenderer.class);
        MinecraftForge.EVENT_BUS.register(CastEffects.class);
        MinecraftForge.EVENT_BUS.register(ItemModels.class);
    }

    /**
     * Applies a cast transition.
     *
     * <p>Hopped onto the client thread. The network handler runs on a netty thread, and touching
     * client state from there is a race whose symptoms appear somewhere else entirely.
     */
    @Override
    public void handleCastSync(final CastSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft.player == null) {
                    // A sync racing a disconnect: nobody to draw for, nothing to hold it against.
                    // The window is not reachable in ordinary play, and dropping the message is
                    // the only honest answer — a belief with no player attached is exactly the
                    // stale state the world-null clear exists to prevent.
                    return;
                }
                if (message.casterId() == minecraft.player.getEntityId()) {
                    if (message.casting()) {
                        ClientCastState.begin(message.durationTicks());
                    } else {
                        ClientCastState.end(message.interrupted());
                    }
                } else if (message.casting()) {
                    ClientCastState.beginObserved(message.casterId(), message.durationTicks(),
                            message.elapsedTicks());
                } else {
                    ClientCastState.endObserved(message.casterId());
                }
            }
        });
    }
}
