package platform.inject.mixin;


import delta.event.CrosshairTargetEvent;
import delta.event.DrawEvent;
import delta.core.EventManager;
import delta.event.HandEvent;
import delta.core.IEvent;
import delta.core.Interface;
import delta.core.InterfaceC0020Opcode;
import delta.event.RatioEvent;
import delta.event.RayTraceEvent;
import delta.event.RemovalsEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public class GameRendererMixin implements Interface {
    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void onGetNightVisionStrength(net.minecraft.entity.LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (delta.core.Delta.h() != null && delta.core.Delta.h().d() != null && delta.core.Delta.h().d().t() != null) {
            delta.module.render.FullBright fullBright = delta.core.Delta.h().d().t().aO();
            if (fullBright != null && fullBright.m()) {
                cir.setReturnValue(1.0f);
            }
        }
    }

    @Inject(method = {"renderHand"}, at = {@At("HEAD")})
    private void preRenderHand(float tickDelta, boolean renderHand, Matrix4f matrix4f, CallbackInfo ci) {
        EventManager.a((IEvent) new HandEvent(HandEvent.a.PRE));
    }

    @Inject(method = {"renderHand"}, at = {@At("TAIL")})
    private void postRenderHand(float tickDelta, boolean renderHand, Matrix4f matrix4f, CallbackInfo ci) {
        EventManager.a((IEvent) new HandEvent(HandEvent.a.POST));
    }

    @Inject(method = {"tiltViewWhenHurt"}, at = {@At("HEAD")}, cancellable = true)
    private void tiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.HURT_CAM);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.cancel();
        }
    }

    @Redirect(method = {"findCrosshairTarget"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"), require = 0)
    private EntityHitResult findCrosshairTarget(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate, double maxDistance) {
        RayTraceEvent event = new RayTraceEvent();
        EventManager.a((IEvent) event);
        if (event.a()) {
            return null;
        }
        return ProjectileUtil.raycast(entity, min, max, box, predicate, maxDistance);
    }

    @Inject(method = {"findCrosshairTarget"}, at = {@At("RETURN")}, cancellable = true, require = 0)
    private void onFindCrosshairTarget(Entity camera, double blockInteractionRange, double entityInteractionRange, float tickProgress, CallbackInfoReturnable<HitResult> cir) {
        CrosshairTargetEvent event = new CrosshairTargetEvent(tickProgress);
        EventManager.a((IEvent) event);
        if (event.a()) {
            cir.setReturnValue(event.c());
        }
    }

    @ModifyArg(method = {"getBasicProjectionMatrix"}, at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1)
    private float getBasicProjectionMatrix(float ratio) {
        RatioEvent event = new RatioEvent(ratio);
        EventManager.a((IEvent) event);
        return event.b();
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"), require = 0)
    private float killEffectZoomFov(float original) {
        float scale = delta.module.render.KillEffect.getKillZoomFovScale();
        return scale == 1.0f ? original : original * scale;
    }

    @ModifyReturnValue(method = "getBasicProjectionMatrix", at = @At("RETURN"), require = 0)
    private Matrix4f killEffectCameraShake(Matrix4f original) {
        if (original == null) {
            return original;
        }
        float shake = delta.module.render.KillEffect.getKillShakeDegrees();
        if (shake == 0.0f) {
            return original;
        }
        return new Matrix4f(original).rotateZ((float) Math.toRadians(shake));
    }
}
