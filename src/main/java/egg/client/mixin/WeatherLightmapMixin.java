package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import egg.client.module.render.WorldTweaks;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapTextureManager.class)
public abstract class WeatherLightmapMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")))
    private Object egg$worldGamma(Object value) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        return tweaks != null && value instanceof Double gamma ? Math.max(gamma, tweaks.gamma()) : value;
    }
}
