package egg.client.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.FullscreenPass;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.render.shader.ProjectionUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

public final class FocusTargetRenderer {
    private static final long ENTER_TIME = 220_000_000L;
    private static final long EXIT_TIME = 170_000_000L;
    private final Vector4f clip = new Vector4f();
    private LivingEntity current;
    private SimpleFramebuffer copy;
    private long entered;
    private long seen;

    public void render(LivingEntity target, Vec3d camera, float tickDelta) {
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
        if (target == current && client.player.canSee(current)) seen = now;
        if (now - seen >= EXIT_TIME) {
            if (target == null) clear();
            return;
        }

        Vec3d feet = current.getLerpedPos(tickDelta);
        double distance = feet.add(0, current.getHeight() * 0.55, 0).distanceTo(camera);
        float alpha = 0.77f * smooth((now - entered) / (float) ENTER_TIME) * (1f - smooth((now - seen) / (float) EXIT_TIME));
        alpha *= smooth((float) (distance - 0.9) / 0.8f);
        if (alpha < 0.008f) return;

        Framebuffer frame = client.getFramebuffer();
        if (frame.getDepthAttachmentView() == null || frame.getColorAttachment() == null
                || (RenderSystem.outputColorTextureOverride != null && RenderSystem.outputColorTextureOverride != frame.getColorAttachmentView())
                || (RenderSystem.outputDepthTextureOverride != null && RenderSystem.outputDepthTextureOverride != frame.getDepthAttachmentView())) return;

        Box box = current.getBoundingBox().offset(feet.subtract(current.getEntityPos())).expand(0.11);
        Bounds bounds = project(box, frame.textureWidth, frame.textureHeight);
        if (bounds == null) return;
        compose(frame, bounds, Math.round(255f * alpha));
    }

    private Bounds project(Box box, int width, int height) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float farDepth = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            if (!ProjectionUtil.worldToClip((corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ, clip)) return null;
            float x = clip.x / clip.w;
            float y = clip.y / clip.w;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            farDepth = Math.max(farDepth, clip.z / clip.w);
        }
        if (farDepth >= 1f) return null;
        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float rx = Math.min(0.57f, Math.max((maxX - minX) * 0.9f, (maxY - minY) * height / width * 0.48f));
        float ry = Math.min(0.82f, (maxY - minY) * 0.72f);
        float left = centerX - rx;
        float right = centerX + rx;
        float bottom = centerY - ry;
        float top = centerY + ry;
        if (left >= 1 || right <= -1 || bottom >= 1 || top <= -1 || rx * width < 3 || ry * height < 3) return null;
        return new Bounds(left, right, bottom, top, farDepth);
    }

    private void compose(Framebuffer frame, Bounds bounds, int alpha) {
        if (copy == null) {
            copy = new SimpleFramebuffer("Egg focus copy", frame.textureWidth, frame.textureHeight, false);
        } else if (copy.textureWidth != frame.textureWidth || copy.textureHeight != frame.textureHeight) {
            copy.resize(frame.textureWidth, frame.textureHeight);
        }
        try (var state = FullscreenPass.guardState(); var allocator = new BufferAllocator(128)) {
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            FullscreenPass.blitUnflipped(Pipelines.COLOR_COPY, copy, frame.getColorAttachmentView(), sampler, 0);

            var mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int color = alpha << 24 | 0xFFFFFF;
            mesh.vertex(bounds.left, bounds.top, bounds.depth).texture(0, 1).color(color);
            mesh.vertex(bounds.left, bounds.bottom, bounds.depth).texture(0, 0).color(color);
            mesh.vertex(bounds.right, bounds.bottom, bounds.depth).texture(1, 0).color(color);
            mesh.vertex(bounds.right, bounds.top, bounds.depth).texture(1, 1).color(color);
            MeshDrawUtil.drawWithTexture(Pipelines.TARGET_FOCUS, mesh.end(), copy.getColorAttachmentView(), sampler);
        }
    }

    public void clear() {
        current = null;
        if (copy != null) {
            copy.delete();
            copy = null;
        }
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private record Bounds(float left, float right, float bottom, float top, float depth) {}
}
