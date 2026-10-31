package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.render.shader.ProjectionUtil;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import egg.client.util.LyricsFetcher;
import egg.client.util.MediaTracker;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.List;

public class Lyrics extends Module {
    private final BooleanSetting demoMode = new BooleanSetting("Демо трек", false, "Воспроизведение демо-трека Queen - Bohemian Rhapsody");
    private final SliderSetting distance = new SliderSetting("Дистанция", 28.0, 10.0, 80.0, 1.0, "Расстояние до текста в скайбоксе");
    private final SliderSetting elevation = new SliderSetting("Высота", 14.0, 0.0, 50.0, 1.0, "Высота над горизонтом");
    private final SliderSetting size = new SliderSetting("Размер", 1.25, 0.5, 3.0, 0.1, "Масштаб шрифта");
    private final SliderSetting followSpeed = new SliderSetting("Слежение", 0.28, 0.05, 1.0, 0.02, "Плавность инерции за взглядом");
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
    public void onEnable() {
        overallAlpha = 1.0f;
        currentFloatingPos = null;
        if (demoMode.isEnabled()) {
            playDemo();
        }
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

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        if (camera == null) return;

        float delta = (tickCounter != null) ? tickCounter.getTickProgress(true) : 1.0f;
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
            if (demoPlayTimeMs > 90000L) demoPlayTimeMs = 0L;
            currentArtist = "Queen";
            currentTitle = "Bohemian Rhapsody";
            playTimeMs = demoPlayTimeMs;
            if (activeLyrics == null || activeLyrics.isEmpty()) {
                activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
            }
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

        overallAlpha += (targetAlpha - overallAlpha) * Math.min(1.0f, dt * 6.0f);
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

        Vec3d camPos = camera.getCameraPos();
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        double radPitch = Math.toRadians(pitch);
        double radYaw = Math.toRadians(yaw);
        double lookX = -Math.sin(radYaw) * Math.cos(radPitch);
        double lookY = -Math.sin(radPitch);
        double lookZ = Math.cos(radYaw) * Math.cos(radPitch);

        double elevOff = elevation.getValue() / 40.0;
        double skyY = Math.max(0.12 + elevOff, lookY + elevOff);
        Vec3d skyUnit = new Vec3d(lookX, skyY, lookZ).normalize();
        double dist = distance.getValue();
        Vec3d targetPos = camPos.add(skyUnit.multiply(dist));

        if (currentFloatingPos == null) {
            currentFloatingPos = targetPos;
        } else {
            float fSpeed = followSpeed.getValueFloat();
            float lerpAmt = 1.0f - (float) Math.exp(-fSpeed * dt * 10.0f);
            currentFloatingPos = currentFloatingPos.lerp(targetPos, lerpAmt);
        }

        Vector2f screen = ProjectionUtil.worldToScreen(currentFloatingPos);
        if (screen == null) {
            screen = ProjectUtil.toScreen(currentFloatingPos.x, currentFloatingPos.y, currentFloatingPos.z, delta);
            if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) {
                return;
            }
        }

        float cx = screen.x;
        float cy = screen.y;

        long elapsed = System.currentTimeMillis() - lineChangeTimeMs;
        float t = MathHelper.clamp(elapsed / 340.0f, 0.0f, 1.0f);
        float ease = (float) (0.5 - 0.5 * Math.cos(t * Math.PI));

        int accentColor = Theme.getAccent();
        int activeColor = switch (colorMode.get()) {
            case "Тема" -> accentColor;
            case "Градиент" -> ColorUtil.lerp(0xFFFFFFFF, accentColor, 0.45f);
            default -> 0xFFFFFFFF;
        };

        String currentText;
        String nextText = null;

        if (activeLyrics != null && !activeLyrics.isEmpty()) {
            int mainIdx = currentLineIndex >= 0 ? currentLineIndex : 0;
            currentText = activeLyrics.get(mainIdx).text();
            if (mainIdx + 1 < activeLyrics.size()) {
                nextText = activeLyrics.get(mainIdx + 1).text();
            }
        } else {
            currentText = (activeLyrics == null) ? "Поиск текста песни..." : "Инструментал";
        }

        float userScale = size.getValueFloat();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(userScale, userScale);

        if (trackBadge.isEnabled() && !currentTitle.isEmpty()) {
            String badge = "♫ " + (currentArtist.isEmpty() ? "" : currentArtist + " — ") + currentTitle;
            float bw = ClientFont.getWidth(badge, ClientFont.SF_MEDIUM) + 14.0f;
            float bh = 15.0f;
            float by = -34.0f;
            if (background.isEnabled()) {
                Render2D.drawRoundedRect(context, -bw / 2.0f, by, bw, bh, 4.0f, ColorUtil.rgba(14, 15, 20, (int) (170 * overallAlpha)));
                Render2D.drawRoundedOutline(context, -bw / 2.0f, by, bw, bh, 4.0f, 0.8f, ColorUtil.withAlphaFloat(accentColor, 0.55f * overallAlpha));
            }
            ClientFont.drawCentered(context, badge, 0.0f, by + bh / 2.0f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(accentColor, 0.95f * overallAlpha));
        }

