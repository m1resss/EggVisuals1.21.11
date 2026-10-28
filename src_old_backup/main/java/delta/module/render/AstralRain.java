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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "Astral Rain", b = "Астральный плазменный дождь и метеоры в окружающем мире", c = Category.Render)
public class AstralRain extends Module implements Interface {

    private static final Identifier GLOW_TEXTURE = Identifier.of("delta", "textures/glowing.png");

    private final SliderSetting radius = new SliderSetting("Радиус мира", 24.0f, 8.0f, 50.0f, 1.0f);
    private final SliderSetting height = new SliderSetting("Высота неба", 16.0f, 6.0f, 32.0f, 1.0f);
    private final SliderSetting density = new SliderSetting("Количество", 60.0f, 15.0f, 200.0f, 5.0f);
    private final SliderSetting speed = new SliderSetting("Скорость падения", 8.0f, 2.0f, 25.0f, 0.5f);
    private final SliderSetting tailLength = new SliderSetting("Длина хвоста", 14.0f, 4.0f, 30.0f, 1.0f);
    private final SliderSetting cometSize = new SliderSetting("Размер комет", 0.35f, 0.12f, 0.85f, 0.02f);
    private final SliderSetting windDrift = new SliderSetting("Дрифт ветра", 0.0f, -10.0f, 10.0f, 0.5f);

    private final BooleanSetting splashes = new BooleanSetting("Всплески на земле", true);
    private final BooleanSetting coreGlow = new BooleanSetting("Свечение ядра", true);
    private final BooleanSetting additiveBlend = new BooleanSetting("Яркое свечение (Additive)", true);

