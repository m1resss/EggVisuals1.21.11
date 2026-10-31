package egg.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.Window;

public enum ParticleBlurRenderer {
    INSTANCE;

    private SimpleFramebuffer captureTarget;
    private final PingPongBlurTarget blur = new PingPongBlurTarget("egg_particle_blur");

    public void renderMesh(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, float f, float f2) {
        this.render(f, f2, () -> MeshDrawUtil.drawLabeled(renderPipeline, builtBuffer, "egg particles capture"));
    }

    public void render(float f, float f2, Runnable runnable) {
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer != null) {
            this.ensureTargets();
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.captureTarget.getColorAttachment(), 0);
            GpuTextureView gpuTextureView = RenderSystem.outputColorTextureOverride;
            RenderSystem.outputColorTextureOverride = this.captureTarget.getColorAttachmentView();

            try {
                runnable.run();
            } finally {
                RenderSystem.outputColorTextureOverride = gpuTextureView;
            }

            GpuSampler gpuSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

            try (FullscreenPass.RenderStateGuard renderStateGuard = FullscreenPass.guardState()) {
                this.blur.blurFirstPassAlt(this.captureTarget.getColorAttachmentView(), f, f / 2.0F, gpuSampler);
            }

            int alpha = (int) (f2 * 255.0F);
            int i = (alpha << 24) | 0x00FFFFFF;
            FullscreenPass.compositeUnflipped(Pipelines.PARTICLE_FLUFF_COMPOSITE, framebuffer.getColorAttachmentView(), this.blur.getColorView(), gpuSampler, i);
        }
    }

    private void ensureTargets() {
        Window window = MinecraftClient.getInstance().getWindow();
        int i = Math.max(1, window.getFramebufferWidth());
        int i2 = Math.max(1, window.getFramebufferHeight());
        this.captureTarget = ensureTarget(this.captureTarget, "egg_particle_capture", i, i2);
        this.blur.resize(i / 2, i2 / 2);
    }

    private static SimpleFramebuffer ensureTarget(SimpleFramebuffer simpleFramebuffer, String string, int i, int i2) {
        if (simpleFramebuffer == null) {
            return new SimpleFramebuffer(string, i, i2, false);
        }

        if (simpleFramebuffer.textureWidth != i || simpleFramebuffer.textureHeight != i2) {
            simpleFramebuffer.resize(i, i2);
        }

        return simpleFramebuffer;
    }
}
