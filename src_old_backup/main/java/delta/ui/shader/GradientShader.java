package delta.ui.shader;

import delta.ui.shader.Shader;

import delta.render.CustomUniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

public class GradientShader extends Shader {
    public CustomUniform c;
    public CustomUniform d;
    public CustomUniform e;
    public CustomUniform f;
    public CustomUniform g;
    public CustomUniform h;
    public CustomUniform i;

    public GradientShader() {
        super(Identifier.of("delta", "core/rect/gradient_rect"), VertexFormats.POSITION_COLOR);
    }

    @Override
    protected void b() {
        this.c = a("uSize");
        this.d = a("uRadius");
        this.e = a("uSmoothness");
        this.f = a("uTopLeftColor");
        this.g = a("uBottomLeftColor");
        this.h = a("uTopRightColor");
        this.i = a("uBottomRightColor");
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

    public void a(float r, float g, float b, float a) {
        if (this.f != null) {
            this.f.set(r, g, b, a);
        }
    }

    public void b(float r, float g, float b, float a) {
        if (this.g != null) {
            this.g.set(r, g, b, a);
        }
    }

    public void c(float r, float g, float b, float a) {
        if (this.h != null) {
            this.h.set(r, g, b, a);
        }
    }

    public void d(float r, float g, float b, float a) {
        if (this.i != null) {
            this.i.set(r, g, b, a);
        }
    }
}
