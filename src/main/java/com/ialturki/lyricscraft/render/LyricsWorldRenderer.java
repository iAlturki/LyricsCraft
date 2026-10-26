package com.ialturki.lyricscraft.render;

import com.ialturki.lyricscraft.config.LyricsConfig;
import com.ialturki.lyricscraft.lyrics.LyricLine;
import com.ialturki.lyricscraft.lyrics.LyricsManager;
import com.ialturki.lyricscraft.lyrics.TextNormalizer;
import com.ialturki.lyricscraft.media.MediaManager;
import com.ialturki.lyricscraft.media.TrackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lyrics in the world. Floating: each line is placed ahead of the camera when it starts and stays there, so it keeps
 * perspective, parallax and depth like any other object in the level. On blocks: the words of each line land one after
 * another on the blocks ahead, painted onto walls or standing on the ground.
 */
public final class LyricsWorldRenderer {
    // World size of one font pixel per block of distance: keeps a freshly placed line at a readable angular size.
    private static final float PIXEL_SIZE_PER_BLOCK = 0.004f;
    // Words on blocks are spread across the view instead of stacked, so they can be larger.
    private static final double WORD_PIXEL_SIZE_PER_BLOCK = 0.0065;
    private static final double MAX_WORD_SPREAD = Math.toRadians(70.0);
    private static final int WORD_GAP = 8;
    private static final long FADE_MS = 300L;
    private static final int WRAP_WIDTH = 320;
    private static final int UPCOMING_COLOR = 0xDDB8C0CC;
    // The part of the current line not sung yet.
    private static final int UNSUNG = 0xD0D6E0;
    // Without an outline, pale text disappears against clouds and snow.
    private static final int DARK_OUTLINE = 0xFF101418;
    // Elytra gliding faster than this (blocks per tick) places lines along the flight path instead.
    private static final double GLIDE_SPEED = 0.45;
    private static final double GLIDE_LEAD_TICKS = 30.0;
    // Fractions of the distance and extra heights tried, in order, when blocks would cut through or hide a line.
    private static final double[] PULL_IN = {1.0, 0.8, 0.6, 0.45, 0.3};
    private static final double[] LIFT = {0.0, 2.0, 4.0};
    private static final float[] CORNERS = {-0.5f, 0.0f, 0.5f};
    private static final Pattern WORD = Pattern.compile("\\S+");
    // Sizes tried for a word painted on a face narrower than the word.
    private static final double[] SHRINK = {1.0, 0.75, 0.55};
    // Heights checked down a row box: the letters stop short of its bottom, where the spacing to the next row is.
    private static final float[] ROWS = {-0.35f, 0.0f, 0.2f};

    // FLOAT hangs in the air, STAMP is painted onto a wall face, DROP lands standing on top of a block.
    private enum Motion { FLOAT, STAMP, DROP }

    private record Row(FormattedCharSequence text, int width, int color, int outline, float size) {}
    // active is the current line's text for words on blocks, timed by line from position; both null while the lyrics load.
    private record Lines(String key, List<Row> rows, String active, Style style, int color, int outline, LyricLine line, long position, long nextStart) {}
    private record Piece(Vec3 pos, Quaternionf rotation, float scale, long delay, Motion motion, List<Row> rows) {}
    private record Panel(String key, long born, Vec3 anchor, double distance, double height, boolean gliding, boolean onBlocks, List<Piece> pieces) {}

    private static Panel current, previous;
    private static long previousRetired;
    private static ClientLevel level;

