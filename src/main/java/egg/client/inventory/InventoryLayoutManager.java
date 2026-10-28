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
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InventoryLayoutManager {
    private static final InventoryLayoutManager INSTANCE = new InventoryLayoutManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
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
                EggClient.LOGGER.error("[InventoryLayoutManager] Failed to load inventory presets", e);
            }
        }
    }

    public synchronized void save() {
        try (FileWriter writer = new FileWriter(configFile)) {
            JsonObject root = new JsonObject();
            if (!presets.isEmpty() && activeIndex >= 0 && activeIndex < presets.size()) {
                root.addProperty("active", presets.get(activeIndex).getName());
            }
            JsonArray arr = new JsonArray();
            for (InventoryPreset p : presets) {
                arr.add(p.toJson());
            }
            root.add("presets", arr);
            GSON.toJson(root, writer);
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
