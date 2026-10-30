package egg.client.mixin;

import egg.client.cosmetic.CosmeticsManager;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {

    private static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isIn(ItemTags.SWORDS)) return true;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_sword") || path.contains("sword");
    }

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
        if (isSword(stack)) {
            LivingEntity entity = (heldItemContext instanceof LivingEntity le) ? le : null;
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, entity);
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
        if (isSword(stack)) {
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
        if (isSword(stack)) {
            CosmeticsManager.getInstance().applySwordRenderState(renderState, stack, null);
        }
    }
}
