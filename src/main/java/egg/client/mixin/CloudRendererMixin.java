package egg.client.mixin;

import egg.client.module.render.WorldTweaks;
import egg.client.render.shader.AmbienceRenderer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void egg$volumetricClouds(int i, CloudRenderMode cloudRenderMode, float f, Vec3d vec3d, long l, float f2, CallbackInfo callbackInfo) {
        WorldTweaks wt = WorldTweaks.getInstance();
        if (wt != null && wt.isVolumetricClouds()) {
            callbackInfo.cancel();
            AmbienceRenderer.INSTANCE.renderClouds(i, f, vec3d, l, f2);
        }
    }
}
