package delta.ui.shader;


import delta.render.CustomUniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class EggLogoShader extends Shader {
    public CustomUniform uResolution;
    public CustomUniform uTime;
    public CustomUniform uMouse;
    public CustomUniform uIntroPlayed;
    public CustomUniform uAlpha;
    public CustomUniform uThemeMode;
    public CustomUniform uBurstTime;

    public EggLogoShader() {
        super(Identifier.of("delta", "core/egg_logo/egg_logo"), VertexFormats.POSITION_TEXTURE_COLOR);
    }

    @Override
    protected void b() {
        this.uResolution = a("uResolution");
        this.uTime = a("uTime");
        this.uMouse = a("uMouse");
        this.uIntroPlayed = a("uIntroPlayed");
        this.uAlpha = a("uAlpha");
        this.uThemeMode = a("uThemeMode");
        this.uBurstTime = a("uBurstTime");
    }

    public void setResolution(float width, float height) {
        if (this.uResolution != null) this.uResolution.set(width, height);
    }

    public void setTime(float t) {
        if (this.uTime != null) this.uTime.set(t);
    }

    public void setMouse(float x, float y) {
        if (this.uMouse != null) this.uMouse.set(x, y);
    }

    public void setIntroPlayed(boolean played) {
        if (this.uIntroPlayed != null) this.uIntroPlayed.set(played ? 1.0f : 0.0f);
    }

    public void setAlpha(float a) {
        if (this.uAlpha != null) this.uAlpha.set(a);
    }

    public void setThemeMode(float tm) {
        if (this.uThemeMode != null) this.uThemeMode.set(tm);
    }

    public void setBurstTime(float bt) {
        if (this.uBurstTime != null) this.uBurstTime.set(bt);
    }
}
