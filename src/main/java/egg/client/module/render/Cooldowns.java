package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.mixin.ItemCooldownEntryAccessor;
import egg.client.mixin.ItemCooldownManagerAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

public class Cooldowns extends Module {
    private static Cooldowns instance;

    public static Cooldowns getInstance() {
        return instance;
    }

    private final SliderSetting posX = new SliderSetting("X", 0.5, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.76, 0.0, 1.0, 0.001, "Позиция Y");
    private final ColorSetting bgColor = new ColorSetting("Цвет фона", (int) 0xC4151515L, "Цвет фона капсулы кулдаунов");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 175.0, 10.0, 255.0, 5.0, "Прозрачность фона кулдаунов");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб худа");
    private final BooleanSetting hudThemeGradient = new BooleanSetting("Градиент HUD", true, "Мягкий градиент по бокам капсулы");
    private final SliderSetting hudGradientStrength = new SliderSetting("Сила градиента", 60.0, 5.0, 100.0, 5.0, "Интенсивность градиента");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastX = 200.0f;
    private float lastY = 300.0f;
    private float lastW = 100.0f;
    private float lastH = 34.0f;
    private float animatedY = -1.0f;

    @Override
    public void onDisable() {
        animatedY = -1.0f;
    }

    public float getLastX() { return lastX; }
    public float getLastY() { return lastY; }
    public float getLastW() { return lastW; }
    public float getLastH() { return lastH; }

    private static class DisplayEntry {
        final Identifier id;
        final ItemStack stack;
        final float remainingSec;
        final float progress;

        DisplayEntry(Identifier id, ItemStack stack, float remainingSec, float progress) {
            this.id = id;
            this.stack = stack;
            this.remainingSec = remainingSec;
            this.progress = progress;
        }
    }

    private static final Set<Item> BLACKLIST_ITEMS = Set.of(
            Items.ELYTRA,
            Items.FIREWORK_ROCKET,
            Items.FIREWORK_STAR,
            Items.SLIME_BLOCK,
            Items.WATER_BUCKET
    );

    public Cooldowns() {
        super("Cooldowns", "Показывает оставшееся время перезарядки предметов", Category.ESP);
        instance = this;
        addSettings(bgColor, opacity, scale, hudThemeGradient, hudGradientStrength);
        setEnabled(true);
    }

    private boolean isBlacklisted(Identifier id, Item item) {
        if (item != null && BLACKLIST_ITEMS.contains(item)) return true;
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("elytra") || path.contains("firework") || path.contains("slime_block") || path.equals("slime") || path.contains("water_bucket")) {
                return true;
            }
        }
        return false;
    }

    private List<DisplayEntry> collectCooldowns(MinecraftClient mc) {
        if (mc.player == null) return Collections.emptyList();

        ItemCooldownManagerAccessor mgr = (ItemCooldownManagerAccessor) (Object) mc.player.getItemCooldownManager();
        if (mgr == null) return Collections.emptyList();

        Map<Identifier, Object> entries = mgr.getEntries();
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        int curTick = mgr.getTick();
        List<DisplayEntry> list = new ArrayList<>();

        for (Map.Entry<Identifier, Object> e : entries.entrySet()) {
            Identifier id = e.getKey();
            Object val = e.getValue();
            if (!(val instanceof ItemCooldownEntryAccessor entry)) continue;

            int startTick = entry.getStartTick();
            int endTick = entry.getEndTick();
            int remainingTicks = endTick - curTick;
            if (remainingTicks <= 0) continue;

            Item item = Registries.ITEM.get(id);
            if (isBlacklisted(id, item)) continue;

            ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            int totalTicks = Math.max(1, endTick - startTick);
            float remainingFrac = Math.clamp((float) remainingTicks / (float) totalTicks, 0.0f, 1.0f);
            float remainingSec = remainingTicks / 20.0f;

            list.add(new DisplayEntry(id, stack, remainingSec, remainingFrac));
        }

        list.sort(Comparator.comparingDouble(a -> a.remainingSec));
        return list;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        List<DisplayEntry> list = collectCooldowns(mc);

        if (list.isEmpty()) {
            if (!inChat) return;
            list = List.of(
                    new DisplayEntry(Identifier.of("minecraft", "ender_pearl"), new ItemStack(Items.ENDER_PEARL), 9.5f, 0.65f),
                    new DisplayEntry(Identifier.of("minecraft", "chorus_fruit"), new ItemStack(Items.CHORUS_FRUIT), 1.2f, 0.15f),
                    new DisplayEntry(Identifier.of("minecraft", "sugar"), new ItemStack(Items.SUGAR), 14.0f, 0.90f)
            );
        }

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        Interface iface = Interface.getInstance();
        float combatAlpha = iface != null ? iface.getCombatAlphaFactor("cooldowns") : 1.0f;
        float combatScale = iface != null ? iface.getCombatScaleFactor("cooldowns") : 1.0f;
        if (combatAlpha <= 0.01f) return;

        float s = scale.getValueFloat() * combatScale;

        float slotH = 32.0f * s;
        float pillRadius = slotH / 2.0f;
        float iconDiscR = 11.5f * s;
        float barW = 3.2f * s;
        float barH = 17.0f * s;
        float barR = barW / 2.0f;

        float slotW = (iconDiscR * 2.0f) + (6.0f * s) + barW + (10.0f * s);
        float padSide = 6.0f * s;
        int count = list.size();
        float pillW = padSide * 2.0f + count * slotW;
        float pillH = slotH;

        float userX = posX.getValueFloat() * (screenW - pillW);
        float userY = posY.getValueFloat() * (screenH - pillH);
        float px = Math.clamp(userX, 4.0f, Math.max(4.0f, screenW - pillW - 4.0f));
        float py = Math.clamp(userY, 15.0f, Math.max(15.0f, screenH - pillH - 15.0f));

        float targetY = iface != null ? iface.getAdjustedY(px, py, pillW, pillH) : py;
        if (dragging || animatedY < 0.0f) {
            animatedY = dragging ? py : targetY;
        } else if (Math.abs(animatedY - targetY) > 0.1f) {
            animatedY += (targetY - animatedY) * 0.25f;
        } else {
            animatedY = targetY;
        }
        py = animatedY;

        this.lastX = px;
        this.lastY = py;
        this.lastW = pillW;
        this.lastH = pillH;

        float op = opacity.getValueFloat() / 255.0f;
        int c = bgColor.get();
        int finalBg = ColorUtil.rgba(ColorUtil.red(c), ColorUtil.green(c), ColorUtil.blue(c), (int) (op * 255.0f * combatAlpha));

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, pillW, pillH, pillRadius, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 110), combatAlpha), 8.0f * s);
        Draw2DProcessor.INSTANCE.drawPanelBlur(null, px, py, pillW, pillH, pillRadius, combatAlpha);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, pillW, pillH, pillRadius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, pillW, pillH, pillRadius, 0.8f, ColorUtil.rgba(255, 255, 255, (int) (22 * combatAlpha)));

        if (hudThemeGradient.isEnabled()) {
            int gradC = iface != null ? iface.computeHudGradientColor(hudGradientStrength.getValueFloat(), combatAlpha)
                    : ColorUtil.rgba(168, 85, 247, (int) ((hudGradientStrength.getValueFloat() / 100.0f) * 45.0f * combatAlpha));
            Draw2DProcessor.INSTANCE.drawGradient(null, px, py, pillW, pillH, pillRadius, gradC, 0x00000000, 0x00000000, gradC);
        }
        DrawBatcher.flushPending();
        if (hudThemeGradient.isEnabled() && combatAlpha > 0.15f) {
            Render2D.drawDiagonalSheen(context, px, py, pillW, pillH, pillRadius, 1200L, combatAlpha);
        }

        for (int i = 0; i < count; i++) {
            DisplayEntry entry = list.get(i);
            float slotStartX = px + padSide + i * slotW;

            if (i > 0) {
                float divX = slotStartX - (1.5f * s);
                float divY = py + (pillH - 18.0f * s) / 2.0f;
                Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 18.0f * s, 0.5f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 18), combatAlpha));
            }

            float discCx = slotStartX + iconDiscR + (2.0f * s);
            float discCy = py + pillH / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, discCx - iconDiscR, discCy - iconDiscR, iconDiscR * 2.0f, iconDiscR * 2.0f, iconDiscR, ColorUtil.withAlphaFloat(ColorUtil.rgba(46, 46, 46, 240), combatAlpha));

            float barX = discCx + iconDiscR + (5.0f * s);
            float barY = py + (pillH - barH) / 2.0f;

            Draw2DProcessor.INSTANCE.drawRect(null, barX, barY, barW, barH, barR, ColorUtil.withAlphaFloat(ColorUtil.rgba(42, 42, 42, 255), combatAlpha));

            float frac = Math.clamp(entry.progress, 0.0f, 1.0f);
            float fillH = Math.max(barW, barH * frac);
            float fillY = barY + (barH - fillH);

            int fillColor;
            if (frac <= 0.25f) {
                fillColor = ColorUtil.rgba(236, 34, 31, 255);
            } else {
                fillColor = ColorUtil.rgba(20, 174, 92, 255);
            }

            Draw2DProcessor.INSTANCE.drawRect(null, barX, fillY, barW, fillH, barR, ColorUtil.withAlphaFloat(fillColor, combatAlpha));
        }

        DrawBatcher.flushPending();

        for (int i = 0; i < count; i++) {
            DisplayEntry entry = list.get(i);
            float slotStartX = px + padSide + i * slotW;
            float discCx = slotStartX + iconDiscR + (2.0f * s);
            float discCy = py + pillH / 2.0f;

            if (!entry.stack.isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(discCx, discCy);
                context.getMatrices().scale(s, s);
                context.getMatrices().translate(-8.0f, -8.0f);
                context.drawItem(entry.stack, 0, 0);
                context.getMatrices().popMatrix();
            }
        }
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        var bounds = new HudSnapHelper.HudElementBounds("cooldowns", lastX, lastY, lastW, lastH);
        int scalePercent = Math.round(scale.getValueFloat() * 100.0f);
        if (dragging) {
            onChatMouseDragged(mouseX, mouseY, 0);
            HudSnapHelper.renderDragOverlay(bounds, currentSnap, screenW, screenH, scalePercent);
        } else {
            HudSnapHelper.renderHoverOverlay(bounds, screenW, screenH, mouseX, mouseY, scalePercent);
        }
        DrawBatcher.flushPending();
    }

    public boolean onChatMouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled()) return false;
        boolean inside = mouseX >= lastX - 3.0f && mouseX <= lastX + lastW + 3.0f && mouseY >= lastY - 3.0f && mouseY <= lastY + lastH + 3.0f;
        if (inside && button == 0) {
            dragging = true;
            dragOffsetX = (float) mouseX - lastX;
            dragOffsetY = (float) mouseY - lastY;
            return true;
        }
        return false;
    }

    public boolean onChatMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            currentSnap = null;
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

            float proposedX = (float) mouseX - dragOffsetX;
            float proposedY = (float) mouseY - dragOffsetY;

            HudSnapHelper.SnapResult snap = HudSnapHelper.calculateSnap("cooldowns", proposedX, proposedY, lastW, lastH, screenW, screenH);
            this.currentSnap = snap;

            float maxAvailX = Math.max(1.0f, screenW - lastW);
            float maxAvailY = Math.max(1.0f, screenH - lastH);

            posX.setValue(Math.clamp(snap.snappedX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(snap.snappedY / maxAvailY, 0.0, 1.0));
            return true;
        }
        return false;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;
        boolean inside = mouseX >= lastX - 5.0f && mouseX <= lastX + lastW + 5.0f && mouseY >= lastY - 5.0f && mouseY <= lastY + lastH + 5.0f;
        if (inside) {
            double cur = scale.get();
            double step = (amount > 0) ? 0.05 : -0.05;
            scale.setValue(Math.clamp(cur + step, 0.5, 1.5));
            ConfigManager.save();
            return true;
        }
        return false;
    }
}
