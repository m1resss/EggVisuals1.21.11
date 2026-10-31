package egg.client.mixin;

import egg.client.render.shader.AmbienceRenderer;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, priority = 900)
public class WorldRendererReflectionMixin {
    @Inject(
        method = "method_62214",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/SectionRenderState;renderSection(Lnet/minecraft/client/render/BlockRenderLayerGroup;Lnet/minecraft/client/gl/GpuSampler;)V",
            ordinal = 1
        )
    )
    private void egg$ambienceBeforeTranslucent(CallbackInfo callbackInfo) {
        if (AmbienceRenderer.isReflectionsEnabled()) {
            AmbienceRenderer.INSTANCE.captureOpaque(BlockRenderLayerGroup.TRANSLUCENT.getFramebuffer());
        }
    }

    @Inject(
        method = "method_62214",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/SectionRenderState;renderSection(Lnet/minecraft/client/render/BlockRenderLayerGroup;Lnet/minecraft/client/gl/GpuSampler;)V",
            ordinal = 1,
            shift = Shift.AFTER
        )
    )
    private void egg$ambienceAfterTranslucent(CallbackInfo callbackInfo) {
        if (AmbienceRenderer.isReflectionsEnabled()) {
            AmbienceRenderer.INSTANCE.captureWorldDepth(BlockRenderLayerGroup.TRANSLUCENT.getFramebuffer());
        }
    }
}
