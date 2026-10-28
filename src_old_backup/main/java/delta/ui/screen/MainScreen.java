package delta.ui.screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;

import delta.render.ScaleUtil;
import delta.core.NativeMethodLookup;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Interface;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;
import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.render.Draw2DProcessor;
import delta.render.AnimationUtil;
import delta.api.Compile;
import delta.optmedia.OptMedia;
import delta.optmedia.Artist;
import delta.ui.shader.BlurShader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class MainScreen extends Screen {

    // ==========================================
    // PERSISTENT UNLOCKED & THEME STATES
    // ==========================================
    private static boolean isUnlockedPersistent = true;
    private static boolean isLightTheme = false;

    // ==========================================
    // WALLPAPER MODEL & CONFIG (4 WALLPAPERS)
    // ==========================================
    public static class WallpaperItem {
        public final String id;
        public final String name;
        public final Identifier texture;

        public WallpaperItem(String id, String name, Identifier texture) {
            this.id = id;
            this.name = name;
            this.texture = texture;
        }
    }

    public static final List<WallpaperItem> WALLPAPERS = List.of(
            new WallpaperItem("dark_lotus", "Тёмный лотос", Identifier.of("delta", "pictures/wallpapers/dark_lotus.png")),
            new WallpaperItem("white_tubes", "Белый 3D", Identifier.of("delta", "pictures/wallpapers/white_tubes.png")),
            new WallpaperItem("dark_silk", "Тёмный шёлк", Identifier.of("delta", "pictures/wallpapers/dark_silk.png")),
            new WallpaperItem("blue_wave", "Синяя волна", Identifier.of("delta", "pictures/wallpapers/blue_wave.png")),
            new WallpaperItem("azure_wave", "Лазурная волна", Identifier.of("delta", "pictures/wallpapers/azure_wave.png"))
    );

    private static final Identifier SETTINGS_GEAR_ICON = Identifier.of("delta", "textures/settings_gear.png");

    private static String currentWallpaperId = "dark_lotus";
    private static float wallpaperBlur = 0.0f;
    private static boolean draggingBlurSlider = false;

    private static File getWallpaperConfigFile() {
        File cfgDir = new File(Interface.aM_.runDirectory, "configs");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "wallpaper.txt");
    }

    public static void loadWallpaperConfig() {
        try {
            File file = getWallpaperConfigFile();
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                if (!lines.isEmpty()) {
                    String id = lines.get(0).trim();
                    for (WallpaperItem w : WALLPAPERS) {
                        if (w.id.equalsIgnoreCase(id)) {
                            currentWallpaperId = w.id;
                            break;
                        }
                    }
                    if (lines.size() > 1) {
                        wallpaperBlur = MathHelper.clamp(Float.parseFloat(lines.get(1).trim()), 0.0f, 1.0f);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
        currentWallpaperId = "dark_lotus";
        wallpaperBlur = 0.0f;
    }

    public static void saveWallpaperConfig(String id) {
        saveWallpaperConfig(id, wallpaperBlur);
    }

    public static void saveWallpaperConfig(String id, float blur) {
        currentWallpaperId = id;
        wallpaperBlur = MathHelper.clamp(blur, 0.0f, 1.0f);
        try {
            File file = getWallpaperConfigFile();
            Files.writeString(file.toPath(), id + "\n" + wallpaperBlur);
        } catch (Throwable ignored) {}
    }

    public static WallpaperItem getCurrentWallpaper() {
        for (WallpaperItem w : WALLPAPERS) {
            if (w.id.equals(currentWallpaperId)) {
                return w;
            }
        }
        return WALLPAPERS.get(0);
    }

    public static void loadThemeConfig() {
        isLightTheme = false;
        try {
            saveThemeConfig();
        } catch (Throwable ignored) {}
    }

    public static void saveThemeConfig() {
        try {
            File file = new File(Interface.aM_.runDirectory, "configs/theme_mode.txt");
            Files.writeString(file.toPath(), String.valueOf(isLightTheme));
        } catch (Throwable ignored) {}
    }

    // ==========================================
    // LIQUID GLASS CONFIG & SLIDERS
    // ==========================================
    public static class GlassConfig {
        public float blurStrength = 1.0f;        // 0.0f - 1.0f
        public float refractionStrength = 0.85f;  // 0.0f - 1.0f
        public float specularGloss = 1.0f;        // 0.0f - 1.0f
        public float glassOpacity = 0.80f;        // 0.2f - 1.0f
        public int clockStyle = 0;                // 0: Жидкое стекло, 1: Белый
    }

    private static final GlassConfig GLASS_CONFIG = new GlassConfig();

    private static File getGlassConfigFile() {
        File cfgDir = new File(Interface.aM_.runDirectory, "configs");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "liquid_glass.txt");
    }

    public static void loadGlassConfig() {
        try {
            File file = getGlassConfigFile();
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String val = parts[1].trim();
                        switch (key) {
                            case "blur" -> GLASS_CONFIG.blurStrength = Float.parseFloat(val);
                            case "refraction" -> GLASS_CONFIG.refractionStrength = Float.parseFloat(val);
                            case "gloss" -> GLASS_CONFIG.specularGloss = Float.parseFloat(val);
                            case "opacity" -> GLASS_CONFIG.glassOpacity = Float.parseFloat(val);
                            case "clockStyle" -> GLASS_CONFIG.clockStyle = Integer.parseInt(val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void saveGlassConfig() {
        try {
            File file = getGlassConfigFile();
            String content = "blur=" + GLASS_CONFIG.blurStrength + "\n"
                    + "refraction=" + GLASS_CONFIG.refractionStrength + "\n"
                    + "gloss=" + GLASS_CONFIG.specularGloss + "\n"
                    + "opacity=" + GLASS_CONFIG.glassOpacity + "\n"
                    + "clockStyle=" + GLASS_CONFIG.clockStyle + "\n";
            Files.writeString(file.toPath(), content);
        } catch (Throwable ignored) {}
    }

    // Config for buttons
    private GlassConfig getButtonAndMediaConfig() {
        GlassConfig cfg = new GlassConfig();
        if (isLightTheme) {
            cfg.blurStrength = GLASS_CONFIG.blurStrength;
            cfg.refractionStrength = GLASS_CONFIG.refractionStrength;
            cfg.specularGloss = GLASS_CONFIG.specularGloss;
            cfg.glassOpacity = GLASS_CONFIG.glassOpacity;
        } else {
            cfg.blurStrength = 1.0f;
            cfg.refractionStrength = 0.45f;
            cfg.specularGloss = 0.85f;
            cfg.glassOpacity = 0.75f;
        }
        return cfg;
    }

    // ==========================================
    // MEDIA PLAYER CONFIG & PERSISTENCE
    // ==========================================
    public static class PlayerConfig {
        public int visibilityMode = 0;   // 0: Всегда, 1: С музыкой, 2: Скрыт
        public boolean scrollMarquee = true;
        public boolean showTimeCodes = true;
        public float volume = 1.0f;
        public float cardOpacity = 0.85f;
        public float cardBlur = 1.0f;
        public float playerScale = 1.0f;
    }

    public static final PlayerConfig PLAYER_CONFIG = new PlayerConfig();

    private static File getPlayerConfigFile() {
        File cfgDir = new File(Interface.aM_.runDirectory, "configs");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        return new File(cfgDir, "player_menu.txt");
    }

    public static void loadPlayerConfig() {
        try {
            File file = getPlayerConfigFile();
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        String k = parts[0].trim();
                        String v = parts[1].trim();
                        switch (k) {
                            case "visibilityMode" -> PLAYER_CONFIG.visibilityMode = Integer.parseInt(v);
                            case "scrollMarquee" -> PLAYER_CONFIG.scrollMarquee = Boolean.parseBoolean(v);
                            case "showTimeCodes" -> PLAYER_CONFIG.showTimeCodes = Boolean.parseBoolean(v);
                            case "volume" -> PLAYER_CONFIG.volume = Float.parseFloat(v);
                            case "cardOpacity" -> PLAYER_CONFIG.cardOpacity = Float.parseFloat(v);
                            case "cardBlur" -> PLAYER_CONFIG.cardBlur = Float.parseFloat(v);
                            case "playerScale" -> PLAYER_CONFIG.playerScale = Float.parseFloat(v);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void savePlayerConfig() {
        try {
            File file = getPlayerConfigFile();
            String content = "visibilityMode=" + PLAYER_CONFIG.visibilityMode + "\n"
                    + "scrollMarquee=" + PLAYER_CONFIG.scrollMarquee + "\n"
                    + "showTimeCodes=" + PLAYER_CONFIG.showTimeCodes + "\n"
                    + "volume=" + PLAYER_CONFIG.volume + "\n"
                    + "cardOpacity=" + PLAYER_CONFIG.cardOpacity + "\n"
                    + "cardBlur=" + PLAYER_CONFIG.cardBlur + "\n"
                    + "playerScale=" + PLAYER_CONFIG.playerScale + "\n";
            Files.writeString(file.toPath(), content);
        } catch (Throwable ignored) {}
    }

    private static void resetPlayerConfig() {
        PLAYER_CONFIG.visibilityMode = 0;
        PLAYER_CONFIG.scrollMarquee = true;
        PLAYER_CONFIG.showTimeCodes = true;
        PLAYER_CONFIG.volume = 1.0f;
        PLAYER_CONFIG.cardOpacity = 0.85f;
        PLAYER_CONFIG.cardBlur = 1.0f;
        PLAYER_CONFIG.playerScale = 1.0f;
        MEDIA_EXECUTOR.submit(() -> {
            try {
                if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SetVolume(1.0f);
            } catch (Throwable ignored) {}
        });
        savePlayerConfig();
    }

    private GlassConfig getMediaPlayerGlassConfig() {
        GlassConfig cfg = new GlassConfig();
        cfg.blurStrength = Math.max(0.6f, PLAYER_CONFIG.cardBlur);
        cfg.refractionStrength = isLightTheme ? GLASS_CONFIG.refractionStrength : 0.45f;
        cfg.specularGloss = isLightTheme ? GLASS_CONFIG.specularGloss : 0.85f;
        cfg.glassOpacity = Math.max(0.65f, PLAYER_CONFIG.cardOpacity);
        return cfg;
    }

    // ==========================================
    // LIQUID GLASS RENDER ENGINE
    // ==========================================
    public static void drawLiquidGlass(Draw2DProcessor draw, MatrixStack matrices, float x, float y, float w, float h, float radius, float alpha, boolean isLight, GlassConfig cfg) {
        if (alpha <= 0.01f) return;

        // 1. Soft ambient elevation shadow (physical 3D depth)
        draw.a(matrices, x - 1.5f, y + 2.5f, w + 3.0f, h + 3.5f, radius + 1.5f, ColorUtil.a(0, 0, 0, (int) (95 * alpha)));

        // 2. Deep obsidian frosted glass body
        int baseBg = ColorUtil.a(13, 15, 22, (int) (225 * alpha));
        draw.a(matrices, x, y, w, h, radius, baseBg);

        // 3. Subtle inner highlight (top half soft reflection)
        float topH = Math.max(2.0f, h * 0.40f);
        draw.a(matrices, x + 1.0f, y + 1.0f, w - 2.0f, topH, Math.max(1.0f, radius - 1.0f), ColorUtil.a(255, 255, 255, (int) (8 * alpha)));

        // 4. Ultra-thin elegant frosted border (0.75px)
        draw.a(matrices, x, y, w, h, radius, 0.75f, ColorUtil.a(255, 255, 255, (int) (32 * alpha)));

        // 5. Delicate top specular gleam
        float glossW = Math.max(6.0f, w - (radius * 1.5f));
        float glossX = x + ((w - glossW) * 0.5f);
        draw.a(matrices, glossX, y + 0.6f, glossW, 0.75f, 0.35f, ColorUtil.a(255, 255, 255, (int) (25 * alpha)));
    }

    // ==========================================
    // MEDIA CONTROLLER STATE (FOR LOCKSCREEN)
    // ==========================================
    private static final Identifier LOCK_COVER_ID = Identifier.of("delta", "dynamic/main_lock_cover");
    private static final ExecutorService MEDIA_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Delta-MainScreen-Media");
        t.setDaemon(true);
        return t;
    });

    private final Artist artistStruct = new Artist();
    private long lastMediaPoll = 0L;
    private volatile boolean hasActiveMedia = false;
    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile boolean isPlaying = false;
    private volatile long currentDurationMs = 0L;
    private volatile long currentPositionMs = 0L;
    private volatile byte[] pendingCoverBytes = null;
    private int currentCoverHash = 0;
    private String lastTrackKey = "";
    private NativeImageBackedTexture coverTexture = null;
    private volatile boolean hasCoverArt = false;
    private int mediaFailCount = 0;

    // Hitboxes
    private float lastMediaX = 0.0f;
    private float lastMediaY = 0.0f;
    private float lastMediaW = 208.0f;
    private float lastMediaH = 64.0f;
    private float lastBadgeX = 0.0f;
    private float lastBadgeY = 0.0f;
    private float lastBadgeW = 0.0f;
    private float lastBadgeH = 0.0f;
    private float lastBarX = 0.0f;
    private float lastBarY = 0.0f;
    private float lastBarW = 0.0f;
    private float lastBarH = 6.0f;
    private float lastPrevBtnX = 0.0f;
    private float lastPlayBtnX = 0.0f;
    private float lastNextBtnX = 0.0f;
    private float lastCtrlY = 0.0f;
    private float lastActionBtn1X = 0.0f;
    private float lastActionBtn2X = 0.0f;
    private float lastActionBtn3X = 0.0f;
    private int dominantAccentColor = 0xFF14B8A6;
    private float currentAccentR = 0.08f;
    private float currentAccentG = 0.72f;
    private float currentAccentB = 0.65f;
    private float lastNonMutedVolume = 1.0f;
    private boolean isDraggingSeek = false;

    // Clock hitboxes for Right-Click Context Menu
    private float lastClockX = 0.0f;
    private float lastClockY = 0.0f;
    private float lastClockW = 160.0f;
    private float lastClockH = 62.0f;

    // Slider Dragging State in Context Menu
    private int activeDraggingSlider = -1; // 0: blur, 1: refraction, 2: gloss, 3: opacity
    private float menuSliderX = 0.0f;
    private float menuSliderW = 110.0f;

    // ==========================================
    // ATMOSPHERIC DUST / FLOATING EMBERS
    // ==========================================
    // PURE WHITE FALLING SNOW WITH CRUMB TRAILS
    // ==========================================
    // LIVE FLUID AMBIENT BLOBS (Client Palette, zero snowflakes/cheap stars)
    // ==========================================
    public static class FluidAmbientBlob {
        public float cx;
        public float cy;
        public float radius;
        public float speed;
        public float phase;
        public float baseAlpha;
        public int color;

        public FluidAmbientBlob(float cx, float cy, float radius, float speed, float phase, float baseAlpha, int color) {
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.speed = speed;
            this.phase = phase;
            this.baseAlpha = baseAlpha;
            this.color = color;
        }

        public void render(MatrixStack matrices, Draw2DProcessor draw, float screenW, float screenH, float mouseX, float mouseY, float alpha) {
            float timeSec = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
            float offX = (float) Math.sin(timeSec * this.speed + this.phase) * (screenW * 0.12f);
            float offY = (float) Math.cos(timeSec * (this.speed * 0.85f) + this.phase) * (screenH * 0.10f);

            // Subtle parallax against mouse
            float mOffsetX = ((mouseX / Math.max(1.0f, screenW)) - 0.5f) * 25.0f;
            float mOffsetY = ((mouseY / Math.max(1.0f, screenH)) - 0.5f) * 20.0f;

            float curX = this.cx + offX - mOffsetX;
            float curY = this.cy + offY - mOffsetY;

            float pulse = 1.0f + 0.08f * (float) Math.sin(timeSec * (this.speed * 1.3f) + this.phase);
            float r = this.radius * pulse;

            int blobCol = ColorUtil.a(this.color, (int) (this.baseAlpha * 255.0f * alpha));
            // Soft ambient glow circle (large radius, zero core hardness)
            draw.a(matrices, curX - r, curY - r, r * 2.0f, r * 2.0f, r, 0x00000000, 1.0f, blobCol, r * 0.95f);
        }
    }

    private static final List<FluidAmbientBlob> ambientBlobs = new ArrayList<>();

    public static void renderAtmosphericDustStatic(MatrixStack matrices, Draw2DProcessor draw, float screenW, float screenH, float mouseX, float mouseY, float alpha) {
        // Ambient blobs disabled as requested ("убери шары в мейн меню")
        return;
    }

    // ==========================================
    // CIRCULAR APP BUTTON MODEL (FOR UNLOCKED)
    // ==========================================
    public static class AppCircleButton {
        public final int id;
        public final String label;
        public final Runnable action;
        public final AnimationUtil hoverAnim = new AnimationUtil();
        public float cx, cy, r;

        public AppCircleButton(int id, String label, Runnable action) {
            this.id = id;
            this.label = label;
            this.action = action;
        }
    }

    private final List<AppCircleButton> appButtons = new ArrayList<>();
    private final AnimationUtil homeHoverAnim = new AnimationUtil();
    private final AnimationUtil clockHapticAnim = new AnimationUtil();

    // iOS 26/27 Floating Dock State & Physics
    private int selectedDockIndex = 0;
    private int hoveredDockIndex = -1;
    private boolean isDraggingDock = false;
    private float dockPillCenterX = -1.0f;
    private float targetPillCenterX = -1.0f;
    private float dockPillVelocity = 0.0f;
    private float dockPillStretch = 0.0f;
    private float dockTooltipAlpha = 0.0f;
    private float lastDockX = 0.0f;
    private float lastDockY = 0.0f;
    private float lastDockW = 0.0f;
    private float lastDockH = 0.0f;
    private float lastItemW = 48.0f;
    private float lastDockPad = 6.0f;

    // Player Menu State & Sliders
    private final AnimationUtil playerMenuAnim = new AnimationUtil();
    private boolean playerMenuOpen = false;
    private int activePlayerSlider = -1; // 0: volume, 1: opacity, 2: blur
    private float playerSliderX = 0.0f;
    private float playerSliderW = 100.0f;
    private float lastPlayerMenuX = 0.0f;
    private float lastPlayerMenuY = 0.0f;
    private float lastPlayerMenuW = 208.0f;
    private float lastPlayerMenuH = 264.0f;
    private float lastPlayerGearX = 0.0f;
    private float lastPlayerGearY = 0.0f;

    // ==========================================
    // SCREEN STATE & ANIMATIONS
    // ==========================================
    private static final float[] parallax = new float[2];
    private final AnimationUtil openAnim = new AnimationUtil();
    private final AnimationUtil unlockAnim = new AnimationUtil();
    private final AnimationUtil pickerAnim = new AnimationUtil();
    private final AnimationUtil clockMenuAnim = new AnimationUtil();

    private boolean isUnlocked = false;
    private boolean wallpaperPickerOpen = false;
    private boolean clockMenuOpen = false;
    private float textScrollOffset = 0.0f;
    private long lastScrollTime = 0L;

    // ==========================================
    // PLAYSTATION STARTUP INTRO & 3D EGG LOGO
    // ==========================================
    private static boolean introCompleted = true;
    private static long introStartTime = -1L;
    private static boolean burstTriggered = true;
    private static long burstTriggerTime = -1L;
    private static float currentEggPitch = 0.0f;
    private static float currentEggYaw = 0.0f;
    private static float currentEggRoll = 0.0f;

    public static void triggerBurst() {
        if (!burstTriggered) {
            burstTriggered = true;
            burstTriggerTime = System.currentTimeMillis();
        }
    }

    static {
        NativeMethodLookup.lookup(MainScreen.class, 16);
    }

    public MainScreen() {
        super(Text.empty());
        loadWallpaperConfig();
        loadThemeConfig();
        loadGlassConfig();
        loadPlayerConfig();

        // 1: Singleplayer
        this.appButtons.add(new AppCircleButton(1, "Одиночная", () -> {
            Interface.aM_.setScreen(new SelectWorldScreen((Screen) null));
        }));

        // 2: Multiplayer
        this.appButtons.add(new AppCircleButton(2, "Сетевая", () -> {
            Interface.aM_.setScreen(new MultiplayerScreen((Screen) null));
        }));

        // 3: Accounts
        this.appButtons.add(new AppCircleButton(3, "Аккаунты", () -> {
            Interface.aM_.setScreen(new AltScreen());
        }));

        // 4: Options / Settings
        this.appButtons.add(new AppCircleButton(4, "Настройки", () -> {
            Interface.aM_.setScreen(new OptionsScreen((Screen) null, Interface.aM_.options));
        }));
    }

    @Override
    protected void init() {
        super.init();
        if (introStartTime == -1L) {
            introStartTime = System.currentTimeMillis();
            introCompleted = true;
            burstTriggered = true;
            burstTriggerTime = System.currentTimeMillis();
        }
        this.openAnim.c(1.0f);
        this.openAnim.d(1.0f);
        this.openAnim.e(1.0f);

        // Persistent unlocked state fix: if user was previously unlocked, KEEP unlocked!
        this.isUnlocked = isUnlockedPersistent;
        if (isUnlockedPersistent) {
            this.unlockAnim.c(1.0f);
            this.unlockAnim.d(1.0f);
            this.unlockAnim.e(1.0f);
        } else {
            this.unlockAnim.c(0.0f);
            this.unlockAnim.d(0.0f);
            this.unlockAnim.e(0.0f);
        }

        this.wallpaperPickerOpen = false;
        this.clockMenuOpen = false;
        this.activeDraggingSlider = -1;
        this.isDraggingDock = false;
    }

    // ==========================================
    // MEDIA POLLING & TEXTURES
    // ==========================================
    private void pollMedia() {
        long now = System.currentTimeMillis();
        if (now - this.lastMediaPoll < 280L) return;
        this.lastMediaPoll = now;

        MEDIA_EXECUTOR.submit(() -> {
            if (!OptMedia.init() || OptMedia.INSTANCE == null) {
                if (++this.mediaFailCount >= 4) this.hasActiveMedia = false;
                return;
            }
            try {
                boolean ok = OptMedia.INSTANCE.GetCurrentMediaInfo(this.artistStruct);
                if (ok && this.artistStruct.getTitle() != null && !this.artistStruct.getTitle().trim().isEmpty()) {
                    this.mediaFailCount = 0;
                    this.hasActiveMedia = true;
                    this.currentTitle = this.artistStruct.getTitle().trim();
                    this.currentArtist = this.artistStruct.getArtist().trim();
                    this.isPlaying = this.artistStruct.isPlaying();
                    this.currentDurationMs = Math.max(0L, this.artistStruct.durationMs);
                    this.currentPositionMs = Math.max(0L, this.artistStruct.positionMs);

                    String trackKey = this.currentTitle + "|" + this.currentArtist + "|" + this.currentDurationMs;
                    boolean trackChanged = !trackKey.equals(this.lastTrackKey);
                    if (trackChanged) this.lastTrackKey = trackKey;

                    byte[] art = this.artistStruct.getAlbumArt();
                    if (art != null && art.length > 0) {
                        this.pendingCoverBytes = art;
                    } else if (trackChanged) {
                        this.pendingCoverBytes = null;
                        destroyCoverTexture();
                    }

                    try {
                        OptMedia.INSTANCE.FreeMediaInfo(this.artistStruct);
                        this.artistStruct.albumArtPng = null;
                        this.artistStruct.albumArtSize = 0;
                    } catch (Throwable ignored) {}
                } else {
                    if (++this.mediaFailCount >= 4) this.hasActiveMedia = false;
                }
            } catch (Throwable ignored) {
                if (++this.mediaFailCount >= 4) this.hasActiveMedia = false;
            }
        });
    }

    private void updateCoverTexture() {
        byte[] bytes = this.pendingCoverBytes;
        if (bytes != null) {
            this.pendingCoverBytes = null;
            int hash = java.util.Arrays.hashCode(bytes);
            if (hash == this.currentCoverHash && this.coverTexture != null) return;
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    extractDominantColor(img);
                    TextureManager tm = aM_.getTextureManager();
                    if (this.coverTexture != null) {
                        tm.destroyTexture(LOCK_COVER_ID);
                        this.coverTexture.close();
                    }
                    this.coverTexture = new NativeImageBackedTexture(() -> "lock_cover", img);
                    tm.registerTexture(LOCK_COVER_ID, this.coverTexture);
                    this.currentCoverHash = hash;
                    this.hasCoverArt = true;
                }
            } catch (Throwable ignored) {
                this.hasCoverArt = false;
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

    private void destroyCoverTexture() {
        if (this.hasCoverArt) {
            try {
                aM_.getTextureManager().destroyTexture(LOCK_COVER_ID);
                if (this.coverTexture != null) {
                    this.coverTexture.close();
                    this.coverTexture = null;
                }
            } catch (Throwable ignored) {}
            this.hasCoverArt = false;
            this.currentCoverHash = 0;
        }
    }

    @Override
    public void close() {
        super.close();
        destroyCoverTexture();
    }

    // ==========================================
    // MAIN RENDER LOOP
    // ==========================================
    @Compile
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        pollMedia();
        updateCoverTexture();

        this.openAnim.a(Interface.aM_.currentScreen instanceof MainScreen);
        this.openAnim.a(0.0f, 1.0f, 0.25f, EasingList.g, delta);
        float openProgress = Math.min(1.0f, this.openAnim.c() / 0.9f);

        this.unlockAnim.a(this.isUnlocked);
        this.unlockAnim.a(0.0f, 1.0f, 0.22f, EasingList.g, delta);
        float unlockProgress = this.unlockAnim.c();

        this.pickerAnim.a(this.wallpaperPickerOpen);
        this.pickerAnim.a(0.0f, 1.0f, 0.24f, EasingList.g, delta);
        float pickerProgress = this.pickerAnim.c();

        this.clockMenuAnim.a(this.clockMenuOpen);
        this.clockMenuAnim.a(0.0f, 1.0f, 0.25f, EasingList.g, delta);
        float menuProgress = this.clockMenuAnim.c();

        this.playerMenuAnim.a(this.playerMenuOpen);
        this.playerMenuAnim.a(0.0f, 1.0f, 0.25f, EasingList.g, delta);
        float playerMenuProgress = this.playerMenuAnim.c();

        double dA = MathUtil.scale(mouseX, 2);
        double dA2 = MathUtil.scale(mouseY, 2);
        ScaleUtil.a(context, 2);

        MinecraftClient mc = Interface.aM_;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        int primaryColor = theme.a(ThemeInfo.PRIMARY).a();

        long now = System.currentTimeMillis();
        if (introStartTime == -1L) {
            introStartTime = now;
        }
        float introElapsed = (now - introStartTime) / 1000.0f;
        float burstTime = -1.0f;
        if (burstTriggered && burstTriggerTime > 0L) {
            burstTime = (now - burstTriggerTime) / 1000.0f;
            if (!introCompleted && burstTime >= 2.8f) {
                introCompleted = true;
            }
        }
        boolean inIntro = !introCompleted;

        float fbW = (float) mc.getFramebuffer().textureWidth;
        float fbH = (float) mc.getFramebuffer().textureHeight;

        // Normalized mouse (-1.0 to 1.0) for 3D parallax tilt
        float mouseNormX = MathHelper.clamp(((float) dA / (float) screenW - 0.5f) * 2.0f, -1.0f, 1.0f);
        float mouseNormY = MathHelper.clamp(((float) dA2 / (float) screenH - 0.5f) * 2.0f, -1.0f, 1.0f);

        // 1. Fullscreen Procedural 3D Scene (Living Lotus Wallpaper + 3D Minimalist Egg + Detonation Shockwave)
        draw.drawMenuShader(matrices, (float) screenW, (float) screenH, fbW, fbH, introElapsed, burstTime, mouseNormX, mouseNormY, introCompleted, openProgress, isLightTheme);

        // 1.1 Atmospheric Floating Dust / Embers (Interactive with mouse repulsion & gentle sway)
        renderAtmosphericDustStatic(matrices, draw, (float) screenW, (float) screenH, (float) dA, (float) dA2, openProgress);

        // Calculate smooth button fade-in during the ascent/docking phase (burstTime 1.2s -> 2.8s)
        float buttonAlpha;
        float buttonSlideY;
        if (inIntro) {
            if (burstTime >= 1.3f) {
                float progress = MathHelper.clamp((burstTime - 1.3f) / 1.5f, 0.0f, 1.0f);
                buttonAlpha = progress * progress * (3.0f - 2.0f * progress) * openProgress;
                buttonSlideY = (1.0f - progress) * 35.0f;
            } else {
                buttonAlpha = 0.0f;
                buttonSlideY = 35.0f;
            }
        } else {
            buttonAlpha = openProgress;
            buttonSlideY = 0.0f;
        }

        float centerX = screenW * 0.5f;

        // 2. Date Header & Hitbox
        float clockFloatY = -unlockProgress * 10.0f;
        renderLiquidGlassClock(matrices, draw, centerX, screenH, clockFloatY, buttonAlpha, delta);

        // Glass config for buttons and sound hud
        GlassConfig btnMediaCfg = getButtonAndMediaConfig();

        // 3. Initial Egg Intro Prompt
        if (inIntro && !burstTriggered && openProgress > 0.01f) {
            renderBurstPrompt(matrices, centerX, screenH, openProgress);
        }

        // 4. Unified Interface: Center Floating Dock & Bottom Media Player (No settings popovers)
        if (buttonAlpha > 0.01f) {
            float playerScale = PLAYER_CONFIG.playerScale;
            float playerH = 34.0f * playerScale;
            float playerY = (screenH - playerH - 16.0f) + buttonSlideY;

            // Dock placed in exact vertical center of screen ("клавиши сингл плеер мульти и тд в центр экрана а медия плеер не трогай")
            float dockH = 42.0f;
            float dockY = (screenH - dockH) * 0.5f + buttonSlideY;

            renderIosFloatingDock(matrices, draw, centerX, dockY, buttonAlpha, dA, dA2, delta, primaryColor);
            renderLockscreenMediaPlayer(matrices, draw, centerX, playerY, buttonAlpha, dA, dA2, getMediaPlayerGlassConfig());
        }

        ScaleUtil.a(context);
    }

    // ==========================================
    // 1. WALLPAPER RENDERING
    // ==========================================
    public static void a(DrawContext context, int width, int height, int mouseX, int mouseY, float scale) {
        float marginX = width * 0.025f;
        float marginY = height * 0.025f;
        parallax[0] += ((MathHelper.clamp((((mouseX / (float) width) - 0.5f) * 2.0f) * marginX, (-marginX) * 0.9f, marginX * 0.9f) - parallax[0]) * 0.03f);
        parallax[1] += ((MathHelper.clamp((((mouseY / (float) height) - 0.5f) * 2.0f) * marginY, (-marginY) * 0.9f, marginY * 0.9f) - parallax[1]) * 0.03f);

        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate(width / 2.0f, height / 2.0f, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate((-width) / 2.0f, (-height) / 2.0f, 0.0f);

        WallpaperItem cur = getCurrentWallpaper();

        // 2. High-quality cover scaling preserving native 16:9 aspect ratio
        float totalTargetW = width + (marginX * 2.0f);
        float totalTargetH = height + (marginY * 2.0f);
        float imgAspect = 16.0f / 9.0f;
        float screenAspect = totalTargetW / totalTargetH;
        float renderW, renderH;
        if (screenAspect > imgAspect) {
            renderW = totalTargetW;
            renderH = totalTargetW / imgAspect;
        } else {
            renderH = totalTargetH;
            renderW = totalTargetH * imgAspect;
        }
        float drawX = (-marginX) + parallax[0] + (totalTargetW - renderW) * 0.5f;
        float drawY = (-marginY) + parallax[1] + (totalTargetH - renderH) * 0.5f;

        Delta.h().d().i().a(matrices, cur.texture, drawX, drawY, renderW, renderH, 0.0f, -1);
        matrices.pop();
    }

    // ==========================================
    // 2. PROCEDURAL 3D EGG LOGO & DATE (REPLACES CLOCK)
    // ==========================================
    private void renderLiquidGlassClock(MatrixStack matrices, Draw2DProcessor draw, float centerX, int screenH, float floatY, float alpha, float delta) {
        float dateY = (screenH * 0.08f) + floatY;

        // Docked logo hitbox (where the egg is positioned in the shader: top center)
        float dockedW = 86.0f;
        float dockedH = 106.0f;
        this.lastClockW = dockedW;
        this.lastClockH = dockedH;
        this.lastClockX = centerX - (dockedW * 0.5f);
        this.lastClockY = dateY + 8.0f;
    }

    // ==========================================
    // 3. LIQUID GLASS MEDIA PLAYER
    // ==========================================
    private void renderLockscreenMediaPlayer(MatrixStack matrices, Draw2DProcessor draw, float centerX, float playerY, float alpha, double mouseX, double mouseY, GlassConfig cfg) {
        if (PLAYER_CONFIG.visibilityMode == 2) {
            // Player is configured as hidden: render subtle compact pill so user can access settings anytime
            float pillW = 86.0f;
            float pillH = 22.0f;
            float px = centerX - (pillW * 0.5f);
            float py = playerY;
            this.lastMediaX = px;
            this.lastMediaY = py;
            this.lastMediaW = pillW;
            this.lastMediaH = pillH;
            this.lastPlayerGearX = px + pillW - 13.0f;
            this.lastPlayerGearY = py + 7.0f;

            draw.a(matrices, px - 1.5f, py + 2.0f, pillW + 3.0f, pillH + 3.0f, 12.0f, ColorUtil.a(0, 0, 0, (int) (90 * alpha)));
            draw.b(matrices, px, py, pillW, pillH, 11.0f, ColorUtil.a(10, 10, 12, (int) (185 * alpha)));
            draw.a(matrices, px, py, pillW, pillH, 11.0f, ColorUtil.a(20, 22, 28, (int) (170 * alpha)));
            draw.a(matrices, px, py, pillW, pillH, 11.0f, 0.75f, ColorUtil.a(255, 255, 255, (int) (38 * alpha)));
            boolean hoverPill = MathUtil.a(mouseX, mouseY, px, py, pillW, pillH);
            int pillTextCol = hoverPill ? -1 : ColorUtil.a(200, 205, 220, (int) (220 * alpha));
            Fonts.b.b(matrices, "🎵 Плеер ⚙", px + (pillW * 0.5f), py + 7.5f, 6.5f, pillTextCol);
            return;
        }

        if (PLAYER_CONFIG.visibilityMode == 1 && !this.hasActiveMedia) {
            this.lastMediaW = 0.0f;
            this.lastMediaH = 0.0f;
            return;
        }

        float scale = PLAYER_CONFIG.playerScale;
        float barW = 236.0f * scale;
        float barH = 34.0f * scale;
        float barR = barH * 0.5f;
        float barX = centerX - (barW * 0.5f);
        float barY = playerY;

        this.lastMediaX = barX;
        this.lastMediaY = barY;
        this.lastMediaW = barW;
        this.lastMediaH = barH;

        // 1. Subtle soft ambient shadow
        draw.a(matrices, barX, barY + 3.0f, barW, barH, barR, ColorUtil.a(0, 0, 0, (int) (85 * alpha)));

        // 2. Frosted Blur Acrylic using Dark Liquid Glass system
        draw.b(matrices, barX, barY, barW, barH, barR, ColorUtil.a(10, 11, 15, (int) (180 * alpha)), alpha);

        // 3. Deep Dark Liquid Glass Body ("нижний плеер в мейн меню все еще белый" -> темный цвет)
        int colGlassTop = ColorUtil.a(20, 22, 28, (int) (160 * alpha));
        int colGlassBtm = ColorUtil.a(12, 13, 17, (int) (200 * alpha));
        draw.a(matrices, barX, barY, barW, barH, new org.joml.Vector4f(barR, barR, barR, barR), colGlassTop, colGlassTop, colGlassBtm, colGlassBtm);

        // 4. Subtle Dark Meniscus Highlight
        float specH = Math.max(2.0f, barH * 0.44f);
        int colSpecTop = ColorUtil.a(255, 255, 255, (int) (10 * alpha));
        int colSpecBtm = ColorUtil.a(255, 255, 255, (int) (0 * alpha));
        draw.a(matrices, barX + 1.5f, barY + 1.0f, barW - 3.0f, specH, new org.joml.Vector4f(barR - 1.0f, barR - 1.0f, 2.0f, 2.0f), colSpecTop, colSpecTop, colSpecBtm, colSpecBtm);

        // 5. Ultra-thin Dark Border Outline (0.75px)
        draw.a(matrices, barX, barY, barW, barH, barR, 0.75f, ColorUtil.a(255, 255, 255, (int) (18 * alpha)));

        // ==========================================
        // CENTER: BLACK BADGE WITH GRADIENT (AS IN HUD)
        // "а плашка с медия плеером как в худе с градиентом черная"
        // ==========================================
        float badgeW = 144.0f * scale;
        float badgeH = 26.0f * scale;
        float badgeR = 7.5f * scale;
        float badgeX = barX + barW - badgeW - (4.0f * scale);
        float badgeY = barY + ((barH - badgeH) * 0.5f);

        this.lastBadgeX = badgeX;
        this.lastBadgeY = badgeY;
        this.lastBadgeW = badgeW;
        this.lastBadgeH = badgeH;

        // Badge Ambient Shadow
        draw.a(matrices, badgeX - 1.0f, badgeY + 1.5f, badgeW + 2.0f, badgeH + 2.0f, badgeR + 1.0f, ColorUtil.a(0, 0, 0, (int) (85 * alpha)));

        // Opaque Crisp Black Capsule (Like HUD Dynamic Island: 0xFF0A0A0E)
        draw.a(matrices, badgeX, badgeY, badgeW, badgeH, badgeR, ColorUtil.a(0x0A, 0x0A, 0x0E, (int) (255 * alpha)));

        // Dynamic Fluid Ambient Gradient matching HUD
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

        float t = (float) (System.currentTimeMillis() % 1000000L) * 0.002f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t);
        float gradAlpha = (0.35f + pulse * 0.12f) * alpha;

        int colTL = ColorUtil.a(0, 0, 0, 0);
        int colTR = ColorUtil.a(accentR, accentG, accentB, (int) (gradAlpha * 0.40f * 255.0f));
        int colBL = ColorUtil.a(accentR, accentG, accentB, (int) (gradAlpha * 0.25f * 255.0f));
        int colBR = ColorUtil.a(accentR, accentG, accentB, (int) (gradAlpha * 255.0f));

        draw.a(matrices, badgeX, badgeY, badgeW, badgeH, new org.joml.Vector4f(badgeR, badgeR, badgeR, badgeR), colTL, colTR, colBL, colBR);

        // Badge Outline
        draw.a(matrices, badgeX, badgeY, badgeW, badgeH, badgeR, 0.7f, ColorUtil.a(255, 255, 255, (int) (26 * alpha)));

        // Cover Art inside Badge
        float artSize = badgeH - (6.0f * scale);
        float artX = badgeX + (3.0f * scale);
        float artY = badgeY + (3.0f * scale);
        float artRadius = 4.0f * scale;

        if (this.hasCoverArt) {
            draw.a(matrices, LOCK_COVER_ID, artX, artY, artSize, artSize, artRadius, ColorUtil.a(255, 255, 255, (int) (255 * alpha)));
            draw.a(matrices, artX, artY, artSize, artSize, artRadius, 0.5f, ColorUtil.a(255, 255, 255, (int) (25 * alpha)));
        } else {
            int phBg = ColorUtil.a(24, 22, 32, (int) (230 * alpha));
            draw.a(matrices, artX, artY, artSize, artSize, artRadius, phBg);
            draw.a(matrices, artX, artY, artSize, artSize, artRadius, 0.5f, ColorUtil.a(255, 255, 255, (int) (18 * alpha)));
            Fonts.b.b(matrices, "♪", artX + (artSize * 0.5f), artY + (artSize * 0.5f) - (3.5f * scale), 7.5f * scale, ColorUtil.a(180, 185, 205, (int) (200 * alpha)));
        }

        // Track Text inside Badge
        float textX = artX + artSize + (5.0f * scale);
        float maxTextW = (badgeX + badgeW - (8.0f * scale)) - textX;

        String displayTitle = (this.hasActiveMedia && !this.currentTitle.isEmpty()) ? this.currentTitle : "Музыка не играет";
        String displayArtist = (this.hasActiveMedia && !this.currentArtist.isEmpty()) ? this.currentArtist : "Включите трек в системе";

        int titleColor = ColorUtil.a(255, 255, 255, (int) (245 * alpha));
        int artistColor = ColorUtil.a(160, 165, 180, (int) (200 * alpha));

        float titleW = Fonts.d.a(displayTitle, 6.5f * scale);
        float titleY = badgeY + (3.5f * scale);
        float artistY = badgeY + (12.0f * scale);

        if (PLAYER_CONFIG.scrollMarquee && titleW > maxTextW) {
            long currentNow = System.currentTimeMillis();
            if (this.lastScrollTime == 0L) this.lastScrollTime = currentNow;
            float dt = (currentNow - this.lastScrollTime) / 1000.0f;
            this.lastScrollTime = currentNow;
            this.textScrollOffset += dt * 16.0f;
            float totalLen = titleW + (20.0f * scale);
            if (this.textScrollOffset > totalLen) this.textScrollOffset = 0.0f;

            delta.render.ScissorUtil.a(matrices, textX, titleY - (1.0f * scale), maxTextW, 9.0f * scale);
            Fonts.d.a(matrices, displayTitle, textX - this.textScrollOffset, titleY, 6.5f * scale, titleColor);
            Fonts.d.a(matrices, displayTitle, textX - this.textScrollOffset + totalLen, titleY, 6.5f * scale, titleColor);
            delta.render.ScissorUtil.a(matrices);
        } else {
            delta.render.ScissorUtil.a(matrices, textX, titleY - (1.0f * scale), maxTextW, 9.0f * scale);
            Fonts.d.a(matrices, displayTitle, textX, titleY, 6.5f * scale, titleColor);
            delta.render.ScissorUtil.a(matrices);
        }

        delta.render.ScissorUtil.a(matrices, textX, artistY - (1.0f * scale), maxTextW, 8.0f * scale);
        Fonts.b.a(matrices, displayArtist, textX, artistY, 5.25f * scale, artistColor);
        delta.render.ScissorUtil.a(matrices);

        // Progress Bar across bottom edge of badge (as seen in photo)
        float progress = 0.0f;
        if (this.hasActiveMedia && this.currentDurationMs > 0L) {
            progress = MathHelper.clamp((float) this.currentPositionMs / (float) this.currentDurationMs, 0.0f, 1.0f);
        }

        float pBarX = badgeX + (3.0f * scale);
        float pBarW = badgeW - (6.0f * scale);
        float pBarH = 1.3f * scale;
        float pBarY = badgeY + badgeH - (2.0f * scale);

        this.lastBarX = pBarX;
        this.lastBarY = pBarY - (2.5f * scale); // expanded click zone
        this.lastBarW = pBarW;
        this.lastBarH = 6.0f * scale;

        int unplayedBar = ColorUtil.a(255, 255, 255, (int) (35 * alpha));
        int playedBar = ColorUtil.a(255, 255, 255, (int) (235 * alpha));

        draw.a(matrices, pBarX, pBarY, pBarW, pBarH, 0.65f * scale, unplayedBar);
        float filledW = pBarW * progress;
        if (filledW > 0.5f) {
            draw.a(matrices, pBarX, pBarY, filledW, pBarH, 0.65f * scale, playedBar);
        }

        // ==========================================
        // LEFT CONTROLS: << , || / ▶ , >>
        // ==========================================
        float ctrlCY = barY + (barH * 0.5f);
        this.lastCtrlY = ctrlCY - (8.0f * scale);

        float leftAreaCenter = barX + ((badgeX - barX) * 0.5f);
        float btnSpacing = 20.0f * scale;

        float prevBtnX = leftAreaCenter - btnSpacing;
        float playBtnX = leftAreaCenter;
        float nextBtnX = leftAreaCenter + btnSpacing;

        this.lastPrevBtnX = prevBtnX;
        this.lastPlayBtnX = playBtnX;
        this.lastNextBtnX = nextBtnX;

        boolean hoverPrev = MathUtil.a(mouseX, mouseY, prevBtnX - (8.0f * scale), ctrlCY - (8.0f * scale), 16.0f * scale, 16.0f * scale);
        boolean hoverPlay = MathUtil.a(mouseX, mouseY, playBtnX - (8.0f * scale), ctrlCY - (8.0f * scale), 16.0f * scale, 16.0f * scale);
        boolean hoverNext = MathUtil.a(mouseX, mouseY, nextBtnX - (8.0f * scale), ctrlCY - (8.0f * scale), 16.0f * scale, 16.0f * scale);

        int baseBtnCol = ColorUtil.a(215, 220, 235, (int) (210 * alpha));
        int hoverBtnCol = ColorUtil.a(255, 255, 255, (int) (255 * alpha));

        int prevCol = hoverPrev ? hoverBtnCol : baseBtnCol;
        int playCol = hoverPlay ? hoverBtnCol : baseBtnCol;
        int nextCol = hoverNext ? hoverBtnCol : baseBtnCol;

        // Skip Previous (<<)
        drawLeftTriangle(draw, matrices, prevBtnX - (3.2f * scale), ctrlCY, 3.2f * scale, 6.5f * scale, prevCol);
        drawLeftTriangle(draw, matrices, prevBtnX + (0.5f * scale), ctrlCY, 3.2f * scale, 6.5f * scale, prevCol);

        // Play / Pause (|| or ▶)
        if (this.isPlaying) {
            float pW = 2.0f * scale;
            float pH = 8.5f * scale;
            float pGap = 2.4f * scale;
            float pY = ctrlCY - (pH * 0.5f);
            draw.a(matrices, playBtnX - pW - (pGap * 0.5f), pY, pW, pH, 1.0f * scale, playCol);
            draw.a(matrices, playBtnX + (pGap * 0.5f), pY, pW, pH, 1.0f * scale, playCol);
        } else {
            drawRightTriangle(draw, matrices, playBtnX - (2.8f * scale), ctrlCY, 6.5f * scale, 8.5f * scale, playCol);
        }

        // Skip Next (>>)
        drawRightTriangle(draw, matrices, nextBtnX - (3.2f * scale), ctrlCY, 3.2f * scale, 6.5f * scale, nextCol);
        drawRightTriangle(draw, matrices, nextBtnX + (0.5f * scale), ctrlCY, 3.2f * scale, 6.5f * scale, nextCol);
    }

    private void renderEmblemIcon(Draw2DProcessor draw, MatrixStack matrices, float cx, float cy, float radius, int color) {
        draw.a(matrices, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, radius, 0.85f, color);
        float innerR = radius * 0.45f;
        draw.a(matrices, cx - innerR, cy - innerR, innerR * 2.0f, innerR * 2.0f, innerR, color);
        draw.a(matrices, cx - 0.5f, cy - radius + 1.0f, 1.0f, 1.8f, 0.5f, color);
        draw.a(matrices, cx - radius * 0.7f, cy + radius * 0.35f, 1.8f, 1.0f, 0.5f, color);
        draw.a(matrices, cx + radius * 0.7f - 1.8f, cy + radius * 0.35f, 1.8f, 1.0f, 0.5f, color);
    }

    private void renderSlidersIcon(Draw2DProcessor draw, MatrixStack matrices, float cx, float cy, float w, int color) {
        float halfW = w * 0.5f;
        float lineH = 1.0f;
        float pipW = 2.4f;
        float pipH = 3.6f;
        float pipR = 1.0f;

        float topY = cy - 2.8f;
        draw.a(matrices, cx - halfW, topY, w, lineH, 0.5f, color);
        draw.a(matrices, cx + 1.2f, topY - 1.3f, pipW, pipH, pipR, color);

        float btmY = cy + 2.8f;
        draw.a(matrices, cx - halfW, btmY, w, lineH, 0.5f, color);
        draw.a(matrices, cx - halfW + 1.5f, btmY - 1.3f, pipW, pipH, pipR, color);
    }

    private void renderSpeakerIcon(Draw2DProcessor draw, MatrixStack matrices, float cx, float cy, float size, int color) {
        float boxW = 2.4f;
        float boxH = 3.8f;
        draw.a(matrices, cx - 4.5f, cy - (boxH * 0.5f), boxW, boxH, 0.6f, color);
        drawRightTriangle(draw, matrices, cx - 2.6f, cy, 3.8f, 7.0f, color);

        if (PLAYER_CONFIG.volume > 0.05f) {
            draw.a(matrices, cx + 2.0f, cy - 2.5f, 1.0f, 5.0f, 0.5f, color);
            if (PLAYER_CONFIG.volume > 0.5f) {
                draw.a(matrices, cx + 4.2f, cy - 4.0f, 1.0f, 8.0f, 0.5f, color);
            }
        } else {
            draw.a(matrices, cx + 1.5f, cy - 3.5f, 1.0f, 7.0f, 0.5f, color);
            draw.a(matrices, cx + 1.0f, cy, 2.0f, 1.0f, 0.5f, color);
        }
    }

    // ==========================================
    // 3.1. PLAYER SETTINGS CONTEXT MENU
    // ==========================================
    private void renderPlayerSettingsMenu(MatrixStack matrices, Draw2DProcessor draw, float centerX, int screenH, float progress, double mouseX, double mouseY) {
        float menuW = 208.0f;
        float menuH = 224.0f;
        float menuX = centerX - (menuW * 0.5f);
        float menuY = Math.max(16.0f, screenH - menuH - 58.0f);

        this.lastPlayerMenuX = menuX;
        this.lastPlayerMenuY = menuY;
        this.lastPlayerMenuW = menuW;
        this.lastPlayerMenuH = menuH;

        float alpha = progress;

        // Dim Backdrop
        draw.a(matrices, 0.0f, 0.0f, (float) Interface.aM_.getWindow().getScaledWidth(), (float) Interface.aM_.getWindow().getScaledHeight(), 0.0f, ColorUtil.a(0, 0, 0, (int) (40 * alpha)));

        // Liquid Glass Popover Card
        drawLiquidGlass(draw, matrices, menuX, menuY, menuW, menuH, 14.0f, alpha, false, GLASS_CONFIG);

        // Header
        int textCol = ColorUtil.a(215, 222, 235, (int) (235 * alpha));
        Fonts.d.a(matrices, "Настройки плеера", menuX + 12.0f, menuY + 9.0f, 7.5f, textCol);
        boolean hoverClose = MathUtil.a(mouseX, mouseY, menuX + menuW - 20.0f, menuY + 7.0f, 16.0f, 16.0f);
        int closeCol = hoverClose ? -1 : ColorUtil.a(150, 155, 170, (int) (180 * alpha));
        Fonts.b.b(matrices, "✕", menuX + menuW - 12.0f, menuY + 9.0f, 6.5f, closeCol);

        // Divider
        draw.a(matrices, menuX + 8.0f, menuY + 22.0f, menuW - 16.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (18 * alpha)));

        float curRowY = menuY + 27.0f;
        int primary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();

        // 1. Visibility Mode Pills (Всегда | С музыкой | Скрыт)
        Fonts.b.a(matrices, "Отображение", menuX + 10.0f, curRowY + 4.0f, 6.5f, ColorUtil.a(165, 175, 192, (int) (220 * alpha)));
        float pillW = 38.0f;
        float pillH = 14.0f;
        String[] modes = {"Всегда", "Активен", "Скрыт"};
        for (int i = 0; i < 3; i++) {
            float px = menuX + menuW - 10.0f - ((3 - i) * (pillW + 3.0f)) + 3.0f;
            boolean active = PLAYER_CONFIG.visibilityMode == i;
            boolean hover = MathUtil.a(mouseX, mouseY, px, curRowY, pillW, pillH);
            int bg = active ? ColorUtil.a(primary, (int) (220 * alpha)) : (hover ? ColorUtil.a(255, 255, 255, (int) (30 * alpha)) : ColorUtil.a(255, 255, 255, (int) (16 * alpha)));
            draw.a(matrices, px, curRowY, pillW, pillH, 4.0f, bg);
            int tc = active ? -1 : (hover ? -1 : ColorUtil.a(175, 182, 198, (int) (215 * alpha)));
            Fonts.b.b(matrices, modes[i], px + (pillW * 0.5f), curRowY + 3.5f, 5.5f, tc);
        }

        // 2. Marquee Toggle
        curRowY += 20.0f;
        Fonts.b.a(matrices, "Бегущая строка", menuX + 10.0f, curRowY + 3.5f, 6.5f, ColorUtil.a(165, 175, 192, (int) (220 * alpha)));
        float tglW = 30.0f;
        float tglH = 13.0f;
        float tglX = menuX + menuW - 10.0f - tglW;
        boolean tglActive = PLAYER_CONFIG.scrollMarquee;
        int tglBg = tglActive ? ColorUtil.a(primary, (int) (230 * alpha)) : ColorUtil.a(255, 255, 255, (int) (22 * alpha));
        draw.a(matrices, tglX, curRowY, tglW, tglH, 6.5f, tglBg);
        float knobX = tglActive ? (tglX + tglW - 11.0f) : (tglX + 2.0f);
        draw.a(matrices, knobX, curRowY + 1.5f, 10.0f, 10.0f, 5.0f, ColorUtil.a(230, 235, 245, (int) (245 * alpha)));

        // 3. Timecodes Toggle
        curRowY += 18.0f;
        Fonts.b.a(matrices, "Таймкоды времени", menuX + 10.0f, curRowY + 3.5f, 6.5f, ColorUtil.a(165, 175, 192, (int) (220 * alpha)));
        boolean tcActive = PLAYER_CONFIG.showTimeCodes;
        int tcBg = tcActive ? ColorUtil.a(primary, (int) (230 * alpha)) : ColorUtil.a(255, 255, 255, (int) (22 * alpha));
        draw.a(matrices, tglX, curRowY, tglW, tglH, 6.5f, tcBg);
        float tcKnobX = tcActive ? (tglX + tglW - 11.0f) : (tglX + 2.0f);
        draw.a(matrices, tcKnobX, curRowY + 1.5f, 10.0f, 10.0f, 5.0f, ColorUtil.a(230, 235, 245, (int) (245 * alpha)));

        // 4. Sliders (Volume, Opacity, Blur)
        curRowY += 19.0f;
        renderPlayerSliderRow(matrices, draw, menuX, curRowY, menuW, "Громкость", (int) (PLAYER_CONFIG.volume * 100.0f) + "%", PLAYER_CONFIG.volume, alpha, primary);

        curRowY += 23.0f;
        renderPlayerSliderRow(matrices, draw, menuX, curRowY, menuW, "Прозрачность", (int) (PLAYER_CONFIG.cardOpacity * 100.0f) + "%", (PLAYER_CONFIG.cardOpacity - 0.2f) / 0.8f, alpha, primary);

        curRowY += 23.0f;
        renderPlayerSliderRow(matrices, draw, menuX, curRowY, menuW, "Размытие стекла", (int) (PLAYER_CONFIG.cardBlur * 100.0f) + "%", PLAYER_CONFIG.cardBlur, alpha, primary);

        // 5. Scale Pills (85%, 100%, 115%)
        curRowY += 24.0f;
        Fonts.b.a(matrices, "Размер плеера", menuX + 10.0f, curRowY + 4.0f, 6.5f, ColorUtil.a(200, 205, 220, (int) (225 * alpha)));
        float[] scales = {0.85f, 1.0f, 1.15f};
        String[] scaleLabels = {"85%", "100%", "115%"};
        float scPillW = 32.0f;
        for (int i = 0; i < 3; i++) {
            float px = menuX + menuW - 10.0f - ((3 - i) * (scPillW + 3.0f)) + 3.0f;
            boolean active = Math.abs(PLAYER_CONFIG.playerScale - scales[i]) < 0.05f;
            boolean hover = MathUtil.a(mouseX, mouseY, px, curRowY, scPillW, pillH);
            int bg = active ? ColorUtil.a(primary, (int) (220 * alpha)) : (hover ? ColorUtil.a(255, 255, 255, (int) (40 * alpha)) : ColorUtil.a(255, 255, 255, (int) (20 * alpha)));
            draw.a(matrices, px, curRowY, scPillW, pillH, 4.0f, bg);
            int tc = active ? -1 : (hover ? -1 : ColorUtil.a(190, 195, 210, (int) (220 * alpha)));
            Fonts.b.b(matrices, scaleLabels[i], px + (scPillW * 0.5f), curRowY + 3.5f, 5.5f, tc);
        }

        // 6. Reset Defaults Button
        curRowY += 23.0f;
        boolean hoverReset = MathUtil.a(mouseX, mouseY, menuX + 10.0f, curRowY, menuW - 20.0f, 16.0f);
        int resetBg = hoverReset ? ColorUtil.a(255, 60, 60, (int) (70 * alpha)) : ColorUtil.a(255, 255, 255, (int) (18 * alpha));
        draw.a(matrices, menuX + 10.0f, curRowY, menuW - 20.0f, 16.0f, 5.0f, resetBg);
        int resetCol = hoverReset ? ColorUtil.a(255, 120, 120, (int) (255 * alpha)) : ColorUtil.a(190, 195, 210, (int) (210 * alpha));
        Fonts.b.b(matrices, "↺ Сбросить настройки", menuX + (menuW * 0.5f), curRowY + 4.5f, 6.0f, resetCol);
    }

    private void renderPlayerSliderRow(MatrixStack matrices, Draw2DProcessor draw, float menuX, float y, float menuW, String title, String valStr, float normVal, float alpha, int accentCol) {
        Fonts.b.a(matrices, title, menuX + 10.0f, y + 1.0f, 6.5f, ColorUtil.a(165, 175, 192, (int) (220 * alpha)));
        Fonts.b.b(matrices, valStr, menuX + menuW - 10.0f, y + 1.0f, 6.0f, ColorUtil.a(110, 165, 255, (int) (230 * alpha)));

        float sliderX = menuX + 10.0f;
        float sliderY = y + 12.0f;
        float sliderW = menuW - 20.0f;
        float sliderH = 3.5f;

        draw.a(matrices, sliderX, sliderY, sliderW, sliderH, 1.75f, ColorUtil.a(255, 255, 255, (int) (22 * alpha)));
        float filledW = sliderW * MathHelper.clamp(normVal, 0.0f, 1.0f);
        if (filledW > 0.0f) {
            draw.a(matrices, sliderX, sliderY, filledW, sliderH, 1.75f, ColorUtil.a(85, 150, 255, (int) (230 * alpha)));
        }
        float thumbX = sliderX + filledW;
        draw.a(matrices, thumbX - 3.5f, sliderY - 1.75f, 7.0f, 7.0f, 3.5f, ColorUtil.a(230, 235, 245, (int) (245 * alpha)));
    }

    private void renderBurstPrompt(MatrixStack matrices, float centerX, int screenH, float alpha) {
        float bob = (float) Math.sin(System.currentTimeMillis() * 0.004) * 2.5f;
        float hintY = screenH - 34.0f + bob;

        String hintText = "Нажмите Пробел или нажмите на яйцо";
        int shadowColor = ColorUtil.a(0, 0, 0, (int) (130 * alpha));
        int textColor = ColorUtil.a(255, 255, 255, (int) (235 * alpha));

        Fonts.b.b(matrices, "✦", centerX, hintY - 9.5f, 8.5f, shadowColor);
        Fonts.b.b(matrices, "✦", centerX, hintY - 10.5f, 8.5f, textColor);

        Fonts.b.b(matrices, hintText, centerX, hintY + 1.0f, 7.0f, shadowColor);
        Fonts.b.b(matrices, hintText, centerX, hintY, 7.0f, textColor);
    }

    private void renderUnlockHint(MatrixStack matrices, float centerX, int screenH, float alpha) {
        float bob = (float) Math.sin(System.currentTimeMillis() * 0.0035) * 2.2f;
        float hintY = screenH - 62.0f + bob;

        String hintText = "Нажмите Пробел для разблокировки";
        int shadowColor = ColorUtil.a(0, 0, 0, (int) (100 * alpha));
        int textColor = ColorUtil.a(255, 255, 255, (int) (215 * alpha));

        Fonts.b.b(matrices, "⌃", centerX, hintY - 8.5f, 7.5f, shadowColor);
        Fonts.b.b(matrices, "⌃", centerX, hintY - 9.5f, 7.5f, textColor);

        Fonts.b.b(matrices, hintText, centerX, hintY + 1.0f, 6.75f, shadowColor);
        Fonts.b.b(matrices, hintText, centerX, hintY, 6.75f, textColor);
    }

    // ==========================================
    // 4. IOS 26/27 LIQUID GLASS FLOATING DOCK
    // ==========================================
    private void renderIosFloatingDock(MatrixStack matrices, Draw2DProcessor draw, float centerX, float dockY, float alpha, double mouseX, double mouseY, float delta, int primaryColor) {
        if (alpha <= 0.01f) return;

        int itemCount = 5;
        float itemW = 48.0f;
        float dockPad = 6.0f;
        float dockW = (itemCount * itemW) + (dockPad * 2.0f); // 252.0f
        float dockH = 42.0f;
        float dockRadius = dockH * 0.5f; // 21.0f
        float dockX = centerX - (dockW * 0.5f);

        this.lastDockX = dockX;
        this.lastDockY = dockY;
        this.lastDockW = dockW;
        this.lastDockH = dockH;
        this.lastItemW = itemW;
        this.lastDockPad = dockPad;

        float minCenterX = dockX + dockPad + (itemW * 0.5f);
        float maxCenterX = dockX + dockW - dockPad - (itemW * 0.5f);

        // Initialize pill position on first render
        if (this.dockPillCenterX < 0.0f) {
            this.dockPillCenterX = minCenterX + (this.selectedDockIndex * itemW);
            this.targetPillCenterX = this.dockPillCenterX;
        }

        // Mouse hit test & target positioning with strict iOS rubber-band resistance
        if (this.isDraggingDock) {
            float rawMouseX = (float) mouseX;
            if (rawMouseX < minCenterX) {
                float pull = minCenterX - rawMouseX;
                float maxOver = 5.0f; // strictly slightly over boundary!
                float rubber = (pull * maxOver) / (pull + 20.0f);
                this.targetPillCenterX = minCenterX - rubber;
            } else if (rawMouseX > maxCenterX) {
                float pull = rawMouseX - maxCenterX;
                float maxOver = 5.0f; // strictly slightly over boundary!
                float rubber = (pull * maxOver) / (pull + 20.0f);
                this.targetPillCenterX = maxCenterX + rubber;
            } else {
                this.targetPillCenterX = rawMouseX;
            }
            int closest = Math.round((this.targetPillCenterX - minCenterX) / itemW);
            this.hoveredDockIndex = MathHelper.clamp(closest, 0, itemCount - 1);
        } else {
            boolean inDock = MathUtil.a(mouseX, mouseY, dockX, dockY, dockW, dockH);
            if (inDock) {
                int hSlot = (int) ((mouseX - (dockX + dockPad)) / itemW);
                this.hoveredDockIndex = MathHelper.clamp(hSlot, 0, itemCount - 1);
            } else {
                this.hoveredDockIndex = -1;
            }
            this.targetPillCenterX = minCenterX + (this.selectedDockIndex * itemW);
        }

        // Fluid Jelly Spring Simulation
        float dt = MathHelper.clamp(delta, 0.2f, 2.0f);
        float dx = this.targetPillCenterX - this.dockPillCenterX;
        float springK = this.isDraggingDock ? 0.38f : 0.24f;
        float friction = this.isDraggingDock ? 0.65f : 0.72f;

        this.dockPillVelocity += dx * springK * dt;
        this.dockPillVelocity *= (float) Math.pow(friction, dt);
        this.dockPillCenterX += this.dockPillVelocity * dt;

        // Dynamic fluid stretch & squash physics (limited so it stays sleek and slightly elastic)
        float dynamicStretch = Math.min(8.0f, (Math.abs(this.dockPillVelocity) * 1.2f) + (Math.abs(dx) * 0.15f));
        this.dockPillStretch += (dynamicStretch - this.dockPillStretch) * 0.30f * dt;

        float basePillW = 44.0f;
        float basePillH = 32.0f;
        float curPillW = basePillW + this.dockPillStretch;
        float squashY = this.dockPillStretch * 0.16f;
        float curPillH = Math.max(26.0f, basePillH - squashY);
        float curPillR = curPillH * 0.5f;

        // Leading droplet offset strictly bounded so it never flies out of dock
        float leadOffset = MathHelper.clamp(this.dockPillVelocity * 0.25f, -2.5f, 2.5f);
        float drawPillCenterX = MathHelper.clamp(this.dockPillCenterX + leadOffset, minCenterX - 4.5f, maxCenterX + 4.5f);
        float pillX = drawPillCenterX - (curPillW * 0.5f);
        float pillY = dockY + ((dockH - curPillH) * 0.5f);

        // 1. Sleek Minimalist Dock Container: Deep Dark Matte Glass Material ("кнопки сделай темного цвета")
        draw.b(matrices, dockX, dockY, dockW, dockH, dockRadius, ColorUtil.a(10, 11, 15, (int) (180 * alpha)), alpha);
        int colGlassTop = ColorUtil.a(20, 22, 28, (int) (160 * alpha));
        int colGlassBtm = ColorUtil.a(12, 13, 17, (int) (200 * alpha));
        draw.a(matrices, dockX, dockY, dockW, dockH, new org.joml.Vector4f(dockRadius, dockRadius, dockRadius, dockRadius), colGlassTop, colGlassTop, colGlassBtm, colGlassBtm);
        draw.a(matrices, dockX, dockY, dockW, dockH, dockRadius, 0.75f, ColorUtil.a(255, 255, 255, (int) (18 * alpha)));

        // 2. Active Selection Flexible Jelly Pill: Dark Charcoal / Obsidian Surface
        // Acrylic Frosted Blur
        draw.b(matrices, pillX, pillY, curPillW, curPillH, curPillR, ColorUtil.a(14, 15, 20, (int) (190 * alpha)), alpha);

        // Dark gradient surface for active button
        int pillGlassTop = ColorUtil.a(40, 44, 54, (int) (140 * alpha));
        int pillGlassBtm = ColorUtil.a(24, 26, 32, (int) (170 * alpha));
        draw.a(matrices, pillX, pillY, curPillW, curPillH, new org.joml.Vector4f(curPillR, curPillR, curPillR, curPillR), pillGlassTop, pillGlassTop, pillGlassBtm, pillGlassBtm);

        // Subtle dark rim outline
        draw.a(matrices, pillX, pillY, curPillW, curPillH, curPillR, 0.75f, ColorUtil.a(255, 255, 255, (int) (28 * alpha)));

        if (primaryColor != -1) {
            draw.a(matrices, pillX, pillY, curPillW, curPillH, curPillR, ColorUtil.a(primaryColor, (int) (10 * alpha)));
        }

        // 7. Render 5 Dock Icons (Singleplayer, Multiplayer, Accounts, Settings, Quit)
        for (int i = 0; i < itemCount; i++) {
            float slotX = minCenterX + (i * itemW);
            float slotY = dockY + (dockH * 0.5f);
            float dist = Math.abs(drawPillCenterX - slotX);
            float activeFactor = MathHelper.clamp(1.0f - (dist / (itemW * 0.65f)), 0.0f, 1.0f);
            boolean isSlotHovered = (this.hoveredDockIndex == i);

            int inactiveCol = ColorUtil.a(140, 146, 160, (int) (170 * alpha));
            int hoverCol = ColorUtil.a(210, 215, 228, (int) (230 * alpha));
            int activeCol = ColorUtil.a(245, 248, 255, (int) (255 * alpha));
            int baseCol = isSlotHovered ? hoverCol : inactiveCol;
            int iconColor = ColorUtil.a(baseCol, activeCol, activeFactor);

            matrices.push();
            float iconScale = isSlotHovered ? 1.10f : (1.0f + 0.05f * activeFactor);
            float hoverLift = isSlotHovered ? -1.5f : 0.0f;
            matrices.translate(slotX, slotY + hoverLift, 0.0f);
            matrices.scale(iconScale, iconScale, 1.0f);
            matrices.translate(-slotX, -slotY, 0.0f);
            renderDockIcon(draw, matrices, i, slotX, slotY, iconColor, activeFactor, alpha);
            matrices.pop();
        }

        // 8. Dynamic Floating Tooltip Island Above Dock
        float targetTipAlpha = (this.hoveredDockIndex >= 0 || this.isDraggingDock) ? 1.0f : 0.0f;
        this.dockTooltipAlpha += (targetTipAlpha - this.dockTooltipAlpha) * 0.22f * dt;
        if (this.dockTooltipAlpha > 0.01f) {
            int tipIndex = (this.hoveredDockIndex >= 0) ? this.hoveredDockIndex : this.selectedDockIndex;
            String[] labels = {"Одиночная игра", "Сетевая игра", "Аккаунты", "Настройки", "Выход из игры"};
            String tipText = labels[MathHelper.clamp(tipIndex, 0, 4)];

            float tipW = Fonts.b.a(tipText, 6.0f) + 14.0f;
            float tipH = 16.0f;
            float tipX = MathHelper.clamp(drawPillCenterX - (tipW * 0.5f), 10.0f, (float) Interface.aM_.getWindow().getScaledWidth() - tipW - 10.0f);
            float tipY = dockY - tipH - 7.0f;
            float tipA = alpha * this.dockTooltipAlpha;

            draw.a(matrices, tipX - 1.0f, tipY + 1.5f, tipW + 2.0f, tipH + 2.0f, tipH * 0.5f + 1.0f, ColorUtil.a(0, 0, 0, (int) (75 * tipA)));
            draw.b(matrices, tipX, tipY, tipW, tipH, tipH * 0.5f, ColorUtil.a(12, 16, 24, (int) (180 * tipA)));
            draw.a(matrices, tipX, tipY, tipW, tipH, tipH * 0.5f, ColorUtil.a(16, 20, 30, (int) (200 * tipA)));
            draw.a(matrices, tipX, tipY, tipW, tipH, tipH * 0.5f, 0.75f, ColorUtil.a(255, 255, 255, (int) (35 * tipA)));

            Fonts.b.b(matrices, tipText, tipX + (tipW * 0.5f), tipY + 3.8f, 6.0f, ColorUtil.a(255, 255, 255, (int) (240 * tipA)));
        }
    }

    private void renderDockIcon(Draw2DProcessor draw, MatrixStack matrices, int index, float cx, float cy, int color, float activeFactor, float alpha) {
        switch (index) {
            case 0 -> {
                // Singleplayer (Single person silhouette instead of house: "иконку одиночный игры сделай 1 человека а не домик")
                // Head (circle)
                draw.a(matrices, cx - 2.8f, cy - 6.5f, 5.6f, 5.6f, 2.8f, color);
                // Body / Shoulders (curved torso)
                draw.a(matrices, cx - 5.5f, cy + 0.2f, 11.0f, 6.2f, 2.8f, color);
            }
            case 1 -> {
                // Multiplayer (Users)
                int dimCol = ColorUtil.a(color, 0.60f);
                draw.a(matrices, cx + 1.5f, cy - 6.0f, 5.2f, 5.2f, 2.6f, dimCol);
                draw.a(matrices, cx - 0.5f, cy + 0.5f, 9.5f, 5.0f, 2.2f, dimCol);

                draw.a(matrices, cx - 5.5f, cy - 6.8f, 5.8f, 5.8f, 2.9f, color);
                draw.a(matrices, cx - 8.5f, cy - 0.2f, 11.5f, 6.0f, 2.6f, color);
            }
            case 2 -> {
                // Accounts (ID Badge)
                draw.a(matrices, cx - 7.5f, cy - 6.0f, 15.0f, 12.0f, 2.2f, color);
                int innerDark = ColorUtil.a(18, 22, 32, (int) (225 * alpha));
                draw.a(matrices, cx - 5.2f, cy - 3.8f, 3.8f, 3.8f, 1.2f, innerDark);
                draw.a(matrices, cx + 0.2f, cy - 3.2f, 5.0f, 1.2f, 0.6f, innerDark);
                draw.a(matrices, cx + 0.2f, cy - 1.0f, 3.5f, 1.2f, 0.6f, innerDark);
                draw.a(matrices, cx - 5.2f, cy + 2.0f, 10.4f, 1.2f, 0.6f, innerDark);
            }
            case 3 -> {
                // Settings (Apple Settings Gear from Screenshot 2026-10-12 112051.png without background)
                float gearSz = 14.5f;
                draw.a(matrices, SETTINGS_GEAR_ICON, cx - (gearSz * 0.5f), cy - (gearSz * 0.5f), gearSz, gearSz, 0.0f, color);
            }
            case 4 -> {
                // Quit (Exit door & arrow)
                draw.a(matrices, cx - 7.0f, cy - 6.2f, 8.0f, 12.4f, 1.8f, color);
                int innerDoor = ColorUtil.a(18, 22, 32, (int) (225 * alpha));
                draw.a(matrices, cx - 5.5f, cy - 4.7f, 5.0f, 9.4f, 1.0f, innerDoor);

                draw.a(matrices, cx - 0.5f, cy - 0.85f, 6.5f, 1.7f, 0.85f, color);
                drawRightTriangle(draw, matrices, cx + 3.8f, cy, 3.5f, 5.5f, color);
            }
        }
    }

    private void renderHomeButton(MatrixStack matrices, Draw2DProcessor draw, int screenH, float alpha, double mouseX, double mouseY, float delta, GlassConfig cfg) {
        float homeX = 24.0f;
        float homeY = screenH - 46.0f;
        float baseR = 15.0f;
        float hcx = homeX + baseR;
        float hcy = homeY + baseR;

        boolean hovered = Math.hypot(mouseX - hcx, mouseY - hcy) <= baseR;
        this.homeHoverAnim.a(hovered);
        this.homeHoverAnim.a(0.0f, 1.0f, 0.25f, EasingList.g, delta);
        float hVal = this.homeHoverAnim.c();

        float curR = baseR * (1.0f + (0.10f * hVal));

        // Ambient elevation shadow
        draw.a(matrices, hcx - curR - 1.0f, hcy - curR + 2.0f, (curR + 1.0f) * 2.0f, (curR + 1.0f) * 2.0f, curR + 1.0f, ColorUtil.a(0, 0, 0, (int) (75 * alpha)));

        // Deep obsidian circle
        int btnBg = ColorUtil.a(14, 16, 23, (int) (225 * alpha));
        draw.a(matrices, hcx - curR, hcy - curR, curR * 2.0f, curR * 2.0f, curR, btnBg);

        // Sleek frosted border
        int borderCol = ColorUtil.a(255, 255, 255, (int) ((28 + 55 * hVal) * alpha));
        draw.a(matrices, hcx - curR, hcy - curR, curR * 2.0f, curR * 2.0f, curR, 0.75f, borderCol);

        if (hVal > 0.01f) {
            draw.a(matrices, hcx - curR, hcy - curR, curR * 2.0f, curR * 2.0f, curR, ColorUtil.a(255, 255, 255, (int) (25 * hVal * alpha)));
        }

        int baseHouse = ColorUtil.a(225, 232, 245, (int) (240 * alpha));
        int houseCol = ColorUtil.a(baseHouse, -1, hVal);

        // Modern architectural chevron roof
        for (int i = 0; i <= 6; i++) {
            float t = (float) i / 6.0f;
            float span = 2.0f + (11.0f * t);
            float sy = (hcy - 6.2f) + (i * 0.85f);
            draw.a(matrices, hcx - (span * 0.5f), sy, span, 1.0f, 0.5f, houseCol);
        }
        // Chimney
        draw.a(matrices, hcx + 3.2f, hcy - 6.0f, 1.6f, 3.0f, 0.4f, houseCol);
        // House body
        draw.a(matrices, hcx - 4.6f, hcy - 0.5f, 9.2f, 6.2f, 0.8f, houseCol);
        // Translucent recessed doorway
        int doorCol = ColorUtil.a(10, 13, 20, (int) (180 * alpha));
        draw.a(matrices, hcx - 1.5f, hcy + 1.6f, 3.0f, 4.1f, 1.2f, doorCol);
    }

    // ==========================================
    // 8. CLOCK CONTEXT MENU (WITH SLIDERS)
    // ==========================================
    private void renderClockContextMenu(MatrixStack matrices, Draw2DProcessor draw, float centerX, float menuProgress, double mouseX, double mouseY) {
        float menuW = 182.0f;
        float menuH = 240.0f;
        float menuX = centerX - (menuW * 0.5f);
        float menuY = this.lastClockY + this.lastClockH + 4.0f;

        float alpha = menuProgress;

        // Subtle Dim Backdrop (Haptic Touch focus)
        draw.a(matrices, 0.0f, 0.0f, (float) Interface.aM_.getWindow().getScaledWidth(), (float) Interface.aM_.getWindow().getScaledHeight(), 0.0f, ColorUtil.a(0, 0, 0, (int) (40 * alpha)));

        // Authentic Dark Liquid Glass Popover Menu
        drawLiquidGlass(draw, matrices, menuX, menuY, menuW, menuH, 14.0f, alpha, false, GLASS_CONFIG);

        // Header Title (мягкая благородная платина)
        Fonts.d.a(matrices, "Настройки логотипа и стекла", menuX + 12.0f, menuY + 9.0f, 7.5f, ColorUtil.a(215, 222, 235, (int) (235 * alpha)));
        Fonts.b.b(matrices, "✕", menuX + menuW - 12.0f, menuY + 9.0f, 6.5f, ColorUtil.a(150, 155, 170, (int) (180 * alpha)));

        draw.a(matrices, menuX + 8.0f, menuY + 22.0f, menuW - 16.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (18 * alpha)));

        // 1. Style Row (Toggle between: Жидкое стекло / Белый)
        float curRowY = menuY + 27.0f;
        boolean hoverStyle = MathUtil.a(mouseX, mouseY, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f);
        if (hoverStyle) {
            draw.a(matrices, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f, 4.0f, ColorUtil.a(255, 255, 255, (int) (15 * alpha)));
        }
        Fonts.b.a(matrices, "Стиль стекла", menuX + 10.0f, curRowY + 4.5f, 6.75f, ColorUtil.a(175, 185, 205, (int) (225 * alpha)));
        String styleStr = (GLASS_CONFIG.clockStyle == 0) ? "Жидкое стекло" : "Белый";
        float styleValW = Fonts.b.a(styleStr, 6.75f);
        Fonts.b.a(matrices, styleStr, menuX + menuW - 12.0f - styleValW, curRowY + 4.5f, 6.75f, ColorUtil.a(100, 160, 255, (int) (235 * alpha)));

        // 2. Sliders
        this.menuSliderX = menuX + 10.0f;
        this.menuSliderW = menuW - 20.0f;

        float[] sliderValues = new float[]{
                GLASS_CONFIG.blurStrength,
                GLASS_CONFIG.refractionStrength,
                GLASS_CONFIG.specularGloss,
                GLASS_CONFIG.glassOpacity
        };
        String[] sliderTitles = new String[]{
                "Размытие (Blur)",
                "Преломление",
                "Глянец (Gloss)",
                "Прозрачность"
        };

        curRowY += 22.0f;

        for (int i = 0; i < 4; i++) {
            float sRowH = 24.0f;
            float trackY = curRowY + 14.0f;
            float trackH = 3.5f;

            // Title on left, percentage on right
            Fonts.b.a(matrices, sliderTitles[i], menuX + 10.0f, curRowY + 2.0f, 6.5f, ColorUtil.a(165, 175, 192, (int) (220 * alpha)));
            int pct = Math.round(sliderValues[i] * 100.0f);
            String pctStr = pct + "%";
            float pctW = Fonts.b.a(pctStr, 6.5f);
            Fonts.b.a(matrices, pctStr, menuX + menuW - 10.0f - pctW, curRowY + 2.0f, 6.5f, ColorUtil.a(110, 165, 255, (int) (230 * alpha)));

            // Track background
            draw.a(matrices, this.menuSliderX, trackY, this.menuSliderW, trackH, 1.75f, ColorUtil.a(255, 255, 255, (int) (22 * alpha)));

            // Filled track
            float fillW = MathHelper.clamp(this.menuSliderW * sliderValues[i], 0.0f, this.menuSliderW);
            if (fillW > 0.5f) {
                draw.a(matrices, this.menuSliderX, trackY, fillW, trackH, 1.75f, ColorUtil.a(85, 150, 255, (int) (230 * alpha)));
            }

            // Slider Thumb
            float thumbX = this.menuSliderX + fillW;
            float thumbY = trackY + (trackH * 0.5f);
            draw.a(matrices, thumbX - 4.0f, thumbY - 4.0f, 8.0f, 8.0f, 4.0f, ColorUtil.a(230, 235, 245, (int) (245 * alpha)));

            curRowY += sRowH + 2.0f;
        }

        // Separator before buttons
        draw.a(matrices, menuX + 8.0f, curRowY + 2.0f, menuW - 16.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (20 * alpha)));

        // Reset Button Row
        curRowY += 7.0f;
        boolean hoverReset = MathUtil.a(mouseX, mouseY, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f);
        if (hoverReset) {
            draw.a(matrices, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f, 4.0f, ColorUtil.a(255, 50, 60, (int) (35 * alpha)));
        }
        int resetCol = hoverReset ? ColorUtil.a(255, 80, 90, (int) (255 * alpha)) : ColorUtil.a(255, 105, 115, (int) (230 * alpha));
        Fonts.b.a(matrices, "↺ Сбросить настройки", menuX + 10.0f, curRowY + 4.5f, 6.75f, resetCol);

        // Replay Intro Button Row
        curRowY += 21.0f;
        boolean hoverReplay = MathUtil.a(mouseX, mouseY, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f);
        if (hoverReplay) {
            draw.a(matrices, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f, 4.0f, ColorUtil.a(255, 255, 255, (int) (22 * alpha)));
        }
        int replayCol = hoverReplay ? ColorUtil.a(170, 220, 255, (int) (255 * alpha)) : ColorUtil.a(135, 185, 255, (int) (230 * alpha));
        Fonts.b.a(matrices, "▶ Повторить интро PS", menuX + 10.0f, curRowY + 4.5f, 6.75f, replayCol);

        // Player Settings Button Row
        curRowY += 21.0f;
        boolean hoverPl = MathUtil.a(mouseX, mouseY, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f);
        if (hoverPl) {
            draw.a(matrices, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f, 4.0f, ColorUtil.a(255, 255, 255, (int) (22 * alpha)));
        }
        int plCol = hoverPl ? ColorUtil.a(255, 255, 255, (int) (255 * alpha)) : ColorUtil.a(200, 205, 225, (int) (230 * alpha));
        Fonts.b.a(matrices, "⚙ Настройки плеера", menuX + 10.0f, curRowY + 4.5f, 6.75f, plCol);
    }

    // ==========================================
    // DRAWING UTILITIES
    // ==========================================
    private void drawLeftTriangle(Draw2DProcessor draw, MatrixStack matrices, float x, float cy, float w, float h, int color) {
        int steps = 5;
        float sliceW = w / steps;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.6f) / steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + (i * sliceW);
            float sy = cy - (sliceH * 0.5f);
            draw.a(matrices, sx, sy, sliceW + 0.35f, sliceH, 0.35f, color);
        }
    }

    private void drawRightTriangle(Draw2DProcessor draw, MatrixStack matrices, float x, float cy, float w, float h, int color) {
        int steps = 5;
        float sliceW = w / steps;
        for (int i = 0; i < steps; i++) {
            float t = (steps - 1 - i + 0.6f) / steps;
            float sliceH = Math.max(1.2f, h * t);
            float sx = x + (i * sliceW);
            float sy = cy - (sliceH * 0.5f);
            draw.a(matrices, sx, sy, sliceW + 0.35f, sliceH, 0.35f, color);
        }
    }

    private String formatTime(long ms) {
        if (ms <= 0L) return "0:00";
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10L ? "0" : "") + sec;
    }

    // ==========================================
    // INPUT HANDLING
    // ==========================================
    @Compile
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!introCompleted && !burstTriggered) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                triggerBurst();
                return true;
            }
        }
        if (!introCompleted) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.clockMenuOpen) {
                this.clockMenuOpen = false;
                this.activeDraggingSlider = -1;
                return true;
            }
            if (this.playerMenuOpen) {
                this.playerMenuOpen = false;
                this.activePlayerSlider = -1;
                return true;
            }
            if (this.wallpaperPickerOpen) {
                this.wallpaperPickerOpen = false;
                return true;
            }
        }

        return false;
    }

    @Compile
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!introCompleted && !burstTriggered) {
            if (button == 0) {
                triggerBurst();
                return true;
            }
        }
        if (!introCompleted) {
            return true;
        }

        double dA = MathUtil.scale(mouseX, 2);
        double dA2 = MathUtil.scale(mouseY, 2);

        MinecraftClient mc = Interface.aM_;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        float centerX = screenW * 0.5f;

        // Right-click settings menus removed ("убери все настройки как плеера так и лого в мейн меню")

        if (button == 0) {
            // 1. Clock Context Menu Interactions
            if (this.clockMenuOpen) {
                float menuW = 182.0f;
                float menuH = 240.0f;
                float menuX = centerX - (menuW * 0.5f);
                float menuY = this.lastClockY + this.lastClockH + 4.0f;

                // Close button
                if (MathUtil.a(dA, dA2, menuX + menuW - 18.0f, menuY + 6.0f, 14.0f, 14.0f)) {
                    this.clockMenuOpen = false;
                    this.activeDraggingSlider = -1;
                    return true;
                }

                // Style Row Click
                float curRowY = menuY + 27.0f;
                if (MathUtil.a(dA, dA2, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f)) {
                    GLASS_CONFIG.clockStyle = (GLASS_CONFIG.clockStyle == 0) ? 1 : 0;
                    saveGlassConfig();
                    return true;
                }

                // Check Sliders (4 Sliders)
                curRowY += 22.0f;
                for (int i = 0; i < 4; i++) {
                    float trackY = curRowY + 14.0f;
                    if (MathUtil.a(dA, dA2, this.menuSliderX - 4.0f, trackY - 5.0f, this.menuSliderW + 8.0f, 14.0f)) {
                        this.activeDraggingSlider = i;
                        updateSliderValue(i, (float) dA);
                        return true;
                    }
                    curRowY += 26.0f;
                }

                // Reset Button Click
                curRowY += 7.0f;
                if (MathUtil.a(dA, dA2, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f)) {
                    GLASS_CONFIG.blurStrength = 1.0f;
                    GLASS_CONFIG.refractionStrength = 0.85f;
                    GLASS_CONFIG.specularGloss = 1.0f;
                    GLASS_CONFIG.glassOpacity = 0.80f;
                    GLASS_CONFIG.clockStyle = 0;
                    saveGlassConfig();
                    return true;
                }

                // Replay Intro Button Click
                curRowY += 21.0f;
                if (MathUtil.a(dA, dA2, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f)) {
                    introStartTime = System.currentTimeMillis();
                    introCompleted = false;
                    burstTriggered = false;
                    burstTriggerTime = -1L;
                    this.clockMenuOpen = false;
                    this.activeDraggingSlider = -1;
                    return true;
                }

                // Player Settings Button Click from Clock Menu
                curRowY += 21.0f;
                if (MathUtil.a(dA, dA2, menuX + 6.0f, curRowY, menuW - 12.0f, 18.0f)) {
                    this.clockMenuOpen = false;
                    this.playerMenuOpen = true;
                    this.activeDraggingSlider = -1;
                    this.activePlayerSlider = -1;
                    return true;
                }

                if (!MathUtil.a(dA, dA2, menuX, menuY, menuW, menuH)) {
                    this.clockMenuOpen = false;
                    this.activeDraggingSlider = -1;
                }
                return true;
            }

            // 1.1. Player Context Menu Interactions
            if (this.playerMenuOpen) {
                float menuX = this.lastPlayerMenuX;
                float menuY = this.lastPlayerMenuY;
                float menuW = this.lastPlayerMenuW;
                float menuH = this.lastPlayerMenuH;

                // Close button
                if (MathUtil.a(dA, dA2, menuX + menuW - 18.0f, menuY + 6.0f, 16.0f, 16.0f)) {
                    this.playerMenuOpen = false;
                    this.activePlayerSlider = -1;
                    return true;
                }

                float curRowY = menuY + 27.0f;

                // 1. Visibility Mode Pills (3 pills)
                float pillW = 38.0f;
                float pillH = 14.0f;
                for (int i = 0; i < 3; i++) {
                    float px = menuX + menuW - 10.0f - ((3 - i) * (pillW + 3.0f)) + 3.0f;
                    if (MathUtil.a(dA, dA2, px, curRowY, pillW, pillH)) {
                        PLAYER_CONFIG.visibilityMode = i;
                        savePlayerConfig();
                        return true;
                    }
                }

                // 2. Marquee Toggle
                curRowY += 20.0f;
                float tglW = 30.0f;
                float tglH = 13.0f;
                float tglX = menuX + menuW - 10.0f - tglW;
                if (MathUtil.a(dA, dA2, tglX, curRowY, tglW, tglH)) {
                    PLAYER_CONFIG.scrollMarquee = !PLAYER_CONFIG.scrollMarquee;
                    savePlayerConfig();
                    return true;
                }

                // 3. Timecodes Toggle
                curRowY += 18.0f;
                if (MathUtil.a(dA, dA2, tglX, curRowY, tglW, tglH)) {
                    PLAYER_CONFIG.showTimeCodes = !PLAYER_CONFIG.showTimeCodes;
                    savePlayerConfig();
                    return true;
                }

                // 4. Sliders (Volume, Opacity, Blur)
                float sW = menuW - 20.0f;
                float sX = menuX + 10.0f;

                curRowY += 19.0f; // Volume
                float trackY = curRowY + 12.0f;
                if (MathUtil.a(dA, dA2, sX - 4.0f, trackY - 5.0f, sW + 8.0f, 14.0f)) {
                    this.activePlayerSlider = 0;
                    this.playerSliderX = sX;
                    this.playerSliderW = sW;
                    updatePlayerSliderValue(0, (float) dA);
                    return true;
                }

                curRowY += 23.0f; // Opacity
                trackY = curRowY + 12.0f;
                if (MathUtil.a(dA, dA2, sX - 4.0f, trackY - 5.0f, sW + 8.0f, 14.0f)) {
                    this.activePlayerSlider = 1;
                    this.playerSliderX = sX;
                    this.playerSliderW = sW;
                    updatePlayerSliderValue(1, (float) dA);
                    return true;
                }

                curRowY += 23.0f; // Blur
                trackY = curRowY + 12.0f;
                if (MathUtil.a(dA, dA2, sX - 4.0f, trackY - 5.0f, sW + 8.0f, 14.0f)) {
                    this.activePlayerSlider = 2;
                    this.playerSliderX = sX;
                    this.playerSliderW = sW;
                    updatePlayerSliderValue(2, (float) dA);
                    return true;
                }

                // 5. Scale pills (3 pills)
                curRowY += 24.0f;
                float[] scales = {0.85f, 1.0f, 1.15f};
                float scPillW = 32.0f;
                for (int i = 0; i < 3; i++) {
                    float px = menuX + menuW - 10.0f - ((3 - i) * (scPillW + 3.0f)) + 3.0f;
                    if (MathUtil.a(dA, dA2, px, curRowY, scPillW, pillH)) {
                        PLAYER_CONFIG.playerScale = scales[i];
                        savePlayerConfig();
                        return true;
                    }
                }

                // 6. Reset button
                curRowY += 23.0f;
                if (MathUtil.a(dA, dA2, menuX + 10.0f, curRowY, menuW - 20.0f, 16.0f)) {
                    resetPlayerConfig();
                    return true;
                }

                if (!MathUtil.a(dA, dA2, menuX, menuY, menuW, menuH)) {
                    this.playerMenuOpen = false;
                    this.activePlayerSlider = -1;
                }
                return true;
            }





            // 5. iOS Floating Dock Click & Drag
            if (MathUtil.a(dA, dA2, this.lastDockX - 4.0f, this.lastDockY - 4.0f, this.lastDockW + 8.0f, this.lastDockH + 8.0f)) {
                this.isDraggingDock = true;
                int slot = (int) ((dA - (this.lastDockX + this.lastDockPad)) / this.lastItemW);
                this.selectedDockIndex = MathHelper.clamp(slot, 0, 4);
                return true;
            }

            // 6. Media Player Controls
            // Dots Button (visual indicator only, settings menu removed)

            // If in Mode 2 (Hidden) and clicking on the compact pill
            if (PLAYER_CONFIG.visibilityMode == 2 && MathUtil.a(dA, dA2, this.lastMediaX, this.lastMediaY, this.lastMediaW, this.lastMediaH)) {
                this.playerMenuOpen = !this.playerMenuOpen;
                this.activePlayerSlider = -1;
                return true;
            }

            // Prev Track (<<)
            if (MathUtil.a(dA, dA2, this.lastPrevBtnX - 10.0f, this.lastCtrlY - 2.0f, 20.0f, 20.0f)) {
                MEDIA_EXECUTOR.submit(() -> {
                    try {
                        if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SkipPrevious();
                    } catch (Throwable ignored) {}
                });
                return true;
            }

            // Play / Pause (|| or ▶)
            if (MathUtil.a(dA, dA2, this.lastPlayBtnX - 10.0f, this.lastCtrlY - 2.0f, 20.0f, 20.0f)) {
                MEDIA_EXECUTOR.submit(() -> {
                    try {
                        if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.TogglePlayPause();
                    } catch (Throwable ignored) {}
                });
                return true;
            }

            // Next Track (>>)
            if (MathUtil.a(dA, dA2, this.lastNextBtnX - 10.0f, this.lastCtrlY - 2.0f, 20.0f, 20.0f)) {
                MEDIA_EXECUTOR.submit(() -> {
                    try {
                        if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SkipNext();
                    } catch (Throwable ignored) {}
                });
                return true;
            }

            // Progress Bar Click / Seek
            if (this.lastBarW > 0.0f && MathUtil.a(dA, dA2, this.lastBarX - 3.0f, this.lastBarY, this.lastBarW + 6.0f, this.lastBarH)) {
                if (this.currentDurationMs > 0L) {
                    float pct = MathHelper.clamp((float) (dA - this.lastBarX) / this.lastBarW, 0.0f, 1.0f);
                    long targetMs = (long) (this.currentDurationMs * pct);
                    this.currentPositionMs = targetMs;
                    this.isDraggingSeek = true;
                    MEDIA_EXECUTOR.submit(() -> {
                        try {
                            if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SeekToMs(targetMs);
                        } catch (Throwable ignored) {}
                    });
                }
                return true;
            }

            // Album Art Click -> Toggle Play Pause
            float artX = this.lastBadgeX + 3.0f;
            float artY = this.lastBadgeY + 3.0f;
            float artSize = this.lastBadgeH - 6.0f;
            if (MathUtil.a(dA, dA2, artX, artY, artSize, artSize)) {
                MEDIA_EXECUTOR.submit(() -> {
                    try {
                        if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.TogglePlayPause();
                    } catch (Throwable ignored) {}
                });
                return true;
            }
        }

        return false;
    }

    @Compile
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDraggingDock) {
            return true;
        }
        if (this.isDraggingSeek && this.lastBarW > 0.0f && this.currentDurationMs > 0L) {
            double dA = MathUtil.scale(mouseX, 2);
            float pct = MathHelper.clamp((float) (dA - this.lastBarX) / this.lastBarW, 0.0f, 1.0f);
            long targetMs = (long) (this.currentDurationMs * pct);
            this.currentPositionMs = targetMs;
            MEDIA_EXECUTOR.submit(() -> {
                try {
                    if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SeekToMs(targetMs);
                } catch (Throwable ignored) {}
            });
            return true;
        }
        if (this.clockMenuOpen && this.activeDraggingSlider >= 0) {
            double dA = MathUtil.scale(mouseX, 2);
            updateSliderValue(this.activeDraggingSlider, (float) dA);
            return true;
        }
        if (this.playerMenuOpen && this.activePlayerSlider >= 0) {
            double dA = MathUtil.scale(mouseX, 2);
            updatePlayerSliderValue(this.activePlayerSlider, (float) dA);
            return true;
        }
        if (this.wallpaperPickerOpen && draggingBlurSlider) {
            double dA = MathUtil.scale(mouseX, 2);
            int screenW = Interface.aM_.getWindow().getScaledWidth();
            float sheetW = 188.0f;
            float sheetX = screenW - sheetW - 16.0f;
            float sliderX = sheetX + 10.0f;
            float sliderW = sheetW - 20.0f;
            wallpaperBlur = MathHelper.clamp((float) (dA - sliderX) / sliderW, 0.0f, 1.0f);
            saveWallpaperConfig(currentWallpaperId, wallpaperBlur);
            return true;
        }
        return false;
    }

    @Compile
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingSeek = false;
        if (this.isDraggingDock) {
            this.isDraggingDock = false;
            double dA = MathUtil.scale(mouseX, 2);
            double dA2 = MathUtil.scale(mouseY, 2);

            // If released near the dock vertically (within 40px tolerance)
            if (dA2 >= this.lastDockY - 40.0f && dA2 <= this.lastDockY + this.lastDockH + 40.0f) {
                float minCenterX = this.lastDockX + this.lastDockPad + (this.lastItemW * 0.5f);
                int slot = Math.round((this.dockPillCenterX - minCenterX) / this.lastItemW);
                slot = MathHelper.clamp(slot, 0, 4);
                this.selectedDockIndex = slot;

                switch (slot) {
                    case 0 -> Interface.aM_.setScreen(new SelectWorldScreen((Screen) null));
                    case 1 -> Interface.aM_.setScreen(new MultiplayerScreen((Screen) null));
                    case 2 -> Interface.aM_.setScreen(new AltScreen());
                    case 3 -> Interface.aM_.setScreen(new OptionsScreen((Screen) null, Interface.aM_.options));
                    case 4 -> Interface.aM_.stop();
                }
            }
            return true;
        }
        if (draggingBlurSlider) {
            draggingBlurSlider = false;
            return true;
        }
        if (this.activeDraggingSlider >= 0) {
            this.activeDraggingSlider = -1;
            saveGlassConfig();
            return true;
        }
        if (this.activePlayerSlider >= 0) {
            this.activePlayerSlider = -1;
            savePlayerConfig();
            return true;
        }
        return false;
    }

    private void updateSliderValue(int index, float mouseX) {
        if (this.menuSliderW <= 0.0f) return;
        float norm = MathHelper.clamp((mouseX - this.menuSliderX) / this.menuSliderW, 0.0f, 1.0f);
        switch (index) {
            case 0 -> GLASS_CONFIG.blurStrength = norm;
            case 1 -> GLASS_CONFIG.refractionStrength = norm;
            case 2 -> GLASS_CONFIG.specularGloss = norm;
            case 3 -> GLASS_CONFIG.glassOpacity = MathHelper.clamp(norm, 0.2f, 1.0f);
        }
    }

    private void updatePlayerSliderValue(int index, float mouseX) {
        if (this.playerSliderW <= 0.0f) return;
        float norm = MathHelper.clamp((mouseX - this.playerSliderX) / this.playerSliderW, 0.0f, 1.0f);
        switch (index) {
            case 0 -> {
                PLAYER_CONFIG.volume = norm;
                MEDIA_EXECUTOR.submit(() -> {
                    try {
                        if (OptMedia.INSTANCE != null) OptMedia.INSTANCE.SetVolume(PLAYER_CONFIG.volume);
                    } catch (Throwable ignored) {}
                });
            }
            case 1 -> PLAYER_CONFIG.cardOpacity = MathHelper.clamp(0.2f + (norm * 0.8f), 0.2f, 1.0f);
            case 2 -> PLAYER_CONFIG.cardBlur = norm;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        return mouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        return mouseReleased(click.x(), click.y(), click.button());
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return keyPressed(input.key(), input.scancode(), input.modifiers());
    }

}
