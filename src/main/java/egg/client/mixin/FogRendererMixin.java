package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import egg.client.module.render.WorldTweaks;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
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
        if (wt != null && wt.isEnabled() && (wt.isCustomFogColorEnabled() || wt.isFogTransparencyEnabled())) {
            if (camera != null && camera.getSubmersionType() == CameraSubmersionType.NONE) {
                Vector4f custom = wt.getCustomFogColor(cir.getReturnValue());
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }

    @Inject(
        method = "applyFog",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;"
        )
    )
    private void onApplyFogDistance(Camera camera, int viewDistance, RenderTickCounter tickCounter, float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir, @Local FogData fogData) {
        WorldTweaks wt = WorldTweaks.getInstance();
        if (wt != null && wt.isEnabled() && wt.isCustomFogDistanceEnabled()) {
            if (camera != null && camera.getSubmersionType() == CameraSubmersionType.NONE) {
                wt.applyFogDistance(fogData, (float) (viewDistance * 16));
            }
        }
    }
}
