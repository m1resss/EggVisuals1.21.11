package egg.client.render.msdf;

import egg.client.render.msdf.pipeline.BlurPipeline;
import egg.client.render.msdf.pipeline.OutlinePipeline;
import egg.client.render.msdf.pipeline.RectPipeline;
import egg.client.render.msdf.pipeline.TexturePipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

public class Draw2DProcessor {
    public static final Draw2DProcessor INSTANCE = new Draw2DProcessor();

    private float b = 1.0f;

    private final RectPipeline rectPipeline = new RectPipeline();
    private final OutlinePipeline outlinePipeline = new OutlinePipeline();
    private final TexturePipeline texturePipeline = new TexturePipeline();

    private final BlurPipeline blurPipeline = new BlurPipeline();
    private final TexturePipeline panelBlurPipeline = new TexturePipeline(-1);

    public void setScale(float scale) {
        this.b = scale;
    }

    public float getScale() {
        return this.b;
    }

    public RectPipeline getRectPipeline() {
        return this.rectPipeline;
    }

    public OutlinePipeline getOutlinePipeline() {
        return this.outlinePipeline;
    }

    public TexturePipeline getTexturePipeline() {
        return this.texturePipeline;
    }

    public BlurPipeline getBlurPipeline() {
        return this.blurPipeline;
    }

    public void close() {
        this.rectPipeline.close();
        this.outlinePipeline.close();
        this.texturePipeline.close();
        this.blurPipeline.close();
        this.panelBlurPipeline.close();
    }

    public void captureBlur() {
        this.blurPipeline.capture();
    }

    public static float[] transformRect(Matrix3x2f mat, float x, float y, float width, float height, float radius) {
        if (mat == null) {
            return new float[]{x, y, width, height, radius, 1.0f};
        }
        float p0x = mat.m00 * x + mat.m10 * y + mat.m20;
        float p0y = mat.m01 * x + mat.m11 * y + mat.m21;
        float p1x = mat.m00 * (x + width) + mat.m10 * (y + height) + mat.m20;
        float p1y = mat.m01 * (x + width) + mat.m11 * (y + height) + mat.m21;
        float tx = Math.min(p0x, p1x);
        float ty = Math.min(p0y, p1y);
        float tw = Math.abs(p1x - p0x);
        float th = Math.abs(p1y - p0y);
        float scale = width > 0 ? tw / width : 1.0f;
        return new float[]{tx, ty, tw, th, radius * scale, scale};
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color) {
        drawRect(matrices, x, y, width, height, radius, color);
    }

