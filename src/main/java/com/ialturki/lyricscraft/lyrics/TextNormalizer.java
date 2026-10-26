package com.ialturki.lyricscraft.lyrics;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Cleans song text in any script so Minecraft's font can draw it and so it matches LRCLIB entries. */
public final class TextNormalizer {
    // Direction marks, joiners and emoji variation selectors are invisible elsewhere but Minecraft's font draws them as boxes.
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200D-\\u200F\\u061C\\u202A-\\u202E\\u2066-\\u2069\\uFE0E\\uFE0F\\uFEFF]");
    // Arabic and Hebrew vowel marks: Minecraft gives each mark its own advance, which splits words apart.
    private static final Pattern VOWEL_MARKS = Pattern.compile(
        "[\\u0591-\\u05BD\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06DC\\u06DF-\\u06E4\\u06E7\\u06E8\\u06EA-\\u06ED\\u08D3-\\u08FF]");
    private static final Pattern APOSTROPHES = Pattern.compile("['\\u2019`\\u00B4]");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern WORD_END_VOWEL_H = Pattern.compile("(?:[\\u0647\\u0629]|(?<=[aeiou])h)(?!\\p{L})");
    private static final String[][] DIGRAPHS ={{"kh", "X"}, {"sh", "C"}, {"ch", "C"}, {"gh", "G"}, {"th", "T"}, {"dh", "D"}, {"ph", "F"}};
    // Consonant class of each Latin letter, Arabizi digit and Arabic letter. Vowels, alef, waw, yeh, ain and hamza drop out.
    private static final char[] CONSONANTS = new char[0x0700];

    static {
        String[][] classes = {
            {"b", "bpبپ"}, {"t", "tT6تطث"}, {"j", "jgجگ"}, {"g", "G8غ"}, {"h", "h7حه"}, {"x", "xX5خ"}, {"d", "dDدضذ"},
            {"r", "rر"}, {"z", "zزظژ"}, {"s", "s9سص"}, {"c", "Cشچ"}, {"f", "fvFفڤ"}, {"k", "kqcقكک"}, {"l", "lل"}, {"m", "mم"}, {"n", "nن"}};
        for (String[] group : classes)
            for (char c : group[1].toCharArray()) CONSONANTS[c] = group[0].charAt(0);
    }

    private TextNormalizer() {}

    /**
     * Consonant outline shared by Arabic script and its Latin spellings, so "تملي معاك" and "Tamally Maak" are both
     * "tmlmk" and "حسين الجسمي" and "Hussain Aljassmi" are both "hsnljsm".
     */
    public static String skeleton(String text) {
        String result = forDisplay(text).toLowerCase(Locale.ROOT);
        // A word-final heh or ta marbuta is mostly the vowel "a" ("هبه" is "Heba"), as is a Latin "h" after a vowel ("Allah").
        result = WORD_END_VOWEL_H.matcher(result).replaceAll("");
        for (String[] digraph : DIGRAPHS) result = result.replace(digraph[0], digraph[1]);
        StringBuilder outline = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            char consonant = c < CONSONANTS.length ? CONSONANTS[c] : 0;
            // Doubled letters and Arabic shadda both collapse to one consonant.
            if (consonant != 0 && (outline.isEmpty() || outline.charAt(outline.length() - 1) != consonant)) outline.append(consonant);
        }
        return outline.toString();
    }

    public static String forDisplay(String text) {
        if (text == null) return "";
        // Compose first so decomposed hamza and madda sequences become letters instead of being stripped as marks.
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);
        result = INVISIBLE.matcher(result).replaceAll("");
        return VOWEL_MARKS.matcher(result).replaceAll("");
    }

    /** Spelling-insensitive key: case, spacing, punctuation, tatweel and the common Arabic letter variants are folded together. */
    public static String forMatching(String text) {
        String result = Normalizer.normalize(forDisplay(text), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace("ـ", "");
        result = result.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي');
        return SEPARATORS.matcher(APOSTROPHES.matcher(result).replaceAll("")).replaceAll("");
    }
}
