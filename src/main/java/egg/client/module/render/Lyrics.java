package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lyrics extends Module {
    private final BooleanSetting demoMode = new BooleanSetting("Демо трек", false, "Воспроизведение демо-трека Queen - Bohemian Rhapsody");
    private final SliderSetting distance = new SliderSetting("Дистанция", 32.0, 10.0, 80.0, 1.0, "Расстояние до текста в скайбоксе");
    private final SliderSetting elevation = new SliderSetting("Высота", 14.0, 0.0, 50.0, 1.0, "Высота над горизонтом");
    private final SliderSetting size = new SliderSetting("Размер", 1.2, 0.6, 2.5, 0.05, "Масштаб шрифта");
    private final BooleanSetting glow = new BooleanSetting("Неоновое свечение", true, "Свечение в цвет темы клиента");
    private final BooleanSetting background = new BooleanSetting("Тёмная подложка", true, "Фон для контраста в небе");
    private final BooleanSetting trackBadge = new BooleanSetting("Инфо о треке", false, "Название трека и исполнитель над текстом");
    private final BooleanSetting karaoke = new BooleanSetting("Караоке анимация", true, "Анимация проявления слов по мере пения трека");
    private final ModeSetting colorMode = new ModeSetting("Цвет текста", "Белый", "Белый", "Тема", "Градиент");

    private Vec3d activeSkyDirection = null;
    private Vec3d prevSkyDirection = null;

    private List<LyricLine> activeLyrics = null;
    private String lastSongKey = "";
    private int currentLineIndex = -1;
    private int prevLineIndex = -1;
    private long lineChangeTimeMs = 0L;
    private float overallAlpha = 0.0f;
    private float occlusionAlpha = 1.0f;
    private long lastFrameNanos = 0L;
    private long demoPlayTimeMs = 0L;

    public static class TimedWord {
        public final String word;
        public final float width;
        public final float xOffset;
        public final long startOffsetMs;
        public final long durationMs;

        public TimedWord(String word, float width, float xOffset, long startOffsetMs, long durationMs) {
            this.word = word;
            this.width = width;
            this.xOffset = xOffset;
            this.startOffsetMs = startOffsetMs;
            this.durationMs = durationMs;
        }
    }

    private int cachedWordsLineIndex = -1;
    private float cachedWordScale = -1.0f;
    private List<TimedWord> cachedWords = null;
    private float cachedLineWidth = 0.0f;
    private static final Pattern SYLLABLE_PATTERN = Pattern.compile("<(?:(\\d+):)?(\\d+(?:\\.\\d+)?)>([^<]+)");

    private boolean manualPlayback = false;
    private String manualArtist = "";
    private String manualTitle = "";
    private long manualStartTime = 0L;
    private long manualOffsetMs = 0L;

    public Lyrics() {
        super("Lyrics", "Отображает текст играющей музыки в скайбоксе с плавной анимацией", Category.ESP);
        addSettings(demoMode, distance, elevation, size, glow, background, trackBadge, karaoke, colorMode);
    }

    @Override
    public void onEnable() {
        overallAlpha = 1.0f;
        occlusionAlpha = 1.0f;
        activeSkyDirection = null;
        prevSkyDirection = null;
        if (demoMode.isEnabled()) {
            playDemo();
        }
    }

    @Override
    public void onDisable() {
        activeSkyDirection = null;
        prevSkyDirection = null;
        activeLyrics = null;
        lastSongKey = "";
        currentLineIndex = -1;
        prevLineIndex = -1;
        cachedWordsLineIndex = -1;
        cachedWords = null;
        cachedLineWidth = 0.0f;
        overallAlpha = 0.0f;
    }

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        if (camera == null) return;

        long now = System.nanoTime();
        float dt = (lastFrameNanos == 0L) ? 0.016f : MathHelper.clamp((now - lastFrameNanos) / 1.0E9f, 0.001f, 0.05f);
        lastFrameNanos = now;

        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        boolean hasInterfaceMedia = iface != null && iface.hasActiveMedia() && iface.isMediaPlaying()
                && iface.getCurrentMediaTitle() != null && !iface.getCurrentMediaTitle().isBlank();

        MediaTracker mt = MediaTracker.getInstance();
        boolean hasTrackerMedia = mt != null && mt.isPlaying()
                && mt.getTrackTitle() != null && !mt.getTrackTitle().isBlank();

        boolean isRealMusic = hasInterfaceMedia || hasTrackerMedia;
        boolean isDemo = !isRealMusic && demoMode.isEnabled();

        String currentArtist;
        String currentTitle;
        long playTimeMs;
        float targetAlpha;

        if (manualPlayback && !manualTitle.isEmpty()) {
            currentTitle = manualTitle;
            currentArtist = manualArtist;
            playTimeMs = Math.max(0L, (System.currentTimeMillis() - manualStartTime) + manualOffsetMs);
            targetAlpha = 1.0f;
        } else if (hasInterfaceMedia) {
            currentTitle = iface.getCurrentMediaTitle().trim();
            currentArtist = iface.getCurrentMediaArtist() != null ? iface.getCurrentMediaArtist().trim() : "";
            playTimeMs = iface.getCurrentPositionMs();
            targetAlpha = 1.0f;
        } else if (hasTrackerMedia) {
            currentTitle = mt.getTrackTitle().trim();
            currentArtist = mt.getTrackArtist() != null ? mt.getTrackArtist().trim() : "";
            playTimeMs = mt.getTrackElapsedMs();
            targetAlpha = 1.0f;
        } else if (isDemo) {
            demoPlayTimeMs += (long) (dt * 1000f);
            if (demoPlayTimeMs > 90000L) demoPlayTimeMs = 0L;
            currentArtist = "Queen";
            currentTitle = "Bohemian Rhapsody";
            playTimeMs = demoPlayTimeMs;
            if (activeLyrics == null || activeLyrics.isEmpty()) {
                activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
            }
            targetAlpha = 1.0f;
        } else {
            currentArtist = "";
            currentTitle = "";
            playTimeMs = 0L;
            targetAlpha = 0.0f;
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
                prevSkyDirection = activeSkyDirection;
                currentLineIndex = found;
                lineChangeTimeMs = System.currentTimeMillis();

                activeSkyDirection = calculateSkyDirection(camera);
            }
        }

        if (activeSkyDirection == null) {
            activeSkyDirection = calculateSkyDirection(camera);
        }

        Vec3d camPos = camera.getCameraPos();
        Vec3d lookVec = getCameraLookVector(camera);

        boolean occluded = checkOcclusion(mc, camPos, activeSkyDirection);
        float targetOcc = occluded ? 0.0f : 1.0f;
        this.occlusionAlpha += (targetOcc - this.occlusionAlpha) * Math.min(1.0f, dt * 14.0f);
        if (this.occlusionAlpha <= 0.01f) {
            return;
        }

        double dot = lookVec.dotProduct(activeSkyDirection);
        float lookFactor = (float) MathHelper.clamp((dot - 0.72) / (0.90 - 0.72), 0.0, 1.0);
        if (lookFactor <= 0.01f) {
            return;
        }

        Vec3d targetWorldPos = camPos.add(activeSkyDirection.multiply(distance.getValue()));
        Vector2f currentScreenPos = ProjectionUtil.worldToScreen(targetWorldPos);
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        boolean currentVisible = currentScreenPos != null
                && currentScreenPos.x >= 10 && currentScreenPos.x <= screenW - 10
                && currentScreenPos.y >= 10 && currentScreenPos.y <= screenH - 10;

        Vector2f prevScreenPos = null;
        float prevLookFactor = 0.0f;
        if (prevSkyDirection != null) {
            double prevDot = lookVec.dotProduct(prevSkyDirection);
            prevLookFactor = (float) MathHelper.clamp((prevDot - 0.72) / (0.90 - 0.72), 0.0, 1.0);
            if (prevLookFactor > 0.01f) {
                Vec3d prevWorldPos = camPos.add(prevSkyDirection.multiply(distance.getValue()));
                prevScreenPos = ProjectionUtil.worldToScreen(prevWorldPos);
            }
        }

        if (!currentVisible && prevScreenPos == null) {
            return;
        }

        long elapsedSinceChange = System.currentTimeMillis() - lineChangeTimeMs;
        float animProgress = MathHelper.clamp(elapsedSinceChange / 450.0f, 0.0f, 1.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - animProgress, 3);

        int accentColor = Theme.getAccent();
        int activeTextColor = switch (colorMode.get()) {
            case "Тема" -> accentColor;
            case "Градиент" -> ColorUtil.lerp(0xFFFFFFFF, accentColor, 0.40f);
            default -> 0xFFFFFFFF;
        };

        float userScale = size.getValueFloat();
        float mainFontSize = 21.0f * userScale;
        float nextFontSize = 15.0f * userScale;
        float badgeFontSize = 11.0f * userScale;

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

        float effectiveAlpha = overallAlpha * lookFactor * this.occlusionAlpha;

        if (currentVisible && currentScreenPos != null) {
            float cx = currentScreenPos.x;
            float cy = currentScreenPos.y;

            // Safe clearance against top HUD (Dynamic Island, Watermark) and bottom hotbar
            float topClearanceY = 64.0f * userScale;
            float topFade = MathHelper.clamp((cy - topClearanceY) / (22.0f * userScale), 0.0f, 1.0f);
            float bottomClearanceY = screenH - 52.0f * userScale;
            float bottomFade = MathHelper.clamp((bottomClearanceY - cy) / (22.0f * userScale), 0.0f, 1.0f);
            float hudClearanceFade = topFade * bottomFade;

            float lineEffectiveAlpha = effectiveAlpha * hudClearanceFade;

            if (lineEffectiveAlpha > 0.01f) {
                if (trackBadge.isEnabled() && !currentTitle.isEmpty()) {
                    String badgeText = (currentArtist.isEmpty() ? "" : currentArtist + " — ") + currentTitle;
                    float bw = Fonts.d.a(badgeText, badgeFontSize) + 16.0f * userScale;
                    float bh = Fonts.d.a(badgeFontSize) + 7.0f * userScale;
                    float bx = cx - bw / 2.0f;
                    float by = cy - 32.0f * userScale;

                    if (background.isEnabled()) {
                        Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, bh, 4.0f * userScale, ColorUtil.rgba(14, 15, 20, (int) (180 * lineEffectiveAlpha)));
                        Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, bw, bh, 4.0f * userScale, 0.8f, ColorUtil.withAlphaFloat(accentColor, 0.50f * lineEffectiveAlpha));
                    }
                    float tx = cx - Fonts.d.a(badgeText, badgeFontSize) / 2.0f;
                    float ty = by + (bh - Fonts.d.a(badgeFontSize)) / 2.0f - 0.5f;
                    Fonts.d.a(null, badgeText, tx, ty, badgeFontSize, ColorUtil.withAlphaFloat(accentColor, 0.95f * lineEffectiveAlpha));
                }

                float line1Y = cy + (1.0f - ease) * 14.0f;
                float line1Alpha = (0.50f + 0.50f * ease) * lineEffectiveAlpha;

                if (karaoke.isEnabled() && activeLyrics != null && !activeLyrics.isEmpty()) {
                    int mainIdx = currentLineIndex >= 0 ? currentLineIndex : 0;
                    LyricLine curLine = activeLyrics.get(mainIdx);
                    LyricLine nxtLine = (mainIdx + 1 < activeLyrics.size()) ? activeLyrics.get(mainIdx + 1) : null;
                    renderKaraokeLine(cx, line1Y, mainIdx, curLine, nxtLine, playTimeMs, mainFontSize, line1Alpha, activeTextColor, accentColor, userScale);
                } else {
                    renderVectorLine(cx, line1Y, currentText, mainFontSize, line1Alpha, activeTextColor, accentColor, true, userScale);
                }

                if (nextText != null && !nextText.isBlank()) {
                    float line2Y = cy + Fonts.d.a(mainFontSize) + 10.0f * userScale;
                    float line2Alpha = (0.35f + 0.15f * ease) * lineEffectiveAlpha;
                    int upcomingGrey = ColorUtil.rgba(140, 145, 160, (int) (180 * line2Alpha));
                    renderVectorLine(cx, line2Y, nextText, nextFontSize, line2Alpha, upcomingGrey, accentColor, false, userScale);
                }
            }
        }

        if (prevScreenPos != null && prevLineIndex >= 0 && activeLyrics != null && prevLineIndex < activeLyrics.size() && animProgress < 1.0f) {
            String prevText = activeLyrics.get(prevLineIndex).text();
            float prevAlpha = (1.0f - ease) * 0.70f * overallAlpha * prevLookFactor * this.occlusionAlpha;
            if (prevAlpha > 0.01f) {
                float px = prevScreenPos.x;
                float py = prevScreenPos.y - ease * 12.0f;
                renderVectorLine(px, py, prevText, mainFontSize, prevAlpha, activeTextColor, accentColor, true, userScale);
            }
        }

        DrawBatcher.flushPending();
    }

    private void updateWordCache(LyricLine line, LyricLine nextLine, float fontSize) {
        cachedWords = new ArrayList<>();
        if (line == null || line.text().isBlank()) {
            cachedLineWidth = 0.0f;
            return;
        }

        String rawText = line.text().trim();
        long lineStartTime = line.timeMs();

        if (rawText.contains("<") && rawText.contains(">")) {
            Matcher m = SYLLABLE_PATTERN.matcher(rawText);
            float curX = 0.0f;
            float spaceW = Fonts.d.a(" ", fontSize);
            long prevTagTime = lineStartTime;

            while (m.find()) {
                try {
                    String minStr = m.group(1);
                    int minutes = minStr != null ? Integer.parseInt(minStr) : 0;
                    float seconds = Float.parseFloat(m.group(2));
                    long tagTime = (long) ((minutes * 60 + seconds) * 1000);
                    String wordText = m.group(3).trim();
                    if (!wordText.isEmpty()) {
                        float wWidth = Fonts.d.a(wordText, fontSize);
                        long startOffset = Math.max(0L, tagTime - lineStartTime);
                        long dur = Math.max(180L, tagTime - prevTagTime);
                        cachedWords.add(new TimedWord(wordText, wWidth, curX, startOffset, dur));
                        curX += wWidth + spaceW;
                        prevTagTime = tagTime;
                    }
                } catch (Exception ignored) {}
            }
            if (!cachedWords.isEmpty()) {
                cachedLineWidth = Math.max(0.0f, curX - spaceW);
                return;
            }
        }

        String[] rawWords = rawText.split("\\s+");
        if (rawWords.length == 0) {
            cachedLineWidth = 0.0f;
            return;
        }

        long rawDuration;
        if (nextLine != null && nextLine.timeMs() > lineStartTime) {
            rawDuration = nextLine.timeMs() - lineStartTime;
        } else {
            rawDuration = Math.max(2500L, rawWords.length * 500L);
        }

        long singingDuration = Math.min(rawDuration, Math.max(2000L, rawWords.length * 480L));
        long usableDuration = (long) (singingDuration * 0.90f);

        float totalWeight = 0.0f;
        for (String w : rawWords) {
            totalWeight += Math.max(1.5f, (float) w.length());
        }

        float curX = 0.0f;
        float spaceW = Fonts.d.a(" ", fontSize);
        float accWeight = 0.0f;

        for (int i = 0; i < rawWords.length; i++) {
            String w = rawWords[i];
            float wWidth = Fonts.d.a(w, fontSize);
            float weight = Math.max(1.5f, (float) w.length());

            long startOffsetMs = (long) ((accWeight / totalWeight) * usableDuration);
            long wordDurMs = Math.max(180L, (long) ((weight / totalWeight) * usableDuration));

            cachedWords.add(new TimedWord(w, wWidth, curX, startOffsetMs, wordDurMs));
            curX += wWidth + spaceW;
            accWeight += weight;
        }

        cachedLineWidth = Math.max(0.0f, curX - spaceW);
    }

    private void renderKaraokeLine(float cx, float cy, int lineIndex, LyricLine line, LyricLine nextLine,
                                   long playTimeMs, float fontSize, float alpha,
                                   int activeTextColor, int accentColor, float userScale) {
        if (line == null || line.text().isBlank() || alpha <= 0.01f) return;

        if (cachedWordsLineIndex != lineIndex || Math.abs(cachedWordScale - userScale) > 0.01f || cachedWords == null) {
            cachedWordsLineIndex = lineIndex;
            cachedWordScale = userScale;
            updateWordCache(line, nextLine, fontSize);
        }

        if (cachedWords == null || cachedWords.isEmpty()) {
            renderVectorLine(cx, cy, line.text(), fontSize, alpha, activeTextColor, accentColor, true, userScale);
            return;
        }

        float totalW = cachedLineWidth;
        float lineH = Fonts.d.a(fontSize) + 8.0f * userScale;
        float padX = 14.0f * userScale;
        float boxW = totalW + padX * 2.0f;

        float bx = cx - boxW / 2.0f;
        float by = cy - lineH / 2.0f;

        if (background.isEnabled()) {
            int bgCol = ColorUtil.rgba(12, 14, 20, (int) (180 * alpha));
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, boxW, lineH, 5.0f * userScale, bgCol);

            if (glow.isEnabled()) {
                int outlineCol = ColorUtil.withAlphaFloat(accentColor, 0.45f * alpha);
                Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, boxW, lineH, 5.0f * userScale, 1.0f, outlineCol);
            }
        }

        float lineStartX = cx - totalW / 2.0f;
        float textY = cy - Fonts.d.a(fontSize) / 2.0f - 0.5f;

        long lineStartTime = line.timeMs();
        long lineElapsedMs = playTimeMs - lineStartTime;
        final long DURATION_MS = 180L;

        for (TimedWord tw : cachedWords) {
            float wordBaseX = lineStartX + tw.xOffset;
            long wordElapsed = lineElapsedMs - tw.startOffsetMs;

            if (wordElapsed < 0) {
                int greyCol = ColorUtil.rgba(140, 145, 160, (int) (165 * alpha));
                Fonts.d.a(null, tw.word, wordBaseX, textY, fontSize, greyCol);
            } else if (wordElapsed < DURATION_MS) {
                float t = (float) wordElapsed / (float) DURATION_MS;
                float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

                float oldY = textY - ease * 5.0f * userScale;
                int oldA = Math.round(165 * (1.0f - ease) * alpha);
                if (oldA > 5) {
                    int oldCol = ColorUtil.rgba(140, 145, 160, oldA);
                    Fonts.d.a(null, tw.word, wordBaseX, oldY, fontSize, oldCol);
                }

                float newY = textY + (1.0f - ease) * 5.0f * userScale;
                int newA = Math.round(255 * ease * alpha);
                if (newA > 5) {
                    int brightCol = ColorUtil.withAlphaFloat(activeTextColor, (float) newA / 255.0f);
                    Fonts.d.a(null, tw.word, wordBaseX, newY, fontSize, brightCol);
                }
            } else {
                int brightCol = ColorUtil.withAlphaFloat(activeTextColor, alpha);
                Fonts.d.a(null, tw.word, wordBaseX, textY, fontSize, brightCol);
            }
        }
    }

    private void renderVectorLine(float cx, float cy, String text, float fontSize, float alpha,
                                  int textColor, int glowColor, boolean bold, float userScale) {
        if (text == null || text.isBlank() || alpha <= 0.01f) return;

        float textW = Fonts.d.a(text, fontSize);
        float lineH = Fonts.d.a(fontSize) + 8.0f * userScale;
        float padX = 14.0f * userScale;
        float boxW = textW + padX * 2.0f;

        float bx = cx - boxW / 2.0f;
        float by = cy - lineH / 2.0f;

        if (background.isEnabled()) {
            int bgCol = ColorUtil.rgba(12, 14, 20, (int) (180 * alpha));
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, boxW, lineH, 5.0f * userScale, bgCol);

            if (glow.isEnabled() && bold) {
                int outlineCol = ColorUtil.withAlphaFloat(glowColor, 0.45f * alpha);
                Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, boxW, lineH, 5.0f * userScale, 1.0f, outlineCol);
            }
        }

        float textX = cx - textW / 2.0f;
        float textY = cy - Fonts.d.a(fontSize) / 2.0f - 0.5f;
        int finalCol = ColorUtil.withAlphaFloat(textColor, alpha);
        Fonts.d.a(null, text, textX, textY, fontSize, finalCol);
    }

    private boolean checkOcclusion(MinecraftClient mc, Vec3d camPos, Vec3d skyDir) {
        if (mc.world == null) return false;
        double rayDist = Math.max(10.0, distance.getValue());
        Vec3d rayEnd = camPos.add(skyDir.multiply(rayDist));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                camPos,
                rayEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            return true;
        }

        Box checkRange = new Box(camPos, rayEnd).expand(0.5);
        for (Entity candidate : mc.world.getOtherEntities(mc.player, checkRange)) {
            if (candidate.isAlive() && !candidate.isSpectator()) {
                Box box = candidate.getBoundingBox().expand(0.15);
                if (box.contains(camPos) || box.raycast(camPos, rayEnd).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vec3d calculateSkyDirection(Camera camera) {
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        float skyPitch = (float) -elevation.getValue();
        if (pitch < -5.0f) {
            skyPitch = pitch;
        }

        double radP = Math.toRadians(skyPitch);
        double radY = Math.toRadians(yaw);
        double lx = -Math.sin(radY) * Math.cos(radP);
        double ly = -Math.sin(radP);
        double lz = Math.cos(radY) * Math.cos(radP);

        return new Vec3d(lx, ly, lz).normalize();
    }

    private Vec3d getCameraLookVector(Camera camera) {
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        double radP = Math.toRadians(pitch);
        double radY = Math.toRadians(yaw);
        double lx = -Math.sin(radY) * Math.cos(radP);
        double ly = -Math.sin(radP);
        double lz = Math.cos(radY) * Math.cos(radP);
        return new Vec3d(lx, ly, lz).normalize();
    }

    public void playDemo() {
        this.demoMode.setEnabled(true);
        this.manualPlayback = false;
        this.demoPlayTimeMs = 0L;
        this.activeLyrics = LyricsFetcher.getInstance().getDemoLyrics();
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
        this.lastSongKey = "demo";
        this.activeSkyDirection = null;
        this.cachedWordsLineIndex = -1;
        this.cachedWords = null;
        this.overallAlpha = 1.0f;
        this.occlusionAlpha = 1.0f;
    }

    public void playManual(String artist, String title) {
        this.manualPlayback = true;
        this.demoMode.setEnabled(false);
        this.manualArtist = artist != null ? artist.trim() : "";
        this.manualTitle = title != null ? title.trim() : "";
        this.manualStartTime = System.currentTimeMillis();
        this.manualOffsetMs = 0L;
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
        this.activeSkyDirection = null;
        this.activeLyrics = null;
        this.lastSongKey = "";
        this.cachedWordsLineIndex = -1;
        this.cachedWords = null;
        this.overallAlpha = 1.0f;
        this.occlusionAlpha = 1.0f;
    }

    public void stopManual() {
        this.manualPlayback = false;
        this.demoMode.setEnabled(false);
        this.overallAlpha = 0.0f;
        this.activeLyrics = null;
        this.activeSkyDirection = null;
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
    }

    public void seekManual(long seconds) {
        this.manualOffsetMs = seconds * 1000L;
        this.manualStartTime = System.currentTimeMillis();
        this.currentLineIndex = -1;
        this.prevLineIndex = -1;
    }
}
