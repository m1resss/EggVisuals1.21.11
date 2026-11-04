package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.hud.HudContextMenu;
import egg.client.render.hud.HudSnapHelper;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.animation.Animation;
import egg.client.util.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Keybinds extends Module {
    private static Keybinds instance;

    public static Keybinds getInstance() {
        return instance;
    }

    private final BooleanSetting showHeader = new BooleanSetting("Шапка", true, "Отображать заголовок Keybinds");
    private final BooleanSetting onlyEnabled = new BooleanSetting("Только активные", false, "Отображать только включенные модули");
    private final SliderSetting rounding = new SliderSetting("Скругление", 6.0, 0.0, 16.0, 0.5, "Скругление углов плашки");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 225.0, 20.0, 255.0, 5.0, "Прозрачность фона карточки");
    private final SliderSetting posX = new SliderSetting("X", 0.015, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.38, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб элемента");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private final HudContextMenu contextMenu = new HudContextMenu("Keybinds");
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastX = 20.0f;
    private float lastY = 160.0f;
    private float lastW = 95.0f;
    private float lastH = 45.0f;

    public float getLastX() { return lastX; }
    public float getLastY() { return lastY; }
    public float getLastW() { return lastW; }
    public float getLastH() { return lastH; }

    private final List<KeybindItem> animatedItems = new ArrayList<>();

    public Keybinds() {
        super("Keybinds", "Показывает список назначенных клавиш и состояние модулей", Category.ESP);
        instance = this;

        posX.setVisible(() -> false);
        posY.setVisible(() -> false);

        contextMenu.addToggle("Шапка", () -> showHeader.isEnabled(), () -> showHeader.toggle());
        contextMenu.addToggle("Только активные", () -> onlyEnabled.isEnabled(), () -> onlyEnabled.toggle());

        addSettings(showHeader, onlyEnabled, rounding, opacity, scale, posX, posY);
        setEnabled(true);
    }

    public static String formatKey(int key) {
        if (key <= 0) return "НЕТ";
        if (key >= 1000) {
            int btn = key - 1000;
            return switch (btn) {
                case 0 -> "ЛКМ";
                case 1 -> "ПКМ";
                case 2 -> "СКМ";
                default -> "M" + (btn + 1);
            };
        }
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase(Locale.ROOT);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_SPACE -> "ПРОБЕЛ";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "K" + key;
            }
        };
    }

    public static boolean isKeyPressed(int key) {
        if (key <= 0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        if (handle == 0L) return false;
        try {
            if (key >= 1000) {
                int btn = key - 1000;
                return GLFW.glfwGetMouseButton(handle, btn) == GLFW.GLFW_PRESS;
            } else {
                return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        Interface iface = Interface.getInstance();
        float combatAlpha = iface != null ? iface.getCombatAlphaFactor("keybinds") : 1.0f;
        float combatScale = iface != null ? iface.getCombatScaleFactor("keybinds") : 1.0f;
        float s = scale.getValueFloat() * combatScale;

        boolean inChat = mc.currentScreen instanceof ChatScreen;

        List<Module> boundModules = new ArrayList<>();
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            for (Module m : mm.getModules()) {
                if (m.getKey() > 0) {
                    if (!onlyEnabled.get() || m.isEnabled()) {
                        boundModules.add(m);
                    }
                }
            }
        }

        updateAnimatedItems(boundModules, inChat);

        List<KeybindItem> visibleItems = new ArrayList<>();
        for (KeybindItem item : animatedItems) {
            if (item.anim.getValue() > 0.005f) {
                visibleItems.add(item);
            }
        }

        if (visibleItems.isEmpty() && !inChat) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float maxNameW = Fonts.b.a("Keybinds", 11.0f * s);
        float maxBadgeW = 26.0f * s;
        for (KeybindItem item : visibleItems) {
            float nw = Fonts.b.a(item.getName(), 10.0f * s);
            if (nw > maxNameW) maxNameW = nw;
            float kw = Fonts.b.a(item.getKeyStr(), 9.0f * s);
            float bw = Math.max(20.0f * s, kw + 8.0f * s);
            if (bw > maxBadgeW) maxBadgeW = bw;
        }

        float cardW = Math.max(98.0f * s, maxNameW + maxBadgeW + 28.0f * s);
        float headerH = showHeader.get() ? 19.0f * s : 4.0f * s;
        float rowH = 16.5f * s;
        float rowGap = 3.0f * s;
        float padBottom = 4.0f * s;

        float totalRowsH = 0.0f;
        for (KeybindItem item : visibleItems) {
            totalRowsH += (rowH + rowGap) * item.anim.getValue();
        }
        float cardH = headerH + totalRowsH + padBottom;

        float radius = rounding.getValueFloat() * s;

        int baseBg = iface != null ? iface.getWatermarkBgColor() : 0xFF14151E;
        float op = opacity.getValueFloat() / 255.0f;
        int bgAlpha = (int) (op * 255.0f * combatAlpha);
        int finalBg = ColorUtil.rgba(ColorUtil.red(baseBg), ColorUtil.green(baseBg), ColorUtil.blue(baseBg), bgAlpha);
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

        if (showHeader.get()) {
            Fonts.d.a(null, "Keybinds", px + 8.0f * s, py + 5.0f * s, 10.5f * s, ColorUtil.rgba(255, 255, 255, (int) (245 * combatAlpha)));
            Draw2DProcessor.INSTANCE.drawRect(null, px + 7.0f * s, py + headerH - 1.5f * s, cardW - 14.0f * s, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * combatAlpha)));
        }

        float curY = py + headerH + 2.0f * s;
        for (KeybindItem item : visibleItems) {
            float anim = item.anim.getValue();
            if (anim <= 0.005f) continue;

            float rowAlpha = anim * combatAlpha;
            float effectiveH = (rowH + rowGap) * anim;
            float rowCenterY = curY + rowH / 2.0f;

            boolean pressed = item.isPressed();
            item.pressAnim.setTarget(pressed ? 1.0f : 0.0f);
            float pressT = item.pressAnim.getValue();

            boolean enabled = item.isEnabled();
            int baseNameColor = enabled ? 0xFFF0F3FA : 0xFFA0A8B8;
            int nameColor = ColorUtil.interpolate(baseNameColor, 0xFFFFFFFF, pressT);
            nameColor = ColorUtil.withAlphaFloat(nameColor, rowAlpha);

            float nameFontSize = 9.8f * s;
            float nameX = px + 8.0f * s;
            float nameY = rowCenterY - Fonts.b.a(nameFontSize) / 2.0f;
            Fonts.b.a(null, item.getName(), nameX, nameY, nameFontSize, nameColor);

            String keyStr = item.getKeyStr();
            float keyFontSize = 9.0f * s;
            float keyTextW = Fonts.b.a(keyStr, keyFontSize);
            float badgeW = Math.max(20.0f * s, keyTextW + 8.0f * s) + (pressT * 2.0f * s);
            float badgeH = 12.5f * s;
            float badgeR = 3.5f * s;
            float badgeX = px + cardW - 8.0f * s - badgeW;
            float badgeY = rowCenterY - badgeH / 2.0f;

            int defaultBadgeBg = ColorUtil.rgba(36, 40, 52, (int) (190 * rowAlpha));
            int defaultBadgeOutline = ColorUtil.rgba(255, 255, 255, (int) (28 * rowAlpha));

            int badgeBg = ColorUtil.interpolate(defaultBadgeBg, ColorUtil.withAlphaFloat(Theme.getAccent(), rowAlpha * 0.95f), pressT);
            int badgeOutline = ColorUtil.interpolate(defaultBadgeOutline, ColorUtil.withAlphaFloat(Theme.getAccentHover(), rowAlpha), pressT);

            if (pressT > 0.01f) {
                Draw2DProcessor.INSTANCE.drawGlow(null, badgeX - 1.5f, badgeY - 1.5f, badgeW + 3.0f, badgeH + 3.0f, badgeR, ColorUtil.withAlphaFloat(Theme.getAccent(), pressT * rowAlpha * 0.55f), 6.0f * s);
            }

            Draw2DProcessor.INSTANCE.drawRect(null, badgeX, badgeY, badgeW, badgeH, badgeR, badgeBg);
            Draw2DProcessor.INSTANCE.drawOutline(null, badgeX, badgeY, badgeW, badgeH, badgeR, 0.8f, badgeOutline);

            float textX = badgeX + (badgeW - keyTextW) / 2.0f;
            float textY = badgeY + (badgeH - Fonts.b.a(keyFontSize)) / 2.0f;
            int keyColor = ColorUtil.interpolate(ColorUtil.rgba(215, 220, 235, (int) (230 * rowAlpha)), 0xFFFFFFFF, pressT);
            Fonts.b.a(null, keyStr, textX, textY, keyFontSize, keyColor);

            curY += effectiveH;
        }

        DrawBatcher.flushPending();
    }

    private void updateAnimatedItems(List<Module> boundModules, boolean inChat) {
        if (boundModules.isEmpty() && inChat) {
            String[][] previews = new String[][]{
                    {"AutoSwap", "M4"},
                    {"Sprint", "V"},
                    {"PlayerESP", "B"}
            };
            for (String[] p : previews) {
                KeybindItem found = null;
                for (KeybindItem item : animatedItems) {
                    if (item.isPreview && p[0].equals(item.previewName)) {
                        found = item;
                        break;
                    }
                }
                if (found != null) {
                    if (found.removing) {
                        found.removing = false;
                        found.anim.setTarget(1.0f);
                    }
                } else {
                    KeybindItem newItem = new KeybindItem(p[0], p[1]);
                    newItem.anim.setValue(0.0f);
                    newItem.anim.setTarget(1.0f);
                    animatedItems.add(newItem);
                }
            }

            for (KeybindItem item : animatedItems) {
                if (!item.isPreview && !item.removing) {
                    item.removing = true;
                    item.anim.setTarget(0.0f);
                }
            }
        } else {
            for (Module m : boundModules) {
                KeybindItem found = null;
                for (KeybindItem item : animatedItems) {
                    if (!item.isPreview && item.module == m) {
                        found = item;
                        break;
                    }
                }
                if (found != null) {
                    if (found.removing) {
                        found.removing = false;
                        found.anim.setTarget(1.0f);
                    }
                } else {
                    KeybindItem newItem = new KeybindItem(m);
                    newItem.anim.setValue(0.0f);
                    newItem.anim.setTarget(1.0f);
                    animatedItems.add(newItem);
                }
            }

            for (KeybindItem item : animatedItems) {
                if (item.isPreview) {
                    if (!item.removing) {
                        item.removing = true;
                        item.anim.setTarget(0.0f);
                    }
                } else {
                    if (!boundModules.contains(item.module) && !item.removing) {
                        item.removing = true;
                        item.anim.setTarget(0.0f);
                    }
                }
            }
        }

        Iterator<KeybindItem> it = animatedItems.iterator();
        while (it.hasNext()) {
            KeybindItem item = it.next();
            if (item.removing && item.anim.getValue() <= 0.005f) {
                it.remove();
            }
        }
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float rad = rounding.getValueFloat() * scale.getValueFloat();

        if (dragging) {
            onChatMouseDragged(mouseX, mouseY, 0);
            HudSnapHelper.renderGridAndGuides(context, currentSnap, screenW, screenH,
                    new HudSnapHelper.HudElementBounds("keybinds", "Keybinds", lastX, lastY, lastW, lastH),
                    "Keybinds", Math.round(scale.getValueFloat() * 100.0f), rad);
        } else {
            boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
            if (inside) {
                String scaleBadge = "Keybinds: " + Math.round(scale.getValueFloat() * 100.0f) + "%";
                HudSnapHelper.renderHoverBadge(context, lastX, lastY, lastW, lastH, rad, scaleBadge);
            }
        }

        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            contextMenu.render(context, mouseX, mouseY);
        }
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;
        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside) {
            double cur = scale.getValue();
            double step = amount > 0 ? 0.05 : -0.05;
            scale.setValue(Math.clamp(Math.round((cur + step) * 100.0) / 100.0, 0.5, 1.5));
            ConfigManager.save();
            return true;
        }
        return false;
    }

    public boolean onChatMouseClicked(double mx, double my, int button) {
        if (!isEnabled()) return false;
        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            if (contextMenu.mouseClicked(mx, my, button)) return true;
        }

        boolean inside = mx >= lastX - 2.0f && mx <= lastX + lastW + 2.0f && my >= lastY - 2.0f && my <= lastY + lastH + 2.0f;
        if (inside) {
            if (button == 1) {
                contextMenu.setScale(scale.getValueFloat());
                contextMenu.open((float) mx, (float) my);
                return true;
            } else if (button == 0) {
                this.dragging = true;
                this.dragOffsetX = (float) mx - lastX;
                this.dragOffsetY = (float) my - lastY;
                return true;
            }
        }
        return false;
    }

    public void onChatMouseReleased(double mx, double my, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            this.currentSnap = null;
            ConfigManager.save();
        }
    }

    public void onChatMouseDragged(double mx, double my, int button) {
        if (this.dragging && button == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float proposedX = (float) mx - dragOffsetX;
            float proposedY = (float) my - dragOffsetY;

            HudSnapHelper.SnapResult snap = HudSnapHelper.calculateSnap("keybinds", proposedX, proposedY, lastW, lastH, screenW, screenH);
            this.currentSnap = snap;

            float maxAvailX = Math.max(1.0f, screenW - lastW);
            float maxAvailY = Math.max(1.0f, screenH - lastH);

            posX.setValue(Math.clamp(snap.snappedX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(snap.snappedY / maxAvailY, 0.0, 1.0));
        }
    }

    private static class KeybindItem {
        final Module module;
        final String previewName;
        final String previewKey;
        final boolean isPreview;
        final Animation anim = new Animation(0.0f, 220L, Easing.EASE_OUT_CUBIC);
        final Animation pressAnim = new Animation(0.0f, 130L, Easing.EASE_OUT_CUBIC);
        boolean removing = false;

        KeybindItem(Module module) {
            this.module = module;
            this.previewName = null;
            this.previewKey = null;
            this.isPreview = false;
        }

        KeybindItem(String name, String key) {
            this.module = null;
            this.previewName = name;
            this.previewKey = key;
            this.isPreview = true;
        }

        String getName() {
            return isPreview ? previewName : module.getName();
        }

        String getKeyStr() {
            return isPreview ? previewKey : formatKey(module.getKey());
        }

        boolean isEnabled() {
            return isPreview || module.isEnabled();
        }

        boolean isPressed() {
            if (isPreview) return false;
            return Keybinds.isKeyPressed(module.getKey());
        }
    }
}
