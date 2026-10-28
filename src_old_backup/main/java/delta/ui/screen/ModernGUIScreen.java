package delta.ui.screen;
import delta.render.RenderStateHelper;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;

import delta.api.Compile;

import delta.core.*;

import delta.core.Module;

import delta.discord.DiscordProfileHelper;

import delta.profile.CustomProfileManager;

import delta.module.render.Interface_2;
import delta.module.cosmetics.CosmeticsModule;
import delta.module.cosmetics.CosmeticsConfigManager;
import delta.cosmetic.SkycoreCosmeticsManager;
import net.minecraft.util.math.RotationAxis;

import delta.render.*;

import delta.setting.*;

import delta.ui.shader.BlurShader;
import delta.ui.widget.MediaPlayerWidget;
import delta.util.animation.Animation;
import delta.util.animation.Easing;

import delta.util.KeyUtil;

import delta.util.MathUtil;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gl.SimpleFramebuffer;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.client.util.DefaultSkinHelper;

import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.text.Text;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import delta.config.ThemeInfo;

import org.joml.Vector4f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gui.screen.Screen;

import java.awt.Color;

import java.util.*;

public class ModernGUIScreen extends Screen {

    public enum Tab {

        COMBAT("Combat", "V", Category.Combat),

        MOVEMENT("Movement", "I", Category.Movement),

        VISUALS("Visuals", "t", Category.Render),

        PLAYER("Player", "L", Category.Player),

        MISC("Misc", "D", Category.Misc),

        COSMETICS("Cosmetics", "C", Category.Cosmetics),

        THEMES("Themes", "\\", null);

        private final String label;

        private final String icon;

        private final Category category;

        Tab(String label, String icon, Category category) {

            this.label = label;

            this.icon = icon;

            this.category = category;

        }

        public String getLabel() { return this.label; }

        public String getIcon() { return this.icon; }

        public Category getCategory() { return this.category; }

    }

    private static final Identifier EGG_LOGO = Identifier.of("delta", "textures/egg_logo.png");

    private static final Identifier COLOR_PICKER_TEX = Identifier.of("delta", "pictures/color.png");

    private static final Identifier OPACITY_TEX = Identifier.of("delta", "pictures/opacity.png");

    private Tab selectedTab = Tab.COMBAT;

    private long openStartTime = 0L;

    private float animatedTabY = -1.0f;

    // Scrolling states

    private float scrollY = 0.0f;

    private float targetScrollY = 0.0f;

    private float maxScrollY = 0.0f;

    private float themesScrollY = 0.0f;

    private float targetThemesScrollY = 0.0f;

    private float maxThemesScrollY = 0.0f;

    private float cosmeticsScrollY = 0.0f;

    private float targetCosmeticsScrollY = 0.0f;

    private float maxCosmeticsScrollY = 0.0f;
    private String newCosmeticsPresetName = "";
    private boolean cosmeticsPresetInputFocused = false;
    private float cosmeticsPresetsScrollY = 0.0f;
    private float targetCosmeticsPresetsScrollY = 0.0f;
    private float maxCosmeticsPresetsScrollY = 0.0f;
    private float lastCosmeticsRightPaneX = 0.0f;

    private int cosmeticsCategoryFilter = 0;
    private final Map<String, Float> cosmeticToggleAnim = new HashMap<>();

    private float getCosmeticToggleAnim(String key, boolean active) {
        float target = active ? 1.0f : 0.0f;
        Float current = this.cosmeticToggleAnim.get(key);
        if (current == null) {
            this.cosmeticToggleAnim.put(key, target);
            return target;
        }
        float next = MathUtil.c(current, target, 16.0f);
        this.cosmeticToggleAnim.put(key, next);
        return next;
    }

    private static int mixColors(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >> 24) & 255, r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int a2 = (c2 >> 24) & 255, r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==========================================
    // GUI BOTTOM GRADIENT & FIRE PARTICLES
    // ==========================================
    public static class GuiFireParticle {
        public float relX;
        public float yOffset;
        public float speedY;
        public float swaySpeed;
        public float swayAmp;
        public float phase;
        public float maxTravel;
        public float size;
        public float baseAlpha;
        public float warmth;

        public GuiFireParticle(Random rand, float winH) {
            reset(rand, winH, true);
        }

        public void reset(Random rand, float winH, boolean initial) {
            this.relX = rand.nextFloat();
            this.maxTravel = winH * (0.22f + rand.nextFloat() * 0.08f);
            this.yOffset = initial ? rand.nextFloat() * this.maxTravel : 0.0f;
            this.speedY = 8.0f + rand.nextFloat() * 14.0f; // Slower speed ("медленее")
            this.swaySpeed = 1.4f + rand.nextFloat() * 2.2f;
            this.swayAmp = 2.0f + rand.nextFloat() * 4.0f;
            this.phase = rand.nextFloat() * 6.28f;
            this.size = 0.65f + rand.nextFloat() * 0.95f; // Smaller dots ("точки меньше")
            this.baseAlpha = (0.22f + rand.nextFloat() * 0.28f) * 0.50f; // 50% more transparent ("на 50 процентов прозрачные")
            this.warmth = rand.nextFloat();
        }

        public void update(float dt, Random rand, float winH) {
            this.yOffset += this.speedY * dt;
            this.phase += this.swaySpeed * dt;
            if (this.yOffset >= this.maxTravel) {
                reset(rand, winH, false);
            }
        }
    }

    private static final int FIRE_PARTICLE_COUNT = 45;
    private final List<GuiFireParticle> fireParticles = new ArrayList<>();
    private final Random fireRand = new Random();
    private long lastFireTime = 0L;

    // Search bar state & mouse text selection

    private static String searchQuery = "";

    private boolean searchFocused = false;

    private int searchCursor = 0;

    private int searchSelectionStart = -1;

    private int searchSelectionEnd = -1;

    private boolean isDraggingSearch = false;

    // Search Modal & Wave Animation states
    private boolean searchModalOpen = false;
    private float searchModalAnim = 0.0f;
    private int searchSelectedIndex = 0;
    private float searchModalScrollY = 0.0f;
    private float targetSearchModalScrollY = 0.0f;
    private float searchModalMaxScroll = 0.0f;
    private long tabSwitchTime = 0L;

    // Client Logo Settings / Theme Modal
    private boolean settingsPanelOpen = false;

    // Animated Mode Setting Dropdown
    private ModeSetting openDropdownSetting = null;
    private float openDropdownX = 0.0f;
    private float openDropdownY = 0.0f;
    private float openDropdownW = 0.0f;
    private float openDropdownAnim = 0.0f;
    private float openDropdownScrollY = 0.0f;
    private float openDropdownMaxScroll = 0.0f;

    private Object bindingTarget = null;

    public Object getBindingTarget() {
        return this.bindingTarget;
    }

    // Active slider dragging

    private SliderSetting draggingSlider = null;

    private float sliderTrackX = 0.0f, sliderTrackW = 1.0f;

    // Active Color Picker

    private ColorSetting activeColorPicker = null;

    private float colorPickerX = 0.0f, colorPickerY = 0.0f;

    private float cpCurHue = 0.0f, cpCurSat = 1.0f, cpCurBri = 1.0f, cpCurAlpha = 1.0f;

    private int cpDragMode = 0; // 0=none, 1=sv area, 2=hue, 3=alpha

    // Profile Settings Modal & Color Pickers
    private boolean profileModalOpen = false;
    private float profileModalAnim = 0.0f;
    private int profileColorPickerMode = 0; // 0=none, 1=ring1, 2=ring2
    private String newSetupName = "";
    private boolean setupInputFocused = false;
    private float profileSetupsScrollY = 0.0f;
    private float targetProfileSetupsScrollY = 0.0f;
    private float profileSetupsMaxScroll = 0.0f;

    // Animation states

    private final Map<Module, Float> switchAnims = new HashMap<>();

    private final Map<Module, Float> cardHeightAnims = new HashMap<>();

    private final Map<SliderSetting, Float> sliderAnimatedProgress = new HashMap<>();

    private final Map<SliderSetting, Float> thumbScaleAnims = new HashMap<>();

    private final Map<Module, Float> cardHoverAnims = new HashMap<>();

    private final Map<BooleanSetting, Float> checkboxAnims = new HashMap<>();

    private final Animation windowAnimation = new Animation(0.0f, 260, Easing.EASE_OUT_EXPO);
    private boolean isClosing = false;
    private float searchBorderGlowAnim = 0.0f;

    public ModernGUIScreen(Text title) {

        super(title);

    }

    @Override

    protected void init() {

        super.init();

        this.openStartTime = System.currentTimeMillis();
        this.tabSwitchTime = System.currentTimeMillis();
        this.isClosing = false;
        this.windowAnimation.reset(0.0f);
        this.windowAnimation.setTarget(1.0f, 260, Easing.EASE_OUT_EXPO);
        this.searchModalOpen = false;
        this.searchModalAnim = 0.0f;
        this.searchSelectedIndex = 0;
        this.searchModalScrollY = 0.0f;
        this.targetSearchModalScrollY = 0.0f;
        this.profileModalOpen = false;
        this.profileModalAnim = 0.0f;
        this.profileColorPickerMode = 0;
        this.profileSetupsScrollY = 0.0f;
        this.targetProfileSetupsScrollY = 0.0f;

        this.animatedTabY = -1.0f;

        this.scrollY = 0.0f;

        this.targetScrollY = 0.0f;

        this.themesScrollY = 0.0f;

        this.targetThemesScrollY = 0.0f;

        this.cosmeticsScrollY = 0.0f;

        this.targetCosmeticsScrollY = 0.0f;

        this.searchFocused = false;
        this.searchCursor = searchQuery.length();

        this.searchSelectionStart = -1;

        this.searchSelectionEnd = -1;

        this.isDraggingSearch = false;

        this.settingsPanelOpen = false;

        this.bindingTarget = null;

        this.draggingSlider = null;

        this.activeColorPicker = null;

        this.cpDragMode = 0;

        this.cardHoverAnims.clear();

        this.checkboxAnims.clear();

    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Prevent vanilla screen background darkening/blurring from obscuring the game
    }

    @Override
    public void close() {
        if (!this.isClosing) {
            this.isClosing = true;
            this.windowAnimation.setTarget(0.0f, 220, Easing.EASE_IN_OUT_CUBIC);
            return;
        }
        forceClose();
    }

    public void forceClose() {
        super.close();
        this.openStartTime = 0L;
        this.isClosing = false;
        this.searchFocused = false;
        this.searchModalOpen = false;
        this.searchModalAnim = 0.0f;
        this.settingsPanelOpen = false;
        this.profileModalOpen = false;
        this.profileColorPickerMode = 0;
        this.activeColorPicker = null;
        if (this.bindingTarget instanceof Module) {
            ((Module) this.bindingTarget).b(false);
        }
        this.bindingTarget = null;
        this.draggingSlider = null;
        this.cpDragMode = 0;
    }

    @Compile
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        float anim = this.windowAnimation.update();
        if (this.isClosing && this.windowAnimation.isFinished()) {
            MediaPlayerWidget.triggerAbsorbBounce();
            forceClose();
            return;
        }

        double scaledMouseX = mouseX;
        double scaledMouseY = mouseY;

        try {
            MinecraftClient mc = Interface.aM_;

        int screenW = mc.getWindow().getScaledWidth();

        int screenH = mc.getWindow().getScaledHeight();

        if (this.openStartTime == 0L) {

            this.openStartTime = System.currentTimeMillis();

        }

        // Window size: width ~ 570, height ~ 340
        float winW = Math.min(570.0f, screenW - 24.0f);
        float winH = Math.min(340.0f, screenH - 24.0f);
        float winX = (screenW - winW) * 0.5f;
        float winY = (screenH - winH) * 0.5f;

        float winCenterX = winX + winW * 0.5f;
        float winCenterY = winY + winH * 0.5f;

        // Dynamic Island Target Coordinates
        float islandW = 152.0f;
        float islandH = 20.0f;
        float islandCenterX = screenW * 0.5f;
        float islandCenterY = 8.0f + (islandH * 0.5f);

        float currentCenterX, currentCenterY;
        float scaleX, scaleY;
        float cornerRounding;
        float visualAlpha;

        if (this.isClosing) {
            float closeT = 1.0f - anim; // 0.0 -> 1.0 during closing
            float moveEase = Easing.EASE_IN_OUT_CUBIC.ease(closeT);

            // Interpolate center toward Dynamic Island
            currentCenterX = winCenterX + (islandCenterX - winCenterX) * moveEase;
            currentCenterY = winCenterY + (islandCenterY - winCenterY) * moveEase;

            // Target scale slightly smaller than Dynamic Island dimensions so it tucks inside cleanly
            float targetScaleX = (islandW * 0.90f) / winW;
            float targetScaleY = (islandH * 0.90f) / winH;

            // Fluid stretch along vertical path of movement: stretches slightly mid-flight
            float stretch = (float) Math.sin(closeT * Math.PI) * 0.18f;

            scaleX = MathHelper.lerp(moveEase, 1.0f, targetScaleX) * (1.0f - stretch * 0.40f);
            scaleY = MathHelper.lerp(moveEase, 1.0f, targetScaleY) * (1.0f + stretch);

            // Corner rounding transitions smoothly from modern window 10px to Dynamic Island capsule radius (10px scaled = pill)
            cornerRounding = MathHelper.lerp(moveEase, 10.0f, (islandH * 0.5f) / Math.max(0.001f, scaleY));

            // Alpha stays solid through 70% of trajectory, then fades out as it slides behind the island
            if (closeT > 0.70f) {
                float absorbProgress = (closeT - 0.70f) / 0.30f;
                visualAlpha = (1.0f - absorbProgress) * (1.0f - absorbProgress);
            } else {
                visualAlpha = 1.0f;
            }
        } else {
            // Smooth entrance animation
            currentCenterX = winCenterX;
            currentCenterY = winCenterY + (1.0f - anim) * 12.0f;
            scaleX = 0.92f + 0.08f * anim;
            scaleY = 0.92f + 0.08f * anim;
            cornerRounding = 10.0f;
            visualAlpha = anim;
        }

        // Smooth scroll interpolation

        this.scrollY = MathUtil.c(this.scrollY, this.targetScrollY, 14.0f);

        if (Math.abs(this.scrollY - this.targetScrollY) < 0.2f) {

            this.scrollY = this.targetScrollY;

        }

        this.themesScrollY = MathUtil.c(this.themesScrollY, this.targetThemesScrollY, 14.0f);

        if (Math.abs(this.themesScrollY - this.targetThemesScrollY) < 0.2f) {

            this.themesScrollY = this.targetThemesScrollY;

        }

        this.cosmeticsScrollY = MathUtil.c(this.cosmeticsScrollY, this.targetCosmeticsScrollY, 14.0f);

        if (Math.abs(this.cosmeticsScrollY - this.targetCosmeticsScrollY) < 0.2f) {

            this.cosmeticsScrollY = this.targetCosmeticsScrollY;

        }

        Interface_2 iface = Interface_2.getInstance();

        boolean isDark = iface == null || iface.isDarkTheme();

        MatrixStack matrices = new MatrixStack();

        Draw2DProcessor draw = Delta.h().d().i();

        // Dim background vignette fades out cleanly
        int dimColor = isDark ? ColorUtil.a(0, 0, 0, (int) (130 * visualAlpha)) : ColorUtil.a(0, 0, 0, (int) (55 * visualAlpha));
        draw.a(matrices, 0.0f, 0.0f, (float) screenW, (float) screenH, 0.0f, dimColor);

        // Content-mapped mouse coords to maintain pixel-perfect hover during animation
        double contentMouseX = scaledMouseX;
        double contentMouseY = scaledMouseY;
        if (!this.isClosing && anim < 0.999f && scaleX > 0.01f && scaleY > 0.01f) {
            contentMouseX = (scaledMouseX - currentCenterX) / scaleX + winCenterX;
            contentMouseY = (scaledMouseY - currentCenterY) / scaleY + winCenterY;
        }

        // Apply smooth window scale, stretch, and translation toward Dynamic Island
        matrices.push();
        matrices.translate(currentCenterX, currentCenterY, 0.0f);
        if (scaleX != 1.0f || scaleY != 1.0f) {
            matrices.scale(scaleX, scaleY, 1.0f);
        }
        matrices.translate(-winCenterX, -winCenterY, 0.0f);

        // Colors per theme (use active global interface color from theme!)
        int themeCol = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int primaryAccent = (themeCol != 0) ? themeCol : (isDark ? 0xFF3B82F6 : 0xFF2563EB);
        int winBorder = isDark ? ColorUtil.a(255, 255, 255, (int) (24 * visualAlpha)) : ColorUtil.a(226, 232, 240, (int) (255 * visualAlpha));
        int textTitleColor = isDark ? ColorUtil.a(255, 255, 255, (int) (255 * visualAlpha)) : ColorUtil.a(30, 41, 59, (int) (255 * visualAlpha));
        int textMutedColor = isDark ? ColorUtil.a(148, 163, 184, (int) (210 * visualAlpha)) : ColorUtil.a(100, 116, 139, (int) (220 * visualAlpha));

        // Soft ambient aura around window during opening bloom
        float openFlash = (float) Math.sin(anim * Math.PI);
        if (openFlash > 0.01f && !this.isClosing) {
            int glowAura = ColorUtil.a(primaryAccent, (int) (65 * openFlash));
            draw.a(matrices, winX - 4.0f * openFlash, winY - 4.0f * openFlash, winW + 8.0f * openFlash, winH + 8.0f * openFlash, 12.0f, 0, 1.0f, glowAura, 20.0f * openFlash);
        }

        // Outer window drop shadow with smooth absorption
        draw.a(matrices, winX, winY, winW, winH, cornerRounding, 0x00000000, 1.0f, ColorUtil.a(0, 0, 0, (int) ((isDark ? 150 : 45) * visualAlpha)), 22.0f);

        // ==========================================
        // LEFT SIDEBAR (Width 126px) & RIGHT CONTENT AREA
        // ==========================================
        float sidebarW = 126.0f;

        // Left sidebar: Opaque dark surface with animated morphing rounding
        int sideBg = isDark ? ColorUtil.a(18, 20, 28, (int) (255 * visualAlpha)) : ColorUtil.a(248, 250, 252, (int) (255 * visualAlpha));
        draw.a(matrices, winX, winY, sidebarW, winH, new Vector4f(cornerRounding, 0.0f, cornerRounding, 0.0f), sideBg);

        // Right side: darker deep frosted glass with blur
        int winBg = isDark ? ColorUtil.a(12, 14, 20, (int) (150 * visualAlpha)) : ColorUtil.a(244, 246, 249, (int) (240 * visualAlpha));
        draw.a(matrices, winX + sidebarW, winY, winW - sidebarW, winH, new Vector4f(0.0f, cornerRounding, 0.0f, cornerRounding), winBg);

        // Outer window border & divider
        draw.a(matrices, winX, winY, winW, winH, cornerRounding, 0.8f, winBorder);
        draw.a(matrices, winX + sidebarW, winY + 4.0f, 0.5f, winH - 8.0f, 0.0f, winBorder);

        // Bottom-up glowing gradient & rising fire particles
        renderGuiBottomGradientAndFire(matrices, draw, winX, winY, winW, winH, visualAlpha, primaryAccent);

        // --- Header: Egg Logo (Clickable -> Settings Panel) + "Egg Client" ---

        float logoSize = 20.0f;

        float logoX = winX + 10.0f;

        float logoY = winY + 11.0f;

        boolean logoHovered = MathUtil.a(contentMouseX, contentMouseY, logoX - 2.0f, logoY - 2.0f, logoSize + 4.0f, logoSize + 4.0f);

        if (logoHovered) {

            draw.a(matrices, logoX - 2.0f, logoY - 2.0f, logoSize + 4.0f, logoSize + 4.0f, (logoSize + 4.0f) * 0.5f, ColorUtil.a(primaryAccent, 0.25f * anim));

        }

        // Black circle behind logo with no white frames
        int logoBg = ColorUtil.a(0, 0, 0, (int) (255 * visualAlpha));
        draw.a(matrices, logoX, logoY, logoSize, logoSize, logoSize * 0.5f, logoBg);

        draw.a(matrices, EGG_LOGO, logoX, logoY, logoSize, logoSize, logoSize * 0.5f, ColorUtil.a(255, 255, 255, (int) (255 * visualAlpha)));

        // "Egg Client" text
        Fonts.b.a(matrices, "Egg Client", logoX + logoSize + 7.0f, logoY + 4.5f, 9.5f, textTitleColor);

        // --- Quick Search Bar with Selection Highlight & Mouse Dragging ---

        float searchX = winX + 8.0f;

        float searchY = winY + 36.0f;

        float searchW = sidebarW - 16.0f;

        float searchH = 17.0f;

        int searchBg = isDark ? ColorUtil.a(28, 32, 42, (int) (210 * anim)) : ColorUtil.a(238, 242, 246, (int) (255 * anim));

        this.searchBorderGlowAnim = MathUtil.c(this.searchBorderGlowAnim, (this.searchFocused || this.searchModalOpen) ? 1.0f : 0.0f, 15.0f);
        int idleOutline = isDark ? ColorUtil.a(255, 255, 255, (int) (20 * anim)) : ColorUtil.a(220, 226, 235, (int) (255 * anim));
        int searchOutline = ColorUtil.a(idleOutline, primaryAccent, this.searchBorderGlowAnim);

        draw.a(matrices, searchX, searchY, searchW, searchH, 4.5f, searchBg);

        if (this.searchBorderGlowAnim > 0.01f) {
            int glowAura = ColorUtil.a(primaryAccent, (int) (40 * this.searchBorderGlowAnim * anim));
            draw.a(matrices, searchX - 1.0f, searchY - 1.0f, searchW + 2.0f, searchH + 2.0f, 5.0f, 0, 1.0f, glowAura, 6.0f * this.searchBorderGlowAnim);
        }

        draw.a(matrices, searchX, searchY, searchW, searchH, 4.5f, 0.6f + 0.4f * this.searchBorderGlowAnim, searchOutline);

        // Search icon

        Fonts.a.a(matrices, "Q", searchX + 5.0f, searchY + 4.5f, 7.5f, textMutedColor);

        float textStartX = searchX + 17.0f;

        if (!this.searchQuery.isEmpty() || this.searchFocused) {
            // Draw mouse selection highlight if any
            if (this.searchFocused && hasSearchSelection()) {
                int sA = Math.min(this.searchSelectionStart, this.searchSelectionEnd);
                int sB = Math.max(this.searchSelectionStart, this.searchSelectionEnd);
                sA = Math.max(0, Math.min(sA, this.searchQuery.length()));
                sB = Math.max(0, Math.min(sB, this.searchQuery.length()));
                float selX1 = textStartX + Fonts.d.a(this.searchQuery.substring(0, sA), 7.5f);
                float selX2 = textStartX + Fonts.d.a(this.searchQuery.substring(0, sB), 7.5f);
                draw.a(matrices, selX1, searchY + 2.5f, Math.max(1.0f, selX2 - selX1), searchH - 5.0f, 1.5f, ColorUtil.a(primaryAccent, 0.45f * anim));
            }
            if (!this.searchQuery.isEmpty()) {
                Fonts.d.a(matrices, this.searchQuery, textStartX, searchY + 4.0f, 7.5f, textTitleColor);
            }
            // Blinking cursor if focused
            if (this.searchFocused && (System.currentTimeMillis() / 450) % 2 == 0) {
                int cPos = Math.max(0, Math.min(this.searchCursor, this.searchQuery.length()));
                float cursorX = textStartX + Fonts.d.a(this.searchQuery.substring(0, cPos), 7.5f);
                draw.a(matrices, cursorX + 0.5f, searchY + 3.5f, 1.0f, 10.0f, 0.0f, primaryAccent);
            }
        }

        // Separator line below search

        draw.a(matrices, winX + 8.0f, searchY + searchH + 5.0f, sidebarW - 16.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (14 * anim)));

        // --- "Main" section label (colored in primary blue like reference 2!) ---

        float sectionY = winY + 62.0f;

        Fonts.b.a(matrices, "Main", winX + 10.0f, sectionY, 7.5f, primaryAccent);

        // --- Category Tabs (styled to match reference Image 4!) ---

        float tabX = winX + 8.0f;

        float tabW = sidebarW - 16.0f;

        float tabH = 18.0f;

        float tabGap = 3.0f;

        float startTabY = winY + 73.0f;

        float targetTabY = startTabY + this.selectedTab.ordinal() * (tabH + tabGap);

        if (this.animatedTabY < 0.0f) {

            this.animatedTabY = targetTabY;

        } else {

            this.animatedTabY = MathUtil.c(this.animatedTabY, targetTabY, 14.0f);

        }

        // Draw unselected tab backgrounds first
        float currentTabY = startTabY;
        for (Tab tab : Tab.values()) {
            boolean isHovered = MathUtil.a(contentMouseX, contentMouseY, tabX, currentTabY, tabW, tabH);
            boolean isSelected = tab == this.selectedTab;
            if (!isSelected) {
                int itemBg = isHovered ?
                    (isDark ? ColorUtil.a(32, 36, 48, (int) (185 * anim)) : ColorUtil.a(235, 238, 244, (int) (220 * anim))) :
                    (isDark ? ColorUtil.a(24, 27, 36, (int) (140 * anim)) : ColorUtil.a(240, 243, 248, (int) (180 * anim)));
                int itemBorder = isHovered ? (isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : 0) :
                    (isDark ? ColorUtil.a(255, 255, 255, (int) (10 * anim)) : 0);
                draw.a(matrices, tabX, currentTabY, tabW, tabH, 4.0f, itemBg);
                if (itemBorder != 0) {
                    draw.a(matrices, tabX, currentTabY, tabW, tabH, 4.0f, 0.5f, itemBorder);
                }
            }
            currentTabY += tabH + tabGap;
        }

