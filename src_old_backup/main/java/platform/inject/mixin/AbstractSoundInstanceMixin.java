package platform.inject.mixin;


import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.SoundEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({AbstractSoundInstance.class})
public abstract class AbstractSoundInstanceMixin {
    @ModifyReturnValue(method = {"getVolume"}, at = {@At("RETURN")})
    private float getVolume(float original) {
        SoundEvent event = new SoundEvent((SoundInstance) this, original);
        EventManager.a((IEvent) event);
        return event.c();
    }
}
