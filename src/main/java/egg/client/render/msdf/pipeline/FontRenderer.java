package egg.client.render.msdf.pipeline;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class FontRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("client/FontRenderer");

    private final FontPipeline pipeline;
    private final Map<String, FontAtlas> fonts;
    private boolean initialized = false;

    public FontRenderer() {
        this.pipeline = new FontPipeline();
        this.fonts = new HashMap<>();
    }

    public void loadFont(String name, String path) {
        Identifier jsonId = Identifier.of("delta", "fonts/" + path + ".json");
        Identifier textureId = Identifier.of("delta", "fonts/" + path + ".png");
        FontAtlas atlas = new FontAtlas(jsonId, textureId);
        fonts.put(name, atlas);
        LOGGER.info("Registered font: {} -> {}", name, path);
    }

    public void loadAllFonts(Map<String, String> registry) {
        for (Map.Entry<String, String> entry : registry.entrySet()) {
            loadFont(entry.getKey(), entry.getValue());
        }
    }

    public void initialize() {
        if (initialized) return;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) return;
        LOGGER.info("Initializing {} fonts...", fonts.size());
        long startTime = System.currentTimeMillis();
        for (Map.Entry<String, FontAtlas> entry : fonts.entrySet()) {
            entry.getValue().forceLoad();
        }
        initialized = true;
        LOGGER.info("All fonts initialized in {}ms", System.currentTimeMillis() - startTime);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public FontAtlas getFont(String name) {
        return fonts.get(name);
    }

    public FontPipeline getPipeline() {
        return this.pipeline;
    }

    public void drawText(String fontName, String text, float x, float y, float size, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawText(atlas, text, x, y, size, color, 0, 0, 0);
    }

    public void drawText(String fontName, String text, float x, float y, float size, int color, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawText(atlas, text, x, y, size, color, 0, 0, rotation);
    }

    public void drawTextWithOutline(String fontName, String text, float x, float y, float size,
                                    int color, float outlineWidth, int outlineColor) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawText(atlas, text, x, y, size, color, outlineWidth, outlineColor, 0);
    }

    public void drawTextWithOutline(String fontName, String text, float x, float y, float size,
                                    int color, float outlineWidth, int outlineColor, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawText(atlas, text, x, y, size, color, outlineWidth, outlineColor, rotation);
    }

    public void drawTextFading(String fontName, String text, float x, float y, float size,
                               float maxWidth, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawTextFading(atlas, text, x, y, size, maxWidth, color);
    }

    public void drawTextFadingReverse(String fontName, String text, float x, float y, float size,
                                    float maxWidth, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawTextFadingReverse(atlas, text, x, y, size, maxWidth, color);
    }

    public void drawTextRightAlignFadeLeft(String fontName, String text, float rightX, float y, float size,
                                           float fadeStartX, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        pipeline.drawTextRightAlignFadeLeft(atlas, text, rightX, y, size, fadeStartX, color);
    }

    public void drawCenteredText(String fontName, String text, float x, float y, float size, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        float width = pipeline.getTextWidth(atlas, text, size);
        pipeline.drawText(atlas, text, x - width / 2, y, size, color, 0, 0, 0);
    }

    public void drawCenteredText(String fontName, String text, float x, float y, float size, int color, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        float width = pipeline.getTextWidth(atlas, text, size);
        float height = pipeline.getTextHeight(atlas, text, size);
        float centerX = x;
        float centerY = y + height / 2;
        pipeline.drawTextRotatedAroundPoint(atlas, text, x - width / 2, y, size, color, 0, 0, rotation, centerX, centerY);
    }

    public float getTextWidth(String fontName, String text, float size) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return 0;
        return pipeline.getTextWidth(atlas, text, size);
    }

    public float getStringWidth(String fontName, String text, float size) {
        return getTextWidth(fontName, text, size);
    }

    public float getLineHeight(String fontName, float size) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return size;
        return (atlas.getLineHeight() / atlas.getFontSize()) * size;
    }

    public float getStringHeight(String fontName, float size) {
        return getLineHeight(fontName, size);
    }

    public void flush() {
        pipeline.flush();
    }

    public void close() {
        pipeline.close();
        fonts.clear();
        initialized = false;
    }
}
