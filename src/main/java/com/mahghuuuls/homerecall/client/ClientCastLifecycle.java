package com.mahghuuuls.homerecall.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Clears the client's cast belief when it stops having a world to hold one about.
 *
 * <p>{@link ClientCastState} is static, and a client outlives every server it connects to. A
 * player who quits to the menu mid-cast never receives the end message, because the server that
 * would have sent it is already gone. Without this the belief survives into the next world they
 * join, where the guard would refuse every action until they happened to start and finish another
 * recall. The server would allow all of it, so nothing in any log would show why.
 *
 * <p>This is the client-side twin of the server discarding its casts when it stops, and it exists
 * for the same reason: static state that outlives a session has to be told when the session ended.
 */
@SideOnly(Side.CLIENT)
public final class ClientCastLifecycle {

    private ClientCastLifecycle() {
    }

    /**
     * Clears the belief whenever there is no world.
     *
     * <p>Checked per client tick rather than on a disconnect event, because the ways a client can
     * lose its world are several and quiet: quitting to the menu, a kick, a timeout, and a crash
     * on the integrated server. All of them end with a null world, so that is what is watched.
     * Costs one null check per tick and does nothing at all once the belief is already clear.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().world == null
                && ClientCastState.casting()) {
            ClientCastState.clear();
        }
    }
}
