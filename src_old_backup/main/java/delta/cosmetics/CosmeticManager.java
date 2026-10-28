package delta.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import delta.core.Interface;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class CosmeticManager implements Interface {
    private static CosmeticManager instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = true;
    private CosmeticCategory selectedCategory = CosmeticCategory.SWORDS;
    private final Set<String> equippedItemIds = new HashSet<>();
    private final Map<CosmeticCategory, List<CosmeticItem>> itemsByCategory = new EnumMap<>(CosmeticCategory.class);

    public static CosmeticManager getInstance() {
        if (instance == null) {
            instance = new CosmeticManager();
        }
        return instance;
    }

    public CosmeticManager() {
        instance = this;
        registerDefaultItems();
        initDefaultEquipped();
        ensureFiguraAvatarFiles();
        loadConfig();
    }

    private void initDefaultEquipped() {
        // Default equipped items matching the screenshot (+4 items)
        this.equippedItemIds.add("sword_zenith");
        this.equippedItemIds.add("wings_dragon");
        this.equippedItemIds.add("acc_halo");
        this.equippedItemIds.add("pet_egg");
    }

    private void registerDefaultItems() {
        for (CosmeticCategory cat : CosmeticCategory.values()) {
            this.itemsByCategory.put(cat, new ArrayList<>());
        }

        // SWORDS (Текстуры меча)
        addSword("sword_lazur", "Лазурный двуручник", true, 0xFF00CEC9);
        addSword("sword_velit", "Вэлит", true, 0xFFFF4757);
        addSword("sword_moonlight", "Лунный свет", true, 0xFF70A1FF);
        addSword("sword_zenith", "Зенит", true, 0xFF2ED573);
        addSword("sword_dragon", "Меч дракона", true, 0xFFFFA502);
        addSword("sword_demon", "Демонический меч", false, 0xFF9C88FF);
        addSword("sword_dawn", "Клинок рассвета", false, 0xFFECCC68);
        addSword("sword_excalibur", "Экскалибур", true, 0xFF00D2D3);
        addSword("sword_nightedge", "Грань ночи", true, 0xFF5F27CD);
        addSword("sword_starfury", "Звездная ярость", true, 0xFF54A0FF);
        addSword("sword_terra", "Терра-клинок", true, 0xFF10AC84);
        addSword("sword_tempest", "Буревестник", false, 0xFFFF9F43);

        // WINGS (Крылья)
        addWings("wings_dragon", "Крылья дракона", true, 0xFFFF4757);
        addWings("wings_angel", "Крылья ангела", true, 0xFFFFFFFF);
        addWings("wings_cyber", "Кибер-крылья", true, 0xFF00CEC9);
        addWings("wings_phoenix", "Крылья феникса", true, 0xFFFFA502);
        addWings("wings_shadow", "Теневые крылья", false, 0xFF2F3542);
        addWings("wings_butterfly", "Крылья мотылька", true, 0xFFFF6B81);
        addWings("wings_crystal", "Кристальные крылья", true, 0xFF70A1FF);

        // HATS (Шляпы)
        addHat("hat_crown", "Корона короля", false, 0xFFFFD32A);
        addHat("hat_cylinder", "Цилиндр мага", false, 0xFF1E272E);
        addHat("hat_witch", "Шляпа ведьмы", false, 0xFF801336);
        addHat("hat_shadowhood", "Капюшон тени", false, 0xFF485460);
        addHat("hat_samurai", "Шлем самурая", false, 0xFFEA2027);
        addHat("hat_halo_cap", "Кепка Egg", false, 0xFF8B5CF6);

        // GLASSES (Очки)
        addGlasses("glasses_cyber", "Очки киберпанк", true, 0xFF00CEC9);
        addGlasses("glasses_sun", "Солнцезащитные", false, 0xFF2C3E50);

        // ACCESSORIES (Украшения)
        addAcc("acc_halo", "Нимб святости", true, 0xFFFFFA65);
        addAcc("acc_horns", "Рога демона", true, 0xFFFF3838);
        addAcc("acc_windscarf", "Шарф ветра", true, 0xFF70A1FF);
        addAcc("acc_oni", "Маска они", false, 0xFFFF4D4D);
        addAcc("acc_katana", "Катана на поясе", false, 0xFFC7ECEE);

        // BODY (Тело)
        addBody("body_shadowcloak", "Плащ тьмы", true, 0xFF1E272E);
        addBody("body_firerobe", "Мантия пламени", true, 0xFFFF4757);
        addBody("body_titan", "Броня титана", false, 0xFF747D8C);
        addBody("body_cybervest", "Кибер-жилет", true, 0xFF00CEC9);

        // BOOTS (Обувь)
        addBoots("boots_wind", "Ботинки ветра", true, 0xFF2ED573);

        // PETS (Питомцы)
        addPet("pet_egg", "Яйцо-компаньон", true, 0xFF8B5CF6);
        addPet("pet_dragon", "Мини-дракончик", true, 0xFFFF6348);
        addPet("pet_ghost", "Призрак", true, 0xFF70A1FF);
        addPet("pet_crystal", "Левитирующий куб", true, 0xFF00CEC9);
    }

    private void addSword(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.SWORDS).add(new CosmeticItem(id, name, CosmeticCategory.SWORDS, anim, color, new ItemStack(Items.NETHERITE_SWORD)));
    }

    private void addWings(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.WINGS).add(new CosmeticItem(id, name, CosmeticCategory.WINGS, anim, color, new ItemStack(Items.ELYTRA)));
    }

    private void addHat(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.HATS).add(new CosmeticItem(id, name, CosmeticCategory.HATS, anim, color, new ItemStack(Items.GOLDEN_HELMET)));
    }

    private void addGlasses(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.GLASSES).add(new CosmeticItem(id, name, CosmeticCategory.GLASSES, anim, color, new ItemStack(Items.SPYGLASS)));
    }

    private void addAcc(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.ACCESSORIES).add(new CosmeticItem(id, name, CosmeticCategory.ACCESSORIES, anim, color, new ItemStack(Items.AMETHYST_SHARD)));
    }

    private void addBody(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.BODY).add(new CosmeticItem(id, name, CosmeticCategory.BODY, anim, color, new ItemStack(Items.NETHERITE_CHESTPLATE)));
    }

    private void addBoots(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.BOOTS).add(new CosmeticItem(id, name, CosmeticCategory.BOOTS, anim, color, new ItemStack(Items.NETHERITE_BOOTS)));
    }

    private void addPet(String id, String name, boolean anim, int color) {
        this.itemsByCategory.get(CosmeticCategory.PETS).add(new CosmeticItem(id, name, CosmeticCategory.PETS, anim, color, new ItemStack(Items.EGG)));
    }

    public List<CosmeticItem> getItems(CosmeticCategory category) {
        return this.itemsByCategory.getOrDefault(category, Collections.emptyList());
    }

    public List<CosmeticItem> getFilteredItems(CosmeticCategory category, String filter) {
        List<CosmeticItem> all = getItems(category);
        if (filter == null || filter.trim().isEmpty()) {
            return all;
        }
        String lower = filter.trim().toLowerCase(Locale.ROOT);
        List<CosmeticItem> filtered = new ArrayList<>();
        for (CosmeticItem item : all) {
            if (item.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public boolean isEquipped(CosmeticItem item) {
        return item != null && this.equippedItemIds.contains(item.getId());
    }

    public void toggleEquip(CosmeticItem item) {
        if (item == null) {
            return;
        }
        if (this.equippedItemIds.contains(item.getId())) {
            this.equippedItemIds.remove(item.getId());
        } else {
            // For categories like wings or swords, only one can be active at a time
            if (item.getCategory() == CosmeticCategory.SWORDS || item.getCategory() == CosmeticCategory.WINGS || item.getCategory() == CosmeticCategory.PETS) {
                for (CosmeticItem other : getItems(item.getCategory())) {
                    this.equippedItemIds.remove(other.getId());
                }
            }
            this.equippedItemIds.add(item.getId());
        }
        saveConfig();
    }

    public void clearEquipped() {
        this.equippedItemIds.clear();
        saveConfig();
    }

    public int getEquippedCount() {
        return this.equippedItemIds.size();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveConfig();
    }

    public CosmeticCategory getSelectedCategory() {
        return this.selectedCategory;
    }

    public void setSelectedCategory(CosmeticCategory selectedCategory) {
        this.selectedCategory = selectedCategory;
    }

    public File getFiguraDir() {
        File gameDir = aM_.runDirectory != null ? aM_.runDirectory : new File(".");
        File figuraDir = new File(gameDir, "figura");
        File avatarsDir = new File(figuraDir, "avatars");
        return new File(avatarsDir, "EggClient");
    }

    public void ensureFiguraAvatarFiles() {
        try {
            File avatarDir = getFiguraDir();
            if (!avatarDir.exists()) {
                avatarDir.mkdirs();
            }

            // 1. avatar.json for Figura
            File avatarJson = new File(avatarDir, "avatar.json");
            if (!avatarJson.exists()) {
                JsonObject json = new JsonObject();
                json.addProperty("name", "Egg Client Cosmetics");
                json.addProperty("author", "EggClient");
                json.addProperty("version", "1.0.0");
                json.addProperty("color", "#8B5CF6");
                try (FileWriter writer = new FileWriter(avatarJson)) {
                    this.gson.toJson(json, writer);
                }
            }

            // 2. script.lua for Figura (universal bridge reading egg_config.json)
            File scriptLua = new File(avatarDir, "script.lua");
            if (!scriptLua.exists()) {
                String luaContent = "-- Egg Client Universal Cosmetics Controller\n" +
                        "local configPath = 'egg_config.json'\n" +
                        "local lastRead = 0\n" +
                        "local cosmetics = {}\n\n" +
                        "function pings.syncCosmetics(tbl)\n" +
                        "  cosmetics = tbl\n" +
                        "end\n\n" +
                        "function events.tick()\n" +
                        "  if host:isHost() and world.getTime() - lastRead > 20 then\n" +
                        "    lastRead = world.getTime()\n" +
                        "    local cfg = file:read(configPath)\n" +
                        "    if cfg then\n" +
                        "      local ok, data = pcall(json.decode, cfg)\n" +
                        "      if ok and data then\n" +
                        "        pings.syncCosmetics(data.equipped or {})\n" +
                        "      end\n" +
                        "    end\n" +
                        "  end\n" +
                        "end\n";
                try (FileWriter writer = new FileWriter(scriptLua)) {
                    writer.write(luaContent);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void saveConfig() {
        try {
            File avatarDir = getFiguraDir();
            if (!avatarDir.exists()) {
                avatarDir.mkdirs();
            }
            File configFile = new File(avatarDir, "egg_config.json");
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", this.enabled);
            JsonArray arr = new JsonArray();
            for (String id : this.equippedItemIds) {
                arr.add(id);
            }
            obj.add("equipped", arr);
            obj.addProperty("activeCount", this.equippedItemIds.size());

            try (FileWriter writer = new FileWriter(configFile)) {
                this.gson.toJson(obj, writer);
            }
        } catch (Exception ignored) {
        }
    }

    public void loadConfig() {
        try {
            File configFile = new File(getFiguraDir(), "egg_config.json");
            if (configFile.exists()) {
                try (FileReader reader = new FileReader(configFile)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj.has("enabled")) {
                        this.enabled = obj.get("enabled").getAsBoolean();
                    }
                    if (obj.has("equipped")) {
                        this.equippedItemIds.clear();
                        JsonArray arr = obj.getAsJsonArray("equipped");
                        for (int i = 0; i < arr.size(); i++) {
                            this.equippedItemIds.add(arr.get(i).getAsString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
