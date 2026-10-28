package platform.inject.mixin;

import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.RemovalsEvent;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({StatusEffectFogModifier.class})
public class BackgroundRendererMixin {
    @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
    private void onShouldApply(CameraSubmersionType submersionType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living) {
            if (living.hasStatusEffect(StatusEffects.BLINDNESS)) {
                RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.BLINDNESS);
                EventManager.a((IEvent) event);
                if (event.a()) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            if (living.hasStatusEffect(StatusEffects.DARKNESS)) {
                RemovalsEvent event2 = new RemovalsEvent(RemovalsEvent.a.DARKNESS);
                EventManager.a((IEvent) event2);
                if (event2.a()) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
