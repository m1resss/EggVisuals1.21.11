package delta.ui.shader;

import delta.core.Interface;
import delta.render.CustomUniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

public class LiquidGlassShader extends Shader implements Interface {
    public CustomUniform size;
    public CustomUniform radius;
    public CustomUniform smoothness;
    public CustomUniform cornerSmoothness;
    public CustomUniform globalAlpha;
    public CustomUniform fresnelPower;
    public CustomUniform fresnelColor;
    public CustomUniform fresnelAlpha;
    public CustomUniform baseAlpha;
    public CustomUniform fresnelInvert;
    public CustomUniform fresnelMix;
    public CustomUniform distortStrength;

    public LiquidGlassShader() {
        super(Identifier.of("delta", "core/liquidglass/liquidglass"), VertexFormats.POSITION_TEXTURE_COLOR);
    }

    @Override
    protected void b() {
        this.size = a("Size");
        this.radius = a("Radius");
        this.smoothness = a("Smoothness");
        this.cornerSmoothness = a("CornerSmoothness");
        this.globalAlpha = a("GlobalAlpha");
        this.fresnelPower = a("FresnelPower");
        this.fresnelColor = a("FresnelColor");
        this.fresnelAlpha = a("FresnelAlpha");
        this.baseAlpha = a("BaseAlpha");
        this.fresnelInvert = a("FresnelInvert");
        this.fresnelMix = a("FresnelMix");
        this.distortStrength = a("DistortStrength");
    }

    public void setSize(float width, float height) {
        if (this.size != null) this.size.set(width, height);
    }

    public void setRadius(Vector4f rad) {
        if (this.radius != null) this.radius.set(rad.x, rad.z, rad.y, rad.w);
    }

    public void setSmoothness(float s) {
        if (this.smoothness != null) this.smoothness.set(s);
    }

    public void setCornerSmoothness(float cs) {
        if (this.cornerSmoothness != null) this.cornerSmoothness.set(cs);
    }

    public void setGlobalAlpha(float ga) {
        if (this.globalAlpha != null) this.globalAlpha.set(ga);
    }

    public void setFresnelPower(float fp) {
        if (this.fresnelPower != null) this.fresnelPower.set(fp);
    }

    public void setFresnelColor(float r, float g, float b) {
        if (this.fresnelColor != null) this.fresnelColor.set(r, g, b);
    }

    public void setFresnelAlpha(float fa) {
        if (this.fresnelAlpha != null) this.fresnelAlpha.set(fa);
    }

    public void setBaseAlpha(float ba) {
        if (this.baseAlpha != null) this.baseAlpha.set(ba);
    }

    public void setFresnelInvert(boolean fi) {
        if (this.fresnelInvert != null) this.fresnelInvert.set(fi ? 1 : 0);
    }

    public void setFresnelMix(float fm) {
        if (this.fresnelMix != null) this.fresnelMix.set(fm);
    }

    public void setDistortStrength(float ds) {
        if (this.distortStrength != null) this.distortStrength.set(ds);
    }
}
