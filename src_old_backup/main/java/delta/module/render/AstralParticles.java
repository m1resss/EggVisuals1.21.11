package delta.module.render;
import delta.render.RenderStateHelper;

import static delta.core.Interface.aM_;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;

import net.minecraft.client.render.BufferBuilder;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.awt.Color;

@ModuleRegister(a = "Astral Particles", b = "Астральные плазменные кометы вокруг игрока (только от 3-го лица)", c = Category.Render)
public class AstralParticles extends Module implements Interface {

    private static final Identifier GLOW_TEXTURE = Identifier.of("delta", "textures/glowing.png");

    private final ModeSetting behavior = new ModeSetting("Режим", "Кометы", "Кометы", "Вихрь", "Сфера", "Хаос");

    private final SliderSetting cometsCount = new SliderSetting("Количество", 3.0f, 1.0f, 8.0f, 1.0f);
    private final SliderSetting tailLength = new SliderSetting("Длина хвоста", 16.0f, 4.0f, 36.0f, 1.0f);
    private final SliderSetting radius = new SliderSetting("Радиус", 1.25f, 0.4f, 4.0f, 0.05f);
    private final SliderSetting speed = new SliderSetting("Скорость", 3.5f, 0.5f, 10.0f, 0.1f);
    private final SliderSetting cometSize = new SliderSetting("Размер", 0.40f, 0.15f, 1.0f, 0.02f);
    private final SliderSetting heightOffset = new SliderSetting("Высота", 1.0f, 0.0f, 2.5f, 0.05f);
    private final SliderSetting hoverAmp = new SliderSetting("Колебание высоты", 0.15f, 0.0f, 0.6f, 0.02f);

    private final BooleanSetting coreGlow = new BooleanSetting("Свечение ядра", true);
    private final BooleanSetting additiveBlend = new BooleanSetting("Яркое свечение (Additive)", true);

