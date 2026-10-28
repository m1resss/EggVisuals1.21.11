package egg.client.render.font;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFont {
    private ClientFont() {}

    public static final StyleSpriteSource.Font SF_REGULAR = new StyleSpriteSource.Font(Identifier.of("egg", "sf"));
    public static final StyleSpriteSource.Font SF_MEDIUM = new StyleSpriteSource.Font(Identifier.of("egg", "sf"));
    public static final StyleSpriteSource.Font SF_SEMIBOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_medium"));
    public static final StyleSpriteSource.Font SF_BOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_bold"));

    // Real font height for SF Pro with size ~11 (avoids tr.fontHeight default vanilla 9.0)
    public static final float FONT_HEIGHT = 9.0f;

    // Nested caches to completely eliminate string concatenation (text + "@" + font.id())
    private static final Map<StyleSpriteSource.Font, Map<String, Text>> FONT_TEXT_CACHE = new ConcurrentHashMap<>();
    private static final Map<StyleSpriteSource.Font, Map<String, Float>> FONT_WIDTH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, Text>> GRADIENT_CACHE = new ConcurrentHashMap<>();

    private static Map<String, Text> getTextMap(StyleSpriteSource.Font font) {
        return FONT_TEXT_CACHE.computeIfAbsent(font, f -> new ConcurrentHashMap<>());
    }

    private static Map<String, Float> getWidthMap(StyleSpriteSource.Font font) {
        return FONT_WIDTH_CACHE.computeIfAbsent(font, f -> new ConcurrentHashMap<>());
    }

    public static Text getText(String text, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty()) return Text.empty();
        return getTextMap(font).computeIfAbsent(text, k -> Text.literal(k).setStyle(Style.EMPTY.withFont(font)));
    }

    public static void draw(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        context.drawText(tr, getText(text, font), ix, iy, color, false);
    }

    public static void drawScaled(DrawContext context, String text, float x, float y, float scale, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty()) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        draw(context, text, 0.0f, 0.0f, font, color);
        context.getMatrices().popMatrix();
    }

    public static void drawCentered(DrawContext context, String text, float cx, float cy, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;
        float w = getWidth(text, font);
        int ix = Math.round(cx - w / 2.0f);
        int iy = Math.round(cy - FONT_HEIGHT / 2.0f);
        context.drawText(tr, getText(text, font), ix, iy, color, false);
    }

    public static void drawGradient(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color1, int color2) {
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;

        int alpha1 = (color1 >> 24) & 0xFF;
        int alpha2 = (color2 >> 24) & 0xFF;
        int avgAlpha = (alpha1 + alpha2) / 2;
        if (avgAlpha == 0) avgAlpha = 255;

        long gradKey = (((long) (color1 & 0xFFFFFF)) << 32) | ((long) (color2 & 0xFFFFFF));
        Map<String, Text> gradMap = GRADIENT_CACHE.computeIfAbsent(gradKey, k -> new ConcurrentHashMap<>());
        Text cached = gradMap.computeIfAbsent(text, k -> buildGradientText(k, font, color1 & 0xFFFFFF, color2 & 0xFFFFFF));

        int passColor = (avgAlpha << 24) | 0xFFFFFF;
        context.drawText(tr, cached, Math.round(x), Math.round(y), passColor, false);
    }

    private static Text buildGradientText(String text, StyleSpriteSource.Font font, int rgb1, int rgb2) {
        int len = text.length();
        MutableText root = Text.empty();
        for (int i = 0; i < len; i++) {
            float ratio = len > 1 ? (float) i / (float) (len - 1) : 0.0f;
            int r = (int) (((rgb1 >> 16) & 0xFF) * (1.0f - ratio) + ((rgb2 >> 16) & 0xFF) * ratio);
            int g = (int) (((rgb1 >> 8) & 0xFF) * (1.0f - ratio) + ((rgb2 >> 8) & 0xFF) * ratio);
            int b = (int) ((rgb1 & 0xFF) * (1.0f - ratio) + (rgb2 & 0xFF) * ratio);
            int rgb = (r << 16) | (g << 8) | b;

            root.append(Text.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withFont(font).withColor(TextColor.fromRgb(rgb))));
        }
        return root;
    }

    public static float getWidth(String text, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty()) return 0.0f;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return text.length() * 6.0f;

        Map<String, Float> widthMap = getWidthMap(font);
        Float cached = widthMap.get(text);
        if (cached != null) return cached;

        float w = tr.getWidth(getText(text, font));
        widthMap.put(text, w);
        return w;
    }

    public static void clearCaches() {
        FONT_TEXT_CACHE.clear();
        FONT_WIDTH_CACHE.clear();
        GRADIENT_CACHE.clear();
    }
}
