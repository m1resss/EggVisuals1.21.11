package egg.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CosmeticsConfigManager {

    private static final CosmeticsConfigManager INSTANCE = new CosmeticsConfigManager();

    private final File configFile;
    private final List<CosmeticPreset> presets = new ArrayList<>();
    private String currentPresetName = "Основной";

    public static class CosmeticPreset {
        public String name;
        public String wings = "";
        public String backpacks = "";
        public String hats = "";
        public String masks = "";
        public String pets = "";
        public String sword = "";
        public String swordModel = "";

        public CosmeticPreset(String name) {
            this.name = name;
        }

        public int getEquippedCount() {
            int count = 0;
            if (wings != null && !wings.isEmpty()) count++;
            if (backpacks != null && !backpacks.isEmpty()) count++;
            if (hats != null && !hats.isEmpty()) count++;
            if (masks != null && !masks.isEmpty()) count++;
            if (pets != null && !pets.isEmpty()) count++;
            if (sword != null && !sword.isEmpty()) count++;
            return count;
        }
    }

    private CosmeticsConfigManager() {
        File runDir = MinecraftClient.getInstance() != null && MinecraftClient.getInstance().runDirectory != null
                ? MinecraftClient.getInstance().runDirectory
                : new File(".");
        File configsDir = new File(runDir, "configs");
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
        this.configFile = new File(configsDir, "cosmetics_presets.json");
    }

    public static CosmeticsConfigManager getInstance() {
        return INSTANCE;
    }

    public List<CosmeticPreset> getPresets() {
        return this.presets;
    }

    public String getCurrentPresetName() {
        return this.currentPresetName;
    }

    public void setCurrentPresetName(String currentPresetName) {
        this.currentPresetName = currentPresetName;
        saveConfig();
    }

    public void saveCurrentAsPreset(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Пресет " + (this.presets.size() + 1);
        }
        name = name.trim();

        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null) return;

        CosmeticPreset target = null;
        for (CosmeticPreset p : this.presets) {
            if (p.name.equalsIgnoreCase(name)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            target = new CosmeticPreset(name);
            this.presets.add(target);
        }

        target.wings = mod.selected("wings");
        target.backpacks = mod.selected("backpacks");
        target.hats = mod.selected("hats");
        target.masks = mod.selected("masks");
        target.pets = mod.selected("pets");
        target.sword = mod.selectedSword();
        target.swordModel = mod.selectedSwordModel();

        this.currentPresetName = name;
        saveConfig();
    }

    public void applyPreset(CosmeticPreset preset) {
        if (preset == null) return;
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null) return;

        this.currentPresetName = preset.name;
        mod.select("wings", preset.wings);
        mod.select("backpacks", preset.backpacks);
        mod.select("hats", preset.hats);
        mod.select("masks", preset.masks);
        mod.select("pets", preset.pets);
        mod.selectSword(preset.sword, preset.swordModel);

        saveConfig();
    }

    public void deletePreset(CosmeticPreset preset) {
        if (preset == null) return;
        this.presets.remove(preset);
        if (this.currentPresetName.equalsIgnoreCase(preset.name)) {
            this.currentPresetName = this.presets.isEmpty() ? "Основной" : this.presets.get(0).name;
        }
        saveConfig();
    }

    public void clearEquipped() {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod != null) {
            mod.clearAll();
        }
        this.currentPresetName = "Основной";
        saveConfig();
    }

    public void loadConfig() {
        if (!this.configFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(this.configFile, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            CosmeticsModule mod = CosmeticsModule.getInstance();

            if (json.has("current_preset")) {
                this.currentPresetName = json.get("current_preset").getAsString();
            }

            if (mod != null && json.has("equipped")) {
                JsonObject eq = json.getAsJsonObject("equipped");
                if (eq.has("wings")) mod.select("wings", eq.get("wings").getAsString());
                if (eq.has("backpacks")) mod.select("backpacks", eq.get("backpacks").getAsString());
                if (eq.has("hats")) mod.select("hats", eq.get("hats").getAsString());
                if (eq.has("masks")) mod.select("masks", eq.get("masks").getAsString());
                if (eq.has("pets")) mod.select("pets", eq.get("pets").getAsString());

                String sw = eq.has("sword") ? eq.get("sword").getAsString() : "";
                String swM = eq.has("sword_model") ? eq.get("sword_model").getAsString() : "";
                mod.selectSword(sw, swM);
            }

            this.presets.clear();
            if (json.has("presets") && json.get("presets").isJsonArray()) {
                JsonArray arr = json.getAsJsonArray("presets");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Пресет";
                    CosmeticPreset preset = new CosmeticPreset(name);
                    if (obj.has("wings")) preset.wings = obj.get("wings").getAsString();
                    if (obj.has("backpacks")) preset.backpacks = obj.get("backpacks").getAsString();
                    if (obj.has("hats")) preset.hats = obj.get("hats").getAsString();
                    if (obj.has("masks")) preset.masks = obj.get("masks").getAsString();
                    if (obj.has("pets")) preset.pets = obj.get("pets").getAsString();
                    if (obj.has("sword")) preset.sword = obj.get("sword").getAsString();
                    if (obj.has("sword_model")) preset.swordModel = obj.get("sword_model").getAsString();
                    this.presets.add(preset);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void saveConfig() {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null) return;

        try (FileWriter writer = new FileWriter(this.configFile, StandardCharsets.UTF_8)) {
            JsonObject json = new JsonObject();
            json.addProperty("current_preset", this.currentPresetName);

            JsonObject eq = new JsonObject();
            eq.addProperty("wings", mod.selected("wings"));
            eq.addProperty("backpacks", mod.selected("backpacks"));
            eq.addProperty("hats", mod.selected("hats"));
            eq.addProperty("masks", mod.selected("masks"));
            eq.addProperty("pets", mod.selected("pets"));
            eq.addProperty("sword", mod.selectedSword());
            eq.addProperty("sword_model", mod.selectedSwordModel());
            json.add("equipped", eq);

            JsonArray arr = new JsonArray();
            for (CosmeticPreset p : this.presets) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", p.name);
                obj.addProperty("wings", p.wings);
                obj.addProperty("backpacks", p.backpacks);
                obj.addProperty("hats", p.hats);
                obj.addProperty("masks", p.masks);
                obj.addProperty("pets", p.pets);
                obj.addProperty("sword", p.sword);
                obj.addProperty("sword_model", p.swordModel);
                arr.add(obj);
            }
            json.add("presets", arr);

            writer.write(json.toString());
        } catch (Throwable ignored) {
        }
    }
}
