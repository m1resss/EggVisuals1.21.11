package egg.client.mixin;

import egg.client.module.misc.Fun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "com.trainguy9512.locomotion.config.LocomotionConfig", remap = false)
public abstract class LocomotionConfigMixin {
    @Unique private static volatile Field egg$firstPersonPlayer;
    @Unique private static Field egg$enableRenderer;

    @Inject(method = "data", at = @At("RETURN"))
    private void egg$controlFirstPersonRenderer(CallbackInfoReturnable<Object> cir) throws ReflectiveOperationException {
        Object data = cir.getReturnValue();
        if (egg$firstPersonPlayer == null) {
            Field firstPersonPlayer = data.getClass().getField("firstPersonPlayer");
            egg$enableRenderer = firstPersonPlayer.getType().getField("enableRenderer");
            egg$firstPersonPlayer = firstPersonPlayer;
        }
        egg$enableRenderer.setBoolean(egg$firstPersonPlayer.get(data), Fun.usesLocomotion());
    }
}
