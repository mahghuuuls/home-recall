package com.mahghuuuls.homerecall.recall;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * {@link SpawnLookup} against the running server.
 *
 * <p>Uses {@link DimensionManager} rather than {@code MinecraftServer.getWorld(int)}. Forge patches
 * that method to call {@code initDimension} when the world is not currently loaded, so what reads
 * as a question is actually a command: asking whether a player's Nether bed exists would load the
 * entire Nether, and asking about a dimension whose mod has been removed logs an error with a stack
 * trace. Every method here has to stay a query, because one of them runs on each key press.
 *
 * <p>Dimension-explicit accessors throughout: {@code getBedLocation(int)} rather than the
 * no-argument version, which Forge deprecates because it answers for whichever dimension the player
 * is standing in.
 */
public final class VanillaSpawnLookup implements SpawnLookup {

    private final EntityPlayerMP player;

    public VanillaSpawnLookup(EntityPlayerMP player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        this.player = player;
    }

    @Override
    public int spawnDimension() {
        return player.getSpawnDimension();
    }

    @Override
    public boolean worldExists(int dimension) {
        return DimensionManager.isDimensionRegistered(dimension);
    }

    @Override
    public boolean canRespawnIn(int dimension) {
        WorldServer world = loaded(dimension);
        if (world != null) {
            return world.provider.canRespawnHere();
        }
        // Registered but not loaded. Answering properly would mean loading the world, which is the
        // side effect this class exists to avoid.
        //
        // Building a throwaway provider to ask instead was considered and rejected: it constructs
        // one by reflection on every key press, and a modded provider is free to read state that a
        // detached instance does not have. (Vanilla's own canRespawnHere implementations are
        // constant returns and would have been safe; the hazard is the ones this mod has never
        // seen.)
        //
        // So an unloaded dimension is reported as respawnable. Being wrong here costs less than it
        // looks: if the player really does have a spawn recorded in it, the next step loads that
        // world to read their bed, which is work the recall needs done regardless.
        return true;
    }

    @Override
    public int respawnDimensionFor(int dimension) {
        WorldServer world = loaded(dimension);
        return world == null ? SpawnResolver.OVERWORLD
                : world.provider.getRespawnDimension(player);
    }

    @Override
    public SpawnPos resolveStoredSpawn(int dimension) {
        BlockPos stored = player.getBedLocation(dimension);
        if (stored == null) {
            return null;
        }
        // Loading the destination is unavoidable here: vanilla's bed rules read blocks. This runs
        // only when the player actually has a spawn recorded in that dimension, not on every query.
        WorldServer world = player.server.getWorld(dimension);
        if (world == null) {
            return null;
        }
        BlockPos resolved = EntityPlayer.getBedSpawnLocation(world, stored,
                player.isSpawnForced(dimension));
        return resolved == null ? null : toPos(resolved);
    }

    @Override
    public SpawnPos worldSpawn(int dimension) {
        WorldServer world = player.server.getWorld(dimension);
        WorldServer target = world == null ? player.server.getWorld(SpawnResolver.OVERWORLD) : world;
        return toPos(target.getSpawnPoint());
    }

    private WorldServer loaded(int dimension) {
        return DimensionManager.getWorld(dimension);
    }

    private static SpawnPos toPos(BlockPos pos) {
        return new SpawnPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
