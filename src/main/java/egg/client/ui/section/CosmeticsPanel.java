package egg.client.ui.section;

import egg.client.cosmetic.CosmeticsManager;
import egg.client.cosmetic.CosmeticsRenderer;
import egg.client.cosmetic.sword.SwordStyle;
import egg.client.cosmetic.watch.WatchClockDriver;
import egg.client.cosmetic.watch.WatchStyle;
import egg.client.module.ModuleManager;
import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.render.IconRenderer;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import java.util.*;

public class CosmeticsPanel {
    private static String searchQuery = "";

    private static final String[] CAT_NAMES = new String[] { "Все", "Шляпы", "Крылья", "Рюкзаки", "Маски", "Питомцы", "Часы", "Мечи" };
    private static final String[] CAT_KEYS  = new String[] { "all", "hats", "wings", "backpacks", "masks", "pets", "watches", "swords" };

    private static int activeFilter = 0;
    private static float scrollOffset = 0.0f;
    private static float targetScroll = 0.0f;
    private static float maxScroll = 0.0f;

    private static float playerYaw = 0.0f;
    private static float playerPitch = 0.0f;
    private static boolean isDraggingPlayer = false;
    private static float lastDragMouseX = 0.0f;
    private static float lastDragMouseY = 0.0f;

    private static final Map<String, Float> toggleAnims = new HashMap<>();

    public static void setSearchQuery(String q) {
        searchQuery = q == null ? "" : q.trim();
        targetScroll = 0.0f;
        scrollOffset = 0.0f;
    }

