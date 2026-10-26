package com.ialturki.lyricscraft.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second lyrics source: YouTube Music, through the same interface its web and Android apps use. It has timed lyrics for
 * many songs LRCLIB lacks, Arabic ones included. Results come back as LRCLIB-style entries (trackName, artistName,
 * duration, and syncedLyrics or plainLyrics) so both sources are matched and ranked the same way.
 */
final class YouTubeMusicLyrics {
    private static final String API = "https://music.youtube.com/youtubei/v1/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    // The search filter for songs; without it uploads by other channels crowd out the song itself.
    private static final String SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF";
    private static final Pattern LENGTH = Pattern.compile("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();

    private YouTubeMusicLyrics() {}

    /** Songs YouTube Music finds for the text, as entries with trackName, artistName, duration and videoId. */
    static CompletableFuture<List<JsonObject>> searchSongs(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("query", text);
        body.addProperty("params", SONGS_FILTER);
        return YouTubeMusicLyrics.call("search", body, false).thenApply(response -> {
            List<JsonObject> songs = new ArrayList<>();
            if (response == null) return songs;
            for (JsonElement item : YouTubeMusicLyrics.find(response, "musicResponsiveListItemRenderer")) {
                if (!item.isJsonObject() || !item.getAsJsonObject().has("flexColumns")) continue;
                JsonArray columns = item.getAsJsonObject().getAsJsonArray("flexColumns");
                List<JsonElement> ids = YouTubeMusicLyrics.find(item, "videoId");
                if (columns.size() < 2 || ids.isEmpty()) continue;
                // The second column reads "Artist • Album • 3:22".
                String[] details = YouTubeMusicLyrics.text(columns.get(1)).split("\\s+\u2022\\s+");
                JsonObject song = new JsonObject();
                song.addProperty("videoId", ids.getFirst().getAsString());
                song.addProperty("trackName", YouTubeMusicLyrics.text(columns.get(0)));
                song.addProperty("artistName", details[0]);
                song.addProperty("duration", YouTubeMusicLyrics.seconds(details[details.length - 1]));
                songs.add(song);
            }
            return songs;
        });
    }

    /**
     * The song's lyrics as an entry with syncedLyrics (enhanced LRC with line ends) when YouTube Music has them timed, or
     * plainLyrics otherwise; null when it has no lyrics for the song.
     */
    static CompletableFuture<JsonObject> lyrics(JsonObject song) {
        JsonObject watch = new JsonObject();
        watch.addProperty("videoId", song.get("videoId").getAsString());
        return YouTubeMusicLyrics.call("next", watch, false).thenCompose(next -> {
            String browseId = null;
            if (next != null) {
                for (JsonElement id : YouTubeMusicLyrics.find(next, "browseId")) {
                    if (id.isJsonPrimitive() && id.getAsString().startsWith("MPLY")) {
                        browseId = id.getAsString();
                        break;
                    }
                }
            }
            if (browseId == null) return CompletableFuture.completedFuture(null);
            JsonObject browse = new JsonObject();
            browse.addProperty("browseId", browseId);
            // Only the Android app's answer carries line timings; the web app's carries the plain text.
            return YouTubeMusicLyrics.call("browse", browse, true).thenCompose(timed -> {
                String lrc = timed == null ? null : YouTubeMusicLyrics.timedLrc(timed);
                if (lrc != null) {
                    JsonObject result = song.deepCopy();
                    result.addProperty("syncedLyrics", lrc);
                    return CompletableFuture.completedFuture(result);
                }
                return YouTubeMusicLyrics.call("browse", browse, false).thenApply(plain -> {
                    if (plain == null) return null;
                    for (JsonElement shelf : YouTubeMusicLyrics.find(plain, "musicDescriptionShelfRenderer")) {
                        String text = shelf.isJsonObject() && shelf.getAsJsonObject().has("description")
                            ? YouTubeMusicLyrics.text(shelf.getAsJsonObject().get("description")) : "";
                        if (text.isBlank()) continue;
                        JsonObject result = song.deepCopy();
                        result.addProperty("plainLyrics", text);
                        return result;
                    }
                    return null;
                });
            });
        });
    }

    // "[00:12.220]حي الدلايل ...<00:17.970>": each line with its start and end.
    private static String timedLrc(JsonObject response) {
        List<JsonElement> data = YouTubeMusicLyrics.find(response, "timedLyricsData");
        if (data.isEmpty() || !data.getFirst().isJsonArray()) return null;
        StringBuilder lrc = new StringBuilder();
        for (JsonElement element : data.getFirst().getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject line = element.getAsJsonObject();
            JsonObject cue = line.has("cueRange") ? line.getAsJsonObject("cueRange") : null;
            if (cue == null || !cue.has("startTimeMilliseconds")) continue;
            long start = cue.get("startTimeMilliseconds").getAsLong();
            long end = cue.has("endTimeMilliseconds") ? cue.get("endTimeMilliseconds").getAsLong() : -1L;
            String text = line.has("lyricLine") ? line.get("lyricLine").getAsString() : "";
            lrc.append('[').append(Lyricsfile.stamp(start)).append(']').append(text.replace('\n', ' '));
            if (end > start) lrc.append('<').append(Lyricsfile.stamp(end)).append('>');
            lrc.append('\n');
        }
        return lrc.isEmpty() ? null : lrc.toString();
    }

    private static CompletableFuture<JsonObject> call(String endpoint, JsonObject body, boolean android) {
        JsonObject client = new JsonObject();
        client.addProperty("clientName", android ? "ANDROID_MUSIC" : "WEB_REMIX");
        client.addProperty("clientVersion", android ? "7.21.50" : "1." + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + ".01.00");
        client.addProperty("hl", "en");
        client.addProperty("gl", "US");
        JsonObject context = new JsonObject();
        context.add("client", client);
        JsonObject request = body.deepCopy();
        request.add("context", context);
        HttpRequest http = HttpRequest.newBuilder().uri(URI.create(API + endpoint + "?prettyPrint=false"))
            .header("Content-Type", "application/json").header("User-Agent", USER_AGENT).header("Origin", "https://music.youtube.com")
            .timeout(Duration.ofSeconds(5L)).POST(HttpRequest.BodyPublishers.ofString(request.toString())).build();
        return HTTP_CLIENT.sendAsync(http, HttpResponse.BodyHandlers.ofString()).handle((response, error) -> {
            if (response == null || response.statusCode() != 200) return null;
            try {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            } catch (RuntimeException exception) {
                return null;
            }
        });
    }

    // The responses nest their content deeply and differently per page, so values are found by key anywhere inside.
    private static List<JsonElement> find(JsonElement node, String key) {
        List<JsonElement> found = new ArrayList<>();
        YouTubeMusicLyrics.find(node, key, found);
        return found;
    }

    private static void find(JsonElement node, String key, List<JsonElement> found) {
        if (node.isJsonObject()) {
            for (var entry : node.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals(key)) found.add(entry.getValue());
                YouTubeMusicLyrics.find(entry.getValue(), key, found);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) YouTubeMusicLyrics.find(child, key, found);
        }
    }

    // Text made of "runs", as titles and descriptions are.
    private static String text(JsonElement node) {
        StringBuilder text = new StringBuilder();
        for (JsonElement runs : YouTubeMusicLyrics.find(node, "runs")) {
            if (!runs.isJsonArray()) continue;
            for (JsonElement run : runs.getAsJsonArray()) {
                if (run.isJsonObject() && run.getAsJsonObject().has("text")) text.append(run.getAsJsonObject().get("text").getAsString());
            }
            break;
        }
        return text.toString();
    }

    private static double seconds(String length) {
        Matcher matcher = LENGTH.matcher(length.trim());
        if (!matcher.matches()) return 0.0;
        double hours = matcher.group(1) != null ? Double.parseDouble(matcher.group(1)) : 0.0;
        return hours * 3600.0 + Double.parseDouble(matcher.group(2)) * 60.0 + Double.parseDouble(matcher.group(3));
    }
}
