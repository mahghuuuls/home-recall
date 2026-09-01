package com.mahghuuuls.homerecall.equipment;

import com.mahghuuuls.homerecall.item.ModItems;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * {@code /recallequip <show|set|clear|open> [player]} — the operator's window into the slot.
 *
 * <p>Born as IMP-007's required inspect-and-set path (the slot exists before its GUI does), and
 * kept deliberately: even with the GUI, an operator cannot open another player's equipment
 * screen, and "what does the server think this player has equipped" is exactly the question the
 * diagnostics philosophy says must be answerable on demand. Level 2, like every toolkit command.
 */
public final class CommandRecallEquipment extends CommandBase {

    @Override
    public String getName() {
        return "recallequip";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/recallequip <show|set|clear|open> [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP target = args.length >= 2
                ? getPlayer(server, sender, args[1])
                : getCommandSenderAsPlayer(sender);
        PlayerRecallEquipment equipment = RecallEquipment.of(target);
        if (equipment == null) {
            throw new CommandException(target.getName() + " has no Home Recall equipment attached");
        }

        String action = args[0];
        if ("open".equals(action)) {
            if (!com.mahghuuuls.homerecall.config.ConfigSnapshot.current().registerRecallStone()) {
                throw new CommandException("the stone system is disabled (registerRecallStone=false); "
                        + "show, set, and clear still work");
            }
            // The pre-GUI-button way in: opens the target's own screen, used by the validation
            // cards and by anyone on a server without the inventory button.
            target.openGui(com.mahghuuuls.homerecall.HomeRecallMod.INSTANCE,
                    com.mahghuuuls.homerecall.container.HomeRecallGuiHandler.EQUIPMENT_GUI,
                    target.world, 0, 0, 0);
            return;
        }
        if ("show".equals(action)) {
            ItemStack stone = equipment.stone();
            sender.sendMessage(new TextComponentString(target.getName() + ": "
                    + (stone.isEmpty() ? "slot empty" : "slot holds " + stone.getDisplayName())
                    + ", grant " + (equipment.granted() ? "used" : "unused")));
            return;
        }
        if ("set".equals(action)) {
            equipment.setStone(new ItemStack(ModItems.RECALL_STONE));
        } else if ("clear".equals(action)) {
            equipment.setStone(ItemStack.EMPTY);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
        // An open equipment screen holds its own working copy taken when it opened; a write from
        // here behind its back would leave the screen showing one truth and the capability
        // another, and the next click would write the stale copy back. Closing the screen is the
        // honest resolution: the target reopens it and sees the operator's change.
        if (target.openContainer instanceof
                com.mahghuuuls.homerecall.container.ContainerRecallEquipment) {
            // closeScreen, not closeContainer: only the former sends the close packet, so the
            // target's window actually shuts instead of freezing open over a dead container.
            target.closeScreen();
        }
        // The owner of the slot sees the change immediately, however it was made.
        HomeRecallNetwork.sendEquipmentSync(target);
        sender.sendMessage(new TextComponentString(target.getName() + ": "
                + ("set".equals(action) ? "stone equipped" : "slot cleared")));
    }
}
