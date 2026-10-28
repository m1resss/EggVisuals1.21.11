package egg.client.render.msdf.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class BlurPipeline {

    private static final int MIP_COUNT = 4;
    private static final int MAX_PASS_OFFSET = 24;
    private static final int UNIFORM_SIZE = 16;

    private static final int UNIFORM_SLOTS = 8;

    private static final RenderPipeline DOWN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("delta", "pipeline/kawase_down"))
                    .withVertexShader(Identifier.of("delta", "core/kawase_down"))
                    .withFragmentShader(Identifier.of("delta", "core/kawase_down"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final RenderPipeline UP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("delta", "pipeline/kawase_up"))
                    .withVertexShader(Identifier.of("delta", "core/kawase_up"))
                    .withFragmentShader(Identifier.of("delta", "core/kawase_up"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private GpuTexture[] textures;
    private GpuTextureView[] views;
    private GpuBuffer[] uniformBuffers;
    private GpuBuffer dummyVertexBuffer;
    private int width;
    private int height;

    public void capture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;
        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;
        ensureSize(fbWidth, fbHeight);

        GpuTextureView screenView = client.getFramebuffer().getColorAttachmentView();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        runPass(encoder, DOWN_PIPELINE, screenView, views[0], 0, 0);
        for (int i = 0; i < MIP_COUNT - 1; i++) {
            runPass(encoder, DOWN_PIPELINE, views[i], views[i + 1], i + 1, MAX_PASS_OFFSET * (i + 1) / 3.0f);
        }

        for (int i = MIP_COUNT - 1; i > 0; i--) {
            runPass(encoder, UP_PIPELINE, views[i], views[i - 1], i, MAX_PASS_OFFSET * i / 3.0f);
        }
    }

    public GpuTextureView view() {
        return views != null ? views[0] : null;
    }

    private void runPass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView source,
                         GpuTextureView destination, int slot, float offset) {
        ByteBuffer data = MemoryUtil.memAlloc(UNIFORM_SIZE);
        data.putFloat(width).putFloat(height).putFloat(offset).putFloat(0.0f);
        data.flip();
        encoder.writeToBuffer(uniformBuffer(slot).slice(0, UNIFORM_SIZE), data);
        MemoryUtil.memFree(data);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "delta:kawase_pass",
                destination,
                OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("Sampler0", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.setUniform("KawaseData", uniformBuffer(slot));
            pass.setVertexBuffer(0, dummyVertexBuffer());
            pass.draw(0, 6);
        }
    }

    private void ensureSize(int newWidth, int newHeight) {
        if (views != null && width == newWidth && height == newHeight) return;
        closeTextures();
        this.width = newWidth;
        this.height = newHeight;
        this.textures = new GpuTexture[MIP_COUNT];
        this.views = new GpuTextureView[MIP_COUNT];
        for (int i = 0; i < MIP_COUNT; i++) {
            final int index = i;
            textures[i] = RenderSystem.getDevice().createTexture(
                    () -> "delta:blur_mip_" + index,
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, newWidth, newHeight, 1, 1);
            views[i] = RenderSystem.getDevice().createTextureView(textures[i]);
        }
    }

    private GpuBuffer uniformBuffer(int slot) {
        if (uniformBuffers == null) {
            uniformBuffers = new GpuBuffer[UNIFORM_SLOTS];
        }
        if (uniformBuffers[slot] == null) {
            final int index = slot;
            uniformBuffers[slot] = RenderSystem.getDevice().createBuffer(
                    () -> "delta:blur_uniform_" + index,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        return uniformBuffers[slot];
    }

    private GpuBuffer dummyVertexBuffer() {
        if (dummyVertexBuffer == null) {
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "delta:blur_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummyData);
            MemoryUtil.memFree(dummyData);
        }
        return dummyVertexBuffer;
    }

    private void closeTextures() {
        if (views != null) {
            for (GpuTextureView view : views) {
                if (view != null && !view.isClosed()) view.close();
            }
            views = null;
        }
        if (textures != null) {
            for (GpuTexture texture : textures) {
                if (texture != null && !texture.isClosed()) texture.close();
            }
            textures = null;
        }
    }

    public void close() {
        closeTextures();
        if (uniformBuffers != null) {
            for (GpuBuffer buffer : uniformBuffers) {
                if (buffer != null) buffer.close();
            }
            uniformBuffers = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        width = 0;
        height = 0;
    }
}
