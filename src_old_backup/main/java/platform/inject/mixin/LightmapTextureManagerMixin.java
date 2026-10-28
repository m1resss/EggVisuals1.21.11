package platform.inject.mixin;

import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.RemovalsEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LightmapTextureManager.class})
public class LightmapTextureManagerMixin {
    @ModifyReturnValue(method = {"getDarknessFactor", "getDarkness"}, at = {@At("RETURN")}, require = 0)
    private float onGetDarknessFactor(float original) {
        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.DARKNESS);
        EventManager.a((IEvent) event);
        if (event.a()) {
            return 0.0f;
        }
        return original;
    }
}
