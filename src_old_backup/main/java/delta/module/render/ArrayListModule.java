package delta.module.render;

import delta.core.Category;
import delta.core.Delta;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.MultiModeSetting;
import delta.setting.SliderSetting;

@ModuleRegister(a = "ArrayList", b = "Отображает список активных модулей с градиентной волной", c = Category.Render)
public class ArrayListModule extends Module {
    private static ArrayListModule instance;

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Градиент", "Тема", "Градиент", "Радуга");
    private final ColorSetting color1 = new ColorSetting("Цвет 1", 0xFF8B5CF6);
    private final ColorSetting color2 = new ColorSetting("Цвет 2", 0xFF00CEC9);
    private final MultiModeSetting categoryFilter = new MultiModeSetting("Вкладки",
            new BooleanSetting("Combat", true),
            new BooleanSetting("Movement", true),
            new BooleanSetting("Visual", true),
            new BooleanSetting("Player", true),
            new BooleanSetting("Misc", true));
    private final SliderSetting waveSpeed = new SliderSetting("Скорость волны", 2.5f, 0.5f, 6.0f, 0.1f);
    private final SliderSetting waveFreq = new SliderSetting("Частота волны", 0.04f, 0.01f, 0.12f, 0.005f);
    private final BooleanSetting showSideBar = new BooleanSetting("Боковая полоска", true);
    private final SliderSetting sideBarWidth = new SliderSetting("Ширина полоски", 2.0f, 1.0f, 4.0f, 0.5f);
    private final BooleanSetting showBackground = new BooleanSetting("Фон и блюр", true);
    private final SliderSetting rowRounding = new SliderSetting("Скругление", 4.0f, 0.0f, 8.0f, 0.5f);
    private final SliderSetting rowSpacing = new SliderSetting("Отступ между строками", 2.0f, 0.0f, 6.0f, 0.5f);
    private final SliderSetting fontSize = new SliderSetting("Размер шрифта", 7.5f, 6.0f, 10.0f, 0.5f);

    public ArrayListModule() {
        instance = this;
        a(this.colorMode, this.color1, this.color2, this.categoryFilter, this.waveSpeed, this.waveFreq,
                this.showSideBar, this.sideBarWidth, this.showBackground,
                this.rowRounding, this.rowSpacing, this.fontSize);
    }

    public static ArrayListModule getInstance() {
        return instance;
    }

    public MultiModeSetting getCategoryFilter() {
        return this.categoryFilter;
    }

    public ModeSetting getColorMode() {
        return this.colorMode;
    }

    public ColorSetting getColor1() {
        return this.color1;
    }

    public ColorSetting getColor2() {
        return this.color2;
    }

    public SliderSetting getWaveSpeed() {
        return this.waveSpeed;
    }

    public SliderSetting getWaveFreq() {
        return this.waveFreq;
    }

    public BooleanSetting getShowSideBar() {
        return this.showSideBar;
    }

    public SliderSetting getSideBarWidth() {
        return this.sideBarWidth;
    }

    public BooleanSetting getShowBackground() {
        return this.showBackground;
    }

    public SliderSetting getRowRounding() {
        return this.rowRounding;
    }

    public SliderSetting getRowSpacing() {
        return this.rowSpacing;
    }

    public SliderSetting getFontSize() {
        return this.fontSize;
    }

    @Override
    public void b() {
        super.b();
        setWidgetEnabled(true);
    }

    @Override
    public void c() {
        super.c();
        setWidgetEnabled(false);
    }

    private void setWidgetEnabled(boolean enabled) {
        try {
            Interface_2 inter = Delta.h().d().t().bd();
            if (inter != null) {
                BooleanSetting b = inter.getElementsSetting().a("Список модулей");
                if (b != null) {
                    b.a(Boolean.valueOf(enabled));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
