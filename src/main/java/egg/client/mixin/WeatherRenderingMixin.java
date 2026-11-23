package egg.client.mixin;

import egg.client.module.render.WorldTweaks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void egg$precipitation(VertexConsumerProvider vertices, Vec3d pos, WeatherRenderState state, CallbackInfo ci) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks != null && tweaks.hasWeatherEffects()) ci.cancel();
    }

    @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true)
    private void egg$weatherEffects(ClientWorld world, Camera camera, int ticks, ParticlesMode mode, int distance, CallbackInfo ci) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks != null && tweaks.hasWeatherEffects()) ci.cancel();
    }
}