    private LyricsWorldRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(LyricsWorldRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LyricsConfig config = LyricsConfig.getInstance();
        CameraRenderState camera = context.levelState().cameraRenderState;
        long now = Util.getMillis();
        if (minecraft.level != level) { level = minecraft.level; current = previous = null; }
        Lines lines = config.renderIn3D && minecraft.player != null && level != null && camera.initialized ? lines(minecraft.font, config) : null;
        if (lines == null) retire(now);
        else if (current == null || !current.key.equals(lines.key) || needsMove(current, lines, minecraft, config, camera)) {
            retire(now);
            current = place(lines, minecraft, config, camera, now);
        } else if (!current.onBlocks) {
            // A floating line follows live changes such as the line count or bold setting.
            Piece piece = current.pieces.getFirst();
            current = new Panel(current.key, current.born, current.anchor, current.distance, current.height, current.gliding, false,
                List.of(new Piece(piece.pos, piece.rotation, piece.scale, piece.delay, piece.motion, lines.rows)));
        }

        if (previous != null) {
            float fade = (now - previousRetired) / (float)FADE_MS;
            if (fade >= 1.0f) previous = null;
            else for (Piece piece : previous.pieces) submit(context, camera, piece, 1.0f - fade, -fade, config.twoSided3D, now);
        }
        if (current != null) {
            for (Piece piece : current.pieces) {
                float fade = Math.max(0.0f, Math.min(1.0f, (now - current.born - piece.delay) / (float)FADE_MS));
                float eased = 1.0f - (1.0f - fade) * (1.0f - fade);
                submit(context, camera, piece, fade, 1.0f - eased, config.twoSided3D, now);
            }
        }
    }

    private static void retire(long now) {
        if (current == null) return;
        previous = current;
        previousRetired = now;
        current = null;
    }

    private static Lines lines(Font font, LyricsConfig config) {
        TrackInfo track = MediaManager.getInstance().getCurrentTrack();
        if (!config.showLyricsHud || track == null || !track.isValidTrack()) return null;
        if (config.autoHide && !track.isPlaying()) return null;
        LyricsManager lyrics = LyricsManager.getInstance();
        List<Row> rows = new ArrayList<>();
        if (!lyrics.hasLyrics()) {
            if (!lyrics.isLoading()) return null;
            addRows(rows, font, "Fetching lyrics...", Style.EMPTY, UPCOMING_COLOR, DARK_OUTLINE, 0.75f);
            return new Lines("loading:" + track.getTrackKey(), rows, null, Style.EMPTY, UPCOMING_COLOR, DARK_OUTLINE, null, 0L, -1L);
        }
        long position = track.getCurrentEstimatedPositionMs(config.timeOffsetMs);
        LyricLine line = lyrics.getCurrentLine(position);
        if (line == null || line.text().isBlank()) return null;
        String style = config.hudStyle != null ? config.hudStyle.toUpperCase() : "BORDERLESS";
        boolean compact = "COMPACT".equals(style) || config.compactMode;
        int count = compact ? Math.min(2, config.lyricsLineCount) : Math.max(2, Math.min(6, config.lyricsLineCount));
        int color = 0xFF000000 | LyricsHudRenderer.activeLyricColor(config, track);
        int outline = config.lyricsGlowEffect ? 0xFF000000 | ARGB.scaleRGB(color, 0.3f) : DARK_OUTLINE;
        Style active = Style.EMPTY.withBold(config.boldActiveLyric).withItalic(config.italicLyrics);
        String text = text(line, config);
        LyricLine next = lyrics.getNextLine(position);
        long nextStart = next != null ? next.timestampMs() : -1L;
        // As on the HUD: filled as far as it has been sung, or marked "≈" when the line times are only an estimate.
        if (lyrics.isEstimated()) addRows(rows, font, "≈ " + text, text.length() + 2, active, active, color, outline, 1.0f);
        else if (LyricsHudRenderer.fills(config, line)) addRows(rows, font, text, line.sungChars(position, nextStart), active, active.withColor(UNSUNG), color, outline, 1.0f);
        else addRows(rows, font, text, active, color, outline, 1.0f);
        Style upcoming = Style.EMPTY.withItalic(config.italicLyrics);
        for (LyricLine later : lyrics.getUpcomingLines(position, count - 1))
            addRows(rows, font, text(later, config), upcoming, UPCOMING_COLOR, DARK_OUTLINE, 0.75f);
        return new Lines(track.getTrackKey() + "@" + line.timestampMs(), rows, text, active, color, outline, line, position, nextStart);
    }

    private static String text(LyricLine line, LyricsConfig config) {
        return config.lyricsUppercase ? line.text().toUpperCase() : line.text();
    }

