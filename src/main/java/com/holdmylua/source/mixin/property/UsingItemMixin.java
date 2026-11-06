package com.holdmylua.source.mixin.property;

import com.holdmylua.source.global.GlobalsStorage;
import net.minecraft.client.render.item.property.bool.UsingItemProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UsingItemProperty.class})
public class UsingItemMixin {
    @Inject(method={"test"}, at={@At(value="RETURN")}, cancellable=true)
    private void swapProperty(ItemStack stack, ClientWorld world, LivingEntity entity, int seed, ItemDisplayContext displayContext, CallbackInfoReturnable<Boolean> cir) {
        if (egg.client.module.render.HMI.getInstance() == null || !egg.client.module.render.HMI.getInstance().isEnabled()) {
            return;
        }
        if (entity != null && entity.getActiveItem() == stack && GlobalsStorage.usingItem.containsKey(stack.getItem().toString())) {
            cir.setReturnValue((Boolean)GlobalsStorage.usingItem.get(stack.getItem().toString()));
        }
    }
}
