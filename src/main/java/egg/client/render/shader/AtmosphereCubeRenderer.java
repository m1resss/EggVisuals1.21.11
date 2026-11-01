package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class AtmosphereCubeRenderer {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int INDEX_COUNT = 36;
    private static GpuBuffer cubeBuffer;

    private AtmosphereCubeRenderer() {
    }

    public static void draw(
        RenderPipeline renderPipeline, GpuTextureView gpuTextureView, OptionalInt optionalInt, GpuBufferSlice gpuBufferSlice, Matrix4fc matrix4fc, String string, Consumer<RenderPass> consumer
    ) {
        GpuBuffer gpuBuffer = getCubeBuffer();
        ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
        GpuBuffer gpuBuffer2 = shapeIndexBuffer.getIndexBuffer(INDEX_COUNT);
        GpuBufferSlice gpuBufferSlice2 = RenderSystem.getDynamicUniforms().write(matrix4fc, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Egg " + string, gpuTextureView, optionalInt)) {
            renderPass.setPipeline(renderPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice2);
            renderPass.setUniform("EggAmbiencePost", gpuBufferSlice);
            consumer.accept(renderPass);
            renderPass.setVertexBuffer(0, gpuBuffer);
            renderPass.setIndexBuffer(gpuBuffer2, shapeIndexBuffer.getIndexType());
            renderPass.drawIndexed(0, 0, INDEX_COUNT, 1);
        }
    }

    public static void close() {
        if (cubeBuffer != null) {
            cubeBuffer.close();
            cubeBuffer = null;
        }
    }

    private static GpuBuffer getCubeBuffer() {
        if (cubeBuffer != null && !cubeBuffer.isClosed()) {
            return cubeBuffer;
        }

        try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(24 * VertexFormats.POSITION.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, DrawMode.QUADS, VertexFormats.POSITION);
            addQuad(bufferBuilder, -1.0F, -1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, 1.0F, -1.0F);
            addQuad(bufferBuilder, 1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            addQuad(bufferBuilder, -1.0F, -1.0F, 1.0F, -1.0F, -1.0F, -1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F);
            addQuad(bufferBuilder, 1.0F, -1.0F, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F);
            addQuad(bufferBuilder, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 1.0F, 1.0F);
            addQuad(bufferBuilder, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F);
            BuiltBuffer builtBuffer = bufferBuilder.end();

            try {
                cubeBuffer = RenderSystem.getDevice().createBuffer(() -> "Egg atmosphere cube", 40, builtBuffer.getBuffer());
            } finally {
                builtBuffer.close();
            }
        }

        return cubeBuffer;
    }

    private static void addQuad(
        BufferBuilder bufferBuilder,
        float f,
        float f2,
        float f3,
        float f4,
        float f5,
        float f6,
        float f7,
        float f8,
        float f9,
        float f10,
        float f11,
        float f12
    ) {
        bufferBuilder.vertex(f, f2, f3);
        bufferBuilder.vertex(f4, f5, f6);
        bufferBuilder.vertex(f7, f8, f9);
        bufferBuilder.vertex(f10, f11, f12);
    }
}
