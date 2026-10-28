package delta.setting;

import delta.setting.Setting;
import delta.ui.element.ColorElement;

import delta.ui.element.Element_2;

public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, Integer defaultVal) {
        super(name, defaultVal);
    }

    @Override
    public Element_2<?> d() {
        return new ColorElement(this);
    }
}
