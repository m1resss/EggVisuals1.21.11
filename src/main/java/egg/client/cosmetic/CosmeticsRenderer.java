package egg.client.cosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

public class CosmeticsRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public CosmeticsRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isLocal = false;
        if (mc.player != null && mc.world != null) {
            Entity entity = mc.world.getEntityById(state.id);
            if (entity == mc.player) {
                isLocal = true;
            }
        }
        if (isLocal || state.id <= 0 || (mc.player != null && state.id == mc.player.getId())) {
            CosmeticsManager.getInstance().renderAttached(matrices, queue, getContextModel(), light);
        }
    }
}
