package egg.client.util;

import egg.client.mixin.GameRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Screen projection utility with zero per-frame heap allocations.
 */
public final class ProjectUtil {
    private ProjectUtil() {}

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // Reusable objects (thread-confined to render thread)
    private static final Vector2f OUT_VEC2 = new Vector2f();
    private static final Vector3f WORK_VEC3 = new Vector3f();
    private static final Quaternionf WORK_QUAT = new Quaternionf();
    private static final Quaternionf BOB_QUAT = new Quaternionf();

    public static Vector2f toScreen(double x, double y, double z) {
        MinecraftClient mc = mc();
        if (mc == null || mc.world == null || mc.getEntityRenderDispatcher() == null) {
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        }
        return toScreen(x, y, z, mc.getRenderTickCounter().getTickProgress(true));
    }

    public static Vector2f toScreen(double x, double y, double z, float tickDelta) {
        MinecraftClient mc = mc();
        if (mc == null || mc.getEntityRenderDispatcher() == null || mc.gameRenderer == null) {
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        }
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) {
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        }

        Vec3d cameraPos = camera.getCameraPos();
        camera.getRotation().conjugate(WORK_QUAT);

        WORK_VEC3.set((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z));
        WORK_VEC3.rotate(WORK_QUAT);

        if (mc.options.getBobView().getValue() && mc.getCameraEntity() instanceof AbstractClientPlayerEntity player) {
            applyBobbing(player, WORK_VEC3, tickDelta);
        }

        double fov = ((GameRendererInvoker) (Object) mc.gameRenderer).invokeGetFov(camera, tickDelta, true);
        return projectToScreen(WORK_VEC3, fov);
    }

    private static void applyBobbing(AbstractClientPlayerEntity player, Vector3f result, float tickDelta) {
        float distance = player.getState().getReverseLerpedDistanceMoved(tickDelta);
        float stride = player.getState().lerpMovement(tickDelta);
        float phase = distance * ((float) Math.PI);
        float xRotation = Math.abs(MathHelper.cos(phase - 0.2f) * stride) * 5.0f;
        float zRotation = MathHelper.sin(phase) * stride * 3.0f;

        BOB_QUAT.rotationX((float) Math.toRadians(xRotation));
        result.rotate(BOB_QUAT);
        BOB_QUAT.rotationZ((float) Math.toRadians(zRotation));
        result.rotate(BOB_QUAT);
        result.add(MathHelper.sin(phase) * stride * 0.5f, -Math.abs(MathHelper.cos(phase) * stride), 0.0f);
    }

    private static float lastScale = 1.0f;

    private static Vector2f projectToScreen(Vector3f result, double fov) {
        MinecraftClient mc = mc();
        float halfWidth = mc.getWindow().getScaledWidth() / 2.0f;
        float halfHeight = mc.getWindow().getScaledHeight() / 2.0f;
        float scale = halfHeight / (result.z * (float) Math.tan(Math.toRadians(fov / 2.0d)));
        if (result.z < 0.0f) {
            lastScale = Math.abs(scale);
            return new Vector2f((-result.x * scale) + halfWidth, (result.y * scale) + halfHeight);
        }
        lastScale = 0.0f;
        return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
    }

    public static float getLastScale() {
        return lastScale;
    }

    public static boolean isOnScreen(Vector2f screen) {
        if (screen == null) return false;
        MinecraftClient mc = mc();
        if (mc == null || mc.getWindow() == null) return false;
        return screen.x != Float.MAX_VALUE && screen.y != Float.MAX_VALUE
                && screen.x >= 0.0f && screen.y >= 0.0f
                && screen.x <= mc.getWindow().getScaledWidth()
                && screen.y <= mc.getWindow().getScaledHeight();
    }
}
