package egg.client.ui.section;

import egg.client.config.ConfigManager;
import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.inventory.InventorySlotRule;
import egg.client.module.Category;
import egg.client.module.ModuleManager;
import egg.client.module.player.InventorySorter;
import egg.client.render.GuiOverlays;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.FrostedSurface;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class InventoryConfigPanel {
    private static final Identifier ICON_EYE = Identifier.of("egg", "textures/icons/gui_eye.png");
    private static final Identifier ICON_DOWNLOAD = Identifier.of("egg", "textures/icons/gui_download.png");
    private static final Identifier ICON_EDIT = Identifier.of("egg", "textures/icons/gui_edit.png");
    private static final Identifier ICON_TRASH = Identifier.of("egg", "textures/icons/gui_trash.png");

    private static final float COL_GAP = 8.0f;
    private static final float BOTTOM_H = 22.0f;
    private static final float BOTTOM_GAP = 8.0f;
    private static final float ROW_H = 29.0f;
    private static final float ROW_GAP = 4.0f;

    private static InventoryPreset previewPreset;
    private static InventoryPreset renamingPreset;
    private static StringBuilder renameBuffer = new StringBuilder();
    private static final StringBuilder nameInputBuffer = new StringBuilder();
    private static boolean nameInputFocused = false;
    private static long lastCaretBlink = 0L;

    private static float scrollOffset;
    private static float targetScroll;
    private static float maxScroll;

    private record Bounds(float x, float y, float w, float h) {
        float centerX() { return x + w * 0.5f; }
        float centerY() { return y + h * 0.5f; }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void clip(DrawContext context) {
            Render2D.enableScissor(context, x, y, x + w, y + h);
        }
    }

    private record Layout(
            Bounds leftCard,
            Bounds leftList,
            Bounds rightCard,
            float slotSize,
            float slotGap,
            Bounds[] slots,
            Bounds inputBox,
            Bounds saveBtn,
            Bounds toggleBtn
    ) {}

    private record RowBounds(Bounds row, Bounds loadBtn, Bounds editBtn, Bounds deleteBtn) {}

    private InventoryConfigPanel() {}

    public static void setDetailedPreset(InventoryPreset preset) {
        previewPreset = preset;
    }

    public static InventoryPreset getDetailedPreset() {
        return previewPreset;
    }

    public static boolean isTyping() {
        return nameInputFocused || renamingPreset != null;
    }

    public static void render(DrawContext context, float x, float y, float w, float h,
                              float mouseX, float mouseY, float alpha, boolean blurred) {
        Bounds area = new Bounds(x, y, w, h);
        if (!area.contains(mouseX, mouseY)) {
            mouseX = Float.NaN;
            mouseY = Float.NaN;
        }

        InventoryLayoutManager manager = InventoryLayoutManager.getInstance();
        List<InventoryPreset> presets = manager.getPresets();
        if (previewPreset == null || !presets.contains(previewPreset)) {
            previewPreset = manager.getActivePreset();
        }
        if (renamingPreset != null && !presets.contains(renamingPreset)) {
            renamingPreset = null;
        }

        Layout layout = computeLayout(area);
        renderLeftCard(context, layout, manager, presets, mouseX, mouseY, alpha, blurred);
        renderRightCard(context, layout, manager, mouseX, mouseY, alpha, blurred);
        renderBottomBar(context, layout, mouseX, mouseY, alpha);
    }

    private static void renderLeftCard(DrawContext context, Layout layout, InventoryLayoutManager manager,
                                       List<InventoryPreset> presets, float mouseX, float mouseY,
                                       float alpha, boolean blurred) {
        Bounds card = layout.leftCard;
        drawSurface(context, card, alpha, blurred, false);

        IconRenderer.drawCategoryIcon(context, Category.INVENTORY, card.x + 10.0f, card.y + 8.5f, 9.5f,
                ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        ClientFont.drawScaled(context, "Сохранения", card.x + 23.0f, card.y + 9.0f, 0.94f, ClientFont.SF_MEDIUM,
                ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

        String countLabel = formatSavesCount(presets.size());
        float cw = ClientFont.getWidth(countLabel, ClientFont.SF_REGULAR) * 0.78f;
        ClientFont.drawScaled(context, countLabel, card.x + card.w - 10.0f - cw, card.y + 10.0f, 0.78f,
                ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha));

        Bounds list = layout.leftList;
        float contentH = Math.max(0.0f, presets.size() * (ROW_H + ROW_GAP) - ROW_GAP);
        maxScroll = Math.max(0.0f, contentH - list.h);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);
        scrollOffset += (targetScroll - scrollOffset) * 0.25f;
        scrollOffset = Math.clamp(scrollOffset, 0.0f, maxScroll);

        if (presets.isEmpty()) {
            ClientFont.drawCenteredScaled(context, "Нет сохранений", list.centerX(), list.centerY(), 0.85f,
                    ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha));
            return;
        }

        float rowMouseX = list.contains(mouseX, mouseY) ? mouseX : Float.NaN;
        float rowMouseY = list.contains(mouseX, mouseY) ? mouseY : Float.NaN;

        InventoryPreset active = manager.getActivePreset();
        list.clip(context);
        try {
            for (int i = 0; i < presets.size(); i++) {
                float ry = list.y + i * (ROW_H + ROW_GAP) - scrollOffset;
                if (ry + ROW_H < list.y || ry > list.y + list.h) continue;
                RowBounds rb = rowBounds(list, ry);
                renderPresetRow(context, rb, presets.get(i), presets.get(i) == previewPreset,
                        presets.get(i) == active, rowMouseX, rowMouseY, alpha);
            }
        } finally {
            Render2D.disableScissor(context);
        }
    }

    private static void renderPresetRow(DrawContext context, RowBounds rb, InventoryPreset preset,
                                        boolean selected, boolean active, float mouseX, float mouseY, float alpha) {
        Bounds row = rb.row;
        boolean hovered = row.contains(mouseX, mouseY);
        int bg = selected
                ? ColorUtil.rgba(30, 33, 44, Math.round(185 * alpha))
                : hovered
                        ? ColorUtil.rgba(26, 29, 38, Math.round(165 * alpha))
                        : ColorUtil.rgba(19, 21, 29, Math.round(145 * alpha));
        int border = selected
                ? ColorUtil.withAlphaFloat(ColorUtil.interpolate(0xFFFFFFFF, Theme.getAccent(), 0.45f), 0.34f * alpha)
                : hovered
                        ? ColorUtil.rgba(255, 255, 255, Math.round(32 * alpha))
                        : ColorUtil.rgba(255, 255, 255, Math.round(16 * alpha));

        Render2D.drawRoundedRect(context, row.x, row.y, row.w, row.h, 6.0f, bg);
        Render2D.drawRoundedOutline(context, row.x, row.y, row.w, row.h, 6.0f, 0.75f, border);

        int iconCol = active
                ? ColorUtil.withAlphaFloat(ColorUtil.interpolate(Theme.getAccent(), Theme.TEXT_TITLE, 0.35f), alpha)
                : ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha);
        IconRenderer.drawCategoryIcon(context, Category.INVENTORY, row.x + 8.0f, row.centerY() - 4.5f, 9.0f, iconCol);

        float textX = row.x + 22.0f;
        float maxTextW = Math.max(24.0f, rb.loadBtn.x - textX - 6.0f);

        if (renamingPreset == preset) {
            String editStr = renameBuffer.toString();
            String disp = trimText(editStr, ClientFont.SF_MEDIUM, 0.86f, maxTextW - 4.0f);
            ClientFont.drawScaled(context, disp, textX, row.y + 4.5f, 0.86f, ClientFont.SF_MEDIUM,
                    ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
            if (((System.currentTimeMillis() - lastCaretBlink) / 500L) % 2L == 0L) {
                float cw = ClientFont.getWidth(disp, ClientFont.SF_MEDIUM) * 0.86f;
                Render2D.drawRect(context, textX + cw + 0.8f, row.y + 5.0f, 0.8f, 9.0f,
                        ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
            }
        } else {
            String displayName = trimText(cleanPresetName(preset.getName()), ClientFont.SF_MEDIUM, 0.86f, maxTextW);
            ClientFont.drawScaled(context, displayName, textX, row.y + 4.5f, 0.86f, ClientFont.SF_MEDIUM,
                    ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        }

        int count = countItems(preset);
        String itemsLabel = formatItemCount(count);
        ClientFont.drawScaled(context, trimText(itemsLabel, ClientFont.SF_REGULAR, 0.74f, maxTextW),
                textX, row.y + 15.5f, 0.74f, ClientFont.SF_REGULAR,
                ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha));

        boolean loadHov = rb.loadBtn.contains(mouseX, mouseY);
        int loadBg = loadHov
                ? ColorUtil.rgba(255, 255, 255, Math.round(30 * alpha))
                : ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha));
        int loadBorder = loadHov
                ? ColorUtil.rgba(255, 255, 255, Math.round(48 * alpha))
                : ColorUtil.rgba(255, 255, 255, Math.round(22 * alpha));
        Render2D.drawRoundedRect(context, rb.loadBtn.x, rb.loadBtn.y, rb.loadBtn.w, rb.loadBtn.h, 4.5f, loadBg);
        Render2D.drawRoundedOutline(context, rb.loadBtn.x, rb.loadBtn.y, rb.loadBtn.w, rb.loadBtn.h, 4.5f, 0.7f, loadBorder);
        int loadCol = ColorUtil.withAlphaFloat(loadHov ? 0xFFFFFFFF : Theme.TEXT_SUB, alpha);
        IconRenderer.drawTextureCentered(context, ICON_DOWNLOAD, rb.loadBtn.x + 8.5f, rb.loadBtn.centerY(), 8.0f, loadCol);
        ClientFont.drawScaled(context, "Загрузить", rb.loadBtn.x + 15.0f, rb.loadBtn.y + 4.5f, 0.76f,
                ClientFont.SF_MEDIUM, loadCol);

        boolean editHov = rb.editBtn.contains(mouseX, mouseY);
        if (editHov || renamingPreset == preset) {
            Render2D.drawRoundedRect(context, rb.editBtn.x, rb.editBtn.y, rb.editBtn.w, rb.editBtn.h, 4.5f,
                    ColorUtil.rgba(255, 255, 255, Math.round((renamingPreset == preset ? 32 : 22) * alpha)));
        }
        int editCol = ColorUtil.withAlphaFloat((editHov || renamingPreset == preset) ? Theme.TEXT_TITLE : Theme.TEXT_MUTED, alpha);
        IconRenderer.drawTextureCentered(context, ICON_EDIT, rb.editBtn.centerX(), rb.editBtn.centerY(), 8.5f, editCol);

        boolean delHov = rb.deleteBtn.contains(mouseX, mouseY);
        if (delHov) {
            Render2D.drawRoundedRect(context, rb.deleteBtn.x, rb.deleteBtn.y, rb.deleteBtn.w, rb.deleteBtn.h, 4.5f,
                    ColorUtil.rgba(235, 85, 95, Math.round(48 * alpha)));
        }
        int delCol = ColorUtil.withAlphaFloat(delHov ? 0xFFFF8890 : Theme.TEXT_MUTED, alpha);
        IconRenderer.drawTextureCentered(context, ICON_TRASH, rb.deleteBtn.centerX(), rb.deleteBtn.centerY(), 8.5f, delCol);
    }

    private static void renderRightCard(DrawContext context, Layout layout, InventoryLayoutManager manager,
                                        float mouseX, float mouseY, float alpha, boolean blurred) {
        Bounds card = layout.rightCard;
        drawSurface(context, card, alpha, blurred, false);

        IconRenderer.drawTextureCentered(context, ICON_EYE, card.x + 14.0f, card.y + 13.0f, 10.0f,
                ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        ClientFont.drawScaled(context, "Просмотр", card.x + 23.0f, card.y + 9.0f, 0.94f, ClientFont.SF_MEDIUM,
                ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));

        if (previewPreset != null) {
            float nameX = card.x + 23.0f + ClientFont.getWidth("Просмотр", ClientFont.SF_MEDIUM) * 0.94f + 5.0f;
            float maxW = Math.max(20.0f, card.x + card.w - 10.0f - nameX);
            String pName = trimText(cleanPresetName(previewPreset.getName()), ClientFont.SF_REGULAR, 0.86f, maxW);
            ClientFont.drawScaled(context, pName, nameX, card.y + 9.5f, 0.86f, ClientFont.SF_REGULAR,
                    ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInv = client.player == null ? null : client.player.getInventory();
        int hoveredSlot = -1;
        boolean hoveredAvailable = false;

        card.clip(context);
        try {
            for (int id = 0; id < layout.slots.length; id++) {
                Bounds slot = layout.slots[id];
                boolean hovered = slot.contains(mouseX, mouseY);
                InventorySlotRule rule = previewPreset == null ? null : previewPreset.getRule(id);
                ItemStack stack = rule == null ? ItemStack.EMPTY : rule.getDisplayStack();
                boolean occupied = !stack.isEmpty();

                int fill = hovered
                        ? ColorUtil.rgba(46, 52, 68, Math.round(215 * alpha))
                        : occupied
                                ? ColorUtil.rgba(25, 28, 38, Math.round(195 * alpha))
                                : ColorUtil.rgba(17, 19, 27, Math.round(155 * alpha));
                int outline = hovered
                        ? ColorUtil.withAlphaFloat(ColorUtil.interpolate(0xFFFFFFFF, Theme.getAccent(), 0.5f), 0.55f * alpha)
                        : occupied
                                ? ColorUtil.rgba(255, 255, 255, Math.round(24 * alpha))
                                : ColorUtil.rgba(255, 255, 255, Math.round(14 * alpha));

                Render2D.drawRoundedRect(context, slot.x, slot.y, slot.w, slot.h, 4.0f, fill);
                Render2D.drawRoundedOutline(context, slot.x, slot.y, slot.w, slot.h, 4.0f, 0.65f, outline);

                if (occupied) {
                    int ix = Math.round(slot.centerX() - 8.0f);
                    int iy = Math.round(slot.centerY() - 8.0f);
                    context.drawItem(stack, ix, iy);
                    context.drawStackOverlay(client.textRenderer, stack, ix, iy);
                    if (hovered) {
                        hoveredSlot = id;
                        hoveredAvailable = hasItem(playerInv, rule);
                    }
                }
            }
        } finally {
            Render2D.disableScissor(context);
        }

        if (hoveredSlot >= 0 && previewPreset != null) {
            int slotId = hoveredSlot;
            boolean available = hoveredAvailable;
            InventorySlotRule rule = previewPreset.getRule(slotId);
            GuiOverlays.defer(context, () -> renderTooltip(context, rule, slotId, available, mouseX, mouseY, alpha));
        }
    }

    private static void renderBottomBar(DrawContext context, Layout layout, float mouseX, float mouseY, float alpha) {
        Bounds input = layout.inputBox;
        boolean inputHov = input.contains(mouseX, mouseY);
        int inBg = nameInputFocused
                ? ColorUtil.rgba(24, 27, 36, Math.round(230 * alpha))
                : ColorUtil.rgba(16, 18, 25, Math.round(210 * alpha));
        int inBorder = nameInputFocused
                ? ColorUtil.withAlphaFloat(Theme.getAccent(), 0.72f * alpha)
                : inputHov
                        ? ColorUtil.rgba(255, 255, 255, Math.round(44 * alpha))
                        : ColorUtil.rgba(255, 255, 255, Math.round(22 * alpha));
        Render2D.drawRoundedRect(context, input.x, input.y, input.w, input.h, 6.0f, inBg);
        Render2D.drawRoundedOutline(context, input.x, input.y, input.w, input.h, 6.0f, 0.8f, inBorder);

        String raw = nameInputBuffer.toString();
        float maxInW = input.w - 14.0f;
        if (raw.isEmpty() && !nameInputFocused) {
            ClientFont.drawScaled(context, "Название инвентаря", input.x + 8.0f, input.y + 6.5f, 0.82f,
                    ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_MUTED, alpha));
        } else {
            String vis = trimText(raw, ClientFont.SF_REGULAR, 0.84f, maxInW);
            ClientFont.drawScaled(context, vis, input.x + 8.0f, input.y + 6.5f, 0.84f, ClientFont.SF_REGULAR,
                    ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
            if (nameInputFocused && ((System.currentTimeMillis() - lastCaretBlink) / 500L) % 2L == 0L) {
                float cw = ClientFont.getWidth(vis, ClientFont.SF_REGULAR) * 0.84f;
                Render2D.drawRect(context, input.x + 8.0f + cw + 1.0f, input.y + 5.5f, 0.9f, 11.0f,
                        ColorUtil.withAlphaFloat(Theme.getAccent(), alpha));
            }
        }

        Bounds save = layout.saveBtn;
        boolean saveHov = save.contains(mouseX, mouseY);
        int saveBg = saveHov
                ? ColorUtil.rgba(34, 38, 50, Math.round(225 * alpha))
                : ColorUtil.rgba(20, 22, 30, Math.round(210 * alpha));
        int saveBorder = saveHov
                ? ColorUtil.rgba(255, 255, 255, Math.round(52 * alpha))
                : ColorUtil.rgba(255, 255, 255, Math.round(24 * alpha));
        Render2D.drawRoundedRect(context, save.x, save.y, save.w, save.h, 6.0f, saveBg);
        Render2D.drawRoundedOutline(context, save.x, save.y, save.w, save.h, 6.0f, 0.8f, saveBorder);
        ClientFont.drawCenteredScaled(context, "+ Сохранить", save.centerX(), save.centerY(), 0.82f,
                ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(saveHov ? 0xFFFFFFFF : Theme.TEXT_SUB, alpha));

        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        boolean sorterEnabled = sorter != null && sorter.isEnabled();
        Bounds toggle = layout.toggleBtn;
        boolean togHov = toggle.contains(mouseX, mouseY);
        int togBg = sorterEnabled
                ? ColorUtil.rgba(togHov ? 34 : 22, togHov ? 36 : 24, togHov ? 46 : 32, Math.round(215 * alpha))
                : ColorUtil.rgba(26, 46, 36, Math.round((togHov ? 225 : 195) * alpha));
        int togBorder = sorterEnabled
                ? ColorUtil.rgba(255, 255, 255, Math.round((togHov ? 50 : 24) * alpha))
                : ColorUtil.rgba(52, 211, 153, Math.round((togHov ? 165 : 105) * alpha));
        Render2D.drawRoundedRect(context, toggle.x, toggle.y, toggle.w, toggle.h, 6.0f, togBg);
        Render2D.drawRoundedOutline(context, toggle.x, toggle.y, toggle.w, toggle.h, 6.0f, 0.8f, togBorder);

        String toggleLabel = sorterEnabled ? "Выключить" : "Включить";
        if (sorterEnabled) {
            IconRenderer.drawCrossIcon(context, toggle.x + 12.0f, toggle.centerY(), 7.0f,
                    ColorUtil.withAlphaFloat(togHov ? 0xFFFFFFFF : Theme.TEXT_MUTED, alpha));
            ClientFont.drawScaled(context, toggleLabel, toggle.x + 19.0f, toggle.y + 6.5f, 0.82f,
                    ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(togHov ? 0xFFFFFFFF : Theme.TEXT_SUB, alpha));
        } else {
            ClientFont.drawCenteredScaled(context, toggleLabel, toggle.centerX(), toggle.centerY(), 0.82f,
                    ClientFont.SF_MEDIUM, ColorUtil.rgba(110, 231, 183, Math.round(245 * alpha)));
        }
    }

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        Bounds area = new Bounds(x, y, w, h);
        if ((button != 0 && button != 1) || !area.contains(mouseX, mouseY)) {
            nameInputFocused = false;
            commitRenameIfActive();
            return false;
        }

        Layout layout = computeLayout(area);
        InventoryLayoutManager manager = InventoryLayoutManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();

        if (button == 0) {
            if (layout.inputBox.contains(mouseX, mouseY)) {
                commitRenameIfActive();
                nameInputFocused = true;
                lastCaretBlink = System.currentTimeMillis();
                GuiSoundManager.playSelect();
                return true;
            }
            nameInputFocused = false;

            if (layout.saveBtn.contains(mouseX, mouseY)) {
                commitRenameIfActive();
                saveCurrentWithName(manager, client);
                GuiSoundManager.playSelect();
                return true;
            }

            if (layout.toggleBtn.contains(mouseX, mouseY)) {
                commitRenameIfActive();
                InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
                if (sorter != null) {
                    sorter.toggle();
                    ConfigManager.save();
                }
                GuiSoundManager.playSelect();
                return true;
            }
        }

        if (layout.leftList.contains(mouseX, mouseY) && button == 0) {
            List<InventoryPreset> presets = manager.getPresets();
            for (int i = 0; i < presets.size(); i++) {
                float ry = layout.leftList.y + i * (ROW_H + ROW_GAP) - scrollOffset;
                if (ry + ROW_H < layout.leftList.y || ry > layout.leftList.y + layout.leftList.h) continue;
                RowBounds rb = rowBounds(layout.leftList, ry);
                if (!rb.row.contains(mouseX, mouseY)) continue;

                InventoryPreset preset = presets.get(i);
                if (rb.deleteBtn.contains(mouseX, mouseY)) {
                    if (renamingPreset == preset) renamingPreset = null;
                    manager.setActivePreset(preset.getName());
                    manager.deleteCurrentPreset();
                    previewPreset = manager.getActivePreset();
                    updateSorter(false);
                } else if (rb.editBtn.contains(mouseX, mouseY)) {
                    if (renamingPreset == preset) {
                        commitRenameIfActive();
                    } else {
                        commitRenameIfActive();
                        renamingPreset = preset;
                        renameBuffer = new StringBuilder(cleanPresetName(preset.getName()));
                        lastCaretBlink = System.currentTimeMillis();
                    }
                    previewPreset = preset;
                } else if (rb.loadBtn.contains(mouseX, mouseY)) {
                    commitRenameIfActive();
                    previewPreset = preset;
                    manager.setActivePreset(preset.getName());
                    updateSorter(true);
                } else {
                    commitRenameIfActive();
                    previewPreset = preset;
                }
                GuiSoundManager.playSelect();
                return true;
            }
        }

        if (layout.rightCard.contains(mouseX, mouseY) && previewPreset != null) {
            commitRenameIfActive();
            for (int id = 0; id < layout.slots.length; id++) {
                if (!layout.slots[id].contains(mouseX, mouseY)) continue;
                if (button == 1) {
                    previewPreset.clearSlot(id);
                } else {
                    if (client.player == null) return false;
                    ItemStack cursor = client.player.currentScreenHandler != null
                            ? client.player.currentScreenHandler.getCursorStack() : ItemStack.EMPTY;
                    ItemStack stack = cursor.isEmpty() ? client.player.getMainHandStack() : cursor;
                    if (stack.isEmpty()) return false;
                    previewPreset.setRule(id, InventorySlotRule.fromStack(id, stack));
                }
                manager.save();
                GuiSoundManager.playSelect();
                return true;
            }
        }

        commitRenameIfActive();
        return false;
    }

    public static boolean keyPressed(int key) {
        if (!nameInputFocused && renamingPreset == null) {
            return false;
        }
        lastCaretBlink = System.currentTimeMillis();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            nameInputFocused = false;
            renamingPreset = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (renamingPreset != null) {
                commitRenameIfActive();
            } else if (nameInputFocused) {
                saveCurrentWithName(InventoryLayoutManager.getInstance(), MinecraftClient.getInstance());
                nameInputFocused = false;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            StringBuilder target = renamingPreset != null ? renameBuffer : nameInputBuffer;
            if (!target.isEmpty()) {
                target.deleteCharAt(target.length() - 1);
            }
            return true;
        }
        return true;
    }

    public static boolean charTyped(String ch) {
        if ((!nameInputFocused && renamingPreset == null) || ch == null || ch.isEmpty()) {
            return false;
        }
        char c = ch.charAt(0);
        if (c < 32 || c == 127) {
            return true;
        }
        StringBuilder target = renamingPreset != null ? renameBuffer : nameInputBuffer;
        if (target.length() < 20) {
            target.append(ch);
            lastCaretBlink = System.currentTimeMillis();
        }
        return true;
    }

    private static void saveCurrentWithName(InventoryLayoutManager manager, MinecraftClient client) {
        String name = nameInputBuffer.toString().trim();
        if (name.isEmpty()) {
            List<String> existing = manager.getPresetNames();
            int n = existing.size() + 1;
            while (existing.contains(Integer.toString(n))) n++;
            name = Integer.toString(n);
        }
        if (client.player != null) {
            manager.saveCurrentAsPreset(name, client.player.getInventory());
        } else {
            manager.createNewPreset(name);
        }
        previewPreset = manager.getActivePreset();
        nameInputBuffer.setLength(0);
        updateSorter(false);
    }

    private static void commitRenameIfActive() {
        if (renamingPreset == null) return;
        String nextName = renameBuffer.toString().trim();
        if (!nextName.isEmpty()) {
            InventoryLayoutManager manager = InventoryLayoutManager.getInstance();
            boolean conflict = false;
            for (InventoryPreset p : manager.getPresets()) {
                if (p != renamingPreset && p.getName().equalsIgnoreCase(nextName)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                renamingPreset.setName(nextName);
                manager.save();
                updateSorter(false);
            }
        }
        renamingPreset = null;
    }

    private static void updateSorter(boolean sort) {
        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        if (sorter == null) return;
        sorter.updatePresetModes();
        if (sort) sorter.startSort();
    }

    private static Layout computeLayout(Bounds area) {
        float slotGap = 2.5f;
        float slotSize = Math.clamp((area.w * 0.515f - 18.0f - 8.0f * slotGap) / 9.0f, 16.0f, 20.0f);
        float gridW = 9.0f * slotSize + 8.0f * slotGap;
        float rightPadX = 9.0f;
        float rightW = gridW + rightPadX * 2.0f;
        float leftW = Math.max(150.0f, area.w - COL_GAP - rightW);

        float headerH = 24.0f;
        float armorGap = 5.0f;
        float mainGap = 7.0f;
        float botPad = 9.0f;
        float rightMinH = 6.0f + headerH + slotSize + armorGap + (3.0f * slotSize + 2.0f * slotGap) + mainGap + slotSize + botPad;
        float maxCardsH = Math.max(120.0f, area.h - BOTTOM_H - BOTTOM_GAP);
        float cardsH = Math.min(maxCardsH, Math.max(rightMinH + 14.0f, maxCardsH));

        float totalGroupH = cardsH + BOTTOM_GAP + BOTTOM_H;
        float topY = area.y + Math.max(0.0f, (area.h - totalGroupH) * 0.5f);

        Bounds leftCard = new Bounds(area.x, topY, leftW, cardsH);
        Bounds leftList = new Bounds(leftCard.x + 7.0f, leftCard.y + 26.0f, leftCard.w - 14.0f, leftCard.h - 33.0f);
        Bounds rightCard = new Bounds(leftCard.x + leftW + COL_GAP, topY, rightW, cardsH);

        float gridX = rightCard.x + rightPadX;
        float armorY = rightCard.y + 28.0f;
        float mainY = armorY + slotSize + armorGap;
        float hotbarY = mainY + 3.0f * slotSize + 2.0f * slotGap + mainGap;

        Bounds[] slots = new Bounds[41];
        float step = slotSize + slotGap;
        for (int col = 0; col < 9; col++) {
            float sx = gridX + col * step;
            for (int row = 0; row < 3; row++) {
                slots[9 + row * 9 + col] = new Bounds(sx, mainY + row * step, slotSize, slotSize);
            }
            slots[col] = new Bounds(sx, hotbarY, slotSize, slotSize);
        }
        slots[39] = new Bounds(gridX + 0 * step, armorY, slotSize, slotSize);
        slots[38] = new Bounds(gridX + 1 * step, armorY, slotSize, slotSize);
        slots[37] = new Bounds(gridX + 2 * step, armorY, slotSize, slotSize);
        slots[36] = new Bounds(gridX + 3 * step, armorY, slotSize, slotSize);
        slots[40] = new Bounds(gridX + 8 * step, armorY, slotSize, slotSize);

        float barY = topY + cardsH + BOTTOM_GAP;
        float inW = Math.min(148.0f, area.w * 0.36f);
        float saveW = 78.0f;
        float togW = 78.0f;
        float barGap = 6.0f;
        float totalBarW = inW + saveW + togW + barGap * 2.0f;
        float barX = area.centerX() - totalBarW * 0.5f;

        Bounds inputBox = new Bounds(barX, barY, inW, BOTTOM_H);
        Bounds saveBtn = new Bounds(barX + inW + barGap, barY, saveW, BOTTOM_H);
        Bounds toggleBtn = new Bounds(saveBtn.x + saveW + barGap, barY, togW, BOTTOM_H);

        return new Layout(leftCard, leftList, rightCard, slotSize, slotGap, slots, inputBox, saveBtn, toggleBtn);
    }

    private static RowBounds rowBounds(Bounds list, float ry) {
        float btnH = 18.0f;
        float by = ry + (ROW_H - btnH) * 0.5f;
        float iconBtnW = 18.0f;
        float gap = 3.0f;
        float delX = list.x + list.w - 6.0f - iconBtnW;
        float editX = delX - gap - iconBtnW;
        float loadW = 58.0f;
        float loadX = editX - gap - loadW;
        return new RowBounds(
                new Bounds(list.x, ry, list.w, ROW_H),
                new Bounds(loadX, by, loadW, btnH),
                new Bounds(editX, by, iconBtnW, btnH),
                new Bounds(delX, by, iconBtnW, btnH)
        );
    }

    private static int countItems(InventoryPreset preset) {
        int count = 0;
        for (int i = 0; i <= 40; i++) {
            InventorySlotRule r = preset.getRule(i);
            if (r != null && !r.isEmpty()) count++;
        }
        return count;
    }

    private static String formatItemCount(int count) {
        if (count == 0) return "Пусто";
        int m100 = count % 100;
        int m10 = count % 10;
        if (m100 >= 11 && m100 <= 14) return count + " предметов";
        if (m10 == 1) return count + " предмет";
        if (m10 >= 2 && m10 <= 4) return count + " предмета";
        return count + " предметов";
    }

    private static String formatSavesCount(int count) {
        int m100 = count % 100;
        int m10 = count % 10;
        if (m100 >= 11 && m100 <= 14) return count + " сохранений";
        if (m10 == 1) return count + " сохранение";
        if (m10 >= 2 && m10 <= 4) return count + " сохранения";
        return count + " сохранений";
    }

    private static String cleanPresetName(String name) {
        if (name == null) return "";
        if (name.startsWith("КФГ ")) return name.substring(4);
        if (name.startsWith("Профиль ")) return name.substring(8);
        return name;
    }

    private static String trimText(String text, net.minecraft.text.StyleSpriteSource.Font font, float scale, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (ClientFont.getWidth(text, font) * scale <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && ClientFont.getWidth(text.substring(0, end) + "…", font) * scale > maxWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, Math.max(0, end)) + "…";
    }

    private static boolean hasItem(PlayerInventory inventory, InventorySlotRule rule) {
        if (inventory == null || rule == null || rule.isEmpty()) return false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && rule.matches(stack)) return true;
        }
        return false;
    }

    private static void drawSurface(DrawContext context, Bounds bounds, float alpha, boolean blurred, boolean hovered) {
        FrostedSurface.draw(context, bounds.x, bounds.y, bounds.w, bounds.h, 8.0f,
                hovered ? Theme.getCardBgHover() : Theme.getCardBg(), alpha, hovered ? 0.68f : 0.61f, blurred);
    }

    private static void renderTooltip(DrawContext context, InventorySlotRule rule, int slotId, boolean available,
                                      float mouseX, float mouseY, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        String title = rule.getCustomName();
        if (title == null) title = rule.getDisplayStack().getName().getString();
        String category = getSlotCategoryName(slotId);
        String status = available ? "В наличии" : "Нет в инвентаре";
        float width = Math.max(ClientFont.getWidth(title, ClientFont.SF_BOLD),
                Math.max(ClientFont.getWidth(category, ClientFont.SF_REGULAR), ClientFont.getWidth(status, ClientFont.SF_REGULAR))) + 16.0f;
        float height = 42.0f;
        float tx = mouseX + 10.0f;
        float ty = mouseY + 10.0f;
        if (tx + width > client.getWindow().getScaledWidth() - 4.0f) tx = mouseX - width - 8.0f;
        if (ty + height > client.getWindow().getScaledHeight() - 4.0f) ty = mouseY - height - 8.0f;

        Render2D.drawRoundedRect(context, tx, ty, width, height, 6.0f, ColorUtil.withAlphaFloat(0xFF16181E, alpha));
        ClientFont.draw(context, title, tx + 8.0f, ty + 5.0f, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
        ClientFont.draw(context, category, tx + 8.0f, ty + 17.0f, ClientFont.SF_REGULAR, ColorUtil.withAlphaFloat(Theme.TEXT_SUB, alpha));
        ClientFont.draw(context, status, tx + 8.0f, ty + 29.0f, ClientFont.SF_REGULAR,
                ColorUtil.withAlphaFloat(available ? 0xFF8FCCA9 : 0xFFE1A09D, alpha));
    }

    private static String getSlotCategoryName(int slotId) {
        return switch (slotId) {
            case 39 -> "Броня · Шлем";
            case 38 -> "Броня · Нагрудник";
            case 37 -> "Броня · Поножи";
            case 36 -> "Броня · Ботинки";
            case 40 -> "Вторая рука";
            default -> slotId <= 8 ? "Хотбар · Слот " + (slotId + 1) : "Инвентарь · Слот " + (slotId - 8);
        };
    }

    public static void mouseScrolled(double amount) {
        targetScroll = Math.clamp(targetScroll - (float) amount * 24.0f, 0.0f, maxScroll);
    }

    public static void mouseReleased() {
    }
}
