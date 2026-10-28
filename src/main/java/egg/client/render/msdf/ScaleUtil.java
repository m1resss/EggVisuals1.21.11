package egg.client.render.msdf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.DrawContext;

public class ScaleUtil {
    private ScaleUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void a(DrawContext context, int scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        int target = window.calculateScaleFactor(scale, client.forcesUnicodeFont());
        window.setScaleFactor(target);
        context.getMatrices().pushMatrix();
    }

    public static void a(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().popMatrix();
        client.getWindow().setScaleFactor(client.getWindow().calculateScaleFactor((Integer) client.options.getGuiScale().getValue(), client.forcesUnicodeFont()));
    }

    public static void b(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        a(context, (Integer) client.options.getGuiScale().getValue());
    }

    public static void c(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.getMatrices().popMatrix();
        client.getWindow().setScaleFactor(client.getWindow().calculateScaleFactor(2, client.forcesUnicodeFont()));
    }
}
