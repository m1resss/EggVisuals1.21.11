package egg.client.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import egg.client.EggClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class InventoryLayoutManager {
    private static final InventoryLayoutManager INSTANCE = new InventoryLayoutManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final List<InventoryPreset> presets = new ArrayList<>();
    private int activeIndex = 0;
    private final File configFile;

    private InventoryLayoutManager() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        File eggDir = gameDir.resolve("egg").toFile();
        if (!eggDir.exists()) {
            eggDir.mkdirs();
        }
        configFile = new File(eggDir, "inventory_layouts.json");
        load();
    }

    public static InventoryLayoutManager getInstance() {
        return INSTANCE;
    }

    public synchronized void load() {
        presets.clear();

        File fileToRead = configFile;

        // If local instance file is missing or empty, check global .minecraft fallback
        if (!fileToRead.exists() || fileToRead.length() < 100) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                File fallback = new File(appdata, ".minecraft/egg/inventory_layouts.json");
                if (fallback.exists() && fallback.length() >= 100) {
                    try {
                        Files.copy(fallback.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        EggClient.LOGGER.info("[InventoryLayoutManager] Restored presets from global fallback: " + fallback.getAbsolutePath());
                    } catch (Throwable ignored) {
                        fileToRead = fallback;
                    }
                }
            }
        }

        if (fileToRead.exists() && fileToRead.length() > 0) {
            try (Reader reader = Files.newBufferedReader(fileToRead.toPath(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null && root.has("presets") && root.get("presets").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("presets")) {
                        if (el.isJsonObject()) {
                            presets.add(InventoryPreset.fromJson(el.getAsJsonObject()));
                        }
                    }
                    if (root.has("active")) {
                        String activeName = root.get("active").getAsString();
                        for (int i = 0; i < presets.size(); i++) {
                            if (presets.get(i).getName().equalsIgnoreCase(activeName)) {
                                activeIndex = i;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                EggClient.LOGGER.error("[InventoryLayoutManager] Failed to load inventory presets from " + fileToRead.getAbsolutePath(), e);
                // Create backup of corrupted file instead of silently wiping it
                try {
                    File bak = new File(fileToRead.getParentFile(), fileToRead.getName() + ".corrupted");
                    Files.copy(fileToRead.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable ignored) {}
            }
        }

        if (presets.isEmpty()) {
            presets.add(new InventoryPreset("1"));
            activeIndex = 0;
            save();
        }

        if (activeIndex < 0 || activeIndex >= presets.size()) {
            activeIndex = 0;
        }
    }

    public synchronized void save() {
        try {
            if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            if (!presets.isEmpty() && activeIndex >= 0 && activeIndex < presets.size()) {
                root.addProperty("active", presets.get(activeIndex).getName());
            }
            JsonArray arr = new JsonArray();
            for (InventoryPreset p : presets) {
                arr.add(p.toJson());
            }
            root.add("presets", arr);

            try (Writer writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            // Sync copy to global .minecraft/egg/inventory_layouts.json so Prism and other launchers share presets
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                File fallbackDir = new File(appdata, ".minecraft/egg");
                if (fallbackDir.exists() || fallbackDir.mkdirs()) {
                    File fallbackFile = new File(fallbackDir, "inventory_layouts.json");
                    if (!fallbackFile.getAbsolutePath().equalsIgnoreCase(configFile.getAbsolutePath())) {
                        try (Writer fallbackWriter = Files.newBufferedWriter(fallbackFile.toPath(), StandardCharsets.UTF_8)) {
                            GSON.toJson(root, fallbackWriter);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            EggClient.LOGGER.error("[InventoryLayoutManager] Failed to save inventory presets", e);
        }
    }

    public synchronized InventoryPreset getActivePreset() {
        if (presets.isEmpty()) {
            return null;
        }
        if (activeIndex < 0 || activeIndex >= presets.size()) {
            activeIndex = 0;
        }
        return presets.get(activeIndex);
    }

    public synchronized void setActivePreset(String name) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getName().equalsIgnoreCase(name)) {
                activeIndex = i;
                save();
                return;
            }
        }
    }

    public synchronized void nextPreset() {
        if (presets.isEmpty()) return;
        activeIndex = (activeIndex + 1) % presets.size();
        save();
    }

    public synchronized void prevPreset() {
        if (presets.isEmpty()) return;
        activeIndex = (activeIndex - 1 + presets.size()) % presets.size();
        save();
    }

    public synchronized List<String> getPresetNames() {
        List<String> list = new ArrayList<>();
        for (InventoryPreset p : presets) {
            list.add(p.getName());
        }
        return list;
    }

    public synchronized List<InventoryPreset> getPresets() {
        return presets;
    }

    public synchronized void saveCurrentAsPreset(String name, PlayerInventory playerInv) {
        if (playerInv == null) return;
        InventoryPreset target = null;
        for (InventoryPreset p : presets) {
            if (p.getName().equalsIgnoreCase(name)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            target = new InventoryPreset(name);
            presets.add(target);
            activeIndex = presets.size() - 1;
        }
        target.snapshotFromPlayer(playerInv);
        save();
    }

    public synchronized InventoryPreset createNewPreset(String name) {
        for (InventoryPreset p : presets) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        InventoryPreset p = new InventoryPreset(name);
        presets.add(p);
        activeIndex = presets.size() - 1;
        save();
        return p;
    }

    public synchronized boolean deleteCurrentPreset() {
        if (presets.isEmpty()) return false;
        presets.remove(activeIndex);
        if (activeIndex >= presets.size()) {
            activeIndex = Math.max(0, presets.size() - 1);
        }
        save();
        return true;
    }
}
