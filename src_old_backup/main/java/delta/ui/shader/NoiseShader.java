package delta.ui.shader;

import static delta.core.Interface.aM_;
import delta.core.EventManager;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.event.ResizeEvent;
import delta.render.CustomUniform;
import delta.render.ImmediateBufferRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import delta.render.RenderStateHelper;
import java.awt.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;

public class NoiseShader extends Shader implements Interface {
    private static final Identifier c = Identifier.of("delta", "core/noise/noise_shader");
    private SimpleFramebuffer e;
    private CustomUniform f;
    private CustomUniform g;

    public NoiseShader() {
        super(c, VertexFormats.POSITION_COLOR);
        EventManager.a(this);
    }

    @EventTarget
    public void a(ResizeEvent event) {
        this.e = new SimpleFramebuffer("delta_noise", aM_.getWindow().getFramebufferWidth(), aM_.getWindow().getFramebufferHeight(), true);
    }

    public void e() {
        if (this.e != null) {
            this.e.copyDepthFrom(aM_.getFramebuffer());
        }
    }

    @Override
    protected void b() {
        this.f = a("TintColor");
        this.g = a("Time");
    }

    @Override
    public void uploadDefaultMatrices() {
        CustomUniform mv = a("ModelViewMat");
        if (mv != null) mv.set(new Matrix4f().identity());
        CustomUniform proj = a("ProjMat");
        if (proj != null) proj.set(new Matrix4f().identity());
    }

    public void a(float[] color) {
        if (this.e != null) {
            RenderStateHelper.disableDepthTest();
            RenderStateHelper.enableBlend();
            RenderStateHelper.defaultBlendFunc();
            RenderStateHelper.disableCull();
            RenderStateHelper.setShaderTexture(0, aM_.getFramebuffer().getColorAttachment());
            RenderStateHelper.setShaderTexture(1, aM_.getFramebuffer().getDepthAttachment());
            RenderStateHelper.setShaderTexture(2, this.e.getDepthAttachment());
            a();
            if (this.f != null) {
                this.f.set(color[0], color[1], color[2], color[3]);
            }
            if (this.g != null) {
                this.g.set((System.currentTimeMillis() % 100000) / 1000.0f);
            }
            int white = new Color(255, 255, 255, 255).getRGB();
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            builder.vertex(-1.0f, -1.0f, 0.0f).color(white);
            builder.vertex(-1.0f, 1.0f, 0.0f).color(white);
            builder.vertex(1.0f, 1.0f, 0.0f).color(white);
            builder.vertex(1.0f, -1.0f, 0.0f).color(white);
            ImmediateBufferRenderer.draw(builder.end());
            RenderStateHelper.setShaderTexture(0, 0);
            RenderStateHelper.setShaderTexture(1, 0);
            RenderStateHelper.setShaderTexture(2, 0);
            RenderStateHelper.enableCull();
            RenderStateHelper.disableBlend();
            RenderStateHelper.enableDepthTest();
        }
    }
}
