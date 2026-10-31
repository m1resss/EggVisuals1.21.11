package egg.client.render.shader;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;

public final class PingPongBlurTarget {
    private final String name;
    private SimpleFramebuffer primary;
    private SimpleFramebuffer secondary;

    public PingPongBlurTarget(String string) {
        this.name = string;
    }

    public void resize(int i, int i2) {
        int i3 = Math.max(1, i);
        int i4 = Math.max(1, i2);
        this.primary = ensureSize(this.primary, this.name + "_a", i3, i4);
        this.secondary = ensureSize(this.secondary, this.name + "_b", i3, i4);
    }

    SimpleFramebuffer getFramebuffer() {
        return this.primary;
    }

    public GpuTextureView getColorView() {
        return this.primary != null ? this.primary.getColorAttachmentView() : null;
    }

    public void blur(GpuTextureView gpuTextureView, float f, float f2, GpuSampler gpuSampler) {
        FullscreenPass.blit(Pipelines.GLOW_BLUR, this.secondary, gpuTextureView, gpuSampler, f);
        FullscreenPass.blit(Pipelines.GLOW_BLUR, this.primary, this.secondary.getColorAttachmentView(), gpuSampler, -f2);
    }

    public void blurFirstPassAlt(GpuTextureView gpuTextureView, float f, float f2, GpuSampler gpuSampler) {
        FullscreenPass.blitUnflipped(Pipelines.GLOW_BLUR, this.secondary, gpuTextureView, gpuSampler, f);
        FullscreenPass.blit(Pipelines.GLOW_BLUR, this.primary, this.secondary.getColorAttachmentView(), gpuSampler, -f2);
    }

    public void close() {
        if (this.primary != null) {
            this.primary.delete();
        }

        if (this.secondary != null) {
            this.secondary.delete();
        }

        this.primary = null;
        this.secondary = null;
    }

    private static SimpleFramebuffer ensureSize(SimpleFramebuffer simpleFramebuffer, String string, int i, int i2) {
        if (simpleFramebuffer == null) {
            return new SimpleFramebuffer(string, i, i2, false);
        }

        if (simpleFramebuffer.textureWidth != i || simpleFramebuffer.textureHeight != i2) {
            simpleFramebuffer.resize(i, i2);
        }

        return simpleFramebuffer;
    }
}
