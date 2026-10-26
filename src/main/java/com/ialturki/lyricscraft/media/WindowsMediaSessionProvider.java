package com.ialturki.lyricscraft.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ialturki.lyricscraft.lyrics.LrclibClient;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The song Windows reports as playing: a PowerShell helper reads the system media session (Spotify, ytr-music, browsers,
 * any player that shows up in the Windows volume flyout) and prints a line for it several times a second. When the helper
 * is silent, window titles of known players are read instead. Cover art and missing lengths come from Deezer or iTunes.
 */
public class WindowsMediaSessionProvider implements MediaProvider {
    private static final byte VK_MEDIA_NEXT_TRACK = (byte)0xB0;
    private static final byte VK_MEDIA_PREV_TRACK = (byte)0xB1;
    private static final byte VK_MEDIA_PLAY_PAUSE = (byte)0xB3;
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    // Players without a length are given three minutes until metadata arrives.
    private static final long DEFAULT_DURATION_MS = 180000L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final AtomicReference<TrackInfo> currentTrack = new AtomicReference<>(null);
    private Thread workerThread;
    private Thread sessionThread;
    private volatile Process sessionProcess = null;
    private volatile boolean running = false;
    private volatile long lastSessionUpdateMs = 0L;
    private String lastDetectedKey = "";
    private long trackStartTimeMs = 0L;
    private long cachedDurationMs = 0L;
    private byte[] cachedArtBytes = null;
    private volatile String cachedArtUrl = null;
    private String cachedAlbumName = "";

