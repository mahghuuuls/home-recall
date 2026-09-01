package com.mahghuuuls.homerecall.item;

import com.mahghuuuls.homerecall.Tags;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * The Recall Stone: the object that grants and explains the recall ability.
 *
 * <p>Deliberately inert. It has no use behavior, no durability, and no right-click — equipping it
 * in the Recall Stone Slot is its entire function, and that function lives in the equipment
 * system, not here. An item that also did something on use would blur "this unlocks the ability"
 * into "this is the ability", which the concept keeps apart. (REQ-022)
 *
 * <p>Stack size one: a player equips a stone, not a supply of them, and a slot that could hold
 * sixty-four would imply consumption that does not exist.
 */
public final class ItemRecallStone extends Item {

    public ItemRecallStone() {
        // The registry name is deliberately NOT set here: that call walks into Forge's loader,
        // which exists only in a launched game, and this constructor must also work in a unit
        // test that only needs a real instance for the slot's accept rule. ModItems names the
        // item at registration, the one place a registry name is ever needed.
        setTranslationKey(Tags.MOD_ID + ".recall_stone");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    /**
     * The stone teaches the mechanic: what equipping it does under the current mode, and which
     * key — the one actually bound right now, read live through the proxy so this common class
     * never touches a client type. (REQ-034, REQ-035)
     *
     * <p>{@code @SideOnly(CLIENT)} like the vanilla method it overrides: the signature itself
     * names a client class, so the stripped method is what keeps this common item loadable on a
     * dedicated server.
     */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.world.World world,
                               java.util.List<String> tooltip,
                               net.minecraft.client.util.ITooltipFlag flag) {
        boolean required =
                com.mahghuuuls.homerecall.config.ConfigSnapshot.current().requireRecallStone();
        tooltip.add(net.minecraft.client.resources.I18n.format(abilityLineKey(required)));
        String keyName = com.mahghuuuls.homerecall.HomeRecallMod.proxy.recallKeyDisplayName();
        if (keyName == null || keyName.isEmpty()) {
            tooltip.add(net.minecraft.client.resources.I18n.format(
                    "homerecall.tooltip.recall_stone.unbound"));
        } else {
            tooltip.add(net.minecraft.client.resources.I18n.format(
                    "homerecall.tooltip.recall_stone.key", keyName));
        }
    }

    /**
     * Which explanation the first line carries. Pulled out so the one rule with a wrong answer —
     * claiming the stone is required when recall is innate — is pinned by a test. (REQ-034)
     */
    static String abilityLineKey(boolean requireRecallStone) {
        return requireRecallStone ? "homerecall.tooltip.recall_stone.required"
                : "homerecall.tooltip.recall_stone.innate";
    }

    /**
     * Suppressed from the creative tab (and therefore JEI's listing) when the stone system is
     * off. Hidden, not unregistered: the item must always exist so no world is ever damaged.
     * (REQ-024, ARC-001)
     */
    @Override
    public void getSubItems(CreativeTabs tab, net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> items) {
        if (com.mahghuuuls.homerecall.config.ConfigSnapshot.current().registerRecallStone()) {
            super.getSubItems(tab, items);
        }
    }
}
