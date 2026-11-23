package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.module.ModuleManager;
import egg.client.module.render.PearlPredict;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SectionRenderState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

@Mixin(WorldRenderer.class)
public abstract class PearlWorldRendererMixin {
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

    @Inject(method = "render", at = @At("TAIL"))
    private void egg$capturePearlCamera(ObjectAllocator allocator, RenderTickCounter ticks, boolean renderOutline,
                                        Camera camera, Matrix4f view, Matrix4f projection, Matrix4f frustum,
                                        GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PearlPredict pearlPredict = ModuleManager.getInstance().getModule(PearlPredict.class);
        if (pearlPredict != null && pearlPredict.isEnabled()) {
            pearlPredict.capture(ticks, fog, fogColor, this::egg$renderPearlTerrain);
        }
    }
}
