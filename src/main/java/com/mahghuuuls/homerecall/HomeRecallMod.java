package com.mahghuuuls.homerecall;

import com.mahghuuuls.homerecall.config.ConfigReloadHandler;
import com.mahghuuuls.homerecall.config.ConfigSnapshot;
import com.mahghuuuls.homerecall.guard.CastGuardHandler;
import com.mahghuuuls.homerecall.net.HomeRecallNetwork;
import com.mahghuuuls.homerecall.recall.RecallService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for Home Recall.
 *
 * <p>Loads on both sides. The server decides whether a recall may begin, how long it runs, whether
 * it is cancelled, where it ends, and when the player is moved; the client only sends a request and
 * draws what it is told. Names no type from the {@code client} package, because this class is
 * constructed on a dedicated server and anything it mentions is loaded there too.
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

    @SidedProxy(
            clientSide = "com.mahghuuuls.homerecall.client.ClientProxy",
            serverSide = "com.mahghuuuls.homerecall.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Forge reads the configuration during mod construction, so this is the first point the
        // annotated fields hold what the player wrote rather than the declared defaults. It is
        // therefore also the only correct moment to pin the two options that are consumed once,
        // before anything registers an item or loads a recipe.
        ConfigSnapshot.initialize();
        for (String correction : ConfigSnapshot.current().corrections()) {
            LOGGER.warn(correction);
        }
        MinecraftForge.EVENT_BUS.register(ConfigReloadHandler.class);

        MinecraftForge.EVENT_BUS.register(RecallService.class);
        MinecraftForge.EVENT_BUS.register(CastGuardHandler.class);
        HomeRecallNetwork.register();
        proxy.preInit(event);
    }

    /**
     * Discards recall state when the server stops.
     *
     * <p>Single player stops its integrated server without ending the game, so state that outlives
     * a server would be waiting for the next world.
     */
    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        RecallService.onServerStopping();
    }
}
