package com.mahghuuuls.homerecall.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * What this client believes about its own cast.
 *
 * <p>Believes, not knows. The server decides; this is a mirror of what it last said, and it is
 * allowed to be briefly wrong. Nothing here may be treated as authority: it exists so the client
 * can stop predicting actions the server is about to refuse, and later so the cast bar has
 * something to draw.
 *
 * <p>Only the local player's cast for now. Nearby players' casts arrive in a later slice, which is
 * why this is a small object rather than a field on the player.
 */
@SideOnly(Side.CLIENT)
public final class ClientCastState {

    private static boolean casting;
    private static int durationTicks;

    private ClientCastState() {
    }

    /** Records what the server just said. */
    public static void set(boolean nowCasting, int duration) {
        casting = nowCasting;
        durationTicks = nowCasting ? duration : 0;
    }

    /** Whether this client currently believes it is casting. */
    public static boolean casting() {
        return casting;
    }

    /** The cast's total length in ticks, or zero when not casting. */
    public static int durationTicks() {
        return durationTicks;
    }

    /**
     * Forgets everything.
     *
     * <p>Called when leaving a world. The state is static and the client outlives any one server,
     * so a belief left behind would have the player unable to act on the next world they join,
     * with no cast anywhere to explain it.
     */
    public static void clear() {
        casting = false;
        durationTicks = 0;
    }
}
