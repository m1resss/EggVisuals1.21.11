package egg.client.ui.section;

import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.inventory.InventorySlotRule;
import egg.client.module.ModuleManager;
import egg.client.module.player.InventorySorter;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Dedicated spacious Inventory Configs section in ClickGUI.
 * Displays preset cards and uncompressed visual inventory grid with tooltips and sorting.
 */
public class InventoryConfigPanel {

    private static InventoryPreset detailedPreset = null;
    private static float scrollOffset = 0.0f;
    private static float targetScroll = 0.0f;
    private static float maxScroll = 0.0f;

    // Hover tooltip info
    private static InventorySlotRule hoveredRule = null;
    private static int hoveredSlotId = -1;
    private static boolean hoveredSlotHasItem = false;
    private static float tooltipX = 0;
    private static float tooltipY = 0;

    public static void setDetailedPreset(InventoryPreset preset) {
        detailedPreset = preset;
        targetScroll = 0.0f;
        scrollOffset = 0.0f;
    }

    public static InventoryPreset getDetailedPreset() {
        return detailedPreset;
    }

    public static void render(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        scrollOffset += (targetScroll - scrollOffset) * 0.25f;

        hoveredRule = null;
        hoveredSlotId = -1;

        context.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));

        if (detailedPreset != null) {
            renderDetailView(context, x, y, w, h, mouseX, mouseY, alpha);
        } else {
            renderCardsView(context, x, y, w, h, mouseX, mouseY, alpha);
        }

        context.disableScissor();

        // Render floating tooltip on top of everything
        if (hoveredRule != null && !hoveredRule.getDisplayStack().isEmpty()) {
            renderTooltip(context, hoveredRule, hoveredSlotId, hoveredSlotHasItem, mouseX, mouseY);
        }
    }

    // =========================================================================
    // VIEW 1: PRESET CARDS LIST VIEW
    // =========================================================================

    private static void renderCardsView(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        InventoryLayoutManager lm = InventoryLayoutManager.getInstance();
        List<InventoryPreset> presets = lm.getPresets();
        InventoryPreset activePreset = lm.getActivePreset();

        float curY = y + 4.0f - scrollOffset;

        // Header Title
        ClientFont.draw(context, "Профили инвентаря", x + 4.0f, curY, ClientFont.SF_BOLD, ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));
        ClientFont.draw(context, "Раскладки и авто-сортировка", x + 4.0f, curY + 14.0f, ClientFont.SF_REGULAR, ColorUtil.rgba(160, 168, 185, (int) (210 * alpha)));

        // "+ Создать профиль" button
        float newBtnW = 124.0f;
        float newBtnH = 22.0f;
        float newBtnX = x + w - newBtnW - 4.0f;
        float newBtnY = curY + 2.0f;

        boolean hoverNew = mouseX >= newBtnX && mouseX <= newBtnX + newBtnW && mouseY >= newBtnY && mouseY <= newBtnY + newBtnH;
        int newBg = hoverNew ? Theme.getAccentHover() : Theme.getAccent();
        Render2D.drawRoundedRect(context, newBtnX, newBtnY, newBtnW, newBtnH, 5.0f, ColorUtil.withAlphaFloat(newBg, alpha));
        ClientFont.drawCentered(context, "+ Создать профиль", newBtnX + newBtnW / 2.0f, newBtnY + newBtnH / 2.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        curY += 34.0f;

        if (presets.isEmpty()) {
            float boxY = curY + 6.0f;
            float boxH = 92.0f;
            Render2D.drawRoundedRect(context, x + 4.0f, boxY, w - 8.0f, boxH, 8.0f, ColorUtil.rgba(20, 22, 30, (int) (200 * alpha)));
            Render2D.drawRoundedOutline(context, x + 4.0f, boxY, w - 8.0f, boxH, 8.0f, 1.0f, ColorUtil.rgba(255, 255, 255, (int) (22 * alpha)));
            ClientFont.drawCentered(context, "Нет сохраненных профилей", x + w / 2.0f, boxY + 34.0f, ClientFont.SF_BOLD, ColorUtil.rgba(255, 255, 255, (int) (230 * alpha)));
            ClientFont.drawCentered(context, "Нажмите «+ Создать профиль» вверху, чтобы сохранить текущий инвентарь", x + w / 2.0f, boxY + 50.0f, ClientFont.SF_REGULAR, ColorUtil.rgba(160, 168, 185, (int) (190 * alpha)));
            maxScroll = 0.0f;
            return;
        }

        // Grid of Preset Cards (2 columns)
        float colGap = 8.0f;
        float rowGap = 8.0f;
        float cardW = (w - colGap) / 2.0f;
        float cardH = 102.0f;

        for (int i = 0; i < presets.size(); i++) {
            InventoryPreset p = presets.get(i);
            boolean isLeft = (i % 2 == 0);
            float cx = isLeft ? x : (x + cardW + colGap);
            float cy = curY + (i / 2) * (cardH + rowGap);

            boolean isHovered = mouseX >= cx && mouseX <= (cx + cardW) && mouseY >= cy && mouseY <= (cy + cardH);
            boolean isActive = (p == activePreset);

            int cardBg = isHovered ? Theme.getCardBgHover() : Theme.getCardBg();
            int cardBorder = isActive ? Theme.getAccent() : (isHovered ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);

            Render2D.drawRoundedRect(context, cx, cy, cardW, cardH, Theme.CARD_RADIUS, ColorUtil.withAlphaFloat(cardBg, alpha));
            Render2D.drawRoundedOutline(context, cx, cy, cardW, cardH, Theme.CARD_RADIUS, isActive ? 1.5f : 1.0f, ColorUtil.withAlphaFloat(cardBorder, alpha));

            // Card Title
            String name = p.getName();
            if (name.startsWith("КФГ ")) name = name.substring(4);
            if (name.startsWith("Профиль ")) name = name.substring(8);
            ClientFont.draw(context, name, cx + 10.0f, cy + 9.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

            // Active Badge or Select Pill
            if (isActive) {
                float badgeW = 58.0f;
                float badgeH = 16.0f;
                float bx = cx + cardW - badgeW - 8.0f;
                float by = cy + 7.0f;
                Render2D.drawRoundedRect(context, bx, by, badgeW, badgeH, 4.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
                ClientFont.drawCentered(context, "Активен", bx + badgeW / 2.0f, by + badgeH / 2.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);
            } else {
                float pillW = 60.0f;
                float pillH = 16.0f;
                float px = cx + cardW - pillW - 8.0f;
                float py = cy + 7.0f;
                boolean hoverAct = mouseX >= px && mouseX <= px + pillW && mouseY >= py && mouseY <= py + pillH;
                int actBg = hoverAct ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 20);
                Render2D.drawRoundedRect(context, px, py, pillW, pillH, 4.0f, ColorUtil.withAlphaFloat(actBg, alpha));
                ClientFont.drawCentered(context, "Выбрать", px + pillW / 2.0f, py + pillH / 2.0f, ClientFont.SF_MEDIUM, hoverAct ? 0xFFFFFFFF : 0xFFB0B8C8);
            }

            // Key item preview row (6 items)
            float slotY = cy + 30.0f;
            int[] previewSlots = {0, 1, 2, 3, 39, 40}; // Sword, pearl/gapple, totem, armor, offhand
            for (int s = 0; s < previewSlots.length; s++) {
                float sx = cx + 10.0f + s * 24.0f;
                Render2D.drawRoundedRect(context, sx, slotY, 21.0f, 21.0f, 4.0f, ColorUtil.rgba(24, 26, 34, 220));
                Render2D.drawRoundedOutline(context, sx, slotY, 21.0f, 21.0f, 4.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 16));

                InventorySlotRule rule = p.getRule(previewSlots[s]);
                if (rule != null && !rule.getDisplayStack().isEmpty()) {
                    context.drawItem(rule.getDisplayStack(), (int) sx + 2, (int) slotY + 2);
                }
            }

            // Card Footer Buttons
            float btnH = 20.0f;
            float btnY = cy + cardH - btnH - 8.0f;

            // 1. "Сортировка" Button
            float sortBtnW = 78.0f;
            float sortBtnX = cx + 10.0f;
            boolean hoverSort = mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int sBg = hoverSort ? Theme.getAccentHover() : ColorUtil.rgba(255, 255, 255, 22);
            Render2D.drawRoundedRect(context, sortBtnX, btnY, sortBtnW, btnH, 4.0f, ColorUtil.withAlphaFloat(sBg, alpha));
            ClientFont.drawCentered(context, "Сортировка", sortBtnX + sortBtnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);

            // 2. "Открыть" Button
            float viewBtnW = 66.0f;
            float viewBtnX = sortBtnX + sortBtnW + 6.0f;
            boolean hoverView = mouseX >= viewBtnX && mouseX <= viewBtnX + viewBtnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int vBg = hoverView ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 18);
            Render2D.drawRoundedRect(context, viewBtnX, btnY, viewBtnW, btnH, 4.0f, ColorUtil.withAlphaFloat(vBg, alpha));
            ClientFont.drawCentered(context, "Открыть", viewBtnX + viewBtnW / 2.0f, btnY + btnH / 2.0f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);

            // 3. Delete button
            float delBtnW = 20.0f;
            float delBtnX = cx + cardW - delBtnW - 8.0f;
            boolean hoverDel = mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int dBg = hoverDel ? ColorUtil.rgba(235, 65, 65, 220) : ColorUtil.rgba(255, 255, 255, 12);
            int dBorder = hoverDel ? ColorUtil.rgba(255, 90, 90, 240) : ColorUtil.rgba(255, 255, 255, 20);
            Render2D.drawRoundedRect(context, delBtnX, btnY, delBtnW, btnH, 4.0f, ColorUtil.withAlphaFloat(dBg, alpha));
            Render2D.drawRoundedOutline(context, delBtnX, btnY, delBtnW, btnH, 4.0f, 1.0f, ColorUtil.withAlphaFloat(dBorder, alpha));
            float dcX = delBtnX + delBtnW / 2.0f;
            float dcY = btnY + btnH / 2.0f;
            int crossCol = ColorUtil.withAlphaFloat(hoverDel ? 0xFFFFFFFF : 0xFFE08585, alpha);
            Render2D.drawLine(context, dcX - 3.5f, dcY - 3.5f, dcX + 3.5f, dcY + 3.5f, crossCol, 1.4f);
            Render2D.drawLine(context, dcX + 3.5f, dcY - 3.5f, dcX - 3.5f, dcY + 3.5f, crossCol, 1.4f);
        }

        int rowsCount = (presets.size() + 1) / 2;
        float totalH = 38.0f + rowsCount * (cardH + rowGap);
        maxScroll = Math.max(0.0f, totalH - h);
    }

    // =========================================================================
    // VIEW 2: FULL DETAILED INVENTORY VIEW
    // =========================================================================

    private static void renderDetailView(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        if (detailedPreset == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        InventoryLayoutManager lm = InventoryLayoutManager.getInstance();
        boolean isActive = (detailedPreset == lm.getActivePreset());

        float curY = y + 4.0f - scrollOffset;

        // Navigation Top Bar
        float backBtnW = 68.0f;
        float backBtnH = 22.0f;
        boolean hoverBack = mouseX >= x + 4.0f && mouseX <= x + 4.0f + backBtnW && mouseY >= curY && mouseY <= curY + backBtnH;
        int backBg = hoverBack ? ColorUtil.rgba(40, 46, 62, 240) : ColorUtil.rgba(24, 28, 38, 220);
        int backBorder = hoverBack ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 22);
        Render2D.drawRoundedRect(context, x + 4.0f, curY, backBtnW, backBtnH, 5.0f, ColorUtil.withAlphaFloat(backBg, alpha));
        Render2D.drawRoundedOutline(context, x + 4.0f, curY, backBtnW, backBtnH, 5.0f, 1.0f, ColorUtil.withAlphaFloat(backBorder, alpha));
        float arrowX = x + 13.0f;
        float arrowY = curY + backBtnH / 2.0f;
        int arrCol = ColorUtil.withAlphaFloat(hoverBack ? Theme.getAccent() : 0xFFCBD5E1, alpha);
        Render2D.drawLine(context, arrowX + 4.0f, arrowY - 3.5f, arrowX, arrowY, arrCol, 1.5f);
        Render2D.drawLine(context, arrowX, arrowY, arrowX + 4.0f, arrowY + 3.5f, arrCol, 1.5f);
        Render2D.drawLine(context, arrowX, arrowY, arrowX + 8.0f, arrowY, arrCol, 1.5f);
        ClientFont.draw(context, "Назад", x + 27.0f, curY + (backBtnH - ClientFont.FONT_HEIGHT) / 2.0f + 0.5f, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(hoverBack ? 0xFFFFFFFF : 0xFFE2E8F0, alpha));

        // Title: vertically centered with the 22px bar
        float titleX = x + 4.0f + backBtnW + 12.0f;
        String dName = detailedPreset.getName();
        if (dName.startsWith("КФГ ")) dName = dName.substring(4);
        if (dName.startsWith("Профиль ")) dName = dName.substring(8);
        ClientFont.draw(context, dName, titleX, curY + (backBtnH - ClientFont.FONT_HEIGHT) / 2.0f + 0.5f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        // Right side action buttons
        float actionBtnH = 22.0f;

        // 1. "Сортировка" Button
        float sortBtnW = 84.0f;
        float sortBtnX = x + w - sortBtnW - 4.0f;
        boolean hoverSort = mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= curY && mouseY <= curY + actionBtnH;
        int sBg = hoverSort ? Theme.getAccentHover() : Theme.getAccent();
        Render2D.drawRoundedRect(context, sortBtnX, curY, sortBtnW, actionBtnH, 5.0f, ColorUtil.withAlphaFloat(sBg, alpha));
        ClientFont.drawCentered(context, "Сортировка", sortBtnX + sortBtnW / 2.0f, curY + actionBtnH / 2.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        // 2. "Снимок" Button
        float snapBtnW = 64.0f;
        float snapBtnX = sortBtnX - snapBtnW - 6.0f;
        boolean hoverSnap = mouseX >= snapBtnX && mouseX <= snapBtnX + snapBtnW && mouseY >= curY && mouseY <= curY + actionBtnH;
        int snBg = hoverSnap ? ColorUtil.rgba(255, 255, 255, 45) : ColorUtil.rgba(255, 255, 255, 20);
        Render2D.drawRoundedRect(context, snapBtnX, curY, snapBtnW, actionBtnH, 5.0f, ColorUtil.withAlphaFloat(snBg, alpha));
        ClientFont.drawCentered(context, "Снимок", snapBtnX + snapBtnW / 2.0f, curY + actionBtnH / 2.0f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);

        // 3. "Активен" / "Выбрать" Button
        float actBtnW = 66.0f;
        float actBtnX = snapBtnX - actBtnW - 6.0f;
        boolean hoverAct = mouseX >= actBtnX && mouseX <= actBtnX + actBtnW && mouseY >= curY && mouseY <= curY + actionBtnH;
        int aBg = isActive ? ColorUtil.rgba(46, 204, 113, 220) : (hoverAct ? Theme.getAccent() : ColorUtil.rgba(255, 255, 255, 18));
        Render2D.drawRoundedRect(context, actBtnX, curY, actBtnW, actionBtnH, 5.0f, ColorUtil.withAlphaFloat(aBg, alpha));
        ClientFont.drawCentered(context, isActive ? "Активен" : "Выбрать", actBtnX + actBtnW / 2.0f, curY + actionBtnH / 2.0f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);

        curY += 32.0f;

        // Inventory Container Card
        float containerW = w - 8.0f;
        float containerH = 176.0f;
        float containerX = x + 4.0f;
        float containerY = curY;

        Render2D.drawRoundedRect(context, containerX, containerY, containerW, containerH, Theme.CARD_RADIUS, ColorUtil.withAlphaFloat(Theme.getCardBg(), alpha));
        Render2D.drawRoundedOutline(context, containerX, containerY, containerW, containerH, Theme.CARD_RADIUS, 1.0f, ColorUtil.withAlphaFloat(Theme.CARD_BORDER, alpha));

        // Uncompressed Inventory Layout inside container
        float slotSize = 21.0f;
        float slotStep = 23.0f;

        float gridStartX = containerX + 16.0f;
        float gridStartY = containerY + 14.0f;

        // 1. Armor column on left (Helmet 39, Chest 38, Legs 37, Boots 36)
        int[] armorSlots = {39, 38, 37, 36};
        for (int i = 0; i < 4; i++) {
            float sx = gridStartX;
            float sy = gridStartY + i * slotStep;
            renderSlot(context, detailedPreset, armorSlots[i], sx, sy, slotSize, mouseX, mouseY, mc);
        }

        // 2. Offhand slot (Slot 40)
        float offhandX = gridStartX + 28.0f;
        float offhandY = gridStartY + 3 * slotStep;
        renderSlot(context, detailedPreset, 40, offhandX, offhandY, slotSize, mouseX, mouseY, mc);

        // Divider
        float divX = offhandX + slotSize + 8.0f;
        Render2D.drawRect(context, divX, gridStartY, 1.0f, 4 * slotStep - 2.0f, ColorUtil.rgba(255, 255, 255, 14));

        // 3. Main Inventory (3 rows x 9 columns, slots 9 to 35)
        float mainX = divX + 9.0f;
        float mainY = gridStartY;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotId = 9 + row * 9 + col;
                float sx = mainX + col * slotStep;
                float sy = mainY + row * slotStep;
                renderSlot(context, detailedPreset, slotId, sx, sy, slotSize, mouseX, mouseY, mc);
            }
        }

        // Horizontal divider before hotbar
        float hotbarDivY = mainY + 3 * slotStep + 3.0f;
        Render2D.drawRect(context, mainX, hotbarDivY, 9 * slotStep - 2.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 16));

        // 4. Hotbar (1 row x 9 columns, slots 0 to 8)
        float hotbarY = hotbarDivY + 5.0f;
        for (int col = 0; col < 9; col++) {
            float sx = mainX + col * slotStep;
            renderSlot(context, detailedPreset, col, sx, hotbarY, slotSize, mouseX, mouseY, mc);
        }

        maxScroll = Math.max(0.0f, (containerH + 40.0f) - h);
    }

    private static void renderSlot(DrawContext context, InventoryPreset preset, int slotIndex,
                                   float sx, float sy, float size, float mouseX, float mouseY, MinecraftClient mc) {
        boolean isHovered = mouseX >= sx && mouseX <= sx + size && mouseY >= sy && mouseY <= sy + size;

        InventorySlotRule rule = preset.getRule(slotIndex);
        boolean hasItemInInv = false;
        if (rule != null && mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && rule.matches(stack)) {
                    hasItemInInv = true;
                    break;
                }
            }
        }

        int slotBg = isHovered ? ColorUtil.rgba(36, 40, 52, 240) : ColorUtil.rgba(20, 22, 30, 210);
        int slotBorder = isHovered ? Theme.getAccent() : (hasItemInInv ? ColorUtil.rgba(46, 204, 113, 140) : ColorUtil.rgba(255, 255, 255, 20));

        Render2D.drawRoundedRect(context, sx, sy, size, size, 4.0f, slotBg);
        Render2D.drawRoundedOutline(context, sx, sy, size, size, 4.0f, isHovered ? 1.5f : 1.0f, slotBorder);

        if (rule != null && !rule.getDisplayStack().isEmpty()) {
            ItemStack stack = rule.getDisplayStack();
            // Dimmed if missing in player inventory
            if (!hasItemInInv) {
                context.drawItem(stack, (int) sx + 2, (int) sy + 2);
                Render2D.drawRoundedRect(context, sx + 1.0f, sy + 1.0f, size - 2.0f, size - 2.0f, 3.0f, ColorUtil.rgba(0, 0, 0, 110));
            } else {
                context.drawItem(stack, (int) sx + 2, (int) sy + 2);
                // Subtle green match dot in top-right corner
                Render2D.drawCircle(context, sx + size - 3.5f, sy + 3.5f, 2.0f, ColorUtil.rgba(46, 204, 113, 240));
            }
            context.drawStackOverlay(mc.textRenderer, stack, (int) sx + 2, (int) sy + 2);

            if (isHovered) {
                hoveredRule = rule;
                hoveredSlotId = slotIndex;
                hoveredSlotHasItem = hasItemInInv;
                tooltipX = mouseX + 10.0f;
                tooltipY = mouseY + 10.0f;
            }
        }
    }

    private static void renderTooltip(DrawContext context, InventorySlotRule rule, int slotId, boolean hasItem, float mouseX, float mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack stack = rule.getDisplayStack();

        String title = stack.getName().getString();
        if (rule.getCustomName() != null && !rule.getCustomName().isEmpty()) {
            title = rule.getCustomName();
        }

        String slotCategory = getSlotCategoryName(slotId);
        String statusText = hasItem ? "[+] В наличии в инвентаре" : "[-] Отсутствует у игрока";
        int statusCol = hasItem ? 0xFF2ECC71 : 0xFFE74C3C;

        float titleW = ClientFont.getWidth(title, ClientFont.SF_BOLD);
        float catW = ClientFont.getWidth(slotCategory, ClientFont.SF_REGULAR);
        float statW = ClientFont.getWidth(statusText, ClientFont.SF_REGULAR);

        float tipW = Math.max(titleW, Math.max(catW, statW)) + 18.0f;
        float tipH = 46.0f;

        float tx = mouseX + 12.0f;
        float ty = mouseY + 12.0f;

        // Keep inside screen bounds
        if (tx + tipW > mc.getWindow().getScaledWidth() - 4.0f) {
            tx = mouseX - tipW - 8.0f;
        }
        if (ty + tipH > mc.getWindow().getScaledHeight() - 4.0f) {
            ty = mouseY - tipH - 8.0f;
        }

        Render2D.drawShadow(context, tx, ty, tipW, tipH, 6.0f, 10.0f, ColorUtil.rgba(0, 0, 0, 160));
        Render2D.drawRoundedRect(context, tx, ty, tipW, tipH, 6.0f, ColorUtil.rgba(16, 18, 24, 245));
        Render2D.drawRoundedOutline(context, tx, ty, tipW, tipH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 30));

        ClientFont.draw(context, title, tx + 8.0f, ty + 7.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);
        ClientFont.draw(context, slotCategory, tx + 8.0f, ty + 20.0f, ClientFont.SF_REGULAR, 0xFFA0A8B8);
        ClientFont.draw(context, statusText, tx + 8.0f, ty + 32.0f, ClientFont.SF_REGULAR, statusCol);
    }

    private static String getSlotCategoryName(int slotId) {
        if (slotId == 39) return "Броня · Шлем";
        if (slotId == 38) return "Броня · Нагрудник";
        if (slotId == 37) return "Броня · Поножи";
        if (slotId == 36) return "Броня · Ботинки";
        if (slotId == 40) return "Вторая рука (Оффхэнд)";
        if (slotId >= 0 && slotId <= 8) return "Хотбар · Слот " + (slotId + 1);
        return "Основной инвентарь · Слот " + (slotId - 8);
    }

    // =========================================================================
    // MOUSE INPUT HANDLING
    // =========================================================================

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        InventoryLayoutManager lm = InventoryLayoutManager.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (detailedPreset != null) {
            float curY = y + 4.0f - scrollOffset;

            // 1. "Назад" Button
            float backBtnW = 64.0f;
            float backBtnH = 22.0f;
            if (mouseX >= x + 4.0f && mouseX <= x + 4.0f + backBtnW && mouseY >= curY && mouseY <= curY + backBtnH) {
                detailedPreset = null;
                GuiSoundManager.playSelect();
                return true;
            }

            // 2. "Сортировка" Button
            float sortBtnW = 84.0f;
            float sortBtnX = x + w - sortBtnW - 4.0f;
            if (mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= curY && mouseY <= curY + 22.0f) {
                lm.setActivePreset(detailedPreset.getName());
                InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                if (sorter != null) sorter.startSort();
                GuiSoundManager.playSelect();
                return true;
            }

            // 3. "Снимок" Button
            float snapBtnW = 64.0f;
            float snapBtnX = sortBtnX - snapBtnW - 6.0f;
            if (mouseX >= snapBtnX && mouseX <= snapBtnX + snapBtnW && mouseY >= curY && mouseY <= curY + 22.0f) {
                if (mc.player != null) {
                    lm.saveCurrentAsPreset(detailedPreset.getName(), mc.player.getInventory());
                    GuiSoundManager.playSelect();
                }
                return true;
            }

            // 4. "Активен" / "Выбрать" Button
            float actBtnW = 66.0f;
            float actBtnX = snapBtnX - actBtnW - 6.0f;
            if (mouseX >= actBtnX && mouseX <= actBtnX + actBtnW && mouseY >= curY && mouseY <= curY + 22.0f) {
                lm.setActivePreset(detailedPreset.getName());
                InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                if (sorter != null) sorter.updatePresetModes();
                GuiSoundManager.playSelect();
                return true;
            }

            return false;
        }

        // CARDS VIEW CLICKS
        float curY = y + 4.0f - scrollOffset;

        // 1. "+ Создать профиль" Button
        float newBtnW = 124.0f;
        float newBtnH = 22.0f;
        float newBtnX = x + w - newBtnW - 4.0f;
        float newBtnY = curY + 2.0f;
        if (mouseX >= newBtnX && mouseX <= newBtnX + newBtnW && mouseY >= newBtnY && mouseY <= newBtnY + newBtnH) {
            int nextNum = lm.getPresets().size() + 1;
            String newName = String.valueOf(nextNum);
            InventoryPreset created = lm.createNewPreset(newName);
            if (mc.player != null && created != null) {
                lm.saveCurrentAsPreset(newName, mc.player.getInventory());
            }
            detailedPreset = created;
            InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
            if (sorter != null) sorter.updatePresetModes();
            GuiSoundManager.playSelect();
            return true;
        }

        curY += 34.0f;
        List<InventoryPreset> presets = lm.getPresets();
        float colGap = 8.0f;
        float rowGap = 8.0f;
        float cardW = (w - colGap) / 2.0f;
        float cardH = 102.0f;

        for (int i = 0; i < presets.size(); i++) {
            InventoryPreset p = presets.get(i);
            boolean isLeft = (i % 2 == 0);
            float cx = isLeft ? x : (x + cardW + colGap);
            float cy = curY + (i / 2) * (cardH + rowGap);

            if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                float btnH = 20.0f;
                float btnY = cy + cardH - btnH - 8.0f;

                // 1. "Сортировка" button on card
                float sortBtnW = 78.0f;
                float sortBtnX = cx + 10.0f;
                if (mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    lm.setActivePreset(p.getName());
                    InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                    if (sorter != null) sorter.startSort();
                    GuiSoundManager.playSelect();
                    return true;
                }

                // 2. "Открыть" button on card
                float viewBtnW = 66.0f;
                float viewBtnX = sortBtnX + sortBtnW + 6.0f;
                if (mouseX >= viewBtnX && mouseX <= viewBtnX + viewBtnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    detailedPreset = p;
                    GuiSoundManager.playSelect();
                    return true;
                }

                // 3. Select Pill
                float pillW = 60.0f;
                float pillH = 16.0f;
                float px = cx + cardW - pillW - 8.0f;
                float py = cy + 7.0f;
                if (mouseX >= px && mouseX <= px + pillW && mouseY >= py && mouseY <= py + pillH) {
                    lm.setActivePreset(p.getName());
                    InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                    if (sorter != null) sorter.updatePresetModes();
                    GuiSoundManager.playSelect();
                    return true;
                }

                // 4. Delete button
                float delBtnW = 20.0f;
                float delBtnX = cx + cardW - delBtnW - 8.0f;
                if (mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    lm.setActivePreset(p.getName());
                    lm.deleteCurrentPreset();
                    InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                    if (sorter != null) sorter.updatePresetModes();
                    GuiSoundManager.playSelect();
                    return true;
                }

                // Click on card body -> open detailed view
                detailedPreset = p;
                GuiSoundManager.playSelect();
                return true;
            }
        }

        return false;
    }

    public static void mouseScrolled(double amount) {
        targetScroll = Math.clamp(targetScroll - (float) amount * 24.0f, 0.0f, maxScroll);
    }

    public static void mouseReleased() {
        // cleanup drag if any
    }
}
