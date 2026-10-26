package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.lyrics.LyricLine;
import com.ialturki.lyricscraft.lyrics.LyricsManager;
import com.ialturki.lyricscraft.lyrics.TextNormalizer;
import com.ialturki.lyricscraft.media.TrackInfo;
import com.ialturki.lyricscraft.render.PlayerWidgetRenderer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class LyricsHudRenderer {
    // The part of the current line not sung yet.
    static final int UNSUNG = 0xC8CED8;

    public static void render(GuiGraphicsExtractor class_3322, TrackInfo trackInfo, LyricsConfig lyricsConfig, float f) {
        int n;
        int n2;
        if (!lyricsConfig.showLyricsHud || trackInfo == null || !trackInfo.isValidTrack()) {
            return;
        }
        if (lyricsConfig.autoHide && !trackInfo.isPlaying()) {
            return;
        }
        if (lyricsConfig.renderIn3D) {
            return;
        }
        Minecraft class_3102 = Minecraft.getInstance();
        Font class_3272 = class_3102.font;
        int n3 = class_3102.getWindow().getGuiScaledWidth();
        int n4 = class_3102.getWindow().getGuiScaledHeight();
        long l = trackInfo.getCurrentEstimatedPositionMs(lyricsConfig.timeOffsetMs);
        LyricsManager lyricsManager = LyricsManager.getInstance();
        if (!lyricsManager.hasLyrics()) {
            if (lyricsManager.isLoading()) {
                LyricsHudRenderer.renderSingleLine(class_3322, class_3272, "\u00a78Fetching lyrics...", n3, n4, lyricsConfig);
            }
            return;
        }
        LyricLine lyricLine = lyricsManager.getCurrentLine(l);
        if (lyricLine == null || lyricLine.text().isBlank()) {
            return;
        }
        String string = lyricsConfig.hudStyle != null ? lyricsConfig.hudStyle.toUpperCase() : "BORDERLESS";
        boolean bl = "COMPACT".equals(string) || lyricsConfig.compactMode;
        int n5 = bl ? Math.min(2, lyricsConfig.lyricsLineCount) : Math.max(2, Math.min(6, lyricsConfig.lyricsLineCount));
        List<LyricLine> list = lyricsManager.getUpcomingLines(l, n5 - 1);
        float f2 = lyricsConfig.playerWidgetScale > 0.4f ? lyricsConfig.playerWidgetScale : 1.0f;
        int n6 = (int)(250.0f * f2);
        int n7 = (int)((float)(bl ? 18 : 46) * f2);
        int n8 = bl ? 10 : 13;
        int n9 = (int)((float)(n5 * n8 + 4) * f2);
        int n10 = n2 = 10;
        int n11 = n2 + n7 + 2;
        LyricsConfig.WidgetPosition widgetPosition = lyricsConfig.playerWidgetPosition != null ? lyricsConfig.playerWidgetPosition : LyricsConfig.WidgetPosition.TOP_LEFT;
        switch (widgetPosition) {
            case TOP_LEFT: {
                n10 = n2;
                n11 = n2 + n7 + 2;
                break;
            }
            case TOP_RIGHT: {
                n10 = n3 - n6 - n2;
                n11 = n2 + n7 + 2;
                break;
            }
            case BOTTOM_LEFT: {
                n10 = n2;
                n11 = n4 - n7 - n2 - n9 - 2;
                break;
            }
            case BOTTOM_RIGHT: {
                n10 = n3 - n6 - n2;
                n11 = n4 - n7 - n2 - n9 - 2;
                break;
            }
            case CENTER: {
                n10 = (n3 - n6) / 2;
                n11 = (n4 - n7) / 2 + n7 + 2;
                break;
            }
            default: {
                n10 = n2;
                n11 = n2 + n7 + 2;
            }
        }
        int n12 = PlayerWidgetRenderer.resolveAccentColor(lyricsConfig, trackInfo);
        int n13 = n12 & 0xFFFFFF;
        int right = n10 + n6;
        if ("BORDERED".equals(string) && !bl) {
            n = Math.max(10, Math.min(255, (int)(lyricsConfig.playerWidgetAlpha * 255.0f)));
            int n14 = n << 24 | 0x121620;
            class_3322.fill(n10, n11, n10 + n6, n11 + n9, n14);
            class_3322.fill(n10, n11, n10 + 3, n11 + n9, 0xFF000000 | n13);
            n11 += 4;
            n10 += 8;
            right -= 6;
        }
        n = LyricsHudRenderer.activeLyricColor(lyricsConfig, trackInfo);
        String string3 = lyricsConfig.lyricsUppercase ? lyricLine.text().toUpperCase() : lyricLine.text();
        Style active = Style.EMPTY.withBold(lyricsConfig.boldActiveLyric).withItalic(lyricsConfig.italicLyrics);
        LyricsHudRenderer.line(class_3322, class_3272, LyricsHudRenderer.activeLine(lyricsManager, lyricLine, string3, active, lyricsConfig, l),
            string3, n10, right, n3, n11, 0xFF000000 | n);
        int n15 = n11 + n8;
        Style upcoming = Style.EMPTY.withItalic(lyricsConfig.italicLyrics);
        for (LyricLine lyricLine2 : list) {
            String string5 = lyricsConfig.lyricsUppercase ? lyricLine2.text().toUpperCase() : lyricLine2.text();
            LyricsHudRenderer.line(class_3322, class_3272, BidiText.visual(string5, upcoming), string5, n10, right, n3, n15, -2001684284);
            n15 += n8;
        }
    }

    /**
     * The current line, filled in the lyric colour as far as it has been sung, or marked with "≈" when the lyrics had no
     * timing and the line times are only an estimate.
     */
    static FormattedCharSequence activeLine(LyricsManager lyrics, LyricLine line, String text, Style style, LyricsConfig config, long position) {
        if (lyrics.isEstimated()) return BidiText.visual("≈ " + text, style);
        if (!LyricsHudRenderer.fills(config, line)) return BidiText.visual(text, style);
        LyricLine next = lyrics.getNextLine(position);
        int sung = line.sungChars(position, next != null ? next.timestampMs() : -1L);
        return BidiText.visual(text, sung, style, style.withColor(UNSUNG));
    }

    // Whether the current line is filled as it is sung under the Word-by-Word setting.
    static boolean fills(LyricsConfig config, LyricLine line) {
        String mode = config.wordFill == null ? "SYNCED" : config.wordFill.toUpperCase();
        return "ESTIMATED".equals(mode) || "SYNCED".equals(mode) && line.wordSynced();
    }

    private static void line(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence line, String text, int left, int right, int screenWidth, int y, int color) {
        int x = left;
        if (BidiText.isRightToLeft(TextNormalizer.forDisplay(text))) {
            // Right-to-left lyrics align to the right edge of the lyrics column; long lines still stay on screen.
            int width = font.width(line);
            x = Math.max(Math.min(left, screenWidth - width - 2), right - width);
        }
        graphics.text(font, line, x, y, color);
    }

    static int activeLyricColor(LyricsConfig lyricsConfig, TrackInfo trackInfo) {
        int n = PlayerWidgetRenderer.resolveAccentColor(lyricsConfig, trackInfo) & 0xFFFFFF;
        if (lyricsConfig.matchAlbumColor) {
            return n;
        }
        String string = lyricsConfig.activeLyricColor != null ? lyricsConfig.activeLyricColor.toUpperCase() : "GREEN";
        return switch (string) {
            case "WHITE" -> 0xFFFFFF;
            case "CYAN" -> 58879;
            case "GOLD" -> 16757504;
            case "PURPLE" -> 13959417;
            case "RED" -> 16717636;
            case "YELLOW" -> 0xFFFF55;
            case "BLUE" -> 0x5588FF;
            case "ALBUM" -> n;
            default -> 0x55FF55;
        };
    }

    private static void renderSingleLine(GuiGraphicsExtractor class_3322, Font class_3272, String string, int n, int n2, LyricsConfig lyricsConfig) {
        int n3 = 10;
        int n4 = 62;
        class_3322.text(class_3272, string, n3, n4, -7829368);
    }
}

