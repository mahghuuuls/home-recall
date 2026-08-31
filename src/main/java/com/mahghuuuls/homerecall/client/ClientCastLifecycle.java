package com.mahghuuuls.homerecall.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Owns when the client's cast clock is allowed to run, and clears the cast belief when the client
 * stops having a world to hold one about.
 *
 * <p>The clock rule is one line and it is the whole reason this class exists as an owner rather
 * than a chore: <b>the clock advances only when the authority's does.</b> The client tick event
 * fires even while the game is paused, but a paused single-player world skips the server tick
 * entirely, so the cast does not advance. A clock gated only on "is there a world" fills the bar
 * through the pause menu, and the player comes back to a bar claiming the recall is done while the
 * real cast has barely started. On a dedicated server the client is never game-paused, so the gate
 * costs nothing there.
 *
 * <p>{@link ClientCastState} is static, and a client outlives every server it connects to. A
 * player who quits to the menu mid-cast never receives the end message, because the server that
 * would have sent it is already gone. Without the clear the belief survives into the next world
 * they join, where the guard would refuse every action until they happened to start and finish
 * another recall. The server would allow all of it, so nothing in any log would show why.
 */
@SideOnly(Side.CLIENT)
public final class ClientCastLifecycle {

    private ClientCastLifecycle() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            // Fade included: a bar left mid-fade would draw its first frames over the next world.
            ClientCastState.clear();
            return;
        }
        if (minecraft.isGamePaused()) {
            // The paused integrated server skips its tick, so the cast is not advancing. Neither
            // may the bar.
            return;
        }
        ClientCastState.tick();
    }
}
