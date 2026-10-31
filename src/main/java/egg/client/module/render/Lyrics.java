package egg.client.module.render;

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
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class Lyrics extends Module {
    private final BooleanSetting demoMode = new BooleanSetting("Демо трек", false, "Воспроизведение демо-трека Queen - Bohemian Rhapsody");
    private final SliderSetting distance = new SliderSetting("Дистанция", 32.0, 10.0, 80.0, 1.0, "Расстояние до текста в скайбоксе");
    private final SliderSetting elevation = new SliderSetting("Высота", 14.0, 0.0, 50.0, 1.0, "Высота над горизонтом");
    private final SliderSetting size = new SliderSetting("Размер", 1.25, 0.5, 3.0, 0.1, "Масштаб шрифта");
    private final SliderSetting followSpeed = new SliderSetting("Слежение", 0.22, 0.05, 1.0, 0.02, "Плавность инерции за взглядом");
    private final BooleanSetting glow = new BooleanSetting("Неоновое свечение", true, "Свечение в цвет темы клиента");
    private final BooleanSetting background = new BooleanSetting("Тёмная подложка", true, "Фон для контраста в небе");
    private final BooleanSetting trackBadge = new BooleanSetting("Инфо о треке", true, "Название трека и исполнитель над текстом");
    private final ModeSetting colorMode = new ModeSetting("Цвет текста", "Белый", "Белый", "Тема", "Градиент");

    private Vec3d currentFloatingPos = null;
    private List<LyricLine> activeLyrics = null;
    private String lastSongKey = "";
    private int currentLineIndex = -1;
    private int prevLineIndex = -1;
    private long lineChangeTimeMs = 0L;
    private float overallAlpha = 0.0f;
    private long lastFrameNanos = 0L;
    private long demoPlayTimeMs = 0L;

    private boolean manualPlayback = false;
    private String manualArtist = "";
    private String manualTitle = "";
    private long manualStartTime = 0L;

    public Lyrics() {
        super("Lyrics", "Отображает текст играющей музыки в скайбоксе с плавной анимацией", Category.ESP);
        addSettings(demoMode, distance, elevation, size, followSpeed, glow, background, trackBadge, colorMode);
    }

    @Override
    public void onDisable() {
        currentFloatingPos = null;
        activeLyrics = null;
        lastSongKey = "";
        currentLineIndex = -1;
        prevLineIndex = -1;
        overallAlpha = 0.0f;
        manualPlayback = false;
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.textRenderer == null) return;

        long now = System.nanoTime();
        float dt = (lastFrameNanos == 0L) ? 0.016f : MathHelper.clamp((now - lastFrameNanos) / 1.0E9f, 0.001f, 0.05f);
        lastFrameNanos = now;

        boolean isDemo = demoMode.isEnabled();
        String currentArtist;
        String currentTitle;
        long playTimeMs;
        float targetAlpha;

        if (isDemo) {
            demoPlayTimeMs += (long) (dt * 1000f);
            if (demoPlayTimeMs > 92000L) demoPlayTimeMs = 0L;
            currentArtist = "Queen";
            currentTitle = "Bohemian Rhapsody";
            playTimeMs = demoPlayTimeMs;
            activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
            targetAlpha = 1.0f;
        } else if (manualPlayback) {
            currentArtist = manualArtist;
            currentTitle = manualTitle;
            playTimeMs = Math.max(0L, System.currentTimeMillis() - manualStartTime);
            targetAlpha = 1.0f;
        } else {
            MediaTracker mt = MediaTracker.getInstance();
            if (mt != null && mt.isPlaying()) {
                currentArtist = mt.getTrackArtist();
                currentTitle = mt.getTrackTitle();
                playTimeMs = mt.getTrackElapsedMs();
                targetAlpha = 1.0f;
            } else {
                currentArtist = "";
                currentTitle = "";
                playTimeMs = 0L;
                targetAlpha = 0.0f;
            }
        }

        overallAlpha += (targetAlpha - overallAlpha) * Math.min(1.0f, dt * 5.0f);
        if (overallAlpha <= 0.01f) return;

        if (!isDemo && !currentTitle.isEmpty()) {
            String songKey = LyricsFetcher.makeKey(currentArtist, currentTitle);
            if (!songKey.equals(lastSongKey)) {
                lastSongKey = songKey;
                currentLineIndex = -1;
                prevLineIndex = -1;
                activeLyrics = LyricsFetcher.getInstance().getCachedLyrics(currentArtist, currentTitle);
                if (activeLyrics == null) {
                    LyricsFetcher.getInstance().fetchLyricsAsync(currentArtist, currentTitle).thenAccept(lines -> {
                        if (songKey.equals(lastSongKey)) {
                            activeLyrics = lines;
                        }
                    });
                }
            }
        }

        if (activeLyrics != null && !activeLyrics.isEmpty()) {
            int found = -1;
            for (int i = 0; i < activeLyrics.size(); i++) {
                if (activeLyrics.get(i).timeMs() <= playTimeMs) {
                    found = i;
                } else {
                    break;
                }
            }
            if (found != currentLineIndex) {
                prevLineIndex = currentLineIndex;
                currentLineIndex = found;
                lineChangeTimeMs = System.currentTimeMillis();
            }
        }

        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        double radPitch = Math.toRadians(pitch);
        double radYaw = Math.toRadians(yaw);
        double lookX = -Math.sin(radYaw) * Math.cos(radPitch);
        double lookY = -Math.sin(radPitch);
        double lookZ = Math.cos(radYaw) * Math.cos(radPitch);

        double elevOff = elevation.getValue() / 45.0;
        double skyY = Math.max(0.18 + elevOff, lookY + elevOff);
        Vec3d skyUnit = new Vec3d(lookX, skyY, lookZ).normalize();
        Vec3d targetPos = camPos.add(skyUnit.multiply(distance.getValue()));

        if (currentFloatingPos == null) {
            currentFloatingPos = targetPos;
        } else {
            float fSpeed = followSpeed.getValueFloat();
            float lerpAmt = 1.0f - (float) Math.exp(-fSpeed * dt * 9.0f);
            currentFloatingPos = currentFloatingPos.lerp(targetPos, lerpAmt);
        }

        matrices.push();
        matrices.translate(
                currentFloatingPos.x - camPos.x,
                currentFloatingPos.y - camPos.y,
                currentFloatingPos.z - camPos.z
        );
        matrices.multiply(camera.getRotation());
        float baseScale = 0.038f * size.getValueFloat();
        matrices.scale(-baseScale, -baseScale, baseScale);

        long elapsed = System.currentTimeMillis() - lineChangeTimeMs;
        float t = MathHelper.clamp(elapsed / 360.0f, 0.0f, 1.0f);
        float ease = (float) (0.5 - 0.5 * Math.cos(t * Math.PI));

        TextRenderer tr = mc.textRenderer;
        int accentColor = Theme.getAccent();
        int activeColor = switch (colorMode.get()) {
            case "Тема" -> accentColor;
            case "Градиент" -> ColorUtil.lerp(0xFFFFFFFF, accentColor, 0.45f);
            default -> 0xFFFFFFFF;
        };
        int glowCol = ColorUtil.withAlphaFloat(accentColor, 0.40f * overallAlpha);

        String currentText = (currentLineIndex >= 0 && activeLyrics != null && currentLineIndex < activeLyrics.size())
                ? activeLyrics.get(currentLineIndex).text()
                : (activeLyrics == null ? "Поиск текста песни..." : (activeLyrics.isEmpty() ? "Инструментал" : "♫"));

        float line1Y = 16.0f * (1.0f - ease);
        float line1Scale = 0.85f + 0.15f * ease;
        float line1Alpha = (0.55f + 0.45f * ease) * overallAlpha;
        drawLine(matrices, tr, immediate, currentText, line1Y, line1Scale, line1Alpha, activeColor, glowCol, true);

        if (activeLyrics != null && currentLineIndex + 1 < activeLyrics.size()) {
            String nextText = activeLyrics.get(currentLineIndex + 1).text();
            float line2Y = 16.0f + 16.0f * (1.0f - ease);
            float line2Scale = 0.72f;
            float line2Alpha = (0.35f + 0.20f * ease) * overallAlpha;
            drawLine(matrices, tr, immediate, nextText, line2Y, line2Scale, line2Alpha, ColorUtil.rgba(215, 220, 235, 255), glowCol, false);
        }

        if (prevLineIndex >= 0 && activeLyrics != null && prevLineIndex < activeLyrics.size() && t < 1.0f) {
            String prevText = activeLyrics.get(prevLineIndex).text();
            float prevY = -16.0f * ease;
            float prevScale = 1.0f - 0.15f * ease;
            float prevAlpha = (1.0f - ease) * 0.75f * overallAlpha;
            drawLine(matrices, tr, immediate, prevText, prevY, prevScale, prevAlpha, activeColor, glowCol, true);
        }

        if (trackBadge.isEnabled() && !currentTitle.isEmpty()) {
            String infoText = "♫ " + (currentArtist.isEmpty() ? "" : currentArtist + " — ") + currentTitle;
            drawLine(matrices, tr, immediate, infoText, -18.0f, 0.62f, 0.85f * overallAlpha, accentColor, glowCol, false);
        }

        matrices.pop();
        immediate.draw();
    }

    private void drawLine(MatrixStack matrices, TextRenderer tr, VertexConsumerProvider.Immediate immediate,
                          String text, float y, float scale, float alpha, int color, int glowColor, boolean bold) {
        if (text == null || text.isBlank() || alpha <= 0.01f) return;
        matrices.push();
        matrices.translate(0.0f, y, 0.0f);
        matrices.scale(scale, scale, 1.0f);

        Text textObj = ClientFont.getText(text, bold ? ClientFont.SF_BOLD : ClientFont.SF_MEDIUM);
        int w = tr.getWidth(textObj);
        float x = -w / 2.0f;

        if (glow.isEnabled()) {
            int gCol = ColorUtil.withAlphaFloat(glowColor, 0.35f * alpha);
            tr.draw(textObj, x - 0.5f, 0.0f, gCol, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            tr.draw(textObj, x + 0.5f, 0.0f, gCol, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            tr.draw(textObj, x, -0.5f, gCol, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            tr.draw(textObj, x, 0.5f, gCol, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        }

        int bgCol = background.isEnabled() ? ColorUtil.rgba(12, 13, 18, (int) (135 * alpha)) : 0;
        int fgCol = ColorUtil.withAlphaFloat(color, alpha);
        tr.draw(textObj, x, 0.0f, fgCol, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, bgCol, 0xF000F0);

        matrices.pop();
    }

    public void playManual(String artist, String title) {
        this.manualPlayback = true;
        this.manualArtist = artist;
        this.manualTitle = title;
        this.manualStartTime = System.currentTimeMillis();
        this.lastSongKey = "";
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
        this.overallAlpha = 1.0f;
        LyricsFetcher.getInstance().fetchLyricsAsync(artist, title);
    }

    public void stopManual() {
        this.manualPlayback = false;
        this.manualArtist = "";
        this.manualTitle = "";
    }

    public void seekManual(long seconds) {
        this.manualStartTime = System.currentTimeMillis() - (seconds * 1000L);
    }

    public boolean isManualPlayback() {
        return manualPlayback;
    }
}
