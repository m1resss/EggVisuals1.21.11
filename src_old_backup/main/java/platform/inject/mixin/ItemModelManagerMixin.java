package platform.inject.mixin;

import delta.cosmetic.SkycoreCosmeticsManager;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
    @Inject(method = "updateForLivingEntity(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onUpdateLiving(ItemRenderState renderState, ItemStack stack, ItemDisplayContext transformationMode, LivingEntity entity, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty() && stack.isOf(Items.NETHERITE_SWORD)) {
            if (SkycoreCosmeticsManager.getInstance().applySwordRenderState(renderState, stack, entity)) {
                ci.cancel();
            }
        }
    }
}
