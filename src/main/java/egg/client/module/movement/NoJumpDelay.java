package egg.client.module.movement;

import egg.client.mixin.LivingEntityAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import net.minecraft.client.MinecraftClient;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("No Jump Delay", "Убирает задержку между прыжками для непрерывного распрыга", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player != null) {
            ((LivingEntityAccessor) client.player).setJumpingCooldown(0);
        }
    }
}
