package egg.client.mixin;

import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerHeldItemFeatureRenderer.class)
public abstract class PlayerHeldItemFeatureRendererMixin extends FeatureRenderer<PlayerEntityRenderState, EntityModel<PlayerEntityRenderState>> {

    public PlayerHeldItemFeatureRendererMixin(FeatureRendererContext<PlayerEntityRenderState, EntityModel<PlayerEntityRenderState>> context) {
        super(context);
    }
}
