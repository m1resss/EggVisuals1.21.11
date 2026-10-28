package delta.util;

import static delta.core.Interface.aM_;

import lombok.Generated;
import platform.inject.invokers.GameRendererInvoker;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Screen projection used by Player ESP. Ported from Alphadcp. */
public final class ProjectUtil {
    @Generated
    private ProjectUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Vector2f a(double x, double y, double z) {
        return a(x, y, z, aM_.getRenderTickCounter().getTickProgress(true));
    }

    public static Vector2f a(double x, double y, double z, float tickDelta) {
        Camera camera = aM_.getEntityRenderDispatcher().camera;
        Vec3d cameraPos = camera.getCameraPos();
        Quaternionf cameraRotation = camera.getRotation().conjugate(new Quaternionf());
        Vector3f result = new Vector3f((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z));
        result.rotate(cameraRotation);

        if (aM_.options.getBobView().getValue() && aM_.getCameraEntity() instanceof AbstractClientPlayerEntity player) {
            a(player, result, tickDelta);
        }

        double fov = ((GameRendererInvoker) (Object) aM_.gameRenderer).invokeGetFov(camera, tickDelta, true);
        return a(result, fov);
    }

    private static void a(AbstractClientPlayerEntity player, Vector3f result, float tickDelta) {
        float distance = player.getState().getReverseLerpedDistanceMoved(tickDelta);
        float stride = player.getState().lerpMovement(tickDelta);
        float phase = distance * ((float) Math.PI);
        float xRotation = Math.abs(MathHelper.cos(phase - 0.2f) * stride) * 5.0f;
        float zRotation = MathHelper.sin(phase) * stride * 3.0f;
        result.rotate(new Quaternionf().rotationX((float) Math.toRadians(xRotation)));
        result.rotate(new Quaternionf().rotationZ((float) Math.toRadians(zRotation)));
        result.add(MathHelper.sin(phase) * stride * 0.5f, -Math.abs(MathHelper.cos(phase) * stride), 0.0f);
    }

    private static Vector2f a(Vector3f result, double fov) {
        float halfWidth = aM_.getWindow().getScaledWidth() / 2.0f;
        float halfHeight = aM_.getWindow().getScaledHeight() / 2.0f;
        float scale = halfHeight / (result.z * (float) Math.tan(Math.toRadians(fov / 2.0d)));
        if (result.z < 0.0f) {
            return new Vector2f((-result.x * scale) + halfWidth, (result.y * scale) + halfHeight);
        }
        return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
    }

    public static float[] a(Box box) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            Vector2f screen = a(x, y, z);
            if (screen.x != Float.MAX_VALUE) {
                minX = Math.min(minX, screen.x);
                minY = Math.min(minY, screen.y);
                maxX = Math.max(maxX, screen.x);
                maxY = Math.max(maxY, screen.y);
            }
        }
        return maxX <= minX || maxY <= minY ? null : new float[]{minX, minY, maxX, maxY};
    }

    public static boolean a(Vector2f screen) {
        return screen.x != Float.MAX_VALUE && screen.y != Float.MAX_VALUE
                && screen.x >= 0.0f && screen.y >= 0.0f
                && screen.x <= aM_.getWindow().getScaledWidth()
                && screen.y <= aM_.getWindow().getScaledHeight();
    }
}
