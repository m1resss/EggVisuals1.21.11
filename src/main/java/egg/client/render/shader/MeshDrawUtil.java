package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class MeshDrawUtil {
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f ZERO_OFFSET = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int[] VIEWPORT = new int[4];
    private static final int[] SCISSOR = new int[4];

    private MeshDrawUtil() {
    }

    public static void draw(RenderPipeline renderPipeline, BuiltBuffer builtBuffer) {
        drawInternal(renderPipeline, builtBuffer, null, null, null);
    }

    public static void drawTextured(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, Identifier identifier) {
        AbstractTexture abstractTexture = MinecraftClient.getInstance().getTextureManager().getTexture(identifier);
        drawInternal(renderPipeline, builtBuffer, null, abstractTexture.getGlTextureView(), abstractTexture.getSampler());
    }

    public static void drawWithTexture(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, GpuTextureView gpuTextureView, GpuSampler gpuSampler) {
        drawInternal(renderPipeline, builtBuffer, null, gpuTextureView, gpuSampler);
    }

    public static void drawLabeled(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, String string) {
        drawInternal(renderPipeline, builtBuffer, string, null, null);
    }

    public static void drawLabeledTextured(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, String string, Identifier identifier) {
        AbstractTexture abstractTexture = MinecraftClient.getInstance().getTextureManager().getTexture(identifier);
        drawInternal(renderPipeline, builtBuffer, string, abstractTexture.getGlTextureView(), abstractTexture.getSampler());
    }

    public static void drawWithUniform(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, String string, String string2, GpuBufferSlice gpuBufferSlice) {
        drawFull(renderPipeline, builtBuffer, string, null, null, string2, gpuBufferSlice);
    }

    private static void drawInternal(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, String string, GpuTextureView gpuTextureView, GpuSampler gpuSampler) {
        drawFull(renderPipeline, builtBuffer, string, gpuTextureView, gpuSampler, null, null);
    }

    private static void drawFull(RenderPipeline renderPipeline, BuiltBuffer builtBuffer, String string, GpuTextureView gpuTextureView, GpuSampler gpuSampler, String string2, GpuBufferSlice gpuBufferSlice) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        Framebuffer framebuffer = minecraftClient.getFramebuffer();
        GpuBuffer gpuBuffer = FrameBufferPool.createBuffer(() -> "egg pipeline vbo", builtBuffer.getBuffer());
        ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(builtBuffer.getDrawParameters().mode());
        GpuBuffer gpuBuffer2 = shapeIndexBuffer.getIndexBuffer(builtBuffer.getDrawParameters().indexCount());
        GpuBufferSlice gpuBufferSlice2 = RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(), WHITE, ZERO_OFFSET, IDENTITY);
        String string3 = string != null ? string : "Egg pipeline draw";
        boolean flag = GlStateSnapshot.isOpenGl();
        int i = flag ? GlStateManager.getFrameBuffer(36009) : 0;
        int i2 = flag ? GlStateManager.getFrameBuffer(36008) : 0;
        boolean flag2 = false;
        if (flag) {
            GL11.glGetIntegerv(2978, VIEWPORT);
            GL11.glGetIntegerv(3088, SCISSOR);
            flag2 = GL11.glIsEnabled(3089);
        }

        GpuTextureView gpuTextureView2 = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : framebuffer.getColorAttachmentView();
        GpuTextureView gpuTextureView3 = RenderSystem.outputDepthTextureOverride != null
            ? RenderSystem.outputDepthTextureOverride
            : (framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null);

        try {
            BuiltBuffer builtBuffer2 = builtBuffer;

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> string3, gpuTextureView2, OptionalInt.empty(), gpuTextureView3, OptionalDouble.empty())) {
                renderPass.setPipeline(renderPipeline);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", gpuBufferSlice2);
                if (string2 != null && gpuBufferSlice != null) {
                    renderPass.setUniform(string2, gpuBufferSlice);
                }

                if (gpuTextureView != null && gpuSampler != null) {
                    renderPass.bindTexture("Sampler0", gpuTextureView, gpuSampler);
                }

                renderPass.setVertexBuffer(0, gpuBuffer);
                renderPass.setIndexBuffer(gpuBuffer2, shapeIndexBuffer.getIndexType());
                renderPass.drawIndexed(0, 0, builtBuffer.getDrawParameters().indexCount(), 1);
            } catch (Throwable throwable) {
                if (builtBuffer2 != null) {
                    try {
                        builtBuffer2.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                }

                throw throwable;
            }

            if (builtBuffer != null) {
                builtBuffer.close();
            }
        } finally {
            if (flag) {
                GlStateManager._glBindFramebuffer(36009, i);
                GlStateManager._glBindFramebuffer(36008, i2);
                GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
                GlStateManager._scissorBox(SCISSOR[0], SCISSOR[1], SCISSOR[2], SCISSOR[3]);
                if (flag2) {
                    GlStateManager._enableScissorTest();
                } else {
                    GlStateManager._disableScissorTest();
                }
            }
        }
    }
}
