package com.mahghuuuls.homerecall.guard;

import com.mahghuuuls.homerecall.recall.CancelReason;
import com.mahghuuuls.homerecall.recall.RecallService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The channel breaker: doing anything while recalling cancels the recall, and the thing done
 * happens anyway.
 *
 * <p>One class answers one question, "did a casting player just act", and hands the cast to
 * {@code RecallService.cancel}. It cancels no event, blocks nothing, holds no state, and needs no
 * client half: the action is genuinely allowed, so the client's prediction is simply correct.
 *
 * <p>Every handler here must mean exactly the same thing: this broke the channel. An event whose
 * cancellation semantics would differ — the per-tick item-use event completes the use when
 * cancelled, the toss event destroys the stack — is no longer a hazard, because nothing here
 * cancels an event at all.
 *
 * <p>Looking around, changing hotbar slot, opening a GUI, and dropping an item are deliberately
 * absent. They stay free and break nothing; the toss in particular is a fidget, not an act upon
 * the world. Movement is not here either: it has no event, and the cast's own tick loop watches
 * the anchor. An item use already running when the cast starts is also not this class's business:
 * only starting a use is an action, so an eat in progress finishes in peace.
 */
public final class CastGuardHandler {

    private CastGuardHandler() {
    }

    /**
     * Breaks the channel if this player has one. The common answer is "no cast", one map lookup
     * against a map that is empty whenever nobody is recalling — these are hot paths.
     *
     * <p>{@code isCasting} answers false on the client, so the whole class is inert there without
     * a side check of its own.
     */
    private static void breakChannel(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && RecallService.isCasting(player)) {
            RecallService.cancel((EntityPlayerMP) player, CancelReason.ACTED);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        breakChannel(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        breakChannel(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        breakChannel(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        breakChannel(event.getEntityPlayer());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        breakChannel(event.getEntityPlayer());
    }

    /**
     * The precise-position variant, which vanilla fires for entities that care where they were
     * clicked, such as armour stands. Cheap to cover and idempotent when both fire.
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        breakChannel(event.getEntityPlayer());
    }

    /**
     * A break landing. The left-click above already broke the channel when the digging started;
     * this covers a dig that was in progress before the cast began, and creative's instant break.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        breakChannel(event.getPlayer());
    }

    /**
     * A placement landing. {@code EntityPlaceEvent} rather than its deprecated {@code PlaceEvent}
     * subclass; the entity is null for placements with no placer, which the instanceof absorbs.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            breakChannel((EntityPlayer) event.getEntity());
        }
    }

    /** Eating, drinking, drawing a bow, raising a shield — the moment one begins. */
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            breakChannel((EntityPlayer) event.getEntityLiving());
        }
    }
}