    private final ModeSetting colorMode = new ModeSetting("Цвет", "Астрал", "Астрал", "Неон", "Тема", "Кастом", "Радуга", "Огонь", "Изумруд");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF9933FF).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));
    private final SliderSetting followLag = new SliderSetting("Плавность следования", 0.25f, 0.0f, 0.9f, 0.05f);

    private Vec3d smoothedTargetPos = null;
    private long lastTime = System.currentTimeMillis();
    private float alphaProgress = 0.0f;

    // 8 Pre-configured orbit orientation angles for multi-comet orbits
    private static final double[][] ORBIT_CONFIGS = new double[][]{
            {0.0, 0.0, 0.60},
            {Math.toRadians(36.0), Math.toRadians(18.0), -0.75},
            {Math.toRadians(-36.0), Math.toRadians(-18.0), 0.85},
            {Math.toRadians(24.0), Math.toRadians(-30.0), -0.65},
            {Math.toRadians(-24.0), Math.toRadians(30.0), 0.70},
            {Math.toRadians(42.0), Math.toRadians(0.0), -0.85},
            {Math.toRadians(-42.0), Math.toRadians(0.0), 0.85},
            {Math.toRadians(15.0), Math.toRadians(42.0), -0.75}
    };

    public AstralParticles() {
        a(this.behavior, this.cometsCount, this.tailLength, this.radius,
          this.speed, this.cometSize, this.heightOffset, this.hoverAmp, this.coreGlow,
          this.additiveBlend, this.colorMode, this.customColor, this.followLag);
    }

    @Override
    public void c() {
        super.c();
        this.smoothedTargetPos = null;
        this.alphaProgress = 0.0f;
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!event.c() || aM_.player == null || aM_.world == null || aM_.gameRenderer == null) {
            return;
        }

        // STRICT REQUIREMENT: NEVER render in first person, ONLY in 3rd person (F5)
        if (aM_.options.getPerspective().isFirstPerson()) {
            this.smoothedTargetPos = null;
            this.alphaProgress = 0.0f;
            return;
        }

        long now = System.currentTimeMillis();
        float dt = MathHelper.clamp((float) (now - this.lastTime) / 1000.0f, 0.001f, 0.1f);
        this.lastTime = now;

        // Strictly local player
        PlayerEntity player = aM_.player;
        if (!player.isAlive()) {
            this.alphaProgress = 0.0f;
            this.smoothedTargetPos = null;
            return;
        }

        this.alphaProgress = Math.min(1.0f, this.alphaProgress + dt * 4.5f);

        // Smooth player position tracking
        float tickDelta = event.g();
        double actualX = player.getLerpedPos(tickDelta).x;
        double actualY = player.getLerpedPos(tickDelta).y;
        double actualZ = player.getLerpedPos(tickDelta).z;
        Vec3d actualPos = new Vec3d(actualX, actualY, actualZ);

        if (this.smoothedTargetPos == null) {
            this.smoothedTargetPos = actualPos;
        } else {
            float lagVal = this.followLag.h().floatValue();
            float followSpeed = 1.0f - (lagVal * 0.90f);
            float alphaLag = Math.min(1.0f, dt * 18.0f * followSpeed);
            this.smoothedTargetPos = this.smoothedTargetPos.lerp(actualPos, alphaLag);
        }

        Vec3d center = this.smoothedTargetPos;
        double cy = center.y + this.heightOffset.h().floatValue();

        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        Quaternionf camRot = camera.getRotation();
        Matrix4f matrix = event.h().peek().getPositionMatrix();

        // Setup OpenGL state for Glowing Billboard Quads
        RenderStateHelper.enableBlend();
        if (Boolean.TRUE.equals(this.additiveBlend.h())) {
            RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        } else {
            RenderStateHelper.defaultBlendFunc();
        }
        RenderStateHelper.disableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(false);
        // RenderSystem.setShader(...);
        RenderStateHelper.setShaderTexture(0, GLOW_TEXTURE);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float curTimeSec = (float) (now % 10000000L) / 1000.0f;
        String curBehavior = this.behavior.h();

        // 1. Core Center Pulsing Glow (if enabled)
        if (Boolean.TRUE.equals(this.coreGlow.h())) {
            int[] coreRgb = getPaletteColor(0);
            float corePulse = 1.0f + MathHelper.sin(curTimeSec * 3.5f) * 0.12f;
            float coreRadius = this.radius.h().floatValue() * 0.85f * corePulse;
            float coreAlpha = this.alphaProgress * 80.0f;
            drawBillboard(buffer, matrix, camPos, camRot, center.x, cy, center.z, coreRadius, coreRgb[0], coreRgb[1], coreRgb[2], (int) coreAlpha);
        }

        // 2. Render Orbits according to behavior
        int count = this.cometsCount.h().intValue();
        int steps = this.tailLength.h().intValue();
        float baseR = this.radius.h().floatValue();
        float spd = this.speed.h().floatValue();
        float headSize = this.cometSize.h().floatValue();
        float hAmp = this.hoverAmp.h().floatValue();

        switch (curBehavior) {
            case "Кометы" -> renderOrbits(buffer, matrix, camPos, camRot, center.x, cy, center.z, curTimeSec, count, steps, baseR, spd, headSize, hAmp);
            case "Вихрь" -> renderVortex(buffer, matrix, camPos, camRot, center.x, cy, center.z, curTimeSec, count, steps, baseR, spd, headSize, hAmp);
            case "Сфера" -> renderSphere(buffer, matrix, camPos, camRot, center.x, cy, center.z, curTimeSec, count, steps, baseR, spd, headSize, hAmp);
            case "Хаос" -> renderChaos(buffer, matrix, camPos, camRot, center.x, cy, center.z, curTimeSec, count, steps, baseR, spd, headSize, hAmp);
            default -> renderOrbits(buffer, matrix, camPos, camRot, center.x, cy, center.z, curTimeSec, count, steps, baseR, spd, headSize, hAmp);
        }

        ImmediateBufferRenderer.draw(buffer.end());

        RenderStateHelper.enableCull();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.defaultBlendFunc();
    }

    private void renderOrbits(BufferBuilder buffer, Matrix4f matrix, Vec3d camPos, Quaternionf camRot,
                              double cx, double cy, double cz, float time, int count, int steps,
                              float baseR, float spd, float headSize, float hAmp) {
        float stepDt = 0.022f;

        for (int c = 0; c < count; c++) {
            double[] cfg = ORBIT_CONFIGS[c % ORBIT_CONFIGS.length];
            double pitch = cfg[0];
            double yaw = cfg[1];
            double sign = cfg[2];
            float cPhase = (float) (c * (Math.PI * 2.0 / count));

            for (int s = steps - 1; s >= 0; s--) {
                float trailTime = time - (s * stepDt);
                float angle = (float) ((trailTime * spd * sign) + cPhase);

                // Controlled horizontal orbit with gentle hovering elevation
                double localX = Math.cos(angle) * baseR;
                double localZ = Math.sin(angle) * baseR;
                double localY = Math.sin(angle * 2.0 + c) * hAmp;

                // Rotate by pitch and yaw
                double cosP = Math.cos(pitch);
                double sinP = Math.sin(pitch);
                double y1 = localY * cosP - localZ * sinP;
                double z1 = localY * sinP + localZ * cosP;

                double cosY = Math.cos(yaw);
                double sinY = Math.sin(yaw);
                double x2 = localX * cosY + z1 * sinY;
                double z2 = -localX * sinY + z1 * cosY;

                double wx = cx + x2;
                double wy = cy + y1;
                double wz = cz + z2;

                float prog = 1.0f - ((float) s / (float) steps);
                float size = (s == 0) ? headSize : (headSize * (0.18f + 0.72f * prog));
                float alpha = (s == 0) ? (this.alphaProgress * 255.0f) : (this.alphaProgress * (18.0f + 200.0f * prog * prog));

                int[] col = getPaletteColor(s);
                drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size, col[0], col[1], col[2], (int) alpha);

                // Intense bright nucleus inside comet head
                if (s == 0 && Boolean.TRUE.equals(this.coreGlow.h())) {
                    drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size * 0.45f, 255, 255, 255, (int) (this.alphaProgress * 250.0f));
                }
            }
        }
    }

    private void renderVortex(BufferBuilder buffer, Matrix4f matrix, Vec3d camPos, Quaternionf camRot,
                             double cx, double cy, double cz, float time, int count, int steps,
                             float baseR, float spd, float headSize, float hAmp) {
        float stepDt = 0.024f;

        for (int c = 0; c < count; c++) {
            float cPhase = (float) (c * (Math.PI * 2.0 / count));
            float phaseOff = (float) (c * 1.57f);

            for (int s = steps - 1; s >= 0; s--) {
                float trailTime = time - (s * stepDt);
                float angle = trailTime * spd + cPhase;
                float r = baseR * (0.85f + 0.25f * MathHelper.sin(angle * 0.5f));

                double wx = cx + Math.cos(angle) * r;
                double wz = cz + Math.sin(angle) * r;
                double wy = cy + Math.sin(angle * 1.5f + phaseOff) * hAmp;

                float prog = 1.0f - ((float) s / (float) steps);
                float size = (s == 0) ? headSize : (headSize * (0.2f + 0.75f * prog));
                float alpha = (s == 0) ? (this.alphaProgress * 255.0f) : (this.alphaProgress * (20.0f + 190.0f * prog * prog));

                int[] col = getPaletteColor(s);
                drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size, col[0], col[1], col[2], (int) alpha);

                if (s == 0 && Boolean.TRUE.equals(this.coreGlow.h())) {
                    drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size * 0.45f, 255, 255, 255, (int) (this.alphaProgress * 250.0f));
                }
            }
        }
    }

    private void renderSphere(BufferBuilder buffer, Matrix4f matrix, Vec3d camPos, Quaternionf camRot,
                             double cx, double cy, double cz, float time, int count, int steps,
                             float baseR, float spd, float headSize, float hAmp) {
        float stepDt = 0.022f;

        for (int c = 0; c < count; c++) {
            double phi = (c * Math.PI) / Math.max(1, count - 1) - (Math.PI / 2.0);
            double yaw = (c * 0.785f);

            for (int s = steps - 1; s >= 0; s--) {
                float trailTime = time - (s * stepDt);
                float theta = trailTime * spd;

                double x = baseR * Math.cos(phi) * Math.cos(theta);
                double y = Math.sin(phi) * hAmp;
                double z = baseR * Math.cos(phi) * Math.sin(theta);

                // Rotate by yaw
                double cosY = Math.cos(yaw);
                double sinY = Math.sin(yaw);
                double wx = cx + x * cosY - z * sinY;
                double wz = cz + x * sinY + z * cosY;
                double wy = cy + y;

                float prog = 1.0f - ((float) s / (float) steps);
                float size = (s == 0) ? headSize : (headSize * (0.2f + 0.75f * prog));
                float alpha = (s == 0) ? (this.alphaProgress * 255.0f) : (this.alphaProgress * (20.0f + 190.0f * prog * prog));

                int[] col = getPaletteColor(s);
                drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size, col[0], col[1], col[2], (int) alpha);

                if (s == 0 && Boolean.TRUE.equals(this.coreGlow.h())) {
                    drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size * 0.45f, 255, 255, 255, (int) (this.alphaProgress * 250.0f));
                }
            }
        }
    }

    private void renderChaos(BufferBuilder buffer, Matrix4f matrix, Vec3d camPos, Quaternionf camRot,
                            double cx, double cy, double cz, float time, int count, int steps,
                            float baseR, float spd, float headSize, float hAmp) {
        float stepDt = 0.022f;

        for (int c = 0; c < count; c++) {
            float cPhase = (float) (c * 2.14f);

            for (int s = steps - 1; s >= 0; s--) {
                float t = time - (s * stepDt);
                double wx = cx + Math.sin(t * spd + cPhase) * baseR;
                double wz = cz + Math.cos(t * spd * 1.3f + cPhase) * baseR;
                double wy = cy + Math.sin(t * spd * 0.8f + cPhase) * hAmp;

                float prog = 1.0f - ((float) s / (float) steps);
                float size = (s == 0) ? headSize : (headSize * (0.18f + 0.75f * prog));
                float alpha = (s == 0) ? (this.alphaProgress * 255.0f) : (this.alphaProgress * (20.0f + 195.0f * prog * prog));

                int[] col = getPaletteColor(s);
                drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size, col[0], col[1], col[2], (int) alpha);

                if (s == 0 && Boolean.TRUE.equals(this.coreGlow.h())) {
                    drawBillboard(buffer, matrix, camPos, camRot, wx, wy, wz, size * 0.45f, 255, 255, 255, (int) (this.alphaProgress * 250.0f));
                }
            }
        }
    }

    private void drawBillboard(BufferBuilder buffer, Matrix4f matrix, Vec3d camPos, Quaternionf camRot,
                               double worldX, double worldY, double worldZ, float size,
                               int r, int g, int b, int a) {
        if (a <= 0) return;
        float half = size * 0.5f;

        Vector3f v0 = new Vector3f(-half, -half, 0.0f);
        Vector3f v1 = new Vector3f(half, -half, 0.0f);
        Vector3f v2 = new Vector3f(half, half, 0.0f);
        Vector3f v3 = new Vector3f(-half, half, 0.0f);

        v0.rotate(camRot);
        v1.rotate(camRot);
        v2.rotate(camRot);
        v3.rotate(camRot);

        float rx = (float) (worldX - camPos.x);
        float ry = (float) (worldY - camPos.y);
        float rz = (float) (worldZ - camPos.z);

        buffer.vertex(matrix, rx + v0.x, ry + v0.y, rz + v0.z).texture(0.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix, rx + v1.x, ry + v1.y, rz + v1.z).texture(1.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix, rx + v2.x, ry + v2.y, rz + v2.z).texture(1.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, rx + v3.x, ry + v3.y, rz + v3.z).texture(0.0f, 0.0f).color(r, g, b, a);
    }

    private int[] getPaletteColor(int step) {
        String mode = this.colorMode.h();
        float factor = MathHelper.clamp((float) step / 20.0f, 0.0f, 1.0f);

        switch (mode) {
            case "Неон" -> {
                int r = (int) MathHelper.lerp(factor, 0.0f, 0.0f);
                int g = (int) MathHelper.lerp(factor, 255.0f, 180.0f);
                int b = (int) MathHelper.lerp(factor, 255.0f, 255.0f);
                return new int[]{r, g, b};
            }
            case "Тема" -> {
                int themeRgb = ThemeInfo.PRIMARY.a().a();
                int tr = (themeRgb >> 16) & 0xFF;
                int tg = (themeRgb >> 8) & 0xFF;
                int tb = themeRgb & 0xFF;
                int r = (int) MathHelper.lerp(factor, tr, (int) (tr * 0.4f));
                int g = (int) MathHelper.lerp(factor, tg, (int) (tg * 0.4f));
                int b = (int) MathHelper.lerp(factor, tb, (int) (tb * 0.4f));
                return new int[]{r, g, b};
            }
            case "Кастом" -> {
                int col = this.customColor.c().intValue();
                int cr = (col >> 16) & 0xFF;
                int cg = (col >> 8) & 0xFF;
                int cb = col & 0xFF;
                int r = (int) MathHelper.lerp(factor, cr, (int) (cr * 0.35f));
                int g = (int) MathHelper.lerp(factor, cg, (int) (cg * 0.35f));
                int b = (int) MathHelper.lerp(factor, cb, (int) (cb * 0.35f));
                return new int[]{r, g, b};
            }
            case "Радуга" -> {
                float hue = ((System.currentTimeMillis() % 4000L) / 4000.0f) + (step * 0.035f);
                int rgb = Color.HSBtoRGB(hue % 1.0f, 0.85f, 1.0f);
                return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
            }
            case "Огонь" -> {
                int r = 255;
                int g = (int) MathHelper.lerp(factor, 140.0f, 20.0f);
                int b = (int) MathHelper.lerp(factor, 20.0f, 0.0f);
                return new int[]{r, g, b};
            }
            case "Изумруд" -> {
                int r = (int) MathHelper.lerp(factor, 20.0f, 0.0f);
                int g = 255;
                int b = (int) MathHelper.lerp(factor, 120.0f, 40.0f);
                return new int[]{r, g, b};
            }
            default -> { // "Астрал"
                int r = (int) MathHelper.lerp(factor, 150.0f, 60.0f);
                int g = (int) MathHelper.lerp(factor, 50.0f, 180.0f);
                int b = (int) MathHelper.lerp(factor, 255.0f, 255.0f);
                return new int[]{r, g, b};
            }
        }
    }
}
