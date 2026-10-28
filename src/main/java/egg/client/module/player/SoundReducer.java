package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;

public class SoundReducer extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Приглушение", "Способ обработки звуков", "Приглушение", "Отключение");
    private final SliderSetting volume = new SliderSetting("Volume", 0.3, 0.0, 1.0, 0.05, "Громкость приглушенного звука");
    private final BooleanSetting potionsAndExp = new BooleanSetting("EXP / Potions", true, "Зелья и опыт");
    private final BooleanSetting tntAndCreeper = new BooleanSetting("Explosions", true, "Взрывы криперов и ТНТ");
    private final BooleanSetting anvil = new BooleanSetting("Anvil", true, "Звук наковальни");
    private final BooleanSetting portal = new BooleanSetting("Nether Portal", true, "Звук адского портала");
    private final BooleanSetting wither = new BooleanSetting("Wither", true, "Звук иссушителя");
    private final BooleanSetting totem = new BooleanSetting("Totem", false, "Звук тотема бессмертия");

    public SoundReducer() {
        super("SoundReducer", "Уменьшает или отключает громкость резких игровых звуков", Category.PLAYER);
        addSettings(mode, volume, potionsAndExp, tntAndCreeper, anvil, portal, wither, totem);
    }

    public boolean shouldReduce(String path) {
        if (!isEnabled()) return false;
        if (potionsAndExp.get() && (path.contains("splash_potion") || path.contains("experience_orb") || path.contains("lingering_potion"))) return true;
        if (tntAndCreeper.get() && (path.contains("creeper") || path.contains("explode") || path.contains("tnt"))) return true;
        if (anvil.get() && path.contains("anvil")) return true;
        if (portal.get() && path.contains("portal")) return true;
        if (wither.get() && path.contains("wither") && !path.contains("wither_skeleton")) return true;
        if (totem.get() && path.contains("totem")) return true;
        return false;
    }

    public float getReducedVolume(float original) {
        if (mode.is("Отключение")) return 0.0f;
        return (float) (original * volume.get());
    }
}
