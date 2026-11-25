package egg.client.render.font;

import egg.client.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFont {
    private ClientFont() {}

    public static final StyleSpriteSource.Font SF_REGULAR = new StyleSpriteSource.Font(Identifier.of("egg", "sf_regular"));
    public static final StyleSpriteSource.Font SF_MEDIUM = new StyleSpriteSource.Font(Identifier.of("egg", "sf_medium"));
    public static final StyleSpriteSource.Font SF_SEMIBOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_semibold"));
    public static final StyleSpriteSource.Font SF_BOLD = new StyleSpriteSource.Font(Identifier.of("egg", "sf_bold"));

    public static final StyleSpriteSource.Font ROCKSTAR_ROUND_BOLD = new StyleSpriteSource.Font(Identifier.of("egg", "rockstar_roundbold"));

    public static final StyleSpriteSource.Font CLICKGUI_ICONS = new StyleSpriteSource.Font(Identifier.of("egg", "clickgui_icons"));
    public static final StyleSpriteSource.Font ICON_CLIENT = new StyleSpriteSource.Font(Identifier.of("egg", "iconclient"));
    public static final StyleSpriteSource.Font ICONS = new StyleSpriteSource.Font(Identifier.of("egg", "icons"));

    public static final float FONT_HEIGHT = 9.0f;
    public static boolean FORCE_ROUND_BOLD = false;

    private static final Map<StyleSpriteSource.Font, Map<String, Text>> FONT_TEXT_CACHE = new ConcurrentHashMap<>();
    private static final Map<StyleSpriteSource.Font, Map<String, Float>> FONT_WIDTH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, Text>> GRADIENT_CACHE = new ConcurrentHashMap<>();

    public static boolean isClickGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && (mc.currentScreen instanceof egg.client.ui.ClickGuiScreen
                || mc.currentScreen instanceof egg.client.ui.screen.EggTitleScreen);
    }

    public static egg.client.render.msdf.Font resolveMsdfFont(StyleSpriteSource.Font font) {
        if (font == CLICKGUI_ICONS || font == ICON_CLIENT || font == ICONS) {
            return egg.client.render.msdf.Fonts.a;
        }
        if (font == SF_REGULAR) {
            return egg.client.render.msdf.Fonts.sfRegular;
        }
        return egg.client.render.msdf.Fonts.b;
    }

    private static StyleSpriteSource.Font resolveFont(StyleSpriteSource.Font font) {
        if (font == CLICKGUI_ICONS || font == ICON_CLIENT || font == ICONS) {
            return font;
        }
        if (font == SF_REGULAR || font == SF_MEDIUM || font == SF_SEMIBOLD || font == SF_BOLD) {
            return font;
        }
        if (FORCE_ROUND_BOLD || isClickGui()) {
            return ROCKSTAR_ROUND_BOLD;
        }
        return font;
    }

    private static Map<String, Text> getTextMap(StyleSpriteSource.Font font) {
        return FONT_TEXT_CACHE.computeIfAbsent(font, f -> new ConcurrentHashMap<>());
    }

    private static Map<String, Float> getWidthMap(StyleSpriteSource.Font font) {
        return FONT_WIDTH_CACHE.computeIfAbsent(font, f -> new ConcurrentHashMap<>());
    }

    public static Text getText(String text, StyleSpriteSource.Font font) {
        if (text == null || text.isEmpty()) return Text.empty();
        StyleSpriteSource.Font targetFont = resolveFont(font);
        return getTextMap(targetFont).computeIfAbsent(text, k -> Text.literal(k).setStyle(Style.EMPTY.withFont(targetFont)));
    }

    public static void draw(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        if (isClickGui()) {
            egg.client.render.msdf.Font msdf = resolveMsdfFont(font);
            Matrix3x2f mat = Render2D.getMatrix(context);
            msdf.a(mat, text, x, y - 1.5f, 10.0f, color);
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;
        int ix = Math.round(x);
        int iy = Math.round(y);
        context.drawText(tr, getText(text, font), ix, iy, color, false);
    }

    public static void drawScaled(DrawContext context, String text, float x, float y, float scale, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        if (isClickGui()) {
            egg.client.render.msdf.Font msdf = resolveMsdfFont(font);
            Matrix3x2f mat = Render2D.getMatrix(context);
            msdf.a(mat, text, x, y - 1.5f * scale, 10.0f * scale, color);
            return;
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        draw(context, text, 0.0f, 0.0f, font, color);
        context.getMatrices().popMatrix();
    }

    public static void drawCentered(DrawContext context, String text, float cx, float cy, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        if (isClickGui()) {
            egg.client.render.msdf.Font msdf = resolveMsdfFont(font);
            Matrix3x2f mat = Render2D.getMatrix(context);
            float size = 10.0f;
            float w = msdf.a(text, size);
            float x = cx - w / 2.0f;
            float y = cy - 5.0f;
            msdf.a(mat, text, x, y, size, color);
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;
        float w = getWidth(text, font);
        int ix = Math.round(cx - w / 2.0f);
        int iy = Math.round(cy - FONT_HEIGHT / 2.0f);
        context.drawText(tr, getText(text, font), ix, iy, color, false);
    }

    public static void drawCenteredScaled(DrawContext context, String text, float cx, float cy, float scale, StyleSpriteSource.Font font, int color) {
        if (text == null || text.isEmpty() || context == null) return;
        if (isClickGui()) {
            egg.client.render.msdf.Font msdf = resolveMsdfFont(font);
            Matrix3x2f mat = Render2D.getMatrix(context);
            float size = 10.0f * scale;
            float w = msdf.a(text, size);
            float x = cx - w / 2.0f;
            float y = cy - 5.0f * scale;
            msdf.a(mat, text, x, y, size, color);
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;
        float w = getWidth(text, font);
        float h = tr.fontHeight;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx - (w * scale) / 2.0f, cy - (h * scale) / 2.0f);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            context.getMatrices().scale(scale, scale);
        }
        context.drawText(tr, getText(text, font), 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    public static void drawGradient(DrawContext context, String text, float x, float y, StyleSpriteSource.Font font, int color1, int color2) {
        if (text == null || text.isEmpty() || context == null) return;
        if (isClickGui()) {
            egg.client.render.msdf.Font msdf = resolveMsdfFont(font);
            Matrix3x2f mat = Render2D.getMatrix(context);
            float size = 10.0f;
            int len = text.length();
            float curX = x;
            int a1 = (color1 >> 24) & 0xFF;
            int a2 = (color2 >> 24) & 0xFF;
            int r1 = (color1 >> 16) & 0xFF;
            int r2 = (color2 >> 16) & 0xFF;
            int g1 = (color1 >> 8) & 0xFF;
            int g2 = (color2 >> 8) & 0xFF;
            int b1 = color1 & 0xFF;
            int b2 = color2 & 0xFF;
            for (int i = 0; i < len; i++) {
                float ratio = len > 1 ? (float) i / (float) (len - 1) : 0.0f;
                int a = (int) (a1 * (1.0f - ratio) + a2 * ratio);
                int r = (int) (r1 * (1.0f - ratio) + r2 * ratio);
                int g = (int) (g1 * (1.0f - ratio) + g2 * ratio);
                int b = (int) (b1 * (1.0f - ratio) + b2 * ratio);
                int col = (a << 24) | (r << 16) | (g << 8) | b;
                String ch = String.valueOf(text.charAt(i));
                msdf.a(mat, ch, curX, y - 1.5f, size, col);
                curX += msdf.a(ch, size);
            }
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;

        int alpha1 = (color1 >> 24) & 0xFF;
        int alpha2 = (color2 >> 24) & 0xFF;
        int avgAlpha = (alpha1 + alpha2) / 2;
        if (avgAlpha == 0) avgAlpha = 255;

        StyleSpriteSource.Font targetFont = resolveFont(font);
        if (GRADIENT_CACHE.size() > 256) {
            GRADIENT_CACHE.clear();
        }
        long gradKey = (((long) (color1 & 0xFFFFFF)) << 32) | ((long) (color2 & 0xFFFFFF)) ^ targetFont.hashCode();
        Map<String, Text> gradMap = GRADIENT_CACHE.computeIfAbsent(gradKey, k -> new ConcurrentHashMap<>());
        Text cached = gradMap.computeIfAbsent(text, k -> buildGradientText(k, targetFont, color1 & 0xFFFFFF, color2 & 0xFFFFFF));

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
        if (isClickGui()) {
            return resolveMsdfFont(font).a(text, 10.0f);
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return text.length() * 6.0f;

        StyleSpriteSource.Font targetFont = resolveFont(font);
        Map<String, Float> widthMap = getWidthMap(targetFont);
        Float cached = widthMap.get(text);
        if (cached != null) return cached;

        float w = tr.getWidth(getText(text, targetFont));
        widthMap.put(text, w);
        return w;
    }

    public static void clearCaches() {
        FONT_TEXT_CACHE.clear();
        FONT_WIDTH_CACHE.clear();
        GRADIENT_CACHE.clear();
    }
}
