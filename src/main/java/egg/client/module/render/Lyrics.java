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
        double yaw;
        double pitch;
        boolean flipping = false;
        long flipStartTime = 0L;
        boolean disappearing = false;
        long disappearStartTime = 0L;

        Capsule3D(Vec3d worldPos, String text, long spawnTime, long lifeDurationMs, float seed, double yaw, double pitch) {
            this.worldPos = worldPos;
            this.text = text;
            this.spawnTime = spawnTime;
            this.lifeDurationMs = lifeDurationMs;
            this.seed = seed;
            this.yaw = yaw;
            this.pitch = pitch;
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
                lifetime, fontMode, colorStyle, background, glow, gentleWave, noOverlap, demoMode
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

        lastSide = -lastSide;
        float side = lastSide;
        if (rng.nextFloat() < 0.20f) {
            side = -side;
            lastSide = side;
        }

        float s = side * hScatter * (0.50f + rng.nextFloat() * 0.50f);
        float v = (rng.nextFloat() - 0.5f) * 2.0f * vScatter;

        Vec3d spawnPos = eye.add(F.multiply(baseDist)).add(R.multiply(s)).add(U.multiply(v));

        double dx = eye.x - spawnPos.x;
        double dy = eye.y - spawnPos.y;
        double dz = eye.z - spawnPos.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        double capYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double capPitch = -Math.toDegrees(Math.atan2(dy, Math.max(0.05, h)));

        Capsule3D cap = new Capsule3D(spawnPos, text, now, lifeMs, rng.nextFloat() * 100.0f, capYaw, capPitch);
        activeCapsules.add(cap);

        if (activeCapsules.size() > 14) {
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
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - currentYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) -cap.pitch));

            float s = userScale * popScale * 0.040f;
            // Negative X scale cancels 180 deg Y rotation inversion so text is normal left-to-right (never mirrored)
            matrices.scale(-s * flipScale, -s, s);

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

            int textColor;
            int glowColor;
            int bgCol;

            String style = colorStyle.get();
            if (style.contains("Бел") || style.contains("White")) {
                textColor = ColorUtil.rgba(245, 246, 252, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 255, 255, (int) (150 * alpha));
                bgCol = ColorUtil.rgba(20, 22, 28, (int) (185 * alpha));
            } else if (style.contains("Тем") || style.contains("Theme") || style.contains("Акцент")) {
                int accent = Theme.getAccent();
                textColor = ColorUtil.withAlphaFloat(accent, alpha);
                glowColor = ColorUtil.withAlphaFloat(accent, 0.70f * alpha);
                bgCol = ColorUtil.rgba(18, 20, 26, (int) (185 * alpha));
            } else if (style.contains("Роз") || style.contains("Pink") || style.contains("Корал")) {
                textColor = ColorUtil.rgba(255, 160, 175, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(255, 130, 150, (int) (170 * alpha));
                bgCol = ColorUtil.rgba(22, 18, 24, (int) (185 * alpha));
            } else { // Лавандовый / Фиолетовый (по умолчанию как в референсе)
                textColor = ColorUtil.rgba(235, 225, 255, (int) (255 * alpha));
                glowColor = ColorUtil.rgba(185, 145, 255, (int) (180 * alpha));
                bgCol = ColorUtil.rgba(22, 18, 30, (int) (185 * alpha));
            }

            Matrix4f mat = matrices.peek().getPositionMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            if (background.isEnabled()) {
                VertexConsumer quadConsumer = immediate.getBuffer(RenderLayers.debugQuads());
                int bgR = (bgCol >> 16) & 0xFF;
                int bgG = (bgCol >> 8) & 0xFF;
                int bgB = bgCol & 0xFF;
                int bgA = (bgCol >> 24) & 0xFF;

                // 1a. Глубокая рассеянная тень (oz = 0.05f - сзади капсулы)
                draw3DCapsule(quadConsumer, mat, boxW + 5.0f, boxH + 3.0f, 1.8f, 2.8f, 0.05f, 0, 0, 0, (int) (70 * alpha));

                // 1b. Ближняя контактная тень (oz = 0.03f)
                draw3DCapsule(quadConsumer, mat, boxW + 2.0f, boxH + 1.2f, 0.8f, 1.4f, 0.03f, 0, 0, 0, (int) (120 * alpha));

                // 2. Базовая темная стеклянная капсула (oz = 0.0f)
                draw3DCapsule(quadConsumer, mat, boxW, boxH, 0f, 0f, 0.0f, bgR, bgG, bgB, bgA);

                // 3. Верхний стеклянный глянец (блик, oz = -0.006f - на передней поверхности)
                draw3DTopGloss(quadConsumer, mat, boxW, boxH, -0.006f, (int) (55 * alpha));

                // 4. Неоновый контур плашки (oz = -0.012f)
                if (glow.isEnabled()) {
                    int glR = (glowColor >> 16) & 0xFF;
                    int glG = (glowColor >> 8) & 0xFF;
                    int glB = glowColor & 0xFF;
                    int glA = (glowColor >> 24) & 0xFF;
                    draw3DCapsuleOutline(quadConsumer, mat, boxW, boxH, -0.012f, 1.1f, glR, glG, glB, glA);
                }

                // Сбрасываем буфер геометрии плашки
                immediate.draw(RenderLayers.debugQuads());
            }

            // 5. Отрисовка текста поверх всех слоев (z = -0.025f - ближе всего к камере)
            float tx = -textW / 2.0f;
            float ty = -textH / 2.0f;
            matrices.push();
            matrices.translate(0.0f, 0.0f, -0.025f);
            Matrix4f textMat = matrices.peek().getPositionMatrix();
            matrices.pop();

            boolean drawn = false;
            if (styledText != null && !"Minecraft".equalsIgnoreCase(fontMode.get()) && textW > 0) {
                try {
                    tr.draw(styledText, tx, ty, textColor, true, textMat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
                    drawn = true;
                } catch (Throwable ignored) {}
            }
            if (!drawn) {
                tr.draw(cap.text, tx, ty, textColor, true, textMat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            immediate.draw();

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            matrices.pop();
        }
    }

    private void draw3DCapsule(VertexConsumer buf, Matrix4f mat, float w, float h, float ox, float oy, float oz, int r, int g, int b, int a) {
        float rad = h * 0.5f;
        float x1 = -w * 0.5f + rad + ox;
        float x2 = w * 0.5f - rad + ox;
        float y1 = -h * 0.5f + oy;
        float y2 = h * 0.5f + oy;

        // Center rectangle
        buf.vertex(mat, x1, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y2, oz).color(r, g, b, a);

        int segments = 12;
        float step = (float) Math.PI / segments;

        // Left cap fan
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI * 0.5f + i * step;
            float a2 = a1 + step;
            float px1 = x1 + (float) Math.cos(a1) * rad;
            float py1 = oy + (float) Math.sin(a1) * rad;
            float px2 = x1 + (float) Math.cos(a2) * rad;
            float py2 = oy + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x1, oy, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x1, oy, oz).color(r, g, b, a);
        }

        // Right cap fan
        for (int i = 0; i < segments; i++) {
            float a1 = -(float) Math.PI * 0.5f + i * step;
            float a2 = a1 + step;
            float px1 = x2 + (float) Math.cos(a1) * rad;
            float py1 = oy + (float) Math.sin(a1) * rad;
            float px2 = x2 + (float) Math.cos(a2) * rad;
            float py2 = oy + (float) Math.sin(a2) * rad;

            buf.vertex(mat, x2, oy, oz).color(r, g, b, a);
            buf.vertex(mat, px1, py1, oz).color(r, g, b, a);
            buf.vertex(mat, px2, py2, oz).color(r, g, b, a);
            buf.vertex(mat, x2, oy, oz).color(r, g, b, a);
        }
    }

    private void draw3DTopGloss(VertexConsumer buf, Matrix4f mat, float w, float h, float oz, int a) {
        if (a <= 1) return;
        float rad = h * 0.5f;
        float x1 = -w * 0.5f + rad;
        float x2 = w * 0.5f - rad;
        float y1 = -h * 0.5f;
        float yMid = -0.5f;

        int aTop = a;
        int aMid = (int) (a * 0.15f);

        // Center upper rectangle gloss gradient
        buf.vertex(mat, x1, y1, oz).color(255, 255, 255, aTop);
        buf.vertex(mat, x2, y1, oz).color(255, 255, 255, aTop);
        buf.vertex(mat, x2, yMid, oz).color(255, 255, 255, aMid);
        buf.vertex(mat, x1, yMid, oz).color(255, 255, 255, aMid);

        int segments = 8;
        float step = (float) (Math.PI * 0.5 / segments);

        // Left upper quarter fan
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI + i * step;
            float a2 = a1 + step;
            float px1 = x1 + (float) Math.cos(a1) * rad;
            float py1 = (float) Math.sin(a1) * rad;
            float px2 = x1 + (float) Math.cos(a2) * rad;
            float py2 = (float) Math.sin(a2) * rad;

            buf.vertex(mat, x1, 0f, oz).color(255, 255, 255, aMid);
            buf.vertex(mat, px1, py1, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, px2, py2, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, x1, 0f, oz).color(255, 255, 255, aMid);
        }

        // Right upper quarter fan
        for (int i = 0; i < segments; i++) {
            float a1 = -(float) (Math.PI * 0.5) + i * step;
            float a2 = a1 + step;
            float px1 = x2 + (float) Math.cos(a1) * rad;
            float py1 = (float) Math.sin(a1) * rad;
            float px2 = x2 + (float) Math.cos(a2) * rad;
            float py2 = (float) Math.sin(a2) * rad;

            buf.vertex(mat, x2, 0f, oz).color(255, 255, 255, aMid);
            buf.vertex(mat, px1, py1, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, px2, py2, oz).color(255, 255, 255, aTop);
            buf.vertex(mat, x2, 0f, oz).color(255, 255, 255, aMid);
        }
    }

    private void draw3DCapsuleOutline(VertexConsumer buf, Matrix4f mat, float w, float h, float oz, float thickness, int r, int g, int b, int a) {
        float rad = h * 0.5f;
        float x1 = -w * 0.5f + rad;
        float x2 = w * 0.5f - rad;
        float y1 = -h * 0.5f;
        float y2 = h * 0.5f;

        // Top edge
        buf.vertex(mat, x1, y1 - thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y1 - thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y1, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y1, oz).color(r, g, b, a);

        // Bottom edge
        buf.vertex(mat, x1, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2, oz).color(r, g, b, a);
        buf.vertex(mat, x2, y2 + thickness, oz).color(r, g, b, a);
        buf.vertex(mat, x1, y2 + thickness, oz).color(r, g, b, a);

        int segments = 12;
        float step = (float) Math.PI / segments;

        // Left cap outline ring
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.PI * 0.5f + i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float in1x = x1 + c1 * rad;
            float in1y = s1 * rad;
            float in2x = x1 + c2 * rad;
            float in2y = s2 * rad;

            float out1x = x1 + c1 * (rad + thickness);
            float out1y = s1 * (rad + thickness);
            float out2x = x1 + c2 * (rad + thickness);
            float out2y = s2 * (rad + thickness);

            buf.vertex(mat, in1x, in1y, oz).color(r, g, b, a);
            buf.vertex(mat, out1x, out1y, oz).color(r, g, b, a);
            buf.vertex(mat, out2x, out2y, oz).color(r, g, b, a);
            buf.vertex(mat, in2x, in2y, oz).color(r, g, b, a);
        }

        // Right cap outline ring
        for (int i = 0; i < segments; i++) {
            float a1 = -(float) Math.PI * 0.5f + i * step;
            float a2 = a1 + step;
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float in1x = x2 + c1 * rad;
            float in1y = s1 * rad;
            float in2x = x2 + c2 * rad;
            float in2y = s2 * rad;

            float out1x = x2 + c1 * (rad + thickness);
            float out1y = s1 * (rad + thickness);
            float out2x = x2 + c2 * (rad + thickness);
            float out2y = s2 * (rad + thickness);

            buf.vertex(mat, in1x, in1y, oz).color(r, g, b, a);
            buf.vertex(mat, out1x, out1y, oz).color(r, g, b, a);
            buf.vertex(mat, out2x, out2y, oz).color(r, g, b, a);
            buf.vertex(mat, in2x, in2y, oz).color(r, g, b, a);
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
