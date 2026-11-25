package egg.client.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.EggStartupAnimation;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class OrbitalGlyphsTargetRenderer {
    private static final long ENTER_TIME = 240_000_000L;
    private static final long EXIT_TIME = 180_000_000L;
    private static final int RING1_COUNT = 34;
    private static final int RING2_COUNT = 30;
    private static final int SPARK_COUNT = 10;

    private final Vector3f camRight = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private LivingEntity current;
    private long acquiredAt;
    private long lastSeen;
    private BufferAllocator allocator;

    public void render(LivingEntity target, Camera camera, Vec3d camPos, float tickDelta,
                       float userRadius, float userSpeed, boolean hurtFlash, int accentColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;

        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            acquiredAt = now;
        }
        if (target != null) {
            lastSeen = now;
        }
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != client.world
                || EntityUtil.isNakedInvisible(current)
                || (target == null && now - lastSeen >= EXIT_TIME)) {
            clear();
            return;
        }

        EggStartupAnimation.ensureAtlas(client);
        AbstractTexture glyphTex = client.getTextureManager().getTexture(EggStartupAnimation.GLYPHS);
        if (glyphTex == null || glyphTex.getGlTextureView() == null) return;

        float enter = smooth((now - acquiredAt) / (float) ENTER_TIME);
        float exit = target == null ? 1.0f - smooth((now - lastSeen) / (float) EXIT_TIME) : 1.0f;
        float alpha = enter * exit;
        if (alpha < 0.01f) return;

        Vec3d feet = current.getLerpedPos(tickDelta).subtract(camPos);
        float height = current.getHeight();
        Vec3d center = feet.add(0.0, height * 0.54, 0.0);
        float dist = (float) center.length();
        float proximity = smooth((dist - 0.45f) / 0.65f);
        alpha *= proximity;
        if (alpha < 0.01f) return;

        float hurtFactor = (hurtFlash && current.hurtTime > 0) ? MathHelper.clamp(current.hurtTime / 10.0f, 0.0f, 1.0f) : 0.0f;
        int tintBase = hurtFactor > 0.01f
                ? ColorUtil.interpolate(accentColor, 0xFFFF4D61, hurtFactor * 0.85f)
                : accentColor;

        float baseRadius = (current.getWidth() * 0.62f + 0.24f) * (0.55f + 0.45f * userRadius) * (0.72f + 0.28f * enter);
        float time = (now - acquiredAt) / 1_000_000_000.0f;
        float spinRate = 0.75f + userSpeed * 0.95f;

        camRight.set(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        camUp.set(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());

        Vec3d toCam = center.lengthSquared() > 1.0e-4 ? center.multiply(-1.0).normalize() : new Vec3d(0.0, 0.0, 1.0);
        float atlasW = EggStartupAnimation.GLYPH_BITS.length * EggStartupAnimation.CELL_WIDTH;
        float atlasH = 6.0f;

        int maxQuads = RING1_COUNT + RING2_COUNT + SPARK_COUNT;
        if (allocator == null) {
            allocator = new BufferAllocator(maxQuads * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            emitRing(mesh, center, toCam, baseRadius, RING1_COUNT,
                    0.46f + 0.08f * MathHelper.sin(time * 0.9f), 0.24f,
                    time * spinRate, time, 0.068f, alpha, tintBase, atlasW, atlasH, 0);

            emitRing(mesh, center, toCam, baseRadius * 0.93f, RING2_COUNT,
                    -0.48f - 0.07f * MathHelper.cos(time * 0.8f), -0.28f,
                    -time * spinRate * 1.24f + 0.7f, time, 0.062f, alpha * 0.92f, tintBase, atlasW, atlasH, 137);

            emitSparks(mesh, center, toCam, baseRadius * 1.08f, height * 0.34f,
                    time, spinRate, alpha * 0.78f, tintBase, atlasW, atlasH);

            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawWithTexture(
                        Pipelines.AURA_TEXTURE_DEPTH,
                        built,
                        glyphTex.getGlTextureView(),
                        RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
                );
            }
        } finally {
            allocator.reset();
        }
    }

    private void emitRing(BufferBuilder mesh, Vec3d center, Vec3d toCam, float radius, int count,
                          float pitch, float roll, float spinOffset, float time, float glyphScale,
                          float masterAlpha, int accentColor, float atlasW, float atlasH, int seedBase) {
        float cosP = MathHelper.cos(pitch);
        float sinP = MathHelper.sin(pitch);
        float cosR = MathHelper.cos(roll);
        float sinR = MathHelper.sin(roll);

        for (int i = 0; i < count; i++) {
            float frac = (float) i / count;
            float angle = frac * MathHelper.TAU + spinOffset;
            float lx = MathHelper.cos(angle) * radius;
            float lz = MathHelper.sin(angle) * radius;

            float y1 = -lz * sinP;
            float z1 = lz * cosP;
            float rx = lx * cosR - y1 * sinR;
            float ry = lx * sinR + y1 * cosR;
            float rz = z1;

            Vec3d offset = new Vec3d(rx, ry, rz);
            Vec3d worldPos = center.add(offset);
            Vec3d norm = offset.lengthSquared() > 1.0e-5 ? offset.normalize() : new Vec3d(0.0, 1.0, 0.0);

            float facing = (float) MathHelper.clamp(norm.dotProduct(toCam) * 0.5 + 0.5, 0.0, 1.0);
            float wave = 0.5f + 0.5f * MathHelper.sin(frac * MathHelper.TAU * 3.0f - time * 4.2f + seedBase);
            float seed = cellNoise(seedBase + i * 31);
            float brightness = MathHelper.clamp(0.18f + facing * 0.56f + wave * 0.24f + (seed - 0.5f) * 0.14f, 0.05f, 1.0f);

            int glyphIdx = Math.min(EggStartupAnimation.GLYPH_BITS.length - 1,
                    (int) (brightness * EggStartupAnimation.GLYPH_BITS.length));
            int silverWhite = ColorUtil.interpolate(0xFF9AA3B2, 0xFFFFFFFF, smooth(brightness * 1.25f));
            int finalRgb = ColorUtil.interpolate(silverWhite, accentColor, 0.28f + 0.24f * wave);
            float quadAlpha = (0.38f + 0.62f * facing) * masterAlpha;
            float size = glyphScale * (0.85f + 0.28f * facing);

            emitGlyphBillboard(mesh, worldPos, size, glyphIdx, finalRgb, quadAlpha, atlasW, atlasH);
        }
    }

    private void emitSparks(BufferBuilder mesh, Vec3d center, Vec3d toCam, float radius, float verticalSpan,
                            float time, float spinRate, float masterAlpha, int accentColor,
                            float atlasW, float atlasH) {
        for (int i = 0; i < SPARK_COUNT; i++) {
            float seed = cellNoise(701 + i * 53);
            float angle = i * (MathHelper.TAU / SPARK_COUNT) + time * spinRate * (0.55f + seed * 0.35f);
            float bob = MathHelper.sin(time * (2.2f + seed * 1.4f) + i * 1.7f) * verticalSpan;
            float r = radius * (0.94f + 0.18f * MathHelper.cos(time * 1.7f + i));
            Vec3d offset = new Vec3d(MathHelper.cos(angle) * r, bob, MathHelper.sin(angle) * r);
            Vec3d norm = offset.lengthSquared() > 1.0e-5 ? offset.normalize() : new Vec3d(0.0, 1.0, 0.0);
            float facing = (float) MathHelper.clamp(norm.dotProduct(toCam) * 0.5 + 0.5, 0.0, 1.0);
            float twinkle = 0.45f + 0.55f * (0.5f + 0.5f * MathHelper.sin(time * 5.0f + i * 2.3f));
            int glyphIdx = Math.min(EggStartupAnimation.GLYPH_BITS.length - 1, 2 + (int) (twinkle * 6.0f));
            int color = ColorUtil.interpolate(0xFFEAF0FA, accentColor, 0.42f);
            emitGlyphBillboard(mesh, center.add(offset), 0.052f * (0.85f + 0.3f * twinkle),
                    glyphIdx, color, masterAlpha * twinkle * (0.45f + 0.55f * facing), atlasW, atlasH);
        }
    }

    private void emitGlyphBillboard(BufferBuilder mesh, Vec3d pos, float halfSize, int glyphIdx,
                                    int argbRgb, float alpha, float atlasW, float atlasH) {
        int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
        if (a <= 2) return;
        int r = (argbRgb >> 16) & 0xFF;
        int g = (argbRgb >> 8) & 0xFF;
        int b = argbRgb & 0xFF;

        float u0 = (glyphIdx * EggStartupAnimation.CELL_WIDTH) / atlasW;
        float u1 = (glyphIdx * EggStartupAnimation.CELL_WIDTH + 5.0f) / atlasW;
        float v0 = 0.0f;
        float v1 = 5.0f / atlasH;

        float rx = camRight.x * halfSize;
        float ry = camRight.y * halfSize;
        float rz = camRight.z * halfSize;
        float ux = camUp.x * halfSize;
        float uy = camUp.y * halfSize;
        float uz = camUp.z * halfSize;

        float px = (float) pos.x;
        float py = (float) pos.y;
        float pz = (float) pos.z;

        mesh.vertex(px - rx + ux, py - ry + uy, pz - rz + uz).texture(u0, v0).color(r, g, b, a);
        mesh.vertex(px - rx - ux, py - ry - uy, pz - rz - uz).texture(u0, v1).color(r, g, b, a);
        mesh.vertex(px + rx - ux, py + ry - uy, pz + rz - uz).texture(u1, v1).color(r, g, b, a);
        mesh.vertex(px + rx + ux, py + ry + uy, pz + rz + uz).texture(u1, v0).color(r, g, b, a);
    }

    public void clear() {
        current = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float cellNoise(int cell) {
        int hash = (cell + 1) * 0x9E3779B9;
        hash = (hash ^ (hash >>> 16)) * 0x85EBCA6B;
        return ((hash ^ (hash >>> 13)) & 0xFFFF) / 65535.0f;
    }
}
