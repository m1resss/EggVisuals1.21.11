package egg.client.render.msdf;

import egg.client.render.msdf.pipeline.FontRenderer;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;

import java.util.List;

public class Font {
    private static FontRenderer renderer;

    private final String name;

    public static FontRenderer getRenderer() {
        if (renderer == null) {
            renderer = new FontRenderer();
            renderer.loadFont("icons", "icons");
            renderer.loadFont("hud_icons", "hud_icons");
            renderer.loadFont("sf_regular", "sf_regular");
            renderer.loadFont("onest_regular", "onest_regular");
            renderer.loadFont("sf_medium", "sf_medium");
            renderer.loadFont("gt_regular", "gt_regular");
            renderer.initialize();
        }
        return renderer;
    }

    public Font(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public float a(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        return getRenderer().getStringWidth(this.name, text, size);
    }

    public float a(Text text, float size) {
        if (text == null) return 0f;
        return getRenderer().getStringWidth(this.name, text.getString(), size);
    }

    public float a(float size) {
        return getRenderer().getStringHeight(this.name, size);
    }

    public float b(String text, float size) {
        return a(text, size);
    }

    public float b(String text, float size, float thickness) {
        return a(text, size);
    }

    public float a(String text, float size, float centerY) {
        return (centerY - (a(size) / 2.0f)) - 0.5f;
    }

    public float a(float size, float centerY) {
        return (centerY - (a(size) / 2.0f)) - 0.5f;
    }

    public static float[] transform(Matrix3x2f mat, float x, float y, float size) {
        if (mat == null) {
            return new float[]{x, y, size, 0f, 1f};
        }
        float p0x = mat.m00 * x + mat.m10 * y + mat.m20;
        float p0y = mat.m01 * x + mat.m11 * y + mat.m21;
        float pXx = mat.m00 * (x + 1f) + mat.m10 * y + mat.m20;
        float pXy = mat.m01 * (x + 1f) + mat.m11 * y + mat.m21;
        float dx = pXx - p0x;
        float dy = pXy - p0y;
        float scale = (float) Math.hypot(dx, dy);
        if (scale == 0f) scale = 1f;
        float rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
        return new float[]{p0x, p0y, size * scale, rotation, scale};
    }

    public void a(Matrix3x2f matrices, String text, float x, float y, float size, int color) {
        if (text == null || text.isEmpty()) return;
        float[] p = transform(matrices, x, y, size);
        getRenderer().drawText(this.name, text, p[0], p[1], p[2], color, p[3]);
    }

    public void a(Matrix3x2f matrices, Text text, float x, float y, float size, float shadow, float open) {
        if (text == null) return;
        int alpha = (int) (Math.min(1.0f, Math.max(0.0f, open)) * 255.0f);
        if (alpha <= 0) return;

        float curX = x;
        String root = text.getContent().visit((StringVisitable.Visitor<String>) s -> java.util.Optional.of(s)).orElse("");
        if (!root.isEmpty()) {
            int col = text.getStyle().getColor() != null ? text.getStyle().getColor().getRgb() : -1;
            col = (col & 0x00FFFFFF) | (alpha << 24);
            a(matrices, root, curX, y, size, col);
            curX += a(root, size);
        }

        for (Text part : text.getSiblings()) {
            String str = part.getString();
            int col = part.getStyle().getColor() != null ? part.getStyle().getColor().getRgb() : -1;
            col = (col & 0x00FFFFFF) | (alpha << 24);
            a(matrices, str, curX, y, size, col);
            curX += a(str, size);
        }
    }

    public void a(Matrix3x2f matrices, Text text, float x, float y, float size) {
        a(matrices, text, x, y, size, 0.0f, 1.0f);
    }

    public void c(Matrix3x2f matrices, String text, float x, float y, float size, int color, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) return;
        float[] p = transform(matrices, x, y, size);
        getRenderer().drawTextFading(this.name, text, p[0], p[1], p[2], maxWidth * p[4], color);
    }

    public void c(Matrix3x2f matrices, String text, float x, float y, float size, int color, float shadow, float maxWidth) {
        c(matrices, text, x, y, size, color, maxWidth);
    }

    public float a(Matrix3x2f matrices, String text, float x, float y, float size, int color, float maxWidth, boolean hovered, float scroll, float delta) {
        c(matrices, text, x, y, size, color, maxWidth);
        return scroll;
    }

    public void b(Matrix3x2f matrices, String text, float centerX, float y, float size, int color) {
        float w = a(text, size);
        a(matrices, text, centerX - (w / 2.0f), y, size, color);
    }

    public void drawCentered(Matrix3x2f matrices, String text, float centerX, float centerY, float size, int color, float rotationDeg) {
        if (text == null || text.isEmpty()) return;
        egg.client.render.msdf.pipeline.FontAtlas atlas = getRenderer().getFont(this.name);
        if (atlas == null) return;
        atlas.ensureLoaded();

        float tcx = centerX;
        float tcy = centerY;
        float matScale = 1.0f;
        float matRot = 0.0f;
        if (matrices != null) {
            tcx = matrices.m00 * centerX + matrices.m10 * centerY + matrices.m20;
            tcy = matrices.m01 * centerX + matrices.m11 * centerY + matrices.m21;
            matScale = (float) Math.hypot(matrices.m00, matrices.m01);
            matRot = (float) Math.toDegrees(Math.atan2(matrices.m01, matrices.m00));
        }

        float drawSize = size * matScale;
        float finalRot = rotationDeg + matRot;

        float w = getRenderer().getStringWidth(this.name, text, drawSize);
        float h = atlas.getLineHeight() * (drawSize / atlas.getFontSize());

        float offX = w * 0.5f;
        float offY = h * 0.5f;
        egg.client.render.msdf.pipeline.Glyph g = atlas.getGlyph(text.codePointAt(0));
        if (text.length() == 1 && g != null) {
            float scale = drawSize / atlas.getFontSize();
            offX = (g.xOffset + g.width * 0.5f) * scale;
            offY = (g.yOffset + g.height * 0.5f) * scale;
        }

        getRenderer().getPipeline().drawTextRotatedAroundPoint(
                atlas, text,
                tcx - offX, tcy - offY,
                drawSize, color, 0.0f, 0,
                finalRot, tcx, tcy
        );
    }
}
