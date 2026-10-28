package platform.inject.mixin;

import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({WorldRenderer.class})
public class WorldRendererMixin implements Interface {
    @Inject(
        method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("TAIL"),
        require = 0
    )
    private void onRenderLevelTail(
        ObjectAllocator allocator,
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        Matrix4f mat1,
        Matrix4f mat2,
        Matrix4f mat3,
        GpuBufferSlice fogBuffer,
        Vector4f fogColor,
        boolean thickFog,
        CallbackInfo ci
    ) {
        MatrixStack matrixStack = new MatrixStack();
        if (camera != null) {
            matrixStack.multiply(camera.getRotation().conjugate(new org.joml.Quaternionf()));
        }
        EventManager.a((IEvent) new DrawEvent(matrixStack, tickCounter.getTickProgress(false), DrawEvent.a.D3D));
    }
}
