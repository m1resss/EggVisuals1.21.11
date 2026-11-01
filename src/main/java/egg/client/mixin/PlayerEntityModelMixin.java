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

            // Apply to opponents (and to self in singleplayer testing)
            if (!isSelf || isAlone) {
                float basePitch = -1.5707963f; // ~ -90 degrees forward

                // Running & walking bobbing and sway in sync with stride
                float armBob = MathHelper.cos(state.limbSwingAnimationProgress * 0.6662f) * 0.25f * state.limbSwingAmplitude;
                float armSway = MathHelper.sin(state.limbSwingAnimationProgress * 0.6662f) * 0.10f * state.limbSwingAmplitude;

                // Subtle idle breathing when stationary
                float idlePitch = MathHelper.sin(state.age * 0.067f) * 0.04f;
                float idleRoll = MathHelper.cos(state.age * 0.09f) * 0.04f + 0.04f;

                this.rightArm.pitch = basePitch + armBob + idlePitch;
                this.leftArm.pitch = basePitch - armBob + idlePitch;

                this.rightArm.yaw = -0.05f + armSway;
                this.leftArm.yaw = 0.05f + armSway;

                this.rightArm.roll = idleRoll;
                this.leftArm.roll = -idleRoll;

                // Attack swing integration
                if (state.handSwingProgress > 0.0f) {
                    float swing = state.handSwingProgress;
                    float swingFactor = MathHelper.sin(swing * (float) Math.PI);
                    if (state.mainArm == Arm.RIGHT) {
                        this.rightArm.pitch -= swingFactor * 0.75f;
                    } else {
                        this.leftArm.pitch -= swingFactor * 0.75f;
                    }
                }

                // Synchronize outer 3D clothing layers
                this.rightSleeve.pitch = this.rightArm.pitch;
                this.rightSleeve.yaw = this.rightArm.yaw;
                this.rightSleeve.roll = this.rightArm.roll;
                this.leftSleeve.pitch = this.leftArm.pitch;
                this.leftSleeve.yaw = this.leftArm.yaw;
                this.leftSleeve.roll = this.leftArm.roll;
            }
        }
    }
}
