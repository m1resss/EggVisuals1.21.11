package egg.client.mixin;

import egg.client.module.render.SwingAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void egg$modifyHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && (Object) this == mc.player) {
            SwingAnimation swing = SwingAnimation.getInstance();
            if (swing != null && swing.isEnabled()) {
                cir.setReturnValue(swing.modifySwingDuration(cir.getReturnValue()));
            }
        }
    }
}
