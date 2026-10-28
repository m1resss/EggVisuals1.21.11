package platform.inject.mixin;


import static delta.core.Interface.aM_;

import delta.event.BoundingBoxEvent;
import delta.core.Delta;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import delta.event.RemovalsEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityMixin {

    @Shadow
    private Box boundingBox;

    @Inject(method = {"getBoundingBox"}, at = {@At("HEAD")}, cancellable = true)
    public final void getBoundingBox(CallbackInfoReturnable<Box> cir) {
        BoundingBoxEvent event = new BoundingBoxEvent(this.boundingBox, (Entity)(Object) this);
        EventManager.a((IEvent) event);
        cir.setReturnValue(event.b());
    }

    @Inject(method = {"isGlowing"}, at = {@At("RETURN")}, cancellable = true)
    private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (delta.module.render.ShaderESP.shouldOutline((Entity)(Object) this)) {
            cir.setReturnValue(true);
            return;
        }
        if (((Boolean) cir.getReturnValue()).booleanValue()) {
            RemovalsEvent event = new RemovalsEvent(RemovalsEvent.a.GLOW);
            EventManager.a((IEvent) event);
            if (event.a()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = {"getTeamColorValue"}, at = {@At("HEAD")}, cancellable = true)
    private void onGetTeamColorValue(CallbackInfoReturnable<Integer> cir) {
        if (delta.module.render.ShaderESP.shouldOutline((Entity)(Object) this)) {
            cir.setReturnValue(delta.module.render.ShaderESP.getColor());
        }
    }

    @ModifyExpressionValue(method = {"move"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isLogicalSideForUpdatingMovement()Z")})
    private boolean move(boolean original) {
        if ((Entity)(Object) this == Interface.aM_.player) {
            return false;
        }
        return original;
    }
}
