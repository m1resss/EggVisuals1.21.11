package egg.client.render.world;

import egg.client.util.ColorUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class TrajectoryLines {
    private static final double NEAR_PLANE = 0.075;
    private static final double HAND_MERGE_LENGTH = 2.4;

    private final BufferBuilder mesh;
    private final Vec3d camera;
    private final Vec3d cameraForward;
    private final float width;
    private final float pixelScale;
    private final boolean glow;

    public TrajectoryLines(BufferBuilder mesh, Vec3d camera, Vec3d cameraForward, float width, float pixelScale, boolean glow) {
        this.mesh = mesh;
        this.camera = camera;
        this.cameraForward = cameraForward;
        this.width = width * pixelScale;
        this.pixelScale = pixelScale;
        this.glow = glow;
    }

    public void path(List<Vec3d> points, Vec3d handOffset, int color) {
        int stroke = ColorUtil.interpolate(color, 0xFFFFFFFF, 0.20f);
        if (handOffset.lengthSquared() < 1e-7) {
            for (int i = 1; i < points.size(); i++) {
                segment(points.get(i - 1), points.get(i), stroke, width, 0.84f, 0.84f);
            }
            return;
        }

        double mergeLength = 0.0;
        for (int i = 1; i < points.size() && mergeLength < HAND_MERGE_LENGTH; i++) {
            mergeLength += points.get(i - 1).distanceTo(points.get(i));
        }
        mergeLength = Math.min(mergeLength, HAND_MERGE_LENGTH);
        if (mergeLength < 1e-5) return;

        Vec3d offset = handOffset.multiply(Math.min(1.0, mergeLength / 0.60));
        Vec3d previous = points.getFirst().add(offset);
        double travelled = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Vec3d a = points.get(i - 1);
            Vec3d b = points.get(i);
            double length = a.distanceTo(b);
            if (length < 1e-5) continue;
            int slices = travelled < mergeLength ? Math.min(16, Math.max(1, (int) Math.ceil(length / 0.19))) : 1;
            for (int j = 1; j <= slices; j++) {
                double distance = travelled + length * j / slices;
                Vec3d point = j == slices ? b : a.lerp(b, (double) j / slices);
                if (distance < mergeLength) {
                    point = point.add(offset.multiply(1.0 - smooth(distance / mergeLength)));
                }
                float alphaStart = handAlpha(distance - length / slices);
                segment(previous, point, stroke, width, alphaStart, handAlpha(distance));
                previous = point;
            }
            travelled += length;
        }
    }

    public void impactRay(Vec3d start, Vec3d end, int color) {
        segment(start, end, ColorUtil.interpolate(color, 0xFFFFFFFF, 0.3f), Math.min(width, 1.15f * pixelScale), 0.70f, 0f);
    }

    private void segment(Vec3d from, Vec3d to, int color, float thickness, float startAlpha, float endAlpha) {
        Vec3d a = from.subtract(camera);
        Vec3d b = to.subtract(camera);
        double aDepth = a.dotProduct(cameraForward);
        double bDepth = b.dotProduct(cameraForward);
        if (aDepth > bDepth) {
            Vec3d previous = a;
            a = b;
            b = previous;
            double depth = aDepth;
            aDepth = bDepth;
            bDepth = depth;
            float alpha = startAlpha;
            startAlpha = endAlpha;
            endAlpha = alpha;
        }
        if (bDepth < NEAR_PLANE) return;
        if (aDepth < NEAR_PLANE) {
            double t = (NEAR_PLANE - aDepth) / (bDepth - aDepth);
            a = a.lerp(b, t);
            startAlpha = (float) MathHelper.lerp(t, startAlpha, endAlpha);
        }
        Vec3d delta = b.subtract(a);
        if (delta.lengthSquared() < 1e-10) return;
        Vec3d direction = delta.normalize();
        if (glow) {
            emit(a, direction, color, startAlpha * 0.16f, thickness + 1.8f * pixelScale);
            emit(b, direction, color, endAlpha * 0.16f, thickness + 1.8f * pixelScale);
        }
        emit(a, direction, color, startAlpha, thickness);
        emit(b, direction, color, endAlpha, thickness);
    }

    private void emit(Vec3d pos, Vec3d direction, int color, float alpha, float thickness) {
        mesh.vertex((float) pos.x, (float) pos.y, (float) pos.z).color(ColorUtil.withAlpha(color, Math.round(255f * alpha)))
                .normal((float) direction.x, (float) direction.y, (float) direction.z).lineWidth(thickness);
    }

    private static float handAlpha(double distance) {
        return 0.20f + 0.68f * (float) smooth(distance / 0.72);
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
