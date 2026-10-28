package egg.client.render;

import egg.client.module.Category;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class IconRenderer {
    private IconRenderer() {}

    private static final Identifier COMBAT_ICON = Identifier.of("egg", "textures/icons/combat.png");
    private static final Identifier MOVEMENT_ICON = Identifier.of("egg", "textures/icons/movement.png");
    private static final Identifier VISUAL_ICON = Identifier.of("egg", "textures/icons/visual.png");
    private static final Identifier ESP_ICON = Identifier.of("egg", "textures/icons/esp.png");
    private static final Identifier PLAYER_ICON = Identifier.of("egg", "textures/icons/player.png");
    private static final Identifier MISC_ICON = Identifier.of("egg", "textures/icons/misc.png");
    private static final Identifier COSMETIC_ICON = Identifier.of("egg", "textures/icons/cosmetic.png");
    private static final Identifier MODS_ICON = Identifier.of("egg", "textures/icons/mods.png");
    private static final Identifier CASES_ICON = Identifier.of("egg", "textures/cases/coin.png");
    private static final Identifier SEARCH_ICON = Identifier.of("egg", "textures/icons/search.png");
    private static final Identifier STAR_ICON = Identifier.of("egg", "textures/icons/star.png");

    public static void drawCategoryIcon(DrawContext context, Category category, float x, float y, float size, int color) {
        Identifier id = switch (category) {
            case COMBAT -> COMBAT_ICON;
            case MOVEMENT -> MOVEMENT_ICON;
            case ESP -> ESP_ICON;
            case PLAYER -> PLAYER_ICON;
            case INVENTORY -> MODS_ICON;
            case MISC -> MISC_ICON;
            case SETTINGS -> MODS_ICON;
        };
        drawTextureIcon(context, id, x, y, size, size, color);
    }

    public static void drawSearchIcon(DrawContext context, float cx, float cy, int color) {
        float size = 12.0f;
        drawSearchIcon(context, cx - size / 2.0f, cy - size / 2.0f, size, color);
    }

    public static void drawSearchIcon(DrawContext context, float x, float y, float size, int color) {
        drawTextureIcon(context, SEARCH_ICON, x, y, size, size, color);
    }

    public static void drawStarIcon(DrawContext context, float cx, float cy, boolean filled, int color) {
        float size = 12.0f;
        drawTextureIcon(context, STAR_ICON, cx - size / 2.0f, cy - size / 2.0f, size, size, color);
    }

    public static void drawDotsIcon(DrawContext context, float cx, float cy, int color) {
        float size = 2.4f;
        float r = size / 2.0f;
        float gap = 3.8f;
        Draw2DProcessor.INSTANCE.drawRect(null, cx - r, cy - gap - r, size, size, r, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx - r, cy - r, size, size, r, color);
        Draw2DProcessor.INSTANCE.drawRect(null, cx - r, cy + gap - r, size, size, r, color);
        DrawBatcher.flushPending();
    }

    public static void drawTextureIcon(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        drawTextureScaled(context, texture, x, y, width, height, 64, 64, color);
    }

    public static void drawTextureScaled(DrawContext context, Identifier texture, float x, float y, float width, float height, int texW, int texH, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.max(1, Math.round(width));
        int ih = Math.max(1, Math.round(height));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0.0f, 0.0f, iw, ih, texW, texH, texW, texH, color);
    }
}
