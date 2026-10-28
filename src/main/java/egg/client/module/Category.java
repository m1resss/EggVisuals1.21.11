package egg.client.module;

public enum Category {
    // Main
    COMBAT("Combat", "Бой", "V", "Main"),
    MOVEMENT("Movement", "Движение", "I", "Main"),

    // Visual
    ESP("Visuals", "Визуалы", "&", "Visual"),

    // Other
    PLAYER("Player", "Игрок", "L", "Other"),
    INVENTORY("Inventory", "Инвентарь", "H", "Other"),
    MISC("Misc", "Разное", "D", "Other"),

    // Addons
    SETTINGS("Settings", "Настройки", "*", "Addons");

    private final String name;
    private final String displayNameRu;
    private final String icon;
    private final String section;

    Category(String name, String displayNameRu, String icon, String section) {
        this.name = name;
        this.displayNameRu = displayNameRu;
        this.icon = icon;
        this.section = section;
    }

    public String getName() {
        return name;
    }

    public String getDisplayNameRu() {
        return displayNameRu;
    }

    public String getIcon() {
        return icon;
    }

    public String getSection() {
        return section;
    }
}
