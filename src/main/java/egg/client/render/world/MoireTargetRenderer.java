package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.ByteBuffer;

public final class MoireTargetRenderer {
    private static final int SEGMENTS = 24;
    private static final int HEIGHT_STEPS = 4;
    private static final long ENTER_TIME = 200_000_000;
    private static final long FADE_TIME = 160_000_000;

    private LivingEntity current;
    private long entered;
    private long lastSeen;
    private BufferAllocator allocator;
    private DynamicUniformStorage<View> uniforms;

    public void render(LivingEntity target, Vec3d camera, float tickDelta, int accent) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            entered = now;
        }
        if (target != null) lastSeen = now;
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= FADE_TIME) {
            clear();
            return;
        }

        Vec3d feet = current.getLerpedPos(tickDelta).subtract(camera);
        float height = current.getHeight();
        float distance = (float) feet.add(0, height * 0.55f, 0).length();
        float opacity = smooth((now - entered) / (float) ENTER_TIME) * (1f - smooth((now - lastSeen) / (float) FADE_TIME));
        opacity *= smooth((distance - 0.66f) / 0.86f);
        if (opacity < 0.005f) return;

        float yaw = MathHelper.lerpAngleDegrees(tickDelta, current.lastBodyYaw, current.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        float sin = MathHelper.sin(yaw);
        float cos = MathHelper.cos(yaw);
        float width = current.getWidth();
        float time = (now - entered) * 1e-9f;
        int color = ColorUtil.withAlpha(accent, Math.round(255 * opacity));
        Frame frame = new Frame(feet, height, width * 0.77f + 0.15f, width * 0.64f + 0.08f, sin, cos, time, color);
        double bearing = Math.atan2(-feet.z, -feet.x) - yaw;

        if (allocator == null) allocator = new BufferAllocator(2 * SEGMENTS * HEIGHT_STEPS * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("Egg target moire", 16, 4);
        uniforms.clear();
        var view = uniforms.write(new View((float) Math.cos(bearing), (float) Math.sin(bearing), time));
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buildFlank(mesh, frame, 0, 0);
            buildFlank(mesh, frame, MathHelper.PI, 2);
            MeshDrawUtil.drawWithUniform(Pipelines.TARGET_MOIRE, mesh.end(), "Egg target moire", "MoireView", view);
        } finally {
            allocator.reset();
        }
    }

    private static void buildFlank(BufferBuilder mesh, Frame frame, float center, int textureOffset) {
        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = (float) i / SEGMENTS;
            float u1 = (i + 1f) / SEGMENTS;
            for (int j = 0; j < HEIGHT_STEPS; j++) {
                float v0 = (float) j / HEIGHT_STEPS;
                float v1 = (j + 1f) / HEIGHT_STEPS;
                vertex(mesh, frame, center, textureOffset, u0, v0);
                vertex(mesh, frame, center, textureOffset, u1, v0);
                vertex(mesh, frame, center, textureOffset, u1, v1);
                vertex(mesh, frame, center, textureOffset, u0, v1);
            }
        }
    }

    private static void vertex(BufferBuilder mesh, Frame frame, float center, int textureOffset, float u, float v) {
        float angle = center + (u - 0.5f) * 2.26f + (v - 0.5f) * 0.31f;
        float drift = MathHelper.sin(frame.time * 1.9f - u * 3.2f + center) * 0.024f;
        float fold = 0.026f * MathHelper.cos((v - 0.5f) * 4.9f);
        float localX = MathHelper.cos(angle) * (frame.radiusX + drift + fold);
        float localZ = MathHelper.sin(angle) * (frame.radiusZ + drift);
        float y = frame.height * (0.29f + v * 0.48f + 0.052f * MathHelper.sin(u * MathHelper.PI));
        float x = (float) frame.feet.x + localX * frame.cos - localZ * frame.sin;
        float z = (float) frame.feet.z + localX * frame.sin + localZ * frame.cos;
        mesh.vertex(x, (float) frame.feet.y + y, z).texture(u + textureOffset, v).color(frame.color);
    }

    public void clear() {
        current = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private record Frame(Vec3d feet, float height, float radiusX, float radiusZ, float sin, float cos, float time, int color) {}

    private record View(float x, float z, float time) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            buffer.putFloat(x).putFloat(z).putFloat(time).putFloat(0f);
        }
    }
}
