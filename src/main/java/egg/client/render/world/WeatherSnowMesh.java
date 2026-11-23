package egg.client.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

final class WeatherSnowMesh {
    static final int RADIUS = 42;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private GpuBuffer uniforms;

    void draw(WeatherSurface surface, Vec3d camera, float amount, float depth, float sheen, WeatherSky.State sky) {
        int centerX = MathHelper.floor(camera.x);
        int centerZ = MathHelper.floor(camera.z);
        try (BufferAllocator allocator = new BufferAllocator(256 * 1024)) {
            BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
                for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
                    if (!surface.solid(x, z)) continue;
                    int top = surface.height(x, z);
                    float relativeY = (float) (top - camera.y);
                    if (Math.abs(relativeY) > 32) continue;
                    float distance = (float) Math.hypot(x + 0.5 - camera.x, z + 0.5 - camera.z);
                    float edge = 1 - WeatherProfile.smooth((distance - RADIUS + 8) / 8);
                    if (edge < 0.005f) continue;

                    float near = 1 - Math.min(1, distance / RADIUS);
                    long position = (long) x * 73428767 ^ (long) z * 912931;
                    float coverage = Math.min(1, amount * depth * (1.37f - WeatherProfile.noise(position, 0) * 0.44f));
                    int alpha = Math.round(255 * edge * coverage);
                    if (alpha < 2) continue;

                    int color = alpha << 24 | 0xFFFBF7;
                    float lift = Math.min(0.045f, 0.04f * amount * depth) * (0.33f + 0.67f * near);
                    float x0 = (float) (x - camera.x);
                    float z0 = (float) (z - camera.z);
                    builder.vertex(x0, relativeY + height(x, z, lift), z0).color(color);
                    builder.vertex(x0, relativeY + height(x, z + 1, lift), z0 + 1).color(color);
                    builder.vertex(x0 + 1, relativeY + height(x + 1, z + 1, lift), z0 + 1).color(color);
                    builder.vertex(x0 + 1, relativeY + height(x + 1, z, lift), z0).color(color);
                }
            }
            BuiltBuffer mesh = builder.endNullable();
            if (mesh == null) return;
            updateUniforms(camera, sky, amount, sheen);
            MeshDrawUtil.drawWithUniform(Pipelines.WEATHER_SNOW, mesh, "Egg snow cover", "EggSnowData", uniforms.slice());
        }
    }

    void close() {
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private static float height(int x, int z, float lift) {
        return 0.007f + lift * (0.5f + WeatherProfile.noise((long) x * 73428767 ^ (long) z * 912931, 1) * 0.5f);
    }

    private void updateUniforms(Vec3d eye, WeatherSky.State sky, float wetness, float sheen) {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "Egg snow uniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, 160);
        }
        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(uniforms, false, true)) {
            Vector3fc sun = sky.sun();
            Vector3fc sunlight = sky.sunColor();
            Vector3fc zenith = sky.zenith();
            Vector3fc horizon = sky.horizon();
            Std140Builder.intoBuffer(view.data()).putMat4f(IDENTITY).putVec4(wrap(eye.x), wrap(eye.y), wrap(eye.z), 0)
                    .putVec4(sun.x(), sun.y(), sun.z(), 0).putVec4(sunlight.x(), sunlight.y(), sunlight.z(), 0)
                    .putVec4((zenith.x() + horizon.x()) * 0.5f + 0.16f, (zenith.y() + horizon.y()) * 0.5f + 0.16f,
                            (zenith.z() + horizon.z()) * 0.5f + 0.17f, 0)
                    .putFloat(15).putFloat(3840).putFloat(0.38f).putFloat(0.24f).putFloat(0.19f)
                    .putFloat(0.3f * wetness).putFloat(sheen);
        }
    }

    private static float wrap(double value) {
        return (float) (value - Math.floor(value / 256) * 256);
    }
}
