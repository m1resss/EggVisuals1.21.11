package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmorHUD extends Module {
    private static ArmorHUD instance;

    public static ArmorHUD getInstance() {
        return instance;
    }

    private final ModeSetting style = new ModeSetting("Вид", "Пиллы", "Стиль отображения брони", "Пиллы", "Карточка");
    private final ModeSetting orientation = new ModeSetting("Ориентация", "Вертикальная", "Направление элементов", "Вертикальная", "Горизонтальная");
    private final ModeSetting durabilityFormat = new ModeSetting("Прочность", "Число", "Формат прочности", "Число", "Проценты");
    private final BooleanSetting showHeader = new BooleanSetting("Шапка", true, "Отображать заголовок Armor");
    private final BooleanSetting hideEmpty = new BooleanSetting("Скрывать пустые", false, "Скрывать слоты без надетой брони");
    private final BooleanSetting warningColor = new BooleanSetting("Цвет прочности", true, "Окрашивать число в желтый/красный при низкой прочности");
    private final SliderSetting rounding = new SliderSetting("Скругление", 6.0, 0.0, 16.0, 0.5, "Скругление углов плашек");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 255.0, 20.0, 255.0, 5.0, "Прозрачность фона плашек");
    private final SliderSetting posX = new SliderSetting("X", 0.015, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.55, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб худа");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private boolean contextMenuOpen = false;
    private float contextMenuX = 0.0f;
    private float contextMenuY = 0.0f;

    private float lastX = 20.0f;
    private float lastY = 150.0f;
    private float lastW = 75.0f;
    private float lastH = 115.0f;

    public ArmorHUD() {
        super("ArmorHUD", "Индикатор надетой брони и её прочности", Category.ESP);
        instance = this;
        posX.setVisible(() -> false);
        posY.setVisible(() -> false);
        addSettings(style, orientation, durabilityFormat, showHeader, hideEmpty, warningColor, rounding, opacity, scale, posX, posY);
        setEnabled(true);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        List<ItemStack> armorPieces = collectArmor(mc);
        boolean allEmpty = armorPieces.stream().allMatch(ItemStack::isEmpty);

        if (allEmpty) {
            if (!inChat) return;
            armorPieces = getPreviewArmor();
        }

        Interface iface = Interface.getInstance();
        float combatAlpha = iface != null ? iface.getCombatAlphaFactor("armor") : 1.0f;
        float combatScale = iface != null ? iface.getCombatScaleFactor("armor") : 1.0f;
        float s = scale.getValueFloat() * combatScale;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        List<ItemStack> activePieces = new ArrayList<>();
        for (ItemStack piece : armorPieces) {
            if (piece.isEmpty() && hideEmpty.isEnabled()) continue;
            activePieces.add(piece);
        }

        if (activePieces.isEmpty() && !showHeader.isEnabled()) {
            return;
        }

        float radius = rounding.getValueFloat() * s;
        int baseBg = iface != null ? iface.getWatermarkBgColor() : 0xFF14151E;
        float op = opacity.getValueFloat() / 255.0f;
        int bgAlpha = (int) (op * 255.0f * combatAlpha);
        int finalBg = ColorUtil.rgba(ColorUtil.red(baseBg), ColorUtil.green(baseBg), ColorUtil.blue(baseBg), bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, (int) (22 * combatAlpha));

        boolean isPills = "Пиллы".equalsIgnoreCase(style.get());
        boolean isVertical = "Вертикальная".equalsIgnoreCase(orientation.get());

        if (isPills) {
            renderPills(context, activePieces, s, radius, finalBg, borderColor, combatAlpha, screenW, screenH, isVertical);
        } else {
            renderCard(context, activePieces, s, radius, finalBg, borderColor, combatAlpha, screenW, screenH, isVertical, iface);
        }
    }

    private void renderPills(DrawContext context, List<ItemStack> pieces, float s, float radius, int finalBg, int borderColor, float combatAlpha, int screenW, int screenH, boolean isVertical) {
        boolean header = showHeader.isEnabled();
        float headerIconSize = 11.0f * s;
        float headerFontSize = 10.5f * s;
        float headerTextW = Fonts.d.a("Armor", headerFontSize);
        float headerW = 7.0f * s + headerIconSize + 5.0f * s + 1.0f + 5.0f * s + headerTextW + 8.0f * s;
        float headerH = 20.0f * s;

        float itemPillH = 21.0f * s;
        float itemSize = 16.0f * s;
        float maxItemTextW = 0.0f;
        List<String> durStrings = new ArrayList<>();
        List<Integer> durColors = new ArrayList<>();

        for (ItemStack piece : pieces) {
            String dur = getDurabilityText(piece);
            durStrings.add(dur);
            durColors.add(getDurabilityColor(piece, combatAlpha));
            float tw = Fonts.d.a(dur, 10.5f * s);
            if (tw > maxItemTextW) maxItemTextW = tw;
        }

        float singleItemW = 6.0f * s + itemSize + 4.5f * s + 1.0f + 5.0f * s + maxItemTextW + 7.5f * s;
        float pillGap = 3.5f * s;

        float totalW, totalH;
        float commonPillW = Math.max(headerW, singleItemW);

        if (isVertical) {
            int rowCount = (header ? 1 : 0) + pieces.size();
            totalW = commonPillW;
            totalH = (header ? headerH : 0.0f) + (pieces.size() * itemPillH) + (Math.max(0, rowCount - 1) * pillGap);
        } else {
            int colCount = (header ? 1 : 0) + pieces.size();
            totalW = (header ? headerW + pillGap : 0.0f) + (pieces.size() * singleItemW) + (Math.max(0, pieces.size() - 1) * pillGap);
            totalH = Math.max(header ? headerH : 0.0f, itemPillH);
        }

        float userX = posX.getValueFloat() * (screenW - totalW);
        float userY = posY.getValueFloat() * (screenH - totalH);
        float px = Math.clamp(userX, 4.0f, screenW - totalW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - totalH - 15.0f);

        this.lastX = px;
        this.lastY = py;
        this.lastW = totalW;
        this.lastH = totalH;

        float curX = px;
        float curY = py;

        if (header) {
            float hw = isVertical ? commonPillW : headerW;
            drawPillBackdrop(curX, curY, hw, headerH, radius, finalBg, borderColor, combatAlpha, s);

            float iconX = curX + 7.0f * s;
            float iconY = curY + (headerH - Fonts.hud.a(headerIconSize)) / 2.0f;
            Fonts.hud.a(null, "\uf6e1", iconX, iconY, headerIconSize, ColorUtil.rgba(56, 189, 248, (int) (255 * combatAlpha)));

            float divX = iconX + headerIconSize + 5.0f * s;
            float divY = curY + (headerH - 10.0f * s) / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 10.0f * s, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (28 * combatAlpha)));

            float textX = divX + 1.0f + 5.0f * s;
            float textY = curY + (headerH - Fonts.d.a(headerFontSize)) / 2.0f;
            Fonts.d.a(null, "Armor", textX, textY, headerFontSize, ColorUtil.rgba(255, 255, 255, (int) (245 * combatAlpha)));
            DrawBatcher.flushPending();

            if (isVertical) {
                curY += headerH + pillGap;
            } else {
                curX += hw + pillGap;
            }
        }

        for (int i = 0; i < pieces.size(); i++) {
            ItemStack piece = pieces.get(i);
            String dur = durStrings.get(i);
            int textColor = durColors.get(i);
            float pw = isVertical ? commonPillW : singleItemW;

            drawPillBackdrop(curX, curY, pw, itemPillH, radius, finalBg, borderColor, combatAlpha, s);

            float itemX = curX + 5.0f * s;
            float itemY = curY + (itemPillH - itemSize) / 2.0f;

            DrawBatcher.flushPending();
            if (!piece.isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(itemX, itemY);
                context.getMatrices().scale(s, s);
                context.drawItem(piece, 0, 0);
                context.getMatrices().popMatrix();
            } else {
                // Ghost slot indicator
                float ghostSize = 10.0f * s;
                float gx = itemX + (itemSize - ghostSize) / 2.0f;
                float gy = itemY + (itemSize - ghostSize) / 2.0f;
                Fonts.hud.a(null, "\uf6e1", gx, gy, ghostSize, ColorUtil.rgba(255, 255, 255, (int) (40 * combatAlpha)));
            }

            float divX = itemX + itemSize + 4.5f * s;
            float divY = curY + (itemPillH - 10.0f * s) / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 10.0f * s, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (25 * combatAlpha)));

            float textX = divX + 1.0f + 5.0f * s;
            float textFontSize = 10.5f * s;
            float textY = curY + (itemPillH - Fonts.d.a(textFontSize)) / 2.0f;
            Fonts.d.a(null, dur, textX, textY, textFontSize, textColor);
            DrawBatcher.flushPending();

            if (isVertical) {
                curY += itemPillH + pillGap;
            } else {
                curX += pw + pillGap;
            }
        }
    }

    private void renderCard(DrawContext context, List<ItemStack> pieces, float s, float radius, int finalBg, int borderColor, float combatAlpha, int screenW, int screenH, boolean isVertical, Interface iface) {
        boolean header = showHeader.isEnabled();
        float headerIconSize = 11.0f * s;
        float headerFontSize = 10.5f * s;
        float headerTextW = Fonts.d.a("Armor", headerFontSize);
        float headerW = 7.0f * s + headerIconSize + 5.0f * s + 1.0f + 5.0f * s + headerTextW + 8.0f * s;
        float headerH = header ? 21.0f * s : 0.0f;

        float rowH = 20.0f * s;
        float itemSize = 16.0f * s;
        float maxItemTextW = 0.0f;
        List<String> durStrings = new ArrayList<>();
        List<Integer> durColors = new ArrayList<>();

        for (ItemStack piece : pieces) {
            String dur = getDurabilityText(piece);
            durStrings.add(dur);
            durColors.add(getDurabilityColor(piece, combatAlpha));
            float tw = Fonts.d.a(dur, 10.5f * s);
            if (tw > maxItemTextW) maxItemTextW = tw;
        }

        float singleItemW = 6.0f * s + itemSize + 4.5f * s + 1.0f + 5.0f * s + maxItemTextW + 7.5f * s;
        float cardPadX = 6.0f * s;
        float cardPadY = 6.0f * s;

        float totalW, totalH;
        if (isVertical) {
            totalW = Math.max(headerW + cardPadX * 2.0f, singleItemW + cardPadX * 2.0f);
            totalH = headerH + (pieces.size() * rowH) + cardPadY * 2.0f;
        } else {
            totalW = cardPadX * 2.0f + (pieces.size() * singleItemW) + Math.max(0, pieces.size() - 1) * 4.0f * s;
            totalH = headerH + rowH + cardPadY * 2.0f;
        }

        float userX = posX.getValueFloat() * (screenW - totalW);
        float userY = posY.getValueFloat() * (screenH - totalH);
        float px = Math.clamp(userX, 4.0f, screenW - totalW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - totalH - 15.0f);

        this.lastX = px;
        this.lastY = py;
        this.lastW = totalW;
        this.lastH = totalH;

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, totalW, totalH, radius, ColorUtil.rgba(0, 0, 0, (int) (55 * combatAlpha)), 5.0f * s);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, totalW, totalH, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, totalW, totalH, radius, 0.8f, borderColor);

        if (iface != null && iface.isHudThemeGradient()) {
            float str = iface.getHudGradientStrength() / 100.0f;
            int gradC = ColorUtil.rgba(168, 85, 247, (int) (str * 45.0f * combatAlpha));
            Draw2DProcessor.INSTANCE.drawGradient(null, px, py, totalW, totalH, radius, gradC, 0x00000000, 0x00000000, gradC);
        }

        float curY = py + cardPadY;

        if (header) {
            float iconX = px + cardPadX;
            float iconY = curY + (headerH - Fonts.hud.a(headerIconSize)) / 2.0f;
            Fonts.hud.a(null, "\uf6e1", iconX, iconY, headerIconSize, ColorUtil.rgba(56, 189, 248, (int) (255 * combatAlpha)));

            float divX = iconX + headerIconSize + 5.0f * s;
            float divY = curY + (headerH - 10.0f * s) / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 10.0f * s, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (28 * combatAlpha)));

            float textX = divX + 1.0f + 5.0f * s;
            float textY = curY + (headerH - Fonts.d.a(headerFontSize)) / 2.0f;
            Fonts.d.a(null, "Armor", textX, textY, headerFontSize, ColorUtil.rgba(255, 255, 255, (int) (245 * combatAlpha)));

            float sepY = curY + headerH - 2.0f * s;
            Draw2DProcessor.INSTANCE.drawRect(null, px + 6.0f * s, sepY, totalW - 12.0f * s, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * combatAlpha)));
            curY += headerH + 2.0f * s;
        }

        float curX = px + cardPadX;

        for (int i = 0; i < pieces.size(); i++) {
            ItemStack piece = pieces.get(i);
            String dur = durStrings.get(i);
            int textColor = durColors.get(i);

            float rowY = isVertical ? curY : curY;
            float rowX = isVertical ? curX : curX;

            float itemX = rowX;
            float itemY = rowY + (rowH - itemSize) / 2.0f;

            DrawBatcher.flushPending();
            if (!piece.isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(itemX, itemY);
                context.getMatrices().scale(s, s);
                context.drawItem(piece, 0, 0);
                context.getMatrices().popMatrix();
            }

            float divX = itemX + itemSize + 4.5f * s;
            float divY = rowY + (rowH - 10.0f * s) / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, divX, divY, 1.0f, 10.0f * s, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (25 * combatAlpha)));

            float textX = divX + 1.0f + 5.0f * s;
            float textFontSize = 10.5f * s;
            float textY = rowY + (rowH - Fonts.d.a(textFontSize)) / 2.0f;
            Fonts.d.a(null, dur, textX, textY, textFontSize, textColor);
            DrawBatcher.flushPending();

            if (isVertical) {
                curY += rowH;
            } else {
                curX += singleItemW + 4.0f * s;
            }
        }
    }

    private void drawPillBackdrop(float x, float y, float w, float h, float radius, int finalBg, int borderColor, float combatAlpha, float s) {
        Draw2DProcessor.INSTANCE.drawGlow(null, x, y, w, h, radius, ColorUtil.rgba(0, 0, 0, (int) (50 * combatAlpha)), 5.0f * s);
        Draw2DProcessor.INSTANCE.drawRect(null, x, y, w, h, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, x, y, w, h, radius, 0.8f, borderColor);
    }

    private String getDurabilityText(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return "--";
        int maxDmg = stack.getMaxDamage();
        int currentDmg = stack.getDamage();
        int remaining = Math.max(0, maxDmg - currentDmg);
        if ("Проценты".equalsIgnoreCase(durabilityFormat.get())) {
            float pct = maxDmg > 0 ? (float) remaining / (float) maxDmg : 1.0f;
            return Math.round(pct * 100.0f) + "%";
        }
        return String.valueOf(remaining);
    }

    private int getDurabilityColor(ItemStack stack, float combatAlpha) {
        if (stack.isEmpty() || !stack.isDamageable() || !warningColor.isEnabled()) {
            return ColorUtil.rgba(240, 243, 250, (int) (245 * combatAlpha));
        }
        int maxDmg = stack.getMaxDamage();
        int currentDmg = stack.getDamage();
        int remaining = Math.max(0, maxDmg - currentDmg);
        float frac = maxDmg > 0 ? (float) remaining / (float) maxDmg : 1.0f;
        if (frac <= 0.15f) {
            return ColorUtil.rgba(239, 68, 68, (int) (255 * combatAlpha));
        } else if (frac <= 0.35f) {
            return ColorUtil.rgba(245, 158, 11, (int) (255 * combatAlpha));
        }
        return ColorUtil.rgba(240, 243, 250, (int) (245 * combatAlpha));
    }

    private List<ItemStack> collectArmor(MinecraftClient mc) {
        if (mc.player == null) return Collections.emptyList();
        List<ItemStack> list = new ArrayList<>(4);
        list.add(mc.player.getEquippedStack(EquipmentSlot.HEAD));
        list.add(mc.player.getEquippedStack(EquipmentSlot.CHEST));
        list.add(mc.player.getEquippedStack(EquipmentSlot.LEGS));
        list.add(mc.player.getEquippedStack(EquipmentSlot.FEET));
        return list;
    }

    private List<ItemStack> getPreviewArmor() {
        List<ItemStack> list = new ArrayList<>(4);
        ItemStack helm = new ItemStack(Items.NETHERITE_HELMET);
        helm.setDamage(Math.max(0, helm.getMaxDamage() - 407));
        list.add(helm);

        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        chest.setDamage(Math.max(0, chest.getMaxDamage() - 592));
        list.add(chest);

        ItemStack legs = new ItemStack(Items.NETHERITE_LEGGINGS);
        legs.setDamage(Math.max(0, legs.getMaxDamage() - 555));
        list.add(legs);

        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        boots.setDamage(Math.max(0, boots.getMaxDamage() - 481));
        list.add(boots);

        return list;
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

            String label = "HUD — Броня";
            float lw = Fonts.b.a(label, 11.5f);
            float lx = lastX + (lastW - lw) / 2.0f;
            float ly = lastY + lastH + 6.0f;
            Fonts.b.a(null, label, lx, ly, 11.5f, ColorUtil.rgba(255, 255, 255, 160));
        }

        boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
        if (inside || dragging) {
            float rad = rounding.getValueFloat() * scale.getValueFloat();
            Draw2DProcessor.INSTANCE.drawOutline(null, lastX - 2.0f, lastY - 2.0f, lastW + 4.0f, lastH + 4.0f, rad, 1.0f, Theme.getAccent());

            String scaleBadge = "Броня: " + Math.round(scale.getValueFloat() * 100.0f) + "%";
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
            float mw = 140.0f;
            float mh = 94.0f;
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + mw && mouseY >= contextMenuY && mouseY <= contextMenuY + mh) {
                float iy = contextMenuY + 22.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    style.cycle();
                    ConfigManager.save();
                    return true;
                }
                iy += 16.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    orientation.cycle();
                    ConfigManager.save();
                    return true;
                }
                iy += 16.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    durabilityFormat.cycle();
                    ConfigManager.save();
                    return true;
                }
                iy += 16.0f;
                if (mouseY >= iy && mouseY <= iy + 14.0f) {
                    showHeader.toggle();
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
                contextMenuX = (float) Math.clamp(mouseX, 10.0, screenW - 150.0);
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
        float mw = 140.0f;
        float mh = 94.0f;
        float mx = contextMenuX;
        float my = contextMenuY;

        Draw2DProcessor.INSTANCE.drawGlow(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(0, 0, 0, 190), 10.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, mx, my, mw, mh, 6.0f, ColorUtil.rgba(22, 23, 30, 248));
        Draw2DProcessor.INSTANCE.drawOutline(null, mx, my, mw, mh, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        Fonts.d.a(null, "Настройки брони", mx + 8.0f, my + 6.0f, 12.0f, Theme.getAccentHover());
        Draw2DProcessor.INSTANCE.drawRect(null, mx + 6.0f, my + 18.0f, mw - 12.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 15));

        float itemY = my + 22.0f;
        renderContextItem(context, mx, itemY, mw, "Вид: " + style.get(), true, mouseX, mouseY); itemY += 16.0f;
        renderContextItem(context, mx, itemY, mw, "Ориентация: " + orientation.get(), true, mouseX, mouseY); itemY += 16.0f;
        renderContextItem(context, mx, itemY, mw, "Прочность: " + durabilityFormat.get(), true, mouseX, mouseY); itemY += 16.0f;
        renderContextItem(context, mx, itemY, mw, "Шапка", showHeader.isEnabled(), mouseX, mouseY);
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
