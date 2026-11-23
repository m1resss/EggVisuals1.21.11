package egg.client.mixin;

import egg.client.render.world.PearlCameraRenderer;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass", remap = false)
public abstract class SodiumPearlTargetMixin {
    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void egg$targetPearlFramebuffer(CallbackInfoReturnable<Framebuffer> callback) {
        Framebuffer target = PearlCameraRenderer.getActiveTarget();
        if (target != null) callback.setReturnValue(target);
    }
}
