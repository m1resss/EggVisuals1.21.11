package egg.client.module.cosmetic;

import egg.client.cosmetic.CosmeticsConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;

import java.util.Locale;

public class CosmeticsModule extends Module {
    private static CosmeticsModule instance;

    private final ModeSetting wings = new ModeSetting("Крылья", "None",
            "None", "Angel", "Archangel", "Balloons", "Black Hole", "Butterfly",
            "Draco", "Frost", "Baby Dragon", "Blue Dragon", "Rocker", "Spider",
            "Vulcano", "Ghoul", "Jaguar", "Aurum", "Dragon", "Diamond", "Easter",
            "Fluger", "Trident", "Dark", "Wota", "Rocket", "Steampunk", "Techno");

    private final ModeSetting backpacks = new ModeSetting("Рюкзаки", "None",
            "None", "Adidas", "Gucci", "Hermes", "Louis Vuitton", "Nike", "Katana", "Supreme", "Vlone", "Bape", "Balenciaga", "Chrome Hearts", "Pepe");

    private final ModeSetting hats = new ModeSetting("Шляпы", "None",
            "None", "Halo", "White Halo", "Bear", "Black Hole Halo", "Frog", "Pilot", "Armadillo", "Camel", "Dark Hat", "Four Leaf", "Classic Frog", "Capybara", "Chicken", "Penguin", "Happy");

    private final ModeSetting masks = new ModeSetting("Маски", "None",
            "None", "Angry", "Clown", "Glasses", "Wink");

    private final ModeSetting pets = new ModeSetting("Питомцы", "None",
            "None", "Axolotl", "Big Cat", "Capybara", "Dragon", "Guinea Pig", "Panda", "Patrick", "Perch", "Pig", "Jaguar", "Creeper", "Penguin", "Bee", "Spongebob");

    private String selectedWings = "";
    private String selectedBackpacks = "";
    private String selectedHats = "";
    private String selectedMasks = "";
    private String selectedPets = "";
    private String selectedSword = "";
    private String selectedSwordModel = "";

    public CosmeticsModule() {
        super("Cosmetics", "Кастомные 3D крылья, рюкзаки, шляпы, маски, питомцы и мечи", Category.COSMETICS);
        instance = this;
        addSettings(wings, backpacks, hats, masks, pets);
        CosmeticsConfigManager.getInstance().loadConfig();
    }

    public static CosmeticsModule getInstance() {
        return instance;
    }

    public ModeSetting getWings() {
        return this.wings;
    }

    public ModeSetting getBackpacks() {
        return this.backpacks;
    }

    public ModeSetting getHats() {
        return this.hats;
    }

    public ModeSetting getMasks() {
        return this.masks;
    }

    public ModeSetting getPets() {
        return this.pets;
    }

    public String selected(String category) {
        if (category == null) return "";
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "wings" -> selectedWings;
            case "backpacks" -> selectedBackpacks;
            case "hats" -> selectedHats;
            case "masks" -> selectedMasks;
            case "pets" -> selectedPets;
            default -> "";
        };
    }

    public void select(String category, String id) {
        if (category == null) return;
        String val = (id == null || "None".equalsIgnoreCase(id)) ? "" : id;
        switch (category.toLowerCase(Locale.ROOT)) {
            case "wings" -> {
                selectedWings = val;
                if (val.isEmpty()) wings.set("None");
            }
            case "backpacks" -> {
                selectedBackpacks = val;
                if (val.isEmpty()) backpacks.set("None");
            }
            case "hats" -> {
                selectedHats = val;
                if (val.isEmpty()) hats.set("None");
            }
            case "masks" -> {
                selectedMasks = val;
                if (val.isEmpty()) masks.set("None");
            }
            case "pets" -> {
                selectedPets = val;
                if (val.isEmpty()) pets.set("None");
            }
        }
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public String selectedSword() {
        return this.selectedSword;
    }

    public String selectedSwordModel() {
        return this.selectedSwordModel;
    }

    public void selectSword(String name, String model) {
        this.selectedSword = name == null ? "" : name;
        this.selectedSwordModel = model == null ? "" : model;
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public void clearAll() {
        this.selectedWings = "";
        this.selectedBackpacks = "";
        this.selectedHats = "";
        this.selectedMasks = "";
        this.selectedPets = "";
        this.selectedSword = "";
        this.selectedSwordModel = "";
        this.wings.set("None");
        this.backpacks.set("None");
        this.hats.set("None");
        this.masks.set("None");
        this.pets.set("None");
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public String getSelectedWingId() {
        return this.selectedWings;
    }

    public String getSelectedBackpackId() {
        return this.selectedBackpacks;
    }

    public String getSelectedHatId() {
        return this.selectedHats;
    }

    public String getSelectedMaskId() {
        return this.selectedMasks;
    }

    public String getSelectedPetId() {
        return this.selectedPets;
    }
}