        // Draw smooth sliding active selection capsule
        int activeTabBg = isDark ? ColorUtil.a(10, 12, 16, (int) (235 * anim)) : ColorUtil.a(218, 224, 232, (int) (240 * anim));
        int activeTabBorder = isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : ColorUtil.a(0, 0, 0, (int) (12 * anim));
        draw.a(matrices, tabX, this.animatedTabY, tabW, tabH, 4.0f, activeTabBg);
        draw.a(matrices, tabX, this.animatedTabY, tabW, tabH, 4.0f, 0.5f, activeTabBorder);
        // Draw icons and labels
        currentTabY = startTabY;
        for (Tab tab : Tab.values()) {
            boolean isHovered = MathUtil.a(contentMouseX, contentMouseY, tabX, currentTabY, tabW, tabH);
            boolean isSelected = tab == this.selectedTab;
            int tabIconColor = isSelected ? (isDark ? 0xFFFFFFFF : 0xFF0F172A) :
                (isHovered ? (isDark ? ColorUtil.a(210, 220, 235, (int) (220 * anim)) : ColorUtil.a(100, 116, 139, (int) (220 * anim))) :
                (isDark ? ColorUtil.a(155, 165, 185, (int) (140 * anim)) : ColorUtil.a(148, 163, 184, (int) (180 * anim))));
            int tabTextColor = isSelected ? (isDark ? 0xFFFFFFFF : 0xFF0F172A) :
                (isHovered ? (isDark ? ColorUtil.a(230, 238, 250, (int) (230 * anim)) : ColorUtil.a(51, 65, 85, (int) (240 * anim))) :
                (isDark ? ColorUtil.a(155, 165, 185, (int) (150 * anim)) : ColorUtil.a(100, 116, 139, (int) (180 * anim))));

            Fonts.a.a(matrices, tab.getIcon(), tabX + 7.5f, currentTabY + 5.0f, 7.0f, tabIconColor);
            Fonts.d.a(matrices, tab.getLabel(), tabX + 21.0f, currentTabY + 4.5f, 7.5f, tabTextColor);
            currentTabY += tabH + tabGap;
        }

        // --- Bottom Profile Card (Banner + Elevated Avatar with 2-color ring + Username) ---
        float profH = 34.0f;
        float profY = winY + winH - profH - 8.0f;
        float profX = winX + 6.0f;
        float profW = sidebarW - 12.0f;

        CustomProfileManager profMgr = CustomProfileManager.getInstance();
        DiscordProfileHelper discord = DiscordProfileHelper.getInstance();

        int profBg = isDark ? ColorUtil.a(24, 28, 38, (int) (225 * anim)) : ColorUtil.a(240, 243, 248, (int) (255 * anim));
        int profBorder = isDark ? ColorUtil.a(255, 255, 255, (int) (20 * anim)) : ColorUtil.a(220, 226, 235, (int) (255 * anim));

        // Profile card container background
        draw.a(matrices, profX, profY, profW, profH, 6.0f, profBg);

        // Optional custom banner background inside the card with smooth rounded clip
        if (profMgr.hasCustomBanner() && profMgr.getBannerTexture() != null) {
            draw.a(matrices, profMgr.getBannerTexture(), profX, profY, profW, profH, 6.0f, ColorUtil.a(0xFFFFFFFF, 0.85f * anim));
            // Dark gradient overlay on top of banner for crisp text contrast
            int overlayColTop = ColorUtil.a(0, 0, 0, (int) (60 * anim));
            int overlayColBot = ColorUtil.a(0, 0, 0, (int) (175 * anim));
            draw.a(matrices, profX, profY, profW, profH, 6.0f, overlayColTop, overlayColTop, overlayColBot, overlayColBot);
        }

        draw.a(matrices, profX, profY, profW, profH, 6.0f, 0.6f, profBorder);

        // Avatar positioned with clean, balanced vertical alignment
        float avSize = 22.0f;
        float avX = profX + 6.0f;
        float avY = profY + (profH - avSize) * 0.5f;

        boolean avHovered = MathUtil.a(contentMouseX, contentMouseY, avX - 2.0f, avY - 2.0f, avSize + 4.0f, avSize + 4.0f);

        // Frame / Ring with 2 colors from avatar (or custom picker)
        int ringC1 = ColorUtil.a(profMgr.getRingColor1(), anim);
        int ringC2 = ColorUtil.a(profMgr.getRingColor2(), anim);

        // Circular background glow & gradient ring around avatar
        float ringPad = 1.5f;
        float ringX = avX - ringPad;
        float ringY = avY - ringPad;
        float ringW = avSize + ringPad * 2.0f;
        float ringH = avSize + ringPad * 2.0f;

        // Shadow behind avatar
        draw.a(matrices, ringX, ringY, ringW, ringH, ringW * 0.5f, 0, 1.0f, ColorUtil.a(0, 0, 0, (int) (140 * anim)), 6.0f);

        // 2-color gradient border ring
        draw.a(matrices, ringX, ringY, ringW, ringH, ringW * 0.5f, ringC1, ringC2, ringC2, ringC1);

        // Inner black border to separate ring from image
        draw.a(matrices, avX - 0.4f, avY - 0.4f, avSize + 0.8f, avSize + 0.8f, (avSize + 0.8f) * 0.5f, 0xFF0D0F14);

        // Render Avatar Texture: Custom Avatar -> Discord Avatar -> Minecraft Skin
        Identifier avatarId = profMgr.hasCustomAvatar() ? profMgr.getAvatarTexture() : discord.getAvatarTexture();
        if (avatarId != null) {
            draw.a(matrices, avatarId, avX, avY, avSize, avSize, avSize * 0.5f, -1);
        } else {
            Identifier skin = (mc.player != null) ? mc.player.getSkin().body().id() :
                    (mc.getSession() != null && mc.getSession().getUuidOrNull() != null ?
                            DefaultSkinHelper.getSkinTextures(mc.getSession().getUuidOrNull()).body().id() : null);
            if (skin != null) {
                draw.a(matrices, skin, null, avX, avY, avSize, avSize, avSize * 0.5f, anim);
            } else {
                draw.a(matrices, avX, avY, avSize, avSize, avSize * 0.5f, ColorUtil.a(primaryAccent, 0.4f * anim));
                Fonts.a.b(matrices, "L", avX + avSize * 0.5f, avY + 5.0f, 9.0f, primaryAccent);
            }
        }

        // Hover effect on avatar hinting clickability (gear icon overlay or subtle glow)
        if (avHovered) {
            draw.a(matrices, avX, avY, avSize, avSize, avSize * 0.5f, ColorUtil.a(0, 0, 0, (int) (130 * anim)));
            Fonts.b.b(matrices, "✎", avX + avSize * 0.5f, avY + 4.5f, 7.5f, 0xFFFFFFFF);
        }

        // Small theme shortcut button on right of profile card
        float thBtnX = profX + profW - 14.0f;
        float thBtnY = profY + (profH - 12.0f) * 0.5f;
        boolean thHovered = MathUtil.a(scaledMouseX, scaledMouseY, thBtnX - 2.0f, thBtnY - 2.0f, 16.0f, 16.0f);
        Fonts.a.a(matrices, "✦", thBtnX, thBtnY + 2.0f, 7.0f, thHovered ? primaryAccent : textMutedColor);

        // ==========================================

        // TOP BAR / HEADER (Right side of window)

        // ==========================================

        float contentX = winX + sidebarW + 8.0f;

        float contentY = winY + 10.0f;

        float contentW = winW - sidebarW - 16.0f;

        float contentH = winH - 20.0f;

        // ==========================================

        // CONTENT AREA (Modules OR Themes view)

        // ==========================================

        float areaY = contentY + 16.0f;

        float areaH = contentH - 16.0f;

        if (this.selectedTab == Tab.THEMES) {

            renderThemesView(matrices, contentMouseX, contentMouseY, contentX, areaY, contentW, areaH, delta, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);

        } else if (this.selectedTab == Tab.COSMETICS) {

            renderCosmeticsView(matrices, contentMouseX, contentMouseY, contentX, areaY, contentW, areaH, delta, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);

        } else {

            renderModulesView(matrices, contentMouseX, contentMouseY, contentX, areaY, contentW, areaH, delta, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);

        }

        // Restore matrix transform for floating modals and popups
        matrices.pop();

        // ==========================================

        // FLOATING COLOR PICKER (rendered on top of everything)

        // ==========================================

        if (this.activeColorPicker != null) {

            renderColorPickerPopover(matrices, context, scaledMouseX, scaledMouseY, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);

        }

        // ==========================================

        // ==========================================

        // FLOATING SETTINGS & THEME PANEL (Modal via Egg Logo)

        // ==========================================

        if (this.settingsPanelOpen) {
            renderSettingsModal(matrices, scaledMouseX, scaledMouseY, winX, winY, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);
        }

        // ==========================================
        // FLOATING PROFILE CUSTOMIZATION MODAL (Avatar & Banner Settings)
        // ==========================================
        boolean shouldShowProfileModal = this.profileModalOpen;
        this.profileModalAnim = MathUtil.c(this.profileModalAnim, shouldShowProfileModal ? 1.0f : 0.0f, 16.0f);
        if (this.profileModalAnim > 0.01f) {
            renderProfileModal(matrices, scaledMouseX, scaledMouseY, winX, winY, winW, winH, (float) screenW, (float) screenH, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);
        }

        // ==========================================
        // FLOATING PROFILE COLOR PICKER (Ring Color 1 or 2)
        // ==========================================
        if (this.profileColorPickerMode != 0) {
            renderProfileColorPicker(matrices, scaledMouseX, scaledMouseY, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);
        }

        // ==========================================
        // FLOATING SEARCH RESULTS MODAL (Center popup with blur & similar matches)
        // ==========================================
        boolean shouldShowModal = this.searchModalOpen && !this.searchQuery.trim().isEmpty();
        this.searchModalAnim = MathUtil.c(this.searchModalAnim, shouldShowModal ? 1.0f : 0.0f, 16.0f);
        if (this.searchModalAnim > 0.01f) {
            renderSearchModal(matrices, scaledMouseX, scaledMouseY, winX, winY, winW, winH, (float) screenW, (float) screenH, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);
        }

        // Floating expanding mode setting dropdown overlay
        if (this.openDropdownSetting != null || this.openDropdownAnim > 0.01f) {
            renderOpenDropdown(matrices, scaledMouseX, scaledMouseY, anim, isDark, primaryAccent, textTitleColor, textMutedColor, winBorder);
        }

