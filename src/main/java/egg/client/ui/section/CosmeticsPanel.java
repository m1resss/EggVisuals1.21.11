package egg.client.ui.section;

import egg.client.cosmetic.CosmeticsManager;
import egg.client.cosmetic.sword.SwordStyle;
import egg.client.cosmetic.watch.WatchClockDriver;
import egg.client.cosmetic.watch.WatchStyle;
import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.FrostedSurface;
import egg.client.ui.theme.Theme;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

public class CosmeticsPanel {
    private static final String[] CAT_NAMES = {"Все", "Шляпы", "Крылья", "Рюкзаки", "Маски", "Питомцы", "Часы", "Мечи"};
    private static final String[] CAT_KEYS = {"all", "hats", "wings", "backpacks", "masks", "pets", "watches", "swords"};
    private static final float TAB_H = 16;
    private static final float GAP = 5;
    private static final float CARD_H = 52;
    private static final float SECTION_H = 20;
    private static int activeFilter;
    private static float scrollOffset;
    private static float targetScroll;
    private static float maxScroll;

    private static float playerYaw = 0.0f;
    private static float playerPitch = 0.0f;
    private static boolean isDraggingPlayer = false;
    private static float lastDragMouseX = 0.0f;
    private static float lastDragMouseY = 0.0f;

    private record Card(String id, String title, Object preview) {}
    private record Section(String key, String name, List<Card> cards) {}
    private record Area(float x, float y, float w, float h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public static void render(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha, boolean blurred) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null) return;

        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;
        float rightPaneX = x + mainW + 10.0f;
        float rightPaneY = y;
        float rightPaneH = h;

        Area catalog = new Area(x, y, mainW, h);
        List<Section> sections = sections();
        float gridTop = gridTop(y);
        float gridBottom = gridBottom(catalog);
        float gridHeight = gridBottom - gridTop;
        float contentHeight = 0;
        for (Section section : sections) contentHeight += sectionHeight(section);
        maxScroll = Math.max(0, contentHeight - gridHeight);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        scrollOffset = Math.clamp(scrollOffset + (targetScroll - scrollOffset) * 0.27f, 0, maxScroll);

        for (int i = 0; i < CAT_NAMES.length; i++) {
            Area tab = tab(catalog, i);
            boolean chosen = activeFilter == i;
            boolean hovered = tab.contains(mouseX, mouseY);
            if (chosen || hovered) {
                Render2D.drawRoundedRect(context, tab.x, tab.y, tab.w, tab.h, 4, chosen ? 0x35000000 | (Theme.ACCENT & 0x00FFFFFF) : 0x15FFFFFF);
            }
            ClientFont.drawCenteredScaled(context, CAT_NAMES[i], tab.x + tab.w / 2, tab.y + tab.h / 2, 0.87f,
                    ClientFont.SF_MEDIUM, chosen || hovered ? Theme.TEXT_TITLE : Theme.TEXT_SUB);
        }

        Render2D.drawRect(context, x, gridTop - 3, mainW, 1, 0x14FFFFFF);
        Render2D.enableScissor(context, x, gridTop, x + mainW, gridBottom);
        ScreenRect clip = new ScreenRect((int) x, (int) gridTop, (int) mainW, (int) gridHeight).transform(context.getMatrices());
        float sectionY = gridTop - scrollOffset;
        for (Section section : sections) {
            float sectionBottom = sectionY + sectionHeight(section);
            if (sectionBottom > gridTop && sectionY < gridBottom) {
                ClientFont.draw(context, section.name, x + 4, sectionY + 6, ClientFont.SF_BOLD, Theme.TEXT_TITLE);
                String selected = mod.selected(section.key);
                if (equipped(selected)) {
                    Area remove = remove(catalog, sectionY);
                    var color = remove.contains(mouseX, mouseY) && mouseY >= gridTop && mouseY < gridBottom ? Theme.TEXT_TITLE : Theme.TEXT_SUB;
                    ClientFont.drawCenteredScaled(context, "Снять", remove.x + remove.w / 2, remove.y + remove.h / 2, 0.85f, ClientFont.SF_MEDIUM, color);
                }

                for (int i = 0; i < section.cards.size(); i++) {
                    Area bounds = card(catalog, sectionY, i);
                    if (bounds.y + bounds.h < gridTop || bounds.y > gridBottom) continue;
                    Card item = section.cards.get(i);
                    drawCard(context, item, bounds, clip, item.id.equalsIgnoreCase(selected),
                            mouseY >= gridTop && mouseY < gridBottom && bounds.contains(mouseX, mouseY), alpha, blurred);
                }
            }
            sectionY = sectionBottom;
        }
        Render2D.disableScissor(context);

