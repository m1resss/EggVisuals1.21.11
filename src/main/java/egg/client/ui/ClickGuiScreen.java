package egg.client.ui;

import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.misc.ClientSettings;
import egg.client.module.render.WorldTweaks;
import egg.client.module.render.TargetHUD;

import egg.client.ui.widget.WorldTweaksUI;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.Setting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
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

    // Meizu Liquid Lens & Window animation state
    private long animStartTime = 0L;
    private float animStartProgress = 0.0f;
    private float currentAnimProgress = 0.0f;
    private float lensOriginX = -1.0f;
    private float lensOriginY = -1.0f;
    private boolean closing = false;
    private boolean cascadeStarted = false;

    // Persistent static state across GUI open/close
    private static Category savedCategory = Category.ESP;
    private static Module savedSearchModule = null;
    private static String savedHeaderTitle = "Visual - Visuals";
    private static String savedSearchQuery = "";
    private static float savedScrollOffset = 0.0f;
    private static float savedTargetScroll = 0.0f;

    // Category selection & sliding pill
    private Category selectedCategory = savedCategory;
    private Module selectedSearchModule = savedSearchModule;
    private final Animation categoryPillY = new Animation(0.0f, 260L, Easing.EASE_OUT_EXPO);
    private final Animation categoryPillHeight = new Animation(20.0f, 260L, Easing.EASE_OUT_EXPO);
    private final Map<Category, Animation> categoryHoverAnims = new HashMap<>();

    // Header title transition
    private String headerTitle = savedHeaderTitle;
    private final Animation headerFadeAnim = new Animation(1.0f, 240L, Easing.EASE_OUT_CUBIC);

    // Search bar
    private boolean searchFocused = false;
    private String searchQuery = savedSearchQuery;
    private final Animation searchFocusAnim = new Animation(0.0f, 200L, Easing.EASE_OUT_CUBIC);
    private long lastBlinkTime = 0;
    private int searchCursor = 0;
    private int searchSelectionEnd = 0;
    private boolean searchSelecting = false;

    private int getSearchCursorIndex(float clickX, float searchX) {
        float textX = searchX + 22.0f;
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

    // Scrolling & Scrollbar
    private float scrollOffset = savedScrollOffset;
    private float targetScroll = savedTargetScroll;
    private float maxScroll = 0.0f;
    private final Animation scrollbarAlphaAnim = new Animation(0.0f, 240L, Easing.EASE_OUT_CUBIC);
    private long lastScrollTime = 0;

    // Module animations & drag states
    private final Map<Module, Animation> cardCascadeAnims = new HashMap<>();
    private final Map<Module, Animation> cardHoverAnims = new HashMap<>();
    private final Map<Module, Animation> cardPressAnims = new HashMap<>();
    private final Map<Module, Animation> starScaleAnims = new HashMap<>();
    private final Map<Module, Animation> dotsHoverAnims = new HashMap<>();

    // Static caches to guarantee zero GC allocations in render()
    private final List<Module> cachedFilteredModules = new ArrayList<>();
    private boolean modulesDirty = true;
    private final Map<Module, String> cachedDescriptions = new HashMap<>();
    private final Map<SliderSetting, String> cachedSliderStrings = new HashMap<>();
    private final Map<SliderSetting, Double> lastSliderValues = new HashMap<>();
    private final Map<Module, String> cachedKeybindStrings = new HashMap<>();
    private final Map<Module, Integer> lastModuleKeys = new HashMap<>();

    // Interactive slider dragging & keybind
    private SliderSetting draggingSlider = null;
    private float dragTrackX = 0.0f;
    private float dragTrackW = 100.0f;
    private Module listeningKeybindModule = null;

    // Interactive Modern Color Picker Popup
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

    // Animated slider progress for fluid interpolation
    private final Map<SliderSetting, Float> sliderAnimatedProgress = new HashMap<>();
    private final Map<SliderSetting, Float> sliderDragProgress = new HashMap<>();

    // Animated Mode Setting Dropdown Card
    private ModeSetting openDropdownSetting = null;
    private float openDropdownX = 0.0f;
    private float openDropdownY = 0.0f;
    private float openDropdownW = 0.0f;
    private float openDropdownAnim = 0.0f;
    private float openDropdownScrollY = 0.0f;
    private float openDropdownMaxScroll = 0.0f;

    public ClickGuiScreen() {
        super(Text.of("Egg Visual"));
        for (Category cat : Category.values()) {
            categoryHoverAnims.put(cat, new Animation(0.0f, 160L, Easing.EASE_OUT_CUBIC));
        }
    }

    @Override
    protected void init() {
        super.init();
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

        float guiWidth = Math.clamp(width * 0.58f, 520.0f, 580.0f);
        float guiHeight = Math.clamp(height * 0.76f, 330.0f, 380.0f);
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        if ("Курсор".equals(ClientSettings.getLensCenterMode()) && client != null && client.mouse != null && client.getWindow() != null) {
            double mx = client.mouse.getX() * (double) width / (double) client.getWindow().getWidth();
            double my = client.mouse.getY() * (double) height / (double) client.getWindow().getHeight();
            lensOriginX = (float) mx;
            lensOriginY = (float) my;
        } else {
            lensOriginX = gx + guiWidth / 2.0f;
            lensOriginY = gy + guiHeight / 2.0f;
        }

        // Pre-allocate all module animations once
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
        markModulesDirty();
        if ("Выкл".equals(ClientSettings.getOpenAnimationMode())) {
            resetCascade();
            cascadeStarted = true;
        }

    }


    public void markModulesDirty() {
        this.modulesDirty = true;
    }

    private void resetCascade() {
        List<Module> list = getFilteredModules();
        cardCascadeAnims.clear();
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            long delay = i * 20L;
            Animation anim = new Animation(0.0f, 260L + delay, Easing.EASE_OUT_CUBIC);
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
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
        this.closing = false;
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
            draggingSlider = null;
            openColorPickerSetting = null;
            colorDragMode = ColorDragMode.NONE;
            openDropdownSetting = null;
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
        long durationMs = (long) ((closing ? LensParams.CLOSE_DURATION_MS : LensParams.OPEN_DURATION_MS) / speedMult);
        float elapsedMs = (now - animStartTime) / 1_000_000.0f;
        float linear = Math.clamp(elapsedMs / (float) durationMs, 0.0f, 1.0f);

        if (closing) {
            float ease = LensParams.easeInCubic(linear);
            currentAnimProgress = animStartProgress * (1.0f - ease);
            if (linear >= 1.0f || currentAnimProgress <= 0.005f) {
                currentAnimProgress = 0.0f;
            }
        } else {
            float ease = LensParams.easeOutExpo(linear);
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
            try {
                context.applyBlur();
            } catch (IllegalStateException ignored) {
                // Ignore if already blurred in this frame
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimationProgress();
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

        // Cascade starts at 55-60% progress during opening
        if (!cascadeStarted && p >= 0.55f && !closing) {
            cascadeStarted = true;
            resetCascade();
        }

        // Smooth scroll interpolation
        scrollOffset += (targetScroll - scrollOffset) * 0.26f;

        // Dim background overlay
        int overlayAlpha = (int) (130 * p);
        Render2D.drawRect(context, 0, 0, width, height, ColorUtil.rgba(4, 5, 8, overlayAlpha));

        // Window size & position (strict integer pixel alignment)
        float guiWidth = Math.clamp(width * 0.58f, 520.0f, 580.0f);
        float guiHeight = Math.clamp(height * 0.76f, 330.0f, 380.0f);
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        if ("Простая".equals(animMode)) {
            renderDirectWindow(context, gx, gy, guiWidth, guiHeight, mouseX, mouseY, p, p);
            return;
        }

        // Meizu Liquid Lens Mode ("Lens")
        float k = 1.0f - p; // Distortion strength: 1.0 at start down to 0.0 at end
        float cx = lensOriginX;
        float cy = lensOriginY;
        if (cx < 0.0f || cy < 0.0f) {
            cx = gx + guiWidth / 2.0f;
            cy = gy + guiHeight / 2.0f;
        }

        float maxR = (float) Math.hypot(guiWidth / 2.0f, guiHeight / 2.0f) * 1.05f;
        float R = LensParams.MIN_RADIUS + (maxR - LensParams.MIN_RADIUS) * p;

        float morphToRect = LensParams.smoothstep(0.65f, 0.96f, p);
        float curW = MathHelper.lerp(morphToRect, R * 2.0f, guiWidth);
        float curH = MathHelper.lerp(morphToRect, R * 2.0f, guiHeight);
        float curX = MathHelper.lerp(morphToRect, cx - R, gx);
        float curY = MathHelper.lerp(morphToRect, cy - R, gy);
        float curRadius = MathHelper.lerp(morphToRect, curH / 2.0f, Theme.WINDOW_RADIUS);

        if (p < 0.999f) {
            // Shadow
            boolean shadowEnabled = ClientSettings.isShadowEnabled() && !FpsBenchmark.overrideNoShadow;
            if (shadowEnabled && p > 0.01f) {
                Render2D.drawShadow(context, curX, curY, curW, curH, curRadius, 16.0f, ColorUtil.rgba(0, 0, 0, (int) (150 * p)));
            }

            // Lens container
            int mainBg = ColorUtil.withAlphaFloat(Theme.getWindowBg(), p);
            int mainBorder = ColorUtil.withAlphaFloat(Theme.WINDOW_BORDER, p);
            Render2D.drawRoundedRect(context, curX, curY, curW, curH, curRadius, mainBg);
            Render2D.drawRoundedOutline(context, curX, curY, curW, curH, curRadius, 1.0f, mainBorder);

            // Ripples and Glass Sheen (Phases 1, 2, 3)
            if (k > 0.04f) {
                float timeSec = (float) ((System.nanoTime() - animStartTime) / 1_000_000_000.0);
                float wave = (float) Math.sin(timeSec * LensParams.RIPPLE_SPEED) * 2.5f * k;
                int sheenCol = ColorUtil.rgba(255, 255, 255, (int) (LensParams.GLASS_SHEEN_ALPHA * k * 255.0f));
                Render2D.drawRoundedOutline(context, curX - wave, curY - wave, curW + wave * 2.0f, curH + wave * 2.0f, curRadius + wave, 1.5f, sheenCol);

                float innerWave = (float) Math.cos(timeSec * 7.0f) * 3.0f * k;
                int innerSheen = ColorUtil.withAlphaFloat(Theme.getAccent(), LensParams.GLASS_SHEEN_ALPHA * k * 0.75f);
                Render2D.drawRoundedOutline(context, curX + 2.0f + innerWave, curY + 2.0f + innerWave, curW - 4.0f - innerWave * 2.0f, curH - 4.0f - innerWave * 2.0f, Math.max(1.0f, curRadius - 2.0f), 1.0f, innerSheen);
            }

            // Content reveal
            float contentAlpha = LensParams.smoothstep(0.55f, 0.90f, p) * p;
            if (contentAlpha > 0.01f) {
                context.enableScissor((int) curX, (int) curY, (int) (curX + curW), (int) (curY + curH));
                renderWindowContent(context, gx, gy, guiWidth, guiHeight, mouseX, mouseY, contentAlpha);
                context.disableScissor();
            }
        } else {
            // Finished opening (p == 1.0): 100% direct native render, zero scissor, zero distortion, zero GPU waste!
            renderDirectWindow(context, gx, gy, guiWidth, guiHeight, mouseX, mouseY, 1.0f, 1.0f);
        }


        // Topmost floating dropdown card (in DrawContext layer)
        renderOpenDropdown(context, mouseX, mouseY, 1.0f);

        this.lateMouseX = mouseX;
        this.lateMouseY = mouseY;
    }

    private double lateMouseX;
    private double lateMouseY;

    /**
     * Called at TAIL of GameRenderer.render, after Minecraft's GuiRenderer has finished
     * rendering all DrawContext elements to the framebuffer. This ensures the GPU SDF
     * color picker floating popup is rendered 100% on top of all GUI windows and cards!
     */
    public void renderLate() {
        if (openColorPickerSetting != null || colorPopupAnim > 0.01f) {
            renderOpenColorPicker(null, lateMouseX, lateMouseY, 1.0f);
        }
    }


    private void renderDirectWindow(DrawContext context, float gx, float gy, float guiWidth, float guiHeight, float mouseX, float mouseY, float containerAlpha, float contentAlpha) {
        boolean shadowEnabled = ClientSettings.isShadowEnabled() && !FpsBenchmark.overrideNoShadow;
        if (shadowEnabled && containerAlpha > 0.01f) {
            int shadowColor = ColorUtil.rgba(0, 0, 0, (int) (150 * containerAlpha));
            Render2D.drawShadow(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 16.0f, shadowColor);
        }

        int mainBg = ColorUtil.withAlphaFloat(Theme.getWindowBg(), containerAlpha);
        int mainBorder = ColorUtil.withAlphaFloat(Theme.WINDOW_BORDER, containerAlpha);
        Render2D.drawRoundedRect(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, mainBg);
        Render2D.drawRoundedOutline(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 1.0f, mainBorder);
        Render2D.drawDiagonalSheen(context, gx, gy, guiWidth, guiHeight, Theme.WINDOW_RADIUS, 1200L);

        context.enableScissor((int) gx, (int) gy, (int) (gx + guiWidth), (int) (gy + guiHeight));
        renderWindowContent(context, gx, gy, guiWidth, guiHeight, mouseX, mouseY, contentAlpha);
        context.disableScissor();
    }

    private void renderWindowContent(DrawContext context, float gx, float gy, float guiWidth, float guiHeight, float mouseX, float mouseY, float alpha) {
        float sidebarWidth = 114.0f;

        // Vertical divider between sidebar and content
        int dividerColor = ColorUtil.withAlphaFloat(Theme.SIDEBAR_DIVIDER, alpha);
        Render2D.drawRect(context, gx + sidebarWidth, gy, 1.0f, guiHeight, dividerColor);

        // Sidebar content
        renderSidebar(context, gx, gy, sidebarWidth, guiHeight, mouseX, mouseY, alpha);

        // Header
        float headerHeight = 36.0f;
        renderHeader(context, gx + sidebarWidth, gy, guiWidth - sidebarWidth, headerHeight, mouseX, mouseY, alpha);

        // Content Area
        float contentX = gx + sidebarWidth + 12.0f;
        float contentY = gy + headerHeight + 8.0f;
        float contentW = guiWidth - sidebarWidth - 24.0f;
        float contentH = guiHeight - headerHeight - 14.0f;

        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.render(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        } else {
            renderModules(context, contentX, contentY, contentW, contentH, mouseX, mouseY, alpha);
        }

        // Search matches popover ("плашка совпадений")
        renderSearchMatchesPopup(context, gx + guiWidth - 160.0f - 14.0f, gy + headerHeight + 2.0f, 160.0f, mouseX, mouseY, alpha);
    }

    private void renderSidebar(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        // Logo at top (larger & prominent)
        float logoAreaH = 46.0f;
        renderLogo(context, x, y, width, logoAreaH, alpha);

        // Horizontal divider under logo
        Render2D.drawRect(context, x, y + logoAreaH, width, 1.0f, ColorUtil.withAlphaFloat(Theme.SIDEBAR_DIVIDER, alpha));

        // Categories list
        float itemY = y + logoAreaH + 6.0f;
        float itemPad = 7.0f;
        float itemW = width - (itemPad * 2.0f);
        float itemH = 20.0f;

        String lastSection = "";
        Category[] categories = Category.values();

        // Single sliding pill for active category
        int activePillColor = ColorUtil.withAlphaFloat(Theme.SIDEBAR_ACTIVE_PILL, alpha);
        float pillY = categoryPillY.getValue();
        if (pillY > 0.0f) {
            Render2D.drawRoundedRect(context, x + itemPad, pillY, itemW, categoryPillHeight.getValue(), 5.0f, activePillColor);
            Render2D.drawRoundedOutline(context, x + itemPad, pillY, itemW, categoryPillHeight.getValue(), 5.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)));
        }

        for (Category cat : categories) {
            if (!cat.getSection().equals(lastSection)) {
                lastSection = cat.getSection();
                if (itemY > y + logoAreaH + 8.0f) {
                    itemY += 4.0f;
                }
                int secColor = ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha);
                if (!FpsBenchmark.overrideNoText) {
                    ClientFont.draw(context, lastSection, x + itemPad + 4.0f, itemY, ClientFont.SF_MEDIUM, secColor);
                }
                itemY += 11.0f;
            }

            boolean isSelected = (cat == selectedCategory);
            boolean isHovered = mouseX >= (x + itemPad) && mouseX <= (x + itemPad + itemW) &&
                    mouseY >= itemY && mouseY <= (itemY + itemH);

            Animation hoverAnim = categoryHoverAnims.get(cat);
            if (hoverAnim != null) {
                hoverAnim.setTarget(isHovered && !isSelected ? 1.0f : 0.0f);
            }
            float hoverProgress = (hoverAnim != null) ? hoverAnim.getValue() : 0.0f;

            if (isSelected) {
                categoryPillY.setTarget(itemY);
                categoryPillHeight.setTarget(itemH);
            } else if (hoverProgress > 0.01f) {
                int hoverBg = ColorUtil.withAlphaFloat(Theme.SIDEBAR_HOVER_PILL, hoverProgress * alpha);
                Render2D.drawRoundedRect(context, x + itemPad, itemY, itemW, itemH, 5.0f, hoverBg);
            }

            // Category text and icon color interpolation
            int baseColor = isSelected ? Theme.TEXT_TITLE : Theme.TEXT_MUTED;
            int finalColor = ColorUtil.interpolate(baseColor, Theme.TEXT_TITLE, hoverProgress);
            finalColor = ColorUtil.withAlphaFloat(finalColor, alpha);

            // Texture Icon & Category Name - centered along pill
            float iconSize = 12.0f;
            float iconY = itemY + (itemH - iconSize) / 2.0f;
            float textY = itemY + (itemH - 9.0f) / 2.0f + 0.5f;
            IconRenderer.drawCategoryIcon(context, cat, x + itemPad + 6.0f, iconY, iconSize, finalColor);

            if (!FpsBenchmark.overrideNoText) {
                ClientFont.draw(context, cat.getName(), x + itemPad + 22.0f, textY, ClientFont.SF_REGULAR, finalColor);
            }

            itemY += itemH + 2.0f;
        }
    }

    private void renderLogo(DrawContext context, float x, float y, float width, float height, float alpha) {
        float cx = x + width / 2.0f;
        float cy = y + height / 2.0f;
        float size = 26.0f; // 45% larger prominent egg logo
        int eggColor = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha);
        IconRenderer.drawTextureScaled(context, LOGO_TEXTURE, cx - size / 2.0f, cy - size / 2.0f, size, size, 96, 96, eggColor);
    }

    private void renderHeader(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        // Horizontal divider under header
        Render2D.drawRect(context, x, y + height, width, 1.0f, ColorUtil.withAlphaFloat(Theme.SIDEBAR_DIVIDER, alpha));

        // Breadcrumb Title ("Visual - Esp") with subtle slide + fade animation
        float titleFade = headerFadeAnim.getValue();
        float slideX = (1.0f - titleFade) * 8.0f;
        float headerTextY = y + (height - 9.0f) / 2.0f + 0.5f;
        int titleColor = ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha);

        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, headerTitle, x + 16.0f + slideX, headerTextY, ClientFont.SF_REGULAR, titleColor);
        }

        // Search bar at right
        float searchW = 140.0f;
        float searchH = 22.0f;
        float searchX = x + width - searchW - 14.0f;
        float searchY = y + (height - searchH) / 2.0f;

        searchFocusAnim.setTarget(searchFocused ? 1.0f : 0.0f);
        float focusVal = searchFocusAnim.getValue();

        int searchBg = ColorUtil.rgba(22, 25, 35, (int) (220 * alpha));
        int searchBorder = ColorUtil.interpolate(
                ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)),
                ColorUtil.withAlphaFloat(Theme.getAccent(), alpha),
                focusVal
        );

        Render2D.drawRoundedRect(context, searchX, searchY, searchW, searchH, 5.0f, searchBg);
        Render2D.drawRoundedOutline(context, searchX, searchY, searchW, searchH, 5.0f, 1.0f, searchBorder);

        // Search icon
        int iconColor = ColorUtil.interpolate(
                ColorUtil.rgba(255, 255, 255, (int) (90 * alpha)),
                ColorUtil.withAlphaFloat(Theme.getAccent(), alpha),
                focusVal
        );
        IconRenderer.drawSearchIcon(context, searchX + 7.0f, searchY + (searchH - 11.0f) / 2.0f, 11.0f, iconColor);

        // Search text / placeholder with selection & cursor
        float textX = searchX + 22.0f;
        float searchTextY = searchY + (searchH - 9.0f) / 2.0f + 0.5f;
        if (!FpsBenchmark.overrideNoText) {
            if (searchQuery.isEmpty() && !searchFocused) {
                ClientFont.draw(context, "Search module", textX, searchTextY, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha));
            } else {
                // Draw text selection highlight
                if (searchFocused && searchCursor != searchSelectionEnd) {
                    int selStart = Math.min(searchCursor, searchSelectionEnd);
                    int selEnd = Math.max(searchCursor, searchSelectionEnd);
                    float selX1 = textX + (selStart > 0 ? ClientFont.getWidth(searchQuery.substring(0, selStart), ClientFont.SF_REGULAR) : 0.0f);
                    float selX2 = textX + ClientFont.getWidth(searchQuery.substring(0, selEnd), ClientFont.SF_REGULAR);
                    int selBg = ColorUtil.withAlpha(Theme.getAccent(), (int) (130 * alpha));
                    Render2D.drawRect(context, selX1, searchTextY - 1.5f, selX2 - selX1, 12.0f, selBg);
                }

                ClientFont.draw(context, searchQuery, textX, searchTextY, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

                // Blinking caret
                if (searchFocused && (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500) {
                    int cPos = Math.clamp(searchCursor, 0, searchQuery.length());
                    float cursorOffset = cPos > 0 ? ClientFont.getWidth(searchQuery.substring(0, cPos), ClientFont.SF_REGULAR) : 0.0f;
                    Render2D.drawRect(context, textX + cursorOffset, searchTextY - 1.0f, 1.0f, 11.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
                }
            }
        }
    }

    private static final Map<String, String[]> MODULE_KEYWORDS = new HashMap<>();
    static {
        // Combat
        MODULE_KEYWORDS.put("HitColor", new String[]{"хитколор", "цвет удара", "хит", "вспышка", "урон", "хит колор", "hit color", "hit"});
        MODULE_KEYWORDS.put("SwordAnimations", new String[]{"анимации", "блокхит", "меч", "удар", "свинг", "1.7", "анимация меча", "sword"});
        MODULE_KEYWORDS.put("TapeMouse", new String[]{"тейп", "кликер", "автокликер", "мышь", "зажим", "tape mouse", "clicker"});
        MODULE_KEYWORDS.put("ShiftTAP", new String[]{"шифт", "шифттап", "тап", "сброс кб", "shift tap"});
        MODULE_KEYWORDS.put("AutoSwap", new String[]{"автосвап", "свап", "свапер", "офхенд", "тотем", "сфера", "левая рука", "щит", "auto swap", "autoswap", "swap"});
        MODULE_KEYWORDS.put("NoFriendDamage", new String[]{"друг", "друзья", "без урона", "защита тиммейтов", "тимейт", "no friend damage"});

        // Movement
        MODULE_KEYWORDS.put("Sprint", new String[]{"спринт", "бег", "автобег", "разгон", "sprint", "run"});
        MODULE_KEYWORDS.put("No Jump Delay", new String[]{"прыжок", "джамп", "распрыг", "no jump delay", "jump delay", "nojumpdelay", "bhop", "но джамп делей"});
        MODULE_KEYWORDS.put("Fast Place", new String[]{"фаст плейс", "блоки", "строительство", "fast place", "fastplace"});

        // Visual (ESP)
        MODULE_KEYWORDS.put("Interface", new String[]{"интерфейс", "худ", "ватермарка", "айленд", "остров", "динамик", "музыка", "трек", "бар", "watermark", "island"});
        MODULE_KEYWORDS.put("PotionHUD", new String[]{"зелья", "эффекты", "баффы", "поушн", "зельки", "hud", "potion"});
        MODULE_KEYWORDS.put("FullBright", new String[]{"яркость", "свет", "гамма", "ночное видение", "ночь", "фулбрайт", "full bright"});
        MODULE_KEYWORDS.put("NoRender", new String[]{"норендер", "тыква", "огонь", "тотем", "слепота", "эффекты", "но рендер", "no render"});
        MODULE_KEYWORDS.put("Glow", new String[]{"свечение", "глов", "обводка", "подсветка", "аура", "glow"});
        MODULE_KEYWORDS.put("NameTags", new String[]{"ники", "неймтаги", "имена", "таблички", "хп игроков", "name tags"});
        MODULE_KEYWORDS.put("Crosshair", new String[]{"прицел", "крестик", "точка", "кастом прицел", "crosshair"});
        MODULE_KEYWORDS.put("Hitmarker", new String[]{"хитмаркер", "маркер удара", "звук удара", "колокольчик", "hitmarker"});
        MODULE_KEYWORDS.put("ItemPhysic", new String[]{"физика", "дроп", "вещи", "предметы", "реалистичный дроп", "item physic"});
        MODULE_KEYWORDS.put("TargetESP", new String[]{"таргет", "есп", "вх", "бокс", "души", "кубы", "таргет есп", "target esp", "esp"});
        MODULE_KEYWORDS.put("ViewModel", new String[]{"вьюмодель", "руки", "положение", "оружие", "вид рук", "рука", "view model"});
        MODULE_KEYWORDS.put("AspectRatio", new String[]{"аспект", "соотношение", "растяг", "экран", "4:3", "разрешение", "aspect ratio"});
        MODULE_KEYWORDS.put("BreakBlock", new String[]{"брейк", "ломание", "прогресс", "анимация ломания", "break block"});
        MODULE_KEYWORDS.put("HpCounter", new String[]{"хп", "хпшка", "здоровье", "сердечки", "жизни", "хп каунтер", "hp counter"});
        MODULE_KEYWORDS.put("SmoothEat", new String[]{"еда", "анимация еды", "плавная еда", "кушать", "smooth eat"});
        MODULE_KEYWORDS.put("Trajectories", new String[]{"траектории", "предикшен", "стрелы", "перлы", "зелья", "прицел", "траектория", "арбалет", "prediction", "trajectories"});

        // Player
        MODULE_KEYWORDS.put("AutoRespawn", new String[]{"автореспавн", "респавн", "возрождение", "смерть", "auto respawn"});
        MODULE_KEYWORDS.put("NoChatBackground", new String[]{"чат", "фон чата", "прозрачный чат", "no chat background"});
        MODULE_KEYWORDS.put("FastEXP", new String[]{"опыт", "экспа", "пузырьки", "быстрый опыт", "fast exp"});
        MODULE_KEYWORDS.put("AutoEat", new String[]{"автоеда", "кушать", "авто кушать", "еда", "auto eat"});
        MODULE_KEYWORDS.put("AutoAccept", new String[]{"автопринятие", "тп", "телепорт", "принять", "пати", "auto accept"});
        MODULE_KEYWORDS.put("ItemScroller", new String[]{"скроллер", "быстрое перемещение", "лут", "колесико", "инвентарь", "item scroller"});
        MODULE_KEYWORDS.put("SoundReducer", new String[]{"звуки", "уменьшение звуков", "тишина", "громкость", "sound reducer"});
        MODULE_KEYWORDS.put("ThirdPerson", new String[]{"третье лицо", "вид сзади", "камера", "ф5", "f5", "third person"});
        MODULE_KEYWORDS.put("AutoAuth", new String[]{"автологин", "автоавторизация", "пароль", "логин", "авторегистрация", "auto auth"});

        // Misc
        MODULE_KEYWORDS.put("ClientSettings", new String[]{"настройки", "клиент", "тема", "блюр", "шрифт", "акцент", "цвета", "client settings"});
        MODULE_KEYWORDS.put("StreamerMode", new String[]{"стример", "стрим", "скрыть ник", "ютуб", "защита", "streamer mode"});

        // Cosmetic
        // MODULE_KEYWORDS.put("MeleeSkins", new String[]{"нож", "ножи", "скин", "скины", "косметика", "оружие", "бабочка", "керамбит", "кнайф", "кс", "cs2", "ножик", "ножей", "ножами", "меч", "melee skins"});
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
        List<Module> result = new ArrayList<>();
        if (searchQuery.trim().isEmpty()) return result;
        List<Module> all = EggClient.getInstance().getModuleManager().getModules();
        String q = searchQuery.toLowerCase().trim();
        String qEn = LayoutHelper.toEn(q).toLowerCase().trim();
        String qRu = LayoutHelper.toRu(q).toLowerCase().trim();
        for (Module m : all) {
            if (!m.isBlocked() && matchesModule(m, q, qEn, qRu)) {
                result.add(m);
            }
        }
        return result;
    }

    private void renderSearchMatchesPopup(DrawContext context, float x, float y, float width, float mouseX, float mouseY, float alpha) {
        if (!searchFocused || searchQuery.trim().isEmpty() || selectedSearchModule != null) return;
        List<Module> matches = getSearchMatches();
        int count = matches.size();

        int showCount = Math.min(5, count);
        float itemH = 20.0f;
        float popH = showCount > 0 ? (showCount * itemH + 6.0f) : 24.0f;

        // Soft outer shadow & container
        if (ClientSettings.isShadowEnabled() && !FpsBenchmark.overrideNoShadow) {
            Render2D.drawShadow(context, x, y, width, popH, 6.0f, 8.0f, ColorUtil.rgba(0, 0, 0, (int) (140 * alpha)));
        }
        Render2D.drawRoundedRect(context, x, y, width, popH, 6.0f, ColorUtil.rgba(18, 20, 28, (int) (245 * alpha)));
        Render2D.drawRoundedOutline(context, x, y, width, popH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (20 * alpha)));

        if (count == 0) {
            if (!FpsBenchmark.overrideNoText) {
                ClientFont.draw(context, "Ничего не найдено", x + 8.0f, y + 6.0f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha));
            }
        } else {
            float itemY = y + 3.0f;
            for (int i = 0; i < showCount; i++) {
                Module m = matches.get(i);
                boolean isHov = mouseX >= x + 4.0f && mouseX <= x + width - 4.0f && mouseY >= itemY && mouseY <= itemY + itemH;
                if (isHov) {
                    Render2D.drawRoundedRect(context, x + 4.0f, itemY, width - 8.0f, itemH, 4.0f, ColorUtil.rgba(255, 255, 255, (int) (16 * alpha)));
                }

                if (!FpsBenchmark.overrideNoText) {
                    int nameColor = m.isEnabled() ? Theme.getAccentHover() : (isHov ? Theme.TEXT_TITLE : Theme.TEXT_SUB);
                    ClientFont.draw(context, m.getName(), x + 8.0f, itemY + 5.0f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(nameColor, alpha));

                    String catName = m.getCategory().getName();
                    float catW = ClientFont.getWidth(catName, ClientFont.SF_MEDIUM);
                    ClientFont.draw(context, catName, x + width - catW - 8.0f, itemY + 5.5f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha));
                }

                itemY += itemH;
            }
        }
    }

    private void renderModules(DrawContext context, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
        context.enableScissor((int) (x - 2.0f), (int) y, (int) (x + width + 4.0f), (int) (y + height));

        List<Module> modules = getFilteredModules();
        float currentY = y - scrollOffset;

        float colGap = 8.0f;
        float cardGap = 6.0f;
        float cardW = (width - colGap) / 2.0f;

        float col0Y = currentY;
        float col1Y = currentY;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);

            // Cascade entry animation
            Animation anim = cardCascadeAnims.get(m);
            float cascade = (anim != null) ? anim.getValue() : 1.0f;
            float offsetY = (1.0f - cascade) * 12.0f;
            float alphaM = alpha * cascade;
            float expandM = m.getExpandAnimation().getValue();
            float h = 44.0f + (getSettingsHeight(m) * expandM);

            boolean isLeft = (i % 2 == 0);
            float cardX = isLeft ? x : (x + cardW + colGap);
            float curColY = isLeft ? col0Y : col1Y;
            float cardY = curColY + offsetY;

            // Frustum / Scissor culling: skip rendering cards entirely outside scroll viewport
            if (cardY + h >= y && cardY <= y + height) {
                renderModuleCard(context, m, cardX, cardY, cardW, h, mouseX, mouseY, alphaM, expandM);
            }

            if (isLeft) {
                col0Y += h + cardGap;
            } else {
                col1Y += h + cardGap;
            }
        }

        context.disableScissor();

        // Max scroll limit based on the tallest column
        float maxColY = Math.max(col0Y, col1Y);
        float totalHeight = maxColY - (y - scrollOffset);
        maxScroll = Math.max(0.0f, totalHeight - height);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);

        // Scrollbar fading
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
        boolean isHovered = mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + (expandProgress > 0 ? 44.0f : height));

        Animation hoverAnim = cardHoverAnims.get(module);
        if (hoverAnim != null) {
            hoverAnim.setTarget(isHovered ? 1.0f : 0.0f);
        }
        float hoverVal = (hoverAnim != null) ? hoverAnim.getValue() : 0.0f;

        int cardBg = ColorUtil.interpolate(Theme.getCardBg(), Theme.getCardBgHover(), hoverVal);
        int cardBorder = ColorUtil.interpolate(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, hoverVal);

        Render2D.drawRoundedRect(context, x, y, width, height, Theme.CARD_RADIUS, ColorUtil.withAlphaFloat(cardBg, alpha));
        Render2D.drawRoundedOutline(context, x, y, width, height, Theme.CARD_RADIUS, 1.0f, ColorUtil.withAlphaFloat(cardBorder, alpha));

        // Module title with gradient & description
        float textX = x + 10.0f;
        float titleY = y + 8.0f;

        float switchProgress = module.getSwitchAnimation().getValue();
        int grad1, grad2;
        if (switchProgress > 0.05f) {
            grad1 = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
            grad2 = ColorUtil.interpolate(
                    ColorUtil.rgba(215, 225, 250, (int) (255 * alpha)),
                    Theme.getAccentHover(),
                    switchProgress
            );
        } else {
            int rightC = isHovered ? ColorUtil.rgba(180, 190, 210, (int) (240 * alpha)) : ColorUtil.rgba(148, 158, 175, (int) (210 * alpha));
            grad1 = ColorUtil.rgba(240, 243, 250, (int) (230 * alpha));
            grad2 = rightC;
        }

        if (!FpsBenchmark.overrideNoText) {
            ClientFont.drawGradient(context, module.getName(), textX, titleY, ClientFont.SF_REGULAR, grad1, grad2);

            // Clean cached description text
            float descY = titleY + 14.0f;
            int descColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
            String desc = getTruncatedDescription(module, width - 88.0f);
            ClientFont.draw(context, desc, textX, descY, ClientFont.SF_REGULAR, descColor);
        }

        // Right side elements: Toggle switch, Star (favorite), Dots (settings)
        float toggleW = 26.0f;
        float toggleH = 14.0f;
        float toggleX = x + width - toggleW - 10.0f;
        float toggleY = y + (44.0f - toggleH) / 2.0f;

        renderToggle(context, module, toggleX, toggleY, toggleW, toggleH, alpha);

        // Star icon (Favorite)
        float starX = toggleX - 18.0f;
        float starY = toggleY + 1.0f;
        renderStar(context, module, starX, starY, mouseX, mouseY, alpha);

        // Three dots (Settings expand)
        float dotsX = starX - 16.0f;
        float dotsY = toggleY + 1.0f;
        renderDots(context, module, dotsX, dotsY, mouseX, mouseY, alpha);

        // Expanded settings interior (only rendered if expanded > 0.01)
        if (expandProgress > 0.01f) {
            float settingsY = y + 44.0f;
            float visibleH = height - 44.0f;
            if (visibleH > 1.0f) {
                context.enableScissor((int) x, (int) settingsY, (int) (x + width), (int) (y + height));
                renderSettings(context, module, x + 10.0f, settingsY, width - 20.0f, expandProgress * alpha, mouseX, mouseY);
                context.disableScissor();
            }
        }
    }

    private String getTruncatedDescription(Module module, float maxDescW) {
        String cached = cachedDescriptions.get(module);
        if (cached != null) return cached;

        String raw = module.getDescription();
        TextRenderer tr = client != null ? client.textRenderer : null;
        if (tr != null && ClientFont.getWidth(raw, ClientFont.SF_REGULAR) > maxDescW) {
            cached = tr.trimToWidth(raw, (int) (maxDescW - 8.0f)) + "..";
        } else {
            cached = raw;
        }
        cachedDescriptions.put(module, cached);
        return cached;
    }

    private void renderToggle(DrawContext context, Module module, float x, float y, float width, float height, float alpha) {
        float switchProgress = module.getSwitchAnimation().getValue();

        // Sleek capsule background with subtle border
        int offColor = ColorUtil.rgb(32, 34, 44);
        int onColor = Theme.getAccent();
        int bgColor = ColorUtil.interpolate(offColor, onColor, switchProgress);
        Render2D.drawRoundedRect(context, x, y, width, height, height / 2.0f, ColorUtil.withAlphaFloat(bgColor, alpha));

        int borderColor = ColorUtil.interpolate(
                ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)),
                ColorUtil.rgba(255, 255, 255, (int) (36 * alpha)),
                switchProgress
        );
        Render2D.drawRoundedOutline(context, x, y, width, height, height / 2.0f, 1.0f, borderColor);

        // Smooth sliding white knob
        float knobRadius = height / 2.0f - 2.0f;
        float knobMinX = x + knobRadius + 2.0f;
        float knobMaxX = x + width - knobRadius - 2.0f;
        float knobX = knobMinX + (knobMaxX - knobMinX) * switchProgress;
        float knobY = y + height / 2.0f;

        Render2D.drawCircle(context, knobX, knobY, knobRadius, ColorUtil.withAlphaFloat(Theme.TOGGLE_KNOB, alpha));
    }

    private void renderStar(DrawContext context, Module module, float x, float y, float mouseX, float mouseY, float alpha) {
        boolean isFavorite = module.isFavorite();
        boolean isHovered = mouseX >= x - 2 && mouseX <= x + 14 && mouseY >= y - 2 && mouseY <= y + 16;

        int starColor = isFavorite ? Theme.STAR_ACTIVE : (isHovered ? Theme.TEXT_TITLE : Theme.STAR_INACTIVE);

        // Vector Star Icon
        IconRenderer.drawStarIcon(context, x + 5.0f, y + 8.0f, isFavorite, ColorUtil.withAlphaFloat(starColor, alpha));
    }

    private void renderDots(DrawContext context, Module module, float x, float y, float mouseX, float mouseY, float alpha) {
        boolean isHovered = mouseX >= x - 2 && mouseX <= x + 12 && mouseY >= y - 2 && mouseY <= y + 16;
        int color = module.isExpanded() ? Theme.getAccent() : (isHovered ? Theme.DOTS_ACTIVE : Theme.DOTS_INACTIVE);
        int finalColor = ColorUtil.withAlphaFloat(color, alpha);

        // Vector vertical 3 dots
        IconRenderer.drawDotsIcon(context, x + 5.0f, y + 8.0f, finalColor);
    }

    private float getSettingsHeight(Module module) {
        float h = 10.0f;
        if (module instanceof WorldTweaks) {
            h += WorldTweaksUI.getTotalHeight();
        }
        for (Setting s : module.getSettings()) {
            float progress = s.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            float baseH = 0.0f;
            if (s instanceof BooleanSetting) {
                baseH = 24.0f;
            } else if (s instanceof SliderSetting) {
                baseH = 32.0f;
            } else if (s instanceof ModeSetting) {
                baseH = 26.0f;
            } else if (s instanceof ColorSetting cs) {
                baseH = 24.0f;
            } else if (s instanceof ButtonSetting) {
                baseH = 26.0f;
            }
            h += baseH * progress;
        }
        h += 26.0f; // Keybind row
        return h;
    }

    private void renderSettings(DrawContext context, Module module, float x, float y, float width, float alpha, float mouseX, float mouseY) {
        // Subtle divider before settings
        Render2D.drawRect(context, x, y - 2.0f, width, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (14 * alpha)));

        float currentY = y + 6.0f;
        if (module instanceof WorldTweaks wt) {
            WorldTweaksUI.render(context, wt, x, currentY, width, alpha, mouseX, mouseY);
            currentY += WorldTweaksUI.getTotalHeight();
        }
        for (Setting setting : module.getSettings()) {
            float progress = setting.getVisibilityProgress();
            if (progress <= 0.001f) continue;
            float setAlpha = alpha * progress;
            float setH = 0.0f;

            if (setting instanceof BooleanSetting bool) {
                setH = 24.0f * progress;
                if (progress < 0.99f) {
                    context.enableScissor((int) x, (int) currentY, (int) (x + width), (int) (currentY + setH));
                }
                renderBooleanSetting(context, bool, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    context.disableScissor();
                }
            } else if (setting instanceof SliderSetting slider) {
                setH = 32.0f * progress;
                if (progress < 0.99f) {
                    context.enableScissor((int) x, (int) currentY, (int) (x + width), (int) (currentY + setH));
                }
                renderSliderSetting(context, slider, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    context.disableScissor();
                }
            } else if (setting instanceof ModeSetting mode) {
                setH = 26.0f * progress;
                if (progress < 0.99f) {
                    context.enableScissor((int) x, (int) currentY, (int) (x + width), (int) (currentY + setH));
                }
                renderModeSetting(context, mode, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    context.disableScissor();
                }
            } else if (setting instanceof ColorSetting colorSetting) {
                setH = 24.0f * progress;
                if (progress < 0.99f) {
                    context.enableScissor((int) x, (int) currentY, (int) (x + width), (int) (currentY + setH));
                }
                renderColorSetting(context, colorSetting, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    context.disableScissor();
                }
            } else if (setting instanceof ButtonSetting btn) {
                setH = 26.0f * progress;
                if (progress < 0.99f) {
                    context.enableScissor((int) x, (int) currentY, (int) (x + width), (int) (currentY + setH));
                }
                renderButtonSetting(context, btn, x, currentY, width, setAlpha);
                if (progress < 0.99f) {
                    context.disableScissor();
                }
            }
            currentY += setH;
        }

        // Keybind setting
        renderKeybindRow(context, module, x, currentY, width, alpha);
    }


    private void renderButtonSetting(DrawContext context, ButtonSetting btn, float x, float y, float width, float alpha) {
        float btnW = width - 12.0f;
        float btnH = 20.0f;
        float btnX = x + 6.0f;
        float btnY = y + 2.0f;

        boolean hovered = lateMouseX >= btnX && lateMouseX <= btnX + btnW && lateMouseY >= btnY && lateMouseY <= btnY + btnH;
        int bg = hovered ? Theme.getAccentHover() : Theme.getAccent();
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 5.0f, ColorUtil.withAlphaFloat(bg, alpha * 0.90f));
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 5.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (35 * alpha)));

        ClientFont.drawCentered(context, btn.getName(), btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
    }

    private void renderBooleanSetting(DrawContext context, BooleanSetting bool, float x, float y, float width, float alpha) {
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, bool.getName(), x + 6.0f, y + 3.0f, ClientFont.SF_REGULAR, labelColor);
        }

        // Modern mini switch
        float boxW = 24.0f;
        float boxH = 13.0f;
        float boxX = x + width - boxW - 6.0f;
        float boxY = y + 2.0f;

        int boxBg = bool.get() ? Theme.getAccent() : Theme.TOGGLE_OFF;
        Render2D.drawRoundedRect(context, boxX, boxY, boxW, boxH, boxH / 2.0f, ColorUtil.withAlphaFloat(boxBg, alpha));
        Render2D.drawRoundedOutline(context, boxX, boxY, boxW, boxH, boxH / 2.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)));

        float knobR = boxH / 2.0f - 2.0f;
        float knobX = bool.get() ? (boxX + boxW - knobR - 2.0f) : (boxX + knobR + 2.0f);
        Render2D.drawCircle(context, knobX, boxY + boxH / 2.0f, knobR, ColorUtil.withAlphaFloat(Theme.TOGGLE_KNOB, alpha));
    }

    private void renderModeSetting(DrawContext context, ModeSetting mode, float x, float y, float width, float alpha) {
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, mode.getName(), x + 6.0f, y + 3.0f, ClientFont.SF_REGULAR, labelColor);
        }

        String val = mode.get();
        float btnW = ClientFont.getWidth(val, ClientFont.SF_REGULAR) + 18.0f;
        float btnH = 16.0f;
        float btnX = x + width - btnW - 6.0f;
        float btnY = y + 1.0f;

        boolean isOpened = (this.openDropdownSetting == mode);
        int btnBg = isOpened ? ColorUtil.rgba(35, 42, 60, (int) (240 * alpha)) : ColorUtil.rgba(26, 30, 42, (int) (220 * alpha));
        int btnBorder = isOpened ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (28 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 4.0f, btnBg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 4.0f, 1.0f, isOpened ? Theme.getAccent() : btnBorder);

        if (!FpsBenchmark.overrideNoText) {
            ClientFont.drawCentered(context, val, btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(isOpened ? Theme.getAccent() : Theme.TEXT_TITLE, alpha));
        }
    }

    private void renderColorSetting(DrawContext context, ColorSetting colorSetting, float x, float y, float width, float alpha) {
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, colorSetting.getName(), x + 6.0f, y + 4.0f, ClientFont.SF_REGULAR, labelColor);
        }

        int col = colorSetting.get();
        String hex = colorSetting.getHex();
        float badgeW = 76.0f;
        float badgeH = 17.0f;
        float badgeX = x + width - badgeW - 6.0f;
        float badgeY = y + 1.5f;

        boolean isOpened = (this.openColorPickerSetting == colorSetting);

        // Store badge position for opening the popup
        this.colorSettingBadgePositions.put(colorSetting, new float[]{badgeX, badgeY, badgeW, badgeH});

        int badgeBg = isOpened ? 0xF0222738 : 0xC8171B26;
        int badgeBorder = isOpened ? Theme.getAccent() : 0x24FFFFFF;

        Render2D.drawRoundedRect(context, badgeX, badgeY, badgeW, badgeH, 4.0f, ColorUtil.withAlphaFloat(badgeBg, alpha));
        Render2D.drawRoundedOutline(context, badgeX, badgeY, badgeW, badgeH, 4.0f, 1.0f, ColorUtil.withAlphaFloat(badgeBorder, alpha));

        // Swatch
        float swatchS = 11.0f;
        float swatchX = badgeX + 4.0f;
        float swatchY = badgeY + (badgeH - swatchS) / 2.0f;
        Render2D.drawRoundedRect(context, swatchX, swatchY, swatchS, swatchS, 2.5f, ColorUtil.withAlphaFloat(col, alpha));
        Render2D.drawRoundedOutline(context, swatchX, swatchY, swatchS, swatchS, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (50 * alpha)));

        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, hex, swatchX + swatchS + 5.0f, badgeY + 4.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
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

        // Dynamically track badge position if available
        float[] storedBadge = this.colorSettingBadgePositions.get(openColorPickerSetting);
        if (storedBadge != null) {
            float badgeX = storedBadge[0];
            float badgeY = storedBadge[1];
            float badgeW = storedBadge[2];
            float badgeH = storedBadge[3];

            float guiH = 280.0f;
            float gy = (float) Math.round((height - guiH) / 2.0f);
            float contentY = gy + 36.0f + 8.0f;
            float contentH = guiH - 36.0f - 14.0f;
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

        // Container background, glow shadow & outline
        Draw2DProcessor.INSTANCE.drawGlow(null, popX, popY, popW, popH, 6.0f, ColorUtil.rgba(0, 0, 0, (int) (180 * pAnim)), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, popX, popY, popW, popH, 6.0f, ColorUtil.rgba(16, 17, 24, (int) (250 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(null, popX, popY, popW, popH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (30 * pAnim)));

        ColorSetting cs = openColorPickerSetting;
        float[] hsb = cs.getHSB();
        float hue = hsb[0];
        float sat = hsb[1];
        float bri = hsb[2];
        float curAlpha = cs.getAlpha();
        int curCol = cs.get();
        int pureHueRgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;

        // 1. SV 2D Area
        float svX = popX + 8.0f;
        float svY = popY + 8.0f;
        float svW = 92.0f;
        float svH = 74.0f;

        // Bilinear gradient: Top-Left=White, Top-Right=PureHue, Bottom-Left=Black, Bottom-Right=Black
        Draw2DProcessor.INSTANCE.drawGradient(null, svX, svY, svW, svH, 3.0f,
                ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)),
                ColorUtil.withAlphaFloat((pureHueRgb | 0xFF000000), pAnim),
                ColorUtil.rgba(0, 0, 0, (int) (255 * pAnim)),
                ColorUtil.rgba(0, 0, 0, (int) (255 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(null, svX, svY, svW, svH, 3.0f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        // SV Reticle handle
        float curSX = svX + sat * svW;
        float curSY = svY + (1.0f - bri) * svH;
        Draw2DProcessor.INSTANCE.drawGlow(null, curSX - 3.5f, curSY - 3.5f, 7.0f, 7.0f, 3.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 3.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, curSX - 3.0f, curSY - 3.0f, 6.0f, 6.0f, 3.0f, ColorUtil.withAlphaFloat(curCol | 0xFF000000, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(null, curSX - 3.5f, curSY - 3.5f, 7.0f, 7.0f, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        // 2. Hue Rainbow Bar (Vertical)
        float hueX = svX + svW + 6.0f;
        float hueY = svY;
        float hueW = 12.0f;
        float hueH = svH;

        Draw2DProcessor.INSTANCE.a(null, Identifier.of("delta", "pictures/color.png"), hueX, hueY, hueW, hueH, 2.5f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
        Draw2DProcessor.INSTANCE.drawOutline(null, hueX, hueY, hueW, hueH, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        // Hue indicator thumb
        float hThumbY = Math.clamp(hueY + hue * (hueH - 2.5f), hueY, hueY + hueH - 2.5f);
        Draw2DProcessor.INSTANCE.drawGlow(null, hueX - 1.0f, hThumbY - 0.5f, hueW + 2.0f, 3.5f, 1.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 2.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, hueX - 1.0f, hThumbY, hueW + 2.0f, 2.5f, 1.25f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        // 3. Alpha Opacity Bar (Vertical)
        float alphaX = hueX + hueW + 6.0f;
        float alphaY = svY;
        float alphaW = 12.0f;
        float alphaH = svH;

        Draw2DProcessor.INSTANCE.a(null, Identifier.of("delta", "pictures/opacity.png"), alphaX, alphaY, alphaW, alphaH, 2.5f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
        int topRgb = (curCol & 0xFFFFFF) | 0xFF000000;
        int botRgb = (curCol & 0xFFFFFF) | 0x00000000;
        Draw2DProcessor.INSTANCE.drawGradient(null, alphaX, alphaY, alphaW, alphaH, 2.5f,
                ColorUtil.withAlphaFloat(topRgb, pAnim),
                ColorUtil.withAlphaFloat(topRgb, pAnim),
                ColorUtil.withAlphaFloat(botRgb, pAnim),
                ColorUtil.withAlphaFloat(botRgb, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(null, alphaX, alphaY, alphaW, alphaH, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (35 * pAnim)));

        // Alpha indicator thumb
        float aThumbY = Math.clamp(alphaY + (1.0f - curAlpha) * (alphaH - 2.5f), alphaY, alphaY + alphaH - 2.5f);
        Draw2DProcessor.INSTANCE.drawGlow(null, alphaX - 1.0f, aThumbY - 0.5f, alphaW + 2.0f, 3.5f, 1.5f, ColorUtil.rgba(0, 0, 0, (int) (120 * pAnim)), 2.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, alphaX - 1.0f, aThumbY, alphaW + 2.0f, 2.5f, 1.25f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));

        // 4. Hex & Preview Row
        float hexY = svY + svH + 7.0f;
        float swS = 13.0f;
        Draw2DProcessor.INSTANCE.drawRect(null, svX, hexY, swS, swS, 2.5f, ColorUtil.withAlphaFloat(curCol, pAnim));
        Draw2DProcessor.INSTANCE.drawOutline(null, svX, hexY, swS, swS, 2.5f, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (45 * pAnim)));

        Fonts.d.a(null, cs.getHex(), svX + swS + 6.0f, hexY + 1.5f, 11.5f, ColorUtil.rgba(230, 235, 245, (int) (255 * pAnim)));

        // Reset button
        float resetX = popX + popW - 18.0f;
        Fonts.d.a(null, "↺", resetX, hexY + 1.0f, 11.5f, ColorUtil.rgba(160, 165, 180, (int) (200 * pAnim)));

        // 5. Preset Palette Dots (Row of 8)
        float presetY = hexY + 17.0f;
        int[] presets = new int[] {
                0xFF8B5CF6, 0xFF00CEC9, 0xFF10B981, 0xFFF59E0B,
                0xFFF43F5E, 0xFF3B82F6, 0xFFFFFFFF, 0xFF181A24
        };
        float spacing = (popW - 16.0f) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            float px = popX + 8.0f + i * spacing + spacing / 2.0f;
            float py = presetY + 4.5f;
            Draw2DProcessor.INSTANCE.drawRect(null, px - 4.5f, py - 4.5f, 9.0f, 9.0f, 4.5f, ColorUtil.withAlphaFloat(presets[i], pAnim));
            if ((presets[i] & 0xFFFFFF) == (curCol & 0xFFFFFF)) {
                Draw2DProcessor.INSTANCE.drawOutline(null, px - 5.5f, py - 5.5f, 11.0f, 11.0f, 5.5f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (255 * pAnim)));
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
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, slider.getName(), x + 6.0f, y + 1.0f, ClientFont.SF_REGULAR, labelColor);
        }

        // Minimalist modern slider track matching reference design:
        // Left (active) part: accent line ~2.5px height.
        // Right (inactive) part: grey line ~1.5px height.
        // Thumb: vertical accent bar ~2.5px width and ~12px height centered on the line.
        float trackCenterY = y + 19.0f;
        float trackW = width - 12.0f;
        float trackX = x + 6.0f;

        // Ultra smooth fluid animation progress for slider
        final float targetProgress = Math.clamp((float) ((slider.get() - slider.getMin()) / (slider.getMax() - slider.getMin())), 0.0f, 1.0f);
        float curProgress = this.sliderAnimatedProgress.computeIfAbsent(slider, k -> targetProgress);
        curProgress = curProgress + (targetProgress - curProgress) * 0.28f;
        if (Math.abs(curProgress - targetProgress) < 0.0005f) {
            curProgress = targetProgress;
        }
        this.sliderAnimatedProgress.put(slider, curProgress);

        float fillW = trackW * curProgress;

        // 1. Inactive right track: subtle thin grey line
        float unfillX = trackX + fillW;
        float unfillW = trackW - fillW;
        float unfillH = 1.5f;
        int unfillColor = ColorUtil.rgba(115, 120, 130, (int) (190 * alpha));
        if (unfillW > 0.0f) {
            Render2D.drawRect(context, unfillX, trackCenterY - unfillH / 2.0f, unfillW, unfillH, unfillColor);
        }

        // 2. Active left track: colored accent line
        float fillH = 2.5f;
        int accentColor = ColorUtil.withAlphaFloat(Theme.getAccent(), alpha);
        if (fillW > 0.0f) {
            Render2D.drawRect(context, trackX, trackCenterY - fillH / 2.0f, fillW, fillH, accentColor);
        }

        // 3. Vertical bar thumb cursor ("бугорок")
        float thumbW = 2.5f;
        float thumbH = 12.0f;
        float thumbX = trackX + fillW - thumbW / 2.0f;
        float thumbY = trackCenterY - thumbH / 2.0f;
        Render2D.drawRoundedRect(context, thumbX, thumbY, thumbW, thumbH, 1.0f, accentColor);

        // Value Badge Animation:
        // When dragging, smoothly moves from top-right to floating directly above the thumb ("бугорок")!
        if (!FpsBenchmark.overrideNoText) {
            String valStr = getSliderValueString(slider);
            float valW = ClientFont.getWidth(valStr, ClientFont.SF_REGULAR);
            float badgeW = valW + 10.0f;
            float badgeH = 13.0f;

            // Default position: top right
            float defBadgeX = x + width - badgeW - 6.0f;
            float defBadgeY = y;

            // Target position when dragging: floating directly above thumb cursor
            float dragTargetX = Math.clamp(thumbX + thumbW / 2.0f - badgeW / 2.0f, trackX, trackX + trackW - badgeW);
            float dragTargetY = trackCenterY - thumbH / 2.0f - badgeH - 3.5f;

            float targetDrag = (this.draggingSlider == slider) ? 1.0f : 0.0f;
            float curDrag = this.sliderDragProgress.computeIfAbsent(slider, k -> 0.0f);
            curDrag = curDrag + (targetDrag - curDrag) * 0.22f;
            if (Math.abs(curDrag - targetDrag) < 0.001f) {
                curDrag = targetDrag;
            }
            this.sliderDragProgress.put(slider, curDrag);

            float renderBadgeX = net.minecraft.util.math.MathHelper.lerp(curDrag, defBadgeX, dragTargetX);
            float renderBadgeY = net.minecraft.util.math.MathHelper.lerp(curDrag, defBadgeY, dragTargetY);

            // Interpolate styles: when floating over thumb, subtle glow & accent border
            if (curDrag > 0.02f) {
                Render2D.drawShadow(context, renderBadgeX, renderBadgeY, badgeW, badgeH, 3.5f, 6.0f, ColorUtil.rgba(0, 0, 0, (int) (140 * curDrag * alpha)));
            }
            int badgeBg = ColorUtil.lerp(
                    ColorUtil.rgba(22, 26, 36, (int) (220 * alpha)),
                    ColorUtil.rgba(14, 16, 24, (int) (250 * alpha)),
                    curDrag
            );
            int badgeBorder = ColorUtil.lerp(
                    ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)),
                    ColorUtil.withAlphaFloat(Theme.getAccent(), alpha),
                    curDrag
            );

            Render2D.drawRoundedRect(context, renderBadgeX, renderBadgeY, badgeW, badgeH, 3.0f, badgeBg);
            Render2D.drawRoundedOutline(context, renderBadgeX, renderBadgeY, badgeW, badgeH, 3.0f, 1.0f, badgeBorder);
            ClientFont.drawCentered(context, valStr, renderBadgeX + badgeW / 2.0f, renderBadgeY + badgeH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        }
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
        float dItemH = 17.0f;
        int maxVisible = 6;
        int displayItems = Math.min(dModes.size(), maxVisible);
        float dListH = displayItems * dItemH;
        float dPopH = dListH + 6.0f;

        // Compute max width needed by options
        float maxModeW = 0.0f;
        for (String m : dModes) {
            maxModeW = Math.max(maxModeW, ClientFont.getWidth(m, ClientFont.SF_REGULAR));
        }
        float dPopW = Math.max(this.openDropdownW, maxModeW + 32.0f);
        float dPopX = this.openDropdownX;
        float dPopY = this.openDropdownY;

        float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (dPopX + dPopW > screenW - 8.0f) {
            dPopX = screenW - dPopW - 8.0f;
        }
        if (dPopY + dPopH > screenH - 8.0f) {
            dPopY = Math.max(8.0f, dPopY - dPopH - 22.0f);
        }

        this.openDropdownMaxScroll = Math.max(0.0f, dModes.size() * dItemH - dListH);
        this.openDropdownScrollY = Math.clamp(this.openDropdownScrollY, 0.0f, this.openDropdownMaxScroll);

        // Slide down easing animation offset
        float dropOffset = (1.0f - this.openDropdownAnim) * 5.0f;
        float renderPopY = dPopY + dropOffset;

        // Drop shadow & container card
        Render2D.drawShadow(context, dPopX, renderPopY, dPopW, dPopH, 5.0f, 10.0f, ColorUtil.rgba(0, 0, 0, (int) (140 * dAnim)));
        Render2D.drawRoundedRect(context, dPopX, renderPopY, dPopW, dPopH, 5.0f, ColorUtil.rgba(18, 22, 32, (int) (245 * dAnim)));
        Render2D.drawRoundedOutline(context, dPopX, renderPopY, dPopW, dPopH, 5.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (28 * dAnim)));

        context.enableScissor((int) dPopX, (int) (renderPopY + 3.0f), (int) (dPopX + dPopW), (int) (renderPopY + 3.0f + dListH));

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
                ClientFont.draw(context, mName, dPopX + 8.0f, curItemY + 4.0f, ClientFont.SF_REGULAR, txtColor);

                if (isCur) {
                    float dotR = 2.5f;
                    Render2D.drawCircle(context, dPopX + dPopW - 9.0f, curItemY + dItemH / 2.0f, dotR, ColorUtil.withAlphaFloat(Theme.getAccent(), dAnim));
                }
            }
            curItemY += dItemH;
        }

        context.disableScissor();
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
        int labelColor = ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha);
        if (!FpsBenchmark.overrideNoText) {
            ClientFont.draw(context, "Bind key", x + 6.0f, y + 4.0f, ClientFont.SF_REGULAR, labelColor);
        }

        boolean isListening = (listeningKeybindModule == module);
        String keyText = getKeybindString(module);

        float btnW = ClientFont.getWidth(keyText, ClientFont.SF_REGULAR) + 16.0f;
        float btnH = 18.0f;
        float btnX = x + width - btnW - 6.0f;
        float btnY = y + 1.0f;

        int btnBg = isListening ? Theme.getAccentMuted() : ColorUtil.rgba(255, 255, 255, (int) (16 * alpha));
        int btnBorder = isListening ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, (int) (26 * alpha));
        Render2D.drawRoundedRect(context, btnX, btnY, btnW, btnH, 5.0f, btnBg);
        Render2D.drawRoundedOutline(context, btnX, btnY, btnW, btnH, 5.0f, 1.0f, btnBorder);

        if (!FpsBenchmark.overrideNoText) {
            int textColor = isListening ? Theme.getAccentHover() : Theme.TEXT_TITLE;
            ClientFont.drawCentered(context, keyText, btnX + btnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(textColor, alpha));
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

    // Input handlers
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }

        float mouseX = (float) click.x();
        float mouseY = (float) click.y();
        int button = click.button();

        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null && targetHUD.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Mouse keybind capture when listening
        if (listeningKeybindModule != null) {
            listeningKeybindModule.setKey(1000 + button);
            egg.client.config.ConfigManager.save();
            lastModuleKeys.remove(listeningKeybindModule);
            listeningKeybindModule = null;
            return true;
        }

        // 0. Check open color picker popup first
        if (this.openColorPickerSetting != null) {
            float popW = 144.0f;
            float popH = 132.0f;
            float popX = this.colorPopupX;
            float popY = this.colorPopupY;

            // Check if clicking the badge that opened this picker to toggle/close it
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
                    // Reset button
                    if (mouseX >= popX + popW - 24.0f && mouseX <= popX + popW - 4.0f && mouseY >= hexY - 2.0f && mouseY <= hexY + 16.0f) {
                        this.openColorPickerSetting.reset();
                        egg.client.config.ConfigManager.save();
                        return true;
                    }
                    // Copy hex to clipboard
                    if (mouseX >= svX && mouseX <= svX + 70.0f && mouseY >= hexY - 2.0f && mouseY <= hexY + 16.0f) {
                        if (client != null && client.keyboard != null) {
                            client.keyboard.setClipboard(this.openColorPickerSetting.getHex());
                        }
                        return true;
                    }
                    // Presets
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

        // 1. Check open dropdown popup card first
        if (this.openDropdownSetting != null) {
            float dPopX = this.openDropdownX;
            float dPopY = this.openDropdownY;
            List<String> dModes = this.openDropdownSetting.getModes();
            float dItemH = 17.0f;
            int maxVisible = 6;
            int displayItems = Math.min(dModes.size(), maxVisible);
            float dListH = displayItems * dItemH;
            float dPopH = dListH + 6.0f;

            float maxModeW = 0.0f;
            for (String m : dModes) {
                maxModeW = Math.max(maxModeW, ClientFont.getWidth(m, ClientFont.SF_REGULAR));
            }
            float dPopW = Math.max(this.openDropdownW, maxModeW + 32.0f);

            float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            float screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
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

        float guiWidth = Math.clamp(width * 0.58f, 520.0f, 580.0f);
        float guiHeight = Math.clamp(height * 0.76f, 330.0f, 380.0f);
        float gx = (float) Math.round((width - guiWidth) / 2.0f);
        float gy = (float) Math.round((height - guiHeight) / 2.0f);

        float sidebarWidth = 114.0f;
        float headerHeight = 36.0f;

        // Search matches popup click
        if (searchFocused && !searchQuery.trim().isEmpty() && selectedSearchModule == null) {
            float popW = 160.0f;
            float popX = gx + guiWidth - popW - 14.0f;
            float popY = gy + headerHeight + 2.0f;
            List<Module> matches = getSearchMatches();
            int count = matches.size();
            int showCount = Math.min(5, count);
            float itemH = 20.0f;
            float popH = showCount > 0 ? (showCount * itemH + 6.0f) : 24.0f;

            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + popH) {
                if (showCount > 0) {
                    int clickedIdx = (int) ((mouseY - (popY + 3.0f)) / itemH);
                    if (clickedIdx >= 0 && clickedIdx < showCount) {
                        Module clickedMod = matches.get(clickedIdx);
                        selectedCategory = clickedMod.getCategory();
                        headerTitle = selectedCategory.getSection() + " - " + selectedCategory.getName();
                        selectedSearchModule = clickedMod;
                        searchQuery = clickedMod.getName();
                        searchFocused = false;
                        clickedMod.setExpanded(true);
                        markModulesDirty();
                        resetCascade();
                        return true;
                    }
                }
                return true;
            }
        }

        // Search bar click
        float searchW = 140.0f;
        float searchH = 22.0f;
        float searchX = gx + guiWidth - searchW - 14.0f;
        float searchY = gy + (headerHeight - searchH) / 2.0f;
        if (mouseX >= searchX && mouseX <= (searchX + searchW) && mouseY >= searchY && mouseY <= (searchY + searchH)) {
            searchFocused = true;
            searchSelecting = true;
            lastBlinkTime = System.currentTimeMillis();
            if (selectedSearchModule != null) {
                selectedSearchModule = null;
                searchQuery = "";
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

        // Sidebar categories click
        float itemPad = 7.0f;
        float itemW = sidebarWidth - (itemPad * 2.0f);
        float itemH = 20.0f;
        float itemY = gy + 46.0f + 6.0f;

        String lastSec = "";
        for (Category cat : Category.values()) {
            if (!cat.getSection().equals(lastSec)) {
                lastSec = cat.getSection();
                if (itemY > gy + 46.0f + 8.0f) {
                    itemY += 4.0f;
                }
                itemY += 11.0f;
            }

            if (mouseX >= (gx + itemPad) && mouseX <= (gx + itemPad + itemW) &&
                    mouseY >= itemY && mouseY <= (itemY + itemH)) {
                if (selectedCategory != cat || selectedSearchModule != null) {
                    selectedCategory = cat;
                    selectedSearchModule = null;
                    searchQuery = "";
                    searchFocused = false;
                    headerTitle = cat.getSection() + " - " + cat.getName();
                    headerFadeAnim.setValue(0.0f);
                    headerFadeAnim.setTarget(1.0f);
                    scrollOffset = 0.0f;
                    targetScroll = 0.0f;
                    markModulesDirty();
                    resetCascade();
                }
                return true;
            }
            itemY += itemH + 2.0f;
        }

        // Modules click (2 columns)
        float contentX = gx + sidebarWidth + 12.0f;
        float contentY = gy + headerHeight + 8.0f;
        float contentW = guiWidth - sidebarWidth - 24.0f;
        float contentH = guiHeight - headerHeight - 14.0f;

        if (selectedCategory == Category.INVENTORY) {
            if (egg.client.ui.section.InventoryConfigPanel.mouseClicked(contentX, contentY, contentW, contentH, mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (mouseX >= contentX && mouseX <= (contentX + contentW) && mouseY >= contentY && mouseY <= (contentY + contentH)) {
            float colGap = 8.0f;
            float cardGap = 6.0f;
            float cardW = (contentW - colGap) / 2.0f;
            List<Module> modules = getFilteredModules();
            float col0Y = contentY - scrollOffset;
            float col1Y = contentY - scrollOffset;

            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                float h = 44.0f + (getSettingsHeight(m) * m.getExpandAnimation().getValue());
                boolean isLeft = (i % 2 == 0);
                float cardX = isLeft ? contentX : (contentX + cardW + colGap);
                float curColY = isLeft ? col0Y : col1Y;

                if (checkModuleClick(m, cardX, curColY, cardW, h, mouseX, mouseY, button)) {
                    return true;
                }

                if (isLeft) {
                    col0Y += h + cardGap;
                } else {
                    col1Y += h + cardGap;
                }
            }
        }

        // Nothing in the GUI claimed the click — hand it to the HUD editor

        return super.mouseClicked(click, bl);
    }

    private boolean checkModuleClick(Module module, float cardX, float cardY, float cardW, float cardH, float mouseX, float mouseY, int button) {
        if (mouseX < cardX || mouseX > cardX + cardW || mouseY < cardY || mouseY > cardY + cardH) {
            return false;
        }

        float toggleW = 26.0f;
        float toggleH = 14.0f;
        float toggleX = cardX + cardW - toggleW - 10.0f;
        float toggleY = cardY + (44.0f - toggleH) / 2.0f;

        // Toggle click
        if (mouseX >= toggleX && mouseX <= (toggleX + toggleW) && mouseY >= toggleY && mouseY <= (toggleY + toggleH)) {
            module.toggle();
            egg.client.config.ConfigManager.save();
            return true;
        }

        // Star click
        float starX = toggleX - 18.0f;
        if (mouseX >= starX - 4 && mouseX <= starX + 14 && mouseY >= toggleY - 2 && mouseY <= toggleY + 16) {
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

        // Three dots click or Right click to expand
        float dotsX = starX - 16.0f;
        if (button == 1 || (mouseX >= dotsX - 4 && mouseX <= dotsX + 14 && mouseY >= toggleY - 2 && mouseY <= toggleY + 16)) {
            module.toggleExpanded();
            return true;
        }

        // Interactive settings clicks
        float expandProgress = module.getExpandAnimation().getValue();
        if (expandProgress > 0.8f && mouseY > (cardY + 44.0f)) {
            float sY = cardY + 44.0f + 6.0f;
            float settingsW = cardW - 20.0f;
            float settingsX = cardX + 10.0f;

            if (module instanceof WorldTweaks wt) {
                if (WorldTweaksUI.mouseClicked(wt, settingsX, sY, settingsW, mouseX, mouseY, button)) {
                    return true;
                }
                sY += WorldTweaksUI.getTotalHeight();
            }
            for (Setting s : module.getSettings()) {
                float progress = s.getVisibilityProgress();
                if (progress <= 0.05f) continue;
                if (s instanceof BooleanSetting bool) {
                    float itemH = 24.0f * progress;
                    if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 22.0f)) {
                        bool.set(!bool.get());
                        egg.client.config.ConfigManager.save();
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof SliderSetting slider) {
                    float itemH = 32.0f * progress;
                    if (progress > 0.6f && mouseY >= sY + 8.0f && mouseY <= (sY + 24.0f)) {
                        draggingSlider = slider;
                        dragTrackX = settingsX + 6.0f;
                        dragTrackW = settingsW - 12.0f;
                        updateSliderValue(slider, dragTrackX, dragTrackW, mouseX);
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof ModeSetting mode) {
                    float itemH = 26.0f * progress;
                    if (progress > 0.6f && mouseY >= sY && mouseY <= (sY + 22.0f)) {
                        if (this.openDropdownSetting == mode) {
                            this.openDropdownSetting = null;
                        } else {
                            this.openDropdownSetting = mode;
                            float btnW = ClientFont.getWidth(mode.get(), ClientFont.SF_REGULAR) + 18.0f;
                            float btnX = settingsX + settingsW - btnW - 6.0f;
                            this.openDropdownX = btnX;
                            this.openDropdownY = sY + 20.0f;
                            this.openDropdownW = Math.max(btnW, 105.0f);
                            this.openDropdownScrollY = 0.0f;
                        }
                        return true;
                    }
                    sY += itemH;
                } else if (s instanceof ColorSetting colorSetting) {
                    float itemH = 24.0f * progress;
                    float badgeW = 76.0f;
                    float badgeH = 17.0f;
                    float[] storedBadge = this.colorSettingBadgePositions.get(colorSetting);
                    float badgeX = storedBadge != null ? storedBadge[0] : (settingsX + settingsW - badgeW - 6.0f);
                    float badgeY = storedBadge != null ? storedBadge[1] : (sY + 1.5f);
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
                    float itemH = 26.0f * progress;
                    float bW = settingsW - 12.0f;
                    float bH = 20.0f;
                    float bX = settingsX + 6.0f;
                    float bY = sY + 2.0f;
                    if (progress > 0.6f && mouseX >= bX && mouseX <= (bX + bW) && mouseY >= bY && mouseY <= (bY + bH)) {
                        btn.run();
                        return true;
                    }
                    sY += itemH;
                }
            }

            // Keybind click
            if (mouseY >= sY && mouseY <= (sY + 22.0f)) {
                listeningKeybindModule = (listeningKeybindModule == module) ? null : module;
                return true;
            }
        }

        // Card soft click press animation
        Animation press = cardPressAnims.get(module);
        if (press != null) {
            press.setValue(0.985f);
            press.setTarget(1.0f);
        }

        return true;
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
        egg.client.config.ConfigManager.save();
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        WorldTweaksUI.mouseReleased();
        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.mouseReleased();
        }
        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null) targetHUD.mouseReleased(click.x(), click.y(), click.button());
        searchSelecting = false;
        if (colorDragMode != ColorDragMode.NONE) {
            colorDragMode = ColorDragMode.NONE;
            egg.client.config.ConfigManager.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null) targetHUD.mouseDragged(click.x(), click.y());
        if (searchFocused && searchSelecting) {
            float gWidth = Math.clamp(this.width * 0.58f, 520.0f, 580.0f);
            float gX = (float) Math.round((this.width - gWidth) / 2.0f);
            float sW = 140.0f;
            float sX = gX + gWidth - sW - 14.0f;
            searchCursor = getSearchCursorIndex((float) click.x(), sX);
            return true;
        }
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }
        if (draggingSlider != null) {
            float mouseX = (float) click.x();
            updateSliderValue(draggingSlider, dragTrackX, dragTrackW, mouseX);
            return true;
        }
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && WorldTweaksUI.mouseDragged(wt, (float) click.x(), (float) click.y())) {
            return true;
        }
        if (openColorPickerSetting != null && colorDragMode != ColorDragMode.NONE) {
            float mouseX = (float) click.x();
            float mouseY = (float) click.y();
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
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing || (!closing && currentAnimProgress < 0.70f)) {
            return false;
        }
        if (selectedCategory == Category.INVENTORY) {
            egg.client.ui.section.InventoryConfigPanel.mouseScrolled(verticalAmount);
            return true;
        }
        if (this.openDropdownSetting != null && this.openDropdownMaxScroll > 0.0f) {
            this.openDropdownScrollY = Math.clamp(this.openDropdownScrollY - (float) verticalAmount * 14.0f, 0.0f, this.openDropdownMaxScroll);
            return true;
        }
        targetScroll -= (float) (verticalAmount * 34.0f);
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
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
                closeWithAnimation();
                return true;
            }
            return false;
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
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }

            // Select All (Ctrl+A)
            if (isCtrlDown() && key == GLFW.GLFW_KEY_A) {
                searchSelectionEnd = 0;
                searchCursor = searchQuery.length();
                return true;
            }

            // Copy (Ctrl+C)
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

            // Cut (Ctrl+X)
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
                markModulesDirty();
                resetCascade();
                return true;
            }

            // Paste (Ctrl+V)
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
                        markModulesDirty();
                        resetCascade();
                    }
                }
                return true;
            }

            // Backspace / Delete
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
                markModulesDirty();
                resetCascade();
                return true;
            }

            // Arrow keys & Home / End with Shift selection
            int mods = input.modifiers();
            boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            if (key == GLFW.GLFW_KEY_LEFT) {
                if (searchCursor > 0) {
                    searchCursor--;
                    if (!shift) searchSelectionEnd = searchCursor;
                } else if (!shift) {
                    searchSelectionEnd = 0;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                if (searchCursor < searchQuery.length()) {
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

            // Consume all other keys while search is focused so vanilla hotkeys don't trigger
            return true;
        } else {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
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
