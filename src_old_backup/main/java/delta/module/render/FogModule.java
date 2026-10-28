package delta.module.render;

import static delta.core.Interface.aM_;
import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.AmbienceEvent;
import delta.render.ColorUtil;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import delta.render.FogShape;

@ModuleRegister(a = "Fog", b = "Настраиваемый туман, цвет, дистанция и плотность", c = Category.Render)
public class FogModule extends Module {
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Кастом");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF8B5CF6).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));
    private final ModeSetting shape = new ModeSetting("Форма", "Сфера", "Сфера", "Цилиндр");
    private final SliderSetting fogStart = new SliderSetting("Начало", 5.0f, -50.0f, 100.0f, 1.0f);
    private final SliderSetting fogDistance = new SliderSetting("Дальность", 60.0f, 5.0f, 250.0f, 5.0f);
    private final SliderSetting density = new SliderSetting("Плотность", 1.0f, 0.2f, 3.0f, 0.05f);

    public FogModule() {
        a(this.colorMode, this.customColor, this.shape, this.fogStart, this.fogDistance, this.density);
    }

    @EventTarget
    public void onFogColor(AmbienceEvent.a event) {
        if (!isEnabled()) return;
        int color;
        if (this.colorMode.l("Кастом")) {
            color = this.customColor.c().intValue();
        } else {
            color = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        }
        float[] rgba = ColorUtil.a(color);
        event.a(rgba[0]);
        event.b(rgba[1]);
        event.c(rgba[2]);
        event.d(rgba[3]);
        event.a(true);
    }

    @EventTarget
    public void onFogDistance(AmbienceEvent.b event) {
        if (!isEnabled()) return;
        int color;
        if (this.colorMode.l("Кастом")) {
            color = this.customColor.c().intValue();
        } else {
            color = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        }
        float[] rgba = ColorUtil.a(color);
        FogShape fogShape = this.shape.l("Цилиндр") ? FogShape.CYLINDER : FogShape.SPHERE;
        float start = this.fogStart.c().floatValue();
        float end = Math.max(start + 5.0f, this.fogDistance.c().floatValue());
        event.a(start, end, fogShape, rgba[0], rgba[1], rgba[2], rgba[3]);
    }
}
