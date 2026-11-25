package egg.client.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.redlance.dima_dencep.mods.rrls.utils.OverlayHelper", remap = false)
public abstract class RrlsStartupOverlayMixin {
    @Inject(method = "lookupState", at = @At("RETURN"), cancellable = true)
    private static void egg$showStartupOverlay(Screen screen, boolean reloading, CallbackInfoReturnable<Enum<?>> cir) {
        if (reloading) return;
        for (Enum<?> state : cir.getReturnValue().getDeclaringClass().getEnumConstants()) {
            if (state.name().equals("DEFAULT")) {
                cir.setReturnValue(state);
                return;
            }
        }
    }
}
