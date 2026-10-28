package delta.setting;

import delta.setting.Setting;
import delta.ui.element.ButtonElement;

import delta.core.Action;
import delta.ui.element.Element_2;

public class ButtonSetting extends Setting<Boolean> {
    private final Action a;

    public ButtonSetting(String name, Action action) {
        super(name, false);
        this.a = action;
    }

    public void k() {
        if (this.a != null) {
            this.a.execute();
        }
    }

    @Override
    public Element_2<?> d() {
        return new ButtonElement(this);
    }
}
