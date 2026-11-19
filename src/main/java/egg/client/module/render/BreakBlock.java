package egg.client.module.render;

import egg.client.mixin.ClientPlayerInteractionManagerAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public class BreakBlock extends Module {
    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Тема", "Цветовая схема", "Тема", "Кастом", "Градиент");
    private final ColorSetting customColor = new ColorSetting("Цвет", 0xFF8B5CF6, "Цвет индикатора");
    private final SliderSetting radius = new SliderSetting("Радиус", 0.35, 0.20, 0.46, 0.02, "Размер круга на грани блока");
    private final SliderSetting ringWidth = new SliderSetting("Толщина", 0.045, 0.02, 0.10, 0.005, "Ширина полосы прогресса");
    private final BooleanSetting glow = new BooleanSetting("Свечение", true, "Неоновое свечение полосы прогресса");
    private final BooleanSetting showPercent = new BooleanSetting("Проценты", true, "Отображать процент в центре круга");
    private final BooleanSetting hideVanilla = new BooleanSetting("Скрыть ваниль", true, "Скрыть стандартные пиксельные трещины разрушения");

    private float smoothProgress = 0.0f;
    private BlockPos currentBreakingPos = null;
    private Direction currentBreakingSide = Direction.UP;
    private final Vector2f scratchPos = new Vector2f();

    public BreakBlock() {
        super("Break Block", "Круговой индикатор разрушения блока", Category.ESP);
        this.customColor.setVisible(() -> this.colorMode.is("Кастом"));
        addSettings(colorMode, customColor, radius, ringWidth, glow, showPercent, hideVanilla);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.smoothProgress = 0.0f;
        this.currentBreakingPos = null;
    }

    public boolean shouldHideVanilla() {
        return isEnabled() && this.hideVanilla.get() && this.currentBreakingPos != null && this.smoothProgress > 0.01f;
    }

    public void onTick() {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.smoothProgress = 0.0f;
            this.currentBreakingPos = null;
            return;
        }

        ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
        float rawProgress = accessor.getCurrentBreakingProgress();
        BlockPos pos = accessor.getCurrentBreakingPos();
        boolean isBreaking = accessor.isBreakingBlock() && rawProgress > 0.0f && pos != null;

        if (!isBreaking || mc.world.isAir(pos)) {
            this.smoothProgress = 0.0f;
            this.currentBreakingPos = null;
            return;
        }

        if (this.currentBreakingPos == null || !this.currentBreakingPos.equals(pos) || rawProgress < this.smoothProgress * 0.4f) {
            this.smoothProgress = rawProgress;
        } else {
            this.smoothProgress = MathHelper.lerp(0.55f, this.smoothProgress, rawProgress);
        }
        this.currentBreakingPos = pos;

        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
            this.currentBreakingSide = hit.getSide();
        } else if (this.currentBreakingSide == null) {
            this.currentBreakingSide = Direction.UP;
        }
    }

    private int getActiveColor(float progress, int alpha) {
        if (this.colorMode.is("Кастом")) {
            int rgb = this.customColor.get();
            return ColorUtil.rgba((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
        } else if (this.colorMode.is("Градиент")) {
            float r, g, b;
            if (progress < 0.5f) {
                float factor = progress / 0.5f;
                r = 1.0f;
                g = 0.35f + factor * 0.65f;
                b = 0.15f;
            } else {
                float factor = (progress - 0.5f) / 0.5f;
                r = 1.0f - factor * 0.85f;
                g = 1.0f;
                b = 0.25f + factor * 0.70f;
            }
            return ColorUtil.rgba((int) (r * 255), (int) (g * 255), (int) (b * 255), alpha);
        } else {
            int themeCol = Theme.getAccent();
            return ColorUtil.rgba((themeCol >> 16) & 0xFF, (themeCol >> 8) & 0xFF, themeCol & 0xFF, alpha);
        }
    }

    private static Vec3d getTangentU(Direction side) {
        return switch (side) {
            case UP, DOWN -> new Vec3d(1, 0, 0);
            case NORTH -> new Vec3d(-1, 0, 0);
            case SOUTH -> new Vec3d(1, 0, 0);
            case WEST -> new Vec3d(0, 0, 1);
            case EAST -> new Vec3d(0, 0, -1);
        };
    }

    private static Vec3d getTangentV(Direction side) {
        return switch (side) {
            case UP -> new Vec3d(0, 0, 1);
            case DOWN -> new Vec3d(0, 0, -1);
            case NORTH, SOUTH, WEST, EAST -> new Vec3d(0, 1, 0);
        };
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled() || this.currentBreakingPos == null || this.smoothProgress < 0.01f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.world.isAir(this.currentBreakingPos)) {
            this.smoothProgress = 0.0f;
            this.currentBreakingPos = null;
            return;
        }

        Direction side = this.currentBreakingSide != null ? this.currentBreakingSide : Direction.UP;
        Vec3d blockCenter = new Vec3d(
                this.currentBreakingPos.getX() + 0.5,
                this.currentBreakingPos.getY() + 0.5,
                this.currentBreakingPos.getZ() + 0.5
        );
        Vec3d faceNormal = Vec3d.of(side.getVector());

        Vec3d u = getTangentU(side);
        Vec3d v = getTangentV(side);

        float prog = MathHelper.clamp(this.smoothProgress, 0.0f, 1.0f);
        float baseRad = (float) this.radius.get();
        float w = (float) this.ringWidth.get();
        float rIn = Math.max(0.08f, baseRad - w);
        float rOut = baseRad;

        VertexConsumer quadBuf = immediate.getBuffer(RenderLayers.debugQuads());
        VertexConsumer lightBuf = immediate.getBuffer(RenderLayers.lightning());

        Vec3d trackCenter = blockCenter.add(faceNormal.multiply(0.503));
        renderBackgroundTrack(quadBuf, trackCenter, u, v, camPos, rIn, rOut);

        Vec3d arcCenter = blockCenter.add(faceNormal.multiply(0.506));
        renderRadialProgress(lightBuf, arcCenter, u, v, camPos, rIn, rOut, prog);
    }

    private void renderBackgroundTrack(VertexConsumer buf, Vec3d center, Vec3d u, Vec3d v, Vec3d camPos, float rIn, float rOut) {
        int steps = 48;
        int trackAlpha = 125;

        for (int i = 0; i < steps; i++) {
            double a0 = (i * 2.0 * Math.PI) / steps;
            double a1 = ((i + 1) * 2.0 * Math.PI) / steps;

            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);

            Vec3d p0_in = center.add(u.multiply(c0 * rIn)).add(v.multiply(s0 * rIn));
            Vec3d p0_out = center.add(u.multiply(c0 * rOut)).add(v.multiply(s0 * rOut));
            Vec3d p1_in = center.add(u.multiply(c1 * rIn)).add(v.multiply(s1 * rIn));
            Vec3d p1_out = center.add(u.multiply(c1 * rOut)).add(v.multiply(s1 * rOut));

            buf.vertex((float) (p0_in.x - camPos.x), (float) (p0_in.y - camPos.y), (float) (p0_in.z - camPos.z)).color(14, 16, 22, trackAlpha);
            buf.vertex((float) (p1_in.x - camPos.x), (float) (p1_in.y - camPos.y), (float) (p1_in.z - camPos.z)).color(14, 16, 22, trackAlpha);
            buf.vertex((float) (p1_out.x - camPos.x), (float) (p1_out.y - camPos.y), (float) (p1_out.z - camPos.z)).color(14, 16, 22, trackAlpha);
            buf.vertex((float) (p0_out.x - camPos.x), (float) (p0_out.y - camPos.y), (float) (p0_out.z - camPos.z)).color(14, 16, 22, trackAlpha);
        }
    }

    private void renderRadialProgress(VertexConsumer lightBuf, Vec3d center, Vec3d u, Vec3d v, Vec3d camPos,
                                      float rIn, float rOut, float progress) {
        int totalSteps = 48;
        int activeSteps = Math.max(1, (int) Math.ceil(progress * totalSteps));
        double startAngle = Math.PI * 0.5;

        boolean isGlow = this.glow.get();
        float glowOut = rOut + 0.03f;

        for (int i = 0; i < activeSteps; i++) {
            float t0 = (float) i / totalSteps;
            float t1 = Math.min(progress, (float) (i + 1) / totalSteps);

            double a0 = startAngle - t0 * 2.0 * Math.PI;
            double a1 = startAngle - t1 * 2.0 * Math.PI;

            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);

            Vec3d p0_in = center.add(u.multiply(c0 * rIn)).add(v.multiply(s0 * rIn));
            Vec3d p0_out = center.add(u.multiply(c0 * rOut)).add(v.multiply(s0 * rOut));
            Vec3d p1_in = center.add(u.multiply(c1 * rIn)).add(v.multiply(s1 * rIn));
            Vec3d p1_out = center.add(u.multiply(c1 * rOut)).add(v.multiply(s1 * rOut));

            int col = getActiveColor(t1, 255);
            int cr = (col >> 16) & 0xFF;
            int cg = (col >> 8) & 0xFF;
            int cb = col & 0xFF;

            lightBuf.vertex((float) (p0_in.x - camPos.x), (float) (p0_in.y - camPos.y), (float) (p0_in.z - camPos.z)).color(cr, cg, cb, 240);
            lightBuf.vertex((float) (p1_in.x - camPos.x), (float) (p1_in.y - camPos.y), (float) (p1_in.z - camPos.z)).color(cr, cg, cb, 240);
            lightBuf.vertex((float) (p1_out.x - camPos.x), (float) (p1_out.y - camPos.y), (float) (p1_out.z - camPos.z)).color(cr, cg, cb, 240);
            lightBuf.vertex((float) (p0_out.x - camPos.x), (float) (p0_out.y - camPos.y), (float) (p0_out.z - camPos.z)).color(cr, cg, cb, 240);

            if (isGlow) {
                Vec3d p0_glow = center.add(u.multiply(c0 * glowOut)).add(v.multiply(s0 * glowOut));
                Vec3d p1_glow = center.add(u.multiply(c1 * glowOut)).add(v.multiply(s1 * glowOut));

                lightBuf.vertex((float) (p0_out.x - camPos.x), (float) (p0_out.y - camPos.y), (float) (p0_out.z - camPos.z)).color(cr, cg, cb, 110);
                lightBuf.vertex((float) (p1_out.x - camPos.x), (float) (p1_out.y - camPos.y), (float) (p1_out.z - camPos.z)).color(cr, cg, cb, 110);
                lightBuf.vertex((float) (p1_glow.x - camPos.x), (float) (p1_glow.y - camPos.y), (float) (p1_glow.z - camPos.z)).color(cr, cg, cb, 0);
                lightBuf.vertex((float) (p0_glow.x - camPos.x), (float) (p0_glow.y - camPos.y), (float) (p0_glow.z - camPos.z)).color(cr, cg, cb, 0);
            }
        }

        double headAngle = startAngle - progress * 2.0 * Math.PI;
        double hc = Math.cos(headAngle);
        double hs = Math.sin(headAngle);
        float rMid = (rIn + rOut) * 0.5f;
        Vec3d headPos = center.add(u.multiply(hc * rMid)).add(v.multiply(hs * rMid));
        float sparkR = (rOut - rIn) * 0.55f;

        Vec3d sp0 = headPos.add(u.multiply(-sparkR)).add(v.multiply(-sparkR));
        Vec3d sp1 = headPos.add(u.multiply(sparkR)).add(v.multiply(-sparkR));
        Vec3d sp2 = headPos.add(u.multiply(sparkR)).add(v.multiply(sparkR));
        Vec3d sp3 = headPos.add(u.multiply(-sparkR)).add(v.multiply(sparkR));

        lightBuf.vertex((float) (sp0.x - camPos.x), (float) (sp0.y - camPos.y), (float) (sp0.z - camPos.z)).color(255, 255, 255, 255);
        lightBuf.vertex((float) (sp1.x - camPos.x), (float) (sp1.y - camPos.y), (float) (sp1.z - camPos.z)).color(255, 255, 255, 255);
        lightBuf.vertex((float) (sp2.x - camPos.x), (float) (sp2.y - camPos.y), (float) (sp2.z - camPos.z)).color(255, 255, 255, 255);
        lightBuf.vertex((float) (sp3.x - camPos.x), (float) (sp3.y - camPos.y), (float) (sp3.z - camPos.z)).color(255, 255, 255, 255);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (this.currentBreakingPos == null || this.smoothProgress < 0.01f) return;
        if (mc.world.isAir(this.currentBreakingPos)) return;
        if (!this.showPercent.get()) return;

        Direction side = this.currentBreakingSide != null ? this.currentBreakingSide : Direction.UP;
        Vec3d blockCenter = new Vec3d(
                this.currentBreakingPos.getX() + 0.5,
                this.currentBreakingPos.getY() + 0.5,
                this.currentBreakingPos.getZ() + 0.5
        );
        Vec3d center = blockCenter.add(Vec3d.of(side.getVector()).multiply(0.508));

        float tickDelta = tickCounter != null ? tickCounter.getTickProgress(false) : 1.0f;
        boolean onScreen = ProjectUtil.toScreen(center.x, center.y, center.z, tickDelta, scratchPos);
        if (!onScreen || !ProjectUtil.isOnScreen(scratchPos)) return;

        int percent = MathHelper.clamp((int) Math.round(this.smoothProgress * 100.0f), 1, 100);
        String text = percent + "%";

        Font font = Fonts.ROCKSTAR_ROUND_BOLD;
        float fontSize = 11.5f;
        float textW = font.a(text, fontSize);
        float textH = font.a(fontSize);

        float sx = scratchPos.x;
        float sy = scratchPos.y;

        int activeColor = getActiveColor(this.smoothProgress, 255);

        Matrix3x2f mat = new Matrix3x2f().translate(sx, sy);
        font.a(mat, text, -textW * 0.5f + 0.8f, -textH * 0.5f + 0.8f, fontSize, 0xCC000000);
        font.a(mat, text, -textW * 0.5f, -textH * 0.5f, fontSize, activeColor);
        DrawBatcher.flushPending();
    }
}
