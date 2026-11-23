package egg.client.ui.section;

import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.inventory.InventorySlotRule;
import egg.client.module.ModuleManager;
import egg.client.module.player.InventorySorter;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.FrostedSurface;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.List;

public class InventoryConfigPanel {
    private static final int[] PREVIEW_SLOTS = {0, 1, 2, 3, 39, 40};
    private static final float SLOT_SIZE = 18.0f;
    private static final float SLOT_STEP = 20.0f;
    private static final float CARD_WIDTH = 128.0f;
    private static final float CARD_HEIGHT = 78.0f;
    private static final float CARD_GAP = 8.0f;
    private static final float GRID_TOP = 28.0f;
    private static final float BUTTON_HEIGHT = 16.0f;
    private static final float BUTTON_TEXT_SCALE = 0.82f;
    private static final int BUTTON_FILL = 0x1AFFFFFF;
    private static final int BUTTON_HOVER = 0x32FFFFFF;

    private static InventoryPreset detailedPreset;
    private static float scrollOffset;
    private static float targetScroll;
    private static float maxScroll;

    private record Bounds(float x, float y, float w, float h) {
        float centerX() { return x + w / 2.0f; }
        float centerY() { return y + h / 2.0f; }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        void clip(DrawContext context) {
            Render2D.enableScissor(context, x, y, x + w, y + h);
        }
    }

    private record Card(Bounds body, Bounds status, Bounds sort, Bounds open, Bounds delete) {
        Card(Bounds body) {
            this(body, centered(body.centerX(), body.y + 16.0f, 54.0f),
                    new Bounds(body.centerX() - 55.0f, body.y + 56.0f, 53.0f, BUTTON_HEIGHT),
                    new Bounds(body.centerX() + 1.0f, body.y + 56.0f, 38.0f, BUTTON_HEIGHT),
                    new Bounds(body.centerX() + 42.0f, body.y + 56.0f, 14.0f, BUTTON_HEIGHT));
        }
    }

    private record Editor(Bounds body, Bounds back, Bounds status, Bounds snapshot, Bounds sort, Bounds[] slots) {
        Editor(Bounds area) {
            this(new Bounds(area.centerX() - 124.0f, area.y + 2.0f, 248.0f, 140.0f), area.centerX(), area.y + 2.0f);
        }

        private Editor(Bounds body, float centerX, float top) {
            this(body, new Bounds(body.x + 8.0f, top + 5.0f, 44.0f, BUTTON_HEIGHT),
                    new Bounds(centerX - 82.0f, top + 116.0f, 49.0f, BUTTON_HEIGHT),
                    new Bounds(centerX - 29.0f, top + 116.0f, 48.0f, BUTTON_HEIGHT),
                    new Bounds(centerX + 23.0f, top + 116.0f, 59.0f, BUTTON_HEIGHT),
                    editorSlots(body.x + 12.0f, top + 26.0f));
        }
    }

    public static void setDetailedPreset(InventoryPreset preset) {
        detailedPreset = preset;
    }

    public static InventoryPreset getDetailedPreset() {
        return detailedPreset;
    }

    public static void render(DrawContext context, float x, float y, float w, float h,
                              float mouseX, float mouseY, float alpha, boolean blurred) {
        Bounds area = new Bounds(x, y, w, h);
        if (!area.contains(mouseX, mouseY)) {
            mouseX = Float.NaN;
            mouseY = Float.NaN;
        }
        InventoryLayoutManager manager = InventoryLayoutManager.getInstance();

        if (detailedPreset == null) {
            renderCards(context, area, manager, mouseX, mouseY, alpha, blurred);
        } else {
            renderEditor(context, area, manager, mouseX, mouseY, alpha, blurred);
        }
    }

    private static void renderCards(DrawContext context, Bounds area, InventoryLayoutManager manager,
                                    float mouseX, float mouseY, float alpha, boolean blurred) {
        drawButton(context, createButton(area), "+ Создать профиль", mouseX, mouseY, alpha,
                Theme.TEXT_TITLE, BUTTON_FILL, BUTTON_HOVER);

        List<InventoryPreset> presets = manager.getPresets();
        Bounds grid = cardGrid(area);
        int columns = columns(grid);
        int rows = (presets.size() + columns - 1) / columns;
        maxScroll = Math.max(0.0f, rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP - grid.h);
        targetScroll = Math.min(targetScroll, maxScroll);
        scrollOffset += (targetScroll - scrollOffset) * 0.25f;
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (presets.isEmpty()) {
            drawLabel(context, "Нет сохранённых профилей", grid.centerX(), grid.y + 22.0f, 1.0f, Theme.TEXT_SUB, alpha);
            return;
        }
        if (!grid.contains(mouseX, mouseY)) {
            mouseX = Float.NaN;
            mouseY = Float.NaN;
        }

        InventoryPreset active = manager.getActivePreset();
        grid.clip(context);
        try {
            for (int i = 0; i < presets.size(); i++) {
                Bounds body = cardBounds(grid, i, presets.size(), columns);
                if (body.y + body.h <= grid.y || body.y >= grid.y + grid.h) continue;
                InventoryPreset preset = presets.get(i);
                renderCard(context, new Card(body), preset, preset == active, mouseX, mouseY, alpha, blurred);
            }
        } finally {
            Render2D.disableScissor(context);
        }
    }

