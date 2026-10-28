package platform.inject.mixin;

import static delta.core.Interface.aM_;

import delta.core.Delta;
import delta.core.Interface;
import delta.render.ColorUtil;
import delta.render.TargetScanRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import platform.interfaces.TargetScanRenderState;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    public abstract Identifier getTexture(S state);

    @Shadow
    protected M model;

    @Unique
    private T currentEntity;

    @Unique
    private float pitch;

    @Inject(method = {"updateRenderState*"}, at = {@At("HEAD")})
    private void onUpdateRenderState(T entity, S state, float f, CallbackInfo ci) {
        this.currentEntity = entity;
    }

    @Inject(method = {"updateRenderState*"}, at = {@At("TAIL")})
    private void updateRenderState(T entity, S state, float f, CallbackInfo ci) {
        if (entity == Interface.aM_.player) {
            this.pitch = Delta.h().d().k().a().a() ? MathHelper.lerp(0.5f, this.pitch, ((LivingEntityRenderState) state).pitch) : ((LivingEntityRenderState) state).pitch;
            ((LivingEntityRenderState) state).pitch = this.pitch;
        }

        ((TargetScanRenderState) state).delta$setTargetScanTarget(
                TargetScanRenderer.isTarget(entity));
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void delta$submitTargetScan(
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (!((TargetScanRenderState) state).delta$isTargetScanTarget()) {
            return;
        }

        TargetScanRenderer.ScanParams params = TargetScanRenderer.getParams();
        if (params == null) {
            return;
        }

        TargetScanRenderer.submit(
                this.model,
                state,
                matrices,
                vertexConsumers,
                this.getTexture(state),
                params
        );
    }
}
