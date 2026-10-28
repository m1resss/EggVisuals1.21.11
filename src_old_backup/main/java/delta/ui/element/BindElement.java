package delta.ui.element;

import delta.util.KeyUtil;
import delta.render.ScissorUtil;
import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.render.Draw2DProcessor;
import delta.setting.BindSetting;
import delta.ui.element.Element_2;

import delta.api.Compile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

public class BindElement extends Element_2<BindSetting> {
    private boolean d;

    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (this.d) {
            if (!(setting instanceof BindSetting)) {
                throw new ClassCastException();
            }
            int mouseBind = KeyUtil.c(button);
            if (mouseBind == -1) {
                return false;
            }
            ((BindSetting) setting).a(mouseBind);
            this.d = false;
            return true;
        }
        if (!MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
            return false;
        }
        if (button == 0) {
            this.d = true;
            return true;
        }
        if (button != 2) {
            return false;
        }
        if (!(setting instanceof BindSetting)) {
            throw new ClassCastException();
        }
        ((BindSetting) setting).b();
        return true;
    }

    @Override
    @Compile
    public boolean a(int keyCode, int scanCode, int modifiers) {
        if (!this.d) {
            return false;
        }
        var setting = this.b;
        if (!(setting instanceof BindSetting)) {
            throw new ClassCastException();
        }
        ((BindSetting) setting).a(Integer.valueOf(keyCode));
        this.d = false;
        return true;
    }

    static {
        NativeMethodLookup.lookup(BindElement.class, 7);
    }

    public BindElement(BindSetting setting) {
        super(setting);
        this.a.w = 18.0f;
    }

    @Override
    public void a(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        b().a(this.d);
        b().a(0.0f, 1.0f, 0.4f, EasingList.p, delta);
        float centerY = this.a.y + (this.a.w / 2.0f);
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;
        float anim = b().c();
        float reverse = 1.0f - anim;
        String value = ((BindSetting) this.b).c().intValue() == -1 ? "None" : KeyUtil.b(((BindSetting) this.b).c().intValue());
        float total = (Fonts.b.a(value, 7.5f) * reverse) + (Fonts.b.a("...", 7.5f) * anim);
        float boxWidth = total + 12.0f;
        float boxHeight = Fonts.b.a(7.5f) + 5.0f;
        float boxX = (this.a.x + this.a.z) - boxWidth;
        float boxY = centerY - (boxHeight / 2.0f);
        float textY = (boxY + ((boxHeight - Fonts.b.a(7.5f)) / 2.0f)) - 0.5f;
        a(matrices, Fonts.d, ((BindSetting) this.b).i(), this.a.x, this.a.y, this.a.w, 8.5f, theme.a(ThemeInfo.TEXT).a(), (boxX - this.a.x) - 4.0f, hovered, extend, delta);
        draw.a(matrices, boxX, boxY, boxWidth, boxHeight, 3.5f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), (this.d ? 0.25f : 0.08f) * extend));
        draw.a(matrices, boxX, boxY, boxWidth, boxHeight, 3.5f, 0.5f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), (this.d ? 0.6f : 0.25f) * extend));
        ScissorUtil.a(matrices, boxX, boxY, boxWidth, boxHeight);
        if (reverse > 0.0f) {
            Fonts.b.a(matrices, value, boxX + 6.0f, textY, 7.5f, ColorUtil.a(theme.a(ThemeInfo.TEXT).a(), extend * reverse));
        }
        if (anim > 0.0f) {
            Fonts.b.a(matrices, "...", boxX + 6.0f, textY, 7.5f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), extend * anim));
        }
        ScissorUtil.a(matrices);
    }
}
