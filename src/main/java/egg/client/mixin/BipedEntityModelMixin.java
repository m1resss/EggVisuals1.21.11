package egg.client.mixin;

import egg.client.render.BedrockRunState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("RETURN"))
    private void egg$shortenRunningStride(BipedEntityRenderState state, CallbackInfo ci) {
        if (state instanceof BedrockRunState running && running.egg$isBedrockRunning()) {
            leftLeg.pitch *= 0.13f;
            rightLeg.pitch *= 0.13f;
        }
    }
}
