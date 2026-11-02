package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.shader.ProjectionUtil;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import egg.client.util.LyricsFetcher;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.*;

public class Lyrics extends Module {
    // 3D Capsule and positioning settings matching the reference design
    private final ModeSetting splitMode = new ModeSetting("Разбивка", "По фразам", "По фразам", "По запятой", "Вся строка");
    private final SliderSetting wordsPerCapsule = new SliderSetting("Слов в капсуле", 2.0, 1.0, 4.0, 1.0, "Количество слов на одну 3D плашку");
    private final SliderSetting distance = new SliderSetting("Дистанция", 4.0, 2.0, 12.0, 0.5, "Базовая дистанция появления в мире (в блоках)");
    private final SliderSetting scatterH = new SliderSetting("Разброс X", 3.0, 0.5, 8.0, 0.5, "Разброс капсул влево/вправо");
    private final SliderSetting scatterV = new SliderSetting("Разброс Y", 1.5, 0.2, 5.0, 0.2, "Разброс капсул вверх/вниз");
    private final SliderSetting size = new SliderSetting("Размер", 1.1, 0.5, 2.5, 0.05, "Масштаб капсул");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 5.5, 2.0, 15.0, 0.5, "Сколько секунд капсула держится в мире");
    private final ModeSetting colorStyle = new ModeSetting("Стиль цвета", "Коралловый", "Коралловый", "Белый", "Тема", "Лавандовый");
    private final BooleanSetting background = new BooleanSetting("Фон плашки", true, "Полупрозрачная 3D плашка капсулы");
    private final BooleanSetting glow = new BooleanSetting("Неоновый контур", true, "Тонкая неоновая обводка вокруг капсулы");
    private final BooleanSetting gentleWave = new BooleanSetting("Живое покачивание", true, "Плавный физический дрейф капсул в 3D пространстве");
    private final BooleanSetting noOverlap = new BooleanSetting("Не накладывать", true, "Умный поиск свободного сектора во избежание перекрытия");
    private final BooleanSetting demoMode = new BooleanSetting("Демо-режим", false, "Предпросмотр капсул из референса без Spotify");

    // 3D Active World Capsule representation
    public static class Capsule3D {
        public final Vec3d worldPos;
        public final String text;
        public final long spawnTime;
        public final long lifeDurationMs;
        public final float seed;
        public boolean disappearing = false;
        public long disappearStartTime = 0L;

        public Capsule3D(Vec3d worldPos, String text, long spawnTime, long lifeDurationMs, float seed) {
            this.worldPos = worldPos;
            this.text = text;
            this.spawnTime = spawnTime;
            this.lifeDurationMs = lifeDurationMs;
            this.seed = seed;
        }
    }

    // Scheduled chunk for staggered phrase emergence
    private static class ScheduledChunk {
        final String text;
        final long spawnAtTimeMs;
        final long lifeMs;

        ScheduledChunk(String text, long spawnAtTimeMs, long lifeMs) {
            this.text = text;
            this.spawnAtTimeMs = spawnAtTimeMs;
            this.lifeMs = lifeMs;
        }
    }

    private final List<Capsule3D> activeCapsules = new ArrayList<>();
    private final List<ScheduledChunk> pendingQueue = new ArrayList<>();
    private final Random rng = new Random();

    private List<LyricLine> activeLyrics = null;
    private String lastSongKey = "";
    private int currentLineIndex = -1;
    private float lastSide = 1.0f;
    private float overallAlpha = 0.0f;

    private boolean manualPlayback = false;
    private String manualArtist = "";
    private String manualTitle = "";
    private long manualStartTime = 0L;
    private long manualOffsetMs = 0L;
    private long demoPlayTimeMs = 0L;
    private long lastFrameNanos = 0L;

    public Lyrics() {
        super("Lyrics", "3D капсулы с текстом песни в мире", Category.ESP);
        addSettings(
                splitMode, wordsPerCapsule, distance, scatterH, scatterV, size,
                lifetime, colorStyle, background, glow, gentleWave, noOverlap, demoMode
        );
    }

    @Override
    public void onEnable() {
        overallAlpha = 1.0f;
        activeCapsules.clear();
        pendingQueue.clear();
        currentLineIndex = -1;
        if (demoMode.isEnabled()) {
            playDemo();
        }
    }

    @Override
    public void onDisable() {
        activeCapsules.clear();
        pendingQueue.clear();
        activeLyrics = null;
        lastSongKey = "";
        currentLineIndex = -1;
        overallAlpha = 0.0f;
    }

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        long nowNanos = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = nowNanos;
        float dt = (float) ((nowNanos - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = nowNanos;
        dt = MathHelper.clamp(dt, 0.001f, 0.1f);

        long now = System.currentTimeMillis();

        // Check demo mode toggle
        if (demoMode.isEnabled() && (!manualPlayback || !"demo".equals(lastSongKey))) {
            playDemo();
        } else if (!demoMode.isEnabled() && manualPlayback && "demo".equals(lastSongKey)) {
            stopManual();
        }

        // 1. Determine playback position and active lyrics
        long currentPosMs;
        if (manualPlayback) {
            if ("demo".equals(lastSongKey)) {
                demoPlayTimeMs += (long) (dt * 1000.0f);
                long totalDemoLen = 25000L;
                currentPosMs = demoPlayTimeMs % totalDemoLen;
            } else {
                currentPosMs = (now - manualStartTime) + manualOffsetMs;
            }
        } else {
            MediaTracker mt = MediaTracker.getInstance();
            if (!mt.isPlaying()) {
                overallAlpha = Math.max(0.0f, overallAlpha - dt * 2.0f);
            } else {
                overallAlpha = Math.min(1.0f, overallAlpha + dt * 3.0f);
            }

            String curArtist = mt.getTrackArtist();
            String curTitle = mt.getTrackTitle();
            String songKey = LyricsFetcher.makeKey(curArtist, curTitle);
            if (!songKey.equals(lastSongKey)) {
                lastSongKey = songKey;
                activeLyrics = LyricsFetcher.getInstance().getCachedLyrics(curArtist, curTitle);
                if (activeLyrics == null) {
                    LyricsFetcher.getInstance().fetchLyricsAsync(curArtist, curTitle)
                            .thenAccept(lines -> {
                                if (lines != null && !lines.isEmpty()) {
                                    activeLyrics = lines;
                                }
                            });
                }
                currentLineIndex = -1;
                activeCapsules.clear();
                pendingQueue.clear();
            }
            currentPosMs = mt.getTrackElapsedMs();
        }

        // 2. Track current line and spawn new phrases
        if (activeLyrics != null && !activeLyrics.isEmpty()) {
            int lineIdx = -1;
            for (int i = 0; i < activeLyrics.size(); i++) {
                if (activeLyrics.get(i).timeMs() <= currentPosMs) {
                    lineIdx = i;
                } else {
                    break;
                }
            }

            if (lineIdx >= 0 && lineIdx != currentLineIndex) {
                currentLineIndex = lineIdx;
                LyricLine currentLine = activeLyrics.get(lineIdx);
                long nextTime = lineIdx + 1 < activeLyrics.size() ? activeLyrics.get(lineIdx + 1).timeMs() : currentLine.timeMs() + 4500L;
                long lineDuration = Math.max(1200L, nextTime - currentLine.timeMs());
                breakAndSchedulePhrases(currentLine.text(), now, lineDuration);
            }
        }

        // 3. Process pending phrase queue
        Iterator<ScheduledChunk> queueIter = pendingQueue.iterator();
        while (queueIter.hasNext()) {
            ScheduledChunk chunk = queueIter.next();
            if (now >= chunk.spawnAtTimeMs) {
                spawnCapsuleInWorld(chunk.text, now, chunk.lifeMs, camera);
                queueIter.remove();
            }
        }

        // 4. Update and prune active capsules
        Iterator<Capsule3D> capIter = activeCapsules.iterator();
        while (capIter.hasNext()) {
            Capsule3D cap = capIter.next();
            long age = now - cap.spawnTime;
            if (age >= cap.lifeDurationMs && !cap.disappearing) {
                cap.disappearing = true;
                cap.disappearStartTime = now;
            }
            if (cap.disappearing && now - cap.disappearStartTime >= 700L) {
                capIter.remove();
            }
        }

        if (activeCapsules.isEmpty()) return;

        // 5. Render 3D World Capsules
        render3DCapsules(camera, now, dt);
    }

    private void breakAndSchedulePhrases(String rawText, long now, long lineDuration) {
        if (rawText == null || rawText.isBlank()) return;
        String text = rawText.replaceAll("<[^>]+>", "").trim();
        if (text.isEmpty()) return;

        List<String> chunks = new ArrayList<>();
        String mode = splitMode.get();

        if ("По запятой".equals(mode)) {
            for (String p : text.split("[,;—–]")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) chunks.add(trimmed);
            }
        } else if ("Вся строка".equals(mode)) {
            chunks.add(text);
        } else {
            // "По фразам" (2-3 words per capsule, as seen in screenshots)
            String[] words = text.split("\\s+");
            int groupSize = Math.max(1, (int) wordsPerCapsule.getValue());
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String w : words) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w);
                count++;
                if (count >= groupSize) {
                    chunks.add(sb.toString());
                    sb.setLength(0);
                    count = 0;
                }
            }
            if (sb.length() > 0) {
                chunks.add(sb.toString());
            }
        }

        if (chunks.isEmpty()) return;

        long capLifeMs = (long) (lifetime.getValue() * 1000.0);
        long stepMs = chunks.size() <= 1 ? 0L : Math.min(1800L, Math.max(600L, lineDuration / chunks.size()));

        for (int i = 0; i < chunks.size(); i++) {
            long spawnAt = now + i * stepMs;
            pendingQueue.add(new ScheduledChunk(chunks.get(i), spawnAt, capLifeMs));
        }
    }

    private void spawnCapsuleInWorld(String text, long now, long lifeMs, Camera camera) {
        Vec3d eye = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosP = MathHelper.cos(pitchRad);
        float sinP = MathHelper.sin(pitchRad);
        float cosY = MathHelper.cos(yawRad);
        float sinY = MathHelper.sin(yawRad);

        Vec3d F = new Vec3d(-sinY * cosP, -sinP, cosY * cosP).normalize();
        Vec3d R = new Vec3d(-cosY, 0.0, -sinY).normalize();
        Vec3d U = R.crossProduct(F).normalize();

        float baseDist = (float) distance.getValue();
        float hScatter = (float) scatterH.getValue();
        float vScatter = (float) scatterV.getValue();

        // Alternate sides to balance composition
        lastSide = -lastSide;
        float side = lastSide;
        if (rng.nextFloat() < 0.20f) {
            side = -side;
        }

        // Candidate positions with staggered depth & non-overlapping search
        List<Vec3d> candidates = new ArrayList<>();
        float[] depths = {baseDist, baseDist * 0.90f, baseDist * 1.15f, baseDist * 0.80f};

        for (float d : depths) {
            float ox1 = side * (0.35f + rng.nextFloat() * 0.65f) * hScatter;
            float oy1 = (rng.nextFloat() - 0.5f) * 2.0f * vScatter;
            candidates.add(eye.add(F.multiply(d)).add(R.multiply(ox1)).add(U.multiply(oy1)));

            float ox2 = -side * (0.35f + rng.nextFloat() * 0.65f) * hScatter;
            float oy2 = (rng.nextFloat() - 0.5f) * 2.0f * vScatter;
            candidates.add(eye.add(F.multiply(d)).add(R.multiply(ox2)).add(U.multiply(oy2)));

            float ox3 = side * 0.15f * hScatter;
            float oy3 = (rng.nextFloat() - 0.5f) * 2.0f * vScatter;
            candidates.add(eye.add(F.multiply(d)).add(R.multiply(ox3)).add(U.multiply(oy3)));
        }

        Vec3d bestPos = candidates.get(0);
        float lowestPenalty = Float.MAX_VALUE;

        if (noOverlap.isEnabled() && !activeCapsules.isEmpty()) {
            for (Vec3d cand : candidates) {
                Vector2f candScreen = ProjectionUtil.worldToScreen(cand);
                if (candScreen == null) continue;

                float penalty = 0.0f;
                for (Capsule3D other : activeCapsules) {
                    Vector2f otherScreen = ProjectionUtil.worldToScreen(other.worldPos);
                    if (otherScreen == null) continue;

                    float dx = Math.abs(candScreen.x - otherScreen.x);
                    float dy = Math.abs(candScreen.y - otherScreen.y);
                    float minSpacingX = 90.0f;
                    float minSpacingY = 40.0f;

                    if (dx < minSpacingX && dy < minSpacingY) {
                        penalty += (minSpacingX - dx) * 1.2f + (minSpacingY - dy) * 2.0f;
                    }
                }

                if (penalty < lowestPenalty) {
                    lowestPenalty = penalty;
                    bestPos = cand;
                    if (penalty <= 0.01f) break;
                }
            }
        } else {
            bestPos = candidates.get(rng.nextInt(Math.min(candidates.size(), 3)));
        }

        Capsule3D capsule = new Capsule3D(bestPos, text, now, lifeMs, rng.nextFloat() * 100.0f);
        activeCapsules.add(capsule);

        // Cap max simultaneous capsules to avoid screen clutter
        if (activeCapsules.size() > 10) {
            activeCapsules.get(0).disappearing = true;
            activeCapsules.get(0).disappearStartTime = now;
        }
    }

    private void render3DCapsules(Camera camera, long now, float dt) {
        Vec3d eye = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosP = MathHelper.cos(pitchRad);
        float sinP = MathHelper.sin(pitchRad);
        float cosY = MathHelper.cos(yawRad);
        float sinY = MathHelper.sin(yawRad);

        Vec3d F = new Vec3d(-sinY * cosP, -sinP, cosY * cosP).normalize();
        Vec3d R = new Vec3d(-cosY, 0.0, -sinY).normalize();
        Vec3d U = R.crossProduct(F).normalize();

        // 3D Depth Sorting: Farthest first, closest last (correct painter's algorithm)
        activeCapsules.sort((c1, c2) -> Double.compare(eye.squaredDistanceTo(c2.worldPos), eye.squaredDistanceTo(c1.worldPos)));

        float userScale = (float) size.getValue();

        for (Capsule3D cap : activeCapsules) {
            // Gentle physical bobbing & drift in 3D
            float timeSec = (now - cap.spawnTime) / 1000.0f;
            float waveY = gentleWave.isEnabled() ? (float) Math.sin(timeSec * 2.0f + cap.seed) * 0.07f : 0.0f;
            float waveX = gentleWave.isEnabled() ? (float) Math.cos(timeSec * 1.5f + cap.seed * 1.3f) * 0.04f : 0.0f;
            Vec3d currentPos = cap.worldPos.add(R.multiply(waveX)).add(U.multiply(waveY));

            Vector2f screenPos = ProjectionUtil.worldToScreen(currentPos);
            if (screenPos == null) continue;

            double dist = eye.distanceTo(currentPos);
            float distFactor = (float) (4.0 / Math.max(dist, 0.7));
            float pxScale = MathHelper.clamp(userScale * distFactor, 0.55f, 2.3f);

            // Appear bounce & fade in (first 250ms)
            float appear = MathHelper.clamp((now - cap.spawnTime) / 250.0f, 0.0f, 1.0f);
            appear = appear * appear * (3.0f - 2.0f * appear);
            float popScale = 0.88f + 0.12f * appear;

            // Disappear fade out (last 700ms)
            long elapsed = now - cap.spawnTime;
            long remaining = cap.lifeDurationMs - elapsed;
            float fade = 1.0f;
            if (cap.disappearing) {
                fade = 1.0f - MathHelper.clamp((now - cap.disappearStartTime) / 700.0f, 0.0f, 1.0f);
            } else if (remaining < 700L) {
                fade = MathHelper.clamp(remaining / 700.0f, 0.0f, 1.0f);
            }

            float alpha = appear * fade * (manualPlayback ? 1.0f : overallAlpha);
            if (alpha <= 0.015f) continue;

            // Measure capsule dimensions
            float fontSize = 13.5f * pxScale * popScale;
            float textW = Fonts.d.a(cap.text, fontSize);
            float textH = Fonts.d.a(fontSize);
            float padX = 14.0f * pxScale;
            float padY = 7.5f * pxScale;
            float boxW = textW + padX * 2.0f;
            float boxH = textH + padY * 2.0f;
            float radius = boxH / 2.0f; // Rounded capsule / pill shape

            float bx = screenPos.x - boxW / 2.0f;
            float by = screenPos.y - boxH / 2.0f;

            // Colors based on style
            int textColor;
            int glowColor;
            int bgCol;

            String style = colorStyle.get();
            if ("Белый".equals(style)) {
                // Photo 2 reference: Clean monochrome / white
                textColor = ColorUtil.rgba(240, 242, 248, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 255, 255, (int) (130 * alpha));
                bgCol = ColorUtil.rgba(14, 15, 20, (int) (190 * alpha));
            } else if ("Тема".equals(style)) {
                int accent = Theme.getAccent();
                textColor = ColorUtil.withAlphaFloat(accent, alpha);
                glowColor = ColorUtil.withAlphaFloat(accent, 0.55f * alpha);
                bgCol = ColorUtil.rgba(12, 14, 20, (int) (185 * alpha));
            } else if ("Лавандовый".equals(style)) {
                textColor = ColorUtil.rgba(215, 185, 255, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(195, 160, 255, (int) (150 * alpha));
                bgCol = ColorUtil.rgba(16, 14, 24, (int) (185 * alpha));
            } else {
                // Photo 1 reference: Soft coral / salmon pink
                textColor = ColorUtil.rgba(255, 158, 170, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 135, 155, (int) (150 * alpha));
                bgCol = ColorUtil.rgba(15, 16, 22, (int) (185 * alpha));
            }

            // Draw pill capsule background
            if (background.isEnabled()) {
                Draw2DProcessor.INSTANCE.drawRect(null, bx, by, boxW, boxH, radius, bgCol);

                // Draw pill outline / neon glow
                if (glow.isEnabled()) {
                    float outW = Math.max(1.0f, 1.0f * pxScale);
                    Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, boxW, boxH, radius, outW, glowColor);
                }
            }

            // Draw centered text inside the 3D capsule
            float textX = bx + padX;
            float textY = by + padY - 0.5f;
            Fonts.d.a(null, cap.text, textX, textY, fontSize, textColor);
        }
    }

    public void playDemo() {
        this.demoMode.setEnabled(true);
        this.manualPlayback = true;
        this.demoPlayTimeMs = 0L;
        this.activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
        this.currentLineIndex = -1;
        this.lastSongKey = "demo";
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        this.overallAlpha = 1.0f;
    }

    public void playManual(String artist, String title) {
        this.manualPlayback = true;
        this.demoMode.setEnabled(false);
        this.manualArtist = artist != null ? artist.trim() : "";
        this.manualTitle = title != null ? title.trim() : "";
        this.manualStartTime = System.currentTimeMillis();
        this.manualOffsetMs = 0L;
        this.currentLineIndex = -1;
        this.lastSongKey = LyricsFetcher.makeKey(artist, title);
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        this.overallAlpha = 1.0f;

        this.activeLyrics = LyricsFetcher.getInstance().getCachedLyrics(artist, title);
        if (this.activeLyrics == null) {
            LyricsFetcher.getInstance().fetchLyricsAsync(artist, title)
                    .thenAccept(lines -> {
                        if (lines != null && !lines.isEmpty()) {
                            this.activeLyrics = lines;
                        }
                    });
        }
    }

    public void stopManual() {
        this.manualPlayback = false;
        this.demoMode.setEnabled(false);
        this.overallAlpha = 0.0f;
        this.activeLyrics = null;
        this.lastSongKey = "";
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
    }

    public void seekManual(long seconds) {
        this.manualOffsetMs = seconds * 1000L;
        this.manualStartTime = System.currentTimeMillis();
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
    }
}