    private static void addRows(List<Row> rows, Font font, String text, Style style, int color, int outline, float size) {
        addRows(rows, font, text, text.length(), style, style, color, outline, size);
    }

    private static void addRows(List<Row> rows, Font font, String text, int sung, Style done, Style rest, int color, int outline, float size) {
        for (FormattedCharSequence part : BidiText.split(font, text, sung, done, rest, WRAP_WIDTH))
            rows.add(new Row(part, font.width(part), color, outline, size));
    }

    private static boolean gliding(Minecraft minecraft, LyricsConfig config) {
        return config.elytraFlightAssist && minecraft.player.isFallFlying()
            && minecraft.player.getDeltaMovement().length() > GLIDE_SPEED;
    }

    // Words land on blocks only while walking: gliding overtakes them within seconds.
    private static boolean onBlocks(Lines lines, Minecraft minecraft, LyricsConfig config) {
        return "BLOCKS".equalsIgnoreCase(config.worldLyricsStyle) && lines.active != null && !gliding(minecraft, config);
    }

    private static boolean needsMove(Panel panel, Lines lines, Minecraft minecraft, LyricsConfig config, CameraRenderState camera) {
        if (panel.onBlocks != onBlocks(lines, minecraft, config)) return true;
        if (panel.distance != config.textDistance3D || panel.height != config.textHeightOffset3D) return true;
        if (panel.anchor.distanceTo(camera.pos) > Math.max(panel.distance * 2.0, panel.distance + 24.0)) return true;
        // While gliding the player overtakes a line within seconds, so keep it on the flight path ahead.
        if (panel.gliding || gliding(minecraft, config))
            return panel.pieces.getFirst().pos.subtract(camera.pos).dot(Entity.calculateViewVector(camera.xRot, camera.yRot)) < 2.0;
        return false;
    }

    private static Panel place(Lines lines, Minecraft minecraft, LyricsConfig config, CameraRenderState camera, long now) {
        boolean gliding = gliding(minecraft, config);
        boolean onBlocks = onBlocks(lines, minecraft, config);
        List<Piece> pieces = onBlocks ? placeWords(lines, minecraft, config, camera) : List.of(placeLine(lines.rows, minecraft, config, camera, gliding));
        return new Panel(lines.key, now, camera.pos, config.textDistance3D, config.textHeightOffset3D, gliding, onBlocks, pieces);
    }

    private static Piece placeLine(List<Row> rows, Minecraft minecraft, LyricsConfig config, CameraRenderState camera, boolean gliding) {
        double distance = Math.max(2.0, Math.min(64.0, config.textDistance3D));
        Vec3 direction;
        float yaw, pitch;
        double height;
        if (gliding) {
            Vec3 velocity = minecraft.player.getDeltaMovement();
            direction = velocity.normalize();
            distance += velocity.length() * GLIDE_LEAD_TICKS;
            yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            pitch = (float)Math.toDegrees(-Math.asin(direction.y));
            height = 0.0;
        } else {
            direction = Entity.calculateViewVector(0.0f, camera.yRot);
            yaw = camera.yRot;
            pitch = 0.0f;
            height = config.textHeightOffset3D;
        }
        Quaternionf rotation = facing(yaw, pitch);
        Font font = minecraft.font;
        float width = 0.0f, tall = 0.0f;
        for (Row row : rows) {
            width = Math.max(width, row.width * row.size);
            tall += (font.lineHeight + 2) * row.size;
        }
        // A hill, tree or wall in the way would cut through the line or hide it, so the line comes closer (it keeps its
        // size on screen) or rises until every corner can be seen.
        Piece best = null;
        int fewest = Integer.MAX_VALUE;
        for (double fraction : PULL_IN) {
            for (double lift : gliding ? new double[] {0.0} : LIFT) {
                Vec3 pos = camera.pos.add(direction.scale(Math.max(3.0, distance * fraction))).add(0.0, height * fraction + lift, 0.0);
                float scale = (float)(pos.distanceTo(camera.pos) * PIXEL_SIZE_PER_BLOCK);
                int hidden = hiddenPoints(minecraft, camera.pos, pos, rotation, scale, width, tall);
                if (hidden < fewest) {
                    fewest = hidden;
                    best = new Piece(pos, rotation, scale, 0L, Motion.FLOAT, rows);
                }
                if (hidden == 0) return best;
            }
        }
        return best;
    }

