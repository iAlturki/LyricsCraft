package com.ialturki.lyricscraft.keybinds;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.gui.LyricsConfigScreen;
import com.ialturki.lyricscraft.media.MediaManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class LyricsKeybinds {
    public static KeyMapping toggleHudKey, openConfigKey, playPauseKey, nextTrackKey,
        prevTrackKey, seekBackKey, seekForwardKey;
    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("lyricscraft", "main"));
        toggleHudKey = key("toggle_hud", InputConstants.KEY_UP, category);
        openConfigKey = key("open_config", InputConstants.KEY_DOWN, category);
        prevTrackKey = key("prev_track", InputConstants.KEY_LEFT, category);
        nextTrackKey = key("next_track", InputConstants.KEY_RIGHT, category);
        playPauseKey = key("play_pause", InputConstants.KEY_BACKSLASH, category);
        seekBackKey = key("seek_back", InputConstants.KEY_LBRACKET, category);
        seekForwardKey = key("seek_forward", InputConstants.KEY_RBRACKET, category);
    }
    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricscraft." + name,
            InputConstants.Type.KEYBOARD, code, category));
    }
    public static void handleInput(Minecraft minecraft) {
        // Drain clicks in menus too, so they cannot trigger after a menu closes.
        boolean toggle = drain(toggleHudKey), config = drain(openConfigKey);
        boolean previous = drain(prevTrackKey), next = drain(nextTrackKey);
        boolean play = drain(playPauseKey), back = drain(seekBackKey), forward = drain(seekForwardKey);
        if (minecraft.player == null || minecraft.gui.screen() != null) return;
        if (toggle) {
            LyricsConfig settings = LyricsConfig.getInstance();
            settings.showLyricsHud = !settings.showLyricsHud;
            LyricsConfig.save();
        }
        if (config) minecraft.gui.setScreen(new LyricsConfigScreen(null));
        MediaManager manager = MediaManager.getInstance();
        if (previous) manager.skipPrevious();
        if (next) manager.skipNext();
        if (play) manager.togglePlayPause();
        if (back) manager.seekRelative(-5000L);
        if (forward) manager.seekRelative(5000L);
    }
    private static boolean drain(KeyMapping mapping) {
        boolean clicked = false;
        while (mapping != null && mapping.consumeClick()) clicked = true;
        return clicked;
    }
}