    private static void renderCard(DrawContext context, Card card, InventoryPreset preset, boolean active,
                                   float mouseX, float mouseY, float alpha, boolean blurred) {
        Bounds body = card.body;
        boolean hovered = body.contains(mouseX, mouseY);
        drawSurface(context, body, alpha, blurred, hovered);
        drawName(context, preset, body.centerX(), body.y + 10.0f, body.w - 16.0f, alpha);
        drawStatus(context, card.status, active, mouseX, mouseY, alpha);

        float startX = body.centerX() - (PREVIEW_SLOTS.length * SLOT_STEP - 2.0f) / 2.0f;
        for (int i = 0; i < PREVIEW_SLOTS.length; i++) {
            Bounds slot = slotAt(startX + i * SLOT_STEP, body.y + 33.0f);
            fill(context, slot, 4.0f, 0x570A0B0E, alpha);
            drawStack(context, slot, preset.getRule(PREVIEW_SLOTS[i]).getDisplayStack());
        }

        drawButton(context, card.sort, "Сортировка", mouseX, mouseY, alpha, Theme.TEXT_SUB, 0, BUTTON_HOVER);
        drawButton(context, card.open, "Открыть", mouseX, mouseY, alpha, Theme.TEXT_TITLE, BUTTON_FILL, BUTTON_HOVER);
        boolean deleteHover = card.delete.contains(mouseX, mouseY);
        if (deleteHover) fill(context, card.delete, 4.0f, 0x38E87878, alpha);
        int cross = ColorUtil.withAlphaFloat(deleteHover ? 0xFFF4A2A2 : Theme.TEXT_MUTED, alpha);
        IconRenderer.drawCrossIcon(context, card.delete.centerX(), card.delete.centerY(), 8.0f, cross);
    }

    private static void renderEditor(DrawContext context, Bounds area, InventoryLayoutManager manager,
                                     float mouseX, float mouseY, float alpha, boolean blurred) {
        Editor editor = new Editor(area);
        Bounds body = editor.body;
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory inventory = client.player == null ? null : client.player.getInventory();
        int hoveredSlot = -1;
        boolean hoveredAvailable = false;

        area.clip(context);
        try {
            drawSurface(context, body, alpha, blurred, false);
            drawButton(context, editor.back, "Назад", mouseX, mouseY, alpha, Theme.TEXT_SUB, 0, BUTTON_HOVER);
            drawName(context, detailedPreset, body.centerX(), body.y + 13.0f, body.w - 116.0f, alpha);

            for (int id = 0; id < editor.slots.length; id++) {
                Bounds slot = editor.slots[id];
                InventorySlotRule rule = detailedPreset.getRule(id);
                ItemStack stack = rule.getDisplayStack();
                boolean hovered = slot.contains(mouseX, mouseY);
                boolean available = hasItem(inventory, rule);
                int fill = hovered ? 0x38FFFFFF : available ? 0x3E50846B : 0x650A0B0E;
                fill(context, slot, 4.0f, fill, alpha);
                if (stack.isEmpty()) continue;
                drawStack(context, slot, stack);
                if (!available) fill(context, slot, 4.0f, 0x50000000, alpha);
                context.drawStackOverlay(client.textRenderer, stack, itemX(slot), itemY(slot));

                if (hovered) {
                    hoveredSlot = id;
                    hoveredAvailable = available;
                }
            }

            drawStatus(context, editor.status, detailedPreset == manager.getActivePreset(), mouseX, mouseY, alpha);
            drawButton(context, editor.snapshot, "Снимок", mouseX, mouseY, alpha, Theme.TEXT_SUB, 0, BUTTON_HOVER);
            drawButton(context, editor.sort, "Сортировка", mouseX, mouseY, alpha, Theme.TEXT_TITLE, BUTTON_FILL, BUTTON_HOVER);
        } finally {
            Render2D.disableScissor(context);
        }

        if (hoveredSlot >= 0) {
            renderTooltip(context, detailedPreset.getRule(hoveredSlot), hoveredSlot, hoveredAvailable, mouseX, mouseY, alpha);
        }
    }

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        Bounds area = new Bounds(x, y, w, h);
        if ((button != 0 && button != 1) || !area.contains(mouseX, mouseY)) return false;

