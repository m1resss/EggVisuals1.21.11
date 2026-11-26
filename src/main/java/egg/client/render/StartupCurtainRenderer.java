package egg.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.shader.CausticUniformStorage;
import egg.client.render.shader.CausticUniforms;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.MathHelper;

import java.util.OptionalInt;

public final class StartupCurtainRenderer {
    private static BufferAllocator allocator;

    private StartupCurtainRenderer() {}

    public static void render(MinecraftClient client, float timeSec, float split, float introFade,
                              boolean showProgress, float loadProgress, int scaledW, int scaledH) {
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) return;
        Framebuffer fb = client.getFramebuffer();
        if (fb.getColorAttachmentView() == null) return;

        float fbW = Math.max(1.0f, client.getWindow().getFramebufferWidth());
        float fbH = Math.max(1.0f, client.getWindow().getFramebufferHeight());
        float aspect = fbW / fbH;

        if (allocator == null) {
            allocator = new BufferAllocator(256);
        }
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            mesh.vertex(-1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f).color(255, 255, 255, 255);
            mesh.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f).color(255, 255, 255, 255);
            mesh.vertex(1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f).color(255, 255, 255, 255);
            mesh.vertex(1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f).color(255, 255, 255, 255);
            BuiltBuffer built = mesh.endNullable();
            if (built != null) {
                try {
                    GpuBuffer vbo = FrameBufferPool.createBuffer(() -> "egg startup curtain vbo", built.getBuffer());
                    var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
                    GpuBuffer ibo = indices.getIndexBuffer(built.getDrawParameters().indexCount());
                    CausticUniforms u = new CausticUniforms(
                            timeSec,
                            Math.clamp(split, 0.0f, 1.0f),
                            Math.clamp(introFade, 0.0f, 1.0f),
                            aspect,
                            loadProgress, 0.0f, 0.0f, 0.0f,
                            1.0f, 0.0f, 0.0f, 0.0f
                    );
                    GpuBufferSlice slice = CausticUniformStorage.write(u);
                    try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                            () -> "egg startup curtain", fb.getColorAttachmentView(), OptionalInt.empty())) {
                        pass.setPipeline(Pipelines.STARTUP_CURTAIN);
                        pass.setUniform("CausticUniforms", slice);
                        pass.setVertexBuffer(0, vbo);
                        pass.setIndexBuffer(ibo, indices.getIndexType());
                        pass.drawIndexed(0, 0, built.getDrawParameters().indexCount(), 1);
                    }
                } finally {
                    built.close();
                }
            }
        } finally {
            allocator.reset();
        }

        if (showProgress && split < 0.05f) {
            float barW = Math.max(48.0f, Math.min(scaledW, scaledH) * 0.24f);
            float barH = 2.0f;
            float barX = (scaledW - barW) * 0.5f;
            float barY = scaledH * 0.89f;
            float barAlpha = (1.0f - MathHelper.clamp(split / 0.05f, 0.0f, 1.0f)) * introFade;
            Draw2DProcessor.INSTANCE.drawRect(null, barX, barY, barW, barH, 1.0f,
                    ColorUtil.rgba(255, 255, 255, Math.round(48 * barAlpha)));
            float fillW = Math.max(2.0f, barW * Math.clamp(loadProgress, 0.0f, 1.0f));
            Draw2DProcessor.INSTANCE.drawRect(null, barX, barY, fillW, barH, 1.0f,
                    ColorUtil.rgba(235, 240, 252, Math.round(215 * barAlpha)));
            DrawBatcher.flushPending();
        }
    }

    public static void dispose() {
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }
}
