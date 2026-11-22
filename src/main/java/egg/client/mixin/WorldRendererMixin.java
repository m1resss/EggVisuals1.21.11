package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.EggClient;
import egg.client.module.ModuleManager;
import egg.client.module.render.PearlPredict;
import egg.client.render.NaturalMotionBlurRenderer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.SectionRenderState;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow private BuiltChunkStorage chunks;
    @Shadow private GpuSampler terrainSampler;
    @Shadow protected abstract SectionRenderState renderBlockLayers(Matrix4fc view, double x, double y, double z);

    @Unique private ObjectArrayList<ChunkBuilder.BuiltChunk> egg$previewSections;

    @ModifyExpressionValue(method = "renderBlockLayers", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/WorldRenderer;builtChunks:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ChunkBuilder.BuiltChunk> egg$selectPreviewSections(ObjectArrayList<ChunkBuilder.BuiltChunk> original) {
        return egg$previewSections == null ? original : egg$previewSections;
    }

    @Unique
    private boolean egg$renderPearlTerrain(Matrix4f view, Matrix4f projection, Vec3d position) {
        if (chunks == null || terrainSampler == null) return false;

        Frustum frustum = new Frustum(view, projection);
        frustum.setPosition(position.x, position.y, position.z);
        ObjectArrayList<ChunkBuilder.BuiltChunk> sections = new ObjectArrayList<>();
        for (ChunkBuilder.BuiltChunk chunk : chunks.chunks) {
            if (chunk.getBoundingBox().squaredMagnitude(position) <= 96 * 96 && frustum.isVisible(chunk.getBoundingBox())) {
                sections.add(chunk);
            }
        }
        sections.sort(Comparator.comparingDouble(chunk -> chunk.getBoundingBox().squaredMagnitude(position)));
        egg$previewSections = sections;
        try {
            SectionRenderState state = renderBlockLayers(view, position.x, position.y, position.z);
            if (state.drawsPerLayer().values().stream().allMatch(List::isEmpty)) return false;
            state.renderSection(BlockRenderLayerGroup.OPAQUE, terrainSampler);
            state.renderSection(BlockRenderLayerGroup.TRANSLUCENT, terrainSampler);
            state.renderSection(BlockRenderLayerGroup.TRIPWIRE, terrainSampler);
            return true;
        } finally {
            egg$previewSections = null;
        }
    }

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

    @Inject(method = "pushEntityRenders", at = @At("HEAD"))
    private void egg$naturalMotionBlurBeforeEntities(
            MatrixStack matrices,
            WorldRenderState state,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci
    ) {
        NaturalMotionBlurRenderer.INSTANCE.onBeforeEntities();
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
        PearlPredict pearlPredict = ModuleManager.getInstance().getModule(PearlPredict.class);
        if (pearlPredict != null && pearlPredict.isEnabled()) {
            pearlPredict.capture(renderTickCounter, fogBuffer, fogColor, this::egg$renderPearlTerrain);
        }
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
        EggClient.renderWorld3DPostClouds(renderTickCounter, camera);
    }
}
