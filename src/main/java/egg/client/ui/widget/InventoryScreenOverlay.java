package egg.client.ui.widget;

import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.mixin.HandledScreenAccessor;
import egg.client.module.ModuleManager;
import egg.client.module.player.InventorySorter;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public class InventoryScreenOverlay {

    public static void render(DrawContext context, InventoryScreen screen, float mouseX, float mouseY) {
        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        if (sorter == null || !sorter.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int guiX = ((HandledScreenAccessor) screen).getX();
        int guiY = ((HandledScreenAccessor) screen).getY();

        String status = sorter.getStatusMessage();
        boolean hasStatus = (status != null && !status.isEmpty());

        // Ultra compact & sleek floating pill widget
        float panelW = 106.0f;
        float panelH = hasStatus ? 84.0f : 68.0f;

        // Position on the right side of inventory (guiX + 176 + 6)
        float panelX = guiX + 176.0f + 6.0f;
        float panelY = guiY + 14.0f;

        InventoryLayoutManager mgr = InventoryLayoutManager.getInstance();
        InventoryPreset preset = mgr.getActivePreset();

        // 1. Sleek glass background with subtle rounded outline & shadow
        Render2D.drawShadow(context, panelX, panelY, panelW, panelH, 6.0f, 10.0f, ColorUtil.rgba(0, 0, 0, 140));
        Render2D.drawRoundedRect(context, panelX, panelY, panelW, panelH, 6.0f, ColorUtil.rgba(14, 16, 24, 230));
        Render2D.drawRoundedOutline(context, panelX, panelY, panelW, panelH, 6.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 24));

        // 2. Profile Selector Row: [ < ] [ Profile Number / Name ] [ > ]
        float selY = panelY + 6.0f;
        float arrowBtnW = 15.0f;
        float selH = 16.0f;

        float prevX = panelX + 5.0f;
        boolean hoverPrev = mouseX >= prevX && mouseX <= prevX + arrowBtnW && mouseY >= selY && mouseY <= selY + selH;
        int prevBg = hoverPrev ? ColorUtil.rgba(40, 46, 62, 230) : ColorUtil.rgba(22, 26, 36, 200);
        Render2D.drawRoundedRect(context, prevX, selY, arrowBtnW, selH, 3.5f, prevBg);
        Render2D.drawRoundedOutline(context, prevX, selY, arrowBtnW, selH, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, hoverPrev ? 35 : 16));
        // Draw crisp left vector chevron
        float lcx = prevX + arrowBtnW / 2.0f;
        float lcy = selY + selH / 2.0f;
        int larrCol = hoverPrev ? Theme.getAccent() : 0xFFA0B0D0;
        Render2D.drawLine(context, lcx + 1.5f, lcy - 3.0f, lcx - 1.5f, lcy, larrCol, 1.4f);
        Render2D.drawLine(context, lcx - 1.5f, lcy, lcx + 1.5f, lcy + 3.0f, larrCol, 1.4f);

        float nextX = panelX + panelW - arrowBtnW - 5.0f;
        boolean hoverNext = mouseX >= nextX && mouseX <= nextX + arrowBtnW && mouseY >= selY && mouseY <= selY + selH;
        int nextBg = hoverNext ? ColorUtil.rgba(40, 46, 62, 230) : ColorUtil.rgba(22, 26, 36, 200);
        Render2D.drawRoundedRect(context, nextX, selY, arrowBtnW, selH, 3.5f, nextBg);
        Render2D.drawRoundedOutline(context, nextX, selY, arrowBtnW, selH, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, hoverNext ? 35 : 16));
        // Draw crisp right vector chevron
        float rcx = nextX + arrowBtnW / 2.0f;
        float rcy = selY + selH / 2.0f;
        int rarrCol = hoverNext ? Theme.getAccent() : 0xFFA0B0D0;
        Render2D.drawLine(context, rcx - 1.5f, rcy - 3.0f, rcx + 1.5f, rcy, rarrCol, 1.4f);
        Render2D.drawLine(context, rcx + 1.5f, rcy, rcx - 1.5f, rcy + 3.0f, rarrCol, 1.4f);

        // Center Profile Name
        float nameBoxX = prevX + arrowBtnW + 3.0f;
        float nameBoxW = nextX - nameBoxX - 3.0f;
        Render2D.drawRoundedRect(context, nameBoxX, selY, nameBoxW, selH, 3.5f, ColorUtil.rgba(20, 24, 34, 180));
        Render2D.drawRoundedOutline(context, nameBoxX, selY, nameBoxW, selH, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, 14));

        String pName = (preset != null) ? preset.getName() : "Нет";
        if (pName.startsWith("КФГ ")) pName = pName.substring(4);
        if (pName.startsWith("Профиль ")) pName = pName.substring(8);
        ClientFont.drawCentered(context, pName, nameBoxX + nameBoxW / 2.0f, selY + selH / 2.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        // 3. Compact [ Сортировать ] Action Button
        float sortY = selY + selH + 5.0f;
        float btnW = panelW - 10.0f;
        float btnX = panelX + 5.0f;
        float sortH = 18.0f;

        boolean hoverSort = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= sortY && mouseY <= sortY + sortH;
        int sortBg = hoverSort ? Theme.getAccentHover() : Theme.getAccent();
        Render2D.drawRoundedRect(context, btnX, sortY, btnW, sortH, 4.0f, sortBg);
        Render2D.drawRoundedOutline(context, btnX, sortY, btnW, sortH, 4.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 40));
        ClientFont.drawCentered(context, "Сортировать", btnX + btnW / 2.0f, sortY + sortH / 2.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

        // 4. Compact [ Сохранить ] Action Button
        float saveY = sortY + sortH + 4.0f;
        float saveH = 16.0f;

        boolean hoverSave = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= saveY && mouseY <= saveY + saveH;
        int saveBg = hoverSave ? ColorUtil.rgba(36, 42, 60, 240) : ColorUtil.rgba(22, 26, 36, 200);
        Render2D.drawRoundedRect(context, btnX, saveY, btnW, saveH, 4.0f, saveBg);
        Render2D.drawRoundedOutline(context, btnX, saveY, btnW, saveH, 4.0f, 1.0f, ColorUtil.rgba(255, 255, 255, 22));
        ClientFont.drawCentered(context, "Сохранить", btnX + btnW / 2.0f, saveY + saveH / 2.0f, ClientFont.SF_MEDIUM, hoverSave ? 0xFFFFFFFF : 0xFFCBD5E1);

        // 5. Status message if sorting
        if (hasStatus) {
            int statCol = status.contains("успешно") || status.contains("разложен") ? 0xFF2ECC71 : (status.contains("Сортировка") ? 0xFFF1C40F : 0xFFA0B0C5);
            ClientFont.drawCentered(context, status, panelX + panelW / 2.0f, saveY + saveH + 7.5f, ClientFont.SF_REGULAR, statCol);
        }
    }

    public static boolean mouseClicked(InventoryScreen screen, float mouseX, float mouseY, int button) {
        InventorySorter sorter = ModuleManager.getInstance().getModule(InventorySorter.class);
        if (sorter == null || !sorter.isEnabled()) return false;
        if (button != 0) return false;

        int guiX = ((HandledScreenAccessor) screen).getX();
        int guiY = ((HandledScreenAccessor) screen).getY();

        float panelW = 106.0f;
        float panelX = guiX + 176.0f + 6.0f;
        float panelY = guiY + 14.0f;

        float selY = panelY + 6.0f;
        float arrowBtnW = 15.0f;
        float selH = 16.0f;

        float prevX = panelX + 5.0f;
        float nextX = panelX + panelW - arrowBtnW - 5.0f;

        // Click Prev Chevron
        if (mouseX >= prevX && mouseX <= prevX + arrowBtnW && mouseY >= selY && mouseY <= selY + selH) {
            InventoryLayoutManager.getInstance().prevPreset();
            sorter.updatePresetModes();
            GuiSoundManager.playSelect();
            return true;
        }

        // Click Next Chevron
        if (mouseX >= nextX && mouseX <= nextX + arrowBtnW && mouseY >= selY && mouseY <= selY + selH) {
            InventoryLayoutManager.getInstance().nextPreset();
            sorter.updatePresetModes();
            GuiSoundManager.playSelect();
            return true;
        }

        // Click [ Сортировать ]
        float sortY = selY + selH + 5.0f;
        float btnW = panelW - 10.0f;
        float btnX = panelX + 5.0f;
        float sortH = 18.0f;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= sortY && mouseY <= sortY + sortH) {
            sorter.startSort();
            return true;
        }

        // Click [ Сохранить ]
        float saveY = sortY + sortH + 4.0f;
        float saveH = 16.0f;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= saveY && mouseY <= saveY + saveH) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                InventoryPreset cur = InventoryLayoutManager.getInstance().getActivePreset();
                String targetName = (cur != null) ? cur.getName() : "1";
                InventoryLayoutManager.getInstance().saveCurrentAsPreset(
                        targetName,
                        mc.player.getInventory()
                );
                GuiSoundManager.playToggle(true);
            }
            return true;
        }

        return false;
    }
}
