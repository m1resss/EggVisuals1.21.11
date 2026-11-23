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
    private static final float BLOCK_CONTACT = 0.004f;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private GpuBuffer uniforms;

    void draw(WeatherSurface surface, Vec3d camera, float amount, float depth, float sheen, WeatherSky.State sky) {
        int centerX = MathHelper.floor(camera.x);
        int centerZ = MathHelper.floor(camera.z);
        float thickness = thickness(amount, depth);
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

                    long position = (long) x * 73428767 ^ (long) z * 912931;
                    float coverage = Math.min(1, amount * depth * (1.37f - WeatherProfile.noise(position, 0) * 0.44f));
                    int alpha = Math.round(255 * edge * coverage);
                    if (alpha < 2) continue;

                    int color = alpha << 24 | 0xFFFBF7;
                    float x0 = (float) (x - camera.x);
                    float z0 = (float) (z - camera.z);
                    float x1 = x0 + 1;
                    float z1 = z0 + 1;
                    float y00 = relativeY + cornerHeight(x, z, thickness);
                    float y01 = relativeY + cornerHeight(x, z + 1, thickness);
                    float y11 = relativeY + cornerHeight(x + 1, z + 1, thickness);
                    float y10 = relativeY + cornerHeight(x + 1, z, thickness);
                    builder.vertex(x0, y00, z0).color(color);
                    builder.vertex(x0, y01, z1).color(color);
                    builder.vertex(x1, y11, z1).color(color);
                    builder.vertex(x1, y10, z0).color(color);

                    float bottom = relativeY - BLOCK_CONTACT;
                    if (exposed(surface, x - 1, z, top)) side(builder, x0, z0, y00, x0, z1, y01, bottom, color);
                    if (exposed(surface, x, z + 1, top)) side(builder, x0, z1, y01, x1, z1, y11, bottom, color);
                    if (exposed(surface, x + 1, z, top)) side(builder, x1, z1, y11, x1, z0, y10, bottom, color);
                    if (exposed(surface, x, z - 1, top)) side(builder, x1, z0, y10, x0, z0, y00, bottom, color);
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

    private static boolean exposed(WeatherSurface surface, int x, int z, int top) {
        int neighborTop = surface.height(x, z);
        return neighborTop < top || !surface.solid(x, z);
    }

    private static void side(BufferBuilder builder, float x0, float z0, float y0, float x1, float z1, float y1,
                             float bottom, int color) {
        builder.vertex(x0, bottom, z0).color(color);
        builder.vertex(x1, bottom, z1).color(color);
        builder.vertex(x1, y1, z1).color(color);
        builder.vertex(x0, y0, z0).color(color);
    }

    static float thickness(float amount, float depth) {
        return Math.min(0.24f, 0.13f * amount * depth);
    }

    static float surfaceOffset(double x, double z, float thickness) {
        int x0 = MathHelper.floor(x);
        int z0 = MathHelper.floor(z);
        float offsetX = (float) (x - x0);
        float offsetZ = (float) (z - z0);
        float y00 = cornerHeight(x0, z0, thickness);
        float y11 = cornerHeight(x0 + 1, z0 + 1, thickness);
        if (offsetZ >= offsetX) {
            float y01 = cornerHeight(x0, z0 + 1, thickness);
            return y00 + (y01 - y00) * offsetZ + (y11 - y01) * offsetX;
        }
        float y10 = cornerHeight(x0 + 1, z0, thickness);
        return y00 + (y10 - y00) * offsetX + (y11 - y10) * offsetZ;
    }

    private static float cornerHeight(int x, int z, float thickness) {
        long position = (long) x * 73428767 ^ (long) z * 912931;
        return BLOCK_CONTACT + thickness * (0.72f + 0.28f * WeatherProfile.noise(position, 1));
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
