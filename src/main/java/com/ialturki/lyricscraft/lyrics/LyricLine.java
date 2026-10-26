package com.ialturki.lyricscraft.lyrics;

import java.util.Arrays;

/**
 * One timed lyric line. syncOffsets and syncTimes mark where in the text singing reaches at a given time: word starts
 * when the lyrics are word-synced, and the line end at text.length() when it is known. endMs is -1 when unknown.
 */
public record LyricLine(long timestampMs, String text, long endMs, int[] syncOffsets, long[] syncTimes) implements Comparable<LyricLine>
{
    public LyricLine(long timestampMs, String text) {
        this(timestampMs, text, -1L, new int[0], new long[0]);
    }

    /** Whether the lyrics give the time of each word rather than only of the line. */
    public boolean wordSynced() {
        for (int offset : this.syncOffsets) {
            if (offset > 0 && offset < this.text.length()) return true;
        }
        return false;
    }

    /** How many characters of the text have been sung at positionMs; nextStartMs bounds the line when its end is unknown. */
    public int sungChars(long positionMs, long nextStartMs) {
        int length = this.text.length();
        if (length == 0 || positionMs <= this.timestampMs) return 0;
        int[] offsets = this.anchorOffsets();
        long[] times = this.anchorTimes(nextStartMs);
        if (positionMs >= times[times.length - 1]) return length;
        for (int i = 0; i + 1 < times.length; i++) {
            if (positionMs >= times[i + 1]) continue;
            long span = Math.max(1L, times[i + 1] - times[i]);
            return offsets[i] + (int)((offsets[i + 1] - offsets[i]) * Math.max(0L, positionMs - times[i]) / span);
        }
        return length;
    }

    /** When singing reaches the character at offset: the inverse of sungChars. */
    public long timeAt(int offset, long nextStartMs) {
        int[] offsets = this.anchorOffsets();
        long[] times = this.anchorTimes(nextStartMs);
        for (int i = 0; i + 1 < offsets.length; i++) {
            if (offset > offsets[i + 1]) continue;
            int span = Math.max(1, offsets[i + 1] - offsets[i]);
            return times[i] + (times[i + 1] - times[i]) * Math.max(0, offset - offsets[i]) / span;
        }
        return times[times.length - 1];
    }

    // Line start, the marks inside the line, and the line end, in order.
    private int[] anchorOffsets() {
        int[] offsets = new int[this.syncOffsets.length + 2];
        offsets[0] = 0;
        int count = 1;
        for (int offset : this.syncOffsets) {
            if (offset > 0 && offset < this.text.length()) offsets[count++] = offset;
        }
        offsets[count++] = this.text.length();
        return Arrays.copyOf(offsets, count);
    }

    private long[] anchorTimes(long nextStartMs) {
        long[] times = new long[this.syncOffsets.length + 2];
        long start = this.timestampMs;
        for (int i = 0; i < this.syncOffsets.length; i++) {
            if (this.syncOffsets[i] == 0) start = Math.max(start, this.syncTimes[i]);
        }
        times[0] = start;
        int count = 1;
        for (int i = 0; i < this.syncOffsets.length; i++) {
            if (this.syncOffsets[i] <= 0 || this.syncOffsets[i] >= this.text.length()) continue;
            times[count] = Math.max(times[count - 1], this.syncTimes[i]);
            count++;
        }
        times[count] = Math.max(times[count - 1] + 1L, this.end(start, nextStartMs));
        return Arrays.copyOf(times, count + 1);
    }

    private long end(long start, long nextStartMs) {
        if (this.endMs > start) return this.endMs;
        // Without an end time, a line is sung at roughly 7 characters a second and finishes a little before the next one.
        long gap = nextStartMs > start ? nextStartMs - start : 4000L;
        return start + Math.min(gap - Math.min(300L, gap / 5L), Math.max(1500L, this.text.length() * 140L));
    }

    @Override
    public int compareTo(LyricLine lyricLine) {
        return Long.compare(this.timestampMs, lyricLine.timestampMs);
    }
}
