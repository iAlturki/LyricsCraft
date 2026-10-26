package com.ialturki.lyricscraft.media;

import java.util.Objects;

/** A song as a media source reported it, with the playback position at the moment it was sampled. */
public class TrackInfo {
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final long positionMs;
    private final long sampledAtSystemMs;
    private final boolean isPlaying;
    private final byte[] albumArtBytes;
    private final String albumArtUrl;
    private static String lastTrackKey = "";

    public TrackInfo(String title, String artist, String album, long durationMs, long positionMs, boolean isPlaying, byte[] albumArtBytes,
                     String albumArtUrl, long sampledAtSystemMs) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.durationMs = Math.max(0L, durationMs);
        this.positionMs = Math.max(0L, positionMs);
        this.isPlaying = isPlaying;
        this.albumArtBytes = albumArtBytes;
        this.albumArtUrl = albumArtUrl;
        this.sampledAtSystemMs = sampledAtSystemMs > 0L ? sampledAtSystemMs : System.currentTimeMillis();
    }

    public String getTitle() {
        return this.title;
    }

    public String getArtist() {
        return this.artist;
    }

    public String getAlbum() {
        return this.album;
    }

    public long getDurationMs() {
        return this.durationMs;
    }

    public long getPositionMs() {
        return this.positionMs;
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public byte[] getAlbumArtBytes() {
        return this.albumArtBytes;
    }

    public String getAlbumArtUrl() {
        return this.albumArtUrl;
    }

    public long getSampledAtSystemMs() {
        return this.sampledAtSystemMs;
    }

    /**
     * Where playback is now: the sampled position plus the time since, shifted by the sync offset and the manual timeline
     * adjustment. A new song resets the manual adjustment.
     */
    public synchronized long getCurrentEstimatedPositionMs(long offsetMs) {
        String key = this.getTrackKey();
        if (!key.equals(lastTrackKey)) {
            lastTrackKey = key;
            MediaManager.resetManualSeekOffset();
        }
        long manual = MediaManager.getManualSeekOffsetMs();
        if (!this.isPlaying) {
            return Math.max(0L, this.positionMs + offsetMs + manual);
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.sampledAtSystemMs);
        long position = this.positionMs + elapsed + offsetMs + manual;
        if (this.durationMs > 0L && position > this.durationMs) {
            position = this.durationMs;
        }
        return Math.max(0L, position);
    }

    public boolean isValidTrack() {
        return !this.title.isBlank();
    }

    public String getTrackKey() {
        return (this.artist + " - " + this.title).toLowerCase().trim();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TrackInfo other)) {
            return false;
        }
        return this.durationMs == other.durationMs && Objects.equals(this.title, other.title) && Objects.equals(this.artist, other.artist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.title, this.artist, this.durationMs);
    }

    @Override
    public String toString() {
        return "TrackInfo{title='" + this.title + "', artist='" + this.artist + "', pos=" + this.positionMs + ", dur=" + this.durationMs
            + ", playing=" + this.isPlaying + "}";
    }
}
