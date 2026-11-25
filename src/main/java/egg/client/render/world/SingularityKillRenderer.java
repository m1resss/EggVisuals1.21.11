package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SingularityKillRenderer {
    private static final float COLLAPSE_TIME = 0.88f;
    private static final float BURST_END = 2.15f;
    private static final int INFLOW_COUNT = 64;
    private static final int BURST_COUNT = 78;
    private static final int DISK_SEGMENTS = 48;
    private static final int MAX_EFFECTS = 8;

    private final KillModelPass modelPass;
    private final List<Singularity> effects = new ArrayList<>();
    private final Random random = new Random();
    private final Vector3f cameraRight = new Vector3f();
    private final Vector3f cameraUp = new Vector3f();
    private BufferAllocator allocator;
    private ClientWorld world;

    public SingularityKillRenderer(KillModelPass modelPass) {
        this.modelPass = modelPass;
    }

    public boolean spawn(MinecraftClient client, Vec3d origin, double height, double width, EntityRenderState model, boolean sound) {
        if (client == null || client.world == null || client.player == null) return false;
        if (world != client.world) {
            clear();
            world = client.world;
        }

        float h = (float) Math.clamp(height, 0.75, 3.0);
        float w = (float) Math.clamp(width, 0.40, 1.65);
        Vec3d center = origin.add(0.0, h * 0.52, 0.0);

        List<InflowMote> inflows = new ArrayList<>(INFLOW_COUNT);
        for (int i = 0; i < INFLOW_COUNT; i++) {
            float angle = random.nextFloat() * MathHelper.TAU;
            float rad = w * (0.65f + random.nextFloat() * 1.45f);
            float yOff = (random.nextFloat() - 0.5f) * h * 1.15f;
            float birth = random.nextFloat() * 0.24f;
            float arrive = COLLAPSE_TIME * (0.58f + random.nextFloat() * 0.42f);
            float size = 0.032f + random.nextFloat() * 0.048f;
            float spinSpeed = 5.5f + random.nextFloat() * 6.5f;
            float hue = random.nextFloat();
            inflows.add(new InflowMote(angle, rad, yOff, birth, arrive, size, spinSpeed, hue));
        }

        List<BurstSpark> bursts = new ArrayList<>(BURST_COUNT);
        for (int i = 0; i < BURST_COUNT; i++) {
            double theta = random.nextDouble() * Math.TAU;
            double cosPhi = random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            Vec3d dir = new Vec3d(Math.cos(theta) * sinPhi, cosPhi * 0.85 + 0.08, Math.sin(theta) * sinPhi).normalize();
            float speed = 2.4f + random.nextFloat() * 5.2f;
            float life = 0.55f + random.nextFloat() * 0.65f;
            float size = 0.036f + random.nextFloat() * 0.062f;
            float hue = random.nextFloat();
            bursts.add(new BurstSpark(dir, speed, life, size, hue));
        }

        if (effects.size() >= MAX_EFFECTS) effects.removeFirst();
        effects.add(new Singularity(origin, center, h, w, model, inflows, bursts, System.nanoTime()));
        if (sound) {
            world.playSoundClient(center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    SoundCategory.PLAYERS, 0.55f, 0.62f, false);
        }
        return true;
    }

    public void render(MinecraftClient client, MatrixStack matrices, Camera camera, Vec3d camPos, boolean sound) {
        if (client == null || world != client.world) {
            clear();
            return;
        }
        long now = System.nanoTime();
        effects.removeIf(effect -> effect.age(now) >= BURST_END);
        if (effects.isEmpty()) return;

        for (Singularity effect : effects) {
            float age = effect.age(now);
            if (age < COLLAPSE_TIME) {
                float p = smooth(age / COLLAPSE_TIME);
                float accel = p * p;
                float spinRad = accel * 22.0f;
                float scaleXZ = MathHelper.lerp(accel, 1.0f, 0.055f);
                float scaleY = MathHelper.lerp(accel * 0.92f, 1.0f, 0.065f);
                float flash = smooth((age - 0.56f) / (COLLAPSE_TIME - 0.56f));
                float opacity = 1.0f - smooth((age - (COLLAPSE_TIME - 0.09f)) / 0.09f);
                modelPass.render(client, effect.model, effect.origin, effect.height, matrices, camera, camPos,
                        Pipelines.SINGULARITY_MODEL, p, flash, age, opacity, spinRad, scaleXZ, scaleY);
            }
            if (age >= COLLAPSE_TIME - 0.02f && !effect.burstPlayed) {
                effect.burstPlayed = true;
                if (sound && world != null) {
                    world.playSoundClient(effect.center.x, effect.center.y, effect.center.z,
                            SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.PLAYERS, 0.68f, 0.85f, false);
                    world.playSoundClient(effect.center.x, effect.center.y, effect.center.z,
                            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.52f, 1.25f, false);
                }
            }
        }

        cameraRight.set(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        cameraUp.set(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());

        if (allocator == null) allocator = new BufferAllocator(65536);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (Singularity effect : effects) {
                float age = effect.age(now);
                Vec3d centerRel = effect.center.subtract(camPos);
                drawHorizonAndHalo(mesh, effect, centerRel, age);
                drawAccretionDisk(mesh, effect, centerRel, age);
                drawInflowMotes(mesh, effect, centerRel, age);
                if (age >= COLLAPSE_TIME - 0.04f) {
                    drawPeakFlashAndBurst(mesh, effect, centerRel, age);
                }
            }
            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.SINGULARITY_EFFECT, built, "Egg singularity effect");
            }
        } finally {
            allocator.reset();
        }
    }

    private void drawHorizonAndHalo(BufferBuilder mesh, Singularity effect, Vec3d centerRel, float age) {
        float grow = smooth(age / 0.24f);
        float pinch = age < COLLAPSE_TIME
                ? MathHelper.lerp(smooth((age - 0.62f) / (COLLAPSE_TIME - 0.62f)), 1.0f, 0.34f)
                : MathHelper.lerp(smooth((age - COLLAPSE_TIME) / 0.38f), 0.34f, 0.0f);
        if (pinch <= 0.01f) return;

        float radius = (0.34f + 0.14f * effect.width) * grow * (0.45f + 0.55f * pinch);
        float alpha = grow * (age < COLLAPSE_TIME ? 0.96f : (1.0f - smooth((age - COLLAPSE_TIME) / 0.38f)));
        int col = color(146, 108, 255, alpha);
        billboard(mesh, centerRel, radius, 0.0f, col);
    }

    private void drawAccretionDisk(BufferBuilder mesh, Singularity effect, Vec3d centerRel, float age) {
        float enter = smooth(age / 0.22f);
        float vanish = age < COLLAPSE_TIME ? 1.0f : 1.0f - smooth((age - COLLAPSE_TIME) / 0.42f);
        float alpha = enter * vanish * 0.85f;
        if (alpha <= 0.01f) return;

        float pull = age < COLLAPSE_TIME
                ? MathHelper.lerp(smooth(age / COLLAPSE_TIME) * smooth(age / COLLAPSE_TIME), 1.0f, 0.26f)
                : MathHelper.lerp(smooth((age - COLLAPSE_TIME) / 0.42f), 0.26f, 1.35f);
        float rMid = (effect.width * 0.82f + 0.36f) * pull;
        float halfBand = (0.09f + 0.05f * enter) * Math.max(0.35f, pull);
        float pitch = 0.46f;
        float roll = -0.22f;
        float spin = age * (4.5f + 7.5f * smooth(age / COLLAPSE_TIME));

        emitTiltedRing(mesh, centerRel, rMid - halfBand, rMid + halfBand, pitch, roll, spin, 2.0f,
                color(134, 96, 255, alpha));
        emitTiltedRing(mesh, centerRel, rMid * 0.72f - halfBand * 0.7f, rMid * 0.72f + halfBand * 0.7f,
                -0.38f, 0.30f, -spin * 1.25f + 1.1f, 2.0f,
                color(115, 212, 255, alpha * 0.85f));
    }

    private void drawInflowMotes(BufferBuilder mesh, Singularity effect, Vec3d centerRel, float age) {
        if (age >= COLLAPSE_TIME) return;
        for (InflowMote mote : effect.inflows) {
            if (age <= mote.birth || age >= mote.arrive) continue;
            float t = (age - mote.birth) / (mote.arrive - mote.birth);
            float ease = t * t;
            float r = mote.radius * (1.0f - ease);
            float angle = mote.angle + mote.spinSpeed * (t + ease * 1.4f);
            float y = mote.yOffset * (1.0f - ease);
            Vec3d pos = centerRel.add(MathHelper.cos(angle) * r, y, MathHelper.sin(angle) * r);
            float alpha = smooth(t / 0.18f) * (1.0f - smooth((t - 0.82f) / 0.18f)) * 0.92f;
            float size = mote.size * (1.1f - 0.55f * ease);
            int col = mote.hue < 0.45f
                    ? color(166, 118, 255, alpha)
                    : (mote.hue < 0.82f ? color(120, 216, 255, alpha) : color(240, 246, 255, alpha));
            billboard(mesh, pos, size, 4.0f, col);
        }
    }

    private void drawPeakFlashAndBurst(BufferBuilder mesh, Singularity effect, Vec3d centerRel, float age) {
        float sinceBurst = age - COLLAPSE_TIME;

        if (sinceBurst >= -0.04f && sinceBurst < 0.52f) {
            float ft = MathHelper.clamp((sinceBurst + 0.04f) / 0.56f, 0.0f, 1.0f);
            float rise = smooth(ft / 0.14f);
            float fade = 1.0f - smooth((ft - 0.14f) / 0.86f);
            float starRadius = (0.38f + 0.52f * rise) * (1.0f - 0.35f * smooth((ft - 0.3f) / 0.7f));
            billboard(mesh, centerRel, starRadius, 4.0f, color(236, 244, 255, rise * fade));
            billboard(mesh, centerRel, starRadius * 1.45f, 4.0f, color(152, 128, 255, rise * fade * 0.72f));
        }

        if (sinceBurst >= 0.0f && sinceBurst < 0.58f) {
            float st = sinceBurst / 0.58f;
            float waveEase = 1.0f - (1.0f - st) * (1.0f - st);
            float waveR = 0.08f + waveEase * (1.55f + effect.width * 0.65f);
            float waveW = 0.14f * (1.0f - 0.6f * st);
            float waveAlpha = (1.0f - smooth((st - 0.15f) / 0.85f)) * 0.90f;
            emitTiltedRing(mesh, centerRel, Math.max(0.02f, waveR - waveW), waveR + waveW,
                    0.18f, -0.10f, sinceBurst * 3.0f, 2.0f,
                    color(142, 218, 255, waveAlpha));
        }

        if (sinceBurst >= 0.0f) {
            for (BurstSpark spark : effect.bursts) {
                if (sinceBurst >= spark.life) continue;
                float t = sinceBurst / spark.life;
                float dist = spark.speed * spark.life * (1.0f - (float) Math.exp(-3.4 * t)) / 3.4f;
                Vec3d pos = centerRel.add(spark.dir.multiply(dist)).add(0.0, -0.35 * t * t, 0.0);
                float fade = 1.0f - smooth((t - 0.28f) / 0.72f);
                float size = spark.size * (1.0f - 0.45f * t);
                int col = spark.hue < 0.35f
                        ? color(245, 248, 255, fade)
                        : (spark.hue < 0.72f ? color(122, 218, 255, fade) : color(168, 120, 255, fade));
                billboard(mesh, pos, size, 4.0f, col);
            }
        }
    }

    private void emitTiltedRing(BufferBuilder mesh, Vec3d center, float rIn, float rOut,
                                float pitch, float roll, float spinOffset, float uOffset, int col) {
        if (rOut <= rIn + 0.002f) return;
        float cosP = MathHelper.cos(pitch);
        float sinP = MathHelper.sin(pitch);
        float cosR = MathHelper.cos(roll);
        float sinR = MathHelper.sin(roll);

        for (int i = 0; i < DISK_SEGMENTS; i++) {
            float u0 = uOffset + (float) i / DISK_SEGMENTS;
            float u1 = uOffset + (float) (i + 1) / DISK_SEGMENTS;
            float a0 = ((float) i / DISK_SEGMENTS) * MathHelper.TAU + spinOffset;
            float a1 = ((float) (i + 1) / DISK_SEGMENTS) * MathHelper.TAU + spinOffset;

            Vec3d p0In = center.add(tiltedPoint(a0, rIn, cosP, sinP, cosR, sinR));
            Vec3d p0Out = center.add(tiltedPoint(a0, rOut, cosP, sinP, cosR, sinR));
            Vec3d p1Out = center.add(tiltedPoint(a1, rOut, cosP, sinP, cosR, sinR));
            Vec3d p1In = center.add(tiltedPoint(a1, rIn, cosP, sinP, cosR, sinR));

            mesh.vertex((float) p0In.x, (float) p0In.y, (float) p0In.z).texture(u0, 0.0f).color(col);
            mesh.vertex((float) p0Out.x, (float) p0Out.y, (float) p0Out.z).texture(u0, 1.0f).color(col);
            mesh.vertex((float) p1Out.x, (float) p1Out.y, (float) p1Out.z).texture(u1, 1.0f).color(col);
            mesh.vertex((float) p1In.x, (float) p1In.y, (float) p1In.z).texture(u1, 0.0f).color(col);
        }
    }

    private static Vec3d tiltedPoint(float angle, float radius, float cosP, float sinP, float cosR, float sinR) {
        float lx = MathHelper.cos(angle) * radius;
        float lz = MathHelper.sin(angle) * radius;
        float y1 = -lz * sinP;
        float z1 = lz * cosP;
        float rx = lx * cosR - y1 * sinR;
        float ry = lx * sinR + y1 * cosR;
        return new Vec3d(rx, ry, z1);
    }

    private void billboard(BufferBuilder mesh, Vec3d center, float halfSize, float uOffset, int color) {
        float rx = cameraRight.x * halfSize;
        float ry = cameraRight.y * halfSize;
        float rz = cameraRight.z * halfSize;
        float ux = cameraUp.x * halfSize;
        float uy = cameraUp.y * halfSize;
        float uz = cameraUp.z * halfSize;

        float px = (float) center.x;
        float py = (float) center.y;
        float pz = (float) center.z;

        mesh.vertex(px - rx + ux, py - ry + uy, pz - rz + uz).texture(uOffset, 0.0f).color(color);
        mesh.vertex(px - rx - ux, py - ry - uy, pz - rz - uz).texture(uOffset, 1.0f).color(color);
        mesh.vertex(px + rx - ux, py + ry - uy, pz + rz - uz).texture(uOffset + 1.0f, 1.0f).color(color);
        mesh.vertex(px + rx + ux, py + ry + uy, pz + rz + uz).texture(uOffset + 1.0f, 0.0f).color(color);
    }

    public void clear() {
        effects.clear();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int color(int r, int g, int b, float alpha) {
        int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record InflowMote(float angle, float radius, float yOffset, float birth, float arrive,
                              float size, float spinSpeed, float hue) {}

    private record BurstSpark(Vec3d dir, float speed, float life, float size, float hue) {}

    private static final class Singularity {
        final Vec3d origin;
        final Vec3d center;
        final float height;
        final float width;
        final EntityRenderState model;
        final List<InflowMote> inflows;
        final List<BurstSpark> bursts;
        final long startNanos;
        boolean burstPlayed;

        Singularity(Vec3d origin, Vec3d center, float height, float width, EntityRenderState model,
                    List<InflowMote> inflows, List<BurstSpark> bursts, long startNanos) {
            this.origin = origin;
            this.center = center;
            this.height = height;
            this.width = width;
            this.model = model;
            this.inflows = inflows;
            this.bursts = bursts;
            this.startNanos = startNanos;
        }

        float age(long now) {
            return (now - startNanos) / 1_000_000_000.0f;
        }
    }
}
