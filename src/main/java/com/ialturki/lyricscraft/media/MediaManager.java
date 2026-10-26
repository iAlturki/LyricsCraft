package com.ialturki.lyricscraft.media;

import java.util.ArrayList;
import java.util.List;

/** Asks each media source for the playing song, first one with a song wins, and routes the media keys to it. */
public class MediaManager {
    private static MediaManager INSTANCE;
    private final List<MediaProvider> providers = new ArrayList<>();
    private TrackInfo currentTrack = null;
    private MediaProvider activeProvider = null;
    private static volatile long manualSeekOffsetMs = 0L;

    private MediaManager() {
        this.providers.add(new LocalBridgeServer());
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            this.providers.add(new WindowsMediaSessionProvider());
        }
    }

    public static synchronized MediaManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MediaManager();
        }
        return INSTANCE;
    }

    public void init() {
        for (MediaProvider provider : this.providers) {
            try {
                provider.init();
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    public void tick() {
        TrackInfo found = null;
        for (MediaProvider provider : this.providers) {
            try {
                TrackInfo track = provider.pollTrack();
                if (track == null || !track.isValidTrack()) continue;
                found = track;
                this.activeProvider = provider;
                break;
            }
            catch (Exception ignored) {
            }
        }
        this.currentTrack = found;
    }

    public TrackInfo getCurrentTrack() {
        return this.currentTrack;
    }

    public void skipNext() {
        if (this.activeProvider != null) {
            this.activeProvider.skipNext();
        }
    }

    public void skipPrevious() {
        if (this.activeProvider != null) {
            this.activeProvider.skipPrevious();
        }
    }

    public void togglePlayPause() {
        if (this.activeProvider != null) {
            this.activeProvider.togglePlayPause();
        }
    }

    public void seekRelative(long offsetMs) {
        manualSeekOffsetMs += offsetMs;
    }

    public static long getManualSeekOffsetMs() {
        return manualSeekOffsetMs;
    }

    public static void resetManualSeekOffset() {
        manualSeekOffsetMs = 0L;
    }

    public void close() {
        for (MediaProvider provider : this.providers) {
            try {
                provider.close();
            }
            catch (Exception ignored) {
            }
        }
    }
}
