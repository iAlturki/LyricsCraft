package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.media.TrackInfo;
import com.ialturki.lyricscraft.render.PlayerWidgetRenderer;
import com.ialturki.lyricscraft.render.TextureHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;

public class ToastNotificationRenderer {
    private static TrackInfo currentTrack = null;
    private static long triggerTime = 0L;

    public static void trigger(TrackInfo trackInfo) {
        if (trackInfo == null || !trackInfo.isValidTrack()) {
            return;
        }
        currentTrack = trackInfo;
        triggerTime = System.currentTimeMillis();
    }

    public static void render(GuiGraphicsExtractor class_3322, float f) {
        int n;
        LyricsConfig lyricsConfig = LyricsConfig.getInstance();
        if (!lyricsConfig.showNowPlayingToast) {
            return;
        }
        if (currentTrack == null) {
            return;
        }
        long l = switch (lyricsConfig.toastDuration != null ? lyricsConfig.toastDuration.toUpperCase() : "NORMAL") {
            case "SHORT" -> 2500L;
            case "LONG" -> 6500L;
            default -> 4000L;
        };
        long l2 = 350L;
        long l3 = l - 350L;
        long l4 = System.currentTimeMillis() - triggerTime;
        if (l4 >= l) {
            currentTrack = null;
            return;
        }
        Minecraft class_3102 = Minecraft.getInstance();
        Font class_3272 = class_3102.font;
        int n2 = class_3102.getWindow().getGuiScaledWidth();
        int n3 = 168;
        int n4 = 28;
        int n5 = 10;
        int n6 = n2 - n3 - n5;
        if (lyricsConfig.playerWidgetPosition == LyricsConfig.WidgetPosition.TOP_RIGHT) {
            n6 = n5;
        }
        int n7 = n5;
        int n8 = -n4 - 6;
        float var21_18, var22_20;
        if (l4 < l2) {
            var21_18 = (float)l4 / (float)l2;
            var22_20 = 1.0f - (1.0f - var21_18) * (1.0f - var21_18) * (1.0f - var21_18);
            n = (int)((float)n8 + (float)(n7 - n8) * var22_20);
        } else if (l4 > l3) {
            var21_18 = (float)(l4 - l3) / (float)(l - l3);
            var22_20 = var21_18 * var21_18 * var21_18;
            n = (int)((float)n7 + (float)(n8 - n7) * var22_20);
        } else {
            n = n7;
        }
        int n9 = PlayerWidgetRenderer.resolveAccentColor(lyricsConfig, currentTrack);
        int n10 = n9 & 0xFFFFFF;
        class_3322.fill(n6, n, n6 + n3, n + n4, -267381731);
        class_3322.fill(n6, n, n6 + 2, n + n4, 0xFF000000 | n10);
        class_3322.fill(n6 + 2, n, n6 + n3, n + 1, 0x30FFFFFF);
        class_3322.fill(n6 + 2, n + n4 - 1, n6 + n3, n + n4, 0x20FFFFFF);
        class_3322.fill(n6 + n3 - 1, n, n6 + n3, n + n4, 0x25FFFFFF);
        int n11 = 20;
        int n12 = n6 + 5;
        int n13 = n + 4;
        Identifier class_29602 = TextureHelper.getCoverArtTexture(currentTrack.getAlbumArtBytes(), currentTrack.getAlbumArtUrl());
        if (class_29602 != null) {
            try {
                class_3322.blit(class_29602, n12, n13, n12 + n11, n13 + n11, 0.0f, 1.0f, 0.0f, 1.0f);
            }
            catch (Exception exception) {
                ToastNotificationRenderer.renderDefaultArt(class_3322, class_3272, n12, n13, n11, n10);
            }
        } else {
            ToastNotificationRenderer.renderDefaultArt(class_3322, class_3272, n12, n13, n11, n10);
        }
        int n14 = n12 + n11 + 6;
        int n15 = n3 - n11 - 30;
        class_3322.text(class_3272, BidiText.fit(class_3272, currentTrack.getTitle(), Style.EMPTY, n15, ".."), n14, n + 4, -1);
        class_3322.text(class_3272, BidiText.fit(class_3272, currentTrack.getArtist(), Style.EMPTY, n15, ".."), n14, n + 15, -7629666);
        int n16 = n6 + n3 - 14;
        int n17 = n + 10;
        long l5 = System.currentTimeMillis();
        int n18 = 0xFF000000 | n10;
        for (int i = 0; i < 3; ++i) {
            int n19 = (int)(2.0 + Math.abs(Math.sin((double)l5 / 150.0 + (double)i * 1.2)) * 6.0);
            class_3322.fill(n16 + i * 3, n17 + (8 - n19), n16 + i * 3 + 2, n17 + 8, n18);
        }
    }

    private static void renderDefaultArt(GuiGraphicsExtractor class_3322, Font class_3272, int n, int n2, int n3, int n4) {
        class_3322.fill(n, n2, n + n3, n2 + n3, -2145902544);
        class_3322.text(class_3272, "\u266b", n + n3 / 2 - 3, n2 + n3 / 2 - 4, 0xFF000000 | n4);
    }
}

