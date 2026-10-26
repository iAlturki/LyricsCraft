package com.ialturki.lyricscraft.media;

/** A source of the song that is playing, and the controls for it. */
public interface MediaProvider {
    void init();

    /** The current song, or null when nothing is playing. */
    TrackInfo pollTrack();

    void skipNext();

    void skipPrevious();

    void togglePlayPause();

    void close();
}
