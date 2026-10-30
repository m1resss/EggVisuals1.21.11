package egg.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.module.ModuleManager;
import egg.client.module.render.ItemRadius;
import egg.client.module.render.TotemPopConfetti;
import egg.client.module.render.Trajectories;
import egg.client.module.render.WorldTweaks;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Shadow
    private BufferBuilderStorage bufferBuilders;

    @Inject(
            method = "render",
            at = @At("RETURN")
    )
    private void onRenderReturn(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            GpuBufferSlice gpuBufferSlice,
            Vector4f fogColor,
            boolean renderTargetBlockOutline,
            CallbackInfo ci
    ) {
        if (camera == null) return;
        Vec3d camPos = camera.getCameraPos();
        if (this.bufferBuilders == null) return;
        VertexConsumerProvider.Immediate immediate = this.bufferBuilders.getEntityVertexConsumers();
        if (immediate == null) return;

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

        egg.client.module.render.HitSparks hitSparks = ModuleManager.getInstance().getModule(egg.client.module.render.HitSparks.class);
        if (hitSparks != null && hitSparks.isEnabled()) {
            hitSparks.render(immediate, camPos);
        }

        immediate.draw();
    }
}