        InventoryLayoutManager manager = InventoryLayoutManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (detailedPreset != null) {
            if (!clickEditor(new Editor(area), manager, client, mouseX, mouseY, button)) return false;
        } else {
            if (button != 0 || !clickCards(area, manager, client, mouseX, mouseY)) return false;
        }
        GuiSoundManager.playSelect();
        return true;
    }

    private static boolean clickCards(Bounds area, InventoryLayoutManager manager, MinecraftClient client,
                                      double mouseX, double mouseY) {
        if (createButton(area).contains(mouseX, mouseY)) {
            List<String> names = manager.getPresetNames();
            int number = names.size() + 1;
            while (names.contains(Integer.toString(number))) number++;
            InventoryPreset created = manager.createNewPreset(Integer.toString(number));
            if (client.player != null) manager.saveCurrentAsPreset(created.getName(), client.player.getInventory());
            setDetailedPreset(created);
            updateSorter(false);
            return true;
        }

        Bounds grid = cardGrid(area);
        if (!grid.contains(mouseX, mouseY)) return false;
        List<InventoryPreset> presets = manager.getPresets();
        int columns = columns(grid);
        for (int i = 0; i < presets.size(); i++) {
            Bounds body = cardBounds(grid, i, presets.size(), columns);
            if (!body.contains(mouseX, mouseY)) continue;

            Card card = new Card(body);
            InventoryPreset preset = presets.get(i);
            if (card.delete.contains(mouseX, mouseY)) {
                manager.setActivePreset(preset.getName());
                manager.deleteCurrentPreset();
                updateSorter(false);
            } else if (card.sort.contains(mouseX, mouseY)) {
                activate(manager, preset, true);
            } else if (card.status.contains(mouseX, mouseY)) {
                if (preset == manager.getActivePreset()) return false;
                activate(manager, preset, false);
            } else {
                setDetailedPreset(preset);
            }
            return true;
        }
        return false;
    }

    private static boolean clickEditor(Editor editor, InventoryLayoutManager manager, MinecraftClient client,
                                       double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (editor.back.contains(mouseX, mouseY)) {
                setDetailedPreset(null);
                return true;
            }
            if (editor.sort.contains(mouseX, mouseY)) {
                activate(manager, detailedPreset, true);
                return true;
            }
            if (editor.snapshot.contains(mouseX, mouseY) && client.player != null) {
                manager.saveCurrentAsPreset(detailedPreset.getName(), client.player.getInventory());
                return true;
            }
            if (editor.status.contains(mouseX, mouseY) && detailedPreset != manager.getActivePreset()) {
                activate(manager, detailedPreset, false);
                return true;
            }
        }

        for (int id = 0; id < editor.slots.length; id++) {
            if (!editor.slots[id].contains(mouseX, mouseY)) continue;
            if (button == 1) {
                detailedPreset.clearSlot(id);
            } else {
                if (client.player == null) return false;
                ItemStack cursor = client.player.currentScreenHandler != null
                        ? client.player.currentScreenHandler.getCursorStack() : ItemStack.EMPTY;
                ItemStack stack = cursor.isEmpty() ? client.player.getMainHandStack() : cursor;
                if (stack.isEmpty()) return false;
                detailedPreset.setRule(id, InventorySlotRule.fromStack(id, stack));
            }
            manager.save();
            return true;
        }
        return false;
    }

    private static void activate(InventoryLayoutManager manager, InventoryPreset preset, boolean sort) {
        manager.setActivePreset(preset.getName());
        updateSorter(sort);
    }

    private static void updateSorter(boolean sort) {
        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        if (sorter == null) return;
        sorter.updatePresetModes();
        if (sort) sorter.startSort();
    }

    private static Bounds createButton(Bounds area) {
        return centered(area.centerX(), area.y + 2.0f, 94.0f);
    }

    private static Bounds cardGrid(Bounds area) {
        return new Bounds(area.x, area.y + GRID_TOP, area.w, area.h - GRID_TOP);
    }

    private static int columns(Bounds grid) {
        return Math.clamp((int) ((grid.w + CARD_GAP) / (CARD_WIDTH + CARD_GAP)), 1, 3);
    }

    private static Bounds cardBounds(Bounds grid, int index, int count, int columns) {
        int row = index / columns;
        int cardsInRow = Math.min(columns, count - row * columns);
        float rowWidth = cardsInRow * (CARD_WIDTH + CARD_GAP) - CARD_GAP;
        return new Bounds(grid.centerX() - rowWidth / 2.0f + (index % columns) * (CARD_WIDTH + CARD_GAP),
                grid.y + row * (CARD_HEIGHT + CARD_GAP) - scrollOffset, CARD_WIDTH, CARD_HEIGHT);
    }

    private static Bounds[] editorSlots(float x, float y) {
        Bounds[] slots = new Bounds[41];
        float mainX = x + 46.0f;
        for (int col = 0; col < 9; col++) {
            float sx = mainX + col * SLOT_STEP;
            for (int row = 0; row < 3; row++) {
                slots[9 + row * 9 + col] = slotAt(sx, y + row * SLOT_STEP);
            }
            slots[col] = slotAt(sx, y + 64.0f);
        }
        for (int row = 0; row < 4; row++) {
            slots[39 - row] = slotAt(x, y + row * SLOT_STEP);
        }
        slots[40] = slotAt(x + SLOT_STEP, y + 3 * SLOT_STEP);
        return slots;
    }

    private static Bounds slotAt(float x, float y) {
        return new Bounds(x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private static Bounds centered(float centerX, float top, float width) {
        return new Bounds(centerX - width / 2.0f, top, width, BUTTON_HEIGHT);
    }

    private static int itemX(Bounds slot) { return Math.round(slot.centerX() - 8.0f); }
    private static int itemY(Bounds slot) { return Math.round(slot.centerY() - 8.0f); }

    private static void drawStack(DrawContext context, Bounds slot, ItemStack stack) {
        if (!stack.isEmpty()) context.drawItem(stack, itemX(slot), itemY(slot));
    }

    private static boolean hasItem(PlayerInventory inventory, InventorySlotRule rule) {
        if (inventory == null || rule.isEmpty()) return false;
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

    private static void fill(DrawContext context, Bounds bounds, float radius, int color, float alpha) {
        Render2D.drawRoundedRect(context, bounds.x, bounds.y, bounds.w, bounds.h, radius, ColorUtil.withAlphaFloat(color, alpha));
    }

    private static void drawButton(DrawContext context, Bounds bounds, String label, float mouseX, float mouseY,
                                   float alpha, int textColor, int background, int hoverBackground) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int color = hovered ? hoverBackground : background;
        if (color != 0) fill(context, bounds, 4.0f, color, alpha);
        drawLabel(context, label, bounds.centerX(), bounds.centerY(), BUTTON_TEXT_SCALE, hovered ? Theme.TEXT_TITLE : textColor, alpha);
    }

    private static void drawStatus(DrawContext context, Bounds bounds, boolean active, float mouseX, float mouseY, float alpha) {
        if (active) {
            int tint = ColorUtil.interpolate(Theme.getAccent(), Theme.TEXT_TITLE, 0.48f);
            drawLabel(context, "Активен", bounds.centerX(), bounds.centerY(), BUTTON_TEXT_SCALE, tint, alpha);
        } else {
            drawButton(context, bounds, "Выбрать", mouseX, mouseY, alpha, Theme.TEXT_SUB, 0, BUTTON_HOVER);
        }
    }

    private static void drawLabel(DrawContext context, String label, float x, float y, float scale, int color, float alpha) {
        ClientFont.drawCenteredScaled(context, label, x, y, scale, ClientFont.SF_MEDIUM, ColorUtil.withAlphaFloat(color, alpha));
    }

    private static void drawName(DrawContext context, InventoryPreset preset, float x, float y, float maxWidth, float alpha) {
        String name = preset.getName();
        if (name.startsWith("КФГ ")) name = name.substring(4);
        if (name.startsWith("Профиль ")) name = name.substring(8);

        if (ClientFont.getWidth(name, ClientFont.SF_BOLD) > maxWidth) {
            int end = name.length();
            do {
                end = name.offsetByCodePoints(end, -1);
            } while (end > 0 && ClientFont.getWidth(name.substring(0, end) + "…", ClientFont.SF_BOLD) > maxWidth);
            name = name.substring(0, end) + "…";
        }
        ClientFont.drawCentered(context, name, x, y, ClientFont.SF_BOLD, ColorUtil.withAlphaFloat(Theme.TEXT_TITLE, alpha));
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

        fill(context, new Bounds(tx, ty, width, height), 6.0f, 0xF516181E, alpha);
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
