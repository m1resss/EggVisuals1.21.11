package platform.inject.mixin;

import delta.core.Delta;
import delta.core.EventManager;
import delta.event.HandAnimationEvent;
import delta.event.HandViewEvent;
import delta.core.IEvent;
import delta.util.Look;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import delta.module.render.SmoothEat;
import delta.module.render.HandsShader;

@Mixin({HeldItemRenderer.class})
public class HeldItemRendererMixin {
    @Redirect(method = {"renderItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F"), require = 0)
    private float redirectPitch(ClientPlayerEntity instance, float tickDelta) {
        return Look.c();
    }

    @Redirect(method = {"renderItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F"), require = 0)
    private float redirectYaw(ClientPlayerEntity instance, float tickDelta) {
        return Look.b();
    }

    @Redirect(method = {"renderItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F", ordinal = 0), require = 0)
    private float redirectPitchLerp(float delta, float start, float end) {
        return Look.c();
    }

    @Redirect(method = {"renderItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F", ordinal = 1), require = 0)
    private float redirectYawLerp(float delta, float start, float end) {
        return Look.b();
    }

    @WrapOperation(method = {"renderFirstPersonItem"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V", ordinal = 2)}, require = 0)
    private void wrapHandAnimation(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm, Operation<Void> original, @Local(ordinal = 0, argsOnly = true) Hand hand) {
        HandAnimationEvent event = new HandAnimationEvent(matrices, hand, swingProgress, armX);
        EventManager.a((IEvent) event);
        if (!event.a()) {
            original.call(new Object[]{instance, Float.valueOf(swingProgress), Float.valueOf(equipProgress), matrices, Integer.valueOf(armX), arm});
        }
    }

    @Inject(method = {"renderFirstPersonItem"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER)})
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue renderCommands, int light, CallbackInfo ci) {
        EventManager.a((IEvent) new HandViewEvent(matrices, stack, hand));
    }

    @ModifyConstant(method = {"applyEatOrDrinkTransformation"}, constant = {@Constant(floatValue = 0.1f)})
    private float modifyEatShake(float constant) {
        if (Delta.h() != null && Delta.h().d() != null && Delta.h().d().t() != null) {
            SmoothEat smoothEat = Delta.h().d().t().smoothEat();
            if (smoothEat != null && smoothEat.m()) {
                return smoothEat.getStrength();
            }
        }
        return constant;
    }

    @ModifyExpressionValue(method = {"renderFirstPersonItem"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isInvisible()Z")})
    private boolean renderFirstPersonItem(boolean original) {
        if (Delta.h() != null && Delta.h().d() != null && Delta.h().d().t() != null) {
            HandsShader handsShader = Delta.h().d().t().aT();
            if (handsShader != null && handsShader.m()) {
                return false;
            }
        }
        return original;
    }
}
