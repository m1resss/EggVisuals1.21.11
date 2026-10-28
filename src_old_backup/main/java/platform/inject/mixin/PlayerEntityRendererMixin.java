package platform.inject.mixin;

import delta.core.Delta;
import delta.module.render.Globals;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntityRenderer.class})
public abstract class PlayerEntityRendererMixin {

    @Inject(method = {"renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at = {@At("HEAD")}, cancellable = true, require = 0)
    private void renderLabelIfPresent(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (state.invisible || Delta.h().d().t().aa().r()) {
            ci.cancel();
        }
    }

    @ModifyVariable(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    private Text modifyLabelText(Text original, PlayerEntityRenderState state) {
        Globals globals = Globals.getInstance();
        if (globals != null && globals.m() && globals.isRenderIn3D() && globals.isGlobalUser(state)) {
            return globals.format3DLabel(original);
        }
        return original;
    }
}
