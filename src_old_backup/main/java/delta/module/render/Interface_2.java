package delta.module.render;



import delta.ui.widget.Widget;

import delta.core.Delta;

import delta.core.Interface;

import delta.core.Module;



import delta.config.ThemeInfo;

import delta.core.Category;

import delta.core.EventTarget;

import delta.core.GlobalEvent;

import delta.core.ModuleRegister;

import delta.event.BackendEvent;

import delta.event.DrawEvent;

import delta.event.PacketEvent;

import delta.setting.BooleanSetting;

import delta.ui.widget.ArmorWidget;

import delta.ui.widget.CooldownsWidget;

import delta.ui.widget.HotkeysWidget;

import delta.ui.widget.ItemsWidget;

import delta.ui.widget.NotificationWidget;

import delta.ui.widget.PotionWidget;

import delta.ui.widget.StaffWidget;

import delta.ui.widget.TargetWidget;

import delta.ui.widget.WatermarkWidget;

import delta.ui.widget.ArrayListWidget;

import delta.ui.widget.MediaPlayerWidget;



import delta.setting.ModeSetting;

import delta.setting.ColorSetting;

import delta.setting.MultiModeSetting;

import delta.setting.SliderSetting;

import java.util.ArrayList;

import java.util.List;

import lombok.Generated;



@ModuleRegister(a = "Interface", b = "Отображает выбранные элементы интерфейса на экране", c = Category.Render)

public class Interface_2 extends Module {

    private static Interface_2 instance;

    private final ModeSetting modernTheme = new ModeSetting("Тема меню", "Черная", "Черная", "Белая");

    private final BooleanSetting customHotbar = new BooleanSetting("Кастомный хотбар", true);

    private final BooleanSetting hudThemeGradient = new BooleanSetting("Градиент HUD", false);
    private final SliderSetting hudGradientStrength = new SliderSetting("Сила градиента", 60.0f, 5.0f, 100.0f, 5.0f);

    private final ModeSetting effectMode = new ModeSetting("Отображение эффектов", "Боковой остров", "Боковой остров", "Классический");
    private final ModeSetting effectTimerFormat = new ModeSetting("Формат таймера", "Проценты", "Проценты", "Время");
    private final BooleanSetting effectShowDigits = new BooleanSetting("Цифры таймера", true);

    private final ColorSetting b = new ColorSetting("Глобальный цвет интерфейса", Integer.valueOf(Delta.h().d().o().a(ThemeInfo.PRIMARY).a()));

    private final ColorSetting hudBgColor = new ColorSetting("Цвет фона HUD", 0xD6181A24);

    private final ColorSetting watermarkBgColor = new ColorSetting("Цвет фона ватермарки", 0xD6181A24);
    private final ColorSetting dynamicIslandBgColor = new ColorSetting("Цвет фона айленда", 0xF50A0A0E);

    private final ColorSetting titleColor1 = new ColorSetting("Цвет надписи 1", 0xFF8B5CF6);

    private final ColorSetting titleColor2 = new ColorSetting("Цвет надписи 2", 0xFF00CEC9);

    private final SliderSetting rounding = new SliderSetting("Скругление", 6.5f, 0.0f, 16.0f, 0.5f);

