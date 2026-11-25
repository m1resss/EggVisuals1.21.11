package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BracketsTargetRenderer {
    private static final long ACQUIRE_TIME = 180_000_000L;
    private static final long RELEASE_TIME = 140_000_000L;
    private static final long HIT_TIME = 280_000_000L;

    private LivingEntity current;
    private long acquiredAt;
    private long lastSeen;
    private long hitAt;
    private int lastHurtTime;
    private BufferAllocator allocator;

    public void render(LivingEntity target, Vec3d camera, float tickDelta, float radiusScale, float speedScale, boolean useHurtColor) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            acquiredAt = now;
            lastHurtTime = target.hurtTime;
            hitAt = 0L;
        }
        if (target != null) {
            lastSeen = now;
        }
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= RELEASE_TIME) {
            clear();
            return;
        }

        if (current.hurtTime > lastHurtTime) {
            hitAt = now;
        }
        lastHurtTime = current.hurtTime;

        float enter = smooth((now - acquiredAt) / (float) ACQUIRE_TIME);
        float release = target == null ? 1.0f - smooth((now - lastSeen) / (float) RELEASE_TIME) : 1.0f;
        Vec3d feet = current.getLerpedPos(tickDelta).subtract(camera);
        float height = current.getHeight();
        float proximity = smooth(((float) feet.add(0.0, height * 0.5f, 0.0).length() - 0.50f) / 0.75f);
        float alpha = enter * release * proximity;
        if (alpha < 0.005f) {
            return;
        }

        float hitElapsed = hitAt == 0L ? 1.0f : Math.min(1.0f, (now - hitAt) / (float) HIT_TIME);
        float hitSnap = (hitAt == 0L || hitElapsed >= 1.0f)
                ? 0.0f
                : smooth(hitElapsed / 0.14f) * (1.0f - smooth((hitElapsed - 0.14f) / 0.86f));
        float hurtFlash = current.hurtTime > 0
                ? Math.max(hitSnap, Math.clamp(current.hurtTime / 10.0f, 0.0f, 1.0f))
                : hitSnap;

        float age = (now - acquiredAt) / 1_000_000_000.0f;
        float baseRadius = (current.getWidth() * 0.72f + 0.14f) * Math.max(0.45f, radiusScale / 0.95f);
        float acquireSpread = 1.0f + (1.0f - enter) * 0.36f;
        float clench = 1.0f - hitSnap * 0.16f;
        float quadHalf = baseRadius * acquireSpread * clench * 1.38f;
        float angle = age * (0.40f * speedScale) + hitSnap * 0.11f;
        float y = (float) feet.y + 0.036f;

        int accent = Theme.getAccent();
        int finalColor = accent;
        if (hurtFlash > 0.01f) {
            int flashTarget = useHurtColor ? 0xFFFF4757 : 0xFFFFFFFF;
            finalColor = ColorUtil.interpolate(accent, flashTarget, hurtFlash * 0.88f);
        }
        int r = (finalColor >> 16) & 0xFF;
        int g = (finalColor >> 8) & 0xFF;
        int b = finalColor & 0xFF;
        int a = Math.round(242.0f * alpha);

        if (allocator == null) {
            allocator = new BufferAllocator(8 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            emitRotatedQuad(mesh, (float) feet.x, y, (float) feet.z, quadHalf, angle, 0.0f, r, g, b, a);

            if (hitAt != 0L && hitElapsed < 1.0f) {
                float rippleHalf = baseRadius * (0.76f + 0.42f * smooth(hitElapsed));
                int rippleAlpha = Math.round(185.0f * (1.0f - smooth(hitElapsed)) * alpha);
                if (rippleAlpha > 2) {
                    emitRotatedQuad(mesh, (float) feet.x, y - 0.002f, (float) feet.z, rippleHalf, angle, 4.0f, r, g, b, rippleAlpha);
                }
            }

            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.TARGET_BRACKETS, built, "egg target brackets");
            }
        } finally {
            allocator.reset();
        }
    }

    private static void emitRotatedQuad(BufferBuilder mesh, float cx, float y, float cz, float halfSize, float angle,
                                        float uOffset, int r, int g, int b, int a) {
        float cos = MathHelper.cos(angle);
        float sin = MathHelper.sin(angle);
        float uv = 1.05f;

        emitCorner(mesh, cx, y, cz, -halfSize, -halfSize, cos, sin, uOffset - uv, -uv, r, g, b, a);
        emitCorner(mesh, cx, y, cz, halfSize, -halfSize, cos, sin, uOffset + uv, -uv, r, g, b, a);
        emitCorner(mesh, cx, y, cz, halfSize, halfSize, cos, sin, uOffset + uv, uv, r, g, b, a);
        emitCorner(mesh, cx, y, cz, -halfSize, halfSize, cos, sin, uOffset - uv, uv, r, g, b, a);
    }

    private static void emitCorner(BufferBuilder mesh, float cx, float y, float cz, float lx, float lz,
                                   float cos, float sin, float u, float v, int r, int g, int b, int a) {
        float wx = cx + lx * cos - lz * sin;
        float wz = cz + lx * sin + lz * cos;
        mesh.vertex(wx, y, wz).texture(u, v).color(r, g, b, a);
    }

    public void clear() {
        current = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
