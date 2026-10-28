package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.Trajectories;
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
public class WeatherRenderingMixin {

    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void onRenderPrecipitation(VertexConsumerProvider vertexConsumers, Vec3d cameraPos, WeatherRenderState state, CallbackInfo ci) {
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && wt.isEnabled()) {
            boolean cancelVanilla = wt.renderWeatherPass(vertexConsumers, cameraPos);
            if (cancelVanilla) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true)
    private void onAddParticlesAndSound(ClientWorld world, Camera camera, int ticks, ParticlesMode particlesMode, int i, CallbackInfo ci) {
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && wt.isEnabled() && wt.shouldOverrideVanillaSound()) {
            ci.cancel(); // Cancel vanilla rain sound so custom rainsound.mp3 plays!
        }
    }
}
