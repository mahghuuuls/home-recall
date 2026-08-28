package com.mahghuuuls.homerecall;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for Home Recall.
 *
 * <p>Loads on both sides. The server decides whether a recall may begin, how long it runs, whether
 * it is cancelled, where it ends, and when the player is moved; the client only sends a request and
 * draws what it is told. Nothing here may name a type from the {@code client} package, because this
 * class is constructed on a dedicated server and anything it mentions is loaded there too.
 *
 * <p>The dependency is an inclusive minimum with no upper bound. {@code 1.0.0} is not a convenience
 * floor: it is the first Inventory Button Bar release that builds a container on a dedicated
 * server, and a {@code required-after} on any earlier version fails the server's dependency check
 * even with the jar installed. An upper bound is deliberately absent, so a player may run a later
 * version; that permits it rather than certifying it.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
        dependencies = "required-after:inventorybuttonbar@[1.0.0,)")
public class HomeRecallMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("{} {} loading", Tags.MOD_NAME, Tags.VERSION);
    }

}
