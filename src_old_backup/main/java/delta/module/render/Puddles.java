package delta.module.render;

import static delta.core.Interface.aM_;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.render.RenderStateHelper;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.render.CustomUniform;
import delta.render.ImmediateBufferRenderer;
import delta.render.SadnesMotionBlurCapture;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.SliderSetting;
import delta.config.ThemeInfo;
import delta.ui.shader.Shader;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@ModuleRegister(a = "Puddles", b = "Реалистичные 3D лужи на земле с отражениями и рябью дождя", c = Category.Render)
public class Puddles extends Module implements Interface {

    public static final Shader PUDDLES_SHADER = new Shader(
            Identifier.of("delta", "core/puddles/puddles"),
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

    private final SliderSetting puddleSize = new SliderSetting("Размер луж", 2.0f, 0.5f, 10.0f, 0.5f);
    private final SliderSetting puddleDensity = new SliderSetting("Количество", 0.45f, 0.1f, 0.9f, 0.05f);
    private final BooleanSetting useRain = new BooleanSetting("Капли дождя", true);
    private final SliderSetting dropFrequency = (SliderSetting) new SliderSetting("Частота капель", 1.5f, 0.2f, 5.0f, 0.1f).a(() -> Boolean.valueOf(this.useRain.c().booleanValue()));
    private final BooleanSetting useWaves = new BooleanSetting("Волны", true);
    private final BooleanSetting waveTheme = (BooleanSetting) new BooleanSetting("Цвет волн от темы", false).a(() -> Boolean.valueOf(this.useWaves.c().booleanValue()));
    private final ColorSetting waveColor = (ColorSetting) new ColorSetting("Цвет волн", 0xFFFFFFFF).a(() -> Boolean.valueOf(this.useWaves.c().booleanValue() && !this.waveTheme.c().booleanValue()));

    private SimpleFramebuffer captureFramebuffer;
    private long startMillis = -1;

    public Puddles() {
        a(this.puddleSize, this.puddleDensity, this.useRain, this.dropFrequency, this.useWaves, this.waveTheme, this.waveColor);
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!m() || !event.c()) {
            return;
        }

        if (aM_.player == null || aM_.world == null || aM_.gameRenderer == null) {
            return;
        }

        Framebuffer mainFb = aM_.getFramebuffer();
        if (mainFb == null) {
            return;
        }

        int width = mainFb.textureWidth;
        int height = mainFb.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        if (this.startMillis < 0) {
            this.startMillis = System.currentTimeMillis();
        }
        float timeSeconds = (System.currentTimeMillis() - this.startMillis) / 1000.0f;

        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPosVec = camera.getCameraPos();

        float fovDeg = aM_.options.getFov().getValue().floatValue();
        float fovRad = (float) Math.toRadians(fovDeg);
        float aspect = (float) width / (float) height;
        float nearPlane = 0.05f;
        float farPlane = aM_.gameRenderer.getFarPlaneDistance();

        Matrix4f projMatrix = new Matrix4f().setPerspective(fovRad, aspect, nearPlane, farPlane);
        Matrix4f viewMatrix = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));

        Matrix4f invProjMatrix = new Matrix4f(projMatrix).invert();
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();

        ensureFramebuffer(width, height);

        RenderStateHelper.disableDepthTest();
        RenderStateHelper.depthMask(false);
        RenderStateHelper.disableCull();
        RenderStateHelper.disableBlend();

        // Pass 1: Blit mainFb into captureFramebuffer
        RenderStateHelper.bindFramebuffer(this.captureFramebuffer);
        RenderStateHelper.setShaderTexture(0, mainFb.getColorAttachment());
        SadnesMotionBlurCapture.BLIT_SHADER.a();
        drawFullscreenQuad();

        // Pass 2: Raymarch SSR Puddles shader
        RenderStateHelper.bindFramebuffer(mainFb);
        RenderStateHelper.setShaderTexture(0, this.captureFramebuffer.getColorAttachment());
        RenderStateHelper.setShaderTexture(1, mainFb.getDepthAttachment());

        PUDDLES_SHADER.a();
        CustomUniform uRes = PUDDLES_SHADER.getUniform("Resolution");
        if (uRes != null) uRes.set((float) width, (float) height);

        CustomUniform uTime = PUDDLES_SHADER.getUniform("Time");
        if (uTime != null) uTime.set(timeSeconds);

        CustomUniform uScale = PUDDLES_SHADER.getUniform("Scale");
        if (uScale != null) uScale.set(this.puddleSize.c().floatValue());

        CustomUniform uDensity = PUDDLES_SHADER.getUniform("Density");
        if (uDensity != null) uDensity.set(this.puddleDensity.c().floatValue());

        CustomUniform uUseRain = PUDDLES_SHADER.getUniform("UseRain");
        if (uUseRain != null) uUseRain.set(this.useRain.c().booleanValue() ? 1.0f : 0.0f);

        CustomUniform uDropFreq = PUDDLES_SHADER.getUniform("DropFrequency");
        if (uDropFreq != null) uDropFreq.set(this.dropFrequency.c().floatValue());

        CustomUniform uUseWaves = PUDDLES_SHADER.getUniform("UseWaves");
        if (uUseWaves != null) uUseWaves.set(this.useWaves.c().booleanValue() ? 1.0f : 0.0f);

        CustomUniform uUseWaveTheme = PUDDLES_SHADER.getUniform("UseWaveTheme");
        if (uUseWaveTheme != null) uUseWaveTheme.set(this.waveTheme.c().booleanValue() ? 1.0f : 0.0f);

        CustomUniform uWaveColor = PUDDLES_SHADER.getUniform("WaveColor");
        if (uWaveColor != null) {
            int col = this.waveTheme.c().booleanValue() ? Delta.h().d().o().a(ThemeInfo.PRIMARY).a() : this.waveColor.c().intValue();
            float r = ((col >> 16) & 0xFF) / 255.0f;
            float g = ((col >> 8) & 0xFF) / 255.0f;
            float b = (col & 0xFF) / 255.0f;
            uWaveColor.set(r, g, b);
        }

        CustomUniform uProj = PUDDLES_SHADER.getUniform("ProjMat");
        if (uProj != null) uProj.set(projMatrix);

        CustomUniform uView = PUDDLES_SHADER.getUniform("ViewMat");
        if (uView != null) uView.set(viewMatrix);

        CustomUniform uInvProj = PUDDLES_SHADER.getUniform("InvProjMat");
        if (uInvProj != null) uInvProj.set(invProjMatrix);

        CustomUniform uInvView = PUDDLES_SHADER.getUniform("InvViewMat");
        if (uInvView != null) uInvView.set(invViewMatrix);

        CustomUniform uCamPos = PUDDLES_SHADER.getUniform("CameraPos");
        if (uCamPos != null) uCamPos.set((float) camPosVec.x, (float) camPosVec.y, (float) camPosVec.z);

        drawFullscreenQuad();

        RenderStateHelper.enableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
    }

    private void ensureFramebuffer(int width, int height) {
        if (this.captureFramebuffer == null
                || this.captureFramebuffer.textureWidth != width
                || this.captureFramebuffer.textureHeight != height) {
            if (this.captureFramebuffer != null) {
                this.captureFramebuffer.delete();
            }
            this.captureFramebuffer = new SimpleFramebuffer("delta_puddles", width, height, false);
        }
    }

    private void drawFullscreenQuad() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(-1.0f, -1.0f, 0.0f);
        buffer.vertex(1.0f, -1.0f, 0.0f);
        buffer.vertex(1.0f, 1.0f, 0.0f);
        buffer.vertex(-1.0f, 1.0f, 0.0f);
        ImmediateBufferRenderer.draw(buffer.end());
    }

    @Override
    public void c() {
        super.c();
        if (this.captureFramebuffer != null) {
            this.captureFramebuffer.delete();
            this.captureFramebuffer = null;
        }
        this.startMillis = -1;
    }
}
