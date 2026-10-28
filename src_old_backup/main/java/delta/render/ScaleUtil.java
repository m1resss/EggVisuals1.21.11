package delta.render;

import static delta.core.Interface.aM_;

import delta.core.Interface;

import lombok.Generated;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.DrawContext;

public class ScaleUtil implements Interface {
    @Generated
    private ScaleUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void a(DrawContext context, int scale) {
        if (context == null) return;
        Window window = aM_.getWindow();
        int previous = window.getScaleFactor();
        int target = window.calculateScaleFactor(scale, aM_.forcesUnicodeFont());
        context.getMatrices().pushMatrix();
        if (previous > 0 && target != previous) {
            float s = (float) target / (float) previous;
            context.getMatrices().scale(s, s);
        }
    }

    public static void a(DrawContext context) {
        if (context == null) return;
        context.getMatrices().popMatrix();
    }

    public static void b(DrawContext context) {
        a(context, ((Integer) aM_.options.getGuiScale().getValue()).intValue());
    }

    public static void c(DrawContext context) {
        if (context == null) return;
        context.getMatrices().popMatrix();
    }
}
