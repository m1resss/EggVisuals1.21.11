package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;

/**
 * 1:1 AspectRatio port from egg 1.21.4 (delta.module.render.AspectRatio).
 */
public class AspectRatio extends Module {
    private final ModeSetting mode = new ModeSetting("Соотношение сторон", "Пользовательский", "Режим соотношения",
            "4:3", "16:9", "1:1", "16:10", "Пользовательский");
    private final SliderSetting customRatio = new SliderSetting("Соотношение", 1.9, 0.1, 5.0, 0.1, "Пользовательское значение соотношения");

    public AspectRatio() {
        super("Aspect Ratio", "Изменяет соотношение сторон экрана", Category.ESP);
        addSettings(mode, customRatio);
    }

    public float getRatio() {
        if (!isEnabled()) return 1.0f;
        String val = this.mode.get();
        return switch (val) {
            case "4:3" -> 1.3333334f;
            case "16:9" -> 1.7777778f;
            case "1:1" -> 1.0f;
            case "16:10" -> 1.6f;
            default -> (float) this.customRatio.get();
        };
    }
}
