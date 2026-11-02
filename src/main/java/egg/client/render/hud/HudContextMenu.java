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

    private final String title;
    private final List<MenuItem> items = new ArrayList<>();

    private boolean open = false;
    private float x = 0.0f;
    private float y = 0.0f;
    private float scale = 1.0f;

    public HudContextMenu(String title) {
        this.title = title;
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
        this.open = true;
        this.x = clickX;
        this.y = clickY;
    }

    public void open(float clickX, float clickY, float scale) {
        this.open = true;
        this.x = clickX;
        this.y = clickY;
        setScale(scale);
    }

    public void close() {
        this.open = false;
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

        // Автоматическое удержание внутри экрана с учетом масштаба
        this.x = Math.clamp(this.x, 8.0f, Math.max(8.0f, screenW - visualW - 8.0f));
        this.y = Math.clamp(this.y, 8.0f, Math.max(8.0f, screenH - visualH - 8.0f));

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

        // 1. Тень и мягкое свечение
        Draw2DProcessor.INSTANCE.drawGlow(mat, mx, my, mw, mh, 10.0f, ColorUtil.rgba(0, 0, 0, 190), 12.0f);

        // 2. Темное стекло интерфейса (стиль Dynamic Island)
        Draw2DProcessor.INSTANCE.drawRect(mat, mx, my, mw, mh, 10.0f, ColorUtil.rgba(14, 15, 22, 245));

        // 3. Тонкий контур
        Draw2DProcessor.INSTANCE.drawOutline(mat, mx, my, mw, mh, 10.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        // 4. Верхний мягкий акцентный блик
        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 16.0f, my, mw - 32.0f, 1.5f, 0.75f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.55f));

        // 5. Заголовок с индикатором
        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 10.0f, my + 9.5f, 3.0f, 9.0f, 1.5f, Theme.getAccent());
        Fonts.b.a(mat, title, mx + 18.0f, my + 8.0f, 12.0f, 0xFFFFFFFF);

        // Разделитель
        Draw2DProcessor.INSTANCE.drawRect(mat, mx + 8.0f, my + 26.0f, mw - 16.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 12));

        // 6. Список элементов настроек
        float rowY = my + 30.0f;
        for (MenuItem item : items) {
            boolean hov = localMX >= mx + 4.0f && localMX <= mx + mw - 4.0f && localMY >= rowY - 1.0f && localMY < rowY + 19.0f;
            if (hov) {
                Draw2DProcessor.INSTANCE.drawRect(mat, mx + 4.0f, rowY - 1.0f, mw - 8.0f, 19.0f, 4.5f, ColorUtil.rgba(255, 255, 255, 14));
            }

            // Текст названия
            Fonts.b.a(mat, item.getLabel(), mx + 10.0f, rowY + 3.5f, 11.5f, hov ? 0xFFFFFFFF : ColorUtil.rgba(225, 228, 238, 220));

            if (item.isMode()) {
                // Пилл-значение режима справа
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
                // Компактный переключатель в стиле Island
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
            return true; // Клик внутри меню (например по заголовку) поглощается
        }

        close();
        return false;
    }
}
