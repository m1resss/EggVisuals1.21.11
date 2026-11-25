package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import egg.client.module.render.KillEffect;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(LivingEntityRenderer.class)
public abstract class AscensionLivingRendererMixin {
    @Unique
    private static final Function<Identifier, RenderLayer> egg$iceFractureModel = Util.memoize(texture ->
            RenderLayer.of("egg_ice_fracture_model", RenderSetup.builder(Pipelines.ICE_FRACTURE_MODEL)
                    .texture("Sampler0", texture).useLightmap().build()));

    @Shadow public abstract Identifier getTexture(LivingEntityRenderState state);

    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer egg$ascensionLayer(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
                                           boolean visible, boolean translucent, boolean outline, Operation<RenderLayer> original) {
        if (KillEffect.isRenderingKillModel(state) && visible) return egg$iceFractureModel.apply(getTexture(state));
        if (KillEffect.isRenderingAscension(state)) return RenderLayers.entityTranslucent(getTexture(state));
        return original.call(renderer, state, visible, translucent, outline);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;mix(II)I"))
    private int egg$ascensionOpacity(int color, @Local(argsOnly = true) LivingEntityRenderState state) {
        if (!KillEffect.isRenderingAscension(state)) return color;
        return (color & 0xFFFFFF) | 0x8C000000;
    }
    @Inject(method = "setupTransforms", at = @At("HEAD"), cancellable = true)
    private void egg$keepAscensionUpright(LivingEntityRenderState state, MatrixStack matrices,
                                          float bodyYaw, float baseScale, CallbackInfo ci) {
        if (!KillEffect.isRenderingUpright(state)) return;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        ci.cancel();
    }
}
