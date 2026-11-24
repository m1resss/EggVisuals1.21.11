package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

public final class IronDustTargetRenderer {
    private static final long ENTER_TIME = 220_000_000L;
    private static final long RELEASE_TIME = 150_000_000L;
    private static final long BURST_TIME = 430_000_000L;
    private static final float ARC_LENGTH = 2.30f;
    private static final int GRAINS_PER_LANE = 24;
    private static final Lane[] LANES = {
        new Lane(-0.9f, 0.62f, 0.20f),
        new Lane(1.18f, 0.44f, 0.26f),
        new Lane(3.30f, 0.54f, 0.16f)
    };
    private static final Grain[] GRAINS = createGrains();

    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private LivingEntity current;
    private long acquiredAt;
    private long lastSeen;
    private long hitAt;
    private int lastHurtTime;
    private BufferAllocator allocator;

    public void render(LivingEntity target, Camera camera, Vec3d cameraPos, float tickDelta) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            acquiredAt = now;
            lastHurtTime = target.hurtTime;
            hitAt = 0;
        }
        if (target != null) lastSeen = now;
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= RELEASE_TIME) {
            clear();
            return;
        }

        if (current.hurtTime > lastHurtTime) hitAt = now;
        lastHurtTime = current.hurtTime;

        Vec3d feet = current.getLerpedPos(tickDelta).subtract(cameraPos);
        float height = current.getHeight();
        float proximity = smooth(((float) feet.add(0, height * 0.55f, 0).length() - 0.56f) / 0.94f);
        float enter = smooth((now - acquiredAt) / (float) ENTER_TIME);
        float release = target == null ? 1.0f - smooth((now - lastSeen) / (float) RELEASE_TIME) : 1.0f;
        float opacity = proximity * enter * release;
        if (opacity < 0.005f) return;

        float hurtProgress = hitAt == 0 ? 1.0f : (now - hitAt) / (float) BURST_TIME;
        float burst = smooth(hurtProgress / 0.18f) * (1.0f - smooth((hurtProgress - 0.16f) / 0.84f));
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, current.lastBodyYaw, current.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        float width = current.getWidth();
        float age = (now - acquiredAt) / 1_000_000_000.0f;
        Frame frame = new Frame(feet, height, width * 0.79f + 0.15f, width * 0.43f + 0.11f,
                MathHelper.sin(yaw), MathHelper.cos(yaw), opacity, age, burst);
        right.set(1, 0, 0).rotate(camera.getRotation());
        up.set(0, 1, 0).rotate(camera.getRotation());

        if (allocator == null) allocator = new BufferAllocator(GRAINS.length * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int i = 0; i < GRAINS.length; i++) {
                drawGrain(mesh, frame, LANES[i / GRAINS_PER_LANE], GRAINS[i]);
            }
            var built = mesh.endNullable();
            if (built != null) MeshDrawUtil.drawLabeled(Pipelines.TARGET_IRON_DUST, built, "egg target iron dust");
        } finally {
            allocator.reset();
        }
    }

    private void drawGrain(BufferBuilder mesh, Frame frame, Lane lane, Grain grain) {
        float flow = frame.age * 1.47f;
        float along = grain.along + 0.025f * MathHelper.sin(frame.age * 3.1f + grain.phase);
        float theta = flow + lane.angle + (along - 0.5f) * ARC_LENGTH;
        theta += frame.burst * grain.drift * 0.44f;
        float wave = theta + lane.angle * 0.72f;
        float radiusOffset = grain.offset * 0.074f + frame.burst * grain.scatter;
        float radiusX = frame.radiusX + radiusOffset;
        float radiusZ = frame.radiusZ + radiusOffset * 0.84f;
        float sin = MathHelper.sin(theta);
        float cos = MathHelper.cos(theta);
        float localX = radiusX * cos;
        float localZ = radiusZ * sin;
        float height = lane.height + 0.093f * MathHelper.sin(wave) + grain.offset * 0.028f;
        height += frame.burst * grain.drift * 0.11f;
        float x = (float) frame.feet.x + localX * frame.yawCos - localZ * frame.yawSin;
        float y = (float) frame.feet.y + frame.height * MathHelper.clamp(height, 0.29f, 0.76f);
        float z = (float) frame.feet.z + localX * frame.yawSin + localZ * frame.yawCos;

        float dx = -radiusX * sin * frame.yawCos - radiusZ * cos * frame.yawSin;
        float dy = frame.height * 0.093f * MathHelper.cos(wave);
        float dz = -radiusX * sin * frame.yawSin + radiusZ * cos * frame.yawCos;
        float planeX = right.dot(dx, dy, dz);
        float planeY = up.dot(dx, dy, dz);
        float tilt = (float) Math.atan2(planeY, planeX) + 0.28f * grain.drift;
        tilt += frame.burst * grain.drift * 1.4f;
        float taper = smooth(along / 0.18f) * smooth((1.0f - along) / 0.20f);
        float halfLength = grain.size * (0.66f + 0.34f * taper);
        float halfWidth = grain.size * grain.aspect;
        float glint = 0.5f + 0.5f * MathHelper.sin(grain.phase + frame.age * 3.8f);
        int shade = Math.round(156 + 97 * glint);
        int alpha = Math.round(255 * frame.opacity * grain.opacity * (0.25f + 0.75f * taper));

        float tiltCos = MathHelper.cos(tilt);
        float tiltSin = MathHelper.sin(tilt);
        float uX = (right.x * tiltCos + up.x * tiltSin) * halfLength;
        float uY = (right.y * tiltCos + up.y * tiltSin) * halfLength;
        float uZ = (right.z * tiltCos + up.z * tiltSin) * halfLength;
        float vX = (up.x * tiltCos - right.x * tiltSin) * halfWidth;
        float vY = (up.y * tiltCos - right.y * tiltSin) * halfWidth;
        float vZ = (up.z * tiltCos - right.z * tiltSin) * halfWidth;
        mesh.vertex(x - uX - vX, y - uY - vY, z - uZ - vZ).texture(0, 0).color(shade, shade, shade, alpha);
        mesh.vertex(x + uX - vX, y + uY - vY, z + uZ - vZ).texture(1, 0).color(shade, shade, shade, alpha);
        mesh.vertex(x + uX + vX, y + uY + vY, z + uZ + vZ).texture(1, 1).color(shade, shade, shade, alpha);
        mesh.vertex(x - uX + vX, y - uY + vY, z - uZ + vZ).texture(0, 1).color(shade, shade, shade, alpha);
    }

    public void clear() {
        current = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static Grain[] createGrains() {
        Random random = new Random(762341);
        Grain[] grains = new Grain[LANES.length * GRAINS_PER_LANE];
        for (int i = 0; i < grains.length; i++) {
            float along = (i % GRAINS_PER_LANE + random.nextFloat()) / GRAINS_PER_LANE;
            float size = 0.013f + 0.029f * random.nextFloat();
            float aspect = 0.26f + 0.37f * random.nextFloat();
            float offset = (random.nextFloat() - 0.5f) * 2;
            float phase = random.nextFloat() * MathHelper.TAU;
            float drift = random.nextFloat() * 2 - 1;
            float scatter = 0.07f + random.nextFloat() * LANES[i / GRAINS_PER_LANE].scatter;
            float opacity = 0.56f + 0.40f * random.nextFloat();
            grains[i] = new Grain(along, size, aspect, offset, phase, drift, scatter, opacity);
        }
        return grains;
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record Lane(float angle, float height, float scatter) {}

    private record Grain(float along, float size, float aspect, float offset, float phase, float drift,
                         float scatter, float opacity) {}

    private record Frame(Vec3d feet, float height, float radiusX, float radiusZ, float yawSin, float yawCos,
                         float opacity, float age, float burst) {}
}
