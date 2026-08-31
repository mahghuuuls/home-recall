package com.mahghuuuls.homerecall.recall;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Places a transferring player exactly at the resolved recall destination, and builds nothing.
 *
 * <p>The whole reason this class exists is what it does not do. The default teleporter searches
 * for a Nether portal near the arrival point and constructs one out of obsidian when it finds
 * none, which for a recall would be a permanent scar on the world next to the player's bed. An
 * {@code ITeleporter} that only sets a position is how a dimension transfer opts out of all of
 * that. (ARC-004)
 *
 * <p>Overriding the position here is not optional either: by the time {@code placeEntity} runs,
 * vanilla has already multiplied the player's coordinates by the worlds' movement factor — the
 * Nether's 8x — so the entity arrives pre-placed at a scaled position that has nothing to do
 * with the resolved destination. This class is the last word on where the player lands.
 *
 * <p>Motion is zeroed so momentum from the origin world cannot carry into the first tick at the
 * destination and immediately look like the arrival sliding off a ledge.
 */
public final class RecallTeleporter implements ITeleporter {

    private final double x;
    private final double y;
    private final double z;

    public RecallTeleporter(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void placeEntity(World world, Entity entity, float yaw) {
        entity.setLocationAndAngles(x, y, z, yaw, entity.rotationPitch);
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
    }
}
