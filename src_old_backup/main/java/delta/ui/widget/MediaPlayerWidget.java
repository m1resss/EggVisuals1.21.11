package delta.ui.widget;

import static delta.core.Interface.aM_;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.config.ThemeInfo;
import delta.core.Delta;
import delta.core.EventManager;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.module.render.Interface_2;
import delta.event.ClickEvent;
import delta.event.DrawEvent;
import delta.optmedia.Artist;
import delta.optmedia.OptMedia;
import delta.render.ColorUtil;
import delta.render.Draw2DProcessor;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ScissorUtil;
import delta.ui.element.DragInfo;
import delta.util.MathUtil;
import platform.inject.accessors.BossBarHudAccessor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlayerWidget extends Widget implements Interface {

    private static final Identifier ALBUM_ART_ID = Identifier.of("delta", "dynamic/media_cover");
    private static final ExecutorService POLL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Delta-OptMedia-Poll");
        t.setDaemon(true);
        return t;
    });

    private static final float[] WAVE_SHAPE = new float[] {
            0.40f, 0.55f, 0.70f, 0.85f, 1.00f, 0.90f, 0.75f, 0.60f,
            0.50f, 0.65f, 0.80f, 0.95f, 0.85f, 0.70f, 0.55f, 0.45f,
            0.60f, 0.75f, 0.90f, 0.80f, 0.60f, 0.45f
    };

    private long lastPollTime = 0L;
    private final Artist artistStruct = new Artist();
    private volatile boolean hasActiveMedia = false;
    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile String currentSource = "";
    private volatile boolean isPlaying = false;
    private volatile long currentDurationMs = 0L;
    private volatile long currentPositionMs = 0L;

    private volatile boolean hasCoverArt = false;
    private volatile byte[] pendingCoverBytes = null;
    private int currentCoverHash = 0;
    private String lastTrackKey = "";
    private NativeImageBackedTexture coverTexture = null;
    private int dominantAccentColor = 0xFF14B8A6; // Default emerald / teal like photo 2
    private float currentAccentR = 0.08f;
    private float currentAccentG = 0.72f;
    private float currentAccentB = 0.65f;

    private int failCount = 0;
    private float scrollOffset = 0.0f;
    private long lastScrollTime = 0L;

    // Dynamic Island state & animation
    private boolean expanded = false;
    private final delta.render.AnimationUtil expandAnimation = new delta.render.AnimationUtil();
    private float currentYPos = 8.0f;

    // Absorb bounce deformation for ClickGUI folding
    private static long absorbStartTime = 0L;
    private static float absorbIntensity = 0.0f;

    public static void triggerAbsorbBounce() {
        absorbStartTime = System.currentTimeMillis();
        absorbIntensity = 1.0f;
    }

    public static float getAbsorbScaleX() {
        if (absorbStartTime == 0L) return 1.0f;
        long elapsed = System.currentTimeMillis() - absorbStartTime;
        if (elapsed > 450L) {
            absorbStartTime = 0L;
            return 1.0f;
        }
        float t = (float) elapsed / 450.0f;
        // Damped spring oscillation: expands horizontally, squashes, bounces back
        float oscillation = (float) (Math.sin(t * Math.PI * 3.0) * Math.exp(-t * 4.5));
        return 1.0f + 0.16f * oscillation;
    }

    public static float getAbsorbScaleY() {
        if (absorbStartTime == 0L) return 1.0f;
        long elapsed = System.currentTimeMillis() - absorbStartTime;
        if (elapsed > 450L) {
            absorbStartTime = 0L;
            return 1.0f;
        }
        float t = (float) elapsed / 450.0f;
        // Counter-oscillation vertically for volume preservation
        float oscillation = (float) (Math.sin(t * Math.PI * 3.0) * Math.exp(-t * 4.5));
        return 1.0f - 0.12f * oscillation;
    }

    public static void renderDirect(DrawContext context, float delta) {
        Interface_2 iface = Interface_2.getInstance();
        if (iface == null) return;
        MediaPlayerWidget mpw = iface.getMediaPlayerWidget();
        if (mpw == null) return;
        DrawEvent de = new DrawEvent(context, delta, DrawEvent.a.D2D);
        mpw.a(de);
    }

    // Control hitboxes for clicks
    private float lastArtX = 0.0f;
    private float lastArtY = 0.0f;
    private float lastArtSize = 32.0f;
    private float lastWaveX = 0.0f;
    private float lastWaveW = 54.0f;
    private float lastRowY = 0.0f;
    private float lastBtnPrevX = 0.0f;
    private float lastBtnNextX = 0.0f;
    private float lastCloseX = 0.0f;
    private float lastCloseY = 0.0f;

    public MediaPlayerWidget() {
        super(new DragInfo("Проигрыватель", 10.0f, 8.0f, 152.0f, 20.0f));
        j().a(this);
        j().a(2); // Non-draggable ("его нельзя будет двигать")
        EventManager.a(this);
    }

    private void pollMedia() {
        long now = System.currentTimeMillis();
        if (now - lastPollTime < 300L) {
            return;
        }
        lastPollTime = now;

        POLL_EXECUTOR.submit(() -> {
            if (!OptMedia.init() || OptMedia.INSTANCE == null) {
                if (++failCount >= 4) {
                    hasActiveMedia = false;
                }
                return;
            }
            try {
                boolean ok = OptMedia.INSTANCE.GetCurrentMediaInfo(artistStruct);
                if (ok && artistStruct.getTitle() != null && !artistStruct.getTitle().trim().isEmpty()) {
                    failCount = 0;
                    hasActiveMedia = true;
                    currentTitle = artistStruct.getTitle().trim();
                    currentArtist = artistStruct.getArtist().trim();
                    currentSource = artistStruct.getSource().trim();
                    isPlaying = artistStruct.isPlaying();
                    currentDurationMs = Math.max(0L, artistStruct.durationMs);
                    currentPositionMs = Math.max(0L, artistStruct.positionMs);

                    String trackKey = currentTitle + "|" + currentArtist + "|" + currentDurationMs;
                    boolean trackChanged = !trackKey.equals(lastTrackKey);
                    if (trackChanged) {
                        lastTrackKey = trackKey;
                    }

                    byte[] art = artistStruct.getAlbumArt();
                    if (art != null && art.length > 0) {
                        pendingCoverBytes = art;
                    } else if (trackChanged) {
                        // Track changed and has no cover
                        pendingCoverBytes = null;
                        destroyCoverTexture();
                    }

                    try {
                        OptMedia.INSTANCE.FreeMediaInfo(artistStruct);
                        artistStruct.albumArtPng = null;
                        artistStruct.albumArtSize = 0;
                    } catch (Throwable ignored) {}
                } else {
                    if (++failCount >= 4) {
                        hasActiveMedia = false;
                    }
                }
            } catch (Throwable ignored) {
                if (++failCount >= 4) {
                    hasActiveMedia = false;
                }
            }
        });
    }

    private void destroyCoverTexture() {
        if (hasCoverArt) {
            try {
                aM_.getTextureManager().destroyTexture(ALBUM_ART_ID);
                if (coverTexture != null) {
                    coverTexture.close();
                    coverTexture = null;
                }
            } catch (Throwable ignored) {}
            hasCoverArt = false;
            currentCoverHash = 0;
        }
    }

    private void updateCoverTexture() {
        if (pendingCoverBytes != null) {
            byte[] bytes = pendingCoverBytes;
            pendingCoverBytes = null;
            int hash = Arrays.hashCode(bytes);
            if (hash == currentCoverHash && hasCoverArt) {
                return;
            }
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    extractDominantColor(img);
                    TextureManager tm = aM_.getTextureManager();
                    if (coverTexture != null) {
                        tm.destroyTexture(ALBUM_ART_ID);
                        coverTexture.close();
                    }
                    coverTexture = new NativeImageBackedTexture(() -> "album_art", img);
                    tm.registerTexture(ALBUM_ART_ID, coverTexture);
                    currentCoverHash = hash;
                    hasCoverArt = true;
                }
            } catch (Throwable ignored) {
                hasCoverArt = false;
            }
        }
    }

    private void extractDominantColor(NativeImage img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            long rSum = 0, gSum = 0, bSum = 0, count = 0;
            int stepX = Math.max(1, w / 16);
            int stepY = Math.max(1, h / 16);
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int c = img.getColorArgb(x, y);
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    // Filter out nearly black or washed out white
                    int maxC = Math.max(r, Math.max(g, b));
                    int minC = Math.min(r, Math.min(g, b));
                    if (maxC > 35 && (maxC - minC) > 20) {
                        rSum += r;
                        gSum += g;
                        bSum += b;
                        count++;
                    }
                }
            }
            if (count > 0) {
                int avgR = (int) (rSum / count);
                int avgG = (int) (gSum / count);
                int avgB = (int) (bSum / count);
                this.dominantAccentColor = (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void a(DrawEvent event) {
        pollMedia();
        updateCoverTexture();

        boolean inGui = aM_.currentScreen instanceof ChatScreen || (aM_.currentScreen != null && aM_.currentScreen.getClass().getSimpleName().toLowerCase().contains("gui"));
        boolean visible = hasActiveMedia || inGui;

        d().a(visible);
        d().a(0.0f, 1.0f, 0.25f, EasingList.g, event.g());
        float anim = d().c();
        if (anim <= 0.01f) {
            super.a(event);
            return;
        }

        // Animate Dynamic Island expansion / collapse with smooth spring / cubic easing
        this.expandAnimation.a(this.expanded);
        this.expandAnimation.a(0.0f, 1.0f, 0.32f, this.expanded ? EasingList.s : EasingList.g, event.g());
        float expand = MathHelper.clamp(this.expandAnimation.c(), 0.0f, 1.05f);

        // Adapt position below boss bars if active
        int bossBarCount = 0;
        try {
            if (aM_.inGameHud != null && aM_.inGameHud.getBossBarHud() != null) {
                BossBarHudAccessor accessor = (BossBarHudAccessor) aM_.inGameHud.getBossBarHud();
                if (accessor.getBossBars() != null) {
                    bossBarCount = accessor.getBossBars().size();
                }
            }
        } catch (Throwable ignored) {}

        float targetY = 8.0f;
        if (bossBarCount > 0) {
            targetY = 24.0f + (bossBarCount - 1) * 19.0f;
        }
        this.currentYPos = MathUtil.c(this.currentYPos, targetY, 12.0f);

        // Screen dimensions & island bounds
        float screenW = aM_.getWindow().getScaledWidth();
        float collapsedW = 152.0f;
        float collapsedH = 20.0f;
        float expandedW = 188.0f;
        float expandedH = 58.0f;

        float curW = MathHelper.lerp(expand, collapsedW, expandedW);
        float curH = MathHelper.lerp(expand, collapsedH, expandedH);
        float x = (screenW - curW) / 2.0f;
        float y = this.currentYPos;
        float radius = MathHelper.lerp(expand, 10.0f, 14.0f);

        // Lock drag info and sync coordinates ("его нельзя будет двигать")
        j().a(2);
        j().a(x);
        j().b(y);
        j().c(curW);
        j().d(curH);

        Draw2DProcessor draw = event.d();

        // Absorb deformation transformation (iOS Dynamic Island spring bounce on GUI intake)
        float absorbSx = getAbsorbScaleX();
        float absorbSy = getAbsorbScaleY();
        boolean hasAbsorb = (absorbSx != 1.0f || absorbSy != 1.0f);
        if (hasAbsorb) {
            float icx = x + curW * 0.5f;
            float icy = y + curH * 0.5f;
            event.h().push();
            event.h().translate(icx, icy, 0.0f);
            event.h().scale(absorbSx, absorbSy, 1.0f);
            event.h().translate(-icx, -icy, 0.0f);
        }

        // 1. Sleek OLED Black Capsule (Always opaque without blur as requested: "динамик айленд всегда непрозрачный без блюра")
        delta.module.render.Interface_2 iface = delta.module.render.Interface_2.getInstance();
        int baseIslandBg = 0xFF0A0A0E;
        if (iface != null && iface.getDynamicIslandBgColor() != null) {
            baseIslandBg = iface.getDynamicIslandBgColor().c().intValue();
        }
        int bg = ColorUtil.a(baseIslandBg, (int) (255 * anim));
        int border = ColorUtil.a(255, 255, 255, (int) (24 * anim));

        // Always draw crisp opaque capsule without blur
        draw.a(event.h(), x, y, curW, curH, radius, bg);

        // Dynamic fluid ambient gradient in both closed capsule and expanded card
        int targetCol = this.dominantAccentColor;
        float tr = ((targetCol >> 16) & 0xFF) / 255.0f;
        float tg = ((targetCol >> 8) & 0xFF) / 255.0f;
        float tb = (targetCol & 0xFF) / 255.0f;

        this.currentAccentR = MathUtil.c(this.currentAccentR, tr, 0.15f);
        this.currentAccentG = MathUtil.c(this.currentAccentG, tg, 0.15f);
        this.currentAccentB = MathUtil.c(this.currentAccentB, tb, 0.15f);

        int accentR = Math.max(0, Math.min(255, (int) (this.currentAccentR * 255.0f)));
        int accentG = Math.max(0, Math.min(255, (int) (this.currentAccentG * 255.0f)));
        int accentB = Math.max(0, Math.min(255, (int) (this.currentAccentB * 255.0f)));

        // Dynamic breathing wave oscillation based on playback
        float t = (float) (System.currentTimeMillis() % 1000000L) * 0.002f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t);

        // Fluid gradient active in both compact closed pill and expanded card
        float closedAlpha = (0.28f + pulse * 0.10f) * anim;
        float expandedAlpha = (0.35f + pulse * 0.12f) * anim;
        float gradAlpha = MathHelper.lerp(expand, closedAlpha, expandedAlpha);

        int colTL = ColorUtil.a(0, 0, 0, 0);
        int colTR = ColorUtil.a(accentR, accentG, accentB, (int) (MathHelper.lerp(expand, gradAlpha * 0.40f, gradAlpha * 0.35f) * 255.0f));
        int colBL = ColorUtil.a(accentR, accentG, accentB, (int) (MathHelper.lerp(expand, 0.0f, gradAlpha * 0.25f) * 255.0f));
        int colBR = ColorUtil.a(accentR, accentG, accentB, (int) (MathHelper.lerp(expand, gradAlpha * 0.85f, gradAlpha) * 255.0f));

        draw.a(event.h(), x, y, curW, curH, new org.joml.Vector4f(radius, radius, radius, radius), colTL, colTR, colBL, colBR);

        draw.a(event.h(), x, y, curW, curH, radius, 0.7f, border);

        // 2. Album Cover (Morphs smoothly between Dynamic Island small icon and full card cover)
        float collapsedArtSize = 14.5f;
        float expandedArtSize = 38.0f;
        float artSize = MathHelper.lerp(expand, collapsedArtSize, expandedArtSize);
        float artX = MathHelper.lerp(expand, x + 3.0f, x + 8.5f);
        float artY = MathHelper.lerp(expand, y + (curH - artSize) * 0.5f, y + 8.5f);
        float artRadius = MathHelper.lerp(expand, 3.5f, 7.0f);

        this.lastArtX = artX;
        this.lastArtY = artY;
        this.lastArtSize = artSize;

        if (hasCoverArt) {
            draw.a(event.h(), ALBUM_ART_ID, artX, artY, artSize, artSize, artRadius, ColorUtil.a(255, 255, 255, (int) (255 * anim)));
        } else {
            int phBg = ColorUtil.a(24, 22, 32, (int) (230 * anim));
            draw.a(event.h(), artX, artY, artSize, artSize, artRadius, phBg);
            draw.a(event.h(), artX, artY, artSize, artSize, artRadius, 0.5f, ColorUtil.a(255, 255, 255, (int) (18 * anim)));
        }

        // 3. Dynamic Island Mode: Song Title next to cover + Animated Equalizer Bars on right
        float collapsedAlpha = (1.0f - expand) * anim;
        if (collapsedAlpha > 0.02f) {
            String displayTitle = hasActiveMedia ? currentTitle : "Ничего не играет";

            float barW = 1.8f;
            float barGap = 1.5f;
            float totalBarsW = 5 * barW + 4 * barGap; // 15.0f
            float barsRight = x + curW - 8.0f;
            float barsStartX = barsRight - totalBarsW;
            float barsCenterY = y + curH * 0.5f;

            // Centered Song Title in the middle area between album cover and equalizer bars
            float areaLeft = artX + artSize + 6.0f;
            float areaRight = barsStartX - 6.0f;
            float maxTitleW = areaRight - areaLeft;
            if (maxTitleW > 15.0f) {
                float titleFontSize = 8.5f;
                float titleW = Fonts.e.a(displayTitle, titleFontSize);
                int titleCol = ColorUtil.a(255, 255, 255, (int) (245 * collapsedAlpha));
                float titleY = Fonts.e.a(displayTitle, titleFontSize, y + curH * 0.5f);

                if (titleW > maxTitleW) {
                    long now = System.currentTimeMillis();
                    if (lastScrollTime == 0L) lastScrollTime = now;
                    float dt = (now - lastScrollTime) / 1000.0f;
                    lastScrollTime = now;
                    scrollOffset = (scrollOffset + dt * 16.0f) % (titleW + 18.0f);

                    ScissorUtil.a(event.h(), areaLeft, y, maxTitleW, curH);
                    Fonts.e.a(event.h(), displayTitle, areaLeft - scrollOffset, titleY, titleFontSize, titleCol);
                    Fonts.e.a(event.h(), displayTitle, areaLeft - scrollOffset + titleW + 18.0f, titleY, titleFontSize, titleCol);
                    ScissorUtil.a(event.h());
                } else {
                    float centeredX = areaLeft + (maxTitleW - titleW) * 0.5f;
                    Fonts.e.a(event.h(), displayTitle, centeredX, titleY, titleFontSize, titleCol);
                }
            }

            // Animated Equalizer Waveform Bars on far right
            int themePrimary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
            int waveBase = ColorUtil.a(235, 195, 150, 255); // Apple warm gold
            int waveCol = ColorUtil.a(ColorUtil.a(waveBase, themePrimary, 0.35f), (int) (245 * collapsedAlpha));

            float timeSec = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);
            float[] speeds = new float[] { 3.1f, 4.4f, 2.7f, 3.9f, 2.3f };
            float[] phases = new float[] { 0.2f, 1.5f, 2.8f, 0.9f, 2.1f };
            float[] baseWeights = new float[] { 0.45f, 0.70f, 1.00f, 0.80f, 0.55f };

            for (int b = 0; b < 5; b++) {
                float bx = barsStartX + b * (barW + barGap);
                float barH;
                if (isPlaying && hasActiveMedia) {
                    float osc = (float) Math.abs(Math.sin(timeSec * speeds[b] * 2.2f + phases[b]) * 0.7f + Math.sin(timeSec * 1.5f + b) * 0.3f);
                    barH = 2.4f + osc * 7.5f * baseWeights[b];
                } else {
                    barH = 2.4f; // resting dot
                }
                draw.a(event.h(), bx, barsCenterY - barH * 0.5f, barW, barH, barW * 0.5f, waveCol);
            }
        }

        // 4. Expanded Mode: Full Sound HUD UI Elements
        float expAlpha = expand * anim;
        if (expAlpha > 0.02f) {
            // Play / Pause icon on the enlarged cover art
            float cx = artX + artSize * 0.5f;
            float cy = artY + artSize * 0.5f;
            draw.a(event.h(), artX, artY, artSize, artSize, artRadius, ColorUtil.a(0, 0, 0, (int) (65 * expAlpha)));

            if (isPlaying) {
                float pillW = 2.4f;
                float pillH = 10.0f;
                float pillGap = 2.6f;
                float p1X = cx - pillW - pillGap * 0.5f;
                float p2X = cx + pillGap * 0.5f;
                float pY = cy - pillH * 0.5f;
                int shadow = ColorUtil.a(0, 0, 0, (int) (120 * expAlpha));
                int barCol = ColorUtil.a(255, 255, 255, (int) (255 * expAlpha));
                draw.a(event.h(), p1X + 0.5f, pY + 0.5f, pillW, pillH, 1.2f, shadow);
                draw.a(event.h(), p2X + 0.5f, pY + 0.5f, pillW, pillH, 1.2f, shadow);
                draw.a(event.h(), p1X, pY, pillW, pillH, 1.2f, barCol);
                draw.a(event.h(), p2X, pY, pillW, pillH, 1.2f, barCol);
            } else {
                int triangleCol = ColorUtil.a(255, 255, 255, (int) (255 * expAlpha));
                drawRightTriangle(draw, event.h(), cx - 3.2f, cy, 7.5f, 10.0f, triangleCol);
            }

            // Track Title & Artist
            float textX = artX + artSize + 8.5f;
            float textMaxW = curW - (textX - x) - 20.0f;

            String displayTitle = hasActiveMedia ? currentTitle : "Ничего не играет";
            String displayArtist = hasActiveMedia ? (!currentArtist.isEmpty() ? currentArtist : currentSource) : "Ожидание трека...";

            int titleColor = ColorUtil.a(255, 255, 255, (int) (255 * expAlpha));
            int subColor = ColorUtil.a(155, 160, 175, (int) (200 * expAlpha));

            float titleW = Fonts.e.a(displayTitle, 8.5f);
            if (titleW > textMaxW) {
                long now = System.currentTimeMillis();
                if (lastScrollTime == 0L) lastScrollTime = now;
                float dt = (now - lastScrollTime) / 1000.0f;
                lastScrollTime = now;
                scrollOffset = (scrollOffset + dt * 18.0f) % (titleW + 20.0f);

                ScissorUtil.a(event.h(), textX, y + 6.0f, textMaxW, 11.0f);
                Fonts.e.a(event.h(), displayTitle, textX - scrollOffset, y + 7.5f, 8.5f, titleColor);
                Fonts.e.a(event.h(), displayTitle, textX - scrollOffset + titleW + 20.0f, y + 7.5f, 8.5f, titleColor);
                ScissorUtil.a(event.h());
            } else {
                Fonts.e.a(event.h(), displayTitle, textX, y + 7.5f, 8.5f, titleColor);
            }

            float artistW = Fonts.d.a(displayArtist, 7.0f);
            if (artistW > textMaxW) {
                ScissorUtil.a(event.h(), textX, y + 18.0f, textMaxW, 9.0f);
                Fonts.d.a(event.h(), displayArtist, textX, y + 19.0f, 7.0f, subColor);
                ScissorUtil.a(event.h());
            } else {
                Fonts.d.a(event.h(), displayArtist, textX, y + 19.0f, 7.0f, subColor);
            }

            // Minimalist Collapse Chevron at top right
            float closeX = x + curW - 14.0f;
            float closeY = y + 7.5f;
            this.lastCloseX = closeX;
            this.lastCloseY = closeY;
            double mx = aM_.mouse.getX() / 2.0;
            double my = aM_.mouse.getY() / 2.0;
            boolean hoverClose = MathUtil.a(mx, my, closeX - 3.0f, closeY - 3.0f, 12.0f, 12.0f);
            int closeCol = hoverClose ? ColorUtil.a(255, 255, 255, (int) (255 * expAlpha)) : ColorUtil.a(160, 165, 180, (int) (180 * expAlpha));
            drawChevronUp(draw, event.h(), closeX + 3.0f, closeY + 3.0f, closeCol);

            // Bottom Row: Current Time | Waveform Progress | Remaining Time | Prev | Next
            float rowY = y + 37.0f;
            this.lastRowY = rowY;

            float progress = 0.0f;
            String curTimeStr;
            String remTimeStr;
            if (hasActiveMedia) {
                if (currentDurationMs > 0L) {
                    progress = MathHelper.clamp((float) currentPositionMs / (float) currentDurationMs, 0.0f, 1.0f);
                }
                curTimeStr = formatTime(currentPositionMs);
                remTimeStr = formatRemainingTime(currentPositionMs, currentDurationMs);
            } else {
                progress = 0.38f;
                curTimeStr = "0:38";
                remTimeStr = "-1:40";
            }

            float curTimeW = Fonts.d.a(curTimeStr, 6.5f);
            Fonts.d.a(event.h(), curTimeStr, textX, rowY + 1.5f, 6.5f, ColorUtil.a(175, 180, 195, (int) (210 * expAlpha)));

            // Controls anchored from the right edge with comfortable 13px padding
            float rightEdge = x + curW - 13.0f;
            float btnNextX = rightEdge - 7.5f;
            float btnPrevX = btnNextX - 11.5f;
            this.lastBtnPrevX = btnPrevX;
            this.lastBtnNextX = btnNextX;

            // Remaining Time placed before skip buttons
            float remW = Fonts.d.a(remTimeStr, 6.5f);
            float remX = btnPrevX - 6.0f - remW;
            Fonts.d.a(event.h(), remTimeStr, remX, rowY + 1.5f, 6.5f, ColorUtil.a(175, 180, 195, (int) (210 * expAlpha)));

            // Fluid Waveform Seekbar spanning between curTime and remTime
            float waveX = textX + curTimeW + 4.5f;
            float waveW = (remX - 4.5f) - waveX;
            float waveH = 9.0f;
            float waveCenterY = rowY + 4.5f;
            this.lastWaveX = waveX;
            this.lastWaveW = waveW;

            int themePrimary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
            int playedStart = ColorUtil.a(themePrimary, (int) (245 * expAlpha));
            int playedEnd = ColorUtil.a(ColorUtil.a(themePrimary, -1, 0.25f), (int) (235 * expAlpha));
            int unplayedColor = ColorUtil.a(118, 120, 134, (int) (140 * expAlpha));

            float waveTime = (float) (System.currentTimeMillis() % 1000000L) * 0.0028f;
            boolean playing = hasActiveMedia && isPlaying;
            float motionFactor = playing ? 1.0f : 0.28f;

            int totalSlices = 54;
            float sliceW = waveW / (float) totalSlices;

            for (int i = 0; i < totalSlices; i++) {
                float u = (float) i / (float) (totalSlices - 1);
                float bass = (float) Math.abs(Math.sin(waveTime * 2.2f + u * 8.5f) * 0.65f + Math.sin(waveTime * 1.1f - u * 4.0f) * 0.35f);
                float voice = (float) Math.abs(Math.sin(waveTime * 4.8f + u * 18.0f) * 0.55f + Math.cos(waveTime * 7.2f - u * 26.0f) * 0.30f + Math.sin(waveTime * 3.1f + u * 10.0f) * 0.15f);
                float combined = (float) Math.abs((bass * 0.50f + voice * 0.50f) * 1.15f + Math.sin(waveTime * 9.5f + u * 32.0f) * 0.25f);

                float wBass = MathHelper.clamp(1.0f - (u / 0.38f), 0.0f, 1.0f);
                float wComb = MathHelper.clamp((u - 0.62f) / 0.38f, 0.0f, 1.0f);
                float wVoice = Math.max(0.0f, 1.0f - wBass - wComb);

                float dynamicAmp = (wBass * bass + wVoice * voice + wComb * combined) * motionFactor;
                float organicEnvelope = 0.45f + 0.35f * (float) Math.sin(u * Math.PI) + 0.12f * (float) Math.sin(u * 2.0f * Math.PI + 0.5f);
                float hFactor = MathHelper.clamp(organicEnvelope + dynamicAmp * 0.42f, 0.25f, 1.0f);
                float sliceH = Math.max(2.8f, waveH * hFactor);

                float sx = waveX + i * sliceW;
                float sy = waveCenterY - sliceH * 0.5f;

                int col = (u <= progress) ? ColorUtil.a(playedStart, playedEnd, u) : unplayedColor;
                draw.a(event.h(), sx, sy, sliceW + 0.4f, sliceH, 0.5f, col);
            }

            // Playhead thumb dot
            float headX = waveX + waveW * progress;
            draw.a(event.h(), headX - 4.5f, waveCenterY - 4.5f, 9.0f, 9.0f, 4.5f, ColorUtil.a(themePrimary, (int) (80 * expAlpha)));
            draw.a(event.h(), headX - 2.5f, waveCenterY - 2.5f, 5.0f, 5.0f, 2.5f, ColorUtil.a(255, 255, 255, (int) (255 * expAlpha)));

            // Prev and Next Skip Controls
            boolean hoverPrev = MathUtil.a(mx, my, btnPrevX - 2.0f, rowY - 2.0f, 10.0f, 13.0f);
            boolean hoverNext = MathUtil.a(mx, my, btnNextX - 2.0f, rowY - 2.0f, 10.0f, 13.0f);

            int btnColor = ColorUtil.a(215, 220, 235, (int) (225 * expAlpha));
            int prevColor = hoverPrev ? ColorUtil.a(255, 255, 255, (int) (255 * expAlpha)) : btnColor;
            int nextColor = hoverNext ? ColorUtil.a(255, 255, 255, (int) (255 * expAlpha)) : btnColor;
            float btnCY = waveCenterY;

            drawLeftTriangle(draw, event.h(), btnPrevX, btnCY, 3.5f, 6.5f, prevColor);
            drawLeftTriangle(draw, event.h(), btnPrevX + 3.8f, btnCY, 3.5f, 6.5f, prevColor);

            drawRightTriangle(draw, event.h(), btnNextX, btnCY, 3.5f, 6.5f, nextColor);
            drawRightTriangle(draw, event.h(), btnNextX + 3.8f, btnCY, 3.5f, 6.5f, nextColor);
        }

        if (hasAbsorb) {
            event.h().pop();
        }

        super.a(event);
    }

    private void drawChevronUp(Draw2DProcessor draw, net.minecraft.client.util.math.MatrixStack matrices, float cx, float cy, int color) {
        draw.a(matrices, cx - 3.5f, cy + 1.5f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx - 2.2f, cy + 0.3f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx - 0.9f, cy - 0.9f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx, cy - 1.8f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx + 0.9f, cy - 0.9f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx + 2.2f, cy + 0.3f, 1.4f, 1.4f, 0.7f, color);
        draw.a(matrices, cx + 3.5f, cy + 1.5f, 1.4f, 1.4f, 0.7f, color);
    }

    private void drawLeftTriangle(Draw2DProcessor draw, net.minecraft.client.util.math.MatrixStack matrices, float x, float cy, float w, float h, int color) {
        int steps = 5;
        float sliceW = w / steps;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.6f) / steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + i * sliceW;
            float sy = cy - sliceH * 0.5f;
            draw.a(matrices, sx, sy, sliceW + 0.35f, sliceH, 0.35f, color);
        }
    }

    private void drawRightTriangle(Draw2DProcessor draw, net.minecraft.client.util.math.MatrixStack matrices, float x, float cy, float w, float h, int color) {
        int steps = 5;
        float sliceW = w / steps;
        for (int i = 0; i < steps; i++) {
            float t = (steps - 1 - i + 0.6f) / steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + i * sliceW;
            float sy = cy - sliceH * 0.5f;
            draw.a(matrices, sx, sy, sliceW + 0.35f, sliceH, 0.35f, color);
        }
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (event.b() && event.h() == 0) { // Mouse pressed left button
            double mx = event.f();
            double my = event.g();
            float x = j().a();
            float y = j().b();
            float w = j().f();
            float h = j().g();

            if (!MathUtil.a(mx, my, x, y, w, h)) {
                return;
            }

            if (!this.expanded) {
                // Click anywhere on compact Dynamic Island to expand!
                this.expanded = true;
                return;
            }

            // In expanded mode:
            // 1. Cover click: toggle play/pause
            if (MathUtil.a(mx, my, this.lastArtX, this.lastArtY, this.lastArtSize, this.lastArtSize)) {
                POLL_EXECUTOR.submit(() -> {
                    try {
                        OptMedia.INSTANCE.TogglePlayPause();
                    } catch (Throwable ignored) {}
                });
                return;
            }

            // 2. Prev button click
            if (this.lastBtnPrevX > 0.0f && MathUtil.a(mx, my, this.lastBtnPrevX - 2.0f, this.lastRowY - 2.0f, 10.0f, 13.0f)) {
                POLL_EXECUTOR.submit(() -> {
                    try {
                        OptMedia.INSTANCE.SkipPrevious();
                    } catch (Throwable ignored) {}
                });
                return;
            }

            // 3. Next button click
            if (this.lastBtnNextX > 0.0f && MathUtil.a(mx, my, this.lastBtnNextX - 2.0f, this.lastRowY - 2.0f, 10.0f, 13.0f)) {
                POLL_EXECUTOR.submit(() -> {
                    try {
                        OptMedia.INSTANCE.SkipNext();
                    } catch (Throwable ignored) {}
                });
                return;
            }

            // 4. Waveform seek click
            if (this.lastWaveX > 0.0f && MathUtil.a(mx, my, this.lastWaveX - 2.0f, this.lastRowY - 2.0f, this.lastWaveW + 4.0f, 13.0f)) {
                if (currentDurationMs > 0L) {
                    float pct = MathHelper.clamp((float) (mx - this.lastWaveX) / this.lastWaveW, 0.0f, 1.0f);
                    long targetMs = (long) (currentDurationMs * pct);
                    currentPositionMs = targetMs;
                    POLL_EXECUTOR.submit(() -> {
                        try {
                            OptMedia.INSTANCE.SeekToMs(targetMs);
                        } catch (Throwable ignored) {}
                    });
                }
                return;
            }

            // 5. Close / Collapse button or header background click: collapses back into Dynamic Island
            if (MathUtil.a(mx, my, this.lastCloseX - 4.0f, this.lastCloseY - 4.0f, 14.0f, 14.0f)
                    || (my < this.lastRowY - 2.0f && mx > this.lastArtX + this.lastArtSize)) {
                this.expanded = false;
            }
        }
    }

    private String formatTime(long ms) {
        if (ms <= 0L) {
            return "0:00";
        }
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10L ? "0" : "") + sec;
    }

    private String formatRemainingTime(long positionMs, long durationMs) {
        if (durationMs <= 0L) {
            return "-0:00";
        }
        long remMs = Math.max(0L, durationMs - positionMs);
        return "-" + formatTime(remMs);
    }
}
