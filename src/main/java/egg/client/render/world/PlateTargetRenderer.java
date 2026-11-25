package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlateTargetRenderer {
    private static final long FADE_IN = 255_000_000L;
    private static final long FADE_OUT = 200_000_000L;
    private static final float SPARK_LIFE = 0.39f;
    private static final int COUNT = 56;
    private static final Plate[] PLATES = buildPlates();
    private static final Vec3d LIGHT = new Vec3d(-0.42, 0.80, 0.39).normalize();

    private final List<Fragment> fragments = new ArrayList<>();
    private final long[] released = new long[COUNT];
    private LivingEntity current;
    private long acquired;
    private long lastSeen;
    private int lastHurt;
    private BufferAllocator allocator;

    public void render(LivingEntity target, Vec3d camPos, float tickDelta) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            fragments.clear();
            java.util.Arrays.fill(released, 0);
            current = target;
            acquired = now;
            lastHurt = target.hurtTime;
        }
        if (target != null) lastSeen = now;
        if (current == null) return;
        if (!current.isAlive() || current.isRemoved() || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= FADE_OUT) {
            clear();
            return;
        }

        Vec3d feet = current.getLerpedPos(tickDelta);
        float height = current.getHeight();
        float width = current.getWidth();
        float time = seconds(now - acquired);
        float angle = MathHelper.lerpAngleDegrees(tickDelta, current.lastBodyYaw, current.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        float rangeFade = smooth(((float) feet.add(0, height * 0.54, 0).distanceTo(camPos) - 0.62f) / 0.78f);
        float opacity = rangeFade * smooth((float) (now - acquired) / FADE_IN)
                * (1 - smooth((float) (now - lastSeen) / FADE_OUT));
        boolean hit = current.hurtTime > lastHurt;
        lastHurt = current.hurtTime;
        if (opacity < 0.005f) return;

        if (hit) {
            for (int i = 0; i < COUNT; i++) {
                if (i % 5 != 0) continue;
                Plate plate = PLATES[i];
                Pose pose = pose(plate, feet, height, width, angle, time, 1);
                Vec3d radial = new Vec3d(pose.pos.x - feet.x, 0, pose.pos.z - feet.z).normalize();
                Vec3d velocity = radial.multiply(0.58 + plate.phase * 0.11)
                        .add(0, 0.38 + plate.phase * 0.06, 0);
                fragments.add(new Fragment(plate, pose, velocity, now));
                released[i] = now;
            }
        }
        fragments.removeIf(fragment -> seconds(now - fragment.born) >= SPARK_LIFE);

        if (allocator == null) allocator = new BufferAllocator(16384);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            float collect = smooth(time / 0.30f);
            for (int i = 0; i < COUNT; i++) {
                Plate plate = PLATES[i];
                float regain = released[i] == 0 ? 1 : smooth((seconds(now - released[i]) - 0.12f) / 0.29f);
                if (regain <= 0.005f) continue;
                emit(mesh, plate, pose(plate, feet, height, width, angle, time, collect), camPos, opacity * regain);
            }
            for (Fragment fragment : fragments) {
                float t = seconds(now - fragment.born);
                Pose initial = fragment.pose;
                Vec3d position = initial.pos.add(fragment.velocity.multiply(t)).add(0, -2.1 * t * t, 0);
                emit(mesh, fragment.plate, new Pose(position, initial.yaw + t * 6.3f, initial.tilt + t * 5.2f),
                        camPos, opacity * (1 - smooth(t / SPARK_LIFE)));
            }
            var built = mesh.endNullable();
            if (built != null) MeshDrawUtil.drawLabeled(Pipelines.TARGET_PLATES, built, "egg target plates");
        } finally {
            allocator.reset();
        }
    }

    private static Pose pose(Plate plate, Vec3d feet, float height, float width, float bodyYaw, float time, float collect) {
        float t = plate.level;
        float azimuth = plate.phase + bodyYaw + time * (1.08f + 0.25f * t) + t * 7.0f;
        float c = MathHelper.cos(azimuth);
        float s = MathHelper.sin(azimuth);
        float drift = 0.026f * MathHelper.sin(time * 2.2f + plate.phase * 1.6f);
        float shoulder = 0.85f + 0.22f * smooth((t - 0.35f) / 0.42f);
        float radius = 0.14f + width * shoulder + plate.offset + (1 - collect) * 0.16f + drift;
        float y = height * (0.04f + 0.93f * t + 0.031f * MathHelper.sin(azimuth * 1.4f));
        Vec3d center = feet.add(c * radius, y, s * radius * 0.78f);
        float tilt = 0.34f * MathHelper.sin(time * 2.4f + plate.phase) + plate.tilt;
        return new Pose(center, azimuth + 0.56f * MathHelper.sin(time * 1.9f + plate.phase * 2), tilt);
    }

    private static void emit(BufferBuilder mesh, Plate plate, Pose pose, Vec3d camera, float opacity) {
        float s = MathHelper.sin(pose.yaw);
        float c = MathHelper.cos(pose.yaw);
        float ct = MathHelper.cos(pose.tilt);
        float st = MathHelper.sin(pose.tilt);
        Vec3d tangent = new Vec3d(-s, 0, c);
        Vec3d incline = new Vec3d(c * st, ct, s * st);
        Vec3d normal = tangent.crossProduct(incline);
        Vec3d center = pose.pos.subtract(camera);
        Vec3d view = center.normalize().negate();
        Vec3d half = view.add(LIGHT).normalize();
        float sheen = (float) Math.pow(Math.abs(normal.dotProduct(half)), 9);
        float angle = (float) Math.abs(normal.dotProduct(view));
        float base = Math.clamp(0.58f + 0.33f * sheen + 0.11f * angle, 0, 1);
        int red = Math.round((185 + 66 * plate.tint) * base);
        int green = Math.round((220 + 29 * sheen - 15 * plate.tint) * base);
        int blue = Math.round((238 + 17 * sheen) * base);
        int alpha = Math.round(255 * opacity * (0.69f + 0.31f * sheen));
        Vec3d x = tangent.multiply(plate.size);
        Vec3d y = incline.multiply(plate.size * plate.aspect);
        vertex(mesh, center.subtract(x).subtract(y), 0, 0, red, green, blue, alpha);
        vertex(mesh, center.add(x).subtract(y), 1, 0, red, green, blue, alpha);
        vertex(mesh, center.add(x).add(y), 1, 1, red, green, blue, alpha);
        vertex(mesh, center.subtract(x).add(y), 0, 1, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder mesh, Vec3d p, float u, float v, int r, int g, int b, int alpha) {
        mesh.vertex((float) p.x, (float) p.y, (float) p.z).texture(u, v).color(r, g, b, alpha);
    }

    private static Plate[] buildPlates() {
        Random random = new Random(18547);
        Plate[] result = new Plate[COUNT];
        for (int i = 0; i < COUNT; i++) {
            float level = (i + random.nextFloat()) / COUNT;
            float phase = (i % 4) * MathHelper.HALF_PI + random.nextFloat() * 0.79f;
            result[i] = new Plate(level, phase, random.nextFloat() * 0.13f - 0.044f,
                    0.043f + random.nextFloat() * 0.041f, 0.34f + random.nextFloat() * 0.41f,
                    random.nextFloat() * 1.42f - 0.71f, random.nextFloat());
        }
        return result;
    }

    private static float seconds(long elapsed) {
        return elapsed * 1.0e-9f;
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public void clear() {
        current = null;
        fragments.clear();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private record Plate(float level, float phase, float offset, float size, float aspect, float tilt, float tint) {}
    private record Pose(Vec3d pos, float yaw, float tilt) {}
    private record Fragment(Plate plate, Pose pose, Vec3d velocity, long born) {}
}
