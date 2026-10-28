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
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class BlurShader extends Shader implements Interface {
    private final List<SimpleFramebuffer> n;
    public CustomUniform c;
    public CustomUniform d;
    public CustomUniform e;
    public CustomUniform f;
    public CustomUniform g;
    public CustomUniform h;
    public CustomUniform i;
    public CustomUniform j;
    public CustomUniform k;
    public CustomUniform l;
    public CustomUniform m;
    private final Shader upscaleShader;
    private final Shader downscaleShader;

    @Generated
    public List<SimpleFramebuffer> e() {
        return this.n;
    }

    public BlurShader() {
        super(Identifier.of("delta", "core/rect/blurred_rect"), VertexFormats.POSITION_TEXTURE_COLOR);
        this.n = new ArrayList<>();
        this.upscaleShader = new Shader(Identifier.of("delta", "core/blur/upscale"), VertexFormats.POSITION) {
            @Override
            protected void b() {}
        };
        this.downscaleShader = new Shader(Identifier.of("delta", "core/blur/downscale"), VertexFormats.POSITION) {
            @Override
            protected void b() {}
        };
        EventManager.a(this);
    }

    @EventTarget
    public void a(ResizeEvent event) {
        this.n.clear();
        for (int i = 0; i <= 3; i++) {
            this.n.add(f());
        }
    }

    @Override
    protected void b() {
        this.c = a("uSize");
        this.d = a("uRadius");
        this.e = a("uSmoothness");
        this.f = a("uMix");
        this.g = a("uAlpha");
        this.h = a("uTopLeftColor");
        this.i = a("uBottomLeftColor");
        this.j = a("uTopRightColor");
        this.k = a("uBottomRightColor");
        this.l = a("uGlowColor");
        this.m = a("uGlowRadius");
    }

    public void a(MatrixStack matrixStack) {
        if (!this.n.isEmpty()) {
            int actualPasses = Math.max(this.n.size() - 1, 1);
            try {
                a(matrixStack, this.downscaleShader, aM_.getFramebuffer(), (Framebuffer) this.n.getFirst(), 0, 24);
                for (int i = 0; i < actualPasses; i++) {
                    a(matrixStack, this.downscaleShader, (Framebuffer) this.n.get(i), (Framebuffer) this.n.get(i + 1), i + 1, 24);
                }
                for (int i2 = actualPasses; i2 > 0; i2--) {
                    a(matrixStack, this.upscaleShader, (Framebuffer) this.n.get(i2), (Framebuffer) this.n.get(i2 - 1), i2, 24);
                }
            } finally {
                RenderStateHelper.bindFramebuffer(aM_.getFramebuffer());
            }
        }
    }

    private void a(MatrixStack matrixStack, Shader blurPassShader, Framebuffer source, Framebuffer destination, int pass, int offset) {
        RenderStateHelper.bindFramebuffer(destination);
        RenderStateHelper.setShaderTexture(0, source.getColorAttachment());
        blurPassShader.a();
        CustomUniform halfTexel = blurPassShader.a("uHalfTexelSize");
        CustomUniform off = blurPassShader.a("uOffset");
        if (halfTexel != null) {
            halfTexel.set(0.5f / source.textureWidth, 0.5f / source.textureHeight);
        }
        if (off != null) {
            off.set(offset * (pass / 3.0f));
        }
        a(matrixStack.peek().getPositionMatrix());
        RenderStateHelper.unbindFramebuffer();
    }

    private void a(Matrix4f matrix4f) {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        builder.vertex(matrix4f, 0.0f, 0.0f, 0.0f);
        builder.vertex(matrix4f, 0.0f, aM_.getWindow().getScaledHeight(), 0.0f);
        builder.vertex(matrix4f, aM_.getWindow().getScaledWidth(), aM_.getWindow().getScaledHeight(), 0.0f);
        builder.vertex(matrix4f, aM_.getWindow().getScaledWidth(), 0.0f, 0.0f);
        ImmediateBufferRenderer.draw(builder.end());
    }

    private SimpleFramebuffer f() {
        return new SimpleFramebuffer("delta_blur", aM_.getWindow().getFramebufferWidth(), aM_.getWindow().getFramebufferHeight(), false);
    }

    public void a(float width, float height) {
        if (this.c != null) {
            this.c.set(width, height);
        }
    }

    public void a(Vector4f radius) {
        if (this.d != null) {
            this.d.set(radius.x, radius.z, radius.w, radius.y);
        }
    }

    public void a(float smoothness) {
        if (this.e != null) {
            this.e.set(smoothness);
        }
    }

    public void b(float mix) {
        if (this.f != null) {
            this.f.set(mix);
        }
    }

    public void c(float alpha) {
        if (this.g != null) {
            this.g.set(alpha);
        }
    }

    public void a(float r, float g, float b, float a) {
        if (this.h != null) {
            this.h.set(r, g, b, a);
        }
    }

    public void b(float r, float g, float b, float a) {
        if (this.i != null) {
            this.i.set(r, g, b, a);
        }
    }

    public void c(float r, float g, float b, float a) {
        if (this.j != null) {
            this.j.set(r, g, b, a);
        }
    }

    public void d(float r, float g, float b, float a) {
        if (this.k != null) {
            this.k.set(r, g, b, a);
        }
    }

    public void e(float r, float g, float b, float a) {
        if (this.l != null) {
            this.l.set(r, g, b, a);
        }
    }

    public void d(float r) {
        if (this.m != null) {
            this.m.set(r);
        }
    }
}
