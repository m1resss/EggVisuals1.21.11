package delta.setting;

import delta.setting.Setting;
import delta.ui.element.BooleanElement;

import delta.ui.element.Element_2;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, Boolean defaultVal) {
        super(name, defaultVal);
    }

    @Override
    public Element_2<?> d() {
        return new BooleanElement(this);
    }
}
