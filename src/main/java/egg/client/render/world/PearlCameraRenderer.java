package egg.client.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class PearlCameraRenderer {
    public static final int WIDTH = 384;
    public static final int HEIGHT = 216;
    private static Framebuffer activeTarget;

    private final PreviewCamera camera = new PreviewCamera();
    private final Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(68), WIDTH / (float) HEIGHT, 0.04f, 96.0f);
    private final Matrix4f view = new Matrix4f();
    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projectionBuffer;
    private GlobalSettings globals;

    public record View(Vec3d position, Vec3d focus) {}

    @FunctionalInterface
    public interface TerrainPass {
        boolean render(Matrix4f view, Matrix4f projection, Vec3d position);
    }

    public static Framebuffer getActiveTarget() {
        return activeTarget;
    }

    public GpuTextureView getTexture() {
        return framebuffer == null ? null : framebuffer.getColorAttachmentView();
    }

    public boolean render(View frame, RenderTickCounter ticks, GpuBufferSlice fog, Vector4f fogColor, TerrainPass terrain) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer("egg_pearl_camera", WIDTH, HEIGHT, true);
            projectionBuffer = new RawProjectionMatrix("egg_pearl_projection");
            globals = new GlobalSettings();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        camera.place(frame);
        view.rotation(camera.getRotation().conjugate(new Quaternionf()));
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        var previousGlobals = RenderSystem.getGlobalSettingsUniform();
        var previousFog = RenderSystem.getShaderFog();
        try {
            activeTarget = framebuffer;
            globals.set(WIDTH, HEIGHT, 1.0, client.world.getTime(), ticks, 0, camera,
                    client.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS);
            RenderSystem.setProjectionMatrix(projectionBuffer.set(projection), ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(fog);
            int background = 0xFF000000 | (int) (fogColor.x * 255) << 16 | (int) (fogColor.y * 255) << 8 | (int) (fogColor.z * 255);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    framebuffer.getColorAttachment(), background, framebuffer.getDepthAttachment(), 1.0);
            return terrain.render(view, projection, frame.position());
        } finally {
            activeTarget = null;
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            RenderSystem.setGlobalSettingsUniform(previousGlobals);
            RenderSystem.setShaderFog(previousFog);
        }
    }

    public Vector2f project(Vec3d point) {
        Vec3d relative = point.subtract(camera.getCameraPos());
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1).mul(view).mul(projection);
        if (clip.w <= 0 || Math.abs(clip.x) >= clip.w * 0.92f || Math.abs(clip.y) >= clip.w * 0.88f) return null;
        return new Vector2f(0.5f + clip.x / (2 * clip.w), 0.5f - clip.y / (2 * clip.w));
    }

    public void close() {
        if (framebuffer == null) return;
        framebuffer.delete();
        projectionBuffer.close();
        globals.close();
        framebuffer = null;
        projectionBuffer = null;
        globals = null;
    }

    private static final class PreviewCamera extends Camera {
        void place(View frame) {
            Vec3d direction = frame.focus().subtract(frame.position());
            setPos(frame.position());
            setRotation((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                    (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalLength())));
        }
    }
}