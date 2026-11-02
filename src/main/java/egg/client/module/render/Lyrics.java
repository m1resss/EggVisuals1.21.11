package egg.client.module.render;

import egg.client.command.CommandManager;
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
    private final ModeSetting splitMode = new ModeSetting("Разбивка", "По фразам", "По фразам", "По запятой", "Вся строка");
    private final SliderSetting wordsPerCapsule = new SliderSetting("Слов в плашке", 2.0, 1.0, 4.0, 1.0, "Количество слов на одну 3D плашку");
    private final SliderSetting distance = new SliderSetting("Дистанция", 4.0, 2.0, 10.0, 0.5, "Базовая дальность плашек в мире (блоки)");
    private final SliderSetting scatterH = new SliderSetting("Разброс X", 5.5, 1.0, 9.0, 0.5, "Ширина разброса влево/вправо");
    private final SliderSetting scatterV = new SliderSetting("Разброс Y", 2.2, 0.5, 6.0, 0.2, "Разброс по высоте");
    private final SliderSetting size = new SliderSetting("Масштаб", 1.0, 0.4, 2.5, 0.1, "Базовый размер плашек");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 6.0, 2.0, 14.0, 0.5, "Время отображения фразы в секундах");

    private final ModeSetting colorStyle = new ModeSetting("Цвет текста", "Коралловый", "Коралловый", "Белый", "Лавандовый", "Тема");
    private final BooleanSetting background = new BooleanSetting("Фон плашки", true, "Отображать темную полупрозрачную подложку");
    private final BooleanSetting glow = new BooleanSetting("Неоновое свечение", true, "Свечение контура капсулы");
    private final BooleanSetting gentleWave = new BooleanSetting("Живое покачивание", true, "Плавное 3D покачивание плашек в пространстве");
    private final BooleanSetting noOverlap = new BooleanSetting("Не накладывать", true, "Умное распределение позиций без перекрытия");
    private final BooleanSetting demoMode = new BooleanSetting("Демо режим", false, "Принудительный показ тестовых фраз");

    private static class Capsule3D {
        Vec3d worldPos;
        final String text;
        final long spawnTime;
        final long lifeDurationMs;
        final float seed;
        final float depthTier;
        boolean disappearing = false;
        long disappearStartTime = 0L;

        Capsule3D(Vec3d worldPos, String text, long spawnTime, long lifeDurationMs, float seed, float depthTier) {
            this.worldPos = worldPos;
            this.text = text;
            this.spawnTime = spawnTime;
            this.lifeDurationMs = lifeDurationMs;
            this.seed = seed;
            this.depthTier = depthTier;
        }
    }

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
    private float overallAlpha = 1.0f;

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

        if (demoMode.isEnabled() && (!manualPlayback || !"demo".equals(lastSongKey))) {
            playDemo();
        } else if (!demoMode.isEnabled() && manualPlayback && "demo".equals(lastSongKey)) {
            stopManual();
        }

        long currentPosMs;
        if (manualPlayback) {
            overallAlpha = 1.0f;
            if ("demo".equals(lastSongKey)) {
                demoPlayTimeMs += (long) (dt * 1000.0f);
                long totalDemoLen = 24000L;
                currentPosMs = demoPlayTimeMs % totalDemoLen;
            } else {
                currentPosMs = (now - manualStartTime) + manualOffsetMs;
            }
        } else {
            MediaTracker mt = MediaTracker.getInstance();
            String curArtist = mt.getTrackArtist();
            String curTitle = mt.getTrackTitle();
            boolean hasTrack = !curTitle.isEmpty();

            if (mt.isPlaying() || hasTrack) {
                overallAlpha = Math.min(1.0f, overallAlpha + dt * 3.0f);
            } else {
                overallAlpha = Math.max(0.0f, overallAlpha - dt * 0.8f);
            }

            if (hasTrack) {
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
            }
            currentPosMs = mt.getTrackElapsedMs();
        }

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
                long nextTime = lineIdx + 1 < activeLyrics.size() ? activeLyrics.get(lineIdx + 1).timeMs() : currentLine.timeMs() + 5000L;
                long lineDuration = Math.max(1400L, nextTime - currentLine.timeMs());
                breakAndSchedulePhrases(currentLine.text(), now, lineDuration);
            }
        }

        Iterator<ScheduledChunk> queueIter = pendingQueue.iterator();
        while (queueIter.hasNext()) {
            ScheduledChunk chunk = queueIter.next();
            if (now >= chunk.spawnAtTimeMs) {
                spawnCapsuleInWorld(chunk.text, now, chunk.lifeMs, camera);
                queueIter.remove();
            }
        }

        Iterator<Capsule3D> capIter = activeCapsules.iterator();
        while (capIter.hasNext()) {
            Capsule3D cap = capIter.next();
            long age = now - cap.spawnTime;
            if (age >= cap.lifeDurationMs && !cap.disappearing) {
                cap.disappearing = true;
                cap.disappearStartTime = now;
            }
            if (cap.disappearing && now - cap.disappearStartTime >= 750L) {
                capIter.remove();
            }
        }

        if (activeCapsules.isEmpty()) return;

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
        long stepMs = chunks.size() <= 1 ? 0L : Math.min(1600L, Math.max(600L, lineDuration / chunks.size()));

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

        lastSide = -lastSide;
        float side = lastSide;
        if (rng.nextFloat() < 0.15f) {
            side = -side;
        }

        float[] depthMultipliers = {0.62f, 1.0f, 1.45f, 0.85f, 1.25f};
        float pickedDepthMult = depthMultipliers[rng.nextInt(depthMultipliers.length)];
        float d = baseDist * pickedDepthMult;

        List<Vec3d> candidates = new ArrayList<>();
        float[] xOffsets = {
                side * (0.45f + rng.nextFloat() * 0.55f) * hScatter,
                -side * (0.40f + rng.nextFloat() * 0.60f) * hScatter,
                side * 0.20f * hScatter,
                -side * 0.85f * hScatter,
                0.0f
        };

        for (float ox : xOffsets) {
            float oy = (rng.nextFloat() - 0.5f) * 2.0f * vScatter;
            candidates.add(eye.add(F.multiply(d)).add(R.multiply(ox)).add(U.multiply(oy)));
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
                    float minSpacingX = 110.0f;
                    float minSpacingY = 46.0f;

                    if (dx < minSpacingX && dy < minSpacingY) {
                        penalty += (minSpacingX - dx) * 1.5f + (minSpacingY - dy) * 2.5f;
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

        Capsule3D capsule = new Capsule3D(bestPos, text, now, lifeMs, rng.nextFloat() * 100.0f, pickedDepthMult);
        activeCapsules.add(capsule);

        if (activeCapsules.size() > 12) {
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

        activeCapsules.sort((c1, c2) -> Double.compare(eye.squaredDistanceTo(c2.worldPos), eye.squaredDistanceTo(c1.worldPos)));

        float userScale = (float) size.getValue();

        for (Capsule3D cap : activeCapsules) {
            float timeSec = (now - cap.spawnTime) / 1000.0f;
            float waveY = gentleWave.isEnabled() ? (float) Math.sin(timeSec * 1.8f + cap.seed) * 0.08f : 0.0f;
            float waveX = gentleWave.isEnabled() ? (float) Math.cos(timeSec * 1.4f + cap.seed * 1.2f) * 0.05f : 0.0f;
            Vec3d currentPos = cap.worldPos.add(R.multiply(waveX)).add(U.multiply(waveY));

            Vector2f screenPos = ProjectionUtil.worldToScreen(currentPos);
            if (screenPos == null) continue;

            double dist = eye.distanceTo(currentPos);
            float distFactor = (float) (4.2 / Math.max(dist, 0.8));
            float pxScale = MathHelper.clamp(userScale * distFactor, 0.50f, 2.6f);

            float appear = MathHelper.clamp((now - cap.spawnTime) / 280.0f, 0.0f, 1.0f);
            appear = appear * appear * (3.0f - 2.0f * appear);
            float popScale = 0.85f + 0.15f * appear;

            long elapsed = now - cap.spawnTime;
            long remaining = cap.lifeDurationMs - elapsed;
            float fade = 1.0f;
            if (cap.disappearing) {
                fade = 1.0f - MathHelper.clamp((now - cap.disappearStartTime) / 750.0f, 0.0f, 1.0f);
            } else if (remaining < 750L) {
                fade = MathHelper.clamp(remaining / 750.0f, 0.0f, 1.0f);
            }

            float alpha = appear * fade * (manualPlayback ? 1.0f : overallAlpha);
            if (alpha <= 0.015f) continue;

            float fontSize = 13.5f * pxScale * popScale;
            float textW = Fonts.d.a(cap.text, fontSize);
            float textH = Fonts.d.a(fontSize);
            float padX = 14.5f * pxScale;
            float padY = 7.5f * pxScale;
            float boxW = textW + padX * 2.0f;
            float boxH = textH + padY * 2.0f;
            float radius = boxH / 2.0f;

            float bx = screenPos.x - boxW / 2.0f;
            float by = screenPos.y - boxH / 2.0f;

            int textColor;
            int glowColor;
            int bgCol;

            String style = colorStyle.get();
            if ("Белый".equals(style)) {
                textColor = ColorUtil.rgba(242, 244, 250, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 255, 255, (int) (130 * alpha));
                bgCol = ColorUtil.rgba(14, 15, 20, (int) (185 * alpha));
            } else if ("Тема".equals(style)) {
                int accent = Theme.getAccent();
                textColor = ColorUtil.withAlphaFloat(accent, alpha);
                glowColor = ColorUtil.withAlphaFloat(accent, 0.60f * alpha);
                bgCol = ColorUtil.rgba(12, 14, 20, (int) (185 * alpha));
            } else if ("Лавандовый".equals(style)) {
                textColor = ColorUtil.rgba(215, 185, 255, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(195, 160, 255, (int) (150 * alpha));
                bgCol = ColorUtil.rgba(16, 14, 24, (int) (185 * alpha));
            } else {
                textColor = ColorUtil.rgba(255, 158, 170, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 135, 155, (int) (150 * alpha));
                bgCol = ColorUtil.rgba(15, 16, 22, (int) (185 * alpha));
            }

            if (background.isEnabled()) {
                Draw2DProcessor.INSTANCE.drawRect(null, bx, by, boxW, boxH, radius, bgCol);

                if (glow.isEnabled()) {
                    float outW = Math.max(1.0f, 1.0f * pxScale);
                    Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, boxW, boxH, radius, outW, glowColor);
                }
            }

            float textX = bx + padX;
            float textY = by + padY - 0.5f;
            Fonts.d.a(null, cap.text, textX, textY, fontSize, textColor);
        }
    }

    public void playDemo() {
        this.demoMode.setEnabled(true);
        this.manualPlayback = true;
        this.lastSongKey = "demo";
        this.demoPlayTimeMs = 0L;
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        this.overallAlpha = 1.0f;

        List<LyricLine> demo = new ArrayList<>();
        demo.add(new LyricLine(0L, "whats wrong im sick"));
        demo.add(new LyricLine(2800L, "with and twisted"));
        demo.add(new LyricLine(5800L, "i confess"));
        demo.add(new LyricLine(8800L, "fuck the opps"));
        demo.add(new LyricLine(12000L, "don't ever fuck with world"));
        demo.add(new LyricLine(15500L, "we Haunte"));
        demo.add(new LyricLine(19000L, "whats wrong im sick"));

        this.activeLyrics = demo;
        CommandManager.sendClientMessage("§d[Lyrics] §fЗапущен демонстрационный трек 3D капсул", false);
    }

    public void playManual(String artist, String title) {
        this.demoMode.setEnabled(false);
        this.manualPlayback = true;
        this.manualArtist = artist != null ? artist : "";
        this.manualTitle = title != null ? title : "";
        this.manualStartTime = System.currentTimeMillis();
        this.manualOffsetMs = 0L;
        this.lastSongKey = LyricsFetcher.makeKey(this.manualArtist, this.manualTitle);
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        this.overallAlpha = 1.0f;

        CommandManager.sendClientMessage("§d[Lyrics] §fПоиск текста для: §e" + (this.manualArtist.isEmpty() ? "" : this.manualArtist + " - ") + this.manualTitle, false);
        LyricsFetcher.getInstance().fetchLyricsAsync(this.manualArtist, this.manualTitle)
                .thenAccept(lines -> {
                    if (lines != null && !lines.isEmpty()) {
                        this.activeLyrics = lines;
                    }
                });
    }

    public void stopManual() {
        this.demoMode.setEnabled(false);
        this.manualPlayback = false;
        this.lastSongKey = "";
        this.activeLyrics = null;
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        CommandManager.sendClientMessage("§d[Lyrics] §7Ручной режим выключен (автоопределение плеера активно)", false);
    }

    public void seekManual(long seconds) {
        this.manualOffsetMs = seconds * 1000L;
        this.manualStartTime = System.currentTimeMillis();
        this.currentLineIndex = -1;
        this.activeCapsules.clear();
        this.pendingQueue.clear();
        CommandManager.sendClientMessage("§d[Lyrics] §7Перемотка на §f" + seconds + " §7сек", false);
    }

    public boolean isManualPlayback() {
        return manualPlayback;
    }

    public List<LyricLine> getActiveLyrics() {
        return activeLyrics;
    }
}
