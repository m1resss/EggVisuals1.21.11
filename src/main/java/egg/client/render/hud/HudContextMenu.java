package egg.client.render.hud;

import egg.client.config.ConfigManager;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.msdf.Fonts;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HudContextMenu {

    public interface MenuItem {
        String getLabel();
        boolean isMode();
        String getValue();
        boolean isChecked();
        void onClick();
    }

    public static class ModeItem implements MenuItem {
        private final String label;
        private final Supplier<String> valueSupplier;
        private final Runnable onCycle;

        public ModeItem(String label, Supplier<String> valueSupplier, Runnable onCycle) {
            this.label = label;
            this.valueSupplier = valueSupplier;
            this.onCycle = onCycle;
        }

        @Override public String getLabel() { return label; }
        @Override public boolean isMode() { return true; }
        @Override public String getValue() { return valueSupplier.get(); }
        @Override public boolean isChecked() { return true; }
        @Override public void onClick() { onCycle.run(); }
    }

    public static class ToggleItem implements MenuItem {
        private final String label;
        private final BooleanSupplier stateSupplier;
        private final Runnable onToggle;

        public ToggleItem(String label, BooleanSupplier stateSupplier, Runnable onToggle) {
            this.label = label;
            this.stateSupplier = stateSupplier;
            this.onToggle = onToggle;
        }

        @Override public String getLabel() { return label; }
        @Override public boolean isMode() { return false; }
        @Override public String getValue() { return ""; }
        @Override public boolean isChecked() { return stateSupplier.getAsBoolean(); }
        @Override public void onClick() { onToggle.run(); }
    }

    private static HudContextMenu activeMenu = null;

    private final String title;
    private final List<MenuItem> items = new ArrayList<>();

    private boolean open = false;
    private float x = 0.0f;
    private float y = 0.0f;
    private float scale = 1.0f;

    public HudContextMenu(String title) {
        this.title = title;
    }

    public static void closeAll() {
        if (activeMenu != null) {
            activeMenu.close();
        }
    }

    public void addMode(String label, Supplier<String> valueSupplier, Runnable onCycle) {
        items.add(new ModeItem(label, valueSupplier, onCycle));
    }

    public void addToggle(String label, BooleanSupplier stateSupplier, Runnable onToggle) {
        items.add(new ToggleItem(label, stateSupplier, onToggle));
    }

    public boolean isOpen() {
        return open;
    }

    public void setScale(float scale) {
        this.scale = Math.clamp(scale, 0.5f, 2.0f);
    }

    public float getScale() {
        return this.scale;
    }

    public void open(float clickX, float clickY) {
        openSmart(null, clickX, clickY, 0.0f, 0.0f, clickX, clickY, this.scale);
    }

    public void open(float clickX, float clickY, float scale) {
        openSmart(null, clickX, clickY, 0.0f, 0.0f, clickX, clickY, scale);
    }

    public void openSmart(String elementId, float elemX, float elemY, float elemW, float elemH, float clickX, float clickY, float scale) {
        if (activeMenu != null && activeMenu != this) {
            activeMenu.close();
        }
        activeMenu = this;
        this.open = true;
        setScale(scale);

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 960;
        int screenH = mc != null && mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 540;

        float mw = computeWidth();
        float mh = computeHeight();
        float visualW = mw * this.scale;
        float visualH = mh * this.scale;

        float pad = 6.0f;
        float gap = 6.0f;

        if (elemW <= 1.0f || elemH <= 1.0f) {
            this.x = Math.clamp(clickX, pad, Math.max(pad, screenW - visualW - pad));
            this.y = Math.clamp(clickY, pad, Math.max(pad, screenH - visualH - pad));
            return;
        }

        List<HudSnapHelper.HudElementBounds> others = HudSnapHelper.getOtherElements(elementId);

        boolean isWide = elemW >= visualW * 1.25f || elemW >= elemH * 2.2f;
        float elemCenterX = elemX + elemW * 0.5f;
        float elemCenterY = elemY + elemH * 0.5f;

        float bestScore = Float.MAX_VALUE;
        float bestX = Math.clamp(elemX + elemW + gap, pad, Math.max(pad, screenW - visualW - pad));
        float bestY = Math.clamp(elemY, pad, Math.max(pad, screenH - visualH - pad));

        for (int side = 0; side < 4; side++) {
            float baseSidePenalty;
            if (isWide) {
                if (elemCenterY >= screenH * 0.5f) {
                    if (side == 3) baseSidePenalty = 0.0f;
                    else if (side == 2) baseSidePenalty = 20.0f;
                    else if (side == 0) baseSidePenalty = 50.0f;
                    else baseSidePenalty = 70.0f;
                } else {
                    if (side == 2) baseSidePenalty = 0.0f;
                    else if (side == 3) baseSidePenalty = 20.0f;
                    else if (side == 0) baseSidePenalty = 50.0f;
                    else baseSidePenalty = 70.0f;
                }
            } else {
                boolean rightHalf = elemCenterX >= screenW * 0.5f;
                boolean bottomHalf = elemCenterY >= screenH * 0.5f;
                int primarySide = rightHalf ? 1 : 0;
                int secondarySide = bottomHalf ? 3 : 2;
                int tertiarySide = bottomHalf ? 2 : 3;

                if (side == primarySide) baseSidePenalty = 0.0f;
                else if (side == secondarySide) baseSidePenalty = 15.0f;
                else if (side == tertiarySide) baseSidePenalty = 30.0f;
                else baseSidePenalty = 60.0f;
            }

            if (side == 0) {
                float rawX = elemX + elemW + gap;
                float[] yVariants = new float[]{elemY, elemY + (elemH - visualH) * 0.5f, clickY - 14.0f};
                for (float rawY : yVariants) {
                    float clampedX = Math.clamp(rawX, pad, Math.max(pad, screenW - visualW - pad));
                    float clampedY = Math.clamp(rawY, pad, Math.max(pad, screenH - visualH - pad));

                    float score = evaluateCandidate(clampedX, clampedY, rawX, rawY, visualW, visualH, elemX, elemY, elemW, elemH, others, screenW, screenH, pad, baseSidePenalty, clickX, clickY);
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = clampedX;
                        bestY = clampedY;
                    }
                }
            } else if (side == 1) {
                float rawX = elemX - gap - visualW;
                float[] yVariants = new float[]{elemY, elemY + (elemH - visualH) * 0.5f, clickY - 14.0f};
                for (float rawY : yVariants) {
                    float clampedX = Math.clamp(rawX, pad, Math.max(pad, screenW - visualW - pad));
                    float clampedY = Math.clamp(rawY, pad, Math.max(pad, screenH - visualH - pad));

                    float score = evaluateCandidate(clampedX, clampedY, rawX, rawY, visualW, visualH, elemX, elemY, elemW, elemH, others, screenW, screenH, pad, baseSidePenalty, clickX, clickY);
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = clampedX;
                        bestY = clampedY;
                    }
                }
            } else if (side == 2) {
                float rawY = elemY + elemH + gap;
                float[] xVariants = new float[]{elemX, elemX + (elemW - visualW) * 0.5f, elemX + elemW - visualW};
                for (float rawX : xVariants) {
                    float clampedX = Math.clamp(rawX, pad, Math.max(pad, screenW - visualW - pad));
                    float clampedY = Math.clamp(rawY, pad, Math.max(pad, screenH - visualH - pad));

                    float score = evaluateCandidate(clampedX, clampedY, rawX, rawY, visualW, visualH, elemX, elemY, elemW, elemH, others, screenW, screenH, pad, baseSidePenalty, clickX, clickY);
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = clampedX;
                        bestY = clampedY;
                    }
                }
            } else {
                float rawY = elemY - gap - visualH;
                float[] xVariants = new float[]{elemX, elemX + (elemW - visualW) * 0.5f, elemX + elemW - visualW};
                for (float rawX : xVariants) {
                    float clampedX = Math.clamp(rawX, pad, Math.max(pad, screenW - visualW - pad));
                    float clampedY = Math.clamp(rawY, pad, Math.max(pad, screenH - visualH - pad));

                    float score = evaluateCandidate(clampedX, clampedY, rawX, rawY, visualW, visualH, elemX, elemY, elemW, elemH, others, screenW, screenH, pad, baseSidePenalty, clickX, clickY);
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = clampedX;
                        bestY = clampedY;
                    }
                }
            }
        }

        this.x = bestX;
        this.y = bestY;
    }

    private static float evaluateCandidate(float clampedX, float clampedY, float rawX, float rawY,
                                          float visualW, float visualH,
                                          float elemX, float elemY, float elemW, float elemH,
                                          List<HudSnapHelper.HudElementBounds> others,
                                          int screenW, int screenH, float pad,
                                          float baseSidePenalty, float clickX, float clickY) {
        float outLeft = Math.max(0.0f, pad - rawX);
        float outTop = Math.max(0.0f, pad - rawY);
        float outRight = Math.max(0.0f, (rawX + visualW) - (screenW - pad));
        float outBottom = Math.max(0.0f, (rawY + visualH) - (screenH - pad));
        float screenViolation = outLeft + outTop + outRight + outBottom;

        float parentOverlap = getOverlapArea(clampedX, clampedY, visualW, visualH, elemX, elemY, elemW, elemH);

        float otherOverlap = 0.0f;
        for (HudSnapHelper.HudElementBounds other : others) {
            float ov = getOverlapArea(clampedX, clampedY, visualW, visualH, other.x(), other.y(), other.w(), other.h());
            if (ov > 0.001f) {
                otherOverlap += ov;
            }
        }

        float score = baseSidePenalty;
        if (parentOverlap > 0.001f) {
            score += 2_000_000.0f + parentOverlap * 200.0f;
        }
        if (otherOverlap > 0.001f) {
            score += 200_000.0f + otherOverlap * 50.0f;
        }
        if (screenViolation > 0.001f) {
            score += 50_000.0f + screenViolation * 100.0f;
        }

        float dist = (float) Math.hypot((clampedX + visualW * 0.5f) - clickX, (clampedY + visualH * 0.5f) - clickY);
        score += dist * 0.05f;

        return score;
    }

    private static float getOverlapArea(float x1, float y1, float w1, float h1, float x2, float y2, float w2, float h2) {
        float overlapX = Math.max(0.0f, Math.min(x1 + w1, x2 + w2) - Math.max(x1, x2));
        float overlapY = Math.max(0.0f, Math.min(y1 + h1, y2 + h2) - Math.max(y1, y2));
        return overlapX * overlapY;
    }

    public void close() {
        this.open = false;
        if (activeMenu == this) {
            activeMenu = null;
        }
    }

    public float computeWidth() {
        float maxRowWidth = Fonts.b.a(title, 12.0f) + 30.0f;
        for (MenuItem item : items) {
            float labelW = Fonts.b.a(item.getLabel(), 11.5f);
            float rightW;
            if (item.isMode()) {
                float valW = Fonts.b.a(item.getValue(), 10.5f);
                rightW = valW + 18.0f;
            } else {
                rightW = 22.0f;
            }
            float rowW = labelW + 24.0f + rightW;
            if (rowW > maxRowWidth) {
                maxRowWidth = rowW;
            }
        }
        return Math.max(165.0f, maxRowWidth + 24.0f);
    }

    public float computeHeight() {
        float headerH = 28.0f;
        float itemH = 21.0f;
        float paddingBottom = 6.0f;
        return headerH + (items.size() * itemH) + paddingBottom;
    }

    public void render(DrawContext context, float mouseX, float mouseY) {
        if (!open) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float mw = computeWidth();
        float mh = computeHeight();

        float visualW = mw * scale;
        float visualH = mh * scale;

        this.x = Math.clamp(this.x, 6.0f, Math.max(6.0f, screenW - visualW - 6.0f));
        this.y = Math.clamp(this.y, 6.0f, Math.max(6.0f, screenH - visualH - 6.0f));

        float mx = this.x;
        float my = this.y;

        boolean customScale = Math.abs(this.scale - 1.0f) > 0.001f;
        Matrix3x2f mat = null;
        if (customScale) {
            mat = new Matrix3x2f();
            mat.translate(mx, my);
            mat.scale(this.scale, this.scale);
            mat.translate(-mx, -my);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(mx, my);
            context.getMatrices().scale(this.scale, this.scale);
            context.getMatrices().translate(-mx, -my);
        }

        float localMX = mx + (mouseX - mx) / this.scale;
        float localMY = my + (mouseY - my) / this.scale;

        Draw2DProcessor.INSTANCE.drawGlow(mat, mx, my, mw, mh, 10.0f, ColorUtil.rgba(0, 0, 0, 190), 12.0f);

        Draw2DProcessor.INSTANCE.drawRect(mat, mx, my, mw, mh, 10.0f, ColorUtil.rgba(14, 15, 22, 245));

        Draw2DProcessor.INSTANCE.drawOutline(mat, mx, my, mw, mh, 10.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 16.0f, my, mw - 32.0f, 1.5f, 0.75f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.55f));

        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 10.0f, my + 9.5f, 3.0f, 9.0f, 1.5f, Theme.getAccent());
        Fonts.b.a(mat, title, mx + 18.0f, my + 8.0f, 12.0f, 0xFFFFFFFF);

        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 8.0f, my + 26.0f, mw - 16.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 12));

        float rowY = my + 30.0f;
        for (MenuItem item : items) {
            boolean hov = localMX >= mx + 4.0f && localMX <= mx + mw - 4.0f && localMY >= rowY - 1.0f && localMY < rowY + 19.0f;
            if (hov) {
                Draw2DProcessor.INSTANCE.drawRect(mat, mx + 4.0f, rowY - 1.0f, mw - 8.0f, 19.0f, 4.5f, ColorUtil.rgba(255, 255, 255, 14));
            }

            Fonts.b.a(mat, item.getLabel(), mx + 10.0f, rowY + 3.5f, 11.5f, hov ? 0xFFFFFFFF : ColorUtil.rgba(225, 228, 238, 220));

            if (item.isMode()) {

                String val = item.getValue();
                float valW = Fonts.b.a(val, 10.5f);
                float badgeW = valW + 14.0f;
                float badgeH = 14.5f;
                float badgeX = mx + mw - 8.0f - badgeW;
                float badgeY = rowY + 1.5f;

                Draw2DProcessor.INSTANCE.drawRect(mat, badgeX, badgeY, badgeW, badgeH, 4.0f, hov ? ColorUtil.rgba(255, 255, 255, 24) : ColorUtil.rgba(255, 255, 255, 12));
                Draw2DProcessor.INSTANCE.drawOutline(mat, badgeX, badgeY, badgeW, badgeH, 4.0f, 1.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), hov ? 0.8f : 0.4f));
                Fonts.b.a(mat, val, badgeX + 7.0f, badgeY + 2.0f, 10.5f, Theme.getAccentHover());
            } else {

                boolean on = item.isChecked();
                float swW = 20.0f;
                float swH = 10.5f;
                float swX = mx + mw - 8.0f - swW;
                float swY = rowY + 3.5f;

                int trackColor = on ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 26);
                Draw2DProcessor.INSTANCE.drawRect(mat, swX, swY, swW, swH, 5.25f, trackColor);

                float knobSize = 7.0f;
                float knobX = on ? (swX + swW - knobSize - 1.75f) : (swX + 1.75f);
                float knobY = swY + (swH - knobSize) / 2.0f;

                if (on) {
                    Draw2DProcessor.INSTANCE.drawGlow(mat, knobX, knobY, knobSize, knobSize, knobSize / 2.0f, Theme.getAccent(), 4.0f);
                }
                Draw2DProcessor.INSTANCE.drawRect(mat, knobX, knobY, knobSize, knobSize, knobSize / 2.0f, 0xFFFFFFFF);
            }

            rowY += 21.0f;
        }

        DrawBatcher.flushPending();

        if (customScale) {
            context.getMatrices().popMatrix();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;

        float mw = computeWidth();
        float mh = computeHeight();

        float localMX = this.x + ((float) mouseX - this.x) / this.scale;
        float localMY = this.y + ((float) mouseY - this.y) / this.scale;

        if (localMX >= this.x && localMX <= this.x + mw && localMY >= this.y && localMY <= this.y + mh) {
            float rowY = this.y + 30.0f;
            for (MenuItem item : items) {
                if (localMY >= rowY - 1.0f && localMY < rowY + 19.0f) {
                    item.onClick();
                    ConfigManager.save();
                    return true;
                }
                rowY += 21.0f;
            }
            return true;
        }

        close();
        return false;
    }
}
