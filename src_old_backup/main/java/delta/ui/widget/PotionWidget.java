package delta.ui.widget;
import delta.render.RenderStateHelper;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.mixin.IStatusEffectInstance;
import delta.module.render.Interface_2;
import delta.notification.Notification;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;

import delta.setting.BooleanSetting;
import delta.setting.ModeSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class PotionWidget extends Widget implements Interface {
    private final ModeSetting displayMode;
    private final ModeSetting sideSetting;
    private final ModeSetting timerFormat;
    private final BooleanSetting showDigits;
    private final StatusEffectInstance g;

    private static class Point {
        final float x, y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }

    public PotionWidget() {
        super(new DragInfo("Зелья", 0.0f, 0.0f, 0.0f, 0.0f));
        this.displayMode = new ModeSetting("Режим отображения", "Боковой остров", "Боковой остров", "Классический");
        this.sideSetting = new ModeSetting("Сторона экрана", "Авто", "Авто", "Справа", "Слева");
        this.timerFormat = new ModeSetting("Формат таймера", "Проценты", "Проценты", "Время");
        this.showDigits = new BooleanSetting("Отображать цифры", true);
        this.g = new StatusEffectInstance(StatusEffects.SPEED, 1200, 0);

        this.sideSetting.a(() -> this.displayMode.l("Боковой остров"));
        this.timerFormat.a(() -> this.displayMode.l("Боковой остров"));
        this.showDigits.a(() -> this.displayMode.l("Боковой остров"));

        j().a(this);
        a(this.displayMode, this.sideSetting, this.timerFormat, this.showDigits);
    }

    private void syncSettings() {
        Interface_2 iface = Interface_2.getInstance();
        if (iface != null) {
            if (!this.displayMode.c().equals(iface.getEffectMode().c())) {
                this.displayMode.a(iface.getEffectMode().c());
            }
            if (!this.timerFormat.c().equals(iface.getEffectTimerFormat().c())) {
                this.timerFormat.a(iface.getEffectTimerFormat().c());
            }
            if (this.showDigits.c().booleanValue() != iface.getEffectShowDigits().c().booleanValue()) {
                this.showDigits.a(iface.getEffectShowDigits().c());
            }
        }
    }

    public static int getEffectColor(StatusEffectInstance effect) {
        var entry = effect.getEffectType();
        if (entry.equals(StatusEffects.STRENGTH)) {
            return 0xFFF97316; // Vivid Flame Orange (73% in screenshot)
        } else if (entry.equals(StatusEffects.SPEED)) {
            return 0xFF38BDF8; // Sky Blue
        } else if (entry.equals(StatusEffects.REGENERATION) || entry.equals(StatusEffects.HEALTH_BOOST)) {
            return 0xFF22C55E; // Emerald Green (21% in screenshot)
        } else if (entry.equals(StatusEffects.HASTE) || entry.equals(StatusEffects.ABSORPTION)) {
            return 0xFFFACC15; // Vibrant Gold Yellow (52% in screenshot)
        } else if (entry.equals(StatusEffects.RESISTANCE)) {
            return 0xFF94A3B8; // Slate Shield
        } else if (entry.equals(StatusEffects.FIRE_RESISTANCE)) {
            return 0xFFFB923C; // Amber Flame
        } else if (entry.equals(StatusEffects.WATER_BREATHING)) {
            return 0xFF06B6D4; // Marine Cyan
        } else if (entry.equals(StatusEffects.INVISIBILITY)) {
            return 0xFF818CF8; // Translucent Indigo
        } else if (entry.equals(StatusEffects.JUMP_BOOST)) {
            return 0xFF84CC16; // Lime
        } else if (entry.equals(StatusEffects.POISON)) {
            return 0xFFA3E635; // Toxic Green
        } else if (entry.equals(StatusEffects.WITHER)) {
            return 0xFF475569; // Charcoal Dark
        } else if (entry.equals(StatusEffects.WEAKNESS)) {
            return 0xFF9CA3AF; // Muted Gray
        } else if (entry.equals(StatusEffects.SLOWNESS)) {
            return 0xFF64748B; // Slate Gray
        } else if (entry.value().getCategory() == StatusEffectCategory.HARMFUL) {
            return 0xFFEF4444; // Crimson Red
        }
        int c = entry.value().getColor();
        return 0xFF000000 | c;
    }

    public static void drawCircle(MatrixStack matrices, float cx, float cy, float radius, int color) {
        if (radius <= 0.0f) return;
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        if (a <= 0.0f) return;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx, cy, 0.0f).color(r, g, b, a);
        int segments = Math.max(16, (int) (radius * 3.5f));
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        }
        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableCull();
    }

    public static void drawRingArc(MatrixStack matrices, float cx, float cy, float innerRadius, float outerRadius, float startAngleDeg, float sweepAngleDeg, int color) {
        if (sweepAngleDeg <= 0.0f || outerRadius <= innerRadius) return;
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        if (a <= 0.0f) return;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int segments = Math.max(8, (int) (sweepAngleDeg / 4.0f));
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float currentAngle = (float) Math.toRadians(startAngleDeg + (sweepAngleDeg * (float) i / (float) segments));
            float cos = (float) Math.cos(currentAngle);
            float sin = (float) Math.sin(currentAngle);
            buffer.vertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0.0f).color(r, g, b, a);
        }
        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableCull();
    }

    private void drawDockBody(MatrixStack matrices, float dockX, float dockY, float dockW, float dockH, boolean isRight, int bgColor, int borderColor, float screenW, float anim) {
        float filletR = 14.0f;
        float cornerR = 11.0f;

        List<Point> contour = new ArrayList<>();
        int nFillet = 12;
        int nCorner = 10;

        if (isRight) {
            // 1. Top fillet: flaring out from right screen edge into dock shoulder
            for (int i = 0; i <= nFillet; i++) {
                float t = (float) i / (float) nFillet;
                float fy = dockY - filletR + filletR * t;
                float s = t * t * (3.0f - 2.0f * t); // smoothstep
                float fx = screenW - (dockW - cornerR) * s;
                contour.add(new Point(fx, fy));
            }

            // 2. Top-left corner: rounding 90 degrees into vertical left edge
            float tcx = dockX + cornerR;
            float tcy = dockY + cornerR;
            for (int i = 1; i <= nCorner; i++) {
                float phi = (float) (Math.PI * 0.5 * (float) i / (float) nCorner);
                float fx = tcx - cornerR * (float) Math.sin(phi);
                float fy = tcy - cornerR * (float) Math.cos(phi);
                contour.add(new Point(fx, fy));
            }

            // 3. Vertical straight left edge
            contour.add(new Point(dockX, dockY + dockH - cornerR));

            // 4. Bottom-left corner: rounding 90 degrees into bottom shoulder
            float bcx = dockX + cornerR;
            float bcy = dockY + dockH - cornerR;
            for (int i = 1; i <= nCorner; i++) {
                float phi = (float) (Math.PI * 0.5 * (float) i / (float) nCorner);
                float fx = bcx - cornerR * (float) Math.cos(phi);
                float fy = bcy + cornerR * (float) Math.sin(phi);
                contour.add(new Point(fx, fy));
            }

            // 5. Bottom fillet: flaring back from bottom shoulder into right screen edge
            for (int i = 1; i <= nFillet; i++) {
                float t = (float) i / (float) nFillet;
                float fy = dockY + dockH + filletR * t;
                float s = (1.0f - t) * (1.0f - t) * (3.0f - 2.0f * (1.0f - t));
                float fx = screenW - (dockW - cornerR) * s;
                contour.add(new Point(fx, fy));
            }
        } else {
            // Left side dock (docked to x = 0.0f)
            // 1. Top fillet: flaring out from left screen edge into dock shoulder
            for (int i = 0; i <= nFillet; i++) {
                float t = (float) i / (float) nFillet;
                float fy = dockY - filletR + filletR * t;
                float s = t * t * (3.0f - 2.0f * t);
                float fx = (dockW - cornerR) * s;
                contour.add(new Point(fx, fy));
            }

            // 2. Top-right corner
            float tcx = dockW - cornerR;
            float tcy = dockY + cornerR;
            for (int i = 1; i <= nCorner; i++) {
                float phi = (float) (Math.PI * 0.5 * (float) i / (float) nCorner);
                float fx = tcx + cornerR * (float) Math.sin(phi);
                float fy = tcy - cornerR * (float) Math.cos(phi);
                contour.add(new Point(fx, fy));
            }

            // 3. Vertical straight right edge
            contour.add(new Point(dockW, dockY + dockH - cornerR));

            // 4. Bottom-right corner
            float bcx = dockW - cornerR;
            float bcy = dockY + dockH - cornerR;
            for (int i = 1; i <= nCorner; i++) {
                float phi = (float) (Math.PI * 0.5 * (float) i / (float) nCorner);
                float fx = bcx + cornerR * (float) Math.cos(phi);
                float fy = bcy + cornerR * (float) Math.sin(phi);
                contour.add(new Point(fx, fy));
            }

            // 5. Bottom fillet: flaring back into 0.0f
            for (int i = 1; i <= nFillet; i++) {
                float t = (float) i / (float) nFillet;
                float fy = dockY + dockH + filletR * t;
                float s = (1.0f - t) * (1.0f - t) * (3.0f - 2.0f * (1.0f - t));
                float fx = (dockW - cornerR) * s;
                contour.add(new Point(fx, fy));
            }
        }

        int[] bg = ColorUtil.b(bgColor);
        float bgr = bg[0] / 255.0f;
        float bgg = bg[1] / 255.0f;
        float bgb = bg[2] / 255.0f;
        float bga = (bg[3] / 255.0f) * anim;

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Fill mesh
        BufferBuilder fillBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        float edgeX = isRight ? screenW : 0.0f;
        for (Point p : contour) {
            fillBuffer.vertex(matrix, p.x, p.y, 0.0f).color(bgr, bgg, bgb, bga);
            fillBuffer.vertex(matrix, edgeX, p.y, 0.0f).color(bgr, bgg, bgb, bga);
        }
        ImmediateBufferRenderer.draw(fillBuffer.end());

        // HUD Theme gradient on side island dock
        if (delta.module.render.Interface_2.getInstance().isHudThemeGradient()) {
            float strength = delta.module.render.Interface_2.getInstance().getHudGradientStrength();
            int themeCol = Delta.h().d().o().a(delta.config.ThemeInfo.PRIMARY).a();
            float tr = ((themeCol >> 16) & 0xFF) / 255.0f;
            float tg = ((themeCol >> 8) & 0xFF) / 255.0f;
            float tb = (themeCol & 0xFF) / 255.0f;
            BufferBuilder gradBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (Point p : contour) {
                float vertAlpha = MathHelper.lerp((p.y - dockY) / Math.max(1.0f, dockH), 0.08f, 0.20f) * anim * strength;
                float edgeAlpha = MathHelper.lerp((p.y - dockY) / Math.max(1.0f, dockH), 0.15f, 0.28f) * anim * strength;
                gradBuffer.vertex(matrix, p.x, p.y, 0.0f).color(tr, tg, tb, vertAlpha);
                gradBuffer.vertex(matrix, edgeX, p.y, 0.0f).color(tr, tg, tb, edgeAlpha);
            }
            ImmediateBufferRenderer.draw(gradBuffer.end());
        }

        // Outline border
        int[] bd = ColorUtil.b(borderColor);
        float bdr = bd[0] / 255.0f;
        float bdg = bd[1] / 255.0f;
        float bdb = bd[2] / 255.0f;
        float bda = (bd[3] / 255.0f) * anim;

        if (bda > 0.0f) {
            BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < contour.size() - 1; i++) {
                Point p0 = contour.get(i);
                Point p1 = contour.get(i + 1);
                lineBuffer.vertex(matrix, p0.x, p0.y, 0.0f).color(bdr, bdg, bdb, bda);
                lineBuffer.vertex(matrix, p1.x, p1.y, 0.0f).color(bdr, bdg, bdb, bda);
            }
            ImmediateBufferRenderer.draw(lineBuffer.end());
        }

        RenderStateHelper.enableCull();
    }

    @Override
    public void a(DrawEvent event) {
        syncSettings();

        d().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        Iterator<StatusEffectInstance> it = k().iterator();
        while (it.hasNext()) {
            ((IStatusEffectInstance) (Object) it.next()).getAnimation().a(0.0f, 1.0f, 0.3f, EasingList.g, event.g());
        }

        float anim = a();
        if (anim <= 0.0f) {
            super.a(event);
            return;
        }

        // Collect active status effects
        List<StatusEffectInstance> activeEffects = new ArrayList<>();
        for (StatusEffectInstance effect : k()) {
            IStatusEffectInstance instance = (IStatusEffectInstance) (Object) effect;
            if (instance.getAnimation().c() > 0.0f) {
                activeEffects.add(effect);
            }
        }

        if (activeEffects.isEmpty()) {
            super.a(event);
            return;
        }

        boolean isSide = this.displayMode.l("Боковой остров");

        if (isSide) {
            renderSideDock(event, activeEffects, anim);
        } else {
            renderClassicCard(event, activeEffects, anim);
        }

        super.a(event);
    }

    private void renderSideDock(DrawEvent event, List<StatusEffectInstance> activeEffects, float anim) {
        float screenW = aM_.getWindow().getScaledWidth();
        float screenH = aM_.getWindow().getScaledHeight();
        float dockW = 38.0f;

        boolean isRight;
        if (this.sideSetting.l("Справа")) {
            isRight = true;
        } else if (this.sideSetting.l("Слева")) {
            isRight = false;
        } else {
            isRight = (j().a() + (j().f() / 2.0f)) >= (screenW / 2.0f);
        }

        float ringR = 12.5f;
        float discR = ringR - 1.2f;
        float trackThickness = 2.0f;
        boolean digits = this.showDigits.c().booleanValue();
        float itemHeight = (ringR * 2.0f) + (digits ? 9.0f : 0.0f);
        float gap = 7.0f;

        float contentHeight = (activeEffects.size() * itemHeight) + Math.max(0, (activeEffects.size() - 1)) * gap;
        float totalDockH = 10.0f + contentHeight + 10.0f;

        float dockX = isRight ? (screenW - dockW) : 0.0f;
        float userY = j().b();
        if (userY <= 0.0f) {
            userY = Math.max(20.0f, (screenH - totalDockH) * 0.38f);
        }
        float dockY = MathHelper.clamp(userY, 15.0f, Math.max(15.0f, screenH - totalDockH - 15.0f));

        j().a(dockX);
        j().b(dockY);
        j().c(dockW);
        j().d(totalDockH);

        // Styling
        int hudBg = getHudBackgroundColor();
        float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
        if (baseA <= 0.0f) baseA = 0.94f;
        int dockBg = ColorUtil.a(12, 13, 18, (int) (0.94f * baseA * 255.0f * anim));
        int dockBorder = ColorUtil.a(255, 255, 255, (int) (0.13f * 255.0f * anim));

        // Draw curved edge dock body
        drawDockBody(event.h(), dockX, dockY, dockW, totalDockH, isRight, dockBg, dockBorder, screenW, anim);

        float curItemY = dockY + 10.0f;
        float centerX = isRight ? (dockX + (dockW / 2.0f)) : (dockW / 2.0f);

        for (StatusEffectInstance effect : activeEffects) {
            IStatusEffectInstance instance = (IStatusEffectInstance) (Object) effect;
            float effAnim = instance.getAnimation().c() * anim;
            if (effAnim <= 0.0f) continue;

            float centerY = curItemY + ringR;

            // 1. Duration & Progress
            int current = effect.getDuration();
            int initial = instance.getInitialDuration();
            if (initial <= 0) initial = Math.max(current, 1);
            float progress = current > 1000000 ? 1.0f : MathHelper.clamp((float) current / (float) initial, 0.0f, 1.0f);

            // 2. Effect color
            int effectColor = getEffectColor(effect);

            // 3. Dark inner disc
            int discBg = ColorUtil.a(20, 22, 28, (int) (0.96f * 255.0f * effAnim));
            drawCircle(event.h(), centerX, centerY, discR, discBg);

            // 4. Subtle disc inner rim
            int discRim = ColorUtil.a(255, 255, 255, (int) (0.07f * 255.0f * effAnim));
            drawRingArc(event.h(), centerX, centerY, discR - 0.7f, discR, 0.0f, 360.0f, discRim);

            float spriteScale = 0.58f;
            float spriteW = 18.0f * spriteScale;
            event.e().a(event.i(), net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType()), centerX - (spriteW / 2.0f), centerY - (spriteW / 2.0f), 0.0f, spriteScale, effAnim);

            // 6. Circular timer: Background track
            int trackColor = ColorUtil.a(effectColor, (int) (0.16f * 255.0f * effAnim));
            drawRingArc(event.h(), centerX, centerY, ringR - trackThickness, ringR, 0.0f, 360.0f, trackColor);

            // 7. Circular timer: Active progress arc
            float sweepAngle = progress * 360.0f;
            if (sweepAngle > 0.0f) {
                int activeColor = ColorUtil.a(effectColor, effAnim);
                drawRingArc(event.h(), centerX, centerY, ringR - trackThickness, ringR, -90.0f, sweepAngle, activeColor);

                // Rounded arc caps for smooth Apple-style aesthetic
                float midR = ringR - (trackThickness * 0.5f);
                float capR = trackThickness * 0.5f;
                // Start cap at -90 deg (12 o'clock)
                drawCircle(event.h(), centerX, centerY - midR, capR, activeColor);
                // End cap at (-90 + sweepAngle)
                float endRad = (float) Math.toRadians(-90.0f + sweepAngle);
                float endX = centerX + (float) Math.cos(endRad) * midR;
                float endY = centerY + (float) Math.sin(endRad) * midR;
                drawCircle(event.h(), endX, endY, capR, activeColor);
            }

            // 8. Digits / text below circle
            if (digits) {
                String text;
                if (this.timerFormat.l("Время")) {
                    int sec = current / 20;
                    text = current > 1000000 ? "∞" : (sec / 60) + ":" + String.format("%02d", sec % 60);
                } else {
                    int pct = Math.round(progress * 100.0f);
                    text = pct + "%";
                }
                float tw = Fonts.e.a(text, 7.0f);
                float textX = centerX - (tw / 2.0f);
                float textY = centerY + ringR + 2.5f;
                int textColor = ColorUtil.a(235, 238, 245, (int) (0.92f * 255.0f * effAnim));
                Fonts.e.a(event.h(), text, textX, textY, 7.0f, textColor);
            }

            curItemY += itemHeight + gap;
        }
    }

    private void renderClassicCard(DrawEvent event, List<StatusEffectInstance> activeEffects, float anim) {
        float x = j().a();
        float y = j().b();
        float headerHeight = 17.5f;
        float rowHeight = 15.0f;
        float radius = getRounding();

        // Calculate card dimensions
        float titleW = Fonts.e.a("Эффекты", 8.5f);
        float iconW = Fonts.a.a("E", 8.5f);
        float minRequiredWidth = 8.0f + titleW + 20.0f + iconW + 8.0f;

        float maxRowWidth = minRequiredWidth;
        for (StatusEffectInstance effect : activeEffects) {
            String name = Text.translatable(((StatusEffect) effect.getEffectType().value()).getTranslationKey()).getString() 
                    + " " + (effect.getAmplifier() + 1);
            int seconds = effect.getDuration() / 20;
            String duration = effect.getDuration() > 1000000 ? "∞" : (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            float w = 8.0f + 14.0f + 4.0f + Fonts.e.a(name, 7.5f) + 16.0f + Fonts.e.a(duration, 7.5f) + 8.0f;
            if (w > maxRowWidth) {
                maxRowWidth = w;
            }
        }

        float targetWidth = Math.max(125.0f, maxRowWidth);
        float cardWidth = MathUtil.c(j().f() > 0.0f ? j().f() : targetWidth, targetWidth, 0.35f);
        j().c(cardWidth);

        boolean hasRows = !activeEffects.isEmpty();
        float totalHeight = headerHeight + (hasRows ? 1.0f + (activeEffects.size() * rowHeight) + 3.0f : 0.0f);
        j().d(totalHeight);

        float blurVal = getBlur();
        float opacityVal = getOpacity();

        int primaryColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int accentColor = ColorUtil.a(primaryColor, anim);
        int hudBg = getHudBackgroundColor();
        float baseA = ((hudBg >> 24) & 0xFF) / 255.0f;
        if (baseA <= 0.0f) baseA = 0.85f;
        int cardBg = ColorUtil.a(hudBg, (int) (opacityVal * baseA * 255.0f * anim));
        int cardBorder = ColorUtil.a(255, 255, 255, (int) (0.12f * 255.0f * anim));
        int titleColor = ColorUtil.a(255, 255, 255, (int) (1.0f * 255.0f * anim));

        // 1. Frosted base card
        if (blurVal > 0.0f && !event.d().e().e().isEmpty()) {
            event.d().b(event.h(), x, y, cardWidth, totalHeight, radius, cardBg, anim);
        } else {
            event.d().a(event.h(), x, y, cardWidth, totalHeight, radius, cardBg);
        }

        // HUD Theme gradient
        if (Interface_2.getInstance().isHudThemeGradient()) {
            drawHudThemeGradient(event.d(), event.h(), x, y, cardWidth, totalHeight, radius, anim);
        }

        // 2. Outer card contour border
        event.d().a(event.h(), x, y, cardWidth, totalHeight, radius, 1.0f, cardBorder);

        // Header: "Эффекты" on left, flask icon on right
        float titleY = y + ((headerHeight - Fonts.e.a(8.5f)) / 2.0f) - 0.5f;
        float iconY = y + ((headerHeight - Fonts.a.a(8.5f)) / 2.0f) - 0.2f;
        Fonts.e.a(event.h(), "Эффекты", x + 8.0f, titleY, 8.5f, titleColor);
        Fonts.a.a(event.h(), "E", x + cardWidth - 8.0f - iconW, iconY, 8.5f, accentColor);

        // Body rows: Active effects
        if (hasRows) {
            float rowY = y + headerHeight + 2.5f;
            for (StatusEffectInstance effect : activeEffects) {
                IStatusEffectInstance instance = (IStatusEffectInstance) (Object) effect;
                float effAnim = instance.getAnimation().c() * anim;
                if (effAnim <= 0.0f) {
                    continue;
                }

                String name = Text.translatable(((StatusEffect) effect.getEffectType().value()).getTranslationKey()).getString() 
                        + " " + (effect.getAmplifier() + 1);
                int seconds = effect.getDuration() / 20;
                String duration = effect.getDuration() > 1000000 ? "∞" : (seconds / 60) + ":" + String.format("%02d", seconds % 60);

                boolean harmful = ((StatusEffect) effect.getEffectType().value()).getCategory() == StatusEffectCategory.HARMFUL;
                int nameColor = harmful 
                        ? ColorUtil.a(240, 85, 85, (int) (255.0f * effAnim)) 
                        : ColorUtil.a(235, 238, 248, (int) (0.92f * 255.0f * effAnim));
                int durationColor = ColorUtil.a(primaryColor, effAnim);

                float durW = Fonts.e.a(duration, 7.5f);
                float textY = rowY + ((rowHeight - Fonts.e.a(7.5f)) / 2.0f) - 0.5f;

                // Sprite icon
                event.e().a(event.i(), net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType()), x + 8.0f, rowY + 2.5f, 0.0f, 0.42f, effAnim);

                // Effect name
                Fonts.e.a(event.h(), name, x + 8.0f + 14.0f, textY, 7.5f, nameColor);

                // Duration in accent color
                Fonts.e.a(event.h(), duration, x + cardWidth - 8.0f - durW, textY, 7.5f, durationColor);

                rowY += rowHeight;
            }
        }
    }

    @Override
    public void a(GlobalEvent event) {
        boolean visible = aM_.currentScreen instanceof ChatScreen;
        for (StatusEffectInstance effect : k()) {
            if (!effect.getEffectType().equals(StatusEffects.NIGHT_VISION)) {
                ((IStatusEffectInstance) (Object) effect).getAnimation().a(effect == this.g ? aM_.currentScreen instanceof ChatScreen : effect.getDuration() > 20);
                if (((IStatusEffectInstance) (Object) effect).getAnimation().c() > 0.0d) {
                    visible = true;
                }
                if (effect != this.g && effect.getDuration() == 100 && (effect.getEffectType().equals(StatusEffects.STRENGTH) || effect.getEffectType().equals(StatusEffects.SPEED) || effect.getEffectType().equals(StatusEffects.HEALTH_BOOST) || effect.getEffectType().equals(StatusEffects.INVISIBILITY))) {
                    Delta.h().d().m().a(new Notification("E", Text.literal("Эффект ").append(Text.translatable(((StatusEffect) effect.getEffectType().value()).getTranslationKey()).append(" " + (effect.getAmplifier() + 1)).styled(style -> {
                        return style.withColor(Delta.h().d().o().a(ThemeInfo.PRIMARY).a());
                    })).append(Text.literal(" заканчивается")), 2500));
                }
            }
        }
        d().a(visible);
        super.a(event);
    }

    private List<StatusEffectInstance> k() {
        List<StatusEffectInstance> effects = new ArrayList<>((Collection<? extends StatusEffectInstance>) aM_.player.getStatusEffects());
        boolean empty = effects.stream().allMatch(effect -> {
            return effect.getEffectType().equals(StatusEffects.NIGHT_VISION);
        });
        if (empty && ((aM_.currentScreen instanceof ChatScreen) || ((IStatusEffectInstance) (Object) this.g).getAnimation().c() > 0.0f)) {
            effects.add(this.g);
        }
        effects.sort(Comparator.comparingInt(effect2 -> {
            if (effect2.getEffectType().equals(StatusEffects.STRENGTH)) {
                return 0;
            }
            return effect2.getEffectType().equals(StatusEffects.WEAKNESS) ? 1 : 2;
        }));
        return effects;
    }
}
