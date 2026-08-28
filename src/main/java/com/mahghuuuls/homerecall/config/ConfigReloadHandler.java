package com.mahghuuuls.homerecall.config;

import com.mahghuuuls.homerecall.HomeRecallMod;
import com.mahghuuuls.homerecall.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Picks up an edit made to the configuration while the game is running.
 *
 * <p>Forge's configuration screen writes the edited values to the file and posts this event, and
 * then stops. Nothing reloads the annotated fields for you. Without
 * {@link ConfigManager#sync(String, Config.Type)} here, the file on disk and the values the mod
 * acts on drift apart silently: the user sees their edit saved and sees nothing change.
 *
 * <p>Eleven of the thirteen options are documented as taking effect without a restart, which is
 * only true because of this class. The other two are pinned by {@link ConfigSnapshot} and stay at
 * their boot values however the file is edited.
 *
 * <p>Despite the {@code fml.client} package, {@code ConfigChangedEvent} is safe to reference on a
 * dedicated server: it is a plain event class with no client-only supertype and no
 * {@code @SideOnly}, and it is simply never posted there. A server operator edits the file and
 * restarts, which is the path that already worked.
 */
public final class ConfigReloadHandler {

    private ConfigReloadHandler() {
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Tags.MOD_ID.equals(event.getModID())) {
            return;
        }
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        ConfigSnapshot.refresh();
        for (String correction : ConfigSnapshot.current().corrections()) {
            HomeRecallMod.LOGGER.warn(correction);
        }
    }
}
