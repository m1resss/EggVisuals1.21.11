package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        NoRender noRender = ModuleManager.getInstance().getModule(NoRender.class);
        if (noRender != null && noRender.isEnabled() && noRender.getFire().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void onRenderFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        NoRender noRender = ModuleManager.getInstance().getModule(NoRender.class);
        if (noRender != null && noRender.isEnabled() && noRender.getTotem().isEnabled()) {
            ci.cancel();
        }
    }
}
