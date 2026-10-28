package platform.inject.mixin;


import static delta.core.Interface.aM_;

import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import delta.event.PushEvent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerEntity.class})
public class PlayerEntityMixin {
    @Inject(method = {"isPushedByFluids"}, at = {@At("HEAD")}, cancellable = true)
    private void removePushFromFluids(CallbackInfoReturnable<Boolean> cir) {
        if (((PlayerEntity)(Object) this) == Interface.aM_.player) {
            PushEvent event = new PushEvent(PushEvent.a.FLUIDS);
            EventManager.a((IEvent) event);
            if (event.a()) {
                cir.setReturnValue(false);
            }
        }
    }
}
