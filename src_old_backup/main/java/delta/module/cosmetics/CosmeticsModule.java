package delta.module.cosmetics;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.setting.ModeSetting;

import java.util.ArrayList;
import java.util.List;

@ModuleRegister(a = "Cosmetics", b = "Кастомные локальные 3D аксессуары, крылья, рюкзаки, питомцы и маски", c = Category.Cosmetics)
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

    public CosmeticsModule() {
        instance = this;
        a(this.wings, this.backpacks, this.hats, this.masks, this.pets);
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

    private String selectedWings = "";
    private String selectedBackpacks = "";
    private String selectedHats = "";
    private String selectedMasks = "";
    private String selectedPets = "";
    private String selectedSword = "";
    private String selectedSwordModel = "";

    public String selected(String category) {
        if (category == null) return "";
        return switch (category.toLowerCase(java.util.Locale.ROOT)) {
            case "wings" -> !selectedWings.isEmpty() ? selectedWings : getSelectedWingId();
            case "backpacks" -> !selectedBackpacks.isEmpty() ? selectedBackpacks : getSelectedBackpackId();
            case "hats" -> !selectedHats.isEmpty() ? selectedHats : getSelectedHatId();
            case "masks" -> !selectedMasks.isEmpty() ? selectedMasks : getSelectedMaskId();
            case "pets" -> !selectedPets.isEmpty() ? selectedPets : getSelectedPetId();
            default -> "";
        };
    }

    public void select(String category, String id) {
        if (category == null) return;
        String val = id == null ? "" : id;
        switch (category.toLowerCase(java.util.Locale.ROOT)) {
            case "wings" -> selectedWings = val;
            case "backpacks" -> selectedBackpacks = val;
            case "hats" -> selectedHats = val;
            case "masks" -> selectedMasks = val;
            case "pets" -> selectedPets = val;
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
        this.wings.a("None");
        this.backpacks.a("None");
        this.hats.a("None");
        this.masks.a("None");
        this.pets.a("None");
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public String getSelectedWingId() {
        if (!this.selectedWings.isEmpty()) return this.selectedWings;
        String val = this.wings.c();
        if ("None".equals(val)) return "";
        return switch (val) {
            case "Angel" -> "058_angel";
            case "Archangel" -> "059_archangel";
            case "Balloons" -> "060_balloons";
            case "Black Hole" -> "061_black_hole";
            case "Butterfly" -> "062_butterfly";
            case "Draco" -> "063_draco";
            case "Frost" -> "064_frost";
            case "Baby Dragon" -> "065_baby_dragon_wings";
            case "Blue Dragon" -> "066_blue_dragon_wings";
            case "Rocker" -> "067_rocker_wings";
            case "Spider" -> "068_spider_wings";
            case "Vulcano" -> "069_vulcano_wings";
            case "Ghoul" -> "070_ghoul";
            case "Jaguar" -> "071_jaguar";
            case "Aurum" -> "072_aurum";
            case "Dragon" -> "073_dragon";
            case "Diamond" -> "074_diamond";
            case "Easter" -> "075_easter";
            case "Fluger" -> "076_fluger";
            case "Trident" -> "077_trident";
            case "Dark" -> "078_dark";
            case "Wota" -> "079_wota_wings";
            case "Rocket" -> "080_rocket";
            case "Steampunk" -> "081_steampunk";
            case "Techno" -> "082_techno";
            default -> "";
        };
    }

    public String getSelectedBackpackId() {
        if (!this.selectedBackpacks.isEmpty()) return this.selectedBackpacks;
        String val = this.backpacks.c();
        if ("None".equals(val)) return "";
        return switch (val) {
            case "Adidas" -> "001_adidas";
            case "Gucci" -> "002_gucci";
            case "Hermes" -> "003_hermes";
            case "Louis Vuitton" -> "004_louis_vuitton";
            case "Nike" -> "005_nike";
            case "Katana" -> "006_katana";
            case "Supreme" -> "011_supreme";
            case "Vlone" -> "012_vlone";
            case "Bape" -> "013_bape";
            case "Balenciaga" -> "014_balenciaga";
            case "Chrome Hearts" -> "015_chrome_hearts";
            case "Pepe" -> "016_pepe";
            default -> "";
        };
    }

    public String getSelectedHatId() {
        if (!this.selectedHats.isEmpty()) return this.selectedHats;
        String val = this.hats.c();
        if ("None".equals(val)) return "";
        return switch (val) {
            case "Halo" -> "017_halo";
            case "White Halo" -> "018_white_halo";
            case "Bear" -> "019_bear";
            case "Black Hole Halo" -> "020_black_hole_halo";
            case "Frog" -> "021_frog";
            case "Pilot" -> "022_pilot";
            case "Armadillo" -> "023_armadillo";
            case "Camel" -> "024_camel";
            case "Dark Hat" -> "025_dark_hat";
            case "Four Leaf" -> "026_four_leaf";
            case "Classic Frog" -> "027_classic_frog";
            case "Capybara" -> "029_capybara";
            case "Chicken" -> "030_chicken";
            case "Penguin" -> "034_penguin";
            case "Happy" -> "037_happy";
            default -> "";
        };
    }

    public String getSelectedMaskId() {
        if (!this.selectedMasks.isEmpty()) return this.selectedMasks;
        String val = this.masks.c();
        if ("None".equals(val)) return "";
        return switch (val) {
            case "Angry" -> "039_angry";
            case "Clown" -> "040_clown";
            case "Glasses" -> "041_glasses";
            case "Wink" -> "042_wink";
            default -> "";
        };
    }

    public String getSelectedPetId() {
        if (!this.selectedPets.isEmpty()) return this.selectedPets;
        String val = this.pets.c();
        if ("None".equals(val)) return "";
        return switch (val) {
            case "Axolotl" -> "043_axolotl";
            case "Big Cat" -> "044_big_cat";
            case "Capybara" -> "045_capybara";
            case "Dragon" -> "046_dragon";
            case "Guinea Pig" -> "047_guinea_pig";
            case "Panda" -> "048_panda";
            case "Patrick" -> "049_patrick";
            case "Perch" -> "050_perch";
            case "Pig" -> "051_pig";
            case "Jaguar" -> "052_jaguar";
            case "Creeper" -> "053_creeper";
            case "Penguin" -> "055_penguin";
            case "Bee" -> "056_bee";
            case "Spongebob" -> "057_spongebob";
            default -> "";
        };
    }
}