    public void drawRect(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{color}, new float[]{t[4], t[4], t[4], t[4]});
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, Vector4f radius, int color) {
        float[] t = transformRect(matrices, x, y, width, height, radius.x);
        float s = t[5];
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{color}, new float[]{radius.x * s, radius.y * s, radius.z * s, radius.w * s});
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, float outlineWidth, int color) {
        drawOutline(matrices, x, y, width, height, radius, outlineWidth, color);
    }

    public void drawOutline(Matrix3x2f matrices, float x, float y, float width, float height, float radius, float outlineWidth, int color) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        float s = t[5];
        this.outlinePipeline.drawOutline(t[0], t[1], t[2], t[3], new int[]{color}, new float[]{outlineWidth * s, outlineWidth * s, outlineWidth * s, outlineWidth * s}, new float[]{t[4], t[4], t[4], t[4]}, 0.5f);
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, Vector4f radius, float outlineWidth, int color) {
        float[] t = transformRect(matrices, x, y, width, height, radius.x);
        float s = t[5];
        this.outlinePipeline.drawOutline(t[0], t[1], t[2], t[3], new int[]{color}, new float[]{outlineWidth * s, outlineWidth * s, outlineWidth * s, outlineWidth * s}, new float[]{radius.x * s, radius.y * s, radius.z * s, radius.w * s}, 0.5f);
    }

    public void a(Matrix3x2f matrices, Identifier texture, float x, float y, float width, float height, float radius, int color) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        this.texturePipeline.drawTexture(texture, t[0], t[1], t[2], t[3], 0.0f, 0.0f, 1.0f, 1.0f, new int[]{color}, new float[]{t[4], t[4], t[4], t[4]}, 0.5f);
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color, float u, float v, float textureWidth, float textureHeight, int textureId) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{color}, new float[]{t[4], t[4], t[4], t[4]});
    }

    public void a(Matrix3x2f matrices, Identifier skin, LivingEntity target, float x, float y, float width, float height, float radius, float alpha) {
        if (skin == null) return;
        int r = 255, g = 255, b = 255;
        if (target != null && target.hurtTime > 0) {
            float factor = Math.min(1.0f, target.hurtTime / 10.0f);
            g = (int) (255 * (1.0f - factor * 0.75f));
            b = (int) (255 * (1.0f - factor * 0.75f));
        }
        int color = ColorUtil.a(r, g, b, (int) (alpha * 255.0f));
        float[] t = transformRect(matrices, x, y, width, height, radius);
        this.texturePipeline.drawTexture(skin, t[0], t[1], t[2], t[3], 0.125f, 0.125f, 0.25f, 0.25f, new int[]{color}, new float[]{t[4], t[4], t[4], t[4]}, 0.5f);
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        drawGradient(matrices, x, y, width, height, radius, topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
    }

    public void drawGradient(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{topLeftColor, topRightColor, bottomRightColor, bottomLeftColor}, new float[]{t[4], t[4], t[4], t[4]});
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, Vector4f radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        float[] t = transformRect(matrices, x, y, width, height, radius.x);
        float s = t[5];
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{topLeftColor, topRightColor, bottomRightColor, bottomLeftColor}, new float[]{radius.x * s, radius.y * s, radius.z * s, radius.w * s});
    }

    public void b(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color) {
        a(matrices, x, y, width, height, radius, color, 0.8f);
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color, float mix) {
        a(matrices, x, y, width, height, radius, color);
    }

    public void b(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color, float alpha) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        float s = t[5];
        drawPanelBlur(t, alpha);
        int finalColor = ColorUtil.a(color, (ColorUtil.b(color)[3] / 255.0f) * alpha);
        this.rectPipeline.drawGlow(t[0], t[1], t[2], t[3], ColorUtil.a(0, 0, 0, (int) (30.0f * alpha)), new float[]{t[4], t[4], t[4], t[4]}, 2.5f * s, 0.6f);
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{finalColor}, new float[]{t[4], t[4], t[4], t[4]});
    }

    public void a(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int color, float alpha, int glowColor, float glowRadius) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        float s = t[5];
        drawPanelBlur(t, alpha);
        if (glowRadius > 0.0f) {
            this.rectPipeline.drawGlow(t[0], t[1], t[2], t[3], glowColor, new float[]{t[4], t[4], t[4], t[4]}, glowRadius * s, 0.5f);
        }
        int finalColor = ColorUtil.a(color, (ColorUtil.b(color)[3] / 255.0f) * alpha);
        this.rectPipeline.drawRect(t[0], t[1], t[2], t[3], new int[]{finalColor}, new float[]{t[4], t[4], t[4], t[4]});
    }

    public void drawGlow(Matrix3x2f matrices, float x, float y, float width, float height, float radius, int glowColor, float glowRadius) {
        float[] t = transformRect(matrices, x, y, width, height, radius);
        float s = t[5];
        if (glowRadius > 0.0f) {
            this.rectPipeline.drawGlow(t[0], t[1], t[2], t[3], glowColor, new float[]{t[4], t[4], t[4], t[4]}, glowRadius * s, 0.5f);
        }
    }

    private void drawPanelBlur(float[] t, float alpha) {
        GpuTextureView blurView = this.blurPipeline.view();
        if (blurView != null && !blurView.isClosed() && alpha > 0.0f && t[2] > 2.0f && t[3] > 2.0f) {
            float radius = Math.max(0.0f, t[4]);
            this.panelBlurPipeline.drawBlurTexture(blurView, t[0], t[1], t[2], t[3], new float[]{radius, radius, radius, radius}, alpha);
        }
    }

    public void a(DrawContext context, float x, float y, float width, float height, int color) {
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), color);
    }
}
