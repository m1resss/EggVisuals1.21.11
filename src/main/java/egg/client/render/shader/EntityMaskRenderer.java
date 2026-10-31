package egg.client.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class EntityMaskRenderer {
    private EntityMaskRenderer() {
    }

    public static void renderEntities(SimpleFramebuffer simpleFramebuffer, MatrixStack matrixStack, Camera camera, float f, List<Entity> entities) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        EntityRenderManager entityRenderManager = minecraftClient.getEntityRenderDispatcher();
        RenderDispatcher renderDispatcher = minecraftClient.gameRenderer.getEntityRenderDispatcher();
        Vec3d vec3d = camera.getCameraPos();
        CameraRenderState cameraRenderState = new CameraRenderState();
        cameraRenderState.orientation = camera.getRotation();
        GlStateSnapshot glStateSnapshot = GlStateSnapshot.capture();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(simpleFramebuffer.getColorAttachment(), 0, simpleFramebuffer.getDepthAttachment(), 1.0);
        GpuTextureView gpuTextureView = RenderSystem.outputColorTextureOverride;
        GpuTextureView gpuTextureView2 = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = simpleFramebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = simpleFramebuffer.getDepthAttachmentView();

        try {
            for (Entity entity : entities) {
                EntityRenderer entityRenderer = entityRenderManager.getRenderer(entity);
                if (entityRenderer != null) {
                    EntityRenderState entityRenderState = entityRenderer.getAndUpdateRenderState(entity, f);
                    List list = List.copyOf(entityRenderState.shadowPieces);
                    Text text = entityRenderState.displayName;
                    int i = entityRenderState.outlineColor;
                    entityRenderState.displayName = null;
                    entityRenderState.outlineColor = 0;
                    entityRenderState.shadowPieces.clear();
                    Vec3d vec3d2 = ProjectionUtil.getInterpolatedPos(entity, f);
                    entityRenderManager.render(entityRenderState, cameraRenderState, vec3d2.x - vec3d.x, vec3d2.y - vec3d.y, vec3d2.z - vec3d.z, matrixStack, renderDispatcher.getQueue());
                    renderDispatcher.render();
                    entityRenderState.shadowPieces.addAll(list);
                    entityRenderState.displayName = text;
                    entityRenderState.outlineColor = i;
                }
            }

            minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
        } finally {
            RenderSystem.outputColorTextureOverride = gpuTextureView;
            RenderSystem.outputDepthTextureOverride = gpuTextureView2;
            glStateSnapshot.restore();
        }
    }
}
