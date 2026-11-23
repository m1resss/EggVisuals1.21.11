package egg.client.ui.theme;

import egg.client.module.misc.ClientSettings;
import egg.client.render.Render2D;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

public final class FrostedSurface {
    private FrostedSurface() {
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float radius,
                            int fallback, float alpha, float brightness, boolean blurred) {
        float transmission = (0.93f - ClientSettings.getGuiBgOpacity() * 0.43f) * brightness;
        if (blurred && Draw2DProcessor.INSTANCE.drawFrostedBackdrop(Render2D.getMatrix(context), x, y, width,
                height, radius, alpha, transmission)) return;
        Render2D.drawRoundedRect(context, x, y, width, height, radius, ColorUtil.withAlphaFloat(fallback, alpha));
    }
}
