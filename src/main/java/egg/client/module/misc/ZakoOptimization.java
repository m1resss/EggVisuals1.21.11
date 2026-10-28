package egg.client.module.misc;

import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ButtonSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;

public class ZakoOptimization extends Module {

    private final ButtonSetting settingsBtn = new ButtonSetting("Настроить", "Открыть панель настроек Zako Optimization", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            try {
                Class<?> screenClass = Class.forName("zako.opt.ZakoOptScreen");
                Screen current = mc.currentScreen;
                Constructor<?> ctor = null;
                for (Constructor<?> c : screenClass.getConstructors()) {
                    if (c.getParameterCount() == 1) {
                        ctor = c;
                        break;
                    }
                }
                if (ctor != null) {
                    Screen screen = (Screen) ctor.newInstance(current);
                    mc.setScreen(screen);
                }
            } catch (Throwable t) {
                EggClient.LOGGER.error("[Egg Visual] Failed to open Zako Optimization screen", t);
            }
        }
    });

    public ZakoOptimization() {
        super("Zako Optimization", "Комплексная оптимизация рендера, сущностей, партиклов и CPU на базе ZakoOpt", Category.MISC);
        addSettings(settingsBtn);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        setZakoOptEnabled(true);
    }

    @Override
    public void onDisable() {
        setZakoOptEnabled(false);
    }

    private void setZakoOptEnabled(boolean enabled) {
        try {
            Class<?> configClass = Class.forName("zako.opt.ZakoOptConfig");
            var field = configClass.getField("enabled");
            field.setBoolean(null, enabled);
            var saveMethod = configClass.getMethod("save");
            saveMethod.invoke(null);
            var refreshMethod = configClass.getMethod("refresh");
            refreshMethod.invoke(null);
        } catch (Throwable ignored) {}
    }
}
