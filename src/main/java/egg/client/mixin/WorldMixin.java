package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import egg.client.module.render.WorldTweaks;
import egg.client.module.render.WorldTweaks.Frame;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class WorldMixin {
    @ModifyReturnValue(method = "getTimeOfDay", at = @At("RETURN"))
    private long egg$time(long time) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        return tweaks == null || !((World) (Object) this).isClient() ? time : tweaks.transformTime(time);
    }

    @ModifyReturnValue(method = "getRainGradient", at = @At("RETURN"))
    private float egg$rain(float rain) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || !((World) (Object) this).isClient() || !tweaks.hasWeatherEffects()) return rain;
        Frame frame = tweaks.frame();
        return Math.min(1, frame.rainGradient() + (1 - frame.amount()) * rain);
    }

    @ModifyReturnValue(method = "getThunderGradient", at = @At("RETURN"))
    private float egg$thunder(float thunder) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || !((World) (Object) this).isClient() || !tweaks.hasWeatherEffects()) return thunder;
        Frame frame = tweaks.frame();
        return Math.min(frame.thunder(), frame.rainGradient()) + (1 - frame.amount()) * thunder;
    }
}
