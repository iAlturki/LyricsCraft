package com.ialturki.lyricscraft;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.keybinds.LyricsKeybinds;
import com.ialturki.lyricscraft.lyrics.LyricsManager;
import com.ialturki.lyricscraft.media.MediaManager;
import com.ialturki.lyricscraft.media.TrackInfo;
import com.ialturki.lyricscraft.render.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LyricsCraft implements ClientModInitializer {
    public static final String MOD_ID = "lyricscraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private int tickCounter;
    private String lastLoggedTrack = "";

    @Override public void onInitializeClient() {
        LOGGER.info("[LyricsCraft] Initializing");
        LyricsConfig.load();
        MediaManager.getInstance().init();
        LyricsKeybinds.register();
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(MOD_ID, "media_hud"), (graphics, delta) -> {
                Minecraft minecraft = Minecraft.getInstance();
                LyricsConfig config = LyricsConfig.getInstance();
                if (config.autoHideInScreens && minecraft.gui.screen() != null) return;
                TrackInfo track = MediaManager.getInstance().getCurrentTrack();
                PlayerWidgetRenderer.render(graphics, track, config, 0.0f);
                LyricsHudRenderer.render(graphics, track, config, 0.0f);
                ToastNotificationRenderer.render(graphics, 0.0f);
            });
        LyricsWorldRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            LyricsKeybinds.handleInput(minecraft);
            if (++tickCounter % 5 != 0) return;
            MediaManager.getInstance().tick();
            TrackInfo track = MediaManager.getInstance().getCurrentTrack();
            if (track != null && track.isValidTrack()) {
                String key = track.getTrackKey();
                if (!key.equals(lastLoggedTrack)) {
                    lastLoggedTrack = key;
                    LOGGER.info("[LyricsCraft] Track active: {} - {}", track.getTitle(), track.getArtist());
                    ToastNotificationRenderer.trigger(track);
                }
            } else lastLoggedTrack = "";
            LyricsManager.getInstance().updateTrack(track);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            MediaManager.getInstance().close();
            TextureHelper.close();
        });
        LOGGER.info("[LyricsCraft] Ready");
    }
}
