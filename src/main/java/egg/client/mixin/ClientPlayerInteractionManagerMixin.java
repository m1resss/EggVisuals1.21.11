package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.combat.NoFriendDamage;
import egg.client.module.combat.ShiftTAP;
import egg.client.module.render.Hitmarker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        egg.client.module.render.Interface iface = ModuleManager.getInstance().getModule(egg.client.module.render.Interface.class);
        if (iface != null && iface.isEnabled()) {
            iface.onCombatHit();
        }
        NoFriendDamage noFriend = ModuleManager.getInstance().getModule(NoFriendDamage.class);
        if (noFriend != null && noFriend.shouldCancelAttack(target)) {
            ci.cancel();
            return;
        }

        ShiftTAP shiftTap = ModuleManager.getInstance().getModule(ShiftTAP.class);
        if (shiftTap != null && shiftTap.isEnabled()) {
            shiftTap.onAttack(target);
        }

        Hitmarker hitmarker = ModuleManager.getInstance().getModule(Hitmarker.class);
        if (hitmarker != null && hitmarker.isEnabled()) {
            hitmarker.onAttack(target);
        }

        egg.client.module.render.HitSparks hitSparks = ModuleManager.getInstance().getModule(egg.client.module.render.HitSparks.class);
        if (hitSparks != null && hitSparks.isEnabled()) {
            hitSparks.onAttack(player, target);
        }

        /*
        egg.client.module.render.KillEffect killEffect = ModuleManager.getInstance().getModule(egg.client.module.render.KillEffect.class);
        if (killEffect != null && killEffect.isEnabled()) {
            killEffect.recordAttack(target);
        }

        if (target instanceof PlayerEntity victim && victim != player) {
            egg.client.cases.CaseManager.getInstance().trackAttack(victim);
        }
        */
    }
}
