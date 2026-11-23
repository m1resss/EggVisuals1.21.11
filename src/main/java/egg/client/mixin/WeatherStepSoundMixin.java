package egg.client.mixin;

import egg.client.module.render.WorldTweaks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class WeatherStepSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void egg$weatherStep(BlockPos pos, BlockState state, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || !entity.getEntityWorld().isClient() || !tweaks.hasWeatherEffects()) return;
        if (!entity.getEntityWorld().isSkyVisible(pos.up()) || !state.getFluidState().isEmpty()) return;

        WorldTweaks.Frame weather = tweaks.frame();
        SoundEvent event;
        float gain;
        float pitch = 0.94f + entity.getRandom().nextFloat() * 0.12f;
        if (weather.snow() > 0.2f) {
            event = SoundEvents.BLOCK_SNOW_STEP;
            gain = 0.2f;
        } else if (weather.rain() > 0.3f) {
            event = SoundEvents.BLOCK_WET_GRASS_STEP;
            gain = 0.22f;
        } else if (weather.mist() > 0.9f) {
            event = state.getSoundGroup().getStepSound();
            gain = 0.08f;
            pitch *= 0.78f;
        } else {
            return;
        }
        entity.playSound(event, state.getSoundGroup().getVolume() * gain, state.getSoundGroup().getPitch() * pitch);
        ci.cancel();
    }
}
