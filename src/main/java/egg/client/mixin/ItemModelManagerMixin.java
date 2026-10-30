package egg.client.mixin;

import egg.client.cosmetic.CosmeticsManager;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    @Inject(
            method = "updateForLivingEntity",
            at = @At("RETURN")
    )
    private void onUpdateForLivingEntity(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            LivingEntity entity,
            CallbackInfo ci
    ) {
        if (stack != null && stack.isIn(ItemTags.SWORDS)) {
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, entity);
        }
    }

    @Inject(
            method = "updateForNonLivingEntity",
            at = @At("RETURN")
    )
    private void onUpdateForNonLivingEntity(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Entity entity,
            CallbackInfo ci
    ) {
        if (stack != null && stack.isIn(ItemTags.SWORDS)) {
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, null);
        }
    }
}
