package platform.inject.mixin;

import delta.event.AmbienceEvent;
import delta.core.EventManager;
import delta.core.IEvent;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private void onApplyFog(net.minecraft.client.render.Camera camera, int viewDistance, net.minecraft.client.render.RenderTickCounter tickCounter, float tickDelta, net.minecraft.client.world.ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        Vector4f col = cir.getReturnValue();
        if (col != null) {
            AmbienceEvent.a event = new AmbienceEvent.a(col.x, col.y, col.z, col.w);
            EventManager.a((IEvent) event);
            if (event.a()) {
                col.set(event.b(), event.c(), event.d(), event.e());
                cir.setReturnValue(col);
            }
        }
    }
}
