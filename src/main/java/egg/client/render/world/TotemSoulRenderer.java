package egg.client.render.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import egg.client.render.shader.FullscreenPass;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TotemSoulRenderer {
    private static final long DURATION = 1_350_000_000L;
    private static final int MAX_SOULS = 6;

    private final List<Soul> souls = new ArrayList<>();
    private SimpleFramebuffer target;

    public void capture(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        EntityRenderState model = client.getEntityRenderDispatcher().getAndUpdateRenderState(player, tickDelta);
        model.displayName = null;
        model.outlineColor = EntityRenderState.NO_OUTLINE;
        model.shadowPieces.clear();
        model.shadowRadius = 0;
        model.leashDatas = null;
        model.onFire = false;
        model.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        if (model instanceof LivingEntityRenderState living) living.hurt = false;

        if (souls.size() >= MAX_SOULS) souls.removeFirst();
        souls.add(new Soul(client.world, new Vec3d(model.x, model.y, model.z), model, System.nanoTime()));
    }

    public void render(Camera camera, GpuBufferSlice fog) {
        if (souls.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        souls.removeIf(soul -> soul.world() != client.world || now - soul.started() >= DURATION);
        if (souls.isEmpty()) return;

        Framebuffer screen = client.getFramebuffer();
        if (screen.getDepthAttachment() == null || screen.textureWidth <= 0 || screen.textureHeight <= 0) return;
        if (target == null) {
            target = new SimpleFramebuffer("egg_totem_soul", screen.textureWidth, screen.textureHeight, true);
        } else if (target.textureWidth != screen.textureWidth || target.textureHeight != screen.textureHeight) {
            target.resize(screen.textureWidth, screen.textureHeight);
        }

        Vec3d cameraPos = camera.getCameraPos();
        CameraRenderState cameraState = new CameraRenderState();
        cameraState.pos = cameraPos;
        cameraState.entityPos = cameraPos;
        cameraState.blockPos = BlockPos.ofFloored(cameraPos);
        cameraState.initialized = camera.isReady();
        cameraState.orientation = camera.getRotation();

        souls.sort(Comparator.comparingDouble((Soul soul) -> soul.origin().squaredDistanceTo(cameraPos)).reversed());
        EntityRenderManager manager = client.getEntityRenderDispatcher();
        RenderDispatcher dispatcher = client.gameRenderer.getEntityRenderDispatcher();
        var consumers = client.getBufferBuilders().getEntityVertexConsumers();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        GpuBufferSlice previousFog = RenderSystem.getShaderFog();
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        GlStateSnapshot glState = GlStateSnapshot.capture();
        consumers.draw();
        try {
            RenderSystem.setShaderFog(fog);
            for (Soul soul : souls) {
                float progress = Math.clamp((now - soul.started()) / (float) DURATION, 0, 1);
                float lift = 1 - (1 - progress) * (1 - progress) * (1 - progress);
                float opacity = 0.84f * smoothstep(progress / 0.12f) * (1 - smoothstep((progress - 0.43f) / 0.57f));
                if (opacity <= 0.004f) continue;

                Vec3d offset = soul.origin().add(0, 0.08 + 2.15 * lift, 0).subtract(cameraPos);
                MatrixStack matrices = new MatrixStack();
                matrices.translate(offset.x, offset.y + 0.85, offset.z);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(10 * lift));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-5 * lift));
                matrices.translate(0, -0.85, 0);

                encoder.clearColorTexture(target.getColorAttachment(), 0);
                target.copyDepthFrom(screen);
                RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
                RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
                EntityRenderState model = soul.model();
                manager.getRenderer(model).render(model, matrices, dispatcher.getQueue(), cameraState);
                dispatcher.render();
                consumers.draw();

                RenderSystem.outputColorTextureOverride = previousColor;
                RenderSystem.outputDepthTextureOverride = previousDepth;
                int tint = (Math.round(opacity * 255) << 24) | 0x62DCFF;
                FullscreenPass.composite(Pipelines.TOTEM_SOUL, screen.getColorAttachmentView(),
                        target.getColorAttachmentView(), sampler, tint);
            }
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.setShaderFog(previousFog);
            glState.restore();
        }
    }

    public void clear() {
        souls.clear();
        if (target != null) {
            target.delete();
            target = null;
        }
    }

    private static float smoothstep(float progress) {
        float t = Math.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record Soul(ClientWorld world, Vec3d origin, EntityRenderState model, long started) {}
}
