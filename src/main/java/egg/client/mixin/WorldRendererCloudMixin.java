package egg.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import egg.client.module.render.WorldTweaks;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererCloudMixin {
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getCloudRenderModeValue()Lnet/minecraft/client/option/CloudRenderMode;")
    )
    private CloudRenderMode enableCustomClouds(GameOptions gameOptions, Operation<CloudRenderMode> operation) {
        CloudRenderMode cloudRenderMode = (CloudRenderMode) operation.call(gameOptions);
        WorldTweaks wt = WorldTweaks.getInstance();
        if (wt != null && wt.isEnabled() && wt.isCustomCloudsEnabled()) {
            if ("Выкл".equals(wt.getCloudMode())) {
                return CloudRenderMode.OFF;
            }
            if (cloudRenderMode == CloudRenderMode.OFF) {
                gameOptions.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
                return CloudRenderMode.FANCY;
            }
        }
        return cloudRenderMode;
    }

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/option/CloudRenderMode;Lnet/minecraft/util/math/Vec3d;JFIF)V"
        )
    )
    private void ambienceClouds(
        WorldRenderer worldRenderer, FrameGraphBuilder frameGraphBuilder, CloudRenderMode cloudRenderMode,
        Vec3d vec3d, long l, float f, int i, float f2, Operation<Void> operation
    ) {
        WorldTweaks wt = WorldTweaks.getInstance();
        if (wt != null && wt.isEnabled() && wt.isCustomCloudsEnabled()) {
            if ("Выкл".equals(wt.getCloudMode())) {
                return;
            }
            double speed = wt.getCloudSpeed();
            double d = ((double) l + f) * speed;
            long l2 = (long) Math.floor(d);
            int color = wt.getCloudColor();
            float height = (float) wt.getCloudHeight();
            operation.call(worldRenderer, frameGraphBuilder, cloudRenderMode, vec3d, l2, (float) (d - l2), color, height);
        } else {
            operation.call(worldRenderer, frameGraphBuilder, cloudRenderMode, vec3d, l, f, i, f2);
        }
    }
}
