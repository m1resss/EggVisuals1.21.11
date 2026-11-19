package egg.client.render;

import egg.client.module.Category;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class IconRenderer {
    private static final Identifier CAT_MOVEMENT = Identifier.of("egg", "textures/icons/cat_movement.png");
    private static final Identifier CAT_VISUALS = Identifier.of("egg", "textures/icons/cat_visuals.png");
    private static final Identifier CAT_INVENTORY = Identifier.of("egg", "textures/icons/cat_inventory.png");
    private static final Identifier CAT_MISC = Identifier.of("egg", "textures/icons/cat_misc.png");
    private static final Identifier CAT_COSMETICS = Identifier.of("egg", "textures/icons/cat_cosmetics.png");
    private static final Identifier CAT_MINIGAMES = Identifier.of("egg", "textures/icons/cat_minigames.png");
    private static final Identifier CAT_SETTINGS = Identifier.of("egg", "textures/icons/cat_settings.png");

    private static final Identifier GUI_SEARCH = Identifier.of("egg", "textures/icons/gui_search.png");
    private static final Identifier GUI_STAR_FILLED = Identifier.of("egg", "textures/icons/gui_star_filled.png");
    private static final Identifier GUI_STAR_EMPTY = Identifier.of("egg", "textures/icons/gui_star_empty.png");
    private static final Identifier GUI_CHEVRON_LEFT = Identifier.of("egg", "textures/icons/gui_chevron_left.png");
    private static final Identifier GUI_CHEVRON_RIGHT = Identifier.of("egg", "textures/icons/gui_chevron_right.png");
    private static final Identifier GUI_CHEVRON_DOWN = Identifier.of("egg", "textures/icons/gui_chevron_down.png");
    private static final Identifier GUI_CHEVRON_UP = Identifier.of("egg", "textures/icons/gui_chevron_up.png");
    private static final Identifier GUI_DOTS = Identifier.of("egg", "textures/icons/gui_dots.png");
    private static final Identifier GUI_CROSS = Identifier.of("egg", "textures/icons/gui_cross.png");

    private IconRenderer() {}

    private static Identifier getCategoryTexture(Category category) {
        if (category == null) return CAT_MISC;
        return switch (category) {
            case MOVEMENT -> CAT_MOVEMENT;
            case ESP -> CAT_VISUALS;
            case INVENTORY -> CAT_INVENTORY;
            case COSMETICS -> CAT_COSMETICS;
            case MINI_GAMES -> CAT_MINIGAMES;
            case MISC -> CAT_MISC;
            case SETTINGS -> CAT_SETTINGS;
        };
    }

    public static void drawCategoryIcon(DrawContext context, Category category, float x, float y, float size, int color) {
        drawTextureFloat(context, getCategoryTexture(category), x, y, size, size, color);
    }

    public static void drawSearchIcon(DrawContext context, float cx, float cy, int color) {
        drawTextureCentered(context, GUI_SEARCH, cx, cy, 10.0f, color);
    }

    public static void drawSearchIcon(DrawContext context, float x, float y, float size, int color) {
        drawTextureFloat(context, GUI_SEARCH, x, y, size, size, color);
    }

    public static void drawStarIcon(DrawContext context, float cx, float cy, boolean filled, int color) {
        drawTextureCentered(context, GUI_STAR_FILLED, cx, cy, 8.5f, color);
    }

    public static void drawCollapseIcon(DrawContext context, float cx, float cy, boolean collapsed, int color) {
        Identifier texture = collapsed ? GUI_CHEVRON_RIGHT : GUI_CHEVRON_LEFT;
        drawTextureCentered(context, texture, cx, cy, 8.0f, color);
    }

    public static void drawDropdownChevron(DrawContext context, float cx, float cy, boolean opened, int color) {
        Identifier texture = opened ? GUI_CHEVRON_UP : GUI_CHEVRON_DOWN;
        drawTextureCentered(context, texture, cx, cy, 8.0f, color);
    }

    public static void drawDotsIcon(DrawContext context, float cx, float cy, int color) {
        drawTextureCentered(context, GUI_DOTS, cx, cy, 9.0f, color);
    }

    public static void drawCrossIcon(DrawContext context, float cx, float cy, float size, int color) {
        drawTextureCentered(context, GUI_CROSS, cx, cy, size, color);
    }

    public static void drawTextureCentered(DrawContext context, Identifier texture, float cx, float cy, float size, int color) {
        drawTextureFloat(context, texture, cx - size / 2.0f, cy - size / 2.0f, size, size, color);
    }

    public static void drawTextureFloat(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        drawTextureScaled(context, texture, x, y, width, height, 32, 32, color);
    }

    public static void drawTextureIcon(DrawContext context, Identifier texture, float x, float y, float width, float height, int color) {
        drawTextureScaled(context, texture, x, y, width, height, 32, 32, color);
    }

    public static void drawTextureScaled(DrawContext context, Identifier texture, float x, float y, float width, float height, int texW, int texH, int color) {
        if (context == null || texture == null || width <= 0.0f || height <= 0.0f) return;
        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(width / (float) texW, height / (float) texH);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f, texW, texH, texW, texH, texW, texH, color);
        context.getMatrices().popMatrix();
    }
}
