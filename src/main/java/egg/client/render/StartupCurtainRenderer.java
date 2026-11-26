package egg.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
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
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StartupCurtainRenderer {
    public static final float VIDEO_FPS = 25.0f;
    private static final Identifier VIDEO_TEX_ID = Identifier.of("egg", "startup_intro_frame");
    private static final String VIDEO_RESOURCE = "/assets/egg/startup_intro.bin";

    private static byte[][] encodedFrames;
    private static int frameWidth;
    private static int frameHeight;
    private static boolean loadAttempted;

    private static NativeImageBackedTexture videoTexture;
    private static NativeImage videoImage;
    private static int[] stagingPixels;
    private static int uploadedFrame = -1;

    private static volatile int asyncTargetIdx = -1;
    private static volatile int asyncReadyIdx = -1;
    private static volatile int[] asyncPixels;
    private static ExecutorService decodeExecutor;
    private static BufferAllocator allocator;

    private StartupCurtainRenderer() {}

    public static void preload() {
        if (loadAttempted) return;
        loadAttempted = true;
        try (InputStream raw = StartupCurtainRenderer.class.getResourceAsStream(VIDEO_RESOURCE)) {
            if (raw == null) return;
            DataInputStream in = new DataInputStream(raw);
            int count = in.readInt();
            int w = in.readInt();
            int h = in.readInt();
            if (count <= 0 || w <= 0 || h <= 0 || count > 1000) return;

            byte[][] frames = new byte[count][];
            for (int i = 0; i < count; i++) {
                int len = in.readInt();
                if (len <= 0 || len > 5_000_000) return;
                frames[i] = in.readNBytes(len);
            }
            encodedFrames = frames;
            frameWidth = w;
            frameHeight = h;
            stagingPixels = new int[w * h];
            decodeExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "EggIntroDecoder");
                t.setDaemon(true);
                return t;
            });
            decodeSync(0, stagingPixels);
        } catch (Throwable ignored) {
        }
    }

    public static int getFrameCount() {
        return encodedFrames == null ? 0 : encodedFrames.length;
    }

    public static float getVideoDurationMs() {
        if (encodedFrames == null || encodedFrames.length == 0) return 0.0f;
        return (encodedFrames.length - 4) * (1000.0f / VIDEO_FPS);
    }

    public static void render(MinecraftClient client, float timeSec, float split, float introFade,
                              boolean showProgress, float loadProgress, int scaledW, int scaledH) {
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) return;
        preload();
        if (encodedFrames == null || encodedFrames.length == 0) return;

        ensureTexture(client);
        int targetFrame = Math.min(encodedFrames.length - 1, Math.max(0, (int) (timeSec * VIDEO_FPS)) % encodedFrames.length);
        if (split > 0.001f) {
            targetFrame = Math.min(encodedFrames.length - 1, targetFrame);
        }
        updateTextureFrame(targetFrame);

        Framebuffer fb = client.getFramebuffer();
        if (fb.getColorAttachmentView() == null || videoTexture == null) return;
        GpuTextureView texView = videoTexture.getGlTextureView();
        if (texView == null) return;

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
                    GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
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
                        pass.bindTexture("Sampler0", texView, sampler);
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

    private static void ensureTexture(MinecraftClient client) {
        if (videoTexture != null || frameWidth <= 0 || frameHeight <= 0) return;
        videoImage = new NativeImage(frameWidth, frameHeight, true);
        if (stagingPixels != null) {
            writePixelsToImage(stagingPixels);
            uploadedFrame = 0;
        }
        videoTexture = new NativeImageBackedTexture(() -> VIDEO_TEX_ID.toString(), videoImage);
        videoTexture.upload();
        client.getTextureManager().registerTexture(VIDEO_TEX_ID, videoTexture);
        requestAsyncDecode(1 % encodedFrames.length);
    }

    private static void updateTextureFrame(int frameIdx) {
        if (frameIdx != uploadedFrame && videoImage != null && videoTexture != null) {
            if (asyncReadyIdx == frameIdx && asyncPixels != null) {
                writePixelsToImage(asyncPixels);
                videoTexture.upload();
                uploadedFrame = frameIdx;
            } else if (decodeSync(frameIdx, stagingPixels)) {
                writePixelsToImage(stagingPixels);
                videoTexture.upload();
                uploadedFrame = frameIdx;
            }
            int nextIdx = (frameIdx + 1) % encodedFrames.length;
            requestAsyncDecode(nextIdx);
        }
    }

    private static void requestAsyncDecode(int frameIdx) {
        if (decodeExecutor == null || asyncTargetIdx == frameIdx || asyncReadyIdx == frameIdx) return;
        asyncTargetIdx = frameIdx;
        decodeExecutor.submit(() -> {
            int[] buf = new int[frameWidth * frameHeight];
            if (decodeSync(frameIdx, buf)) {
                asyncPixels = buf;
                asyncReadyIdx = frameIdx;
            }
        });
    }

    private static boolean decodeSync(int frameIdx, int[] outPixels) {
        if (encodedFrames == null || frameIdx < 0 || frameIdx >= encodedFrames.length || outPixels == null) return false;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(encodedFrames[frameIdx])) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) return false;
            img.getRGB(0, 0, frameWidth, frameHeight, outPixels, 0, frameWidth);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writePixelsToImage(int[] pixels) {
        int w = frameWidth;
        int h = frameHeight;
        for (int y = 0; y < h; y++) {
            int rowBase = y * w;
            for (int x = 0; x < w; x++) {
                videoImage.setColorArgb(x, y, pixels[rowBase + x]);
            }
        }
    }

    public static void dispose() {
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
        if (decodeExecutor != null) {
            decodeExecutor.shutdownNow();
            decodeExecutor = null;
        }
        encodedFrames = null;
        stagingPixels = null;
        asyncPixels = null;
        uploadedFrame = -1;
        asyncTargetIdx = -1;
        asyncReadyIdx = -1;
    }
}
