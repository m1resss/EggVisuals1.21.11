package egg.client.module.combat;

import egg.client.mixin.MinecraftClientInvoker;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

public class TapeMouse extends Module {
    private final ModeSetting buttonMode = new ModeSetting("Button", "Правая", "Кнопка мыши для клика", "Правая", "Левая");
    private final SliderSetting delay = new SliderSetting("Delay (ms)", 1000.0, 50.0, 4000.0, 50.0, "Задержка между кликами в мс");
    private final BooleanSetting pauseOnEat = new BooleanSetting("Pause on Eat", true, "Не кликать во время использования предметов/еды");

    private long lastClickTime = 0L;

    public TapeMouse() {
        super("TapeMouse", "Автоматически кликает выбранной кнопкой мыши через интервалы", Category.COMBAT);
        addSettings(buttonMode, delay, pauseOnEat);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (pauseOnEat.get() && client.player.isUsingItem()) return;

        long now = System.currentTimeMillis();
        if (now - lastClickTime >= (long) delay.get()) {
            lastClickTime = now;
            MinecraftClientInvoker invoker = (MinecraftClientInvoker) client;
            if (buttonMode.is("Правая")) {
                invoker.invokeDoItemUse();
            } else {
                invoker.invokeDoAttack();
            }
        }
    }
}
