package egg.client.module.misc;

import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ButtonSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ZakoOptimization extends Module {

    private int tickCounter = 0;

    private static final String[] UNSAFE_FEATURE_NAMES = new String[]{
            "FAST_BLIT",
            "FBO_SHARE",
            "LAZY_CLEAR",
            "HUD_CACHE",
            "GUI_ANIMATED_ITEMS",
            "RENDER_SCALE",
            "IMMEDIATE_RING",
            "RING_ZERO_COPY",
            "LUNAR_MULTIDRAW",
            "FRAME_FENCE"
    };

    private static final String[] UNSAFE_CONFIG_KEYS = new String[]{
            "hudCache",
            "guiAnimatedItems",
            "fboShare",
            "fastBlit",
            "lazyClear",
            "renderScale",
            "immediateRing",
            "immediateRingForce",
            "ringZeroCopy",
            "lunarMultiDraw",
            "frameFence"
    };

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

    @Override
    public void onTick(MinecraftClient mc) {
        tickCounter++;
        if (tickCounter >= 40) {
            tickCounter = 0;
            sanitizeUnsafeFeatures();
        }
    }

    private void sanitizeUnsafeFeatures() {
        try {
            Class<?> featuresClass = Class.forName("zako.opt.feature.Features");
            for (String featName : UNSAFE_FEATURE_NAMES) {
                try {
                    var fField = featuresClass.getField(featName);
                    Object feat = fField.get(null);
                    if (feat != null) {
                        feat.getClass().getField("value").setBoolean(feat, false);
                        feat.getClass().getField("on").setBoolean(feat, false);
                        try {
                            feat.getClass().getField("broken").setBoolean(feat, true);
                        } catch (Throwable ignored) {}
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
        List<Path> paths = new ArrayList<>();
        try {
            paths.add(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("zakoopt.json"));
        } catch (Throwable ignored) {}

        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            paths.add(Path.of(appdata, ".minecraft", "config", "zakoopt.json"));
            paths.add(Path.of(appdata, "PrismLauncher", "instances", "Fabulously Optimized", "minecraft", "config", "zakoopt.json"));
        }

        String home = System.getProperty("user.home");
        if (home != null) {
            paths.add(Path.of(home, "AppData", "Roaming", ".minecraft", "config", "zakoopt.json"));
        }

        paths.add(Path.of("run", "config", "zakoopt.json"));
        paths.add(Path.of("config", "zakoopt.json"));

        for (Path p : paths) {
            try {
                if (Files.exists(p)) {
                    String content = Files.readString(p);
                    String updated = content;
                    for (String key : UNSAFE_CONFIG_KEYS) {
                        updated = updated.replaceAll("\"" + key + "\"\\s*:\\s*true", "\"" + key + "\": false");
                    }
                    if (!updated.equals(content)) {
                        Files.writeString(p, updated);
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
