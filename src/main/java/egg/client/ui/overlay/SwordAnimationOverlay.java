package egg.client.ui.overlay;

import egg.client.module.ModuleManager;
import egg.client.module.render.SwordAnimations;
import egg.client.render.ItemRenderHelper;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

public final class SwordAnimationOverlay {
    private static boolean open = false;
    private static final Animation openAnim = new Animation(0.0f, 220L, Easing.EASE_OUT_BACK);

    // Layout dimensions
    private static final float WINDOW_WIDTH = 560.0f;
    private static final float WINDOW_HEIGHT = 356.0f;

    // Active tab: 0 = Presets, 1 = Start, 2 = End, 3 = Options
    private static int activeTab = 0;
    private static final String[] TABS = {"Пресеты", "Старт", "Конец", "Параметры"};
    private static final Animation tabPillX = new Animation(0.0f, 200L, Easing.EASE_OUT_EXPO);

    // 3D Preview camera state
    private static float previewPitch = 0.0f;
    private static float previewYaw = 0.0f;
    private static boolean draggingPreview = false;
    private static boolean isPaused = false;
    private static float pausedProgress = 0.5f;

    // Slider dragging
    private static int activeSlider = -1; // -1 = none

    public static boolean isOpen() {
        return open;
    }

    public static float getAlpha() {
        return openAnim.getValue();
    }

    public static void open() {
        open = true;
        openAnim.setTarget(1.0f);
        GuiSoundManager.playClick();
    }

    public static void close() {
        if (!open) return;
        open = false;
        openAnim.setTarget(0.0f);
        draggingPreview = false;
        activeSlider = -1;
        GuiSoundManager.playClick();
    }

    public static void toggle() {
        if (open) close();
        else open();
    }

    public static void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float animProgress = open ? Math.max(0.05f, openAnim.getValue()) : openAnim.getValue();
        if (animProgress <= 0.005f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();

        // 1. Dark Backdrop Dimmer
        Render2D.drawRect(context, 0, 0, screenW, screenH, ColorUtil.rgba(0, 0, 0, (int) (165 * animProgress)));

        // Window position & scale
        float winX = (screenW - WINDOW_WIDTH) / 2.0f;
        float winY = (screenH - WINDOW_HEIGHT) / 2.0f;

        context.getMatrices().pushMatrix();
        float scale = 0.94f + 0.06f * animProgress;
        context.getMatrices().translate(screenW / 2.0f, screenH / 2.0f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-screenW / 2.0f, -screenH / 2.0f);

        // 2. Card Shadow & Background
        Render2D.drawShadow(context, winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 14.0f, 20.0f, ColorUtil.rgba(0, 0, 0, (int) (200 * animProgress)));
        Render2D.drawRoundedGradientRect(context, winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 14.0f,
                ColorUtil.rgba(20, 22, 30, (int) (250 * animProgress)),
                ColorUtil.rgba(14, 16, 22, (int) (254 * animProgress)));
        Render2D.drawRoundedOutline(context, winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT, 14.0f, 1.0f,
                ColorUtil.rgba(255, 255, 255, (int) (32 * animProgress)));

        // 3. Header
        float headerH = 38.0f;
        ClientFont.draw(context, "Sword Animations", winX + 16.0f, winY + 11.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(0xFFFFFFFF, animProgress));
        ClientFont.draw(context, "Интерактивная настройка положения и анимаций взмаха", winX + 16.0f, winY + 23.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, animProgress * 0.9f));

        // Close Button [ ✕ ]
        float closeBtnX = winX + WINDOW_WIDTH - 28.0f;
        float closeBtnY = winY + 10.0f;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 18.0f && mouseY >= closeBtnY && mouseY <= closeBtnY + 18.0f;
        int closeColor = closeHover ? ColorUtil.rgba(255, 80, 80, (int) (255 * animProgress)) : ColorUtil.rgba(180, 185, 200, (int) (200 * animProgress));
        Render2D.drawRoundedRect(context, closeBtnX, closeBtnY, 18.0f, 18.0f, 4.0f, closeHover ? ColorUtil.rgba(255, 255, 255, (int) (25 * animProgress)) : 0);
        ClientFont.draw(context, "✕", closeBtnX + 5.0f, closeBtnY + 4.5f, ClientFont.SF_BOLD, closeColor);

