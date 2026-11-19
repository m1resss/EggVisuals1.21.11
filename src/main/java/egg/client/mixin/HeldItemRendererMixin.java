package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.SmoothEat;
import egg.client.module.render.SwingAnimation;
import egg.client.module.render.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    protected abstract void swingArm(float swingProgress, MatrixStack matrices, int armSign, Arm arm);

    @Unique
    private float egg$currentEquipProgress;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void onRenderFirstPersonItemHead(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue renderCommands, int light, CallbackInfo ci) {
        this.egg$currentEquipProgress = equipProgress;
        if (egg.client.cosmetic.watch.WatchAnimator.getInstance().renderWatchInspection((HeldItemRenderer)(Object)this, player, hand, matrices, renderCommands, light)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V",
                    ordinal = 2
            )
    )
    private void egg$redirectSwingArm(HeldItemRenderer instance, float swingProgress, MatrixStack matrices, int armSign, Arm arm) {
        SwingAnimation swing = SwingAnimation.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (swing != null && swing.isEnabled() && mc.player != null && (swing.isBothHands() || arm == mc.player.getMainArm())) {
            swing.applySwing(matrices, swingProgress, this.egg$currentEquipProgress, arm);
        } else {
            this.swingArm(swingProgress, matrices, armSign, arm);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue renderCommands, int light, CallbackInfo ci) {
        ViewModel vm = ModuleManager.getInstance().getModule(ViewModel.class);
        if (vm != null && vm.isEnabled()) {
            vm.applyTransform(matrices, hand);
        }
    }

    @ModifyConstant(method = "applyEatOrDrinkTransformation", constant = @Constant(floatValue = 0.1f))
    private float modifyEatShake(float constant) {
        SmoothEat smoothEat = ModuleManager.getInstance().getModule(SmoothEat.class);
        if (smoothEat != null && smoothEat.isEnabled()) {
            return smoothEat.getStrength();
        }
        return constant;
    }
}
