package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class NoRender extends Module {
    private final BooleanSetting fire = new BooleanSetting(
            "Огонь",
            true,
            "Отключает оверлей огня на экране"
    );

    private final BooleanSetting pumpkin = new BooleanSetting(
            "Тыква",
            true,
            "Отключает оверлей тыквенной маски"
    );

    private final BooleanSetting totem = new BooleanSetting(
            "Тотем",
            true,
            "Отключает всплывающую анимацию использования тотема"
    );

    private final BooleanSetting blindness = new BooleanSetting(
            "Слепота",
            true,
            "Отключает эффекты слепоты и темноты"
    );

    private final BooleanSetting fog = new BooleanSetting(
            "Туман",
            true,
            "Отключает густой туман в мире"
    );

    private final BooleanSetting hurtCam = new BooleanSetting(
            "Тряска камеры",
            false,
            "Отключает тряску камеры при получении урона"
    );

    public NoRender() {
        super("No Render", "Отключение визуальных оверлеев и мешающих эффектов", Category.ESP);
        addSettings(fire, pumpkin, totem, blindness, fog, hurtCam);
    }

    public BooleanSetting getFire() {
        return fire;
    }

    public BooleanSetting getPumpkin() {
        return pumpkin;
    }

    public BooleanSetting getTotem() {
        return totem;
    }

    public BooleanSetting getBlindness() {
        return blindness;
    }

    public BooleanSetting getFog() {
        return fog;
    }

    public BooleanSetting getHurtCam() {
        return hurtCam;
    }
}
