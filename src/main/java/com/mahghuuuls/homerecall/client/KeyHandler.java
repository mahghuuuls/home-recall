package com.mahghuuuls.homerecall.client;

import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Turns a key press into a request and does nothing else.
 *
 * <p>No eligibility check happens here, deliberately. The client does not know whether the player
 * may recall and must not guess: a check here would either duplicate the server's rules, and drift
 * from them, or become something a modified client could remove. Sending the request and letting
 * the server answer is both simpler and the only version that cannot be bypassed.
 */
@SideOnly(Side.CLIENT)
public final class KeyHandler {

    private KeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (HomeRecallKeys.recall() == null || Minecraft.getMinecraft().player == null) {
            return;
        }
        // One request per tick. isPressed pops one queued press, and presses queue on key-down, so
        // holding the key produces a single press while mashing it can queue several in one tick.
        // Draining and discarding the rest keeps this side from creating a flood the server would
        // then have to absorb.
        if (HomeRecallKeys.recall().isPressed()) {
            HomeRecallNetwork.sendRecallRequest();
            while (HomeRecallKeys.recall().isPressed()) {
                // Drop the remaining presses buffered for this tick.
            }
        }
    }
}
