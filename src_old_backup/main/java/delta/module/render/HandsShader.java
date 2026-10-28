package delta.module.render;

import delta.core.Interface;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;
import delta.render.ColorUtil;

import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.HandEvent;
import delta.ui.shader.NoiseShader;

import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.client.option.Perspective;

@ModuleRegister(a = "Hands", b = "Накладывает стильный шейдер и эффекты на руку от первого лица", c = Category.Render)
public class HandsShader extends Module {
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 0.6f, 0.0f, 1.0f, 0.05f);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Кастом", "Радуга");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF8B5CF6).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));
    private final SliderSetting speed = new SliderSetting("Скорость", 1.0f, 0.2f, 3.0f, 0.1f);
    private final SliderSetting intensity = new SliderSetting("Интенсивность", 0.85f, 0.1f, 2.0f, 0.05f);

    public HandsShader() {
        a(this.opacity, this.colorMode, this.customColor, this.speed, this.intensity);
    }

    @EventTarget
    public void a(HandEvent event) {
        NoiseShader shader = Delta.h().d().i().f();
        if (aM_.options.getPerspective() == Perspective.FIRST_PERSON) {
            if (event.b()) {
                shader.e();
            }
            if (event.c()) {
                int base;
                if (this.colorMode.l("Кастом")) {
                    base = this.customColor.c().intValue();
                } else if (this.colorMode.l("Радуга")) {
                    float hue = ((System.currentTimeMillis() % 3000L) / 3000.0f);
                    base = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
                } else {
                    base = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
                }
                float[] color = ColorUtil.a(base);
                color[3] = this.opacity.c().floatValue() * this.intensity.c().floatValue();
                shader.a(color);
            }
        }
    }
}
