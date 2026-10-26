package com.ialturki.lyricscraft.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads LRCLIB's Lyricsfile (a YAML document) into enhanced LRC: "[00:12.340]<00:12.340>word <00:12.800>word<00:14.000>",
 * with word times when it is word-synced and the line end when it is known.
 */
final class Lyricsfile {
    private static final class Part {
        String text = "";
        long start = -1L, end = -1L;
        final List<Part> words = new ArrayList<>();
    }

    private Lyricsfile() {}

    /** Enhanced LRC for the document, or null when it has no timed lines. */
    static String toLrc(String yaml) {
        List<Part> lines = new ArrayList<>();
        Part line = null, word = null;
        int lineIndent = -1;
        boolean inLines = false;
        for (String raw : yaml.split("\\r?\\n")) {
            if (raw.isBlank()) continue;
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
            String content = raw.substring(indent);
            // Top-level keys: only the "lines:" list matters; "metadata:" and the "plain:" block are skipped.
            if (indent == 0 && !content.startsWith("- ")) {
                inLines = content.startsWith("lines:");
                continue;
            }
            if (!inLines) continue;
            boolean item = content.startsWith("- ");
            if (item) content = content.substring(2);
            int keyIndent = item ? indent + 2 : indent;
            if (item) {
                // The first list item sets the line indent; items deeper than that are the words of the current line.
                if (lineIndent < 0 || indent <= lineIndent) {
                    lineIndent = indent;
                    line = new Part();
                    lines.add(line);
                    word = null;
                } else if (line != null) {
                    word = new Part();
                    line.words.add(word);
                }
            }
            if (line == null) continue;
            int colon = content.indexOf(':');
            if (colon < 0) continue;
            String key = content.substring(0, colon).trim();
            String value = content.substring(colon + 1).trim();
            Part target = word != null && keyIndent > lineIndent + 2 ? word : line;
            switch (key) {
                case "text" -> target.text = Lyricsfile.scalar(value);
                case "start_ms" -> target.start = Lyricsfile.number(value);
                case "end_ms" -> target.end = Lyricsfile.number(value);
                default -> {}
            }
        }
        StringBuilder lrc = new StringBuilder();
        for (Part part : lines) {
            if (part.start < 0L) continue;
            lrc.append('[').append(Lyricsfile.stamp(part.start)).append(']');
            boolean words = !part.words.isEmpty() && part.words.stream().allMatch(w -> w.start >= 0L);
            if (words) {
                for (Part w : part.words) lrc.append('<').append(Lyricsfile.stamp(w.start)).append('>').append(w.text);
            } else {
                lrc.append(part.text);
            }
            if (part.end > part.start) lrc.append('<').append(Lyricsfile.stamp(part.end)).append('>');
            lrc.append('\n');
        }
        return lrc.isEmpty() ? null : lrc.toString();
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    // Plain, "double-quoted" (with escapes) or 'single-quoted' YAML scalars.
    private static String scalar(String value) {
        if (value.startsWith("\"")) {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') break;
                if (c != '\\' || i + 1 >= value.length()) {
                    text.append(c);
                    continue;
                }
                char escaped = value.charAt(++i);
                switch (escaped) {
                    case 'n' -> text.append('\n');
                    case 't' -> text.append('\t');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            text.append((char)Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> text.append(escaped);
                }
            }
            return text.toString();
        }
        if (value.startsWith("'")) {
            int close = value.lastIndexOf('\'');
            return (close > 0 ? value.substring(1, close) : value.substring(1)).replace("''", "'");
        }
        return value;
    }

    // Locale.ROOT: an Arabic Windows locale would otherwise write Arabic-Indic digits the parser cannot read.
    static String stamp(long millis) {
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60000L, millis / 1000L % 60L, millis % 1000L);
    }
}
