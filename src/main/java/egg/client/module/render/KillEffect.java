package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KillEffect extends Module {

    private static KillEffect instance;

    private final BooleanSetting onlyTarget = new BooleanSetting("Только цель", true, "Создавать эффект только для атакованных вами врагов");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false, "Создавать эффект только при смерти игроков");
    private final SliderSetting amount = new SliderSetting("Количество", 85, 20, 250, 5, "Количество 3D осколков стекла");
    private final SliderSetting size = new SliderSetting("Размер", 0.22, 0.05, 0.60, 0.01, "Размер осколков стекла");
    private final SliderSetting speed = new SliderSetting("Скорость", 6.5, 2.0, 15.0, 0.5, "Скорость разлета осколков во все стороны");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 2.0, 0.5, 5.0, 0.1, "Длительность существования осколков (сек)");
    private final BooleanSetting bounce = new BooleanSetting("Отскок", true, "Отскок осколков стекла от блоков и пола");
    private final BooleanSetting sound = new BooleanSetting("Звук стекла", true, "Звук разбивающегося стекла при смерти");
    private final BooleanSetting syncColor = new BooleanSetting("Синхр. цвета", true, "Использовать цвет темы клиента");
    private final ColorSetting color = new ColorSetting("Цвет стекла", 0xFF88DDFF, "Пользовательский цвет стекла");
    private final BooleanSetting sparkle = new BooleanSetting("Блики", true, "Яркие вспышки и отблески на гранях стекла");

    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Set<Integer> deadEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public KillEffect() {
        super("KillEffect", "3D взрыв осколков стекла во все стороны на разных высотах при смерти противника", Category.ESP);
        instance = this;
        addSettings(onlyTarget, onlyPlayers, amount, size, speed, lifetime, bounce, sound, syncColor, color, sparkle);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    public void onAttack(Entity target) {
        if (!isEnabled() || target == null) return;
        recentAttacks.put(target.getId(), System.currentTimeMillis());
    }

    public void onEntityDeath(Entity entity) {
        if (!isEnabled() || entity == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || entity == mc.player) return;

        if (onlyPlayers.isEnabled() && !(entity instanceof PlayerEntity)) {
            return;
        }

        int entityId = entity.getId();
        if (onlyTarget.isEnabled()) {
            Long lastAttack = recentAttacks.get(entityId);
            if (lastAttack == null || (System.currentTimeMillis() - lastAttack) > 6000L) {
                return;
            }
        }

        if (deadEntities.add(entityId)) {
            recentAttacks.remove(entityId);
            spawnGlassBurst(entity);
        }
    }

    public void spawnGlassBurst(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d basePos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        double height = entity.getHeight();

        int count = Math.clamp((int) amount.get(), 10, 300);
        float baseSpeed = (float) speed.get();
        float baseSize = (float) size.get();
        float baseLife = (float) lifetime.get();
        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(basePos.x, basePos.y + height * 0.5, basePos.z,
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS,
                    1.2f, 0.95f + random.nextFloat() * 0.25f, false);
        }

        for (int i = 0; i < count; i++) {
            double spawnY = basePos.y + random.nextDouble() * height;
            double px = basePos.x + (random.nextDouble() - 0.5) * 0.35;
            double py = spawnY;
            double pz = basePos.z + (random.nextDouble() - 0.5) * 0.35;

            double azimuth = random.nextDouble() * Math.PI * 2.0;

            int tier = i % 3;
            double elevation;
            float speedTier;
            if (tier == 0) {
                elevation = -0.05 + random.nextDouble() * 0.25;
                speedTier = 1.15f;
            } else if (tier == 1) {
                elevation = 0.30 + random.nextDouble() * 0.45;
                speedTier = 1.00f;
            } else {
                elevation = 0.80 + random.nextDouble() * 0.55;
                speedTier = 1.25f;
            }

            float currentSpeed = baseSpeed * (0.65f + 0.65f * random.nextFloat()) * speedTier;
            double vHoriz = Math.cos(elevation) * currentSpeed;
            double vx = Math.cos(azimuth) * vHoriz;
            double vy = Math.sin(elevation) * currentSpeed;
            double vz = Math.sin(azimuth) * vHoriz;

            int shapeType = random.nextInt(4);
            float shardSize = baseSize * (0.75f + 0.55f * random.nextFloat());
            float shardLife = baseLife * (0.70f + 0.60f * random.nextFloat());

            float pitch = random.nextFloat() * 360.0f;
            float yaw = random.nextFloat() * 360.0f;
            float roll = random.nextFloat() * 360.0f;

            float rotPitch = (random.nextFloat() - 0.5f) * 750.0f;
            float rotYaw = (random.nextFloat() - 0.5f) * 750.0f;
            float rotRoll = (random.nextFloat() - 0.5f) * 750.0f;

            particles.add(new DeathGlassShard(
                    px, py, pz,
                    vx, vy, vz,
                    pitch, yaw, roll,
                    rotPitch, rotYaw, rotRoll,
                    shardSize, shardLife, shapeType, tint
            ));
        }

        while (particles.size() > 500) {
            particles.remove(0);
        }
    }

    public void render(VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) {
            if (!particles.isEmpty()) particles.clear();
            recentAttacks.clear();
            deadEntities.clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && !recentAttacks.isEmpty()) {
            long nowMs = System.currentTimeMillis();
            recentAttacks.entrySet().removeIf(entry -> {
                if (nowMs - entry.getValue() > 6000L) {
                    return true;
                }
                Entity target = mc.world.getEntityById(entry.getKey());
                if (target instanceof LivingEntity living && (!living.isAlive() || living.getHealth() <= 0.0f)) {
                    onEntityDeath(living);
                    return true;
                }
                return false;
            });
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        boolean doBounce = bounce.isEnabled() && mc.world != null;

        Iterator<DeathGlassShard> it = particles.iterator();
        while (it.hasNext()) {
            DeathGlassShard p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }

            p.vy -= 9.8f * dt;
            float drag = (float) Math.exp(-1.3f * dt);
            p.vx *= drag;
            p.vy *= drag;
            p.vz *= drag;

            p.pitch += p.rotPitch * dt;
            p.yaw += p.rotYaw * dt;
            p.roll += p.rotRoll * dt;

            double nextX = p.x + p.vx * dt;
            double nextY = p.y + p.vy * dt;
            double nextZ = p.z + p.vz * dt;

            if (doBounce) {
                BlockPos bp = BlockPos.ofFloored(nextX, nextY, nextZ);
                BlockState bs = mc.world.getBlockState(bp);
                if (!bs.isAir() && bs.isSolidBlock(mc.world, bp)) {
                    p.vy = -p.vy * 0.42;
                    p.vx *= 0.65;
                    p.vz *= 0.65;
                    p.rotPitch *= 0.6f;
                    p.rotYaw *= 0.6f;
                    p.rotRoll *= 0.6f;
                    nextY = p.y;
                }
            }

            p.x = nextX;
            p.y = nextY;
            p.z = nextZ;
        }

        if (particles.isEmpty()) return;

        VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
        Vector3f lightDir = new Vector3f(0.35f, 0.90f, 0.25f).normalize();

        for (DeathGlassShard p : particles) {
            float fadeProgress = 1.0f - (p.age / p.life);
            float alpha = MathHelper.clamp(fadeProgress * 1.5f, 0.0f, 1.0f);
            if (alpha <= 0.01f) continue;

            Quaternionf rot = new Quaternionf()
                    .rotationYXZ((float) Math.toRadians(p.yaw),
                                 (float) Math.toRadians(p.pitch),
                                 (float) Math.toRadians(p.roll));

            renderShard3D(quadBuffer, p, rot, camPos, lightDir, alpha);
        }

        if (sparkle.isEnabled()) {
            VertexConsumer glintBuffer = immediate.getBuffer(RenderLayers.lightning());
            for (DeathGlassShard p : particles) {
                float fadeProgress = 1.0f - (p.age / p.life);
                float alpha = MathHelper.clamp(fadeProgress * 1.5f, 0.0f, 1.0f);
                if (alpha <= 0.05f) continue;

                Vector3f toCam = new Vector3f((float) (camPos.x - p.x), (float) (camPos.y - p.y), (float) (camPos.z - p.z)).normalize();
                Quaternionf rot = new Quaternionf()
                        .rotationYXZ((float) Math.toRadians(p.yaw),
                                     (float) Math.toRadians(p.pitch),
                                     (float) Math.toRadians(p.roll));
                Vector3f frontNormal = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);

                float alignment = Math.abs(frontNormal.dot(toCam));
                if (alignment > 0.88f) {
                    float glintPower = (alignment - 0.88f) / 0.12f;
                    int glintAlpha = (int) (220.0f * alpha * glintPower);
                    renderGlintSparkle(glintBuffer, p, camPos, glintAlpha);
                }
            }
        }
    }

    private void renderShard3D(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos, Vector3f lightDir, float alphaFactor) {
        float s = p.size;
        float t = s * 0.26f;
        Vector3f toCam = new Vector3f((float) (camPos.x - p.x), (float) (camPos.y - p.y), (float) (camPos.z - p.z)).normalize();

        if (p.shapeType == 0) {
            float x0 = -0.45f * s, y0 = -0.40f * s;
            float x1 =  0.55f * s, y1 = -0.25f * s;
            float x2 = -0.15f * s, y2 =  0.65f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x2, y2, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x2, y2, -t, x1, y1, -t, x0, y0, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x0, y0, t);
        } else if (p.shapeType == 1) {
            float x0 =  0.00f * s, y0 = -0.60f * s;
            float x1 =  0.45f * s, y1 = -0.05f * s;
            float x2 = -0.05f * s, y2 =  0.60f * s;
            float x3 = -0.50f * s, y3 =  0.05f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x3, y3, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x3, y3, -t, x2, y2, -t, x1, y1, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x3, y3, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x3, y3, x0, y0, t);
        } else if (p.shapeType == 2) {
            float x0 = -0.50f * s, y0 = -0.35f * s;
            float x1 =  0.40f * s, y1 = -0.50f * s;
            float x2 =  0.55f * s, y2 =  0.30f * s;
            float x3 = -0.25f * s, y3 =  0.55f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x3, y3, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x3, y3, -t, x2, y2, -t, x1, y1, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x3, y3, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x3, y3, x0, y0, t);
        } else {
            float x0 = -0.12f * s, y0 = -0.75f * s;
            float x1 =  0.14f * s, y1 = -0.60f * s;
            float x2 =  0.05f * s, y2 =  0.80f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x2, y2, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x2, y2, -t, x1, y1, -t, x0, y0, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x0, y0, t);
        }
    }

    private void emitSideQuad(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos, Vector3f lightDir, Vector3f toCam, float alphaFactor,
                              float ax, float ay, float bx, float by, float t) {
        float dx = bx - ax;
        float dy = by - ay;
        Vector3f sideNorm = new Vector3f(dy, -dx, 0.0f).normalize().rotate(rot);

        int[] col = calculateFaceColor(p.tintColor, sideNorm, lightDir, toCam, alphaFactor, true);
        emitQuad3D(buf, p, rot, camPos,
                ax, ay,  t,
                bx, by,  t,
                bx, by, -t,
                ax, ay, -t,
                col[0], col[1], col[2], col[3]);
    }

    private int[] calculateFaceColor(int baseColor, Vector3f normal, Vector3f lightDir, Vector3f toCam, float alphaFactor, boolean isSide) {
        float dotLight = Math.abs(normal.dot(lightDir));
        float diff = 0.55f + 0.45f * dotLight;

        float dotCam = Math.abs(normal.dot(toCam));
        float fresnel = (float) Math.pow(1.0f - dotCam, 2.0);

        int br = (baseColor >> 16) & 0xFF;
        int bg = (baseColor >> 8) & 0xFF;
        int bb = baseColor & 0xFF;

        float faceMultiplier = isSide ? 1.25f : 1.0f;
        int r = MathHelper.clamp(Math.round(br * diff * faceMultiplier + 100.0f * fresnel), 0, 255);
        int g = MathHelper.clamp(Math.round(bg * diff * faceMultiplier + 100.0f * fresnel), 0, 255);
        int b = MathHelper.clamp(Math.round(bb * diff * faceMultiplier + 100.0f * fresnel), 0, 255);

        float baseAlpha = isSide ? 220.0f : 160.0f;
        int a = MathHelper.clamp(Math.round(baseAlpha * alphaFactor), 0, 255);

        return new int[]{r, g, b, a};
    }

    private void emitQuad3D(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            int r, int g, int b, int a) {
        Vector3f v0 = new Vector3f(x0, y0, z0).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, z1).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, z2).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, z3).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r, g, b, a);
    }

    private void renderGlintSparkle(VertexConsumer buf, DeathGlassShard p, Vec3d camPos, int alpha) {
        float gs = p.size * 0.85f;
        float gw = gs * 0.18f;

        float px = (float) (p.x - camPos.x);
        float py = (float) (p.y - camPos.y);
        float pz = (float) (p.z - camPos.z);

        buf.vertex(px - gw, py - gs, pz).color(255, 255, 255, 0);
        buf.vertex(px + gw, py - gs, pz).color(255, 255, 255, 0);
        buf.vertex(px + gw, py + gs, pz).color(255, 255, 255, alpha);
        buf.vertex(px - gw, py + gs, pz).color(255, 255, 255, alpha);

        buf.vertex(px - gs, py - gw, pz).color(255, 255, 255, 0);
        buf.vertex(px - gs, py + gw, pz).color(255, 255, 255, 0);
        buf.vertex(px + gs, py + gw, pz).color(255, 255, 255, alpha);
        buf.vertex(px + gs, py - gw, pz).color(255, 255, 255, alpha);
    }

    @Override
    public void onDisable() {
        particles.clear();
        recentAttacks.clear();
        deadEntities.clear();
    }

    private static class DeathGlassShard {
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotPitch, rotYaw, rotRoll;
        final float size;
        final float life;
        final int shapeType;
        final int tintColor;
        float age = 0.0f;

        DeathGlassShard(double x, double y, double z,
                        double vx, double vy, double vz,
                        float pitch, float yaw, float roll,
                        float rotPitch, float rotYaw, float rotRoll,
                        float size, float life, int shapeType, int tintColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.rotPitch = rotPitch;
            this.rotYaw = rotYaw;
            this.rotRoll = rotRoll;
            this.size = size;
            this.life = life;
            this.shapeType = shapeType;
            this.tintColor = tintColor;
        }
    }
}
