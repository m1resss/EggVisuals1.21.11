package delta.ui.element;

import delta.core.NativeMethodLookup;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.Fonts;
import delta.render.ColorUtil;
import delta.util.MathUtil;

import delta.config.ThemeInfo;
import delta.config.ThemeProcessor;
import delta.render.Draw2DProcessor;
import delta.setting.ModeSetting;
import delta.ui.element.Element_2;

import delta.render.AnimationUtil;
import delta.api.Compile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import java.util.List;

public class ModeElement extends Element_2<ModeSetting> {
    private final AnimationUtil[] d;

    @Override
    @Compile
    public boolean a(double mouseX, double mouseY, int button) {
        Vector4f vector4f = this.a;
        var setting = this.b;
        if (delta.core.Interface.aM_.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            if (MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
                if (setting instanceof ModeSetting modeSetting) {
                    List<String> modes = modeSetting.k();
                    int idx = modes.indexOf(modeSetting.c());
                    if (button == 0) {
                        int next = (idx + 1) % modes.size();
                        modeSetting.a(modes.get(next));
                    } else if (button == 1) {
                        int prev = (idx - 1 + modes.size()) % modes.size();
                        modeSetting.a(modes.get(prev));
                    }
                    return true;
                }
            }
            return false;
        }
        if (button != 0) {
            if (button != 2 || !MathUtil.a(mouseX, mouseY, vector4f.x, vector4f.y, vector4f.z, vector4f.w)) {
                return false;
            }
            if (!(setting instanceof ModeSetting)) {
                throw new ClassCastException();
            }
            ((ModeSetting) setting).b();
            return true;
        }
        float f = vector4f.x;
        float fA = vector4f.y + Fonts.d.a(6.5f) + 5.0f;
        if (!(setting instanceof ModeSetting)) {
            throw new ClassCastException();
        }
        ModeSetting modeSetting = (ModeSetting) setting;
        for (String str : modeSetting.k()) {
            if (!(str instanceof String)) {
                throw new ClassCastException();
            }
            String str2 = str;
            float fA2 = Fonts.b.a(str2, 7.5f) + 12.0f;
            if (f + fA2 > vector4f.x + vector4f.z) {
                f = vector4f.x;
                fA += 17.0f;
            }
            if (MathUtil.a(mouseX, mouseY, f, fA, fA2, 13.5f)) {
                modeSetting.a(str2);
                return true;
            }
            f += fA2 + 4.0f;
        }
        return false;
    }

    static {
        NativeMethodLookup.lookup(ModeElement.class, 11);
    }

    public ModeElement(ModeSetting setting) {
        super(setting);
        this.d = new AnimationUtil[setting.k().size()];
        for (int i = 0; i < this.d.length; i++) {
            this.d[i] = new AnimationUtil();
        }
    }

    @Override
    public void a(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = new MatrixStack();
        Draw2DProcessor draw = Delta.h().d().i();
        ThemeProcessor theme = Delta.h().d().o();
        Fonts.d.a(matrices, ((ModeSetting) this.b).i(), this.a.x, this.a.y, 8.5f, ColorUtil.a(theme.a(ThemeInfo.TEXT).a(), extend));
        float x = this.a.x;
        float y = this.a.y + Fonts.d.a(8.5f) + 5.5f;
        int i = 0;
        for (String mode : ((ModeSetting) this.b).k()) {
            float width = Fonts.b.a(mode, 7.5f) + 12.0f;
            if (x + width > this.a.x + this.a.z) {
                x = this.a.x;
                y += 17.0f;
            }
            this.d[i].a(((ModeSetting) this.b).l(mode));
            this.d[i].a(0.0f, 1.0f, 0.3f, EasingList.i, delta);
            float value = this.d[i].c();
            draw.a(matrices, x, y, width, 13.5f, 4.0f, ColorUtil.a(theme.a(ThemeInfo.PRIMARY).a(), ((12.0f + (70.0f * value)) / 255.0f) * extend));
            draw.a(matrices, x, y, width, 13.5f, 4.0f, 0.5f, ColorUtil.a(theme.a(ThemeInfo.OUTLINE_SMALL).a(), theme.a(ThemeInfo.OUTLINE_SMALL).b() * extend));
            int color = ColorUtil.a(theme.a(ThemeInfo.TEXT_DISABLED).a(), theme.a(ThemeInfo.TEXT).a(), value);
            Fonts.b.b(matrices, mode, x + (width / 2.0f), (y + ((13.5f - Fonts.b.a(7.5f)) / 2.0f)) - 0.5f, 7.5f, ColorUtil.a(color, extend));
            x += width + 4.5f;
            i++;
        }
        this.a.w = (y + 13.5f) - this.a.y;
    }

    @Override
    public void a(delta.event.DrawEvent event, float x, float y, float width, float animation) {
        ThemeProcessor theme = Delta.h().d().o();
        int primary = theme.a(ThemeInfo.PRIMARY).a();
        float rowHeight = 15.0f;
        float fontSize = 7.5f;

        float textY = y + ((rowHeight - Fonts.e.a(fontSize)) / 2.0f) - 0.5f;
        int textColor = ColorUtil.a(235, 238, 248, (int) (240 * animation));
        Fonts.e.a(event.h(), ((ModeSetting) this.b).i(), x, textY, fontSize, textColor);

        String current = ((ModeSetting) this.b).c();
        float badgeTextW = Fonts.e.a(current, 7.0f);
        float badgePadH = 6.0f;
        float badgeW = badgeTextW + badgePadH * 2.0f;
        float badgeH = 11.5f;
        float badgeR = 3.5f;
        float badgeX = (x + width) - badgeW;
        float badgeY = y + ((rowHeight - badgeH) / 2.0f);

        int badgeBg = ColorUtil.a(28, 30, 42, (int) (225 * animation));
        int badgeBorder = ColorUtil.a(primary, (int) (160 * animation));
        event.d().a(event.h(), badgeX, badgeY, badgeW, badgeH, badgeR, badgeBg);
        event.d().a(event.h(), badgeX, badgeY, badgeW, badgeH, badgeR, 0.5f, badgeBorder);

        float bTextY = badgeY + ((badgeH - Fonts.e.a(7.0f)) / 2.0f) - 0.5f;
        Fonts.e.a(event.h(), current, badgeX + badgePadH, bTextY, 7.0f, ColorUtil.a(255, 255, 255, (int) (255 * animation)));
    }
}
