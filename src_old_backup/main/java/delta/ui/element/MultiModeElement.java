package delta.ui.element;

import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.setting.Setting;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.render.Draw2DProcessor;
import delta.setting.BooleanSetting;
import delta.setting.MultiModeSetting;
import delta.ui.element.Element_2;

import delta.render.AnimationUtil;
import delta.api.Compile;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;

public class MultiModeElement extends Element_2<MultiModeSetting> {
    private final AnimationUtil[] d;

    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (button != 0) {
            if (button != 2 || !MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
                return false;
            }
            if (!(setting instanceof MultiModeSetting)) {
                throw new ClassCastException();
            }
            List<BooleanSetting> listC = ((MultiModeSetting) setting).c();
            if (!(listC instanceof List)) {
                throw new ClassCastException();
            }
            listC.forEach(new Consumer() {
                @Override
                public void accept(Object obj) {
                    ((Setting) obj).b();
                }
            });
            return true;
        }
        float f = vector4f.x;
        float fA = vector4f.y + Fonts.d.a(6.5f) + 5.0f;
        if (!(setting instanceof MultiModeSetting)) {
            throw new ClassCastException();
        }
        List<BooleanSetting> listC2 = ((MultiModeSetting) setting).c();
        if (!(listC2 instanceof List)) {
            throw new ClassCastException();
        }
        for (BooleanSetting booleanSetting : listC2) {
            if (!(booleanSetting instanceof BooleanSetting)) {
                throw new ClassCastException();
            }
            BooleanSetting booleanSetting2 = booleanSetting;
            float fA2 = Fonts.b.a(booleanSetting2.i(), 7.5f) + 12.0f;
            if (f + fA2 > vector4f.x + vector4f.z) {
                f = vector4f.x;
                fA += 17.0f;
            }
            if (MathUtil.a(mouseX, mouseY, f, fA, fA2, 13.5f)) {
                Boolean boolC = booleanSetting2.c();
                if (!(boolC instanceof Boolean)) {
                    throw new ClassCastException();
                }
                booleanSetting2.a(Boolean.valueOf(!boolC.booleanValue()));
                return true;
            }
            f += fA2 + 4.0f;
        }
        return false;
    }

    static {
        NativeMethodLookup.lookup(MultiModeElement.class, 12);
    }

    public MultiModeElement(MultiModeSetting setting) {
        super(setting);
        this.d = new AnimationUtil[setting.c().size()];
        for (int i = 0; i < this.d.length; i++) {
            this.d[i] = new AnimationUtil();
        }
    }

    @Override
    public void a(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        long selectedCount = ((MultiModeSetting) this.b).c().stream().filter((v0) -> {
            return v0.c();
        }).count();
        int totalCount = ((MultiModeSetting) this.b).c().size();
        String counter = selectedCount + " из " + totalCount;
        float counterWidth = Fonts.b.a(counter, 7.5f);
        boolean hovered = MathUtil.a(mouseX, mouseY, this.a.x, this.a.y, this.a.z, this.a.w) && extend >= 1.0f;
        a(matrices, Fonts.d, ((MultiModeSetting) this.b).i(), this.a.x, this.a.y, Fonts.d.a(8.5f) + 1.0f, 8.5f, theme.a(ThemeInfo.TEXT).a(), (this.a.z - counterWidth) - 4.0f, hovered, extend, delta);
        Fonts.b.a(matrices, counter, (this.a.x + this.a.z) - counterWidth, this.a.y + 0.5f, 7.5f, ColorUtil.a(theme.a(ThemeInfo.TEXT_DISABLED).a(), extend));
        float x = this.a.x;
        float y = this.a.y + Fonts.d.a(8.5f) + 5.5f;
        int i = 0;
        for (BooleanSetting mode : ((MultiModeSetting) this.b).c()) {
            float width = Fonts.b.a(mode.i(), 7.5f) + 12.0f;
            if (x + width > this.a.x + this.a.z) {
                x = this.a.x;
                y += 17.0f;
            }
            this.d[i].a(mode.c().booleanValue());
            this.d[i].a(0.0f, 1.0f, 0.3f, EasingList.i, delta);
            float value = this.d[i].c();
            draw.a(matrices, x, y, width, 13.5f, 4.0f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), ((12.0f + (70.0f * value)) / 255.0f) * extend));
            draw.a(matrices, x, y, width, 13.5f, 4.0f, 0.5f, ColorUtil.a(theme.a(ThemeInfo.OUTLINE_SMALL).a(), theme.a(ThemeInfo.OUTLINE_SMALL).b() * extend));
            int color = ColorUtil.a(theme.a(ThemeInfo.TEXT_DISABLED).a(), theme.a(ThemeInfo.TEXT).a(), value);
            Fonts.b.b(matrices, mode.i(), x + (width / 2.0f), (y + ((13.5f - Fonts.b.a(7.5f)) / 2.0f)) - 0.5f, 7.5f, ColorUtil.a(color, extend));
            x += width + 4.5f;
            i++;
        }
        this.a.w = (y + 13.5f) - this.a.y;
    }
}
