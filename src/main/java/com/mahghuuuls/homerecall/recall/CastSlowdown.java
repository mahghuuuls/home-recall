package com.mahghuuuls.homerecall.recall;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;

import java.util.UUID;

/**
 * The movement slow a player carries while they are recalling.
 *
 * <p>One fixed modifier identity, applied when a cast starts and removed when it ends. Callers
 * call {@link #apply} and {@link #remove}; they never learn that an attribute modifier exists,
 * which value it holds, or that a slow of exactly normal speed is not applied at all.
 *
 * <p>{@link #remove} is safe to call on a player who has none, so every path that ends a cast can
 * call it unconditionally rather than first working out whether it needs to.
 *
 * <p>Server-side. Attribute changes are synchronized to the client by vanilla.
 */
public final class CastSlowdown {

    /**
     * One fixed identity so the slow can always be found and removed, and can never stack.
     *
     * <p>Deliberately not the identity Everfilling Flasks uses for its drink slow. The two mods
     * run together in the owner's pack and a shared identity would mean one removing the other's.
     */
    private static final UUID MODIFIER_ID = UUID.fromString("b7d41f92-08c3-4a6e-9f21-3ce5d0a7b184");

    private static final String MODIFIER_NAME = "homerecall cast slowdown";

    /**
     * Vanilla operation 2, multiply total. The final speed is the base multiplied by
     * {@code 1 + amount}, which is what makes {@link #modifierAmount} a subtraction.
     */
    private static final int MULTIPLY_TOTAL = 2;

    private CastSlowdown() {
    }

    /**
     * The modifier amount that produces the given fraction of normal speed.
     *
     * <p>Separated out because it is the one piece of this class that can be wrong quietly. An
     * amount of {@code 0.2} rather than {@code -0.8} does not fail, throw, or log; it makes the
     * player twenty percent <em>faster</em> during a recall, which reads as "the slow does not
     * work" long before anyone suspects the sign.
     */
    static double modifierAmount(double speedFraction) {
        return speedFraction - 1.0D;
    }

    /**
     * Slows the player to {@code speedFraction} of their normal speed for the rest of their cast.
     *
     * <p>A fraction of 1.0 or more applies nothing at all. That is the configured-off case, and
     * applying a zero-amount modifier instead would leave a modifier on every casting player for
     * no effect, which the removal paths would then have to be trusted to clean up.
     *
     * @return whether a modifier now exists because of this call. False both when the
     *         configuration asked for no slow and when one was already present. This is the only
     *         place that threshold is decided, so no caller has to repeat it to describe what
     *         happened.
     */
    public static boolean apply(EntityPlayer player, double speedFraction) {
        if (speedFraction >= 1.0D) {
            return false;
        }
        IAttributeInstance speed = speedAttribute(player);
        if (speed.getModifier(MODIFIER_ID) != null) {
            return false;
        }
        AttributeModifier modifier = new AttributeModifier(
                MODIFIER_ID, MODIFIER_NAME, modifierAmount(speedFraction), MULTIPLY_TOTAL);
        // setSaved(false) is the reason this cannot outlive a session. A saved modifier is written
        // into the player's attribute NBT, so a server killed mid-cast would restore a permanently
        // slow player with nothing in any log to explain it.
        speed.applyModifier(modifier.setSaved(false));
        return true;
    }

    /**
     * Removes the slow if the player has it.
     *
     * @return true only when a modifier was actually found and removed, so a caller that expected
     *         none can report that one existed. Nothing else should branch on this.
     */
    public static boolean remove(EntityPlayer player) {
        IAttributeInstance speed = speedAttribute(player);
        if (speed.getModifier(MODIFIER_ID) == null) {
            return false;
        }
        speed.removeModifier(MODIFIER_ID);
        return true;
    }

    private static IAttributeInstance speedAttribute(EntityPlayer player) {
        return player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
    }
}
