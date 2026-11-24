package egg.client.render.world;

import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SilkTargetRenderer {
    private static final long WRAP_TIME = 250_000_000L;
    private static final long RELEASE_TIME = 155_000_000L;
    private static final int SEGMENTS = 36;
    private static final float[] EDGES = {-1, -0.52f, 0, 0.52f, 1};
    private static final float[] EDGE_OPACITY = {0, 1, 0.84f, 0.63f, 0};

    private LivingEntity current;
    private long acquiredAt;
    private long lastSeen;
    private long lastFrame;
    private float trailingYaw;

    public void render(LivingEntity target, VertexConsumerProvider consumers, Vec3d camera,
                       float tickDelta, int color, float opacity) {
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            acquiredAt = now;
            lastFrame = now;
            trailingYaw = bodyYaw(target, tickDelta);
        }
        if (target != null) lastSeen = now;
        if (current == null || !current.isAlive() || current.isRemoved()
                || current.getEntityWorld() != MinecraftClient.getInstance().world
                || EntityUtil.isNakedInvisible(current) || now - lastSeen >= RELEASE_TIME) {
            clear();
            return;
        }

        float bodyYaw = bodyYaw(current, tickDelta);
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;
        trailingYaw = MathHelper.lerpAngleDegrees(1 - (float) Math.exp(-dt * 11), trailingYaw, bodyYaw);
        float lag = Math.clamp(MathHelper.wrapDegrees(trailingYaw - bodyYaw), -38, 38) * MathHelper.RADIANS_PER_DEGREE;
        float wrap = smooth((now - acquiredAt) / (float) WRAP_TIME);
        float fade = target == null ? 1 - smooth((now - lastSeen) / (float) RELEASE_TIME) : 1;
        Vec3d feet = current.getLerpedPos(tickDelta).subtract(camera);
        float height = current.getHeight();
        float width = current.getWidth();
        float turn = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        float armorPadding = current.getEquippedStack(EquipmentSlot.CHEST).isEmpty() ? 0 : 0.045f;
        float proximity = smooth(((float) feet.add(0, height * 0.57f, 0).length() - 0.48f) / 0.72f);
        Frame frame = new Frame(feet, height, width * 0.81f + 0.061f + armorPadding,
                width * 0.38f + 0.060f + armorPadding, MathHelper.sin(turn), MathHelper.cos(turn),
                lag, wrap, opacity * fade * proximity * smooth(wrap / 0.65f), (now - acquiredAt) / 1_000_000_000.0f);
        if (frame.opacity <= 0.003f) return;

        int bodyColor = ColorUtil.interpolate(color, 0xFFE0E4F6, 0.28f);
        int foldColor = ColorUtil.interpolate(color, 0xFF73809D, 0.15f);
        VertexConsumer ribbon = consumers.getBuffer(RenderLayers.debugQuads());
        drawRibbon(ribbon, frame, 1, 0.77f, 0.42f, bodyColor);
        drawRibbon(ribbon, frame, -1, 0.70f, 0.36f, foldColor);
    }

    private static float bodyYaw(LivingEntity entity, float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, entity.lastBodyYaw, entity.bodyYaw);
    }

    private static void drawRibbon(VertexConsumer vertices, Frame frame, int side, float high, float low, int tint) {
        int segments = Math.max(2, Math.round(frame.wrap * SEGMENTS));
        Slice a = slice(frame, side, high, low, 0);
        for (int i = 0; i < segments; i++) {
            float end = (i + 1) * frame.wrap / segments;
            Slice b = slice(frame, side, high, low, end);
            for (int band = 0; band < EDGES.length - 1; band++) {
                vertex(vertices, frame, a, band, tint);
                vertex(vertices, frame, b, band, tint);
                vertex(vertices, frame, b, band + 1, tint);
                vertex(vertices, frame, a, band + 1, tint);
            }
            a = b;
        }
    }

    private static Slice slice(Frame frame, int side, float high, float low, float t) {
        float tail = t * t * t;
        float flutter = MathHelper.sin(frame.time * 3.05f - t * 7.0f + side);
        float turn = t * (0.42f + 0.58f * t);
        float arc = side * (0.22f + 3.33f * turn) + frame.lag * tail;
        float radial = (1 - frame.wrap) * 0.11f + (0.030f + 0.012f * flutter) * tail;
        float envelope = MathHelper.sin(t * MathHelper.PI);
        float y = frame.height * (MathHelper.lerp(t, high, low) + 0.016f * envelope + 0.023f * tail * flutter);
        float width = frame.height * 0.037f * (0.28f + 0.72f * envelope);
        float fold = (0.16f + 0.36f * t) * MathHelper.sin(t * 8.5f - frame.time * 2.2f + side);
        float alpha = frame.opacity * smooth(t / 0.16f) * smooth((frame.wrap - t) / 0.15f);
        float x = roundedSide(MathHelper.sin(arc));
        float z = roundedSide(MathHelper.cos(arc));
        return new Slice(x, z, radial, y, width, fold, alpha);
    }

    private static float roundedSide(float coordinate) {
        return Math.copySign((float) Math.sqrt(Math.abs(coordinate)), coordinate);
    }

    private static void vertex(VertexConsumer vertices, Frame frame, Slice point, int band, int tint) {
        float cross = EDGES[band] * point.width;
        float x = point.x * (frame.radiusX + point.radial + cross * point.fold);
        float z = point.z * (frame.radiusZ + point.radial + cross * point.fold);
        int color = ColorUtil.withAlphaFloat(tint, point.alpha * EDGE_OPACITY[band]);
        vertices.vertex((float) frame.feet.x + x * frame.cos - z * frame.sin,
                (float) frame.feet.y + point.y + cross,
                (float) frame.feet.z + x * frame.sin + z * frame.cos).color(color);
    }

    public void clear() {
        current = null;
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record Frame(Vec3d feet, float height, float radiusX, float radiusZ, float sin, float cos,
                         float lag, float wrap, float opacity, float time) {}

    private record Slice(float x, float z, float radial, float y, float width, float fold, float alpha) {}
}
