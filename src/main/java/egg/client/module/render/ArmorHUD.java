package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
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
    private final BooleanSetting smartAutoHide = new BooleanSetting("Умное скрытие", false, "Скрывать броню, если вся экипировка цела на 100%");
    private final SliderSetting rounding = new SliderSetting("Скругление", 6.0, 0.0, 16.0, 0.5, "Скругление углов плашек");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 175.0, 10.0, 255.0, 5.0, "Прозрачность фона плашек");
    private final SliderSetting posX = new SliderSetting("X", 0.015, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.55, 0.0, 1.0, 0.001, "Позиция Y");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 1.5, 0.05, "Масштаб худа");

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private final HudContextMenu contextMenu = new HudContextMenu("Настройки брони");
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastX = 20.0f;
    private float lastY = 150.0f;
    private float lastW = 48.0f;
    private float lastH = 88.0f;
    private float animatedY = -1.0f;
    private final Animation autoHideAnim = new Animation(1.0f, 260L, Easing.EASE_OUT_CUBIC);

    @Override
    public void onDisable() {
        animatedY = -1.0f;
        autoHideAnim.setValue(1.0f);
    }

    public float getLastX() { return lastX; }
    public float getLastY() { return lastY; }
    public float getLastW() { return lastW; }
    public float getLastH() { return lastH; }

    public ArmorHUD() {
        super("ArmorHUD", "Показывает надетую броню, предметы в руках и их прочность", Category.ESP);
        instance = this;
        posX.setVisible(() -> false);
        posY.setVisible(() -> false);

        contextMenu.addMode("Вид", () -> style.get(), () -> style.cycle());
        contextMenu.addMode("Ориентация", () -> orientation.get(), () -> orientation.cycle());
        contextMenu.addMode("Прочность", () -> durabilityFormat.get(), () -> durabilityFormat.cycle());
        contextMenu.addToggle("Шапка", () -> showHeader.isEnabled(), () -> showHeader.toggle());
        contextMenu.addToggle("Умное скрытие", () -> smartAutoHide.isEnabled(), () -> smartAutoHide.toggle());

        addSettings(style, orientation, durabilityFormat, showHeader, hideEmpty, warningColor, smartAutoHide, rounding, opacity, scale, posX, posY);
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

        boolean allUndamaged = true;
        for (ItemStack piece : armorPieces) {
            if (!piece.isEmpty() && piece.isDamageable() && piece.getDamage() > 0) {
                allUndamaged = false;
                break;
            }
        }
        if (smartAutoHide.isEnabled() && allUndamaged && !inChat) {
            autoHideAnim.setTarget(0.0f);
        } else {
            autoHideAnim.setTarget(1.0f);
        }
        float autoHideFactor = autoHideAnim.get();
        if (autoHideFactor <= 0.01f) return;

        Interface iface = Interface.getInstance();
        float containerFactor = iface != null ? iface.getContainerAlphaFactor() : 1.0f;
        float combatAlpha = (iface != null ? iface.getCombatAlphaFactor("armor") : 1.0f) * autoHideFactor * containerFactor;
        if (combatAlpha <= 0.01f) return;
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
            renderPills(context, activePieces, s, radius, finalBg, borderColor, combatAlpha, screenW, screenH, isVertical, iface);
        } else {
            renderCard(context, activePieces, s, radius, finalBg, borderColor, combatAlpha, screenW, screenH, isVertical, iface);
        }
    }

    private void renderPills(DrawContext context, List<ItemStack> pieces, float s, float radius, int finalBg, int borderColor, float combatAlpha, int screenW, int screenH, boolean isVertical, Interface iface) {
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

        float targetY = iface != null ? iface.getAdjustedY(px, py, totalW, totalH) : py;
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
        float headerIconSize = 9.5f * s;
        float headerFontSize = 9.5f * s;
        float headerTextW = Fonts.d.a("Armor", headerFontSize);
        float cardPadX = 4.0f * s;
        float cardPadY = 3.5f * s;
        float headerW = headerIconSize + 3.5f * s + headerTextW;
        float headerH = header ? 15.0f * s : 0.0f;

        float rowH = 16.5f * s;
        float itemSize = 15.0f * s;
        float itemScale = (15.0f / 16.0f) * s;
        float itemTextGap = 3.5f * s;
        float textFontSize = 9.0f * s;

        float maxItemTextW = 0.0f;
        List<String> durStrings = new ArrayList<>();
        List<Integer> durColors = new ArrayList<>();

        for (ItemStack piece : pieces) {
            String dur = getDurabilityText(piece);
            durStrings.add(dur);
            durColors.add(getDurabilityColor(piece, combatAlpha));
            float tw = Fonts.d.a(dur, textFontSize);
            if (tw > maxItemTextW) maxItemTextW = tw;
        }

        float singleItemW = itemSize + itemTextGap + maxItemTextW;

        float totalW, totalH;
        if (isVertical) {
            totalW = Math.max(headerW, singleItemW) + cardPadX * 2.0f;
            totalH = cardPadY * 2.0f + (header ? headerH + 2.0f * s : 0.0f) + (pieces.size() * rowH);
        } else {
            float itemGap = 4.0f * s;
            totalW = cardPadX * 2.0f + (pieces.size() * singleItemW) + Math.max(0, pieces.size() - 1) * itemGap;
            totalW = Math.max(totalW, headerW + cardPadX * 2.0f);
            totalH = cardPadY * 2.0f + (header ? headerH + 2.0f * s : 0.0f) + rowH;
        }

        float userX = posX.getValueFloat() * (screenW - totalW);
        float userY = posY.getValueFloat() * (screenH - totalH);
        float px = Math.clamp(userX, 4.0f, screenW - totalW - 4.0f);
        float py = Math.clamp(userY, 15.0f, screenH - totalH - 15.0f);

        float targetY = iface != null ? iface.getAdjustedY(px, py, totalW, totalH) : py;
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
        this.lastW = totalW;
        this.lastH = totalH;

        Draw2DProcessor.INSTANCE.drawGlow(null, px, py, totalW, totalH, radius, ColorUtil.rgba(0, 0, 0, (int) (55 * combatAlpha)), 5.0f * s);
        Draw2DProcessor.INSTANCE.drawPanelBlur(null, px, py, totalW, totalH, radius, combatAlpha);
        Draw2DProcessor.INSTANCE.drawRect(null, px, py, totalW, totalH, radius, finalBg);
        Draw2DProcessor.INSTANCE.drawOutline(null, px, py, totalW, totalH, radius, 0.8f, borderColor);

        if (iface != null && iface.isHudThemeGradient()) {
            int gradC = iface.computeHudGradientColor(iface.getHudGradientStrength(), combatAlpha);
            Draw2DProcessor.INSTANCE.drawGradient(null, px, py, totalW, totalH, radius, gradC, 0x00000000, 0x00000000, gradC);
        }
        DrawBatcher.flushPending();
        if (iface != null && iface.isHudThemeGradient() && combatAlpha > 0.15f) {
            Render2D.drawDiagonalSheen(context, px, py, totalW, totalH, radius, 2800L, combatAlpha);
        }

        float curY = py + cardPadY;

        if (header) {
            float iconX = px + cardPadX;
            float iconY = curY + (headerH - Fonts.hud.a(headerIconSize)) / 2.0f;
            Fonts.hud.a(null, "\uf6e1", iconX, iconY, headerIconSize, ColorUtil.rgba(56, 189, 248, (int) (255 * combatAlpha)));

            float textX = iconX + headerIconSize + 3.5f * s;
            float textY = curY + (headerH - Fonts.d.a(headerFontSize)) / 2.0f;
            Fonts.d.a(null, "Armor", textX, textY, headerFontSize, ColorUtil.rgba(255, 255, 255, (int) (245 * combatAlpha)));

            float sepY = curY + headerH - 1.0f * s;
            Draw2DProcessor.INSTANCE.drawRect(null, px + cardPadX, sepY, totalW - cardPadX * 2.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, (int) (18 * combatAlpha)));
            curY += headerH + 2.0f * s;
        }

        float curX = px + cardPadX;

        for (int i = 0; i < pieces.size(); i++) {
            ItemStack piece = pieces.get(i);
            String dur = durStrings.get(i);
            int textColor = durColors.get(i);

            float rowY = curY;
            float rowX = isVertical ? px + cardPadX : curX;

            float itemX = rowX;
            float itemY = rowY + (rowH - itemSize) / 2.0f;

            DrawBatcher.flushPending();
            if (!piece.isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(itemX, itemY);
                context.getMatrices().scale(itemScale, itemScale);
                context.drawItem(piece, 0, 0);
                context.getMatrices().popMatrix();
            } else {
                float ghostSize = 9.0f * s;
                float gx = itemX + (itemSize - ghostSize) / 2.0f;
                float gy = itemY + (itemSize - ghostSize) / 2.0f;
                Fonts.hud.a(null, "\uf6e1", gx, gy, ghostSize, ColorUtil.rgba(255, 255, 255, (int) (35 * combatAlpha)));
            }

            float textX = itemX + itemSize + itemTextGap;
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
        Draw2DProcessor.INSTANCE.drawPanelBlur(null, x, y, w, h, radius, combatAlpha);
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
            HudSnapHelper.renderGridAndGuides(context, currentSnap, screenW, screenH,
                    new HudSnapHelper.HudElementBounds("armor", "Броня", lastX, lastY, lastW, lastH),
                    "HUD — Броня", Math.round(scale.getValueFloat() * 100.0f),
                    rounding.getValueFloat() * scale.getValueFloat());
        } else {
            boolean inside = mouseX >= lastX - 2.0f && mouseX <= lastX + lastW + 2.0f && mouseY >= lastY - 2.0f && mouseY <= lastY + lastH + 2.0f;
            if (inside) {
                float rad = rounding.getValueFloat() * scale.getValueFloat();
                String scaleBadge = "Броня: " + Math.round(scale.getValueFloat() * 100.0f) + "%";
                HudSnapHelper.renderHoverBadge(context, lastX, lastY, lastW, lastH, rad, scaleBadge);
            }
        }

        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            contextMenu.render(context, mouseX, mouseY);
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

        if (contextMenu.isOpen()) {
            contextMenu.setScale(scale.getValueFloat());
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        boolean inside = mouseX >= lastX - 3.0f && mouseX <= lastX + lastW + 3.0f && mouseY >= lastY - 3.0f && mouseY <= lastY + lastH + 3.0f;
        if (inside) {
            if (button == 1) {
                contextMenu.openSmart("armor", lastX, lastY, lastW, lastH, (float) mouseX, (float) mouseY, scale.getValueFloat());
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

            HudSnapHelper.SnapResult snap = HudSnapHelper.calculateSnap("armor", proposedX, proposedY, lastW, lastH, screenW, screenH);
            this.currentSnap = snap;

            float maxAvailX = Math.max(1.0f, screenW - lastW);
            float maxAvailY = Math.max(1.0f, screenH - lastH);

            posX.setValue(Math.clamp(snap.snappedX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(snap.snappedY / maxAvailY, 0.0, 1.0));
            return true;
        }
        return false;
    }
}