        if (maxScroll > 0) {
            float thumb = Math.max(13, gridHeight * gridHeight / contentHeight);
            float thumbY = gridTop + (gridHeight - thumb) * scrollOffset / maxScroll;
            Render2D.drawRoundedRect(context, x + mainW - 1.5f, thumbY, 1.5f, thumb, 0.75f, 0x38FFFFFF);
        }

        Render2D.drawRect(context, x, gridBottom + 3, mainW, 1, 0x14FFFFFF);
        Area resetAll = resetAll(catalog);
        if (hasEquipped(mod)) {
            ClientFont.drawCenteredScaled(context, "Снять всё", resetAll.x + resetAll.w / 2, resetAll.y + resetAll.h / 2, 0.85f,
                    ClientFont.SF_MEDIUM, resetAll.contains(mouseX, mouseY) ? Theme.TEXT_TITLE : Theme.TEXT_SUB);
        }

        FrostedSurface.draw(context, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f,
                Theme.CARD_BG, alpha, 0.70f, blurred);
        Render2D.drawRoundedOutline(context, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, 0.6f, Theme.CARD_BORDER);

        ClientFont.draw(context, "Предпросмотр", rightPaneX + 10.0f, rightPaneY + 10.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);
        ClientFont.draw(context, "3D модель игрока", rightPaneX + 10.0f, rightPaneY + 22.0f, ClientFont.SF_REGULAR, 0xFFA0A8B8);

        float rstW = 46.0f, rstH = 16.0f;
        float rstX = rightPaneX + rightPaneW - rstW - 10.0f;
        float rstY = rightPaneY + 10.0f;
        boolean hovReset = mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH;
        Render2D.drawRoundedRect(context, rstX, rstY, rstW, rstH, 3.5f, hovReset ? Theme.ACCENT : 0x22FFFFFF);
        Render2D.drawRoundedOutline(context, rstX, rstY, rstW, rstH, 3.5f, 0.5f, hovReset ? Theme.ACCENT : Theme.CARD_BORDER);
        ClientFont.drawCentered(context, "Сброс", rstX + rstW * 0.5f, rstY + rstH * 0.5f, ClientFont.SF_REGULAR, hovReset ? 0xFFFFFFFF : 0xFFA0A8B8);

        Render2D.drawRect(context, rightPaneX + 8.0f, rightPaneY + 34.0f, rightPaneW - 16.0f, 1.0f, 0x15FFFFFF);

        if (mc.player != null) {
            float previewBoxX = rightPaneX + 4.0f;
            float previewBoxY = rightPaneY + 36.0f;
            float previewBoxW = rightPaneW - 8.0f;
            float previewBoxH = rightPaneH - 60.0f;

            Render2D.enableScissor(context, previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + previewBoxH);

            float centerX = previewBoxX + previewBoxW * 0.5f;
            float centerY = previewBoxY + previewBoxH * 0.82f;
            int baseSize = (int) (previewBoxH * 0.44f);

            Matrix3x2f mat = Render2D.getMatrix(context);
            float actualCenterX = centerX;
            float actualCenterY = centerY;
            float matrixScale = 1.0f;
            if (mat != null) {
                actualCenterX = (mat.m00 * centerX) + (mat.m10 * centerY) + mat.m20;
                actualCenterY = (mat.m01 * centerX) + (mat.m11 * centerY) + mat.m21;
                matrixScale = (float) Math.hypot(mat.m00, mat.m01);
            }
            int scaledBaseSize = (int) Math.round(baseSize * matrixScale);

            int x1 = (int) Math.round(actualCenterX - scaledBaseSize * 0.8f);
            int y1 = (int) Math.round(actualCenterY - scaledBaseSize * 1.8f);
            int x2 = (int) Math.round(actualCenterX + scaledBaseSize * 0.8f);
            int y2 = (int) Math.round(actualCenterY + scaledBaseSize * 0.3f);

            float lookX = actualCenterX - playerYaw * 2.5f * matrixScale;
            float lookY = actualCenterY - playerPitch * 2.5f * matrixScale;
            InventoryScreen.drawEntity(context, x1, y1, x2, y2, scaledBaseSize, 0.0625f, lookX, lookY, mc.player);

            Render2D.disableScissor(context);
        }

