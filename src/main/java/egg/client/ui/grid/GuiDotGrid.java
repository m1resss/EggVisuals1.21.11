package egg.client.ui.grid;

import egg.client.module.misc.ClientSettings;
import egg.client.render.Render2D;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

public final class GuiDotGrid {
    private GuiDotGrid() {}

    public static void render(DrawContext context, float x, float y, float w, float h, float mouseX, float mouseY, float alpha) {
        if (!ClientSettings.isDotGrid() || alpha <= 0.01f || w <= 20.0f || h <= 20.0f) {
            return;
        }

        float spacing = 18.0f;
        float startX = x + 12.0f;
        float startY = y + 10.0f;
        float endX = x + w - 8.0f;
        float endY = y + h - 8.0f;

        int baseColor = Theme.TEXT_TITLE;
        int r = ColorUtil.red(baseColor);
        int g = ColorUtil.green(baseColor);
        int b = ColorUtil.blue(baseColor);

        for (float py = startY; py < endY; py += spacing) {
            for (float px = startX; px < endX; px += spacing) {
                float dx = px - mouseX;
                float dy = py - mouseY;
                float distSq = dx * dx + dy * dy;

                float dotAlpha = 0.04f;
                if (distSq < 2500.0f) {
                    float dist = (float) Math.sqrt(distSq);
                    dotAlpha += (1.0f - dist / 50.0f) * 0.07f;
                }

                int color = ColorUtil.rgba(r, g, b, (int) (dotAlpha * alpha * 255.0f));
                Render2D.drawRect(context, px, py, 1.0f, 1.0f, color);
            }
        }
    }
}
