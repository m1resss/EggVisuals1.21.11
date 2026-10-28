package delta.render;

import static delta.core.Interface.aM_;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.ui.shader.Shader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class SadnesMotionBlurCapture {
    public static final SadnesMotionBlurCapture INSTANCE = new SadnesMotionBlurCapture();

    public static final Shader BLIT_SHADER = new Shader(
            Identifier.of("delta", "core/motion_blur/blit"),
            VertexFormats.POSITION
    ) {
        @Override
        protected void b() {}
        @Override
        public void uploadDefaultMatrices() {
            CustomUniform mv = a("ModelViewMat");
            if (mv != null) mv.set(new Matrix4f().identity());
            CustomUniform proj = a("ProjMat");
            if (proj != null) proj.set(new Matrix4f().identity());
        }
    };

    public static final Shader MOTION_BLUR_SHADER = new Shader(
            Identifier.of("delta", "core/motion_blur/motion_blur"),
            VertexFormats.POSITION
    ) {
        @Override
        protected void b() {}
        @Override
        public void uploadDefaultMatrices() {
            CustomUniform mv = a("ModelViewMat");
            if (mv != null) mv.set(new Matrix4f().identity());
            CustomUniform proj = a("ProjMat");
            if (proj != null) proj.set(new Matrix4f().identity());
        }
    };

    private SimpleFramebuffer captureFramebuffer;
    private long lastTimeNanos = 0L;
    private float lastYaw;
    private float lastPitch;
    private float smoothedYawVel;
    private float smoothedPitchVel;
    private boolean hasPreviousFrame;

    private SadnesMotionBlurCapture() {
    }

    public void process(float strength) {
        MinecraftClient mc = aM_;
        if (mc == null || mc.player == null || mc.world == null) {
            this.hasPreviousFrame = false;
            return;
        }

        Framebuffer mainFb = mc.getFramebuffer();
        if (mainFb == null) {
            return;
        }

        int width = mainFb.textureWidth;
        int height = mainFb.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        float currentYaw = camera.getYaw();
        float currentPitch = camera.getPitch();
        long currentTimeNanos = System.nanoTime();

        if (!this.hasPreviousFrame || this.lastTimeNanos <= 0L) {
            this.lastTimeNanos = currentTimeNanos;
            this.lastYaw = currentYaw;
            this.lastPitch = currentPitch;
            this.smoothedYawVel = 0.0f;
            this.smoothedPitchVel = 0.0f;
            this.hasPreviousFrame = true;
            return;
        }

        float dt = (currentTimeNanos - this.lastTimeNanos) / 1_000_000_000.0f;
        this.lastTimeNanos = currentTimeNanos;

        if (dt <= 0.0f || dt > 0.2f) {
            dt = 0.0166667f;
        }

        float deltaYaw = MathHelper.wrapDegrees(currentYaw - this.lastYaw);
        float deltaPitch = currentPitch - this.lastPitch;

        this.lastYaw = currentYaw;
        this.lastPitch = currentPitch;

        float targetYawVel = deltaYaw / dt;
        float targetPitchVel = deltaPitch / dt;

        float smoothFactor = 1.0f - (float) Math.exp(-dt * 18.0f);
        this.smoothedYawVel = MathHelper.lerp(smoothFactor, this.smoothedYawVel, targetYawVel);
        this.smoothedPitchVel = MathHelper.lerp(smoothFactor, this.smoothedPitchVel, targetPitchVel);

        float velScale = 0.00035f * strength;
        float velX = -this.smoothedYawVel * velScale;
        float velY = -this.smoothedPitchVel * velScale;

        float maxBlur = 0.05f;
        velX = MathHelper.clamp(velX, -maxBlur, maxBlur);
        velY = MathHelper.clamp(velY, -maxBlur, maxBlur);

        if (Math.abs(velX) < 1.0e-5f && Math.abs(velY) < 1.0e-5f) {
            return;
        }

        ensureFramebuffer(width, height);

        RenderStateHelper.disableDepthTest();
        RenderStateHelper.depthMask(false);
        RenderStateHelper.disableCull();
        RenderStateHelper.disableBlend();

        // Pass 1: Blit mainFb into captureFramebuffer
        RenderStateHelper.bindFramebuffer(this.captureFramebuffer);
        RenderStateHelper.setShaderTexture(0, mainFb.getColorAttachment());
        BLIT_SHADER.a();
        drawFullscreenQuad();

        // Pass 2: Render motion blur shader from captureFramebuffer back to mainFb
        RenderStateHelper.bindFramebuffer(mainFb);
        RenderStateHelper.setShaderTexture(0, this.captureFramebuffer.getColorAttachment());
        MOTION_BLUR_SHADER.a();
        CustomUniform uVelocity = MOTION_BLUR_SHADER.getUniform("uVelocity");
        if (uVelocity != null) {
            uVelocity.set(velX, velY);
        }
        drawFullscreenQuad();

        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableCull();
        RenderStateHelper.enableBlend();

        RenderStateHelper.bindFramebuffer(mainFb);
    }

    private void drawFullscreenQuad() {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        builder.vertex(-1.0f, -1.0f, 0.0f);
        builder.vertex(-1.0f, 1.0f, 0.0f);
        builder.vertex(1.0f, 1.0f, 0.0f);
        builder.vertex(1.0f, -1.0f, 0.0f);
        ImmediateBufferRenderer.draw(builder.end());
    }

    private void ensureFramebuffer(int width, int height) {
        if (this.captureFramebuffer == null) {
            this.captureFramebuffer = new SimpleFramebuffer("delta_motion_blur", width, height, false);
        } else if (this.captureFramebuffer.textureWidth != width || this.captureFramebuffer.textureHeight != height) {
            this.captureFramebuffer.resize(width, height);
        }
    }

    public void reset() {
        if (this.captureFramebuffer != null) {
            this.captureFramebuffer.delete();
            this.captureFramebuffer = null;
        }
        this.lastTimeNanos = 0L;
        this.hasPreviousFrame = false;
        this.smoothedYawVel = 0.0f;
        this.smoothedPitchVel = 0.0f;
    }
}
