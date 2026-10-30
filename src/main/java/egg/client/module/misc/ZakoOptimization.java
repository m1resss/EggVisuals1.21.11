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
        sanitizeUnsafeFeatures();
    }

    @Override
    public void onEnable() {
        setZakoOptEnabled(true);
    }

    @Override
    public void onDisable() {
        setZakoOptEnabled(false);
    }

    private void sanitizeUnsafeFeatures() {
        try {
            Class<?> featuresClass = Class.forName("zako.opt.feature.Features");
            for (String featName : new String[]{"FAST_BLIT", "FBO_SHARE", "LAZY_CLEAR", "HUD_CACHE", "GUI_ANIMATED_ITEMS", "RENDER_SCALE"}) {
                try {
                    var fField = featuresClass.getField(featName);
                    Object feat = fField.get(null);
                    if (feat != null) {
                        feat.getClass().getField("value").setBoolean(feat, false);
                        feat.getClass().getField("on").setBoolean(feat, false);
                        if ("HUD_CACHE".equals(featName) || "GUI_ANIMATED_ITEMS".equals(featName)) {
                            try {
                                feat.getClass().getField("broken").setBoolean(feat, true);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> configClass = Class.forName("zako.opt.ZakoOptConfig");
            try {
                var foreignField = configClass.getField("FOREIGN_HUD");
                foreignField.setBoolean(null, true);
            } catch (Throwable ignored) {}
            try {
                var foreignModField = configClass.getField("FOREIGN_HUD_MOD");
                foreignModField.set(null, "egg");
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        sanitizeConfigFile();
    }

    private void sanitizeConfigFile() {
        java.util.List<java.nio.file.Path> paths = new java.util.ArrayList<>();
        try {
            paths.add(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("zakoopt.json"));
        } catch (Throwable ignored) {}
        try {
            String home = System.getProperty("user.home");
            if (home != null) {
                paths.add(java.nio.file.Path.of(home, "AppData", "Roaming", ".minecraft", "config", "zakoopt.json"));
            }
        } catch (Throwable ignored) {}
        paths.add(java.nio.file.Path.of("run", "config", "zakoopt.json"));
        paths.add(java.nio.file.Path.of("config", "zakoopt.json"));

        for (java.nio.file.Path p : paths) {
            try {
                if (java.nio.file.Files.exists(p)) {
                    String content = java.nio.file.Files.readString(p);
                    String updated = content
                            .replaceAll("\"hudCache\"\\s*:\\s*true", "\"hudCache\": false")
                            .replaceAll("\"guiAnimatedItems\"\\s*:\\s*true", "\"guiAnimatedItems\": false")
                            .replaceAll("\"fboShare\"\\s*:\\s*true", "\"fboShare\": false")
                            .replaceAll("\"fastBlit\"\\s*:\\s*true", "\"fastBlit\": false")
                            .replaceAll("\"lazyClear\"\\s*:\\s*true", "\"lazyClear\": false")
                            .replaceAll("\"renderScale\"\\s*:\\s*true", "\"renderScale\": false");
                    if (!updated.equals(content)) {
                        java.nio.file.Files.writeString(p, updated);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void setZakoOptEnabled(boolean enabled) {
        try {
            Class<?> configClass = Class.forName("zako.opt.ZakoOptConfig");
            var field = configClass.getField("enabled");
            field.setBoolean(null, enabled);
            sanitizeUnsafeFeatures();
            var saveMethod = configClass.getMethod("save");
            saveMethod.invoke(null);
            var refreshMethod = configClass.getMethod("refresh");
            refreshMethod.invoke(null);
        } catch (Throwable ignored) {}
    }
}
