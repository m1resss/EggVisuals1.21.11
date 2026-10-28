package egg.client.render.msdf;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class ImmediateRenderer {

    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();

    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();

    private static RenderPipeline pipeline(boolean world, boolean textured, VertexFormat.DrawMode mode, boolean additive) {        String key = (world ? "3d" : "2d") + "_" + (textured ? "tex_" : "") + mode.name().toLowerCase(java.util.Locale.ROOT) + (additive ? "_add" : "");
        return PIPELINES.computeIfAbsent(key, k -> {
            Identifier shader = Identifier.of("delta", textured ? "core/immediate_world_texture" : (world ? "core/immediate_world_color" : "core/immediate_color"));
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.of("delta", "pipeline/immediate_" + k))
                    .withVertexShader(shader)
                    .withFragmentShader(shader)
                    .withUniform("ImmediateData", UniformType.UNIFORM_BUFFER);

            builder.withBlend(additive ? (world ? BlendFunction.LIGHTNING : BlendFunction.ADDITIVE) : BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false);
            if (textured) {
                builder.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, mode)
                        .withSampler("Sampler0");
            } else {
                builder.withVertexFormat(VertexFormats.POSITION_COLOR, mode);
            }
            RenderPipeline built = builder.build();
            return RenderPipelines.register(built);
        });
    }

    public static void init() {
        pipeline(false, false, VertexFormat.DrawMode.TRIANGLE_STRIP, false);
        pipeline(false, false, VertexFormat.DrawMode.DEBUG_LINES, false);
        pipeline(false, false, VertexFormat.DrawMode.TRIANGLES, false);
        pipeline(false, false, VertexFormat.DrawMode.LINES, false);
        pipeline(true, false, VertexFormat.DrawMode.TRIANGLES, false);
        pipeline(true, false, VertexFormat.DrawMode.DEBUG_LINES, false);
    }

    static {
        init();
    }

    public static void setWorldProjection(Matrix4f projection) {
        WORLD_PROJECTION.set(projection);
        a_projectionReady = true;
    }

    private static final VertexFormat WORLD_LINE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("Normal", VertexFormatElement.NORMAL)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    private static final int LINE_NORMAL_OFFSET;
    private static final int LINE_WIDTH_OFFSET;
    private static final int LINE_NORMAL_PAD;
    private static final int LINE_VERTEX_SIZE;

    static {
        int normalOff = -1;
        int widthOff = -1;
        int size = 0;
        for (VertexFormatElement element : WORLD_LINE_FORMAT.getElements()) {
            if (element == VertexFormatElement.NORMAL) {
                normalOff = size;
            }
            if (element == VertexFormatElement.LINE_WIDTH) {
                widthOff = size;
            }
            size += element.byteSize();
        }
        LINE_NORMAL_OFFSET = normalOff;
        LINE_WIDTH_OFFSET = widthOff;
        LINE_NORMAL_PAD = normalOff >= 0 && widthOff >= 0 ? widthOff - normalOff - 3 : 0;
        LINE_VERTEX_SIZE = WORLD_LINE_FORMAT.getVertexSize();
    }

    private static final RenderPipeline WORLD_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("delta", "pipeline/world_line"))
                    .withVertexShader(Identifier.of("delta", "core/world_line"))
                    .withFragmentShader(Identifier.of("delta", "core/world_line"))
                    .withVertexFormat(WORLD_LINE_FORMAT, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("LineData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    public static Matrix4f worldLineProjView(Matrix4f modelView) {
        Matrix4f projView = new Matrix4f(WORLD_PROJECTION);
        Quaternionf cameraRotation = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
        if (cameraRotation != null) {
            projView.mul(new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf())));
        }
        projView.mul(modelView);
        return projView;
    }

    public static void drawWorldLines(Matrix4f modelView, ByteBuffer data, int lineCount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null || lineCount <= 0) return;
        data.position(0);
        if (!data.hasRemaining()) return;

        Matrix4f projView = worldLineProjView(modelView);

        java.nio.FloatBuffer params = java.nio.FloatBuffer.allocate(4);
        params.put(client.getWindow().getFramebufferWidth());
        params.put(client.getWindow().getFramebufferHeight());
        params.put(0f);
        params.put(0f);
        params.rewind();

        GpuBuffer uniformBuffer = null;
        GpuBuffer vertexBuffer = null;
        try {
            java.nio.ByteBuffer uboData = MemoryUtil.memAlloc(80);
            projView.get(0, uboData);
            uboData.putFloat(64, params.get(0));
            uboData.putFloat(68, params.get(1));
            uboData.putFloat(72, 0f);
            uboData.putFloat(76, 0f);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "delta:world_line_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    uboData);
            MemoryUtil.memFree(uboData);

            data.position(0);
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "delta:world_line_vertices",
                    GpuBuffer.USAGE_VERTEX,
                    data);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "delta:world_lines_pass",
                    client.getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(WORLD_LINE_PIPELINE);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setUniform("LineData", uniformBuffer);
                pass.draw(0, lineCount * 6);
            }
        } finally {
            if (uniformBuffer != null) uniformBuffer.close();
            if (vertexBuffer != null) vertexBuffer.close();
        }
    }

    public static void putWorldLineVertex(ByteBuffer buffer, float x, float y, float z,
                                          float dirX, float dirY, float dirZ,
                                          int r, int g, int b, int a, float signedWidth) {
        buffer.putFloat(x).putFloat(y).putFloat(z);
        buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        buffer.put((byte) Math.round(dirX * 127.0f))
              .put((byte) Math.round(dirY * 127.0f))
              .put((byte) Math.round(dirZ * 127.0f));
        for (int i = 0; i < LINE_NORMAL_PAD; i++) {
            buffer.put((byte) 0);
        }
        buffer.putFloat(signedWidth);
    }

    public static int worldLineVertexSize() {
        return LINE_VERTEX_SIZE;
    }

    public static boolean projectionReady() {
        return a_projectionReady;
    }

    private static boolean a_projectionReady = false;

    public static void draw2D(VertexFormat.DrawMode mode, ByteBuffer data, int vertexCount) {
        draw(false, false, mode, null, null, data, vertexCount);
    }

    public static void draw2DAdditive(VertexFormat.DrawMode mode, ByteBuffer data, int vertexCount) {
        draw(false, false, mode, null, null, data, vertexCount, true);
    }

    public static void drawWorld(VertexFormat.DrawMode mode, Matrix4f modelView, ByteBuffer data, int vertexCount) {
        drawWorld(mode, modelView, data, vertexCount, false);
    }

    public static void drawWorld(VertexFormat.DrawMode mode, Matrix4f modelView, ByteBuffer data, int vertexCount, boolean additive) {
        draw(true, false, mode, modelView, null, data, vertexCount, additive);
    }

    public static void drawWorldTextured(GpuTextureView texture, Matrix4f modelView, ByteBuffer data, int vertexCount, boolean additive) {
        draw(true, true, VertexFormat.DrawMode.TRIANGLES, modelView, texture, data, vertexCount, additive);
    }

    private static void draw(boolean world, boolean textured, VertexFormat.DrawMode mode,
                             Matrix4f modelView, GpuTextureView texture, ByteBuffer data, int vertexCount) {
        draw(world, textured, mode, modelView, texture, data, vertexCount, false);
    }

    private static void draw(boolean world, boolean textured, VertexFormat.DrawMode mode,
                             Matrix4f modelView, GpuTextureView texture, ByteBuffer data, int vertexCount, boolean additive) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null || vertexCount <= 0) return;
        data.position(0);
        if (!data.hasRemaining()) return;
        if (!world && textured) return;

        RenderPipeline pipeline = pipeline(world, textured, mode, additive);
        data.position(0);
        GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "delta:immediate_vertices",
                GpuBuffer.USAGE_VERTEX,
                data);

        GpuBuffer uniformBuffer = null;
        if (world) {
            Matrix4f projView = new Matrix4f(WORLD_PROJECTION);

            Quaternionf cameraRotation = client.gameRenderer.getCamera().getRotation();
            if (cameraRotation != null) {
                projView.mul(new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf())));
            }
            projView.mul(modelView);
            ByteBuffer uboData = MemoryUtil.memAlloc(64);
            projView.get(uboData);

            uboData.rewind();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "delta:immediate_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    uboData);
            MemoryUtil.memFree(uboData);
        } else {
            Matrix4f ortho = new Matrix4f().ortho(0.0f, (float) client.getWindow().getScaledWidth(), (float) client.getWindow().getScaledHeight(), 0.0f, -1000.0f, 1000.0f);
            ByteBuffer uboData = MemoryUtil.memAlloc(64);
            ortho.get(uboData);
            uboData.rewind();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "delta:immediate_uniform_2d",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    uboData);
            MemoryUtil.memFree(uboData);
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "delta:immediate_pass",
                    client.getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, vertexBuffer);
                if (uniformBuffer != null) {
                    pass.setUniform("ImmediateData", uniformBuffer);
                }
                if (textured) {
                    pass.bindTexture("Sampler0", texture, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                }
                pass.draw(0, vertexCount);
            }
        } finally {
            vertexBuffer.close();
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
        }
    }

    private static final org.joml.Vector4f COLOR_MODULATOR = new org.joml.Vector4f(1f, 1f, 1f, 1f);
    private static final org.joml.Vector3f MODEL_OFFSET = new org.joml.Vector3f(0f, 0f, 0f);
    private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();

    private static com.mojang.blaze3d.buffers.GpuBufferSlice writeDynamicTransforms() {
        return RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
    }

    public static void putVertexColor(ByteBuffer buffer, float x, float y, float z, int r, int g, int b, int a) {
        buffer.putFloat(x).putFloat(y).putFloat(z);
        buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
    }

    public static void putVertexTextured(ByteBuffer buffer, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        buffer.putFloat(x).putFloat(y).putFloat(z);
        buffer.putFloat(u).putFloat(v);
        buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
    }

    private ImmediateRenderer() {
    }
}
