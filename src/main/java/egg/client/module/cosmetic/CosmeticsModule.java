package egg.client.module.cosmetic;

import egg.client.cosmetic.CosmeticsConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;

import java.util.Locale;

public class CosmeticsModule extends Module {
    private static CosmeticsModule instance;

    private final ModeSetting wings = new ModeSetting("Крылья", "None", "Выбор крыльев",
            "None", "Angel", "Archangel", "Balloons", "Black Hole", "Butterfly",
            "Draco", "Frost", "Baby Dragon", "Blue Dragon", "Rocker", "Spider",
            "Vulcano", "Ghoul", "Jaguar", "Aurum", "Dragon", "Diamond", "Easter",
            "Fluger", "Trident", "Dark", "Wota", "Rocket", "Steampunk", "Techno");

    private final ModeSetting backpacks = new ModeSetting("Рюкзаки", "None", "Выбор рюкзака",
            "None", "Adidas", "Gucci", "Hermes", "Louis Vuitton", "Nike", "Katana", "Supreme", "Vlone", "Bape", "Balenciaga", "Chrome Hearts", "Pepe");

    private final ModeSetting hats = new ModeSetting("Шапки", "None", "Выбор шапки",
            "None", "Halo", "White Halo", "Bear", "Black Hole Halo", "Frog", "Pilot", "Armadillo", "Camel", "Dark Hat", "Four Leaf", "Classic Frog", "Capybara", "Chicken", "Penguin", "Happy");

    private final ModeSetting masks = new ModeSetting("Маски", "None", "Выбор маски",
            "None", "Angry", "Clown", "Glasses", "Wink");

    private final ModeSetting pets = new ModeSetting("Питомцы", "None", "Выбор питомца",
            "None", "Axolotl", "Big Cat", "Capybara", "Dragon", "Guinea Pig", "Panda", "Shark", "Tiger");

    private final ModeSetting watches = new ModeSetting("Часы", "None", "Выбор часов",
            "None", "Submariner", "Hulk", "GMT Pepsi", "Daytona", "Day-Date", "Datejust",
            "Nautilus", "Aquanaut", "Calatrava", "Royal Oak", "AP Offshore", "Richard Mille",
            "Piaget Polo", "Cartier Santos", "Big Bang", "Speedmaster", "G-Shock", "Apple Ultra",
            "TAG Monaco", "Navitimer", "Astronomia", "Godfather");

    private final ModeSetting swords = new ModeSetting("Мечи", "None", "Выбор 3D меча",
            "None", "Wanderer Katana", "Knight Longsword", "Cyber Blade", "Pirate Cutlass",
            "Frost Brand", "Crossguard Saber", "Obsidian Greatsword", "Sakura Wakizashi",
            "Gladius", "Zweihander", "Sultan Scimitar", "Musketeer Rapier", "Highland Claymore",
            "Khopesh", "Flamberge", "Void Runeblade", "Buster Sword", "Leaf Blade", "Bone Sword",
            "Jade Dao", "Holy Avenger", "Prism Sword", "Shotel", "Gunblade", "Candy Cane Sword",
            "Coral Blade", "Thunder Nodachi", "Bokken", "Serpent Kris", "Moonlight Greatsword",
            "Bloodthirster", "Pixel Sword", "Clockwork Blade");

    private final egg.client.module.setting.BindSetting inspectWatchKey = new egg.client.module.setting.BindSetting("Осмотр часов", org.lwjgl.glfw.GLFW.GLFW_KEY_J,
            () -> egg.client.cosmetic.watch.WatchAnimator.getInstance().trigger(), "Анимация осмотра часов от 1-го лица");

    private String selectedWings = "";
    private String selectedBackpacks = "";
    private String selectedHats = "";
    private String selectedMasks = "";
    private String selectedPets = "";
    private String selectedSword = "";
    private String selectedSwordModel = "";

    public CosmeticsModule() {
        super("Cosmetics", "Отображает украшения персонажа: крылья, шляпы, маски и питомцев", Category.COSMETICS);
        instance = this;
        setEnabled(true);
        addSettings(wings, backpacks, hats, masks, pets, watches, swords, inspectWatchKey);
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
            case "watches" -> getSelectedWatch();
            case "swords" -> selectedSword();
            default -> "";
        };
    }

    public void select(String category, String id) {
        if (!isEnabled()) {
            setEnabled(true);
        }
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
            case "watches" -> {
                this.watches.set(val.isEmpty() ? "None" : val);
            }
            case "swords" -> {
                this.selectedSword = val;
                this.selectedSwordModel = val;
                this.swords.set(val.isEmpty() ? "None" : val);
            }
        }
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public String selectedSword() {
        String s = this.swords.get();
        if (s != null && !s.isEmpty() && !"None".equalsIgnoreCase(s)) {
            return s;
        }
        return this.selectedSword;
    }

    public String selectedSwordModel() {
        return this.selectedSwordModel;
    }

    public void selectSword(String name, String model) {
        if (!isEnabled()) {
            setEnabled(true);
        }
        String val = (name == null || "None".equalsIgnoreCase(name)) ? "" : name;
        this.selectedSword = val;
        this.selectedSwordModel = model == null ? val : model;
        this.swords.set(val.isEmpty() ? "None" : val);
        CosmeticsConfigManager.getInstance().saveConfig();
    }

    public void selectSword(String name) {
        selectSword(name, name);
    }

    public void clearSlot(String category) {
        if ("swords".equalsIgnoreCase(category)) {
            selectSword("", "");
        } else {
            select(category, "");
        }
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
        this.watches.set("None");
        this.swords.set("None");
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

    public ModeSetting getWatches() {
        return this.watches;
    }

    public egg.client.module.setting.BindSetting getInspectWatchKey() {
        return this.inspectWatchKey;
    }

    public String getSelectedWatch() {
        return this.watches.get();
    }

    public void setSelectedWatch(String name) {
        if (name != null) {
            this.watches.set(name);
        }
    }

    public egg.client.cosmetic.watch.WatchStyle getSelectedWatchStyle() {
        return egg.client.cosmetic.watch.WatchStyle.fromName(this.watches.get());
    }

    public ModeSetting getSwords() {
        return this.swords;
    }

    public egg.client.cosmetic.sword.SwordStyle getSelectedSwordStyle() {
        return egg.client.cosmetic.sword.SwordStyle.fromName(selectedSword());
    }
}
