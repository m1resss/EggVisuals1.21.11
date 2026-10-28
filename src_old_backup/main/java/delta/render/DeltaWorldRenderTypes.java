package delta.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;

/** Render pipeline used by the model-surface scan effect. */
public final class DeltaWorldRenderTypes {

    public static RenderLayer targetEspScan(Identifier texture) {
        return RenderLayers.entityTranslucentEmissive(texture, false);
    }

    private DeltaWorldRenderTypes() {
    }

    /** Call once from the existing Fabric client initializer. */
    public static void initialize() {
    }
}
