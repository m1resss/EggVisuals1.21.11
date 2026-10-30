package egg.client.ui.section;

import egg.client.render.IconRenderer;

import egg.client.cosmetic.SkycoreCosmeticsManager;
import egg.client.module.ModuleManager;
import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class CosmeticsPanel {
    private static String searchQuery = "";

    private static final String[] CAT_NAMES = new String[] { "Все", "Шляпы", "Крылья", "Рюкзаки", "Маски", "Питомцы", "Мечи" };
    private static final String[] CAT_KEYS  = new String[] { "all", "hats", "wings", "backpacks", "masks", "pets", "swords" };

    private static int activeFilter = 0;
    private static float scrollOffset = 0.0f;
    private static float targetScroll = 0.0f;
    private static float maxScroll = 0.0f;

    // 3D Player Preview state
    private static float playerYaw = 0.0f;
    private static float playerPitch = 0.0f;
    private static float playerScale = 1.0f;
    private static boolean isDraggingPlayer = false;
    private static float lastDragMouseX = 0.0f;
    private static float lastDragMouseY = 0.0f;

    // Animation progress for cards and toggles
    private static final Map<String, Float> toggleAnims = new HashMap<>();

    public static void render(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CosmeticsModule mod = ModuleManager.getInstance().getModule(CosmeticsModule.class);
        if (mod == null) return;

        SkycoreCosmeticsManager mgr = SkycoreCosmeticsManager.getInstance();
        mgr.ensureIndex();

        scrollOffset += (targetScroll - scrollOffset) * 0.25f;

        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;
        float rightPaneX = x + mainW + 10.0f;
        float rightPaneY = y;
        float rightPaneH = h;

        // ----------------------------------------------------
        // 1. LEFT CATALOG PANEL
        // ----------------------------------------------------
        context.enableScissor((int) x, (int) y, (int) (x + mainW), (int) (y + h));

        float curY = y - scrollOffset;
        float heroH = 46.0f;
        float heroX = x;
        float heroW = mainW;

        // A. HERO STUDIO BANNER CARD
        Render2D.drawRoundedRect(context, heroX, curY, heroW, heroH, 6.0f, Theme.CARD_BG);
        Render2D.drawRoundedOutline(context, heroX, curY, heroW, heroH, 6.0f, 0.6f, Theme.CARD_BORDER);

        // Subtle primary glow gradient inside banner
        int glowL = ColorUtil.withAlpha(Theme.ACCENT, Math.round(35 * alpha));
        int glowR = ColorUtil.withAlpha(Theme.ACCENT, Math.round(10 * alpha));
        Render2D.drawGradientRect(context, heroX, curY, heroW, heroH, 6.0f, glowL, glowR, glowR, glowL);

        // Icon badge
        float icSz = 26.0f;
        float icX = heroX + 10.0f;
        float icY = curY + (heroH - icSz) * 0.5f;
        Render2D.drawRoundedRect(context, icX, icY, icSz, icSz, 5.0f, ColorUtil.withAlpha(Theme.ACCENT, Math.round(55 * alpha)));
        Render2D.drawRoundedOutline(context, icX, icY, icSz, icSz, 5.0f, 0.6f, ColorUtil.withAlpha(Theme.ACCENT, Math.round(180 * alpha)));
        ClientFont.drawCentered(context, "C", icX + icSz * 0.5f, icY + 6.0f, ClientFont.SF_BOLD, Theme.ACCENT);

        // Master toggle slider & "Снять все"
        float masterTogW = 24.0f, masterTogH = 13.0f;
        float masterTogX = heroX + heroW - masterTogW - 10.0f;
        float masterTogY = curY + (heroH - masterTogH) * 0.5f;

        float clrW = 54.0f, clrH = 15.0f;
        float clrX = masterTogX - clrW - 8.0f;
        float clrY = curY + (heroH - clrH) * 0.5f;

        // Banner titles
        float textX = icX + icSz + 10.0f;
        ClientFont.draw(context, "Cosmetics Studio", textX, curY + 9.5f, ClientFont.SF_BOLD, 0xFFFFFFFF);
        ClientFont.draw(context, "Кастомные 3D крылья, рюкзаки, шляпы, питомцы и мечи", textX, curY + 23.5f, ClientFont.SF_REGULAR, 0xFFA0A8B8);

        // Master toggle switch
        boolean isModEnabled = mod.isEnabled();
        float mAnim = getAnim("__master__", isModEnabled);
        int pillBg = ColorUtil.lerp(0xFF232838, Theme.ACCENT, mAnim);
        Render2D.drawRoundedRect(context, masterTogX, masterTogY, masterTogW, masterTogH, 6.5f, pillBg);
        Render2D.drawRoundedOutline(context, masterTogX, masterTogY, masterTogW, masterTogH, 6.5f, 0.5f, Theme.CARD_BORDER);

        float mDotSz = 9.0f;
        float mDotX = masterTogX + 2.0f + mAnim * 11.0f;
        float mDotY = masterTogY + 2.0f;
        Render2D.drawRoundedRect(context, mDotX, mDotY, mDotSz, mDotSz, 4.5f, 0xFFFFFFFF);

        // "Снять все" button
        boolean hovClr = mouseX >= clrX && mouseX <= clrX + clrW && mouseY >= clrY && mouseY <= clrY + clrH;
        Render2D.drawRoundedRect(context, clrX, clrY, clrW, clrH, 4.0f, hovClr ? 0xDDFF4444 : 0x22FFFFFF);
        Render2D.drawRoundedOutline(context, clrX, clrY, clrW, clrH, 4.0f, 0.5f, hovClr ? 0xFFFF6B6B : Theme.CARD_BORDER);
        ClientFont.drawCentered(context, "Снять все", clrX + clrW * 0.5f, clrY + 3.5f, ClientFont.SF_REGULAR, hovClr ? 0xFFFFFFFF : 0xFFA0A8B8);

        // B. CATEGORY FILTER PILLS
        float navY = curY + heroH + 8.0f;
        float pillGap = 4.0f;
        float pillW = (heroW - (CAT_NAMES.length - 1) * pillGap) / CAT_NAMES.length;
        float navH = 19.0f;

        for (int i = 0; i < CAT_NAMES.length; i++) {
            float pX = heroX + i * (pillW + pillGap);
            boolean isSel = (activeFilter == i);
            boolean hov = mouseX >= pX && mouseX <= pX + pillW && mouseY >= navY && mouseY <= navY + navH;

            int pBg = isSel ? Theme.ACCENT : (hov ? 0x28FFFFFF : 0x16FFFFFF);
            Render2D.drawRoundedRect(context, pX, navY, pillW, navH, 4.0f, pBg);
            if (isSel) {
                Render2D.drawRoundedOutline(context, pX, navY, pillW, navH, 4.0f, 0.6f, Theme.ACCENT);
            }
            int txtCol = isSel ? 0xFFFFFFFF : (hov ? 0xFFE0E6F0 : 0xFFA0A8B8);
            ClientFont.drawCentered(context, CAT_NAMES[i], pX + pillW * 0.5f, navY + 4.5f, ClientFont.SF_MEDIUM, txtCol);
        }

        // C. COSMETICS CARDS GRID
        float currentCatY = navY + navH + 10.0f;
        List<String> activeCats = new ArrayList<>();
        if (activeFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("swords");
        } else {
            activeCats.add(CAT_KEYS[activeFilter]);
        }

        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (heroW - (cols - 1) * colGap) / cols;
        float cardH = 132.0f;
        float previewSize = 72.0f;

        float measuredTotalH = (currentCatY - (y - scrollOffset));

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            String catTitle = switch (catKey) {
                case "hats" -> "Шляпы (Hats)";
                case "wings" -> "Крылья (Wings)";
                case "backpacks" -> "Рюкзаки (Backpacks)";
                case "masks" -> "Маски (Masks)";
                case "pets" -> "Питомцы (Pets)";
                case "swords" -> "Мечи (Swords)";
                default -> catKey;
            };

            List<SkycoreCosmeticsManager.Asset> assetsList = isSwords ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SkycoreCosmeticsManager.SwordEntry> swordsList = isSwords ? mgr.getSwords(searchQuery) : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : assetsList.size();

            // Category Header
            float catHeaderH = 20.0f;
            ClientFont.draw(context, catTitle, heroX + 2.0f, currentCatY + 3.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);

            // "Снять" (clear category) button if something in category is equipped
            String selectedId = isSwords ? mod.selectedSword() : mod.selected(catKey);
            boolean hasEquipped = selectedId != null && !selectedId.isEmpty() && !"None".equalsIgnoreCase(selectedId);
            if (hasEquipped) {
                float badgeW = 44.0f;
                float badgeX = heroX + heroW - badgeW;
                float badgeY = currentCatY + 1.0f;
                boolean hovRst = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + 14.0f;
                Render2D.drawRoundedRect(context, badgeX, badgeY, badgeW, 14.0f, 3.0f, hovRst ? 0xDDFF4444 : 0x22FFFFFF);
                Render2D.drawRoundedOutline(context, badgeX, badgeY, badgeW, 14.0f, 3.0f, 0.5f, hovRst ? 0xFFFF6B6B : Theme.CARD_BORDER);
                ClientFont.drawCentered(context, "Снять", badgeX + badgeW * 0.5f, badgeY + 3.0f, ClientFont.SF_REGULAR, hovRst ? 0xFFFFFFFF : 0xFFA0A8B8);
            }

            currentCatY += catHeaderH;

            int rows = Math.max(1, (itemCount + cols - 1) / cols);

            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = heroX + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                if (cardY + cardH < y - 5.0f || cardY > y + h + 5.0f) {
                    continue;
                }

                SkycoreCosmeticsManager.Asset asset = isSwords ? null : assetsList.get(i);
                SkycoreCosmeticsManager.SwordEntry sword = isSwords ? swordsList.get(i) : null;
                String displayName = isSwords ? sword.name() : asset.displayName();

                boolean isSelected = isSwords
                        ? (sword.name().equalsIgnoreCase(mod.selectedSword()))
                        : (asset.id().equalsIgnoreCase(mod.selected(catKey)));

                boolean isHovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

                int cBg = isSelected ? 0x264C8DFF : (isHovered ? 0x1AFFFFFF : Theme.CARD_BG);
                int cBrd = isSelected ? Theme.ACCENT : (isHovered ? 0x55FFFFFF : Theme.CARD_BORDER);

                Render2D.drawRoundedRect(context, cardX, cardY, cardW, cardH, 6.0f, cBg);
                Render2D.drawRoundedOutline(context, cardX, cardY, cardW, cardH, 6.0f, 0.6f, cBrd);

                // 3D Preview inside card
                float pX = cardX + (cardW - previewSize) * 0.5f;
                float pY = cardY + 8.0f;

                if (!isSwords && asset != null) {
                    mgr.renderGuiPreview(context, asset.id(), pX, pY, previewSize, alpha);
                } else if (isSwords && sword != null) {
                    IconRenderer.drawTextureScaled(context, sword.texture(), pX, pY, previewSize, previewSize, 32, 32, 0xFFFFFFFF);
                }

                // Divider line
                Render2D.drawRect(context, cardX + 6.0f, cardY + 94.0f, cardW - 12.0f, 1.0f, 0x15FFFFFF);

                // Cosmetic title
                String title = displayName;
                float maxTitleW = cardW - 36.0f;
                if (ClientFont.getWidth(title, ClientFont.SF_MEDIUM) > maxTitleW) {
                    while (title.length() > 3 && ClientFont.getWidth(title + "..", ClientFont.SF_MEDIUM) > maxTitleW) {
                        title = title.substring(0, title.length() - 1);
                    }
                    title += "..";
                }
                ClientFont.draw(context, title, cardX + 7.0f, cardY + 106.0f, ClientFont.SF_MEDIUM, isSelected ? Theme.ACCENT : 0xFFFFFFFF);

                // Toggle switch pill
                float swW = 20.0f, swH = 11.0f;
                float swX = cardX + cardW - swW - 7.0f;
                float swY = cardY + 105.0f;

                String animKey = isSwords ? "sw_" + sword.name() : "cs_" + asset.id();
                float swAnim = getAnim(animKey, isSelected);
                int swBg = ColorUtil.lerp(0xFF232838, 0xFF4CD137, swAnim);
                Render2D.drawRoundedRect(context, swX, swY, swW, swH, 5.5f, swBg);

                float swDotSz = 7.0f;
                float swDotX = swX + 2.0f + swAnim * 9.0f;
                float swDotY = swY + 2.0f;
                Render2D.drawRoundedRect(context, swDotX, swDotY, swDotSz, swDotSz, 3.5f, 0xFFFFFFFF);
            }

            currentCatY += rows * (cardH + rowGap) + 12.0f;
        }

        measuredTotalH = currentCatY - (y - scrollOffset);
        maxScroll = Math.max(0.0f, measuredTotalH - h);

        context.disableScissor();

        // ----------------------------------------------------
        // 2. RIGHT PANE: 3D INTERACTIVE PLAYER PREVIEW
        // ----------------------------------------------------
        Render2D.drawRoundedRect(context, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, Theme.CARD_BG);
        Render2D.drawRoundedOutline(context, rightPaneX, rightPaneY, rightPaneW, rightPaneH, 6.0f, 0.6f, Theme.CARD_BORDER);

        // Header inside right pane
        ClientFont.draw(context, "Предпросмотр", rightPaneX + 10.0f, rightPaneY + 10.0f, ClientFont.SF_BOLD, 0xFFFFFFFF);
        ClientFont.draw(context, "3D модель игрока", rightPaneX + 10.0f, rightPaneY + 22.0f, ClientFont.SF_REGULAR, 0xFFA0A8B8);

        // Reset button
        float rstW = 38.0f, rstH = 15.0f;
        float rstX = rightPaneX + rightPaneW - rstW - 8.0f;
        float rstY = rightPaneY + 9.0f;
        boolean hovReset = mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH;
        Render2D.drawRoundedRect(context, rstX, rstY, rstW, rstH, 3.5f, hovReset ? Theme.ACCENT : 0x22FFFFFF);
        Render2D.drawRoundedOutline(context, rstX, rstY, rstW, rstH, 3.5f, 0.5f, hovReset ? Theme.ACCENT : Theme.CARD_BORDER);
        ClientFont.drawCentered(context, "Сброс", rstX + rstW * 0.5f, rstY + 3.0f, ClientFont.SF_REGULAR, hovReset ? 0xFFFFFFFF : 0xFFA0A8B8);

        // Divider
        Render2D.drawRect(context, rightPaneX + 8.0f, rightPaneY + 32.0f, rightPaneW - 16.0f, 1.0f, 0x15FFFFFF);

        // Render 3D player
        if (mc.player != null) {
            float previewBoxX = rightPaneX + 4.0f;
            float previewBoxY = rightPaneY + 34.0f;
            float previewBoxW = rightPaneW - 8.0f;
            float previewBoxH = rightPaneH - 58.0f;

            context.enableScissor((int) previewBoxX, (int) previewBoxY, (int) (previewBoxX + previewBoxW), (int) (previewBoxY + previewBoxH));

            float centerX = previewBoxX + previewBoxW * 0.5f;
            float centerY = previewBoxY + previewBoxH * 0.82f;
            int baseSize = (int) (previewBoxH * 0.44f * playerScale);

            render3DPlayer(context, centerX, centerY, baseSize, playerYaw, playerPitch, mc.player);

            context.disableScissor();
        }

        // Hints at the bottom of the preview panel
        Render2D.drawRect(context, rightPaneX + 8.0f, rightPaneY + rightPaneH - 22.0f, rightPaneW - 16.0f, 1.0f, 0x15FFFFFF);
        ClientFont.drawCentered(context, "ЛКМ: Вращение • Колесико: Зум", rightPaneX + rightPaneW * 0.5f, rightPaneY + rightPaneH - 14.0f, ClientFont.SF_REGULAR, 0xFF8A92A2);
    }

    private static void render3DPlayer(DrawContext context, float centerX, float centerY, int size, float yaw, float pitch, LivingEntity entity) {
        if (entity == null) return;
        int x1 = (int) (centerX - size * 0.8f);
        int y1 = (int) (centerY - size * 1.8f);
        int x2 = (int) (centerX + size * 0.8f);
        int y2 = (int) (centerY + size * 0.3f);
        net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(context, x1, y1, x2, y2, size, -yaw, -pitch, 1.0f, entity);
    }

    public static boolean mouseClicked(float x, float y, float w, float h, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        CosmeticsModule mod = ModuleManager.getInstance().getModule(CosmeticsModule.class);
        if (mod == null) return false;

        SkycoreCosmeticsManager mgr = SkycoreCosmeticsManager.getInstance();
        mgr.ensureIndex();

        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;
        float rightPaneX = x + mainW + 10.0f;
        float rightPaneY = y;
        float rightPaneH = h;

        // Check clicks on right pane (Preview)
        if (mouseX >= rightPaneX && mouseX <= rightPaneX + rightPaneW && mouseY >= rightPaneY && mouseY <= rightPaneY + rightPaneH) {
            // Reset button
            float rstW = 38.0f, rstH = 15.0f;
            float rstX = rightPaneX + rightPaneW - rstW - 8.0f;
            float rstY = rightPaneY + 9.0f;
            if (mouseX >= rstX && mouseX <= rstX + rstW && mouseY >= rstY && mouseY <= rstY + rstH) {
                playerYaw = 0.0f;
                playerPitch = 0.0f;
                playerScale = 1.0f;
                GuiSoundManager.playClick();
                return true;
            }

            // Start dragging player model
            isDraggingPlayer = true;
            lastDragMouseX = (float) mouseX;
            lastDragMouseY = (float) mouseY;
            return true;
        }

        // Check clicks on left catalog
        if (mouseX < x || mouseX > x + mainW || mouseY < y || mouseY > y + h) {
            return false;
        }

        float curY = y - scrollOffset;
        float heroH = 46.0f;
        float heroX = x;
        float heroW = mainW;

        // Master toggle switch click
        float masterTogW = 24.0f, masterTogH = 13.0f;
        float masterTogX = heroX + heroW - masterTogW - 10.0f;
        float masterTogY = curY + (heroH - masterTogH) * 0.5f;
        if (mouseX >= masterTogX && mouseX <= masterTogX + masterTogW && mouseY >= masterTogY && mouseY <= masterTogY + masterTogH) {
            mod.setEnabled(!mod.isEnabled());
            GuiSoundManager.playClick();
            return true;
        }

        // "Снять все" click
        float clrW = 54.0f, clrH = 15.0f;
        float clrX = masterTogX - clrW - 8.0f;
        float clrY = curY + (heroH - clrH) * 0.5f;
        if (mouseX >= clrX && mouseX <= clrX + clrW && mouseY >= clrY && mouseY <= clrY + clrH) {
            mod.clearAll();
            GuiSoundManager.playClick();
            return true;
        }

        // Filter pills click
        float navY = curY + heroH + 8.0f;
        float pillGap = 4.0f;
        float pillW = (heroW - (CAT_NAMES.length - 1) * pillGap) / CAT_NAMES.length;
        float navH = 19.0f;

        if (mouseY >= navY && mouseY <= navY + navH) {
            for (int i = 0; i < CAT_NAMES.length; i++) {
                float pX = heroX + i * (pillW + pillGap);
                if (mouseX >= pX && mouseX <= pX + pillW) {
                    activeFilter = i;
                    targetScroll = 0.0f;
                    scrollOffset = 0.0f;
                    GuiSoundManager.playClick();
                    return true;
                }
            }
        }

        // Cards and Category Reset clicks
        float currentCatY = navY + navH + 10.0f;
        List<String> activeCats = new ArrayList<>();
        if (activeFilter == 0) {
            activeCats.add("hats");
            activeCats.add("wings");
            activeCats.add("backpacks");
            activeCats.add("masks");
            activeCats.add("pets");
            activeCats.add("swords");
        } else {
            activeCats.add(CAT_KEYS[activeFilter]);
        }

        int cols = 3;
        float colGap = 6.0f;
        float rowGap = 7.0f;
        float cardW = (heroW - (cols - 1) * colGap) / cols;
        float cardH = 132.0f;

        for (String catKey : activeCats) {
            boolean isSwords = "swords".equals(catKey);
            List<SkycoreCosmeticsManager.Asset> assetsList = isSwords ? Collections.emptyList() : mgr.getCategoryAssets(catKey, searchQuery);
            List<SkycoreCosmeticsManager.SwordEntry> swordsList = isSwords ? mgr.getSwords(searchQuery) : Collections.emptyList();
            int itemCount = isSwords ? swordsList.size() : assetsList.size();

            // Check "Снять" category click
            float badgeW = 44.0f;
            float badgeX = heroX + heroW - badgeW;
            float badgeY = currentCatY + 1.0f;
            if (mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + 14.0f) {
                if (isSwords) {
                    mod.selectSword("", "");
                } else {
                    mod.select(catKey, "");
                }
                GuiSoundManager.playClick();
                return true;
            }

            currentCatY += 20.0f;

            int rows = Math.max(1, (itemCount + cols - 1) / cols);

            for (int i = 0; i < itemCount; i++) {
                int row = i / cols;
                int col = i % cols;
                float cardX = heroX + col * (cardW + colGap);
                float cardY = currentCatY + row * (cardH + rowGap);

                if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                    if (isSwords) {
                        SkycoreCosmeticsManager.SwordEntry sword = swordsList.get(i);
                        boolean isSelected = sword.name().equalsIgnoreCase(mod.selectedSword());
                        if (isSelected) {
                            mod.selectSword("", "");
                        } else {
                            mod.selectSword(sword.name(), sword.model());
                        }
                    } else {
                        SkycoreCosmeticsManager.Asset asset = assetsList.get(i);
                        boolean isSelected = asset.id().equalsIgnoreCase(mod.selected(catKey));
                        if (isSelected) {
                            mod.select(catKey, "");
                        } else {
                            mod.select(catKey, asset.id());
                        }
                    }
                    GuiSoundManager.playClick();
                    return true;
                }
            }

            currentCatY += rows * (cardH + rowGap) + 12.0f;
        }

        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (isDraggingPlayer) {
            float dx = (float) mouseX - lastDragMouseX;
            float dy = (float) mouseY - lastDragMouseY;
            playerYaw += dx * 1.5f;
            playerPitch = Math.clamp(playerPitch + dy * 1.5f, -65.0f, 65.0f);
            lastDragMouseX = (float) mouseX;
            lastDragMouseY = (float) mouseY;
            return true;
        }
        return false;
    }

    public static void mouseReleased() {
        isDraggingPlayer = false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount, float w) {
        float rightPaneW = 158.0f;
        float mainW = w - rightPaneW - 10.0f;

        // If cursor is over right preview pane -> Zoom player model
        if (mouseX >= rightPaneW) {
            playerScale = Math.clamp(playerScale + (float) amount * 0.12f, 0.5f, 2.8f);
            return true;
        }

        // If cursor is over catalog -> scroll catalog
        targetScroll -= (float) (amount * 34.0f);
        targetScroll = Math.clamp(targetScroll, 0.0f, maxScroll);
        return true;
    }

    private static float getAnim(String key, boolean target) {
        float current = toggleAnims.getOrDefault(key, target ? 1.0f : 0.0f);
        float dest = target ? 1.0f : 0.0f;
        current += (dest - current) * 0.28f;
        toggleAnims.put(key, current);
        return current;
    }
}