        Render2D.drawRect(context, rightPaneX + 8.0f, rightPaneY + rightPaneH - 22.0f, rightPaneW - 16.0f, 1.0f, 0x15FFFFFF);
        ClientFont.drawCentered(context, "ЛКМ: Вращение модели", rightPaneX + rightPaneW * 0.5f, rightPaneY + rightPaneH - 11.0f, ClientFont.SF_REGULAR, 0xFF8A92A2);
    }

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null) return false;

        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;
        float rightPaneX = x + mainW + 10.0f;
        float rightPaneY = y;
        float rightPaneH = h;

        if (mouseX >= rightPaneX && mouseX <= rightPaneX + rightPaneW && mouseY >= rightPaneY && mouseY <= rightPaneY + rightPaneH) {
            float rstW = 46.0f, rstH = 16.0f;
            float rstX = rightPaneX + rightPaneW - rstW - 10.0f;
            float rstY = rightPaneY + 10.0f;
            if (mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH) {
                playerYaw = 0.0f;
                playerPitch = 0.0f;
                GuiSoundManager.playClick();
                return true;
            }

            isDraggingPlayer = true;
            lastDragMouseX = (float) mouseX;
            lastDragMouseY = (float) mouseY;
            return true;
        }


        Area catalog = new Area(x, y, mainW, h);
        if (!catalog.contains(mouseX, mouseY)) return false;

        for (int i = 0; i < CAT_NAMES.length; i++) {
            if (tab(catalog, i).contains(mouseX, mouseY)) {
                activeFilter = i;
                scrollOffset = targetScroll = 0;
                GuiSoundManager.playClick();
                return true;
            }
        }

        if (resetAll(catalog).contains(mouseX, mouseY) && hasEquipped(mod)) {
            mod.clearAll();
            GuiSoundManager.playClick();
            return true;
        }

        float gridTop = gridTop(y);
        if (mouseY < gridTop || mouseY >= gridBottom(catalog)) return false;
        float sectionY = gridTop - scrollOffset;
        for (Section section : sections()) {
            String selected = mod.selected(section.key);
            if (equipped(selected) && remove(catalog, sectionY).contains(mouseX, mouseY)) {
                mod.clearSlot(section.key);
                GuiSoundManager.playClick();
                return true;
            }

            for (int i = 0; i < section.cards.size(); i++) {
                if (!card(catalog, sectionY, i).contains(mouseX, mouseY)) continue;
                Card item = section.cards.get(i);
                if (item.id.equalsIgnoreCase(selected)) {
                    mod.clearSlot(section.key);
                } else {
                    mod.select(section.key, item.id);
                }
                GuiSoundManager.playClick();
                return true;
            }
            sectionY += sectionHeight(section);
        }

        return false;
    }

    private static List<Section> sections() {
        CosmeticsManager manager = CosmeticsManager.getInstance();
        manager.ensureIndex();
        List<Section> result = new ArrayList<>();
        int start = activeFilter == 0 ? 1 : activeFilter;
        int end = activeFilter == 0 ? CAT_KEYS.length : activeFilter + 1;
        for (int i = start; i < end; i++) {
            String key = CAT_KEYS[i];
            List<Card> cards = new ArrayList<>();
            switch (key) {
                case "swords" -> {
                    for (SwordStyle style : SwordStyle.values()) {
                        if (style != SwordStyle.NONE) cards.add(new Card(style.getName(), style.getName(), style));
                    }
                }
                case "watches" -> {
                    for (WatchStyle style : WatchStyle.values()) {
                        if (style != WatchStyle.NONE) cards.add(new Card(style.getName(), style.getName(), style));
                    }
                }
                default -> {
                    for (CosmeticsManager.Asset asset : manager.getCategoryAssets(key, "")) {
                        cards.add(new Card(asset.id(), asset.displayName(), asset));
                    }
                }
            }
            if (!cards.isEmpty()) result.add(new Section(key, CAT_NAMES[i], cards));
        }
        return result;
    }

    private static void drawCard(DrawContext context, Card item, Area bounds, ScreenRect clip, boolean selected,
                                 boolean hovered, float alpha, boolean blurred) {
        float x = bounds.x;
        float y = bounds.y;
        float w = bounds.w;
        float cy = y + bounds.h / 2;
        int bg = selected ? 0x28000000 | (Theme.ACCENT & 0xFFFFFF) : hovered ? 0x20FFFFFF : Theme.CARD_BG;
        FrostedSurface.draw(context, x, y, w, bounds.h, 5, bg, alpha, hovered ? 0.77f : 0.70f, blurred);
        if (selected) Render2D.drawRoundedRect(context, x + 2, y + 14, 1.5f, 24, 0.75f, Theme.ACCENT);

        float iconX = x + 6;
        float iconY = cy - 20;
        switch (item.preview) {
            case SwordStyle sword -> {
                if (sword.getIcon() != null) {
                    IconRenderer.drawTextureScaled(context, sword.getIcon(), iconX, iconY, 40, 40, 64, 64, 0xFFFFFFFF);
                }
            }
            case WatchStyle watch -> renderWatchCardPreview(context, watch, iconX + 20, cy, selected);
            case CosmeticsManager.Asset asset -> CosmeticsManager.getInstance()
                    .renderGuiPreview(context, asset.id(), iconX, iconY, 40, 1, clip);
            default -> throw new IllegalStateException("Unknown cosmetic preview");
        }

        float textX = x + 49;
        float textWidth = w - 55;
        if (ClientFont.getWidth(item.title, ClientFont.SF_MEDIUM) * 0.91f > textWidth && item.title.contains(" ")) {
            int split = item.title.indexOf(' ', item.title.length() / 2);
            if (split < 0) split = item.title.lastIndexOf(' ');
            drawName(context, item.title.substring(0, split), textX, cy - 8, textWidth, selected);
            drawName(context, item.title.substring(split + 1), textX, cy + 3, textWidth, selected);
        } else {
            drawName(context, item.title, textX, cy - 3, textWidth, selected);
        }
    }

    private static void drawName(DrawContext context, String name, float x, float y, float width, boolean selected) {
        float naturalWidth = ClientFont.getWidth(name, ClientFont.SF_MEDIUM);
        float scale = Math.min(0.91f, width / Math.max(1, naturalWidth));
        ClientFont.drawScaled(context, name, x, y, scale, ClientFont.SF_MEDIUM, selected ? Theme.ACCENT : Theme.TEXT_TITLE);
    }

    private static Area tab(Area catalog, int index) {
        int col = index % 5;
        float w = (catalog.w - 4 * GAP) / 5;
        return new Area(catalog.x + col * (w + GAP), catalog.y + 2 + index / 5 * (TAB_H + 2), w, TAB_H);
    }

    private static float gridTop(float y) {
        return y + 2 * TAB_H + 10;
    }

    private static float gridBottom(Area catalog) {
        return catalog.y + catalog.h - 20;
    }

    private static Area resetAll(Area catalog) {
        return new Area(catalog.x + catalog.w - 56, gridBottom(catalog) + 5, 55, 15);
    }

    private static Area remove(Area catalog, float sectionY) {
        return new Area(catalog.x + catalog.w - 43, sectionY, 41, SECTION_H);
    }

    private static Area card(Area catalog, float sectionY, int index) {
        float width = (catalog.w - 4 - GAP) / 2;
        return new Area(catalog.x + index % 2 * (width + GAP), sectionY + SECTION_H + index / 2 * (CARD_H + GAP), width, CARD_H);
    }

    private static float sectionHeight(Section section) {
        return SECTION_H + ((section.cards.size() + 1) / 2) * (CARD_H + GAP) + 4;
    }

    private static boolean equipped(String id) {
        return !id.isEmpty() && !"None".equalsIgnoreCase(id);
    }

    private static boolean hasEquipped(CosmeticsModule mod) {
        for (int i = 1; i < CAT_KEYS.length; i++) {
            if (equipped(mod.selected(CAT_KEYS[i]))) return true;
        }
        return false;
    }

    public static void mouseReleased() {
        isDraggingPlayer = false;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingPlayer) {
            isDraggingPlayer = false;
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (isDraggingPlayer) {
            float dx = (float) mouseX - lastDragMouseX;
            float dy = (float) mouseY - lastDragMouseY;
            playerYaw = Math.clamp(playerYaw + dx * 1.5f, -85.0f, 85.0f);
            playerPitch = Math.clamp(playerPitch + dy * 1.5f, -45.0f, 45.0f);
            lastDragMouseX = (float) mouseX;
            lastDragMouseY = (float) mouseY;
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return mouseDragged(mouseX, mouseY);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount, float contentX, float contentY, float contentW, float contentH) {
        if (mouseX < contentX || mouseX >= contentX + contentW - 168 || mouseY < gridTop(contentY)
                || mouseY >= contentY + contentH - 20) return false;
        targetScroll -= (float) (amount * 34.0f);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);
        return true;
    }

    private static void renderWatchCardPreview(DrawContext context, WatchStyle watch, float cx, float cy, boolean isSelected) {
        float strapW = 10.0f;
        float strapH = 38.0f;
        float strapX = cx - strapW * 0.5f;
        float strapY = cy - strapH * 0.5f;

        Render2D.drawRoundedRect(context, strapX, strapY, strapW, strapH, 3.5f, 0xFF353945);
        if (watch.getTexture() != null) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(cx, cy);
            context.getMatrices().rotate((float) (Math.PI * 0.5));
            context.drawTexture(RenderPipelines.GUI_TEXTURED, watch.getTexture(), (int) (-strapH * 0.5f), (int) (-strapW * 0.5f), 0.0f, 64.0f, (int) strapH, (int) strapW, 64, 32, 128, 128, 0xEEFFFFFF);
            context.getMatrices().popMatrix();
        }
        Render2D.drawRoundedOutline(context, strapX, strapY, strapW, strapH, 3.5f, 0.6f, isSelected ? Theme.ACCENT : 0x33FFFFFF);

        float dialSize = 26.0f;
        float dialX = cx - dialSize * 0.5f;
        float dialY = cy - dialSize * 0.5f;

        Render2D.drawCircleShadow(context, cx, cy, dialSize * 0.44f, 6.0f, 0x55000000);
        if (watch.getTexture() != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, watch.getTexture(), (int) dialX, (int) dialY, 0.0f, 0.0f, (int) dialSize, (int) dialSize, 64, 64, 128, 128, 0xFFFFFFFF);
        }

        float hrAngle = (float) Math.toRadians(WatchClockDriver.getHourAngle() - 90.0f);
        float minAngle = (float) Math.toRadians(WatchClockDriver.getMinuteAngle() - 90.0f);
        float secAngle = (float) Math.toRadians(WatchClockDriver.getSecondAngle() - 90.0f);

        Render2D.drawCircle(context, cx, cy, 1.8f, 0xFFE0E0E0);
        float hrLen = 5.4f;
        float minLen = 7.2f;
        float secLen = 14.0f;
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(hrAngle) * hrLen, cy + (float) Math.sin(hrAngle) * hrLen, 0xFFFFFFFF, 1.4f);
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(minAngle) * minLen, cy + (float) Math.sin(minAngle) * minLen, 0xFFFFFFFF, 1.0f);
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(secAngle) * secLen, cy + (float) Math.sin(secAngle) * secLen, 0xFFFF4444, 0.8f);
    }

}
