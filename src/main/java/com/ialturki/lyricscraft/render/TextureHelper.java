package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.LyricsCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public final class TextureHelper {
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("lyricscraft", "cover_art_texture");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, byte[]> URL_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static byte[] lastArt, moodArt;
    private static String moodKey = "";
    private static boolean hasTexture;
    private static int moodColor = 0x2ECC71;

    public static Identifier getCoverArtTexture(byte[] bytes, String url) {
        bytes = resolveBytes(bytes, url);
        if (bytes == null || bytes.length == 0) return null;
        if (Arrays.equals(bytes, lastArt)) return hasTexture ? COVER : null;
        lastArt = bytes;
        try {
            NativeImage image = NativeImage.read(bytes);
            DynamicTexture texture;
            try { texture = new DynamicTexture(() -> "LyricsCraft album cover", image); }
            catch (RuntimeException exception) { image.close(); throw exception; }
            // TextureManager owns and closes the previous texture when replacing it.
            Minecraft.getInstance().getTextureManager().register(COVER, texture);
            hasTexture = true;
        } catch (Exception exception) {
            hasTexture = false;
            LyricsCraft.LOGGER.debug("[LyricsCraft] Could not decode album cover", exception);
        }
        return hasTexture ? COVER : null;
    }
    private static byte[] resolveBytes(byte[] bytes, String url) {
        if (bytes != null && bytes.length > 0) return bytes;
        if (url == null || url.isBlank()) return null;
        byte[] cached = URL_CACHE.get(url);
        if (cached != null) return cached;
        if (PENDING.add(url)) {
            try {
                URI uri = URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                    PENDING.remove(url); return null;
                }
                HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "LyricsCraft/3.0.0 (Minecraft 26.3)").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
                        try {
                            if (error == null && response.statusCode() == 200 && response.body().length > 0) {
                                if (URL_CACHE.size() >= 64) URL_CACHE.clear();
                                URL_CACHE.put(url, response.body());
                            }
                        } finally { PENDING.remove(url); }
                    });
            } catch (RuntimeException exception) { PENDING.remove(url); }
        }
        return null;
    }
    public static int getMoodColorForTrack(byte[] bytes, String title, String artist) {
        String key = title + "\n" + artist;
        if (key.equals(moodKey) && Arrays.equals(bytes, moodArt)) return moodColor;
        moodKey = key; moodArt = bytes; moodColor = 0x2ECC71;
        if (bytes == null || bytes.length == 0) return moodColor;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return moodColor;
            long red = 0, green = 0, blue = 0; int count = 0, best = -1; float saturation = 0;
            int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 16);
            for (int x = 0; x < image.getWidth(); x += step) {
                for (int y = 0; y < image.getHeight(); y += step) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >>> 24) < 128) continue;
                    int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, bl = pixel & 255;
                    red += r; green += g; blue += bl; count++;
                    float[] hsv = Color.RGBtoHSB(r, g, bl, null);
                    if (hsv[2] > .3f && hsv[2] < .95f && hsv[1] > saturation) {
                        saturation = hsv[1]; best = pixel & 0xFFFFFF;
                    }
                }
            }
            if (saturation >= .25f && best != -1) moodColor = best;
            else if (count > 0) moodColor = ((int)(red/count) << 16) | ((int)(green/count) << 8) | (int)(blue/count);
        } catch (Exception exception) {
            LyricsCraft.LOGGER.debug("[LyricsCraft] Could not sample album color", exception);
        }
        return moodColor;
    }
    public static void close() {
        if (hasTexture) Minecraft.getInstance().getTextureManager().release(COVER);
        hasTexture = false; lastArt = null; URL_CACHE.clear(); PENDING.clear();
    }
}
