package egg.client.module.misc;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.fabricmc.loader.api.FabricLoader;

public class Fun extends Module {
    private static Fun instance;
    private static final boolean HAS_SMOOTH_ANIMATIONS = FabricLoader.getInstance().isModLoaded("spa_packs");
    private static final boolean HAS_LOCOMOTION = FabricLoader.getInstance().isModLoaded("locomotion");

    private final BooleanSetting raisedArms = new BooleanSetting("Поднятые руки", true, "Противники бегают с поднятыми руками как в старом майнкрафте");
    private final BooleanSetting bedrockRun = new BooleanSetting("Bedrock Бег", false, "Руки и ноги игроков едва двигаются во время спринта");

    private final BooleanSetting smoothAnimations = new BooleanSetting("SmoothPlayerAnimations", false,
            HAS_SMOOTH_ANIMATIONS ? "Плавная ходьба и бег от третьего лица" : "Требуется мод SmoothPlayerAnimations");
    private final BooleanSetting locomotion = new BooleanSetting("Locomotion", false,
            HAS_LOCOMOTION ? "Анимации рук и предметов от первого лица" : "Требуется мод Locomotion");

    public Fun() {
        super("Fun", "Визуальные эффекты вращения и движения персонажа", Category.MISC);
        instance = this;
        addSettings(raisedArms, bedrockRun, smoothAnimations, locomotion);
    }

    public static Fun getInstance() {
        return instance;
    }

    public boolean isRaisedArms() {
        return raisedArms.isEnabled() && !usesSmoothAnimations();
    }

    public boolean isBedrockRun() {
        return isEnabled() && bedrockRun.isEnabled() && !usesSmoothAnimations();
    }

    public static boolean usesSmoothAnimations() {
        Fun fun = instance;
        return HAS_SMOOTH_ANIMATIONS && fun != null && fun.isEnabled() && fun.smoothAnimations.isEnabled();
    }

    public static boolean usesLocomotion() {
        Fun fun = instance;
        return HAS_LOCOMOTION && fun != null && fun.isEnabled() && fun.locomotion.isEnabled();
    }
}
