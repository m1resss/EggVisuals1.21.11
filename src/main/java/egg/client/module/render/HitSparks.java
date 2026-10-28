package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 1:1 HitSparks module ported from Deadcode (org.deadcode.client.modules.impl.visual.HitSparks).
 * Renders glowing ballistic needle sparks on entity attack with exact air resistance and gravity.
 */
public class HitSparks extends Module {

    private static HitSparks instance;

    private final BooleanSetting normalHits = new BooleanSetting("Обычные удары", false, "Создавать искры не только при критах, но и при обычных ударах");
    private final SliderSetting amount = new SliderSetting("Количество", 1.0f, 0.2f, 3.0f, 0.05f, "Множитель количества искр удара");
    private final BooleanSetting syncColor = new BooleanSetting("Синхр. цвета", true, "Использовать акцентный цвет текущей темы клиента");
    private final ColorSetting color = new ColorSetting("Цвет искр", 0xFFFFD700, "Пользовательский цвет искр");

    private final List<SparkParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public HitSparks() {
        super("HitSparks", "Искры удара из Deadcode: физика сопротивления воздуха, разлёт конусом и светящиеся иглы", Category.ESP);
        instance = this;
        addSettings(normalHits, amount, syncColor, color);
    }

    public static HitSparks getInstance() {
        return instance;
    }

    /**
     * Called on player attacking entity (from ClientPlayerInteractionManagerMixin).
     */
    public void onAttack(PlayerEntity player, Entity target) {
        if (!isEnabled() || target == null || !(target instanceof LivingEntity)) return;

        boolean isCrit = isCriticalHit(player, target);
        if (!isCrit && !normalHits.isEnabled()) {
            return;
        }

        Vec3d hitPos = getHitPosition(player, target);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d spray = new Vec3d(-look.x, -look.y * 0.3 + 0.55, -look.z);

        spawnBurst(hitPos, spray, isCrit, (float) amount.get());
    }

    /**
     * Checks if the attack satisfies Minecraft's critical hit criteria (Class1080.method7222 in Deadcode).
     */
    public static boolean isCriticalHit(PlayerEntity player, Entity target) {
        return target instanceof LivingEntity
                && player.getAttackCooldownProgress(0.5f) > 0.9f
                && player.fallDistance > 0.0
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting();
    }

