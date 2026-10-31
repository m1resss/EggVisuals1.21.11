package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.util.IStatusEffectInstance;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
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
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
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

    private final ModeSetting style = new ModeSetting("Вид", "Интерфейс", "Стиль отображения зелий", "Интерфейс", "Капсулы");
    private final ModeSetting timerFormat = new ModeSetting("Формат таймера", "Время", "Тип отображения оставшегося времени", "Время", "Проценты");
    private final BooleanSetting showDigits = new BooleanSetting("Отображать цифры", true, "Отображать значение под иконкой (для стиля Капсулы)");
    private final SliderSetting posX = new SliderSetting("X", 0.01, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.35, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб зелий");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private boolean contextMenuOpen = false;
    private float contextMenuX = 0.0f;
    private float contextMenuY = 0.0f;

    private float lastX = 8.0f;
    private float lastY = 100.0f;
    private float lastW = 140.0f;
    private float lastH = 100.0f;

    public PotionHUD() {
        super("Potions", "Отображение активных эффектов зелий", Category.ESP);
        instance = this;
        posX.setVisible(() -> false);
        posY.setVisible(() -> false);
        addSettings(style, timerFormat, showDigits, scale, posX, posY);
        setEnabled(true);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        List<StatusEffectInstance> effects = collectEffects(mc);
        if (effects.isEmpty()) {
            if (!inChat) return;
            effects = createDummyEffects();
        }

        Interface iface = Interface.getInstance();
        float combatAlpha = iface != null ? iface.getCombatAlphaFactor("potions") : 1.0f;
        float combatScale = iface != null ? iface.getCombatScaleFactor("potions") : 1.0f;
        float s = scale.getValueFloat() * combatScale;

        if ("Капсулы".equalsIgnoreCase(style.get())) {
            renderCapsulesMode(context, mc, effects, s, combatAlpha);
        } else {
            renderInterfaceMode(context, mc, effects, s, combatAlpha, iface);
        }
    }

    private void renderInterfaceMode(DrawContext context, MinecraftClient mc, List<StatusEffectInstance> effects, float s, float combatAlpha, Interface iface) {
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float maxRowW = 0.0f;
        for (StatusEffectInstance effect : effects) {
            String name = formatEffectName(effect);
            float nameW = Fonts.b.a(name, 10.5f * s);
            String timer = formatTimer(effect);
            float timerW = Fonts.b.a(timer, 9.5f * s);
            float badgeW = Math.max(26.0f * s, timerW + 10.0f * s);
            float rowW = 8.0f * s + 14.0f * s + 6.0f * s + nameW + 10.0f * s + badgeW + 8.0f * s;
            if (rowW > maxRowW) maxRowW = rowW;
        }

        float headerW = Fonts.d.a("Зелья", 11.0f * s) + 24.0f * s;
        float cardW = Math.max(136.0f * s, Math.max(headerW, maxRowW));

        float headerH = 21.0f * s;
        float rowH = 19.0f * s;
        float rowGap = 3.5f * s;
        float padBottom = 5.0f * s;
        int count = effects.size();
        float cardH = headerH + count * rowH + Math.max(0, count - 1) * rowGap + padBottom;

        float radius = (iface != null ? iface.getRounding() : 7.0f) * s;
        int baseBg = iface != null ? iface.getWatermarkBgColor() : 0xD614151E;
        int finalBg = ColorUtil.rgba(ColorUtil.red(baseBg), ColorUtil.green(baseBg), ColorUtil.blue(baseBg), (int) (214 * combatAlpha));
        int borderColor = ColorUtil.rgba(255, 255, 255, (int) (22 * combatAlpha));

        float userX = posX.getValueFloat() * (screenW - cardW);
        float userY = posY.getValueFloat() * (screenH - cardH);
        float px = Math.clamp(userX, 4.0f, screenW - cardW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - cardH - 15.0f);

        this.lastX = px;
        this.lastY = py;
        this.lastW = cardW;
        this.lastH = cardH;

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, cardW, cardH, radius, ColorUtil.rgba(0, 0, 0, (int) (55 * combatAlpha)), 6.0f * s);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, cardW, cardH, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, cardW, cardH, radius, 0.8f, borderColor);

        if (iface != null && iface.isHudThemeGradient()) {
            float str = iface.getHudGradientStrength() / 100.0f;
            int gradC = ColorUtil.rgba(168, 85, 247, (int) (str * 45.0f * combatAlpha));
            Draw2DProcessor.INSTANCE.drawGradient(null, px, py, cardW, cardH, radius, gradC, 0x00000000, 0x00000000, gradC);
        }

        Fonts.d.a(null, "Зелья", px + 8.0f * s, py + 5.5f * s, 11.0f * s, ColorUtil.rgba(255, 255, 255, (int) (245 * combatAlpha)));
        Draw2DProcessor.INSTANCE.drawRect(null, px + 7.0f * s, py + headerH - 1.5f * s, cardW - 14.0f * s, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * combatAlpha)));

        float curY = py + headerH + 2.0f * s;

        for (StatusEffectInstance effect : effects) {
            float rowCenterY = curY + rowH / 2.0f;

            Identifier icon = InGameHud.getEffectTexture(effect.getEffectType());
            float iconSize = 13.5f * s;
            float iconX = px + 8.0f * s;
            float iconY = rowCenterY - iconSize / 2.0f;

            DrawBatcher.flushPending();
            if (icon != null) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, icon, Math.round(iconX), Math.round(iconY), Math.round(iconSize), Math.round(iconSize), ColorUtil.withAlphaFloat(0xFFFFFFFF, combatAlpha));
            }

            String name = formatEffectName(effect);
            boolean harmful = isHarmful(effect);
            int nameColor = harmful ? ColorUtil.rgba(239, 68, 68, (int) (255 * combatAlpha)) : ColorUtil.rgba(240, 243, 250, (int) (240 * combatAlpha));
            float nameFontSize = 10.5f * s;
            float nameX = iconX + iconSize + 6.0f * s;
            float nameY = rowCenterY - Fonts.b.a(nameFontSize) / 2.0f;
            Fonts.b.a(null, name, nameX, nameY, nameFontSize, nameColor);

            String timer = formatTimer(effect);
            float timerFontSize = 9.5f * s;
            float timerTextW = Fonts.b.a(timer, timerFontSize);
            float badgeW = Math.max(26.0f * s, timerTextW + 10.0f * s);
            float badgeH = 13.5f * s;
            float badgeR = 3.5f * s;
            float badgeX = px + cardW - 8.0f * s - badgeW;
            float badgeY = rowCenterY - badgeH / 2.0f;

            int badgeBg = ColorUtil.rgba(36, 40, 52, (int) (190 * combatAlpha));
            int badgeOutline = ColorUtil.rgba(255, 255, 255, (int) (28 * combatAlpha));
            Draw2DProcessor.INSTANCE.drawRect(null, badgeX, badgeY, badgeW, badgeH, badgeR, badgeBg);
            Draw2DProcessor.INSTANCE.drawOutline(null, badgeX, badgeY, badgeW, badgeH, badgeR, 0.8f, badgeOutline);

            float textX = badgeX + (badgeW - timerTextW) / 2.0f;
            float textY = badgeY + (badgeH - Fonts.b.a(timerFontSize)) / 2.0f;
            Fonts.b.a(null, timer, textX, textY, timerFontSize, ColorUtil.rgba(235, 238, 245, (int) (240 * combatAlpha)));

            curY += rowH + rowGap;
        }

        DrawBatcher.flushPending();
    }

    private void renderCapsulesMode(DrawContext context, MinecraftClient mc, List<StatusEffectInstance> effects, float s, float combatAlpha) {
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

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

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, pillW, pillH, radius, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 90), combatAlpha), 8.0f * s);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, pillW, pillH, radius, ColorUtil.withAlphaFloat(ColorUtil.rgba(14, 15, 20, 240), combatAlpha));
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, pillW, pillH, radius, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 24), combatAlpha));

        DrawBatcher.flushPending();

        float centerX = px + pillW / 2.0f;
        float curY = py + padY;

        for (StatusEffectInstance effect : effects) {
            float cy = curY + ringR;

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

            Primitive2D.drawCircle(centerX, cy, discR, ColorUtil.withAlphaFloat(discBg, combatAlpha));
            Primitive2D.drawProgressRing(centerX, cy, innerR, ringR, progress, ColorUtil.withAlphaFloat(trackColor, combatAlpha), ColorUtil.withAlphaFloat(effectColor, combatAlpha));

            Identifier icon = InGameHud.getEffectTexture(effect.getEffectType());
            if (icon != null) {
                float iconSize = 14.0f * s;
                int ix = Math.round(centerX - iconSize / 2.0f);
                int iy = Math.round(cy - iconSize / 2.0f);
                int is = Math.max(1, Math.round(iconSize));
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, icon, ix, iy, is, is, ColorUtil.withAlphaFloat(0xFFFFFFFF, combatAlpha));
            }

            if (digits) {
                String label = formatTimer(effect);
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

    private List<StatusEffectInstance> createDummyEffects() {
        List<StatusEffectInstance> list = new ArrayList<>();
        StatusEffectInstance speed = new StatusEffectInstance(StatusEffects.SPEED, 20 * 68, 1);
        if (speed instanceof IStatusEffectInstance inst) inst.setInitialDuration(20 * 90);
        list.add(speed);

        StatusEffectInstance strength = new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 58, 1);
        if (strength instanceof IStatusEffectInstance inst) inst.setInitialDuration(20 * 90);
        list.add(strength);

        StatusEffectInstance weakness = new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 30, 0);
        if (weakness instanceof IStatusEffectInstance inst) inst.setInitialDuration(20 * 45);
        list.add(weakness);

        return list;
    }

    public static boolean isHarmful(StatusEffectInstance effect) {
        if (effect == null || effect.getEffectType() == null) return false;
        return effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
    }

    public static String formatEffectName(StatusEffectInstance effect) {
        if (effect == null || effect.getEffectType() == null) return "";
        String name = effect.getEffectType().value().getName().getString();
        int amp = effect.getAmplifier();
        if (amp > 0) {
            return name + " " + toRoman(amp + 1);
        }
        return name;
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    private String formatTimer(StatusEffectInstance effect) {
        int duration = effect.getDuration();
        if ("Проценты".equalsIgnoreCase(timerFormat.get())) {
            int initial = 1;
            if (effect instanceof IStatusEffectInstance inst) {
                initial = inst.getInitialDuration();
            }
            if (initial <= 0) initial = Math.max(duration, 1);
            float progress = duration > 1000000 ? 1.0f : Math.clamp((float) duration / (float) initial, 0.0f, 1.0f);
            return Math.round(progress * 100.0f) + "%";
        } else {
            int sec = duration / 20;
            return duration > 1000000 ? "∞" : String.format("%d:%02d", sec / 60, sec % 60);
        }
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
            return 0xFFF97316;
        } else if (entry.equals(StatusEffects.SPEED)) {
            return 0xFF38BDF8;
        } else if (entry.equals(StatusEffects.REGENERATION) || entry.equals(StatusEffects.HEALTH_BOOST)) {
            return 0xFF22C55E;
        } else if (entry.equals(StatusEffects.HASTE) || entry.equals(StatusEffects.ABSORPTION)) {
            return 0xFFFACC15;
        } else if (entry.equals(StatusEffects.RESISTANCE)) {
            return 0xFF94A3B8;
        } else if (entry.equals(StatusEffects.FIRE_RESISTANCE)) {
            return 0xFFFB923C;
        } else if (entry.equals(StatusEffects.INVISIBILITY)) {
            return 0xFF818CF8;
        } else if (entry.value().getCategory() == StatusEffectCategory.HARMFUL) {
            return 0xFFEF4444;
        }
        int c = entry.value().getColor();
        return 0xFF000000 | c;
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
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

            String label = "HUD — Зелья";
            float lw = Fonts.b.a(label, 11.5f);
            float lx = lastX + (lastW - lw) / 2.0f;
            float ly = lastY + lastH + 6.0f;
            Fonts.b.a(null, label, lx, ly, 11.5f, ColorUtil.rgba(255, 255, 255, 160));
        }

        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside || dragging) {
            float rad = "Капсулы".equalsIgnoreCase(style.get()) ? Math.min(lastW / 2.0f, 13.0f * scale.getValueFloat()) : 7.0f * scale.getValueFloat();
            Draw2DProcessor.INSTANCE.drawOutline(null, lastX - 2.0f, lastY - 2.0f, lastW + 4.0f, lastH + 4.0f, rad, 1.0f, Theme.getAccent());

            String scaleBadge = "Зелья: " + Math.round(scale.getValueFloat() * 100.0f) + "%";
            float bw = Fonts.b.a(scaleBadge, 9.5f) + 8.0f;
            float bx = lastX + (lastW - bw) / 2.0f;
            float by = lastY + lastH + 5.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 13.0f, 3.0f, ColorUtil.rgba(14, 15, 20, 220));
            Fonts.b.a(null, scaleBadge, bx + 4.0f, by + 2.5f, 9.5f, Theme.getAccentHover());
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

        if (contextMenuOpen) {
            float mw = 135.0f;
            float mh = 78.0f;
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + mw && mouseY >= contextMenuY && mouseY <= contextMenuY + mh) {
                float iy = contextMenuY + 22.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    style.cycle();
                    ConfigManager.save();
                    return true;
                }
                iy += 16.0f;
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

        boolean inside = mouseX >= lastX - 3.0f && mouseX <= lastX + lastW + 3.0f && mouseY >= lastY - 3.0f && mouseY <= lastY + lastH + 3.0f;
        if (inside) {
            if (button == 1) {
                contextMenuOpen = true;
                contextMenuX = (float) Math.clamp(mouseX, 10.0, screenW - 145.0);
                contextMenuY = (float) Math.clamp(mouseY, 10.0, screenH - 120.0);
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
        float mw = 135.0f;
        float mh = 78.0f;
        float mx = contextMenuX;
        float my = contextMenuY;

        Draw2DProcessor.INSTANCE.drawGlow(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(0, 0, 0, 190), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(22, 23, 30, 248));
        Draw2DProcessor.INSTANCE.drawOutline(null, mx, my, mw, mh, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        Fonts.d.a(null, "Настройки зелий", mx + 8.0f, my + 6.0f, 12.0f, Theme.getAccentHover());
        Draw2DProcessor.INSTANCE.drawRect(null, mx + 6.0f, my + 18.0f, mw - 12.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 15));

        float itemY = my + 22.0f;
        renderContextItem(context, mx, itemY, mw, "Вид: " + style.get(), true, mouseX, mouseY); itemY += 16.0f;
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
