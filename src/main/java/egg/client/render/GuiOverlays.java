package egg.client.render;

import egg.client.render.msdf.pipeline.DrawBatcher;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public final class GuiOverlays {
    private static final List<Runnable> pending = new ArrayList<>(2);

    private GuiOverlays() {
    }

    public static void defer(Runnable renderer) {
        pending.add(renderer);
    }

    public static void defer(DrawContext context, Runnable renderer) {
        var pose = Render2D.getMatrix(context);
        defer(() -> {
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.set(pose);
            try {
                renderer.run();
            } finally {
                matrices.popMatrix();
            }
        });
    }

    public static void renderPending() {
        if (pending.isEmpty()) return;

        DrawBatcher.flushPending();
        try {
            for (Runnable renderer : pending) renderer.run();
        } finally {
            pending.clear();
        }
    }
}
