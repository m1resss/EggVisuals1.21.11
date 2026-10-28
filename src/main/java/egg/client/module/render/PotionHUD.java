package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.util.IStatusEffectInstance;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.IconRenderer;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.Primitive2D;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PotionHUD extends Module {
    private static PotionHUD instance;

    public static PotionHUD getInstance() {
        return instance;
    }

    private final ModeSetting timerFormat = new ModeSetting("Формат таймера", "Проценты", "Тип отображения оставшегося времени", "Проценты", "Время");
    private final BooleanSetting showDigits = new BooleanSetting("Отображать цифры", true, "Отображать значение под иконкой");
    private final SliderSetting posX = new SliderSetting("X", 0.01, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.35, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб зелий");

    // Drag & context menu
    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private boolean contextMenuOpen = false;
    private float contextMenuX = 0.0f;
    private float contextMenuY = 0.0f;

    // Bounding box
    private float lastX = 8.0f;
    private float lastY = 100.0f;
    private float lastW = 44.0f;
    private float lastH = 120.0f;

    public PotionHUD() {
        super("Potions", "Боковой капсульный док активных эффектов зелий", Category.ESP);
        instance = this;
        addSettings(timerFormat, showDigits, scale);
        setEnabled(true);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        List<StatusEffectInstance> effects = collectEffects(mc);
        if (effects.isEmpty()) return;

        Interface iface = Interface.getInstance();
        float combatAlpha = iface != null ? iface.getCombatAlphaFactor("potions") : 1.0f;
        float combatScale = iface != null ? iface.getCombatScaleFactor("potions") : 1.0f;

        float s = scale.getValueFloat() * combatScale;
        float ringR = 12.0f * s;
        float innerR = ringR - 2.0f * s;
        float discR = ringR - 1.2f * s;
        boolean digits = showDigits.isEnabled();
        float itemH = (ringR * 2.0f) + (digits ? 9.0f * s : 0.0f);
        float gap = 7.0f * s;
        float padY = 8.0f * s;
        float padX = 8.0f * s;
        float pillW = (ringR * 2.0f) + padX * 2.0f;

        int count = effects.size();
        float totalContentH = count * itemH + Math.max(0, count - 1) * gap;
        float pillH = padY * 2.0f + totalContentH;
        float radius = Math.min(pillW / 2.0f, 13.0f * s);

        float userX = posX.getValueFloat() * (screenW - pillW);
        float userY = posY.getValueFloat() * (screenH - pillH);
        float px = Math.clamp(userX, 4.0f, screenW - pillW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - pillH - 15.0f);

        this.lastX = px;
        this.lastY = py;
        this.lastW = pillW;
        this.lastH = pillH;

        // 1. OLED Glass Capsule Background
        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, pillW, pillH, radius, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 90), combatAlpha), 8.0f * s);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, pillW, pillH, radius, ColorUtil.withAlphaFloat(ColorUtil.rgba(14, 15, 20, 240), combatAlpha));
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, pillW, pillH, radius, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 24), combatAlpha));

        // Flush background before drawing primitives
        DrawBatcher.flushPending();

        float centerX = px + pillW / 2.0f;
        float curY = py + padY;

        for (StatusEffectInstance effect : effects) {
            float cy = curY + ringR;

            // Compute remaining duration and progress
            int duration = effect.getDuration();
            int initial = 1;
            if (effect instanceof IStatusEffectInstance instance) {
                initial = instance.getInitialDuration();
            }
            if (initial <= 0) initial = Math.max(duration, 1);
            float progress = duration > 1000000 ? 1.0f : Math.clamp((float) duration / (float) initial, 0.0f, 1.0f);

            int effectColor = getEffectColor(effect);
            int discBg = ColorUtil.rgba(22, 24, 32, 250);
            int trackColor = ColorUtil.rgba(ColorUtil.red(effectColor), ColorUtil.green(effectColor), ColorUtil.blue(effectColor), 45);

            // 1. Dark inner circle
            Primitive2D.drawCircle(centerX, cy, discR, ColorUtil.withAlphaFloat(discBg, combatAlpha));

            // 2. Circular progress ring
            Primitive2D.drawProgressRing(centerX, cy, innerR, ringR, progress, ColorUtil.withAlphaFloat(trackColor, combatAlpha), ColorUtil.withAlphaFloat(effectColor, combatAlpha));

            // 3. Potion sprite inside circle
            Identifier icon = InGameHud.getEffectTexture(effect.getEffectType());
            if (icon != null) {
                float iconSize = 14.0f * s;
                int ix = Math.round(centerX - iconSize / 2.0f);
                int iy = Math.round(cy - iconSize / 2.0f);
                int is = Math.max(1, Math.round(iconSize));
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, icon, ix, iy, is, is, ColorUtil.withAlphaFloat(0xFFFFFFFF, combatAlpha));
            }

            // 4. Subtitle digits (scaled proportionally with s)
            if (digits) {
                String label;
                if ("Время".equalsIgnoreCase(timerFormat.get())) {
                    int sec = duration / 20;
                    label = duration > 1000000 ? "∞" : String.format("%d:%02d", sec / 60, sec % 60);
                } else {
                    int pct = Math.round(progress * 100.0f);
                    label = pct + "%";
                }
                float fontSize = 9.5f * s;
                float textW = Fonts.b.a(label, fontSize);
                float textX = centerX - textW / 2.0f;
                float textY = cy + ringR + 2.0f * s;
                Fonts.b.a(null, label, textX, textY, fontSize, ColorUtil.withAlphaFloat(ColorUtil.rgba(235, 238, 245, 235), combatAlpha));
            }

            curY += itemH + gap;
        }

        DrawBatcher.flushPending();
    }

    private List<StatusEffectInstance> collectEffects(MinecraftClient mc) {
        List<StatusEffectInstance> list = new ArrayList<>();
        if (mc.player != null) {
            Collection<StatusEffectInstance> playerEffects = mc.player.getStatusEffects();
            if (playerEffects != null) {
                list.addAll(playerEffects);
            }
        }
        return list;
    }

    public static int getEffectColor(StatusEffectInstance effect) {
        var entry = effect.getEffectType();
        if (entry.equals(StatusEffects.STRENGTH)) {
            return 0xFFF97316; // Flame Orange
        } else if (entry.equals(StatusEffects.SPEED)) {
            return 0xFF38BDF8; // Sky Blue
        } else if (entry.equals(StatusEffects.REGENERATION) || entry.equals(StatusEffects.HEALTH_BOOST)) {
            return 0xFF22C55E; // Emerald Green
        } else if (entry.equals(StatusEffects.HASTE) || entry.equals(StatusEffects.ABSORPTION)) {
            return 0xFFFACC15; // Vibrant Gold
        } else if (entry.equals(StatusEffects.RESISTANCE)) {
            return 0xFF94A3B8; // Slate Shield
        } else if (entry.equals(StatusEffects.FIRE_RESISTANCE)) {
            return 0xFFFB923C; // Amber Flame
        } else if (entry.equals(StatusEffects.INVISIBILITY)) {
            return 0xFF818CF8; // Soft Indigo
        } else if (entry.value().getCategory() == StatusEffectCategory.HARMFUL) {
            return 0xFFEF4444; // Crimson Red
        }
        int c = entry.value().getColor();
        return 0xFF000000 | c;
    }

    // =========================================================================
    // Chat Overlay & Right-click settings
    // =========================================================================

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        if (dragging) {
            float rad = 13.0f;
            Draw2DProcessor.INSTANCE.drawOutline(null, lastX - 2.0f, lastY - 2.0f, lastW + 4.0f, lastH + 4.0f, rad, 1.2f, Theme.getAccent());
        }

        if (contextMenuOpen) {
            renderContextMenu(context, mouseX, mouseY);
        }
        DrawBatcher.flushPending();
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;
        if (mouseX >= lastX - 4.0f && mouseX <= lastX + lastW + 4.0f
                && mouseY >= lastY - 4.0f && mouseY <= lastY + lastH + 4.0f) {
            double cur = scale.getValue();
            double next = Math.clamp(cur + (amount > 0 ? 0.05 : -0.05), 0.5, 1.5);
            next = Math.round(next * 100.0) / 100.0;
            scale.setValue(next);
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

        // 1. Inside context menu
        if (contextMenuOpen) {
            float mw = 130.0f;
            float mh = 62.0f;
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + mw && mouseY >= contextMenuY && mouseY <= contextMenuY + mh) {
                float iy = contextMenuY + 22.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    timerFormat.cycle();
                    ConfigManager.save();
                    return true;
                }
                iy += 16.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    showDigits.toggle();
                    ConfigManager.save();
                    return true;
                }
                return true;
            }
            contextMenuOpen = false;
        }

        // 2. Click on Potions pill
        boolean inside = mouseX >= lastX - 3.0f && mouseX <= lastX + lastW + 3.0f && mouseY >= lastY - 3.0f && mouseY <= lastY + lastH + 3.0f;
        if (inside) {
            if (button == 1) { // Right click -> open context popup
                contextMenuOpen = true;
                contextMenuX = (float) Math.clamp(mouseX, 10.0, screenW - 140.0);
                contextMenuY = (float) Math.clamp(mouseY, 10.0, screenH - 120.0);
                return true;
            } else if (button == 0) { // Left click -> drag
                dragging = true;
                dragOffsetX = (float) mouseX - lastX;
                dragOffsetY = (float) mouseY - lastY;
                return true;
            }
        }

        return false;
    }

    public boolean onChatMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            ConfigManager.save();
            return true;
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
        float mh = 62.0f;
        float mx = contextMenuX;
        float my = contextMenuY;

        Draw2DProcessor.INSTANCE.drawGlow(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(0, 0, 0, 190), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(22, 23, 30, 248));
        Draw2DProcessor.INSTANCE.drawOutline(null, mx, my, mw, mh, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        Fonts.d.a(null, "Настройки зелий", mx + 8.0f, my + 6.0f, 12.0f, Theme.getAccentHover());
        Draw2DProcessor.INSTANCE.drawRect(null, mx + 6.0f, my + 18.0f, mw - 12.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 15));

        float itemY = my + 22.0f;
        renderContextItem(context, mx, itemY, mw, "Формат: " + timerFormat.get(), true, mouseX, mouseY); itemY += 16.0f;
        renderContextItem(context, mx, itemY, mw, "Цифры", showDigits.isEnabled(), mouseX, mouseY);
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

        Fonts.b.a(null, label, cbX + cbSize + 6.0f, iy + 1.0f, 11.0f, checked ? 0xFFFFFFFF : Theme.TEXT_SUB);
    }
}
