package egg.client.mixin;

import egg.client.render.shader.AmbienceRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingAmbienceMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void egg$ambienceSkyColor(ClientWorld clientWorld, float f, Camera camera, SkyRenderState skyRenderState, CallbackInfo callbackInfo) {
        AmbienceRenderer.setSkyColor(skyRenderState.skyColor, skyRenderState.sunAngle);
    }
}