    private final SliderSetting blur = new SliderSetting("Блюр", 45.0f, 0.0f, 100.0f, 5.0f);

    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 215.0f, 20.0f, 255.0f, 5.0f);

    private final BooleanSetting liquidGlass = new BooleanSetting("Жидкое стекло", true);

    private final SliderSetting lgBlur = new SliderSetting("Стекло: Блюр", 85.0f, 0.0f, 100.0f, 5.0f);

    private final SliderSetting lgRefraction = new SliderSetting("Стекло: Преломление", 40.0f, 0.0f, 100.0f, 5.0f);

    private final SliderSetting lgGloss = new SliderSetting("Стекло: Глянец", 70.0f, 0.0f, 100.0f, 5.0f);

    private final SliderSetting lgDispersion = new SliderSetting("Стекло: Дисперсия", 35.0f, 0.0f, 100.0f, 5.0f);

    private final SliderSetting lgOpacity = new SliderSetting("Стекло: Прозрачность", 80.0f, 10.0f, 100.0f, 5.0f);

    private final MultiModeSetting c = new MultiModeSetting("Элементы интерфейса", new BooleanSetting("Клавиши", true), new BooleanSetting("Таргет-худ", true), new BooleanSetting("Задержки", true), new BooleanSetting("Инфо-панель", true), new BooleanSetting("Уведомления", true), new BooleanSetting("Зелья", true), new BooleanSetting("Предметы", true), new BooleanSetting("Броня", true), new BooleanSetting("Стафф", true), new BooleanSetting("Список модулей", true), new BooleanSetting("Проигрыватель", true), new BooleanSetting("Хотбар", true));

    private final List<Widget> d = new ArrayList();



    public static Interface_2 getInstance() {

        return instance != null ? instance : Delta.h().d().t().bd();

    }



    public boolean isHudThemeGradient() {
        return this.hudThemeGradient.c().booleanValue();
    }

    public float getHudGradientStrength() {
        return this.hudGradientStrength.c().floatValue() / 100.0f;
    }

    public ModeSetting getEffectMode() {
        return this.effectMode;
    }

    public ModeSetting getEffectTimerFormat() {
        return this.effectTimerFormat;
    }

    public BooleanSetting getEffectShowDigits() {
        return this.effectShowDigits;
    }

    public ColorSetting getHudBgColor() {

        return this.hudBgColor;

    }



    public ColorSetting getWatermarkBgColor() {

        return this.watermarkBgColor;

    }

    public ColorSetting getDynamicIslandBgColor() {
        return this.dynamicIslandBgColor;
    }



    public ColorSetting getTitleColor1() {

        return this.titleColor1;

    }



    public ColorSetting getTitleColor2() {

        return this.titleColor2;

    }



    public float getRounding() {

        return this.rounding.c().floatValue();

    }



    public ColorSetting getGlobalColorSetting() {
        return this.b;
    }

    public float getBlur() {

        return this.blur.c().floatValue();

    }



    public float getOpacity() {

        return this.opacity.c().floatValue() / 255.0f;

    }



    public boolean isLiquidGlass() {

        return this.liquidGlass.c().booleanValue();

    }



    public float getLgBlur() {

        return this.lgBlur.c().floatValue();

    }



    public float getLgRefraction() {

        return this.lgRefraction.c().floatValue();

    }



    public float getLgGloss() {

        return this.lgGloss.c().floatValue();

    }



    public float getLgDispersion() {

        return this.lgDispersion.c().floatValue();

    }



    public float getLgOpacity() {

        return this.lgOpacity.c().floatValue() / 100.0f;

    }



    public MultiModeSetting getElementsSetting() {

        return this.c;

    }



    public ArrayListWidget getArrayListWidget() {

        for (Widget widget : this.d) {

            if (widget instanceof ArrayListWidget) {

                return (ArrayListWidget) widget;

            }

        }

        return null;

    }



    public NotificationWidget getNotificationWidget() {

        for (Widget widget : this.d) {

            if (widget instanceof NotificationWidget) {

                return (NotificationWidget) widget;

            }

        }

        return null;

    }



    @Generated
    public List<Widget> q() {
        return this.d;
    }

    public MediaPlayerWidget getMediaPlayerWidget() {
        for (Widget widget : this.d) {
            if (widget instanceof MediaPlayerWidget) {
                return (MediaPlayerWidget) widget;
            }
        }
        return null;
    }



    public boolean isModernGui() {
        return true;
    }



    public ModeSetting getModernTheme() {

        return this.modernTheme;

    }



    public boolean isDarkTheme() {

        return this.modernTheme.l("Черная");

    }



    public void setModernTheme(String theme) {

        this.modernTheme.a(theme);

    }



    public boolean isCustomHotbar() {
        if (!this.customHotbar.c().booleanValue()) {
            return false;
        }
        BooleanSetting elementSetting = this.c.a("Хотбар");
        if (elementSetting != null && !elementSetting.c().booleanValue()) {
            return false;
        }
        return true;
    }



    public Interface_2() {

        instance = this;

        this.lgBlur.a(() -> this.liquidGlass.c().booleanValue());

        this.lgRefraction.a(() -> this.liquidGlass.c().booleanValue());

        this.lgGloss.a(() -> this.liquidGlass.c().booleanValue());

        this.lgDispersion.a(() -> this.liquidGlass.c().booleanValue());

        this.lgOpacity.a(() -> this.liquidGlass.c().booleanValue());

        this.blur.a(() -> !this.liquidGlass.c().booleanValue());

        this.effectTimerFormat.a(() -> this.effectMode.l("Боковой остров"));
        this.effectShowDigits.a(() -> this.effectMode.l("Боковой остров"));
        this.hudGradientStrength.a(() -> this.hudThemeGradient.c().booleanValue());

        a(this.customHotbar, this.hudThemeGradient, this.hudGradientStrength, this.effectMode, this.effectTimerFormat, this.effectShowDigits, this.b, this.hudBgColor, this.watermarkBgColor, this.dynamicIslandBgColor, this.titleColor1, this.titleColor2, this.rounding, this.blur, this.opacity, this.liquidGlass, this.lgBlur, this.lgRefraction, this.lgGloss, this.lgDispersion, this.lgOpacity, this.c);

        this.d.add(new ArmorWidget());

        this.d.add(new HotkeysWidget());

        this.d.add(new CooldownsWidget());

        this.d.add(new TargetWidget());

        this.d.add(new WatermarkWidget());

        this.d.add(new PotionWidget());

        this.d.add(new ItemsWidget());

        this.d.add(new NotificationWidget());

        this.d.add(new StaffWidget());
        this.d.add(new ArrayListWidget());
        this.d.add(new MediaPlayerWidget());

    }



    @EventTarget

    public void a(DrawEvent event) {

        if (event.b()) {

            Delta.h().d().o().a(ThemeInfo.PRIMARY).a(this.b.c().intValue());

            for (Widget widget : this.d) {

                if (this.c.a(widget.j().j()).c().booleanValue()) {

                    widget.a(event);

                }

            }

        }

    }



    @EventTarget

    public void a(GlobalEvent event) {

        for (Widget widget : this.d) {

            if (this.c.a(widget.j().j()).c().booleanValue()) {

                widget.a(event);

            }

        }

    }



    @EventTarget

    public void a(PacketEvent event) {

        for (Widget widget : this.d) {

            if (this.c.a(widget.j().j()).c().booleanValue()) {

                widget.a(event);

            }

        }

    }



    @EventTarget

    public void a(BackendEvent event) {

        for (Widget widget : this.d) {

            if (this.c.a(widget.j().j()).c().booleanValue()) {

                widget.a(event);

            }

        }

    }

}

