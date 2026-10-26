package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.lyrics.TextNormalizer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Song titles and lyrics in any script, laid out for Minecraft's font. Minecraft only reorders right-to-left text and
 * shapes Arabic letters when it draws formatted text, so plain strings from Arabic, Persian or Hebrew songs would come
 * out backwards and unjoined in an English game.
 */
public final class BidiText {
    // A zero-width glyph with a -1 advance from assets/lyricscraft/font/lyrics.json. Minecraft's font leaves a one-pixel
    // gap after every glyph, which breaks the baseline that joins Arabic letters; this closes it between joined letters.
    private static final int PULL = 0xE7F0;
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lyricscraft", "lyrics"));
    // Shaped Arabic presentation forms that connect to the glyph drawn to their left or right.
    private static final BitSet JOINS_LEFT = new BitSet(), JOINS_RIGHT = new BitSet();

    static {
        JOINS_LEFT.set(0x0640);
        JOINS_RIGHT.set(0x0640);
        for (int c = 0xFB50; c <= 0xFEFC; c++) {
            String name = Character.getName(c);
            if (name == null) continue;
            if (name.endsWith("MEDIAL FORM")) { JOINS_LEFT.set(c); JOINS_RIGHT.set(c); }
            else if (name.endsWith("INITIAL FORM")) JOINS_LEFT.set(c);
            else if (name.endsWith("FINAL FORM")) JOINS_RIGHT.set(c);
        }
    }

    private BidiText() {}

    public static FormattedCharSequence visual(String text, Style style) {
        return join(Language.getInstance().getVisualOrder(FormattedText.of(TextNormalizer.forDisplay(text), styled(style))));
    }

    /** Like {@link #visual}, with the first {@code sung} characters of the text in {@code done} and the rest in {@code rest}. */
    public static FormattedCharSequence visual(String text, int sung, Style done, Style rest) {
        return join(Language.getInstance().getVisualOrder(swept(text, sung, done, rest)));
    }

    public static List<FormattedCharSequence> split(Font font, String text, Style style, int width) {
        return split(font, text, text.length(), style, style, width);
    }

    public static List<FormattedCharSequence> split(Font font, String text, int sung, Style done, Style rest, int width) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (FormattedCharSequence line : font.split(swept(text, sung, done, rest), width))
            lines.add(join(line));
        return lines;
    }

    // Shaping and reordering run over both parts together, so letters stay joined across the colour change.
    private static FormattedText swept(String text, int sung, Style done, Style rest) {
        int at = Math.max(0, Math.min(text.length(), sung));
        if (at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1))) at--;
        if (at >= text.length()) return FormattedText.of(TextNormalizer.forDisplay(text), styled(done));
        return FormattedText.composite(FormattedText.of(TextNormalizer.forDisplay(text.substring(0, at)), styled(done)),
            FormattedText.of(TextNormalizer.forDisplay(text.substring(at)), styled(rest)));
    }

    /** Like {@link #visual}, cut at the logical end with {@code ellipsis} so it fits in {@code width}. */
    public static FormattedCharSequence fit(Font font, String text, Style style, int width, String ellipsis) {
        String clean = TextNormalizer.forDisplay(text);
        FormattedCharSequence line = visual(clean, style);
        if (font.width(line) <= width) return line;
        return visual(font.plainSubstrByWidth(clean, Math.max(0, width - font.width(ellipsis))).trim() + ellipsis, style);
    }

    /** Whether the first letter with a direction reads right to left. */
    public static boolean isRightToLeft(String text) {
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            byte direction = Character.getDirectionality(c);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
            i += Character.charCount(c);
        }
        return false;
    }

    private static boolean fontLoaded() {
        return Minecraft.getInstance().font.width(FormattedCharSequence.codepoint(PULL, Style.EMPTY.withFont(FONT))) < 0;
    }

    // Without the font (a failed resource reload) the text keeps the default font and the gaps.
    private static Style styled(Style style) {
        return fontLoaded() ? style.withFont(FONT) : style;
    }

    private static FormattedCharSequence join(FormattedCharSequence line) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        line.accept((position, style, codepoint) -> {
            codepoints.add(codepoint);
            styles.add(style);
            return true;
        });
        boolean pull = fontLoaded();
        return sink -> {
            for (int i = 0; i < codepoints.size(); i++) {
                int c = codepoints.getInt(i);
                if (!sink.accept(i, styles.get(i), c)) return false;
                // Bold adds one pixel to every advance, so the pull glyph is never bold.
                if (pull && i + 1 < codepoints.size() && JOINS_RIGHT.get(c) && JOINS_LEFT.get(codepoints.getInt(i + 1))
                    && !sink.accept(i, styles.get(i).withBold(false), PULL)) return false;
            }
            return true;
        };
    }
}