    private final ModeSetting colorMode = new ModeSetting("Цвет", "Астрал", "Астрал", "Неон", "Тема", "Кастом", "Радуга", "Огонь", "Изумруд");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF9933FF).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));

    private final List<AstralDrop> drops = new ArrayList<>();
    private final List<Splash> activeSplashes = new ArrayList<>();
    private long lastTime = System.currentTimeMillis();
    private Vec3d lastCenter = null;

    public AstralRain() {
        a(this.radius, this.height, this.density, this.speed, this.tailLength,
          this.cometSize, this.windDrift, this.splashes, this.coreGlow,
          this.additiveBlend, this.colorMode, this.customColor);
    }

    @Override
    public void b() {
        super.b();
        this.drops.clear();
        this.activeSplashes.clear();
        this.lastCenter = null;
    }

    @Override
    public void c() {
        super.c();
        this.drops.clear();
        this.activeSplashes.clear();
        this.lastCenter = null;
    }

    private static class AstralDrop {
        double relX;
        double relZ;
        double y;
        double startY;
        float speedMult;
        float sizeMult;
        int seed;

        AstralDrop(double rX, double rZ, double curY, double sY, float sMult, float szMult, int s) {
            this.relX = rX;
            this.relZ = rZ;
            this.y = curY;
            this.startY = sY;
            this.speedMult = sMult;
            this.sizeMult = szMult;
            this.seed = s;
        }
    }

    private static class Splash {
        double x, y, z;
        float age;
        float maxAge;
        float maxRadius;

        Splash(double x, double y, double z, float maxAge, float maxRadius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = 0.0f;
            this.maxAge = maxAge;
            this.maxRadius = maxRadius;
        }
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!event.c() || aM_.player == null || aM_.world == null || aM_.gameRenderer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float dt = MathHelper.clamp((float) (now - this.lastTime) / 1000.0f, 0.001f, 0.1f);
        this.lastTime = now;

        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        Quaternionf camRot = camera.getRotation();
        Matrix4f matrix = event.h().peek().getPositionMatrix();

        float rVal = this.radius.h().floatValue();
        float hVal = this.height.h().floatValue();
        int targetCount = this.density.h().intValue();
        float fallSpeed = this.speed.h().floatValue();
        float wind = this.windDrift.h().floatValue();
        int tailSteps = this.tailLength.h().intValue();
        float headBaseSize = this.cometSize.h().floatValue();
        boolean doSplashes = Boolean.TRUE.equals(this.splashes.h());

        // Center on camera / player
        double centerX = camPos.x;
        double centerY = camPos.y;
        double centerZ = camPos.z;

        // Shift relative positions smoothly if camera moved
        if (this.lastCenter != null) {
            double dx = centerX - this.lastCenter.x;
            double dz = centerZ - this.lastCenter.z;
            for (AstralDrop drop : this.drops) {
                drop.relX -= dx;
                drop.relZ -= dz;
                // Wrap around cylinder
                if (drop.relX > rVal) drop.relX -= rVal * 2.0;
                else if (drop.relX < -rVal) drop.relX += rVal * 2.0;
                if (drop.relZ > rVal) drop.relZ -= rVal * 2.0;
                else if (drop.relZ < -rVal) drop.relZ += rVal * 2.0;
            }
        }
        this.lastCenter = new Vec3d(centerX, centerY, centerZ);

        // Adjust pool count
        while (this.drops.size() < targetCount) {
            double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * rVal;
            double rx = Math.cos(ang) * dist;
            double rz = Math.sin(ang) * dist;
            double sY = hVal * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4);
            double curY = ThreadLocalRandom.current().nextDouble() * sY;
            float sMult = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.35f;
            float szMult = 0.8f + ThreadLocalRandom.current().nextFloat() * 0.45f;
            int seed = ThreadLocalRandom.current().nextInt(1000);
            this.drops.add(new AstralDrop(rx, rz, curY, sY, sMult, szMult, seed));
        }
        while (this.drops.size() > targetCount) {
            this.drops.remove(this.drops.size() - 1);
        }

        // Setup OpenGL state for world glowing quads with depth testing against terrain
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

        // Update & Render each drop
        for (AstralDrop drop : this.drops) {
            drop.y -= fallSpeed * drop.speedMult * dt;
            drop.relX += wind * dt * 0.5;

            double headWx = centerX + drop.relX;
            double headWy = centerY + drop.y;
            double headWz = centerZ + drop.relZ;

            // Check if drop hit ground/block or fell below threshold
            BlockPos bPos = BlockPos.ofFloored(headWx, headWy, headWz);
            boolean hitBlock = false;
            if (aM_.world != null && aM_.world.isChunkLoaded((int) Math.floor(headWx) >> 4, (int) Math.floor(headWz) >> 4)) {
                if (!aM_.world.isAir(bPos)) {
                    var bState = aM_.world.getBlockState(bPos);
                    if (bState.blocksMovement() || !bState.getCollisionShape(aM_.world, bPos).isEmpty()) {
                        hitBlock = true;
                    }
                }
            }

            // When hitting floor relative to player feet (-1.6 blocks below camera) or hitting a block/roof
            if (drop.y <= -2.0 || hitBlock) {
                if (doSplashes) {
                    double splashWx = headWx;
                    double splashWy = hitBlock ? (bPos.getY() + 1.02) : (centerY - 1.8);
                    double splashWz = headWz;
                    this.activeSplashes.add(new Splash(splashWx, splashWy, splashWz, 0.35f, headBaseSize * 2.8f));
                }

                // Reset drop to top
                double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * rVal;
                drop.relX = Math.cos(ang) * dist;
                drop.relZ = Math.sin(ang) * dist;
                drop.startY = hVal * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4);
                drop.y = drop.startY;
            }

            float dropSize = headBaseSize * drop.sizeMult;
            float stepOffset = (fallSpeed * 0.024f) * (18.0f / (float) tailSteps);

            // Render trailing tail pointing upward along fall trajectory
            for (int s = tailSteps - 1; s >= 0; s--) {
                double tailY = headWy + (s * stepOffset);
                double tailX = headWx - (s * wind * 0.012);
                double tailZ = headWz;

                float prog = 1.0f - ((float) s / (float) tailSteps);
                float size = (s == 0) ? dropSize : (dropSize * (0.22f + 0.70f * prog));
                float alpha = (s == 0) ? 255.0f : (15.0f + 210.0f * prog * prog);

                int[] col = getPaletteColor(s + (drop.seed % 5));
                drawBillboard(buffer, matrix, camPos, camRot, tailX, tailY, tailZ, size, col[0], col[1], col[2], (int) alpha);

                // Bright core in head
                if (s == 0 && Boolean.TRUE.equals(this.coreGlow.h())) {
                    drawBillboard(buffer, matrix, camPos, camRot, tailX, tailY, tailZ, size * 0.40f, 255, 255, 255, 250);
                }
            }
        }

        // Update & Render splashes
        if (doSplashes && !this.activeSplashes.isEmpty()) {
            this.activeSplashes.removeIf(splash -> {
                splash.age += dt;
                return splash.age >= splash.maxAge;
            });

            for (Splash splash : this.activeSplashes) {
                float progress = splash.age / splash.maxAge;
                float curRadius = splash.maxRadius * (0.3f + 0.7f * progress);
                float splashAlpha = (1.0f - progress) * 220.0f;
                int[] col = getPaletteColor(0);

                // Ground splash billboard facing camera
                drawBillboard(buffer, matrix, camPos, camRot, splash.x, splash.y, splash.z, curRadius, col[0], col[1], col[2], (int) splashAlpha);
            }
        }

        ImmediateBufferRenderer.draw(buffer.end());

        RenderStateHelper.enableCull();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.defaultBlendFunc();
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
