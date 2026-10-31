package egg.client.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

public enum DischargeRenderer {
    INSTANCE;

    private static final long START_TIME = System.nanoTime();
    private SimpleFramebuffer captureTarget;
    private final PingPongBlurTarget blur = new PingPongBlurTarget("egg_discharge_blur");

    public void render(MatrixStack matrixStack, Camera camera, float f, Entity entity, int i, float f2, float f3, float f4, float f5) {
        Vec3d vec3d = ProjectionUtil.getInterpolatedPos(entity, f);
        Vec3d vec3d2 = vec3d.add(0.0, entity.getHeight() * 0.5, 0.0);
        Vector2f vector2f = ProjectionUtil.worldToScreen(vec3d2);
        if (vector2f != null && this.prepareTargets()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            float f6 = MathHelper.lerp((1.0F - MathHelper.cos(f5)) * 0.5F, f3, f4);
            Vector2f vector2f2 = ProjectionUtil.worldToScreen(vec3d.add(0.0, f6, 0.0));
            float f7 = vector2f2 == null ? -1.0F : 1.0F - vector2f2.y / sh;
            Framebuffer framebuffer = mc.getFramebuffer();
            EntityMaskRenderer.renderEntities(this.captureTarget, matrixStack, camera, f, List.of(entity));
            double d = camera.getCameraPos().distanceTo(vec3d2);
            float f8 = ProjectionUtil.getPixelsPerBlock(d) / framebuffer.textureHeight;
            Vector2f vector2f3 = ProjectionUtil.worldToScreen(vec3d.add(0.0, f3, 0.0));
            Vector2f vector2f4 = ProjectionUtil.worldToScreen(vec3d.add(0.0, f4, 0.0));
            float f9 = vector2f3 != null && vector2f4 != null ? (vector2f3.y - vector2f4.y) / sh : (f4 - f3) * f8;
            float f10 = Math.min(ProjectionUtil.getScaledSize(0.07F, d) / 2.0F, 16.0F);
            GpuSampler gpuSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

            try (FullscreenPass.RenderStateGuard renderStateGuard = FullscreenPass.guardState()) {
                FullscreenPass.blitUnflipped(Pipelines.GLOW_MASK, this.blur.getFramebuffer(), this.captureTarget.getColorAttachmentView(), gpuSampler, f10);
                this.blur.blur(this.blur.getColorView(), f10, f10, gpuSampler);
            }

            int i2 = framebuffer.textureHeight;
            FullscreenPass.drawDischargeShell(
                Pipelines.DISCHARGE_SHELL,
                framebuffer.getColorAttachmentView(),
                this.blur.getColorView(),
                this.captureTarget.getColorAttachmentView(),
                gpuSampler,
                i,
                getTime(),
                f2,
                vector2f.x / sw,
                1.0F - vector2f.y / sh,
                f8,
                (float)framebuffer.textureWidth / i2,
                f7,
                MathHelper.sin(f5),
                f5,
                f9
            );
        }
    }

    private static float getTime() {
        return (float)((System.nanoTime() - START_TIME) % 1000000000000L) / 1.0E9F;
    }

    private boolean prepareTargets() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        Framebuffer framebuffer = minecraftClient.getFramebuffer();
        if (framebuffer != null && framebuffer.getColorAttachmentView() != null) {
            Window window = minecraftClient.getWindow();
            int i = Math.max(1, window.getFramebufferWidth());
            int i2 = Math.max(1, window.getFramebufferHeight());
            if (this.captureTarget == null) {
                this.captureTarget = new SimpleFramebuffer("egg_discharge_capture", i, i2, true);
            } else if (this.captureTarget.textureWidth != i || this.captureTarget.textureHeight != i2) {
                this.captureTarget.resize(i, i2);
            }

            this.blur.resize(i / 2, i2 / 2);
            return true;
        } else {
            return false;
        }
    }

    public void reset() {
        if (this.captureTarget != null) {
            this.captureTarget.delete();
        }

        this.captureTarget = null;
        this.blur.close();
    }
}
