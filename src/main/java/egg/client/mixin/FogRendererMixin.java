package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import egg.client.module.render.WorldTweaks;
import egg.client.render.world.WorldWeatherEffects;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void egg$weatherFogColor(Camera camera, float progress, ClientWorld world, int distance,
                                     float darkness, CallbackInfoReturnable<Vector4f> cir) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks != null && camera.getSubmersionType() == CameraSubmersionType.NONE
                && (tweaks.isEnabled() || tweaks.hasWeatherEffects())) {
            cir.setReturnValue(tweaks.fogColor(cir.getReturnValue(), WorldWeatherEffects.weatherFog(tweaks), tweaks.frame()));
        }
    }

    @Inject(method = "applyFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;"))
    private void egg$weatherFog(Camera camera, int distance, RenderTickCounter ticks, float darkness, ClientWorld world,
                                CallbackInfoReturnable<Vector4f> cir, @Local FogData data, @Local Vector4f color) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks != null && camera.getSubmersionType() == CameraSubmersionType.NONE
                && (tweaks.isEnabled() || tweaks.hasWeatherEffects())) {
            tweaks.applyFog(data, tweaks.frame());
        }
        WorldWeatherEffects.captureFog(data, color);
    }
}
