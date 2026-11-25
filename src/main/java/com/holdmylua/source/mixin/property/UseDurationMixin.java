package com.holdmylua.source.mixin.property;

import com.holdmylua.source.global.GlobalsStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.property.numeric.UseDurationProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UseDurationProperty.class})
public class UseDurationMixin {
    @Inject(method={"getValue"}, at={@At(value="RETURN")}, cancellable=true)
    private void swapProperty(ItemStack stack, ClientWorld world, HeldItemContext context, int seed, CallbackInfoReturnable<Float> cir) {
        if (!egg.client.module.render.HMI.usesAnimations()) {
            return;
        }
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getActiveItem() == stack && GlobalsStorage.useDuration.containsKey(stack.getItem().toString())) {
            cir.setReturnValue(Float.valueOf(Float.parseFloat(GlobalsStorage.useDuration.get(stack.getItem().toString()).toString())));
        }
    }
}