        // When closing, render Dynamic Island on top so GUI smoothly folds BEHIND the island
        if (this.isClosing) {
            MediaPlayerWidget.renderDirect(context, delta);
        }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            ScissorUtil.clear();
        }
    }

    private void renderOpenDropdown(MatrixStack matrices, double mouseX, double mouseY, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {
        Draw2DProcessor draw = Delta.h().d().i();
        boolean shouldShow = (this.openDropdownSetting != null);
        this.openDropdownAnim = MathUtil.c(this.openDropdownAnim, shouldShow ? 1.0f : 0.0f, 18.0f);
        if (this.openDropdownAnim <= 0.01f || this.openDropdownSetting == null) {
            return;
        }
        float dAnim = this.openDropdownAnim * anim;

        List<String> dModes = this.openDropdownSetting.k();
        float dItemH = 14.0f;
        int maxVisible = 7;
        int displayItems = Math.min(dModes.size(), maxVisible);
        float dListH = displayItems * dItemH;
        float dPopH = dListH + 6.0f;
        float dPopW = Math.max(this.openDropdownW, 88.0f);
        float dPopX = this.openDropdownX;
        float dPopY = this.openDropdownY;

        this.openDropdownMaxScroll = Math.max(0.0f, dModes.size() * dItemH - dListH);

        matrices.push();
        // Smooth slide-down spring animation
        float dropOffset = (1.0f - this.openDropdownAnim) * 4.0f;
        matrices.translate(0.0f, -dropOffset, 0.0f);

        // Container frosted glass card & outline
        int dropBg = isDark ? ColorUtil.a(18, 22, 32, (int) (250 * dAnim)) : ColorUtil.a(250, 252, 255, (int) (252 * dAnim));
        int dropBorder = isDark ? ColorUtil.a(255, 255, 255, (int) (28 * dAnim)) : ColorUtil.a(210, 218, 230, (int) (240 * dAnim));
        draw.a(matrices, dPopX, dPopY, dPopW, dPopH, 4.5f, dropBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (90 * dAnim)), 10.0f);
        draw.a(matrices, dPopX, dPopY, dPopW, dPopH, 4.5f, 0.6f, dropBorder);

        ScissorUtil.a(matrices, dPopX + 1.0f, dPopY + 3.0f, dPopW - 2.0f, dListH);
        float curItemY = dPopY + 3.0f - this.openDropdownScrollY;

        for (int i = 0; i < dModes.size(); i++) {
            String mName = dModes.get(i);
            boolean isCur = this.openDropdownSetting.c().equalsIgnoreCase(mName);
            boolean isHov = MathUtil.a(mouseX, mouseY, dPopX + 2.0f, curItemY, dPopW - 4.0f, dItemH);

            if (curItemY + dItemH >= dPopY + 3.0f && curItemY <= dPopY + 3.0f + dListH) {
                if (isHov || isCur) {
                    int rBg = isCur ? ColorUtil.a(primaryAccent, 0.22f * dAnim) : ColorUtil.a(255, 255, 255, (int) (14 * dAnim));
                    draw.a(matrices, dPopX + 2.5f, curItemY + 1.0f, dPopW - 5.0f, dItemH - 2.0f, 3.0f, rBg);
                }

                int txtColor = isCur ? primaryAccent : (isHov ? (isDark ? 0xFFFFFFFF : 0xFF0F172A) : titleColor);
                Fonts.b.a(matrices, mName, dPopX + 6.0f, curItemY + 3.5f, 6.5f, txtColor);

                if (isCur) {
                    drawCheckmark(matrices, dPopX + dPopW - 9.0f, curItemY + dItemH * 0.5f, 5.0f, 1.2f, primaryAccent);
                }
            }
            curItemY += dItemH;
        }

        ScissorUtil.a(matrices);
        matrices.pop();
    }

    private static final String RU_LAYOUT = "йцукенгшщзхъфывапролджэячсмитьбю.ё\"№;:?";
    private static final String EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`@#$^&";

    public static String convertRuToEnLayout(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toLowerCase().toCharArray()) {
            int idx = RU_LAYOUT.indexOf(ch);
            sb.append(idx != -1 ? EN_LAYOUT.charAt(idx) : ch);
        }
        return sb.toString();
    }

    public static String convertEnToRuLayout(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toLowerCase().toCharArray()) {
            int idx = EN_LAYOUT.indexOf(ch);
            sb.append(idx != -1 ? RU_LAYOUT.charAt(idx) : ch);
        }
        return sb.toString();
    }

    public static boolean matchesSearch(Module m, String q) {
        if (m == null || q == null || q.isEmpty()) return true;
        String query = q.toLowerCase().trim();
        if (matchesSingleQuery(m, query)) return true;

        String convertedEn = convertRuToEnLayout(query);
        if (!convertedEn.equals(query) && matchesSingleQuery(m, convertedEn)) return true;

        String convertedRu = convertEnToRuLayout(query);
        if (!convertedRu.equals(query) && matchesSingleQuery(m, convertedRu)) return true;

        return false;
    }

    private static boolean matchesSingleQuery(Module m, String query) {
        if (m.j() != null && m.j().toLowerCase().contains(query)) return true;
        if (m.k() != null && m.k().toLowerCase().contains(query)) return true;
        if (m.e() != null) {
            for (delta.setting.Setting<?> s : m.e()) {
                if (s.i() != null && s.i().toLowerCase().contains(query)) return true;
            }
        }
        return false;
    }

    private List<Module> getSearchModalMatches() {
        List<Module> all = Delta.h().d().t().e();
        String q = this.searchQuery.trim();
        return all.stream().filter(m -> {
            if (m instanceof Interface_2) return false;
            if (!q.isEmpty()) {
                return matchesSearch(m, q);
            }
            return true;
        }).sorted(Comparator.comparing(Module::j, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private String getCategoryIcon(Category cat) {
        if (cat == null) return "D";
        return switch (cat) {
            case Combat -> "V";
            case Movement -> "I";
            case Render -> "t";
            case Player -> "L";
            case Misc -> "D";
            case Cosmetics -> "C";
        };
    }

    private void ensureSearchRowVisible(int row) {
        float rowH = 22.0f;
        float rowTop = row * rowH;
        float rowBottom = rowTop + rowH;
        float visibleH = 7 * rowH;
        if (rowTop < this.targetSearchModalScrollY) {
            this.targetSearchModalScrollY = rowTop;
        } else if (rowBottom > this.targetSearchModalScrollY + visibleH) {
            this.targetSearchModalScrollY = rowBottom - visibleH;
        }
    }

    private void openModuleFromSearch(Module m) {
        if (m == null) return;
        this.searchModalOpen = false;
        this.searchFocused = false;
        if (m.l() != null) {
            for (Tab tab : Tab.values()) {
                if (tab.getCategory() == m.l()) {
                    this.selectedTab = tab;
                    this.tabSwitchTime = System.currentTimeMillis();
                    this.scrollY = 0.0f;
                    this.targetScrollY = 0.0f;
                    break;
                }
            }
        }
    }

    private void renderSearchModal(MatrixStack matrices, double mouseX, double mouseY, float winX, float winY, float winW, float winH, float screenW, float screenH, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {
        Draw2DProcessor draw = Delta.h().d().i();
        float mAnim = this.searchModalAnim * anim;
        if (mAnim <= 0.01f) return;

        // Dropdown directly below search bar inside sidebar
        float sidebarW = 126.0f;
        float modalX = winX + 8.0f;
        float modalY = winY + 36.0f + 17.0f + 3.0f;
        float modalW = sidebarW - 16.0f;

        List<Module> matches = getSearchModalMatches();
        float headerH = 14.0f;
        float rowH = 17.0f;
        int maxRows = 5;
        int displayRows = Math.max(1, Math.min(maxRows, matches.size()));
        float listH = displayRows * rowH;
        float modalH = headerH + listH + 4.0f;

        this.searchModalMaxScroll = Math.max(0.0f, matches.size() * rowH - listH);
        this.searchModalScrollY = MathUtil.c(this.searchModalScrollY, this.targetSearchModalScrollY, 14.0f);

        matrices.push();
        // Smooth slide-down & fade animation
        float popOffset = (1.0f - this.searchModalAnim) * 5.0f;
        matrices.translate(0.0f, -popOffset, 0.0f);

        // Dropdown card frosted background & outline (NO screen dimming)
        int cardBg = isDark ? ColorUtil.a(18, 22, 32, (int) (245 * mAnim)) : ColorUtil.a(250, 252, 255, (int) (250 * mAnim));
        int cardOutline = isDark ? ColorUtil.a(255, 255, 255, (int) (28 * mAnim)) : ColorUtil.a(210, 218, 230, (int) (240 * mAnim));
        draw.a(matrices, modalX, modalY, modalW, modalH, 5.0f, cardBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (80 * mAnim)), 10.0f);
        draw.a(matrices, modalX, modalY, modalW, modalH, 5.0f, 0.6f, cardOutline);

        // Header: [Q] "Найдено: N" ... "[TAB]"
        Fonts.a.a(matrices, "Q", modalX + 5.0f, modalY + 3.5f, 6.0f, primaryAccent);
        String headerTitle = matches.size() + " найд.";
        Fonts.b.a(matrices, headerTitle, modalX + 15.0f, modalY + 3.5f, 6.5f, mutedColor);
        String tabHint = "TAB";
        float hintW = Fonts.b.a(tabHint, 6.0f);
        draw.a(matrices, modalX + modalW - 6.0f - hintW - 3.0f, modalY + 2.5f, hintW + 6.0f, 9.0f, 2.0f, ColorUtil.a(primaryAccent, (int) (35 * mAnim)));
        Fonts.b.a(matrices, tabHint, modalX + modalW - 6.0f - hintW, modalY + 3.5f, 6.0f, primaryAccent);

        // Header divider
        draw.a(matrices, modalX + 4.0f, modalY + headerH, modalW - 8.0f, 0.5f, 0.0f, isDark ? ColorUtil.a(255, 255, 255, (int) (14 * mAnim)) : ColorUtil.a(0, 0, 0, (int) (12 * mAnim)));

        // Content list
        float listY = modalY + headerH + 2.0f;
        if (matches.isEmpty()) {
            Fonts.b.b(matrices, "Ничего нет", modalX + modalW * 0.5f, listY + 5.0f, 6.5f, mutedColor);
        } else {
            ScissorUtil.a(matrices, modalX + 1.0f, listY, modalW - 2.0f, listH);
            float curRowY = listY - this.searchModalScrollY;
            for (int i = 0; i < matches.size(); i++) {
                Module m = matches.get(i);
                if (curRowY + rowH >= listY && curRowY <= listY + listH) {
                    boolean isHovered = MathUtil.a(mouseX, mouseY, modalX + 3.0f, curRowY, modalW - 6.0f, rowH);
                    boolean isSelected = (i == this.searchSelectedIndex);

                    if (isHovered || isSelected) {
                        int rBg = isSelected ? ColorUtil.a(primaryAccent, 0.22f * mAnim) : ColorUtil.a(255, 255, 255, (int) (12 * mAnim));
                        draw.a(matrices, modalX + 3.0f, curRowY + 1.0f, modalW - 6.0f, rowH - 2.0f, 3.5f, rBg);
                        if (isSelected) {
                            draw.a(matrices, modalX + 3.0f, curRowY + 1.0f, modalW - 6.0f, rowH - 2.0f, 3.5f, 0.5f, ColorUtil.a(primaryAccent, 0.55f * mAnim));
                        }
                    }

                    // Section Icon on left adapts dynamically to theme primary accent!
                    String icon = getCategoryIcon(m.l());
                    int iconColor = ColorUtil.a(primaryAccent, (int) (240 * mAnim));
                    Fonts.a.a(matrices, icon, modalX + 6.0f, curRowY + 4.5f, 6.5f, iconColor);

                    // Module Name in middle
                    Fonts.b.a(matrices, m.j(), modalX + 17.0f, curRowY + 4.5f, 7.0f, titleColor);
                }
                curRowY += rowH;
            }
            ScissorUtil.a(matrices);
        }

        matrices.pop();
    }

    private void renderSettingsModal(MatrixStack matrices, double mouseX, double mouseY, float winX, float winY, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {

        Draw2DProcessor draw = Delta.h().d().i();

        Interface_2 iface = Interface_2.getInstance();

        float popX = winX + 12.0f;

        float popY = winY + 34.0f;

        float popW = 150.0f;

        float popH = 60.0f;

        // Modal shadow & background

        int modalBg = isDark ? ColorUtil.a(20, 23, 31, (int) (245 * anim)) : ColorUtil.a(255, 255, 255, (int) (255 * anim));

        draw.a(matrices, popX, popY, popW, popH, 7.0f, modalBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (130 * anim)), 18.0f);

        draw.a(matrices, popX, popY, popW, popH, 7.0f, 0.8f, isDark ? ColorUtil.a(primaryAccent, 0.6f * anim) : border);

        // Header

        Fonts.d.a(matrices, "Настройки клиента", popX + 8.0f, popY + 7.0f, 8.0f, titleColor);

        // Close button

        float closeX = popX + popW - 14.0f, closeY = popY + 6.0f;

        boolean closeHover = MathUtil.a(mouseX, mouseY, closeX - 2.0f, closeY - 2.0f, 12.0f, 12.0f);

        drawCross(matrices, closeX + 4.0f, closeY + 5.0f, 6.0f, 1.2f, closeHover ? primaryAccent : mutedColor);

        // Separator

        draw.a(matrices, popX + 6.0f, popY + 20.0f, popW - 12.0f, 0.5f, 0.0f, border);

        // Section: "Тема оформления"

        Fonts.b.a(matrices, "Тема оформления", popX + 8.0f, popY + 25.0f, 7.0f, mutedColor);

        float thBtnW = (popW - 22.0f) * 0.5f;

        float thBtnH = 14.0f;

        float darkThX = popX + 8.0f;

        float lightThX = darkThX + thBtnW + 6.0f;

        float thBtnY = popY + 36.0f;

        boolean isDarkCur = iface == null || iface.isDarkTheme();

        int darkThBg = isDarkCur ? primaryAccent : (isDark ? ColorUtil.a(35, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

        int lightThBg = !isDarkCur ? primaryAccent : (isDark ? ColorUtil.a(35, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

        draw.a(matrices, darkThX, thBtnY, thBtnW, thBtnH, 3.5f, darkThBg);

        Fonts.b.b(matrices, "Черная", darkThX + thBtnW * 0.5f, thBtnY + 2.5f, 7.0f, isDarkCur ? 0xFFFFFFFF : mutedColor);

        draw.a(matrices, lightThX, thBtnY, thBtnW, thBtnH, 3.5f, lightThBg);

        Fonts.b.b(matrices, "Белая", lightThX + thBtnW * 0.5f, thBtnY + 2.5f, 7.0f, !isDarkCur ? 0xFFFFFFFF : mutedColor);

    }

    // ==========================================
    // FLOATING PROFILE CUSTOMIZATION MODAL
    // ==========================================
    private void renderProfileModal(MatrixStack matrices, double mouseX, double mouseY, float winX, float winY, float winW, float winH, float screenW, float screenH, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {
        Draw2DProcessor draw = Delta.h().d().i();
        float mAnim = this.profileModalAnim * anim;

        // 1. Full backdrop dim with darker blur
        int backdropColor = ColorUtil.a(0, 0, 0, (int) (185 * mAnim));
        if (draw.e() != null && !draw.e().e().isEmpty()) {
            draw.b(matrices, 0.0f, 0.0f, screenW, screenH, 0.0f, backdropColor, mAnim);
        } else {
            draw.a(matrices, 0.0f, 0.0f, screenW, screenH, 0.0f, backdropColor);
        }

        CustomProfileManager profMgr = CustomProfileManager.getInstance();
        DiscordProfileHelper discord = DiscordProfileHelper.getInstance();
        MinecraftClient mc = Interface.aM_;

        float modalW = 460.0f;
        float modalH = 245.0f;
        float modalX = winX + (winW - modalW) * 0.5f;
        float modalY = winY + (winH - modalH) * 0.5f;

        // Spring pop transform
        matrices.push();
        float cx = modalX + modalW * 0.5f;
        float cy = modalY + modalH * 0.5f;
        matrices.translate(cx, cy, 0.0f);
        float scale = 0.94f + 0.06f * this.profileModalAnim;
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-cx, -cy, 0.0f);

        // Card glass background
        int cardBg = ColorUtil.a(16, 18, 26, (int) (245 * mAnim));
        int cardBorder = ColorUtil.a(255, 255, 255, (int) (24 * mAnim));
        if (draw.e() != null && !draw.e().e().isEmpty()) {
            draw.b(matrices, modalX, modalY, modalW, modalH, 10.0f, cardBg, mAnim);
        } else {
            draw.a(matrices, modalX, modalY, modalW, modalH, 10.0f, cardBg);
        }
        draw.a(matrices, modalX, modalY, modalW, modalH, 10.0f, 0.7f, cardBorder);

        // Header
        Fonts.d.a(matrices, "Настройки профиля", modalX + 12.0f, modalY + 9.0f, 8.5f, titleColor);

        draw.a(matrices, modalX + 8.0f, modalY + 23.0f, modalW - 16.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (15 * mAnim)));

        // Vertical divider separating Settings (Left 250px) and Setups/Presets (Right)
        float leftPaneW = 240.0f;
        draw.a(matrices, modalX + leftPaneW, modalY + 28.0f, 0.5f, modalH - 36.0f, 0.0f, ColorUtil.a(255, 255, 255, (int) (18 * mAnim)));

        // Preview Banner & Avatar Box
        float prevX = modalX + 12.0f;
        float prevY = modalY + 30.0f;
        float prevW = leftPaneW - 20.0f;
        float prevH = 46.0f;

        draw.a(matrices, prevX, prevY, prevW, prevH, 6.0f, ColorUtil.a(24, 28, 38, (int) (230 * mAnim)));
        if (profMgr.hasCustomBanner() && profMgr.getBannerTexture() != null) {
            draw.a(matrices, profMgr.getBannerTexture(), prevX, prevY, prevW, prevH, 6.0f, ColorUtil.a(0xFFFFFFFF, 0.9f * mAnim));
            draw.a(matrices, prevX, prevY, prevW, prevH, 6.0f, ColorUtil.a(0, 0, 0, (int) (60 * mAnim)), ColorUtil.a(0, 0, 0, (int) (60 * mAnim)), ColorUtil.a(0, 0, 0, (int) (180 * mAnim)), ColorUtil.a(0, 0, 0, (int) (180 * mAnim)));
        }
        draw.a(matrices, prevX, prevY, prevW, prevH, 6.0f, 0.5f, cardBorder);

        // Avatar inside preview
        float prevAvSz = 26.0f;
        float prevAvX = prevX + 10.0f;
        float prevAvY = prevY + (prevH - prevAvSz) * 0.5f;

        int rCol1 = ColorUtil.a(profMgr.getRingColor1(), mAnim);
        int rCol2 = ColorUtil.a(profMgr.getRingColor2(), mAnim);
        float pRingPad = 1.8f;
        draw.a(matrices, prevAvX - pRingPad, prevAvY - pRingPad, prevAvSz + pRingPad * 2.0f, prevAvSz + pRingPad * 2.0f, (prevAvSz + pRingPad * 2.0f) * 0.5f, rCol1, rCol2, rCol2, rCol1);
        draw.a(matrices, prevAvX - 0.4f, prevAvY - 0.4f, prevAvSz + 0.8f, prevAvSz + 0.8f, (prevAvSz + 0.8f) * 0.5f, 0xFF0D0F14);

        Identifier avId = profMgr.hasCustomAvatar() ? profMgr.getAvatarTexture() : discord.getAvatarTexture();
        if (avId != null) {
            draw.a(matrices, avId, prevAvX, prevAvY, prevAvSz, prevAvSz, prevAvSz * 0.5f, -1);
        } else {
            Identifier skin = (mc.player != null) ? mc.player.getSkin().body().id() :
                    (mc.getSession() != null && mc.getSession().getUuidOrNull() != null ?
                            DefaultSkinHelper.getSkinTextures(mc.getSession().getUuidOrNull()).body().id() : null);
            if (skin != null) {
                draw.a(matrices, skin, null, prevAvX, prevAvY, prevAvSz, prevAvSz, prevAvSz * 0.5f, mAnim);
            } else {
                draw.a(matrices, prevAvX, prevAvY, prevAvSz, prevAvSz, prevAvSz * 0.5f, ColorUtil.a(primaryAccent, 0.4f * mAnim));
                Fonts.a.b(matrices, "L", prevAvX + prevAvSz * 0.5f, prevAvY + 6.0f, 10.0f, primaryAccent);
            }
        }

        // Active Setup Name Badge (as requested: "в квадратике с округлениями с градиентом под цвет рамки")
        String activeName = profMgr.getCurrentSetupName();
        if (activeName != null && !activeName.isEmpty()) {
            float tagW = Fonts.d.a(activeName, 6.5f) + 10.0f;
            float tagH = 13.0f;
            float tagX = prevX + prevW - tagW - 6.0f;
            float tagY = prevY + prevH - tagH - 5.0f;

            // Dark graphite base
            draw.a(matrices, tagX, tagY, tagW, tagH, 3.5f, ColorUtil.a(16, 18, 26, (int) (225 * mAnim)));
            // Gradient matching avatar ring colors
            draw.a(matrices, tagX, tagY, tagW, tagH, new Vector4f(3.5f, 3.5f, 3.5f, 3.5f), ColorUtil.a(rCol1, (int) (80 * mAnim)), ColorUtil.a(rCol2, (int) (80 * mAnim)), ColorUtil.a(rCol1, (int) (35 * mAnim)), ColorUtil.a(rCol2, (int) (35 * mAnim)));
            draw.a(matrices, tagX, tagY, tagW, tagH, 3.5f, 0.6f, ColorUtil.a(rCol1, (int) (180 * mAnim)));
            Fonts.d.b(matrices, activeName, tagX + tagW * 0.5f, tagY + 2.5f, 6.5f, 0xFFFFFFFF);
        }

        // 1. Row: Avatar file selection
        float row1Y = modalY + 86.0f;
        Fonts.b.a(matrices, "Аватарка:", modalX + 14.0f, row1Y + 3.0f, 7.5f, titleColor);

        float btnPickAvW = 68.0f, btnPickAvH = 15.0f;
        float btnPickAvX = modalX + leftPaneW - btnPickAvW - 10.0f;
        boolean hovPickAv = MathUtil.a(mouseX, mouseY, btnPickAvX, row1Y, btnPickAvW, btnPickAvH);
        draw.a(matrices, btnPickAvX, row1Y, btnPickAvW, btnPickAvH, 3.5f, hovPickAv ? primaryAccent : ColorUtil.a(30, 35, 48, (int) (240 * mAnim)));
        Fonts.b.b(matrices, "Выбрать файл", btnPickAvX + btnPickAvW * 0.5f, row1Y + 3.0f, 6.0f, 0xFFFFFFFF);

        if (profMgr.hasCustomAvatar()) {
            float btnResetAvX = btnPickAvX - 30.0f;
            boolean hovResetAv = MathUtil.a(mouseX, mouseY, btnResetAvX, row1Y, 26.0f, btnPickAvH);
            draw.a(matrices, btnResetAvX, row1Y, 26.0f, btnPickAvH, 3.5f, hovResetAv ? 0xFFFF4B4B : ColorUtil.a(40, 25, 30, (int) (220 * mAnim)));
            Fonts.b.b(matrices, "Сброс", btnResetAvX + 13.0f, row1Y + 3.0f, 5.5f, 0xFFFFFFFF);
        }

        // 2. Row: Banner file selection
        float row2Y = modalY + 109.0f;
        Fonts.b.a(matrices, "Баннер:", modalX + 14.0f, row2Y + 3.0f, 7.5f, titleColor);

        float btnPickBanX = modalX + leftPaneW - btnPickAvW - 10.0f;
        boolean hovPickBan = MathUtil.a(mouseX, mouseY, btnPickBanX, row2Y, btnPickAvW, btnPickAvH);
        draw.a(matrices, btnPickBanX, row2Y, btnPickAvW, btnPickAvH, 3.5f, hovPickBan ? primaryAccent : ColorUtil.a(30, 35, 48, (int) (240 * mAnim)));
        Fonts.b.b(matrices, "Выбрать файл", btnPickBanX + btnPickAvW * 0.5f, row2Y + 3.0f, 6.0f, 0xFFFFFFFF);

        if (profMgr.hasCustomBanner()) {
            float btnResetBanX = btnPickBanX - 30.0f;
            boolean hovResetBan = MathUtil.a(mouseX, mouseY, btnResetBanX, row2Y, 26.0f, btnPickAvH);
            draw.a(matrices, btnResetBanX, row2Y, 26.0f, btnPickAvH, 3.5f, hovResetBan ? 0xFFFF4B4B : ColorUtil.a(40, 25, 30, (int) (220 * mAnim)));
            Fonts.b.b(matrices, "Сброс", btnResetBanX + 13.0f, row2Y + 3.0f, 5.5f, 0xFFFFFFFF);
        }

        draw.a(matrices, modalX + 12.0f, modalY + 132.0f, leftPaneW - 24.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (15 * mAnim)));

        // 3. Section: Ring color source (Auto 2 colors from avatar vs Manual Color Picker)
        float row3Y = modalY + 140.0f;
        Fonts.b.a(matrices, "Цвета рамки аватарки:", modalX + 14.0f, row3Y, 7.5f, titleColor);

        float tabW = (leftPaneW - 32.0f) * 0.5f;
        float tabH = 15.0f;
        float tabAutoX = modalX + 14.0f;
        float tabManualX = tabAutoX + tabW + 4.0f;
        float tabY = row3Y + 12.0f;

        boolean isAuto = !profMgr.isCustomColors();
        draw.a(matrices, tabAutoX, tabY, tabW, tabH, 3.5f, isAuto ? primaryAccent : ColorUtil.a(28, 32, 44, (int) (240 * mAnim)));
        Fonts.b.b(matrices, "Авто", tabAutoX + tabW * 0.5f, tabY + 3.0f, 6.5f, isAuto ? 0xFFFFFFFF : mutedColor);

        draw.a(matrices, tabManualX, tabY, tabW, tabH, 3.5f, !isAuto ? primaryAccent : ColorUtil.a(28, 32, 44, (int) (240 * mAnim)));
        Fonts.b.b(matrices, "Колор пикер", tabManualX + tabW * 0.5f, tabY + 3.0f, 6.5f, !isAuto ? 0xFFFFFFFF : mutedColor);

        // Color swatches (Color 1 & Color 2)
        float swatchY = tabY + tabH + 11.0f;
        Fonts.b.a(matrices, "Цвет 1:", modalX + 16.0f, swatchY + 2.0f, 7.0f, mutedColor);

        float sw1X = modalX + 54.0f;
        float swW = 26.0f, swH = 13.0f;
        draw.a(matrices, sw1X, swatchY, swW, swH, 3.0f, profMgr.getRingColor1());
        draw.a(matrices, sw1X, swatchY, swW, swH, 3.0f, 0.6f, this.profileColorPickerMode == 1 ? primaryAccent : cardBorder);

        Fonts.b.a(matrices, "Цвет 2:", modalX + 124.0f, swatchY + 2.0f, 7.0f, mutedColor);
        float sw2X = modalX + 162.0f;
        draw.a(matrices, sw2X, swatchY, swW, swH, 3.0f, profMgr.getRingColor2());
        draw.a(matrices, sw2X, swatchY, swW, swH, 3.0f, 0.6f, this.profileColorPickerMode == 2 ? primaryAccent : cardBorder);

        // Footnote
        Fonts.b.a(matrices, isAuto ? "Авто-цвета с фото аватарки" : "Кликните на цвет для пикера", modalX + 14.0f, swatchY + 23.0f, 6.0f, mutedColor);

        // ==========================================
        // RIGHT PANE: SETUPS / PRESETS ("типо конфигов можно назвать свой сетап")
        // ==========================================
        float rightPaneX = modalX + leftPaneW + 12.0f;
        float rightPaneW = modalW - leftPaneW - 24.0f;
        float rightTopY = modalY + 30.0f;

        Fonts.d.a(matrices, "Сохраненные сетапы", rightPaneX, rightTopY, 8.0f, titleColor);

        // Setup Name Input & Save Button
        float inputY = rightTopY + 15.0f;
        float saveBtnW = 54.0f;
        float inputH = 15.0f;
        float inputW = rightPaneW - saveBtnW - 4.0f;

        int inputBg = isDark ? ColorUtil.a(24, 28, 38, (int) (240 * mAnim)) : ColorUtil.a(240, 244, 250, (int) (240 * mAnim));
        int inputBorder = this.setupInputFocused ? primaryAccent : ColorUtil.a(255, 255, 255, (int) (22 * mAnim));
        draw.a(matrices, rightPaneX, inputY, inputW, inputH, 3.5f, inputBg);
        draw.a(matrices, rightPaneX, inputY, inputW, inputH, 3.5f, 0.6f, inputBorder);

        String showInputText = this.newSetupName.isEmpty() ? (this.setupInputFocused ? "" : "Имя сетапа...") : this.newSetupName;
        int inputTxtCol = this.newSetupName.isEmpty() ? mutedColor : 0xFFFFFFFF;
        ScissorUtil.a(matrices, rightPaneX + 4.0f, inputY, inputW - 8.0f, inputH);
        Fonts.b.a(matrices, showInputText, rightPaneX + 5.0f, inputY + 3.0f, 6.5f, inputTxtCol);
        if (this.setupInputFocused && (System.currentTimeMillis() / 450) % 2 == 0) {
            float curX = rightPaneX + 5.0f + Fonts.b.a(this.newSetupName, 6.5f);
            draw.a(matrices, curX, inputY + 2.5f, 1.0f, 10.0f, 0.0f, primaryAccent);
        }
        ScissorUtil.a(matrices);

        // Save Button
        float saveBtnX = rightPaneX + inputW + 4.0f;
        boolean hovSave = MathUtil.a(mouseX, mouseY, saveBtnX, inputY, saveBtnW, inputH);
        draw.a(matrices, saveBtnX, inputY, saveBtnW, inputH, 3.5f, hovSave ? primaryAccent : ColorUtil.a(35, 42, 58, (int) (240 * mAnim)));
        Fonts.b.b(matrices, "+ Сохранить", saveBtnX + saveBtnW * 0.5f, inputY + 3.0f, 6.0f, 0xFFFFFFFF);

        // Presets List Cards
        float listY = inputY + inputH + 8.0f;
        float cardH = 34.0f;
        float cardGap = 5.0f;
        float listH = modalH - (listY - modalY) - 10.0f;
        List<CustomProfileManager.ProfileSetup> setupsList = profMgr.getSetups();

        float totalSetupsH = setupsList.isEmpty() ? 50.0f : (setupsList.size() * cardH + Math.max(0, setupsList.size() - 1) * cardGap);
        this.profileSetupsMaxScroll = Math.max(0.0f, totalSetupsH - listH);
        this.profileSetupsScrollY = MathUtil.c(this.profileSetupsScrollY, this.targetProfileSetupsScrollY, 14.0f);
        if (Math.abs(this.profileSetupsScrollY - this.targetProfileSetupsScrollY) < 0.2f) {
            this.profileSetupsScrollY = this.targetProfileSetupsScrollY;
        }

        ScissorUtil.a(matrices, rightPaneX - 2.0f, listY - 1.0f, rightPaneW + 4.0f, listH + 2.0f);

        if (setupsList.isEmpty()) {
            draw.a(matrices, rightPaneX, listY, rightPaneW, 50.0f, 4.0f, ColorUtil.a(22, 26, 36, (int) (160 * mAnim)));
            Fonts.b.b(matrices, "Нет сохраненных сетапов", rightPaneX + rightPaneW * 0.5f, listY + 16.0f, 6.5f, mutedColor);
            Fonts.b.b(matrices, "Введите имя и нажмите Сохранить", rightPaneX + rightPaneW * 0.5f, listY + 28.0f, 5.5f, mutedColor);
        } else {
            for (int i = 0; i < setupsList.size(); i++) {
                CustomProfileManager.ProfileSetup setup = setupsList.get(i);
                float sY = listY + i * (cardH + cardGap) - this.profileSetupsScrollY;
                if (sY + cardH < listY - 5.0f || sY > listY + listH + 5.0f) {
                    continue;
                }
                boolean isCurrent = setup.name.equalsIgnoreCase(profMgr.getCurrentSetupName());

                // Dedicated Delete button geometry on right edge of card
                float delBtnSz = 16.0f;
                float delBtnX = rightPaneX + rightPaneW - delBtnSz - 7.0f;
                float delBtnY = sY + (cardH - delBtnSz) * 0.5f;
                boolean hovDel = MathUtil.a(mouseX, mouseY, delBtnX - 1.5f, delBtnY - 1.5f, delBtnSz + 3.0f, delBtnSz + 3.0f);
                boolean hovCard = MathUtil.a(mouseX, mouseY, rightPaneX, sY, rightPaneW - (delBtnSz + 14.0f), cardH);

                // Setup Card base
                int sCardBg = isDark ? ColorUtil.a(20, 24, 34, (int) (230 * mAnim)) : ColorUtil.a(244, 246, 250, (int) (240 * mAnim));
                draw.a(matrices, rightPaneX, sY, rightPaneW, cardH, 4.0f, sCardBg);

                // Preview banner texture if present
                if (setup.bannerTexId != null) {
                    draw.a(matrices, setup.bannerTexId, rightPaneX, sY, rightPaneW, cardH, 4.0f, ColorUtil.a(0xFFFFFFFF, 0.75f * mAnim));
                    draw.a(matrices, rightPaneX, sY, rightPaneW, cardH, 4.0f, ColorUtil.a(0, 0, 0, (int) (110 * mAnim)));
                }

                // Card border
                int cBorder = isCurrent ? primaryAccent : (hovCard ? ColorUtil.a(primaryAccent, 0.6f * mAnim) : ColorUtil.a(255, 255, 255, (int) (18 * mAnim)));
                draw.a(matrices, rightPaneX, sY, rightPaneW, cardH, 4.0f, isCurrent ? 1.0f : 0.5f, cBorder);

                // Micro avatar
                float microAvSz = 20.0f;
                float microAvX = rightPaneX + 6.0f;
                float microAvY = sY + (cardH - microAvSz) * 0.5f;

                int sR1 = ColorUtil.a(setup.ringColor1, mAnim);
                int sR2 = ColorUtil.a(setup.ringColor2, mAnim);
                draw.a(matrices, microAvX - 1.2f, microAvY - 1.2f, microAvSz + 2.4f, microAvSz + 2.4f, (microAvSz + 2.4f) * 0.5f, sR1, sR2, sR2, sR1);

                if (setup.avatarTexId != null) {
                    draw.a(matrices, setup.avatarTexId, microAvX, microAvY, microAvSz, microAvSz, microAvSz * 0.5f, -1);
                } else if (avId != null) {
                    draw.a(matrices, avId, microAvX, microAvY, microAvSz, microAvSz, microAvSz * 0.5f, -1);
                } else {
                    draw.a(matrices, microAvX, microAvY, microAvSz, microAvSz, microAvSz * 0.5f, ColorUtil.a(primaryAccent, 0.4f * mAnim));
                }

                // Name tag with gradient matching setup colors
                float tagW = Fonts.d.a(setup.name, 6.0f) + 8.0f;
                float tagH = 11.5f;
                float tagX = microAvX + microAvSz + 6.0f;
                float tagY = sY + (cardH - tagH) * 0.5f;

                draw.a(matrices, tagX, tagY, tagW, tagH, 3.0f, ColorUtil.a(14, 16, 22, (int) (225 * mAnim)));
                draw.a(matrices, tagX, tagY, tagW, tagH, new Vector4f(3.0f, 3.0f, 3.0f, 3.0f), ColorUtil.a(sR1, (int) (70 * mAnim)), ColorUtil.a(sR2, (int) (70 * mAnim)), ColorUtil.a(sR1, (int) (30 * mAnim)), ColorUtil.a(sR2, (int) (30 * mAnim)));
                draw.a(matrices, tagX, tagY, tagW, tagH, 3.0f, 0.5f, ColorUtil.a(sR1, (int) (160 * mAnim)));
                Fonts.d.b(matrices, setup.name, tagX + tagW * 0.5f, tagY + 2.0f, 6.0f, 0xFFFFFFFF);

                // Dedicated Delete button on right edge of card
                int delBg = hovDel ? ColorUtil.a(239, 68, 68, (int) (235 * mAnim)) : ColorUtil.a(16, 20, 28, (int) (200 * mAnim));
                int delBorder = hovDel ? ColorUtil.a(255, 120, 120, (int) (240 * mAnim)) : ColorUtil.a(255, 255, 255, (int) (25 * mAnim));
                draw.a(matrices, delBtnX, delBtnY, delBtnSz, delBtnSz, 4.0f, delBg);
                draw.a(matrices, delBtnX, delBtnY, delBtnSz, delBtnSz, 4.0f, 0.6f, delBorder);

                // Sharp procedural cross icon
                float crossCx = delBtnX + delBtnSz * 0.5f;
                float crossCy = delBtnY + delBtnSz * 0.5f;
                int crossCol = hovDel ? 0xFFFFFFFF : ColorUtil.a(185, 190, 205, (int) (210 * mAnim));
                drawCross(matrices, crossCx, crossCy, 6.0f, 1.2f, crossCol);

                if (hovDel) {
                    float tipW = Fonts.b.a("Удалить", 5.5f);
                    draw.a(matrices, delBtnX - tipW - 8.0f, delBtnY + 1.0f, tipW + 6.0f, 14.0f, 3.0f, ColorUtil.a(20, 8, 10, (int) (230 * mAnim)));
                    draw.a(matrices, delBtnX - tipW - 8.0f, delBtnY + 1.0f, tipW + 6.0f, 14.0f, 3.0f, 0.5f, 0xFFFF4B4B);
                    Fonts.b.a(matrices, "Удалить", delBtnX - tipW - 5.0f, delBtnY + 3.5f, 5.5f, 0xFFFF6B6B);
                }
            }
        }

        ScissorUtil.a(matrices);

        matrices.pop();
    }

    // ==========================================
    // PROFILE COLOR PICKER POPOVER
    // ==========================================
    private void renderProfileColorPicker(MatrixStack matrices, double mouseX, double mouseY, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {
        Draw2DProcessor draw = Delta.h().d().i();
        CustomProfileManager profMgr = CustomProfileManager.getInstance();

        float popW = 92.0f;
        float popH = 82.0f;
        float px = this.colorPickerX;
        float py = this.colorPickerY;

        int popBg = isDark ? ColorUtil.a(20, 23, 31, (int) (245 * anim)) : ColorUtil.a(255, 255, 255, (int) (255 * anim));
        draw.a(matrices, px, py, popW, popH, 6.0f, popBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (140 * anim)), 16.0f);
        draw.a(matrices, px, py, popW, popH, 6.0f, 0.8f, ColorUtil.a(primaryAccent, 0.7f * anim));

        // Saturation/Value Area
        float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;
        int hueRgb = Color.HSBtoRGB(this.cpCurHue, 1.0f, 1.0f);
        draw.a(matrices, svX, svY, svW, svH, 2.5f, ColorUtil.a(0xFFFFFFFF, anim), ColorUtil.a(hueRgb, anim), ColorUtil.a(0xFF000000, anim), ColorUtil.a(0xFF000000, anim));
        draw.a(matrices, svX, svY, svW, svH, 2.5f, 0.5f, border);

        float curX = svX + this.cpCurSat * svW;
        float curY = svY + (1.0f - this.cpCurBri) * svH;
        draw.a(matrices, curX - 2.0f, curY - 2.0f, 4.0f, 4.0f, 2.0f, 0xFFFFFFFF);
        draw.a(matrices, curX - 2.0f, curY - 2.0f, 4.0f, 4.0f, 2.0f, 0.5f, 0xFF000000);

        // Hue strip
        float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;
        draw.a(matrices, COLOR_PICKER_TEX, hX, hY, hW, hH, 2.0f, ColorUtil.a(0xFFFFFFFF, anim));
        draw.a(matrices, hX, hY, hW, hH, 2.0f, 0.5f, border);
        float hCursorY = hY + this.cpCurHue * hH;
        draw.a(matrices, hX - 0.5f, hCursorY - 1.0f, hW + 1.0f, 2.0f, 0.5f, 0xFFFFFFFF);

        // Alpha strip
        float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;
        int currentRgb = Color.HSBtoRGB(this.cpCurHue, this.cpCurSat, this.cpCurBri);
        draw.a(matrices, OPACITY_TEX, aX, aY, aW, aH, 2.0f, ColorUtil.a(0xFFFFFFFF, 0.15f * anim));
        draw.a(matrices, aX, aY, aW, aH, 2.0f, ColorUtil.a(currentRgb, anim), ColorUtil.a(currentRgb, anim), ColorUtil.a(currentRgb, 0.0f), ColorUtil.a(currentRgb, 0.0f));
        draw.a(matrices, aX, aY, aW, aH, 2.0f, 0.5f, border);
        float aCursorY = aY + (1.0f - this.cpCurAlpha) * aH;
        draw.a(matrices, aX - 0.5f, aCursorY - 1.0f, aW + 1.0f, 2.0f, 0.5f, 0xFFFFFFFF);

        int curCol = this.profileColorPickerMode == 1 ? profMgr.getRingColor1() : profMgr.getRingColor2();
        String hexStr = String.format("#%06X", (curCol & 0xFFFFFF));
        Fonts.b.a(matrices, hexStr, px + 8.0f, py + 68.0f, 6.5f, titleColor);

        float pvW = 14.0f, pvH = 8.0f, pvX = px + popW - pvW - 6.0f, pvY = py + 66.0f;
        draw.a(matrices, pvX, pvY, pvW, pvH, 2.5f, curCol);
        draw.a(matrices, pvX, pvY, pvW, pvH, 2.5f, 0.5f, border);
    }

    // ==========================================
    // BOTTOM-UP GRADIENT & RISING FIRE PARTICLES
    // "с низу вверх в гуи градиент и как будто от туда частица огня слабо видные и когда они прошли примерно 1/4 гуи то плавно пропадают"
    // ==========================================
    private void renderGuiBottomGradientAndFire(MatrixStack matrices, Draw2DProcessor draw, float winX, float winY, float winW, float winH, float anim, int primaryAccent) {
        if (anim <= 0.01f) return;

        // Clip strictly inside the window bounds
        ScissorUtil.a(matrices, winX, winY, winW, winH);

        int themeCol = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int tr = (themeCol >> 16) & 0xFF;
        int tg = (themeCol >> 8) & 0xFF;
        int tb = themeCol & 0xFF;

        // 1. Bottom-to-top glowing gradient across bottom 35% of GUI
        float gradH = winH * 0.35f;
        float gradY = winY + winH - gradH;
        int colTop = ColorUtil.a(tr, tg, tb, 0);
        int colBotL = ColorUtil.a(tr, tg, tb, (int) (26 * anim));
        int colBotR = ColorUtil.a(tr, tg, tb, (int) (52 * anim));
        draw.a(matrices, winX, gradY, winW, gradH, new Vector4f(0.0f, 0.0f, 10.0f, 10.0f), colTop, colTop, colBotL, colBotR);

        // Delicate illuminated rim at bottom edge matching screenshot
        draw.a(matrices, winX + 12.0f, winY + winH - 1.2f, winW - 24.0f, 0.8f, 0.4f, ColorUtil.a(tr, tg, tb, (int) (70 * anim)));

        // 2. Rising subtle fire particles ("частица огня слабо видные, когда прошли 1/4 гуи плавно пропадают")
        if (this.fireParticles.isEmpty()) {
            for (int i = 0; i < FIRE_PARTICLE_COUNT; i++) {
                this.fireParticles.add(new GuiFireParticle(this.fireRand, winH));
            }
        }

        long now = System.currentTimeMillis();
        float dt = (this.lastFireTime == 0L) ? 0.016f : MathHelper.clamp((now - this.lastFireTime) / 1000.0f, 0.001f, 0.08f);
        this.lastFireTime = now;

        for (GuiFireParticle p : this.fireParticles) {
            p.update(dt, this.fireRand, winH);

            float px = winX + (p.relX * winW) + (float) Math.sin(p.phase) * p.swayAmp;
            float py = winY + winH - p.yOffset;

            // Travel progress towards 1/4 of GUI (0.0 at bottom -> 1.0 at 1/4 height)
            float progress = MathHelper.clamp(p.yOffset / p.maxTravel, 0.0f, 1.0f);

            // Smooth fade-in near bottom, smooth dissolution/fade-out as it reaches 1/4 height
            float fadeFactor;
            if (progress < 0.20f) {
                fadeFactor = progress / 0.20f;
            } else if (progress > 0.60f) {
                fadeFactor = (1.0f - progress) / 0.40f;
            } else {
                fadeFactor = 1.0f;
            }

            float pAlpha = p.baseAlpha * fadeFactor * anim;
            if (pAlpha > 0.01f) {
                // Flame particles styled under theme color ("пламя должно быть под цвет темы")
                int finalColor = ColorUtil.a(themeCol, -1, 0.25f * p.warmth);

                // Soft outer glowing fire halo
                float hr = p.size * 2.0f;
                draw.a(matrices, px - hr, py - hr, hr * 2.0f, hr * 2.0f, hr, ColorUtil.a(finalColor, (int) (30 * pAlpha)));

                // Fiery core ember spark
                draw.a(matrices, px - p.size, py - p.size, p.size * 2.0f, p.size * 2.0f, p.size, ColorUtil.a(finalColor, (int) (180 * pAlpha)));
            }
        }

        ScissorUtil.a(matrices);
    }

    // --- THEMES VIEW WITH MOVED INTERFACE SETTINGS ---

    private void renderThemesView(MatrixStack matrices, double mouseX, double mouseY, float x, float y, float w, float h, float delta, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {

        Draw2DProcessor draw = Delta.h().d().i();

        Interface_2 iface = Interface_2.getInstance();

        // Calculate total scrollable height for themes view

        float interfaceCardH = (iface != null) ? calculateCardHeight(iface, w - 8.0f) : 0.0f;

        float totalThemesH = 36.0f + 92.0f + 20.0f + interfaceCardH + 20.0f;

        this.maxThemesScrollY = Math.max(0.0f, totalThemesH - h);

        ScissorUtil.a(matrices, x - 2.0f, y, w + 4.0f, h);

        float curY = y - this.themesScrollY;

        Fonts.d.a(matrices, "All available themes", x + 4.0f, curY + 2.0f, 10.0f, titleColor);

        Fonts.b.a(matrices, "Default themes.", x + 4.0f, curY + 20.0f, 8.0f, mutedColor);

        // Two large theme cards side-by-side

        float cardW = (w - 18.0f) * 0.5f;

        float cardH = 92.0f;

        // Card 1: Dark standard (Черная)

        float darkX = x + 4.0f;

        float darkY = curY + 34.0f;

        boolean darkHovered = MathUtil.a(mouseX, mouseY, darkX, darkY, cardW, cardH);

        int darkCardBg = isDark ? ColorUtil.a(25, 28, 36, (int) (230 * anim)) : ColorUtil.a(255, 255, 255, (int) (255 * anim));

        int darkCardBorder = isDark ? primaryAccent : (darkHovered ? primaryAccent : border);

        draw.a(matrices, darkX, darkY, cardW, cardH, 6.0f, darkCardBg);

        draw.a(matrices, darkX, darkY, cardW, cardH, 6.0f, isDark ? 1.0f : 0.6f, darkCardBorder);

        draw.a(matrices, darkX + 8.0f, darkY + 8.0f, 18.0f, 18.0f, 9.0f, primaryAccent);

        Fonts.a.b(matrices, "t", darkX + 17.0f, darkY + 12.0f, 8.0f, 0xFFFFFFFF);

        Fonts.d.a(matrices, "Dark standard", darkX + 32.0f, darkY + 8.0f, 8.5f, titleColor);

        Fonts.b.a(matrices, "The default dark color palette with blur.", darkX + 32.0f, darkY + 18.0f, 7.0f, mutedColor);

        if (isDark) {

            float badgeW = 32.0f, badgeH = 12.0f;

            float badgeX = darkX + cardW - badgeW - 8.0f;

            float badgeY = darkY + 8.0f;

            draw.a(matrices, badgeX, badgeY, badgeW, badgeH, 3.0f, primaryAccent);

            Fonts.b.b(matrices, "Active", badgeX + badgeW * 0.5f, badgeY + 2.0f, 6.5f, 0xFFFFFFFF);

        }

        // Preview box

        float prevX = darkX + 8.0f, prevY = darkY + 34.0f, prevW = cardW - 16.0f, prevH = 50.0f;

        draw.a(matrices, prevX, prevY, prevW, prevH, 4.0f, 0xFF14161D);

        draw.a(matrices, prevX + 4.0f, prevY + 4.0f, 26.0f, prevH - 8.0f, 2.0f, 0xFF0E1015);

        draw.a(matrices, prevX + 34.0f, prevY + 6.0f, (prevW - 42.0f) * 0.5f, 16.0f, 2.0f, 0xFF1E212B);

        draw.a(matrices, prevX + 34.0f + (prevW - 42.0f) * 0.5f + 4.0f, prevY + 6.0f, (prevW - 42.0f) * 0.5f, 16.0f, 2.0f, 0xFF1E212B);

        draw.a(matrices, prevX + 34.0f, prevY + 26.0f, (prevW - 42.0f) * 0.5f, 18.0f, 2.0f, 0xFF1E212B);

        draw.a(matrices, prevX + 34.0f + (prevW - 42.0f) * 0.5f + 4.0f, prevY + 26.0f, (prevW - 42.0f) * 0.5f, 18.0f, 2.0f, 0xFF1E212B);

        // Card 2: Light standard (Белая)

        float lightX = x + cardW + 12.0f;

        float lightY = curY + 34.0f;

        boolean lightHovered = MathUtil.a(mouseX, mouseY, lightX, lightY, cardW, cardH);

        int lightCardBg = isDark ? ColorUtil.a(25, 28, 36, (int) (230 * anim)) : ColorUtil.a(255, 255, 255, (int) (255 * anim));

        int lightCardBorder = !isDark ? primaryAccent : (lightHovered ? primaryAccent : border);

        draw.a(matrices, lightX, lightY, cardW, cardH, 6.0f, lightCardBg);

        draw.a(matrices, lightX, lightY, cardW, cardH, 6.0f, !isDark ? 1.0f : 0.6f, lightCardBorder);

        draw.a(matrices, lightX + 8.0f, lightY + 8.0f, 18.0f, 18.0f, 9.0f, primaryAccent);

        Fonts.a.b(matrices, "t", lightX + 17.0f, lightY + 12.0f, 8.0f, 0xFFFFFFFF);

        Fonts.d.a(matrices, "Light standard", lightX + 32.0f, lightY + 8.0f, 8.5f, titleColor);

        Fonts.b.a(matrices, "The default light color palette without blur.", lightX + 32.0f, lightY + 18.0f, 7.0f, mutedColor);

        if (!isDark) {

            float badgeW = 32.0f, badgeH = 12.0f;

            float badgeX = lightX + cardW - badgeW - 8.0f;

            float badgeY = lightY + 8.0f;

            draw.a(matrices, badgeX, badgeY, badgeW, badgeH, 3.0f, primaryAccent);

            Fonts.b.b(matrices, "Active", badgeX + badgeW * 0.5f, badgeY + 2.0f, 6.5f, 0xFFFFFFFF);

        }

        float lPrevX = lightX + 8.0f, lPrevY = lightY + 34.0f, lPrevW = cardW - 16.0f, lPrevH = 50.0f;

        draw.a(matrices, lPrevX, lPrevY, lPrevW, lPrevH, 4.0f, 0xFFF1F4F9);

        draw.a(matrices, lPrevX + 4.0f, lPrevY + 4.0f, 26.0f, lPrevH - 8.0f, 2.0f, 0xFFFFFFFF);

        draw.a(matrices, lPrevX + 34.0f, lPrevY + 6.0f, (lPrevW - 42.0f) * 0.5f, 16.0f, 2.0f, 0xFFFFFFFF);

        draw.a(matrices, lPrevX + 34.0f + (lPrevW - 42.0f) * 0.5f + 4.0f, lPrevY + 6.0f, (lPrevW - 42.0f) * 0.5f, 16.0f, 2.0f, 0xFFFFFFFF);

        draw.a(matrices, lPrevX + 34.0f, lPrevY + 26.0f, (lPrevW - 42.0f) * 0.5f, 18.0f, 2.0f, 0xFFFFFFFF);

        draw.a(matrices, lPrevX + 34.0f + (lPrevW - 42.0f) * 0.5f + 4.0f, lPrevY + 26.0f, (lPrevW - 42.0f) * 0.5f, 18.0f, 2.0f, 0xFFFFFFFF);

        // Section: "Настройки интерфейса"

        float secY = curY + 138.0f;

        Fonts.d.a(matrices, "Настройки интерфейса (HUD)", x + 4.0f, secY, 9.0f, titleColor);

        Fonts.b.a(matrices, "Кастомизация цветов, размытия и элементов интерфейса", x + 4.0f, secY + 12.0f, 7.0f, mutedColor);

        // Render Interface Module Settings Card inside Themes

        if (iface != null) {

            float iCardY = secY + 24.0f;

            float iCardW = w - 8.0f;

            renderModuleCard(matrices, iface, mouseX, mouseY, x + 4.0f, iCardY, iCardW, interfaceCardH, delta, anim, isDark, primaryAccent, titleColor, mutedColor, border);

        }

        ScissorUtil.a(matrices);

    }

    // ==========================================
    // COSMETICS VIEW & INTERACTION SYSTEM
    // ==========================================
    private static class CosmeticCategoryData {
        final String title;
        final String icon;
        final ModeSetting setting;
        final String defaultNone;

        CosmeticCategoryData(String title, String icon, ModeSetting setting, String defaultNone) {
            this.title = title;
            this.icon = icon;
            this.setting = setting;
            this.defaultNone = defaultNone;
        }
    }

    private List<CosmeticCategoryData> getCosmeticCategoriesList(CosmeticsModule cosmetics) {
        List<CosmeticCategoryData> list = new ArrayList<>();
        if (cosmetics != null) {
            list.add(new CosmeticCategoryData("Крылья (Wings)", "W", cosmetics.getWings(), "None"));
            list.add(new CosmeticCategoryData("Рюкзаки (Backpacks)", "B", cosmetics.getBackpacks(), "None"));
            list.add(new CosmeticCategoryData("Шляпы (Hats)", "H", cosmetics.getHats(), "None"));
            list.add(new CosmeticCategoryData("Маски (Masks)", "M", cosmetics.getMasks(), "None"));
            list.add(new CosmeticCategoryData("Питомцы (Pets)", "P", cosmetics.getPets(), "None"));
        }
        return list;
    }

    private static final String[] COSMETIC_CAT_NAMES = new String[] { "Все", "Шляпы", "Крылья", "Рюкзаки", "Маски", "Питомцы", "Мечи" };
    private static final String[] COSMETIC_CAT_KEYS = new String[] { "all", "hats", "wings", "backpacks", "masks", "pets", "swords" };

    private void renderCosmeticsView(MatrixStack matrices, double mouseX, double mouseY, float x, float y, float w, float h, float delta, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {
        Draw2DProcessor draw = Delta.h().d().i();
        CosmeticsModule cosmetics = CosmeticsModule.getInstance();
        if (cosmetics == null) {
            cosmetics = (CosmeticsModule) Delta.h().d().t().e().stream().filter(m -> m instanceof CosmeticsModule).findFirst().orElse(null);
        }

        SkycoreCosmeticsManager mgr = SkycoreCosmeticsManager.getInstance();
        mgr.ensureIndex();

        float heroH = 46.0f;
        float navH = 20.0f;
        float rightPaneW = 155.0f;
        float mainW = w - rightPaneW - 8.0f;
        float heroX = x + 4.0f;
        float heroW = mainW - 8.0f;
        this.lastCosmeticsRightPaneX = x + mainW + 4.0f;
        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (heroW - (cols - 1) * colGap) / cols;
        float cardH = 136.0f;
        float previewSize = 78.0f;
        float separatorYOffset = 96.0f;
        float bottomYOffset = 104.0f;

        // Categories to display
        List<String> activeCats = new ArrayList<>();
        if (this.cosmeticsCategoryFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("swords");
        } else if (this.cosmeticsCategoryFilter < COSMETIC_CAT_KEYS.length) {
            activeCats.add(COSMETIC_CAT_KEYS[this.cosmeticsCategoryFilter]);
        }

        // Calculate total content height for smooth scrolling
        float totalH = heroH + 8.0f + navH + 10.0f;
        for (String catKey : activeCats) {
            int count = "swords".equals(catKey) ? mgr.getSwords(searchQuery).size() : mgr.getCategoryAssets(catKey, searchQuery).size();
            int rows = Math.max(1, (count + cols - 1) / cols);
            totalH += 22.0f + rows * (cardH + rowGap) + 12.0f;
        }

        this.maxCosmeticsScrollY = Math.max(0.0f, totalH - h);

        ScissorUtil.a(matrices, x - 2.0f, y, mainW + 4.0f, h);

        float curY = y - this.cosmeticsScrollY;

        // 1. HERO STUDIO BANNER CARD
        int cardBg = isDark ? ColorUtil.a(18, 22, 32, (int) (225 * anim)) : ColorUtil.a(240, 244, 250, (int) (230 * anim));
        int cardBrd = isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : ColorUtil.a(0, 0, 0, (int) (14 * anim));

        draw.a(matrices, heroX, curY, heroW, heroH, 6.0f, cardBg);
        draw.a(matrices, heroX, curY, heroW, heroH, 6.0f, 0.6f, cardBrd);

        // Subtle gradient glow behind hero
        int hGlowL = ColorUtil.a(primaryAccent, (int) (32 * anim));
        int hGlowR = ColorUtil.a(primaryAccent, (int) (8 * anim));
        draw.a(matrices, heroX, curY, heroW, heroH, new Vector4f(6.0f, 6.0f, 6.0f, 6.0f), hGlowL, hGlowR, hGlowR, hGlowL);

        // Icon badge
        float icSz = 26.0f;
        float icX = heroX + 10.0f;
        float icY = curY + (heroH - icSz) * 0.5f;
        draw.a(matrices, icX, icY, icSz, icSz, 5.0f, ColorUtil.a(primaryAccent, (int) (50 * anim)));
        draw.a(matrices, icX, icY, icSz, icSz, 5.0f, 0.6f, ColorUtil.a(primaryAccent, (int) (160 * anim)));
        Fonts.a.b(matrices, "C", icX + icSz * 0.5f, icY + 6.0f, 13.0f, primaryAccent);

        // Right side master toggle slider (ползунок) & "Снять все"
        float masterTogW = 24.0f, masterTogH = 13.0f;
        float masterTogX = heroX + heroW - masterTogW - 10.0f;
        float masterTogY = curY + (heroH - masterTogH) * 0.5f;

        float clrW = 48.0f, clrH = 15.0f;
        float clrX = masterTogX - clrW - 8.0f;
        float clrY = curY + (heroH - clrH) * 0.5f;

        // Title and description with max width protection against overlapping buttons
        float textX = icX + icSz + 10.0f;
        float maxTextW = Math.max(10.0f, clrX - textX - 8.0f);
        Fonts.d.a(matrices, "Cosmetics Studio", textX, curY + 10.0f, 9.5f, titleColor);

        String subDesc = "Кастомные 3D крылья, рюкзаки, шляпы, маски, питомцы и мечи";
        if (Fonts.b.a(subDesc, 6.5f) > maxTextW) {
            while (subDesc.length() > 3 && Fonts.b.a(subDesc + "..", 6.5f) > maxTextW) {
                subDesc = subDesc.substring(0, subDesc.length() - 1);
            }
            subDesc = subDesc + "..";
        }
        Fonts.b.a(matrices, subDesc, textX, curY + 23.5f, 6.5f, mutedColor);

        if (cosmetics != null) {
            boolean isModEnabled = cosmetics.isEnabled();
            float masterAnim = getCosmeticToggleAnim("__master_cosmetics__", isModEnabled);

            // Modern toggle slider pill (ползунок)
            int offPillBg = isDark ? 0xFF232838 : 0xFFCBD5E1;
            int onPillBg = primaryAccent;
            int pillBg = ColorUtil.a(mixColors(offPillBg, onPillBg, masterAnim), (int) (255 * anim));
            draw.a(matrices, masterTogX, masterTogY, masterTogW, masterTogH, 6.5f, pillBg);
            draw.a(matrices, masterTogX, masterTogY, masterTogW, masterTogH, 6.5f, 0.5f, isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : cardBrd);

            float mDotSz = 9.0f;
            float mDotX = masterTogX + 2.0f + masterAnim * 11.0f;
            float mDotY = masterTogY + 2.0f;
            draw.a(matrices, mDotX, mDotY, mDotSz, mDotSz, 4.5f, 0xFFFFFFFF);

            // "Снять всё" (Clear all) button
            boolean hovClr = MathUtil.a(mouseX, mouseY, clrX, clrY, clrW, clrH);
            draw.a(matrices, clrX, clrY, clrW, clrH, 4.0f, hovClr ? ColorUtil.a(239, 68, 68, (int) (200 * anim)) : ColorUtil.a(28, 32, 44, (int) (180 * anim)));
            draw.a(matrices, clrX, clrY, clrW, clrH, 4.0f, 0.5f, hovClr ? 0xFFFF6B6B : cardBrd);
            Fonts.b.b(matrices, "Снять все", clrX + clrW * 0.5f, clrY + 3.5f, 5.5f, hovClr ? 0xFFFFFFFF : mutedColor);
        }

        // 2. CATEGORY NAV PILLS
        float navY = curY + heroH + 8.0f;
        float totalNavW = heroW;
        float pillGap = 3.5f;
        float pillW = (totalNavW - (COSMETIC_CAT_NAMES.length - 1) * pillGap) / COSMETIC_CAT_NAMES.length;

        for (int i = 0; i < COSMETIC_CAT_NAMES.length; i++) {
            float pX = heroX + i * (pillW + pillGap);
            boolean isSel = this.cosmeticsCategoryFilter == i;
            boolean hov = MathUtil.a(mouseX, mouseY, pX, navY, pillW, navH);

            int pBg = isSel ? primaryAccent : (hov ? (isDark ? ColorUtil.a(36, 42, 58, (int) (220 * anim)) : ColorUtil.a(225, 230, 240, (int) (240 * anim))) :
                    (isDark ? ColorUtil.a(22, 26, 36, (int) (190 * anim)) : ColorUtil.a(238, 242, 248, (int) (210 * anim))));
            int pBrd = isSel ? primaryAccent : (isDark ? ColorUtil.a(255, 255, 255, (int) (14 * anim)) : 0);

            draw.a(matrices, pX, navY, pillW, navH, 3.5f, pBg);
            if (pBrd != 0) {
                draw.a(matrices, pX, navY, pillW, navH, 3.5f, 0.5f, pBrd);
            }
            int txtCol = isSel ? 0xFFFFFFFF : (hov ? titleColor : mutedColor);
            Fonts.b.b(matrices, COSMETIC_CAT_NAMES[i], pX + pillW * 0.5f, navY + 5.5f, 5.5f, txtCol);
        }

        // 3. CATEGORY SECTIONS WITH 3D PREVIEW CARDS
        float currentCatY = navY + navH + 10.0f;

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            String catTitle = switch (catKey) {
                case "hats" -> "Шляпы (Hats)";
                case "wings" -> "Крылья (Wings)";
                case "backpacks" -> "Рюкзаки (Backpacks)";
                case "masks" -> "Маски (Masks)";
                case "pets" -> "Питомцы (Pets)";
                case "swords" -> "Мечи (Swords)";
                default -> catKey;
            };

            List<SkycoreCosmeticsManager.Asset> assetsList = isSwords ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SkycoreCosmeticsManager.SwordEntry> swordsList = isSwords ? mgr.getSwords(searchQuery) : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : assetsList.size();

            // Category Header
            float catHeaderH = 22.0f;
            Fonts.d.a(matrices, catTitle, heroX + 2.0f, currentCatY + 4.0f, 8.5f, titleColor);

            // Equipped status or clear badge
            String selectedId = isSwords ? (cosmetics != null ? cosmetics.selectedSword() : "") : (cosmetics != null ? cosmetics.selected(catKey) : "");
            boolean hasEquipped = selectedId != null && !selectedId.isEmpty() && !"None".equalsIgnoreCase(selectedId);
            if (hasEquipped) {
                float badgeW = 46.0f;
                float badgeX = heroX + heroW - badgeW;
                float badgeY = currentCatY + 2.0f;
                boolean hovRst = MathUtil.a(mouseX, mouseY, badgeX, badgeY, badgeW, 14.0f);
                draw.a(matrices, badgeX, badgeY, badgeW, 14.0f, 3.0f, hovRst ? ColorUtil.a(239, 68, 68, (int) (180 * anim)) : ColorUtil.a(28, 34, 48, (int) (180 * anim)));
                draw.a(matrices, badgeX, badgeY, badgeW, 14.0f, 3.0f, 0.5f, hovRst ? 0xFFFF6B6B : cardBrd);
                Fonts.b.a(matrices, "Снять", badgeX + 5.0f, badgeY + 3.0f, 5.5f, hovRst ? 0xFFFFFFFF : mutedColor);
                drawCross(matrices, badgeX + badgeW - 9.0f, badgeY + 7.0f, 5.0f, 1.2f, hovRst ? 0xFFFFFFFF : 0xFFFF6B6B);
            }

            currentCatY += catHeaderH;

            // Empty state if nothing matches search
            if (itemCount == 0) {
                Fonts.b.a(matrices, "Ничего не найдено", heroX + 4.0f, currentCatY + 6.0f, 6.5f, mutedColor);
                currentCatY += 24.0f;
                continue;
            }

            int rows = (itemCount + cols - 1) / cols;

            // Render Cards Grid
            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = heroX + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                // Scissor culling check
                if (cardY + cardH < y || cardY > y + h) {
                    continue;
                }

                SkycoreCosmeticsManager.Asset asset = isSwords ? null : assetsList.get(i);
                SkycoreCosmeticsManager.SwordEntry sword = isSwords ? swordsList.get(i) : null;
                String itemKey = isSwords ? "sword:" + sword.name() : "cosmetic:" + asset.id();
                String displayName = isSwords ? sword.name() : asset.displayName();

                boolean isSelected = isSwords
                        ? (cosmetics != null && sword.name().equalsIgnoreCase(cosmetics.selectedSword()))
                        : (cosmetics != null && asset.id().equalsIgnoreCase(cosmetics.selected(catKey)));

                boolean isHovered = MathUtil.a(mouseX, mouseY, cardX, cardY, cardW, cardH);

                // Card Background & Border
                int cBg = isSelected
                        ? (isDark ? ColorUtil.a(26, 34, 52, (int) (235 * anim)) : ColorUtil.a(235, 242, 255, (int) (240 * anim)))
                        : (isHovered
                                ? (isDark ? ColorUtil.a(22, 27, 38, (int) (220 * anim)) : ColorUtil.a(238, 242, 248, (int) (230 * anim)))
                                : (isDark ? ColorUtil.a(16, 20, 28, (int) (210 * anim)) : ColorUtil.a(244, 247, 252, (int) (220 * anim))));

                int cBrd = isSelected
                        ? primaryAccent
                        : (isHovered
                                ? ColorUtil.a(primaryAccent, (int) (140 * anim))
                                : (isDark ? ColorUtil.a(255, 255, 255, (int) (14 * anim)) : ColorUtil.a(0, 0, 0, (int) (12 * anim))));

                draw.a(matrices, cardX, cardY, cardW, cardH, 6.0f, cBg);
                draw.a(matrices, cardX, cardY, cardW, cardH, 6.0f, 0.6f, cBrd);

                // Preview Box
                float pX = cardX + (cardW - previewSize) * 0.5f;
                float pY = cardY + 8.0f;

                if (isSwords) {
                    matrices.push();
                    float cx = pX + previewSize * 0.5f;
                    float cy = pY + previewSize * 0.5f;
                    float thumbSize = previewSize * 0.85f;
                    matrices.translate(cx, cy, 0.0f);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(32.0f));
                    float half = thumbSize * 0.5f;
                    int swordCol = ColorUtil.a(255, 255, 255, (int) (255 * anim));
                    draw.a(matrices, sword.texture(), -half, -half, thumbSize, thumbSize, 0.0f, swordCol);
                    matrices.pop();
                } else {
                    mgr.renderGuiPreview(matrices, asset.id(), pX, pY, previewSize, anim);
                }

                // Thin Divider Line
                draw.a(matrices, cardX + 6.0f, cardY + separatorYOffset, cardW - 12.0f, 0.5f, 0.0f, ColorUtil.a(255, 255, 255, (int) (14 * anim)));

                // Bottom: Display Name
                float maxTxtW = cardW - 36.0f;
                String truncatedName = displayName;
                if (Fonts.b.a(truncatedName, 6.0f) > maxTxtW) {
                    while (truncatedName.length() > 2 && Fonts.b.a(truncatedName + "..", 6.0f) > maxTxtW) {
                        truncatedName = truncatedName.substring(0, truncatedName.length() - 1);
                    }
                    truncatedName = truncatedName + "..";
                }
                int nameColor = isSelected ? primaryAccent : (isHovered ? titleColor : (isDark ? 0xFFE2E8F0 : 0xFF1E293B));
                Fonts.b.a(matrices, truncatedName, cardX + 7.0f, cardY + bottomYOffset + 2.5f, 6.0f, nameColor);

                // Bottom: Toggle Switch Pill (off: dark gray, on: bright green)
                float togW = 20.0f, togH = 11.0f;
                float togX = cardX + cardW - togW - 7.0f;
                float togY = cardY + bottomYOffset + 1.5f;
                float togAnim = getCosmeticToggleAnim(itemKey, isSelected);

                int offPillBg = isDark ? 0xFF232838 : 0xFFCBD5E1;
                int onPillBg = 0xFF22C55E; // Emerald green as in reference
                int pillBg = ColorUtil.a(mixColors(offPillBg, onPillBg, togAnim), (int) (255 * anim));
                draw.a(matrices, togX, togY, togW, togH, 5.5f, pillBg);

                float dotSz = 7.0f;
                float dotX = togX + 2.0f + togAnim * 9.0f;
                float dotY = togY + 2.0f;
                draw.a(matrices, dotX, dotY, dotSz, dotSz, 3.5f, 0xFFFFFFFF);
            }

            currentCatY += rows * (cardH + rowGap) + 12.0f;
        }

        ScissorUtil.a(matrices);

        // ==========================================
        // RIGHT PANE: COSMETICS PRESETS / CONFIGS
        // ==========================================
        float rightPaneX = x + mainW + 4.0f;
        float rightPaneY = y;
        float rightPaneH = h;
        CosmeticsConfigManager cfgMgr = CosmeticsConfigManager.getInstance();

        int rpBg = isDark ? ColorUtil.a(18, 22, 32, (int) (225 * anim)) : ColorUtil.a(240, 244, 250, (int) (230 * anim));
        int rpBrd = isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : ColorUtil.a(0, 0, 0, (int) (14 * anim));
        draw.a(matrices, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, rpBg);
        draw.a(matrices, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, 0.6f, rpBrd);

        float rpPad = 8.0f;
        float rpContentX = rightPaneX + rpPad;
        float rpContentW = rightPaneW - rpPad * 2.0f;
        float rTopY = rightPaneY + 10.0f;

        Fonts.d.a(matrices, "Конфиги", rpContentX, rTopY, 7.5f, titleColor);
        Fonts.b.a(matrices, "Сохраненные сеты", rpContentX, rTopY + 11.0f, 5.5f, mutedColor);

        // Name Input & Save Button
        float inputY = rTopY + 24.0f;
        float saveBtnW = 46.0f;
        float inputH = 15.0f;
        float inputW = rpContentW - saveBtnW - 4.0f;

        int inputBg = isDark ? ColorUtil.a(24, 28, 38, (int) (240 * anim)) : ColorUtil.a(240, 244, 250, (int) (240 * anim));
        int inputBorder = this.cosmeticsPresetInputFocused ? primaryAccent : ColorUtil.a(255, 255, 255, (int) (22 * anim));
        draw.a(matrices, rpContentX, inputY, inputW, inputH, 3.5f, inputBg);
        draw.a(matrices, rpContentX, inputY, inputW, inputH, 3.5f, 0.6f, inputBorder);

        String showInputText = this.newCosmeticsPresetName.isEmpty() ? (this.cosmeticsPresetInputFocused ? "" : "Имя сета...") : this.newCosmeticsPresetName;
        int inputTxtCol = this.newCosmeticsPresetName.isEmpty() ? mutedColor : 0xFFFFFFFF;
        ScissorUtil.a(matrices, rpContentX + 3.0f, inputY, inputW - 6.0f, inputH);
        Fonts.b.a(matrices, showInputText, rpContentX + 4.0f, inputY + 3.0f, 6.0f, inputTxtCol);
        if (this.cosmeticsPresetInputFocused && (System.currentTimeMillis() / 450) % 2 == 0) {
            float curCaretX = rpContentX + 4.0f + Fonts.b.a(this.newCosmeticsPresetName, 6.0f);
            draw.a(matrices, curCaretX, inputY + 2.5f, 1.0f, 10.0f, 0.0f, primaryAccent);
        }
        ScissorUtil.a(matrices);

        // Save Button
        float saveBtnX = rpContentX + inputW + 4.0f;
        boolean hovSave = MathUtil.a(mouseX, mouseY, saveBtnX, inputY, saveBtnW, inputH);
        draw.a(matrices, saveBtnX, inputY, saveBtnW, inputH, 3.5f, hovSave ? primaryAccent : ColorUtil.a(35, 42, 58, (int) (240 * anim)));
        Fonts.b.b(matrices, "+ Сохр.", saveBtnX + saveBtnW * 0.5f, inputY + 3.0f, 5.5f, 0xFFFFFFFF);

        // Presets List Cards
        float listY = inputY + inputH + 8.0f;
        float pCardH = 30.0f;
        float cardGap = 4.0f;
        float listH = rightPaneH - (listY - rightPaneY) - 8.0f;
        java.util.List<CosmeticsConfigManager.CosmeticPreset> presetsList = cfgMgr.getPresets();

        float totalPresetsH = presetsList.isEmpty() ? 45.0f : (presetsList.size() * pCardH + Math.max(0, presetsList.size() - 1) * cardGap);
        this.maxCosmeticsPresetsScrollY = Math.max(0.0f, totalPresetsH - listH);
        this.cosmeticsPresetsScrollY = MathUtil.c(this.cosmeticsPresetsScrollY, this.targetCosmeticsPresetsScrollY, 14.0f);
        if (Math.abs(this.cosmeticsPresetsScrollY - this.targetCosmeticsPresetsScrollY) < 0.2f) {
            this.cosmeticsPresetsScrollY = this.targetCosmeticsPresetsScrollY;
        }

        ScissorUtil.a(matrices, rpContentX - 2.0f, listY - 1.0f, rpContentW + 4.0f, listH + 2.0f);

        if (presetsList.isEmpty()) {
            draw.a(matrices, rpContentX, listY, rpContentW, 45.0f, 4.0f, ColorUtil.a(22, 26, 36, (int) (160 * anim)));
            Fonts.b.b(matrices, "Нет сетов", rpContentX + rpContentW * 0.5f, listY + 14.0f, 6.0f, mutedColor);
            Fonts.b.b(matrices, "Сохраните текущий", rpContentX + rpContentW * 0.5f, listY + 26.0f, 5.0f, mutedColor);
        } else {
            for (int i = 0; i < presetsList.size(); i++) {
                CosmeticsConfigManager.CosmeticPreset preset = presetsList.get(i);
                float sY = listY + i * (pCardH + cardGap) - this.cosmeticsPresetsScrollY;
                if (sY + pCardH < listY - 5.0f || sY > listY + listH + 5.0f) {
                    continue;
                }
                boolean isCurrent = preset.name.equalsIgnoreCase(cfgMgr.getCurrentPresetName());

                float delBtnSz = 14.0f;
                float delBtnX = rpContentX + rpContentW - delBtnSz - 4.0f;
                float delBtnY = sY + (pCardH - delBtnSz) * 0.5f;
                boolean hovDel = MathUtil.a(mouseX, mouseY, delBtnX - 1.0f, delBtnY - 1.0f, delBtnSz + 2.0f, delBtnSz + 2.0f);
                boolean hovCard = MathUtil.a(mouseX, mouseY, rpContentX, sY, rpContentW - (delBtnSz + 8.0f), pCardH);

                int pCardBg = isDark ? ColorUtil.a(24, 28, 40, (int) (230 * anim)) : ColorUtil.a(244, 246, 250, (int) (240 * anim));
                draw.a(matrices, rpContentX, sY, rpContentW, pCardH, 3.5f, pCardBg);
                if (isCurrent) {
                    draw.a(matrices, rpContentX, sY, rpContentW, pCardH, 3.5f, 0.8f, primaryAccent);
                } else if (hovCard) {
                    draw.a(matrices, rpContentX, sY, rpContentW, pCardH, 3.5f, 0.6f, ColorUtil.a(255, 255, 255, (int) (30 * anim)));
                }

                String pName = preset.name;
                float maxNameW = rpContentW - delBtnSz - 14.0f;
                if (Fonts.b.a(pName, 6.0f) > maxNameW) {
                    while (pName.length() > 2 && Fonts.b.a(pName + "..", 6.0f) > maxNameW) {
                        pName = pName.substring(0, pName.length() - 1);
                    }
                    pName += "..";
                }
                Fonts.b.a(matrices, pName, rpContentX + 6.0f, sY + 5.0f, 6.0f, isCurrent ? primaryAccent : titleColor);

                int eqCount = preset.getEquippedCount();
                String countText = eqCount == 0 ? "Пустой" : (eqCount + " предм.");
                Fonts.b.a(matrices, countText, rpContentX + 6.0f, sY + 16.5f, 5.0f, mutedColor);

                draw.a(matrices, delBtnX, delBtnY, delBtnSz, delBtnSz, 2.5f, hovDel ? 0xDDFF4444 : ColorUtil.a(255, 255, 255, (int) (16 * anim)));
                drawCross(matrices, delBtnX + delBtnSz * 0.5f, delBtnY + delBtnSz * 0.5f, 5.0f, 1.0f, hovDel ? 0xFFFFFFFF : mutedColor);
            }
        }

        ScissorUtil.a(matrices);
    }

    private boolean handleCosmeticsClicks(double mouseX, double mouseY, float x, float y, float w, float h, int button) {
        if (button != 0) return false;
        CosmeticsModule cosmetics = CosmeticsModule.getInstance();
        if (cosmetics == null) {
            cosmetics = (CosmeticsModule) Delta.h().d().t().e().stream().filter(m -> m instanceof CosmeticsModule).findFirst().orElse(null);
            if (cosmetics == null) return false;
        }

        SkycoreCosmeticsManager mgr = SkycoreCosmeticsManager.getInstance();
        mgr.ensureIndex();

        float heroH = 46.0f;
        float navH = 20.0f;
        float rightPaneW = 155.0f;
        float mainW = w - rightPaneW - 8.0f;
        float rightPaneX = x + mainW + 4.0f;
        float rightPaneY = y;
        float rightPaneH = h;
        CosmeticsConfigManager cfgMgr = CosmeticsConfigManager.getInstance();

        if (MathUtil.a(mouseX, mouseY, rightPaneX, rightPaneY, rightPaneW, rightPaneH)) {
            float rpPad = 8.0f;
            float rpContentX = rightPaneX + rpPad;
            float rpContentW = rightPaneW - rpPad * 2.0f;
            float rTopY = rightPaneY + 10.0f;
            float inputY = rTopY + 24.0f;
            float saveBtnW = 46.0f;
            float inputH = 15.0f;
            float inputW = rpContentW - saveBtnW - 4.0f;

            if (MathUtil.a(mouseX, mouseY, rpContentX, inputY, inputW, inputH)) {
                this.cosmeticsPresetInputFocused = true;
                return true;
            } else {
                this.cosmeticsPresetInputFocused = false;
            }

            float saveBtnX = rpContentX + inputW + 4.0f;
            if (MathUtil.a(mouseX, mouseY, saveBtnX, inputY, saveBtnW, inputH)) {
                cfgMgr.saveCurrentAsPreset(this.newCosmeticsPresetName);
                this.newCosmeticsPresetName = "";
                this.cosmeticsPresetInputFocused = false;
                return true;
            }

            float listY = inputY + inputH + 8.0f;
            float cardH = 30.0f;
            float cardGap = 4.0f;
            float listH = rightPaneH - (listY - rightPaneY) - 8.0f;
            java.util.List<CosmeticsConfigManager.CosmeticPreset> presetsList = cfgMgr.getPresets();

            for (int i = 0; i < presetsList.size(); i++) {
                CosmeticsConfigManager.CosmeticPreset preset = presetsList.get(i);
                float sY = listY + i * (cardH + cardGap) - this.cosmeticsPresetsScrollY;
                if (sY + cardH < listY || sY > listY + listH) continue;

                float delBtnSz = 14.0f;
                float delBtnX = rpContentX + rpContentW - delBtnSz - 4.0f;
                float delBtnY = sY + (cardH - delBtnSz) * 0.5f;

                if (MathUtil.a(mouseX, mouseY, delBtnX - 1.0f, delBtnY - 1.0f, delBtnSz + 2.0f, delBtnSz + 2.0f)) {
                    cfgMgr.deletePreset(preset);
                    return true;
                }

                if (MathUtil.a(mouseX, mouseY, rpContentX, sY, rpContentW - (delBtnSz + 8.0f), cardH)) {
                    cfgMgr.applyPreset(preset);
                    return true;
                }
            }

            return true;
        }

        this.cosmeticsPresetInputFocused = false;

        float heroX = x + 4.0f;
        float heroW = mainW - 8.0f;
        float curY = y - this.cosmeticsScrollY;

        // Master Toggle Switch Slider (Ползунок)
        float masterTogW = 24.0f, masterTogH = 13.0f;
        float masterTogX = heroX + heroW - masterTogW - 10.0f;
        float masterTogY = curY + (heroH - masterTogH) * 0.5f;
        if (MathUtil.a(mouseX, mouseY, masterTogX - 3.0f, masterTogY - 3.0f, masterTogW + 6.0f, masterTogH + 6.0f)) {
            cosmetics.a();
            return true;
        }

        // "Снять все" Button
        float clrW = 48.0f, clrH = 15.0f;
        float clrX = masterTogX - clrW - 8.0f;
        float clrY = curY + (heroH - clrH) * 0.5f;
        if (MathUtil.a(mouseX, mouseY, clrX, clrY, clrW, clrH)) {
            cosmetics.clearAll();
            return true;
        }

        // Category Filter Pills
        float navY = curY + heroH + 8.0f;
        float pillGap = 3.5f;
        float pillW = (heroW - (COSMETIC_CAT_NAMES.length - 1) * pillGap) / COSMETIC_CAT_NAMES.length;

        for (int i = 0; i < COSMETIC_CAT_NAMES.length; i++) {
            float pX = heroX + i * (pillW + pillGap);
            if (MathUtil.a(mouseX, mouseY, pX, navY, pillW, navH)) {
                this.cosmeticsCategoryFilter = i;
                this.targetCosmeticsScrollY = 0.0f;
                return true;
            }
        }

        // Active categories
        List<String> activeCats = new ArrayList<>();
        if (this.cosmeticsCategoryFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("swords");
        } else if (this.cosmeticsCategoryFilter < COSMETIC_CAT_KEYS.length) {
            activeCats.add(COSMETIC_CAT_KEYS[this.cosmeticsCategoryFilter]);
        }

        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (heroW - (cols - 1) * colGap) / cols;
        float cardH = 136.0f;
        float currentCatY = navY + navH + 10.0f;

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            List<SkycoreCosmeticsManager.Asset> assetsList = isSwords ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SkycoreCosmeticsManager.SwordEntry> swordsList = isSwords ? mgr.getSwords(searchQuery) : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : assetsList.size();

            // Category quick reset badge click
            String selectedId = isSwords ? cosmetics.selectedSword() : cosmetics.selected(catKey);
            boolean hasEquipped = selectedId != null && !selectedId.isEmpty() && !"None".equalsIgnoreCase(selectedId);
            if (hasEquipped) {
                float badgeW = 44.0f;
                float badgeX = heroX + heroW - badgeW;
                float badgeY = currentCatY + 2.0f;
                if (MathUtil.a(mouseX, mouseY, badgeX, badgeY, badgeW, 14.0f)) {
                    if (isSwords) {
                        cosmetics.selectSword("", "");
                    } else {
                        cosmetics.select(catKey, "");
                    }
                    return true;
                }
            }

            currentCatY += 22.0f;

            if (itemCount == 0) {
                currentCatY += 24.0f;
                continue;
            }

            int rows = (itemCount + cols - 1) / cols;

            // Card clicks
            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = heroX + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                if (MathUtil.a(mouseX, mouseY, cardX, cardY, cardW, cardH)) {
                    if (isSwords) {
                        SkycoreCosmeticsManager.SwordEntry sword = swordsList.get(i);
                        if (sword.name().equalsIgnoreCase(cosmetics.selectedSword())) {
                            cosmetics.selectSword("", "");
                        } else {
                            cosmetics.selectSword(sword.name(), sword.model());
                            if (!cosmetics.isEnabled()) {
                                cosmetics.a(true);
                            }
                        }
                    } else {
                        SkycoreCosmeticsManager.Asset asset = assetsList.get(i);
                        if (asset.id().equalsIgnoreCase(cosmetics.selected(catKey))) {
                            cosmetics.select(catKey, "");
                        } else {
                            cosmetics.select(catKey, asset.id());
                            if (!cosmetics.isEnabled()) {
                                cosmetics.a(true);
                            }
                        }
                    }
                    return true;
                }
            }

            currentCatY += rows * (cardH + rowGap) + 12.0f;
        }

        return false;
    }

    private void renderModulesView(MatrixStack matrices, double mouseX, double mouseY, float x, float y, float w, float h, float delta, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {

        List<Module> modules = getFilteredModules();

        if (modules.isEmpty()) {

            Fonts.b.b(matrices, "No modules found.", x + w * 0.5f, y + h * 0.5f - 6.0f, 8.5f, mutedColor);

            return;

        }

        // Masonry 2-column layout with dynamically animated card heights

        float colW = (w - 8.0f) * 0.5f;

        float col1X = x;

        float col2X = x + colW + 8.0f;

        float col1H = 0.0f;

        float col2H = 0.0f;

        for (Module m : modules) {

            float targetH = calculateCardHeight(m, colW);

            float curH = this.cardHeightAnims.computeIfAbsent(m, k -> targetH);

            curH = MathUtil.c(curH, targetH, 14.0f);

            this.cardHeightAnims.put(m, curH);

            if (col1H <= col2H) {

                col1H += curH + 8.0f;

            } else {

                col2H += curH + 8.0f;

            }

        }

        float totalH = Math.max(col1H, col2H);

        this.maxScrollY = Math.max(0.0f, totalH - h);

        // Scissor clip content area for scrolling

        ScissorUtil.a(matrices, x - 2.0f, y, w + 4.0f, h);

        float curCol1Y = y - this.scrollY;
        float curCol2Y = y - this.scrollY;

        long now = System.currentTimeMillis();
        long timeSinceOpen = Math.min(now - this.openStartTime, now - this.tabSwitchTime);
        if (timeSinceOpen < 0L) timeSinceOpen = 0L;

        int cardIdx = 0;
        for (Module m : modules) {
            float cardH = this.cardHeightAnims.getOrDefault(m, calculateCardHeight(m, colW));
            float cardX, cardY;

            if (curCol1Y <= curCol2Y) {
                cardX = col1X;
                cardY = curCol1Y;
                curCol1Y += cardH + 8.0f;
            } else {
                cardX = col2X;
                cardY = curCol2Y;
                curCol2Y += cardH + 8.0f;
            }

            // Top-to-bottom wave cascade: staggered delay based on card row
            int rowIdx = cardIdx / 2;
            float waveDelayMs = rowIdx * 28.0f;
            float cardTime = Math.max(0.0f, (float) timeSinceOpen - waveDelayMs);
            float cardProgress = Math.min(1.0f, cardTime / 260.0f);
            float cardEase = (cardProgress >= 1.0f) ? 1.0f : (1.0f - (float) Math.pow(1.0f - cardProgress, 4.0));
            float cardFade = anim * cardEase;
            float cardOffsetY = (1.0f - cardEase) * 12.0f;
            float cardScale = (cardProgress >= 1.0f) ? 1.0f : (0.95f + 0.05f * cardEase);

            cardIdx++;

            // Only draw visible cards within scissor viewport
            if (cardY + cardH >= y - 10.0f && cardY <= y + h + 10.0f) {
                if (cardFade > 0.01f) {
                    matrices.push();
                    float cCenterX = cardX + colW * 0.5f;
                    float cCenterY = cardY + cardH * 0.5f;
                    matrices.translate(cCenterX, cCenterY + cardOffsetY, 0.0f);
                    if (cardScale != 1.0f) {
                        matrices.scale(cardScale, cardScale, 1.0f);
                    }
                    matrices.translate(-cCenterX, -cCenterY, 0.0f);
                    renderModuleCard(matrices, m, mouseX, mouseY, cardX, cardY, colW, cardH, delta, cardFade, isDark, primaryAccent, titleColor, mutedColor, border);
                    matrices.pop();
                }
            }
        }

        ScissorUtil.a(matrices);

    }

    private float calculateCardHeight(Module m, float colW) {

        float h = 30.0f; // Header with title, desc and toggle switch

        List<Setting<?>> settings = m.e();

        if (settings != null && !settings.isEmpty()) {

            float availW = colW - 16.0f;

            for (Setting<?> s : settings) {

                if (s.e() != null && !s.e().get()) {

                    continue; // Skip hidden conditional settings

                }

                if (s instanceof BooleanSetting) {

                    h += 15.0f;

                } else if (s instanceof SliderSetting) {

                    h += 22.0f;

                } else if (s instanceof ColorSetting) {

                    h += 16.0f;

                } else if (s instanceof ModeSetting) {

                    ModeSetting ms = (ModeSetting) s;

                    h += calculateModeSettingHeight(ms, availW);

                } else if (s instanceof MultiModeSetting) {

                    MultiModeSetting mms = (MultiModeSetting) s;

                    h += calculateMultiModeHeight(mms, availW);

                } else if (s instanceof BindSetting) {

                    h += 16.0f;

                } else {

                    h += 16.0f;

                }

            }

            h += 4.0f; // bottom padding

        }

        return h;

    }

    private float calculateMultiModeHeight(MultiModeSetting mms, float availW) {

        float rowX = 0.0f;

        int rowCount = 1;

        float chipGap = 3.5f;

        for (BooleanSetting sub : mms.c()) {

            float chipW = Fonts.b.a(sub.i(), 6.5f) + 8.0f;

            if (rowX + chipW > availW && rowX > 0.0f) {

                rowCount++;

                rowX = chipW + chipGap;

            } else {

                rowX += chipW + chipGap;

            }

        }

        return 16.0f + rowCount * (11.5f + 3.5f);

    }

    private float calculateModeSettingHeight(ModeSetting ms, float availW) {

        List<String> modes = ms.k();

        float totalW = 0.0f;

        for (String str : modes) {

            totalW += Fonts.b.a(str, 6.5f) + 8.0f + 3.0f;

        }

        if (modes.size() <= 4 && totalW <= availW + 10.0f) {

            return 28.0f;

        } else if (modes.size() <= 6) {

            float rowX = 0.0f;

            int rowCount = 1;

            for (String str : modes) {

                float bW = Fonts.b.a(str, 6.5f) + 8.0f;

                if (rowX + bW > availW && rowX > 0.0f) {

                    rowCount++;

                    rowX = bW + 3.5f;

                } else {

                    rowX += bW + 3.5f;

                }

            }

            return 16.0f + rowCount * (11.5f + 3.5f);

        } else {

            return 17.0f;

        }

    }

    private void renderModuleCard(MatrixStack matrices, Module m, double mouseX, double mouseY, float x, float y, float w, float h, float delta, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {

        Draw2DProcessor draw = Delta.h().d().i();

        float hAnim = this.cardHoverAnims.computeIfAbsent(m, k -> 0.0f);
        boolean isCardHovered = MathUtil.a(mouseX, mouseY, x, y, w, h);
        hAnim = MathUtil.c(hAnim, isCardHovered ? 1.0f : 0.0f, 14.0f);
        this.cardHoverAnims.put(m, hAnim);

        int cardBg = isDark ?
            ColorUtil.a(24 + (int) (6 * hAnim), 27 + (int) (7 * hAnim), 36 + (int) (10 * hAnim), (int) (245 * anim)) :
            ColorUtil.a(255, 255, 255, (int) (245 * anim));

        int cardBorder = isDark ? ColorUtil.a(255, 255, 255, (int) (20 * anim)) : ColorUtil.a(226, 232, 240, (int) (255 * anim));
        int blendedBorder = ColorUtil.a(cardBorder, primaryAccent, hAnim * 0.40f);

        // Soft ambient glow behind card on hover
        if (hAnim > 0.01f) {
            draw.a(matrices, x - 1.0f, y - 1.0f, w + 2.0f, h + 2.0f, 7.0f, 0, 1.0f, ColorUtil.a(primaryAccent, (int) (38 * hAnim * anim)), 8.0f * hAnim);
        }

        // Crisp solid frosted card container with soft ambient shadow
        if (!isDark) {
            draw.a(matrices, x, y, w, h, 6.0f, cardBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (22 * anim)), 6.0f);
        } else {
            draw.a(matrices, x, y, w, h, 6.0f, cardBg, 1.0f, ColorUtil.a(0, 0, 0, (int) ((60 + 20 * hAnim) * anim)), 8.0f);
        }

        draw.a(matrices, x, y, w, h, 6.0f, 0.6f + 0.35f * hAnim, blendedBorder);

        // Active indicator on left side
        float actAnim = this.switchAnims.computeIfAbsent(m, k -> m.m() ? 1.0f : 0.0f);
        if (actAnim > 0.01f) {
            float indH = (h - 12.0f) * actAnim;
            float indY = y + (h - indH) * 0.5f;
            draw.a(matrices, x + 1.5f, indY, 2.0f, indH, 1.0f, ColorUtil.a(primaryAccent, (int) (240 * actAnim * anim)));
        }

        // --- Card Header: Name + Description ---

        float headerY = y + 7.0f;

        int titleCol = ColorUtil.a(titleColor, isDark ? 0xFFFFFFFF : primaryAccent, hAnim * 0.45f);

        Fonts.d.a(matrices, m.j(), x + 8.0f, headerY, 8.5f, titleCol);

        String desc = (m.k() != null && !m.k().isEmpty()) ? m.k() : "Module description.";

        float maxDescW = w - 46.0f;

        String trimmedDesc = desc;

        while (trimmedDesc.length() > 3 && Fonts.b.a(trimmedDesc, 6.5f) > maxDescW) {

            trimmedDesc = trimmedDesc.substring(0, trimmedDesc.length() - 1);

        }

        if (!trimmedDesc.equals(desc)) {

            trimmedDesc = trimmedDesc.substring(0, Math.max(1, trimmedDesc.length() - 1)) + "..";

        }

        Fonts.b.a(matrices, trimmedDesc, x + 8.0f, headerY + 11.0f, 6.5f, mutedColor);

        // --- iOS-style Toggle Switch ---

        float swW = 18.0f, swH = 10.0f;

        float swX = x + w - swW - 8.0f;

        float swY = y + 8.0f;

        float swAnim = this.switchAnims.computeIfAbsent(m, k -> m.m() ? 1.0f : 0.0f);

        swAnim = MathUtil.c(swAnim, m.m() ? 1.0f : 0.0f, 16.0f);

        this.switchAnims.put(m, swAnim);

        int swOffColor = isDark ? ColorUtil.a(55, 65, 81, (int) (255 * anim)) : ColorUtil.a(209, 213, 219, (int) (255 * anim));

        int swTrackColor = ColorUtil.a(swOffColor, primaryAccent, swAnim);

        draw.a(matrices, swX, swY, swW, swH, swH * 0.5f, swTrackColor);

        float thumbRadius = (swH - 2.5f) * 0.5f;

        float thumbX = swX + 1.25f + (swW - swH) * swAnim + thumbRadius;

        float thumbY = swY + swH * 0.5f;

        draw.a(matrices, thumbX - thumbRadius, thumbY - thumbRadius, thumbRadius * 2.0f, thumbRadius * 2.0f, thumbRadius, 0xFFFFFFFF);

        // --- Keybind badge next to switch ---

        boolean isListening = this.bindingTarget == m;

        String bindText = isListening ? "..." : (m.p() != -1 ? KeyUtil.b(m.p()) : "none");

        float bindBadgeW = Fonts.b.a(bindText, 6.5f) + 8.0f;

        float bindBadgeH = 11.0f;

        float bindBadgeX = swX - bindBadgeW - 6.0f;

        float bindBadgeY = y + 7.5f;

        int bindBadgeBg = isListening ? ColorUtil.a(primaryAccent, (int) (65 * anim)) : (isDark ? ColorUtil.a(36, 40, 52, (int) (220 * anim)) : ColorUtil.a(235, 240, 248, (int) (220 * anim)));

        int bindBadgeBorder = isListening ? primaryAccent : (isDark ? ColorUtil.a(255, 255, 255, (int) (18 * anim)) : ColorUtil.a(210, 218, 230, (int) (220 * anim)));

        int bindBadgeText = isListening ? primaryAccent : (m.p() != -1 ? titleColor : mutedColor);

        draw.a(matrices, bindBadgeX, bindBadgeY, bindBadgeW, bindBadgeH, 3.0f, bindBadgeBg);

        draw.a(matrices, bindBadgeX, bindBadgeY, bindBadgeW, bindBadgeH, 3.0f, 0.5f, bindBadgeBorder);

        Fonts.b.b(matrices, bindText, bindBadgeX + bindBadgeW * 0.5f, bindBadgeY + 1.25f, 6.5f, bindBadgeText);

        // --- Settings inside Card ---

        float curSettingY = y + 29.0f;

        List<Setting<?>> settings = m.e();

        if (settings != null && !settings.isEmpty()) {

            draw.a(matrices, x + 6.0f, curSettingY - 2.0f, w - 12.0f, 0.5f, 0.0f, cardBorder);

            curSettingY += 2.0f;

            for (Setting<?> s : settings) {

                if (s.e() != null && !s.e().get()) {

                    continue; // Condition not met (e.g. only appears when "Ромб" is chosen!)

                }

                if (s instanceof BooleanSetting) {

                    BooleanSetting bs = (BooleanSetting) s;

                    renderCheckbox(matrices, bs, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                    curSettingY += 15.0f;

                } else if (s instanceof SliderSetting) {

                    SliderSetting ss = (SliderSetting) s;

                    renderSlider(matrices, ss, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                    curSettingY += 22.0f;

                } else if (s instanceof ColorSetting) {

                    ColorSetting cs = (ColorSetting) s;

                    renderColorSettingRow(matrices, cs, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                    curSettingY += 16.0f;

                } else if (s instanceof ModeSetting) {

                    ModeSetting ms = (ModeSetting) s;

                    curSettingY += renderModeSetting(matrices, ms, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                } else if (s instanceof MultiModeSetting) {

                    MultiModeSetting mms = (MultiModeSetting) s;

                    curSettingY += renderMultiMode(matrices, mms, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                } else if (s instanceof BindSetting) {

                    BindSetting bnds = (BindSetting) s;

                    renderKeybind(matrices, bnds, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                    curSettingY += 16.0f;

                } else {

                    renderGenericSetting(matrices, s, mouseX, mouseY, x + 8.0f, curSettingY, w - 16.0f, anim, isDark, primaryAccent, titleColor, mutedColor);

                    curSettingY += 16.0f;

                }

            }

        }

    }

    private void renderCheckbox(MatrixStack matrices, BooleanSetting bs, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        Fonts.b.a(matrices, bs.i(), x, y + 2.5f, 7.5f, titleColor);

        float cbSize = 9.0f;

        float cbX = x + w - cbSize;

        float cbY = y + 2.0f;

        boolean checked = bs.c();

        float cbAnim = this.checkboxAnims.computeIfAbsent(bs, k -> checked ? 1.0f : 0.0f);
        cbAnim = MathUtil.c(cbAnim, checked ? 1.0f : 0.0f, 15.0f);
        this.checkboxAnims.put(bs, cbAnim);

        int ringColor = isDark ? ColorUtil.a(75, 85, 99, (int) (255 * anim)) : ColorUtil.a(156, 163, 175, (int) (255 * anim));
        int activeFill = ColorUtil.a(primaryAccent, (int) (cbAnim * anim * 255.0f));
        int borderCol = ColorUtil.a(ringColor, primaryAccent, cbAnim);

        if (cbAnim > 0.01f) {
            draw.a(matrices, cbX, cbY, cbSize, cbSize, cbSize * 0.5f, activeFill);
            float dotR = 1.5f * cbAnim;
            if (dotR > 0.1f) {
                draw.a(matrices, cbX + cbSize * 0.5f - dotR, cbY + cbSize * 0.5f - dotR, dotR * 2.0f, dotR * 2.0f, dotR, ColorUtil.a(0xFFFFFFFF, (int) (cbAnim * anim * 255.0f)));
            }
        }

        draw.a(matrices, cbX, cbY, cbSize, cbSize, cbSize * 0.5f, 0.8f, borderCol);

    }

    // --- ANIMATED SLIDERS WITH PERFECT CENTERING ---

    private void renderSlider(MatrixStack matrices, SliderSetting ss, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        // Label on left

        Fonts.b.a(matrices, ss.i(), x, y + 1.0f, 7.5f, titleColor);

        // Value badge pill on right

        String valStr = String.format(Locale.ROOT, ss.c % 1.0f == 0.0f ? "%.0f" : "%.1f", ss.c());

        float badgeH = 10.0f;

        float badgeW = Math.max(18.0f, Fonts.b.a(valStr, 6.5f) + 8.0f);

        float badgeX = x + w - badgeW;

        float badgeY = y;

        int badgeBg = isDark ? ColorUtil.a(primaryAccent, 0.25f * anim) : ColorUtil.a(primaryAccent, 0.15f * anim);

        draw.a(matrices, badgeX, badgeY, badgeW, badgeH, 3.0f, badgeBg);

        draw.a(matrices, badgeX, badgeY, badgeW, badgeH, 3.0f, 0.5f, primaryAccent);

        // Perfectly centered number horizontally and vertically

        float textY = badgeY + 0.75f;

        Fonts.b.b(matrices, valStr, badgeX + badgeW * 0.5f, textY, 6.5f, primaryAccent);

        // Slider horizontal track bar

        float trackY = y + 13.5f;

        float trackH = 3.0f;

        int trackBg = isDark ? ColorUtil.a(40, 44, 56, (int) (255 * anim)) : ColorUtil.a(220, 226, 235, (int) (255 * anim));

        draw.a(matrices, x, trackY, w, trackH, 1.5f, trackBg);

        // SMOOTH PROGRESS ANIMATION:

        float targetProgress = Math.min(1.0f, Math.max(0.0f, (ss.c() - ss.a) / (ss.b - ss.a)));

        float curProgress = this.sliderAnimatedProgress.computeIfAbsent(ss, k -> targetProgress);

        // Fluid glide over ~100-150ms

        curProgress = curProgress + (targetProgress - curProgress) * 0.22f;

        if (Math.abs(curProgress - targetProgress) < 0.001f) {

            curProgress = targetProgress;

        }

        this.sliderAnimatedProgress.put(ss, curProgress);

        float fillW = w * curProgress;

        if (fillW > 0.0f) {

            draw.a(matrices, x, trackY, fillW, trackH, 1.5f, primaryAccent);

        }

        // SMOOTH THUMB SCALE ANIMATION (hover/drag pulse):

        boolean isHovered = MathUtil.a(mouseX, mouseY, x - 2.0f, trackY - 5.0f, w + 4.0f, 13.0f) || this.draggingSlider == ss;

        float targetScale = (this.draggingSlider == ss) ? 1.45f : (isHovered ? 1.25f : 1.0f);

        float curScale = this.thumbScaleAnims.computeIfAbsent(ss, k -> 1.0f);

        curScale = curScale + (targetScale - curScale) * 0.25f;

        this.thumbScaleAnims.put(ss, curScale);

        float thumbRadius = 3.5f * curScale;

        float thumbX = x + fillW;

        float thumbY = trackY + trackH * 0.5f;

        draw.a(matrices, thumbX - thumbRadius, thumbY - thumbRadius, thumbRadius * 2.0f, thumbRadius * 2.0f, thumbRadius, 0xFFFFFFFF);

        draw.a(matrices, thumbX - thumbRadius, thumbY - thumbRadius, thumbRadius * 2.0f, thumbRadius * 2.0f, thumbRadius, 0.75f, primaryAccent);

    }

    // --- COLOR PICKER ROW ---

    private void renderColorSettingRow(MatrixStack matrices, ColorSetting cs, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        Fonts.b.a(matrices, cs.i(), x, y + 2.0f, 7.5f, titleColor);

        float swatchW = 22.0f;

        float swatchH = 10.5f;

        float swatchX = x + w - swatchW;

        float swatchY = y + 1.5f;

        int color = cs.c();

        boolean isOpen = this.activeColorPicker == cs;

        draw.a(matrices, swatchX, swatchY, swatchW, swatchH, 3.5f, color);

        draw.a(matrices, swatchX, swatchY, swatchW, swatchH, 3.5f, 0.6f, isOpen ? primaryAccent : (isDark ? ColorUtil.a(255, 255, 255, (int) (40 * anim)) : ColorUtil.a(0, 0, 0, (int) (30 * anim))));

    }

    private void renderColorPickerPopover(MatrixStack matrices, DrawContext context, double mouseX, double mouseY, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor, int border) {

        Draw2DProcessor draw = Delta.h().d().i();

        ColorSetting cs = this.activeColorPicker;

        if (cs == null) return;

        float popW = 92.0f;

        float popH = 82.0f;

        float px = this.colorPickerX;

        float py = this.colorPickerY;

        int popBg = isDark ? ColorUtil.a(20, 23, 31, (int) (245 * anim)) : ColorUtil.a(255, 255, 255, (int) (255 * anim));

        draw.a(matrices, px, py, popW, popH, 6.0f, popBg, 1.0f, ColorUtil.a(0, 0, 0, (int) (130 * anim)), 16.0f);

        draw.a(matrices, px, py, popW, popH, 6.0f, 0.8f, isDark ? ColorUtil.a(primaryAccent, 0.6f * anim) : border);

        // Saturation/Value 2D Area

        float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;

        int hueRgb = Color.HSBtoRGB(this.cpCurHue, 1.0f, 1.0f);

        draw.a(matrices, svX, svY, svW, svH, 2.5f, ColorUtil.a(0xFFFFFFFF, anim), ColorUtil.a(hueRgb, anim), ColorUtil.a(0xFF000000, anim), ColorUtil.a(0xFF000000, anim));

        draw.a(matrices, svX, svY, svW, svH, 2.5f, 0.5f, border);

        float curX = svX + this.cpCurSat * svW;

        float curY = svY + (1.0f - this.cpCurBri) * svH;

        draw.a(matrices, curX - 2.0f, curY - 2.0f, 4.0f, 4.0f, 2.0f, 0xFFFFFFFF);

        draw.a(matrices, curX - 2.0f, curY - 2.0f, 4.0f, 4.0f, 2.0f, 0.5f, 0xFF000000);

        // Hue strip

        float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;

        draw.a(matrices, COLOR_PICKER_TEX, hX, hY, hW, hH, 2.0f, ColorUtil.a(0xFFFFFFFF, anim));

        draw.a(matrices, hX, hY, hW, hH, 2.0f, 0.5f, border);

        float hCursorY = hY + this.cpCurHue * hH;

        draw.a(matrices, hX - 0.5f, hCursorY - 1.0f, hW + 1.0f, 2.0f, 0.5f, 0xFFFFFFFF);

        // Alpha strip

        float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;

        int currentRgb = Color.HSBtoRGB(this.cpCurHue, this.cpCurSat, this.cpCurBri);

        draw.a(matrices, OPACITY_TEX, aX, aY, aW, aH, 2.0f, ColorUtil.a(0xFFFFFFFF, 0.15f * anim));

        draw.a(matrices, aX, aY, aW, aH, 2.0f, ColorUtil.a(currentRgb, anim), ColorUtil.a(currentRgb, anim), ColorUtil.a(currentRgb, 0.0f), ColorUtil.a(currentRgb, 0.0f));

        draw.a(matrices, aX, aY, aW, aH, 2.0f, 0.5f, border);

        float aCursorY = aY + (1.0f - this.cpCurAlpha) * aH;

        draw.a(matrices, aX - 0.5f, aCursorY - 1.0f, aW + 1.0f, 2.0f, 0.5f, 0xFFFFFFFF);

        // Hex preview text

        String hexStr = String.format("#%06X", (cs.c() & 0xFFFFFF));

        Fonts.b.a(matrices, hexStr, px + 8.0f, py + 68.0f, 6.5f, titleColor);

        float pvW = 14.0f, pvH = 8.0f, pvX = px + popW - pvW - 6.0f, pvY = py + 66.0f;

        draw.a(matrices, pvX, pvY, pvW, pvH, 2.5f, cs.c());

        draw.a(matrices, pvX, pvY, pvW, pvH, 2.5f, 0.5f, border);

    }

    private void updateColorPicker(double mouseX, double mouseY) {

        if (this.profileColorPickerMode != 0) {
            updateProfileColorPicker(mouseX, mouseY);
            return;
        }

        if (this.activeColorPicker == null || this.cpDragMode == 0) return;

        float px = this.colorPickerX;

        float py = this.colorPickerY;

        float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;

        float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;

        float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;

        if (this.cpDragMode == 1) { // SV

            this.cpCurSat = MathUtil.b((float) (mouseX - svX) / svW, 0.0f, 1.0f);

            this.cpCurBri = 1.0f - MathUtil.b((float) (mouseY - svY) / svH, 0.0f, 1.0f);

        } else if (this.cpDragMode == 2) { // HUE

            this.cpCurHue = MathUtil.b((float) (mouseY - hY) / hH, 0.0f, 1.0f);

        } else if (this.cpDragMode == 3) { // ALPHA

            this.cpCurAlpha = 1.0f - MathUtil.b((float) (mouseY - aY) / aH, 0.0f, 1.0f);

        }

        int rgb = Color.HSBtoRGB(this.cpCurHue, this.cpCurSat, this.cpCurBri);

        int argb = ColorUtil.a(rgb, this.cpCurAlpha);

        this.activeColorPicker.a(argb);

    }

    private void updateProfileColorPicker(double mouseX, double mouseY) {
        if (this.profileColorPickerMode == 0 || this.cpDragMode == 0) return;

        float px = this.colorPickerX;
        float py = this.colorPickerY;
        float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;
        float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;
        float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;

        if (this.cpDragMode == 1) {
            this.cpCurSat = MathUtil.b((float) (mouseX - svX) / svW, 0.0f, 1.0f);
            this.cpCurBri = 1.0f - MathUtil.b((float) (mouseY - svY) / svH, 0.0f, 1.0f);
        } else if (this.cpDragMode == 2) {
            this.cpCurHue = MathUtil.b((float) (mouseY - hY) / hH, 0.0f, 1.0f);
        } else if (this.cpDragMode == 3) {
            this.cpCurAlpha = 1.0f - MathUtil.b((float) (mouseY - aY) / aH, 0.0f, 1.0f);
        }

        int rgb = Color.HSBtoRGB(this.cpCurHue, this.cpCurSat, this.cpCurBri);
        int argb = ColorUtil.a(rgb, this.cpCurAlpha);

        CustomProfileManager profMgr = CustomProfileManager.getInstance();
        if (this.profileColorPickerMode == 1) {
            profMgr.setRingColor1(argb);
        } else if (this.profileColorPickerMode == 2) {
            profMgr.setRingColor2(argb);
        }
    }

    // --- MODE SETTINGS WITH SPACED OUT BUTTONS ---

    private float renderModeSetting(MatrixStack matrices, ModeSetting ms, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        Fonts.b.a(matrices, ms.i(), x, y + 1.0f, 7.5f, titleColor);

        List<String> modes = ms.k();

        float totalW = 0.0f;

        for (String str : modes) {

            totalW += Fonts.b.a(str, 6.5f) + 8.0f + 3.0f;

        }

        // Clean vertical spacing: buttons start at y + 14.0f (not in contact with label!)

        if (modes.size() <= 4 && totalW <= w + 10.0f) {

            float btnY = y + 14.0f;

            float btnH = 11.5f;

            float gap = 3.0f;

            float btnW = (w - (modes.size() - 1) * gap) / modes.size();

            for (int i = 0; i < modes.size(); i++) {

                String mName = modes.get(i);

                float bX = x + i * (btnW + gap);

                boolean isCurrent = ms.c().equalsIgnoreCase(mName);

                int btnBg = isCurrent ? primaryAccent : (isDark ? ColorUtil.a(36, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

                int bTextColor = isCurrent ? 0xFFFFFFFF : mutedColor;

                draw.a(matrices, bX, btnY, btnW, btnH, 3.0f, btnBg);

                if (!isCurrent) {

                    draw.a(matrices, bX, btnY, btnW, btnH, 3.0f, 0.5f, isDark ? ColorUtil.a(255, 255, 255, (int) (12 * anim)) : ColorUtil.a(210, 218, 230, (int) (255 * anim)));

                }

                Fonts.b.b(matrices, mName, bX + btnW * 0.5f, btnY + 2.5f, 6.5f, bTextColor);

            }

            return 28.0f;

        } else if (modes.size() <= 6) {

            float curX = x;

            float curY = y + 14.0f;

            float chipH = 11.5f;

            int lines = 1;

            for (String mName : modes) {

                float bW = Fonts.b.a(mName, 6.5f) + 8.0f;

                if (curX + bW > x + w && curX > x) {

                    curX = x;

                    curY += chipH + 3.5f;

                    lines++;

                }

                boolean isCurrent = ms.c().equalsIgnoreCase(mName);

                int btnBg = isCurrent ? primaryAccent : (isDark ? ColorUtil.a(36, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

                int bTextColor = isCurrent ? 0xFFFFFFFF : mutedColor;

                draw.a(matrices, curX, curY, bW, chipH, 3.0f, btnBg);

                Fonts.b.b(matrices, mName, curX + bW * 0.5f, curY + 2.5f, 6.5f, bTextColor);

                curX += bW + 3.5f;

            }

            return 16.0f + lines * (chipH + 3.5f);

        } else {

            float dropW = Math.min(88.0f, w * 0.55f);

            float dropH = 13.0f;

            float dropX = x + w - dropW;

            float dropY = y;

            boolean isOpen = (this.openDropdownSetting == ms);

            int dropBg = isDark ? ColorUtil.a(32, 36, 48, (int) (240 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim));

            int dropBorder = isOpen ? primaryAccent : (isDark ? ColorUtil.a(255, 255, 255, (int) (22 * anim)) : ColorUtil.a(210, 218, 230, (int) (240 * anim)));

            draw.a(matrices, dropX, dropY, dropW, dropH, 3.5f, dropBg);

            draw.a(matrices, dropX, dropY, dropW, dropH, 3.5f, 0.5f, dropBorder);

            String modeLabel = ms.c();

            if (Fonts.b.a(modeLabel, 6.5f) > dropW - 18.0f) {

                while (modeLabel.length() > 3 && Fonts.b.a(modeLabel + "..", 6.5f) > dropW - 18.0f) {

                    modeLabel = modeLabel.substring(0, modeLabel.length() - 1);

                }

                modeLabel += "..";

            }

            Fonts.b.a(matrices, modeLabel, dropX + 5.0f, dropY + 3.0f, 6.5f, isOpen ? primaryAccent : titleColor);

            // Procedural vector chevron arrow: points down when closed, up when open!
            drawChevron(matrices, dropX + dropW - 8.0f, dropY + 6.5f, 4.5f, 1.2f, isOpen ? primaryAccent : mutedColor, isOpen);

            return 18.0f;

        }

    }

    // --- MULTI-LINE WRAPPING FOR MULTI MODES WITH CLEAN SPACING ---

    private float renderMultiMode(MatrixStack matrices, MultiModeSetting mms, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        long activeCount = mms.c().stream().filter(Setting::c).count();

        Fonts.b.a(matrices, mms.i(), x, y + 1.0f, 7.5f, titleColor);

        Fonts.b.a(matrices, activeCount + "/" + mms.c().size(), x + w - 16.0f, y + 1.0f, 6.5f, mutedColor);

        // Fixed spacing: chips start at y + 14.0f so they aren't touching the text!

        float chipCurX = x;

        float chipCurY = y + 14.0f;

        float chipH = 11.5f;

        float chipGap = 3.5f;

        int rowCount = 1;

        for (BooleanSetting sub : mms.c()) {

            float chipW = Fonts.b.a(sub.i(), 6.5f) + 8.0f;

            // WRAP TO NEXT LINE IF OVERFLOWS CARD BOUNDS!

            if (chipCurX + chipW > x + w && chipCurX > x) {

                chipCurX = x;

                chipCurY += chipH + 3.5f;

                rowCount++;

            }

            boolean on = sub.c();

            int chipBg = on ? primaryAccent : (isDark ? ColorUtil.a(36, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

            int chipTextColor = on ? 0xFFFFFFFF : mutedColor;

            draw.a(matrices, chipCurX, chipCurY, chipW, chipH, 3.0f, chipBg);

            Fonts.b.b(matrices, sub.i(), chipCurX + chipW * 0.5f, chipCurY + 2.5f, 6.5f, chipTextColor);

            chipCurX += chipW + chipGap;

        }

        return 16.0f + rowCount * (chipH + 3.5f);

    }

    private void renderKeybind(MatrixStack matrices, BindSetting bnds, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        Fonts.b.a(matrices, bnds.i(), x, y + 2.0f, 7.5f, titleColor);

        boolean isListening = this.bindingTarget == bnds;

        String keyText = isListening ? "[ ... ]" : (bnds.c() == -1 ? "NONE" : KeyUtil.b(bnds.c()));

        float bindW = Fonts.b.a(keyText, 6.5f) + 10.0f;

        float bindH = 11.0f;

        float bindX = x + w - bindW;

        float bindY = y + 1.0f;

        int bindBg = isListening ? ColorUtil.a(primaryAccent, 0.3f * anim) : (isDark ? ColorUtil.a(36, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim)));

        draw.a(matrices, bindX, bindY, bindW, bindH, 3.0f, bindBg);

        draw.a(matrices, bindX, bindY, bindW, bindH, 3.0f, 0.5f, isListening ? primaryAccent : (isDark ? ColorUtil.a(255, 255, 255, (int) (15 * anim)) : ColorUtil.a(210, 218, 230, (int) (255 * anim))));

        Fonts.b.b(matrices, keyText, bindX + bindW * 0.5f, bindY + 1.25f, 6.5f, isListening ? primaryAccent : titleColor);

    }

    private void renderGenericSetting(MatrixStack matrices, Setting<?> s, double mouseX, double mouseY, float x, float y, float w, float anim, boolean isDark, int primaryAccent, int titleColor, int mutedColor) {

        Draw2DProcessor draw = Delta.h().d().i();

        Fonts.b.a(matrices, s.i(), x, y + 2.0f, 7.5f, titleColor);

        String valStr = String.valueOf(s.c());

        float valW = Fonts.b.a(valStr, 6.5f) + 8.0f;

        float valH = 11.0f;

        float valX = x + w - valW;

        float valY = y + 1.0f;

        int bg = isDark ? ColorUtil.a(36, 40, 52, (int) (255 * anim)) : ColorUtil.a(235, 240, 248, (int) (255 * anim));

        draw.a(matrices, valX, valY, valW, valH, 3.0f, bg);

        Fonts.b.b(matrices, valStr, valX + valW * 0.5f, valY + 2.0f, 6.5f, mutedColor);

    }

    // --- SEARCH FILTER WITH KEYBOARD LAYOUT CONVERSION (фгкф -> aura) ---

    private List<Module> getFilteredModules() {

        List<Module> all = Delta.h().d().t().e();

        String q = this.searchQuery.trim();

        return all.stream().filter(m -> {

            // Interface module is moved into Themes tab!

            if (m instanceof Interface_2) {

                return false;

            }

            if (!q.isEmpty()) {

                return matchesSearch(m, q);

            }

            return this.selectedTab.getCategory() != null && m.l() == this.selectedTab.getCategory();

        }).sorted(Comparator.comparing(Module::j, String.CASE_INSENSITIVE_ORDER)).toList();

    }

    private boolean hasSearchSelection() {

        return this.searchSelectionStart != -1 && this.searchSelectionEnd != -1 && this.searchSelectionStart != this.searchSelectionEnd;

    }

    private int getSearchCharIndexAt(double mouseX, float textStartX) {

        if (this.searchQuery.isEmpty()) return 0;

        if (mouseX <= textStartX) return 0;

        for (int i = 0; i < this.searchQuery.length(); i++) {

            float charW = Fonts.d.a(String.valueOf(this.searchQuery.charAt(i)), 7.5f);

            float curW = Fonts.d.a(this.searchQuery.substring(0, i), 7.5f);

            if (mouseX <= textStartX + curW + charW * 0.5f) {

                return i;

            }

        }

        return this.searchQuery.length();

    }

    // ==========================================

    // INPUT HANDLING: Clicks, Drags, Keys, Scroll

    // ==========================================

        public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (this.isClosing) {
            return false;
        }

        double mX = mouseX;
        double mY = mouseY;

        // 0. Active keybinding listening via mouse button (MMB / wheel = button 2, side buttons, etc.)

        if (this.bindingTarget != null) {

            if (button == 1) { // RMB cancels / unbinds

                if (this.bindingTarget instanceof BindSetting) {

                    ((BindSetting) this.bindingTarget).a(-1);

                } else if (this.bindingTarget instanceof Module) {

                    ((Module) this.bindingTarget).a(-1);

                    ((Module) this.bindingTarget).b(false);

                }

            } else {

                int mouseBind = KeyUtil.c(button);

                if (mouseBind != -1) {

                    if (this.bindingTarget instanceof BindSetting) {

                        ((BindSetting) this.bindingTarget).a(mouseBind);

                    } else if (this.bindingTarget instanceof Module) {

                        ((Module) this.bindingTarget).a(mouseBind);

                        ((Module) this.bindingTarget).b(false);

                    }

                }

            }

            this.bindingTarget = null;

            return true;

        }

        MinecraftClient mc = Interface.aM_;

        int screenW = mc.getWindow().getScaledWidth();

        int screenH = mc.getWindow().getScaledHeight();

        float winW = Math.min(570.0f, screenW - 24.0f);

        float winH = Math.min(340.0f, screenH - 24.0f);

        float winX = (screenW - winW) * 0.5f;

        float winY = (screenH - winH) * 0.5f;

        float sidebarW = 126.0f;

        // Mode Dropdown Overlay Click Handling
        if (this.openDropdownSetting != null) {
            List<String> dModes = this.openDropdownSetting.k();
            float dItemH = 14.0f;
            int maxVisible = 7;
            int displayItems = Math.min(dModes.size(), maxVisible);
            float dListH = displayItems * dItemH;
            float dPopH = dListH + 6.0f;
            float dPopW = Math.max(this.openDropdownW, 88.0f);
            float dPopX = this.openDropdownX;
            float dPopY = this.openDropdownY;

            if (MathUtil.a(mX, mY, dPopX, dPopY, dPopW, dPopH)) {
                float relY = (float) mY - (dPopY + 3.0f) + this.openDropdownScrollY;
                int clickedIdx = (int) (relY / dItemH);
                if (clickedIdx >= 0 && clickedIdx < dModes.size()) {
                    this.openDropdownSetting.a(dModes.get(clickedIdx));
                    this.openDropdownSetting = null;
                    return true;
                }
                return true;
            } else {
                this.openDropdownSetting = null;
            }
        }

        // Search Dropdown Click Handling (compact inside sidebar under search bar)
        if (this.searchModalOpen && !this.searchQuery.trim().isEmpty()) {
            float modalX = winX + 8.0f;
            float modalY = winY + 36.0f + 17.0f + 3.0f;
            float modalW = sidebarW - 16.0f;
            float headerH = 14.0f;
            float rowH = 17.0f;
            List<Module> matches = getSearchModalMatches();
            int maxRows = 5;
            int displayRows = Math.max(1, Math.min(maxRows, matches.size()));
            float listH = displayRows * rowH;
            float modalH = headerH + listH + 4.0f;

            if (MathUtil.a(mX, mY, modalX, modalY, modalW, modalH)) {
                float listY = modalY + headerH + 2.0f;
                if (MathUtil.a(mX, mY, modalX + 2.0f, listY, modalW - 4.0f, listH)) {
                    float clickRelY = (float) mY - listY + this.searchModalScrollY;
                    int rowClicked = (int) (clickRelY / rowH);
                    if (rowClicked >= 0 && rowClicked < matches.size()) {
                        openModuleFromSearch(matches.get(rowClicked));
                        return true;
                    }
                }
                return true;
            } else if (!MathUtil.a(mX, mY, winX + 8.0f, winY + 36.0f, sidebarW - 16.0f, 17.0f)) {
                // Click outside (and not on search input itself) closes the dropdown
                this.searchModalOpen = false;
                this.searchFocused = false;
                return true;
            }
        }

        // Profile Color Picker Active Handling
        if (this.profileColorPickerMode != 0) {
            float px = this.colorPickerX, py = this.colorPickerY;
            float popW = 92.0f, popH = 82.0f;
            if (MathUtil.a(mX, mY, px, py, popW, popH)) {
                float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;
                float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;
                float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;

                if (MathUtil.a(mX, mY, svX, svY, svW, svH)) {
                    this.cpDragMode = 1;
                    updateColorPicker(mX, mY);
                    return true;
                } else if (MathUtil.a(mX, mY, hX - 2.0f, hY, hW + 4.0f, hH)) {
                    this.cpDragMode = 2;
                    updateColorPicker(mX, mY);
                    return true;
                } else if (MathUtil.a(mX, mY, aX - 2.0f, aY, aW + 4.0f, aH)) {
                    this.cpDragMode = 3;
                    updateColorPicker(mX, mY);
                    return true;
                }
                return true;
            } else {
                this.profileColorPickerMode = 0;
                this.cpDragMode = 0;
            }
        }

        // Profile Modal Click Handling
        if (this.profileModalOpen) {
            float modalW = 460.0f;
            float modalH = 245.0f;
            float modalX = winX + (winW - modalW) * 0.5f;
            float modalY = winY + (winH - modalH) * 0.5f;
            float leftPaneW = 240.0f;

            if (MathUtil.a(mX, mY, modalX, modalY, modalW, modalH)) {
                CustomProfileManager profMgr = CustomProfileManager.getInstance();

                // 1. Avatar File Pick / Reset
                float row1Y = modalY + 86.0f;
                float btnPickAvW = 68.0f, btnPickAvH = 15.0f;
                float btnPickAvX = modalX + leftPaneW - btnPickAvW - 10.0f;
                if (MathUtil.a(mX, mY, btnPickAvX, row1Y, btnPickAvW, btnPickAvH)) {
                    profMgr.chooseAvatarFile();
                    return true;
                }
                if (profMgr.hasCustomAvatar()) {
                    float btnResetAvX = btnPickAvX - 30.0f;
                    if (MathUtil.a(mX, mY, btnResetAvX, row1Y, 26.0f, btnPickAvH)) {
                        profMgr.resetAvatar();
                        return true;
                    }
                }

                // 2. Banner File Pick / Reset
                float row2Y = modalY + 109.0f;
                float btnPickBanX = modalX + leftPaneW - btnPickAvW - 10.0f;
                if (MathUtil.a(mX, mY, btnPickBanX, row2Y, btnPickAvW, btnPickAvH)) {
                    profMgr.chooseBannerFile();
                    return true;
                }
                if (profMgr.hasCustomBanner()) {
                    float btnResetBanX = btnPickBanX - 30.0f;
                    if (MathUtil.a(mX, mY, btnResetBanX, row2Y, 26.0f, btnPickAvH)) {
                        profMgr.resetBanner();
                        return true;
                    }
                }

                // 3. Tab: Auto vs Manual Color Picker
                float row3Y = modalY + 140.0f;
                float tabW = (leftPaneW - 32.0f) * 0.5f;
                float tabH = 15.0f;
                float tabAutoX = modalX + 14.0f;
                float tabManualX = tabAutoX + tabW + 4.0f;
                float tabY = row3Y + 12.0f;

                if (MathUtil.a(mX, mY, tabAutoX, tabY, tabW, tabH)) {
                    profMgr.setCustomColors(false);
                    this.profileColorPickerMode = 0;
                    return true;
                }
                if (MathUtil.a(mX, mY, tabManualX, tabY, tabW, tabH)) {
                    profMgr.setCustomColors(true);
                    return true;
                }

                // 4. Color Swatches (Click to open Color Picker popover)
                float swatchY = tabY + tabH + 11.0f;
                float sw1X = modalX + 54.0f;
                float sw2X = modalX + 162.0f;
                float swW = 26.0f, swH = 13.0f;

                if (MathUtil.a(mX, mY, sw1X - 2.0f, swatchY - 2.0f, swW + 4.0f, swH + 4.0f)) {
                    profMgr.setCustomColors(true);
                    this.profileColorPickerMode = 1;
                    this.colorPickerX = sw1X - 10.0f;
                    this.colorPickerY = swatchY + swH + 6.0f;
                    initColorPickerHSB(profMgr.getRingColor1());
                    return true;
                }

                if (MathUtil.a(mX, mY, sw2X - 2.0f, swatchY - 2.0f, swW + 4.0f, swH + 4.0f)) {
                    profMgr.setCustomColors(true);
                    this.profileColorPickerMode = 2;
                    this.colorPickerX = sw2X - 40.0f;
                    this.colorPickerY = swatchY + swH + 6.0f;
                    initColorPickerHSB(profMgr.getRingColor2());
                    return true;
                }

                // 5. Right Pane: Setup Input & Save Button
                float rightPaneX = modalX + leftPaneW + 12.0f;
                float rightPaneW = modalW - leftPaneW - 24.0f;
                float rightTopY = modalY + 30.0f;
                float inputY = rightTopY + 15.0f;
                float saveBtnW = 54.0f;
                float inputH = 15.0f;
                float inputW = rightPaneW - saveBtnW - 4.0f;

                if (MathUtil.a(mX, mY, rightPaneX, inputY, inputW, inputH)) {
                    this.setupInputFocused = true;
                    return true;
                } else {
                    this.setupInputFocused = false;
                }

                // Click Save button
                float saveBtnX = rightPaneX + inputW + 4.0f;
                if (MathUtil.a(mX, mY, saveBtnX, inputY, saveBtnW, inputH)) {
                    String name = this.newSetupName.trim().isEmpty() ? ("Сетап " + (profMgr.getSetups().size() + 1)) : this.newSetupName.trim();
                    profMgr.saveCurrentAsSetup(name);
                    this.newSetupName = "";
                    this.setupInputFocused = false;
                    return true;
                }

                // Click Presets List Cards
                float listY = inputY + inputH + 8.0f;
                float cardH = 34.0f;
                float cardGap = 5.0f;
                float listH = modalH - (listY - modalY) - 10.0f;
                List<CustomProfileManager.ProfileSetup> setupsList = profMgr.getSetups();
                for (int i = 0; i < setupsList.size(); i++) {
                    CustomProfileManager.ProfileSetup setup = setupsList.get(i);
                    float sY = listY + i * (cardH + cardGap) - this.profileSetupsScrollY;

                    if (sY + cardH < listY || sY > listY + listH) {
                        continue;
                    }

                    // Dedicated Delete button click
                    float delBtnSz = 16.0f;
                    float delBtnX = rightPaneX + rightPaneW - delBtnSz - 7.0f;
                    float delBtnY = sY + (cardH - delBtnSz) * 0.5f;
                    if (MathUtil.a(mX, mY, delBtnX - 2.0f, delBtnY - 2.0f, delBtnSz + 4.0f, delBtnSz + 4.0f)) {
                        profMgr.deleteSetup(setup);
                        return true;
                    }

                    // Apply card click
                    if (MathUtil.a(mX, mY, rightPaneX, sY, rightPaneW - (delBtnSz + 14.0f), cardH)) {
                        profMgr.applySetup(setup);
                        return true;
                    }
                }

                return true;
            } else {
                this.profileModalOpen = false;
                this.profileColorPickerMode = 0;
                this.setupInputFocused = false;
                return true;
            }
        }

        // 1. Color Picker Active Handling

        if (this.activeColorPicker != null) {

            float px = this.colorPickerX, py = this.colorPickerY;

            float popW = 92.0f, popH = 82.0f;

            if (MathUtil.a(mX, mY, px, py, popW, popH)) {

                float svX = px + 6.0f, svY = py + 6.0f, svW = 56.0f, svH = 56.0f;

                float hX = svX + svW + 5.0f, hY = svY, hW = 6.0f, hH = 56.0f;

                float aX = hX + hW + 5.0f, aY = svY, aW = 6.0f, aH = 56.0f;

                if (MathUtil.a(mX, mY, svX, svY, svW, svH)) {

                    this.cpDragMode = 1;

                    updateColorPicker(mX, mY);

                    return true;

                } else if (MathUtil.a(mX, mY, hX - 2.0f, hY, hW + 4.0f, hH)) {

                    this.cpDragMode = 2;

                    updateColorPicker(mX, mY);

                    return true;

                } else if (MathUtil.a(mX, mY, aX - 2.0f, aY, aW + 4.0f, aH)) {

                    this.cpDragMode = 3;

                    updateColorPicker(mX, mY);

                    return true;

                }

                return true;

            } else {

                this.activeColorPicker = null;

                this.cpDragMode = 0;

            }

        }

        // 2. Settings Modal handling

        if (this.settingsPanelOpen) {

            float popX = winX + 12.0f, popY = winY + 34.0f, popW = 150.0f, popH = 104.0f;

            Interface_2 iface = Interface_2.getInstance();

            if (MathUtil.a(mX, mY, popX, popY, popW, popH)) {

                if (MathUtil.a(mX, mY, popX + popW - 16.0f, popY + 4.0f, 14.0f, 14.0f)) {

                    this.settingsPanelOpen = false;

                    return true;

                }

                float thBtnW = (popW - 22.0f) * 0.5f;

                float darkThX = popX + 8.0f;

                float lightThX = darkThX + thBtnW + 6.0f;

                float thBtnY = popY + 36.0f;

                if (MathUtil.a(mX, mY, darkThX, thBtnY, thBtnW, 14.0f)) {

                    if (iface != null) iface.setModernTheme("Черная");

                    return true;

                }

                if (MathUtil.a(mX, mY, lightThX, thBtnY, thBtnW, 14.0f)) {

                    if (iface != null) iface.setModernTheme("Белая");

                    return true;

                }

                return true;

            } else {

                this.settingsPanelOpen = false;

            }

        }

        // Adjust mouse coords if clicking during opening transition animation
        long openElapsed = System.currentTimeMillis() - this.openStartTime;
        if (openElapsed < 320L && openElapsed > 0L) {
            float rProg = (float) openElapsed / 320.0f;
            float eOut = 1.0f - (float) Math.pow(1.0f - rProg, 4.0);
            float p_ = rProg - 1.0f;
            float eSpring = 1.0f + 0.85f * (p_ * p_ * p_) + 0.75f * (p_ * p_);
            float wScale = 0.92f + 0.08f * eSpring;
            float wOffsetY = (1.0f - eOut) * 12.0f;
            float wCenterX = winX + winW * 0.5f;
            float wCenterY = winY + winH * 0.5f;
            if (wScale > 0.01f) {
                mX = (mX - wCenterX) / wScale + wCenterX;
                mY = (mY - (wCenterY + wOffsetY)) / wScale + wCenterY;
            }
        }

        // 3. Client Logo Click -> Toggles Settings Panel!

        float logoSize = 20.0f, logoX = winX + 10.0f, logoY = winY + 11.0f;

        if (MathUtil.a(mX, mY, logoX - 2.0f, logoY - 2.0f, logoSize + 4.0f, logoSize + 4.0f)) {

            this.settingsPanelOpen = !this.settingsPanelOpen;

            return true;

        }

        // 4. Search Bar Click with Mouse Text Selection

        float searchX = winX + 8.0f, searchY = winY + 36.0f, searchW = sidebarW - 16.0f, searchH = 17.0f;

        float textStartX = searchX + 17.0f;

        if (MathUtil.a(mX, mY, searchX, searchY, searchW, searchH)) {
            this.searchFocused = true;
            this.searchModalOpen = !this.searchQuery.trim().isEmpty();
            this.searchSelectedIndex = 0;
            int idx = getSearchCharIndexAt(mX, textStartX);
            this.searchCursor = idx;
            this.searchSelectionStart = idx;
            this.searchSelectionEnd = idx;
            this.isDraggingSearch = true;
            return true;
        } else {

            this.searchFocused = false;

            this.searchSelectionStart = -1;

            this.searchSelectionEnd = -1;

            this.isDraggingSearch = false;

        }

        // 6. Category Tabs Click

        float tabX = winX + 8.0f, tabW = sidebarW - 16.0f, tabH = 18.0f, tabGap = 3.0f;

        float startTabY = winY + 73.0f;

        for (int i = 0; i < Tab.values().length; i++) {

            float tY = startTabY + i * (tabH + tabGap);

            if (MathUtil.a(mX, mY, tabX, tY, tabW, tabH)) {

                this.selectedTab = Tab.values()[i];
                this.tabSwitchTime = System.currentTimeMillis();
                this.searchQuery = "";
                this.searchFocused = false;
                this.searchModalOpen = false;
                this.searchSelectionStart = -1;
                this.searchSelectionEnd = -1;

                this.scrollY = 0.0f;

                this.targetScrollY = 0.0f;

                this.themesScrollY = 0.0f;

                this.targetThemesScrollY = 0.0f;

                this.cosmeticsScrollY = 0.0f;

                this.targetCosmeticsScrollY = 0.0f;

                return true;

            }

        }

        // 7. Profile Card & Avatar Click Handling
        float profH = 34.0f;
        float profY = winY + winH - profH - 8.0f;
        float profX = winX + 6.0f, profW = sidebarW - 12.0f;

        // Theme shortcut button on the right edge of profile card
        if (MathUtil.a(mX, mY, profX + profW - 16.0f, profY, 16.0f, profH)) {
            this.selectedTab = Tab.THEMES;
            this.searchQuery = "";
            this.searchFocused = false;
            this.searchModalOpen = false;
            return true;
        }

        // Avatar click (or clicking profile card) opens profile modal ("все настройки при нажатии на аватарку выходят")
        float avSize = 22.0f;
        float avX = profX + 6.0f;
        float avY = profY + (profH - avSize) * 0.5f;
        if (MathUtil.a(mX, mY, avX - 3.0f, avY - 3.0f, avSize + 6.0f, avSize + 6.0f)
                || MathUtil.a(mX, mY, profX, profY, profW - 16.0f, profH)) {
            this.profileModalOpen = true;
            this.profileColorPickerMode = 0;
            return true;
        }

        // 8. Themes Tab Click (including Interface settings inside Themes)

        float contentX = winX + sidebarW + 8.0f;

        float areaY = winY + 26.0f;

        float contentW = winW - sidebarW - 16.0f;

        float areaH = winH - 36.0f;

        if (this.selectedTab == Tab.THEMES) {

            float curY = areaY - this.themesScrollY;

            float cardW = (contentW - 18.0f) * 0.5f;

            float cardH = 92.0f;

            float darkX = contentX + 4.0f, darkY = curY + 34.0f;

            float lightX = contentX + cardW + 12.0f, lightY = curY + 34.0f;

            if (MathUtil.a(mX, mY, darkX, darkY, cardW, cardH)) {

                Interface_2.getInstance().setModernTheme("Черная");

                return true;

            }

            if (MathUtil.a(mX, mY, lightX, lightY, cardW, cardH)) {

                Interface_2.getInstance().setModernTheme("Белая");

                return true;

            }

            // Interface Module inside Themes Tab

            Interface_2 iface = Interface_2.getInstance();

            if (iface != null) {

                float secY = curY + 138.0f;

                float iCardY = secY + 24.0f;

                float iCardW = contentW - 8.0f;

                float iCardH = calculateCardHeight(iface, iCardW);

                if (handleCardSettingsClicks(iface, mX, mY, contentX + 4.0f, iCardY, iCardW, button)) {

                    return true;

                }

            }

            return false;

        }

        // Cosmetics Tab Click Handling
        if (this.selectedTab == Tab.COSMETICS) {
            if (handleCosmeticsClicks(mX, mY, contentX, areaY, contentW, areaH, button)) {
                return true;
            }
            return false;
        }

        // 9. Modules View clicks

        if (MathUtil.a(mX, mY, contentX, areaY, contentW, areaH)) {

            List<Module> modules = getFilteredModules();

            float colW = (contentW - 8.0f) * 0.5f;

            float col1X = contentX;

            float col2X = contentX + colW + 8.0f;

            float curCol1Y = areaY - this.scrollY;

            float curCol2Y = areaY - this.scrollY;

            for (Module m : modules) {

                float cardH = this.cardHeightAnims.getOrDefault(m, calculateCardHeight(m, colW));

                float cardX, cardY;

                if (curCol1Y <= curCol2Y) {

                    cardX = col1X;

                    cardY = curCol1Y;

                    curCol1Y += cardH + 8.0f;

                } else {

                    cardX = col2X;

                    cardY = curCol2Y;

                    curCol2Y += cardH + 8.0f;

                }

                if (cardY + cardH < areaY || cardY > areaY + areaH) {

                    continue;

                }

                if (handleCardSettingsClicks(m, mX, mY, cardX, cardY, colW, button)) {

                    return true;

                }

            }

        }

        return false;

    }

    private boolean handleCardSettingsClicks(Module m, double mX, double mY, float cardX, float cardY, float colW, int button) {

        // Toggle switch

        float swW = 18.0f, swH = 10.0f;

        float swX = cardX + colW - swW - 8.0f;

        float swY = cardY + 8.0f;

        if (MathUtil.a(mX, mY, swX - 3.0f, swY - 2.0f, swW + 6.0f, swH + 4.0f)) {

            if (button == 0) {

                m.a();

                return true;

            }

        }

        // Keybind badge next to switch

        boolean isListening = this.bindingTarget == m;

        String bText = isListening ? "..." : (m.p() != -1 ? KeyUtil.b(m.p()) : "none");

        float bBadgeW = Fonts.b.a(bText, 6.5f) + 8.0f;

        float bBadgeX = swX - bBadgeW - 6.0f;

        float bBadgeY = cardY + 7.5f;

        // Click on Bind badge (LMB, RMB to unbind, MMB)

        if (MathUtil.a(mX, mY, bBadgeX - 3.0f, bBadgeY - 2.0f, bBadgeW + 6.0f, 15.0f)) {

            if (button == 1) { // RMB on bind badge unbinds directly!

                m.a(-1);

                m.b(false);

                if (this.bindingTarget == m) this.bindingTarget = null;

                return true;

            }

            if (this.bindingTarget == m) {

                this.bindingTarget = null;

                m.b(false);

            } else {

                this.bindingTarget = m;

                m.b(true);

            }

            return true;

        }

        // Middle click (mouse wheel / button 2) anywhere on card header starts/toggles keybind listening!

        if (button == 2 && MathUtil.a(mX, mY, cardX, cardY, colW, 28.0f)) {

            if (this.bindingTarget == m) {

                this.bindingTarget = null;

                m.b(false);

            } else {

                this.bindingTarget = m;

                m.b(true);

            }

            return true;

        }

        float curSY = cardY + 29.0f;

        List<Setting<?>> settings = m.e();

        if (settings != null) {

            for (Setting<?> s : settings) {

                if (s.e() != null && !s.e().get()) {

                    continue;

                }

                if (s instanceof BooleanSetting) {

                    BooleanSetting bs = (BooleanSetting) s;

                    if (MathUtil.a(mX, mY, cardX + 8.0f, curSY, colW - 16.0f, 14.0f)) {

                        bs.a(!bs.c());

                        return true;

                    }

                    curSY += 15.0f;

                } else if (s instanceof SliderSetting) {

                    SliderSetting ss = (SliderSetting) s;

                    float trackY = curSY + 13.5f;

                    if (MathUtil.a(mX, mY, cardX + 8.0f, trackY - 5.0f, colW - 16.0f, 12.0f)) {

                        this.draggingSlider = ss;

                        this.sliderTrackX = cardX + 8.0f;

                        this.sliderTrackW = colW - 16.0f;

                        updateSliderValue(ss, mX);

                        return true;

                    }

                    curSY += 22.0f;

                } else if (s instanceof ColorSetting) {

                    ColorSetting cs = (ColorSetting) s;

                    float swatchW = 22.0f, swatchH = 10.5f;

                    float swatchX = cardX + colW - swatchW - 8.0f;

                    float swatchY = curSY + 1.5f;

                    if (MathUtil.a(mX, mY, swatchX - 2.0f, swatchY - 2.0f, swatchW + 4.0f, swatchH + 4.0f)) {

                        if (this.activeColorPicker == cs) {

                            this.activeColorPicker = null;

                        } else {

                            this.activeColorPicker = cs;

                            this.colorPickerX = Math.max(cardX, swatchX - 70.0f);

                            this.colorPickerY = swatchY + 14.0f;

                            initColorPickerHSB(cs.c());

                        }

                        return true;

                    }

                    curSY += 16.0f;

                } else if (s instanceof ModeSetting) {

                    ModeSetting ms = (ModeSetting) s;

                    List<String> modes = ms.k();

                    float totalW = 0.0f;

                    for (String str : modes) totalW += Fonts.b.a(str, 6.5f) + 8.0f + 3.0f;

                    if (modes.size() <= 4 && totalW <= (colW - 16.0f) + 10.0f) {

                        float btnY = curSY + 14.0f, btnH = 11.5f, gap = 3.0f;

                        float btnW = (colW - 16.0f - (modes.size() - 1) * gap) / modes.size();

                        for (int mi = 0; mi < modes.size(); mi++) {

                            float bX = cardX + 8.0f + mi * (btnW + gap);

                            if (MathUtil.a(mX, mY, bX, btnY, btnW, btnH)) {

                                ms.a(modes.get(mi));

                                return true;

                            }

                        }

                        curSY += 28.0f;

                    } else if (modes.size() <= 6) {

                        float curBX = cardX + 8.0f, curBY = curSY + 14.0f, chipH = 11.5f;

                        int lines = 1;

                        for (String mName : modes) {

                            float bW = Fonts.b.a(mName, 6.5f) + 8.0f;

                            if (curBX + bW > cardX + colW - 8.0f && curBX > cardX + 8.0f) {

                                curBX = cardX + 8.0f;

                                curBY += chipH + 3.5f;

                                lines++;

                            }

                            if (MathUtil.a(mX, mY, curBX, curBY, bW, chipH)) {

                                ms.a(mName);

                                return true;

                            }

                            curBX += bW + 3.5f;

                        }

                        curSY += 16.0f + lines * (chipH + 3.5f);

                    } else {

                        float dropW = Math.min(88.0f, (colW - 16.0f) * 0.55f);

                        float dropX = cardX + colW - dropW - 8.0f;

                        if (MathUtil.a(mX, mY, dropX, curSY, dropW, 13.0f)) {

                            if (this.openDropdownSetting == ms) {
                                this.openDropdownSetting = null;
                            } else {
                                this.openDropdownSetting = ms;
                                this.openDropdownX = dropX;
                                this.openDropdownY = curSY + 14.0f;
                                this.openDropdownW = Math.max(dropW, 88.0f);
                                this.openDropdownScrollY = 0.0f;
                            }

                            return true;

                        }

                        curSY += 18.0f;

                    }

                } else if (s instanceof MultiModeSetting) {

                    MultiModeSetting mms = (MultiModeSetting) s;

                    float chipCurX = cardX + 8.0f;

                    float chipCurY = curSY + 14.0f;

                    float chipH = 11.5f;

                    int rowCount = 1;

                    for (BooleanSetting sub : mms.c()) {

                        float chipW = Fonts.b.a(sub.i(), 6.5f) + 8.0f;

                        if (chipCurX + chipW > cardX + colW - 8.0f && chipCurX > cardX + 8.0f) {

                            chipCurX = cardX + 8.0f;

                            chipCurY += chipH + 3.5f;

                            rowCount++;

                        }

                        if (MathUtil.a(mX, mY, chipCurX, chipCurY, chipW, chipH)) {

                            sub.a(!sub.c());

                            return true;

                        }

                        chipCurX += chipW + 3.5f;

                    }

                    curSY += 16.0f + rowCount * (chipH + 3.5f);

                } else if (s instanceof BindSetting) {

                    BindSetting bnds = (BindSetting) s;

                    float bW = 40.0f;

                    float bX = cardX + colW - bW - 8.0f;

                    if (MathUtil.a(mX, mY, bX, curSY, bW, 12.0f)) {

                        if (button == 1) { // RMB = unbind

                            bnds.a(-1);

                            if (this.bindingTarget == bnds) this.bindingTarget = null;

                            return true;

                        }

                        this.bindingTarget = (this.bindingTarget == bnds) ? null : bnds;

                        return true;

                    }

                    curSY += 16.0f;

                } else {

                    curSY += 16.0f;

                }

            }

        }

        return false;

    }

    private void initColorPickerHSB(int color) {

        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);

        this.cpCurHue = hsb[0];

        this.cpCurSat = hsb[1];

        this.cpCurBri = hsb[2];

        this.cpCurAlpha = ((color >> 24) & 0xFF) / 255.0f;

    }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {

        if (this.draggingSlider != null) {

            this.draggingSlider = null;

            return true;

        }

        if (this.cpDragMode != 0) {

            this.cpDragMode = 0;

            return true;

        }

        if (this.isDraggingSearch) {

            this.isDraggingSearch = false;

        }

        return false;

    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {

        double mX = mouseX;
        double mY = mouseY;

        if (this.draggingSlider != null) {

            updateSliderValue(this.draggingSlider, mX);

            return true;

        }

        if ((this.activeColorPicker != null || this.profileColorPickerMode != 0) && this.cpDragMode != 0) {

            updateColorPicker(mX, mY);

            return true;

        }

        if (this.searchFocused && this.isDraggingSearch) {

            int screenW = Interface.aM_.getWindow().getScaledWidth();

            float winW = Math.min(570.0f, screenW - 24.0f);

            float winX = (screenW - winW) * 0.5f;

            float textStartX = winX + 8.0f + 17.0f;

            this.searchSelectionEnd = getSearchCharIndexAt(mX, textStartX);

            this.searchCursor = this.searchSelectionEnd;

            return true;

        }

        return false;

    }

    private void updateSliderValue(SliderSetting ss, double mX) {

        float frac = (float) ((mX - this.sliderTrackX) / this.sliderTrackW);

        frac = Math.min(1.0f, Math.max(0.0f, frac));

        float rawVal = ss.a + frac * (ss.b - ss.a);

        // Snap to increment

        if (ss.c > 0.0f) {

            rawVal = Math.round(rawVal / ss.c) * ss.c;

        }

        rawVal = Math.min(ss.b, Math.max(ss.a, rawVal));

        ss.a(rawVal);

    }

    @Override

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (verticalAmount != 0.0) {
            if (this.openDropdownSetting != null) {
                this.openDropdownScrollY = Math.max(0.0f, Math.min(this.openDropdownMaxScroll, this.openDropdownScrollY - (float) verticalAmount * 14.0f));
                return true;
            }

            if (this.profileModalOpen) {
                this.targetProfileSetupsScrollY = Math.min(this.profileSetupsMaxScroll, Math.max(0.0f, this.targetProfileSetupsScrollY - (float) verticalAmount * 24.0f));
                return true;
            }

            if (this.searchModalOpen) {
                this.targetSearchModalScrollY = Math.min(this.searchModalMaxScroll, Math.max(0.0f, this.targetSearchModalScrollY - (float) verticalAmount * 22.0f));
                return true;
            }

            if (this.selectedTab == Tab.THEMES) {

                this.targetThemesScrollY = Math.min(this.maxThemesScrollY, Math.max(0.0f, this.targetThemesScrollY - (float) verticalAmount * 24.0f));

            } else if (this.selectedTab == Tab.COSMETICS) {

                if (mouseX >= this.lastCosmeticsRightPaneX && this.lastCosmeticsRightPaneX > 0) {
                    this.targetCosmeticsPresetsScrollY = Math.min(this.maxCosmeticsPresetsScrollY, Math.max(0.0f, this.targetCosmeticsPresetsScrollY - (float) verticalAmount * 24.0f));
                } else {
                    this.targetCosmeticsScrollY = Math.min(this.maxCosmeticsScrollY, Math.max(0.0f, this.targetCosmeticsScrollY - (float) verticalAmount * 24.0f));
                }

            } else {

                this.targetScrollY = Math.min(this.maxScrollY, Math.max(0.0f, this.targetScrollY - (float) verticalAmount * 24.0f));

            }

            return true;

        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);

    }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (this.isClosing) {
            return false;
        }

        if (keyCode == 344 && this.bindingTarget == null) {
            this.close();
            return true;
        }

        // If listening for bind:

        if (this.bindingTarget != null) {

            if (keyCode == 256 || keyCode == 261) { // ESC or DEL = unbind

                if (this.bindingTarget instanceof BindSetting) {

                    ((BindSetting) this.bindingTarget).a(-1);

                } else if (this.bindingTarget instanceof Module) {

                    ((Module) this.bindingTarget).a(-1);

                    ((Module) this.bindingTarget).b(false);

                }

            } else {

                if (this.bindingTarget instanceof BindSetting) {

                    ((BindSetting) this.bindingTarget).a(keyCode);

                } else if (this.bindingTarget instanceof Module) {

                    ((Module) this.bindingTarget).a(keyCode);

                    ((Module) this.bindingTarget).b(false);

                }

            }

            this.bindingTarget = null;

            return true;

        }

        // Search bar typing:

        // Ctrl + F: open search modal
        if (keyCode == 70 && (modifiers & 2) != 0) {
            this.searchFocused = true;
            this.searchModalOpen = true;
            this.searchSelectedIndex = 0;
            this.searchSelectionStart = 0;
            this.searchSelectionEnd = searchQuery.length();
            this.searchCursor = searchQuery.length();
            return true;
        }

        if (this.searchModalOpen) {
            if (keyCode == 256) { // ESC
                this.searchModalOpen = false;
                this.searchFocused = false;
                return true;
            }
            List<Module> matches = getSearchModalMatches();
            if (keyCode == 265) { // UP
                this.searchSelectedIndex = Math.max(0, this.searchSelectedIndex - 1);
                ensureSearchRowVisible(this.searchSelectedIndex);
                return true;
            }
            if (keyCode == 264) { // DOWN
                this.searchSelectedIndex = Math.min(Math.max(0, matches.size() - 1), this.searchSelectedIndex + 1);
                ensureSearchRowVisible(this.searchSelectedIndex);
                return true;
            }
            if (keyCode == 258 || keyCode == 257) { // TAB or ENTER
                if (!matches.isEmpty()) {
                    int sel = Math.max(0, Math.min(this.searchSelectedIndex, matches.size() - 1));
                    openModuleFromSearch(matches.get(sel));
                    return true;
                }
            }
        }

        if (this.searchFocused || this.searchModalOpen) {

            // Ctrl + A: select all text

            if (keyCode == 65 && (modifiers & 2) != 0) {

                this.searchSelectionStart = 0;

                this.searchSelectionEnd = this.searchQuery.length();

                this.searchCursor = this.searchQuery.length();

                return true;

            }

            if (keyCode == 259) { // Backspace

                if (hasSearchSelection()) {

                    int sA = Math.min(this.searchSelectionStart, this.searchSelectionEnd);

                    int sB = Math.max(this.searchSelectionStart, this.searchSelectionEnd);

                    this.searchQuery = this.searchQuery.substring(0, sA) + this.searchQuery.substring(sB);

                    this.searchCursor = sA;

                    this.searchSelectionStart = -1;

                    this.searchSelectionEnd = -1;

                    this.scrollY = 0.0f;

                    this.targetScrollY = 0.0f;

                } else if (!this.searchQuery.isEmpty() && this.searchCursor > 0) {

                    this.searchQuery = this.searchQuery.substring(0, this.searchCursor - 1) + this.searchQuery.substring(this.searchCursor);

                    this.searchCursor--;

                    this.scrollY = 0.0f;

                    this.targetScrollY = 0.0f;

                }
                this.searchModalOpen = !this.searchQuery.trim().isEmpty();
                return true;

            } else if (keyCode == 261) { // Delete

                if (hasSearchSelection()) {

                    int sA = Math.min(this.searchSelectionStart, this.searchSelectionEnd);

                    int sB = Math.max(this.searchSelectionStart, this.searchSelectionEnd);

                    this.searchQuery = this.searchQuery.substring(0, sA) + this.searchQuery.substring(sB);

                    this.searchCursor = sA;

                    this.searchSelectionStart = -1;

                    this.searchSelectionEnd = -1;

                    this.scrollY = 0.0f;

                    this.targetScrollY = 0.0f;

                } else if (this.searchCursor < this.searchQuery.length()) {

                    this.searchQuery = this.searchQuery.substring(0, this.searchCursor) + this.searchQuery.substring(this.searchCursor + 1);

                    this.scrollY = 0.0f;

                    this.targetScrollY = 0.0f;

                }
                this.searchModalOpen = !this.searchQuery.trim().isEmpty();
                return true;

            } else if (keyCode == 263) { // Left arrow

                if (this.searchCursor > 0) this.searchCursor--;

                this.searchSelectionStart = -1;

                this.searchSelectionEnd = -1;

                return true;

            } else if (keyCode == 262) { // Right arrow

                if (this.searchCursor < this.searchQuery.length()) this.searchCursor++;

                this.searchSelectionStart = -1;

                this.searchSelectionEnd = -1;

                return true;

            } else if (keyCode == 256) { // ESC

                this.searchFocused = false;

                this.searchSelectionStart = -1;

                this.searchSelectionEnd = -1;

                return true;

            }

        }

        // Cosmetics preset input typing:
        if (this.selectedTab == Tab.COSMETICS && this.cosmeticsPresetInputFocused) {
            if (keyCode == 259) { // Backspace
                if (!this.newCosmeticsPresetName.isEmpty()) {
                    this.newCosmeticsPresetName = this.newCosmeticsPresetName.substring(0, this.newCosmeticsPresetName.length() - 1);
                }
                return true;
            } else if (keyCode == 257) { // Enter saves
                String name = this.newCosmeticsPresetName.trim().isEmpty() ? ("Сет " + (CosmeticsConfigManager.getInstance().getPresets().size() + 1)) : this.newCosmeticsPresetName.trim();
                CosmeticsConfigManager.getInstance().saveCurrentAsPreset(name);
                this.newCosmeticsPresetName = "";
                this.cosmeticsPresetInputFocused = false;
                return true;
            } else if (keyCode == 256) { // ESC
                this.cosmeticsPresetInputFocused = false;
                return true;
            }
        }

        // Setup name input typing in Profile Modal:
        if (this.profileModalOpen && this.setupInputFocused) {
            if (keyCode == 259) { // Backspace
                if (!this.newSetupName.isEmpty()) {
                    this.newSetupName = this.newSetupName.substring(0, this.newSetupName.length() - 1);
                }
                return true;
            } else if (keyCode == 257) { // Enter saves setup
                String name = this.newSetupName.trim().isEmpty() ? ("Сетап " + (CustomProfileManager.getInstance().getSetups().size() + 1)) : this.newSetupName.trim();
                CustomProfileManager.getInstance().saveCurrentAsSetup(name);
                this.newSetupName = "";
                this.setupInputFocused = false;
                return true;
            } else if (keyCode == 256) { // ESC unfocuses input
                this.setupInputFocused = false;
                return true;
            }
        }

        if (keyCode == 256) { // ESC closes settings modal or color picker first

            if (this.profileColorPickerMode != 0) {

                this.profileColorPickerMode = 0;

                return true;

            }

            if (this.profileModalOpen) {

                this.profileModalOpen = false;

                return true;

            }

            if (this.activeColorPicker != null) {

                this.activeColorPicker = null;

                return true;

            }

            if (this.settingsPanelOpen) {
                this.settingsPanelOpen = false;
                return true;
            }

            this.close();
            return true;
        }

        if (keyCode == 344) { // Right Shift closes GUI
            this.close();
            return true;
        }

        return false;
    }

        public boolean charTyped(char character, int modifiers) {

        if (this.selectedTab == Tab.COSMETICS && this.cosmeticsPresetInputFocused) {
            if (character >= 32 && character != 127 && this.newCosmeticsPresetName.length() < 24) {
                this.newCosmeticsPresetName += character;
                return true;
            }
        }

        if (this.profileModalOpen && this.setupInputFocused) {
            if (character >= 32 && character != 127 && this.newSetupName.length() < 24) {
                this.newSetupName += character;
                return true;
            }
        }

        if (this.searchFocused || this.searchModalOpen) {
            if (character >= 32 && character != 127) {
                if (hasSearchSelection()) {
                    int sA = Math.min(this.searchSelectionStart, this.searchSelectionEnd);
                    int sB = Math.max(this.searchSelectionStart, this.searchSelectionEnd);
                    this.searchQuery = this.searchQuery.substring(0, sA) + character + this.searchQuery.substring(sB);
                    this.searchCursor = sA + 1;
                    this.searchSelectionStart = -1;
                    this.searchSelectionEnd = -1;
                } else {
                    int cPos = Math.max(0, Math.min(this.searchCursor, this.searchQuery.length()));
                    this.searchQuery = this.searchQuery.substring(0, cPos) + character + this.searchQuery.substring(cPos);
                    this.searchCursor = cPos + 1;
                }
                this.searchSelectedIndex = 0;
                this.targetSearchModalScrollY = 0.0f;
                this.scrollY = 0.0f;
                this.targetScrollY = 0.0f;
                this.searchModalOpen = !this.searchQuery.trim().isEmpty();
                return true;
            }
        }

        return false;

    }

    public static void drawCross(MatrixStack matrices, float cx, float cy, float size, float thickness, int color) {
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        if (a <= 0.0f) return;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float halfThick = thickness * 0.5f;
        float halfSz = size * 0.5f;

        float cos = 0.70710678f;
        float sin = 0.70710678f;
        float dx1 = halfSz * cos, dy1 = halfSz * sin;
        float px1 = -halfThick * sin, py1 = halfThick * cos;

        // Arm 1 (\):
        buffer.vertex(matrix, cx - dx1 - px1, cy - dy1 - py1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx1 - px1, cy + dy1 - py1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx1 + px1, cy + dy1 + py1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx - dx1 - px1, cy - dy1 - py1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx1 + px1, cy + dy1 + py1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx - dx1 + px1, cy - dy1 + py1, 0.0f).color(r, g, b, a);

        // Arm 2 (/):
        float dx2 = halfSz * cos, dy2 = -halfSz * sin;
        float px2 = halfThick * sin, py2 = halfThick * cos;
        buffer.vertex(matrix, cx - dx2 - px2, cy - dy2 - py2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx2 - px2, cy + dy2 - py2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx2 + px2, cy + dy2 + py2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx - dx2 - px2, cy - dy2 - py2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx + dx2 + px2, cy + dy2 + py2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, cx - dx2 + px2, cy - dy2 + py2, 0.0f).color(r, g, b, a);

        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableCull();
    }

    public static void drawCheckmark(MatrixStack matrices, float cx, float cy, float size, float thickness, int color) {
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        if (a <= 0.0f) return;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float ht = thickness * 0.5f;

        float x0 = cx - size * 0.40f, y0 = cy;
        float x1 = cx - size * 0.08f, y1 = cy + size * 0.32f;
        float x2 = cx + size * 0.45f, y2 = cy - size * 0.35f;

        // P0 -> P1
        float dx01 = x1 - x0, dy01 = y1 - y0;
        float len01 = (float) Math.sqrt(dx01 * dx01 + dy01 * dy01);
        if (len01 > 0.001f) {
            float nx01 = -dy01 / len01 * ht, ny01 = dx01 / len01 * ht;
            buffer.vertex(matrix, x0 - nx01, y0 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 - nx01, y1 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx01, y1 + ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x0 - nx01, y0 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx01, y1 + ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x0 + nx01, y0 + ny01, 0.0f).color(r, g, b, a);
        }

        // P1 -> P2
        float dx12 = x2 - x1, dy12 = y2 - y1;
        float len12 = (float) Math.sqrt(dx12 * dx12 + dy12 * dy12);
        if (len12 > 0.001f) {
            float nx12 = -dy12 / len12 * ht, ny12 = dx12 / len12 * ht;
            buffer.vertex(matrix, x1 - nx12, y1 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 - nx12, y2 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 + nx12, y2 + ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 - nx12, y1 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 + nx12, y2 + ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx12, y1 + ny12, 0.0f).color(r, g, b, a);
        }

        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableCull();
    }

    public static void drawChevron(MatrixStack matrices, float cx, float cy, float size, float thickness, int color, boolean open) {
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        if (a <= 0.0f) return;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float ht = thickness * 0.5f;

        float dir = open ? -1.0f : 1.0f;
        float x0 = cx - size * 0.42f, y0 = cy - size * 0.18f * dir;
        float x1 = cx,               y1 = cy + size * 0.22f * dir;
        float x2 = cx + size * 0.42f, y2 = cy - size * 0.18f * dir;

        // Left arm
        float dx01 = x1 - x0, dy01 = y1 - y0;
        float len01 = (float) Math.sqrt(dx01 * dx01 + dy01 * dy01);
        if (len01 > 0.001f) {
            float nx01 = -dy01 / len01 * ht, ny01 = dx01 / len01 * ht;
            buffer.vertex(matrix, x0 - nx01, y0 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 - nx01, y1 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx01, y1 + ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x0 - nx01, y0 - ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx01, y1 + ny01, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x0 + nx01, y0 + ny01, 0.0f).color(r, g, b, a);
        }

        // Right arm
        float dx12 = x2 - x1, dy12 = y2 - y1;
        float len12 = (float) Math.sqrt(dx12 * dx12 + dy12 * dy12);
        if (len12 > 0.001f) {
            float nx12 = -dy12 / len12 * ht, ny12 = dx12 / len12 * ht;
            buffer.vertex(matrix, x1 - nx12, y1 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 - nx12, y2 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 + nx12, y2 + ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 - nx12, y1 - ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2 + nx12, y2 + ny12, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1 + nx12, y1 + ny12, 0.0f).color(r, g, b, a);
        }

        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableCull();
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
    public boolean mouseDragged(Click click, double dx, double dy) {
        return mouseDragged(click.x(), click.y(), click.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return keyPressed(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharInput input) {
        return charTyped((char) input.codepoint(), input.modifiers());
    }

}
