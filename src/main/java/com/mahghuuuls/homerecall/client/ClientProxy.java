package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.CommonProxy;
import com.mahghuuuls.homerecall.net.CastSyncMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
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
    }

    /**
     * Whether the client believes the local player is casting.
     *
     * <p>Answers only for the local player. Another player's cast is not synchronized yet, and a
     * client that guessed would be worse than one that says no: the guard would refuse actions the
     * server is going to allow, which is a bug the server can never correct.
     */
    @Override
    public boolean isLocalPlayerCasting(EntityPlayer player) {
        return player == Minecraft.getMinecraft().player && ClientCastState.casting();
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
                ClientCastState.set(message.casting(), message.durationTicks());
            }
        });
    }
}
