package egg.client.mixin;

import egg.client.module.misc.Fun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher", remap = false)
public abstract class LocomotionBlockAnimationsMixin {
    @Inject(method = "blockEntityIsEnabledInConfig", at = @At("HEAD"), cancellable = true)
    private static void egg$controlBlockAnimations(CallbackInfoReturnable<Boolean> cir) {
        if (!Fun.usesLocomotion()) {
            cir.setReturnValue(false);
        }
    }
}
