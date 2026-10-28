package delta.render;

import delta.core.NativeMethodLookup;
import static delta.core.Interface.aM_;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.BaseProcessor;
import delta.core.Interface;

import delta.ui.shader.BlurShader;
import delta.ui.shader.GradientShader;
import delta.ui.shader.NoiseShader;
import delta.ui.shader.RectangleShader;
import delta.ui.shader.TextureShader;
import delta.module.render.Interface_2;
import delta.api.Compile;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Generated;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.gl.Framebuffer;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class Draw2DProcessor extends BaseProcessor implements Interface {
    private float b = 1.0f;
    private final RectangleShader c = new RectangleShader();
    private final TextureShader d = new TextureShader();
    private final GradientShader e = new GradientShader();
    private final BlurShader f = new BlurShader();
    private final NoiseShader g = new NoiseShader();
    private final delta.ui.shader.LiquidGlassShader liquidGlassShader = new delta.ui.shader.LiquidGlassShader();
    private final delta.ui.shader.EggLogoShader eggLogoShader = new delta.ui.shader.EggLogoShader();

    @Override
    @Compile
    public void setup() {
    }

    static {
        NativeMethodLookup.lookup(Draw2DProcessor.class, 28);
    }

    @Generated
    public void a(float scale) {
        this.b = scale;
    }

    @Generated
    public float a() {
        return this.b;
    }

    @Generated
    public RectangleShader b() {
        return this.c;
    }

    @Generated
    public TextureShader c() {
        return this.d;
    }

    @Generated
    public GradientShader d() {
        return this.e;
    }

    @Generated
    public BlurShader e() {
        return this.f;
    }

    @Generated
    public NoiseShader f() {
        return this.g;
    }

    public delta.ui.shader.LiquidGlassShader getLiquidGlassShader() {
        return this.liquidGlassShader;
    }

    public delta.ui.shader.EggLogoShader getEggLogoShader() {
        return this.eggLogoShader;
    }

    @Override
    public void unSetup() {
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        a(matrices, x, y, width, height, b(radius), color);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color) {
        float[] padding = MathUtil.b(0.8f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        g();
        this.c.a();
        this.c.a(width, height);
        this.c.a(radius);
        this.c.a(0.8f);
        this.c.b(0.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        a(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, float outlineWidth, int color) {
        a(matrices, x, y, width, height, b(radius), outlineWidth, color);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, float outlineWidth, int color) {
        float[] padding = MathUtil.b(0.8f);
        float halfOutlineWidth = outlineWidth * 0.5f;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = (x - halfOutlineWidth) - (padding[0] / 2.0f);
        float drawY = (y - halfOutlineWidth) - (padding[1] / 2.0f);
        float drawWidth = width + outlineWidth + padding[0];
        float drawHeight = height + outlineWidth + padding[1];
        g();
        this.c.a();
        this.c.a(width + outlineWidth, height + outlineWidth);
        this.c.a(new Vector4f(radius.x + halfOutlineWidth, radius.y + halfOutlineWidth, radius.z + halfOutlineWidth, radius.w + halfOutlineWidth));
        this.c.a(0.8f);
        this.c.b(outlineWidth);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        a(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void a(MatrixStack matrices, Identifier texture, float x, float y, float width, float height, float radius, int color) {
        a(matrices, x, y, width, height, radius, color, 0.0f, 0.0f, 1.0f, 1.0f, RenderStateHelper.getTextureId(texture));
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float u, float v, float textureWidth, float textureHeight, int textureId) {
        a(matrices, x, y, width, height, b(radius), color, u, v, textureWidth, textureHeight, textureId);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color, float u, float v, float textureWidth, float textureHeight, int textureId) {
        float[] padding = MathUtil.b(0.8f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        g();
        RenderStateHelper.setShaderTexture(0, textureId);
        this.d.a();
        this.d.a(width, height);
        this.d.a(radius);
        this.d.a(0.8f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        a(buffer, matrix, drawX, drawY, drawWidth, drawHeight, u, v, textureWidth, textureHeight, color);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void a(MatrixStack matrices, Identifier skin, LivingEntity target, float x, float y, float width, float height, float radius, float alpha) {
        if (skin == null) {
            return;
        }
        int color = ColorUtil.a(255, 255, 255, (int) (alpha * 255.0f));
        int textureId = RenderStateHelper.getTextureId(skin);
        a(matrices, x, y, width, height, radius, color, 0.125f, 0.125f, 0.125f, 0.125f, textureId);
        a(matrices, x, y, width, height, radius, color, 0.625f, 0.125f, 0.125f, 0.125f, textureId);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        a(matrices, x, y, width, height, b(radius), topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        float[] padding = MathUtil.b(1.0f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float[] normalizedTopLeft = ColorUtil.a(topLeftColor);
        float[] normalizedBottomLeft = ColorUtil.a(bottomLeftColor);
        float[] normalizedBottomRight = ColorUtil.a(bottomRightColor);
        float[] normalizedTopRight = ColorUtil.a(topRightColor);
        float drawX = x - (padding[0] / 2.0f);
        float drawY = y - (padding[1] / 2.0f);
        float drawWidth = width + padding[0];
        float drawHeight = height + padding[1];
        g();
        this.e.a();
        this.e.a(width, height);
        this.e.a(radius);
        this.e.a(1.0f);
        this.e.a(normalizedTopLeft[0], normalizedTopLeft[1], normalizedTopLeft[2], normalizedTopLeft[3]);
        this.e.b(normalizedBottomLeft[0], normalizedBottomLeft[1], normalizedBottomLeft[2], normalizedBottomLeft[3]);
        this.e.d(normalizedBottomRight[0], normalizedBottomRight[1], normalizedBottomRight[2], normalizedBottomRight[3]);
        this.e.c(normalizedTopRight[0], normalizedTopRight[1], normalizedTopRight[2], normalizedTopRight[3]);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).color(topLeftColor);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).color(bottomLeftColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).color(bottomRightColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).color(topRightColor);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void b(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        a(matrices, x, y, width, height, radius, color, 0.8f);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float mix) {
        a(matrices, x, y, width, height, b(radius), color, color, color, color, mix);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor, float mix) {
        if (this.f.e().isEmpty()) {
            return;
        }
        Framebuffer framebuffer = (Framebuffer) this.f.e().getFirst();
        float uLeft = 0.0f;
        float uRight = 0.0f;
        float vTop = 0.0f;
        float vBottom = 0.0f;
        if (mix != 1.0f) {
            float scale = framebuffer.textureWidth / aM_.getWindow().getScaledWidth();
            uLeft = (x * scale) / framebuffer.textureWidth;
            uRight = ((x + width) * scale) / framebuffer.textureWidth;
            vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
            vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);
        }
        float[] normalizedTopLeft = ColorUtil.a(topLeftColor);
        float[] normalizedBottomLeft = ColorUtil.a(bottomLeftColor);
        float[] normalizedBottomRight = ColorUtil.a(bottomRightColor);
        float[] normalizedTopRight = ColorUtil.a(topRightColor);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (1.2f * 0.5f);
        float drawY = y - (1.2f * 0.5f);
        float drawWidth = width + 1.2f;
        float drawHeight = height + 1.2f;
        g();
        RenderStateHelper.setShaderTexture(0, framebuffer.getColorAttachment());
        this.f.a();
        this.f.a(width, height);
        this.f.a(radius);
        this.f.a(0.8f);
        this.f.b(mix);
        this.f.c((normalizedTopLeft[3] + normalizedBottomLeft[3] + normalizedBottomRight[3] + normalizedTopRight[3]) * 0.25f);
        this.f.a(normalizedTopLeft[0], normalizedTopLeft[1], normalizedTopLeft[2], normalizedTopLeft[3]);
        this.f.b(normalizedBottomLeft[0], normalizedBottomLeft[1], normalizedBottomLeft[2], normalizedBottomLeft[3]);
        this.f.d(normalizedBottomRight[0], normalizedBottomRight[1], normalizedBottomRight[2], normalizedBottomRight[3]);
        this.f.c(normalizedTopRight[0], normalizedTopRight[1], normalizedTopRight[2], normalizedTopRight[3]);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).texture(uLeft, vTop).color(topLeftColor);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).texture(uLeft, vBottom).color(bottomLeftColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).texture(uRight, vBottom).color(bottomRightColor);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).texture(uRight, vTop).color(topRightColor);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float alpha, int glowColor, float glowRadius) {
        a(matrices, x, y, width, height, b(radius), color, alpha, glowColor, glowRadius);
    }

    public void a(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color, float alpha, int glowColor, float glowRadius) {
        float clampedGlowRadius = Math.max(glowRadius, 0.0f);
        if (clampedGlowRadius > 0.001f) {
            float innerWidth = width - ((-1.1f) * 2.0f);
            float innerHeight = height - ((-1.1f) * 2.0f);
            Vector4f innerRadius = new Vector4f(Math.max(0.0f, radius.x - (-1.1f)), Math.max(0.0f, radius.y - (-1.1f)), Math.max(0.0f, radius.z - (-1.1f)), Math.max(0.0f, radius.w - (-1.1f)));
            float[] normalizedGlowColor = ColorUtil.a(glowColor);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            a(matrix, (x - 1.1f) - clampedGlowRadius, (y - 1.1f) - clampedGlowRadius, innerWidth + (clampedGlowRadius * 2.0f), innerHeight + (clampedGlowRadius * 2.0f), innerWidth, innerHeight, innerRadius, alpha, 0.8f, clampedGlowRadius, normalizedGlowColor, 0.0f, 0.0f, 1.0f, 1.0f, null);
        }
        if (color != 0 && ((color >> 24) & 0xFF) != 0) {
            a(matrices, x, y, width, height, radius, color);
        }
    }

    public void b(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float alpha) {
        // Always draw the rich base card background first, guaranteeing HUD cards are solid & visible
        a(matrices, x, y, width, height, radius, color);

        if (this.f.e().isEmpty()) {
            return;
        }

        Interface_2 iface = null;
        try {
            iface = Interface_2.getInstance();
        } catch (Exception ignored) {}

        boolean isLiquid = iface != null && iface.isLiquidGlass();

        if (isLiquid) {
            float lgBlur = iface.getLgBlur() / 100.0f;
            float lgRefr = iface.getLgRefraction() / 100.0f;
            float lgGloss = iface.getLgGloss() / 100.0f;
            float lgDisp = iface.getLgDispersion() / 100.0f;
            float lgOpacity = iface.getLgOpacity();

            float distortStrength = 0.02f + 0.08f * lgRefr;
            float fresnelPower = 40.0f + 60.0f * (1.0f - lgGloss * 0.5f);
            float baseAlpha = Math.max(0.18f, lgOpacity);

            // Overlay specular reflection & glass sheen on top of the solid card
            drawLiquidGlass(matrices, x, y, width, height, b(radius), ColorUtil.a(255, 255, 255, (int) (100 * alpha)), alpha * lgBlur, fresnelPower, -1, baseAlpha * 0.35f, true, 0.2f, distortStrength, 2.0f, false);
        } else {
            float padding = 0.8f * 1.5f;
            Framebuffer framebuffer = (Framebuffer) this.f.e().getFirst();
            float scale = framebuffer.textureWidth / aM_.getWindow().getScaledWidth();
            float uLeft = (x * scale) / framebuffer.textureWidth;
            float uRight = ((x + width) * scale) / framebuffer.textureWidth;
            float vTop = 1.0f - ((y * scale) / framebuffer.textureHeight);
            float vBottom = 1.0f - (((y + height) * scale) / framebuffer.textureHeight);
            a(matrices.peek().getPositionMatrix(), x - (padding * 0.5f), y - (padding * 0.5f), width + padding, height + padding, width, height, b(radius), alpha * 0.35f, 0.8f, 0.0f, null, uLeft, vTop, uRight, vBottom, framebuffer);
        }
    }

    public void drawLiquidGlass(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, float cornerSmoothness, boolean clean) {
        if (this.f.e().isEmpty()) {
            int origAlpha = (color >> 24) & 255;
            int glassTint = ColorUtil.a(color, (int) (origAlpha * Math.min(0.65f, baseAlpha) * globalAlpha));
            a(matrices, x, y, width, height, radius, glassTint);
            return;
        }
        Framebuffer fb = (Framebuffer) this.f.e().getFirst();
        if (fb == null || fb == aM_.getFramebuffer()) {
            int origAlpha = (color >> 24) & 255;
            int glassTint = ColorUtil.a(color, (int) (origAlpha * Math.min(0.65f, baseAlpha) * globalAlpha));
            a(matrices, x, y, width, height, radius, glassTint);
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        g();
        RenderStateHelper.disableCull();
        RenderStateHelper.setShaderTexture(0, fb.getColorAttachment());

        this.liquidGlassShader.a();
        this.liquidGlassShader.setSize(width, height);
        this.liquidGlassShader.setRadius(radius);
        this.liquidGlassShader.setSmoothness(0.5f);
        this.liquidGlassShader.setCornerSmoothness(cornerSmoothness);
        this.liquidGlassShader.setGlobalAlpha(globalAlpha);
        this.liquidGlassShader.setFresnelPower(fresnelPower);
        float[] fc = ColorUtil.a(fresnelColor);
        this.liquidGlassShader.setFresnelColor(fc[0], fc[1], fc[2]);
        this.liquidGlassShader.setFresnelAlpha(fc[3]);
        this.liquidGlassShader.setBaseAlpha(baseAlpha);
        this.liquidGlassShader.setFresnelInvert(fresnelInvert);
        this.liquidGlassShader.setFresnelMix(fresnelMix);
        this.liquidGlassShader.setDistortStrength(distortStrength);

        int screenWidth = aM_.getWindow().getScaledWidth();
        int screenHeight = aM_.getWindow().getScaledHeight();
        float u = x / (float) screenWidth;
        float v = ((float) screenHeight - y - height) / (float) screenHeight;
        float texWidth = width / (float) screenWidth;
        float texHeight = height / (float) screenHeight;

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, 0.0f).texture(u, v + texHeight).color(color);
        builder.vertex(matrix, x, y + height, 0.0f).texture(u, v).color(color);
        builder.vertex(matrix, x + width, y + height, 0.0f).texture(u + texWidth, v).color(color);
        builder.vertex(matrix, x + width, y, 0.0f).texture(u + texWidth, v + texHeight).color(color);
        ImmediateBufferRenderer.draw(builder.end());

        RenderStateHelper.setShaderTexture(0, 0);
        RenderStateHelper.enableCull();
        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
    }

    public void drawLiquidGlass(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, float cornerSmoothness, boolean clean) {
        drawLiquidGlass(matrices, x, y, width, height, b(radius), color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, cornerSmoothness, clean);
    }

    public void drawLiquidGlass(MatrixStack matrices, float x, float y, float width, float height, float radius, int color, float globalAlpha, float distortStrength) {
        drawLiquidGlass(matrices, x, y, width, height, b(radius), color, globalAlpha, 50.0f, -1, 0.35f, true, 0.0f, distortStrength, 2.0f, false);
    }

    public void drawMenuShader(MatrixStack matrices, float width, float height, float fbWidth, float fbHeight, float time, float burstTime, float mouseXNorm, float mouseYNorm, boolean introPlayed, float alpha, boolean lightTheme) {
        if (alpha <= 0.001f) return;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        g();
        RenderStateHelper.disableCull();
        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        try {
            Identifier logoId = Identifier.of("delta", "textures/menu_logo_sdf.png");
            int glTexId = RenderStateHelper.getTextureId(logoId);
            if (glTexId > 0) {
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, glTexId);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                RenderStateHelper.setShaderTexture(0, glTexId);
            } else {
                RenderStateHelper.setShaderTexture(0, 0);
            }
        } catch (Throwable t) {
            RenderStateHelper.setShaderTexture(0, 0);
        }

        this.eggLogoShader.a();
        this.eggLogoShader.setResolution(fbWidth, fbHeight);
        this.eggLogoShader.setTime(time);
        this.eggLogoShader.setBurstTime(burstTime);
        this.eggLogoShader.setMouse(mouseXNorm, mouseYNorm);
        this.eggLogoShader.setIntroPlayed(introPlayed);
        this.eggLogoShader.setAlpha(alpha);
        this.eggLogoShader.setThemeMode(lightTheme ? 1.0f : 0.0f);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int color = ColorUtil.a(255, 255, 255, (int) (255 * alpha));
        builder.vertex(matrix, 0.0f, 0.0f, 0.0f).texture(0.0f, 0.0f).color(color);
        builder.vertex(matrix, 0.0f, height, 0.0f).texture(0.0f, 1.0f).color(color);
        builder.vertex(matrix, width, height, 0.0f).texture(1.0f, 1.0f).color(color);
        builder.vertex(matrix, width, 0.0f, 0.0f).texture(1.0f, 0.0f).color(color);
        ImmediateBufferRenderer.draw(builder.end());

        RenderStateHelper.enableCull();
        RenderStateHelper.disableBlend();
    }

    private void a(Matrix4f matrix, float drawX, float drawY, float drawWidth, float drawHeight, float width, float height, Vector4f radius, float alpha, float smoothness, float glowRadius, float[] glowColor, float uLeft, float vTop, float uRight, float vBottom, Framebuffer framebuffer) {
        g();
        if (framebuffer != null && framebuffer != aM_.getFramebuffer()) {
            RenderStateHelper.setShaderTexture(0, framebuffer.getColorAttachment());
        } else {
            RenderStateHelper.setShaderTexture(0, 0);
        }
        this.f.a();
        this.f.a(width, height);
        this.f.a(radius);
        this.f.c(alpha);
        this.f.d(glowRadius);
        if (glowColor != null) {
            this.f.e(glowColor[0], glowColor[1], glowColor[2], glowColor[3]);
        }
        this.f.a(smoothness);
        this.f.b(0.0f);
        this.f.a(1.0f, 1.0f, 1.0f, 1.0f);
        this.f.b(1.0f, 1.0f, 1.0f, 1.0f);
        this.f.d(1.0f, 1.0f, 1.0f, 1.0f);
        this.f.c(1.0f, 1.0f, 1.0f, 1.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, drawX, drawY, 0.0f).texture(uLeft, vTop).color(-1);
        buffer.vertex(matrix, drawX, drawY + drawHeight, 0.0f).texture(uLeft, vBottom).color(-1);
        buffer.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0.0f).texture(uRight, vBottom).color(-1);
        buffer.vertex(matrix, drawX + drawWidth, drawY, 0.0f).texture(uRight, vTop).color(-1);
        ImmediateBufferRenderer.draw(buffer.end());
        h();
    }

    public void a(DrawContext context, float x, float y, float width, float height, int color) {
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), color);
    }

    private void g() {
        RenderStateHelper.disableDepthTest();
        RenderStateHelper.depthMask(false);
        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();
    }

    private void h() {
        RenderStateHelper.setShaderTexture(0, 0);
        RenderStateHelper.disableCull();
        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableDepthTest();
        RenderStateHelper.depthMask(false);
    }

    private Vector4f b(float radius) {
        return new Vector4f(radius, radius, radius, radius);
    }

    private void a(BufferBuilder buffer, Matrix4f matrix, float x, float y, float width, float height, int color) {
        buffer.vertex(matrix, x, y, 0.0f).color(color);
        buffer.vertex(matrix, x, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y, 0.0f).color(color);
    }

    private void a(BufferBuilder buffer, Matrix4f matrix, float x, float y, float width, float height, float u, float v, float textureWidth, float textureHeight, int color) {
        buffer.vertex(matrix, x, y, 0.0f).texture(u, v).color(color);
        buffer.vertex(matrix, x, y + height, 0.0f).texture(u, v + textureHeight).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0f).texture(u + textureWidth, v + textureHeight).color(color);
        buffer.vertex(matrix, x + width, y, 0.0f).texture(u + textureWidth, v).color(color);
    }

    public static int getTextureId(com.mojang.blaze3d.textures.GpuTexture texture) {
        return RenderStateHelper.getTextureId(texture);
    }

    public static int getTextureId(Identifier texture) {
        return RenderStateHelper.getTextureId(texture);
    }

    public static int getTextureId(net.minecraft.client.texture.AbstractTexture texture) {
        return RenderStateHelper.getTextureId(texture);
    }
}
