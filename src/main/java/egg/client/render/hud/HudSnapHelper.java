package egg.client.render.hud;

import egg.client.module.ModuleManager;
import egg.client.module.render.ArmorHUD;
import egg.client.module.render.Cooldowns;
import egg.client.module.render.Interface;
import egg.client.module.render.PotionHUD;
import egg.client.module.render.PearlPredict;
import egg.client.module.render.TargetHUD;
import egg.client.module.render.Keybinds;
import egg.client.module.render.EventTracker;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.msdf.Fonts;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public class HudSnapHelper {

    private static final int GRID_STEP = 25;
    private static final float SELECTION_PAD = 3.0f;

    public record HudElementBounds(String id, float x, float y, float w, float h) {}

    public static class SnapResult {
        public float snappedX;
        public float snappedY;

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
            list.add(new HudElementBounds("interface", iface.getLastX(), iface.getLastY(), iface.getLastW(), iface.getLastH()));
        }
        if (iface != null && iface.isIslandActive() && iface.getLastIslandW() > 10.0f) {
            list.add(new HudElementBounds("island", iface.getLastIslandX(), iface.getLastIslandY(), iface.getLastIslandW(), iface.getLastIslandH()));
        }

        ArmorHUD armor = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armor != null && armor.isEnabled() && !"armor".equals(currentId) && armor.getLastW() > 10.0f) {
            list.add(new HudElementBounds("armor", armor.getLastX(), armor.getLastY(), armor.getLastW(), armor.getLastH()));
        }

        PotionHUD potion = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potion != null && potion.isEnabled() && !"potion".equals(currentId) && potion.getLastW() > 10.0f) {
            list.add(new HudElementBounds("potion", potion.getLastX(), potion.getLastY(), potion.getLastW(), potion.getLastH()));
        }

        TargetHUD target = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (target != null && target.isEnabled() && !"target".equals(currentId) && target.getMode().is("Экран") && target.getLastW() > 10.0f) {
            list.add(new HudElementBounds("target", target.getLastX(), target.getLastY(), target.getLastW(), target.getLastH()));
        }

        Cooldowns cd = ModuleManager.getInstance().getModule(Cooldowns.class);
        if (cd != null && cd.isEnabled() && !"cooldowns".equals(currentId) && cd.getLastW() > 10.0f) {
            list.add(new HudElementBounds("cooldowns", cd.getLastX(), cd.getLastY(), cd.getLastW(), cd.getLastH()));
        }

        Keybinds kb = ModuleManager.getInstance().getModule(Keybinds.class);
        if (kb != null && kb.isEnabled() && !"keybinds".equals(currentId) && kb.getLastW() > 10.0f) {
            list.add(new HudElementBounds("keybinds", kb.getLastX(), kb.getLastY(), kb.getLastW(), kb.getLastH()));
        }

        EventTracker tracker = ModuleManager.getInstance().getModule(EventTracker.class);
        if (tracker != null && tracker.isEnabled() && !"event_tracker".equals(currentId) && tracker.getLastW() > 10.0f) {
            list.add(new HudElementBounds("event_tracker", tracker.getLastX(), tracker.getLastY(), tracker.getLastW(), tracker.getLastH()));
        }

        PearlPredict pearlPredict = ModuleManager.getInstance().getModule(PearlPredict.class);
        if (pearlPredict != null && pearlPredict.isEnabled()) {
            HudElementBounds pearl = pearlPredict.getBounds();
            if (pearl != null && !pearl.id().equals(currentId)) list.add(pearl);
        }

        return list;
    }

    public static SnapResult calculateSnap(String currentId, float proposedX, float proposedY, float w, float h, float screenW, float screenH) {
        SnapResult res = new SnapResult(proposedX, proposedY);
        List<HudElementBounds> others = getOtherElements(currentId);

        float snapThreshold = 7.0f;
        float gap = 6.0f;

        float bestX = proposedX;
        float minDiffX = snapThreshold;
        HudElementBounds snapTargetX = null;
        float bestGuideX = -1.0f;
        float bestGuideXMinY = 0.0f;
        float bestGuideXMaxY = screenH;

        float midX = (screenW - w) / 2.0f;
        float diffCenterX = Math.abs(proposedX - midX);
        if (diffCenterX < minDiffX) {
            minDiffX = diffCenterX;
            bestX = midX;
            bestGuideX = screenW / 2.0f;
            bestGuideXMinY = 0.0f;
            bestGuideXMaxY = screenH;
        }

        for (HudElementBounds target : others) {
            float tMinY = Math.min(proposedY, target.y()) - 4.0f;
            float tMaxY = Math.max(proposedY + h, target.y() + target.h()) + 4.0f;

            float dLeft = Math.abs(proposedX - target.x());
            if (dLeft < minDiffX) {
                minDiffX = dLeft;
                bestX = target.x();
                snapTargetX = target;
                bestGuideX = target.x();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            float dRight = Math.abs(proposedX - (target.x() + target.w() - w));
            if (dRight < minDiffX) {
                minDiffX = dRight;
                bestX = target.x() + target.w() - w;
                snapTargetX = target;
                bestGuideX = target.x() + target.w();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            float dCenter = Math.abs(proposedX - (target.x() + (target.w() - w) / 2.0f));
            if (dCenter < minDiffX) {
                minDiffX = dCenter;
                bestX = target.x() + (target.w() - w) / 2.0f;
                snapTargetX = target;
                bestGuideX = target.x() + target.w() / 2.0f;
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            float dAdjRight = Math.abs(proposedX - (target.x() + target.w() + gap));
            if (dAdjRight < minDiffX) {
                minDiffX = dAdjRight;
                bestX = target.x() + target.w() + gap;
                snapTargetX = target;
                bestGuideX = target.x() + target.w();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }

            float dAdjLeft = Math.abs(proposedX - (target.x() - w - gap));
            if (dAdjLeft < minDiffX) {
                minDiffX = dAdjLeft;
                bestX = target.x() - w - gap;
                snapTargetX = target;
                bestGuideX = target.x();
                bestGuideXMinY = tMinY;
                bestGuideXMaxY = tMaxY;
            }
        }

        if (minDiffX >= snapThreshold) {
            float nearestGridX = Math.round(proposedX / GRID_STEP) * GRID_STEP;
            if (Math.abs(proposedX - nearestGridX) < 5.0f) {
                bestX = nearestGridX;
            }
        }

        res.snappedX = bestX;
        res.snappedElemX = snapTargetX;
        res.guideX = bestGuideX;
        res.guideXMinY = bestGuideXMinY;
        res.guideXMaxY = bestGuideXMaxY;

        float bestY = proposedY;
        float minDiffY = snapThreshold;
        HudElementBounds snapTargetY = null;
        float bestGuideY = -1.0f;
        float bestGuideYMinX = 0.0f;
        float bestGuideYMaxX = screenW;

        float midY = (screenH - h) / 2.0f;
        float diffCenterY = Math.abs(proposedY - midY);
        if (diffCenterY < minDiffY) {
            minDiffY = diffCenterY;
            bestY = midY;
            bestGuideY = screenH / 2.0f;
            bestGuideYMinX = 0.0f;
            bestGuideYMaxX = screenW;
        }

        for (HudElementBounds target : others) {
            float tMinX = Math.min(proposedX, target.x()) - 4.0f;
            float tMaxX = Math.max(proposedX + w, target.x() + target.w()) + 4.0f;

            float dTop = Math.abs(proposedY - target.y());
            if (dTop < minDiffY) {
                minDiffY = dTop;
                bestY = target.y();
                snapTargetY = target;
                bestGuideY = target.y();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            float dBottom = Math.abs(proposedY - (target.y() + target.h() - h));
            if (dBottom < minDiffY) {
                minDiffY = dBottom;
                bestY = target.y() + target.h() - h;
                snapTargetY = target;
                bestGuideY = target.y() + target.h();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            float dCenter = Math.abs(proposedY - (target.y() + (target.h() - h) / 2.0f));
            if (dCenter < minDiffY) {
                minDiffY = dCenter;
                bestY = target.y() + (target.h() - h) / 2.0f;
                snapTargetY = target;
                bestGuideY = target.y() + target.h() / 2.0f;
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            float dAdjBelow = Math.abs(proposedY - (target.y() + target.h() + gap));
            if (dAdjBelow < minDiffY) {
                minDiffY = dAdjBelow;
                bestY = target.y() + target.h() + gap;
                snapTargetY = target;
                bestGuideY = target.y() + target.h();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }

            float dAdjAbove = Math.abs(proposedY - (target.y() - h - gap));
            if (dAdjAbove < minDiffY) {
                minDiffY = dAdjAbove;
                bestY = target.y() - h - gap;
                snapTargetY = target;
                bestGuideY = target.y();
                bestGuideYMinX = tMinX;
                bestGuideYMaxX = tMaxX;
            }
        }

        if (minDiffY >= snapThreshold) {
            float nearestGridY = Math.round(proposedY / GRID_STEP) * GRID_STEP;
            if (Math.abs(proposedY - nearestGridY) < 5.0f) {
                bestY = nearestGridY;
            }
        }

        res.snappedY = bestY;
        res.snappedElemY = snapTargetY;
        res.guideY = bestGuideY;
        res.guideYMinX = bestGuideYMinX;
        res.guideYMaxX = bestGuideYMaxX;

        return res;
    }

    public static void renderHoverOverlay(HudElementBounds bounds, float screenW, float screenH,
                                          double mouseX, double mouseY, int scalePercent) {
        if (mouseX < bounds.x() - 2.0f || mouseX > bounds.x() + bounds.w() + 2.0f
                || mouseY < bounds.y() - 2.0f || mouseY > bounds.y() + bounds.h() + 2.0f) return;

        renderSelection(bounds, screenW, screenH, scalePercent, 0xBFDDE2EB);
        DrawBatcher.flushPending();
    }

    public static void renderDragOverlay(HudElementBounds bounds, SnapResult snap, float screenW, float screenH, int scalePercent) {
        Draw2DProcessor draw = Draw2DProcessor.INSTANCE;
        for (int x = GRID_STEP; x < screenW; x += GRID_STEP) {
            draw.drawRect(null, x, 0, 0.6f, screenH, 0, 0x0AFFFFFF);
        }
        for (int y = GRID_STEP; y < screenH; y += GRID_STEP) {
            draw.drawRect(null, 0, y, screenW, 0.6f, 0, 0x0AFFFFFF);
        }

        if (snap != null) {
            renderGuide(true, snap.guideX, snap.guideXMinY, Math.min(snap.guideXMaxY, screenH), bounds, snap.snappedElemX);
            renderGuide(false, snap.guideY, snap.guideYMinX, Math.min(snap.guideYMaxX, screenW), bounds, snap.snappedElemY);
            if (snap.snappedElemX != null) renderCorners(snap.snappedElemX, screenW, screenH, 0x8AB4BECE);
            if (snap.snappedElemY != null && !snap.snappedElemY.equals(snap.snappedElemX)) {
                renderCorners(snap.snappedElemY, screenW, screenH, 0x8AB4BECE);
            }
        }

        int color = ColorUtil.interpolate(0xFFF3F5F9, Theme.getAccent(), 0.24f);
        renderSelection(bounds, screenW, screenH, scalePercent, color);
        DrawBatcher.flushPending();
    }

    private static void renderSelection(HudElementBounds bounds, float screenW, float screenH, int scalePercent, int color) {
        if (bounds.w() <= 0.0f || bounds.h() <= 0.0f) return;

        renderCorners(bounds, screenW, screenH, color);

        String label = scalePercent + "%";
        float textSize = 8.0f;
        float width = Math.round(Fonts.b.a(label, textSize)) + 10.0f;
        float height = 12.0f;
        float margin = 3.0f;
        float maxX = screenW - width - margin;
        float maxY = screenH - height - 15.0f;
        if (maxX < margin || maxY < margin) return;

        float x = Math.clamp(Math.round(bounds.x() + bounds.w() + SELECTION_PAD - width), margin, maxX);
        float y = bounds.y() - SELECTION_PAD - 3.0f - height;
        if (y < margin) y = bounds.y() + bounds.h() + SELECTION_PAD + 3.0f;
        y = Math.clamp(Math.round(y), margin, maxY);

        Draw2DProcessor.INSTANCE.drawRect(null, x, y, width, height, 3.0f, 0xEB1B1D23);
        Fonts.b.drawCentered(null, label, x + width / 2.0f, y + height / 2.0f, textSize, 0xFFF0F2F6, 0);
    }

    private static void renderCorners(HudElementBounds bounds, float screenW, float screenH, int color) {
        float left = Math.max(0.0f, Math.round(bounds.x() - SELECTION_PAD));
        float top = Math.max(0.0f, Math.round(bounds.y() - SELECTION_PAD));
        float right = Math.min(screenW, Math.round(bounds.x() + bounds.w() + SELECTION_PAD));
        float bottom = Math.min(screenH, Math.round(bounds.y() + bounds.h() + SELECTION_PAD));
        float leg = Math.min(6.0f, Math.min(right - left, bottom - top) / 2.0f);
        if (leg < 2.0f) return;

        drawCorner(left, top, leg, false, false, color);
        drawCorner(right, top, leg, true, false, color);
        drawCorner(left, bottom, leg, false, true, color);
        drawCorner(right, bottom, leg, true, true, color);
    }

    private static void drawCorner(float x, float y, float leg, boolean right, boolean bottom, int color) {
        Draw2DProcessor draw = Draw2DProcessor.INSTANCE;
        draw.drawRect(null, right ? x - leg : x, bottom ? y - 1.0f : y, leg, 1.0f, 0, color);
        draw.drawRect(null, right ? x - 1.0f : x, bottom ? y - leg : y + 1.0f, 1.0f, leg - 1.0f, 0, color);
    }

    private static void renderGuide(boolean vertical, float coordinate, float start, float end,
                                    HudElementBounds current, HudElementBounds target) {
        if (coordinate < 0.0f) return;

        HudElementBounds first = current;
        HudElementBounds second = target;
        if (target != null && axisStart(target, vertical) < axisStart(current, vertical)) {
            first = target;
            second = current;
        }

        float line = Math.round(coordinate);
        float cursor = Math.max(0.0f, Math.round(start));
        cursor = drawToElement(vertical, line, cursor, end, first);
        cursor = drawToElement(vertical, line, cursor, end, second);
        drawGuideSegment(vertical, line, cursor, end);
    }

    private static float drawToElement(boolean vertical, float line, float cursor, float end, HudElementBounds bounds) {
        if (bounds == null) return cursor;
        float crossStart = axisStart(bounds, !vertical);
        float crossSize = vertical ? bounds.w() : bounds.h();
        if (line < crossStart - SELECTION_PAD || line > crossStart + crossSize + SELECTION_PAD) return cursor;

        float start = axisStart(bounds, vertical);
        float size = vertical ? bounds.h() : bounds.w();
        drawGuideSegment(vertical, line, cursor, Math.min(end, start - SELECTION_PAD));
        return Math.max(cursor, start + size + SELECTION_PAD);
    }

    private static float axisStart(HudElementBounds bounds, boolean vertical) {
        return vertical ? bounds.y() : bounds.x();
    }

    private static void drawGuideSegment(boolean vertical, float line, float start, float end) {
        for (float position = start; position + 2.0f <= end; position += 8.0f) {
            float length = Math.min(4.0f, end - position);
            Draw2DProcessor.INSTANCE.drawRect(null, vertical ? line : position, vertical ? position : line,
                    vertical ? 1.0f : length, vertical ? length : 1.0f, 0, 0x97BCC9DC);
        }
    }
}
