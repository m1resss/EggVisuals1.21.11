package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.Trajectories;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
            method = "renderTargetBlockOutline",
            at = @At("RETURN")
    )
    private void onRenderTargetBlockOutline(VertexConsumerProvider.Immediate immediate, MatrixStack matrices, boolean bl, WorldRenderState state, CallbackInfo ci) {
        if (bl) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        TotemPopConfetti confetti = ModuleManager.getInstance().getModule(TotemPopConfetti.class);
        if (confetti != null && confetti.isEnabled()) {
            confetti.render(immediate, camPos);
        }

        Trajectories traj = ModuleManager.getInstance().getModule(Trajectories.class);
        if (traj != null && traj.isEnabled()) {
            traj.render(immediate, camPos);
        }

        ItemRadius itemRadius = ModuleManager.getInstance().getModule(ItemRadius.class);
        if (itemRadius != null && itemRadius.isEnabled()) {
            itemRadius.render(immediate, camPos);
        }

        /*
        egg.client.module.render.KillEffect killEffect = ModuleManager.getInstance().getModule(egg.client.module.render.KillEffect.class);
        if (killEffect != null && killEffect.isEnabled()) {
            killEffect.render(immediate, camPos);
        }
        */

        egg.client.module.render.HitSparks hitSparks = ModuleManager.getInstance().getModule(egg.client.module.render.HitSparks.class);
        if (hitSparks != null && hitSparks.isEnabled()) {
            hitSparks.render(immediate, camPos);
        }

        immediate.draw();
    }
}
