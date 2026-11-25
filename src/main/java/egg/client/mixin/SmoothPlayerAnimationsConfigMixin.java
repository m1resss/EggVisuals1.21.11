package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import egg.client.module.misc.Fun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.huaxiaosheng.spapacks.client.ModConfig", remap = false)
public abstract class SmoothPlayerAnimationsConfigMixin {
    @ModifyExpressionValue(method = "on", at = @At(value = "FIELD",
            target = "Lcom/huaxiaosheng/spapacks/client/ModConfig;enabled:Z"))
    private static boolean egg$controlAnimations(boolean enabled) {
        return Fun.usesSmoothAnimations();
    }
}
