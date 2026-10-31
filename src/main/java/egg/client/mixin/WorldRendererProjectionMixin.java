package egg.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.render.shader.ProjectionUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererProjectionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void egg$captureMatrices(
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
        ProjectionUtil.updateMatrices(matrix4f, matrix4f2);
    }
}
