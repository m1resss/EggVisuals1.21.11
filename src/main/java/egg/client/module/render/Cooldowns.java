package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.mixin.ItemCooldownEntryAccessor;
import egg.client.mixin.ItemCooldownManagerAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
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
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб худа");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    private float lastX = 200.0f;
    private float lastY = 300.0f;
    private float lastW = 100.0f;
    private float lastH = 34.0f;

    private static class DisplayEntry {
        final Identifier id;
        final ItemStack stack;
        final float remainingSec;
        final float progress; // 0.0 = just started (full cooldown), 1.0 = almost done / finished

        DisplayEntry(Identifier id, ItemStack stack, float remainingSec, float progress) {
            this.id = id;
            this.stack = stack;
            this.remainingSec = remainingSec;
            this.progress = progress;
        }
    }

    // Blacklist of items requested by user:
    // "также не показывай кд у элитр , феерверков и блоков слизи и ведра воды"
    private static final Set<Item> BLACKLIST_ITEMS = Set.of(
            Items.ELYTRA,
            Items.FIREWORK_ROCKET,
            Items.FIREWORK_STAR,
            Items.SLIME_BLOCK,
            Items.WATER_BUCKET
    );

    public Cooldowns() {
        super("Cooldowns", "Индикатор перезарядки предметов и способностей в виде горизонтальной капсулы", Category.ESP);
        instance = this;
        addSettings(bgColor, scale);
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
            // progress: 1.0 -> 0.0 as time expires, so fraction remaining = remainingTicks / totalTicks
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
            // In chat GUI editor, show dummy preview so player can drag/position the widget
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

        float s = scale.getValueFloat() * combatScale;

        // Exact pill dimensions according to reference:
        // Height ~ 32px scaled
        // Each item slot: icon disc + vertical bar + divider
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
        float px = Math.clamp(userX, 4.0f, screenW - pillW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - pillH - 15.0f);

        this.lastX = px;
        this.lastY = py;
        this.lastW = pillW;
        this.lastH = pillH;

        // 1. Soft atmospheric dark drop shadow
        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, pillW, pillH, pillRadius, ColorUtil.withAlphaFloat(ColorUtil.rgba(0, 0, 0, 110), combatAlpha), 8.0f * s);

        // 2. Pill background (customizable, default #C4151515)
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, pillW, pillH, pillRadius, ColorUtil.withAlphaFloat(bgColor.get(), combatAlpha));

        // 3. Crisp subtle border outline
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, pillW, pillH, pillRadius, 0.8f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 20), combatAlpha));

        // Render slots
        for (int i = 0; i < count; i++) {
            DisplayEntry entry = list.get(i);
            float slotStartX = px + padSide + i * slotW;

            // Draw vertical separator line between slots (except before the first slot)
            if (i > 0) {
                float divX = slotStartX - (1.5f * s);
                float divY = py + (pillH - 18.0f * s) / 2.0f;
                Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 18.0f * s, 0.5f, ColorUtil.withAlphaFloat(ColorUtil.rgba(255, 255, 255, 18), combatAlpha));
            }

            // Circular icon backdrop disc (#2E2E2E / rgba(48, 48, 48, 255))
            float discCx = slotStartX + iconDiscR + (2.0f * s);
            float discCy = py + pillH / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, discCx - iconDiscR, discCy - iconDiscR, iconDiscR * 2.0f, iconDiscR * 2.0f, iconDiscR, ColorUtil.withAlphaFloat(ColorUtil.rgba(46, 46, 46, 240), combatAlpha));

            // Vertical progress bar
            float barX = discCx + iconDiscR + (5.0f * s);
            float barY = py + (pillH - barH) / 2.0f;

            // Dark empty bar track (#2A2A2A)
            Draw2DProcessor.INSTANCE.drawRect(null, barX, barY, barW, barH, barR, ColorUtil.withAlphaFloat(ColorUtil.rgba(42, 42, 42, 255), combatAlpha));

            // Filled progress (from bottom to top)
            // progress = fraction remaining: 1.0 -> 0.0
            float frac = Math.clamp(entry.progress, 0.0f, 1.0f);
            float fillH = Math.max(barW, barH * frac);
            float fillY = barY + (barH - fillH);

            // Color coding according to remaining fraction:
            // When < 0.25 (last quarter of cooldown, almost ready): red (RGB 236, 34, 31)
            // When > 0.25 (running cooldown): vibrant emerald green (RGB 20, 174, 92)
            int fillColor;
            if (frac <= 0.25f) {
                fillColor = ColorUtil.rgba(236, 34, 31, 255);
            } else {
                fillColor = ColorUtil.rgba(20, 174, 92, 255);
            }

            Draw2DProcessor.INSTANCE.drawRect(null, barX, fillY, barW, fillH, barR, ColorUtil.withAlphaFloat(fillColor, combatAlpha));
        }

        DrawBatcher.flushPending();

        // 4. Render 3D item models on top of their circular discs
        for (int i = 0; i < count; i++) {
            DisplayEntry entry = list.get(i);
            float slotStartX = px + padSide + i * slotW;
            float discCx = slotStartX + iconDiscR + (2.0f * s);
            float discCy = py + pillH / 2.0f;

            if (!entry.stack.isEmpty()) {
                context.drawItem(entry.stack, Math.round(discCx - 8.0f), Math.round(discCy - 8.0f));
            }
        }
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

        // Render cooldowns widget in chat screen for drag & drop
        render(context, RenderTickCounter.ONE);

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

            String label = "HUD - Кулдауны";
            float lw = Fonts.b.a(label, 11.5f);
            float lx = lastX + (lastW - lw) / 2.0f;
            float ly = lastY + lastH + 6.0f;
            Fonts.b.a(null, label, lx, ly, 11.5f, ColorUtil.rgba(255, 255, 255, 160));
        }

        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside || dragging) {
            float rad = (lastH + 4.0f) / 2.0f;
            Draw2DProcessor.INSTANCE.drawOutline(null, lastX - 2.0f, lastY - 2.0f, lastW + 4.0f, lastH + 4.0f, rad, 1.0f, Theme.getAccent());

            String scaleBadge = "КД: " + Math.round(scale.getValueFloat() * 100.0f) + "%";
            float bw = Fonts.b.a(scaleBadge, 9.5f) + 8.0f;
            float bx = lastX + (lastW - bw) / 2.0f;
            float by = lastY + lastH + 5.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 13.0f, 3.0f, ColorUtil.rgba(14, 15, 20, 220));
            Fonts.b.a(null, scaleBadge, bx + 4.0f, by + 2.5f, 9.5f, Theme.getAccentHover());
            DrawBatcher.flushPending();
        }
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
