package egg.client.module.misc;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;

public class Fun extends Module {
    private static Fun instance;

    private final BooleanSetting raisedArms = new BooleanSetting("Поднятые руки", true, "Противники бегают с поднятыми руками как в старом майнкрафте");

    public Fun() {
        super("Fun", "Развлекательные анимации и визуальные эффекты", Category.MISC);
        instance = this;
        addSettings(this.raisedArms);
    }

    public static Fun getInstance() {
        return instance;
    }

    public boolean isRaisedArms() {
        return this.raisedArms.isEnabled();
    }
}
