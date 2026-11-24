package egg.client.render.shader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class ProjectionUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Matrix4f modelViewMatrix = new Matrix4f();
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static boolean matricesValid = false;
    private static Frustum frustum;
    private static final Vector4f workVec4 = new Vector4f();

    public static void updateMatrices(Matrix4f matrix4f, Matrix4f matrix4f2) {
        modelViewMatrix.set(matrix4f);
        projectionMatrix.set(matrix4f2);
        matricesValid = true;
        Frustum frustum2 = new Frustum(matrix4f, matrix4f2);
        Vec3d vec3d = MC.gameRenderer.getCamera().getCameraPos();
        frustum2.setPosition(vec3d.x, vec3d.y, vec3d.z);
        frustum = frustum2;
    }

    public static boolean isVisible(Box box) {
        return frustum == null || frustum.isVisible(box);
    }

    public static Vector2f worldToScreen(Vec3d vec3d) {
        return worldToScreen(vec3d.x, vec3d.y, vec3d.z);
    }

    public static boolean worldToScreen(Vec3d vec3d, Vector2f out) {
        return worldToScreen(vec3d.x, vec3d.y, vec3d.z, out);
    }

    public static boolean worldToScreen(double d, double d2, double d3, Vector2f out) {
        if (!worldToClip(d, d2, d3, workVec4)) return false;
        float invW = 1.0F / workVec4.w;
        int sw = MC.getWindow().getScaledWidth();
        int sh = MC.getWindow().getScaledHeight();
        out.set((workVec4.x * invW + 1.0F) * 0.5F * sw, (1.0F - workVec4.y * invW) * 0.5F * sh);
        return true;
    }

    public static boolean worldToClip(double x, double y, double z, Vector4f out) {
        if (!matricesValid) return false;
        Camera camera = MC.gameRenderer.getCamera();
        if (camera == null) return false;
        Vec3d cameraPos = camera.getCameraPos();
        out.set((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z), 1f)
                .mul(modelViewMatrix).mul(projectionMatrix);
        return out.w > 0f;
    }

    public static Vector2f worldToScreen(double d, double d2, double d3) {
        Vector2f res = new Vector2f();
        return worldToScreen(d, d2, d3, res) ? res : null;
    }

    public static float getPixelsPerBlock(double d) {
        return !matricesValid ? 0.0F : (float)(projectionMatrix.m11() * MC.getWindow().getFramebufferHeight() / (2.0 * Math.max(d, 0.5)));
    }

    public static float getScaledSize(float f, double d) {
        return MathHelper.clamp(f * getPixelsPerBlock(d), 1.0F, 36.0F);
    }

    public static Vec3d getInterpolatedPos(Entity entity, float f) {
        return new Vec3d(
            MathHelper.lerp(f, entity.lastRenderX, entity.getX()),
            MathHelper.lerp(f, entity.lastRenderY, entity.getY()),
            MathHelper.lerp(f, entity.lastRenderZ, entity.getZ())
        );
    }

    private ProjectionUtil() {
    }
}