        // Header separator
        Render2D.drawRect(context, winX + 14.0f, winY + headerH, WINDOW_WIDTH - 28.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * animProgress)));

        // 4. Left Panel: 3D Preview (Width: 215.0f, Height: 300.0f)
        float previewX = winX + 14.0f;
        float previewY = winY + headerH + 8.0f;
        float previewW = 215.0f;
        float previewH = WINDOW_HEIGHT - headerH - 18.0f;
        try {
            render3DPreview(context, mc, previewX, previewY, previewW, previewH, mouseX, mouseY, animProgress);
        } catch (Throwable ignored) {}

        // 5. Right Panel: Tabs & Controls (Width: 310.0f, Height: 300.0f)
        float controlsX = winX + 238.0f;
        float controlsY = winY + headerH + 8.0f;
        float controlsW = WINDOW_WIDTH - 238.0f - 14.0f;
        float controlsH = previewH;
        renderControls(context, controlsX, controlsY, controlsW, controlsH, mouseX, mouseY, animProgress);

        context.getMatrices().popMatrix();
    }

    private static void render3DPreview(DrawContext context, MinecraftClient mc, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        // Preview box background
        Render2D.drawRoundedRect(context, x, y, w, h, 10.0f, ColorUtil.rgba(12, 14, 20, (int) (240 * alpha)));
        Render2D.drawRoundedOutline(context, x, y, w, h, 10.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (24 * alpha)));

        // Top badges: "3D ПРЕВЬЮ" + Reset button
        ClientFont.draw(context, "3D ПРЕВЬЮ", x + 10.0f, y + 8.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));

        float rstW = 48.0f, rstH = 14.0f;
        float rstX = x + w - rstW - 8.0f;
        float rstY = y + 7.0f;
        boolean rstHover = mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH;
        Render2D.drawRoundedRect(context, rstX, rstY, rstW, rstH, 4.0f,
                rstHover ? ColorUtil.rgba(255, 255, 255, (int) (35 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha)));
        ClientFont.draw(context, "↺ Сброс", rstX + 6.0f, rstY + 3.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(0xFFD0D5E0, alpha));

        // Animation calculations
        SwordAnimations anim = ModuleManager.getInstance().getModule(SwordAnimations.class);
        float swingSpeed = anim != null ? anim.speed : 1.8f;
        float durationMs = 1000.0f / Math.max(0.3f, swingSpeed);
        long now = System.currentTimeMillis();
        float swingProgress = isPaused ? pausedProgress : (float) ((now % (long) durationMs) / durationMs);

        SwordAnimations.Transform t = anim != null
                ? anim.calculateTransform(swingProgress)
                : new SwordAnimations.Transform(0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0f);

        // Get held item or Diamond Sword
        ItemStack stack = mc.player != null && !mc.player.getMainHandStack().isEmpty()
                ? mc.player.getMainHandStack()
                : new ItemStack(Items.DIAMOND_SWORD);

        // Viewport Scissor
        context.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));

        // 3D Matrix rendering
        MatrixStack matrices = new MatrixStack();
        matrices.push();

        float centerX = x + w / 2.0f;
        float centerY = y + h / 2.0f + 14.0f;
        matrices.translate(centerX, centerY, 300.0f);

        // User orbit rotation
        matrices.multiply(new Quaternionf().rotateX((float) Math.toRadians(previewPitch)).rotateY((float) Math.toRadians(previewYaw)));

        // Scale
        float baseScale = 90.0f * t.scale;
        matrices.scale(baseScale, -baseScale, baseScale);

        // Custom Sword Animation Transforms:
        // 1. Pivot / Anchor
        matrices.translate(t.anchorX, t.anchorY, t.anchorZ);
        // 2. Move & Rotate
        matrices.translate(t.moveX, t.moveY, t.moveZ);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(t.rotX),
                (float) Math.toRadians(t.rotY),
                (float) Math.toRadians(t.rotZ)
        ));
        // 3. Un-anchor
        matrices.translate(-t.anchorX, -t.anchorY, -t.anchorZ);

        // Standard first person item anchor
        matrices.translate(0.56f, -0.52f, -0.72f);

        // Render 3D item with lighting!
        try {
            ItemRenderHelper.renderItem(
                    mc.player,
                    stack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    matrices,
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    15728880
            );
        } catch (Throwable ignored) {}

        matrices.pop();
        context.disableScissor();

        // Bottom status & play/pause button
        float bottomBarH = 22.0f;
        float bottomBarY = y + h - bottomBarH - 6.0f;
        Render2D.drawRoundedRect(context, x + 8.0f, bottomBarY, w - 16.0f, bottomBarH, 5.0f, ColorUtil.rgba(20, 24, 32, (int) (220 * alpha)));

        String itemName = stack.getName().getString();
        if (itemName.length() > 18) itemName = itemName.substring(0, 17) + "…";
        ClientFont.draw(context, itemName, x + 16.0f, bottomBarY + 6.5f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

        // Play/Pause button
        float ppW = 20.0f, ppH = 14.0f;
        float ppX = x + w - 16.0f - ppW;
        float ppY = bottomBarY + 4.0f;
        boolean ppHover = mouseX >= ppX && mouseX <= ppX + ppW && mouseY >= ppY && mouseY <= ppY + ppH;
        Render2D.drawRoundedRect(context, ppX, ppY, ppW, ppH, 4.0f, ppHover ? ColorUtil.rgba(255, 255, 255, (int) (35 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (15 * alpha)));
        ClientFont.draw(context, isPaused ? "▶" : "❚❚", ppX + 5.5f, ppY + 2.5f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha));
    }

    private static void renderControls(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        SwordAnimations anim = ModuleManager.getInstance().getModule(SwordAnimations.class);
        if (anim == null) return;

        // 1. Tab Bar
        float tabW = (w - 12.0f) / TABS.length;
        float tabH = 22.0f;
        Render2D.drawRoundedRect(context, x, y, w, tabH, 6.0f, ColorUtil.rgba(14, 16, 22, (int) (230 * alpha)));

        float activePillTarget = x + 2.0f + (activeTab * tabW);
        tabPillX.setTarget(activePillTarget);
        Render2D.drawRoundedRect(context, tabPillX.getValue(), y + 2.0f, tabW - 4.0f, tabH - 4.0f, 5.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * 0.85f));

        for (int i = 0; i < TABS.length; i++) {
            float tx = x + 2.0f + (i * tabW);
            float strW = ClientFont.getWidth(TABS[i], ClientFont.SF_BOLD);
            int tabCol = (i == activeTab) ? 0xFFFFFFFF : ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha * 0.9f);
            ClientFont.draw(context, TABS[i], tx + (tabW - 4.0f - strW) / 2.0f, y + 6.5f, ClientFont.SF_BOLD, tabCol);
        }

        // 2. Active Tab Content Area
        float contentY = y + tabH + 8.0f;
        float contentH = h - tabH - 8.0f;

        switch (activeTab) {
            case 0 -> renderPresetsTab(context, anim, x, contentY, w, contentH, mouseX, mouseY, alpha);
            case 1 -> renderTransformTab(context, anim, true, x, contentY, w, contentH, mouseX, mouseY, alpha);
            case 2 -> renderTransformTab(context, anim, false, x, contentY, w, contentH, mouseX, mouseY, alpha);
            case 3 -> renderOptionsTab(context, anim, x, contentY, w, contentH, mouseX, mouseY, alpha);
        }
    }

    private static void renderPresetsTab(DrawContext context, SwordAnimations anim, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        SwordAnimations.Preset[] presets = SwordAnimations.Preset.values();
        float cardGap = 6.0f;
        float cardW = (w - cardGap) / 2.0f;
        float cardH = 58.0f;

        for (int i = 0; i < presets.length; i++) {
            SwordAnimations.Preset p = presets[i];
            int col = i % 2;
            int row = i / 2;
            float cx = x + col * (cardW + cardGap);
            float cy = y + row * (cardH + cardGap);

            boolean isCurrent = anim.currentPreset == p;
            boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;

            int bg = isCurrent
                    ? ColorUtil.withAlpha(Theme.getAccent(), (int) (60 * alpha))
                    : (hover ? ColorUtil.rgba(255, 255, 255, (int) (22 * alpha)) : ColorUtil.rgba(14, 16, 22, (int) (200 * alpha)));
            Render2D.drawRoundedRect(context, cx, cy, cardW, cardH, 7.0f, bg);

            int border = isCurrent
                    ? ColorUtil.withAlphaFloat(Theme.getAccent(), alpha)
                    : (hover ? ColorUtil.rgba(255, 255, 255, (int) (40 * alpha)) : ColorUtil.rgba(255, 255, 255, (int) (18 * alpha)));
            Render2D.drawRoundedOutline(context, cx, cy, cardW, cardH, 7.0f, 1.0f, border);

            // Title
            ClientFont.draw(context, p.title, cx + 8.0f, cy + 8.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(isCurrent ? 0xFFFFFFFF : Theme.TEXT_MUTED, alpha));
            // Description
            ClientFont.draw(context, p.desc, cx + 8.0f, cy + 22.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_DIM, alpha * 0.85f));

            // Badge / speed info
            String tag = String.format("Speed: %.1fx", p.speed);
            ClientFont.draw(context, tag, cx + 8.0f, cy + 38.0f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha * 0.9f));

            if (isCurrent) {
                ClientFont.draw(context, "✓", cx + cardW - 16.0f, cy + 8.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
            }
        }
    }

    private static void renderTransformTab(DrawContext context, SwordAnimations anim, boolean isStart, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        float startY = y + 4.0f;
        float rowH = 38.0f;

        // Position Sliders
        float mX = isStart ? anim.startMoveX : anim.endMoveX;
        float mY = isStart ? anim.startMoveY : anim.endMoveY;
        float mZ = isStart ? anim.startMoveZ : anim.endMoveZ;

        float rX = isStart ? anim.startRotX : anim.endRotX;
        float rY = isStart ? anim.startRotY : anim.endRotY;
        float rZ = isStart ? anim.startRotZ : anim.endRotZ;

        int baseIdx = isStart ? 0 : 6;

        renderCustomSlider(context, "Смещение X (Move X)", mX, -2.5f, 2.5f, 0.05f, "", x, startY, w, mouseX, mouseY, alpha, baseIdx);
        renderCustomSlider(context, "Смещение Y (Move Y)", mY, -2.5f, 2.5f, 0.05f, "", x, startY + rowH, w, mouseX, mouseY, alpha, baseIdx + 1);
        renderCustomSlider(context, "Смещение Z (Move Z)", mZ, -2.5f, 2.5f, 0.05f, "", x, startY + rowH * 2, w, mouseX, mouseY, alpha, baseIdx + 2);

        renderCustomSlider(context, "Вращение X (Pitch)", rX, -360.0f, 360.0f, 5.0f, "°", x, startY + rowH * 3 + 6.0f, w, mouseX, mouseY, alpha, baseIdx + 3);
        renderCustomSlider(context, "Вращение Y (Yaw)", rY, -360.0f, 360.0f, 5.0f, "°", x, startY + rowH * 4 + 6.0f, w, mouseX, mouseY, alpha, baseIdx + 4);
        renderCustomSlider(context, "Вращение Z (Roll)", rZ, -360.0f, 360.0f, 5.0f, "°", x, startY + rowH * 5 + 6.0f, w, mouseX, mouseY, alpha, baseIdx + 5);
    }

    private static void renderOptionsTab(DrawContext context, SwordAnimations anim, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        float startY = y + 4.0f;
        float rowH = 38.0f;

        renderCustomSlider(context, "Точка опоры X (Anchor X)", anim.anchorX, -2.0f, 2.0f, 0.05f, "", x, startY, w, mouseX, mouseY, alpha, 12);
        renderCustomSlider(context, "Точка опоры Y (Anchor Y)", anim.anchorY, -2.0f, 2.0f, 0.05f, "", x, startY + rowH, w, mouseX, mouseY, alpha, 13);
        renderCustomSlider(context, "Точка опоры Z (Anchor Z)", anim.anchorZ, -2.0f, 2.0f, 0.05f, "", x, startY + rowH * 2, w, mouseX, mouseY, alpha, 14);

        renderCustomSlider(context, "Скорость взмаха (Speed)", anim.speed, 0.5f, 3.5f, 0.1f, "x", x, startY + rowH * 3 + 4.0f, w, mouseX, mouseY, alpha, 15);
        renderCustomSlider(context, "Размер меча (Scale)", anim.scale, 0.5f, 2.0f, 0.05f, "x", x, startY + rowH * 4 + 4.0f, w, mouseX, mouseY, alpha, 16);

        // Toggles: SwingBack and OnlyAura
        float toggleY = startY + rowH * 5 + 8.0f;
        float togW = (w - 8.0f) / 2.0f;
        renderToggle(context, "Возврат (Swing Back)", anim.swingBack, x, toggleY, togW, mouseX, mouseY, alpha);
        renderToggle(context, "Только с Киллаурой", anim.onlyAura, x + togW + 8.0f, toggleY, togW, mouseX, mouseY, alpha);
    }

    private static void renderCustomSlider(DrawContext context, String label, float val, float min, float max, float step, String unit,
                                           float x, float y, float w, float mouseX, float mouseY, float alpha, int sliderIdx) {
        ClientFont.draw(context, label, x, y, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

        String valStr = String.format("%.2f%s", val, unit);
        if (unit.equals("°")) valStr = String.format("%.0f°", val);
        else if (unit.equals("x")) valStr = String.format("%.1fx", val);
        float vw = ClientFont.getWidth(valStr, ClientFont.SF_BOLD);
        ClientFont.draw(context, valStr, x + w - vw, y, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));

        float trackY = y + 14.0f;
        float trackH = 4.0f;
        Render2D.drawRoundedRect(context, x, trackY, w, trackH, 2.0f, ColorUtil.rgba(255, 255, 255, (int) (30 * alpha)));

        float ratio = MathHelper.clamp((val - min) / (max - min), 0.0f, 1.0f);
        float fillW = w * ratio;
        if (fillW > 0.0f) {
            Render2D.drawRoundedRect(context, x, trackY, fillW, trackH, 2.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
        }

        float knobX = x + fillW;
        float knobY = trackY + trackH / 2.0f;
        Render2D.drawCircle(context, knobX, knobY, 4.5f, ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));
    }

    private static void renderToggle(DrawContext context, String label, boolean state, float x, float y, float w, float mouseX, float mouseY, float alpha) {
        float h = 24.0f;
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = state
                ? ColorUtil.withAlpha(Theme.getAccent(), (int) (50 * alpha))
                : (hover ? ColorUtil.rgba(255, 255, 255, (int) (20 * alpha)) : ColorUtil.rgba(14, 16, 22, (int) (180 * alpha)));
        Render2D.drawRoundedRect(context, x, y, w, h, 6.0f, bg);
        Render2D.drawRoundedOutline(context, x, y, w, h, 6.0f, 1.0f, state ? ColorUtil.withAlphaFloat(Theme.getAccent(), alpha) : ColorUtil.rgba(255, 255, 255, (int) (20 * alpha)));

        ClientFont.draw(context, label, x + 8.0f, y + 7.5f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(state ? 0xFFFFFFFF : Theme.TEXT_MUTED, alpha));

        // Switch pill
        float swW = 24.0f, swH = 12.0f;
        float swX = x + w - swW - 8.0f;
        float swY = y + 6.0f;
        Render2D.drawRoundedRect(context, swX, swY, swW, swH, 6.0f, state ? ColorUtil.withAlphaFloat(Theme.getAccent(), alpha) : ColorUtil.rgba(255, 255, 255, (int) (40 * alpha)));
        float dotX = state ? (swX + swW - 6.0f) : (swX + 6.0f);
        Render2D.drawCircle(context, dotX, swY + swH / 2.0f, 4.0f, ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));
    }

    public static boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!open || openAnim.getValue() < 0.70f) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();

        float winX = (screenW - WINDOW_WIDTH) / 2.0f;
        float winY = (screenH - WINDOW_HEIGHT) / 2.0f;

        // Click outside window -> close
        if (mouseX < winX || mouseX > winX + WINDOW_WIDTH || mouseY < winY || mouseY > winY + WINDOW_HEIGHT) {
            close();
            return true;
        }

        // Close button [ ✕ ]
        float closeBtnX = winX + WINDOW_WIDTH - 28.0f;
        float closeBtnY = winY + 10.0f;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 18.0f && mouseY >= closeBtnY && mouseY <= closeBtnY + 18.0f) {
            close();
            return true;
        }

        // 3D Preview area clicks
        float previewX = winX + 14.0f;
        float previewY = winY + 38.0f + 8.0f;
        float previewW = 215.0f;
        float previewH = WINDOW_HEIGHT - 38.0f - 18.0f;

        // Reset rotation button
        float rstW = 48.0f, rstH = 14.0f;
        float rstX = previewX + previewW - rstW - 8.0f;
        float rstY = previewY + 7.0f;
        if (mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH) {
            previewPitch = 0.0f;
            previewYaw = 0.0f;
            GuiSoundManager.playClick();
            return true;
        }

        // Play / pause button
        float bottomBarH = 22.0f;
        float bottomBarY = previewY + previewH - bottomBarH - 6.0f;
        float ppW = 20.0f, ppH = 14.0f;
        float ppX = previewX + previewW - 16.0f - ppW;
        float ppY = bottomBarY + 4.0f;
        if (mouseX >= ppX && mouseX <= ppX + ppW && mouseY >= ppY && mouseY <= ppY + ppH) {
            isPaused = !isPaused;
            GuiSoundManager.playClick();
            return true;
        }

        // Drag inside 3D preview
        if (mouseX >= previewX && mouseX <= previewX + previewW && mouseY >= previewY && mouseY <= previewY + previewH) {
            draggingPreview = true;
            return true;
        }

        // Controls area clicks
        float controlsX = winX + 238.0f;
        float controlsY = winY + 38.0f + 8.0f;
        float controlsW = WINDOW_WIDTH - 238.0f - 14.0f;
        float tabW = (controlsW - 12.0f) / TABS.length;
        float tabH = 22.0f;

        // Tabs click
        if (mouseY >= controlsY && mouseY <= controlsY + tabH) {
            for (int i = 0; i < TABS.length; i++) {
                float tx = controlsX + 2.0f + (i * tabW);
                if (mouseX >= tx && mouseX <= tx + tabW) {
                    activeTab = i;
                    GuiSoundManager.playClick();
                    return true;
                }
            }
        }

        // Tab Content clicks
        float contentY = controlsY + tabH + 8.0f;
        SwordAnimations anim = ModuleManager.getInstance().getModule(SwordAnimations.class);
        if (anim == null) return true;

        if (activeTab == 0) {
            // Presets tab
            SwordAnimations.Preset[] presets = SwordAnimations.Preset.values();
            float cardGap = 6.0f;
            float cardW = (controlsW - cardGap) / 2.0f;
            float cardH = 58.0f;

            for (int i = 0; i < presets.length; i++) {
                int col = i % 2;
                int row = i / 2;
                float cx = controlsX + col * (cardW + cardGap);
                float cy = contentY + row * (cardH + cardGap);
                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    anim.applyPreset(presets[i]);
                    GuiSoundManager.playClick();
                    return true;
                }
            }
        } else if (activeTab == 1 || activeTab == 2) {
            // Start or End tab
            float startY = contentY + 4.0f;
            float rowH = 38.0f;
            int baseIdx = activeTab == 1 ? 0 : 6;
            for (int s = 0; s < 6; s++) {
                float sy = startY + s * rowH + (s >= 3 ? 6.0f : 0.0f);
                if (mouseY >= sy + 10.0f && mouseY <= sy + 24.0f && mouseX >= controlsX - 4.0f && mouseX <= controlsX + controlsW + 4.0f) {
                    activeSlider = baseIdx + s;
                    updateSliderValue(anim, activeSlider, mouseX, controlsX, controlsW);
                    return true;
                }
            }
        } else if (activeTab == 3) {
            // Options tab
            float startY = contentY + 4.0f;
            float rowH = 38.0f;
            for (int s = 0; s < 5; s++) {
                float sy = startY + s * rowH + (s >= 3 ? 4.0f : 0.0f);
                if (mouseY >= sy + 10.0f && mouseY <= sy + 24.0f && mouseX >= controlsX - 4.0f && mouseX <= controlsX + controlsW + 4.0f) {
                    activeSlider = 12 + s;
                    updateSliderValue(anim, activeSlider, mouseX, controlsX, controlsW);
                    return true;
                }
            }

            // Toggles
            float toggleY = startY + rowH * 5 + 8.0f;
            float togW = (controlsW - 8.0f) / 2.0f;
            if (mouseY >= toggleY && mouseY <= toggleY + 24.0f) {
                if (mouseX >= controlsX && mouseX <= controlsX + togW) {
                    anim.swingBack = !anim.swingBack;
                    anim.saveConfig();
                    GuiSoundManager.playClick();
                    return true;
                } else if (mouseX >= controlsX + togW + 8.0f && mouseX <= controlsX + controlsW) {
                    anim.onlyAura = !anim.onlyAura;
                    anim.saveConfig();
                    GuiSoundManager.playClick();
                    return true;
                }
            }
        }

        return true;
    }

    public static void mouseReleased() {
        draggingPreview = false;
        activeSlider = -1;
    }

    public static boolean mouseDragged(float mouseX, float mouseY, int button, double dx, double dy) {
        if (!open) return false;

        if (draggingPreview) {
            previewYaw += (float) dx * 0.9f;
            previewPitch = MathHelper.clamp(previewPitch + (float) dy * 0.9f, -80.0f, 80.0f);
            return true;
        }

        if (activeSlider != -1) {
            MinecraftClient mc = MinecraftClient.getInstance();
            float screenW = mc.getWindow().getScaledWidth();
            float winX = (screenW - WINDOW_WIDTH) / 2.0f;
            float controlsX = winX + 238.0f;
            float controlsW = WINDOW_WIDTH - 238.0f - 14.0f;

            SwordAnimations anim = ModuleManager.getInstance().getModule(SwordAnimations.class);
            if (anim != null) {
                updateSliderValue(anim, activeSlider, mouseX, controlsX, controlsW);
            }
            return true;
        }

        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!open) return false;
        if (isPaused) {
            pausedProgress = MathHelper.clamp(pausedProgress + (float) amount * 0.05f, 0.0f, 1.0f);
            return true;
        }
        return false;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    private static void updateSliderValue(SwordAnimations anim, int sliderIdx, float mouseX, float x, float w) {
        float ratio = MathHelper.clamp((mouseX - x) / w, 0.0f, 1.0f);

        switch (sliderIdx) {
            // Start Move & Rot (0..5)
            case 0 -> anim.startMoveX = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 1 -> anim.startMoveY = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 2 -> anim.startMoveZ = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 3 -> anim.startRotX = snap(-360.0f + ratio * 720.0f, 5.0f);
            case 4 -> anim.startRotY = snap(-360.0f + ratio * 720.0f, 5.0f);
            case 5 -> anim.startRotZ = snap(-360.0f + ratio * 720.0f, 5.0f);

            // End Move & Rot (6..11)
            case 6 -> anim.endMoveX = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 7 -> anim.endMoveY = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 8 -> anim.endMoveZ = snap(-2.5f + ratio * 5.0f, 0.05f);
            case 9 -> anim.endRotX = snap(-360.0f + ratio * 720.0f, 5.0f);
            case 10 -> anim.endRotY = snap(-360.0f + ratio * 720.0f, 5.0f);
            case 11 -> anim.endRotZ = snap(-360.0f + ratio * 720.0f, 5.0f);

            // Options (12..16)
            case 12 -> anim.anchorX = snap(-2.0f + ratio * 4.0f, 0.05f);
            case 13 -> anim.anchorY = snap(-2.0f + ratio * 4.0f, 0.05f);
            case 14 -> anim.anchorZ = snap(-2.0f + ratio * 4.0f, 0.05f);
            case 15 -> anim.speed = snap(0.5f + ratio * 3.0f, 0.1f);
            case 16 -> anim.scale = snap(0.5f + ratio * 1.5f, 0.05f);
        }

        anim.saveConfig();
    }

    private static float snap(float val, float step) {
        return Math.round(val / step) * step;
    }
}
