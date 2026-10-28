package delta.module.render;

import delta.core.Category;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.setting.SliderSetting;
import lombok.Generated;

@ModuleRegister(a = "Smooth Eat", b = "Убирает тряску руки при еде и питье", c = Category.Render)
public class SmoothEat extends Module {
    private final SliderSetting strength = new SliderSetting("Сила тряски", 0.25f, 0.0f, 1.0f, 0.05f);

    public SmoothEat() {
        a(this.strength);
    }

    @Generated
    public SliderSetting getStrengthSetting() {
        return this.strength;
    }

    public float getStrength() {
        return this.strength.c().floatValue() * 0.1f;
    }
}
