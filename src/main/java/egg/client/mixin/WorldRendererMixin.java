package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.HitSparks;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.Trajectories;
import egg.client.module.render.WorldTweaks;
import egg.client.module.render.TargetESP;
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
            method = "renderBlockDamage",
            at = @At("RETURN")
    )
    private void onRenderBlockDamage(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            WorldRenderState state,
            CallbackInfo ci
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && wt.isEnabled()) {
            wt.renderWorld3D(immediate, camPos);
            wt.renderWeatherPass(immediate, camPos);
        }

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

        HitSparks hitSparks = ModuleManager.getInstance().getModule(HitSparks.class);
        if (hitSparks != null && hitSparks.isEnabled()) {
            hitSparks.render(immediate, camPos);
        }
        TargetESP targetESP = ModuleManager.getInstance().getModule(TargetESP.class);
        if (targetESP != null && targetESP.isEnabled()) {
            targetESP.renderWorld3D(immediate, camPos);
        }

        immediate.draw();
    }
}
