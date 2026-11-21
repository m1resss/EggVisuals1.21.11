package egg.client.ui;

import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.misc.ClientSettings;
import egg.client.module.render.WorldTweaks;
import egg.client.module.render.Keybinds;

import egg.client.ui.widget.WorldTweaksUI;
import egg.client.module.setting.BindSetting;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.Setting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.DynamicGuiTextures;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.ScissorUtil;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.grid.GuiDotGrid;
import egg.client.ui.lens.LensParams;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.FpsBenchmark;
import egg.client.util.GuiSoundManager;
import egg.client.util.LayoutHelper;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ClickGuiScreen extends Screen {
    private static ClickGuiScreen instance;

    public static ClickGuiScreen getInstance() {
        if (instance == null) {
            instance = new ClickGuiScreen();
        }
        return instance;
    }

    private static final Identifier LOGO_TEXTURE = Identifier.of("egg", "textures/egg_logo.png");

    private long animStartTime = 0L;
    private float animStartProgress = 0.0f;
    private float currentAnimProgress = 0.0f;
    private float lensOriginX = -1.0f;
    private float lensOriginY = -1.0f;
    private boolean closing = false;
    private boolean cascadeStarted = false;
    private int previousVanillaBlur = -1;

    private static Category savedCategory = Category.ESP;
    private static Module savedSearchModule = null;
    private static String savedHeaderTitle = "Visual - Visuals";
    private static String savedSearchQuery = "";
    private static float savedScrollOffset = 0.0f;
    private static float savedTargetScroll = 0.0f;
    private static float savedSidebarScrollOffset = 0.0f;
    private static float savedSidebarTargetScroll = 0.0f;
    private static boolean savedSidebarCollapsed = false;

    private Category selectedCategory = savedCategory;
    private Module selectedSearchModule = savedSearchModule;
    private boolean sidebarCollapsed = savedSidebarCollapsed;
    private final Animation sidebarCollapseAnim = new Animation(savedSidebarCollapsed ? 1.0f : 0.0f, 260L, Easing.EASE_OUT_CUBIC);
    private final Animation categoryPillY = new Animation(0.0f, 260L, Easing.EASE_OUT_EXPO);
    private final Animation categoryPillHeight = new Animation(20.0f, 260L, Easing.EASE_OUT_EXPO);
    private final Map<Category, Animation> categoryHoverAnims = new HashMap<>();

    private String headerTitle = savedHeaderTitle;
    private final Animation headerFadeAnim = new Animation(1.0f, 240L, Easing.EASE_OUT_CUBIC);

    private boolean searchFocused = false;
    private String searchQuery = savedSearchQuery;
    private final Animation searchFocusAnim = new Animation(0.0f, 200L, Easing.EASE_OUT_CUBIC);
    private final Animation searchPillY = new Animation(0.0f, 120L, Easing.EASE_OUT_CUBIC);
    private int searchSelectedIndex = 0;
    private long lastBlinkTime = 0;
    private int searchCursor = 0;
    private int searchSelectionEnd = 0;
    private boolean searchSelecting = false;

    private void selectSearchModule(Module clickedMod) {
        if (clickedMod == null) return;
        selectedCategory = clickedMod.getCategory();
        ensureCategoryVisible(selectedCategory);
        headerTitle = selectedCategory.getSection() + " - " + selectedCategory.getName();
        selectedSearchModule = clickedMod;
        searchQuery = clickedMod.getName();
        searchCursor = searchQuery.length();
        searchSelectionEnd = searchCursor;
        searchFocused = false;
        searchSelectedIndex = 0;
        clickedMod.setExpanded(true);
        markModulesDirty();
        resetCascade();
        GuiSoundManager.playClick();
    }

    private int getSearchCursorIndex(float clickX, float searchX) {
        float textX = searchX + 19.0f;
        if (searchQuery.isEmpty() || clickX <= textX) return 0;
        float totalW = ClientFont.getWidth(searchQuery, ClientFont.SF_REGULAR);
        if (clickX >= textX + totalW) return searchQuery.length();

        int bestIndex = 0;
        float bestDist = Math.abs(clickX - textX);
        for (int idx = 1; idx <= searchQuery.length(); idx++) {
            float w = ClientFont.getWidth(searchQuery.substring(0, idx), ClientFont.SF_REGULAR);
            float dist = Math.abs(clickX - (textX + w));
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = idx;
            }
        }
        return bestIndex;
    }

    private float scrollOffset = savedScrollOffset;
    private float targetScroll = savedTargetScroll;
    private float maxScroll = 0.0f;
    private float scrollVelocity = 0.0f;
    private float smoothedScrollVelocity = 0.0f;
    private float sidebarScrollOffset = savedSidebarScrollOffset;
    private float sidebarTargetScroll = savedSidebarTargetScroll;
    private float sidebarMaxScroll = 0.0f;
    private Category deferredRailTooltipCat = null;
    private float deferredRailTooltipY = 0.0f;
    private final Animation scrollbarAlphaAnim = new Animation(0.0f, 240L, Easing.EASE_OUT_CUBIC);
    private long lastScrollTime = 0;

    private final Map<Module, Animation> cardCascadeAnims = new HashMap<>();
    private final Map<Module, Animation> cardHoverAnims = new HashMap<>();
    private final Map<Module, Animation> cardPressAnims = new HashMap<>();
    private final Map<Module, Animation> starScaleAnims = new HashMap<>();
    private final Map<Module, Animation> dotsHoverAnims = new HashMap<>();

    private final List<Module> cachedFilteredModules = new ArrayList<>();
    private boolean modulesDirty = true;
    private final Map<SliderSetting, String> cachedSliderStrings = new HashMap<>();
    private final Map<SliderSetting, Double> lastSliderValues = new HashMap<>();
    private final Map<Module, String> cachedKeybindStrings = new HashMap<>();
    private final Map<Module, Integer> lastModuleKeys = new HashMap<>();
    private final Map<String, String> truncatedLabelsCache = new HashMap<>();
    private final Map<Module, Long> cardHoverTimes = new HashMap<>();

    private float lastWrappedCardW = -1.0f;
    private final Map<Module, List<String>> cachedDescLines = new HashMap<>();
    private final Map<Module, Float> cachedHeaderHeights = new HashMap<>();
    private final Map<Module, String> cachedTruncatedDescs = new HashMap<>();

    private String lastSearchQuery = null;
    private final List<Module> cachedSearchMatches = new ArrayList<>();

    private List<Setting> cachedClientSettingsLeft = null;
    private List<Setting> cachedClientSettingsRight = null;

    private Object lastTooltipTarget = null;
    private List<String> cachedTooltipLines = null;

    private float openDropdownMaxModeW = 0.0f;

    private static final int[] CATEGORY_MODULE_COUNTS = new int[Category.values().length];
    private static final String[] CATEGORY_COUNT_STRINGS = new String[Category.values().length];
    private static final float[] CATEGORY_COUNT_WIDTHS = new float[Category.values().length];
    private static final String[] CATEGORY_BREADCRUMBS = new String[Category.values().length];
    private static boolean categoryCountsInitialized = false;

    private static void initCategoryData() {
        if (categoryCountsInitialized) return;
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int count;
            if (cat == Category.SETTINGS) count = 1;
            else if (cat == Category.INVENTORY) count = 4;
            else if (cat == Category.COSMETICS) count = 8;
            else count = ModuleManager.getInstance().getModulesByCategory(cat).size();

            CATEGORY_MODULE_COUNTS[i] = count;
            String str = String.valueOf(count);
            CATEGORY_COUNT_STRINGS[i] = str;
            CATEGORY_COUNT_WIDTHS[i] = ClientFont.getWidth(str, ClientFont.SF_REGULAR);
            CATEGORY_BREADCRUMBS[i] = cat.getSection() + " / " + cat.getName();
        }
        categoryCountsInitialized = true;
    }

    private void ensureClientSettingsSplit(ClientSettings cs) {
        if (cachedClientSettingsLeft != null && cachedClientSettingsRight != null) return;
        cachedClientSettingsLeft = new ArrayList<>();
        cachedClientSettingsRight = new ArrayList<>();
        for (Setting s : cs.getSettings()) {
            String name = s.getName();
            if (name.contains("Пресет") || name.contains("Accent") || name.contains("курсор") || name.contains("сетка") || name.contains("Сетка") || name.contains("открытия")) {
                cachedClientSettingsLeft.add(s);
            } else {
                cachedClientSettingsRight.add(s);
            }
        }
    }
    private Module activeCandidateModule = null;
    private Setting activeCandidateSetting = null;
    private Module hoveredTooltipModule = null;
    private Setting hoveredTooltipSetting = null;
    private long hoverStateStartTime = 0L;
    private final Animation tooltipAlphaAnim = new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC);
    private float tooltipMouseX = 0.0f;
    private float tooltipMouseY = 0.0f;

    private SliderSetting draggingSlider = null;
    private float dragTrackX = 0.0f;
    private float dragTrackW = 100.0f;
    private float dragStartClickX = 0.0f;
    private float lastRawClickX = 0.0f;
    private double dragStartValue = 0.0;
    private float dragStartScale = 1.0f;
    private float currentGuiScale = 1.0f;

    private float getGuiBaseWidth() {
        return Math.clamp(this.width * 0.58f, 540.0f, 600.0f);
    }

    private float getGuiBaseHeight() {
        return Math.clamp(this.height * 0.62f, 280.0f, 320.0f);
    }

    private float getGuiCenterX() {
        float w = getGuiBaseWidth();
        return (float) Math.round((this.width - w) / 2.0f) + w * 0.5f;
    }

    private float getGuiCenterY() {
        float h = getGuiBaseHeight();
        return (float) Math.round((this.height - h) / 2.0f) + h * 0.5f;
    }
    private Module listeningKeybindModule = null;
    private BindSetting listeningBindSetting = null;

    private ColorSetting openColorPickerSetting = null;
    private float colorPopupX = 0.0f;
    private float colorPopupY = 0.0f;
    private float colorPopupAnim = 0.0f;
    private enum ColorDragMode {
        NONE,
        AREA_SV,
        HUE,
        ALPHA
    }
    private ColorDragMode colorDragMode = ColorDragMode.NONE;
    private final Map<ColorSetting, float[]> colorSettingBadgePositions = new HashMap<>();

    private final Map<SliderSetting, Float> sliderAnimatedProgress = new HashMap<>();
    private final Map<SliderSetting, Float> sliderDragProgress = new HashMap<>();

    private ModeSetting openDropdownSetting = null;
    private float openDropdownX = 0.0f;
    private float openDropdownY = 0.0f;
    private float openDropdownW = 0.0f;
    private float openDropdownAnim = 0.0f;
    private float openDropdownScrollY = 0.0f;
    private float openDropdownMaxScroll = 0.0f;

    private boolean isWorldTweaksCustomSetting(Module module, Setting s) {
        if (!(module instanceof WorldTweaks) || s == null) return false;
        String name = s.getName();
        return "Режим времени".equals(name) || "Время суток".equals(name) || "Время (ч)".equals(name)
                || "Сезон мира".equals(name) || "Погода".equals(name);
    }

    public ClickGuiScreen() {
        super(Text.of("Egg Visual"));
        for (Category cat : Category.values()) {
            categoryHoverAnims.put(cat, new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC));
        }
    }

    @Override
    protected void init() {
        super.init();
        if (client != null && client.options != null) {
            if (previousVanillaBlur == -1) {
                previousVanillaBlur = client.options.getMenuBackgroundBlurrinessValue();
            }
            if (ClientSettings.isBlurEnabled()) {
                client.options.getMenuBackgroundBlurriness().setValue(ClientSettings.getBlurStrength());
            }
        }
        GuiSoundManager.playScreenOpen();
        if (closing) {
            closing = false;
            animStartProgress = currentAnimProgress;
        } else {
            animStartProgress = 0.0f;
            currentAnimProgress = 0.0f;
        }
        animStartTime = System.nanoTime();
        cascadeStarted = false;
        DynamicGuiTextures.clearCache();

        currentGuiScale = ClientSettings.getGuiScale();
        float guiWidth = getGuiBaseWidth();
        float guiHeight = getGuiBaseHeight();
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        if ("Курсор".equals(ClientSettings.getLensCenterMode()) && client != null && client.mouse != null && client.getWindow() != null) {
            double mx = client.mouse.getX() * (double) width / (double) client.getWindow().getWidth();
            double my = client.mouse.getY() * (double) height / (double) client.getWindow().getHeight();
            float scale = currentGuiScale;
            float cx = getGuiCenterX();
            float cy = getGuiCenterY();
            lensOriginX = (float) ((mx - cx) / scale + cx);
            lensOriginY = (float) ((my - cy) / scale + cy);
        } else {
            lensOriginX = getGuiCenterX();
            lensOriginY = getGuiCenterY();
        }

        for (Module m : EggClient.getInstance().getModuleManager().getModules()) {
            cardHoverAnims.putIfAbsent(m, new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC));
            cardPressAnims.putIfAbsent(m, new Animation(1.0f, 120L, Easing.EASE_OUT_CUBIC));
            starScaleAnims.putIfAbsent(m, new Animation(1.0f, 160L, Easing.EASE_OUT_BACK));
            dotsHoverAnims.putIfAbsent(m, new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC));
        }

        selectedCategory = savedCategory;
        selectedSearchModule = savedSearchModule;
        if ("Visual - Esp".equals(savedHeaderTitle)) {
            savedHeaderTitle = "Visual - Visuals";
        }
        headerTitle = savedHeaderTitle;
        searchQuery = savedSearchQuery;
        scrollOffset = savedScrollOffset;
        targetScroll = savedTargetScroll;
        scrollVelocity = 0.0f;
        smoothedScrollVelocity = 0.0f;
        sidebarScrollOffset = savedSidebarScrollOffset;
        sidebarTargetScroll = savedSidebarTargetScroll;
        sidebarCollapsed = savedSidebarCollapsed;
        sidebarCollapseAnim.setValue(sidebarCollapsed ? 1.0f : 0.0f);
        sidebarCollapseAnim.setTarget(sidebarCollapsed ? 1.0f : 0.0f);
        ensureCategoryVisible(selectedCategory);
        markModulesDirty();
        if ("Выкл".equals(ClientSettings.getOpenAnimationMode())) {
            resetCascade();
            cascadeStarted = true;
        }

    }

    private void ensureCategoryVisible(Category cat) {
        float cp = sidebarCollapseAnim.getValue();
        float startY = MathHelper.lerp(cp, 60.0f, 38.0f) + 4.0f;
        float curY = startY;
        String lastSec = "";
        float catY = -1.0f;
        for (Category c : Category.values()) {
            if (!c.getSection().equals(lastSec)) {
                lastSec = c.getSection();
                if (curY > startY + 2.0f) {
                    curY += MathHelper.lerp(cp, 8.0f, 2.0f);
                }
                curY += MathHelper.lerp(cp, 14.0f, 4.0f);
            }
            if (c == cat) {
                catY = curY;
                break;
            }
            curY += 25.0f;
        }
        if (catY >= 0.0f) {
            float gh = getGuiBaseHeight();
            float viewportH = gh - startY - 4.0f;
            float topBound = startY;
            if (catY - sidebarTargetScroll < topBound) {
                sidebarTargetScroll = Math.max(0.0f, catY - topBound);
            } else if (catY + 22.0f - sidebarTargetScroll > topBound + viewportH) {
                sidebarTargetScroll = Math.max(0.0f, catY + 22.0f - (topBound + viewportH));
            }
        }
    }

    public void markModulesDirty() {
        this.modulesDirty = true;
        this.truncatedLabelsCache.clear();
        this.cachedDescLines.clear();
        this.cachedHeaderHeights.clear();
        this.cachedTruncatedDescs.clear();
        this.lastSearchQuery = null;
    }

    private void resetCascade() {
        List<Module> list = getFilteredModules();
        cardCascadeAnims.clear();
        boolean isWave = "Волна".equals(ClientSettings.getOpenAnimationMode());
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            long delay = isWave ? (long) (i * 30L) : (i * 20L);
            long dur = isWave ? 420L + delay : 260L + delay;
            Animation anim = new Animation(0.0f, dur, Easing.EASE_OUT_CUBIC);
            anim.setValue(0.0f);
            anim.setTarget(1.0f);
            cardCascadeAnims.put(m, anim);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void close() {
        requestClose();
    }

    public void saveState() {
        savedCategory = this.selectedCategory;
        savedSearchModule = this.selectedSearchModule;
        savedHeaderTitle = this.headerTitle;
        savedSearchQuery = this.searchQuery;
        savedScrollOffset = this.scrollOffset;
        savedTargetScroll = this.targetScroll;
        savedSidebarScrollOffset = this.sidebarScrollOffset;
        savedSidebarTargetScroll = this.sidebarTargetScroll;
        savedSidebarCollapsed = this.sidebarCollapsed;
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
        if (client != null && client.options != null && previousVanillaBlur != -1) {
            client.options.getMenuBackgroundBlurriness().setValue(previousVanillaBlur);
            previousVanillaBlur = -1;
        }
        this.closing = false;
        this.cardHoverTimes.clear();
        this.activeCandidateModule = null;
        this.activeCandidateSetting = null;
        this.hoveredTooltipModule = null;
        this.hoveredTooltipSetting = null;
        this.tooltipAlphaAnim.setValue(0.0f);
        this.tooltipAlphaAnim.setTarget(0.0f);
    }

    public void closeWithAnimation() {
        requestClose();
    }

    private void requestClose() {
        if (!closing) {
            saveState();
            closing = true;
            GuiSoundManager.playScreenClose();
            animStartProgress = currentAnimProgress;
            animStartTime = System.nanoTime();
            searchFocused = false;
            listeningKeybindModule = null;
            listeningBindSetting = null;
            draggingSlider = null;
            openColorPickerSetting = null;
            colorDragMode = ColorDragMode.NONE;
            openDropdownSetting = null;
            tooltipAlphaAnim.setTarget(0.0f);
            cardHoverTimes.clear();
            egg.client.config.ConfigManager.save();

        }
    }

    private void updateAnimationProgress() {
        String animMode = ClientSettings.getOpenAnimationMode();
        if ("Выкл".equals(animMode)) {
            currentAnimProgress = closing ? 0.0f : 1.0f;
            return;
        }

        long now = System.nanoTime();
        float speedMult = ClientSettings.getSpeedMultiplier();
        if (speedMult <= 0.05f) speedMult = 0.05f;
        long openDuration = "Волна".equals(animMode) ? 1200L : 950L;
        long closeDuration = "Волна".equals(animMode) ? 450L : LensParams.CLOSE_DURATION_MS;
        long durationMs = (long) ((closing ? closeDuration : openDuration) / speedMult);
        float elapsedMs = (now - animStartTime) / 1_000_000.0f;
        float linear = Math.clamp(elapsedMs / (float) durationMs, 0.0f, 1.0f);

        if (closing) {
            float ease = LensParams.easeInCubic(linear);
            currentAnimProgress = animStartProgress * (1.0f - ease);
            if (linear >= 1.0f || currentAnimProgress <= 0.005f) {
                currentAnimProgress = 0.0f;
            }
        } else {
            float ease = "Волна".equals(animMode) ? (float) Math.sin(linear * Math.PI * 0.5f) : 1.0f - (float) Math.pow(1.0f - linear, 3.0);
            currentAnimProgress = animStartProgress + (1.0f - animStartProgress) * ease;
            if (linear >= 1.0f || (1.0f - currentAnimProgress) <= 0.001f) {
                currentAnimProgress = 1.0f;
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimationProgress();
        boolean blurEnabled = ClientSettings.isBlurEnabled() && !FpsBenchmark.overrideNoBlur;
        if (blurEnabled && currentAnimProgress > 0.01f && client != null && client.world != null) {
            if (client.options != null) {
                int targetBlur = ClientSettings.getBlurStrength();
                if (client.options.getMenuBackgroundBlurrinessValue() != targetBlur) {
                    client.options.getMenuBackgroundBlurriness().setValue(targetBlur);
                }
            }
            try {
                client.gameRenderer.renderBlur();
            } catch (Throwable ignored) {

            }
        }
        if (currentAnimProgress > 0.01f) {
            float dimFactor = ClientSettings.getScreenDimOpacity();
            int dimAlpha = (int) (255 * dimFactor * currentAnimProgress);
            int acc = Theme.getAccent();
            int dr = Math.min(16, (int) (ColorUtil.red(acc) * 0.025f));
            int dg = Math.min(16, (int) (ColorUtil.green(acc) * 0.025f));
            int db = Math.min(20, (int) (ColorUtil.blue(acc) * 0.035f));

            int topColor = ColorUtil.rgba(dr, dg, db, Math.min(255, (int) (dimAlpha * 1.15f)));
            int midColor = ColorUtil.rgba(dr, dg, db, dimAlpha);
            int botColor = ColorUtil.rgba(dr, dg, db, Math.min(255, (int) (dimAlpha * 1.25f)));

            int halfH = height / 2;
            Render2D.drawGradientRect(context, 0, 0, width, halfH, 0.0f, topColor, topColor, midColor, midColor);
            Render2D.drawGradientRect(context, 0, halfH, width, height - halfH, 0.0f, midColor, midColor, botColor, botColor);
            DrawBatcher.flushPending();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimationProgress();
        this.lateContext = context;
        activeCandidateModule = null;
        activeCandidateSetting = null;
        WorldTweaks wtModule = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wtModule != null) wtModule.syncAudioState();

        if (closing && currentAnimProgress <= 0.005f) {
            closing = false;
            if (client != null) {
                client.setScreen(null);
            }
            return;
        }

        String animMode = ClientSettings.getOpenAnimationMode();
        float p = currentAnimProgress;

        if (!cascadeStarted && p >= 0.35f && !closing) {
            cascadeStarted = true;
            resetCascade();
        }

        float prevScroll = scrollOffset;
        scrollOffset += (targetScroll - scrollOffset) * 0.22f;
        scrollVelocity = scrollOffset - prevScroll;
        if (Math.abs(scrollVelocity) > Math.abs(smoothedScrollVelocity)) {
            smoothedScrollVelocity = MathHelper.lerp(0.55f, smoothedScrollVelocity, scrollVelocity);
        } else {
            smoothedScrollVelocity = MathHelper.lerp(0.12f, smoothedScrollVelocity, scrollVelocity);
        }
        sidebarScrollOffset += (sidebarTargetScroll - sidebarScrollOffset) * 0.26f;

        float guiWidth = getGuiBaseWidth();
        float guiHeight = getGuiBaseHeight();
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        float targetScale = ClientSettings.getGuiScale();
        if (currentGuiScale <= 0.01f) {
            currentGuiScale = targetScale;
        } else if (Math.abs(currentGuiScale - targetScale) > 0.001f) {
            currentGuiScale = MathHelper.lerp(0.24f, currentGuiScale, targetScale);
            if (Math.abs(currentGuiScale - targetScale) < 0.002f) {
                currentGuiScale = targetScale;
            }
        }
        float scale = currentGuiScale;

        float cx = getGuiCenterX();
        float cy = getGuiCenterY();
        float smX = (mouseX - cx) / scale + cx;
        float smY = (mouseY - cy) / scale + cy;

        context.getMatrices().pushMatrix();
        if (Math.abs(scale - 1.0f) > 0.001f) {
            context.getMatrices().translate(cx, cy);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-cx, -cy);
        }

        if ("Простая".equals(animMode)) {
            renderDirectWindow(context, gx, gy, guiWidth, guiHeight, smX, smY, p, p);
            this.lateMouseX = smX;
            this.lateMouseY = smY;
            context.getMatrices().popMatrix();
            DrawBatcher.flushPending();
            return;
        }

        if ("Волна".equals(animMode)) {
            float waveP = p;
            float waveOffsetY = (1.0f - waveP) * 16.0f;
            float curGy = gy + waveOffsetY;
            float alphaVal = Math.clamp(waveP * 1.25f, 0.0f, 1.0f);

            Render2D.drawShadow(context, gx, curGy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 28.0f, ColorUtil.rgba(0, 0, 0, (int) (135 * alphaVal)));
            int mainBg = ColorUtil.withAlphaFloat(Theme.getWindowBg(), alphaVal);
            Render2D.drawRoundedRect(context, gx, curGy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, mainBg);
            Render2D.drawRoundedOutline(context, gx, curGy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (16 * alphaVal)));

            enableGuiScissor(context, gx, Math.max(0.0f, curGy), gx + guiWidth, curGy + guiHeight);
            renderWindowContent(context, gx, curGy, guiWidth, guiHeight, smX, smY, alphaVal);
            disableGuiScissor(context);

            this.lateMouseX = smX;
            this.lateMouseY = smY;
            context.getMatrices().popMatrix();
            DrawBatcher.flushPending();
            return;
        }

        float k = 1.0f - p;
        float lcx = lensOriginX;
        float lcy = lensOriginY;
        if (lcx < 0.0f || lcy < 0.0f) {
            lcx = gx + guiWidth / 2.0f;
            lcy = gy + guiHeight / 2.0f;
        }

        float maxR = (float) Math.hypot(guiWidth / 2.0f, guiHeight / 2.0f) * 1.05f;
        float R = LensParams.MIN_RADIUS + (maxR - LensParams.MIN_RADIUS) * p;

        float morphToRect = LensParams.smoothstep(0.65f, 0.96f, p);
        float curW = MathHelper.lerp(morphToRect, R * 2.0f, guiWidth);
        float curH = MathHelper.lerp(morphToRect, R * 2.0f, guiHeight);
        float curX = MathHelper.lerp(morphToRect, lcx - R, gx);
        float curY = MathHelper.lerp(morphToRect, lcy - R, gy);
        float curRadius = MathHelper.lerp(morphToRect, curH / 2.0f, Theme.WINDOW_RADIUS);

        if (p < 0.999f) {
            Render2D.drawShadow(context, curX, curY, curW, curH, curRadius, 28.0f, ColorUtil.rgba(0, 0, 0, (int) (135 * p)));
            int mainBg = ColorUtil.withAlphaFloat(Theme.getWindowBg(), p);
            Render2D.drawRoundedRect(context, curX, curY, curW, curH, curRadius, mainBg);
            Render2D.drawRoundedOutline(context, curX, curY, curW, curH, curRadius, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (16 * p)));

            float contentAlpha = LensParams.smoothstep(0.55f, 0.90f, p) * p;
            if (contentAlpha > 0.01f) {
                enableGuiScissor(context, curX, curY, curX + curW, curY + curH);
                renderWindowContent(context, gx, gy, guiWidth, guiHeight, smX, smY, contentAlpha);
                disableGuiScissor(context);
            }
        } else {
            renderDirectWindow(context, gx, gy, guiWidth, guiHeight, smX, smY, 1.0f, 1.0f);
        }

        this.lateMouseX = smX;
        this.lateMouseY = smY;
        context.getMatrices().popMatrix();
        DrawBatcher.flushPending();
    }

    private DrawContext lateContext;
    private double lateMouseX;
    private double lateMouseY;

    public void renderLate() {
        if (lateContext != null) {
            float scale = currentGuiScale;
            float cx = getGuiCenterX();
            float cy = getGuiCenterY();
            lateContext.getMatrices().pushMatrix();
            if (Math.abs(scale - 1.0f) > 0.001f) {
                lateContext.getMatrices().translate(cx, cy);
                lateContext.getMatrices().scale(scale, scale);
                lateContext.getMatrices().translate(-cx, -cy);
            }

            renderOpenDropdown(lateContext, lateMouseX, lateMouseY, 1.0f);
            updateAndRenderDeferredTooltip(lateContext, (float) lateMouseX, (float) lateMouseY);

            lateContext.getMatrices().popMatrix();
        }

        if (openColorPickerSetting != null || colorPopupAnim > 0.01f) {
            renderOpenColorPicker(null, lateMouseX, lateMouseY, 1.0f);
        }
        DrawBatcher.flushPending();
    }

    private void enableGuiScissor(DrawContext context, float x1, float y1, float x2, float y2) {
        Render2D.enableScissor(context, x1, y1, x2, y2);
    }

    private void disableGuiScissor(DrawContext context) {
        Render2D.disableScissor(context);
    }

    private void renderDirectWindow(DrawContext context, float gx, float gy, float guiWidth, float guiHeight, float mouseX, float mouseY, float containerAlpha, float contentAlpha) {
        Render2D.drawShadow(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 28.0f, ColorUtil.rgba(0, 0, 0, (int) (135 * containerAlpha)));
        int mainBg = ColorUtil.withAlphaFloat(Theme.getWindowBg(), containerAlpha);
        Render2D.drawRoundedRect(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, mainBg);
        Render2D.drawRoundedOutline(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (16 * containerAlpha)));

        enableGuiScissor(context, gx, gy, gx + guiWidth, gy + guiHeight);
        renderWindowContent(context, gx, gy, guiWidth, guiHeight, mouseX, mouseY, contentAlpha);
        disableGuiScissor(context);
    }

    private void renderWindowContent(DrawContext context, float gx, float gy, float guiWidth, float guiHeight, float mouseX, float mouseY, float alpha) {
        float collapseProgress = sidebarCollapseAnim.getValue();
        float sidebarWidth = MathHelper.lerp(collapseProgress, 132.0f, 36.0f);
        float headerHeight = 44.0f;
        deferredRailTooltipCat = null;

        if (ClientSettings.isCursorGlow() && mouseX >= gx && mouseX <= gx + guiWidth && mouseY >= gy && mouseY <= gy + guiHeight) {
            float distLeft = mouseX - gx;
            float distRight = (gx + guiWidth) - mouseX;
            float distTop = mouseY - gy;
            float distBottom = (gy + guiHeight) - mouseY;
            float edgeDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));

            float cornerRadius = 16.0f;
            if (distLeft < cornerRadius && distTop < cornerRadius) {
                float cx = gx + cornerRadius;
                float cy = gy + cornerRadius;
                edgeDist = Math.min(edgeDist, cornerRadius - (float) Math.hypot(cx - mouseX, cy - mouseY));
            } else if (distRight < cornerRadius && distTop < cornerRadius) {
                float cx = gx + guiWidth - cornerRadius;
                float cy = gy + cornerRadius;
                edgeDist = Math.min(edgeDist, cornerRadius - (float) Math.hypot(mouseX - cx, cy - mouseY));
            } else if (distLeft < cornerRadius && distBottom < cornerRadius) {
                float cx = gx + cornerRadius;
                float cy = gy + guiHeight - cornerRadius;
                edgeDist = Math.min(edgeDist, cornerRadius - (float) Math.hypot(cx - mouseX, mouseY - cy));
            } else if (distRight < cornerRadius && distBottom < cornerRadius) {
                float cx = gx + guiWidth - cornerRadius;
                float cy = gy + guiHeight - cornerRadius;
                edgeDist = Math.min(edgeDist, cornerRadius - (float) Math.hypot(mouseX - cx, mouseY - cy));
            }

            float fadeMargin = 40.0f;
            float edgeFade = Math.clamp(edgeDist / fadeMargin, 0.0f, 1.0f);
            edgeFade = edgeFade * edgeFade * (3.0f - 2.0f * edgeFade);

            float glowSpread = 16.0f;
            float glowSize = 36.0f;
            float glowRadius = 18.0f;
            float glowAlpha = 0.03f * edgeFade * alpha;
            if (glowAlpha > 0.002f) {
                Render2D.drawShadow(context, mouseX - glowRadius, mouseY - glowRadius, glowSize, glowSize, glowRadius, glowSpread, ColorUtil.withAlphaFloat(Theme.getAccent(), glowAlpha));
            }
        }

        enableGuiScissor(context, gx, gy, gx + sidebarWidth, gy + guiHeight);
        Render2D.drawRoundedRect(context, gx, gy, sidebarWidth + Theme.WINDOW_RADIUS, guiHeight, Theme.WINDOW_RADIUS, ColorUtil.withAlphaFloat(Theme.getSidebarBg(), alpha));
        disableGuiScissor(context);

        GuiDotGrid.render(context, gx + sidebarWidth, gy + headerHeight, guiWidth - sidebarWidth, guiHeight - headerHeight, mouseX, mouseY, alpha);

        int dividerColor = ColorUtil.withAlphaFloat(Theme.SIDEBAR_DIVIDER, alpha);
        Render2D.drawRect(context, gx + sidebarWidth, gy, 1.0f, guiHeight, dividerColor);
        Render2D.drawRect(context, gx + sidebarWidth, gy + headerHeight, guiWidth - sidebarWidth, 1.0f, dividerColor);

        renderSidebar(context, gx, gy, sidebarWidth, guiHeight, mouseX, mouseY, alpha, collapseProgress);

        renderHeader(context, gx + sidebarWidth, gy, guiWidth - sidebarWidth, headerHeight, mouseX, mouseY, alpha);

        float contentX = gx + sidebarWidth + 14.0f;
        float contentY = gy + headerHeight + 10.0f;
        float contentW = guiWidth - sidebarWidth - 28.0f;
        float contentH = guiHeight - headerHeight - 16.0f;

        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.render(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        } else if (selectedCategory == Category.COSMETICS) {
            egg.client.ui.section.CosmeticsPanel.render(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        } else if (selectedCategory == Category.SETTINGS) {
            renderClientSettingsPanel(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        } else {
            renderModules(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        }

        if (collapseProgress < 0.2f) {
            float searchPopupW = sidebarWidth - 16.0f;
            renderSearchMatchesPopup(context, gx + 8.0f, gy + 60.0f, searchPopupW, mouseX, mouseY, alpha);
        }

        if (collapseProgress > 0.6f && deferredRailTooltipCat != null) {
            float ttAlpha = Math.clamp((collapseProgress - 0.6f) / 0.4f, 0.0f, 1.0f) * alpha;
            float ttX = gx + sidebarWidth + 8.0f;
            float ttY = Math.clamp(deferredRailTooltipY + 1.0f, gy + 6.0f, gy + guiHeight - 24.0f);
            String ttText = deferredRailTooltipCat.getName() + " (" + getCategoryModuleCount(deferredRailTooltipCat) + ")";
            float ttW = ClientFont.getWidth(ttText, ClientFont.SF_MEDIUM) + 14.0f;
            float ttH = 20.0f;
            Render2D.drawShadow(context, ttX, ttY, ttW, ttH, 5.0f, 8.0f, ColorUtil.rgba(0, 0, 0, (int) (140 * ttAlpha)));
            Render2D.drawRoundedRect(context, ttX, ttY, ttW, ttH, 5.0f, ColorUtil.rgba(24, 24, 27, (int) (250 * ttAlpha)));
            Render2D.drawRoundedOutline(context, ttX, ttY, ttW, ttH, 5.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (20 * ttAlpha)));
            ClientFont.draw(context, ttText, ttX + 7.0f, ttY + 5.5f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, ttAlpha));
        }
    }

    private void renderSidebar(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha, float collapseProgress) {
        float logoAreaH = 34.0f;
        renderLogo(context, x, y, width, logoAreaH, mouseX, mouseY, alpha, collapseProgress);

        float searchH = 22.0f * (1.0f - collapseProgress);
        float searchY = y + logoAreaH + 2.0f * (1.0f - collapseProgress);
        float searchAlpha = Math.max(0.0f, 1.0f - collapseProgress * 2.5f) * alpha;
        if (searchAlpha > 0.01f) {
            renderSidebarSearch(context, x + 8.0f, searchY, width - 16.0f, searchH, searchAlpha, mouseX, mouseY);
        }

        float listStartY = searchY + searchH + MathHelper.lerp(collapseProgress, 4.0f, 4.0f);
        enableGuiScissor(context, x, listStartY, x + width, y + height);

        float startItemY = listStartY + 4.0f;
        float itemY = startItemY - sidebarScrollOffset;
        float itemPad = MathHelper.lerp(collapseProgress, 8.0f, 4.0f);
        float itemW = width - (itemPad * 2.0f);
        float itemH = 22.0f;

        boolean isSearchOpen = collapseProgress < 0.2f && searchFocused && !searchQuery.trim().isEmpty() && selectedSearchModule == null;
        float listAlpha = isSearchOpen ? alpha * 0.35f : alpha;

        String lastSection = "";
        Category[] categories = Category.values();
        boolean isWave = "Волна".equals(ClientSettings.getOpenAnimationMode());
        float pAnim = this.currentAnimProgress;

        int activePillColor = ColorUtil.withAlphaFloat(Theme.SIDEBAR_ACTIVE_PILL, listAlpha);
        float pillW = MathHelper.lerp(collapseProgress, width - 16.0f, 24.0f);
        float pillX = MathHelper.lerp(collapseProgress, x + 8.0f, x + (width - 24.0f) / 2.0f);
        float pillY = categoryPillY.getValue();
        if (pillY > 0.0f) {
            Render2D.drawRoundedRect(context, pillX, pillY, pillW, categoryPillHeight.getValue(), 6.0f, activePillColor);
            Render2D.drawRoundedOutline(context, pillX, pillY, pillW, categoryPillHeight.getValue(), 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (14 * listAlpha)));
        }

        for (Category cat : categories) {
            if (!cat.getSection().equals(lastSection)) {
                lastSection = cat.getSection();
                if (itemY > startItemY - sidebarScrollOffset + 2.0f) {
                    itemY += MathHelper.lerp(collapseProgress, 8.0f, 2.0f);
                }
                float secAlpha = Math.max(0.0f, 1.0f - collapseProgress * 2.0f) * listAlpha;
                if (secAlpha > 0.01f && !FpsBenchmark.overrideNoText) {
                    ClientFont.draw(context, lastSection, x + 14.0f, itemY, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, secAlpha));
                }
                itemY += MathHelper.lerp(collapseProgress, 13.0f, 4.0f);
            }

            float catWaveY = 0.0f;
            if (isWave && pAnim < 0.999f) {
                float wavePhase = Math.max(0.0f, pAnim * (float) Math.PI * 1.8f - (float) cat.ordinal() * 0.15f);
                catWaveY = (1.0f - pAnim) * 6.0f * Math.max(0.0f, (float) Math.sin(wavePhase));
            }
            float curItemY = itemY + catWaveY;

            boolean isSelected = (cat == selectedCategory);
            boolean isHovered = mouseX >= x && mouseX <= (x + width) &&
                    mouseY >= listStartY && mouseY <= (y + height) &&
                    mouseY >= curItemY && mouseY <= (curItemY + itemH);

            if (isHovered && collapseProgress > 0.6f) {
                deferredRailTooltipCat = cat;
                deferredRailTooltipY = curItemY;
            }

            Animation hoverAnim = categoryHoverAnims.get(cat);
            if (hoverAnim != null) {
                hoverAnim.setTarget(isHovered && !isSelected ? 1.0f : 0.0f);
            }
            float hoverProgress = (hoverAnim != null) ? hoverAnim.getValue() : 0.0f;

            if (isSelected) {
                categoryPillY.setTarget(curItemY);
                categoryPillHeight.setTarget(itemH);
            } else if (hoverProgress > 0.01f) {
                int hoverBg = ColorUtil.withAlphaFloat(Theme.SIDEBAR_HOVER_PILL, hoverProgress * listAlpha);
                float hovW = MathHelper.lerp(collapseProgress, width - 16.0f, 24.0f);
                float hovX = MathHelper.lerp(collapseProgress, x + 8.0f, x + (width - 24.0f) / 2.0f);
                Render2D.drawRoundedRect(context, hovX, curItemY, hovW, itemH, 6.0f, hoverBg);
            }

            int baseColor = isSelected ? Theme.TEXT_TITLE : Theme.TEXT_MUTED;
            int finalColor = ColorUtil.interpolate(baseColor, Theme.TEXT_TITLE, hoverProgress);
            finalColor = ColorUtil.withAlphaFloat(finalColor, listAlpha);

            float iconSize = 12.0f;
            float iconX = MathHelper.lerp(collapseProgress, x + 8.0f + 7.0f, x + (width - iconSize) / 2.0f);
            float iconY = curItemY + (itemH - iconSize) / 2.0f;
            int iconCol = isSelected ? ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, listAlpha) : finalColor;
            IconRenderer.drawCategoryIcon(context, cat, iconX, iconY, iconSize, iconCol);

            float textAlpha = Math.max(0.0f, 1.0f - collapseProgress * 2.0f) * listAlpha;
            if (textAlpha > 0.01f && !FpsBenchmark.overrideNoText) {
                float textX = x + 8.0f + 7.0f + iconSize + 7.0f;
                float textY = curItemY + (itemH - 9.0f) / 2.0f + 0.5f;
                int labelCol = isSelected ? ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, textAlpha) : ColorUtil.withAlphaFloat(finalColor, textAlpha);
                ClientFont.draw(context, cat.getName(), textX, textY, isSelected ? ClientFont.SF_MEDIUM : ClientFont.SF_REGULAR, labelCol);

                initCategoryData();
                String countStr = CATEGORY_COUNT_STRINGS[cat.ordinal()];
                float countW = CATEGORY_COUNT_WIDTHS[cat.ordinal()];
                float countAlpha = Math.max(0.0f, 1.0f - collapseProgress * 2.5f) * listAlpha;
                if (countAlpha > 0.01f) {
                    int countCol = isSelected ? ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, countAlpha) : ColorUtil.withAlphaFloat(Theme.TEXT_DIM, countAlpha);
                    ClientFont.draw(context, countStr, x + width - countW - 13.0f, textY, ClientFont.SF_REGULAR, countCol);
                }
            }

            itemY += itemH + 3.0f;
        }

        disableGuiScissor(context);

        float totalItemsH = (itemY + sidebarScrollOffset) - startItemY + 6.0f;
        float viewportH = height - listStartY;
        sidebarMaxScroll = Math.max(0.0f, totalItemsH - viewportH + 6.0f);
        sidebarTargetScroll = Math.clamp(sidebarTargetScroll, 0.0f, sidebarMaxScroll);

        if (sidebarMaxScroll > 0.0f && collapseProgress < 0.6f) {
            float sbarW = 2.0f;
            float sbarPad = 2.0f;
            float trackH = viewportH - 8.0f;
            float thumbH = Math.max(16.0f, trackH * (viewportH / totalItemsH));
            float thumbY = listStartY + 4.0f + (sidebarScrollOffset / sidebarMaxScroll) * (trackH - thumbH);
            Render2D.drawRoundedRect(context, x + width - sbarW - sbarPad, thumbY, sbarW, thumbH, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (40 * alpha)));
        }
    }

    private void renderLogo(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha, float collapseProgress) {
        float logoAlpha = Math.max(0.0f, 1.0f - collapseProgress * 2.2f) * alpha;
        if (logoAlpha > 0.01f) {
            float size = 16.0f;
            float iconX = x + 10.0f;
            float iconY = y + (height - size) / 2.0f;
            long now = System.nanoTime();
            float elapsedMs = (now - animStartTime) / 1_000_000.0f;
            boolean animsEnabled = ClientSettings.animationsEnabled() && !"Выкл".equals(ClientSettings.getOpenAnimationMode());

            float speedMult = ClientSettings.getSpeedMultiplier();
            float speedFactor = (float) Math.sqrt(Math.clamp(speedMult, 0.4f, 2.5f));
            float charDelayMs = 45.0f / speedFactor;
            float charDurationMs = 520.0f / speedFactor;
            float iconDurationMs = 560.0f / speedFactor;

            float iconWave = 0.0f;
            if (!closing && animsEnabled && elapsedMs < iconDurationMs) {
                float iconP = Math.clamp(elapsedMs / iconDurationMs, 0.0f, 1.0f);
                iconWave = -(float) Math.sin(iconP * Math.PI) * (1.0f - iconP * 0.35f) * 2.8f;
            }
            int eggColor = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, logoAlpha);
            IconRenderer.drawTextureScaled(context, LOGO_TEXTURE, iconX, iconY + iconWave, size, size, 128, 128, eggColor);

            if (!FpsBenchmark.overrideNoText) {
                float textX = iconX + size + 7.0f;
                float textY = iconY + (size - ClientFont.FONT_HEIGHT) / 2.0f + 0.5f;
                String logoText = "Egg Visuals";
                int grad1 = ColorUtil.interpolate(0xFFFFFFFF, Theme.getGradient1(), 0.25f);
                int grad2 = ColorUtil.interpolate(0xFFFFFFFF, Theme.getGradient2(), 0.60f);
                float curCharX = textX;
                for (int i = 0; i < logoText.length(); i++) {
                    String ch = String.valueOf(logoText.charAt(i));
                    float ratio = logoText.length() > 1 ? (float) i / (float) (logoText.length() - 1) : 0.0f;
                    int charColor = ColorUtil.interpolate(grad1, grad2, ratio);

                    float charWave = 0.0f;
                    float charAlpha = logoAlpha;
                    if (!closing && animsEnabled) {
                        float delay = (float) i * charDelayMs;
                        if (elapsedMs < delay) {
                            charAlpha = 0.0f;
                        } else if (elapsedMs < delay + charDurationMs) {
                            float charP = (elapsedMs - delay) / charDurationMs;
                            float wave = (float) Math.sin(charP * Math.PI) * (1.0f - charP * 0.30f);
                            charWave = -wave * 3.6f;
                            float inEase = (float) Math.sin(Math.min(1.0f, charP * 1.6f) * Math.PI * 0.5f);
                            charAlpha = logoAlpha * inEase;
                        }
                    }

                    if (" ".equals(ch)) {
                        curCharX += ClientFont.getWidth(" ", ClientFont.SF_BOLD);
                        continue;
                    }

                    int finalCharColor = ColorUtil.withAlphaFloat(charColor, charAlpha);

                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(curCharX, textY + charWave);
                    ClientFont.draw(context, ch, 0.0f, 0.0f, ClientFont.SF_BOLD, finalCharColor);
                    context.getMatrices().popMatrix();

                    curCharX += ClientFont.getWidth(ch, ClientFont.SF_BOLD);
                }
            }
        }

        float glyphW = 13.0f;
        float glyphH = 10.0f;
        float glyphX = MathHelper.lerp(collapseProgress, x + width - glyphW - 10.0f, x + (width - glyphW) / 2.0f);
        float glyphY = y + (height - glyphH) / 2.0f;

        boolean btnHovered = (mouseX >= glyphX - 4.0f && mouseX <= glyphX + glyphW + 4.0f && mouseY >= glyphY - 4.0f && mouseY <= glyphY + glyphH + 4.0f)
                || (collapseProgress > 0.5f && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height);
        int glyphCol = btnHovered ? Theme.TEXT_TITLE : (collapseProgress > 0.5f ? Theme.TEXT_MUTED : Theme.TEXT_DIM);
        glyphCol = ColorUtil.withAlphaFloat(glyphCol, alpha);

        if (btnHovered) {
            Render2D.drawRoundedRect(context, glyphX - 3.0f, glyphY - 3.0f, glyphW + 6.0f, glyphH + 6.0f, 4.0f, ColorUtil.rgba(255, 255, 255, (int) (14 * alpha)));
        }

        IconRenderer.drawCollapseIcon(context, glyphX + glyphW / 2.0f, glyphY + glyphH / 2.0f, collapseProgress > 0.5f, glyphCol);
    }

    private void renderSidebarSearch(DrawContext context, float searchX, float searchY, float searchW, float searchH, float alpha, float mouseX, float mouseY) {
        searchFocusAnim.setTarget(searchFocused ? 1.0f : 0.0f);
        float focusVal = searchFocusAnim.getValue();

        int searchBg = ColorUtil.rgba(20, 20, 27, (int) (255 * alpha));
        int normalBorder = ColorUtil.rgba(255, 255, 255, (int) (18 * alpha));
        int focusedBorder = ColorUtil.rgba(120, 130, 255, (int) (140 * alpha));
        int searchBorder = ColorUtil.interpolate(normalBorder, focusedBorder, focusVal);

        Render2D.drawRoundedRect(context, searchX, searchY, searchW, searchH, 5.0f, searchBg);
        Render2D.drawRoundedOutline(context, searchX, searchY, searchW, searchH, 5.0f, 1.0f, searchBorder);

        int iconColor = ColorUtil.interpolate(
                ColorUtil.rgba(255, 255, 255, (int) (90 * alpha)),
                ColorUtil.withAlphaFloat(Theme.getAccent(), alpha),
                focusVal
        );
        IconRenderer.drawSearchIcon(context, searchX + 6.0f, searchY + (searchH - 9.0f) / 2.0f, 9.0f, iconColor);

        float textX = searchX + 19.0f;
        float searchTextY = searchY + (searchH - 9.0f) / 2.0f + 0.5f;
        if (!FpsBenchmark.overrideNoText) {
            if (searchQuery.isEmpty()) {
                ClientFont.draw(context, "Search...", textX, searchTextY, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha));
                if (searchFocused) {
                    float blink = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() - lastBlinkTime) * 0.007);
                    int caretColor = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * blink);
                    Render2D.drawRect(context, textX, searchTextY - 1.0f, 1.0f, 10.0f, caretColor);
                }
            } else {
                if (searchFocused && searchCursor != searchSelectionEnd) {
                    int selStart = Math.min(searchCursor, searchSelectionEnd);
                    int selEnd = Math.max(searchCursor, searchSelectionEnd);
                    float selX1 = textX + (selStart > 0 ? ClientFont.getWidth(searchQuery.substring(0, selStart), ClientFont.SF_REGULAR) : 0.0f);
                    float selX2 = textX + ClientFont.getWidth(searchQuery.substring(0, selEnd), ClientFont.SF_REGULAR);
                    int selBg = ColorUtil.withAlpha(Theme.getAccent(), (int) (130 * alpha));
                    Render2D.drawRect(context, selX1, searchTextY - 1.5f, selX2 - selX1, 11.0f, selBg);
                }

                ClientFont.draw(context, searchQuery, textX, searchTextY, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

                if (searchFocused) {
                    int cPos = Math.clamp(searchCursor, 0, searchQuery.length());
                    float cursorOffset = cPos > 0 ? ClientFont.getWidth(searchQuery.substring(0, cPos), ClientFont.SF_REGULAR) : 0.0f;
                    float blink = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() - lastBlinkTime) * 0.007);
                    int caretColor = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * blink);
                    Render2D.drawRect(context, textX + cursorOffset, searchTextY - 1.0f, 1.0f, 10.0f, caretColor);
                }
            }

            if (!searchQuery.isEmpty()) {
                float clearSize = 14.0f;
                float clearX = searchX + searchW - clearSize - 4.5f;
                float clearY = searchY + (searchH - clearSize) / 2.0f;
                boolean isClearHov = mouseX >= clearX - 2.0f && mouseX <= clearX + clearSize + 2.0f && mouseY >= clearY - 2.0f && mouseY <= clearY + clearSize + 2.0f;
                if (isClearHov) {
                    Render2D.drawRoundedRect(context, clearX, clearY, clearSize, clearSize, clearSize / 2.0f, ColorUtil.rgba(255, 255, 255, (int) (24 * alpha)));
                }
                int crossCol = isClearHov ? ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)) : ColorUtil.rgba(160, 160, 175, (int) (180 * alpha));
                IconRenderer.drawCrossIcon(context, clearX + clearSize / 2.0f, clearY + clearSize / 2.0f, 8.0f, crossCol);
            }
        }
    }

    private int getCategoryModuleCount(Category cat) {
        initCategoryData();
        return CATEGORY_MODULE_COUNTS[cat.ordinal()];
    }

    private void renderHeader(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        float titleFade = headerFadeAnim.getValue();
        float slideX = (1.0f - titleFade) * 6.0f;
        int titleColor = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha);
        int dimColor = ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha);

        if (!FpsBenchmark.overrideNoText) {
            float iconSize = 10.0f;
            float iconX = (float) Math.round(x + 16.0f + slideX);
            float iconY = (float) Math.round(y + 5.5f);
            IconRenderer.drawCategoryIcon(context, selectedCategory, iconX, iconY, iconSize, dimColor);

            initCategoryData();
            String breadcrumb = CATEGORY_BREADCRUMBS[selectedCategory.ordinal()];
            float textX = iconX + iconSize + 5.0f;
            float textY = (float) Math.round(y + 7.0f);
            ClientFont.draw(context, breadcrumb, textX, textY, ClientFont.SF_REGULAR, dimColor);

            String title = selectedCategory == Category.SETTINGS ? "Настройки" : selectedCategory.getName();
            float titleX = (float) Math.round(x + 16.0f + slideX);
            float titleY = (float) Math.round(y + 22.0f);
            ClientFont.draw(context, title, titleX, titleY, ClientFont.SF_MEDIUM, titleColor);
        }
    }

    private static final Map<String, String[]> MODULE_KEYWORDS = new HashMap<>();
    static {

        MODULE_KEYWORDS.put("HitColor", new String[]{"хитколор", "цвет удара", "хит", "вспышка", "урон", "хит колор", "hit color", "hit"});
        MODULE_KEYWORDS.put("TapeMouse", new String[]{"тейп", "кликер", "автокликер", "мышь", "зажим", "tape mouse", "clicker"});
        MODULE_KEYWORDS.put("ShiftTAP", new String[]{"шифт", "шифттап", "тап", "сброс кб", "shift tap"});
        MODULE_KEYWORDS.put("AutoSwap", new String[]{"автосвап", "свап", "свапер", "офхенд", "тотем", "сфера", "левая рука", "щит", "auto swap", "autoswap", "swap"});
        MODULE_KEYWORDS.put("NoFriendDamage", new String[]{"друг", "друзья", "без урона", "защита тиммейтов", "тимейт", "no friend damage"});

        MODULE_KEYWORDS.put("Sprint", new String[]{"спринт", "бег", "автобег", "разгон", "sprint", "run"});
        MODULE_KEYWORDS.put("Fast Place", new String[]{"фаст плейс", "блоки", "строительство", "fast place", "fastplace"});

        MODULE_KEYWORDS.put("Interface", new String[]{"интерфейс", "худ", "ватермарка", "айленд", "остров", "динамик", "музыка", "трек", "бар", "watermark", "island"});
        MODULE_KEYWORDS.put("Keybinds", new String[]{"бинды", "бинд", "клавиши", "кнопки", "keybinds", "keys", "binds"});
        MODULE_KEYWORDS.put("EventTracker", new String[]{"ивенты", "мистики", "мистик", "радар", "компас", "сундук", "маяк", "босс", "метеорит", "алтарь", "вулкан", "посылка", "груз", "корабль", "event", "events", "radar", "tracker"});
        MODULE_KEYWORDS.put("MotionBlur", new String[]{"моушен", "блюр", "моушен блюр", "размытие", "смазывание", "лунар", "натурал", "камера", "motion", "blur", "motion blur", "lunar", "natural"});
        MODULE_KEYWORDS.put("PotionHUD", new String[]{"зелья", "эффекты", "баффы", "поушн", "зельки", "hud", "potion"});
        MODULE_KEYWORDS.put("ArmorHUD", new String[]{"броня", "бронь", "шлем", "нагрудник", "поножи", "ботинки", "armor", "hud", "армор"});
        MODULE_KEYWORDS.put("FullBright", new String[]{"яркость", "свет", "гамма", "ночное видение", "ночь", "фулбрайт", "full bright"});
        MODULE_KEYWORDS.put("NoRender", new String[]{"норендер", "тыква", "огонь", "тотем", "слепота", "эффекты", "но рендер", "no render"});
        MODULE_KEYWORDS.put("Glow", new String[]{"свечение", "глов", "обводка", "подсветка", "аура", "glow"});
        MODULE_KEYWORDS.put("NameTags", new String[]{"ники", "неймтаги", "имена", "таблички", "хп игроков", "name tags"});
        MODULE_KEYWORDS.put("Crosshair", new String[]{"прицел", "крестик", "точка", "кастом прицел", "crosshair"});
        MODULE_KEYWORDS.put("Hitmarker", new String[]{"хитмаркер", "маркер удара", "звук удара", "колокольчик", "hitmarker"});
        MODULE_KEYWORDS.put("HitSounds", new String[]{"хитсаунд", "хит саунд", "звук удара", "звуки попадания", "звук попадания", "hitsounds", "hitsound", "skeet", "neverlose"});
        MODULE_KEYWORDS.put("ItemPhysic", new String[]{"физика", "дроп", "вещи", "предметы", "реалистичный дроп", "item physic"});
        MODULE_KEYWORDS.put("TargetESP", new String[]{"таргет", "есп", "снежинки", "созвездие", "нейросеть", "нити", "паутинка", "симбиот", "вх", "бокс", "души", "кубы", "таргет есп", "target esp", "esp"});
        MODULE_KEYWORDS.put("ViewModel", new String[]{"вьюмодель", "руки", "положение", "оружие", "вид рук", "рука", "view model"});
        MODULE_KEYWORDS.put("AspectRatio", new String[]{"аспект", "соотношение", "растяг", "экран", "4:3", "разрешение", "aspect ratio"});
        MODULE_KEYWORDS.put("BreakBlock", new String[]{"брейк", "ломание", "прогресс", "анимация ломания", "майнинг", "брейкер", "радиальный", "radialmining", "block breaker", "break block"});
        MODULE_KEYWORDS.put("Block Overlay", new String[]{"блок", "блок оверлей", "оверлей", "подсветка блока", "выделение", "шейдер", "неон", "куб", "block overlay", "overlay"});
        MODULE_KEYWORDS.put("HpCounter", new String[]{"хп", "хпшка", "здоровье", "сердечки", "жизни", "хп каунтер", "hp counter"});
        MODULE_KEYWORDS.put("SmoothEat", new String[]{"еда", "анимация еды", "плавная еда", "кушать", "smooth eat"});
        MODULE_KEYWORDS.put("Trajectories", new String[]{"траектории", "предикшен", "стрелы", "перлы", "зелья", "прицел", "траектория", "арбалет", "prediction", "trajectories"});
                MODULE_KEYWORDS.put("ItemESP", new String[]{"предметы", "айтем", "item", "item esp", "esp", "лут", "дроп", "вещи", "items", "loot", "drop", "глитч", "оболочка"});
MODULE_KEYWORDS.put("CustomHitbox", new String[]{"хитбокс", "хитбоксы", "customhitbox", "hitbox", "обводка", "заливка", "бокс", "кубы", "hitboxes"});

        MODULE_KEYWORDS.put("FastEXP", new String[]{"опыт", "экспа", "пузырьки", "быстрый опыт", "fast exp"});
        MODULE_KEYWORDS.put("AutoAccept", new String[]{"автопринятие", "тп", "телепорт", "принять", "пати", "auto accept"});
        MODULE_KEYWORDS.put("ItemScroller", new String[]{"скроллер", "быстрое перемещение", "лут", "колесико", "инвентарь", "item scroller"});
        MODULE_KEYWORDS.put("SoundReducer", new String[]{"звуки", "уменьшение звуков", "тишина", "громкость", "sound reducer"});
        MODULE_KEYWORDS.put("ThirdPerson", new String[]{"третье лицо", "вид сзади", "камера", "ф5", "f5", "third person"});

        MODULE_KEYWORDS.put("ClientSettings", new String[]{"настройки", "клиент", "тема", "блюр", "шрифт", "акцент", "цвета", "звук", "громкость", "звук меню", "client settings"});
        MODULE_KEYWORDS.put("StreamerMode", new String[]{"стример", "стрим", "скрыть ник", "ютуб", "защита", "streamer mode"});
        MODULE_KEYWORDS.put("Server Assistant", new String[]{"server assistant", "сервер ассистент", "ассистент", "донки", "трапка", "аукцион", "донат"});

    }

    private boolean matchesModule(Module m, String q, String qEn, String qRu) {
        String name = m.getName().toLowerCase();
        String catName = m.getCategory().getName().toLowerCase();
        String catRu = m.getCategory().getDisplayNameRu().toLowerCase();

        if (name.contains(q) || catName.contains(q) || catRu.contains(q)) return true;
        if (!qEn.isEmpty() && (name.contains(qEn) || catName.contains(qEn) || catRu.contains(qEn))) return true;
        if (!qRu.isEmpty() && (name.contains(qRu) || catName.contains(qRu) || catRu.contains(qRu))) return true;

        String[] kws = MODULE_KEYWORDS.get(m.getName());
        if (kws != null) {
            for (String kw : kws) {
                String kwLow = kw.toLowerCase();
                if (kwLow.contains(q) || q.contains(kwLow)) return true;
                if (!qEn.isEmpty() && kwLow.contains(qEn)) return true;
                if (!qRu.isEmpty() && kwLow.contains(qRu)) return true;
            }
        }
        return false;
    }

    private List<Module> getSearchMatches() {
        if (lastSearchQuery != null && lastSearchQuery.equals(searchQuery)) {
            return cachedSearchMatches;
        }
        cachedSearchMatches.clear();
        lastSearchQuery = searchQuery;
        if (searchQuery.trim().isEmpty()) return cachedSearchMatches;
        List<Module> all = EggClient.getInstance().getModuleManager().getModules();
        String q = searchQuery.toLowerCase().trim();
        String qEn = LayoutHelper.toEn(q).toLowerCase().trim();
        String qRu = LayoutHelper.toRu(q).toLowerCase().trim();
        for (Module m : all) {
            if (!m.isBlocked() && matchesModule(m, q, qEn, qRu)) {
                cachedSearchMatches.add(m);
            }
        }
        return cachedSearchMatches;
    }

    private void renderSearchMatchesPopup(DrawContext context, float x, float y, float width, float mouseX, float mouseY, float alpha) {
        if (!searchFocused || searchQuery.trim().isEmpty() || selectedSearchModule != null) return;
        List<Module> matches = getSearchMatches();
        int count = matches.size();

        int showCount = Math.min(5, count);
        float itemH = 27.0f;
        float popH = showCount > 0 ? (showCount * itemH + 8.0f) : 28.0f;

        if (ClientSettings.isShadowEnabled() && !FpsBenchmark.overrideNoShadow) {
            Render2D.drawShadow(context, x, y + 6.0f, width, popH, 8.0f, 16.0f, ColorUtil.rgba(0, 0, 0, (int) (190 * alpha)));
        }
        Render2D.drawRoundedRect(context, x, y, width, popH, 8.0f, ColorUtil.rgba(23, 23, 31, (int) (255 * alpha)));
        Render2D.drawRoundedOutline(context, x, y, width, popH, 8.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (23 * alpha)));

        if (count == 0) {
            if (!FpsBenchmark.overrideNoText) {
                String noMatchText = "Ничего не найдено";
                float scale = 0.88f;
                float textW = ClientFont.getWidth(noMatchText, ClientFont.SF_REGULAR) * scale;
                float textX = x + (width - textW) / 2.0f;
                float textY = y + (popH - ClientFont.FONT_HEIGHT * scale) / 2.0f;
                int textCol = ColorUtil.withAlphaFloat(ColorUtil.rgb(114, 114, 127), alpha);
                ClientFont.drawScaled(context, noMatchText, textX, textY, scale, ClientFont.SF_REGULAR, textCol);
            }
        } else {
            if (searchSelectedIndex >= showCount) {
                searchSelectedIndex = Math.max(0, showCount - 1);
            }

            float itemX = x + 4.0f;
            float itemW = width - 8.0f;
            float targetPillY = y + 4.0f + searchSelectedIndex * itemH;
            searchPillY.setTarget(targetPillY);
            if (searchPillY.getValue() <= 0.0f) {
                searchPillY.setValue(targetPillY);
            }

            Render2D.drawRoundedRect(context, itemX, searchPillY.getValue(), itemW, itemH, 5.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)));

            for (int i = 0; i < showCount; i++) {
                Module m = matches.get(i);
                float itemY = y + 4.0f + i * itemH;
                boolean isSelected = (i == searchSelectedIndex);

                boolean isHov = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemY && mouseY <= itemY + itemH;
                if (isHov && searchSelectedIndex != i) {
                    searchSelectedIndex = i;
                }

                float iconSize = 12.0f;
                float iconX = itemX + 6.0f;
                float iconY = itemY + (itemH - iconSize) / 2.0f;
                int iconCol = isSelected ? ColorUtil.withAlphaFloat(Theme.getAccent(), alpha) : ColorUtil.rgba(140, 140, 150, (int) (200 * alpha));
                IconRenderer.drawCategoryIcon(context, m.getCategory(), iconX, iconY, iconSize, iconCol);

                if (!FpsBenchmark.overrideNoText) {
                    float textX = itemX + 22.0f;
                    float rightPad = m.isEnabled() ? 14.0f : 4.0f;
                    float titleMaxW = itemW - 22.0f - rightPad;
                    String title = getTruncatedLabel(m.getName(), titleMaxW, ClientFont.SF_MEDIUM);
                    int titleColor = ColorUtil.withAlphaFloat(ColorUtil.rgb(242, 242, 247), alpha);
                    ClientFont.draw(context, title, textX, itemY + 4.5f, ClientFont.SF_MEDIUM, titleColor);

                    String catName = m.getCategory().getName();
                    int catColor = ColorUtil.withAlphaFloat(ColorUtil.rgb(120, 120, 133), isSelected ? alpha * 0.95f : alpha * 0.75f);
                    ClientFont.drawScaled(context, catName, textX, itemY + 16.5f, 0.78f, ClientFont.SF_REGULAR, catColor);
                }

                if (m.isEnabled()) {
                    float dotSize = 4.0f;
                    float dotX = itemX + itemW - dotSize - 5.0f;
                    float dotY = itemY + 8.5f;
                    Render2D.drawRoundedRect(context, dotX, dotY, dotSize, dotSize, dotSize / 2.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
                }
            }
        }
    }

    private float calculateSettingsGroupHeight(List<Setting> settings) {
        float h = 34.0f;
        for (Setting s : settings) {
            float progress = s.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            if (s instanceof BooleanSetting) {
                h += 26.0f * progress;
            } else if (s instanceof SliderSetting) {
                h += 34.0f * progress;
            } else if (s instanceof ModeSetting) {
                h += 28.0f * progress;
            } else if (s instanceof ColorSetting) {
                h += 26.0f * progress;
            } else if (s instanceof BindSetting) {
                h += 28.0f * progress;
            }
        }
        return h + 6.0f;
    }

    private void renderClientSettingsPanel(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        enableGuiScissor(context, x - 2.0f, y, x + width + 4.0f, y + height);

        ClientSettings cs = ModuleManager.getInstance().getModule(ClientSettings.class);
        if (cs == null) {
            disableGuiScissor(context);
            return;
        }

        float currentY = y - scrollOffset;
        float colGap = 8.0f;
        float cardW = (width - colGap) / 2.0f;

        float col0X = x;
        float col1X = x + cardW + colGap;

        float col0Y = currentY;
        float col1Y = currentY;

        ensureClientSettingsSplit(cs);
        List<Setting> leftSettings = cachedClientSettingsLeft;
        List<Setting> rightSettings = cachedClientSettingsRight;

        float leftH = calculateSettingsGroupHeight(leftSettings);
        float rightH = calculateSettingsGroupHeight(rightSettings);

        boolean blurEnabled = ClientSettings.isScrollMotionBlurEnabled();
        float speed = Math.abs(smoothedScrollVelocity);
        float blurFactor = blurEnabled ? Math.clamp(speed / 3.0f, 0.0f, 1.0f) : 0.0f;
        float trailDist = Math.clamp(smoothedScrollVelocity * 1.85f, -24.0f, 24.0f);

        if (blurFactor > 0.02f) {
            float wake0Y = col0Y + trailDist * 0.70f;
            float wake1Y = col1Y + trailDist * 0.70f;
            int wakeBg = ColorUtil.withAlphaFloat(Theme.getCardBg(), alpha * 0.50f * blurFactor);
            int wakeBorder = ColorUtil.withAlphaFloat(Theme.CARD_BORDER, alpha * 0.60f * blurFactor);
            int accentGlow = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * 0.25f * blurFactor);

            Render2D.drawRoundedRect(context, col0X, wake0Y, cardW, leftH, Theme.CARD_RADIUS, wakeBg);
            Render2D.drawRoundedOutline(context, col0X, wake0Y, cardW, leftH, Theme.CARD_RADIUS, 1.0f, wakeBorder);
            Render2D.drawRoundedOutline(context, col0X - 0.5f, wake0Y - 0.5f, cardW + 1.0f, leftH + 1.0f, Theme.CARD_RADIUS, 1.0f, accentGlow);

            Render2D.drawRoundedRect(context, col1X, wake1Y, cardW, rightH, Theme.CARD_RADIUS, wakeBg);
            Render2D.drawRoundedOutline(context, col1X, wake1Y, cardW, rightH, Theme.CARD_RADIUS, 1.0f, wakeBorder);
            Render2D.drawRoundedOutline(context, col1X - 0.5f, wake1Y - 0.5f, cardW + 1.0f, rightH + 1.0f, Theme.CARD_RADIUS, 1.0f, accentGlow);
        }

        renderSettingsCard(context, "Оформление", leftSettings, col0X, col0Y, cardW, leftH, mouseX, mouseY, alpha);
        renderSettingsCard(context, "Интерфейс", rightSettings, col1X, col1Y, cardW, rightH, mouseX, mouseY, alpha);

        if (blurFactor > 0.02f) {
            renderSettingsCardTrail(context, "Оформление", col0X, col0Y + trailDist * 0.32f, cardW, leftH, alpha * 0.44f * blurFactor);
            renderSettingsCardTrail(context, "Оформление", col0X, col0Y + trailDist * 0.65f, cardW, leftH, alpha * 0.22f * blurFactor);
            renderSettingsCardTrail(context, "Интерфейс", col1X, col1Y + trailDist * 0.32f, cardW, rightH, alpha * 0.44f * blurFactor);
            renderSettingsCardTrail(context, "Интерфейс", col1X, col1Y + trailDist * 0.65f, cardW, rightH, alpha * 0.22f * blurFactor);
        }

        if (scrollOffset > 1.0f) {
            float topFadeH = 16.0f;
            int fadeCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.96f * alpha);
            int clearCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.0f);
            Render2D.drawGradientRect(context, x - 2.0f, y, width + 4.0f, topFadeH, 0.0f, fadeCol, fadeCol, clearCol, clearCol);
        }
        if (scrollOffset < maxScroll - 1.0f) {
            float botFadeH = 22.0f;
            int fadeCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.96f * alpha);
            int clearCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.0f);
            Render2D.drawGradientRect(context, x - 2.0f, y + height - botFadeH, width + 4.0f, botFadeH, 0.0f, clearCol, clearCol, fadeCol, fadeCol);
        }

        disableGuiScissor(context);

        float maxColY = Math.max(col0Y + leftH, col1Y + rightH);
        float totalHeight = maxColY - (y - scrollOffset);
        maxScroll = Math.max(0.0f, totalHeight - height);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);

        long timeSinceScroll = System.currentTimeMillis() - lastScrollTime;
        scrollbarAlphaAnim.setTarget(timeSinceScroll < 1200 ? 1.0f : 0.0f);
        float sbarAlpha = scrollbarAlphaAnim.getValue() * alpha;

        if (maxScroll > 0.0f && sbarAlpha > 0.01f) {
            float sbarW = 3.0f;
            float sbarX = x + width - sbarW;
            float viewRatio = height / totalHeight;
            float thumbH = Math.max(20.0f, height * viewRatio);
            float thumbY = y + (scrollOffset / maxScroll) * (height - thumbH);

            int sbarColor = ColorUtil.rgba(255, 255, 255, (int) (70 * sbarAlpha));
            Render2D.drawRoundedRect(context, sbarX, thumbY, sbarW, thumbH, 1.5f, sbarColor);
        }
    }

    private void renderSettingsCard(DrawContext context, String title, List<Setting> settings, float cardX, float cardY, float cardW, float cardH, float mouseX, float mouseY, float alpha) {
        int cardBg = Theme.getCardBg();
        int cardBorder = Theme.CARD_BORDER;

        Render2D.drawRoundedRect(context, cardX, cardY, cardW, cardH, Theme.CARD_RADIUS, ColorUtil.withAlphaFloat(cardBg, alpha));
        Render2D.drawRoundedOutline(context, cardX, cardY, cardW, cardH, Theme.CARD_RADIUS, 1.0f, ColorUtil.withAlphaFloat(cardBorder, alpha));

        ClientFont.draw(context, title, cardX + 12.0f, cardY + 11.0f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        Render2D.drawRect(context, cardX + 12.0f, cardY + 28.0f, cardW - 24.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (14 * alpha)));

        float sY = cardY + 34.0f;
        float settingsW = cardW - 24.0f;
        float settingsX = cardX + 12.0f;

        for (Setting setting : settings) {
            float progress = setting.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            float setAlpha = alpha * progress;
            float setH = 0.0f;

            if (setting instanceof BooleanSetting bool) {
                setH = 26.0f * progress;
                renderBooleanSetting(context, bool, settingsX, sY, settingsW, setAlpha);
            } else if (setting instanceof SliderSetting slider) {
                setH = 34.0f * progress;
                renderSliderSetting(context, slider, settingsX, sY, settingsW, setAlpha);
            } else if (setting instanceof ModeSetting mode) {
                setH = 28.0f * progress;
                renderModeSetting(context, mode, settingsX, sY, settingsW, setAlpha);
            } else if (setting instanceof ColorSetting colorSetting) {
                setH = 26.0f * progress;
                renderColorSetting(context, colorSetting, settingsX, sY, settingsW, setAlpha);
            } else if (setting instanceof BindSetting bind) {
                setH = 28.0f * progress;
                renderBindSetting(context, bind, settingsX, sY, settingsW, setAlpha);
            }
            if (mouseX >= settingsX && mouseX <= (settingsX + settingsW) && mouseY >= sY && mouseY <= (sY + setH)) {
                if (setting.getDescription() != null && !setting.getDescription().isEmpty()) {
                    activeCandidateSetting = setting;
                }
            }
            sY += setH;
        }
    }

    private void renderModules(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        enableGuiScissor(context, x - 2.0f, y, x + width + 4.0f, y + height);

        List<Module> modules = getFilteredModules();
        float currentY = y - scrollOffset;

        float colGap = 10.0f;
        float cardGap = 10.0f;
        float cardW = (width - colGap) / 2.0f;

        float col0Y = currentY;
        float col1Y = currentY;

        boolean blurEnabled = ClientSettings.isScrollMotionBlurEnabled();
        float speed = Math.abs(smoothedScrollVelocity);
        float blurFactor = blurEnabled ? Math.clamp(speed / 3.0f, 0.0f, 1.0f) : 0.0f;
        float trailDist = Math.clamp(smoothedScrollVelocity * 1.85f, -24.0f, 24.0f);

        boolean isWave = "Волна".equals(ClientSettings.getOpenAnimationMode());
        float pAnim = this.currentAnimProgress;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);

            Animation anim = cardCascadeAnims.get(m);
            float cascade = (anim != null) ? anim.getValue() : 1.0f;
            float offsetY = (1.0f - cascade) * 10.0f;
            if (isWave && pAnim < 0.999f) {
                float wavePhase = Math.max(0.0f, pAnim * (float) Math.PI * 1.8f - (float) i * 0.16f);
                float waveSin = (float) Math.sin(wavePhase);
                offsetY += (1.0f - pAnim) * 8.0f * Math.max(0.0f, waveSin);
            }
            float alphaM = alpha * cascade;
            float expandM = m.getExpandAnimation().getValue();
            float headerH = getModuleHeaderHeight(m, cardW);
            float h = headerH + (expandM > 0.001f ? (getSettingsHeight(m) * expandM) : 0.0f);

            boolean isLeft = (i % 2 == 0);
            float cardX = isLeft ? x : (x + cardW + colGap);
            float curColY = isLeft ? col0Y : col1Y;
            float cardY = curColY + offsetY;

            if (cardY + h >= y - 24.0f && cardY <= y + height + 24.0f) {
                if (blurFactor > 0.02f) {
                    float wakeY = cardY + trailDist * 0.70f;
                    int wakeBg = ColorUtil.withAlphaFloat(Theme.getCardBg(), alphaM * 0.50f * blurFactor);
                    Render2D.drawRoundedRect(context, cardX, wakeY, cardW, h, Theme.CARD_RADIUS, wakeBg);
                    int wakeBorder = ColorUtil.withAlphaFloat(Theme.CARD_BORDER, alphaM * 0.60f * blurFactor);
                    Render2D.drawRoundedOutline(context, cardX, wakeY, cardW, h, Theme.CARD_RADIUS, 1.0f, wakeBorder);
                    int accentGlow = ColorUtil.withAlphaFloat(Theme.getAccent(), alphaM * 0.25f * blurFactor);
                    Render2D.drawRoundedOutline(context, cardX - 0.5f, wakeY - 0.5f, cardW + 1.0f, h + 1.0f, Theme.CARD_RADIUS, 1.0f, accentGlow);
                }

                if (cardY + h >= y && cardY <= y + height) {
                    renderModuleCard(context, m, cardX, cardY, cardW, h, mouseX, mouseY, alphaM, expandM);
                }

                if (blurFactor > 0.02f && cardY + h >= y - 10.0f && cardY <= y + height + 10.0f) {
                    renderCardMotionTrail(context, m, cardX, cardY, cardW, h, alphaM, trailDist * 0.32f, 0.44f * blurFactor);
                    renderCardMotionTrail(context, m, cardX, cardY, cardW, h, alphaM, trailDist * 0.65f, 0.22f * blurFactor);
                }
            }

            if (isLeft) {
                col0Y += h + cardGap;
            } else {
                col1Y += h + cardGap;
            }
        }

        if (scrollOffset > 1.0f) {
            float topFadeH = 16.0f;
            int fadeCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.96f * alpha);
            int clearCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.0f);
            Render2D.drawGradientRect(context, x - 2.0f, y, width + 4.0f, topFadeH, 0.0f, fadeCol, fadeCol, clearCol, clearCol);
        }
        if (scrollOffset < maxScroll - 1.0f) {
            float botFadeH = 22.0f;
            int fadeCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.96f * alpha);
            int clearCol = ColorUtil.withAlphaFloat(Theme.getWindowBg(), 0.0f);
            Render2D.drawGradientRect(context, x - 2.0f, y + height - botFadeH, width + 4.0f, botFadeH, 0.0f, clearCol, clearCol, fadeCol, fadeCol);
        }

        disableGuiScissor(context);

        float maxColY = Math.max(col0Y, col1Y);
        float totalHeight = maxColY - (y - scrollOffset);
        maxScroll = Math.max(0.0f, totalHeight - height);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);

        long timeSinceScroll = System.currentTimeMillis() - lastScrollTime;
        scrollbarAlphaAnim.setTarget(timeSinceScroll < 1200 ? 1.0f : 0.0f);
        float sbarAlpha = scrollbarAlphaAnim.getValue() * alpha;

        if (maxScroll > 0.0f && sbarAlpha > 0.01f) {
            float sbarW = 3.0f;
            float sbarX = x + width - sbarW;
            float viewRatio = height / totalHeight;
            float thumbH = Math.max(20.0f, height * viewRatio);
            float thumbY = y + (scrollOffset / maxScroll) * (height - thumbH);

            int sbarColor = ColorUtil.rgba(255, 255, 255, (int) (70 * sbarAlpha));
            Render2D.drawRoundedRect(context, sbarX, thumbY, sbarW, thumbH, 1.5f, sbarColor);
        }
    }

    private void renderModuleCard(DrawContext context, Module module, float x, float y, float width, float height, float mouseX, float mouseY, float alpha, float expandProgress) {
        float headerH = getModuleHeaderHeight(module, width);
        boolean isHovered = mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + (expandProgress > 0 ? headerH : height));

        Animation hoverAnim = cardHoverAnims.get(module);
        if (hoverAnim != null) {
            hoverAnim.setTarget(isHovered ? 1.0f : 0.0f);
        }
        float hoverVal = (hoverAnim != null) ? hoverAnim.getValue() : 0.0f;

        int cardBg = ColorUtil.interpolate(Theme.getCardBg(), Theme.getCardBgHover(), hoverVal);
        int targetBorder = ColorUtil.interpolate(Theme.CARD_BORDER_HOVER, Theme.getAccentHover(), 0.35f);
        int cardBorder = ColorUtil.interpolate(Theme.CARD_BORDER, targetBorder, hoverVal);

        Render2D.drawRoundedRect(context, x, y, width, height, Theme.CARD_RADIUS, ColorUtil.withAlphaFloat(cardBg, alpha));
        Render2D.drawRoundedOutline(context, x, y, width, height, Theme.CARD_RADIUS, 1.0f, ColorUtil.withAlphaFloat(cardBorder, alpha));

        float switchProgress = module.getSwitchAnimation().getValue();

        boolean isListening = (listeningKeybindModule == module);
        if (isListening) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 130.0);
            int pulseBorder = ColorUtil.interpolate(Theme.getAccent(), Theme.getAccentHover(), pulse);
            Render2D.drawRoundedOutline(context, x - 0.5f, y - 0.5f, width + 1.0f, height + 1.0f, Theme.CARD_RADIUS, 1.5f, ColorUtil.withAlphaFloat(pulseBorder, alpha));
        }

        float toggleW = 34.0f;
        float toggleH = 16.0f;
        float toggleX = x + width - toggleW - 12.0f;
        float toggleY = y + 10.0f;

        float starX = toggleX - 14.0f;
        float starY = toggleY + 1.0f;

        boolean hasSettings = !module.getSettings().isEmpty();
        float dotsX = hasSettings ? (starX - 11.0f) : starX;
        float dotsY = toggleY + 1.0f;

        float textX = x + 12.0f;
        float titleY = y + 10.0f;
        float controlsLeft = (hasSettings ? dotsX : starX) - 8.0f;
        float availableTextW = Math.max(30.0f, controlsLeft - textX);
        float maxTitleW = availableTextW;

        if (isHovered && mouseX < controlsLeft && mouseY <= (y + headerH)) {
            activeCandidateModule = module;
        }

        if (!FpsBenchmark.overrideNoText) {
            float badgeSpace = 0.0f;
            if (isListening) {
                float tw = ClientFont.getWidth("Жду клавишу...", ClientFont.SF_REGULAR);
                badgeSpace = tw + 16.0f;
            } else if (module.getKey() > 0) {
                String keyStr = Keybinds.formatKey(module.getKey());
                float tw = ClientFont.getWidth(keyStr, ClientFont.SF_REGULAR);
                badgeSpace = Math.max(16.0f, tw + 8.0f) + 8.0f;
            }
            maxTitleW = Math.max(20.0f, maxTitleW - badgeSpace);

            String title = getTruncatedLabel(module.getName(), maxTitleW, ClientFont.SF_MEDIUM);
            ClientFont.draw(context, title, textX, titleY, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
            float nameW = ClientFont.getWidth(title, ClientFont.SF_MEDIUM);

            if (isListening) {
                float badgeX = textX + nameW + 8.0f;
                String waitText = "Жду клавишу...";
                float tw = ClientFont.getWidth(waitText, ClientFont.SF_REGULAR);
                float bw = tw + 8.0f;
                float bh = 12.0f;
                float by = titleY - 1.0f;
                int bBg = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * 0.9f);
                Render2D.drawRoundedRect(context, badgeX, by, bw, bh, 4.0f, bBg);
                ClientFont.draw(context, waitText, badgeX + 4.0f, by + 1.5f, ClientFont.SF_REGULAR, 0xFFFFFFFF);
            } else if (module.getKey() > 0) {
                String keyStr = Keybinds.formatKey(module.getKey());
                float tw = ClientFont.getWidth(keyStr, ClientFont.SF_REGULAR);
                float bw = Math.max(16.0f, tw + 8.0f);
                float bh = 12.0f;
                float badgeX = textX + nameW + 8.0f;
                float by = titleY - 1.0f;
                boolean pressed = Keybinds.isKeyPressed(module.getKey());
                int bBg = pressed ? ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * 0.9f) : ColorUtil.rgba(35, 35, 37, (int) (220 * alpha));
                int bBorder = pressed ? ColorUtil.withAlphaFloat(Theme.getAccentHover(), alpha) : ColorUtil.rgba(255, 255, 255, (int) (20 * alpha));
                Render2D.drawRoundedRect(context, badgeX, by, bw, bh, 4.0f, bBg);
                Render2D.drawRoundedOutline(context, badgeX, by, bw, bh, 4.0f, 1.0f, bBorder);
                int kColor = pressed ? 0xFFFFFFFF : ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha);
                ClientFont.draw(context, keyStr, badgeX + (bw - tw) / 2.0f, by + 1.5f, ClientFont.SF_REGULAR, kColor);
            }

            int descColor = ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha);
            float maxDescW = width - 24.0f;
            List<String> descLines = getWrappedDescriptionLines(module, maxDescW);
            if (descLines.size() <= 1) {
                float descY = y + 33.0f;
                if (!descLines.isEmpty()) {
                    ClientFont.draw(context, descLines.get(0), textX, descY, ClientFont.SF_REGULAR, descColor);
                }
            } else {
                float descY1 = y + 31.0f;
                float descY2 = y + 43.0f;
                ClientFont.draw(context, descLines.get(0), textX, descY1, ClientFont.SF_REGULAR, descColor);
                ClientFont.draw(context, descLines.get(1), textX, descY2, ClientFont.SF_REGULAR, descColor);
            }
        }

        renderToggle(context, module, toggleX, toggleY, toggleW, toggleH, alpha);
        renderStar(context, module, starX, starY, mouseX, mouseY, alpha);
        if (hasSettings) {
            renderDots(context, module, dotsX, dotsY, mouseX, mouseY, alpha);
        }

        if (expandProgress > 0.01f) {
            float settingsY = y + headerH;
            float visibleH = height - headerH;
            if (visibleH > 1.0f) {
                enableGuiScissor(context, x, settingsY, x + width, y + height);
                renderSettings(context, module, x + 12.0f, settingsY, width - 24.0f, expandProgress * alpha, mouseX, mouseY);
                disableGuiScissor(context);
            }
        }
    }

    private void renderCardMotionTrail(DrawContext context, Module module, float x, float y, float width, float height, float alpha, float offset, float sampleAlpha) {
        float trailY = y + offset;
        float effAlpha = alpha * sampleAlpha;
        if (effAlpha <= 0.005f) return;

        int cardBorder = ColorUtil.withAlphaFloat(Theme.CARD_BORDER, effAlpha * 0.90f);
        Render2D.drawRoundedOutline(context, x, trailY, width, height, Theme.CARD_RADIUS, 1.0f, cardBorder);

        float toggleW = 34.0f;
        float toggleH = 16.0f;
        float toggleX = x + width - toggleW - 12.0f;
        float toggleY = trailY + 10.0f;

        float starX = toggleX - 18.0f;
        float starY = toggleY + 1.0f;

        boolean hasSettings = !module.getSettings().isEmpty();
        float dotsX = hasSettings ? (starX - 16.0f) : starX;
        float dotsY = toggleY + 1.0f;

        float controlsLeft = (hasSettings ? dotsX : starX) - 8.0f;
        float availableTextW = Math.max(30.0f, controlsLeft - (x + 12.0f));

        if (!FpsBenchmark.overrideNoText) {
            float textX = x + 12.0f;
            float titleY = trailY + 9.0f;
            float maxTitleW = availableTextW;
            String title = getTruncatedLabel(module.getName(), maxTitleW, ClientFont.SF_MEDIUM);
            int titleColor = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, effAlpha * 0.85f);
            ClientFont.draw(context, title, textX, titleY, ClientFont.SF_MEDIUM, titleColor);

            float descY = titleY + 14.0f;
            float maxDescW = availableTextW;
            String desc = getTruncatedDescription(module, maxDescW);
            int descColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, effAlpha * 0.70f);
            ClientFont.draw(context, desc, textX, descY, ClientFont.SF_REGULAR, descColor);
        }

        renderToggle(context, module, toggleX, toggleY, toggleW, toggleH, effAlpha * 0.80f);
        renderStar(context, module, starX, starY, -1.0f, -1.0f, effAlpha * 0.75f);
        if (hasSettings) {
            renderDots(context, module, dotsX, dotsY, -1.0f, -1.0f, effAlpha * 0.75f);
        }
    }

    private void renderSettingsCardTrail(DrawContext context, String title, float cardX, float cardY, float cardW, float cardH, float alpha) {
        if (alpha <= 0.005f) return;
        int cardBorder = ColorUtil.withAlphaFloat(Theme.CARD_BORDER, alpha * 0.90f);
        Render2D.drawRoundedOutline(context, cardX, cardY, cardW, cardH, Theme.CARD_RADIUS, 1.0f, cardBorder);
        if (!FpsBenchmark.overrideNoText) {
            int titleCol = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha * 0.85f);
            ClientFont.draw(context, title, cardX + 12.0f, cardY + 11.0f, ClientFont.SF_MEDIUM, titleCol);
        }
    }

    private String getTruncatedLabel(String text, float maxW, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty() || maxW <= 6.0f) return "";
        if (ClientFont.getWidth(text, font) <= maxW) return text;

        String cacheKey = text + "_" + (int) maxW + "_" + font.hashCode();
        String cached = truncatedLabelsCache.get(cacheKey);
        if (cached != null) return cached;

        String dots = "...";
        float dotsW = ClientFont.getWidth(dots, font);
        float targetW = maxW - dotsW;
        if (targetW <= 2.0f) {
            cached = dots;
            truncatedLabelsCache.put(cacheKey, cached);
            return cached;
        }

        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String sub = text.substring(0, mid);
            if (ClientFont.getWidth(sub, font) <= targetW) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        String result = text.substring(0, best).trim() + dots;
        while (best > 0 && ClientFont.getWidth(result, font) > maxW) {
            best--;
            result = text.substring(0, best).trim() + dots;
        }
        if (ClientFont.getWidth(result, font) > maxW) {
            result = dots;
        }
        cached = result;
        truncatedLabelsCache.put(cacheKey, cached);
        return cached;
    }

    private float getColorBadgeWidth(ColorSetting colorSetting) {
        String hex = colorSetting.getHex();
        float hexW = ClientFont.getWidth(hex, ClientFont.SF_REGULAR);
        return Math.max(54.0f, 11.0f + hexW + 12.0f);
    }

    private float getModeButtonWidth(ModeSetting mode, float availableWidth) {
        float rawBtnW = ClientFont.getWidth(mode.get(), ClientFont.SF_REGULAR) + 22.0f;
        float maxBtnW = Math.max(46.0f, availableWidth * 0.48f);
        return Math.min(rawBtnW, maxBtnW);
    }

    private void checkWrappedCache(float cardW) {
        if (Math.abs(cardW - lastWrappedCardW) > 0.5f) {
            lastWrappedCardW = cardW;
            cachedDescLines.clear();
            cachedHeaderHeights.clear();
            cachedTruncatedDescs.clear();
        }
    }

    private List<String> getWrappedDescriptionLines(Module module, float maxDescW) {
        List<String> cached = cachedDescLines.get(module);
        if (cached != null) return cached;
        String rawDesc = module.getDescription();
        if (rawDesc == null || rawDesc.isEmpty()) {
            cached = Collections.emptyList();
            cachedDescLines.put(module, cached);
            return cached;
        }
        float fullW = ClientFont.getWidth(rawDesc, ClientFont.SF_REGULAR);
        if (fullW <= maxDescW) {
            cached = Collections.singletonList(rawDesc);
            cachedDescLines.put(module, cached);
            return cached;
        }
        String[] words = rawDesc.split(" ");
        StringBuilder l1 = new StringBuilder();
        int idx = 0;
        while (idx < words.length) {
            String w = words[idx];
            String cand = l1.isEmpty() ? w : l1 + " " + w;
            if (ClientFont.getWidth(cand, ClientFont.SF_REGULAR) <= maxDescW) {
                l1.append(l1.isEmpty() ? "" : " ").append(w);
                idx++;
            } else {
                break;
            }
        }
        if (l1.isEmpty() && idx < words.length) {
            l1.append(words[idx++]);
        }
        StringBuilder l2 = new StringBuilder();
        while (idx < words.length) {
            l2.append(l2.isEmpty() ? "" : " ").append(words[idx++]);
        }
        String line2Str = l2.toString();
        if (ClientFont.getWidth(line2Str, ClientFont.SF_REGULAR) > maxDescW) {
            line2Str = getTruncatedLabel(line2Str, maxDescW, ClientFont.SF_REGULAR);
        }
        cached = List.of(l1.toString(), line2Str);
        cachedDescLines.put(module, cached);
        return cached;
    }

    private float getModuleHeaderHeight(Module module, float cardW) {
        checkWrappedCache(cardW);
        Float h = cachedHeaderHeights.get(module);
        if (h != null) return h;
        float maxDescW = cardW - 24.0f;
        List<String> lines = getWrappedDescriptionLines(module, maxDescW);
        float computed = lines.size() > 1 ? 62.0f : 50.0f;
        cachedHeaderHeights.put(module, computed);
        return computed;
    }

    private String getTruncatedDescription(Module module, float maxDescW) {
        String cached = cachedTruncatedDescs.get(module);
        if (cached != null) return cached;
        String raw = module.getDescription();
        cached = getTruncatedLabel(raw, maxDescW, ClientFont.SF_REGULAR);
        cachedTruncatedDescs.put(module, cached);
        return cached;
    }

    private float calculateMarqueeOffset(long elapsedMs, float overflow) {
        if (overflow <= 0.0f) return 0.0f;
        float speed = 28.0f;
        float travelSec = overflow / speed;
        float pauseStart = 0.5f;
        float pauseEnd = 0.8f;
        float totalCycle = pauseStart + travelSec + pauseEnd + travelSec;
        float sec = (elapsedMs % (long) (totalCycle * 1000.0f)) / 1000.0f;

        if (sec < pauseStart) {
            return 0.0f;
        } else if (sec < pauseStart + travelSec) {
            float t = (sec - pauseStart) / travelSec;
            float ease = 0.5f - 0.5f * (float) Math.cos(t * Math.PI);
            return ease * overflow;
        } else if (sec < pauseStart + travelSec + pauseEnd) {
            return overflow;
        } else {
            float t = (sec - (pauseStart + travelSec + pauseEnd)) / travelSec;
            float ease = 0.5f - 0.5f * (float) Math.cos(t * Math.PI);
            return (1.0f - ease) * overflow;
        }
    }

    private List<String> wrapText(String text, float maxW, StyleSpriteSource.Font font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() == 0) {
                sb.append(w);
            } else {
                String candidate = sb + " " + w;
                if (ClientFont.getWidth(candidate, font) <= maxW) {
                    sb.append(" ").append(w);
                } else {
                    lines.add(sb.toString());
                    sb = new StringBuilder(w);
                }
            }
        }
        if (sb.length() > 0) {
            lines.add(sb.toString());
        }
        return lines;
    }

    private void updateAndRenderDeferredTooltip(DrawContext context, float mouseX, float mouseY) {
        if (activeCandidateModule != hoveredTooltipModule || activeCandidateSetting != hoveredTooltipSetting) {
            hoveredTooltipModule = activeCandidateModule;
            hoveredTooltipSetting = activeCandidateSetting;
            hoverStateStartTime = System.currentTimeMillis();
        }

        long hoverDur = System.currentTimeMillis() - hoverStateStartTime;
        boolean shouldShowTooltip = (hoveredTooltipModule != null || hoveredTooltipSetting != null) && hoverDur > 320L && !closing;
        tooltipAlphaAnim.setTarget(shouldShowTooltip ? 1.0f : 0.0f);
        if (shouldShowTooltip) {
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        }

        renderDeferredTooltip(context, tooltipMouseX, tooltipMouseY, 1.0f);
    }

    private void renderDeferredTooltip(DrawContext context, float mouseX, float mouseY, float alpha) {
        float ttAlpha = tooltipAlphaAnim.getValue() * alpha;
        if (ttAlpha <= 0.01f) return;

        String title = null;
        String desc = null;
        String hint = null;

        if (hoveredTooltipModule != null) {
            title = hoveredTooltipModule.getName();
            desc = hoveredTooltipModule.getDescription();
            boolean hasSet = !hoveredTooltipModule.getSettings().isEmpty();
            int key = hoveredTooltipModule.getKey();
            if (key > 0) {
                hint = (hasSet ? "ПКМ — настройки  •  " : "") + "Бинд: " + Keybinds.formatKey(key);
            } else {
                hint = hasSet ? "ПКМ — настройки  •  СКМ — бинд" : "СКМ — назначить клавишу";
            }
        } else if (hoveredTooltipSetting != null) {
            title = hoveredTooltipSetting.getName();
            desc = hoveredTooltipSetting.getDescription();
            if (hoveredTooltipSetting instanceof BooleanSetting) {
                hint = "ЛКМ — переключить";
            } else if (hoveredTooltipSetting instanceof SliderSetting) {
                hint = "Перетащите ползунок для настройки";
            } else if (hoveredTooltipSetting instanceof ModeSetting) {
                hint = "ЛКМ — выбор режима";
            } else if (hoveredTooltipSetting instanceof BindSetting) {
                hint = "ЛКМ — назначить, ПКМ — сбросить";
            } else if (hoveredTooltipSetting instanceof ColorSetting) {
                hint = "ЛКМ — палитра цветов";
            }
        }

        if (desc == null || desc.isEmpty()) return;

        float wrapW = 180.0f;
        Object currentTarget = hoveredTooltipModule != null ? hoveredTooltipModule : hoveredTooltipSetting;
        List<String> lines;
        if (currentTarget == lastTooltipTarget && cachedTooltipLines != null) {
            lines = cachedTooltipLines;
        } else {
            lines = wrapText(desc, wrapW, ClientFont.SF_REGULAR);
            lastTooltipTarget = currentTarget;
            cachedTooltipLines = lines;
        }
        if (lines.isEmpty()) return;

        float padX = 9.0f;
        float padY = 7.0f;
        float titleH = (title != null && !title.isEmpty()) ? 11.5f : 0.0f;
        float hintH = (hint != null && !hint.isEmpty()) ? 9.5f : 0.0f;
        float linesH = lines.size() * 10.5f;

        float maxContentW = 0.0f;
        if (title != null) {
            maxContentW = Math.max(maxContentW, ClientFont.getWidth(title, ClientFont.SF_MEDIUM));
        }
        for (String line : lines) {
            maxContentW = Math.max(maxContentW, ClientFont.getWidth(line, ClientFont.SF_REGULAR));
        }
        if (hint != null) {
            maxContentW = Math.max(maxContentW, ClientFont.getWidth(hint, ClientFont.SF_REGULAR));
        }

        float boxW = Math.max(80.0f, Math.min(wrapW, maxContentW) + padX * 2.0f);
        float boxH = padY * 2.0f + titleH + (titleH > 0 ? 2.5f : 0.0f) + linesH + (hintH > 0 ? (hintH + 4.5f) : 0.0f);

        float tx = mouseX + 12.0f;
        float ty = mouseY + 14.0f;
        int screenW = client != null ? client.getWindow().getScaledWidth() : 800;
        int screenH = client != null ? client.getWindow().getScaledHeight() : 600;

        if (tx + boxW > screenW - 8.0f) {
            tx = mouseX - boxW - 8.0f;
        }
        if (tx < 8.0f) tx = 8.0f;

        if (ty + boxH > screenH - 8.0f) {
            ty = mouseY - boxH - 6.0f;
        }
        if (ty < 8.0f) ty = 8.0f;

        Render2D.drawShadow(context, tx, ty, boxW, boxH, 4.5f, 6.0f, ColorUtil.rgba(0, 0, 0, (int) (140 * ttAlpha)));
        Render2D.drawRoundedRect(context, tx, ty, boxW, boxH, 4.5f, ColorUtil.rgba(18, 18, 22, (int) (246 * ttAlpha)));
        Render2D.drawRoundedOutline(context, tx, ty, boxW, boxH, 4.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (20 * ttAlpha)));

        float curY = ty + padY;
        float contentStartX = tx + padX;

        if (title != null && !title.isEmpty()) {
            ClientFont.draw(context, title, contentStartX, curY, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, ttAlpha));
            curY += titleH + 2.5f;
        }

        int bodyColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, ttAlpha);
        for (String line : lines) {
            ClientFont.draw(context, line, contentStartX, curY, ClientFont.SF_REGULAR, bodyColor);
            curY += 10.5f;
        }

        if (hint != null && !hint.isEmpty()) {
            curY += 1.5f;
            int divCol = ColorUtil.rgba(255, 255, 255, (int) (10 * ttAlpha));
            Render2D.drawRect(context, contentStartX, curY, boxW - padX * 2.0f, 1.0f, divCol);
            curY += 3.0f;
            int hintColor = ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, ttAlpha * 0.85f);
            ClientFont.draw(context, hint, contentStartX, curY, ClientFont.SF_REGULAR, hintColor);
        }
    }

    private void renderToggle(DrawContext context, Module module, float x, float y, float width, float height, float alpha) {
        float switchProgress = module.getSwitchAnimation().getValue();

        int offColor = Theme.TOGGLE_OFF;
        int onColor = Theme.getAccent();
        int bgColor = ColorUtil.interpolate(offColor, onColor, switchProgress);
        if (switchProgress > 0.05f) {
            Render2D.drawShadow(context, x, y, width, height, height / 2.0f, 5.0f, ColorUtil.withAlphaFloat(onColor, switchProgress * 0.40f * alpha));
        }
        Render2D.drawCapsule(context, x, y, width, height, ColorUtil.withAlphaFloat(bgColor, alpha));

        int borderColor = ColorUtil.interpolate(
                ColorUtil.rgba(255, 255, 255, (int) (14 * alpha)),
                0,
                switchProgress
        );
        if ((borderColor >> 24) != 0) {
            Render2D.drawCapsuleOutline(context, x, y, width, height, borderColor);
        }

        float pad = 2.0f;
        float knobH = height - pad * 2.0f;
        float knobW = 21.0f;

        float knobMinX = x + pad;
        float knobMaxX = x + width - pad - knobW;
        float knobX = knobMinX + (knobMaxX - knobMinX) * switchProgress;
        float knobY = y + pad;

        Render2D.drawCapsule(context, knobX, knobY, knobW, knobH, ColorUtil.withAlphaFloat(Theme.TOGGLE_KNOB, alpha));
    }

    private void renderStar(DrawContext context, Module module, float x, float y, float mouseX, float mouseY, float alpha) {
        boolean isFavorite = module.isFavorite();
        boolean isHovered = mouseX >= x - 2 && mouseX <= x + 14 && mouseY >= y - 2 && mouseY <= y + 16;

        int starColor = isFavorite ? Theme.STAR_ACTIVE : (isHovered ? Theme.TEXT_TITLE : ColorUtil.rgba(255, 255, 255, 56));

        IconRenderer.drawStarIcon(context, x + 5.0f, y + 8.0f, isFavorite, ColorUtil.withAlphaFloat(starColor, alpha));
    }

    private void renderDots(DrawContext context, Module module, float x, float y, float mouseX, float mouseY, float alpha) {
        boolean isHovered = mouseX >= x - 2 && mouseX <= x + 12 && mouseY >= y - 2 && mouseY <= y + 16;
        Animation hoverAnim = dotsHoverAnims.get(module);
        if (hoverAnim != null) {
            hoverAnim.setTarget(isHovered ? 1.0f : 0.0f);
        }
        float hoverVal = (hoverAnim != null) ? hoverAnim.getValue() : (isHovered ? 1.0f : 0.0f);
        int baseColor = ColorUtil.interpolate(Theme.DOTS_INACTIVE, Theme.DOTS_ACTIVE, hoverVal);
        int color = module.isExpanded() ? Theme.getAccent() : baseColor;
        int finalColor = ColorUtil.withAlphaFloat(color, alpha);

        IconRenderer.drawDotsIcon(context, x + 5.0f, y + 8.0f, finalColor);
    }

    private float getSettingsHeight(Module module) {
        float h = 10.0f;
        if (module instanceof WorldTweaks) {
            h += WorldTweaksUI.getTotalHeight();
        }
        for (Setting s : module.getSettings()) {
            if (isWorldTweaksCustomSetting(module, s)) continue;
            float progress = s.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            float baseH = 0.0f;
            if (s instanceof BooleanSetting) {
                baseH = 26.0f;
            } else if (s instanceof SliderSetting) {
                baseH = 34.0f;
            } else if (s instanceof ModeSetting) {
                baseH = 28.0f;
            } else if (s instanceof ColorSetting cs) {
                baseH = 26.0f;
            } else if (s instanceof ButtonSetting) {
                baseH = 28.0f;
            } else if (s instanceof BindSetting) {
                baseH = 28.0f;
            }
            h += baseH * progress;
        }
        h += 28.0f;
        return h;
    }

    private void renderSettings(DrawContext context, Module module, float x, float y, float width, float alpha, float mouseX, float mouseY) {

        Render2D.drawRect(context, x, y - 2.0f, width, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (14 * alpha)));

        float currentY = y + 6.0f;
        if (module instanceof WorldTweaks wt) {
            WorldTweaksUI.render(context, wt, x, currentY, width, alpha, mouseX, mouseY);
            currentY += WorldTweaksUI.getTotalHeight();
        }
        for (Setting setting : module.getSettings()) {
            if (isWorldTweaksCustomSetting(module, setting)) continue;
            float progress = setting.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            float setAlpha = alpha * progress;
            float setH = 0.0f;

            if (setting instanceof BooleanSetting bool) {
                setH = 26.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderBooleanSetting(context, bool, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            } else if (setting instanceof SliderSetting slider) {
                setH = 34.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderSliderSetting(context, slider, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            } else if (setting instanceof ModeSetting mode) {
                setH = 28.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderModeSetting(context, mode, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            } else if (setting instanceof ColorSetting colorSetting) {
                setH = 26.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderColorSetting(context, colorSetting, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            } else if (setting instanceof ButtonSetting btn) {
                setH = 28.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderButtonSetting(context, btn, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            } else if (setting instanceof BindSetting bind) {
                setH = 28.0f * progress;
                if (progress < 0.99f) {
                    enableGuiScissor(context, x, currentY, x + width, currentY + setH);
                }
                renderBindSetting(context, bind, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    disableGuiScissor(context);
                }
            }
            if (mouseX >= x && mouseX <= (x + width) && mouseY >= currentY && mouseY <= (currentY + setH)) {
                if (setting.getDescription() != null && !setting.getDescription().isEmpty()) {
                    activeCandidateSetting = setting;
                }
            }
            currentY += setH;
        }

        renderKeybindRow(context, module, x, currentY, width, alpha);
    }

    private void renderButtonSetting(DrawContext context, ButtonSetting btn, float x, float y, float width, float alpha) {
        float btnW = width;
        float btnH = 22.0f;
        float btnX = x;
        float btnY = y + 2.0f;

        boolean hovered = lateMouseX >= btnX && lateMouseX <= btnX + btnW && lateMouseY >= btnY && lateMouseY <= btnY + btnH;
        int bg = hovered ? ColorUtil.rgba(255, 255, 255, (int) (28 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha));
        int border = hovered ? ColorUtil.rgba(255, 255, 255, (int) (38 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (16 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 5.0f, bg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 5.0f, 1.0f, border);

        String lower = btn.getName().toLowerCase();
        Identifier icon = null;
        if (lower.contains("папк") || lower.contains("folder")) {
            icon = DynamicGuiTextures.getFolderIcon();
        } else if (lower.contains("обнов") || lower.contains("refresh") || lower.contains("sync")) {
            icon = DynamicGuiTextures.getRefreshIcon();
        }

        String btnText = getTruncatedLabel(btn.getName(), btnW - (icon != null ? 32.0f : 12.0f), ClientFont.SF_REGULAR);
        int textColor = hovered ? 0xFFFFFFFF : ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha);

        if (icon != null) {
            float iconSize = 12.0f;
            float gap = 5.0f;
            float textW = ClientFont.getWidth(btnText, ClientFont.SF_REGULAR);
            float totalW = iconSize + gap + textW;
            float startX = btnX + (btnW - totalW) / 2.0f;
            IconRenderer.drawTextureScaled(context, icon, startX, btnY + (btnH - iconSize) / 2.0f, iconSize, iconSize, 20, 20, textColor);
            ClientFont.draw(context, btnText, startX + iconSize + gap, btnY + (btnH - 8.0f) / 2.0f, ClientFont.SF_REGULAR, textColor);
        } else {
            ClientFont.drawCentered(context, btnText, btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, textColor);
        }
    }

    private void renderBindSetting(DrawContext context, BindSetting bind, float x, float y, float width, float alpha) {
        boolean isListening = (listeningBindSetting == bind);
        String keyText = isListening ? "Press Key/Mouse..." : bind.getKeyName();
        float rawBtnW = ClientFont.getWidth(keyText, ClientFont.SF_REGULAR) + 16.0f;
        float maxBtnW = Math.max(50.0f, width * 0.48f);
        float btnW = Math.min(rawBtnW, maxBtnW);
        float btnH = 18.0f;
        float btnX = x + width - btnW;
        float btnY = y + 2.0f;

        float maxLabelW = Math.max(10.0f, btnX - x - 8.0f);
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            String label = getTruncatedLabel(bind.getName(), maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x, y + 5.0f, ClientFont.SF_REGULAR, labelColor);
        }

        int btnBg = isListening ? Theme.getAccentMuted() : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha));
        int btnBorder = isListening ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (22 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 4.0f, btnBg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 4.0f, 1.0f, btnBorder);

        if (!FpsBenchmark.overrideNoText) {
            int textColor = isListening ? Theme.getAccentHover() : Theme.TEXT_TITLE;
            String text = getTruncatedLabel(keyText, btnW - 8.0f, ClientFont.SF_REGULAR);
            ClientFont.drawCentered(context, text, btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(textColor, alpha));
        }
    }

    private void renderBooleanSetting(DrawContext context, BooleanSetting bool, float x, float y, float width, float alpha) {
        float boxSize = 14.0f;
        float boxX = x + width - boxSize;
        float boxY = y + 6.0f;

        float maxLabelW = Math.max(10.0f, boxX - x - 8.0f);
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            String label = getTruncatedLabel(bool.getName(), maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x, y + 6.0f, ClientFont.SF_REGULAR, labelColor);
        }

        if (bool.get()) {
            Render2D.drawShadow(context, boxX, boxY, boxSize, boxSize, 3.5f, 4.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.35f * alpha));
            Render2D.drawRoundedRect(context, boxX, boxY, boxSize, boxSize, 3.5f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
            Render2D.drawCheckmark(context, boxX, boxY, boxSize, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
        } else {
            Render2D.drawRoundedRect(context, boxX, boxY, boxSize, boxSize, 3.5f, ColorUtil.rgba(255, 255, 255, (int) (12 * alpha)));
            Render2D.drawRoundedOutline(context, boxX, boxY, boxSize, boxSize, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (28 * alpha)));
        }
    }

    private void renderModeSetting(DrawContext context, ModeSetting mode, float x, float y, float width, float alpha) {
        String val = mode.get();
        float btnW = getModeButtonWidth(mode, width);
        float btnH = 18.0f;
        float btnX = x + width - btnW;
        float btnY = y + 4.0f;

        float maxLabelW = Math.max(10.0f, btnX - x - 8.0f);
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            String label = getTruncatedLabel(mode.getName(), maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x, y + 6.0f, ClientFont.SF_REGULAR, labelColor);
        }

        boolean isOpened = (this.openDropdownSetting == mode);
        int btnBg = isOpened ? ColorUtil.rgba(35, 38, 48, (int) (245 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha));
        int btnBorder = isOpened ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (22 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 4.0f, btnBg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 4.0f, 1.0f, isOpened ? Theme.getAccent() : btnBorder);

        if (!FpsBenchmark.overrideNoText) {
            String valText = val;
            float textW = ClientFont.getWidth(val, ClientFont.SF_REGULAR);
            float arrowW = 5.0f;
            float gap = 4.0f;
            float contentW = textW + gap + arrowW;
            float startX;
            float arrowX;
            if (contentW <= btnW - 10.0f) {
                startX = btnX + (btnW - contentW) / 2.0f;
                arrowX = startX + textW + gap;
            } else {
                startX = btnX + 6.0f;
                arrowX = btnX + btnW - 10.0f;
                valText = getTruncatedLabel(val, arrowX - startX - gap, ClientFont.SF_REGULAR);
            }
            float textY = btnY + (btnH - ClientFont.FONT_HEIGHT) / 2.0f + 0.5f;
            int textColor = ColorUtil.withAlphaFloat(isOpened ? Theme.getAccent() : Theme.TEXT_TITLE, alpha);
            ClientFont.draw(context, valText, startX, textY, ClientFont.SF_REGULAR, textColor);

            float arrowY = btnY + btnH / 2.0f;
            int arrowColor = ColorUtil.withAlphaFloat(isOpened ? Theme.getAccent() : Theme.TEXT_MUTED, alpha);
            IconRenderer.drawDropdownChevron(context, arrowX + 2.5f, arrowY, isOpened, arrowColor);
        }
    }

    private void renderColorSetting(DrawContext context, ColorSetting colorSetting, float x, float y, float width, float alpha) {
        int col = colorSetting.get();
        String hex = colorSetting.getHex();
        float badgeW = getColorBadgeWidth(colorSetting);
        float badgeH = 18.0f;
        float badgeX = x + width - badgeW;
        float badgeY = y + 3.0f;

        float maxLabelW = Math.max(10.0f, badgeX - x - 8.0f);
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            String label = getTruncatedLabel(colorSetting.getName(), maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x, y + 5.0f, ClientFont.SF_REGULAR, labelColor);
        }

        boolean isOpened = (this.openColorPickerSetting == colorSetting);

        this.colorSettingBadgePositions.put(colorSetting, new float[]{badgeX, badgeY, badgeW, badgeH});

        int badgeBg = isOpened ? ColorUtil.rgba(35, 38, 48, (int) (245 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha));
        int badgeBorder = isOpened ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (22 * alpha));

        Render2D.drawRoundedRect(context, badgeX, badgeY, badgeW, badgeH, 4.0f, ColorUtil.withAlphaFloat(badgeBg, alpha));
        Render2D.drawRoundedOutline(context, badgeX, badgeY, badgeW, badgeH, 4.0f, 1.0f, ColorUtil.withAlphaFloat(badgeBorder, alpha));

        float swatchS = 11.0f;
        float swatchX = badgeX + 4.0f;
        float swatchY = badgeY + (badgeH - swatchS) / 2.0f;
        Render2D.drawRoundedRect(context, swatchX, swatchY, swatchS, swatchS, 2.5f, ColorUtil.withAlphaFloat(col, alpha));
        Render2D.drawRoundedOutline(context, swatchX, swatchY, swatchS, swatchS, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (50 * alpha)));

        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, hex, swatchX + swatchS + 4.0f, badgeY + 4.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
        }
    }

    private void renderOpenColorPicker(DrawContext context, double mouseX, double mouseY, float alpha) {
        if (openColorPickerSetting == null) {
            colorPopupAnim = Math.max(0.0f, colorPopupAnim - 0.25f);
            if (colorPopupAnim <= 0.01f) return;
        } else {
            colorPopupAnim = Math.min(1.0f, colorPopupAnim + 0.25f);
        }

        if (colorPopupAnim <= 0.01f || openColorPickerSetting == null) {
            return;
        }

        float[] storedBadge = this.colorSettingBadgePositions.get(openColorPickerSetting);
        if (storedBadge != null) {
            float badgeX = storedBadge[0];
            float badgeY = storedBadge[1];
            float badgeW = storedBadge[2];
            float badgeH = storedBadge[3];

            float guiH = getGuiBaseHeight();
            float gy = (float) Math.round((height - guiH) / 2.0f);
            float contentY = gy + 38.0f + 8.0f;
            float contentH = guiH - 38.0f - 14.0f;
            if (badgeY < contentY - 30.0f || badgeY > contentY + contentH + 30.0f) {
                this.openColorPickerSetting = null;
                return;
            }

            float popW = 144.0f;
            float popH = 132.0f;
            float px = badgeX + badgeW - popW;
            float py = badgeY + badgeH + 4.0f;
            int screenW = client != null ? client.getWindow().getScaledWidth() : 800;
            int screenH = client != null ? client.getWindow().getScaledHeight() : 600;
            if (px + popW > screenW - 8.0f) px = screenW - popW - 8.0f;
            if (px < 8.0f) px = 8.0f;
            if (py + popH > screenH - 8.0f) py = badgeY - popH - 4.0f;
            if (py < 8.0f) py = 8.0f;
            this.colorPopupX = px;
            this.colorPopupY = py;
        }

        float pAnim = colorPopupAnim * alpha;
        float popW = 144.0f;
        float popH = 132.0f;
        float popX = colorPopupX;
        float popY = colorPopupY;

        float scale = currentGuiScale;
        Matrix3x2f mat = null;
        if (Math.abs(scale - 1.0f) > 0.001f) {
            float cx = getGuiCenterX();
            float cy = getGuiCenterY();
            mat = new Matrix3x2f();
            mat.translate(cx, cy);
            mat.scale(scale, scale);
            mat.translate(-cx, -cy);
        }

        Draw2DProcessor.INSTANCE.drawGlow(mat, popX, popY, popW, popH, 6.0f, ColorUtil.rgba(0, 0, 0, (int) (180 * pAnim)), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, popX, popY, popW, popH, 6.0f, ColorUtil.rgba(16, 17, 24, (int) (250 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(mat, popX, popY, popW, popH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (30 * pAnim)));

        ColorSetting cs = openColorPickerSetting;
        float[] hsb = cs.getHSB();
        float hue = hsb[0];
        float sat = hsb[1];
        float bri = hsb[2];
        float curAlpha = cs.getAlpha();
        int curCol = cs.get();
        int pureHueRgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;

        float svX = popX + 8.0f;
        float svY = popY + 8.0f;
        float svW = 92.0f;
        float svH = 74.0f;

        Draw2DProcessor.INSTANCE.drawGradient(mat, svX, svY, svW, svH, 3.0f,
                ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)),
                ColorUtil.withAlphaFloat((pureHueRgb | 0xFF000000), pAnim),
                ColorUtil.rgba(0, 0, 0, (int) (255 * pAnim)),
                ColorUtil.rgba(0, 0, 0, (int) (255 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(mat, svX, svY, svW, svH, 3.0f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        float curSX = svX + sat * svW;
        float curSY = svY + (1.0f - bri) * svH;
        Draw2DProcessor.INSTANCE.drawGlow(mat, curSX - 3.5f, curSY - 3.5f, 7.0f, 7.0f, 3.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 3.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, curSX - 3.0f, curSY - 3.0f, 6.0f, 6.0f, 3.0f, ColorUtil.withAlphaFloat(curCol | 0xFF000000, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(mat, curSX - 3.5f, curSY - 3.5f, 7.0f, 7.0f, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        float hueX = svX + svW + 6.0f;
        float hueY = svY;
        float hueW = 12.0f;
        float hueH = svH;

        Draw2DProcessor.INSTANCE.a(mat, Identifier.of("delta", "pictures/color.png"), hueX, hueY, hueW, hueH, 2.5f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(mat, hueX, hueY, hueW, hueH, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        float hThumbY = Math.clamp(hueY + hue * (hueH - 2.5f), hueY, hueY + hueH - 2.5f);
        Draw2DProcessor.INSTANCE.drawGlow(mat, hueX - 1.0f, hThumbY - 0.5f, hueW + 2.0f, 3.5f, 1.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 2.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, hueX - 1.0f, hThumbY, hueW + 2.0f, 2.5f, 1.25f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        float alphaX = hueX + hueW + 6.0f;
        float alphaY = svY;
        float alphaW = 12.0f;
        float alphaH = svH;

        Draw2DProcessor.INSTANCE.a(mat, Identifier.of("delta", "pictures/opacity.png"), alphaX, alphaY, alphaW, alphaH, 2.5f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
        int topRgb = (curCol & 0xFFFFFF) | 0xFF000000;
        int botRgb = (curCol & 0xFFFFFF) | 0x00000000;
        Draw2DProcessor.INSTANCE.drawGradient(mat, alphaX, alphaY, alphaW, alphaH, 2.5f,
                ColorUtil.withAlphaFloat(topRgb, pAnim),
                ColorUtil.withAlphaFloat(topRgb, pAnim),
                ColorUtil.withAlphaFloat(botRgb, pAnim),
                ColorUtil.withAlphaFloat(botRgb, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(mat, alphaX, alphaY, alphaW, alphaH, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        float aThumbY = Math.clamp(alphaY + (1.0f - curAlpha) * (alphaH - 2.5f), alphaY, alphaY + alphaH - 2.5f);
        Draw2DProcessor.INSTANCE.drawGlow(mat, alphaX - 1.0f, aThumbY - 0.5f, alphaW + 2.0f, 3.5f, 1.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 2.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, alphaX - 1.0f, aThumbY, alphaW + 2.0f, 2.5f, 1.25f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        float hexY = svY + svH + 7.0f;
        float swS = 13.0f;
        Draw2DProcessor.INSTANCE.drawRect(mat, svX, hexY, swS, swS, 2.5f, ColorUtil.withAlphaFloat(curCol, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(mat, svX, hexY, swS, swS, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (45 * pAnim)));

        Fonts.ROCKSTAR_ROUND_BOLD.a(mat, cs.getHex(), svX + swS + 6.0f, hexY + 1.5f, 11.5f, ColorUtil.rgba(230, 235, 245, (int) (255 * pAnim)));

        float resetX = popX + popW - 18.0f;
        Fonts.ROCKSTAR_ROUND_BOLD.a(mat, "↺", resetX, hexY + 1.0f, 11.5f, ColorUtil.rgba(160, 165, 180, (int) (200 * pAnim)));

        float presetY = hexY + 17.0f;
        int[] presets = new int[] {
                0xFF8B5CF6, 0xFF00CEC9, 0xFF10B981, 0xFFF59E0B,
                0xFFF43F5E, 0xFF3B82F6, 0xFFFFFFFF, 0xFF181A24
        };
        float spacing = (popW - 16.0f) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            float px = popX + 8.0f + i * spacing + spacing / 2.0f;
            float py = presetY + 4.5f;
            Draw2DProcessor.INSTANCE.drawRect(mat, px - 4.5f, py - 4.5f, 9.0f, 9.0f, 4.5f, ColorUtil.withAlphaFloat(presets[i], pAnim));
            if ((presets[i] & 0xFFFFFF) == (curCol & 0xFFFFFF)) {
                Draw2DProcessor.INSTANCE.drawOutline(mat, px - 5.5f, py - 5.5f, 11.0f, 11.0f, 5.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
            }
        }

        DrawBatcher.flushPending();
    }

    private void updateColorSV(ColorSetting setting, float svX, float svY, float svW, float svH, float mouseX, float mouseY) {
        float[] hsb = setting.getHSB();
        float curAlpha = setting.getAlpha();
        float sat = Math.clamp((mouseX - svX) / svW, 0.0f, 1.0f);
        float bri = 1.0f - Math.clamp((mouseY - svY) / svH, 0.0f, 1.0f);
        setting.setFromHSBA(hsb[0], sat, bri, curAlpha);
    }

    private void updateColorHueBar(ColorSetting setting, float hueY, float hueH, float mouseY) {
        float[] hsb = setting.getHSB();
        float curAlpha = setting.getAlpha();
        float hue = Math.clamp((mouseY - hueY) / hueH, 0.0f, 1.0f);
        setting.setFromHSBA(hue, hsb[1], hsb[2], curAlpha);
    }

    private void updateColorAlphaBar(ColorSetting setting, float alphaY, float alphaH, float mouseY) {
        float[] hsb = setting.getHSB();
        float alpha = 1.0f - Math.clamp((mouseY - alphaY) / alphaH, 0.0f, 1.0f);
        setting.setFromHSBA(hsb[0], hsb[1], hsb[2], alpha);
    }

    private void renderSliderSetting(DrawContext context, SliderSetting slider, float x, float y, float width, float alpha) {
        String valStr = getSliderValueString(slider);
        float valW = ClientFont.getWidth(valStr, ClientFont.SF_REGULAR);
        float valX = x + width - valW;

        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            float maxLabelW = Math.max(10.0f, valX - x - 8.0f);
            String label = getTruncatedLabel(slider.getName(), maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x, y + 2.0f, ClientFont.SF_REGULAR, labelColor);
            ClientFont.draw(context, valStr, valX, y + 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        }

        float trackCenterY = y + 22.0f;
        float trackW = width;
        float trackX = x;

        final float targetProgress = Math.clamp((float) ((slider.get() - slider.getMin()) / (slider.getMax() - slider.getMin())), 0.0f, 1.0f);
        float curProgress = this.sliderAnimatedProgress.computeIfAbsent(slider, k -> targetProgress);
        curProgress = curProgress + (targetProgress - curProgress) * 0.28f;
        if (Math.abs(curProgress - targetProgress) < 0.0005f) {
            curProgress = targetProgress;
        }
        this.sliderAnimatedProgress.put(slider, curProgress);

        float fillW = trackW * curProgress;

        float trackH = 3.0f;
        float trackY = trackCenterY - trackH / 2.0f;
        Render2D.drawCapsule(context, trackX, trackY, trackW, trackH, ColorUtil.rgba(255, 255, 255, (int) (20 * alpha)));
        if (fillW > 0.0f) {
            enableGuiScissor(context, trackX, trackY - 1.0f, (float) Math.ceil(trackX + fillW), (float) Math.ceil(trackY + trackH + 1.0f));
            Render2D.drawCapsule(context, trackX, trackY, trackW, trackH, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
            disableGuiScissor(context);
        }

        float thumbR = 5.0f;
        float thumbCenterX = trackX + fillW;
        float thumbCenterY = trackCenterY;
        Render2D.drawCircleShadow(context, thumbCenterX, thumbCenterY, thumbR, 4.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.45f * alpha));
        Render2D.drawCircle(context, thumbCenterX, thumbCenterY, thumbR, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
        Render2D.drawCircleOutline(context, thumbCenterX, thumbCenterY, thumbR, 1.0f, ColorUtil.rgba(0, 0, 0, (int) (45 * alpha)));
    }

    private void openDropdown(ModeSetting mode, float x, float y, float w) {
        this.openDropdownSetting = mode;
        this.openDropdownX = x;
        this.openDropdownY = y;
        this.openDropdownW = w;
        this.openDropdownScrollY = 0.0f;
        float maxW = 0.0f;
        for (String m : mode.getModes()) {
            maxW = Math.max(maxW, ClientFont.getWidth(m, ClientFont.SF_REGULAR));
        }
        this.openDropdownMaxModeW = maxW;
    }

    private void renderOpenDropdown(DrawContext context, double mouseX, double mouseY, float alpha) {
        boolean shouldShow = (this.openDropdownSetting != null);
        if (shouldShow) {
            this.openDropdownAnim = Math.min(1.0f, this.openDropdownAnim + 0.20f);
        } else {
            this.openDropdownAnim = Math.max(0.0f, this.openDropdownAnim - 0.20f);
        }
        if (this.openDropdownAnim <= 0.01f || this.openDropdownSetting == null) {
            return;
        }

        float dAnim = this.openDropdownAnim * alpha;
        List<String> dModes = this.openDropdownSetting.getModes();
        float dItemH = 19.0f;
        int maxVisible = 6;
        int displayItems = Math.min(dModes.size(), maxVisible);
        float dListH = displayItems * dItemH;
        float dPopH = dListH + 6.0f;

        if (this.openDropdownMaxModeW <= 0.0f) {
            float maxW = 0.0f;
            for (String m : dModes) {
                maxW = Math.max(maxW, ClientFont.getWidth(m, ClientFont.SF_REGULAR));
            }
            this.openDropdownMaxModeW = maxW;
        }
        float dPopW = Math.max(this.openDropdownW, this.openDropdownMaxModeW + 28.0f);
        float btnRight = this.openDropdownX + this.openDropdownW;
        float dPopX = btnRight - dPopW;
        float dPopY = this.openDropdownY;

        float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (dPopX < 8.0f) {
            dPopX = 8.0f;
        }
        if (dPopX + dPopW > screenW - 8.0f) {
            dPopX = screenW - dPopW - 8.0f;
        }
        if (dPopY + dPopH > screenH - 8.0f) {
            dPopY = Math.max(8.0f, dPopY - dPopH - 22.0f);
        }

        this.openDropdownMaxScroll = Math.max(0.0f, dModes.size() * dItemH - dListH);
        this.openDropdownScrollY = Math.clamp(this.openDropdownScrollY, 0.0f, this.openDropdownMaxScroll);

        float dropOffset = (1.0f - this.openDropdownAnim) * 5.0f;
        float renderPopY = dPopY + dropOffset;

        Render2D.drawShadow(context, dPopX, renderPopY, dPopW, dPopH, 6.0f, 12.0f, ColorUtil.rgba(0, 0, 0, (int) (180 * dAnim)));
        Render2D.drawRoundedRect(context, dPopX, renderPopY, dPopW, dPopH, 6.0f, ColorUtil.rgba(18, 20, 26, (int) (255 * dAnim)));
        Render2D.drawRoundedOutline(context, dPopX, renderPopY, dPopW, dPopH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (38 * dAnim)));

        enableGuiScissor(context, dPopX, renderPopY + 3.0f, dPopX + dPopW, renderPopY + 3.0f + dListH);

        float curItemY = renderPopY + 3.0f - this.openDropdownScrollY;
        String curMode = this.openDropdownSetting.get();

        for (String mName : dModes) {
            boolean isCur = mName.equalsIgnoreCase(curMode);
            boolean isHov = mouseX >= dPopX && mouseX <= (dPopX + dPopW) && mouseY >= curItemY && mouseY <= (curItemY + dItemH);

            if (curItemY + dItemH >= renderPopY + 3.0f && curItemY <= renderPopY + 3.0f + dListH) {
                if (isHov || isCur) {
                    int rBg = isCur ? ColorUtil.withAlphaFloat(Theme.getAccent(), 0.24f * dAnim) : ColorUtil.rgba(255, 255, 255, (int) (14 * dAnim));
                    Render2D.drawRoundedRect(context, dPopX + 3.0f, curItemY + 1.0f, dPopW - 6.0f, dItemH - 2.0f, 3.0f, rBg);
                }

                int txtColor = isCur ? Theme.getAccent() : (isHov ? 0xFFFFFFFF : ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, dAnim));
                ClientFont.draw(context, mName, dPopX + 8.0f, curItemY + 5.0f, ClientFont.SF_REGULAR, txtColor);

                if (isCur) {
                    float dotR = 2.5f;
                    Render2D.drawCircle(context, dPopX + dPopW - 9.0f, curItemY + dItemH / 2.0f, dotR, ColorUtil.withAlphaFloat(Theme.getAccent(), dAnim));
                }
            }
            curItemY += dItemH;
        }

        disableGuiScissor(context);
    }

    private String getSliderValueString(SliderSetting slider) {
        Double last = lastSliderValues.get(slider);
        double cur = slider.get();
        if (last == null || Math.abs(last - cur) > 0.0001) {
            String str = String.format(Locale.US, "%.1f", cur);
            cachedSliderStrings.put(slider, str);
            lastSliderValues.put(slider, cur);
            return str;
        }
        return cachedSliderStrings.get(slider);
    }

    private void renderKeybindRow(DrawContext context, Module module, float x, float y, float width, float alpha) {
        boolean isListening = (listeningKeybindModule == module);
        String keyText = getKeybindString(module);

        float rawBtnW = ClientFont.getWidth(keyText, ClientFont.SF_REGULAR) + 16.0f;
        float maxBtnW = Math.max(50.0f, width * 0.48f);
        float btnW = Math.min(rawBtnW, maxBtnW);
        float btnH = 18.0f;
        float btnX = x + width - btnW - 6.0f;
        float btnY = y + 1.0f;

        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            float maxLabelW = Math.max(10.0f, btnX - (x + 6.0f) - 10.0f);
            String label = getTruncatedLabel("Bind key", maxLabelW, ClientFont.SF_REGULAR);
            ClientFont.draw(context, label, x + 6.0f, y + 4.0f, ClientFont.SF_REGULAR, labelColor);
        }

        int btnBg = isListening ? Theme.getAccentMuted() : ColorUtil.rgba(35, 35, 37, (int) (220 * alpha));
        int btnBorder = isListening ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (18 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 5.0f, btnBg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 5.0f, 1.0f, btnBorder);

        if (!FpsBenchmark.overrideNoText) {
            int textColor = isListening ? Theme.getAccentHover() : Theme.TEXT_TITLE;
            String dispKey = getTruncatedLabel(keyText, btnW - 8.0f, ClientFont.SF_REGULAR);
            ClientFont.drawCentered(context, dispKey, btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(textColor, alpha));
        }
    }

    private String getKeybindString(Module module) {
        if (listeningKeybindModule == module) {
            return "Press Key/Mouse...";
        }
        int k = module.getKey();
        Integer last = lastModuleKeys.get(module);
        if (last == null || last != k) {
            String str;
            if (k == 0) {
                str = "None";
            } else if (k >= 1000) {
                int mouseBtn = k - 1000;
                str = switch (mouseBtn) {
                    case 0 -> "LMB";
                    case 1 -> "RMB";
                    case 2 -> "MMB";
                    default -> "M" + (mouseBtn + 1);
                };
            } else {
                String name = GLFW.glfwGetKeyName(k, 0);
                str = (name != null) ? name.toUpperCase() : "Key " + k;
            }
            cachedKeybindStrings.put(module, str);
            lastModuleKeys.put(module, k);
            return str;
        }
        return cachedKeybindStrings.get(module);
    }

    private List<Module> getFilteredModules() {
        if (!modulesDirty) {
            return cachedFilteredModules;
        }
        cachedFilteredModules.clear();
        if (selectedSearchModule != null) {
            if (!selectedSearchModule.isBlocked()) {
                cachedFilteredModules.add(selectedSearchModule);
            }
            modulesDirty = false;
            return cachedFilteredModules;
        }
        List<Module> all = EggClient.getInstance().getModuleManager().getModules();
        if (!searchQuery.trim().isEmpty()) {
            String q = searchQuery.toLowerCase().trim();
            String qEn = LayoutHelper.toEn(q).toLowerCase().trim();
            String qRu = LayoutHelper.toRu(q).toLowerCase().trim();
            for (Module m : all) {
                if (!m.isBlocked() && matchesModule(m, q, qEn, qRu)) {
                    cachedFilteredModules.add(m);
                }
            }
        } else {
            for (Module m : all) {
                if (!m.isBlocked() && m.getCategory() == selectedCategory) {
                    cachedFilteredModules.add(m);
                }
            }
        }
        cachedFilteredModules.sort((a, b) -> Boolean.compare(b.isFavorite(), a.isFavorite()));
        modulesDirty = false;
        return cachedFilteredModules;
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }

        this.lastRawClickX = (float) click.x();
        float scale = currentGuiScale;
        float cx = getGuiCenterX();
        float cy = getGuiCenterY();
        float mouseX = (float) ((click.x() - cx) / scale + cx);
        float mouseY = (float) ((click.y() - cy) / scale + cy);
        int button = click.button();

        if (listeningBindSetting != null) {
            if (button == 1) {
                listeningBindSetting.setKey(0);
                egg.client.config.ConfigManager.save();
                listeningBindSetting = null;
                GuiSoundManager.playClick();
                return true;
            } else if (button >= 3) {
                listeningBindSetting.setKey(1000 + button);
                egg.client.config.ConfigManager.save();
                listeningBindSetting = null;
                GuiSoundManager.playClick();
                return true;
            } else if (button == 0) {
                listeningBindSetting = null;
            }
        }

        if (listeningKeybindModule != null) {
            if (button == 1) {
                listeningKeybindModule.setKey(0);
                egg.client.config.ConfigManager.save();
                lastModuleKeys.remove(listeningKeybindModule);
                listeningKeybindModule = null;
                GuiSoundManager.playClick();
                return true;
            } else if (button >= 3) {
                listeningKeybindModule.setKey(1000 + button);
                egg.client.config.ConfigManager.save();
                lastModuleKeys.remove(listeningKeybindModule);
                listeningKeybindModule = null;
                GuiSoundManager.playClick();
                return true;
            } else if (button == 0) {
                listeningKeybindModule = null;
            }
        }

        if (this.openColorPickerSetting != null) {
            float popW = 144.0f;
            float popH = 132.0f;
            float popX = this.colorPopupX;
            float popY = this.colorPopupY;

            float[] curBadge = this.colorSettingBadgePositions.get(this.openColorPickerSetting);
            if (curBadge != null && mouseX >= curBadge[0] && mouseX <= curBadge[0] + curBadge[2]
                    && mouseY >= curBadge[1] - 2.0f && mouseY <= curBadge[1] + curBadge[3] + 2.0f) {
                this.openColorPickerSetting = null;
                this.colorDragMode = ColorDragMode.NONE;
                return true;
            }

            float svX = popX + 8.0f;
            float svY = popY + 8.0f;
            float svW = 92.0f;
            float svH = 74.0f;

            float hueX = svX + svW + 6.0f;
            float hueY = svY;
            float hueW = 12.0f;
            float hueH = svH;

            float alphaX = hueX + hueW + 6.0f;
            float alphaY = svY;
            float alphaW = 12.0f;
            float alphaH = svH;

            float hexY = svY + svH + 7.0f;
            float presetY = hexY + 17.0f;

            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + popH) {
                if (button == 0) {
                    if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                        this.colorDragMode = ColorDragMode.AREA_SV;
                        updateColorSV(this.openColorPickerSetting, svX, svY, svW, svH, mouseX, mouseY);
                        return true;
                    }
                    if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                        this.colorDragMode = ColorDragMode.HUE;
                        updateColorHueBar(this.openColorPickerSetting, hueY, hueH, mouseY);
                        return true;
                    }
                    if (mouseX >= alphaX && mouseX <= alphaX + alphaW && mouseY >= alphaY && mouseY <= alphaY + alphaH) {
                        this.colorDragMode = ColorDragMode.ALPHA;
                        updateColorAlphaBar(this.openColorPickerSetting, alphaY, alphaH, mouseY);
                        return true;
                    }

                    if (mouseX >= popX + popW - 24.0f && mouseX <= popX + popW - 4.0f && mouseY >= hexY - 2.0f && mouseY <= hexY + 16.0f) {
                        this.openColorPickerSetting.reset();
                        egg.client.config.ConfigManager.save();
                        return true;
                    }

                    if (mouseX >= svX && mouseX <= svX + 70.0f && mouseY >= hexY - 2.0f && mouseY <= hexY + 16.0f) {
                        if (client != null && client.keyboard != null) {
                            client.keyboard.setClipboard(this.openColorPickerSetting.getHex());
                        }
                        return true;
                    }

                    int[] presets = new int[] {
                            0xFF8B5CF6, 0xFF00CEC9, 0xFF10B981, 0xFFF59E0B,
                            0xFFF43F5E, 0xFF3B82F6, 0xFFFFFFFF, 0xFF181A24
                    };
                    float spacing = (popW - 16.0f) / presets.length;
                    for (int i = 0; i < presets.length; i++) {
                        float px = popX + 8.0f + i * spacing + spacing / 2.0f;
                        float py = presetY + 4.5f;
                        float dx = mouseX - px;
                        float dy = mouseY - py;
                        if (dx * dx + dy * dy <= 6.0f * 6.0f) {
                            this.openColorPickerSetting.set(presets[i]);
                            egg.client.config.ConfigManager.save();
                            return true;
                        }
                    }
                }
                return true;
            } else {
                this.openColorPickerSetting = null;
                this.colorDragMode = ColorDragMode.NONE;
            }
        }

        if (this.openDropdownSetting != null) {
            List<String> dModes = this.openDropdownSetting.getModes();
            float dItemH = 19.0f;
            int maxVisible = 6;
            int displayItems = Math.min(dModes.size(), maxVisible);
            float dListH = displayItems * dItemH;
            float dPopH = dListH + 6.0f;

            if (this.openDropdownMaxModeW <= 0.0f) {
                float maxW = 0.0f;
                for (String m : dModes) {
                    maxW = Math.max(maxW, ClientFont.getWidth(m, ClientFont.SF_REGULAR));
                }
                this.openDropdownMaxModeW = maxW;
            }
            float dPopW = Math.max(this.openDropdownW, this.openDropdownMaxModeW + 28.0f);
            float btnRight = this.openDropdownX + this.openDropdownW;
            float dPopX = btnRight - dPopW;
            float dPopY = this.openDropdownY;

            float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            if (dPopX < 8.0f) {
                dPopX = 8.0f;
            }
            if (dPopX + dPopW > screenW - 8.0f) {
                dPopX = screenW - dPopW - 8.0f;
            }
            if (dPopY + dPopH > screenH - 8.0f) {
                dPopY = Math.max(8.0f, dPopY - dPopH - 22.0f);
            }

            if (mouseX >= dPopX && mouseX <= dPopX + dPopW && mouseY >= dPopY && mouseY <= dPopY + dPopH) {
                float curItemY = dPopY + 3.0f - this.openDropdownScrollY;
                for (String mName : dModes) {
                    if (mouseY >= curItemY && mouseY <= curItemY + dItemH) {
                        this.openDropdownSetting.set(mName);
                        this.openDropdownSetting = null;
                        egg.client.config.ConfigManager.save();
                        return true;
                    }
                    curItemY += dItemH;
                }
                return true;
            } else {
                this.openDropdownSetting = null;
            }
        }

        float guiWidth = getGuiBaseWidth();
        float guiHeight = getGuiBaseHeight();
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        float collapseProgress = sidebarCollapseAnim.getValue();
        float sidebarWidth = MathHelper.lerp(collapseProgress, 132.0f, 36.0f);
        float headerHeight = 44.0f;

        float btnW = 13.0f;
        float btnH = 10.0f;
        float btnX = MathHelper.lerp(collapseProgress, gx + sidebarWidth - btnW - 12.0f, gx + (sidebarWidth - btnW) / 2.0f);
        float btnY = gy + (34.0f - btnH) / 2.0f;

        boolean btnClicked = false;
        if (collapseProgress > 0.5f) {
            if (mouseX >= gx && mouseX <= (gx + sidebarWidth) && mouseY >= gy && mouseY <= (gy + 34.0f)) {
                btnClicked = true;
            }
        } else {
            if (mouseX >= (btnX - 5.0f) && mouseX <= (btnX + btnW + 5.0f) && mouseY >= (btnY - 5.0f) && mouseY <= (btnY + btnH + 5.0f)) {
                btnClicked = true;
            }
        }

        if (btnClicked) {
            sidebarCollapsed = !sidebarCollapsed;
            sidebarCollapseAnim.setTarget(sidebarCollapsed ? 1.0f : 0.0f);
            savedSidebarCollapsed = sidebarCollapsed;
            GuiSoundManager.playClick();
            return true;
        }

        if (collapseProgress < 0.3f && searchFocused && !searchQuery.trim().isEmpty() && selectedSearchModule == null) {
            float popW = sidebarWidth - 16.0f;
            float popX = gx + 8.0f;
            float popY = gy + 60.0f;
            List<Module> matches = getSearchMatches();
            int count = matches.size();
            int showCount = Math.min(5, count);
            float itemH = 27.0f;
            float popH = showCount > 0 ? (showCount * itemH + 8.0f) : 28.0f;

            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + popH) {
                if (showCount > 0) {
                    int clickedIdx = (int) ((mouseY - (popY + 4.0f)) / itemH);
                    if (clickedIdx >= 0 && clickedIdx < showCount) {
                        selectSearchModule(matches.get(clickedIdx));
                        return true;
                    }
                }
                return true;
            }
        }

        float searchH = 22.0f * (1.0f - collapseProgress);
        if (collapseProgress < 0.3f && searchH > 2.0f) {
            float searchW = sidebarWidth - 16.0f;
            float searchX = gx + 8.0f;
            float searchY = gy + 34.0f + 2.0f;
            if (mouseX >= searchX && mouseX <= (searchX + searchW) && mouseY >= searchY && mouseY <= (searchY + searchH)) {
                if (!searchQuery.isEmpty()) {
                    float clearSize = 14.0f;
                    float clearX = searchX + searchW - clearSize - 4.5f;
                    float clearY = searchY + (searchH - clearSize) / 2.0f;
                    if (mouseX >= clearX - 3.0f && mouseX <= clearX + clearSize + 3.0f && mouseY >= clearY - 3.0f && mouseY <= clearY + clearSize + 3.0f) {
                        searchQuery = "";
                        searchCursor = 0;
                        searchSelectionEnd = 0;
                        selectedSearchModule = null;
                        searchSelectedIndex = 0;
                        markModulesDirty();
                        GuiSoundManager.playClick();
                        return true;
                    }
                }
                searchFocused = true;
                searchSelecting = true;
                lastBlinkTime = System.currentTimeMillis();
                if (selectedSearchModule != null) {
                    selectedSearchModule = null;
                    searchQuery = "";
                    searchCursor = 0;
                    searchSelectionEnd = 0;
                    searchSelectedIndex = 0;
                }
                int idx = getSearchCursorIndex((float) mouseX, searchX);
                searchCursor = idx;
                searchSelectionEnd = idx;
                markModulesDirty();
                return true;
            } else {
                searchFocused = false;
                searchSelecting = false;
            }
        }

        float listStartY = gy + 34.0f + 2.0f + searchH + MathHelper.lerp(collapseProgress, 4.0f, 4.0f);
        if (mouseX >= gx && mouseX <= gx + sidebarWidth && mouseY >= listStartY && mouseY <= gy + guiHeight) {
            float itemH = 22.0f;
            float startItemY = listStartY + 4.0f;
            float itemY = startItemY - sidebarScrollOffset;

            String lastSec = "";
            for (Category cat : Category.values()) {
                if (!cat.getSection().equals(lastSec)) {
                    lastSec = cat.getSection();
                    if (itemY > startItemY - sidebarScrollOffset + 2.0f) {
                        itemY += MathHelper.lerp(collapseProgress, 8.0f, 2.0f);
                    }
                    itemY += MathHelper.lerp(collapseProgress, 13.0f, 4.0f);
                }

                if (mouseX >= gx && mouseX <= (gx + sidebarWidth) &&
                        mouseY >= itemY && mouseY <= (itemY + itemH)) {
                    if (selectedCategory != cat || selectedSearchModule != null) {
                        selectedCategory = cat;
                        ensureCategoryVisible(cat);
                        selectedSearchModule = null;
                        searchQuery = "";
                        searchFocused = false;
                        headerTitle = cat.getSection() + " - " + cat.getName();
                        headerFadeAnim.setValue(0.0f);
                        headerFadeAnim.setTarget(1.0f);
                        scrollOffset = 0.0f;
                        targetScroll = 0.0f;
                        scrollVelocity = 0.0f;
                        smoothedScrollVelocity = 0.0f;
                        markModulesDirty();
                        resetCascade();
                    }
                    return true;
                }
                itemY += itemH + 3.0f;
            }
        }

        if (collapseProgress > 0.6f && deferredRailTooltipCat != null) {
            float ttX = gx + sidebarWidth + 8.0f;
            float ttY = Math.clamp(deferredRailTooltipY + 1.0f, gy + 6.0f, gy + guiHeight - 24.0f);
            String ttText = deferredRailTooltipCat.getName() + " (" + getCategoryModuleCount(deferredRailTooltipCat) + ")";
            float ttW = ClientFont.getWidth(ttText, ClientFont.SF_MEDIUM) + 14.0f;
            float ttH = 20.0f;
            if (mouseX >= ttX && mouseX <= (ttX + ttW) && mouseY >= ttY && mouseY <= (ttY + ttH)) {
                if (selectedCategory != deferredRailTooltipCat || selectedSearchModule != null) {
                    selectedCategory = deferredRailTooltipCat;
                    ensureCategoryVisible(deferredRailTooltipCat);
                    selectedSearchModule = null;
                    searchQuery = "";
                    searchFocused = false;
                    headerTitle = deferredRailTooltipCat.getSection() + " - " + deferredRailTooltipCat.getName();
                    headerFadeAnim.setValue(0.0f);
                    headerFadeAnim.setTarget(1.0f);
                    scrollOffset = 0.0f;
                    targetScroll = 0.0f;
                    scrollVelocity = 0.0f;
                    smoothedScrollVelocity = 0.0f;
                    markModulesDirty();
                    resetCascade();
                }
                return true;
            }
        }

        float contentX = gx + sidebarWidth + 14.0f;
        float contentY = gy + headerHeight + 10.0f;
        float contentW = guiWidth - sidebarWidth - 28.0f;
        float contentH = guiHeight - headerHeight - 16.0f;

        if (selectedCategory == Category.INVENTORY) {
            if (egg.client.ui.section.InventoryConfigPanel.mouseClicked(contentX, contentY, contentW, contentH, mouseX, mouseY, button)) {
                return true;
            }
            return true;
        } else if (selectedCategory == Category.COSMETICS) {
            if (egg.client.ui.section.CosmeticsPanel.mouseClicked(contentX, contentY, contentW, contentH, mouseX, mouseY, button)) {
                return true;
            }
            return true;
        } else if (selectedCategory == Category.SETTINGS) {
            if (checkClientSettingsClick(contentX, contentY, contentW, contentH, mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (mouseX >= contentX && mouseX <= (contentX + contentW) && mouseY >= contentY && mouseY <= (contentY + contentH)) {
            float colGap = 10.0f;
            float cardGap = 10.0f;
            float cardW = (contentW - colGap) / 2.0f;
            List<Module> modules = getFilteredModules();
            float col0Y = contentY - scrollOffset;
            float col1Y = contentY - scrollOffset;

            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                Animation anim = cardCascadeAnims.get(m);
                float cascade = (anim != null) ? anim.getValue() : 1.0f;
                float offsetY = (1.0f - cascade) * 10.0f;
                float expandM = m.getExpandAnimation().getValue();
                float headerH = getModuleHeaderHeight(m, cardW);
                float h = headerH + (expandM > 0.001f ? (getSettingsHeight(m) * expandM) : 0.0f);
                boolean isLeft = (i % 2 == 0);
                float cardX = isLeft ? contentX : (contentX + cardW + colGap);
                float curColY = isLeft ? col0Y : col1Y;
                float cardY = curColY + offsetY;

                if (checkModuleClick(m, cardX, cardY, cardW, h, mouseX, mouseY, button)) {
                    return true;
                }

                if (isLeft) {
                    col0Y += h + cardGap;
                } else {
                    col1Y += h + cardGap;
                }
            }
        }

        if (listeningKeybindModule != null && button == 2) {
            listeningKeybindModule = null;
        }
        if (listeningBindSetting != null && button == 2) {
            listeningBindSetting = null;
        }

        return super.mouseClicked(click, bl);
    }

    private boolean checkModuleClick(Module module, float cardX, float cardY, float cardW, float cardH, float mouseX, float mouseY, int button) {
        if (mouseX < cardX || mouseX > cardX + cardW || mouseY < cardY || mouseY > cardY + cardH) {
            return false;
        }

        if (button == 2) {
            listeningKeybindModule = (listeningKeybindModule == module) ? null : module;
            GuiSoundManager.playClick();
            return true;
        }

        float toggleW = 34.0f;
        float toggleH = 16.0f;
        float toggleX = cardX + cardW - toggleW - 12.0f;
        float toggleY = cardY + 10.0f;

        if (mouseX >= toggleX - 2.0f && mouseX <= (toggleX + toggleW + 2.0f) && mouseY >= toggleY - 2.0f && mouseY <= (toggleY + toggleH + 2.0f)) {
            module.toggle();
            egg.client.config.ConfigManager.save();
            return true;
        }

        float starX = toggleX - 14.0f;
        if (mouseX >= starX - 4 && mouseX <= starX + 11 && mouseY >= toggleY - 2 && mouseY <= toggleY + 18) {
            module.toggleFavorite();
            Animation starAnim = starScaleAnims.get(module);
            if (starAnim != null) {
                starAnim.setValue(1.35f);
                starAnim.setTarget(1.0f);
            }
            markModulesDirty();
            egg.client.config.ConfigManager.save();
            return true;
        }

        float headerH = getModuleHeaderHeight(module, cardW);
        boolean hasSettings = !module.getSettings().isEmpty() || (module instanceof WorldTweaks);
        if (mouseY <= cardY + headerH) {
            float dotsX = hasSettings ? (starX - 11.0f) : starX;
            if (button == 1 || (hasSettings && mouseX >= dotsX - 4 && mouseX <= dotsX + 11 && mouseY >= toggleY - 2 && mouseY <= toggleY + 18)) {
                if (hasSettings) {
                    module.toggleExpanded();
                    GuiSoundManager.playClick();
                }
                return true;
            }
        }

        float expandProgress = module.getExpandAnimation().getValue();
        if (expandProgress > 0.20f && mouseY > (cardY + headerH)) {
            float sY = cardY + headerH + 6.0f;
            float settingsW = cardW - 24.0f;
            float settingsX = cardX + 12.0f;

            if (module instanceof WorldTweaks wt) {
                if (WorldTweaksUI.mouseClicked(wt, settingsX, sY, settingsW, mouseX, mouseY, button)) {
                    return true;
                }
                sY += WorldTweaksUI.getTotalHeight();
            }
            for (Setting s : module.getSettings()) {
                if (isWorldTweaksCustomSetting(module, s)) continue;
                float progress = s.getVisibilityProgress();
                if (progress <= 0.05f) continue;
                if (s instanceof BooleanSetting bool) {
                    float itemH = 26.0f * progress;
                    if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f)) {
                        bool.set(!bool.get());
                        egg.client.config.ConfigManager.save();
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof SliderSetting slider) {
                    float itemH = 34.0f * progress;
                    if (progress > 0.6f && mouseY >= sY + 6.0f && mouseY <= (sY + 30.0f)) {
                        draggingSlider = slider;
                        dragTrackX = settingsX;
                        dragTrackW = settingsW;
                        dragStartClickX = this.lastRawClickX;
                        dragStartScale = currentGuiScale;
                        updateSliderValue(slider, dragTrackX, dragTrackW, mouseX);
                        dragStartValue = slider.get();
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof ModeSetting mode) {
                    float itemH = 28.0f * progress;
                    float btnW = getModeButtonWidth(mode, settingsW);
                    float btnX = settingsX + settingsW - btnW;
                    if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f)) {
                        if (this.openDropdownSetting == mode) {
                            this.openDropdownSetting = null;
                        } else {
                            openDropdown(mode, btnX, sY + 22.0f, btnW);
                        }
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof ColorSetting colorSetting) {
                    float itemH = 26.0f * progress;
                    float[] storedBadge = this.colorSettingBadgePositions.get(colorSetting);
                    float badgeW = storedBadge != null ? storedBadge[2] : getColorBadgeWidth(colorSetting);
                    float badgeH = storedBadge != null ? storedBadge[3] : 18.0f;
                    float badgeX = storedBadge != null ? storedBadge[0] : (settingsX + settingsW - badgeW);
                    float badgeY = storedBadge != null ? storedBadge[1] : (sY + 3.0f);
                    if (progress > 0.6f && mouseY >= badgeY - 2.0f && mouseY <= badgeY + badgeH + 2.0f && mouseX >= badgeX && mouseX <= badgeX + badgeW) {
                        if (this.openColorPickerSetting == colorSetting) {
                            this.openColorPickerSetting = null;
                            this.colorDragMode = ColorDragMode.NONE;
                        } else {
                            this.openColorPickerSetting = colorSetting;
                            this.colorDragMode = ColorDragMode.NONE;
                            this.colorPopupAnim = 0.0f;
                            float popW = 144.0f;
                            float popH = 132.0f;
                            float px = badgeX + badgeW - popW;
                            float py = badgeY + badgeH + 4.0f;
                            int screenW = client != null ? client.getWindow().getScaledWidth() : 800;
                            int screenH = client != null ? client.getWindow().getScaledHeight() : 600;
                            if (px + popW > screenW - 8.0f) px = screenW - popW - 8.0f;
                            if (px < 8.0f) px = 8.0f;
                            if (py + popH > screenH - 8.0f) py = badgeY - popH - 4.0f;
                            if (py < 8.0f) py = 8.0f;
                            this.colorPopupX = px;
                            this.colorPopupY = py;
                        }
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof ButtonSetting btn) {
                    float itemH = 28.0f * progress;
                    float bW = settingsW;
                    float bH = 22.0f;
                    float bX = settingsX;
                    float bY = sY + 2.0f;
                    if (progress > 0.6f && mouseX >= bX && mouseX <= (bX + bW) && mouseY >= bY && mouseY <= (bY + bH)) {
                        btn.run();
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof BindSetting bind) {
                    float itemH = 28.0f * progress;
                    if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f)) {
                        if (button == 1) {
                            bind.setKey(0);
                            listeningBindSetting = null;
                            egg.client.config.ConfigManager.save();
                        } else {
                            listeningBindSetting = (listeningBindSetting == bind) ? null : bind;
                        }
                        GuiSoundManager.playClick();
                        return true;
                    }
                    sY += itemH;
                }
            }

            if (mouseY >= sY && mouseY <= (sY + 24.0f)) {
                listeningKeybindModule = (listeningKeybindModule == module) ? null : module;
                return true;
            }
        }

        Animation press = cardPressAnims.get(module);
        if (press != null) {
            press.setValue(0.985f);
            press.setTarget(1.0f);
        }

        return true;
    }

    private boolean checkClientSettingsClick(float contentX, float contentY, float contentW, float contentH, float mouseX, float mouseY, int button) {
        if (mouseX < contentX || mouseX > contentX + contentW || mouseY < contentY || mouseY > contentY + contentH) {
            return false;
        }

        ClientSettings cs = ModuleManager.getInstance().getModule(ClientSettings.class);
        if (cs == null) return false;

        float currentY = contentY - scrollOffset;
        float colGap = 8.0f;
        float cardW = (contentW - colGap) / 2.0f;

        float col0X = contentX;
        float col1X = contentX + cardW + colGap;

        ensureClientSettingsSplit(cs);
        List<Setting> leftSettings = cachedClientSettingsLeft;
        List<Setting> rightSettings = cachedClientSettingsRight;

        float leftH = calculateSettingsGroupHeight(leftSettings);
        float rightH = calculateSettingsGroupHeight(rightSettings);

        if (checkSettingsGroupClick(leftSettings, col0X, currentY, cardW, leftH, mouseX, mouseY, button)) {
            return true;
        }
        if (checkSettingsGroupClick(rightSettings, col1X, currentY, cardW, rightH, mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    private boolean checkSettingsGroupClick(List<Setting> settings, float cardX, float cardY, float cardW, float cardH, float mouseX, float mouseY, int button) {
        if (mouseX < cardX || mouseX > cardX + cardW || mouseY < cardY || mouseY > cardY + cardH) {
            return false;
        }

        float sY = cardY + 34.0f;
        float settingsW = cardW - 24.0f;
        float settingsX = cardX + 12.0f;

        for (Setting s : settings) {
            float progress = s.getVisibilityProgress();
            if (progress <= 0.05f) continue;

            if (s instanceof BooleanSetting bool) {
                float itemH = 26.0f * progress;
                if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f) && mouseX >= settingsX && mouseX <= settingsX + settingsW) {
                    bool.set(!bool.get());
                    egg.client.config.ConfigManager.save();
                    GuiSoundManager.playToggle(bool.get());
                    return true;
                }
                sY += itemH;
            } else if (s instanceof SliderSetting slider) {
                float itemH = 34.0f * progress;
                if (progress > 0.6f && mouseY >= sY + 6.0f && mouseY <= (sY + 30.0f) && mouseX >= settingsX && mouseX <= settingsX + settingsW) {
                    draggingSlider = slider;
                    dragTrackX = settingsX;
                    dragTrackW = settingsW;
                    dragStartClickX = this.lastRawClickX;
                    dragStartScale = currentGuiScale;
                    updateSliderValue(slider, dragTrackX, dragTrackW, mouseX);
                    dragStartValue = slider.get();
                    return true;
                }
                sY += itemH;
            } else if (s instanceof ModeSetting mode) {
                float itemH = 28.0f * progress;
                float btnW = getModeButtonWidth(mode, settingsW);
                float btnX = settingsX + settingsW - btnW;
                if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f) && mouseX >= settingsX && mouseX <= settingsX + settingsW) {
                    if (this.openDropdownSetting == mode) {
                        this.openDropdownSetting = null;
                    } else {
                        openDropdown(mode, btnX, sY + 22.0f, btnW);
                    }
                    return true;
                }
                sY += itemH;
            } else if (s instanceof ColorSetting colorSetting) {
                float itemH = 26.0f * progress;
                float[] storedBadge = this.colorSettingBadgePositions.get(colorSetting);
                float badgeW = storedBadge != null ? storedBadge[2] : getColorBadgeWidth(colorSetting);
                float badgeH = storedBadge != null ? storedBadge[3] : 18.0f;
                float badgeX = storedBadge != null ? storedBadge[0] : (settingsX + settingsW - badgeW);
                float badgeY = storedBadge != null ? storedBadge[1] : (sY + 3.0f);
                if (progress > 0.6f && mouseY >= badgeY - 2.0f && mouseY <= badgeY + badgeH + 2.0f && mouseX >= badgeX && mouseX <= badgeX + badgeW) {
                    if (this.openColorPickerSetting == colorSetting) {
                        this.openColorPickerSetting = null;
                        this.colorDragMode = ColorDragMode.NONE;
                    } else {
                        this.openColorPickerSetting = colorSetting;
                        this.colorDragMode = ColorDragMode.NONE;
                        this.colorPopupAnim = 0.0f;
                        float popW = 144.0f;
                        float popH = 132.0f;
                        float px = badgeX + badgeW - popW;
                        float py = badgeY + badgeH + 4.0f;
                        int screenW = client != null ? client.getWindow().getScaledWidth() : 800;
                        int screenH = client != null ? client.getWindow().getScaledHeight() : 600;
                        if (px + popW > screenW - 8.0f) px = screenW - popW - 8.0f;
                        if (px < 8.0f) px = 8.0f;
                        if (py + popH > screenH - 8.0f) py = badgeY - popH - 4.0f;
                        if (py < 8.0f) py = 8.0f;
                        this.colorPopupX = px;
                        this.colorPopupY = py;
                    }
                    return true;
                }
                sY += itemH;
            } else if (s instanceof BindSetting bind) {
                float itemH = 28.0f * progress;
                if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 24.0f) && mouseX >= settingsX && mouseX <= settingsX + settingsW) {
                    if (button == 1) {
                        bind.setKey(0);
                        listeningBindSetting = null;
                        egg.client.config.ConfigManager.save();
                    } else {
                        listeningBindSetting = (listeningBindSetting == bind) ? null : bind;
                    }
                    GuiSoundManager.playClick();
                    return true;
                }
                sY += itemH;
            }
        }
        return false;
    }

    private void updateSliderValue(SliderSetting slider, float trackX, float trackW, float mouseX) {
        float ratio = (mouseX - trackX) / trackW;
        ratio = Math.clamp(ratio, 0.0f, 1.0f);
        double oldVal = slider.get();
        double val = slider.getMin() + (slider.getMax() - slider.getMin()) * ratio;
        slider.set(val);
        if (Math.abs(slider.get() - oldVal) > 1e-4) {
            GuiSoundManager.playSlider();
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingSlider != null) {
            egg.client.config.ConfigManager.save();
            draggingSlider = null;
        }
        WorldTweaksUI.mouseReleased();
        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.mouseReleased();
        } else if (selectedCategory == Category.COSMETICS) {
            egg.client.ui.section.CosmeticsPanel.mouseReleased();
        }

        searchSelecting = false;
        if (colorDragMode != ColorDragMode.NONE) {
            colorDragMode = ColorDragMode.NONE;
            egg.client.config.ConfigManager.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        float scale = currentGuiScale;
        float cx = getGuiCenterX();
        float cy = getGuiCenterY();
        float mouseX = (float) ((click.x() - cx) / scale + cx);
        float mouseY = (float) ((click.y() - cy) / scale + cy);

        if (searchFocused && searchSelecting) {
            float gWidth = getGuiBaseWidth();
            float gX = (float) Math.round((this.width - gWidth) / 2.0f);
            float sX = gX + 8.0f;
            searchCursor = getSearchCursorIndex(mouseX, sX);
            return true;
        }
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }
        if (draggingSlider != null) {
            if ("Масштаб GUI".equals(draggingSlider.getName())) {
                float physicalDeltaX = (float) click.x() - dragStartClickX;
                float physicalTrackW = dragTrackW * Math.max(0.1f, dragStartScale);
                double targetVal = dragStartValue + (physicalDeltaX / physicalTrackW) * (draggingSlider.getMax() - draggingSlider.getMin());
                double oldVal = draggingSlider.get();
                draggingSlider.set(targetVal);
                if (Math.abs(draggingSlider.get() - oldVal) > 1e-4) {
                    GuiSoundManager.playSlider();
                }
            } else {
                updateSliderValue(draggingSlider, dragTrackX, dragTrackW, mouseX);
            }
            return true;
        }
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && WorldTweaksUI.mouseDragged(wt, mouseX, mouseY)) {
            return true;
        }
        if (openColorPickerSetting != null && colorDragMode != ColorDragMode.NONE) {
            float svX = colorPopupX + 8.0f;
            float svY = colorPopupY + 8.0f;
            float svW = 92.0f;
            float svH = 74.0f;
            float hueX = svX + svW + 6.0f;
            float hueY = svY;
            float hueH = svH;
            float alphaX = hueX + 12.0f + 6.0f;
            float alphaY = svY;
            float alphaH = svH;

            if (colorDragMode == ColorDragMode.AREA_SV) {
                updateColorSV(openColorPickerSetting, svX, svY, svW, svH, mouseX, mouseY);
                return true;
            } else if (colorDragMode == ColorDragMode.HUE) {
                updateColorHueBar(openColorPickerSetting, hueY, hueH, mouseY);
                return true;
            } else if (colorDragMode == ColorDragMode.ALPHA) {
                updateColorAlphaBar(openColorPickerSetting, alphaY, alphaH, mouseY);
                return true;
            }
        }
        if (selectedCategory == Category.COSMETICS) {
            if (egg.client.ui.section.CosmeticsPanel.mouseDragged(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }
        float scale = currentGuiScale;
        float cx = getGuiCenterX();
        float cy = getGuiCenterY();
        mouseX = (mouseX - cx) / scale + cx;
        mouseY = (mouseY - cy) / scale + cy;
        float guiW = getGuiBaseWidth();
        float guiH = getGuiBaseHeight();
        float guiX = (float) Math.round((width - guiW) / 2.0f);
        float guiY = (float) Math.round((height - guiH) / 2.0f);
        float collapseProgress = sidebarCollapseAnim.getValue();
        float sidebarW = MathHelper.lerp(collapseProgress, 132.0f, 36.0f);

        if (mouseX >= guiX && mouseX <= guiX + sidebarW && mouseY >= guiY + 34.0f && mouseY <= guiY + guiH) {
            sidebarTargetScroll -= (float) (verticalAmount * 26.0f);
            sidebarTargetScroll = Math.clamp(sidebarTargetScroll, 0.0f, sidebarMaxScroll);
            return true;
        }

        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.mouseScrolled(verticalAmount);
            return true;
        } else if (selectedCategory == Category.COSMETICS) {
            float contentX = guiX + sidebarW + 14.0f;
            float contentY = guiY + 44.0f + 10.0f;
            float contentW = guiW - sidebarW - 28.0f;
            float contentH = guiH - 44.0f - 16.0f;
            egg.client.ui.section.CosmeticsPanel.mouseScrolled(mouseX, mouseY, verticalAmount, contentX, contentY, contentW, contentH);
            return true;
        }
        if (this.openDropdownSetting != null && this.openDropdownMaxScroll > 0.0f) {
            this.openDropdownScrollY = Math.clamp(this.openDropdownScrollY - (float) verticalAmount * 14.0f, 0.0f, this.openDropdownMaxScroll);
            return true;
        }
        targetScroll -= (float) (verticalAmount * 38.0f);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);
        lastScrollTime = System.currentTimeMillis();
        return true;
    }

    private static boolean isCtrlDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (closing) {
            return true;
        }

        if (!closing && currentAnimProgress < 0.70f) {
            int openKey = ClientSettings.getOpenKey();
            if ((openKey > 0 && key == openKey) || key == GLFW.GLFW_KEY_ESCAPE) {
                closeWithAnimation();
                return true;
            }
            return false;
        }

        if (listeningBindSetting != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) {
                listeningBindSetting.setKey(0);
            } else {
                listeningBindSetting.setKey(key);
            }
            egg.client.config.ConfigManager.save();
            listeningBindSetting = null;
            return true;
        }

        if (listeningKeybindModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) {
                listeningKeybindModule.setKey(0);
            } else {
                listeningKeybindModule.setKey(key);
            }
            lastModuleKeys.remove(listeningKeybindModule);
            egg.client.config.ConfigManager.save();
            listeningKeybindModule = null;
            return true;
        }

        if (this.openDropdownSetting != null && key == GLFW.GLFW_KEY_ESCAPE) {
            this.openDropdownSetting = null;
            return true;
        }

        if (searchFocused) {
            lastBlinkTime = System.currentTimeMillis();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }

            if (key == GLFW.GLFW_KEY_UP) {
                List<Module> matches = getSearchMatches();
                int showCount = Math.min(5, matches.size());
                if (showCount > 0) {
                    searchSelectedIndex = (searchSelectedIndex - 1 + showCount) % showCount;
                    return true;
                }
            }

            if (key == GLFW.GLFW_KEY_DOWN) {
                List<Module> matches = getSearchMatches();
                int showCount = Math.min(5, matches.size());
                if (showCount > 0) {
                    searchSelectedIndex = (searchSelectedIndex + 1) % showCount;
                    return true;
                }
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                List<Module> matches = getSearchMatches();
                int showCount = Math.min(5, matches.size());
                if (showCount > 0 && searchSelectedIndex >= 0 && searchSelectedIndex < showCount) {
                    selectSearchModule(matches.get(searchSelectedIndex));
                } else {
                    searchFocused = false;
                }
                return true;
            }

            if (isCtrlDown() && key == GLFW.GLFW_KEY_A) {
                searchSelectionEnd = 0;
                searchCursor = searchQuery.length();
                return true;
            }

            if (isCtrlDown() && key == GLFW.GLFW_KEY_C) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.keyboard != null) {
                    if (searchCursor != searchSelectionEnd) {
                        int sStart = Math.min(searchCursor, searchSelectionEnd);
                        int sEnd = Math.max(searchCursor, searchSelectionEnd);
                        mc.keyboard.setClipboard(searchQuery.substring(sStart, sEnd));
                    } else if (!searchQuery.isEmpty()) {
                        mc.keyboard.setClipboard(searchQuery);
                    }
                }
                return true;
            }

            if (isCtrlDown() && key == GLFW.GLFW_KEY_X) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.keyboard != null) {
                    if (searchCursor != searchSelectionEnd) {
                        int sStart = Math.min(searchCursor, searchSelectionEnd);
                        int sEnd = Math.max(searchCursor, searchSelectionEnd);
                        mc.keyboard.setClipboard(searchQuery.substring(sStart, sEnd));
                        searchQuery = searchQuery.substring(0, sStart) + searchQuery.substring(sEnd);
                        searchCursor = sStart;
                        searchSelectionEnd = sStart;
                    } else if (!searchQuery.isEmpty()) {
                        mc.keyboard.setClipboard(searchQuery);
                        searchQuery = "";
                        searchCursor = 0;
                        searchSelectionEnd = 0;
                    }
                }
                selectedSearchModule = null;
                searchSelectedIndex = 0;
                markModulesDirty();
                resetCascade();
                return true;
            }

            if (isCtrlDown() && key == GLFW.GLFW_KEY_V) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.keyboard != null) {
                    String clip = mc.keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        clip = clip.replace("\r", "").replace("\n", "");
                        int sStart = Math.min(searchCursor, searchSelectionEnd);
                        int sEnd = Math.max(searchCursor, searchSelectionEnd);
                        String pre = searchQuery.substring(0, sStart);
                        String post = searchQuery.substring(sEnd);
                        String combined = pre + clip + post;
                        if (combined.length() > 32) combined = combined.substring(0, 32);
                        searchQuery = combined;
                        searchCursor = Math.min(pre.length() + clip.length(), 32);
                        searchSelectionEnd = searchCursor;
                        selectedSearchModule = null;
                        searchSelectedIndex = 0;
                        markModulesDirty();
                        resetCascade();
                    }
                }
                return true;
            }

            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                if (searchCursor != searchSelectionEnd) {
                    int sStart = Math.min(searchCursor, searchSelectionEnd);
                    int sEnd = Math.max(searchCursor, searchSelectionEnd);
                    searchQuery = searchQuery.substring(0, sStart) + searchQuery.substring(sEnd);
                    searchCursor = sStart;
                    searchSelectionEnd = sStart;
                } else if (key == GLFW.GLFW_KEY_BACKSPACE && searchCursor > 0) {
                    if (isCtrlDown()) {
                        searchQuery = searchQuery.substring(searchCursor);
                        searchCursor = 0;
                        searchSelectionEnd = 0;
                    } else {
                        searchQuery = searchQuery.substring(0, searchCursor - 1) + searchQuery.substring(searchCursor);
                        searchCursor--;
                        searchSelectionEnd = searchCursor;
                    }
                } else if (key == GLFW.GLFW_KEY_DELETE && searchCursor < searchQuery.length()) {
                    searchQuery = searchQuery.substring(0, searchCursor) + searchQuery.substring(searchCursor + 1);
                    searchSelectionEnd = searchCursor;
                }
                selectedSearchModule = null;
                searchSelectedIndex = 0;
                markModulesDirty();
                resetCascade();
                return true;
            }

            int mods = input.modifiers();
            boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            if (key == GLFW.GLFW_KEY_LEFT) {
                if (!shift && searchCursor != searchSelectionEnd) {
                    searchCursor = Math.min(searchCursor, searchSelectionEnd);
                    searchSelectionEnd = searchCursor;
                } else if (searchCursor > 0) {
                    searchCursor--;
                    if (!shift) searchSelectionEnd = searchCursor;
                } else if (!shift) {
                    searchSelectionEnd = 0;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                if (!shift && searchCursor != searchSelectionEnd) {
                    searchCursor = Math.max(searchCursor, searchSelectionEnd);
                    searchSelectionEnd = searchCursor;
                } else if (searchCursor < searchQuery.length()) {
                    searchCursor++;
                    if (!shift) searchSelectionEnd = searchCursor;
                } else if (!shift) {
                    searchSelectionEnd = searchCursor;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME) {
                searchCursor = 0;
                if (!shift) searchSelectionEnd = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_END) {
                searchCursor = searchQuery.length();
                if (!shift) searchSelectionEnd = searchCursor;
                return true;
            }

            return true;
        } else {
            if (isCtrlDown() && (key == GLFW.GLFW_KEY_F || key == GLFW.GLFW_KEY_K)) {
                searchFocused = true;
                searchCursor = searchQuery.length();
                searchSelectionEnd = 0;
                lastBlinkTime = System.currentTimeMillis();
                return true;
            }
            int openKey = ClientSettings.getOpenKey();
            if ((openKey > 0 && key == openKey) || key == GLFW.GLFW_KEY_ESCAPE) {
                closeWithAnimation();
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }
        if (searchFocused) {
            String ch = input.asString();
            if (ch != null && !ch.isEmpty()) {
                char c = ch.charAt(0);
                if (c >= 32 && c != 127) {
                    int sStart = Math.min(searchCursor, searchSelectionEnd);
                    int sEnd = Math.max(searchCursor, searchSelectionEnd);
                    String pre = searchQuery.substring(0, sStart);
                    String post = searchQuery.substring(sEnd);
                    if (pre.length() + post.length() < 32) {
                        searchQuery = pre + ch + post;
                        searchCursor = pre.length() + 1;
                        searchSelectionEnd = searchCursor;
                        selectedSearchModule = null;
                        searchSelectedIndex = 0;
                        lastBlinkTime = System.currentTimeMillis();
                        markModulesDirty();
                        resetCascade();
                        return true;
                    }
                }
            }
            return true;
        }
        return super.charTyped(input);
    }
}
