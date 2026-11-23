package egg.client.module;

public enum Category {

    MOVEMENT("Movement", "Движение", "d", "Main"),

    ESP("Visuals", "Визуалы", "f", "Visual"),

    INVENTORY("Inventory", "Инвентарь", "c", "Other"),
    MISC("Misc", "Разное", "g", "Other"),
    COSMETICS("Cosmetics", "Косметика", "r", "Other"),

    SETTINGS("Settings", "Настройки", "s", "Addons");

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
