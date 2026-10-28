package delta.cosmetics;

public enum CosmeticCategory {
    HATS("Шляпы", 19),
    GLASSES("Очки", 2),
    ACCESSORIES("Украшения", 7),
    WINGS("Крылья", 43),
    BODY("Тело", 12),
    BOOTS("Обувь", 1),
    PETS("Питомцы", 12),
    SWORDS("Текстуры меча", 79);

    private final String displayName;
    private final int defaultCount;

    CosmeticCategory(String displayName, int defaultCount) {
        this.displayName = displayName;
        this.defaultCount = defaultCount;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getDefaultCount() {
        return this.defaultCount;
    }
}