    /**
     * Raycasts eye vector to entity bounding box (Class1080.method7223 in Deadcode).
     */
    public static Vec3d getHitPosition(PlayerEntity player, Entity target) {
        Vec3d eyePos = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d reachEnd = eyePos.add(look.multiply(8.0));
        Box box = target.getBoundingBox().expand(0.05);
        Optional<Vec3d> hitOpt = box.raycast(eyePos, reachEnd);
        return hitOpt.orElseGet(() -> new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.7, target.getZ()));
    }

    /**
     * Spawns a burst of needle sparks according to Deadcode's HitSparksRenderer.Style.
     */
    public void spawnBurst(Vec3d origin, Vec3d sprayAxis, boolean isCrit, float amountMul) {
        // Deadcode Style parameters:
        // Crit (field5658): count=38, speed=6.5, life=0.75, halfAngle=70 deg, width=2.0, length=2.4
        // Normal (NORMAL):  count=14, speed=4.5, life=0.50, halfAngle=55 deg, width=1.7, length=1.7
        int baseCount = isCrit ? 38 : 14;
        int count = Math.clamp(Math.round(baseCount * amountMul), 1, 96);
        float baseSpeed = isCrit ? 6.5f : 4.5f;
        float baseLife = isCrit ? 0.75f : 0.50f;
        float halfAngle = isCrit ? (float) Math.toRadians(70.0) : (float) Math.toRadians(55.0);
        float baseWidth = isCrit ? 2.0f : 1.7f;
        float baseLen = isCrit ? 2.4f : 1.7f;

        // Coordinate frame for the spray cone
        Vec3d axisD = sprayAxis.normalize();
        Vec3d helper = Math.abs(axisD.y) < 0.95 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d axisT = helper.crossProduct(axisD).normalize();
        Vec3d axisB = axisD.crossProduct(axisT);

        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();

        for (int i = 0; i < count; i++) {
            float randX = random.nextFloat();
            float randY = random.nextFloat();
            float randZ = random.nextFloat();
            float randR = random.nextFloat();
            float randG = random.nextFloat();
            float randB = random.nextFloat();
            float randA = random.nextFloat();

            // Uniform sampling inside solid angle of cone
            float cosA = MathHelper.lerp(randX, 1.0f, (float) Math.cos(halfAngle));
            float sinA = (float) Math.sqrt(Math.max(0.0f, 1.0f - cosA * cosA));
            float phi = (float) (2.0 * Math.PI * randY);

            Vec3d dir = axisT.multiply(Math.cos(phi) * sinA)
                    .add(axisB.multiply(Math.sin(phi) * sinA))
                    .add(axisD.multiply(cosA));

            float speed = baseSpeed * (0.4f + 0.9f * randZ);
            float life = baseLife * (0.5f + 1.0f * randR);
            float startDelay = 0.02f * randA;

            particles.add(new SparkParticle(origin, dir, speed, life, startDelay, randG, randB, baseWidth, baseLen, tint));
        }

        // Keep maximum 256 sparks active to guarantee peak 144+ FPS
        while (particles.size() > 256) {
            particles.remove(0);
        }
    }

    /**
     * Renders all active sparks in 3D world space (called from WorldRendererMixin).
     */
    public void render(VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) {
            if (!particles.isEmpty()) particles.clear();
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        Iterator<SparkParticle> it = particles.iterator();
        while (it.hasNext()) {
            SparkParticle p = it.next();
            p.age += dt;
            if (p.age >= p.life + p.startDelay) {
                it.remove();
            }
        }

        if (particles.isEmpty()) return;

        VertexConsumer buf = immediate.getBuffer(RenderLayers.debugQuads());

        for (SparkParticle p : particles) {
            float t = p.age - p.startDelay;
            if (t <= 0.0f || t >= p.life) continue;

            // Closed-form ballistic flight with air resistance (k=2.6) and gravity (w=8.0)
            float k = 2.6f;
            float g = 8.0f;
            float decay = (float) Math.exp(-k * t);

            double px = p.origin.x + p.dir.x * (p.speed * (1.0f - decay) / k);
            double py = p.origin.y + p.dir.y * (p.speed * (1.0f - decay) / k) - 0.5 * g * t * t;
            double pz = p.origin.z + p.dir.z * (p.speed * (1.0f - decay) / k);

            double vx = p.dir.x * (p.speed * decay);
            double vy = p.dir.y * (p.speed * decay) - g * t;
            double vz = p.dir.z * (p.speed * decay);

            // Vector to camera
            double rx = px - camPos.x;
            double ry = py - camPos.y;
            double rz = pz - camPos.z;
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (dist < 0.05) dist = 0.05;

            double toCamX = -rx / dist;
            double toCamY = -ry / dist;
            double toCamZ = -rz / dist;

            double vLen = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double alongX = vLen > 0.0001 ? vx / vLen : 0.0;
            double alongY = vLen > 0.0001 ? vy / vLen : 1.0;
            double alongZ = vLen > 0.0001 ? vz / vLen : 0.0;

            // Lateral axis perpendicular to speed vector and view
            double sideX = alongY * toCamZ - alongZ * toCamY;
            double sideY = alongZ * toCamX - alongX * toCamZ;
            double sideZ = alongX * toCamY - alongY * toCamX;
            double sideLen = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
            if (sideLen > 0.0001) {
                sideX /= sideLen;
                sideY /= sideLen;
                sideZ /= sideLen;
            } else {
                sideX = 1.0; sideY = 0.0; sideZ = 0.0;
            }

            float fade = 1.0f - (t / p.life);
            // Units per pixel scale for needle thickness
            float unitsPerPixel = (float) (dist / 700.0);
            float coreWidthPx = Math.max(1.0f, p.baseWidth * (0.75f + 0.5f * p.randG) * (0.7f + 0.3f * fade));
            float width = coreWidthPx * 3.0f * unitsPerPixel;
            float len = Math.max(coreWidthPx * 4.0f * unitsPerPixel, (float) (vLen * p.baseLen * (0.6f + 0.8f * p.randB) * 0.08f));

            float alpha = Math.min(1.0f, fade * 3.0f);
            if (alpha <= 0.005f) continue;

            int tr = (p.tintColor >> 16) & 0xFF;
            int tg = (p.tintColor >> 8) & 0xFF;
            int tb = p.tintColor & 0xFF;

            // Head color: intense hot core (mix of theme color and warm white-yellow)
            int headR = MathHelper.clamp((int) MathHelper.lerp(fade * 0.85f, tr, 255), 0, 255);
            int headG = MathHelper.clamp((int) MathHelper.lerp(fade * 0.85f, tg, 252), 0, 255);
            int headB = MathHelper.clamp((int) MathHelper.lerp(fade * 0.85f, tb, 235), 0, 255);
            int headAlpha = (int) (255 * alpha);

            // Tail color: fades to 0 quadratically
            int tailR = tr;
            int tailG = tg;
            int tailB = tb;
            int tailAlpha = 0;

            double hx = rx;
            double hy = ry;
            double hz = rz;

            double tx = hx - alongX * len;
            double ty = hy - alongY * len;
            double tz = hz - alongZ * len;

            // 1. Soft glowing outer halo
            double haloHw = width * 0.5;
            double hsx = sideX * haloHw;
            double hsy = sideY * haloHw;
            double hsz = sideZ * haloHw;
            int haloA = (int) (headAlpha * 0.40f);
            drawNeedleQuad(buf, hx, hy, hz, tx, ty, tz, hsx, hsy, hsz, tr, tg, tb, haloA, tailR, tailG, tailB, tailAlpha);

            // 2. Sharp hot needle core
            double coreHw = width * 0.18;
            double csx = sideX * coreHw;
            double csy = sideY * coreHw;
            double csz = sideZ * coreHw;
            drawNeedleQuad(buf, hx, hy, hz, tx, ty, tz, csx, csy, csz, headR, headG, headB, headAlpha, tailR, tailG, tailB, tailAlpha);
        }
    }

    private void drawNeedleQuad(VertexConsumer buf,
                                double hx, double hy, double hz,
                                double tx, double ty, double tz,
                                double sx, double sy, double sz,
                                int r1, int g1, int b1, int a1,
                                int r2, int g2, int b2, int a2) {
        buf.vertex((float) (hx - sx), (float) (hy - sy), (float) (hz - sz)).color(r1, g1, b1, a1);
        buf.vertex((float) (hx + sx), (float) (hy + sy), (float) (hz + sz)).color(r1, g1, b1, a1);
        buf.vertex((float) (tx + sx), (float) (ty + sy), (float) (tz + sz)).color(r2, g2, b2, a2);
        buf.vertex((float) (tx - sx), (float) (ty - sy), (float) (tz - sz)).color(r2, g2, b2, a2);
    }

    @Override
    public void onDisable() {
        particles.clear();
    }

    private static class SparkParticle {
        final Vec3d origin;
        final Vec3d dir;
        final float speed;
        final float life;
        final float startDelay;
        final float randG;
        final float randB;
        final float baseWidth;
        final float baseLen;
        final int tintColor;
        float age = 0.0f;

        SparkParticle(Vec3d origin, Vec3d dir, float speed, float life, float startDelay,
                      float randG, float randB, float baseWidth, float baseLen, int tintColor) {
            this.origin = origin;
            this.dir = dir;
            this.speed = speed;
            this.life = life;
            this.startDelay = startDelay;
            this.randG = randG;
            this.randB = randB;
            this.baseWidth = baseWidth;
            this.baseLen = baseLen;
            this.tintColor = tintColor;
        }
    }
}
