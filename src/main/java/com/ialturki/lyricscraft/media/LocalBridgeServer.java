package com.ialturki.lyricscraft.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A local endpoint that a browser extension or another player can post the playing song to:
 * POST http://127.0.0.1:8974/update with {"title", "artist", "album", "durationMs", "positionMs", "isPlaying", "artUrl"}.
 */
public class LocalBridgeServer implements MediaProvider {
    public static final int PORT = 8974;
    private HttpServer server;
    private final AtomicReference<TrackInfo> currentTrack = new AtomicReference<>(null);

    @Override
    public void init() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            this.server.createContext("/update", this::handleUpdate);
            this.server.createContext("/status", exchange -> LocalBridgeServer.respond(exchange, 200, "{\"status\":\"ok\",\"mod\":\"LyricsCraft\"}"));
            this.server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "LyricsCraft-Bridge");
                thread.setDaemon(true);
                return thread;
            }));
            this.server.start();
        }
        catch (IOException exception) {
            System.err.println("[LyricsCraft] Failed to start the local bridge: " + exception.getMessage());
        }
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1L);
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (InputStream input = exchange.getRequestBody()) {
                JsonObject body = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String title = body.has("title") ? body.get("title").getAsString() : "";
                String artist = body.has("artist") ? body.get("artist").getAsString() : "";
                String album = body.has("album") ? body.get("album").getAsString() : "";
                long duration = body.has("durationMs") ? body.get("durationMs").getAsLong() : 0L;
                long position = body.has("positionMs") ? body.get("positionMs").getAsLong() : 0L;
                boolean playing = body.has("isPlaying") && body.get("isPlaying").getAsBoolean();
                String artUrl = body.has("artUrl") ? body.get("artUrl").getAsString() : null;
                this.currentTrack.set(title.isBlank() ? null : new TrackInfo(title, artist, album, duration, position, playing, null, artUrl, 0L));
                LocalBridgeServer.respond(exchange, 200, "{\"success\":true}");
                return;
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        exchange.sendResponseHeaders(400, -1L);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public TrackInfo pollTrack() {
        return this.currentTrack.get();
    }

    @Override
    public void skipNext() {
    }

    @Override
    public void skipPrevious() {
    }

    @Override
    public void togglePlayPause() {
    }

    @Override
    public void close() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }
}
