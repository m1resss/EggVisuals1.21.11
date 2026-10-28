package delta.ui.element;

import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.render.Draw2DProcessor;
import delta.setting.SliderSetting;
import delta.ui.element.Element_2;

import delta.api.Compile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

public class SliderElement extends Element_2<SliderSetting> {
    private boolean d;

    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (!MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y + Fonts.d.a(6.5f), vector4f.z, 14.0f)) {
            return false;
        }
        if (button == 0) {
            this.d = true;
            a(mouseX);
            return true;
        }
        if (button != 2) {
            return false;
        }
        if (!(setting instanceof SliderSetting)) {
            throw new ClassCastException();
        }
        ((SliderSetting) setting).b();
        return true;
    }

    @Override
    @Compile
    public boolean b(double mouseX, double mouseY, int button) {
        this.d = false;
        return false;
    }

    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, double amount) {
        var setting = this.b;
        Vector4f vector4f = this.a;
        if (!(setting instanceof SliderSetting)) {
            throw new ClassCastException();
        }
        SliderSetting sliderSetting = (SliderSetting) setting;
        if (!sliderSetting.e || !MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, ((vector4f.y + Fonts.d.a(6.5f)) + 12.0f) - vector4f.y)) {
            return false;
        }
        Float fC = sliderSetting.c();
        if (!(fC instanceof Float)) {
            throw new ClassCastException();
        }
        sliderSetting.a(Float.valueOf(MathUtil.b(Math.round((fC.floatValue() + (((float) Math.signum(amount)) * sliderSetting.c)) / sliderSetting.c) * sliderSetting.c, sliderSetting.a, sliderSetting.b)));
        return true;
    }

    static {
        NativeMethodLookup.lookup(SliderElement.class, 13);
    }

    public SliderElement(SliderSetting setting) {
        super(setting);
        this.a.w = 23.0f;
    }

    @Override
    public void a(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        this.a.w = 23.0f;
        if (this.d) {
            a(mouseX);
        }
        b().c(MathUtil.c(b().a(), (((SliderSetting) this.b).c().floatValue() - ((SliderSetting) this.b).a) / (((SliderSetting) this.b).b - ((SliderSetting) this.b).a), 1.0f));
        float progress = b().a();
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;
        float current = ((SliderSetting) this.b).a + ((((SliderSetting) this.b).b - ((SliderSetting) this.b).a) * progress);
        String value = ((SliderSetting) this.b).c % 1.0f == 0.0f ? String.valueOf(Math.round(current)) : String.valueOf(Math.round(current * 100.0f) / 100.0f);
        float boxWidth = Fonts.d.a(value, 7.5f) + 10.0f;
        float boxHeight = 12.0f;
        float boxX = (this.a.x + this.a.z) - boxWidth;
        a(matrices, Fonts.d, ((SliderSetting) this.b).i(), this.a.x, this.a.y + 0.5f, Fonts.d.a(8.5f), 8.5f, theme.a(ThemeInfo.TEXT).a(), (boxX - this.a.x) - 4.0f, hovered, extend, delta);
        draw.a(matrices, boxX, this.a.y, boxWidth, boxHeight, 3.5f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), 0.14f * extend));
        draw.a(matrices, boxX, this.a.y, boxWidth, boxHeight, 3.5f, 0.5f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), 0.40f * extend));
        Fonts.d.b(matrices, value, boxX + (boxWidth / 2.0f), this.a.y + 2.0f, 7.5f, ColorUtil.a(theme.a(ThemeInfo.TEXT).a(), extend));
        
        // Oval track (height 4.5f, radius 2.25f)
        float trackH = 4.5f;
        float trackRadius = 2.25f;
        float trackY = this.a.y + Fonts.d.a(8.5f) + 6.0f;
        draw.a(matrices, this.a.x, trackY, this.a.z, trackH, trackRadius, ColorUtil.a(ColorUtil.a(44, 40, 48, 255), extend * 0.65f));
        if (progress > 0.01f) {
            float fillW = Math.max(trackH, this.a.z * progress);
            draw.a(matrices, this.a.x, trackY, fillW, trackH, trackRadius, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), extend));
        }
        // Round knob (diameter 9.5f, radius 4.75f)
        float knobSize = 9.5f;
        float knobX = this.a.x + (this.a.z - knobSize) * progress;
        float knobY = trackY + (trackH - knobSize) / 2.0f;
        draw.a(matrices, knobX, knobY, knobSize, knobSize, knobSize / 2.0f, ColorUtil.a(ColorUtil.a(255, 255, 255, 255), extend));
    }

    private void a(double mouseX) {
        float progress = MathUtil.b(((float) (mouseX - ((double) this.a.x))) / this.a.z, 0.0f, 1.0f);
        float value = ((SliderSetting) this.b).a + ((((SliderSetting) this.b).b - ((SliderSetting) this.b).a) * progress);
        ((SliderSetting) this.b).a(Float.valueOf(MathUtil.b(Math.round(value / ((SliderSetting) this.b).c) * ((SliderSetting) this.b).c, ((SliderSetting) this.b).a, ((SliderSetting) this.b).b)));
    }
}
