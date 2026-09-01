package com.mahghuuuls.homerecall.equipment;

import com.mahghuuuls.homerecall.Tags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import java.util.concurrent.Callable;

/**
 * The capability plumbing around {@link PlayerRecallEquipment}, and the one way to reach it.
 *
 * <p>{@link #of(EntityPlayer)} is the entire public surface: it returns the player's equipment or
 * null, and callers tolerate null without learning anything about capabilities. Everything else
 * here — the storage, the provider, the registration — is Forge ceremony that no other class
 * should ever need to see.
 */
public final class RecallEquipment {

    /** The key the provider is attached under, and the name the data wears in player NBT. */
    public static final ResourceLocation KEY = new ResourceLocation(Tags.MOD_ID, "equipment");

    @CapabilityInject(PlayerRecallEquipment.class)
    private static Capability<PlayerRecallEquipment> CAPABILITY;

    private RecallEquipment() {
    }

    /** Registers the capability. Called once, from preInit, on both sides. */
    public static void register() {
        CapabilityManager.INSTANCE.register(PlayerRecallEquipment.class,
                new Capability.IStorage<PlayerRecallEquipment>() {
                    @Override
                    public NBTBase writeNBT(Capability<PlayerRecallEquipment> capability,
                                            PlayerRecallEquipment instance, EnumFacing side) {
                        return instance.serializeNBT();
                    }

                    @Override
                    public void readNBT(Capability<PlayerRecallEquipment> capability,
                                        PlayerRecallEquipment instance, EnumFacing side,
                                        NBTBase nbt) {
                        instance.deserializeNBT((NBTTagCompound) nbt);
                    }
                },
                new Callable<PlayerRecallEquipment>() {
                    @Override
                    public PlayerRecallEquipment call() {
                        return new PlayerRecallEquipment();
                    }
                });
    }

    /**
     * This player's equipment, or null.
     *
     * <p>Null is an answer, not an error: a player another mod built strangely, or a moment
     * before attachment, simply has no equipment, and every caller treats that as "no stone,
     * no grant" without throwing.
     */
    public static PlayerRecallEquipment of(EntityPlayer player) {
        return player == null ? null : player.getCapability(CAPABILITY, null);
    }

    /** The provider attached to every player entity. Serializes through the instance's own NBT. */
    static final class Provider implements ICapabilitySerializable<NBTTagCompound> {

        private final PlayerRecallEquipment equipment = new PlayerRecallEquipment();

        @Override
        public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
            return capability == CAPABILITY;
        }

        @Override
        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
            return capability == CAPABILITY ? CAPABILITY.cast(equipment) : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return equipment.serializeNBT();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            equipment.deserializeNBT(nbt);
        }
    }
}
