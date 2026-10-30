package egg.client.mixin;

import egg.client.cosmetic.CosmeticsManager;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    @Inject(
            method = "clearAndUpdate",
            at = @At("RETURN")
    )
    private void onClearAndUpdate(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            World world,
            HeldItemContext heldItemContext,
            int seed,
            CallbackInfo ci
    ) {
        if (CosmeticsManager.isSword(stack)) {
            LivingEntity entity = (heldItemContext instanceof LivingEntity le) ? le : null;
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, entity, displayContext);
        }
    }

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
        if (CosmeticsManager.isSword(stack)) {
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, entity, displayContext);
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
        if (CosmeticsManager.isSword(stack)) {
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, null, displayContext);
        }
    }
}
