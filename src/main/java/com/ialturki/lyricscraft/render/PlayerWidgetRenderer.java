package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.media.TrackInfo;
import com.ialturki.lyricscraft.render.TextureHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class PlayerWidgetRenderer {
    public static final int BASE_WIDGET_WIDTH = 250;
    public static final int BASE_WIDGET_HEIGHT = 46;

    public static void render(GuiGraphicsExtractor class_3322, TrackInfo trackInfo, LyricsConfig lyricsConfig, float f) {
        int n;
        if (!lyricsConfig.showPlayerWidget || trackInfo == null || !trackInfo.isValidTrack()) {
            return;
        }
        if (lyricsConfig.autoHide && !trackInfo.isPlaying()) {
            return;
        }
        Minecraft class_3102 = Minecraft.getInstance();
        Font class_3272 = class_3102.font;
        int n2 = class_3102.getWindow().getGuiScaledWidth();
        int n3 = class_3102.getWindow().getGuiScaledHeight();
        long l = System.currentTimeMillis();
        String string = lyricsConfig.hudStyle != null ? lyricsConfig.hudStyle.toUpperCase() : "BORDERLESS";
        boolean bl = "COMPACT".equals(string) || lyricsConfig.compactMode;
        float f2 = lyricsConfig.playerWidgetScale > 0.4f ? lyricsConfig.playerWidgetScale : 1.0f;
        int n4 = (int)(250.0f * f2);
        int n5 = (int)((float)(bl ? 18 : 46) * f2);
        int n6 = n = 10;
        int n7 = n;
        LyricsConfig.WidgetPosition widgetPosition = lyricsConfig.playerWidgetPosition != null ? lyricsConfig.playerWidgetPosition : LyricsConfig.WidgetPosition.TOP_LEFT;
        switch (widgetPosition) {
            case TOP_LEFT: {
                n6 = n;
                n7 = n;
                break;
            }
            case TOP_RIGHT: {
                n6 = n2 - n4 - n;
                n7 = n;
                break;
            }
            case BOTTOM_LEFT: {
                n6 = n;
                n7 = n3 - n5 - n;
                break;
            }
            case BOTTOM_RIGHT: {
                n6 = n2 - n4 - n;
                n7 = n3 - n5 - n;
                break;
            }
            case CENTER: {
                n6 = (n2 - n4) / 2;
                n7 = n;
                break;
            }
            default: {
                n6 = n;
                n7 = n;
            }
        }
        int n8 = PlayerWidgetRenderer.resolveAccentColor(lyricsConfig, trackInfo);
        int n9 = n8 & 0xFFFFFF;
        if ("BORDERLESS".equals(string) && !bl) {
            int n10 = 36;
            int n11 = n6 + 2;
            int n12 = n7 + 2;
            Identifier class_29602 = TextureHelper.getCoverArtTexture(trackInfo.getAlbumArtBytes(), trackInfo.getAlbumArtUrl());
            if (class_29602 != null) {
                class_3322.fill(n11 + 1, n12 + 1, n11 + n10 + 2, n12 + n10 + 2, 0x55000000);
                class_3322.blit(class_29602, n11, n12, n11 + n10, n12 + n10, 0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                class_3322.fill(n11, n12, n11 + n10, n12 + n10, -1726933470);
                class_3322.fill(n11, n12, n11 + 2, n12 + n10, 0xFF000000 | n9);
                class_3322.text(class_3272, "\u266b", n11 + 13, n12 + 14, 0xFF000000 | n9);
            }
            int n13 = n11 + n10 + 8;
            int n14 = n4 - (n10 + 12);
            class_3322.text(class_3272, BidiText.fit(class_3272, trackInfo.getTitle(), Style.EMPTY, n14, "\u2026"), n13, n12 + 2, -1);
            class_3322.text(class_3272, BidiText.fit(class_3272, trackInfo.getArtist(), Style.EMPTY, n14 - 32, "\u2026"), n13, n12 + 15, -5195580);
            int n17 = n6 + n4 - 22;
            int n18 = n12 + 13;
            PlayerWidgetRenderer.renderEqualizer(class_3322, trackInfo.isPlaying(), l, n17, n18, n9);
            int n19 = n13;
            int n20 = n12 + 27;
            int n21 = n4 - (n10 + 14);
            PlayerWidgetRenderer.renderProgressBar(class_3322, trackInfo, lyricsConfig, n19, n20, n21, n9);
            return;
        }
        if (bl) {
            int n22 = 14;
            int n23 = n6;
            int n24 = n7 + 1;
            Identifier class_29603 = TextureHelper.getCoverArtTexture(trackInfo.getAlbumArtBytes(), trackInfo.getAlbumArtUrl());
            if (class_29603 != null) {
                class_3322.blit(class_29603, n23, n24, n23 + n22, n24 + n22, 0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                class_3322.fill(n23, n24, n23 + n22, n24 + n22, -1441720798);
                class_3322.text(class_3272, "\u266b", n23 + 3, n24 + 3, 0xFF000000 | n9);
            }
            int n25 = n23 + n22 + 5;
            int n26 = n4 - (n22 + 44);
            class_3322.text(class_3272, PlayerWidgetRenderer.titleAndArtist(class_3272, trackInfo, n26), n25, n7 + 3, -1);
            int n27 = n6 + n4 - 16;
            int n28 = n7 + 2;
            PlayerWidgetRenderer.renderEqualizer(class_3322, trackInfo.isPlaying(), l, n27, n28, n9);
            int n29 = n25;
            int n30 = n7 + 14;
            int n31 = n4 - (n22 + 22);
            PlayerWidgetRenderer.renderProgressBar(class_3322, trackInfo, lyricsConfig, n29, n30, n31, n9);
            return;
        }
        int n32 = Math.max(10, Math.min(255, (int)(lyricsConfig.playerWidgetAlpha * 255.0f)));
        int n33 = n32 << 24 | 0x121620;
        class_3322.fill(n6, n7, n6 + n4, n7 + n5, n33);
        class_3322.fill(n6, n7, n6 + 3, n7 + n5, 0xFF000000 | n9);
        class_3322.fill(n6 + 3, n7, n6 + n4, n7 + 1, 0x33FFFFFF);
        class_3322.fill(n6 + 3, n7 + n5 - 1, n6 + n4, n7 + n5, 0x22000000);
        int n34 = 34;
        int n35 = n6 + 8;
        int n36 = n7 + (n5 - n34) / 2;
        Identifier class_29604 = TextureHelper.getCoverArtTexture(trackInfo.getAlbumArtBytes(), trackInfo.getAlbumArtUrl());
        if (class_29604 != null) {
            class_3322.blit(class_29604, n35, n36, n35 + n34, n36 + n34, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            class_3322.fill(n35, n36, n35 + n34, n36 + n34, -1725946315);
            class_3322.text(class_3272, "\u266b", n35 + 12, n36 + 13, 0xFF000000 | n9);
        }
        int n37 = n35 + n34 + 8;
        int n38 = n4 - (n34 + 22);
        class_3322.text(class_3272, BidiText.fit(class_3272, trackInfo.getTitle(), Style.EMPTY, n38, "\u2026"), n37, n36 + 2, -1);
        class_3322.text(class_3272, BidiText.fit(class_3272, trackInfo.getArtist(), Style.EMPTY, n38 - 30, "\u2026"), n37, n36 + 14, -6377793);
        int n39 = n6 + n4 - 20;
        int n40 = n36 + 12;
        PlayerWidgetRenderer.renderEqualizer(class_3322, trackInfo.isPlaying(), l, n39, n40, n9);
        int n41 = n37;
        int n42 = n36 + 26;
        int n43 = n4 - (n34 + 20);
        PlayerWidgetRenderer.renderProgressBar(class_3322, trackInfo, lyricsConfig, n41, n42, n43, n9);
    }

    // Title and artist are laid out separately so each keeps its own reading direction.
    private static FormattedCharSequence titleAndArtist(Font font, TrackInfo trackInfo, int width) {
        FormattedCharSequence title = BidiText.fit(font, trackInfo.getTitle(), Style.EMPTY, width, "…");
        String separator = " • ";
        int room = width - font.width(title) - font.width(separator);
        if (trackInfo.getArtist().isBlank() || room < 12) {
            return title;
        }
        Style gray = Style.EMPTY.withColor(ChatFormatting.GRAY);
        return FormattedCharSequence.composite(title, FormattedCharSequence.forward(separator, gray), BidiText.fit(font, trackInfo.getArtist(), gray, room, "…"));
    }

    private static void renderEqualizer(GuiGraphicsExtractor class_3322, boolean bl, long l, int n, int n2, int n3) {
        int n4 = 0xFF000000 | n3;
        for (int i = 0; i < 3; ++i) {
            int n5 = bl ? (int)(3.0 + Math.abs(Math.sin((double)l / 160.0 + (double)i * 1.2)) * 8.0) : 2;
            int n6 = n + i * 4;
            int n7 = n2 + (10 - n5);
            class_3322.fill(n6, n7, n6 + 2, n7 + n5, n4);
        }
    }

    private static void renderProgressBar(GuiGraphicsExtractor class_3322, TrackInfo trackInfo, LyricsConfig lyricsConfig, int n, int n2, int n3, int n4) {
        if (n3 <= 0) {
            return;
        }
        long l = trackInfo.getDurationMs();
        long l2 = trackInfo.getCurrentEstimatedPositionMs(lyricsConfig.timeOffsetMs);
        float f = l > 0L ? Math.min(1.0f, (float)l2 / (float)l) : 0.0f;
        class_3322.fill(n, n2, n + n3, n2 + 2, 0x44FFFFFF);
        int n5 = (int)((float)n3 * f);
        if (n5 > 0) {
            class_3322.fill(n, n2, n + n5, n2 + 2, 0xFF000000 | n4);
        }
    }

    public static int resolveAccentColor(LyricsConfig lyricsConfig, TrackInfo trackInfo) {
        String string;
        String string2 = string = lyricsConfig.accentColor != null ? lyricsConfig.accentColor.toUpperCase() : "ALBUM";
        if ("ALBUM".equals(string) && trackInfo != null) {
            int n = TextureHelper.getMoodColorForTrack(trackInfo.getAlbumArtBytes(), trackInfo.getTitle(), trackInfo.getArtist());
            return 0xFF000000 | n & 0xFFFFFF;
        }
        return switch (string) {
            case "WHITE" -> -1;
            case "CYAN" -> -16718337;
            case "GOLD" -> -19712;
            case "PURPLE" -> -2817799;
            case "RED" -> -59580;
            default -> -13710223;
        };
    }
}

