package egg.client.module.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import egg.client.command.CommandManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import egg.client.util.LyricsFetcher;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.*;

public class Lyrics extends Module {
    private final ModeSetting splitMode = new ModeSetting("Разбивка", "По фразам", "По фразам", "По запятой", "Вся строка");
    private final SliderSetting wordsPerCapsule = new SliderSetting("Слов в плашке", 2.0, 1.0, 4.0, 1.0, "Количество слов на одну 3D плашку");
    private final SliderSetting distance = new SliderSetting("Дистанция", 4.0, 2.0, 12.0, 0.5, "Базовая дальность плашек в мире (блоки)");
    private final SliderSetting scatterH = new SliderSetting("Разброс X", 3.0, 0.5, 8.0, 0.5, "Ширина разброса влево/вправо");
    private final SliderSetting scatterV = new SliderSetting("Разброс Y", 1.2, 0.2, 4.0, 0.2, "Разброс по высоте");
    private final SliderSetting size = new SliderSetting("Масштаб", 1.0, 0.4, 2.5, 0.1, "Базовый размер плашек");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 6.0, 2.0, 14.0, 0.5, "Время отображения фразы в секундах");

    private final ModeSetting fontMode = new ModeSetting("Шрифт", "SF Medium", "SF Medium", "SF Semibold", "SF Bold", "SF Regular", "Minecraft");
    private final ModeSetting colorStyle = new ModeSetting("Цвет текста", "Белый", "Белый", "Лавандовый", "Коралловый", "Тема");
    private final BooleanSetting background = new BooleanSetting("Фон плашки", true, "Отображать темную полупрозрачную подложку");
    private final BooleanSetting glow = new BooleanSetting("Неоновое свечение", true, "Свечение контура капсулы");
    private final BooleanSetting gentleWave = new BooleanSetting("Живое покачивание", true, "Плавное 3D покачивание плашек в пространстве");
    private final BooleanSetting wordAnimation = new BooleanSetting("Анимация слов", true, "Караоке-анимация и подпрыгивание активного слова как в Dynamic Island");
    private final BooleanSetting noOverlap = new BooleanSetting("Не накладывать", true, "Умное распределение позиций без перекрытия");
    private final BooleanSetting demoMode = new BooleanSetting("Демо режим", false, "Принудительный показ тестовых фраз");

    static class WordInfo {
        final String text;
        final float width;
        final float xOffset;
        float currentScale = 1.0f;
        float targetScale = 1.0f;

        WordInfo(String text, float width, float xOffset) {
            this.text = text;
            this.width = width;
            this.xOffset = xOffset;
        }
    }

    private static class Capsule3D {
        Vec3d worldPos;
        final String text;
        long spawnTime;
        final long lifeDurationMs;
        final long singDurationMs;
        final float seed;
        double yaw;
        double pitch;
        boolean flipping = false;
        long flipStartTime = 0L;
        boolean disappearing = false;
        long disappearStartTime = 0L;
        float boxW = 80.0f;
        float boxH = 22.0f;
        final List<WordInfo> words = new ArrayList<>();

        Capsule3D(Vec3d worldPos, String text, long spawnTime, long lifeDurationMs, long singDurationMs, float seed, double yaw, double pitch, float boxW, float boxH) {
            this.worldPos = worldPos;
            this.text = text;
            this.spawnTime = spawnTime;
            this.lifeDurationMs = lifeDurationMs;
            this.singDurationMs = Math.max(800L, singDurationMs);
            this.seed = seed;
            this.yaw = yaw;
            this.pitch = pitch;
            this.boxW = boxW;
            this.boxH = boxH;
        }
    }

    private static class ScheduledChunk {
        final String text;
        long spawnAtTimeMs;
        final long lifeMs;
        final long singDurationMs;

        ScheduledChunk(String text, long spawnAtTimeMs, long lifeMs, long singDurationMs) {
            this.text = text;
            this.spawnAtTimeMs = spawnAtTimeMs;
            this.lifeMs = lifeMs;
            this.singDurationMs = singDurationMs;
        }
    }

    private final List<Capsule3D> activeCapsules = new ArrayList<>();
    private final List<ScheduledChunk> pendingQueue = new ArrayList<>();
    private final Random rng = new Random();

    private volatile List<LyricLine> activeLyrics = null;
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
                lifetime, fontMode, colorStyle, background, glow, gentleWave, wordAnimation, noOverlap, demoMode
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

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || camera == null) return;

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
            boolean isPlaying = mt.isPlaying();

            if (isPlaying) {
                overallAlpha = Math.min(1.0f, overallAlpha + dt * 3.0f);
            } else if (hasTrack) {
                overallAlpha = 1.0f;
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
                                        currentLineIndex = -1;
                                    }
                                });
                    }
                    currentLineIndex = -1;
                    activeCapsules.clear();
                    pendingQueue.clear();
                }
            }
            currentPosMs = mt.getTrackElapsedMs();

            // Если трек стоит на паузе — замораживаем таймеры очереди и активных плашек
            if (!isPlaying && hasTrack) {
                long dtMs = (long) (dt * 1000.0f);
                for (ScheduledChunk chunk : pendingQueue) {
                    chunk.spawnAtTimeMs += dtMs;
                }
                for (Capsule3D cap : activeCapsules) {
                    cap.spawnTime += dtMs;
                    if (cap.disappearing) {
                        cap.disappearStartTime += dtMs;
                    }
                }
            }
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

                // Плавно гасим капсулы предыдущей строки при начале новой
                for (Capsule3D oldCap : activeCapsules) {
                    if (!oldCap.disappearing) {
                        oldCap.disappearing = true;
                        oldCap.disappearStartTime = now;
                    }
                }
                pendingQueue.clear();

                breakAndSchedulePhrases(currentLine.text(), now, lineDuration);
            }
        }

        Iterator<ScheduledChunk> queueIter = pendingQueue.iterator();
        while (queueIter.hasNext()) {
            ScheduledChunk chunk = queueIter.next();
            if (now >= chunk.spawnAtTimeMs) {
                spawnCapsuleInWorld(chunk.text, now, chunk.lifeMs, chunk.singDurationMs, camera);
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
            if (cap.disappearing && now - cap.disappearStartTime >= 650L) {
                capIter.remove();
            }
        }

        if (activeCapsules.isEmpty()) return;

        renderWorld3DInternal(matrices, camera, now, dt, immediate, camPos);
    }

    private void breakAndSchedulePhrases(String rawText, long now, long lineDuration) {
        if (rawText == null || rawText.isBlank()) return;
        String text = rawText.replaceAll("<[^>]+>", "").trim();
        if (text.isEmpty()) return;

        List<String> chunks = new ArrayList<>();
        String mode = splitMode.get();

        if (mode != null && (mode.contains("запят") || mode.contains("Comma"))) {
            for (String p : text.split("[,;—–\"']")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) chunks.add(trimmed);
            }
        } else if (mode != null && (mode.contains("строк") || mode.contains("Line"))) {
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

        long userMaxLifetime = (long) (lifetime.getValue() * 1000.0);
        long stepMs = chunks.size() <= 1 ? 0L : Math.min(1800L, Math.max(500L, lineDuration / chunks.size()));
        long perChunkSingMs = chunks.isEmpty() ? 1500L : Math.max(900L, lineDuration / chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            long spawnAt = now + i * stepMs;
            // Учитываем ползунок lifetime, но гарантируем допевание фразы (+ запас на чтение 800мс)
            long remainingLineMs = Math.max(1200L, lineDuration - i * stepMs + 800L);
            long capLife = Math.max(userMaxLifetime, remainingLineMs);
            pendingQueue.add(new ScheduledChunk(chunks.get(i), spawnAt, capLife, perChunkSingMs));
        }
    }

    private void spawnCapsuleInWorld(String text, long now, long lifeMs, long singDurationMs, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        float approxTextW = tr != null ? tr.getWidth(text) : (text.length() * 7.5f);
        float boxW = approxTextW + 28.0f;
        float boxH = 22.0f;

        Vec3d eye = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = MathHelper.clamp(camera.getPitch(), -72.0f, 72.0f);

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
        float userScale = (float) size.getValue();

        lastSide = -lastSide;
        float primarySide = lastSide;
        float otherSide = -primarySide;

        // Поиск свободного слота (NoOverlap) без наложения на другие плашки
        List<float[]> candidates = new ArrayList<>();
        // 1. Основная сторона
        candidates.add(new float[]{ primarySide * hScatter * 0.70f, 0.0f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 0.70f, vScatter * 0.65f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 0.70f, -vScatter * 0.65f, baseDist });

        // 2. Дальше / ближе по основной стороне
        candidates.add(new float[]{ primarySide * hScatter * 1.10f, 0.0f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 1.10f, vScatter * 0.85f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 1.10f, -vScatter * 0.85f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 0.35f, vScatter * 0.70f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 0.35f, -vScatter * 0.70f, baseDist });

        // 3. Противоположная сторона
        candidates.add(new float[]{ otherSide * hScatter * 0.70f, 0.0f, baseDist });
        candidates.add(new float[]{ otherSide * hScatter * 0.70f, vScatter * 0.65f, baseDist });
        candidates.add(new float[]{ otherSide * hScatter * 0.70f, -vScatter * 0.65f, baseDist });
        candidates.add(new float[]{ otherSide * hScatter * 1.10f, 0.0f, baseDist });
        candidates.add(new float[]{ otherSide * hScatter * 1.10f, vScatter * 0.85f, baseDist });
        candidates.add(new float[]{ otherSide * hScatter * 1.10f, -vScatter * 0.85f, baseDist });

        // 4. Дополнительные слоты по высоте и глубине
        candidates.add(new float[]{ 0.0f, vScatter * 0.80f, baseDist + 0.6f });
        candidates.add(new float[]{ 0.0f, -vScatter * 0.80f, baseDist + 0.6f });
        candidates.add(new float[]{ primarySide * hScatter * 0.50f, vScatter * 1.25f, baseDist });
        candidates.add(new float[]{ primarySide * hScatter * 0.50f, -vScatter * 1.25f, baseDist });

        Vec3d bestPos = null;
        double minOverlapScore = Double.MAX_VALUE;
        Capsule3D worstOverlapCap = null;

        for (float[] cand : candidates) {
            float cs = cand[0];
            float cv = cand[1];
            float cd = cand[2];
            Vec3d candPos = eye.add(F.multiply(cd)).add(R.multiply(cs)).add(U.multiply(cv));

            double overlapScore = 0.0;
            Capsule3D candidateConflicting = null;

            if (noOverlap.isEnabled()) {
                for (Capsule3D existing : activeCapsules) {
                    if (existing.disappearing && (now - existing.disappearStartTime > 250L)) continue;

                    Vec3d diff = candPos.subtract(existing.worldPos);
                    double dR = Math.abs(diff.dotProduct(R));
                    double dU = Math.abs(diff.dotProduct(U));
                    double dF = Math.abs(diff.dotProduct(F));

                    double safeR = (boxW * 0.5 + existing.boxW * 0.5) * userScale * 0.030 + 0.15;
                    double safeU = (boxH * 0.5 + existing.boxH * 0.5) * userScale * 0.030 + 0.12;

                    if (dR < safeR && dU < safeU && dF < 1.6) {
                        double overlapAmount = (safeR - dR) * 1.5 + (safeU - dU) * 3.0;
                        overlapScore += overlapAmount;
                        if (candidateConflicting == null || existing.spawnTime < candidateConflicting.spawnTime) {
                            candidateConflicting = existing;
                        }
                    }
                }
            }

            if (overlapScore <= 0.001) {
                bestPos = candPos;
                minOverlapScore = 0.0;
                worstOverlapCap = null;
                break;
            }

            if (overlapScore < minOverlapScore) {
                minOverlapScore = overlapScore;
                bestPos = candPos;
                worstOverlapCap = candidateConflicting;
            }
        }

        if (bestPos == null) {
            float s = primarySide * hScatter * 0.65f;
            bestPos = eye.add(F.multiply(baseDist)).add(R.multiply(s));
        }

        // Если все слоты заняты, старая плашка плавно растворяется только если уже повисела достаточно
        if (noOverlap.isEnabled() && minOverlapScore > 0.001 && worstOverlapCap != null && !worstOverlapCap.disappearing) {
            long existingAge = now - worstOverlapCap.spawnTime;
            if (existingAge > 2000L || existingAge > worstOverlapCap.lifeDurationMs * 0.6) {
                worstOverlapCap.disappearing = true;
                worstOverlapCap.disappearStartTime = now;
            }
        }

        double dx = eye.x - bestPos.x;
        double dy = eye.y - bestPos.y;
        double dz = eye.z - bestPos.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        double capYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double capPitch = -Math.toDegrees(Math.atan2(dy, Math.max(0.05, h)));

        Capsule3D cap = new Capsule3D(bestPos, text, now, lifeMs, singDurationMs, rng.nextFloat() * 100.0f, capYaw, capPitch, boxW, boxH);
        activeCapsules.add(cap);

        if (activeCapsules.size() > 10) {
            activeCapsules.get(0).disappearing = true;
            activeCapsules.get(0).disappearStartTime = now;
        }
    }

    private void renderWorld3DInternal(MatrixStack matrices, Camera camera, long now, float dt, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        // Sort back to front for proper translucent blending
        activeCapsules.sort((c1, c2) -> Double.compare(camPos.squaredDistanceTo(c2.worldPos), camPos.squaredDistanceTo(c1.worldPos)));

        float userScale = (float) size.getValue();

        for (Capsule3D cap : activeCapsules) {
            // Check camera behind capsule -> trigger back-face flip animation
            double radY = Math.toRadians(cap.yaw);
            double radP = Math.toRadians(cap.pitch);
            double cosB = Math.cos(radP);
            double sinB = Math.sin(radP);
            double sinA = Math.sin(radY);
            double cosA = Math.cos(radY);

            double dx = camPos.x - cap.worldPos.x;
            double dy = camPos.y - cap.worldPos.y;
            double dz = camPos.z - cap.worldPos.z;

            // Dot product with capsule forward normal
            double dot = dx * (-sinA * cosB) - dy * sinB + dz * (cosA * cosB);

            // Trigger flip when camera is clearly behind capsule, with cooldown
            if (dot < -0.25 && !cap.flipping && (now - cap.flipStartTime > 800L)) {
                cap.flipping = true;
                cap.flipStartTime = now;
            }

            float currentYaw = (float) cap.yaw;
            float flipScale = 1.0f;

            if (cap.flipping) {
                float f = (now - cap.flipStartTime) / 400.0f;
                if (f >= 1.0f) {
                    cap.flipping = false;
                    cap.yaw = (cap.yaw + 180.0) % 360.0;
                    currentYaw = (float) cap.yaw;
                    flipScale = 1.0f;
                } else if (f < 0.3f) {
                    // Compress horizontally towards 0
                    flipScale = (float) Math.cos(f * 5.235988f);
                } else {
                    // Turn 180 deg to face camera from the other side and spring expand back
                    currentYaw = (float) ((cap.yaw + 180.0) % 360.0);
                    float b_ = (f - 0.3f) / 0.7f - 1.0f;
                    flipScale = 1.0f + 2.70158f * b_ * b_ * b_ + 1.70158f * b_ * b_;
                }
            }
            flipScale = Math.max(0.01f, flipScale);

            float appear = MathHelper.clamp((now - cap.spawnTime) / 280.0f, 0.0f, 1.0f);
            appear = appear * appear * (3.0f - 2.0f * appear);

            long elapsed = now - cap.spawnTime;
            long remaining = cap.lifeDurationMs - elapsed;
            float fade = 1.0f;
            if (cap.disappearing) {
                fade = 1.0f - MathHelper.clamp((now - cap.disappearStartTime) / 600.0f, 0.0f, 1.0f);
            } else if (remaining < 600L) {
                fade = MathHelper.clamp(remaining / 600.0f, 0.0f, 1.0f);
            }

            float alpha = appear * fade * (manualPlayback ? 1.0f : overallAlpha);
            if (alpha <= 0.015f) continue;

            float popScale = 0.85f + 0.15f * appear;
            float timeSec = (now - cap.spawnTime) / 1000.0f;
            float waveY = gentleWave.isEnabled() ? (float) Math.sin(timeSec * 1.8f + cap.seed) * 0.04f : 0.0f;
            float waveX = gentleWave.isEnabled() ? (float) Math.cos(timeSec * 1.4f + cap.seed * 1.2f) * 0.03f : 0.0f;

            matrices.push();
            matrices.translate(cap.worldPos.x - camPos.x + waveX, cap.worldPos.y - camPos.y + waveY, cap.worldPos.z - camPos.z);
            // Прямой биллборд на камеру игрока — плашка всегда идеально ровная (без кривизны и кренов)
            matrices.multiply(camera.getRotation());

            float s = userScale * popScale * 0.026f;
            // Стандарт 3D-текста в Minecraft: -s по X и -s по Y для прямого слева-направо чтения и нормального верха
            matrices.scale(-s, -s, s);

            StyleSpriteSource.Font selectedFont = switch (fontMode.get()) {
                case "SF Semibold" -> ClientFont.SF_SEMIBOLD;
                case "SF Bold" -> ClientFont.SF_BOLD;
                case "SF Regular" -> ClientFont.SF_REGULAR;
                case "Minecraft" -> null;
                default -> ClientFont.SF_MEDIUM;
            };

            Text styledText = selectedFont != null ? ClientFont.getText(cap.text, selectedFont) : null;
            float textW = styledText != null ? tr.getWidth(styledText) : tr.getWidth(cap.text);
            float textH = tr.fontHeight;
            float padX = 14.0f;
            float padY = 7.0f;
            float boxW = textW + padX * 2.0f;
            float boxH = textH + padY * 2.0f;
            cap.boxW = boxW;
            cap.boxH = boxH;

            int accent = Theme.getAccent();
            int ar = (accent >> 16) & 0xFF;
            int ag = (accent >> 8) & 0xFF;
            int ab = accent & 0xFF;

            int textColor;
            int tintR, tintG, tintB;

            String style = colorStyle.get();
            if (style.contains("Тем") || style.contains("Theme") || style.contains("Акцент")) {
                textColor = ColorUtil.withAlphaFloat(accent, alpha);
                tintR = ar;
                tintG = ag;
                tintB = ab;
            } else if (style.contains("Роз") || style.contains("Pink") || style.contains("Корал")) {
                textColor = ColorUtil.rgba(255, 175, 195, (int) (255 * alpha));
                tintR = 255;
                tintG = 145;
                tintB = 180;
            } else if (style.contains("Лаван") || style.contains("Фиолет")) {
                textColor = ColorUtil.rgba(235, 225, 255, (int) (255 * alpha));
                tintR = 195;
                tintG = 160;
                tintB = 255;
            } else { // Белый: чисто белый текст, а полупрозрачный фон под цвет активной темы клиента
                textColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
                tintR = ar;
                tintG = ag;
                tintB = ab;
            }

            // Полупрозрачный темный фон под цвет темы (глубокое матовое стекло, ~50% прозрачности как в референсе)
            int bgR = (int) (14 * 0.70f + tintR * 0.30f);
            int bgG = (int) (12 * 0.70f + tintG * 0.30f);
            int bgB = (int) (24 * 0.70f + tintB * 0.30f);
            int bgA = (int) (130 * alpha);

            Matrix4f mat = matrices.peek().getPositionMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            if (background.isEnabled()) {
                VertexConsumer quadConsumer = immediate.getBuffer(RenderLayers.debugQuads());
                float cornerRadius = 6.0f;

                // 1. Мягкая контактная тень под плашкой (oz = -0.04f)
                draw3DRoundedRect(quadConsumer, mat, boxW + 2.0f, boxH + 2.0f, cornerRadius + 1.5f, 0.0f, 1.8f, -0.04f, 0, 0, 0, (int) (70 * alpha));

                // 2. Мягкое неоновое свечение темы вокруг плашки (oz = -0.02f)
                if (glow.isEnabled()) {
                    draw3DRoundedRect(quadConsumer, mat, boxW + 4.5f, boxH + 4.0f, cornerRadius + 2.5f, 0.0f, 0.0f, -0.02f, tintR, tintG, tintB, (int) (35 * alpha));
                }

                // 3. Полупрозрачное темное стекло под цвет темы (oz = 0.0f)
                draw3DRoundedRect(quadConsumer, mat, boxW, boxH, cornerRadius, 0.0f, 0.0f, 0.0f, bgR, bgG, bgB, bgA);

                // 4. Тонкий верхний стеклянный глянец (oz = 0.008f)
                draw3DTopGloss(quadConsumer, mat, boxW, boxH, cornerRadius, 0.008f, (int) (38 * alpha));

                // 5. Тонкая аккуратная рамка по контуру под цвет темы (oz = 0.015f)
                draw3DRoundedOutline(quadConsumer, mat, boxW, boxH, cornerRadius, 0.9f, 0.015f, tintR, tintG, tintB, (int) (80 * alpha));

                // Сбрасываем буфер геометрии подложки
                immediate.draw(RenderLayers.debugQuads());
            }

            // 6. Отрисовка текста поверх всех слоев (z = 0.035f - ближе всего к камере)
            int pureLight = 15728640; // Полный дневной свет (Sky=15, Block=0) — нулевой оттенок факела/желтизны

            // Инициализация слов плашки (при первом кадре с учетом выбранного шрифта)
            if (cap.words.isEmpty() && !cap.text.isBlank()) {
                String[] rawWords = cap.text.trim().split("\\s+");
                Text spaceText = selectedFont != null ? ClientFont.getText(" ", selectedFont) : null;
                float spaceW = spaceText != null ? tr.getWidth(spaceText) : tr.getWidth(" ");
                float curX = 0.0f;
                for (String w : rawWords) {
                    if (w.isEmpty()) continue;
                    Text wText = selectedFont != null ? ClientFont.getText(w, selectedFont) : null;
                    float ww = wText != null ? tr.getWidth(wText) : tr.getWidth(w);
                    cap.words.add(new WordInfo(w, ww, curX));
                    curX += ww + spaceW;
                }
            }

            if (wordAnimation.isEnabled() && !cap.words.isEmpty()) {
                int numWords = cap.words.size();
                float singProgress = MathHelper.clamp((float) elapsed / (float) cap.singDurationMs, 0.0f, 1.0f);
                int activeWordIdx = (int) (singProgress * numWords);
                if (activeWordIdx >= numWords) activeWordIdx = numWords - 1;
                boolean isSinging = elapsed < cap.singDurationMs;

                float startX = -textW / 2.0f;
                float baseTy = -textH / 2.0f;

                for (int w = 0; w < numWords; w++) {
                    WordInfo word = cap.words.get(w);

                    // Активное слово подпрыгивает и увеличивается до 1.15x как в Dynamic Island
                    float targetScale = (isSinging && w == activeWordIdx) ? 1.15f : 1.0f;
                    word.currentScale += (targetScale - word.currentScale) * Math.min(1.0f, dt * 12.0f);

                    // Плавный прыжок вверх при пении
                    float hop = (word.currentScale - 1.0f) * 9.0f;

                    // Спетое и активное слово светятся 100% белым/акцентом, еще не спетые — мягкие 45% прозрачности
                    int wColor;
                    if (!isSinging || w <= activeWordIdx) {
                        wColor = textColor;
                    } else {
                        wColor = ColorUtil.withAlphaFloat(textColor, alpha * 0.45f);
                    }

                    float wx = startX + word.xOffset;
                    float wy = baseTy - hop;
                    float cx = wx + word.width * 0.5f;
                    float cy = wy + textH * 0.5f;

                    matrices.push();
                    matrices.translate(cx, cy, 0.035f);
                    if (Math.abs(word.currentScale - 1.0f) > 0.002f) {
                        matrices.scale(word.currentScale, word.currentScale, 1.0f);
                    }
                    matrices.translate(-cx, -cy, 0.0f);
                    Matrix4f wMat = matrices.peek().getPositionMatrix();
                    matrices.pop();

                    Text wText = selectedFont != null ? ClientFont.getText(word.text, selectedFont) : null;
                    if (wText != null && !"Minecraft".equalsIgnoreCase(fontMode.get())) {
                        tr.draw(wText, wx, wy, wColor, false, wMat, immediate, TextRenderer.TextLayerType.NORMAL, 0, pureLight);
                    } else {
                        tr.draw(word.text, wx, wy, wColor, false, wMat, immediate, TextRenderer.TextLayerType.NORMAL, 0, pureLight);
                    }
                }
            } else {
                float tx = -textW / 2.0f;
                float ty = -textH / 2.0f;
                matrices.push();
                matrices.translate(0.0f, 0.0f, 0.035f);
                Matrix4f textMat = matrices.peek().getPositionMatrix();
                matrices.pop();

                if (styledText != null && !"Minecraft".equalsIgnoreCase(fontMode.get()) && textW > 0) {
                    try {
                        tr.draw(styledText, tx, ty, textColor, false, textMat, immediate, TextRenderer.TextLayerType.NORMAL, 0, pureLight);
                    } catch (Throwable ignored) {}
                } else {
                    tr.draw(cap.text, tx, ty, textColor, false, textMat, immediate, TextRenderer.TextLayerType.NORMAL, 0, pureLight);
                }
            }
            immediate.draw();

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            matrices.pop();
        }
    }

    private void draw3DRoundedRect(VertexConsumer buf, Matrix4f mat, float w, float h, float rad, float ox, float oy, float oz, int r, int g, int b, int a) {
        if (a <= 0) return;
        rad = Math.min(rad, Math.min(w * 0.5f, h * 0.5f));
        float x1 = -w * 0.5f + rad + ox;
        float x2 = w * 0.5f - rad + ox;
        float y1 = -h * 0.5f + rad + oy;
        float y2 = h * 0.5f - rad + oy;

        // Center rectangle
        buf.vertex(mat, x1, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y2, oz).color(r, g, b, a);

        // Top strip
        buf.vertex(mat, x1, -h * 0.5f + oy, oz).color(r, g, b, a);
        buf.vertex(mat, x2, -h * 0.5f + oy, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y1, oz).color(r, g, b, a);

        // Bottom strip
        buf.vertex(mat, x1, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x2, h * 0.5f + oy, oz).color(r, g, b, a);
        buf.vertex(mat, x1, h * 0.5f + oy, oz).color(r, g, b, a);

        // Left strip
        buf.vertex(mat, -w * 0.5f + ox, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y2, oz).color(r, g, b, a);
        buf.vertex(mat, -w * 0.5f + ox, y2, oz).color(r, g, b, a);

        // Right strip
        buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        buf.vertex(mat, w * 0.5f + ox, y1, oz).color(r, g, b, a);
        buf.vertex(mat, w * 0.5f + ox, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2, oz).color(r, g, b, a);

        int segments = 8;
        float step = (float) (Math.PI * 0.5 / segments);

        // Top-Left corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI + i * step;
            float a2 = a1 + step;
            float px1 = x1 + (float) Math.cos(a1) * rad;
            float py1 = y1 + (float) Math.sin(a1) * rad;
            float px2 = x1 + (float) Math.cos(a2) * rad;
            float py2 = y1 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x1, y1, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x1, y1, oz).color(r, g, b, a);
        }

        // Top-Right corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (Math.PI * 1.5) + i * step;
            float a2 = a1 + step;
            float px1 = x2 + (float) Math.cos(a1) * rad;
            float py1 = y1 + (float) Math.sin(a1) * rad;
            float px2 = x2 + (float) Math.cos(a2) * rad;
            float py2 = y1 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        }

        // Bottom-Right corner
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = a1 + step;
            float px1 = x2 + (float) Math.cos(a1) * rad;
            float py1 = y2 + (float) Math.sin(a1) * rad;
            float px2 = x2 + (float) Math.cos(a2) * rad;
            float py2 = y2 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
        }

        // Bottom-Left corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (Math.PI * 0.5) + i * step;
            float a2 = a1 + step;
            float px1 = x1 + (float) Math.cos(a1) * rad;
            float py1 = y2 + (float) Math.sin(a1) * rad;
            float px2 = x1 + (float) Math.cos(a2) * rad;
            float py2 = y2 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x1, y2, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x1, y2, oz).color(r, g, b, a);
        }
    }

    private void draw3DTopGloss(VertexConsumer buf, Matrix4f mat, float w, float h, float rad, float oz, int a) {
        if (a <= 1) return;
        rad = Math.min(rad, Math.min(w * 0.5f, h * 0.5f));
        float x1 = -w * 0.5f + rad;
        float x2 = w * 0.5f - rad;
        float y1 = -h * 0.5f + rad;
        float yMid = 0.0f;

        int aTop = a;
        int aMid = (int) (a * 0.08f);

        // Center upper rectangle gloss gradient
        buf.vertex(mat, x1, -h * 0.5f, oz).color(255, 255, 255, aTop);
        buf.vertex(mat, x2, -h * 0.5f, oz).color(255, 255, 255, aTop);
        buf.vertex(mat, x2, yMid, oz).color(255, 255, 255, aMid);
        buf.vertex(mat, x1, yMid, oz).color(255, 255, 255, aMid);

        int segments = 8;
        float step = (float) (Math.PI * 0.5 / segments);

        // Left upper quarter fan
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI + i * step;
            float a2 = a1 + step;
            float px1 = x1 + (float) Math.cos(a1) * rad;
            float py1 = y1 + (float) Math.sin(a1) * rad;
            float px2 = x1 + (float) Math.cos(a2) * rad;
            float py2 = y1 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x1, y1, oz).color(255, 255, 255, aMid);
            buf.vertex(mat, px1, py1, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, px2, py2, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, x1, y1, oz).color(255, 255, 255, aMid);
        }

        // Right upper quarter fan
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (Math.PI * 1.5) + i * step;
            float a2 = a1 + step;
            float px1 = x2 + (float) Math.cos(a1) * rad;
            float py1 = y1 + (float) Math.sin(a1) * rad;
            float px2 = x2 + (float) Math.cos(a2) * rad;
            float py2 = y1 + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x2, y1, oz).color(255, 255, 255, aMid);
            buf.vertex(mat, px1, py1, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, px2, py2, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, x2, y1, oz).color(255, 255, 255, aMid);
        }
    }

    private void draw3DRoundedOutline(VertexConsumer buf, Matrix4f mat, float w, float h, float rad, float thickness, float oz, int r, int g, int b, int a) {
        if (a <= 1) return;
        rad = Math.min(rad, Math.min(w * 0.5f, h * 0.5f));
        float x1 = -w * 0.5f + rad;
        float x2 = w * 0.5f - rad;
        float y1 = -h * 0.5f + rad;
        float y2 = h * 0.5f - rad;

        // Top edge
        buf.vertex(mat, x1, -h * 0.5f - thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x2, -h * 0.5f - thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x2, -h * 0.5f, oz).color(r, g, b, a);
        buf.vertex(mat, x1, -h * 0.5f, oz).color(r, g, b, a);

        // Bottom edge
        buf.vertex(mat, x1, h * 0.5f, oz).color(r, g, b, a);
        buf.vertex(mat, x2, h * 0.5f, oz).color(r, g, b, a);
        buf.vertex(mat, x2, h * 0.5f + thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x1, h * 0.5f + thickness, oz).color(r, g, b, a);

        // Left edge
        buf.vertex(mat, -w * 0.5f - thickness, y1, oz).color(r, g, b, a);
        buf.vertex(mat, -w * 0.5f, y1, oz).color(r, g, b, a);
        buf.vertex(mat, -w * 0.5f, y2, oz).color(r, g, b, a);
        buf.vertex(mat, -w * 0.5f - thickness, y2, oz).color(r, g, b, a);

        // Right edge
        buf.vertex(mat, w * 0.5f, y1, oz).color(r, g, b, a);
        buf.vertex(mat, w * 0.5f + thickness, y1, oz).color(r, g, b, a);
        buf.vertex(mat, w * 0.5f + thickness, y2, oz).color(r, g, b, a);
        buf.vertex(mat, w * 0.5f, y2, oz).color(r, g, b, a);

        int segments = 8;
        float step = (float) (Math.PI * 0.5 / segments);

        // Top-Left corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI + i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            buf.vertex(mat, x1 + c1 * rad, y1 + s1 * rad, oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c1 * (rad + thickness), y1 + s1 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c2 * (rad + thickness), y1 + s2 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c2 * rad, y1 + s2 * rad, oz).color(r, g, b, a);
        }

        // Top-Right corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (Math.PI * 1.5) + i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            buf.vertex(mat, x2 + c1 * rad, y1 + s1 * rad, oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c1 * (rad + thickness), y1 + s1 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c2 * (rad + thickness), y1 + s2 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c2 * rad, y1 + s2 * rad, oz).color(r, g, b, a);
        }

        // Bottom-Right corner
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            buf.vertex(mat, x2 + c1 * rad, y2 + s1 * rad, oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c1 * (rad + thickness), y2 + s1 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c2 * (rad + thickness), y2 + s2 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x2 + c2 * rad, y2 + s2 * rad, oz).color(r, g, b, a);
        }

        // Bottom-Left corner
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (Math.PI * 0.5) + i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            buf.vertex(mat, x1 + c1 * rad, y2 + s1 * rad, oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c1 * (rad + thickness), y2 + s1 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c2 * (rad + thickness), y2 + s2 * (rad + thickness), oz).color(r, g, b, a);
            buf.vertex(mat, x1 + c2 * rad, y2 + s2 * rad, oz).color(r, g, b, a);
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
