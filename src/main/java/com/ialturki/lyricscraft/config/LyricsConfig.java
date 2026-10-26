package com.ialturki.lyricscraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.FabricLoader;

public class LyricsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lyricscraft.json");
    // Settings of LyricsPlus, which LyricsCraft started from, are picked up on the first launch.
    private static final File LYRICSPLUS_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lyricsplus.json");
    private static LyricsConfig INSTANCE;
    public String hudStyle = "BORDERLESS";
    public WidgetPosition playerWidgetPosition = WidgetPosition.TOP_LEFT;
    public boolean showAlbumArt = true;
    public boolean showArtist = true;
    public boolean showProgressBar = true;
    public boolean showTime = true;
    public boolean compactMode = false;
    public boolean autoHide = false;
    public boolean showPlayerWidget = true;
    public float playerWidgetAlpha = 0.95f;
    public float playerWidgetScale = 1.0f;
    public String accentColor = "ALBUM";
    public boolean showShadow = true;
    public boolean smoothAnimation = true;
    public boolean showEqualizer = true;
    public boolean showLyricsHud = true;
    public int lyricsLineCount = 4;
    public String lyricsSpacing = "NORMAL";
    public float lyricsBgAlpha = 0.9f;
    public String lyricsPositionMode = "BELOW_HUD";
    public boolean matchAlbumColor = false;
    public String activeLyricColor = "GREEN";
    public boolean boldActiveLyric = true;
    public boolean italicLyrics = false;
    public boolean lyricsUppercase = false;
    public boolean showNextLinePreview = true;
    // Fill the current line in the lyric color as it is sung. SYNCED: only with real word timings. ESTIMATED: also spread
    // over lines that only have a start time, which can run ahead of or behind the singer within a line. OFF.
    public String wordFill = "SYNCED";
    public String fontStyle = "DEFAULT";
    public boolean renderIn3D = false;
    // FLOATING: each line hangs in the air ahead. BLOCKS: the words of each line land on the blocks ahead.
    public String worldLyricsStyle = "FLOATING";
    public double textDistance3D = 18.0;
    public double textHeightOffset3D = 2.5;
    public boolean elytraFlightAssist = true;
    public boolean twoSided3D = true;
    public long timeOffsetMs = 120L;
    public int playerWidgetCustomX = 10;
    public int playerWidgetCustomY = 10;
    public int lyricsCustomX = 10;
    public int lyricsCustomY = 10;
    public float lyricsScale = 1.0f;
    public int lyricsColorHex = 0x55FF55;
    public boolean lyricsDropShadow = true;
    public boolean lyricsGlowEffect = true;
    public boolean showNowPlayingToast = true;
    public boolean beatPulseEffect = true;
    public boolean smartCombatFade = true;
    public float combatFadeAlpha = 0.15f;
    public boolean showInstrumentalCountdown = true;
    public boolean scrollingMarquee = true;
    public boolean showLyricsLoading = true;
    public boolean smoothLyricTransition = true;
    public boolean autoHideInScreens = true;
    public int hudMargin = 10;
    public String toastDuration = "NORMAL";
    public float lyricsFontScale = 1.0f;
    public boolean activeLineHighlight = true;
    public boolean idleAutoDim = false;

    public static LyricsConfig getInstance() {
        if (INSTANCE == null) {
            LyricsConfig.load();
        }
        return INSTANCE;
    }

    public static void load() {
        File source = CONFIG_FILE.exists() ? CONFIG_FILE : LYRICSPLUS_FILE;
        if (source.exists()) {
            try (FileReader fileReader = new FileReader(source, StandardCharsets.UTF_8);){
                INSTANCE = (LyricsConfig)GSON.fromJson((Reader)fileReader, LyricsConfig.class);
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new LyricsConfig();
        }
        if (!CONFIG_FILE.exists()) {
            LyricsConfig.save();
        }
    }

    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new LyricsConfig();
        }
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter fileWriter = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8);){
                GSON.toJson((Object)INSTANCE, (Appendable)fileWriter);
            }
        }
        catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }

    public static enum WidgetPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER,
        CUSTOM;

    }

    public static enum LyricsPosition {
        TOP_LEFT,
        ABOVE_CROSSHAIR,
        CENTER,
        TOP,
        BOTTOM,
        CUSTOM;

    }
}
