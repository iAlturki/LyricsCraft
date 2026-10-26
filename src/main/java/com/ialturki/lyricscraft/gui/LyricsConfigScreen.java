package com.ialturki.lyricscraft.gui;

import com.ialturki.lyricscraft.config.LyricsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

public final class LyricsConfigScreen extends Screen {
    private final Screen parent;
    private final LyricsConfig config = LyricsConfig.getInstance();
    private static int activeTab;
    private int left, row;

    public LyricsConfigScreen(Screen parent) {
        super(Component.literal("LyricsCraft Settings"));
        this.parent = parent;
    }
    @Override protected void init() {
        int tabWidth = 76, tabLeft = width / 2 - 158;
        String[] tabs = {"Display", "Appearance", "Lyrics", "3D & Flight"};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            addRenderableWidget(Button.builder(Component.literal((activeTab == i ? "\u00a7a\u00a7l" : "\u00a77") + tabs[i]),
                button -> { activeTab = tab; rebuildWidgets(); })
                .bounds(tabLeft + i * 80, 12, tabWidth, 18).build());
        }
        left = width / 2 - 140; row = 42;
        switch (activeTab) {
            case 0 -> {
                control(() -> "Style: " + config.hudStyle, "Choose Borderless, Bordered, or Compact.", () -> {
                    config.hudStyle = cycle(config.hudStyle, "BORDERLESS", "BORDERED", "COMPACT");
                    config.compactMode = "COMPACT".equals(config.hudStyle);
                });
                control(() -> "Player Widget: " + on(config.showPlayerWidget), "Show the music player card.",
                    () -> config.showPlayerWidget = !config.showPlayerWidget);
                control(() -> "Lyrics HUD: " + on(config.showLyricsHud), "Show synchronized lyrics.",
                    () -> config.showLyricsHud = !config.showLyricsHud);
                control(() -> "Widget Position: " + config.playerWidgetPosition, "Position of the player and lyrics.", () -> {
                    LyricsConfig.WidgetPosition[] values = LyricsConfig.WidgetPosition.values();
                    int next = (config.playerWidgetPosition.ordinal() + 1) % values.length;
                    config.playerWidgetPosition = values[next];
                });
                control(() -> "Hide in Menus: " + on(config.autoHideInScreens), "Hide the HUD while another screen is open.",
                    () -> config.autoHideInScreens = !config.autoHideInScreens);
                control(() -> "Song Toast: " + on(config.showNowPlayingToast), "Show a notification when the track changes.",
                    () -> config.showNowPlayingToast = !config.showNowPlayingToast);
            }
            case 1 -> {
                control(() -> "Accent Color: " + config.accentColor, "Select the player accent color.",
                    () -> config.accentColor = cycle(config.accentColor, "ALBUM", "GREEN", "WHITE", "CYAN", "GOLD", "PURPLE", "RED"));
                control(() -> "Scale: " + String.format("%.1fx", config.playerWidgetScale), "Adjust the player widget size.",
                    () -> config.playerWidgetScale = config.playerWidgetScale >= 1.9f ? .6f : Math.round((config.playerWidgetScale + .2f) * 10) / 10f);
                control(() -> "Opacity: " + Math.round(config.playerWidgetAlpha * 100) + "%", "Adjust the player background opacity.",
                    () -> config.playerWidgetAlpha = config.playerWidgetAlpha >= .99f ? .2f : Math.min(1f, config.playerWidgetAlpha + .15f));
                control(() -> "Auto-Hide Paused: " + on(config.autoHide), "Hide the HUD when playback is paused.",
                    () -> config.autoHide = !config.autoHide);
                control(() -> "Toast Duration: " + config.toastDuration, "How long song notifications stay visible.",
                    () -> config.toastDuration = cycle(config.toastDuration, "SHORT", "NORMAL", "LONG"));
            }
            case 2 -> {
                control(() -> "Lines: " + config.lyricsLineCount, "Show two to six lyric lines.",
                    () -> config.lyricsLineCount = config.lyricsLineCount >= 6 ? 2 : config.lyricsLineCount + 1);
                control(() -> "Sync: " + config.timeOffsetMs + "ms", "Fine-tune lyric timing to compensate for audio latency.",
                    () -> config.timeOffsetMs = config.timeOffsetMs >= 1000 ? -1000 : config.timeOffsetMs + 100);
                control(() -> "Word-by-Word: " + switch (config.wordFill == null ? "SYNCED" : config.wordFill.toUpperCase()) {
                        case "ESTIMATED" -> "§eEstimated";
                        case "OFF" -> "§cOFF";
                        default -> "§aSynced only";
                    },
                    "Fill the current line in the lyric color as it is sung. Synced only: for songs LRCLIB has word timings for. Estimated: every line, spread evenly, so it can run ahead of or behind the singer.",
                    () -> config.wordFill = cycle(config.wordFill, "SYNCED", "ESTIMATED", "OFF"));
                control(() -> "Bold Active: " + on(config.boldActiveLyric), "Use bold text for the current line.",
                    () -> config.boldActiveLyric = !config.boldActiveLyric);
                control(() -> "Italic: " + on(config.italicLyrics), "Use italic text for lyrics.",
                    () -> config.italicLyrics = !config.italicLyrics);
                control(() -> "Uppercase: " + on(config.lyricsUppercase), "Capitalize lyric lines.",
                    () -> config.lyricsUppercase = !config.lyricsUppercase);
                control(() -> "Match Album: " + on(config.matchAlbumColor), "Use the album color for the active line.",
                    () -> config.matchAlbumColor = !config.matchAlbumColor);
                control(() -> "Lyric Color: " + config.activeLyricColor, "Choose the active lyric color.",
                    () -> config.activeLyricColor = cycle(config.activeLyricColor, "GREEN", "WHITE", "CYAN", "GOLD", "PURPLE", "RED", "YELLOW", "BLUE"));
            }
            default -> {
                control(() -> "3D World Lyrics: " + on(config.renderIn3D), "Float glowing lyrics in the world instead of the on-screen lyrics HUD.",
                    () -> config.renderIn3D = !config.renderIn3D);
                control(() -> "3D Style: " + ("BLOCKS".equalsIgnoreCase(config.worldLyricsStyle) ? "On Blocks" : "Floating"),
                    "Floating: each line hangs ahead and moves out of the way of blocks. On Blocks: the words land one by one on walls and the ground ahead.",
                    () -> config.worldLyricsStyle = cycle(config.worldLyricsStyle, "FLOATING", "BLOCKS"));
                control(() -> "Distance: " + blocks(config.textDistance3D), "How far ahead each lyric line appears.",
                    () -> config.textDistance3D = next(config.textDistance3D, 8, 12, 18, 24, 32));
                control(() -> "Height: " + blocks(config.textHeightOffset3D), "How high above eye level lyrics float.",
                    () -> config.textHeightOffset3D = next(config.textHeightOffset3D, 0, 1, 2.5, 4, 6));
                control(() -> "Two-Sided: " + on(config.twoSided3D), "Keep lyrics readable from behind after walking past them.",
                    () -> config.twoSided3D = !config.twoSided3D);
                control(() -> "Glow: " + on(config.lyricsGlowEffect), "Give the active line a glowing colored outline instead of a dark one.",
                    () -> config.lyricsGlowEffect = !config.lyricsGlowEffect);
                control(() -> "Elytra Flight Assist: " + on(config.elytraFlightAssist), "Place lyrics further ahead along your flight path while gliding.",
                    () -> config.elytraFlightAssist = !config.elytraFlightAssist);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }
    private void control(Supplier<String> label, String help, Runnable change) {
        addRenderableWidget(Button.builder(Component.literal(label.get()), button -> {
            change.run(); LyricsConfig.save(); button.setMessage(Component.literal(label.get()));
        }).bounds(left, row, 280, 20).tooltip(Tooltip.create(Component.literal(help))).build());
        row += 22;
    }
    private static String on(boolean value) { return value ? "\u00a7aON" : "\u00a7cOFF"; }
    private static String cycle(String current, String... values) {
        for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(current)) return values[(i+1) % values.length];
        return values[0];
    }
    private static double next(double current, double... values) {
        for (double value : values) if (value > current + 1e-6) return value;
        return values[0];
    }
    private static String blocks(double value) {
        return (value == Math.rint(value) ? String.valueOf((int)value) : String.valueOf(value)) + " blocks";
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66111620);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { LyricsConfig.save(); minecraft.gui.setScreen(parent); }
}
