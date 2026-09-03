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

    /** Whether the key was down at the end of the previous tick — the other half of an edge. */
    private static boolean wasDown;

    private KeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (HomeRecallKeys.recall() == null || Minecraft.getMinecraft().player == null) {
            wasDown = false;
            return;
        }
        // Edges, not counts. In a bare game a held key queues one press, but a modpack can leave
        // keyboard repeat events switched on (any mod's screen can do that), and then a held key
        // streams presses — each one toggling the cast between cancelled and started. Seen in
        // the target pack. So the queue is only WITNESSED, never counted: it says whether any
        // press landed this tick, which is what catches a tap too quick to still be down when
        // this samples (down and up inside one tick). The raw down state supplies the other
        // half of the edge, so a hold is one request whatever the repeat state. (Review B2.)
        boolean tapped = false;
        while (HomeRecallKeys.recall().isPressed()) {
            tapped = true;
        }
        boolean down = HomeRecallKeys.recall().isKeyDown();
        if (shouldRequest(down, tapped, wasDown)) {
            HomeRecallNetwork.sendRecallRequest();
        }
        // The raw state, not "down or tapped": a tap must leave no edge behind for the next
        // tick to mistake for a release, or the press after it would be swallowed.
        wasDown = down;
        // Known and accepted: closing a screen while physically holding the key re-reads the
        // key state, so that instant can register as a fresh press and start a cast. Rare, and
        // the result is a visible, cancellable cast rather than anything destructive. (Review S4.)
    }

    /**
     * The whole rule: a request when the key is down now or was pressed since last tick, and
     * never while it merely stays down from before.
     */
    static boolean shouldRequest(boolean downNow, boolean tappedSince, boolean downBefore) {
        return (downNow || tappedSince) && !downBefore;
    }
}
