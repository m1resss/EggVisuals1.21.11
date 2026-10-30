package egg.client.cosmetic;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public class CosmeticMeshGuiRenderState implements SimpleGuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float[] quadVertices;
    private final int color;
    private final ScreenRect scissorArea;
    private final ScreenRect bounds;

    public CosmeticMeshGuiRenderState(Identifier texture, Matrix3x2f pose, float[] quadVertices, int color,
                                     float x, float y, float w, float h, ScreenRect scissorArea) {
        this.pipeline = RenderPipelines.GUI_TEXTURED;
        this.pose = pose != null ? pose : new Matrix3x2f();
        this.quadVertices = quadVertices;
        this.color = color;
        this.scissorArea = scissorArea;
        this.bounds = new ScreenRect((int) x, (int) y, Math.max(1, (int) w), Math.max(1, (int) h));

        MinecraftClient mc = MinecraftClient.getInstance();
        AbstractTexture tex = mc.getTextureManager().getTexture(texture);
        GpuTextureView view = tex != null ? tex.getGlTextureView() : null;
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        this.textureSetup = TextureSetup.of(view, sampler);
    }

    @Override
    public void setupVertices(VertexConsumer consumer) {
        for (int i = 0; i + 3 < this.quadVertices.length; i += 4) {
            float x = this.quadVertices[i];
            float y = this.quadVertices[i + 1];
            float u = this.quadVertices[i + 2];
            float v = this.quadVertices[i + 3];
            consumer.vertex(this.pose, x, y).texture(u, v).color(this.color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return this.textureSetup;
    }

    @Override
    public ScreenRect scissorArea() {
        return this.scissorArea;
    }

    @Override
    public ScreenRect bounds() {
        return this.bounds;
    }
}