    public static void render(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CosmeticsModule mod = ModuleManager.getInstance().getModule(CosmeticsModule.class);
        if (mod == null) return;

        CosmeticsManager mgr = CosmeticsManager.getInstance();
        mgr.ensureIndex();

        scrollOffset += (targetScroll - scrollOffset) * 0.25f;

        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;
        float rightPaneX = x + mainW + 10.0f;
        float rightPaneY = y;
        float rightPaneH = h;

        float navY = y + 2.0f;
        float navH = 17.0f;
        float pillGap = 3.5f;
        float navRowGap = 3.5f;
        int totalPills = CAT_NAMES.length + 1;
        int row0Count = 5;
        int row1Count = totalPills - row0Count;

        for (int i = 0; i < totalPills; i++) {
            int row = (i < row0Count) ? 0 : 1;
            int col = (row == 0) ? i : (i - row0Count);
            int rowCols = (row == 0) ? row0Count : row1Count;
            float pillW = (mainW - (rowCols - 1) * pillGap) / (float) rowCols;
            float pX = x + col * (pillW + pillGap);
            float pY = navY + row * (navH + navRowGap);

            String label = (i < CAT_NAMES.length) ? CAT_NAMES[i] : "Снять все";
            boolean isAction = (i == CAT_NAMES.length);
            boolean isSel = !isAction && (activeFilter == i);
            boolean hov = mouseX >= pX && mouseX <= pX + pillW && mouseY >= pY && mouseY <= pY + navH;

            int pBg;
            if (isAction) {
                pBg = hov ? 0xDDFF4444 : 0x18FFFFFF;
            } else {
                pBg = isSel ? Theme.ACCENT : (hov ? 0x28FFFFFF : 0x16FFFFFF);
            }

            Render2D.drawRoundedRect(context, pX, pY, pillW, navH, 4.0f, pBg);
            if (isSel) {
                Render2D.drawRoundedOutline(context, pX, pY, pillW, navH, 4.0f, 0.6f, Theme.ACCENT);
            } else if (isAction && hov) {
                Render2D.drawRoundedOutline(context, pX, pY, pillW, navH, 4.0f, 0.6f, 0xFFFF6B6B);
            }

            int txtCol = isSel ? 0xFFFFFFFF : (hov ? 0xFFFFFFFF : (isAction ? 0xFFC0C8D8 : 0xFFA0A8B8));
            ClientFont.drawCentered(context, label, pX + pillW * 0.5f, pY + navH * 0.5f, ClientFont.SF_MEDIUM, txtCol);
        }

        float gridTop = navY + navH * 2.0f + navRowGap + 5.0f;
        Render2D.drawRect(context, x, gridTop - 2.0f, mainW, 1.0f, 0x12FFFFFF);

        Render2D.enableScissor(context, x, gridTop, x + mainW, y + h);

        float currentCatY = gridTop + 4.0f - scrollOffset;
        List<String> activeCats = new ArrayList<>();
        if (activeFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("watches");
            activeCats.add("swords");
        } else {
            activeCats.add(CAT_KEYS[activeFilter]);
        }

        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (mainW - (cols - 1) * colGap) / cols;
        float cardH = 108.0f;

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            boolean isWatches = "watches".equals(catKey);
            String catTitle = switch (catKey) {
                case "hats" -> "Шляпы (Hats)";
                case "wings" -> "Крылья (Wings)";
                case "backpacks" -> "Рюкзаки (Backpacks)";
                case "masks" -> "Маски (Masks)";
                case "pets" -> "Питомцы (Pets)";
                case "swords" -> "Мечи (Swords)";
                case "watches" -> "Часы (Watches)";
                default -> catKey;
            };

            List<CosmeticsManager.Asset> assetsList = (isSwords || isWatches) ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SwordStyle> swordsList = isSwords ? getFilteredSwords() : Collections.emptyList();
            List<WatchStyle> watchesList = isWatches ? getFilteredWatches() : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : (isWatches ? watchesList.size() : assetsList.size());

            float catHeaderH = 20.0f;
            ClientFont.draw(context, catTitle, x + 2.0f, currentCatY + 4.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

            String selectedId = isSwords ? mod.selectedSword() : mod.selected(catKey);
            boolean hasEquipped = selectedId != null && !selectedId.isEmpty() && !"None".equalsIgnoreCase(selectedId);
            if (hasEquipped) {
                float badgeW = 46.0f;
                float badgeH = 14.0f;
                float badgeX = x + mainW - badgeW;
                float badgeY = currentCatY + 2.0f;
                boolean hovRst = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + badgeH;
                Render2D.drawRoundedRect(context, badgeX, badgeY, badgeW, badgeH, 3.5f, hovRst ? 0xDDFF4444 : 0x22FFFFFF);
                Render2D.drawRoundedOutline(context, badgeX, badgeY, badgeW, badgeH, 3.5f, 0.5f, hovRst ? 0xFFFF6B6B : Theme.CARD_BORDER);
                ClientFont.drawCentered(context, "Снять", badgeX + badgeW * 0.5f, badgeY + badgeH * 0.5f, ClientFont.SF_REGULAR, hovRst ? 0xFFFFFFFF : 0xFFA0A8B8);
            }

            currentCatY += catHeaderH;

            int rows = Math.max(1, (itemCount + cols - 1) / cols);

            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = x + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                if (cardY + cardH < gridTop || cardY > y + h) {
                    continue;
                }

                CosmeticsManager.Asset asset = (isSwords || isWatches) ? null : assetsList.get(i);
                SwordStyle sword = isSwords ? swordsList.get(i) : null;
                WatchStyle watch = isWatches ? watchesList.get(i) : null;
                String displayName = isSwords ? sword.getName() : (isWatches ? watch.getName() : asset.displayName());

                boolean isSelected = isSwords
                        ? (sword == mod.getSelectedSwordStyle())
                        : (isWatches ? (watch == mod.getSelectedWatchStyle()) : (asset.id().equalsIgnoreCase(mod.selected(catKey))));

                boolean isHovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

                int cBg = isSelected ? 0x264C8DFF : (isHovered ? 0x1AFFFFFF : Theme.CARD_BG);
                int cBrd = isSelected ? Theme.ACCENT : (isHovered ? 0x55FFFFFF : Theme.CARD_BORDER);

                Render2D.drawRoundedRect(context, cardX, cardY, cardW, cardH, 6.0f, cBg);
                Render2D.drawRoundedOutline(context, cardX, cardY, cardW, cardH, 6.0f, 0.6f, cBrd);

                if (isSwords && sword != null) {
                    renderSwordCardPreview(context, sword, cardX, cardY, cardW);
                } else if (isWatches && watch != null) {
                    renderWatchCardPreview(context, watch, cardX, cardY, cardW, isSelected);
                } else if (!isSwords && !isWatches && asset != null) {
                    float pSize = cardW - 20.0f;
                    float pX = cardX + 10.0f;
                    float pY = cardY + 6.0f;
                    net.minecraft.client.gui.ScreenRect cardsScissor = new net.minecraft.client.gui.ScreenRect((int) x, (int) gridTop, Math.max(1, (int) mainW), Math.max(1, (int) (y + h - gridTop))); mgr.renderGuiPreview(context, asset.id(), pX, pY, pSize, 1.0f, cardsScissor);
                }

                float bottomH = 22.0f;
                float bottomY = cardY + cardH - bottomH;

                float swW = 20.0f, swH = 11.0f;
                float swX = cardX + cardW - swW - 8.0f;
                float swY = bottomY + (bottomH - swH) * 0.5f;

                String animKey = isSwords ? "sw_" + sword.getName() : (isWatches ? "wt_" + watch.getName() : "cs_" + asset.id());
                float swAnim = getAnim(animKey, isSelected);
                int swBg = ColorUtil.lerp(0xFF232838, 0xFF4CD137, swAnim);
                Render2D.drawRoundedRect(context, swX, swY, swW, swH, 5.5f, swBg);

                float swDotSz = 7.0f;
                float swDotX = swX + 2.0f + swAnim * 9.0f;
                float swDotY = swY + 2.0f;
                Render2D.drawRoundedRect(context, swDotX, swDotY, swDotSz, swDotSz, 3.5f, 0xFFFFFFFF);

                String title = displayName;
                float maxTitleW = cardW - swW - 16.0f;
                if (ClientFont.getWidth(title, ClientFont.SF_MEDIUM) > maxTitleW) {
                    while (title.length() > 3 && ClientFont.getWidth(title + "..", ClientFont.SF_MEDIUM) > maxTitleW) {
                        title = title.substring(0, title.length() - 1);
                    }
                    title += "..";
                }
                float titleY = bottomY + (bottomH - 8.0f) * 0.5f;
                ClientFont.draw(context, title, cardX + 7.0f, titleY, ClientFont.SF_MEDIUM, isSelected ? Theme.ACCENT : 0xFFFFFFFF);
            }

            currentCatY += rows * (cardH + rowGap) + 12.0f;
        }

