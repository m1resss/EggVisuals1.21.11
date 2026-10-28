package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class ThirdPerson extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "По нажатию", "Режим активации", "По нажатию", "По зажатию");
    private Perspective previousPerspective = Perspective.FIRST_PERSON;

    public ThirdPerson() {
        super("ThirdPerson", "Быстрый свободный обзор от третьего лица", Category.PLAYER);
        addSettings(mode);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            previousPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.setPerspective(previousPerspective != null ? previousPerspective : Perspective.FIRST_PERSON);
        }
    }
}
