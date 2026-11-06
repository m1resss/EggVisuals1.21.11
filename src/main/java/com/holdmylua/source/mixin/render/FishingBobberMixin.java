package com.holdmylua.source.mixin.render;

import com.holdmylua.source.LuaTestHMI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.FishingBobberEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={FishingBobberEntityRenderer.class})
public abstract class FishingBobberMixin {
    private EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
    float DEG_TO_RAD = (float)Math.PI / 180;

    @Inject(method={"getHandPos"}, at={@At(value="HEAD")}, cancellable=true)
    private void getHandPos(PlayerEntity player, float f, float tickProgress, CallbackInfoReturnable<Vec3d> cir) {
        float m = 960.0f / (float)((Integer)this.dispatcher.gameOptions.getFov().getValue()).intValue();
        int i = FishingBobberEntityRenderer.getArmHoldingRod((PlayerEntity)player) == Arm.RIGHT ? 1 : -1;
        Matrix4f matrix = FishingBobberEntityRenderer.getArmHoldingRod((PlayerEntity)player) == Arm.RIGHT ? LuaTestHMI.matricesMain : LuaTestHMI.matricesOff;
        matrix.translate(0.0f, 0.0f, 0.0f);
        Vector4f tipPos = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        tipPos.mul((Matrix4fc)matrix);
        Vec3d worldPos = new Vec3d((double)tipPos.x(), (double)tipPos.y(), (double)tipPos.z());
        worldPos.multiply((double)m);
        if (this.dispatcher.gameOptions.getPerspective().isFirstPerson() && player == MinecraftClient.getInstance().player) {
            Vec3d vec3d = this.dispatcher.camera.getProjection().getPosition(0.7f, 0.0f).multiply((double)m).add(worldPos);
            cir.setReturnValue(this.dispatcher.camera.getCameraPos().add(vec3d));
        }
    }

    private void getHandPose(PlayerEntity player, float f, float tickProgress, CallbackInfoReturnable<Vec3d> cir) {
        float m = 960.0f / (float)((Integer)this.dispatcher.gameOptions.getFov().getValue()).intValue();
        int i = FishingBobberEntityRenderer.getArmHoldingRod((PlayerEntity)player) == Arm.RIGHT ? 1 : -1;
        Matrix4f matrix = FishingBobberEntityRenderer.getArmHoldingRod((PlayerEntity)player) == Arm.RIGHT ? LuaTestHMI.matricesMain : LuaTestHMI.matricesOff;
        matrix.translate(0.0f, 0.0f, 0.0f);
        Vector4f tipPos = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        tipPos.mul((Matrix4fc)matrix);
        Vec3d worldPos = new Vec3d((double)tipPos.x(), (double)tipPos.y(), (double)tipPos.z());
        worldPos.multiply((double)m);
        if (this.dispatcher.gameOptions.getPerspective().isFirstPerson() && player == MinecraftClient.getInstance().player) {
            Vec3d vec3d = this.dispatcher.camera.getProjection().getPosition(0.7f, 0.0f).multiply((double)m).add(worldPos);
            cir.setReturnValue(this.dispatcher.camera.getCameraPos().add(vec3d));
        }
    }
}