        float measuredTotalH = currentCatY - (gridTop - scrollOffset);
        maxScroll = Math.max(0.0f, measuredTotalH - (h - (gridTop - y)));

        Render2D.disableScissor(context);

        Render2D.drawRoundedRect(context, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, Theme.CARD_BG);
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

            int x1 = (int) (centerX - baseSize * 0.8f);
            int y1 = (int) (centerY - baseSize * 1.8f);
            int x2 = (int) (centerX + baseSize * 0.8f);
            int y2 = (int) (centerY + baseSize * 0.3f);

            float lookX = centerX - playerYaw * 2.5f;
            float lookY = centerY - playerPitch * 2.5f;
            InventoryScreen.drawEntity(context, x1, y1, x2, y2, baseSize, 0.0625f, lookX, lookY, mc.player);

            Render2D.disableScissor(context);
        }

        Render2D.drawRect(context, rightPaneX + 8.0f, rightPaneY + rightPaneH - 22.0f, rightPaneW - 16.0f, 1.0f, 0x15FFFFFF);
        ClientFont.drawCentered(context, "ЛКМ: Вращение модели", rightPaneX + rightPaneW * 0.5f, rightPaneY + rightPaneH - 11.0f, ClientFont.SF_REGULAR, 0xFF8A92A2);
    }

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        CosmeticsModule mod = ModuleManager.getInstance().getModule(CosmeticsModule.class);
        if (mod == null) return false;

        CosmeticsManager mgr = CosmeticsManager.getInstance();
        mgr.ensureIndex();

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

        if (mouseX < x || mouseX > x + mainW || mouseY < y || mouseY > y + h) {
            return false;
        }

        float navY = y + 2.0f;
        float navH = 17.0f;
        float pillGap = 3.5f;
        float navRowGap = 3.5f;
        int totalPills = CAT_NAMES.length + 1;
        int row0Count = 5;
        int row1Count = totalPills - row0Count;

        for (int i = 0; i < totalPills; i++) {
            int row = (i < row0Count) ? 0 : 1;
            int col = (row == 0) ? i : (i - row0Count);
            int rowCols = (row == 0) ? row0Count : row1Count;
            float pillW = (mainW - (rowCols - 1) * pillGap) / (float) rowCols;
            float pX = x + col * (pillW + pillGap);
            float pY = navY + row * (navH + navRowGap);

            if (mouseX >= pX && mouseX <= pX + pillW && mouseY >= pY && mouseY <= pY + navH) {
                if (i == CAT_NAMES.length) {
                    mod.clearAll();
                    GuiSoundManager.playClick();
                    return true;
                }
                if (i < CAT_NAMES.length) {
                    activeFilter = i;
                    targetScroll = 0.0f;
                    scrollOffset = 0.0f;
                    GuiSoundManager.playClick();
                    return true;
                }
            }
        }

        float gridTop = navY + navH * 2.0f + navRowGap + 5.0f;
        if (mouseY < gridTop) return false;

        float currentCatY = gridTop + 4.0f - scrollOffset;
        List<String> activeCats = new ArrayList<>();
        if (activeFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("watches");
            activeCats.add("swords");
        } else {
            activeCats.add(CAT_KEYS[activeFilter]);
        }

        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (mainW - (cols - 1) * colGap) / cols;
        float cardH = 108.0f;

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            boolean isWatches = "watches".equals(catKey);
            List<CosmeticsManager.Asset> assetsList = (isSwords || isWatches) ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SwordStyle> swordsList = isSwords ? getFilteredSwords() : Collections.emptyList();
            List<WatchStyle> watchesList = isWatches ? getFilteredWatches() : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : (isWatches ? watchesList.size() : assetsList.size());

            float catHeaderH = 20.0f;

            String selectedId = isSwords ? mod.selectedSword() : mod.selected(catKey);
            boolean hasEquipped = selectedId != null && !selectedId.isEmpty() && !"None".equalsIgnoreCase(selectedId);
            if (hasEquipped) {
                float badgeW = 46.0f;
                float badgeH = 14.0f;
                float badgeX = x + mainW - badgeW;
                float badgeY = currentCatY + 2.0f;
                if (mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + badgeH) {
                    mod.clearSlot(catKey);
                    GuiSoundManager.playClick();
                    return true;
                }
            }

            currentCatY += catHeaderH;

            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = x + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                    if (isSwords) {
                        SwordStyle sword = swordsList.get(i);
                        if (sword == mod.getSelectedSwordStyle()) {
                            mod.clearSlot("swords");
                        } else {
                            mod.selectSword(sword.getName(), sword.getName());
                        }
                    } else if (isWatches) {
                        WatchStyle watch = watchesList.get(i);
                        if (watch == mod.getSelectedWatchStyle()) {
                            mod.clearSlot("watches");
                        } else {
                            mod.select("watches", watch.getName());
                        }
                    } else {
                        CosmeticsManager.Asset asset = assetsList.get(i);
                        if (asset.id().equalsIgnoreCase(mod.selected(catKey))) {
                            mod.clearSlot(catKey);
                        } else {
                            mod.select(catKey, asset.id());
                        }
                    }
                    GuiSoundManager.playClick();
                    return true;
                }
            }

            int rows = Math.max(1, (itemCount + cols - 1) / cols);
            currentCatY += rows * (cardH + rowGap) + 12.0f;
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
        targetScroll -= (float) (amount * 34.0f);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);
        return true;
    }

    private static float getAnim(String key, boolean state) {
        float current = toggleAnims.getOrDefault(key, state ? 1.0f : 0.0f);
        float target = state ? 1.0f : 0.0f;
        current += (target - current) * 0.22f;
        toggleAnims.put(key, current);
        return current;
    }

    private static List<WatchStyle> getFilteredWatches() {
        List<WatchStyle> list = new ArrayList<>();
        for (WatchStyle style : WatchStyle.values()) {
            if (style == WatchStyle.NONE) continue;
            if (searchQuery.isEmpty() || style.getName().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                list.add(style);
            }
        }
        return list;
    }

    private static void renderWatchCardPreview(DrawContext context, WatchStyle watch, float cardX, float cardY, float cardW, boolean isSelected) {
        float cx = cardX + cardW * 0.5f;
        float cy = cardY + 44.0f;
        float strapW = 16.0f;
        float strapH = 58.0f;
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

        float dialSize = 42.0f;
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
        float hrLen = 8.5f;
        float minLen = 12.0f;
        float secLen = 14.0f;
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(hrAngle) * hrLen, cy + (float) Math.sin(hrAngle) * hrLen, 0xFFFFFFFF, 1.4f);
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(minAngle) * minLen, cy + (float) Math.sin(minAngle) * minLen, 0xFFFFFFFF, 1.0f);
        Render2D.drawLine(context, cx, cy, cx + (float) Math.cos(secAngle) * secLen, cy + (float) Math.sin(secAngle) * secLen, 0xFFFF4444, 0.8f);
    }

    private static List<SwordStyle> getFilteredSwords() {
        List<SwordStyle> list = new ArrayList<>();
        for (SwordStyle style : SwordStyle.values()) {
            if (style == SwordStyle.NONE) continue;
            if (searchQuery.isEmpty() || style.getName().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                list.add(style);
            }
        }
        return list;
    }

    private static void renderSwordCardPreview(DrawContext context, SwordStyle sword, float cardX, float cardY, float cardW) {
        float cx = cardX + cardW * 0.5f;
        float cy = cardY + 44.0f;
        int glowRgb = sword.getGlowColor() != 0 ? sword.getGlowColor() : sword.getBladeColor();
        int glowArgb = 0x38000000 | (glowRgb & 0x00FFFFFF);
        Render2D.drawCircleShadow(context, cx, cy, 14.0f, 16.0f, glowArgb);
        if (sword.getIcon() != null) {
            float iconSize = 54.0f;
            float iconX = cx - iconSize * 0.5f;
            float iconY = cy - iconSize * 0.5f;
            IconRenderer.drawTextureScaled(context, sword.getIcon(), iconX, iconY, iconSize, iconSize, 64, 64, 0xFFFFFFFF);
        }
    }
}
