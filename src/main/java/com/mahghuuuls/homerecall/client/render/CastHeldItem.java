package com.mahghuuuls.homerecall.client.render;

import com.mahghuuuls.homerecall.client.ClientCastState;
import com.mahghuuuls.homerecall.item.ModItems;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The one question this package answers: given a player, which stack does a renderer draw in
 * their main hand instead of the real one — or none, meaning draw what is really there.
 *
 * <p>The answer comes entirely from {@link ClientCastState}, the client's mirror of what the
 * server said. Nothing here asks whether the player is <i>eligible</i> to recall, owns a stone,
 * or which mode the config is in: a cast the server started is the only trigger, so the stone is
 * shown in Innate Mode, in Equipment-Gated Mode, and with {@code registerRecallStone=false}
 * alike. (REQ-050)
 *
 * <p>Display only. The stack handed out is this class's own, never placed in any slot, and the
 * renderers that receive it must not mutate it.
 */
@SideOnly(Side.CLIENT)
public final class CastHeldItem {

    /**
     * The stack every renderer shares. Built on first use rather than at class load, so this
     * class never races item construction; display only, so one instance serves every frame and
     * every player.
     */
    private static ItemStack stone = ItemStack.EMPTY;

    private CastHeldItem() {
    }

    /**
     * The stack to draw in this player's main hand, or {@link ItemStack#EMPTY} to draw reality.
     *
     * <p>The client's own player is an {@link EntityPlayerSP} and is asked about the client's own
     * cast; everyone else is looked up among the observed casts by entity id. That distinction is
     * the whole self/other split — no {@code Minecraft} lookup is needed to make it.
     */
    public static ItemStack stackFor(EntityPlayer player) {
        if (!showsStoneFor(player instanceof EntityPlayerSP, player.getEntityId())) {
            return ItemStack.EMPTY;
        }
        if (stone.isEmpty()) {
            stone = new ItemStack(ModItems.RECALL_STONE);
        }
        return stone;
    }

    /**
     * The decision alone, separated from Minecraft for the tests: the client's own belief for its
     * own player, the observed-cast table for anyone else.
     */
    static boolean showsStoneFor(boolean self, int entityId) {
        return self ? ClientCastState.casting() : ClientCastState.observedCasting(entityId);
    }
}
