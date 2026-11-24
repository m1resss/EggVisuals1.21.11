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

public final class WaterTargetRenderer {
    private static final long WRAP_TIME = 260_000_000L;
    private static final long RELEASE_TIME = 150_000_000L;
    private static final int SEGMENTS = 96;
    private static final int BANDS = 9;

    private LivingEntity current;
    private long acquiredAt;
    private long lastSeen;
    private BufferAllocator allocator;

    public void render(LivingEntity target, Vec3d camera, float tickDelta, float opacity) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            acquiredAt = now;
        }
        if (target != null) lastSeen = now;
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= RELEASE_TIME) {
            clear();
            return;
        }

        float wrap = smooth((now - acquiredAt) / (float) WRAP_TIME);
        float release = target == null ? 1 - smooth((now - lastSeen) / (float) RELEASE_TIME) : 1;
        Vec3d feet = current.getLerpedPos(tickDelta).subtract(camera);
        float height = current.getHeight();
        float proximity = smooth(((float) feet.add(0, height * 0.52f, 0).length() - 0.56f) / 0.75f);
        float alpha = opacity * release * proximity * smooth(wrap / 0.45f);
        if (alpha < 0.003f) return;

        float width = current.getWidth();
        float viewAngle = (float) Math.atan2(feet.z, feet.x);
        float time = (now - acquiredAt) / 1_000_000_000.0f;
        Frame frame = new Frame(feet, height, width * 0.73f + 0.15f, width * 0.57f + 0.13f,
                alpha, time, viewAngle, wrap, width * 0.48f + 0.15f);
        if (allocator == null) allocator = new BufferAllocator(SEGMENTS * BANDS * 4 * 24);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int segments = Math.max(1, Math.round(SEGMENTS * wrap));
            for (int i = 0; i < segments; i++) {
                float previous = wrap * i / segments;
                float next = wrap * (i + 1) / segments;
                for (int j = 0; j < BANDS; j++) {
                    float inner = (float) j / BANDS;
                    float outer = (j + 1.0f) / BANDS;
                    vertex(mesh, frame, previous, inner);
                    vertex(mesh, frame, next, inner);
                    vertex(mesh, frame, next, outer);
                    vertex(mesh, frame, previous, outer);
                }
            }
            var built = mesh.endNullable();
            if (built != null) MeshDrawUtil.drawLabeled(Pipelines.TARGET_WATER, built, "egg target water");
        } finally {
            allocator.reset();
        }
    }

    private static void vertex(BufferBuilder mesh, Frame frame, float along, float across) {
        float angle = frame.viewAngle + along * MathHelper.TAU;
        float motion = angle - frame.time * 2.55f;
        float roll = MathHelper.sin(motion * 3) * 0.57f + MathHelper.sin(motion * 7 + 0.9f) * 0.28f
                + MathHelper.sin(angle * 11 - frame.time * 5.2f) * 0.15f;
        float swell = MathHelper.sin(angle * 2 - frame.time * 1.7f);
        float outer = smooth(across);
        float curl = MathHelper.sin(across * MathHelper.PI);
        float distance = frame.thickness * across * (0.86f + 0.17f * roll) + 0.044f * swell + 0.032f * roll;
        float y = frame.height * (0.49f + 0.063f * swell + (0.034f + 0.038f * outer) * roll)
                + frame.height * (curl * (0.079f + 0.040f * roll) - 0.068f * across * across);
        float x = (float) frame.feet.x + MathHelper.cos(angle) * (frame.radiusX + distance);
        float z = (float) frame.feet.z + MathHelper.sin(angle) * (frame.radiusZ + distance);
        float endFade = frame.wrap >= 0.999f ? 1
                : smooth(along / 0.07f) * smooth((frame.wrap - along) / 0.09f);
        float front = MathHelper.cos(angle - frame.viewAngle);
        int alpha = Math.round(255 * frame.alpha * endFade * (0.77f - 0.17f * front));
        mesh.vertex(x, (float) frame.feet.y + y, z)
                .texture(angle / MathHelper.TAU - frame.time * 0.38f, across)
                .color(255, 255, 255, alpha);
    }

    public void clear() {
        current = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record Frame(Vec3d feet, float height, float radiusX, float radiusZ, float alpha, float time,
                         float viewAngle, float wrap, float thickness) {}
}
