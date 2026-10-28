package platform.inject.mixin;


import delta.render.Animations;
import delta.event.CameraPositionEvent;
import delta.core.Delta;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.event.RemovalsEvent;
import delta.event.RotationEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import platform.inject.accessors.CameraAccessor;

@Mixin({Camera.class})
public abstract class CameraMixin {

    @Inject(method = {"update"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", shift = At.Shift.AFTER)})
    private void onUpdate(net.minecraft.world.World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        Camera camera = (Camera)(Object) this;
        CameraPositionEvent posEvent = new CameraPositionEvent(camera.getCameraPos());
        EventManager.a((IEvent) posEvent);
        if (posEvent.a() && posEvent.b() != null) {
            Vec3d pos = posEvent.b();
            ((CameraAccessor) this).invokeSetPos(pos.getX(), pos.getY(), pos.getZ());
        }
        RotationEvent event = new RotationEvent(focusedEntity.getYaw(tickDelta), focusedEntity.getPitch(tickDelta));
        EventManager.a((IEvent) event);
        if (event.a != focusedEntity.getYaw(tickDelta) || event.b != focusedEntity.getPitch(tickDelta)) {
            ((CameraAccessor) this).invokeSetRotation(event.a, event.b);
        }
    }

    @Inject(method = {"getSubmersionType"}, at = {@At("HEAD")}, cancellable = true)
    private void getSubmergedFluidState(CallbackInfoReturnable<CameraSubmersionType> ci) {
        RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.WATER);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.setReturnValue(CameraSubmersionType.NONE);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
    private float modifyCameraDistance(float desiredCameraDistance) {
        Animations animations = Delta.h().d().t().Q();
        if (animations != null && animations.m() && animations.q().a("Смена перспективы").c().booleanValue()) {
            return desiredCameraDistance * animations.u().c();
        }
        return desiredCameraDistance;
    }
}
