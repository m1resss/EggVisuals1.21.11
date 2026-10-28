package platform.inject.mixin;


import delta.event.ConsumeEvent;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.mixin.ILivingEntity;
import delta.event.JumpEvent;
import delta.event.PushEvent;
import delta.event.WillLandEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import platform.inject.accessors.LivingEntityGravityInvoker;
import platform.inject.invokers.EntityMovementInvoker;
import net.minecraft.util.Hand;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin implements ILivingEntity {

    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void delta_fullBrightNightVision(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect == net.minecraft.entity.effect.StatusEffects.NIGHT_VISION && (Object) this == net.minecraft.client.MinecraftClient.getInstance().player) {
            if (delta.core.Delta.h() != null && delta.core.Delta.h().d() != null && delta.core.Delta.h().d().t() != null) {
                delta.module.render.FullBright fullBright = delta.core.Delta.h().d().t().aO();
                if (fullBright != null && fullBright.m()) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = {"consumeItem"}, at = {@At("HEAD")})
    private void consumeItem(CallbackInfo ci) {
        LivingEntity livingEntity = (LivingEntity)(Object) this;
        if ((livingEntity instanceof ClientPlayerEntity) && livingEntity.isUsingItem()) {
            EventManager.a((IEvent) new ConsumeEvent(livingEntity.getActiveItem().copy()));
        }
    }

    @Inject(method = {"jump"}, at = {@At("HEAD")}, cancellable = true)
    public void jump(CallbackInfo ci) {
        LivingEntity livingEntity = (LivingEntity)(Object) this;
        if (livingEntity instanceof ClientPlayerEntity) {
            JumpEvent event = new JumpEvent(livingEntity);
            EventManager.a((IEvent) event);
            if (event.a()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = {"isPushable"}, at = {@At("HEAD")}, cancellable = true)
    private void removePushFromEntity(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity)(Object) this;
        if (entity instanceof ClientPlayerEntity) {
            PushEvent event = new PushEvent(PushEvent.a.ENTITIES);
            EventManager.a((IEvent) event);
            if (event.a()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = {"travelMidAir"}, at = {@At("TAIL")})
    private void travelMidAir(Vec3d movementInput, CallbackInfo ci) {
        EntityMovementInvoker entityMovementInvoker = (EntityMovementInvoker)(Object) this;
        if (entityMovementInvoker instanceof ClientPlayerEntity) {
            double gravity = ((LivingEntityGravityInvoker) (Object) this).getGravityInvoker();
            double predictedNextYDelta = (((net.minecraft.entity.Entity) (Object) this).getVelocity().y - gravity) * 0.98d;
            Vec3d verticalAttempt = new Vec3d(0.0d, predictedNextYDelta, 0.0d);
            Vec3d allowedVertical = entityMovementInvoker.getAdjustMovementForCollisions(verticalAttempt);
            boolean willLand = predictedNextYDelta < 0.0d && allowedVertical.y != predictedNextYDelta;
            EventManager.a((IEvent) new WillLandEvent(willLand));
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"), require = 0)
    private void delta_onDeath(DamageSource source, CallbackInfo ci) {
        delta.module.render.KillEffect.notifyEntityDied((LivingEntity) (Object) this, source);
    }

    @Inject(method = "setHealth", at = @At("HEAD"), require = 0)
    private void delta_setHealth(float health, CallbackInfo ci) {
        if (health <= 0.0f) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.getHealth() > 0.0f) {
                delta.module.render.KillEffect.notifyEntityDied(self, null);
            }
        }
    }
}
