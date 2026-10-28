package delta.cosmetic;

import static delta.core.Interface.aM_;
import delta.core.Delta;

import delta.core.Interface;
import delta.cosmetic.CosmeticsType;

import java.util.UUID;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

public class CosmeticsRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> implements Interface {
    public CosmeticsRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        VertexConsumerProvider buffers = aM_.getBufferBuilders().getEntityVertexConsumers();
        Entity entity = aM_.world != null ? aM_.world.getEntityById(state.id) : null;
        if (entity != null) {
            UUID uuid = entity.getUuid();
            float tickDelta = aM_.getRenderTickCounter().getTickProgress(false);
            for (Cosmetic cosmetic : Delta.h().d().r().getCosmetics()) {
                if (uuid.equals(cosmetic.getUuid()) && cosmetic.getType() == CosmeticsType.COSMETIC) {
                    matrices.push();
                    cosmetic.getCategory().transform(matrices, (PlayerEntityModel) getContextModel(), cosmetic);
                    cosmetic.getRenderer().render(matrices, cosmetic, buffers, null, null, light, tickDelta);
                    matrices.pop();
                }
            }

            // Local player skycore cosmetics
            if (entity == aM_.player) {
                SkycoreCosmeticsManager.getInstance().renderAttached(matrices, buffers, (PlayerEntityModel) getContextModel(), light);
            } else {
                delta.module.render.Globals globals = delta.module.render.Globals.getInstance();
                if (globals != null && globals.m() && globals.isSyncCosmetics()) {
                    delta.module.render.Globals.PeerCosmetics peer = globals.getPeerCosmetics(entity);
                    if (peer != null) {
                        SkycoreCosmeticsManager.getInstance().renderAttachedForPeer(matrices, buffers, (PlayerEntityModel) getContextModel(), light, peer);
                    }
                }
            }
        }
    }
}
