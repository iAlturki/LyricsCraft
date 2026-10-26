package com.ialturki.lyricscraft.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public class LrclibClient {
    private static final String API_BASE = "https://lrclib.net/api";
    // LRCLIB asks clients to name themselves and link their homepage.
    private static final String USER_AGENT = "LyricsCraft (https://github.com/iAlturki/LyricsCraft)";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();
    private static final File CACHE_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lyricscraft/cache");
    private static final String NOISE = "official|video|audio|lyrics|hd|4k|remix|feat|ft\\.|فيديو|كليب|كلمات|حصري|اوديو|أوديو|ريمكس|لايف|مترجم";
    // Brackets naming the upload rather than the song: "(Official Video)", "(فيديو كليب)", "[كلمات]", "(حصريا)", "(مترجمة)".
    private static final Pattern CLEAN_PATTERN = Pattern.compile("\\(.*?(" + NOISE + ").*?\\)|\\[.*?(" + NOISE + ").*?\\]|\\|.*$", Pattern.CASE_INSENSITIVE);
    // Words that make up a whole "- كلمات" or "- Official Lyric Video" part of an upload title.
    private static final Set<String> NOISE_WORDS = Set.of("official", "video", "audio", "lyric", "lyrics", "music", "clip", "hd", "4k", "mv",
        "visualizer", "exclusive", "new", "version");
    private static final Pattern NOISE_ARABIC = Pattern.compile("فيديو|كليب|كلمات|حصري|اوديو|أوديو|لايف|مترجم|جديد|اغنية|أغنية|رسمي");
    private static final Pattern SYMBOLS = Pattern.compile("\\p{So}");
    private static final Pattern TIMESTAMPS = Pattern.compile("\\[[^\\]]*\\]");
    // The length of the LRCLIB entry, recorded at the end of cached lyrics.
    private static final Pattern LENGTH_TAG = Pattern.compile("^\\[lp:length=(\\d+)\\]\\s*$", Pattern.MULTILINE);
    // A media session that reports no length is given exactly three minutes, which says nothing about the song.
    private static final double UNKNOWN_DURATION = 180.0;
    // LRCLIB keeps many versions of a song whose lengths differ by their intros, and a version that much longer or
    // shorter than the one playing runs early or late throughout.
    private static final double CLOSE_ENOUGH = 8.0;
    private static final double CACHE_MISMATCH = 15.0;
    // LRCLIB's own match for a title and artist outranks a search hit of the same version.
    private static final double EXACT_BONUS = 100.0;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    // Lyrics guessed from unsynced text are looked up again after this long, in case synced lyrics were added.
    private static final long ESTIMATE_CACHE_MS = 3L * DAY_MS;
    // A song LRCLIB did not have is not looked up again for a while, so skipping back to it does not wait on LRCLIB.
    private static final long MISS_MEMORY_MS = 20L * 60L * 1000L;
    private static final Map<String, Long> MISSES = new ConcurrentHashMap<>();

    // Outlines are TextNormalizer.skeleton forms, which match Arabic script against Latin spellings.
    private record Query(List<String> titles, List<String> artists, List<String> titleOutlines, List<String> artistOutlines,
                         String albumOutline, double duration) {}
    // A reading of an upload title: "Hamaki - Ya Sattar" is the song "Ya Sattar" by "Hamaki".
    private record Guess(String title, String artist) {}
    // One LRCLIB entry and how it was found: by an exact lookup, or by a search under the title-only rules or not.
    private record Candidate(JsonObject result, boolean exact, boolean titleOnly) {}

    // title and artist: 2 when equal, 1 when one contains the other (the artist also by outline). soundsLike: the title
    // outline matches, as "Tamally maak" does for "تملي معاك". inLyrics: the title is sung in the lyrics. durationDiff is
    // negative when either length is unknown.
    private record Match(int title, int artist, boolean soundsLike, boolean inLyrics, boolean distinctive, double durationDiff, double score) {
        boolean accepted(boolean titleOnly) {
            boolean sameLength = this.durationDiff >= 0.0 && this.durationDiff < 4.0;
            boolean likely = (this.soundsLike || this.inLyrics) && (this.artist > 0 || sameLength);
            // The artist alone would accept another song by them.
            if (!titleOnly) return this.title > 0 || this.artist > 0 && sameLength || likely;
            // Without the artist in the query the title has to carry the match. Common titles like "حبيبي" need a length,
            // and music videos often run longer than the track.
            boolean exact = this.title == 2 && (this.durationDiff >= 0.0 ? this.durationDiff <= 30.0 : this.distinctive);
            return exact || this.title > 0 && (this.artist > 0 || sameLength) || likely;
        }
    }

    // Everything the requests of one lookup found, and album names learned along the way.
    private static final class Lookup {
        final Query query;
        final List<Candidate> pool = new CopyOnWriteArrayList<>();
        final Set<String> albums = Collections.synchronizedSet(new LinkedHashSet<>());

        Lookup(Query query) {
            this.query = query;
        }
    }

    public static CompletableFuture<String> fetchSyncedLyrics(String title, String artist, String album, double duration) {
        if (title == null || title.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String track = LrclibClient.cleanTitle(title);
        String singer = LrclibClient.cleanTitle(artist);
        if (singer.isBlank() && track.contains(" - ")) {
            String[] parts = track.split(" - ", 2);
            singer = parts[0].trim();
            track = parts[1].trim();
        }
        boolean known = duration > 0.0 && Math.abs(duration - UNKNOWN_DURATION) > 0.001;
        String key = LrclibClient.cacheFileName(singer, track);
        File cache = new File(CACHE_DIR, key);
        String cached = LrclibClient.readCache(cache);
        if (cached != null && LrclibClient.fresh(cached, cache, known ? duration : 0.0)) {
            return CompletableFuture.completedFuture(cached);
        }
        Long missed = MISSES.get(key);
        if (missed != null && System.currentTimeMillis() - missed < MISS_MEMORY_MS) {
            return CompletableFuture.completedFuture(cached);
        }
        List<Guess> guesses = LrclibClient.guesses(track, singer);
        Lookup lookup = new Lookup(LrclibClient.query(track, singer, guesses, album, duration));
        String name = track;
        String by = singer;
        // The exact lookups, asking for the version of this length, and the combined search run together; most songs
        // are settled there.
        List<CompletableFuture<Void>> first = new ArrayList<>();
        first.add(LrclibClient.exactGet(name, by, lookup));
        // YouTube uploads read "Artist - Title", often in two languages, with the channel as the artist.
        for (Guess guess : guesses) {
            if (!guess.title.equals(name) || !guess.artist.equals(by)) first.add(LrclibClient.exactGet(guess.title, guess.artist, lookup));
        }
        first.add(LrclibClient.search(by.isBlank() ? name : name + " " + by, lookup, false));
        // YouTube Music has timed lyrics for many songs LRCLIB lacks ("حي الدلايل" by Shela), and Arabic-script ones where
        // LRCLIB only has a transliteration. It is asked at the same time, so a song LRCLIB does not have is not kept
        // waiting until LRCLIB gives up.
        Guess reading = guesses.isEmpty() ? new Guess(name, by) : guesses.getFirst();
        CompletableFuture<JsonObject> youtube = YouTubeMusicLyrics.searchSongs((reading.title + " " + reading.artist).trim())
            .thenCompose(songs -> {
                JsonObject song = LrclibClient.bestSong(songs, lookup.query);
                return song == null ? CompletableFuture.<JsonObject>completedFuture(null) : YouTubeMusicLyrics.lyrics(song);
            })
            .exceptionally(throwable -> null);
        return LrclibClient.all(first).thenCompose(ignored -> {
            Candidate best = LrclibClient.choose(lookup, true);
            if (best != null && LrclibClient.lengthGap(best.result, lookup) <= CLOSE_ENOUGH) {
                // An Arabic song LRCLIB only has transliterated ("Tamally maak" for "تملي معاك") takes YouTube Music's
                // Arabic-script lyrics when they are of the same length.
                if (!LrclibClient.mostlyArabic(name + by) || LrclibClient.mostlyArabic(LrclibClient.string(best.result, "syncedLyrics"))) {
                    return CompletableFuture.completedFuture(best.result);
                }
                return youtube.thenApply(found -> found != null && LrclibClient.lengthGap(found, lookup) <= CLOSE_ENOUGH
                    && LrclibClient.mostlyArabic(LrclibClient.string(found, "syncedLyrics")) ? found : best.result);
            }
            return youtube.thenCompose(found -> {
                if (found != null && found.has("syncedLyrics") && LrclibClient.lengthGap(found, lookup) <= CLOSE_ENOUGH) {
                    return CompletableFuture.completedFuture(found);
                }
                return LrclibClient.moreSearches(lookup, name, by, guesses).thenApply(more -> {
                    // A version of another length from either source, the closer one first; LRCLIB on a tie.
                    boolean timed = found != null && found.has("syncedLyrics");
                    if (more != null && (!timed || LrclibClient.lengthGap(more.result, lookup) <= LrclibClient.lengthGap(found, lookup))) return more.result;
                    if (timed) return found;
                    Candidate plain = LrclibClient.choose(lookup, false);
                    if (plain != null) return plain.result;
                    return found;
                });
            });
        }).thenApply(result -> {
            if (result == null) return null;
            if (!LrclibClient.string(result, "syncedLyrics").isBlank()) return LrclibClient.withLength(LrclibClient.lyricsOf(result), result);
            return LrclibClient.withLength(LrclibClient.estimate(result, lookup.query.duration), result);
        }).thenApply(lrc -> {
            if (lrc == null) {
                MISSES.put(key, System.currentTimeMillis());
                return cached;
            }
            LrclibClient.saveToCache(cache, lrc);
            return lrc;
        }).exceptionally(throwable -> cached);
    }

    // The YouTube Music song that is ours: matched on title, artist and length like an LRCLIB entry.
    private static JsonObject bestSong(List<JsonObject> songs, Query query) {
        JsonObject best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (JsonObject song : songs) {
            Match match = LrclibClient.match(song, "", query);
            if (!match.accepted(false) || !(match.score > bestScore)) continue;
            bestScore = match.score;
            best = song;
        }
        return best;
    }

    // Title and artist searches, and the album search after them, for songs the first round did not settle.
    private static CompletableFuture<Candidate> moreSearches(Lookup lookup, String name, String by, List<Guess> guesses) {
        // Arabic songs are often listed with the artist in another script ("بشرة خير" by "Hussain Aljassmi"), and LRCLIB
        // cannot find "صعبان عليا" when it only knows "Saaban alaya" but does list the artist's songs. These searches
        // also bring more versions of a song when the first ones had another length.
        Set<String> texts = new LinkedHashSet<>();
        if (!by.isBlank()) texts.add(name);
        for (Guess guess : guesses) texts.add(guess.title);
        if (!by.isBlank()) texts.add(by);
        for (Guess guess : guesses) texts.add(guess.artist);
        texts.remove(by.isBlank() ? name : name + " " + by);
        List<CompletableFuture<Void>> second = new ArrayList<>();
        for (String text : texts) {
            if (second.size() < 4) second.add(LrclibClient.search(text, lookup, true));
        }
        return LrclibClient.all(second).thenCompose(ignored -> {
            Candidate again = LrclibClient.choose(lookup, true);
            if (again != null || lookup.albums.isEmpty()) {
                return CompletableFuture.completedFuture(again);
            }
            // The artist's songs can name the album in Latin letters ("Hob Aldaarwish" for "حب الدراويش"), and the album
            // lists songs the Arabic title cannot reach ("Tannesh" for "طنش").
            String albumName;
            synchronized (lookup.albums) {
                albumName = lookup.albums.iterator().next();
            }
            return LrclibClient.search(albumName, lookup, true).thenApply(ignored2 -> LrclibClient.choose(lookup, true));
        });
    }

    private static CompletableFuture<Void> all(List<CompletableFuture<Void>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // Cached lyrics are used unless they are an old estimate, or were saved for a version of another length, or before
    // lengths were recorded: those are looked up again so a closer version can replace them.
    private static boolean fresh(String cached, File file, double duration) {
        long age = System.currentTimeMillis() - file.lastModified();
        if (LrcParser.isEstimated(cached)) return age < ESTIMATE_CACHE_MS;
        if (duration <= 0.0) return true;
        Matcher length = LENGTH_TAG.matcher(cached);
        if (!length.find()) return false;
        double seconds = Double.parseDouble(length.group(1));
        return seconds <= 0.0 || Math.abs(seconds - duration) <= CACHE_MISMATCH || age < DAY_MS;
    }

    private static String readCache(File file) {
        if (!file.exists()) return null;
        try {
            String cached = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return cached.isBlank() ? null : cached;
        }
        catch (IOException exception) {
            return null;
        }
    }

    private static String withLength(String lrc, JsonObject result) {
        if (lrc == null) return null;
        long seconds = Math.round(LrclibClient.length(result));
        String source = result.has("videoId") ? "YouTube Music" : "LRCLIB";
        return (lrc.endsWith("\n") ? lrc : lrc + "\n") + "[lp:length=" + seconds + "]\n[lp:source=" + source + "]\n";
    }

    // The best entry with synced lyrics, or with only unsynced ones.
    private static Candidate choose(Lookup lookup, boolean synced) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : lookup.pool) {
            String text = LrclibClient.string(candidate.result, synced ? "syncedLyrics" : "plainLyrics");
            if (text.isBlank() || !synced && !LrclibClient.string(candidate.result, "syncedLyrics").isBlank()) continue;
            Match match = LrclibClient.match(candidate.result, text, lookup.query);
            if (!candidate.exact && !match.accepted(candidate.titleOnly)) continue;
            double score = match.score + (candidate.exact ? EXACT_BONUS : 0.0);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static double lengthGap(JsonObject result, Lookup lookup) {
        double length = LrclibClient.length(result);
        return lookup.query.duration > 0.0 && length > 0.0 ? Math.abs(length - lookup.query.duration) : 0.0;
    }

    private static double length(JsonObject result) {
        return result.has("duration") && !result.get("duration").isJsonNull() ? result.get("duration").getAsDouble() : 0.0;
    }

    // "Hamaki - Ya Sattar / حماقي - يا ستار - كلمات" gives ("Ya Sattar", "Hamaki") and ("يا ستار", "حماقي").
    private static List<Guess> guesses(String track, String singer) {
        List<Guess> guesses = new ArrayList<>();
        for (String part : track.split("\\s+/\\s+")) {
            List<String> segments = new ArrayList<>();
            for (String segment : part.split("\\s+[-\u2013\u2014]\\s+")) {
                if (!segment.isBlank() && !LrclibClient.noise(segment)) segments.add(segment.trim());
            }
            if (segments.isEmpty()) continue;
            Guess guess = segments.size() == 1 ? new Guess(segments.getFirst(), singer)
                : new Guess(String.join(" - ", segments.subList(1, segments.size())), segments.getFirst());
            if (!guesses.contains(guess)) guesses.add(guess);
            if (guesses.size() == 2) break;
        }
        return guesses;
    }

    // Whether a title part only names the upload: "كلمات", "Official Lyric Video", "فيديو كليب حصريا".
    private static boolean noise(String segment) {
        for (String word : segment.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !NOISE_WORDS.contains(word) && !NOISE_ARABIC.matcher(word).find()) return false;
        }
        return true;
    }

    private static Query query(String track, String singer, List<Guess> guesses, String album, double duration) {
        List<String> titles = new ArrayList<>(List.of(track));
        List<String> artists = new ArrayList<>(List.of(singer));
        // Uploads titled "Artist - Title" match on either half.
        if (track.contains(" - ")) {
            for (String part : track.split(" - ", 2)) {
                titles.add(part);
                artists.add(part);
            }
        }
        for (Guess guess : guesses) {
            titles.add(guess.title);
            artists.add(guess.artist);
        }
        boolean known = duration > 0.0 && Math.abs(duration - UNKNOWN_DURATION) > 0.001;
        return new Query(LrclibClient.keys(titles, false), LrclibClient.keys(artists, false), LrclibClient.keys(titles, true),
            LrclibClient.keys(artists, true), TextNormalizer.skeleton(LrclibClient.cleanTitle(album)), known ? duration : 0.0);
    }

    private static List<String> keys(List<String> texts, boolean outline) {
        List<String> keys = new ArrayList<>();
        for (String text : texts) {
            String key = outline ? TextNormalizer.skeleton(text) : TextNormalizer.forMatching(text);
            if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    // With the song's length LRCLIB answers with the version within two seconds of it, when it has one.
    private static CompletableFuture<Void> exactGet(String track, String artist, Lookup lookup) {
        StringBuilder url = new StringBuilder(API_BASE).append("/get?track_name=").append(URLEncoder.encode(track, StandardCharsets.UTF_8));
        if (!artist.isBlank()) {
            url.append("&artist_name=").append(URLEncoder.encode(artist, StandardCharsets.UTF_8));
        }
        if (lookup.query.duration > 0.0) {
            url.append("&duration=").append(Math.round(lookup.query.duration));
        }
        return LrclibClient.request(url.toString(), true).thenAccept(body -> {
            if (body == null) return;
            try {
                lookup.pool.add(new Candidate(JsonParser.parseString(body).getAsJsonObject(), true, false));
            }
            catch (RuntimeException ignored) {
            }
        });
    }

    private static CompletableFuture<Void> search(String text, Lookup lookup, boolean titleOnly) {
        String url = API_BASE + "/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        return LrclibClient.request(url, true).thenAccept(body -> {
            if (body == null) return;
            try {
                for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject candidate = element.getAsJsonObject();
                    LrclibClient.learnAlbum(candidate, lookup);
                    lookup.pool.add(new Candidate(candidate, false, titleOnly));
                }
            }
            catch (RuntimeException ignored) {
            }
        });
    }

    // Album names that match ours by outline but are spelled differently, for the album search.
    private static void learnAlbum(JsonObject candidate, Lookup lookup) {
        String album = LrclibClient.string(candidate, "albumName").trim();
        String outline = TextNormalizer.skeleton(album);
        if (outline.length() >= 4 && outline.equals(lookup.query.albumOutline)) lookup.albums.add(album);
    }

    // Word times and line ends come from the Lyricsfile when LRCLIB has one; otherwise the LRC lines are used as they are.
    private static String lyricsOf(JsonObject result) {
        String synced = LrclibClient.string(result, "syncedLyrics");
        if (synced.isBlank()) return null;
        String file = LrclibClient.string(result, "lyricsfile");
        if (!file.isBlank()) {
            try {
                String converted = Lyricsfile.toLrc(file);
                if (converted != null) return converted;
            }
            catch (RuntimeException ignored) {
            }
        }
        return synced;
    }

    /**
     * When LRCLIB only has unsynced lyrics, they are spread over the song: the first line starts after an intro, each line
     * takes time in proportion to its length, and a blank line between verses is a short pause. The result is marked as
     * estimated so it is shown as such.
     */
    private static String estimate(JsonObject result, double duration) {
        String plain = LrclibClient.string(result, "plainLyrics");
        double listed = LrclibClient.length(result);
        double seconds = listed > 30.0 ? listed : duration > 30.0 ? duration : 210.0;
        double total = seconds * 1000.0;
        double intro = Math.min(15000.0, total * 0.08);
        double outro = total * 0.05;
        String[] rows = plain.split("\\r?\\n");
        double weights = 0.0;
        for (String row : rows) weights += LrclibClient.weight(row);
        if (weights <= 0.0) return null;
        double perWeight = (total - intro - outro) / weights;
        StringBuilder lrc = new StringBuilder(LrcParser.ESTIMATED).append('\n');
        double time = intro;
        for (String row : rows) {
            if (!row.isBlank()) lrc.append('[').append(Lyricsfile.stamp((long)time)).append(']').append(row.trim()).append('\n');
            time += LrclibClient.weight(row) * perWeight;
        }
        return lrc.toString();
    }

    private static double weight(String row) {
        return row.isBlank() ? 0.8 : 1.0 + row.trim().length() / 25.0;
    }

    private static Match match(JsonObject candidate, String lyrics, Query query) {
        String trackName = LrclibClient.string(candidate, "trackName");
        String artistName = LrclibClient.string(candidate, "artistName");
        String name = TextNormalizer.forMatching(trackName);
        String artist = TextNormalizer.forMatching(artistName);
        String nameOutline = TextNormalizer.skeleton(trackName);
        String artistOutline = TextNormalizer.skeleton(artistName);
        double length = LrclibClient.length(candidate);
        double diff = query.duration > 0.0 && length > 0.0 ? Math.abs(length - query.duration) : -1.0;
        String sung = TextNormalizer.forMatching(TIMESTAMPS.matcher(lyrics).replaceAll(" "));
        boolean inLyrics = query.titles.stream().anyMatch(title -> title.length() >= 6 && sung.contains(title));
        boolean distinctive = name.length() >= 10;
        // Many Arabic songs are on LRCLIB both in Arabic script and transliterated; the Arabic copy wins close calls.
        double script = LrclibClient.mostlyArabic(sung) ? 40.0 : 0.0;
        Match direct = LrclibClient.match(LrclibClient.similarity(name, query.titles),
            Math.max(LrclibClient.similarity(artist, query.artists), LrclibClient.alike(artistOutline, query.artistOutlines, 4) ? 1 : 0),
            LrclibClient.alike(nameOutline, query.titleOutlines, 3), inLyrics, distinctive, diff, script);
        // Some entries have title and artist the wrong way round.
        Match swapped = LrclibClient.match(LrclibClient.similarity(name, query.artists),
            Math.max(LrclibClient.similarity(artist, query.titles), LrclibClient.alike(artistOutline, query.titleOutlines, 4) ? 1 : 0),
            LrclibClient.alike(nameOutline, query.artistOutlines, 3), inLyrics, distinctive, diff, script);
        return swapped.title > 0 && swapped.artist > 0 && swapped.score > direct.score ? swapped : direct;
    }

    private static boolean mostlyArabic(String text) {
        long arabic = text.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC).count();
        long latin = text.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN).count();
        return arabic > latin;
    }

    private static Match match(int title, int artist, boolean soundsLike, boolean inLyrics, boolean distinctive, double diff, double bonus) {
        double score = 100.0 + (title == 2 ? 100.0 : title == 1 ? 60.0 : 0.0) + (artist == 2 ? 80.0 : artist == 1 ? 40.0 : 0.0)
            + (soundsLike ? 50.0 : 0.0) + (inLyrics ? 50.0 : 0.0) + bonus;
        // The version of the same length is the one whose timing fits, so length weighs as much as the title.
        if (diff >= 0.0) {
            score += diff <= 3.0 ? 80.0 : diff <= CLOSE_ENOUGH ? 40.0 : diff <= CACHE_MISMATCH ? 0.0 : -Math.min(80.0, diff * 2.0);
        }
        return new Match(title, artist, soundsLike, inLyrics, distinctive, diff, score);
    }

    private static int similarity(String value, List<String> targets) {
        int best = 0;
        if (value.isEmpty()) return best;
        for (String target : targets) {
            if (value.equals(target)) return 2;
            // Short Arabic words like "حب" sit inside many other titles.
            if (Math.min(value.length(), target.length()) >= 4 && (value.contains(target) || target.contains(value))) best = 1;
        }
        return best;
    }

    // Short title outlines like "tnc" ("طنش", "Tannesh") still count, because a title matched this way also needs the
    // artist or the length; artist outlines need more letters since they unlock that.
    private static boolean alike(String outline, List<String> outlines, int shortest) {
        for (String target : outlines) {
            int shorter = Math.min(outline.length(), target.length());
            if (shorter >= shortest && outline.equals(target) || shorter >= 5 && (outline.contains(target) || target.contains(outline))) return true;
        }
        return false;
    }

    private static CompletableFuture<String> request(String url, boolean retry) {
        return LrclibClient.request(url, retry ? 2 : 0);
    }

    // LRCLIB answers bursts of requests with 429 or 503, which pass within a second or two, so those are retried. A
    // request that times out is not retried, so a slow LRCLIB delays the lyrics by one timeout rather than several.
    private static CompletableFuture<String> request(String url, int retries) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(5L)).GET().build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, error) -> {
            if (response != null && response.statusCode() == 200) {
                return CompletableFuture.completedFuture(response.body());
            }
            boolean busy = response != null && (response.statusCode() == 429 || response.statusCode() >= 500);
            if (retries <= 0 || !busy) {
                return CompletableFuture.<String>completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> url, CompletableFuture.delayedExecutor(retries == 2 ? 800L : 1600L, TimeUnit.MILLISECONDS))
                .thenCompose(again -> LrclibClient.request(again, retries - 1));
        }).thenCompose(future -> future);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    public static String cleanTitle(String string) {
        if (string == null) {
            return "";
        }
        String string2 = CLEAN_PATTERN.matcher(TextNormalizer.forDisplay(string)).replaceAll("");
        return SYMBOLS.matcher(string2).replaceAll("").replaceAll("\\s{2,}", " ").trim();
    }

    private static String cacheFileName(String artist, String title) {
        String name = artist + "_" + title;
        if (name.chars().allMatch(c -> c < 128)) {
            return (name + ".lrc").replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        // Non-Latin letters used to become "_", so two Arabic songs with names of the same length shared a cache file.
        String readable = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (readable.codePointCount(0, readable.length()) > 80) {
            readable = readable.substring(0, readable.offsetByCodePoints(0, 80));
        }
        return readable + "_" + Integer.toHexString((artist + "\u0000" + title).hashCode()) + ".lrc";
    }

    private static void saveToCache(File file, String string) {
        try (FileWriter fileWriter = new FileWriter(file, StandardCharsets.UTF_8);){
            fileWriter.write(string);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    static {
        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }
}
