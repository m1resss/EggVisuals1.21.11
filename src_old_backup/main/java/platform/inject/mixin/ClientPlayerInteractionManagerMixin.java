package platform.inject.mixin;

import delta.event.AttackEvent;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerInteractionManager.class})
public class ClientPlayerInteractionManagerMixin implements Interface {
    @Inject(method = {"attackEntity"}, at = {@At("HEAD")}, cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        AttackEvent event = new AttackEvent(target);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.cancel();
        }
    }

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(net.minecraft.util.math.BlockPos pos, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (delta.core.Delta.h() != null && delta.core.Delta.h().d() != null && delta.core.Delta.h().d().t() != null) {
            delta.module.render.BreakBlock breakBlock = delta.core.Delta.h().d().t().getBreakBlock();
            if (breakBlock != null && breakBlock.m()) {
                breakBlock.notifyBlockBroken(pos);
            }
        }
    }
}
