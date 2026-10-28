package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.SliderSetting;

/**
 * 1:1 SmoothEat port from egg 1.21.4 (delta.module.render.SmoothEat).
 */
public class SmoothEat extends Module {
    private final SliderSetting strength = new SliderSetting("Сила тряски", 0.25, 0.0, 1.0, 0.05, "Интенсивность тряски при поедании");

    public SmoothEat() {
        super("Smooth Eat", "Убирает тряску руки при еде и питье", Category.ESP);
        addSettings(strength);
    }

    public float getStrength() {
        return (float) this.strength.get() * 0.1f;
    }
}
