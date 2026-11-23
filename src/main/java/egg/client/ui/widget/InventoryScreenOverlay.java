package egg.client.ui.widget;

import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.mixin.HandledScreenAccessor;
import egg.client.module.player.InventorySorter;
import egg.client.render.GuiOverlays;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.msdf.Fonts;
import egg.client.ui.theme.Theme;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public final class InventoryScreenOverlay {
    private static final float WIDTH = 172.0f;
    private static final float HEIGHT = 20.0f;
    private static final float STATUS_HEIGHT = 10.0f;
    private static final float GAP = 4.0f;

    private record Bounds(float x, float y, float width, float height) {
        float centerX() { return x + width / 2.0f; }
        float centerY() { return y + height / 2.0f; }

        boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record Layout(Bounds panel, Bounds previous, Bounds next, Bounds sort, Bounds save, boolean statusFits) {
        Layout(float x, float y, boolean statusFits) {
            this(new Bounds(x, y, WIDTH, HEIGHT),
                    new Bounds(x + 3, y + 2, 13, 16),
                    new Bounds(x + 60, y + 2, 13, 16),
                    new Bounds(x + 75, y + 2, 57, 16),
                    new Bounds(x + 133, y + 2, 36, 16),
                    statusFits);
        }
    }

    private InventoryScreenOverlay() {
    }

    private static Layout layout(InventoryScreen screen) {
        var inventory = (HandledScreenAccessor) screen;
        int top = inventory.getY();
        int bottom = top + inventory.getBackgroundHeight();
        float below = screen.height - bottom - GAP - 2;
        float above = top - GAP - 2;
        boolean placeBelow = above < HEIGHT;
        float space = placeBelow ? below : above;
        if (space < HEIGHT || screen.width < WIDTH + GAP * 2) return null;

        boolean statusFits = space >= HEIGHT + STATUS_HEIGHT;
        float x = Math.clamp(inventory.getX() + (inventory.getBackgroundWidth() - WIDTH) / 2.0f,
                GAP, screen.width - WIDTH - GAP);
        float y = placeBelow ? bottom + GAP : top - GAP - HEIGHT - (statusFits ? STATUS_HEIGHT : 0);
        return new Layout(x, y, statusFits);
    }

    public static void render(DrawContext context, InventoryScreen screen, float mouseX, float mouseY) {
        InventorySorter sorter = InventorySorter.getInstance();
        if (sorter == null || !sorter.isEnabled() || MinecraftClient.getInstance().player == null) return;
        Layout layout = layout(screen);
        if (layout == null) return;

        GuiOverlays.defer(context, () -> drawPanel(context, sorter, layout, mouseX, mouseY));
    }

    private static void drawPanel(DrawContext context, InventorySorter sorter, Layout layout, float mouseX, float mouseY) {
        Bounds panel = layout.panel;
        String status = sorter.getStatusMessage();
        boolean showStatus = layout.statusFits && status != null && !status.isEmpty();
        Render2D.drawRoundedRect(context, panel.x, panel.y, panel.width,
                panel.height + (showStatus ? STATUS_HEIGHT : 0), 4, 0xF31B1D22);

        InventoryPreset preset = InventoryLayoutManager.getInstance().getActivePreset();
        String name = preset == null ? "Нет" : preset.getName();
        if (name.startsWith("КФГ ")) name = name.substring(4);
        if (name.startsWith("Профиль ")) name = name.substring(8);

        float nameWidth = Math.min(42, Fonts.b.a(name, 9));
        Fonts.b.c(Render2D.getMatrix(context), name, panel.x + 38 - nameWidth / 2,
                panel.y + 5.5f, 9, Theme.TEXT_TITLE, 42);

        drawArrow(context, layout.previous, true, mouseX, mouseY);
        drawArrow(context, layout.next, false, mouseX, mouseY);
        drawButton(context, layout.sort, "Сортировать", true, mouseX, mouseY);
        drawButton(context, layout.save, "Снимок", false, mouseX, mouseY);
        if (showStatus) {
            Fonts.sfRegular.drawCentered(Render2D.getMatrix(context), status, panel.centerX(),
                    panel.y + HEIGHT + 3.5f, 8, Theme.TEXT_SUB, 0);
        }
    }

    private static void drawArrow(DrawContext context, Bounds bounds, boolean previous, float mouseX, float mouseY) {
        boolean hover = bounds.contains(mouseX, mouseY);
        if (hover) Render2D.drawRoundedRect(context, bounds.x, bounds.y, bounds.width, bounds.height, 3, 0x1EFFFFFF);
        IconRenderer.drawCollapseIcon(context, bounds.centerX(), bounds.centerY(), !previous,
                hover ? Theme.TEXT_TITLE : Theme.TEXT_SUB);
    }

    private static void drawButton(DrawContext context, Bounds bounds, String label, boolean primary, float mouseX, float mouseY) {
        boolean hover = bounds.contains(mouseX, mouseY);
        if (primary || hover) {
            Render2D.drawRoundedRect(context, bounds.x, bounds.y, bounds.width, bounds.height, 3,
                    hover ? 0x2BFFFFFF : 0x1BFFFFFF);
        }
        Fonts.b.drawCentered(Render2D.getMatrix(context), label, bounds.centerX(), bounds.centerY(), 8.0f,
                primary || hover ? Theme.TEXT_TITLE : Theme.TEXT_SUB, 0);
    }

    public static boolean mouseClicked(InventoryScreen screen, float mouseX, float mouseY, int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        InventorySorter sorter = InventorySorter.getInstance();
        if (sorter == null || !sorter.isEnabled() || client.player == null || button != 0) return false;
        Layout layout = layout(screen);
        if (layout == null) return false;

        InventoryLayoutManager manager = InventoryLayoutManager.getInstance();
        if (layout.sort.contains(mouseX, mouseY)) {
            sorter.startSort();
            return true;
        }
        if (layout.save.contains(mouseX, mouseY)) {
            InventoryPreset preset = manager.getActivePreset();
            manager.saveCurrentAsPreset(preset == null ? "1" : preset.getName(), client.player.getInventory());
            sorter.updatePresetModes();
            GuiSoundManager.playToggle(true);
            return true;
        }
        if (layout.previous.contains(mouseX, mouseY)) {
            manager.prevPreset();
        } else if (layout.next.contains(mouseX, mouseY)) {
            manager.nextPreset();
        } else {
            return false;
        }
        sorter.updatePresetModes();
        GuiSoundManager.playSelect();
        return true;
    }
}