    @Override
    public void init() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.startSessionThread();
        this.workerThread = new Thread(() -> {
            while (this.running) {
                try {
                    if (System.currentTimeMillis() - this.lastSessionUpdateMs > 800L) {
                        this.pollWindowTitles();
                    }
                    Thread.sleep(150L);
                }
                catch (InterruptedException interrupted) {
                    break;
                }
                catch (Throwable ignored) {
                }
            }
        }, "LyricsCraft-WindowTitles");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    // Lines read "LP|status|positionMs|durationMs|artist|title|album".
    private void startSessionThread() {
        this.sessionThread = new Thread(() -> {
            File script = this.writeSessionScript();
            if (script == null) {
                return;
            }
            while (this.running) {
                try {
                    ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.getAbsolutePath());
                    builder.redirectErrorStream(true);
                    this.sessionProcess = builder.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.sessionProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (this.running && (line = reader.readLine()) != null) {
                            String[] fields;
                            if (!(line = line.trim()).startsWith("LP|") || (fields = line.split("\\|", -1)).length < 7) continue;
                            String status = fields[1];
                            long position = this.parseLongSafe(fields[2]);
                            long duration = this.parseLongSafe(fields[3]);
                            String artist = fields[4].trim();
                            String title = fields[5].trim();
                            String album = fields[6].trim();
                            if (!title.isEmpty()) {
                                this.lastSessionUpdateMs = System.currentTimeMillis();
                                this.handleTrackUpdate(title, artist, album, duration, position, "Playing".equalsIgnoreCase(status));
                                continue;
                            }
                            if ("NONE".equals(status)) {
                                this.lastSessionUpdateMs = 0L;
                            }
                        }
                    }
                }
                catch (Throwable ignored) {
                }
                finally {
                    if (this.sessionProcess != null) {
                        try {
                            this.sessionProcess.destroyForcibly();
                        }
                        catch (Throwable ignored) {
                        }
                        this.sessionProcess = null;
                    }
                }
                try {
                    Thread.sleep(2000L);
                }
                catch (InterruptedException interrupted) {
                    break;
                }
            }
        }, "LyricsCraft-MediaSession");
        this.sessionThread.setDaemon(true);
        this.sessionThread.start();
    }

    private File writeSessionScript() {
        try (InputStream input = WindowsMediaSessionProvider.class.getResourceAsStream("/assets/lyricscraft/media_session.ps1")) {
            if (input == null) {
                return null;
            }
            File directory = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lyricscraft");
            directory.mkdirs();
            File script = new File(directory, "media_session.ps1");
            Files.write(script.toPath(), input.readAllBytes());
            return script;
        }
        catch (Exception exception) {
            return null;
        }
    }

    private void handleTrackUpdate(String title, String artist, String album, long durationMs, long positionMs, boolean playing) {
        String key = artist + "_" + title;
        if (!key.equals(this.lastDetectedKey)) {
            this.lastDetectedKey = key;
            this.trackStartTimeMs = System.currentTimeMillis() - positionMs;
            this.cachedDurationMs = durationMs > 0L ? durationMs : DEFAULT_DURATION_MS;
            this.cachedArtBytes = null;
            this.cachedArtUrl = null;
            this.cachedAlbumName = album;
            this.fetchMetadataAsync(title, artist);
        } else {
            if (durationMs > 0L) {
                this.cachedDurationMs = durationMs;
            }
            if (!album.isEmpty() && this.cachedAlbumName.isEmpty()) {
                this.cachedAlbumName = album;
            }
        }
        long duration = this.cachedDurationMs > 0L ? this.cachedDurationMs : DEFAULT_DURATION_MS;
        long position = Math.min(Math.max(0L, positionMs), duration);
        this.currentTrack.set(new TrackInfo(title, artist, this.cachedAlbumName, duration, position, playing, this.cachedArtBytes, this.cachedArtUrl, System.currentTimeMillis()));
    }

    // Window titles of Spotify, browsers playing YouTube Music, YouTube, SoundCloud or TIDAL, and Apple Music.
    private void pollWindowTitles() {
        AtomicReference<String> foundTitle = new AtomicReference<>(null);
        AtomicReference<String> foundArtist = new AtomicReference<>(null);
        User32.INSTANCE.EnumWindows((window, data) -> {
            char[] buffer = new char[512];
            if (User32.INSTANCE.GetWindowText(window, buffer, 512) <= 0) {
                return true;
            }
            String windowTitle = Native.toString(buffer).trim();
            if (windowTitle.isEmpty()) {
                return true;
            }
            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, processId);
            ProcessHandle.of(processId.getValue()).ifPresent(process -> {
                String command = process.info().command().orElse("");
                String exe = (command.contains("\\") ? command.substring(command.lastIndexOf('\\') + 1) : command).toLowerCase();
                WindowsMediaSessionProvider.readWindowTitle(exe, windowTitle, foundTitle, foundArtist);
            });
            return foundTitle.get() == null;
        }, null);
        String title = foundTitle.get();
        String artist = foundArtist.get() != null ? foundArtist.get() : "";
        if (title != null && !title.isBlank()) {
            long position = Math.max(0L, System.currentTimeMillis() - this.trackStartTimeMs);
            this.handleTrackUpdate(title, artist, "", this.cachedDurationMs, position, true);
        } else if (!this.lastDetectedKey.isEmpty() && System.currentTimeMillis() - this.lastSessionUpdateMs > 3000L) {
            this.lastDetectedKey = "";
            this.currentTrack.set(null);
        }
    }

    private static void readWindowTitle(String exe, String windowTitle, AtomicReference<String> title, AtomicReference<String> artist) {
        if (exe.equals("spotify.exe")) {
            if (windowTitle.contains(" - ") && !windowTitle.equalsIgnoreCase("spotify") && !windowTitle.equalsIgnoreCase("spotify free")
                && !windowTitle.equalsIgnoreCase("spotify premium") && !windowTitle.contains("GDI+") && !windowTitle.startsWith("Angle")
                && !windowTitle.startsWith("Default IME")) {
                String[] parts = windowTitle.split(" - ", 2);
                artist.set(parts[0].trim());
                title.set(parts[1].trim());
            }
        } else if (exe.equals("chrome.exe") || exe.equals("msedge.exe") || exe.equals("brave.exe") || exe.equals("firefox.exe") || exe.equals("opera.exe")
            || exe.equals("vivaldi.exe") || exe.equals("arc.exe") || exe.equals("thorium.exe") || exe.equals("floorp.exe") || exe.contains("youtube music")
            || exe.contains("ytmusic") || exe.equals("youtube-music.exe")) {
            int music = windowTitle.indexOf("- YouTube Music");
            if (music < 0) music = windowTitle.indexOf("– YouTube Music");
            if (music < 0) music = windowTitle.indexOf("| YouTube Music");
            if (music > 0) {
                String song = windowTitle.substring(0, music).trim();
                if (song.contains(" • ")) {
                    String[] parts = song.split(" • ", 2);
                    title.set(parts[0].trim());
                    artist.set(parts[1].trim());
                } else if (song.contains(" - ")) {
                    String[] parts = song.split(" - ", 2);
                    title.set(parts[0].trim());
                    artist.set(parts[1].trim());
                } else if (!song.isBlank()) {
                    title.set(song);
                    artist.set("");
                }
            } else if (windowTitle.contains("- YouTube") || windowTitle.contains("– YouTube")) {
                int youtube = windowTitle.contains("- YouTube") ? windowTitle.indexOf("- YouTube") : windowTitle.indexOf("– YouTube");
                String video = windowTitle.substring(0, youtube).trim();
                // Notification counts like "(3) " lead YouTube tab titles.
                if (video.matches("^\\(\\d+\\)\\s+.*")) video = video.replaceFirst("^\\(\\d+\\)\\s+", "");
                if (video.contains(" - ")) {
                    String[] parts = video.split(" - ", 2);
                    artist.set(parts[0].trim());
                    title.set(parts[1].trim());
                } else if (video.contains(" • ")) {
                    String[] parts = video.split(" • ", 2);
                    title.set(parts[0].trim());
                    artist.set(parts[1].trim());
                }
            } else if (windowTitle.contains("- Spotify")) {
                String song = windowTitle.substring(0, windowTitle.indexOf("- Spotify")).trim();
                if (song.contains(" - ")) {
                    String[] parts = song.split(" - ", 2);
                    artist.set(parts[0].trim());
                    title.set(parts[1].trim());
                }
            } else if (windowTitle.contains("| SoundCloud") || windowTitle.contains("- SoundCloud")) {
                String marker = windowTitle.contains("| SoundCloud") ? "| SoundCloud" : "- SoundCloud";
                String song = windowTitle.substring(0, windowTitle.indexOf(marker)).trim();
                if (song.contains(" by ")) {
                    String[] parts = song.split(" by ", 2);
                    title.set(parts[0].trim());
                    artist.set(parts[1].trim());
                } else if (song.contains(" - ")) {
                    String[] parts = song.split(" - ", 2);
                    artist.set(parts[0].trim());
                    title.set(parts[1].trim());
                }
            } else if (windowTitle.contains("- TIDAL")) {
                String song = windowTitle.substring(0, windowTitle.indexOf("- TIDAL")).trim();
                if (song.contains(" - ")) {
                    String[] parts = song.split(" - ", 2);
                    title.set(parts[0].trim());
                    artist.set(parts[1].trim());
                }
            }
        } else if (exe.equals("applemusic.exe") && windowTitle.contains(" - ") && !windowTitle.equalsIgnoreCase("apple music")) {
            String[] parts = windowTitle.split(" - ", 2);
            title.set(parts[0].trim());
            artist.set(parts[1].trim());
        }
    }

    // Deezer first, iTunes second, then Deezer with the title alone.
    private void fetchMetadataAsync(String title, String artist) {
        String cleanTitle = LrclibClient.cleanTitle(title);
        String cleanArtist = LrclibClient.cleanTitle(artist);
        String query = !cleanArtist.isBlank() ? cleanArtist + " " + cleanTitle : cleanTitle;
        this.fetchDeezerMetadata(query, found -> {
            if (!found) {
                this.fetchItunesMetadata(query, foundItunes -> {
                    if (!foundItunes && !cleanArtist.isBlank()) {
                        this.fetchDeezerMetadata(cleanTitle, null);
                    }
                });
            }
        });
    }

    private void fetchDeezerMetadata(String query, Consumer<Boolean> done) {
        try {
            String url = "https://api.deezer.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=1";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", BROWSER_USER_AGENT).timeout(Duration.ofSeconds(5L)).GET().build();
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                boolean found = false;
                if (response.statusCode() == 200) {
                    try {
                        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray data;
                        if (body.has("data") && (data = body.getAsJsonArray("data")).size() > 0) {
                            JsonObject song = data.get(0).getAsJsonObject();
                            if (song.has("duration") && this.cachedDurationMs == DEFAULT_DURATION_MS) {
                                this.cachedDurationMs = song.get("duration").getAsLong() * 1000L;
                            }
                            if (song.has("album")) {
                                JsonObject album = song.getAsJsonObject("album");
                                if (album.has("title") && this.cachedAlbumName.isEmpty()) {
                                    this.cachedAlbumName = album.get("title").getAsString();
                                }
                                String cover = null;
                                if (album.has("cover_big") && !album.get("cover_big").isJsonNull()) {
                                    cover = album.get("cover_big").getAsString();
                                } else if (album.has("cover_medium") && !album.get("cover_medium").isJsonNull()) {
                                    cover = album.get("cover_medium").getAsString();
                                }
                                if (cover != null && !cover.isBlank()) {
                                    this.cachedArtUrl = cover;
                                    this.pushTrackUpdateWithArt();
                                    this.fetchCoverArtBytes(cover);
                                    found = true;
                                }
                            }
                        }
                    }
                    catch (Exception ignored) {
                    }
                }
                if (done != null) {
                    done.accept(found);
                }
            }).exceptionally(error -> {
                if (done != null) {
                    done.accept(false);
                }
                return null;
            });
        }
        catch (Exception exception) {
            if (done != null) {
                done.accept(false);
            }
        }
    }

    private void fetchItunesMetadata(String query, Consumer<Boolean> done) {
        try {
            String url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&entity=song&limit=1";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", BROWSER_USER_AGENT).timeout(Duration.ofSeconds(5L)).GET().build();
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                boolean found = false;
                if (response.statusCode() == 200) {
                    try {
                        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray results;
                        if (body.has("results") && (results = body.getAsJsonArray("results")).size() > 0) {
                            JsonObject song = results.get(0).getAsJsonObject();
                            if (song.has("trackTimeMillis")) {
                                this.cachedDurationMs = song.get("trackTimeMillis").getAsLong();
                            }
                            if (song.has("collectionName") && this.cachedAlbumName.isEmpty()) {
                                this.cachedAlbumName = song.get("collectionName").getAsString();
                            }
                            if (song.has("artworkUrl100")) {
                                String cover = song.get("artworkUrl100").getAsString().replace("100x100bb.jpg", "600x600bb.jpg");
                                this.cachedArtUrl = cover;
                                this.pushTrackUpdateWithArt();
                                this.fetchCoverArtBytes(cover);
                                found = true;
                            }
                        }
                    }
                    catch (Exception ignored) {
                    }
                }
                if (done != null) {
                    done.accept(found);
                }
            }).exceptionally(error -> {
                if (done != null) {
                    done.accept(false);
                }
                return null;
            });
        }
        catch (Exception exception) {
            if (done != null) {
                done.accept(false);
            }
        }
    }

    private void fetchCoverArtBytes(String url) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", BROWSER_USER_AGENT).timeout(Duration.ofSeconds(6L)).GET().build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
            if (response.statusCode() == 200 && response.body().length > 0) {
                this.cachedArtBytes = response.body();
                this.pushTrackUpdateWithArt();
            }
        }).exceptionally(error -> null);
    }

    private void pushTrackUpdateWithArt() {
        TrackInfo track = this.currentTrack.get();
        if (track != null) {
            this.currentTrack.set(new TrackInfo(track.getTitle(), track.getArtist(), !this.cachedAlbumName.isEmpty() ? this.cachedAlbumName : track.getAlbum(),
                this.cachedDurationMs > 0L ? this.cachedDurationMs : track.getDurationMs(), track.getPositionMs(), track.isPlaying(), this.cachedArtBytes,
                this.cachedArtUrl, track.getSampledAtSystemMs()));
        }
    }

    private long parseLongSafe(String text) {
        try {
            return Long.parseLong(text.trim());
        }
        catch (Exception exception) {
            return 0L;
        }
    }

    @Override
    public TrackInfo pollTrack() {
        return this.currentTrack.get();
    }

    @Override
    public void skipNext() {
        this.sendMediaKey(VK_MEDIA_NEXT_TRACK);
    }

    @Override
    public void skipPrevious() {
        this.sendMediaKey(VK_MEDIA_PREV_TRACK);
    }

    @Override
    public void togglePlayPause() {
        this.sendMediaKey(VK_MEDIA_PLAY_PAUSE);
    }

    private void sendMediaKey(byte key) {
        new Thread(() -> {
            try {
                User32Media.INSTANCE.keybd_event(key, (byte)0, 0, 0);
                Thread.sleep(20L);
                User32Media.INSTANCE.keybd_event(key, (byte)0, 2, 0);
            }
            catch (Throwable ignored) {
            }
        }).start();
    }

    @Override
    public void close() {
        this.running = false;
        if (this.sessionProcess != null) {
            try {
                this.sessionProcess.destroyForcibly();
            }
            catch (Throwable ignored) {
            }
            this.sessionProcess = null;
        }
        if (this.workerThread != null) {
            this.workerThread.interrupt();
        }
        if (this.sessionThread != null) {
            this.sessionThread.interrupt();
        }
    }

    public interface User32Media extends Library {
        User32Media INSTANCE = Native.load("user32", User32Media.class);

        void keybd_event(byte virtualKey, byte scanCode, int flags, int extraInfo);
    }
}
