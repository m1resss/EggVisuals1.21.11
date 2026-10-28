package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    private final BooleanSetting instant = new BooleanSetting("Instant", true, "Мгновенное возрождение без задержки");

    public AutoRespawn() {
        super("AutoRespawn", "Автоматическое возрождение после гибели", Category.PLAYER);
        addSettings(instant);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) return;
        if (client.player != null && (client.player.isDead() || client.currentScreen instanceof DeathScreen)) {
            client.player.requestRespawn();
            client.setScreen(null);
        }
    }
}
