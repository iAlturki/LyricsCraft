package com.ialturki.lyricscraft.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", 2);
    // Enhanced LRC marks inside a line, "<00:12.34>word": when singing reaches that point. One at the very end gives the line's end.
    private static final Pattern WORD_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    /** First line of lyrics whose timing was estimated from unsynced lyrics. */
    public static final String ESTIMATED = "[lp:estimated]";

    public static boolean isEstimated(String lrc) {
        return lrc != null && lrc.startsWith(ESTIMATED);
    }

    public static List<LyricLine> parse(String string) {
        if (string == null || string.isBlank()) {
            return Collections.emptyList();
        }
        ArrayList<LyricLine> arrayList = new ArrayList<LyricLine>();
        long l = 0L;
        for (String string2 : string.split("\\r?\\n")) {
            String string3 = string2.trim();
            if (string3.isEmpty()) continue;
            Matcher matcher = OFFSET_PATTERN.matcher(string3);
            if (matcher.find()) {
                try {
                    l = Long.parseLong(matcher.group(1));
                }
                catch (NumberFormatException numberFormatException) {}
                continue;
            }
            Matcher matcher2 = TIMESTAMP_PATTERN.matcher(string3);
            ArrayList<Long> arrayList2 = new ArrayList<Long>();
            int n = 0;
            while (matcher2.find()) {
                arrayList2.add(LrcParser.millis(matcher2));
                n = matcher2.end();
            }
            if (arrayList2.isEmpty()) continue;
            // Take the word marks out of the text, remembering where each one was.
            String rest = string3.substring(n);
            StringBuilder text = new StringBuilder();
            List<Integer> offsets = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            Matcher words = WORD_PATTERN.matcher(rest);
            int last = 0;
            while (words.find()) {
                text.append(rest, last, words.start());
                offsets.add(text.length());
                times.add(LrcParser.millis(words));
                last = words.end();
            }
            text.append(rest.substring(last));
            int lead = 0;
            while (lead < text.length() && text.charAt(lead) <= ' ') lead++;
            String string5 = text.toString().trim();
            long first = arrayList2.getFirst();
            for (long l2 : arrayList2) {
                // A repeated line ("[00:10.00][01:10.00]...") moves its marks along with it.
                long shift = l2 - first + l;
                long end = -1L;
                List<Integer> inner = new ArrayList<>();
                List<Long> innerTimes = new ArrayList<>();
                for (int i = 0; i < offsets.size(); i++) {
                    int offset = Math.max(0, Math.min(string5.length(), offsets.get(i) - lead));
                    long time = Math.max(0L, times.get(i) + shift);
                    if (offset >= string5.length()) end = time;
                    else { inner.add(offset); innerTimes.add(time); }
                }
                arrayList.add(new LyricLine(Math.max(0L, l2 + l), string5, end,
                    inner.stream().mapToInt(Integer::intValue).toArray(), innerTimes.stream().mapToLong(Long::longValue).toArray()));
            }
        }
        Collections.sort(arrayList);
        return arrayList;
    }

    private static long millis(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        long fraction = 0L;
        String digits = matcher.group(3);
        if (digits != null) {
            fraction = digits.length() == 1 ? Long.parseLong(digits) * 100L : (digits.length() == 2 ? Long.parseLong(digits) * 10L : Long.parseLong(digits.substring(0, 3)));
        }
        return minutes * 60L * 1000L + seconds * 1000L + fraction;
    }
}
