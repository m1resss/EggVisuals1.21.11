package delta.ui.element;

import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.core.InterfaceC0020Opcode;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.event.DrawEvent;
import delta.render.Draw2DProcessor;
import delta.setting.BooleanSetting;
import delta.ui.element.Element_2;

import delta.api.Compile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

public class BooleanElement extends Element_2<BooleanSetting> {
    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (!MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
            return false;
        }
        if (button != 0) {
            if (button != 2) {
                return false;
            }
            if (!(setting instanceof BooleanSetting)) {
                throw new ClassCastException();
            }
            ((BooleanSetting) setting).b();
            return true;
        }
        if (!(setting instanceof BooleanSetting)) {
            throw new ClassCastException();
        }
        BooleanSetting booleanSetting = (BooleanSetting) setting;
        Boolean boolC = booleanSetting.c();
        if (!(boolC instanceof Boolean)) {
            throw new ClassCastException();
        }
        booleanSetting.a(Boolean.valueOf(!boolC.booleanValue()));
        return true;
    }

    static {
        NativeMethodLookup.lookup(BooleanElement.class, 8);
    }

    public BooleanElement(BooleanSetting setting) {
        super(setting);
        this.a.w = 18.0f;
    }

    @Override
    public void a(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        int primaryColor = theme.a(ThemeInfo.PRIMARY).a();
        this.a.w = 18.0f;
        b().a(((BooleanSetting) this.b).c().booleanValue());
        b().a(0.0f, 1.0f, 0.4f, EasingList.i, delta);
        float enabled = b().c();
        float centerY = this.a.y + (this.a.w / 2.0f);
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;

        float switchW = 19.0f;
        float switchH = 10.0f;
        float switchRadius = 5.0f;
        float boxX = (this.a.x + this.a.z) - switchW;
        float boxY = centerY - (switchH / 2.0f);

        a(matrices, Fonts.d, ((BooleanSetting) this.b).i(), this.a.x, this.a.y, this.a.w, 8.5f, theme.a(ThemeInfo.TEXT).a(), (boxX - this.a.x) - 4.0f, hovered, extend, delta);

        // Recessed oval track
        int trackBg = ColorUtil.a(28, 22, 26, (int) (180 * extend));
        draw.a(matrices, boxX, boxY, switchW, switchH, switchRadius, trackBg);
        draw.a(matrices, boxX, boxY, switchW, switchH, switchRadius, 0.5f, ColorUtil.a(255, 255, 255, (int) (12 * extend)));

        // Sliding knob (white when active)
        float knobW = 10.5f;
        float knobH = 7.0f;
        float knobR = 3.5f;
        float knobX = boxX + 1.5f + (switchW - knobW - 3.0f) * enabled;
        float knobY = boxY + (switchH - knobH) * 0.5f;
        int knobOff = ColorUtil.a(142, 138, 138, (int) (255 * extend));
        int knobOn = ColorUtil.a(255, 255, 255, (int) (255 * extend));
        int knobColor = ColorUtil.a(knobOff, knobOn, enabled);
        draw.a(matrices, knobX, knobY, knobW, knobH, knobR, knobColor);
    }

    @Override
    public void a(DrawEvent event, float x, float y, float width, float animation) {
        b().a(((BooleanSetting) this.b).c().booleanValue());
        b().a(0.0f, 1.0f, 0.35f, EasingList.i, event.g());
        ThemeProcessor theme = Delta.h().d().o();
        int primary = theme.a(ThemeInfo.PRIMARY).a();
        float value = b().c();

        float rowHeight = 15.0f;
        float fontSize = 7.5f;
        float textY = y + ((rowHeight - Fonts.e.a(fontSize)) / 2.0f) - 0.5f;
        int textColor = ColorUtil.a(235, 238, 248, (int) (240 * animation));
        Fonts.e.a(event.h(), ((BooleanSetting) this.b).i(), x, textY, fontSize, textColor);

        float switchW = 18.0f;
        float switchH = 9.5f;
        float switchR = 4.75f;
        float toggleX = (x + width) - switchW;
        float toggleY = y + ((rowHeight - switchH) / 2.0f);

        int offTrackBg = ColorUtil.a(28, 30, 40, (int) (220 * animation));
        int onTrackBg = ColorUtil.a(primary, (int) (210 * animation));
        int trackColor = ColorUtil.a(offTrackBg, onTrackBg, value);
        event.d().a(event.h(), toggleX, toggleY, switchW, switchH, switchR, trackColor);

        int trackBorder = ColorUtil.a(255, 255, 255, (int) ((18 + (int) (25 * value)) * animation));
        event.d().a(event.h(), toggleX, toggleY, switchW, switchH, switchR, 0.5f, trackBorder);

        float knobW = 7.5f;
        float knobH = 7.5f;
        float knobR = 3.75f;
        float knobX = toggleX + 1.0f + (switchW - knobW - 2.0f) * value;
        float knobY = toggleY + (switchH - knobH) * 0.5f;

        int knobOff = ColorUtil.a(148, 150, 162, (int) (255 * animation));
        int knobOn = ColorUtil.a(255, 255, 255, (int) (255 * animation));
        int knobColor = ColorUtil.a(knobOff, knobOn, value);

        event.d().a(event.h(), knobX, knobY, knobW, knobH, knobR, knobColor);
    }
}