    // Corners, edge midpoints and centre of a panel that a block hides or cuts through.
    private static int hiddenPoints(Minecraft minecraft, Vec3 eye, Vec3 center, Quaternionf rotation, float scale, float width, float tall) {
        return hiddenPoints(minecraft, eye, center, rotation, scale, width, tall, ClipContext.Block.COLLIDER);
    }

    private static int hiddenPoints(Minecraft minecraft, Vec3 eye, Vec3 center, Quaternionf rotation, float scale, float width, float tall, ClipContext.Block shapes) {
        int hidden = 0;
        for (Vec3 point : samples(center, rotation, scale, width, tall)) {
            if (clip(minecraft, eye, point, shapes, ClipContext.Fluid.NONE).getType() == HitResult.Type.BLOCK) hidden++;
        }
        return hidden;
    }

    private static List<Vec3> samples(Vec3 center, Quaternionf rotation, float scale, float width, float tall) {
        List<Vec3> points = new ArrayList<>();
        for (float x : CORNERS) {
            for (float y : ROWS) {
                Vector3f offset = rotation.transform(new Vector3f(x * width * scale, -y * tall * scale, 0.0f));
                points.add(center.add(offset.x, offset.y, offset.z));
            }
        }
        return points;
    }

    private static BlockHitResult clip(Minecraft minecraft, Vec3 from, Vec3 to, ClipContext.Fluid fluid) {
        return clip(minecraft, from, to, ClipContext.Block.COLLIDER, fluid);
    }

    private static BlockHitResult clip(Minecraft minecraft, Vec3 from, Vec3 to, ClipContext.Block shapes, ClipContext.Fluid fluid) {
        return minecraft.level.clip(new ClipContext(from, to, shapes, fluid, minecraft.player));
    }

    // A painted word fits when solid face lies behind every part of it, nothing (not even grass) stands in front of it,
    // and every part can be seen; otherwise it would hang off the face's edge or into the block next to it.
    private static boolean fitsOnFace(Minecraft minecraft, Vec3 eye, Vec3 pos, Quaternionf rotation, float scale, float width, float tall, Direction face) {
        Vec3 normal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
        for (Vec3 point : samples(pos, rotation, scale, width, tall)) {
            BlockPos behind = BlockPos.containing(point.subtract(normal.scale(0.1)));
            BlockPos front = BlockPos.containing(point.add(normal.scale(0.1)));
            if (minecraft.level.getBlockState(behind).getCollisionShape(minecraft.level, behind).isEmpty()) return false;
            if (!minecraft.level.getBlockState(front).getShape(minecraft.level, front).isEmpty()) return false;
            if (clip(minecraft, eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE).getType() == HitResult.Type.BLOCK) return false;
        }
        return true;
    }

    private static List<Piece> placeWords(Lines lines, Minecraft minecraft, LyricsConfig config, CameraRenderState camera) {
        Font font = minecraft.font;
        boolean rightToLeft = BidiText.isRightToLeft(TextNormalizer.forDisplay(lines.active));
        List<FormattedCharSequence> visuals = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        Matcher word = WORD.matcher(lines.active);
        while (word.find()) {
            FormattedCharSequence visual = BidiText.visual(word.group(), lines.style);
            int width = font.width(visual);
            if (width <= 0) continue;
            visuals.add(visual);
            widths.add(width);
            // Each word lands as it is sung: at its own time in word-synced lyrics, otherwise spread over the line.
            delays.add(Math.max(0L, lines.line.timeAt(word.start(), lines.nextStart) - lines.position));
        }
        double total = WORD_GAP * (widths.size() - 1.0);
        for (int width : widths) total += width;
        // Radians of view per font pixel; long lines shrink so the words stay within the view.
        double perPixel = Math.min(WORD_PIXEL_SIZE_PER_BLOCK, MAX_WORD_SPREAD / Math.max(1.0, total));
        double distance = Math.max(4.0, Math.min(48.0, config.textDistance3D));
        List<Piece> pieces = new ArrayList<>();
        double cursor = -total / 2.0;
        for (int i = 0; i < visuals.size(); i++) {
            double center = cursor + widths.get(i) / 2.0;
            cursor += widths.get(i) + WORD_GAP;
            // Reading order runs left to right, or right to left for Arabic, Persian and Hebrew.
            float yaw = camera.yRot + (float)Math.toDegrees((rightToLeft ? -center : center) * perPixel);
            List<Row> rows = List.of(new Row(visuals.get(i), widths.get(i), lines.color, lines.outline, 1.0f));
            pieces.add(placeWord(minecraft, camera.pos, yaw, distance, perPixel, delays.get(i), rows));
        }
        return pieces;
    }

