package egg.client.config;

import com.google.gson.*;
import egg.client.EggClient;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR;
    private static final File ACTIVE_CONFIG_FILE;
    private static String currentConfig = "test1";

    // 2-minute recurring autosave executor
    private static final ScheduledExecutorService AUTOSAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Egg-Config-AutoSave");
        t.setDaemon(true);
        return t;
    });

    static {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        CONFIG_DIR = gameDir.resolve("egg").resolve("configs").toFile();
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        ACTIVE_CONFIG_FILE = new File(CONFIG_DIR, "active_config.txt");

        // Migrate legacy single config or bundle if default.json does not exist yet
        File defaultFile = new File(CONFIG_DIR, "default.json");
        if (!defaultFile.exists()) {
            File legacyFile = FabricLoader.getInstance().getConfigDir().resolve("egg_client.json").toFile();
            if (legacyFile.exists()) {
                try {
                    java.nio.file.Files.copy(legacyFile.toPath(), defaultFile.toPath());
                    EggClient.LOGGER.info("[Egg Visual] Migrated legacy egg_client.json to egg/configs/default.json");
                } catch (Exception e) {
                    EggClient.LOGGER.error("[Egg Visual] Failed to migrate legacy config", e);
                }
            } else {
                try (java.io.InputStream in = ConfigManager.class.getResourceAsStream("/assets/egg/default_config.json")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, defaultFile.toPath());
                        File test1File = new File(CONFIG_DIR, "test1.json");
                        if (!test1File.exists()) {
                            java.nio.file.Files.copy(defaultFile.toPath(), test1File.toPath());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Restore active config name from active_config.txt
        if (ACTIVE_CONFIG_FILE.exists()) {
            try {
                String saved = java.nio.file.Files.readString(ACTIVE_CONFIG_FILE.toPath()).trim();
                if (!saved.isEmpty() && getConfigFile(saved).exists()) {
                    currentConfig = saved;
                }
            } catch (Exception ignored) {}
        } else if (new File(CONFIG_DIR, "test1.json").exists()) {
            currentConfig = "test1";
        } else if (new File(CONFIG_DIR, "default.json").exists()) {
            currentConfig = "default";
        }

        // Schedule autosave every 2 minutes (120 seconds)
        AUTOSAVE_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                save();
                EggClient.LOGGER.info("[Egg Visual] Config autosaved: " + currentConfig);
            } catch (Throwable t) {
                EggClient.LOGGER.error("[Egg Visual] Background autosave failed", t);
            }
        }, 2, 2, TimeUnit.MINUTES);

        // JVM shutdown hook save
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
                egg.client.inventory.InventoryLayoutManager.getInstance().save();
            } catch (Throwable ignored) {}
        }, "Egg-Config-ShutdownSave"));
    }

    private static void persistActiveConfig(String name) {
        try {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }
            java.nio.file.Files.writeString(ACTIVE_CONFIG_FILE.toPath(), name);
        } catch (Exception ignored) {}
    }

    public static File getConfigDir() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        return CONFIG_DIR;
    }

    public static String getCurrentConfig() {
        return currentConfig;
    }

    public static File getConfigFile(String name) {
        String clean = (name == null || name.trim().isEmpty()) ? "default" : name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (clean.isEmpty()) clean = "default";
        return new File(getConfigDir(), clean + ".json");
    }

    public static synchronized boolean save() {
        return save(currentConfig);
    }

    public static synchronized boolean save(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = currentConfig;
        }
        String cleanName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (cleanName.isEmpty()) cleanName = "default";

        File file = getConfigFile(cleanName);
        try {
            JsonObject root = new JsonObject();
            JsonObject modulesObj = new JsonObject();

            for (Module m : ModuleManager.getInstance().getModules()) {
                JsonObject mObj = new JsonObject();
                mObj.addProperty("enabled", m.isEnabled());
                mObj.addProperty("favorite", m.isFavorite());
                mObj.addProperty("key", m.getKey());

                JsonObject settingsObj = new JsonObject();
                for (Setting s : m.getSettings()) {
                    if (s instanceof BooleanSetting b) {
                        settingsObj.addProperty(s.getName(), b.get());
                    } else if (s instanceof SliderSetting sl) {
                        settingsObj.addProperty(s.getName(), sl.get());
                    } else if (s instanceof ModeSetting md) {
                        settingsObj.addProperty(s.getName(), md.get());
                    } else if (s instanceof ColorSetting cs) {
                        settingsObj.addProperty(s.getName(), cs.get());
                    } else if (s instanceof StringSetting ss) {
                        settingsObj.addProperty(s.getName(), ss.get());
                    }
                }
                mObj.add("settings", settingsObj);
                modulesObj.add(m.getName(), mObj);
            }

            root.add("modules", modulesObj);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }

            currentConfig = cleanName;
            persistActiveConfig(cleanName);
            try {
                egg.client.inventory.InventoryLayoutManager.getInstance().save();
            } catch (Throwable ignored) {}
            return true;
        } catch (Exception e) {
            EggClient.LOGGER.error("[Egg Visual] Failed to save config: " + file.getName(), e);
            return false;
        }
    }

    public static synchronized boolean load() {
        return load(currentConfig);
    }

    public static synchronized boolean load(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = currentConfig;
        }
        String cleanName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (cleanName.isEmpty()) cleanName = "default";

        File file = getConfigFile(cleanName);
        if (!file.exists()) {
            // Fallback to default if requested was different
            if (!cleanName.equals("default")) {
                return false;
            }
            return false;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("modules")) {
                JsonObject modulesObj = root.getAsJsonObject("modules");
                for (Module m : ModuleManager.getInstance().getModules()) {
                    if (modulesObj.has(m.getName())) {
                        JsonObject mObj = modulesObj.getAsJsonObject(m.getName());
                        if (mObj.has("favorite")) {
                            m.setFavorite(mObj.get("favorite").getAsBoolean());
                        }
                        if (mObj.has("enabled")) {
                            m.setEnabled(mObj.get("enabled").getAsBoolean());
                        }
                        if (mObj.has("key")) {
                            m.setKey(mObj.get("key").getAsInt());
                        }
                        if (mObj.has("settings")) {
                            JsonObject settingsObj = mObj.getAsJsonObject("settings");
                            for (Setting s : m.getSettings()) {
                                if (settingsObj.has(s.getName())) {
                                    JsonElement el = settingsObj.get(s.getName());
                                    if (s instanceof BooleanSetting b && el.isJsonPrimitive()) {
                                        b.set(el.getAsBoolean());
                                    } else if (s instanceof SliderSetting sl && el.isJsonPrimitive()) {
                                        sl.set(el.getAsDouble());
                                    } else if (s instanceof ModeSetting md && el.isJsonPrimitive()) {
                                        md.set(el.getAsString());
                                    } else if (s instanceof ColorSetting cs && el.isJsonPrimitive()) {
                                        cs.set(el.getAsInt());
                                    } else if (s instanceof StringSetting ss && el.isJsonPrimitive()) {
                                        ss.set(el.getAsString());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            currentConfig = cleanName;
            persistActiveConfig(cleanName);
            try {
                egg.client.inventory.InventoryLayoutManager.getInstance().load();
                egg.client.module.player.InventorySorter.getInstance().updatePresetModes();
            } catch (Throwable ignored) {}
            return true;
        } catch (Exception e) {
            EggClient.LOGGER.error("[Egg Visual] Failed to load config: " + file.getName(), e);
            return false;
        }
    }

    public static synchronized boolean delete(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String cleanName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "");
        File file = getConfigFile(cleanName);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted && cleanName.equals(currentConfig)) {
                currentConfig = new File(CONFIG_DIR, "test1.json").exists() ? "test1" : "default";
                persistActiveConfig(currentConfig);
            }
            return deleted;
        }
        return false;
    }

    public static List<String> getConfigs() {
        List<String> list = new ArrayList<>();
        File dir = getConfigDir();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName();
                list.add(n.substring(0, n.length() - 5));
            }
        }
        return list;
    }

    public static void openConfigDir() {
        File dir = getConfigDir();
        try {
            Util.getOperatingSystem().open(dir);
        } catch (Throwable t) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(dir);
                }
            } catch (Throwable ignored) {}
        }
    }
}
