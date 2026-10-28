package egg.client.mixin;

import egg.client.module.render.WorldTweaks;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        WorldTweaks wt = WorldTweaks.getInstance();
        if (wt != null && wt.isEnabled() && wt.isCustomFogColorEnabled()) {
            if (camera != null && camera.getSubmersionType() == CameraSubmersionType.NONE) {
                Vector4f custom = wt.getCustomFogColor(cir.getReturnValue());
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }
}
