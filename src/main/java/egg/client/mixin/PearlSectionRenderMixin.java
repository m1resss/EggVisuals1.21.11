package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import egg.client.render.world.PearlCameraRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.SectionRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionRenderState.class)
public abstract class PearlSectionRenderMixin {
    @ModifyExpressionValue(method = "renderSection", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BlockRenderLayerGroup;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private Framebuffer egg$previewTarget(Framebuffer original) {
        Framebuffer target = PearlCameraRenderer.getActiveTarget();
        return target == null ? original : target;
    }
}