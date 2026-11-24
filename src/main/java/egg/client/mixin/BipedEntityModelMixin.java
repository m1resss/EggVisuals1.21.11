package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import egg.client.render.BedrockRunState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {
    @ModifyExpressionValue(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;limbSwingAmplitude:F", ordinal = 0))
    private float egg$shortenRunningStride(float amplitude, BipedEntityRenderState state) {
        return amplitude * BedrockRunState.strideScale(state);
    }
}
