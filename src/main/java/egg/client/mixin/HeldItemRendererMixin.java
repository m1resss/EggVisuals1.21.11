package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.SmoothEat;
import egg.client.module.render.SwordAnimations;
import egg.client.module.render.ViewModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    public abstract void renderItem(
        LivingEntity entity,
        ItemStack item,
        ItemDisplayContext displayContext,
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        int light
    );

    @Inject(
        method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderFirstPersonItemHead(
        AbstractClientPlayerEntity player,
        float tickDelta,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        MatrixStack matrices,
        OrderedRenderCommandQueue renderCommands,
        int light,
        CallbackInfo ci
    ) {
        SwordAnimations swordAnim = ModuleManager.getInstance().getModule(SwordAnimations.class);
        if (swordAnim != null && swordAnim.isEnabled() && hand == Hand.MAIN_HAND && swordAnim.isEligibleItem(stack)) {
            Arm arm = player.getMainArm();
            boolean rightArm = arm == Arm.RIGHT;
            int dir = rightArm ? 1 : -1;

            matrices.push();

            // Smooth equip drop/rise animation
            float equipY = -0.6f * (1.0f - equipProgress);
            matrices.translate(0.0f, equipY, 0.0f);

            // ViewModel base offset if active
            ViewModel vm = ModuleManager.getInstance().getModule(ViewModel.class);
            if (vm != null && vm.isEnabled()) {
                vm.applyTransform(matrices, hand);
            }

            // Custom sword animation transforms
            SwordAnimations.Transform t = swordAnim.calculateTransform(swingProgress);

            // Anchor -> Move -> Rotate -> -Anchor
            matrices.translate(dir * t.anchorX, t.anchorY, t.anchorZ);
            matrices.translate(dir * t.moveX, t.moveY, t.moveZ);
            matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(t.rotX),
                (float) Math.toRadians(dir * t.rotY),
                (float) Math.toRadians(dir * t.rotZ)
            ));
            matrices.translate(-dir * t.anchorX, -t.anchorY, -t.anchorZ);

            if (t.scale != 1.0f) {
                matrices.scale(t.scale, t.scale, t.scale);
            }

            // Standard first-person item translation
            matrices.translate(dir * 0.56f, -0.52f, -0.72f);

            if (!stack.isEmpty()) {
                ItemDisplayContext context = rightArm
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                this.renderItem(player, stack, context, matrices, renderCommands, light);
            }

            matrices.pop();
            ci.cancel();
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
