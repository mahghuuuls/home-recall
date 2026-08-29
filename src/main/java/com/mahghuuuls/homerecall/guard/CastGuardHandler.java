package com.mahghuuuls.homerecall.guard;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.recall.RecallService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The action block: while a player is recalling they cannot attack, break, place, interact, or
 * start using an item.
 *
 * <p>One class answers one question, "is this player casting", and cancels. It holds no state and
 * it never starts or ends a cast: a prevented action leaves the cast running. Ending a cast
 * belongs to {@code RecallService} and nothing here may reach into it.
 *
 * <p>Walking, jumping, looking around, changing hotbar slot, and opening a GUI are deliberately
 * absent from this list. They stay available. So is dropping an item, and that one is not an
 * oversight: cancelling {@code ItemTossEvent} destroys the stack, because the item has already
 * left the inventory by the time the event fires.
 *
 * <p>Runs on both sides, and that is not redundancy. The server decides. The client cancels the
 * same actions so it does not predict one the server is about to refuse: without it a player
 * watches a block appear and vanish, an eating animation that never completes, and a stack count
 * that drops and comes back. Nothing is wrong in any of those cases, which is exactly what makes
 * them read as bugs.
 *
 * <p>Two things the client cancel does not do, both by vanilla design. The arm swing still plays,
 * because it is triggered separately from the attack and plays when you swing at empty air too.
 * And a block whose breaking had already begun when the cast started keeps its client-side
 * progress, because the left-click event does not fire again mid-break; that block breaks locally
 * and is put back by the server. Both are cosmetic and neither can change what the server allows.
 */
public final class CastGuardHandler {

    private CastGuardHandler() {
    }

    /**
     * Whether this player's actions are currently blocked.
     *
     * <p>Every handler below starts here, and the common answer is "no". Combat and block breaking
     * are hot paths, so this stays a map lookup on a map that is empty whenever nobody is
     * recalling, or one boolean read on the client.
     *
     * <p>Two sources, one question. On the server the answer is the truth. On the client it is a
     * belief, and cancelling there only stops a prediction: the server refuses independently and
     * would still refuse a client that skipped this. Asking through the proxy is what keeps this
     * class free of any client type, so it stays loadable on a dedicated server.
     */
    private static boolean casting(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.world.isRemote) {
            return HomeRecallMod.proxy.isLocalPlayerCasting(player);
        }
        return RecallService.isCasting(player);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * The precise-position variant. Vanilla fires this one first for entities that care where they
     * were clicked, such as armour stands, and cancelling only the general event would leave those
     * interactions working during a cast.
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (casting(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Block breaking completing. {@code LeftClickBlock} above stops the swing from starting; this
     * stops a break that was already in progress when the cast began from landing.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (casting(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Block placing.
     *
     * <p>{@code EntityPlaceEvent} rather than its {@code PlaceEvent} subclass, which Forge marks
     * deprecated for removal. The subclass carries a player-typed accessor and nothing else, and
     * the entity here is null for a few placements with no placer at all, so it is checked rather
     * than cast.
     *
     * <p>This event is server-only for an ordinary placement, so an unmodified client no longer
     * reaches it: the right-click cancel above stops the prediction first. The inventory is resent
     * anyway, for the client that did not cancel. Forge restores the server-side stack before this
     * event is posted, so nothing is ever consumed, but a client that predicted the placement has
     * already decremented its own copy, and left alone that count stays wrong until something
     * happens to resync it. A player then watches blocks vanish and their stack drop, with no way
     * to know nothing was lost. One packet on a path that is already refusing an action.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (!casting(player)) {
            return;
        }
        event.setCanceled(true);
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        }
    }

    /**
     * Eating, drinking, drawing a bow, and raising a shield, at the moment one begins.
     *
     * <p>Only at the moment one begins. This fires from {@code setActiveHand}, which is guarded by
     * {@code !isHandActive}, so it never fires again for a use already under way. A use that was
     * running when the cast started is not this handler's to stop, and is not stoppable from any
     * event here: cancelling the per-tick use event makes vanilla treat the use as
     * <em>finished</em> rather than abandoned, because the cancel returns a duration of -1 and the
     * decrement immediately after it drives the countdown past zero into {@code onItemUseFinish}.
     * That would let a player swallow a slow food, or trigger a chorus fruit's teleport, by
     * starting a recall. {@code RecallService} clears the active hand once when the cast begins
     * instead, which abandons the use without telling the item it completed.
     */
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof EntityPlayer
                && casting((EntityPlayer) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }
}
