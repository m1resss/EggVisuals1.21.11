package egg.client.render.hud;

import egg.client.module.ModuleManager;
import egg.client.module.render.ArmorHUD;
import egg.client.module.render.Cooldowns;
import egg.client.module.render.Interface;
import egg.client.module.render.PotionHUD;
import egg.client.module.render.TargetHUD;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.msdf.Fonts;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class HudSnapHelper {

    public record HudElementBounds(String id, String displayName, float x, float y, float w, float h) {}

    public static class SnapResult {
        public float snappedX;
        public float snappedY;

        public boolean isCenterSnapX = false;
        public boolean isCenterSnapY = false;

        public HudElementBounds snappedElemX = null;
        public HudElementBounds snappedElemY = null;

        public float guideX = -1.0f;
        public float guideXMinY = 0.0f;
        public float guideXMaxY = 0.0f;

        public float guideY = -1.0f;
        public float guideYMinX = 0.0f;
        public float guideYMaxX = 0.0f;

        public SnapResult(float x, float y) {
            this.snappedX = x;
            this.snappedY = y;
        }
    }

    public static List<HudElementBounds> getOtherElements(String currentId) {
        List<HudElementBounds> list = new ArrayList<>();

        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        if (iface != null && iface.isEnabled() && !"interface".equals(currentId) && iface.getLastW() > 10.0f) {
            list.add(new HudElementBounds("interface", "Интерфейс", iface.getLastX(), iface.getLastY(), iface.getLastW(), iface.getLastH()));
        }

        ArmorHUD armor = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armor != null && armor.isEnabled() && !"armor".equals(currentId) && armor.getLastW() > 10.0f) {
            list.add(new HudElementBounds("armor", "Броня", armor.getLastX(), armor.getLastY(), armor.getLastW(), armor.getLastH()));
        }

        PotionHUD potion = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potion != null && potion.isEnabled() && !"potion".equals(currentId) && potion.getLastW() > 10.0f) {
            list.add(new HudElementBounds("potion", "Зелья", potion.getLastX(), potion.getLastY(), potion.getLastW(), potion.getLastH()));
        }

        TargetHUD target = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (target != null && target.isEnabled() && !"target".equals(currentId) && target.getMode().is("Экран") && target.getLastW() > 10.0f) {
            list.add(new HudElementBounds("target", "Target HUD", target.getLastX(), target.getLastY(), target.getLastW(), target.getLastH()));
        }

        Cooldowns cd = ModuleManager.getInstance().getModule(Cooldowns.class);
        if (cd != null && cd.isEnabled() && !"cooldowns".equals(currentId) && cd.getLastW() > 10.0f) {
            list.add(new HudElementBounds("cooldowns", "Кулдауны", cd.getLastX(), cd.getLastY(), cd.getLastW(), cd.getLastH()));
        }

        return list;
    }

    public static SnapResult calculateSnap(String currentId, float proposedX, float proposedY, float w, float h, float screenW, float screenH) {
        SnapResult res = new SnapResult(proposedX, proposedY);
        List<HudElementBounds> others = getOtherElements(currentId);

        float snapThreshold = 7.0f;
        float gap = 6.0f;

        // Snapping по X
        float bestX = proposedX;
        float minDiffX = snapThreshold;
        boolean centerSnapX = false;
        HudElementBounds snapTargetX = null;
        float bestGuideX = -1.0f;
        float bestGuideXMinY = 0.0f;
        float bestGuideXMaxY = screenH;

        // 1. Экранный центр X
        float midX = (screenW - w) / 2.0f;
        float diffCenterX = Math.abs(proposedX - midX);
        if (diffCenterX < minDiffX) {
            minDiffX = diffCenterX;
            bestX = midX;
            centerSnapX = true;
            bestGuideX = screenW / 2.0f;
            bestGuideXMinY = 0.0f;
            bestGuideXMaxY = screenH;
        }

        // 2. Выравнивание по другим элементам
        for (HudElementBounds target : others) {
            float tMinY = Math.min(proposedY, target.y()) - 4.0f;
            float tMaxY = Math.max(proposedY + h, target.y() + target.h()) + 4.0f;

            // Выравнивание по левому краю
            float dLeft = Math.abs(proposedX - target.x());
            if (dLeft < minDiffX) {
                minDiffX = dLeft;
                bestX = target.x();
                centerSnapX = false;
                snapTargetX = target;
                bestGuideX = target.x();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            // Выравнивание по правому краю
            float dRight = Math.abs(proposedX - (target.x() + target.w() - w));
            if (dRight < minDiffX) {
                minDiffX = dRight;
                bestX = target.x() + target.w() - w;
                centerSnapX = false;
                snapTargetX = target;
                bestGuideX = target.x() + target.w();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            // Выравнивание по центру
            float dCenter = Math.abs(proposedX - (target.x() + (target.w() - w) / 2.0f));
            if (dCenter < minDiffX) {
                minDiffX = dCenter;
                bestX = target.x() + (target.w() - w) / 2.0f;
                centerSnapX = false;
                snapTargetX = target;
                bestGuideX = target.x() + target.w() / 2.0f;
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            // Примагничивание справа от элемента (с зазором)
            float dAdjRight = Math.abs(proposedX - (target.x() + target.w() + gap));
            if (dAdjRight < minDiffX) {
                minDiffX = dAdjRight;
                bestX = target.x() + target.w() + gap;
                centerSnapX = false;
                snapTargetX = target;
                bestGuideX = target.x() + target.w();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            // Примагничивание слева от элемента (с зазором)
            float dAdjLeft = Math.abs(proposedX - (target.x() - w - gap));
            if (dAdjLeft < minDiffX) {
                minDiffX = dAdjLeft;
                bestX = target.x() - w - gap;
                centerSnapX = false;
                snapTargetX = target;
                bestGuideX = target.x();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }
        }

        // Если не примагнитились к элементам или центру — проверяем сетку 25px
        if (minDiffX >= snapThreshold) {
            float nearestGridX = Math.round(proposedX / 25.0f) * 25.0f;
            if (Math.abs(proposedX - nearestGridX) < 5.0f) {
                bestX = nearestGridX;
            }
        }

        res.snappedX = bestX;
        res.isCenterSnapX = centerSnapX;
        res.snappedElemX = snapTargetX;
        res.guideX = bestGuideX;
        res.guideXMinY = bestGuideXMinY;
        res.guideXMaxY = bestGuideXMaxY;

        // Snapping по Y
        float bestY = proposedY;
        float minDiffY = snapThreshold;
        boolean centerSnapY = false;
        HudElementBounds snapTargetY = null;
        float bestGuideY = -1.0f;
        float bestGuideYMinX = 0.0f;
        float bestGuideYMaxX = screenW;

        // 1. Экранный центр Y
        float midY = (screenH - h) / 2.0f;
        float diffCenterY = Math.abs(proposedY - midY);
        if (diffCenterY < minDiffY) {
            minDiffY = diffCenterY;
            bestY = midY;
            centerSnapY = true;
            bestGuideY = screenH / 2.0f;
            bestGuideYMinX = 0.0f;
            bestGuideYMaxX = screenW;
        }

        // 2. Выравнивание по другим элементам
        for (HudElementBounds target : others) {
            float tMinX = Math.min(proposedX, target.x()) - 4.0f;
            float tMaxX = Math.max(proposedX + w, target.x() + target.w()) + 4.0f;

            // Выравнивание по верхнему краю
            float dTop = Math.abs(proposedY - target.y());
            if (dTop < minDiffY) {
                minDiffY = dTop;
                bestY = target.y();
                centerSnapY = false;
                snapTargetY = target;
                bestGuideY = target.y();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            // Выравнивание по нижнему краю
            float dBottom = Math.abs(proposedY - (target.y() + target.h() - h));
            if (dBottom < minDiffY) {
                minDiffY = dBottom;
                bestY = target.y() + target.h() - h;
                centerSnapY = false;
                snapTargetY = target;
                bestGuideY = target.y() + target.h();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            // Выравнивание по центру Y
            float dCenter = Math.abs(proposedY - (target.y() + (target.h() - h) / 2.0f));
            if (dCenter < minDiffY) {
                minDiffY = dCenter;
                bestY = target.y() + (target.h() - h) / 2.0f;
                centerSnapY = false;
                snapTargetY = target;
                bestGuideY = target.y() + target.h() / 2.0f;
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            // Примагничивание снизу (с зазором)
            float dAdjBelow = Math.abs(proposedY - (target.y() + target.h() + gap));
            if (dAdjBelow < minDiffY) {
                minDiffY = dAdjBelow;
                bestY = target.y() + target.h() + gap;
                centerSnapY = false;
                snapTargetY = target;
                bestGuideY = target.y() + target.h();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            // Примагничивание сверху (с зазором)
            float dAdjAbove = Math.abs(proposedY - (target.y() - h - gap));
            if (dAdjAbove < minDiffY) {
                minDiffY = dAdjAbove;
                bestY = target.y() - h - gap;
                centerSnapY = false;
                snapTargetY = target;
                bestGuideY = target.y();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }
        }

        // Если не примагнитились к элементам или центру — сетка 25px
        if (minDiffY >= snapThreshold) {
            float nearestGridY = Math.round(proposedY / 25.0f) * 25.0f;
            if (Math.abs(proposedY - nearestGridY) < 5.0f) {
                bestY = nearestGridY;
            }
        }

        res.snappedY = bestY;
        res.isCenterSnapY = centerSnapY;
        res.snappedElemY = snapTargetY;
        res.guideY = bestGuideY;
        res.guideYMinX = bestGuideYMinX;
        res.guideYMaxX = bestGuideYMaxX;

        return res;
    }

    public static void renderGridAndGuides(DrawContext context, SnapResult snap, float screenW, float screenH,
                                           HudElementBounds current, String label, int scalePercent, float radius) {
        // 1. Отрисовка базовой 25px сетки
        int gridStep = 25;
        int gridColor = ColorUtil.rgba(255, 255, 255, 12);
        for (int gx = gridStep; gx < screenW; gx += gridStep) {
            Draw2DProcessor.INSTANCE.drawRect(null, gx, 0, 1.0f, screenH, 0.0f, gridColor);
        }
        for (int gy = gridStep; gy < screenH; gy += gridStep) {
            Draw2DProcessor.INSTANCE.drawRect(null, 0, gy, screenW, 1.0f, 0.0f, gridColor);
        }

        // 2. Отрисовка экранных осей центра
        float midX = screenW / 2.0f;
        float midY = screenH / 2.0f;
        int centerColor = (snap != null && snap.isCenterSnapX)
                ? Theme.getAccentHover()
                : ColorUtil.rgba(168, 85, 247, 50);
        Draw2DProcessor.INSTANCE.drawRect(null, midX, 0, 1.0f, screenH, 0.0f, centerColor);
        if (snap != null && snap.isCenterSnapX) {
            Draw2DProcessor.INSTANCE.drawGlow(null, midX - 1.0f, 0, 2.0f, screenH, 0.0f, Theme.getAccent(), 4.0f);
        }

        int centerColorY = (snap != null && snap.isCenterSnapY)
                ? Theme.getAccentHover()
                : ColorUtil.rgba(168, 85, 247, 50);
        Draw2DProcessor.INSTANCE.drawRect(null, 0, midY, screenW, 1.0f, 0.0f, centerColorY);
        if (snap != null && snap.isCenterSnapY) {
            Draw2DProcessor.INSTANCE.drawGlow(null, 0, midY - 1.0f, screenW, 2.0f, 0.0f, Theme.getAccent(), 4.0f);
        }

        // 3. Подсветка целевых элементов, с которыми выровнены
        if (snap != null) {
            if (snap.snappedElemX != null) {
                HudElementBounds target = snap.snappedElemX;
                Draw2DProcessor.INSTANCE.drawOutline(null, target.x() - 2.0f, target.y() - 2.0f, target.w() + 4.0f, target.h() + 4.0f, 6.0f, 1.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.7f));
                Draw2DProcessor.INSTANCE.drawGlow(null, target.x() - 2.0f, target.y() - 2.0f, target.w() + 4.0f, target.h() + 4.0f, 6.0f, Theme.getAccent(), 6.0f);

                // Бейдж элемента
                float bw = Fonts.b.a(target.displayName(), 9.0f) + 8.0f;
                float bx = target.x() + (target.w() - bw) / 2.0f;
                float by = Math.max(4.0f, target.y() - 15.0f);
                Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 12.0f, 3.0f, ColorUtil.rgba(14, 15, 22, 220));
                Fonts.b.a(null, target.displayName(), bx + 4.0f, by + 2.0f, 9.0f, Theme.getAccentHover());
            }

            if (snap.snappedElemY != null && snap.snappedElemY != snap.snappedElemX) {
                HudElementBounds target = snap.snappedElemY;
                Draw2DProcessor.INSTANCE.drawOutline(null, target.x() - 2.0f, target.y() - 2.0f, target.w() + 4.0f, target.h() + 4.0f, 6.0f, 1.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.7f));
                Draw2DProcessor.INSTANCE.drawGlow(null, target.x() - 2.0f, target.y() - 2.0f, target.w() + 4.0f, target.h() + 4.0f, 6.0f, Theme.getAccent(), 6.0f);

                float bw = Fonts.b.a(target.displayName(), 9.0f) + 8.0f;
                float bx = target.x() + (target.w() - bw) / 2.0f;
                float by = Math.max(4.0f, target.y() - 15.0f);
                Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 12.0f, 3.0f, ColorUtil.rgba(14, 15, 22, 220));
                Fonts.b.a(null, target.displayName(), bx + 4.0f, by + 2.0f, 9.0f, Theme.getAccentHover());
            }

            // Отрисовка светящихся направляющих линий (Smart Guides)
            if (snap.guideX > 0.0f && !snap.isCenterSnapX) {
                float gx = snap.guideX;
                float gy1 = snap.guideXMinY;
                float gy2 = snap.guideXMaxY;
                Draw2DProcessor.INSTANCE.drawGlow(null, gx - 1.0f, gy1, 2.0f, gy2 - gy1, 0.0f, Theme.getAccent(), 5.0f);
                Draw2DProcessor.INSTANCE.drawRect(null, gx, gy1, 1.0f, gy2 - gy1, 0.0f, Theme.getAccentHover());
            }

            if (snap.guideY > 0.0f && !snap.isCenterSnapY) {
                float gy = snap.guideY;
                float gx1 = snap.guideYMinX;
                float gx2 = snap.guideYMaxX;
                Draw2DProcessor.INSTANCE.drawGlow(null, gx1, gy - 1.0f, gx2 - gx1, 2.0f, 0.0f, Theme.getAccent(), 5.0f);
                Draw2DProcessor.INSTANCE.drawRect(null, gx1, gy, gx2 - gx1, 1.0f, 0.0f, Theme.getAccentHover());
            }
        }

        // 4. Отрисовка контура и бейджа текущего перетаскиваемого элемента
        Draw2DProcessor.INSTANCE.drawOutline(null, current.x() - 2.0f, current.y() - 2.0f, current.w() + 4.0f, current.h() + 4.0f, radius, 1.2f, Theme.getAccent());
        Draw2DProcessor.INSTANCE.drawGlow(null, current.x() - 2.0f, current.y() - 2.0f, current.w() + 4.0f, current.h() + 4.0f, radius, Theme.getAccent(), 8.0f);

        String badgeText = label + " (" + scalePercent + "%)";
        float bw = Fonts.b.a(badgeText, 9.5f) + 10.0f;
        float bx = current.x() + (current.w() - bw) / 2.0f;
        float by = current.y() + current.h() + 6.0f;
        if (by + 16.0f > screenH) {
            by = current.y() - 18.0f;
        }
        Draw2DProcessor.INSTANCE.drawGlow(null, bx, by, bw, 14.0f, 4.0f, ColorUtil.rgba(0, 0, 0, 160), 6.0f);
        Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 14.0f, 4.0f, ColorUtil.rgba(14, 15, 22, 235));
        Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, bw, 14.0f, 4.0f, 1.0f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.5f));
        Fonts.b.a(null, badgeText, bx + 5.0f, by + 2.5f, 9.5f, Theme.getAccentHover());

        DrawBatcher.flushPending();
    }

    public static void renderHoverBadge(DrawContext context, float x, float y, float w, float h, float radius, String badgeText) {
        Draw2DProcessor.INSTANCE.drawOutline(null, x - 2.0f, y - 2.0f, w + 4.0f, h + 4.0f, radius, 1.0f, Theme.getAccent());

        float bw = Fonts.b.a(badgeText, 9.5f) + 10.0f;
        float bx = x + (w - bw) / 2.0f;
        float by = y + h + 5.0f;
        Draw2DProcessor.INSTANCE.drawRect(null, bx, by, bw, 13.0f, 3.5f, ColorUtil.rgba(14, 15, 22, 230));
        Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, bw, 13.0f, 3.5f, 1.0f, ColorUtil.rgba(255, 255, 255, 20));
        Fonts.b.a(null, badgeText, bx + 5.0f, by + 2.5f, 9.5f, Theme.getAccentHover());
        DrawBatcher.flushPending();
    }
}
