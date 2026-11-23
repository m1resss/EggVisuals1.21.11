package egg.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.EggClient;
import egg.client.module.ModuleManager;
import egg.client.module.render.KillEffect;
import egg.client.module.render.TotemPop;
import egg.client.render.NaturalMotionBlurRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void egg$naturalMotionBlurHead(
            ObjectAllocator objectAllocator,
            RenderTickCounter renderTickCounter,
            boolean renderOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        NaturalMotionBlurRenderer.INSTANCE.onWorldRenderHead(objectAllocator, camera, positionMatrix, projectionMatrix);
    }

    @WrapOperation(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean egg$hideAscensionOriginal(List<EntityRenderState> states, Object state, Operation<Boolean> original,
                                               @Local Entity entity) {
        KillEffect effect = ModuleManager.getInstance().getModule(KillEffect.class);
        if (effect != null && effect.shouldHideAscensionOriginal(entity)) return false;
        return original.call(states, state);
    }

    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void egg$naturalMotionBlurBeforeEntities(
            MatrixStack matrices,
            WorldRenderState state,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci
    ) {
        NaturalMotionBlurRenderer.INSTANCE.onBeforeEntities();
    }

    @Inject(method = "pushEntityRenders", at = @At("RETURN"))
    private void egg$queueAscensionModels(MatrixStack matrices, WorldRenderState state,
                                           OrderedRenderCommandQueue queue, CallbackInfo ci) {
        KillEffect effect = ModuleManager.getInstance().getModule(KillEffect.class);
        if (effect != null) effect.queueAscensionModels(matrices, queue, state.cameraRenderState);
    }


    @Inject(method = "render", at = @At("TAIL"))
    private void egg$naturalMotionBlurTail(
            ObjectAllocator objectAllocator,
            RenderTickCounter renderTickCounter,
            boolean renderOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        NaturalMotionBlurRenderer.INSTANCE.onWorldRenderTail();
    }

    @Inject(
            method = "renderBlockDamage",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderBlockDamage(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            WorldRenderState state,
            CallbackInfo ci
    ) {
        egg.client.module.render.BreakBlock bb = egg.client.module.ModuleManager.getInstance().getModule(egg.client.module.render.BreakBlock.class);
        if (bb != null && bb.isEnabled() && bb.shouldHideVanilla()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V",
                    shift = Shift.AFTER
            )
    )
    private void onWorldRenderPostClouds(
            ObjectAllocator objectAllocator,
            RenderTickCounter renderTickCounter,
            boolean flag,
            Camera camera,
            Matrix4f matrix4f,
            Matrix4f matrix4f2,
            Matrix4f matrix4f3,
            GpuBufferSlice gpuBufferSlice,
            Vector4f vector4f,
            boolean flag2,
            CallbackInfo callbackInfo
    ) {
        TotemPop totemPop = ModuleManager.getInstance().getModule(TotemPop.class);
        if (totemPop != null) totemPop.renderSouls(camera, gpuBufferSlice);
        EggClient.renderWorld3DPostClouds(renderTickCounter, camera);
    }
}
