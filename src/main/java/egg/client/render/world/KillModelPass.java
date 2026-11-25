package egg.client.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.FullscreenPass;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.render.shader.Pipelines;
import egg.client.render.shader.ProjectionUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class KillModelPass {
    private final Matrix4f invViewProj = new Matrix4f();
    private SimpleFramebuffer sceneCopy;
    private SimpleFramebuffer targetMask;
    private DynamicUniformStorage<ModelUniform> uniforms;
    private BufferAllocator quadAllocator;
    private EntityRenderState renderingModel;

    public boolean isRendering(EntityRenderState state) {
        return state != null && state == renderingModel;
    }

    void render(MinecraftClient client, EntityRenderState model, Vec3d position, float height,
                MatrixStack matrices, Camera camera, Vec3d camPos, RenderPipeline pipeline,
                float front, float progress, float age, float opacity) {
        render(client, model, position, height, matrices, camera, camPos, pipeline, front, progress, age, opacity, 0.0f, 1.0f, 1.0f);
    }

    void render(MinecraftClient client, EntityRenderState model, Vec3d position, float height,
                MatrixStack matrices, Camera camera, Vec3d camPos, RenderPipeline pipeline,
                float front, float progress, float age, float opacity,
                float spinRad, float scaleXZ, float scaleY) {
        if (model == null || !GlStateSnapshot.isOpenGl()) return;
        Framebuffer frame = client.getFramebuffer();
        if (frame == null || frame.getColorAttachmentView() == null || frame.getDepthAttachmentView() == null
                || (RenderSystem.outputColorTextureOverride != null && RenderSystem.outputColorTextureOverride != frame.getColorAttachmentView())
                || (RenderSystem.outputDepthTextureOverride != null && RenderSystem.outputDepthTextureOverride != frame.getDepthAttachmentView())) {
            return;
        }

        sceneCopy = resize(sceneCopy, "Egg kill scene", frame, false);
        targetMask = resize(targetMask, "Egg kill mask", frame, true);

        try (var ignored = FullscreenPass.guardState()) {
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            FullscreenPass.blitUnflipped(Pipelines.COLOR_COPY, sceneCopy, frame.getColorAttachmentView(), sampler, 0);
            renderCapturedModel(client, model, position, height, matrices, camera, camPos, spinRad, scaleXZ, scaleY);
            compose(frame, position.subtract(camPos), height, pipeline, front, progress, age, opacity);
        }
    }

    private void renderCapturedModel(MinecraftClient client, EntityRenderState model, Vec3d position, float height,
                                     MatrixStack matrices, Camera camera, Vec3d camPos,
                                     float spinRad, float scaleXZ, float scaleY) {
        EntityRenderManager manager = client.getEntityRenderDispatcher();
        RenderDispatcher dispatcher = client.gameRenderer.getEntityRenderDispatcher();
        CameraRenderState camState = new CameraRenderState();
        camState.orientation = camera.getRotation();

        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                targetMask.getColorAttachment(), 0, targetMask.getDepthAttachment(), 1.0);
        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = targetMask.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = targetMask.getDepthAttachmentView();
        renderingModel = model;
        try {
            Vec3d offset = position.subtract(camPos);
            if (spinRad != 0.0f || scaleXZ != 1.0f || scaleY != 1.0f) {
                float pivotY = height * 0.52f;
                matrices.push();
                matrices.translate(offset.x, offset.y + pivotY, offset.z);
                if (spinRad != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotation(spinRad));
                matrices.scale(scaleXZ, scaleY, scaleXZ);
                matrices.translate(0.0, -pivotY, 0.0);
                manager.render(model, camState, 0.0, 0.0, 0.0, matrices, dispatcher.getQueue());
                matrices.pop();
            } else {
                manager.render(model, camState, offset.x, offset.y, offset.z, matrices, dispatcher.getQueue());
            }
            dispatcher.render();
            client.getBufferBuilders().getEntityVertexConsumers().draw();
        } finally {
            renderingModel = null;
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
            snapshot.restore();
        }
    }

    private void compose(Framebuffer frame, Vec3d rel, float height, RenderPipeline pipeline,
                         float front, float progress, float age, float opacity) {
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("EggKillModel", 112, 16);
        uniforms.clear();
        ProjectionUtil.getInverseViewProjection(invViewProj);
        GpuBufferSlice uniformSlice = uniforms.write(new ModelUniform(
                invViewProj,
                (float) rel.x, (float) rel.y, (float) rel.z, height,
                front, progress, age, opacity,
                ProjectionUtil.isZeroToOneDepth() ? 1.0f : 0.0f
        ));

        if (quadAllocator == null) quadAllocator = new BufferAllocator(256);
        try {
            BufferBuilder mesh = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            mesh.vertex(-1, 1, 0).texture(0, 1).color(255, 255, 255, 255);
            mesh.vertex(-1, -1, 0).texture(0, 0).color(255, 255, 255, 255);
            mesh.vertex(1, -1, 0).texture(1, 0).color(255, 255, 255, 255);
            mesh.vertex(1, 1, 0).texture(1, 1).color(255, 255, 255, 255);
            try (var built = mesh.end()) {
                var vertices = FrameBufferPool.createBuffer(() -> "Egg kill model quad", built.getBuffer());
                var indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                var indexBuffer = indices.getIndexBuffer(built.getDrawParameters().indexCount());
                var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Egg kill model composite",
                        frame.getColorAttachmentView(),
                        OptionalInt.empty(),
                        frame.getDepthAttachmentView(),
                        OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    pass.bindTexture("Sampler0", sceneCopy.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler1", targetMask.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler2", targetMask.getDepthAttachmentView(), sampler);
                    pass.setUniform("KillModelUniforms", uniformSlice);
                    pass.setVertexBuffer(0, vertices);
                    pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                    pass.drawIndexed(0, 0, built.getDrawParameters().indexCount(), 1);
                }
            }
        } finally {
            quadAllocator.reset();
        }
    }

    private static SimpleFramebuffer resize(SimpleFramebuffer target, String name, Framebuffer source, boolean depth) {
        if (target == null) return new SimpleFramebuffer(name, source.textureWidth, source.textureHeight, depth);
        if (target.textureWidth != source.textureWidth || target.textureHeight != source.textureHeight) {
            target.resize(source.textureWidth, source.textureHeight);
        }
        return target;
    }

    public void clear() {
        if (sceneCopy != null) {
            sceneCopy.delete();
            sceneCopy = null;
        }
        if (targetMask != null) {
            targetMask.delete();
            targetMask = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (quadAllocator != null) {
            quadAllocator.close();
            quadAllocator = null;
        }
    }

    private record ModelUniform(Matrix4fc invViewProj, float relX, float relY, float relZ, float height,
                                 float front, float progress, float age, float opacity,
                                 float zeroToOneDepth) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            for (float v : invViewProj.get(new float[16])) {
                buffer.putFloat(v);
            }
            buffer.putFloat(relX).putFloat(relY).putFloat(relZ).putFloat(height);
            buffer.putFloat(front).putFloat(progress).putFloat(age).putFloat(opacity);
            buffer.putFloat(zeroToOneDepth).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }
    }
}
