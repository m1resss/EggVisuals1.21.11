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

    private static final float BRACKET_BOX = 0.64f;
    private static final float CORNER_RADIUS = 0.17f;
    private static final float ARM_START = 0.27f;
    private static final float STROKE_HALF = 0.046f;
    private static final float QUAD_UV = 1.05f;
    private static final int ARC_STEPS = 8;
    private static final int CAP_STEPS = 6;

    private static final float[] PATH_X = new float[ARC_STEPS + 3];
    private static final float[] PATH_Z = new float[ARC_STEPS + 3];
    private static final float[] NORM_X = new float[ARC_STEPS + 3];
    private static final float[] NORM_Z = new float[ARC_STEPS + 3];

    static {
        float c = BRACKET_BOX - CORNER_RADIUS;
        PATH_X[0] = ARM_START;
        PATH_Z[0] = BRACKET_BOX;
        NORM_X[0] = 0.0f;
        NORM_Z[0] = 1.0f;

        for (int i = 0; i <= ARC_STEPS; i++) {
            float phi = (MathHelper.PI * 0.5f) * ((float) i / ARC_STEPS);
            float sx = MathHelper.sin(phi);
            float cz = MathHelper.cos(phi);
            PATH_X[i + 1] = c + sx * CORNER_RADIUS;
            PATH_Z[i + 1] = c + cz * CORNER_RADIUS;
            NORM_X[i + 1] = sx;
            NORM_Z[i + 1] = cz;
        }

        int last = ARC_STEPS + 2;
        PATH_X[last] = BRACKET_BOX;
        PATH_Z[last] = ARM_START;
        NORM_X[last] = 1.0f;
        NORM_Z[last] = 0.0f;
    }

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
        float liftPhase = 0.5f - 0.5f * MathHelper.cos(age * (1.45f * speedScale));
        float minH = Math.max(0.05f, height * 0.06f);
        float maxH = Math.max(minH + 0.10f, height * 0.84f);
        float y = (float) feet.y + MathHelper.lerp(liftPhase, minH, maxH);
        float halfH = 0.036f;

        int accent = Theme.getAccent();
        int finalColor = accent;
        if (hurtFlash > 0.01f) {
            int flashTarget = useHurtColor ? 0xFFFF4757 : 0xFFFFFFFF;
            finalColor = ColorUtil.interpolate(accent, flashTarget, hurtFlash * 0.88f);
        }
        int r = (finalColor >> 16) & 0xFF;
        int g = (finalColor >> 8) & 0xFF;
        int b = finalColor & 0xFF;
        int a = Math.round(248.0f * alpha);

        if (allocator == null) {
            allocator = new BufferAllocator(360 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }

        float cx = (float) feet.x;
        float cz = (float) feet.z;

        try {
            BufferBuilder solidMesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            emitSolidBrackets(solidMesh, cx, y, cz, halfH, quadHalf, angle, r, g, b, a);
            var builtSolid = solidMesh.endNullable();
            if (builtSolid != null) {
                MeshDrawUtil.drawLabeled(Pipelines.TARGET_BRACKETS, builtSolid, "egg target brackets");
            }
        } finally {
            allocator.reset();
        }

        try {
            BufferBuilder flatMesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            emitRotatedQuad(flatMesh, cx, y, cz, quadHalf, angle, 8.0f, r, g, b, a);
            if (hitAt != 0L && hitElapsed < 1.0f) {
                float rippleHalf = baseRadius * (0.76f + 0.42f * smooth(hitElapsed));
                int rippleAlpha = Math.round(185.0f * (1.0f - smooth(hitElapsed)) * alpha);
                if (rippleAlpha > 2) {
                    emitRotatedQuad(flatMesh, cx, y, cz, rippleHalf, angle, 4.0f, r, g, b, rippleAlpha);
                }
            }
            var builtFlat = flatMesh.endNullable();
            if (builtFlat != null) {
                MeshDrawUtil.drawLabeled(Pipelines.TARGET_BRACKETS_FLAT, builtFlat, "egg target brackets flat");
            }
        } finally {
            allocator.reset();
        }
    }

    private static void emitSolidBrackets(BufferBuilder mesh, float cx, float y, float cz, float halfH, float quadHalf,
                                          float angle, int r, int g, int b, int a) {
        float cos = MathHelper.cos(angle);
        float sin = MathHelper.sin(angle);
        float unitScale = quadHalf / QUAD_UV;
        float wallOffset = STROKE_HALF * unitScale;
        float yBot = y - halfH;
        float yTop = y + halfH;

        int wallR = Math.round(r * 0.88f);
        int wallG = Math.round(g * 0.88f);
        int wallB = Math.round(b * 0.88f);

        int n = PATH_X.length;
        for (int q = 0; q < 4; q++) {
            float qxCos = q == 0 ? 1.0f : (q == 1 ? 0.0f : (q == 2 ? -1.0f : 0.0f));
            float qxSin = q == 0 ? 0.0f : (q == 1 ? 1.0f : (q == 2 ? 0.0f : -1.0f));

            for (int i = 0; i < n - 1; i++) {
                float sx0 = (PATH_X[i] * qxCos - PATH_Z[i] * qxSin) * unitScale;
                float sz0 = (PATH_X[i] * qxSin + PATH_Z[i] * qxCos) * unitScale;
                float nx0 = (NORM_X[i] * qxCos - NORM_Z[i] * qxSin) * wallOffset;
                float nz0 = (NORM_X[i] * qxSin + NORM_Z[i] * qxCos) * wallOffset;

                float sx1 = (PATH_X[i + 1] * qxCos - PATH_Z[i + 1] * qxSin) * unitScale;
                float sz1 = (PATH_X[i + 1] * qxSin + PATH_Z[i + 1] * qxCos) * unitScale;
                float nx1 = (NORM_X[i + 1] * qxCos - NORM_Z[i + 1] * qxSin) * wallOffset;
                float nz1 = (NORM_X[i + 1] * qxSin + NORM_Z[i + 1] * qxCos) * wallOffset;

                float ox0 = sx0 + nx0;
                float oz0 = sz0 + nz0;
                float ix0 = sx0 - nx0;
                float iz0 = sz0 - nz0;

                float ox1 = sx1 + nx1;
                float oz1 = sz1 + nz1;
                float ix1 = sx1 - nx1;
                float iz1 = sz1 - nz1;

                emitCorner(mesh, cx, yTop, cz, ox0, oz0, cos, sin, 1.0f, 1.0f, r, g, b, a);
                emitCorner(mesh, cx, yTop, cz, ox1, oz1, cos, sin, 1.0f, 1.0f, r, g, b, a);
                emitCorner(mesh, cx, yTop, cz, ix1, iz1, cos, sin, -1.0f, 1.0f, r, g, b, a);
                emitCorner(mesh, cx, yTop, cz, ix0, iz0, cos, sin, -1.0f, 1.0f, r, g, b, a);

                emitCorner(mesh, cx, yBot, cz, ox0, oz0, cos, sin, 1.0f, 0.0f, r, g, b, a);
                emitCorner(mesh, cx, yBot, cz, ix0, iz0, cos, sin, -1.0f, 0.0f, r, g, b, a);
                emitCorner(mesh, cx, yBot, cz, ix1, iz1, cos, sin, -1.0f, 0.0f, r, g, b, a);
                emitCorner(mesh, cx, yBot, cz, ox1, oz1, cos, sin, 1.0f, 0.0f, r, g, b, a);

                emitWallSegment(mesh, cx, cz, yBot, yTop, cos, sin, ox0, oz0, ox1, oz1, wallR, wallG, wallB, a);
                emitWallSegment(mesh, cx, cz, yBot, yTop, cos, sin, ix1, iz1, ix0, iz0, wallR, wallG, wallB, a);
            }

            float s0x = (PATH_X[0] * qxCos - PATH_Z[0] * qxSin) * unitScale;
            float s0z = (PATH_X[0] * qxSin + PATH_Z[0] * qxCos) * unitScale;
            float n0x = (NORM_X[0] * qxCos - NORM_Z[0] * qxSin) * wallOffset;
            float n0z = (NORM_X[0] * qxSin + NORM_Z[0] * qxCos) * wallOffset;
            float d0x = (-qxCos) * wallOffset;
            float d0z = (-qxSin) * wallOffset;
            emitRoundedCap(mesh, cx, cz, yBot, yTop, cos, sin, s0x, s0z, -n0x, -n0z, d0x, d0z, r, g, b, wallR, wallG, wallB, a);

            int last = n - 1;
            float s1x = (PATH_X[last] * qxCos - PATH_Z[last] * qxSin) * unitScale;
            float s1z = (PATH_X[last] * qxSin + PATH_Z[last] * qxCos) * unitScale;
            float n1x = (NORM_X[last] * qxCos - NORM_Z[last] * qxSin) * wallOffset;
            float n1z = (NORM_X[last] * qxSin + NORM_Z[last] * qxCos) * wallOffset;
            float d1x = (qxSin) * wallOffset;
            float d1z = (-qxCos) * wallOffset;
            emitRoundedCap(mesh, cx, cz, yBot, yTop, cos, sin, s1x, s1z, n1x, n1z, d1x, d1z, r, g, b, wallR, wallG, wallB, a);
        }
    }

    private static void emitRoundedCap(BufferBuilder mesh, float cx, float cz, float yBot, float yTop,
                                       float cos, float sin, float sx, float sz,
                                       float startX, float startZ, float tipX, float tipZ,
                                       int r, int g, int b, int wr, int wg, int wb, int a) {
        float px0 = sx + startX;
        float pz0 = sz + startZ;
        for (int s = 1; s <= CAP_STEPS; s++) {
            float t = MathHelper.PI * ((float) s / CAP_STEPS);
            float ct = MathHelper.cos(t);
            float st = MathHelper.sin(t);
            float px1 = sx + startX * ct + tipX * st;
            float pz1 = sz + startZ * ct + tipZ * st;

            emitCorner(mesh, cx, yTop, cz, sx, sz, cos, sin, 0.0f, 1.0f, r, g, b, a);
            emitCorner(mesh, cx, yTop, cz, px0, pz0, cos, sin, 1.0f, 1.0f, r, g, b, a);
            emitCorner(mesh, cx, yTop, cz, px1, pz1, cos, sin, 1.0f, 1.0f, r, g, b, a);
            emitCorner(mesh, cx, yTop, cz, sx, sz, cos, sin, 0.0f, 1.0f, r, g, b, a);

            emitCorner(mesh, cx, yBot, cz, sx, sz, cos, sin, 0.0f, 0.0f, r, g, b, a);
            emitCorner(mesh, cx, yBot, cz, px1, pz1, cos, sin, 1.0f, 0.0f, r, g, b, a);
            emitCorner(mesh, cx, yBot, cz, px0, pz0, cos, sin, 1.0f, 0.0f, r, g, b, a);
            emitCorner(mesh, cx, yBot, cz, sx, sz, cos, sin, 0.0f, 0.0f, r, g, b, a);

            emitWallSegment(mesh, cx, cz, yBot, yTop, cos, sin, px0, pz0, px1, pz1, wr, wg, wb, a);

            px0 = px1;
            pz0 = pz1;
        }
    }

    private static void emitWallSegment(BufferBuilder mesh, float cx, float cz, float yBot, float yTop,
                                        float cos, float sin, float lx0, float lz0, float lx1, float lz1,
                                        int r, int g, int b, int a) {
        emitCorner(mesh, cx, yBot, cz, lx0, lz0, cos, sin, 2.0f, 0.0f, r, g, b, a);
        emitCorner(mesh, cx, yBot, cz, lx1, lz1, cos, sin, 2.0f, 0.0f, r, g, b, a);
        emitCorner(mesh, cx, yTop, cz, lx1, lz1, cos, sin, 2.0f, 1.0f, r, g, b, a);
        emitCorner(mesh, cx, yTop, cz, lx0, lz0, cos, sin, 2.0f, 1.0f, r, g, b, a);
    }

    private static void emitRotatedQuad(BufferBuilder mesh, float cx, float y, float cz, float halfSize, float angle,
                                        float uOffset, int r, int g, int b, int a) {
        float cos = MathHelper.cos(angle);
        float sin = MathHelper.sin(angle);

        emitCorner(mesh, cx, y, cz, -halfSize, -halfSize, cos, sin, uOffset - QUAD_UV, -QUAD_UV, r, g, b, a);
        emitCorner(mesh, cx, y, cz, halfSize, -halfSize, cos, sin, uOffset + QUAD_UV, -QUAD_UV, r, g, b, a);
        emitCorner(mesh, cx, y, cz, halfSize, halfSize, cos, sin, uOffset + QUAD_UV, QUAD_UV, r, g, b, a);
        emitCorner(mesh, cx, y, cz, -halfSize, halfSize, cos, sin, uOffset - QUAD_UV, QUAD_UV, r, g, b, a);
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