    private static Piece placeWord(Minecraft minecraft, Vec3 eye, float yaw, double distance, double perPixel, long delay, List<Row> rows) {
        Vec3 direction = Entity.calculateViewVector(0.0f, yaw);
        Row row = rows.getFirst();
        float tall = minecraft.font.lineHeight + 2;
        BlockHitResult wall = clip(minecraft, eye, eye.add(direction.scale(distance)), ClipContext.Fluid.NONE);
        if (wall.getType() == HitResult.Type.BLOCK && wall.getDirection().getAxis().isHorizontal()) {
            Direction face = wall.getDirection();
            // Painted just off the face so it does not flicker against the block texture, and made smaller when it is
            // wider than the face it landed on.
            Vec3 pos = wall.getLocation().add(face.getStepX() * 0.02, 0.0, face.getStepZ() * 0.02);
            Quaternionf rotation = facing(face.getOpposite().toYRot(), 0.0f);
            for (double shrink : SHRINK) {
                float scale = (float)(wall.getLocation().distanceTo(eye) * perPixel * shrink);
                if (fitsOnFace(minecraft, eye, pos, rotation, scale, row.width, tall, face)) return new Piece(pos, rotation, scale, delay, Motion.STAMP, rows);
            }
        }
        double reach = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation().distanceTo(eye) - 0.5 : distance;
        // Blocks or tall grass between the player and the spot, like a low wall, would hide the word or cut through it,
        // so it tries spots closer in. Over the obstacle itself the drop lands on top of it, so the word ends up standing
        // on what was in the way.
        Piece best = null;
        int fewest = Integer.MAX_VALUE;
        for (double fraction : PULL_IN) {
            Vec3 ahead = eye.add(direction.scale(Math.max(2.0, reach * fraction)));
            Piece piece = dropWord(minecraft, eye, ahead, yaw, perPixel, delay, rows);
            int hidden = hiddenPoints(minecraft, eye, piece.pos, piece.rotation, piece.scale, row.width, tall, ClipContext.Block.OUTLINE);
            if (hidden < fewest) {
                fewest = hidden;
                best = piece;
            }
            if (hidden == 0) return piece;
        }
        // On rough ground with no clear spot, the word floats at eye height instead of sinking into the terrain.
        for (double fraction : PULL_IN) {
            Vec3 ahead = eye.add(direction.scale(Math.max(2.0, reach * fraction)));
            Piece piece = new Piece(ahead, facing(yaw, 0.0f), (float)(ahead.distanceTo(eye) * perPixel), delay, Motion.FLOAT, rows);
            if (hiddenPoints(minecraft, eye, piece.pos, piece.rotation, piece.scale, row.width, tall, ClipContext.Block.OUTLINE) == 0) return piece;
        }
        return best;
    }

