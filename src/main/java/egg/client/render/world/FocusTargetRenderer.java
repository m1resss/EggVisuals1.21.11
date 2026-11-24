package egg.client.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.EntityMaskRenderer;
import egg.client.render.shader.FrameBufferPool;
import egg.client.render.shader.FullscreenPass;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.render.shader.Pipelines;
import egg.client.render.shader.ProjectionUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.OptionalInt;

public final class FocusTargetRenderer {
    private static final long ENTER_TIME = 220_000_000L;
    private static final long EXIT_TIME = 170_000_000L;
    private LivingEntity current;
    private SimpleFramebuffer sceneCopy;
    private SimpleFramebuffer targetMask;
    private long entered;
    private long seen;

    public void render(LivingEntity target, MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!GlStateSnapshot.isOpenGl() || client.player == null) return;
        long now = System.nanoTime();
        if (target != null && target != current) {
            current = target;
            entered = now;
            seen = now;
        }
        if (current == null) return;
        if (!current.isAlive() || current.isRemoved() || current.getEntityWorld() != client.world
                || EntityUtil.isNakedInvisible(current)) {
            clear();
            return;
        }

        boolean visible = ProjectionUtil.isVisible(current.getBoundingBox()) && client.player.canSee(current);
        if (target == current && visible) {
            if (now - seen >= EXIT_TIME) entered = now;
            seen = now;
        }
        if (now - seen >= EXIT_TIME) {
            if (target == null) clear();
            return;
        }
        float alpha = smooth((now - entered) / (float) ENTER_TIME) * (1f - smooth((now - seen) / (float) EXIT_TIME));
        if (alpha < 0.008f) return;

        Framebuffer frame = client.getFramebuffer();
        if (frame.getDepthAttachmentView() == null || frame.getColorAttachmentView() == null
                || (RenderSystem.outputColorTextureOverride != null && RenderSystem.outputColorTextureOverride != frame.getColorAttachmentView())
                || (RenderSystem.outputDepthTextureOverride != null && RenderSystem.outputDepthTextureOverride != frame.getDepthAttachmentView())) return;

        sceneCopy = resize(sceneCopy, "Egg focus scene", frame, false);
        targetMask = resize(targetMask, "Egg focus target", frame, true);
        try (var ignored = FullscreenPass.guardState()) {
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            FullscreenPass.blitUnflipped(Pipelines.COLOR_COPY, sceneCopy, frame.getColorAttachmentView(), sampler, 0);
            EntityMaskRenderer.renderEntities(targetMask, matrices, camera, tickDelta, List.of(current));
            compose(frame, Math.round(255f * alpha));
        }
    }

    private void compose(Framebuffer frame, int alpha) {
        try (var allocator = new BufferAllocator(128)) {
            var mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int color = alpha << 24 | 0xFFFFFF;
            mesh.vertex(-1, 1, 0).texture(0, 1).color(color);
            mesh.vertex(-1, -1, 0).texture(0, 0).color(color);
            mesh.vertex(1, -1, 0).texture(1, 0).color(color);
            mesh.vertex(1, 1, 0).texture(1, 1).color(color);
            try (var built = mesh.end()) {
                var vertices = FrameBufferPool.createBuffer(() -> "Egg focus vertices", built.getBuffer());
                var indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
                var indexBuffer = indices.getIndexBuffer(built.getDrawParameters().indexCount());
                var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
                try (var pass = RenderSystem.getDevice().createCommandEncoder()
                        .createRenderPass(() -> "Egg focus composite", frame.getColorAttachmentView(), OptionalInt.empty())) {
                    pass.setPipeline(Pipelines.TARGET_FOCUS);
                    pass.bindTexture("Sampler0", sceneCopy.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler1", frame.getDepthAttachmentView(), sampler);
                    pass.bindTexture("Sampler2", targetMask.getColorAttachmentView(), sampler);
                    pass.bindTexture("Sampler3", targetMask.getDepthAttachmentView(), sampler);
                    pass.setVertexBuffer(0, vertices);
                    pass.setIndexBuffer(indexBuffer, indices.getIndexType());
                    pass.drawIndexed(0, 0, built.getDrawParameters().indexCount(), 1);
                }
            }
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
        current = null;
        if (sceneCopy != null) {
            sceneCopy.delete();
            sceneCopy = null;
        }
        if (targetMask != null) {
            targetMask.delete();
            targetMask = null;
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
