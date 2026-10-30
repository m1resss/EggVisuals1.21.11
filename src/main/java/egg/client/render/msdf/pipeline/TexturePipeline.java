package egg.client.render.msdf.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TexturePipeline implements DrawBatcher.Batched {

    private static final Identifier PIPELINE_ID = Identifier.of("delta", "pipeline/texture");
    private static final Identifier VERTEX_SHADER = Identifier.of("delta", "core/texture");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("delta", "core/texture");

    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("TextureData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("delta", "pipeline/texture_glow"))
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("TextureData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final int BUFFER_SIZE = 256;

    private static final int UNIFORM_RING = 64;

    private record TexDraw(RenderPipeline pipeline, GpuTextureView view, GpuBuffer uniformBuffer) {
    }

    private GpuBuffer[] uniformBuffers;
    private int uniformRingIndex = 0;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private final ArrayList<TexDraw> pendingDraws = new ArrayList<>();

    private final int batchLayer;

    private final java.util.Map<GpuTexture, GpuTextureView> textureViewCache = new java.util.HashMap<>();

    public TexturePipeline() {
        this(2);
    }

    public TexturePipeline(int batchLayer) {
        this.batchLayer = batchLayer;
    }

    private void ensureInitialized() {
        if (initialized)
            return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:texture_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData);
        MemoryUtil.memFree(dummyData);

        this.uniformBuffers = new GpuBuffer[UNIFORM_RING];

        initialized = true;
    }

    private GpuBuffer nextUniformBuffer() {
        GpuBuffer buf = uniformBuffers[uniformRingIndex];
        if (buf == null) {
            final int idx = uniformRingIndex;
            buf = RenderSystem.getDevice().createBuffer(
                    () -> "minecraft:texture_uniform_" + idx,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    BUFFER_SIZE);
            uniformBuffers[uniformRingIndex] = buf;
        }
        uniformRingIndex = (uniformRingIndex + 1) % UNIFORM_RING;
        return buf;
    }

    public void drawGlowTexture(Identifier textureId, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1,
                               int[] colors, float[] radii, float smoothness, float rotation) {
        queueDraw(textureId, x, y, width, height, u0, v0, u1, v1, colors, radii, smoothness, rotation, GLOW_PIPELINE);
    }

    public void drawTexture(Identifier textureId, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1,
                            int[] colors, float[] radii, float smoothness) {
        drawTexture(textureId, x, y, width, height, u0, v0, u1, v1, colors, radii, smoothness, 0f);
    }

    public void drawTexture(Identifier textureId, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1,
                            int[] colors, float[] radii, float smoothness, float rotation) {
        queueDraw(textureId, x, y, width, height, u0, v0, u1, v1, colors, radii, smoothness, rotation, PIPELINE);
    }

    private void queueDraw(Identifier textureId, float x, float y, float width, float height,
                           float u0, float v0, float u1, float v1,
                           int[] colors, float[] radii, float smoothness, float rotation,
                           RenderPipeline pipeline) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        AbstractTexture texture = client.getTextureManager().getTexture(textureId);
        if (texture == null) return;

        GpuTexture gpuTexture = texture.getGlTexture();
        if (gpuTexture == null) return;

        queueDraw(getCachedTextureView(gpuTexture), x, y, width, height, u0, v0, u1, v1,
                colors, radii, smoothness, rotation, pipeline);
    }

    private void queueDraw(GpuTextureView textureView, float x, float y, float width, float height,
                           float u0, float v0, float u1, float v1,
                           int[] colors, float[] radii, float smoothness, float rotation,
                           RenderPipeline pipeline) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        float guiScale = (float) (client.getWindow() != null ? client.getWindow().getScaleFactor() : 2.0);
        float screenWidth = client.getWindow() != null ? (float) client.getWindow().getScaledWidth() : 960f;
        float screenHeight = client.getWindow() != null ? (float) client.getWindow().getScaledHeight() : 540f;

        prepareUniformData(x, y, width, height, u0, v0, u1, v1,
                screenWidth, screenHeight, guiScale,
                colors, radii, smoothness, rotation);

        GpuBuffer uniformBuffer = nextUniformBuffer();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);

        pendingDraws.add(new TexDraw(pipeline, textureView, uniformBuffer));

        if (DrawBatcher.isEnabled()) {
            DrawBatcher.register(this);

            if (pendingDraws.size() >= UNIFORM_RING) {
                DrawBatcher.drawImmediate(this);
            }
        } else {

            DrawBatcher.flushPending();
            DrawBatcher.drawImmediate(this);
        }
    }

    public void drawBlurTexture(GpuTextureView blurView, float x, float y, float width, float height,
                                float[] radii, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null || blurView == null || alpha <= 0.0f) return;

        float fbWidth = client.getWindow().getFramebufferWidth();
        float fbHeight = client.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        float guiScale = (float) (client.getWindow() != null ? client.getWindow().getScaleFactor() : 2.0);
        float u0 = (x * guiScale) / fbWidth;
        float u1 = ((x + width) * guiScale) / fbWidth;
        float v0 = 1.0f - (y * guiScale) / fbHeight;
        float v1 = 1.0f - ((y + height) * guiScale) / fbHeight;

        int alphaByte = (int) (Math.min(1.0f, Math.max(0.0f, alpha)) * 255.0f);
        queueDraw(blurView, x, y, width, height, u0, v0, u1, v1,
                new int[]{(alphaByte << 24) | 0x00FFFFFF}, radii, 1.0f, 0f, PIPELINE);
    }

    @Override
    public int batchLayer() {
        return this.batchLayer;
    }

    @Override
    public void uploadBatch(CommandEncoder encoder) {

    }

    @Override
    public void drawBatch(RenderPass pass) {
        if (pendingDraws.isEmpty()) return;

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        RenderPipeline currentPipeline = null;
        for (TexDraw draw : pendingDraws) {
            if (draw.view().isClosed()) continue;
            if (draw.pipeline() != currentPipeline) {
                pass.setPipeline(draw.pipeline());
                pass.setVertexBuffer(0, dummyVertexBuffer);
                currentPipeline = draw.pipeline();
            }
            pass.bindTexture("Sampler0", draw.view(), sampler);
            pass.setUniform("TextureData", draw.uniformBuffer());
            pass.draw(0, 6);
        }

        pendingDraws.clear();
    }

    @Override
    public void discardBatch() {
        pendingDraws.clear();
    }

    public void flush() {
        if (pendingDraws.isEmpty()) return;
        DrawBatcher.drawImmediate(this);
    }

    private void prepareUniformData(float x, float y, float w, float h,
                                    float u0, float v0, float u1, float v1,
                                    float screenWidth, float screenHeight, float guiScale,
                                    int[] colors, float[] radii, float smoothness, float rotation) {
        dataBuffer.clear();

        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(smoothness);
        dataBuffer.putFloat(guiScale);

        dataBuffer.putFloat(x);
        dataBuffer.putFloat(y);
        dataBuffer.putFloat(w);
        dataBuffer.putFloat(h);

        dataBuffer.putFloat(u0);
        dataBuffer.putFloat(v0);
        dataBuffer.putFloat(u1);
        dataBuffer.putFloat(v1);

        dataBuffer.putFloat(radii[0]);
        dataBuffer.putFloat(radii[1]);
        dataBuffer.putFloat(radii[2]);
        dataBuffer.putFloat(radii[3]);

        float rotationRadians = (float) Math.toRadians(rotation);
        dataBuffer.putFloat(rotationRadians);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);

        for (int i = 0; i < 4; i++) {
            int color = i < colors.length ? colors[i] : colors[colors.length - 1];
            float a = ((color >> 24) & 0xFF) / 255.0f;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            dataBuffer.putFloat(r);
            dataBuffer.putFloat(g);
            dataBuffer.putFloat(b);
            dataBuffer.putFloat(a);
        }

        dataBuffer.flip();
    }

    private GpuTextureView getCachedTextureView(GpuTexture gpuTexture) {
        GpuTextureView view = textureViewCache.get(gpuTexture);
        if (view == null || view.isClosed()) {
            textureViewCache.entrySet().removeIf(e -> {
                if (e.getKey().isClosed() || e.getValue().isClosed()) {
                    if (!e.getValue().isClosed()) e.getValue().close();
                    return true;
                }
                return false;
            });
            view = RenderSystem.getDevice().createTextureView(gpuTexture);
            textureViewCache.put(gpuTexture, view);
        }
        return view;
    }

    public void close() {
        pendingDraws.clear();
        for (GpuTextureView view : textureViewCache.values()) {
            if (!view.isClosed()) view.close();
        }
        textureViewCache.clear();
        if (uniformBuffers != null) {
            for (GpuBuffer buf : uniformBuffers) {
                if (buf != null) buf.close();
            }
            uniformBuffers = null;
        }
        uniformRingIndex = 0;
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}
