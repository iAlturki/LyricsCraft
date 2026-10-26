package com.ialturki.lyricscraft.lyrics;

import com.ialturki.lyricscraft.media.TrackInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class LyricsManager {
    private static LyricsManager INSTANCE;
    private final List<LyricLine> currentLyrics = new CopyOnWriteArrayList<LyricLine>();
    private volatile String lastTrackKey = "";
    private volatile boolean loading = false;
    private volatile boolean fetchFailed = false;
    private volatile boolean cached = false;
    // The song only has unsynced lyrics on LRCLIB, so the line times are a guess.
    private volatile boolean estimated = false;

    public static synchronized LyricsManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LyricsManager();
        }
        return INSTANCE;
    }

    public void updateTrack(TrackInfo trackInfo) {
        if (trackInfo == null || !trackInfo.isValidTrack()) {
            this.currentLyrics.clear();
            this.lastTrackKey = "";
            return;
        }
        String string = trackInfo.getTrackKey();
        if (!Objects.equals(string, this.lastTrackKey)) {
            this.lastTrackKey = string;
            this.currentLyrics.clear();
            this.loading = true;
            this.fetchFailed = false;
            this.cached = false;
            this.estimated = false;
            String string3 = string;
            LrclibClient.fetchSyncedLyrics(trackInfo.getTitle(), trackInfo.getArtist(), trackInfo.getAlbum(), (double)trackInfo.getDurationMs() / 1000.0).thenAccept(string2 -> {
                if (Objects.equals(this.lastTrackKey, string3)) {
                    this.currentLyrics.clear();
                    if (string2 != null && !string2.isBlank()) {
                        List<LyricLine> list = LrcParser.parse(string2);
                        this.currentLyrics.addAll(list);
                        this.cached = true;
                        this.estimated = LrcParser.isEstimated(string2);
                        System.out.println("[LyricsCraft] Loaded " + list.size() + (this.estimated ? " unsynced" : " synced") + " lines for: " + trackInfo.getTitle());
                    } else {
                        this.fetchFailed = true;
                    }
                    this.loading = false;
                }
            }).exceptionally(throwable -> {
                if (Objects.equals(this.lastTrackKey, string3)) {
                    this.loading = false;
                    this.fetchFailed = true;
                }
                return null;
            });
        }
    }

    public LyricLine getCurrentLine(long l) {
        if (this.currentLyrics.isEmpty()) {
            return null;
        }
        LyricLine lyricLine = null;
        for (LyricLine lyricLine2 : this.currentLyrics) {
            if (lyricLine2.timestampMs() > l) break;
            lyricLine = lyricLine2;
        }
        return lyricLine;
    }

    public LyricLine getNextLine(long l) {
        if (this.currentLyrics.isEmpty()) {
            return null;
        }
        for (LyricLine lyricLine : this.currentLyrics) {
            if (lyricLine.timestampMs() <= l) continue;
            return lyricLine;
        }
        return null;
    }

    public List<LyricLine> getUpcomingLines(long l, int n) {
        if (this.currentLyrics.isEmpty() || n <= 0) {
            return Collections.emptyList();
        }
        ArrayList<LyricLine> arrayList = new ArrayList<LyricLine>(n);
        for (LyricLine lyricLine : this.currentLyrics) {
            if (lyricLine.timestampMs() <= l) continue;
            arrayList.add(lyricLine);
            if (arrayList.size() < n) continue;
            break;
        }
        return arrayList;
    }

    public boolean hasLyrics() {
        return !this.currentLyrics.isEmpty();
    }

    public boolean isLoading() {
        return this.loading;
    }

    public boolean isFetchFailed() {
        return this.fetchFailed;
    }

    public boolean isCached() {
        return this.cached;
    }

    public boolean isEstimated() {
        return this.estimated;
    }

    public List<LyricLine> getLyrics() {
        return Collections.unmodifiableList(this.currentLyrics);
    }
}
