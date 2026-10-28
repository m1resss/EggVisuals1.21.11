package platform.inject.mixin;

import delta.event.AmbienceEvent;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.RemovalsEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({WeatherRendering.class})
public class WeatherRenderingMixin {
    @Inject(method = {"renderPrecipitation"}, at = {@At("HEAD")}, cancellable = true)
    private void onRenderPrecipitation(net.minecraft.client.render.VertexConsumerProvider consumers, Vec3d pos, net.minecraft.client.render.state.WeatherRenderState state, CallbackInfo ci) {
        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.WEATHER);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = {"addParticlesAndSound"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getRainGradient(F)F")})
    private float onPrecipitationParticles(float original) {
        AmbienceEvent.d event = new AmbienceEvent.d(AmbienceEvent.d.a.PRECIPITATION_PARTICLES, original);
        EventManager.a((IEvent) event);
        return event.c();
    }

    @ModifyReturnValue(method = {"getPrecipitationAt"}, at = {@At(value = "RETURN", ordinal = 1)})
    private Biome.Precipitation onGetPrecipitationAt(Biome.Precipitation original, World world, BlockPos pos) {
        AmbienceEvent.d event = new AmbienceEvent.d(AmbienceEvent.d.a.PRECIPITATION, original);
        EventManager.a((IEvent) event);
        return event.d();
    }
}
