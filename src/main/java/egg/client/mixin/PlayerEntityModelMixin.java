package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.misc.Fun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin extends BipedEntityModel<PlayerEntityRenderState> {

    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("RETURN"))
    private void onSetAngles(PlayerEntityRenderState state, CallbackInfo ci) {
        Fun fun = ModuleManager.getInstance().getModule(Fun.class);
        if (fun != null && fun.isEnabled() && fun.isRaisedArms()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isSelf = (mc.player != null && (state.id == mc.player.getId() || state.id <= 0));
            boolean isAlone = (mc.world != null && mc.world.getPlayers().size() <= 1);

            if (!isSelf || isAlone) {
                float basePitch = -(float) Math.PI;

                float armBob = MathHelper.cos(state.limbSwingAnimationProgress * 0.6662f) * 0.35f * state.limbSwingAmplitude;
                float armSway = MathHelper.sin(state.limbSwingAnimationProgress * 0.6662f) * 0.15f * state.limbSwingAmplitude;

                float idlePitch = MathHelper.sin(state.age * 0.067f) * 0.04f;
                float idleRoll = MathHelper.cos(state.age * 0.09f) * 0.03f;

                this.rightArm.pitch = basePitch + armBob + idlePitch;
                this.leftArm.pitch = basePitch - armBob + idlePitch;

                this.rightArm.yaw = armSway * 0.5f;
                this.leftArm.yaw = armSway * 0.5f;

                this.rightArm.roll = 0.12f + armSway * 0.2f + idleRoll;
                this.leftArm.roll = -0.12f + armSway * 0.2f - idleRoll;

                if (state.handSwingProgress > 0.0f) {
                    float swing = state.handSwingProgress;
                    float swingFactor = MathHelper.sin(swing * (float) Math.PI);
                    if (state.mainArm == Arm.RIGHT) {
                        this.rightArm.pitch += swingFactor * 1.1f;
                    } else {
                        this.leftArm.pitch += swingFactor * 1.1f;
                    }
                }

                this.rightSleeve.resetTransform();
                this.leftSleeve.resetTransform();
                this.rightSleeve.pitch = 0.0f;
                this.rightSleeve.yaw = 0.0f;
                this.rightSleeve.roll = 0.0f;
                this.leftSleeve.pitch = 0.0f;
                this.leftSleeve.yaw = 0.0f;
                this.leftSleeve.roll = 0.0f;
            }
        }
    }
}
