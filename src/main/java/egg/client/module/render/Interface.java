package egg.client.module.render;

import org.joml.Matrix3x2f;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.util.math.MathHelper;

import egg.client.config.ConfigManager;
import egg.client.media.Artist;
import egg.client.media.OptMedia;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.DynamicGuiTextures;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Interface extends Module {
    private static Interface instance;

    public static Interface getInstance() {
        return instance;
    }

    private static final Identifier EGG_LOGO = Identifier.of("egg", "textures/egg_logo.png");
    private static final Identifier ICON_FPS = Identifier.of("egg", "textures/hud/fps.png");
    private static final Identifier ICON_TPS = Identifier.of("egg", "textures/hud/tps.png");
    private static final Identifier ICON_TIME = Identifier.of("egg", "textures/hud/time.png");
    private static final Identifier ICON_PING = Identifier.of("egg", "textures/hud/ping.png");
    private static final Identifier ICON_SERVER = Identifier.of("egg", "textures/hud/server.png");
    private static final Identifier ICON_USER = Identifier.of("egg", "textures/hud/user.png");

    private static final Identifier FUNTIME_ICON = Identifier.of("egg", "textures/servers/funtime.png");
    private static final Identifier REALLYWORLD_ICON = Identifier.of("egg", "textures/servers/reallyworld.png");
    private static final Identifier SPACETIME_ICON = Identifier.of("egg", "textures/servers/spacetime.png");
    private static final Identifier ALBUM_ART_ID = Identifier.of("egg", "dynamic/media_cover");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final ExecutorService MEDIA_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Egg-OptMedia-Poll");
        t.setDaemon(true);
        return t;
    });

    private final BooleanSetting showDynamicIsland = new BooleanSetting("Динамик айленд", true, "Отображать Dynamic Island при воспроизведении музыки");
    private final SliderSetting islandScale = new SliderSetting("Размер острова", 1.0, 0.5, 1.5, 0.05, "Масштаб Dynamic Island");
    private final ColorSetting dynamicIslandBgColor = new ColorSetting("Цвет фона айленда", 0xFF0D0E14);

    private final ModeSetting brand = new ModeSetting("Бренд", "Egg", "Бренд в ватермарке", "Egg", "Egg Visuals", "none");
    private final BooleanSetting showLogo = new BooleanSetting("Иконка", true, "Показывать логотип");
    private final BooleanSetting showNick = new BooleanSetting("Логин в клиенте", false, "Отображать ник игрока");
    private final BooleanSetting showServer = new BooleanSetting("Сервер", false, "Отображать сервер и иконку");
    private final BooleanSetting showFps = new BooleanSetting("Частота кадров", true, "Показывать FPS");
    private final BooleanSetting showTps = new BooleanSetting("Задержка сервера", true, "Показывать TPS");
    private final BooleanSetting showPing = new BooleanSetting("Задержка игрока", false, "Показывать Ping");
    private final BooleanSetting showTime = new BooleanSetting("Текущее время", true, "Показывать время");
    private final BooleanSetting showCoords = new BooleanSetting("Координаты", false, "Показывать координаты XYZ");
    private final BooleanSetting showSpeed = new BooleanSetting("Скорость игрока", false, "Показывать скорость перемещения");
    private final BooleanSetting sideMode = new BooleanSetting("Боковое отображение", false, "Прижимать ватермарку к левому краю");

    private final ColorSetting watermarkBgColor = new ColorSetting("Цвет фона ватермарки", 0xD614151E);
    private final ColorSetting titleColor1 = new ColorSetting("Цвет надписи 1", 0xFF8B5CF6);
    private final ColorSetting titleColor2 = new ColorSetting("Цвет надписи 2", 0xFF00CEC9);
    private final SliderSetting rounding = new SliderSetting("Скругление", 7.0, 0.0, 16.0, 0.5, "Скругление углов элементов");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 225.0, 20.0, 255.0, 5.0, "Прозрачность фона интерфейса");
    private final BooleanSetting hudThemeGradient = new BooleanSetting("Градиент HUD", false, "Мягкий градиент на фоне элементов");
    private final SliderSetting hudGradientStrength = new SliderSetting("Сила градиента", 60.0, 5.0, 100.0, 5.0, "Интенсивность градиента");

    private final SliderSetting posX = new SliderSetting("X", 0.015, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.015, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting hudScale = new SliderSetting("Размер HUD", 1.0, 0.5, 1.5, 0.05, "Масштаб элементов HUD");

    private final BooleanSetting combatMode = new BooleanSetting("ПВП режим", false, "Изменять элементы интерфейса во время боя");
    private final ModeSetting combatAction = new ModeSetting("Режим в бою", "Прозрачность", "Что делать с элементами в бою", "Прозрачность", "Размер", "Оба");
    private final ModeSetting combatElements = new ModeSetting("Элементы в бою", "Все", "Какие элементы изменять в бою", "Все", "Только ватермарка", "Ватермарка + Поушены", "Ватермарка + Остров");
    private final SliderSetting combatScale = new SliderSetting("Размер в бою", 0.70, 0.20, 1.0, 0.05, "Масштаб элементов в бою");
    private final SliderSetting combatOpacity = new SliderSetting("Прозрачность в бою", 50.0, 0.0, 100.0, 5.0, "Прозрачность элементов в бою (%)");
    private final SliderSetting combatTimeout = new SliderSetting("Время боя", 5.0, 1.0, 15.0, 0.5, "Длительность режима боя после удара (сек)");

    private final Animation combatAnim = new Animation(0.0f, 320L, Easing.EASE_OUT_CUBIC);
    private long lastCombatTime = 0L;

    private float lastIslandX = 0.0f;
    private float lastIslandY = 0.0f;
    private float lastIslandW = 185.0f;
    private float lastIslandH = 30.0f;

    private float animatedFps = 60.0f;
    private float animatedPing = 0.0f;
    private double lastPlayerX = 0.0;
    private double lastPlayerZ = 0.0;
    private float animatedSpeedBps = 0.0f;
    private float animatedWatermarkY = 8.0f;

    private final RollingNumber fpsRolling = new RollingNumber();
    private final RollingNumber tpsRolling = new RollingNumber();

    private long lastMediaPollTime = 0L;
    private final Artist artistStruct = new Artist();
    private volatile boolean hasActiveMedia = false;
    private volatile String currentMediaTitle = "";
    private volatile String currentMediaArtist = "";
    private volatile boolean isMediaPlaying = false;
    private volatile byte[] pendingCoverBytes = null;
    private int currentCoverHash = 0;
    private String lastTrackKey = "";
    private NativeImageBackedTexture coverTexture = null;
    private boolean hasCoverArt = false;
    private int dominantAccentColor = 0xFF4A7CC4;

    private boolean islandExpanded = false;
    private float islandExpandAnim = 0.0f;
    private boolean isSeeking = false;
    private long currentDurationMs = 0L;
    private long currentPositionMs = 0L;
    private long lastFrameTime = 0L;
    private float scrollOffset = 0.0f;
    private long lastScrollTime = 0L;

    private float lastArtX = 0.0f;
    private float lastArtY = 0.0f;
    private float lastArtSize = 22.0f;
    private float lastWaveX = 0.0f;
    private float lastWaveW = 0.0f;
    private float lastRowY = 0.0f;
    private float lastBtnPrevX = 0.0f;
    private float lastBtnNextX = 0.0f;
    private float lastCloseX = 0.0f;
    private float lastCloseY = 0.0f;

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private boolean contextMenuOpen = false;
    private float contextMenuX = 0.0f;
    private float contextMenuY = 0.0f;

    private float lastX = 0.0f;
    private float lastY = 0.0f;
    private float lastW = 100.0f;
    private float lastH = 28.0f;

    public Interface() {
        super("Interface", "Отображает выбранные элементы интерфейса на экране", Category.ESP);
        instance = this;
        addSettings(
                showDynamicIsland, islandScale,
                brand, showLogo, showNick, showServer, showFps, showTps, showPing, showTime, showCoords, showSpeed, sideMode,
                watermarkBgColor, titleColor1, titleColor2, rounding, opacity, hudThemeGradient, hudGradientStrength,
                hudScale,
                combatMode, combatAction, combatElements, combatScale, combatOpacity, combatTimeout
        );
        setEnabled(true);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        pollMedia();
        updateCoverTexture();

        if (mc.player != null && mc.player.hurtTime > 0) {
            onCombatHit();
        }
        boolean inCombat = isInCombat();
        combatAnim.setTarget(inCombat ? 1.0f : 0.0f);

        boolean islandActive = false;
        float islandBottom = 8.0f;
        if (showDynamicIsland.isEnabled()) {
            boolean inChat = (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen);
            if (!inChat) {
                islandExpanded = false;
            }
            float targetExpand = islandExpanded ? 1.0f : 0.0f;
            this.islandExpandAnim += (targetExpand - this.islandExpandAnim) * 0.22f;
            if (Math.abs(this.islandExpandAnim - targetExpand) < 0.001f) {
                this.islandExpandAnim = targetExpand;
            }

            if (hasActiveMedia && isMediaPlaying && currentDurationMs > 0L) {
                long nowTime = System.currentTimeMillis();
                if (lastFrameTime != 0L) {
                    long elapsed = nowTime - lastFrameTime;
                    if (!isSeeking && elapsed > 0 && elapsed < 500) {
                        currentPositionMs = Math.min(currentDurationMs, currentPositionMs + elapsed);
                    }
                }
                lastFrameTime = nowTime;
            } else {
                lastFrameTime = System.currentTimeMillis();
            }

            boolean playing = (hasActiveMedia && isMediaPlaying) || this.islandExpandAnim > 0.01f;
            if (playing) {
                islandActive = true;
                renderDynamicIsland(context, screenW);
                float curH = MathHelper.lerp(this.islandExpandAnim, 30.0f, 70.0f);
                islandBottom = 8.0f + curH + 6.0f;
            }
        }

        this.animatedFps += (mc.getCurrentFps() - this.animatedFps) * 0.15f;
        fpsRolling.update(mc.getCurrentFps());

        float targetPing = 0.0f;
        if (mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                targetPing = entry.getLatency();
            }
        }
        this.animatedPing += (targetPing - this.animatedPing) * 0.15f;

        float tpsVal = 20.0f;
        if (mc.world != null && mc.world.getTickManager() != null) {
            tpsVal = mc.world.getTickManager().getTickRate();
        }
        tpsRolling.update(Math.round(tpsVal));

        if (mc.player != null) {
            double dx = mc.player.getX() - lastPlayerX;
            double dz = mc.player.getZ() - lastPlayerZ;
            lastPlayerX = mc.player.getX();
            lastPlayerZ = mc.player.getZ();
            float bps = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0);
            this.animatedSpeedBps += (bps - this.animatedSpeedBps) * 0.2f;
        }

        boolean hasLogo = showLogo.isEnabled();
        boolean hasBrand = !"none".equalsIgnoreCase(brand.get());
        String brandStr = brand.get();
        if (brandStr.equalsIgnoreCase("egg client") || brandStr.equalsIgnoreCase("Egg")) {
            brandStr = "Egg";
        }

        List<WatermarkItem> items = collectItems(mc);
        if (!hasBrand && !hasLogo && items.isEmpty()) return;

        float baseScale = hudScale.getValueFloat();
        float scale = baseScale * getCombatScaleFactor("watermark");

        float radius = rounding.getValueFloat();
        float baseOpacityVal = opacity.getValueFloat() / 255.0f;
        float alphaFactor = getCombatAlphaFactor("watermark");
        float opacityVal = baseOpacityVal * alphaFactor;

        float pillHeight = 28.0f;
        float totalW = calculateWatermarkWidth(hasLogo, hasBrand, brandStr, items);

        float baseX;
        if (sideMode.isEnabled()) {
            baseX = 8.0f;
        } else {
            float pX = posX.getValueFloat();

            if (Math.abs(pX - 0.5f) < 0.02f) {
                baseX = (float) Math.round((screenW - totalW) / 2.0f);
            } else {
                baseX = Math.clamp(pX * (screenW - totalW), 4.0f, screenW - totalW - 4.0f);
            }
        }

        if (dragging && Math.abs(baseX - (screenW - totalW) / 2.0f) < 14.0f) {
            baseX = (float) Math.round((screenW - totalW) / 2.0f);
        }

        float curIslandW = MathHelper.lerp(this.islandExpandAnim, 185.0f, 242.0f);
        float islandX = (screenW - curIslandW) / 2.0f;
        boolean horizontalOverlap = (baseX < islandX + curIslandW + 4.0f) && (baseX + totalW > islandX - 4.0f);

        float userTargetY = Math.clamp(posY.getValueFloat() * (screenH - pillHeight), 4.0f, screenH - pillHeight - 4.0f);
        float targetBaseY = (islandActive && horizontalOverlap && userTargetY < islandBottom) ? islandBottom : userTargetY;

        if (Math.abs(this.animatedWatermarkY - targetBaseY) > 0.1f) {
            this.animatedWatermarkY += (targetBaseY - this.animatedWatermarkY) * 0.25f;
        } else {
            this.animatedWatermarkY = targetBaseY;
        }
        float baseY = this.animatedWatermarkY;

        boolean customScale = Math.abs(scale - 1.0f) > 0.001f;

        Matrix3x2f mat = null;
        if (customScale) {
            mat = new Matrix3x2f();
            mat.translate(baseX, baseY);
            mat.scale(scale, scale);
            mat.translate(-baseX, -baseY);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(baseX, baseY);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-baseX, -baseY);
        }

        this.lastX = baseX;
        this.lastY = baseY;
        this.lastW = totalW * scale;
        this.lastH = pillHeight * scale;

        int baseBg = watermarkBgColor.get();
        int finalBg = ColorUtil.rgba(ColorUtil.red(baseBg), ColorUtil.green(baseBg), ColorUtil.blue(baseBg), (int) (opacityVal * 255.0f));
        int borderColor = ColorUtil.rgba(255, 255, 255, (int) (22 * alphaFactor));

        Draw2DProcessor.INSTANCE.drawGlow(mat, baseX, baseY, totalW, pillHeight, radius, ColorUtil.rgba(0, 0, 0, (int) (opacityVal * 65.0f)), 6.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, baseX, baseY, totalW, pillHeight, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(mat, baseX, baseY, totalW, pillHeight, radius, 0.8f, borderColor);

        if (hudThemeGradient.isEnabled()) {
            float str = hudGradientStrength.getValueFloat() / 100.0f;
            int gradC = ColorUtil.rgba(168, 85, 247, (int) (str * 45.0f * alphaFactor));
            Draw2DProcessor.INSTANCE.drawGradient(mat, baseX, baseY, totalW, pillHeight, radius, gradC, 0x00000000, 0x00000000, gradC);
        }

        DrawBatcher.flushPending();
        if (alphaFactor > 0.15f) {
            Render2D.drawDiagonalSheen(context, baseX, baseY, totalW, pillHeight, radius, 0L);
        }

        float cursor = baseX + 7.0f;
        float centerY = baseY + pillHeight / 2.0f;

        int tintWithAlpha = ColorUtil.rgba(255, 255, 255, (int) (255 * alphaFactor));

        if (hasLogo) {
            float badgeSize = 24.0f;
            float badgeX = cursor;
            float badgeY = centerY - badgeSize / 2.0f;
            float badgeR = badgeSize / 2.0f;

            Draw2DProcessor.INSTANCE.drawRect(mat, badgeX, badgeY, badgeSize, badgeSize, badgeR, ColorUtil.rgba(10, 11, 15, (int) (250 * alphaFactor)));
            Draw2DProcessor.INSTANCE.drawOutline(mat, badgeX, badgeY, badgeSize, badgeSize, badgeR, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (28 * alphaFactor)));

            IconRenderer.drawTextureScaled(context, EGG_LOGO, badgeX + 2.0f, badgeY + 2.0f, 20.0f, 20.0f, 128, 128, tintWithAlpha);
            cursor += badgeSize + 8.0f;

            if (hasBrand || !items.isEmpty()) {
                drawDivider(mat, cursor - 4.0f, baseY, pillHeight, alphaFactor);
            }
        }

        if (hasBrand && brandStr != null && !brandStr.isEmpty()) {
            float brandFontY = centerY - Fonts.e.a(13.0f) / 2.0f - 0.5f;
            drawBrandGradient(mat, brandStr, cursor, brandFontY, 13.0f, alphaFactor);
            cursor += Fonts.e.a(brandStr, 13.0f) + 8.0f;

            if (!items.isEmpty()) {
                drawDivider(mat, cursor - 4.0f, baseY, pillHeight, alphaFactor);
            }
        }

        float valFontSize = 10.8f;
        float capFontSize = 7.2f;

        int iconColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alphaFactor));
        int valColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alphaFactor));
        int capColor = ColorUtil.rgba(215, 218, 230, (int) (255 * alphaFactor));

        for (int i = 0; i < items.size(); i++) {
            WatermarkItem item = items.get(i);

            if (item.iconGlyph != null) {
                float glyphSize = 10.5f;
                float glyphY = centerY - Fonts.hud.a(glyphSize) / 2.0f;
                Fonts.hud.a(mat, item.iconGlyph, cursor, glyphY, glyphSize, iconColor);
                cursor += Fonts.hud.a(item.iconGlyph, glyphSize) + 5.5f;
            } else if (item.texture != null) {
                float iconSize = 12.0f;
                float iconY = centerY - iconSize / 2.0f;
                IconRenderer.drawTextureScaled(context, item.texture, cursor, iconY, iconSize, iconSize, 32, 32, tintWithAlpha);
                cursor += iconSize + 5.5f;
            }

            if (item.subText != null && !item.subText.isEmpty()) {
                float valY = baseY + 4.0f;
                float capY = baseY + 15.0f;

                if (item.rolling != null) {
                    item.rolling.render(mat, cursor, valY, valFontSize, valColor, alphaFactor);
                } else {
                    Fonts.d.a(mat, item.valText, cursor, valY, valFontSize, valColor);
                }
                Fonts.d.a(mat, item.subText, cursor, capY, capFontSize, capColor);

                float colW = Math.max(Fonts.d.a(item.valText, valFontSize), Fonts.b.a(item.subText, capFontSize));
                cursor += colW + 8.5f;
            } else {

                float textH = Fonts.d.a(valFontSize);
                float valY = centerY - textH / 2.0f;
                Fonts.d.a(mat, item.valText, cursor, valY, valFontSize, valColor);

                float colW = Fonts.d.a(item.valText, valFontSize);
                cursor += colW + 8.5f;
            }

            if (i < items.size() - 1) {
                drawDivider(mat, cursor - 4.5f, baseY, pillHeight, alphaFactor);
            }
        }

        DrawBatcher.flushPending();
        if (customScale) {
            context.getMatrices().popMatrix();
        }
    }

    private float calculateWatermarkWidth(boolean hasLogo, boolean hasBrand, String brandStr, List<WatermarkItem> items) {
        float curW = 14.0f;
        if (hasLogo) {
            curW += 22.0f + 8.0f;
        }
        if (hasBrand && brandStr != null && !brandStr.isEmpty()) {
            curW += Fonts.e.a(brandStr, 13.0f) + 8.0f;
        }
        float valFontSize = 10.8f;
        float capFontSize = 7.2f;
        for (int i = 0; i < items.size(); i++) {
            WatermarkItem item = items.get(i);
            if (item.iconGlyph != null) {
                curW += Fonts.hud.a(item.iconGlyph, 10.5f) + 5.5f;
            } else if (item.texture != null) {
                curW += 12.0f + 5.5f;
            }
            float colW = (item.subText != null && !item.subText.isEmpty())
                    ? Math.max(Fonts.d.a(item.valText, valFontSize), Fonts.b.a(item.subText, capFontSize))
                    : Fonts.d.a(item.valText, valFontSize);
            curW += colW + 8.5f;
        }
        return Math.max(curW + 4.0f, 60.0f);
    }

    private void drawDivider(Matrix3x2f mat, float cx, float baseY, float pillH, float alphaFactor) {
        float lineH = 15.0f;
        float lineY = baseY + (pillH - lineH) / 2.0f;
        Draw2DProcessor.INSTANCE.drawRect(mat, cx, lineY, 1.0f, lineH, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (24 * alphaFactor)));
    }

    private void renderDynamicIsland(DrawContext context, int screenW) {
        float collapsedW = 185.0f;
        float collapsedH = 30.0f;
        float expandedW = 242.0f;
        float expandedH = 70.0f;

        float curW = MathHelper.lerp(this.islandExpandAnim, collapsedW, expandedW);
        float curH = MathHelper.lerp(this.islandExpandAnim, collapsedH, expandedH);
        float radius = MathHelper.lerp(this.islandExpandAnim, 15.0f, 16.0f);

        float baseIslandS = islandScale.getValueFloat();
        float combatS = getCombatScaleFactor("island");
        float islandS = baseIslandS * combatS;
        boolean customIslandScale = Math.abs(islandS - 1.0f) > 0.001f;

        float alphaFactor = getCombatAlphaFactor("island");

        float x = (screenW - curW) / 2.0f;
        float y = 8.0f;
        float centerX = x + curW / 2.0f;

        this.lastIslandX = (screenW - curW * islandS) / 2.0f;
        this.lastIslandY = y;
        this.lastIslandW = curW * islandS;
        this.lastIslandH = curH * islandS;

        Matrix3x2f mat = null;
        if (customIslandScale) {
            mat = new Matrix3x2f();
            mat.translate(centerX, y);
            mat.scale(islandS, islandS);
            mat.translate(-centerX, -y);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(centerX, y);
            context.getMatrices().scale(islandS, islandS);
            context.getMatrices().translate(-centerX, -y);
        }

        int baseIslandBg = ColorUtil.withAlphaFloat(0xFF141418, alphaFactor);
        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, curW, curH, radius, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 160), alphaFactor), 12.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, curW, curH, radius, baseIslandBg);

        int accent = (dominantAccentColor & 0xFFFFFF) != 0 ? dominantAccentColor : 0xFF30D158;
        float glowA = (0.25f + 0.10f * (float) Math.sin(System.currentTimeMillis() * 0.002)) * alphaFactor;
        int accentGrad = ColorUtil.withAlphaFloat(ColorUtil.rgba(ColorUtil.red(accent), ColorUtil.green(accent), ColorUtil.blue(accent), (int) (110 * (0.3f + 0.7f * this.islandExpandAnim))), glowA);
        Draw2DProcessor.INSTANCE.drawGradient(mat, x, y, curW, curH, radius, 0x00000000, 0x00000000, 0x00000000, accentGrad);

        Draw2DProcessor.INSTANCE.drawOutline(mat, x, y, curW, curH, radius, 0.9f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 28), alphaFactor));
        DrawBatcher.flushPending();
        if (alphaFactor > 0.15f) {
            Render2D.drawDiagonalSheen(context, x, y, curW, curH, radius, 2500L);
        }

        float collapsedArtSize = 22.0f;
        float expandedArtSize = 46.0f;
        float artSize = MathHelper.lerp(this.islandExpandAnim, collapsedArtSize, expandedArtSize);
        float artX = MathHelper.lerp(this.islandExpandAnim, x + 6.0f, x + 10.0f);
        float artY = MathHelper.lerp(this.islandExpandAnim, y + (curH - artSize) / 2.0f, y + 11.0f);
        float artRadius = MathHelper.lerp(this.islandExpandAnim, 5.0f, 8.0f);

        this.lastArtX = artX;
        this.lastArtY = artY;
        this.lastArtSize = artSize;

        if (hasCoverArt) {
            Draw2DProcessor.INSTANCE.a(mat, ALBUM_ART_ID, artX, artY, artSize, artSize, artRadius, ColorUtil.withAlphaFloat(0xFFFFFFFF, alphaFactor));
            Draw2DProcessor.INSTANCE.drawOutline(mat, artX, artY, artSize, artSize, artRadius, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 30), alphaFactor));
        } else {
            Draw2DProcessor.INSTANCE.drawRect(mat, artX, artY, artSize, artSize, artRadius, ColorUtil.withAlphaFloat(ColorUtil.rgba(24, 25, 34, 250), alphaFactor));
            Draw2DProcessor.INSTANCE.drawOutline(mat, artX, artY, artSize, artSize, artRadius, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 22), alphaFactor));
            Identifier noteIcon = DynamicGuiTextures.getMusicNoteIcon();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, noteIcon, (int) (artX + (artSize - 12.0f) / 2.0f), (int) (artY + (artSize - 12.0f) / 2.0f), 0, 0, 12, 12, 12, 12, ColorUtil.withAlphaFloat(0xFFE0E0E0, alphaFactor));
        }

        if (this.islandExpandAnim > 0.05f) {
            float expA = this.islandExpandAnim * alphaFactor;
            float cx = artX + artSize * 0.5f;
            float cy = artY + artSize * 0.5f;
            float btnR = 11.0f;

            Draw2DProcessor.INSTANCE.drawRect(mat, cx - btnR, cy - btnR, btnR * 2.0f, btnR * 2.0f, btnR, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 150), expA));
            Draw2DProcessor.INSTANCE.drawOutline(mat, cx - btnR, cy - btnR, btnR * 2.0f, btnR * 2.0f, btnR, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 60), expA));

            int iconCol = ColorUtil.withAlphaFloat(0xFFFFFFFF, expA);
            if (isMediaPlaying) {

                float pW = 2.4f;
                float pH = 9.0f;
                float gap = 2.6f;
                Draw2DProcessor.INSTANCE.drawRect(mat, cx - pW - gap * 0.5f, cy - pH * 0.5f, pW, pH, 1.0f, iconCol);
                Draw2DProcessor.INSTANCE.drawRect(mat, cx + gap * 0.5f, cy - pH * 0.5f, pW, pH, 1.0f, iconCol);
            } else {

                drawPlayIconVector(mat, cx + 0.6f, cy, 8.5f, iconCol);
            }
        }

        String displayTitle = hasActiveMedia ? currentMediaTitle : "Unknown Track";
        String displayArtist = hasActiveMedia ? currentMediaArtist : "Unknown Artist";

        float collapsedAlpha = (1.0f - this.islandExpandAnim) * alphaFactor;
        if (collapsedAlpha > 0.05f) {
            float sepX = artX + artSize + 6.0f;
            drawDivider(mat, sepX, y, curH, collapsedAlpha);

            float barW = 2.0f;
            float barGap = 1.8f;
            int barCount = 5;
            float totalBarsW = barCount * barW + (barCount - 1) * barGap;
            float barsStartX = x + curW - 10.0f - totalBarsW;
            float barsCenterY = y + curH / 2.0f;

            long now = System.currentTimeMillis();
            float timeSec = (float) (now % 1000000L) / 1000.0f;

            for (int b = 0; b < barCount; b++) {
                float phase = b * 1.25f;
                float wave = (float) Math.sin(timeSec * 7.5f + phase);
                float bh = 4.0f + 8.5f * (0.5f + 0.5f * wave);
                float bx = barsStartX + b * (barW + barGap);
                float by = barsCenterY - bh / 2.0f;
                int barBaseColor = (hasCoverArt && (dominantAccentColor & 0xFFFFFF) != 0) ? dominantAccentColor : 0xFF4A7CC4;
                int barColor = ColorUtil.lerp(barBaseColor, 0xFFFFFFFF, (float) b / 7.0f);
                Draw2DProcessor.INSTANCE.drawRect(mat, bx, by, barW, bh, 1.0f, ColorUtil.withAlphaFloat(barColor, collapsedAlpha));
            }

            float textStartX = sepX + 7.0f;
            float maxTextW = barsStartX - 6.0f - textStartX;
            float titleY = y + 3.2f;
            float artistY = y + 15.6f;

            float tw = Fonts.d.a(displayTitle, 11.5f);
            if (tw <= maxTextW) {
                Fonts.d.a(mat, displayTitle, textStartX, titleY, 11.5f, ColorUtil.withAlphaFloat(0xFFFFFFFF, collapsedAlpha));
            } else {
                Fonts.d.c(mat, displayTitle, textStartX, titleY, 11.5f, ColorUtil.withAlphaFloat(0xFFFFFFFF, collapsedAlpha), maxTextW);
            }

            float aw = Fonts.d.a(displayArtist, 8.5f);
            if (aw <= maxTextW) {
                Fonts.d.a(mat, displayArtist, textStartX, artistY, 8.5f, ColorUtil.withAlphaFloat(0xFFE2E4EE, collapsedAlpha));
            } else {
                Fonts.d.c(mat, displayArtist, textStartX, artistY, 8.5f, ColorUtil.withAlphaFloat(0xFFE2E4EE, collapsedAlpha), maxTextW);
            }
        }

        float expandedAlpha = this.islandExpandAnim * alphaFactor;
        if (expandedAlpha > 0.05f) {

            float closeX = x + curW - 16.0f;
            float closeY = y + 10.0f;
            this.lastCloseX = closeX;
            this.lastCloseY = closeY;
            int closeCol = ColorUtil.withAlphaFloat(0xFF8E8E93, expandedAlpha);
            drawChevronUpVector(mat, closeX + 2.0f, closeY + 2.0f, closeCol);

            float textStartX = artX + artSize + 10.0f;
            float textMaxW = (closeX - 8.0f) - textStartX;
            float titleY = y + 10.5f;
            float artistY = y + 25.5f;

            Fonts.d.c(mat, displayTitle, textStartX, titleY, 11.5f, ColorUtil.withAlphaFloat(0xFFFFFFFF, expandedAlpha), textMaxW);
            Fonts.d.c(mat, displayArtist, textStartX, artistY, 9.0f, ColorUtil.withAlphaFloat(0xFF8E8E93, expandedAlpha), textMaxW);

            float rowY = y + 47.0f;
            this.lastRowY = rowY;

            long duration = currentDurationMs;
            long position = currentPositionMs;
            float progress = 0.0f;
            String curTimeStr;
            String remTimeStr;

            if (duration > 0L) {
                progress = MathHelper.clamp((float) position / (float) duration, 0.0f, 1.0f);
                curTimeStr = formatTime(position);
                remTimeStr = formatRemainingTime(position, duration);
            } else {
                curTimeStr = "0:00";
                remTimeStr = "-0:00";
            }

            int timerColor = ColorUtil.withAlphaFloat(Theme.getAccent(), expandedAlpha);
            float curTimeW = Fonts.b.a(curTimeStr, 8.5f);
            Fonts.b.a(mat, curTimeStr, textStartX, rowY + 1.0f, 8.5f, timerColor);

            float rightEdge = x + curW - 12.0f;
            float btnNextX = rightEdge - 8.0f;
            float btnPrevX = btnNextX - 16.0f;
            this.lastBtnPrevX = btnPrevX;
            this.lastBtnNextX = btnNextX;

            float remW = Fonts.b.a(remTimeStr, 8.5f);
            float remX = btnPrevX - 8.0f - remW;
            Fonts.b.a(mat, remTimeStr, remX, rowY + 1.0f, 8.5f, timerColor);

            float waveX = textStartX + curTimeW + 6.0f;
            float waveW = (remX - 6.0f) - waveX;
            float waveH = 9.0f;
            float waveCenterY = rowY + 5.0f;
            this.lastWaveX = waveX;
            this.lastWaveW = waveW;

            int playedStart = ColorUtil.withAlphaFloat(0xFF30D158, expandedAlpha);
            int playedEnd = ColorUtil.withAlphaFloat(ColorUtil.lerp(0xFF30D158, Theme.getAccent(), 0.35f), expandedAlpha);
            int unplayedColor = ColorUtil.withAlphaFloat(0xFF44444A, expandedAlpha * 0.75f);

            float waveTime = (float) (System.currentTimeMillis() % 1000000L) * 0.0028f;
            float motionFactor = isMediaPlaying ? 1.0f : 0.25f;

            int totalSlices = 32;
            float sliceW = waveW / (float) totalSlices;

            for (int i = 0; i < totalSlices; i++) {
                float u = (float) i / (float) (totalSlices - 1);
                float bass = (float) Math.abs(Math.sin(waveTime * 2.2f + u * 8.5f) * 0.65f + Math.sin(waveTime * 1.1f - u * 4.0f) * 0.35f);
                float voice = (float) Math.abs(Math.sin(waveTime * 4.8f + u * 18.0f) * 0.55f + Math.cos(waveTime * 7.2f - u * 26.0f) * 0.30f);
                float combined = (float) Math.abs((bass * 0.50f + voice * 0.50f) * 1.15f + Math.sin(waveTime * 9.5f + u * 32.0f) * 0.25f);

                float wBass = MathHelper.clamp(1.0f - (u / 0.38f), 0.0f, 1.0f);
                float wComb = MathHelper.clamp((u - 0.62f) / 0.38f, 0.0f, 1.0f);
                float wVoice = Math.max(0.0f, 1.0f - wBass - wComb);

                float dynamicAmp = (wBass * bass + wVoice * voice + wComb * combined) * motionFactor;
                float organicEnvelope = 0.45f + 0.35f * (float) Math.sin(u * Math.PI) + 0.12f * (float) Math.sin(u * 2.0f * Math.PI + 0.5f);
                float hFactor = MathHelper.clamp(organicEnvelope + dynamicAmp * 0.42f, 0.30f, 1.0f);
                float sliceH = Math.max(2.5f, waveH * hFactor);

                float sx = waveX + i * sliceW;
                float sy = waveCenterY - sliceH * 0.5f;

                int col = (u <= progress) ? ColorUtil.lerp(playedStart, playedEnd, u) : unplayedColor;
                Draw2DProcessor.INSTANCE.drawRect(mat, sx, sy, Math.max(1.2f, sliceW - 0.8f), sliceH, 0.8f, col);
            }

            float headX = waveX + waveW * progress;
            Draw2DProcessor.INSTANCE.drawRect(mat, headX - 4.0f, waveCenterY - 4.0f, 8.0f, 8.0f, 4.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), expandedAlpha * 0.5f));
            Draw2DProcessor.INSTANCE.drawRect(mat, headX - 2.0f, waveCenterY - 2.0f, 4.0f, 4.0f, 2.0f, ColorUtil.withAlphaFloat(0xFFFFFFFF, expandedAlpha));

            int btnColor = ColorUtil.withAlphaFloat(0xFFB0B0B8, expandedAlpha);
            drawSkipPrevVector(mat, btnPrevX, waveCenterY, btnColor);
            drawSkipNextVector(mat, btnNextX, waveCenterY, btnColor);
        }

        DrawBatcher.flushPending();
        if (customIslandScale) {
            context.getMatrices().popMatrix();
        }
    }

    private void drawPlayIconVector(Matrix3x2f mat, float cx, float cy, float size, int color) {
        float half = size * 0.5f;
        int steps = (int) size;
        float sliceW = size / Math.max(1, steps);
        float startX = cx - half * 0.6f;
        for (int i = 0; i < steps; i++) {
            float t = (steps - 1 - i + 0.6f) / (float) steps;
            float sliceH = Math.max(1.2f, size * t);
            float sx = startX + i * sliceW;
            float sy = cy - sliceH * 0.5f;
            Draw2DProcessor.INSTANCE.drawRect(mat, sx, sy, sliceW + 0.3f, sliceH, 0.4f, color);
        }
    }

    private void drawSkipPrevVector(Matrix3x2f mat, float x, float cy, int color) {
        float barW = 1.4f;
        float h = 8.0f;
        Draw2DProcessor.INSTANCE.drawRect(mat, x, cy - h * 0.5f, barW, h, 0.5f, color);
        drawLeftTriangleVector(mat, x + barW + 1.2f, cy, 3.8f, h, color);
        drawLeftTriangleVector(mat, x + barW + 5.2f, cy, 3.8f, h, color);
    }

    private void drawSkipNextVector(Matrix3x2f mat, float x, float cy, int color) {
        float h = 8.0f;
        drawRightTriangleVector(mat, x, cy, 3.8f, h, color);
        drawRightTriangleVector(mat, x + 4.0f, cy, 3.8f, h, color);
        float barW = 1.4f;
        Draw2DProcessor.INSTANCE.drawRect(mat, x + 8.2f, cy - h * 0.5f, barW, h, 0.5f, color);
    }

    private void drawLeftTriangleVector(Matrix3x2f mat, float x, float cy, float w, float h, int color) {
        int steps = 4;
        float sliceW = w / (float) steps;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.6f) / (float) steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + i * sliceW;
            float sy = cy - sliceH * 0.5f;
            Draw2DProcessor.INSTANCE.drawRect(mat, sx, sy, sliceW + 0.3f, sliceH, 0.3f, color);
        }
    }

    private void drawRightTriangleVector(Matrix3x2f mat, float x, float cy, float w, float h, int color) {
        int steps = 4;
        float sliceW = w / (float) steps;
        for (int i = 0; i < steps; i++) {
            float t = (steps - 1 - i + 0.6f) / (float) steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + i * sliceW;
            float sy = cy - sliceH * 0.5f;
            Draw2DProcessor.INSTANCE.drawRect(mat, sx, sy, sliceW + 0.3f, sliceH, 0.3f, color);
        }
    }

    private void drawChevronUpVector(Matrix3x2f mat, float cx, float cy, int color) {
        Draw2DProcessor.INSTANCE.drawRect(mat, cx - 3.5f, cy + 1.5f, 1.5f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx - 2.2f, cy + 0.3f, 1.4f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx - 0.9f, cy - 0.9f, 1.4f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx, cy - 1.8f, 1.4f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx + 0.9f, cy - 0.9f, 1.4f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx + 2.2f, cy + 0.3f, 1.4f, 1.3f, 0.3f, color);
        Draw2DProcessor.INSTANCE.drawRect(mat, cx + 3.5f, cy + 1.5f, 1.4f, 1.3f, 0.3f, color);
    }

    private String formatTime(long ms) {
        if (ms <= 0L) return "0:00";
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10L ? "0" : "") + sec;
    }

    private String formatRemainingTime(long positionMs, long durationMs) {
        if (durationMs <= 0L) return "-0:00";
        long remMs = Math.max(0L, durationMs - positionMs);
        return "-" + formatTime(remMs);
    }

    private boolean isInside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void seekWave(double mouseX) {
        if (lastWaveW <= 1.0f) return;
        float pct = MathHelper.clamp((float) (mouseX - lastWaveX) / lastWaveW, 0.0f, 1.0f);
        if (currentDurationMs > 0L) {
            long targetMs = (long) (currentDurationMs * pct);
            currentPositionMs = targetMs;
            if (OptMedia.init() && OptMedia.INSTANCE != null) {
                try {
                    OptMedia.INSTANCE.SeekToMs(targetMs);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void pollMedia() {
        long now = System.currentTimeMillis();
        if (now - lastMediaPollTime < 400L) return;
        lastMediaPollTime = now;

        MEDIA_EXECUTOR.submit(() -> {
            try {
                if (OptMedia.init() && OptMedia.INSTANCE != null) {
                    boolean ok = OptMedia.INSTANCE.GetCurrentMediaInfo(artistStruct);
                    if (ok && artistStruct.getTitle() != null && !artistStruct.getTitle().trim().isEmpty()) {
                        hasActiveMedia = true;
                        currentMediaTitle = artistStruct.getTitle().trim();
                        currentMediaArtist = artistStruct.getArtist().trim();
                        isMediaPlaying = artistStruct.isPlaying();
                        currentDurationMs = Math.max(0L, artistStruct.durationMs);
                        if (!isSeeking) {
                            currentPositionMs = Math.max(0L, artistStruct.positionMs);
                        }

                        String trackKey = currentMediaTitle + "|" + currentMediaArtist;
                        boolean trackChanged = !trackKey.equals(lastTrackKey);
                        if (trackChanged) {
                            lastTrackKey = trackKey;
                        }

                        byte[] art = artistStruct.getAlbumArt();
                        if (art != null && art.length > 0) {
                            pendingCoverBytes = art;
                        } else if (trackChanged) {
                            pendingCoverBytes = null;
                            hasCoverArt = false;
                        }

                        try {
                            OptMedia.INSTANCE.FreeMediaInfo(artistStruct);
                            artistStruct.albumArtPng = null;
                            artistStruct.albumArtSize = 0;
                        } catch (Throwable ignored) {}
                        return;
                    }
                }
            } catch (Throwable ignored) {}

            MediaTracker mt = MediaTracker.getInstance();
            if (mt != null && mt.isPlaying()) {
                hasActiveMedia = true;
                currentMediaTitle = mt.getTrackTitle();
                currentMediaArtist = mt.getTrackArtist();
                isMediaPlaying = true;
            } else {
                hasActiveMedia = false;
                isMediaPlaying = false;
            }
        });
    }

    private void updateCoverTexture() {
        if (pendingCoverBytes != null) {
            byte[] bytes = pendingCoverBytes;
            pendingCoverBytes = null;
            int hash = Arrays.hashCode(bytes);
            if (hash == currentCoverHash && hasCoverArt) return;

            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.getTextureManager() != null) {
                        TextureManager tm = mc.getTextureManager();
                        if (coverTexture != null) {
                            tm.destroyTexture(ALBUM_ART_ID);
                            coverTexture.close();
                        }

                        long sumR = 0, sumG = 0, sumB = 0;
                        int samples = 0;
                        int stepX = Math.max(1, img.getWidth() / 16);
                        int stepY = Math.max(1, img.getHeight() / 16);
                        for (int py = 0; py < img.getHeight(); py += stepY) {
                            for (int px = 0; px < img.getWidth(); px += stepX) {
                                int argb = img.getColorArgb(px, py);
                                int a = (argb >> 24) & 0xFF;
                                if (a < 128) continue;
                                int r = (argb >> 16) & 0xFF;
                                int g = (argb >> 8) & 0xFF;
                                int b = argb & 0xFF;
                                int bri = (r + g + b) / 3;
                                if (bri > 25 && bri < 240) {
                                    sumR += r;
                                    sumG += g;
                                    sumB += b;
                                    samples++;
                                }
                            }
                        }
                        if (samples > 0) {
                            dominantAccentColor = 0xFF000000 | ((int) (sumR / samples) << 16) | ((int) (sumG / samples) << 8) | (int) (sumB / samples);
                        }

                        coverTexture = new NativeImageBackedTexture(() -> "album_art", img);
                        coverTexture.upload();
                        tm.registerTexture(ALBUM_ART_ID, coverTexture);
                        currentCoverHash = hash;
                        hasCoverArt = true;
                    }
                }
            } catch (Throwable ignored) {
                hasCoverArt = false;
            }
        }
    }

    private void drawBrandGradient(Matrix3x2f mat, String text, float x, float y, float size, float alphaFactor) {
        int col1 = titleColor1.get();
        int col2 = titleColor2.get();
        float time = (float) (System.currentTimeMillis() % 4000L) / 4000.0f;
        float curX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float progress = (float) i / Math.max(1, text.length() - 1);
            float wave = 0.5f + 0.5f * (float) Math.sin((progress * 2.2f) - (time * Math.PI * 2.0));
            int color = ColorUtil.lerp(col1, col2, wave);
            int a = (int) (255 * alphaFactor);
            int rgb = color & 0x00FFFFFF;
            Fonts.e.a(mat, ch, curX, y, size, (a << 24) | rgb);
            curX += Fonts.e.a(ch, size);
        }
    }

    private List<WatermarkItem> collectItems(MinecraftClient mc) {
        List<WatermarkItem> list = new ArrayList<>();

        if (showNick.isEnabled()) {
            String username = (mc.player != null && mc.player.getName() != null)
                    ? mc.player.getName().getString()
                    : (mc.getSession() != null ? mc.getSession().getUsername() : "User");
            list.add(new WatermarkItem(username, "USER", null, "\uf007", null));
        }

        if (showServer.isEnabled()) {
            String serverName;
            Identifier serverIco = null;
            if (mc.isInSingleplayer()) {
                serverName = "Local";
            } else if (mc.getCurrentServerEntry() != null) {
                String address = mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
                if (address.contains("funtime")) {
                    serverName = "FunTime";
                    serverIco = FUNTIME_ICON;
                } else if (address.contains("reallyworld")) {
                    serverName = "ReallyWorld";
                    serverIco = REALLYWORLD_ICON;
                } else if (address.contains("spacetime")) {
                    serverName = "SpaceTime";
                    serverIco = SPACETIME_ICON;
                } else {
                    serverName = mc.getCurrentServerEntry().address;
                }
            } else {
                serverName = "Local";
            }
            if (serverIco != null) {
                list.add(new WatermarkItem(serverName, null, serverIco, null, null));
            } else {
                list.add(new WatermarkItem(serverName, null, null, "\uf0ac", null));
            }
        }

        if (showFps.isEnabled()) {
            int fps = Math.round(animatedFps);
            list.add(new WatermarkItem(String.valueOf(fps), "FPS", null, "\uf624", fpsRolling));
        }

        if (showTps.isEnabled()) {
            float tpsVal = 20.0f;
            if (mc.world != null && mc.world.getTickManager() != null) {
                tpsVal = mc.world.getTickManager().getTickRate();
            }
            int tpsInt = Math.round(tpsVal);
            list.add(new WatermarkItem(String.valueOf(tpsInt), "TPS", null, "\uf57d", tpsRolling));
        }

        if (showPing.isEnabled()) {
            int ping = Math.round(animatedPing);
            list.add(new WatermarkItem(ping + "ms", "PING", null, "\uf1eb", null));
        }

        if (showTime.isEnabled()) {
            String timeStr = LocalTime.now().format(TIME_FORMATTER);
            list.add(new WatermarkItem(timeStr, "TIME", null, "\uf017", null));
        }

        if (showCoords.isEnabled() && mc.player != null) {
            int px = (int) Math.floor(mc.player.getX());
            int py = (int) Math.floor(mc.player.getY());
            int pz = (int) Math.floor(mc.player.getZ());
            list.add(new WatermarkItem(px + " " + py + " " + pz, "XYZ", null, "\uf68f", null));
        }

        if (showSpeed.isEnabled()) {
            list.add(new WatermarkItem(String.format(Locale.ROOT, "%.1f", animatedSpeedBps), "BPS", null, "\uf70c", null));
        }

        return list;
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        if (dragging) {
            onChatMouseDragged(mouseX, mouseY, 0);
        }

        if (dragging) {
            int gridStep = 25;
            int gridColor = ColorUtil.rgba(255, 255, 255, 14);
            for (int gx = gridStep; gx < screenW; gx += gridStep) {
                Draw2DProcessor.INSTANCE.drawRect(null, gx, 0, 1.0f, screenH, 0.0f, gridColor);
            }
            for (int gy = gridStep; gy < screenH; gy += gridStep) {
                Draw2DProcessor.INSTANCE.drawRect(null, 0, gy, screenW, 1.0f, 0.0f, gridColor);
            }

            float midX = screenW / 2.0f;
            float midY = screenH / 2.0f;
            int centerColor = ColorUtil.rgba(168, 85, 247, 60);
            Draw2DProcessor.INSTANCE.drawRect(null, midX, 0, 1.0f, screenH, 0.0f, centerColor);
            Draw2DProcessor.INSTANCE.drawRect(null, 0, midY, screenW, 1.0f, 0.0f, centerColor);

            String label = "HUD — сетка";
            float lw = Fonts.b.a(label, 11.5f);
            float lx = lastX + (lastW - lw) / 2.0f;
            float ly = lastY + lastH + 6.0f;
            Fonts.b.a(null, label, lx, ly, 11.5f, ColorUtil.rgba(255, 255, 255, 160));
        }

        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside || dragging) {
            float rad = rounding.getValueFloat() * hudScale.getValueFloat() + 2.0f;
            Draw2DProcessor.INSTANCE.drawOutline(null, lastX - 2.0f, lastY - 2.0f, lastW + 4.0f, lastH + 4.0f, rad, 1.0f, Theme.getAccent());

            String scaleBadge = "HUD: " + Math.round(hudScale.getValueFloat() * 100.0f) + "%";
            float bw = Fonts.b.a(scaleBadge, 9.5f) + 8.0f;
            float bx = lastX + (lastW - bw) / 2.0f;
            float by = lastY + lastH + 5.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 13.0f, 3.0f, ColorUtil.rgba(14, 15, 20, 220));
            Fonts.b.a(null, scaleBadge, bx + 4.0f, by + 2.5f, 9.5f, Theme.getAccentHover());
        }

        boolean insideIsland = showDynamicIsland.isEnabled() && hasActiveMedia
                && mouseX >= lastIslandX - 2.0f && mouseX <= lastIslandX + lastIslandW + 2.0f
                && mouseY >= lastIslandY - 2.0f && mouseY <= lastIslandY + lastIslandH + 2.0f;
        if (insideIsland && !islandExpanded) {
            float iRad = 15.0f * islandScale.getValueFloat() + 1.0f;
            Draw2DProcessor.INSTANCE.drawOutline(null, lastIslandX - 2.0f, lastIslandY - 2.0f, lastIslandW + 4.0f, lastIslandH + 4.0f, iRad, 1.0f, Theme.getAccent());

            String islandBadge = "Остров: " + Math.round(islandScale.getValueFloat() * 100.0f) + "%";
            float ibw = Fonts.b.a(islandBadge, 9.5f) + 8.0f;
            float ibx = lastIslandX + (lastIslandW - ibw) / 2.0f;
            float iby = lastIslandY + lastIslandH + 5.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, ibx, iby, ibw, 13.0f, 3.0f, ColorUtil.rgba(14, 15, 20, 220));
            Fonts.b.a(null, islandBadge, ibx + 4.0f, iby + 2.5f, 9.5f, Theme.getAccentHover());
        }

        if (isSeeking) {
            long window = MinecraftClient.getInstance().getWindow().getHandle();
            if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                float curW = MathHelper.lerp(this.islandExpandAnim, 185.0f, 242.0f);
                float x = (screenW - curW) / 2.0f;
                float centerX = x + curW / 2.0f;
                float baseIslandS = islandScale.getValueFloat();
                float combatS = getCombatScaleFactor("island");
                float islandS = baseIslandS * combatS;
                float localMX = mouseX;
                if (Math.abs(islandS - 1.0f) > 0.001f && islandS > 0.01f) {
                    localMX = centerX + (localMX - centerX) / islandS;
                }
                seekWave(localMX);
            } else {
                isSeeking = false;
            }
        }

        if (contextMenuOpen) {
            renderContextMenu(context, mouseX, mouseY);
        }

        DrawBatcher.flushPending();
    }

    public void onCombatHit() {
        lastCombatTime = System.currentTimeMillis();
    }

    public boolean isInCombat() {
        if (!combatMode.isEnabled()) return false;
        long timeoutMs = (long) (combatTimeout.getValueFloat() * 1000.0f);
        return (System.currentTimeMillis() - lastCombatTime) < timeoutMs;
    }

    public boolean isElementAffected(String element) {
        if (!combatMode.isEnabled()) return false;
        String targets = combatElements.get();
        if ("Все".equalsIgnoreCase(targets)) return true;
        if ("Только ватермарка".equalsIgnoreCase(targets)) return "watermark".equalsIgnoreCase(element);
        if ("Ватермарка + Поушены".equalsIgnoreCase(targets)) return "watermark".equalsIgnoreCase(element) || "potions".equalsIgnoreCase(element);
        if ("Ватермарка + Остров".equalsIgnoreCase(targets)) return "watermark".equalsIgnoreCase(element) || "island".equalsIgnoreCase(element);
        return true;
    }

    public float getCombatAlphaFactor(String element) {
        if (!isElementAffected(element)) return 1.0f;
        String action = combatAction.get();
        boolean changeOpacity = "Прозрачность".equalsIgnoreCase(action) || "Оба".equalsIgnoreCase(action);
        if (!changeOpacity) return 1.0f;
        float combatT = combatAnim.getValue();
        if (combatT <= 0.001f) return 1.0f;
        float opacityReduction = combatOpacity.getValueFloat() / 100.0f;
        float targetAlpha = Math.max(0.05f, 1.0f - opacityReduction);
        return net.minecraft.util.math.MathHelper.lerp(combatT, 1.0f, targetAlpha);
    }

    public float getCombatScaleFactor(String element) {
        if (!isElementAffected(element)) return 1.0f;
        String action = combatAction.get();
        boolean changeScale = "Размер".equalsIgnoreCase(action) || "Масштаб".equalsIgnoreCase(action) || "Оба".equalsIgnoreCase(action);
        if (!changeScale) return 1.0f;
        float combatT = combatAnim.getValue();
        if (combatT <= 0.001f) return 1.0f;
        float targetScale = combatScale.getValueFloat();
        return net.minecraft.util.math.MathHelper.lerp(combatT, 1.0f, targetScale);
    }

    public float getCombatAlphaFactor() {
        return getCombatAlphaFactor("watermark");
    }

    public float getCombatScaleFactor() {
        return getCombatScaleFactor("watermark");
    }

    public int getWatermarkBgColor() {
        return watermarkBgColor.get();
    }

    public float getRounding() {
        return rounding.getValueFloat();
    }

    public boolean isHudThemeGradient() {
        return hudThemeGradient.isEnabled();
    }

    public float getHudGradientStrength() {
        return hudGradientStrength.getValueFloat();
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;

        if (showDynamicIsland.isEnabled() && hasActiveMedia) {
            if (mouseX >= lastIslandX - 4.0f && mouseX <= lastIslandX + lastIslandW + 4.0f
                    && mouseY >= lastIslandY - 4.0f && mouseY <= lastIslandY + lastIslandH + 4.0f) {
                double cur = islandScale.getValue();
                double next = Math.clamp(cur + (amount > 0 ? 0.05 : -0.05), 0.5, 1.5);
                next = Math.round(next * 100.0) / 100.0;
                islandScale.setValue(next);
                ConfigManager.save();
                return true;
            }
        }

        if (mouseX >= lastX - 4.0f && mouseX <= lastX + lastW + 4.0f
                && mouseY >= lastY - 4.0f && mouseY <= lastY + lastH + 4.0f) {
            double cur = hudScale.getValue();
            double next = Math.clamp(cur + (amount > 0 ? 0.05 : -0.05), 0.5, 1.5);
            next = Math.round(next * 100.0) / 100.0;
            hudScale.setValue(next);
            ConfigManager.save();
            return true;
        }

        return false;
    }

    public boolean onChatMouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        if (showDynamicIsland.isEnabled() && hasActiveMedia && button == 0) {
            float collapsedW = 185.0f;
            float expandedW = 242.0f;
            float curW = MathHelper.lerp(this.islandExpandAnim, collapsedW, expandedW);
            float curH = MathHelper.lerp(this.islandExpandAnim, 30.0f, 70.0f);
            float x = (screenW - curW) / 2.0f;
            float y = 8.0f;
            float centerX = x + curW / 2.0f;

            float baseIslandS = islandScale.getValueFloat();
            float combatS = getCombatScaleFactor("island");
            float islandS = baseIslandS * combatS;
            boolean customIslandScale = Math.abs(islandS - 1.0f) > 0.001f;

            float localMX = (float) mouseX;
            float localMY = (float) mouseY;
            if (customIslandScale && islandS > 0.01f) {
                localMX = centerX + (localMX - centerX) / islandS;
                localMY = y + (localMY - y) / islandS;
            }

            if (!islandExpanded) {
                if (localMX >= x && localMX <= x + curW && localMY >= y && localMY <= y + curH) {
                    islandExpanded = true;
                    return true;
                }
            } else {

                if (localMX >= lastArtX - 2.0f && localMX <= lastArtX + lastArtSize + 2.0f
                        && localMY >= lastArtY - 2.0f && localMY <= lastArtY + lastArtSize + 2.0f) {
                    if (OptMedia.init() && OptMedia.INSTANCE != null) {
                        try {
                            OptMedia.INSTANCE.TogglePlayPause();
                        } catch (Throwable ignored) {}
                    }
                    return true;
                }

                if (localMX >= lastCloseX - 8.0f && localMX <= x + curW + 4.0f
                        && localMY >= y && localMY <= y + 24.0f) {
                    islandExpanded = false;
                    return true;
                }

                if (lastBtnNextX > 0.0f && localMX >= lastBtnNextX - 4.0f && localMX <= lastBtnNextX + 16.0f
                        && localMY >= lastRowY - 6.0f && localMY <= lastRowY + 18.0f) {
                    if (OptMedia.init() && OptMedia.INSTANCE != null) {
                        try {
                            OptMedia.INSTANCE.SkipNext();
                        } catch (Throwable ignored) {}
                    }
                    return true;
                }

                if (lastBtnPrevX > 0.0f && localMX >= lastBtnPrevX - 6.0f && localMX <= lastBtnPrevX + 10.0f
                        && localMY >= lastRowY - 6.0f && localMY <= lastRowY + 18.0f) {
                    if (OptMedia.init() && OptMedia.INSTANCE != null) {
                        try {
                            OptMedia.INSTANCE.SkipPrevious();
                        } catch (Throwable ignored) {}
                    }
                    return true;
                }

                if (lastWaveX > 0.0f && localMX >= lastWaveX - 4.0f && localMX <= lastWaveX + lastWaveW + 4.0f
                        && localMY >= lastRowY - 6.0f && localMY <= lastRowY + 18.0f) {
                    isSeeking = true;
                    seekWave(localMX);
                    return true;
                }

                if (localMX >= x && localMX <= x + curW && localMY >= y && localMY <= y + curH) {
                    return true;
                }
            }
        }

        if (contextMenuOpen) {
            float mw = 130.0f;
            float mh = 195.0f;
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + mw && mouseY >= contextMenuY && mouseY <= contextMenuY + mh) {
                float iy = contextMenuY + 22.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showDynamicIsland.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showLogo.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showNick.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showServer.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showFps.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showTps.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showPing.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showTime.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showCoords.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { showSpeed.toggle(); ConfigManager.save(); return true; }
                iy += 15.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) { sideMode.toggle(); ConfigManager.save(); return true; }
                return true;
            }
            contextMenuOpen = false;
        }

        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside) {
            if (button == 1) {
                contextMenuOpen = true;
                contextMenuX = (float) Math.clamp(mouseX, 10.0, screenW - 140.0);
                contextMenuY = (float) Math.clamp(mouseY, 10.0, screenH - 220.0);
                return true;
            } else if (button == 0) {
                dragging = true;
                dragOffsetX = (float) mouseX - lastX;
                dragOffsetY = (float) mouseY - lastY;
                return true;
            }
        }

        return false;
    }

    public boolean onChatMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isSeeking) {
                isSeeking = false;
                return true;
            }
            if (dragging) {
                dragging = false;
                ConfigManager.save();
                return true;
            }
        }
        return false;
    }

    public boolean onChatMouseDragged(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();

            float newX = (float) mouseX - dragOffsetX;
            float newY = (float) mouseY - dragOffsetY;

            float midX = (screenW - lastW) / 2.0f;
            if (Math.abs(newX - midX) < 8.0f) {
                newX = midX;
            }
            float midY = (screenH - lastH) / 2.0f;
            if (Math.abs(newY - midY) < 8.0f) {
                newY = midY;
            }

            float nearestGridX = Math.round(newX / 25.0f) * 25.0f;
            if (Math.abs(newX - nearestGridX) < 5.0f) {
                newX = nearestGridX;
            }
            float nearestGridY = Math.round(newY / 25.0f) * 25.0f;
            if (Math.abs(newY - nearestGridY) < 5.0f) {
                newY = nearestGridY;
            }

            float maxAvailX = Math.max(1.0f, screenW - lastW);
            float maxAvailY = Math.max(1.0f, screenH - lastH);

            posX.setValue(Math.clamp(newX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(newY / maxAvailY, 0.0, 1.0));
            return true;
        }
        return false;
    }

    private void renderContextMenu(DrawContext context, float mouseX, float mouseY) {
        float mw = 130.0f;
        float mh = 195.0f;
        float mx = contextMenuX;
        float my = contextMenuY;

        Draw2DProcessor.INSTANCE.drawGlow(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(0, 0, 0, 190), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(22, 23, 30, 248));
        Draw2DProcessor.INSTANCE.drawOutline(null, mx, my, mw, mh, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        Fonts.d.a(null, "Элементы HUD", mx + 8.0f, my + 6.0f, 12.0f, Theme.getAccentHover());
        Draw2DProcessor.INSTANCE.drawRect(null, mx + 6.0f, my + 18.0f, mw - 12.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 15));

        float itemY = my + 22.0f;
        renderContextItem(context, mx, itemY, mw, "Айленд", showDynamicIsland.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Иконка", showLogo.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Логин", showNick.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Сервер", showServer.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "FPS", showFps.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "TPS", showTps.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Ping", showPing.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Время", showTime.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Координаты", showCoords.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Скорость", showSpeed.isEnabled(), mouseX, mouseY); itemY += 15.0f;
        renderContextItem(context, mx, itemY, mw, "Боковой режим", sideMode.isEnabled(), mouseX, mouseY);
    }

    private void renderContextItem(DrawContext context, float mx, float iy, float mw, String label, boolean checked, float mouseX, float mouseY) {
        boolean hov = mouseX >= mx + 4.0f && mouseX <= mx + mw - 4.0f && mouseY >= iy - 1.0f && mouseY <= iy + 13.0f;
        if (hov) {
            Draw2DProcessor.INSTANCE.drawRect(null, mx + 4.0f, iy - 1.0f, mw - 8.0f, 13.0f, 2.5f, ColorUtil.rgba(255, 255, 255, 14));
        }

        float cbX = mx + 8.0f;
        float cbY = iy + 1.0f;
        float cbSize = 9.0f;
        Draw2DProcessor.INSTANCE.drawRect(null, cbX, cbY, cbSize, cbSize, 2.0f, checked ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 22));
        if (checked) {
            Draw2DProcessor.INSTANCE.drawRect(null, cbX + cbSize / 2.0f - 1.5f, cbY + cbSize / 2.0f - 1.5f, 3.0f, 3.0f, 1.5f, 0xFFFFFFFF);
        }

        Fonts.b.a(null, label, cbX + cbSize + 6.0f, iy + 1.0f, 11.5f, checked ? 0xFFFFFFFF : Theme.TEXT_SUB);
    }

    public static class RollingNumber {
        private int currentVal = -1;
        private int prevVal = -1;
        private long changeTime = 0L;
        private static final long DURATION_MS = 180L;

        public void update(int newVal) {
            if (currentVal == -1) {
                currentVal = newVal;
                prevVal = newVal;
                return;
            }
            if (newVal != currentVal) {
                prevVal = currentVal;
                currentVal = newVal;
                changeTime = System.currentTimeMillis();
            }
        }

        public void render(Matrix3x2f mat, float x, float y, float size, int color, float alphaFactor) {
            long elapsed = System.currentTimeMillis() - changeTime;
            int baseA = (int) (((color >> 24) & 0xFF) * alphaFactor);
            int rgb = color & 0x00FFFFFF;
            if (elapsed >= DURATION_MS || prevVal == currentVal) {
                Fonts.d.a(mat, String.valueOf(currentVal), x, y, size, (baseA << 24) | rgb);
                return;
            }

            float t = (float) elapsed / DURATION_MS;
            float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

            String oldText = String.valueOf(prevVal);
            String newText = String.valueOf(currentVal);

            float oldY = y - ease * 5.0f;
            int oldA = Math.round(baseA * (1.0f - ease));
            if (oldA > 5) {
                Fonts.d.a(mat, oldText, x, oldY, size, (oldA << 24) | rgb);
            }

            float newY = y + (1.0f - ease) * 5.0f;
            int newA = Math.round(baseA * ease);
            if (newA > 5) {
                Fonts.d.a(mat, newText, x, newY, size, (newA << 24) | rgb);
            }
        }
    }

    public boolean isTestMusic() {
        return false;
    }

    private static class WatermarkItem {
        final String valText;
        final String subText;
        final Identifier texture;
        final String iconGlyph;
        final RollingNumber rolling;

        WatermarkItem(String valText, String subText, Identifier texture, String iconGlyph, RollingNumber rolling) {
            this.valText = valText;
            this.subText = subText;
            this.texture = texture;
            this.iconGlyph = iconGlyph;
            this.rolling = rolling;
        }
    }
}
