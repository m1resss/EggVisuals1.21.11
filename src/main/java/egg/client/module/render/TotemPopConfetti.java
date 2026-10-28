package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TotemPopConfetti extends Module {

    private final SliderSetting count = new SliderSetting("Количество", 100, 30, 250, 10, "Число 3D частиц конфетти при выстреле");
    private final SliderSetting speed = new SliderSetting("Сила выстрела", 1.2f, 0.4f, 2.5f, 0.1f, "Начальная скорость разлета конфетти");
    private final SliderSetting gravity = new SliderSetting("Гравитация", 1.0f, 0.2f, 2.0f, 0.1f, "Сила тяжести при падении");
    private final SliderSetting flutter = new SliderSetting("Парение (флаттер)", 1.2f, 0.0f, 2.0f, 0.1f, "Аэродинамическое покачивание и скольжение бумаги");
    private final SliderSetting lifetime = new SliderSetting("Время жизни (сек)", 4.5f, 1.5f, 8.0f, 0.5f, "Сколько секунд конфетти кружится до исчезновения");
    private final BooleanSetting glow = new BooleanSetting("Свечение кружков", true, "Неоновое свечение желтых круглых конфетти");
    private final BooleanSetting cancelVanilla = new BooleanSetting("Скрыть дефолт частицы", true, "Отключает стандартные 2D частицы тотема");

    private final ButtonSetting testBtn = new ButtonSetting("Тест конфетти", "Произвести взрыв 3D конфетти прямо сейчас", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Vec3d origin = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight() * 0.7, mc.player.getZ());
            spawnBurst(origin);
        }
    });

    private final List<ConfettiParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    // Green shades for polygons (parallelograms, triangles, rectangles)
    private static final int[] GREEN_PALETTE = {
            0x2ECC71, // Emerald
            0x10B981, // Mint
            0x22C55E, // Bright Spring Green
            0x4ADE80, // Pastel Green
            0x16A34A, // Forest Green
            0x84CC16, // Lime
            0x059669  // Deep Jade
    };

    // Yellow shades for glowing circles
    private static final int[] YELLOW_PALETTE = {
            0xFFD700, // Gold
            0xFFFF33, // Neon Yellow
            0xFFE033, // Radiant Yellow
            0xFFF566  // Light Sun Yellow
    };

    public TotemPopConfetti() {
        super("TotemPopConfetti", "3D конфетти с физикой падения при попе тотема", Category.ESP);
        addSettings(count, speed, gravity, flutter, lifetime, glow, cancelVanilla, testBtn);
    }

    public boolean shouldCancelVanilla() {
        return isEnabled() && cancelVanilla.isEnabled();
    }

    public void onTotemPop(Entity entity) {
        if (!isEnabled() || entity == null) return;
        Vec3d origin = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.65, entity.getZ());
        spawnBurst(origin);
    }

    public void spawnBurst(Vec3d origin) {
        int num = count.getValueInt();
        float speedMul = speed.getValueFloat();
        float lifeSec = lifetime.getValueFloat();

        for (int i = 0; i < num; i++) {
            // Pick shape: 0 = PARALLELOGRAM, 1 = TRIANGLE, 2 = RECTANGLE, 3 = GLOWING_CIRCLE
            int typeIndex = i % 4;
            Shape shape = Shape.values()[typeIndex];

            ConfettiParticle p = new ConfettiParticle();
            p.shape = shape;
            p.x = origin.x + (random.nextDouble() - 0.5) * 0.2;
            p.y = origin.y + (random.nextDouble() - 0.5) * 0.2;
            p.z = origin.z + (random.nextDouble() - 0.5) * 0.2;

            // Explosion velocity: fountain cone upwards
            double azimuth = random.nextDouble() * Math.PI * 2.0;
            double elevation = 0.25 + random.nextDouble() * 1.15; // 15 to 80 degrees up
            double vMag = (0.16 + random.nextDouble() * 0.30) * speedMul;

            p.vx = Math.cos(azimuth) * Math.cos(elevation) * vMag;
            p.vz = Math.sin(azimuth) * Math.cos(elevation) * vMag;
            p.vy = Math.sin(elevation) * vMag + (0.10 + random.nextDouble() * 0.16) * speedMul;

            // Terminal fall speed for light paper
            p.terminalVelocity = 0.038f + random.nextFloat() * 0.035f;

            // 3D orientation & rotation
            p.pitch = random.nextFloat() * 360.0f;
            p.yaw = random.nextFloat() * 360.0f;
            p.roll = random.nextFloat() * 360.0f;

            // Angular velocity (tumble rate)
            p.rotSpeedPitch = (random.nextFloat() - 0.5f) * 450.0f;
            p.rotSpeedYaw = (random.nextFloat() - 0.5f) * 450.0f;
            p.rotSpeedRoll = (random.nextFloat() - 0.5f) * 450.0f;

            // Aerodynamic flutter
            p.flutterPhaseX = random.nextFloat() * 6.28f;
            p.flutterPhaseZ = random.nextFloat() * 6.28f;
            p.flutterFreqX = 3.5f + random.nextFloat() * 3.5f;
            p.flutterFreqZ = 3.0f + random.nextFloat() * 3.5f;
            p.flutterAmp = 0.015f + random.nextFloat() * 0.020f;

            p.age = 0.0f;
            p.maxAge = lifeSec * (0.85f + random.nextFloat() * 0.35f);

            // Shape dimensions & colors
            if (shape == Shape.GLOWING_CIRCLE) {
                p.radius = 0.045f + random.nextFloat() * 0.035f;
                int baseCol = YELLOW_PALETTE[random.nextInt(YELLOW_PALETTE.length)];
                p.r = (baseCol >> 16) & 0xFF;
                p.g = (baseCol >> 8) & 0xFF;
                p.b = baseCol & 0xFF;
            } else {
                int baseCol = GREEN_PALETTE[random.nextInt(GREEN_PALETTE.length)];
                p.r = (baseCol >> 16) & 0xFF;
                p.g = (baseCol >> 8) & 0xFF;
                p.b = baseCol & 0xFF;

                if (shape == Shape.PARALLELOGRAM) {
                    p.width = 0.08f + random.nextFloat() * 0.05f;
                    p.height = 0.05f + random.nextFloat() * 0.035f;
                    p.shear = p.width * (0.25f + random.nextFloat() * 0.25f);
                } else if (shape == Shape.TRIANGLE) {
                    p.width = 0.09f + random.nextFloat() * 0.05f;
                    p.height = 0.07f + random.nextFloat() * 0.04f;
                    p.shear = (random.nextFloat() - 0.5f) * p.width * 0.4f;
                } else { // RECTANGLE
                    p.width = 0.045f + random.nextFloat() * 0.025f;
                    p.height = 0.11f + random.nextFloat() * 0.06f;
                }
            }

            synchronized (particles) {
                particles.add(p);
            }
        }
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            synchronized (particles) {
                particles.clear();
            }
            return;
        }

        synchronized (particles) {
            if (particles.isEmpty()) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        float gravMul = gravity.getValueFloat();
        float flutterMul = flutter.getValueFloat();

        // 1. Physics update
        Iterator<ConfettiParticle> it = particles.iterator();
        while (it.hasNext()) {
            ConfettiParticle p = it.next();
            p.age += dt;
            if (p.age >= p.maxAge) {
                it.remove();
                continue;
            }

            if (!p.onGround) {
                // Drag (air friction)
                double drag = Math.pow(0.92, dt * 20.0);
                p.vx *= drag;
                p.vz *= drag;

                // Gravity
                p.vy -= 0.007 * gravMul * (dt * 20.0);

                // Terminal velocity for falling paper
                if (p.vy < -p.terminalVelocity) {
                    p.vy = MathHelper.lerp(0.08f * (dt * 20.0f), (float) p.vy, -p.terminalVelocity);
                }

                // Aerodynamic flutter / glide
                float swayX = (float) Math.sin(p.age * p.flutterFreqX + p.flutterPhaseX) * p.flutterAmp * flutterMul;
                float swayZ = (float) Math.cos(p.age * p.flutterFreqZ + p.flutterPhaseZ) * p.flutterAmp * flutterMul;

                double nextX = p.x + (p.vx + swayX) * (dt * 20.0);
                double nextY = p.y + p.vy * (dt * 20.0);
                double nextZ = p.z + (p.vz + swayZ) * (dt * 20.0);

                // Collision with blocks
                BlockPos bpos = BlockPos.ofFloored(nextX, nextY, nextZ);
                if (mc.world.isChunkLoaded(bpos.getX() >> 4, bpos.getZ() >> 4)) {
                    BlockState state = mc.world.getBlockState(bpos);
                    if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
                        p.onGround = true;
                        p.vx = 0;
                        p.vy = 0;
                        p.vz = 0;
                        p.y = bpos.getY() + 1.002;
                    } else {
                        p.x = nextX;
                        p.y = nextY;
                        p.z = nextZ;
                    }
                } else {
                    p.x = nextX;
                    p.y = nextY;
                    p.z = nextZ;
                }

                // 3D Tumbling rotation
                p.pitch += p.rotSpeedPitch * dt;
                p.yaw += p.rotSpeedYaw * dt;
                p.roll += p.rotSpeedRoll * dt;

                // Rotational damping
                float rotDamp = (float) Math.pow(0.985, dt * 20.0);
                p.rotSpeedPitch *= rotDamp;
                p.rotSpeedYaw *= rotDamp;
                p.rotSpeedRoll *= rotDamp;
            } else {
                // Resting on ground: settle flat gently
                p.pitch = MathHelper.lerp(dt * 8.0f, p.pitch, 0.0f);
                p.roll = MathHelper.lerp(dt * 8.0f, p.roll, 0.0f);
            }
        }

        if (particles.isEmpty()) return;

        // 2. Render 3D Paper Confetti
        // RenderLayers.debugQuads() uses POSITION_COLOR, translucent blend, no face culling (visible from both sides!)
        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());

        // Directional light vector from above-front
        Vector3f lightDir = new Vector3f(0.25f, 0.95f, 0.18f).normalize();

        for (ConfettiParticle p : particles) {
            // Fade-out over the last 1.2 seconds of lifetime
            float fadeProgress = (p.maxAge - p.age) / 1.2f;
            float alpha = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);
            if (alpha <= 0.005f) continue;

            int alphaInt = Math.round(alpha * 255.0f);

            // 3D rotation quaternion
            Quaternionf rot = new Quaternionf()
                    .rotationYXZ((float) Math.toRadians(p.yaw),
                                 (float) Math.toRadians(p.pitch),
                                 (float) Math.toRadians(p.roll));

            // Surface normal in world space (for realistic paper lighting)
            Vector3f normal = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            float dotLight = Math.abs(normal.dot(lightDir));
            float brightness = 0.65f + 0.35f * dotLight;

            // Camera view vector for backface shading
            float dx = (float) (p.x - camPos.x);
            float dy = (float) (p.y - camPos.y);
            float dz = (float) (p.z - camPos.z);
            Vector3f viewDir = new Vector3f(dx, dy, dz).normalize();
            boolean isFrontFace = normal.dot(viewDir) < 0;
            float faceMul = isFrontFace ? 1.0f : 0.82f; // Distinct front/back face shading!

            int cr = MathHelper.clamp(Math.round(p.r * brightness * faceMul), 0, 255);
            int cg = MathHelper.clamp(Math.round(p.g * brightness * faceMul), 0, 255);
            int cb = MathHelper.clamp(Math.round(p.b * brightness * faceMul), 0, 255);

            if (p.shape == Shape.PARALLELOGRAM) {
                renderParallelogram(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
            } else if (p.shape == Shape.TRIANGLE) {
                renderTriangle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
            } else if (p.shape == Shape.RECTANGLE) {
                renderRectangle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
            } else if (p.shape == Shape.GLOWING_CIRCLE) {
                renderCircle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
            }
        }

        // 3. Render glowing halos for yellow circles with additive lighting
        if (glow.isEnabled()) {
            VertexConsumer glowBuffer = consumers.getBuffer(RenderLayers.lightning());
            for (ConfettiParticle p : particles) {
                if (p.shape != Shape.GLOWING_CIRCLE) continue;

                float fadeProgress = (p.maxAge - p.age) / 1.2f;
                float alpha = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);
                if (alpha <= 0.01f) continue;

                Quaternionf rot = new Quaternionf()
                        .rotationYXZ((float) Math.toRadians(p.yaw),
                                     (float) Math.toRadians(p.pitch),
                                     (float) Math.toRadians(p.roll));

                int glowAlpha = Math.round(alpha * 140.0f);
                renderCircleGlow(glowBuffer, p, rot, camPos, glowAlpha);
            }
        }
    }
}

    private static final int CIRCLE_SEGS = 12;
    private static final float[] COS_TABLE = new float[CIRCLE_SEGS + 1];
    private static final float[] SIN_TABLE = new float[CIRCLE_SEGS + 1];

    static {
        for (int i = 0; i <= CIRCLE_SEGS; i++) {
            double ang = (i * 2.0 * Math.PI) / CIRCLE_SEGS;
            COS_TABLE[i] = (float) Math.cos(ang);
            SIN_TABLE[i] = (float) Math.sin(ang);
        }
    }

    private void renderRectangle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;

        emitQuad(buf, p, rot, camPos,
                -hw, -hh,
                 hw, -hh,
                 hw,  hh,
                -hw,  hh,
                r, g, b, a);
    }

    private void renderParallelogram(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;
        float s = p.shear;

        emitQuad(buf, p, rot, camPos,
                -hw,     -hh,
                 hw,     -hh,
                 hw + s,  hh,
                -hw + s,  hh,
                r, g, b, a);
    }

    private void renderTriangle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;
        float apex = p.shear;

        // Quad with 4th vertex collapsed to 3rd forms a true 3D triangle
        emitQuad(buf, p, rot, camPos,
                -hw,  -hh,
                 hw,  -hh,
                 apex, hh,
                 apex, hh,
                r, g, b, a);
    }

    private void renderCircle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float rad = p.radius;
        // Clean 12-segment triangle fan disc centered at (0, 0)
        for (int i = 0; i < CIRCLE_SEGS; i++) {
            float x1 = COS_TABLE[i] * rad;
            float y1 = SIN_TABLE[i] * rad;
            float x2 = COS_TABLE[i + 1] * rad;
            float y2 = SIN_TABLE[i + 1] * rad;

            emitQuad(buf, p, rot, camPos,
                    0.0f, 0.0f,
                    x1, y1,
                    x2, y2,
                    x2, y2,
                    r, g, b, a);
        }
    }

    private void renderCircleGlow(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int a) {
        float innerRad = p.radius * 0.55f;
        float outerRad = p.radius * 2.1f;

        // Radial glowing ring: smoothly fades from glowAlpha at circle boundary to 0 at outer perimeter
        // Circular 12-segment ring completely eliminates square boxes
        for (int i = 0; i < CIRCLE_SEGS; i++) {
            float inX0 = COS_TABLE[i] * innerRad;
            float inY0 = SIN_TABLE[i] * innerRad;
            float inX1 = COS_TABLE[i + 1] * innerRad;
            float inY1 = SIN_TABLE[i + 1] * innerRad;

            float outX0 = COS_TABLE[i] * outerRad;
            float outY0 = SIN_TABLE[i] * outerRad;
            float outX1 = COS_TABLE[i + 1] * outerRad;
            float outY1 = SIN_TABLE[i + 1] * outerRad;

            emitColoredQuad(buf, p, rot, camPos,
                    inX0, inY0, 255, 235, 75, a,
                    inX1, inY1, 255, 235, 75, a,
                    outX1, outY1, 255, 235, 75, 0,
                    outX0, outY0, 255, 235, 75, 0);
        }
    }

    private void emitQuad(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos,
                          float x0, float y0,
                          float x1, float y1,
                          float x2, float y2,
                          float x3, float y3,
                          int r, int g, int b, int a) {
        Vector3f v0 = new Vector3f(x0, y0, 0.0f).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, 0.0f).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, 0.0f).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, 0.0f).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r, g, b, a);
    }

    private void emitColoredQuad(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos,
                                 float x0, float y0, int r0, int g0, int b0, int a0,
                                 float x1, float y1, int r1, int g1, int b1, int a1,
                                 float x2, float y2, int r2, int g2, int b2, int a2,
                                 float x3, float y3, int r3, int g3, int b3, int a3) {
        Vector3f v0 = new Vector3f(x0, y0, 0.0f).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, 0.0f).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, 0.0f).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, 0.0f).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r0, g0, b0, a0);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r1, g1, b1, a1);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r2, g2, b2, a2);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r3, g3, b3, a3);
    }

    public enum Shape {
        PARALLELOGRAM,
        TRIANGLE,
        RECTANGLE,
        GLOWING_CIRCLE
    }

    private static class ConfettiParticle {
        Shape shape;
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotSpeedPitch, rotSpeedYaw, rotSpeedRoll;
        float terminalVelocity;

        float flutterPhaseX, flutterPhaseZ;
        float flutterFreqX, flutterFreqZ;
        float flutterAmp;

        float width, height, shear;
        float radius;
        int r, g, b;

        float age;
        float maxAge;
        boolean onGround = false;
    }
}
