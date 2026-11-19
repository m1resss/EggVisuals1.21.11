package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;

public class MotionBlur extends Module {
    private static MotionBlur instance;

    public static MotionBlur getInstance() {
        return instance;
    }

    private final ModeSetting mode = new ModeSetting("Режим", "Лунар", "Режим размытия движения", "Лунар", "Шейдер", "Natural");
    private final SliderSetting strength = new SliderSetting("Сила", 5.0, 1.0, 10.0, 0.5, "Интенсивность размытия движения");
    private final ModeSetting naturalProfile = new ModeSetting("Профиль", "Uniform", "Профиль размытия Natural", "Uniform", "Weighted").setVisible(() -> mode.is("Natural"));
    private final BooleanSetting refreshRateScaling = new BooleanSetting("Масштаб по Гц", true, "Адаптация силы размытия к частоте монитора").setVisible(() -> mode.is("Natural"));
    private final BooleanSetting disableInGui = new BooleanSetting("Отключать в меню", true, "Отключать размытие при открытом инвентаре или меню");

    public MotionBlur() {
        super("MotionBlur", "Плавное размытие движения как в Lunar Client", Category.ESP);
        instance = this;
        addSettings(mode, strength, naturalProfile, refreshRateScaling, disableInGui);
    }

    public ModeSetting getMode() {
        return mode;
    }

    public SliderSetting getStrength() {
        return strength;
    }

    public ModeSetting getNaturalProfile() {
        return naturalProfile;
    }

    public BooleanSetting getRefreshRateScaling() {
        return refreshRateScaling;
    }

    public BooleanSetting getDisableInGui() {
        return disableInGui;
    }
}
