package platform.inject.mixin;


import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.ResizeEvent;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Window.class})
public class WindowMixin {
    @Inject(method = {"setIcon"}, at = {@At("TAIL")})
    private void onSetIcon(CallbackInfo ci) {
        EventManager.a((IEvent) new ResizeEvent());
    }

    @Inject(method = {"onFramebufferSizeChanged"}, at = {@At("TAIL")})
    private void onFramebufferSizeChanged(long window, int width, int height, CallbackInfo ci) {
        EventManager.a((IEvent) new ResizeEvent());
    }
}
