package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.WorldTweaks;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        World self = (World) (Object) this;
        if (self.isClient()) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomTime()) {
                cir.setReturnValue(wt.getTimeOfDay());
            }
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        World self = (World) (Object) this;
        if (self.isClient()) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomWeather()) {
                cir.setReturnValue(wt.getRainGradient());
            }
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        World self = (World) (Object) this;
        if (self.isClient()) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomWeather()) {
                cir.setReturnValue(wt.getThunderGradient());
            }
        }
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void onIsRaining(CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (self.isClient()) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomWeather()) {
                cir.setReturnValue(wt.isRaining());
            }
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void onIsThundering(CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (self.isClient()) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomWeather()) {
                cir.setReturnValue(wt.isThundering());
            }
        }
    }
}
