package egg.client.module.movement;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final BooleanSetting keepSprint = new BooleanSetting(
            "Keep Sprint",
            true,
            "Удерживать спринт при атаке или получении урона"
    );

    public Sprint() {
        super("Sprint", "Автоматический спринт при движении вперед", Category.MOVEMENT);
        addSettings(keepSprint);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        boolean canSprint = !client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6;
        boolean movingForward = client.options.forwardKey.isPressed();

        if (canSprint && movingForward) {
            client.player.setSprinting(true);
        }
    }

    public BooleanSetting getKeepSprint() {
        return keepSprint;
    }
}