        float line1Y = 16.0f * (1.0f - ease);
        float line1Alpha = (0.60f + 0.40f * ease) * overallAlpha;
        float line1Scale = 0.90f + 0.10f * ease;
        renderLine2D(context, currentText, line1Y, line1Scale, line1Alpha, activeColor, accentColor, true);

        if (nextText != null) {
            float line2Y = 16.0f + 16.0f * (1.0f - ease);
            float line2Alpha = (0.35f + 0.20f * ease) * overallAlpha;
            float line2Scale = 0.78f;
            renderLine2D(context, nextText, line2Y, line2Scale, line2Alpha, ColorUtil.rgba(215, 220, 235, 255), accentColor, false);
        }

        if (prevLineIndex >= 0 && activeLyrics != null && prevLineIndex < activeLyrics.size() && t < 1.0f) {
            String prevText = activeLyrics.get(prevLineIndex).text();
            float prevY = -16.0f * ease;
            float prevAlpha = (1.0f - ease) * 0.70f * overallAlpha;
            float prevScale = 1.0f - 0.10f * ease;
            renderLine2D(context, prevText, prevY, prevScale, prevAlpha, activeColor, accentColor, true);
        }

        context.getMatrices().popMatrix();
    }

    private void renderLine2D(DrawContext context, String text, float y, float scale, float alpha,
                              int textColor, int glowColor, boolean bold) {
        if (text == null || text.isBlank() || alpha <= 0.01f) return;
        var font = bold ? ClientFont.SF_BOLD : ClientFont.SF_MEDIUM;
        float textW = ClientFont.getWidth(text, font);
        float lineH = ClientFont.FONT_HEIGHT + 7.0f;
        float boxW = textW + 16.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, y);
        context.getMatrices().scale(scale, scale);

        float bx = -boxW / 2.0f;
        float by = -lineH / 2.0f;

        if (background.isEnabled()) {
            Render2D.drawShadow(context, bx, by, boxW, lineH, 5.0f, 6.0f, ColorUtil.rgba(0, 0, 0, (int) (125 * alpha)));
            Render2D.drawRoundedRect(context, bx, by, boxW, lineH, 5.0f, ColorUtil.rgba(14, 15, 20, (int) (165 * alpha)));
            if (glow.isEnabled() && bold) {
                Render2D.drawRoundedOutline(context, bx, by, boxW, lineH, 5.0f, 1.0f, ColorUtil.withAlphaFloat(glowColor, 0.45f * alpha));
            }
        }

        int finalCol = ColorUtil.withAlphaFloat(textColor, alpha);
        ClientFont.drawCentered(context, text, 0.0f, 0.0f, font, finalCol);

        context.getMatrices().popMatrix();
    }

    public void playDemo() {
        this.demoMode.setEnabled(true);
        this.manualPlayback = false;
        this.demoPlayTimeMs = 0L;
        this.activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
        this.overallAlpha = 1.0f;
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
        this.currentFloatingPos = null;
    }

    public void playManual(String artist, String title) {
        this.demoMode.setEnabled(false);
        this.manualPlayback = true;
        this.manualArtist = artist;
        this.manualTitle = title;
        this.manualStartTime = System.currentTimeMillis();
        this.lastSongKey = "";
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
        this.overallAlpha = 1.0f;
        this.currentFloatingPos = null;

        if (artist.equalsIgnoreCase("Queen") && title.toLowerCase().contains("bohemian")) {
            this.activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
        } else {
            LyricsFetcher.getInstance().fetchLyricsAsync(artist, title).thenAccept(lines -> {
                if (manualPlayback && manualTitle.equals(title)) {
                    this.activeLyrics = lines;
                }
            });
        }
    }

    public void stopManual() {
        this.manualPlayback = false;
        this.manualArtist = "";
        this.manualTitle = "";
        this.demoMode.setEnabled(false);
    }

    public void seekManual(long seconds) {
        if (demoMode.isEnabled()) {
            this.demoPlayTimeMs = seconds * 1000L;
        } else {
            this.manualStartTime = System.currentTimeMillis() - (seconds * 1000L);
        }
    }

    public BooleanSetting getDemoMode() {
        return demoMode;
    }

    public boolean isManualPlayback() {
        return manualPlayback;
    }
}
