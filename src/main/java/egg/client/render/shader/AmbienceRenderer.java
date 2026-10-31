package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import egg.client.module.ModuleManager;
import egg.client.module.render.WorldTweaks;
import egg.client.util.ColorUtil;
import java.util.Arrays;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public enum AmbienceRenderer {
    INSTANCE;

    private static final int BLOOM_MIPS = 4;
    private static final float BLUR_OFFSET = 1.2F;
    private static float skyBrightness = 1.0F;
    private static float skyVisibility = 1.0F;
    private static int skyColor = 0xFF7FB2FF;
    private static int horizonColor = 0xFFC0D8FF;
    private static float sunAngle = 0.0F;

    private SimpleFramebuffer captureTarget;
    private SimpleFramebuffer resultTarget;
    private SimpleFramebuffer[] mipTargets = new SimpleFramebuffer[0];

    private final TextureSnapshot opaqueDepth = new TextureSnapshot("egg_ambience_depth_opaque", TextureFormat.DEPTH32);
    private final TextureSnapshot worldDepth = new TextureSnapshot("egg_ambience_depth_world", TextureFormat.DEPTH32);
    private final TextureSnapshot sceneColor = new TextureSnapshot("egg_ambience_scene_color", TextureFormat.RGBA8);
    private boolean opaqueCaptured;
    private boolean reflectionReady;

    public static boolean isReflectionsEnabled() {
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        return wt != null && wt.isEnabled() && wt.isReflectionsEnabled();
    }

    public void captureOpaque(Framebuffer framebuffer) {
        Framebuffer mcFbo = MinecraftClient.getInstance().getFramebuffer();
        this.opaqueDepth.copyFrom(framebuffer == null ? null : framebuffer.getDepthAttachment());
        this.sceneColor.copyFrom(mcFbo == null ? null : mcFbo.getColorAttachment());
        this.opaqueCaptured = (this.opaqueDepth.getView() != null && this.sceneColor.getView() != null);
    }

    public void captureWorldDepth(Framebuffer framebuffer) {
        if (this.opaqueCaptured) {
            this.worldDepth.copyFrom(framebuffer == null ? null : framebuffer.getDepthAttachment());
            this.opaqueCaptured = false;
            this.reflectionReady = (this.worldDepth.getView() != null);
        }
    }

    public static void setSkyParams(float f, float f2) {
        skyBrightness = f;
        skyVisibility = f2;
    }

    public static boolean isOpenGl() {
        return GlStateSnapshot.isOpenGl();
    }

    public void renderPostEffects(GameRenderer gameRenderer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (mc.player != null && mc.world != null && wt != null && wt.isEnabled()) {
            boolean openGl = isOpenGl();
            boolean doReflections = wt.isReflectionsEnabled() && this.reflectionReady && openGl;
            this.reflectionReady = false;
            boolean doBloom = wt.isBloomEnabled();
            boolean doColorGrade = wt.isColorGradeEnabled();
            boolean noSubmersion = gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.NONE;

            if (doReflections || doBloom || doColorGrade) {
                Framebuffer framebuffer = mc.getFramebuffer();
                if (isFramebufferUsable(framebuffer)) {
                    int w = framebuffer.textureWidth;
                    int h = framebuffer.textureHeight;
                    Quaternionf rot = gameRenderer.getCamera().getRotation().conjugate(new Quaternionf());
                    GpuBufferSlice ubo = this.createUniforms(gameRenderer, wt, rot);
                    GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                    GpuSampler nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

                    if (doReflections || doColorGrade) {
                        this.captureTarget = ensureTarget(this.captureTarget, "egg_ambience_post_capture", w, h);
                    } else if (this.captureTarget != null) {
                        deleteTarget(this.captureTarget);
                        this.captureTarget = null;
                    }

                    if (doBloom) {
                        try (FullscreenPass.RenderStateGuard guard = FullscreenPass.guardState()) {
                            this.renderBloom(framebuffer, ubo, linearSampler);
                        }
                    }

                    if (doReflections && noSubmersion) {
                        try (FullscreenPass.RenderStateGuard guard = FullscreenPass.guardState()) {
                            this.renderReflections(framebuffer, ubo, linearSampler, nearestSampler);
                        }
                    }


                    if (doColorGrade) {
                        this.captureScene(framebuffer, w, h, nearestSampler);
                        try (FullscreenPass.RenderStateGuard guard = FullscreenPass.guardState()) {
                            FullscreenPass.drawAmbiencePass(Pipelines.AMBIENCE_COLOR_GRADE, framebuffer, this.captureTarget.getColorAttachmentView(), nearestSampler, ubo);
                        }
                    }
                }
            }
        } else {
            this.releaseIfUnused();
        }
    }

    private void captureScene(Framebuffer framebuffer, int w, int h, GpuSampler sampler) {
        if (GlStateSnapshot.isOpenGl()) {
            RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(framebuffer.getColorAttachment(), this.captureTarget.getColorAttachment(), 0, 0, 0, 0, 0, w, h);
        }
    }

    private void renderReflections(Framebuffer framebuffer, GpuBufferSlice ubo, GpuSampler linear, GpuSampler nearest) {
        FullscreenPass.drawAmbienceReflection(
            Pipelines.AMBIENCE_REFLECTION,
            this.captureTarget,
            this.sceneColor.getView(),
            this.worldDepth.getView(),
            this.opaqueDepth.getView(),
            linear,
            nearest,
            ubo
        );
        FullscreenPass.drawAmbiencePass(Pipelines.AMBIENCE_REFLECTION_COMPOSITE, framebuffer, this.captureTarget.getColorAttachmentView(), linear, ubo);
    }

    private void renderBloom(Framebuffer framebuffer, GpuBufferSlice ubo, GpuSampler linear) {
        int w = framebuffer.textureWidth;
        int h = framebuffer.textureHeight;
        this.resultTarget = ensureTarget(this.resultTarget, "egg_ambience_bloom_result", Math.max(1, w / 2), Math.max(1, h / 2));
        this.ensureMipTargets(BLOOM_MIPS);

        FullscreenPass.drawAmbiencePass(Pipelines.AMBIENCE_BLOOM_EXTRACT, this.resultTarget, framebuffer.getColorAttachmentView(), linear, ubo);
        FullscreenPass.blitUnflipped(Pipelines.AMBIENCE_BLUR_DOWN, this.mipTargets[0], this.resultTarget.getColorAttachmentView(), linear, BLUR_OFFSET);

        for (int i = 1; i < BLOOM_MIPS; i++) {
            FullscreenPass.blitUnflipped(Pipelines.AMBIENCE_BLUR_DOWN, this.mipTargets[i], this.mipTargets[i - 1].getColorAttachmentView(), linear, BLUR_OFFSET);
        }

        for (int i = 2; i >= 0; i--) {
            FullscreenPass.blitUnflipped(Pipelines.AMBIENCE_BLUR_UP_ADD, this.mipTargets[i], this.mipTargets[i + 1].getColorAttachmentView(), linear, BLUR_OFFSET);
        }

        FullscreenPass.blitUnflipped(Pipelines.AMBIENCE_BLUR_UP, this.resultTarget, this.mipTargets[0].getColorAttachmentView(), linear, BLUR_OFFSET);
        FullscreenPass.drawAmbiencePass(Pipelines.AMBIENCE_BLOOM_COMPOSITE, framebuffer, this.resultTarget.getColorAttachmentView(), linear, ubo);
    }

    private GpuBufferSlice createUniforms(GameRenderer gameRenderer, WorldTweaks wt, Quaternionf rot) {
        Vector3f up = rot.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f right = rot.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f forward = rot.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f sunDir = rot.transform(new Vector3f(-MathHelper.sin(sunAngle), MathHelper.cos(sunAngle), 0.0F));
        Vec3d camPos = gameRenderer.getCamera().getCameraPos();

        float blurDist = 99999.0F;
        float waveTime = (float)((System.nanoTime() % 1000000000000L) / 1.0E9);

        return AmbiencePostUniformStorage.write(
            new AmbiencePostUniforms(
                skyBrightness,
                skyVisibility,
                0.05F,
                gameRenderer.getFarPlaneDistance(),
                blurDist,
                blurDist * 1.5F,
                (float) wt.getBloomThreshold(),
                (float) wt.getBloomIntensity(),
                up.x,
                up.y,
                up.z,
                0.55F,
                200.0F,
                ((skyColor >> 16) & 0xFF) / 255.0F,
                ((skyColor >> 8) & 0xFF) / 255.0F,
                (skyColor & 0xFF) / 255.0F,
                ((horizonColor >> 16) & 0xFF) / 255.0F,
                ((horizonColor >> 8) & 0xFF) / 255.0F,
                (horizonColor & 0xFF) / 255.0F,
                sunDir.x,
                sunDir.y,
                sunDir.z,
                right.x,
                right.y,
                right.z,
                forward.x,
                forward.y,
                forward.z,
                (float) camPos.x,
                (float) camPos.z,
                waveTime,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                (float) camPos.x, (float) camPos.y, (float) camPos.z,
                wt.getShaderQualityScale(),
                0,
                0.0F,
                0.0F,
                (float) camPos.x, (float) camPos.z,
                128.0F, 16.0F, 0.5F, 1.0F, 256.0F, 0.0F,
                1.0F, 1.0F, 1.0F, 1.0F, 0.0F,
                (float) wt.getColorGradeSaturation(),
                (float) wt.getColorGradeContrast(),
                (float) wt.getColorGradeBrightness(),
                (float) wt.getColorGradeTemperature()
            )
        );
    }

    private static boolean isFramebufferUsable(Framebuffer framebuffer) {
        return framebuffer != null
            && framebuffer.getColorAttachment() != null
            && framebuffer.getColorAttachment().getFormat() == TextureFormat.RGBA8
            && framebuffer.getColorAttachmentView() != null
            && !framebuffer.getColorAttachmentView().isClosed()
            && framebuffer.getDepthAttachmentView() != null;
    }

    private void ensureMipTargets(int count) {
        int w = this.resultTarget.textureWidth;
        int h = this.resultTarget.textureHeight;
        if (this.mipTargets.length < count) {
            this.mipTargets = Arrays.copyOf(this.mipTargets, count);
        }

        for (int i = 0; i < count; i++) {
            this.mipTargets[i] = ensureTarget(
                this.mipTargets[i], "egg_ambience_post_mip" + i, Math.max(1, w >> (i + 1)), Math.max(1, h >> (i + 1))
            );
        }
    }

    public void reset() {
        this.releaseTargets();
    }

    private void releaseIfUnused() {
        if (this.captureTarget != null || this.resultTarget != null || this.mipTargets.length > 0 || this.opaqueDepth.getView() != null) {
            this.releaseTargets();
        }
    }

    private void releaseTargets() {
        deleteTarget(this.captureTarget);
        this.captureTarget = null;
        deleteTarget(this.resultTarget);
        this.resultTarget = null;
        for (SimpleFramebuffer fb : this.mipTargets) {
            deleteTarget(fb);
        }
        this.mipTargets = new SimpleFramebuffer[0];
        this.opaqueDepth.close();
        this.worldDepth.close();
        this.sceneColor.close();
        this.opaqueCaptured = false;
        this.reflectionReady = false;
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

    private static void deleteTarget(SimpleFramebuffer simpleFramebuffer) {
        if (simpleFramebuffer != null) {
            simpleFramebuffer.delete();
        }
    }

    static final class TextureSnapshot {
        private final String label;
        private final TextureFormat format;
        private GpuTexture texture;
        private GpuTextureView textureView;

        TextureSnapshot(String string, TextureFormat textureFormat) {
            this.label = string;
            this.format = textureFormat;
        }

        GpuTextureView getView() {
            return this.textureView;
        }

        void copyFrom(GpuTexture gpuTexture) {
            if (gpuTexture != null && gpuTexture.getFormat() == this.format) {
                int i = gpuTexture.getWidth(0);
                int i2 = gpuTexture.getHeight(0);
                if (this.texture == null || this.texture.getWidth(0) != i || this.texture.getHeight(0) != i2) {
                    this.close();
                    GpuDevice gpuDevice = RenderSystem.getDevice();
                    this.texture = gpuDevice.createTexture(this.label, 15, this.format, i, i2, 1, 1);
                    this.textureView = gpuDevice.createTextureView(this.texture);
                }

                RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(gpuTexture, this.texture, 0, 0, 0, 0, 0, i, i2);
            }
        }

        void close() {
            if (this.textureView != null) {
                this.textureView.close();
            }

            if (this.texture != null) {
                this.texture.close();
            }

            this.textureView = null;
            this.texture = null;
        }
    }
}