    private static Piece dropWord(Minecraft minecraft, Vec3 eye, Vec3 ahead, float yaw, double perPixel, long delay, List<Row> rows) {
        // Plants count as ground here, so a word stands on top of tall grass instead of half inside it.
        BlockHitResult ground = clip(minecraft, ahead, ahead.subtract(0.0, 24.0, 0.0), ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY);
        if (ground.getType() == HitResult.Type.BLOCK && ground.getDirection() == Direction.UP) {
            // Flat on the ground the word would be squashed past reading at a distance, so it stands on the block,
            // its lowest letters just above the top face.
            float scale = (float)(ground.getLocation().distanceTo(eye) * perPixel);
            Vec3 pos = ground.getLocation().add(0.0, 3.0 * scale + 0.05, 0.0);
            return new Piece(pos, facing(yaw, 0.0f), scale, delay, Motion.DROP, rows);
        }
        // Over a drop too deep to reach, the word floats where the ground would be.
        return new Piece(ahead, facing(yaw, 0.0f), (float)(ahead.distanceTo(eye) * perPixel), delay, Motion.FLOAT, rows);
    }

    // Same convention as the camera: the front faces a viewer looking along (pitch, yaw).
    private static Quaternionf facing(float yaw, float pitch) {
        return new Quaternionf().rotationYXZ((float)Math.PI - (float)Math.toRadians(yaw), -(float)Math.toRadians(pitch), 0.0f);
    }

    // offset: 1 when a piece starts arriving, 0 at rest, down to -1 as it leaves.
    private static void submit(LevelRenderContext context, CameraRenderState camera, Piece piece, float alpha, float offset, boolean twoSided, long now) {
        if (alpha <= 0.03f) return;
        PoseStack poses = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        float arriving = Math.max(0.0f, offset);
        poses.pushPose();
        poses.translate(piece.pos.subtract(camera.pos));
        if (piece.motion == Motion.DROP) poses.translate(0.0f, 1.2f * arriving, 0.0f);
        poses.rotate(piece.rotation);
        float scale = piece.scale * (piece.motion == Motion.STAMP ? 1.0f + 0.35f * arriving : 1.0f);
        poses.scale(scale, -scale, scale);
        // A slow bob sells the hologram look without moving the line far enough to hurt readability.
        if (piece.motion == Motion.FLOAT) poses.translate(0.0f, 8.0f * offset + (float)Math.sin(now / 650.0) * 1.5f, 0.0f);
        boolean ghost = piece.motion == Motion.FLOAT;
        submitRows(poses, collector, piece.rows, alpha, ghost);
        // Painted words sit on a wall, so their back would be inside the block.
        if (twoSided && piece.motion != Motion.STAMP) {
            // Text is back-face culled, so a mirrored copy keeps the line readable after walking past it.
            poses.rotate(new Quaternionf().rotationY((float)Math.PI));
            submitRows(poses, collector, piece.rows, alpha, ghost);
        }
        poses.popPose();
    }

    private static void submitRows(PoseStack poses, SubmitNodeCollector collector, List<Row> rows, float alpha, boolean ghost) {
        Font font = Minecraft.getInstance().font;
        float height = 0.0f;
        for (Row row : rows) height += (font.lineHeight + 2) * row.size;
        float y = -height / 2.0f;
        for (Row row : rows) {
            poses.pushPose();
            poses.translate(0.0f, y, 0.0f);
            poses.scale(row.size, row.size, row.size);
            float x = -row.width / 2.0f;
            int color = ARGB.multiplyAlpha(row.color, alpha);
            // Fonts treat alpha below 4 as opaque, so nearly invisible rows are skipped instead.
            if (ARGB.alpha(color) >= 8) {
                int light = LightCoordsUtil.FULL_BRIGHT;
                collector.submitText(poses, x, 0.0f, row.text, false, Font.DisplayMode.NORMAL, light, color, 0,
                    ARGB.multiplyAlpha(row.outline, alpha));
                // A faint copy that ignores depth keeps a floating line findable behind hills and walls.
                int faint = ARGB.multiplyAlpha(color, 0.25f);
                if (ghost && ARGB.alpha(faint) >= 8)
                    collector.submitText(poses, x, 0.0f, row.text, false, Font.DisplayMode.SEE_THROUGH, light, faint, 0, 0);
            }
            poses.popPose();
            y += (font.lineHeight + 2) * row.size;
        }
    }
}
