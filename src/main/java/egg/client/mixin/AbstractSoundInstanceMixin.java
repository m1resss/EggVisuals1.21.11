package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.player.SoundReducer;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void onGetVolume(CallbackInfoReturnable<Float> cir) {
        SoundReducer reducer = ModuleManager.getInstance().getModule(SoundReducer.class);
        if (reducer != null && reducer.isEnabled()) {
            SoundInstance inst = (SoundInstance) (Object) this;
            if (inst.getId() != null && reducer.shouldReduce(inst.getId().getPath())) {
                cir.setReturnValue(reducer.getReducedVolume(cir.getReturnValueF()));
            }
        }
    }
}
