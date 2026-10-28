package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.SmoothEat;
import egg.client.module.render.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

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
