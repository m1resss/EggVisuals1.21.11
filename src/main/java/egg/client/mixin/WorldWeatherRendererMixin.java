package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.render.world.WorldWeatherEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldWeatherRendererMixin {
    @Shadow @Final private DefaultFramebufferSet framebufferSet;

    @Inject(method = "render", at = @At("HEAD"))
    private void egg$beginWeather(ObjectAllocator allocator, RenderTickCounter ticks, boolean outline, Camera camera,
                                  Matrix4f position, Matrix4f projection, Matrix4f frustum,
                                  GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        WorldWeatherEffects.begin(projection.m11());
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/FramePass;setRenderer(Ljava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void egg$weatherSky(FrameGraphBuilder graph, Camera camera, GpuBufferSlice fog, CallbackInfo ci) {
        WorldWeatherEffects.appendSky(MinecraftClient.getInstance(), graph, framebufferSet, camera);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V"))
    private void egg$weatherPost(ObjectAllocator allocator, RenderTickCounter ticks, boolean outline, Camera camera,
                                 Matrix4f position, Matrix4f projection, Matrix4f frustum, GpuBufferSlice fog,
                                 Vector4f fogColor, boolean sky, CallbackInfo ci, @Local FrameGraphBuilder graph) {
        WorldWeatherEffects.appendWorld(MinecraftClient.getInstance(), graph, framebufferSet, camera);
    }

    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void egg$weatherTerrain(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue commands, CallbackInfo ci) {
        WorldWeatherEffects.captureTerrain(MinecraftClient.getInstance());
    }
}
