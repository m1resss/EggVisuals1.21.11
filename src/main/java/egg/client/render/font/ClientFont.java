package egg.client.render.font;

import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClientFont {
    private ClientFont() {}

    public static final StyleSpriteSource.Font SF_REGULAR = new StyleSpriteSource.Font(Identifier.of("egg", "sf_regular"));
    public static final StyleSpriteSource.Font SF_MEDIUM = new StyleSpriteSource.Font(Identifier.of("egg", "sf_medium"));
    public static final StyleSpriteSource.Font SF_SEMIBOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_semibold"));
    public static final StyleSpriteSource.Font SF_BOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_bold"));

    public static final float FONT_HEIGHT = 9.0f;
    public static final float DEFAULT_SIZE = 10.5f;

    public static Font getFont(StyleSpriteSource.Font font) {
        if (font == SF_REGULAR) {
            return Fonts.sfRegular;
        }
        return Fonts.b; // sf_medium (Apple SF Pro Medium)
    }

    public static Text getText(String text, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty()) return Text.empty();
        return Text.literal(text).setStyle(Style.EMPTY.withFont(font));
    }

    public static void draw(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color) {
        draw(context, text, x, y, DEFAULT_SIZE, font, color);
    }

    public static void draw(DrawContext context, String text, float x, float y, float size, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        Font f = getFont(font);
        f.a(context.getMatrices(), text, x, y, size, color);
    }

    public static void drawScaled(DrawContext context, String text, float x, float y, float scale, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        Font f = getFont(font);
        f.a(context.getMatrices(), text, x, y, DEFAULT_SIZE * scale, color);
    }

    public static void drawCentered(DrawContext context, String text, float cx, float cy, StyleSpriteSource.Font font, int color) {
        drawCentered(context, text, cx, cy, DEFAULT_SIZE, font, color);
    }

    public static void drawCentered(DrawContext context, String text, float cx, float cy, float size, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        Font f = getFont(font);
        float w = f.a(text, size);
        float x = cx - w / 2.0f;
        float y = cy - FONT_HEIGHT / 2.0f;
        f.a(context.getMatrices(), text, x, y, size, color);
    }

    public static void drawGradient(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color1, int color2) {
        if (text == null || text.isEmpty() || context == null) return;
        Font f = getFont(font);
        int len = text.length();
        float curX = x;
        for (int i = 0; i < len; i++) {
            float ratio = len > 1 ? (float) i / (float) (len - 1) : 0.0f;
            int col = ColorUtil.interpolate(color1, color2, ratio);
            String ch = String.valueOf(text.charAt(i));
            f.a(context.getMatrices(), ch, curX, y, DEFAULT_SIZE, col);
            curX += f.a(ch, DEFAULT_SIZE);
        }
    }

    public static float getWidth(String text, StyleSpriteSource.Font font) {
        return getWidth(text, DEFAULT_SIZE, font);
    }

    public static float getWidth(String text, float size, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty()) return 0.0f;
        Font f = getFont(font);
        return f.a(text, size);
    }

    public static void clearCaches() {
    }
}
