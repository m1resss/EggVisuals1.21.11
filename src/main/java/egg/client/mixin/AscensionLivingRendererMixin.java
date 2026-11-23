package egg.client.mixin;

import egg.client.module.render.KillEffect;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class AscensionLivingRendererMixin {
    @Inject(method = "setupTransforms", at = @At("HEAD"), cancellable = true)
    private void egg$keepAscensionUpright(LivingEntityRenderState state, MatrixStack matrices,
                                          float bodyYaw, float baseScale, CallbackInfo ci) {
        if (!KillEffect.isRenderingAscension(state)) return;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        ci.cancel();
    }
}
