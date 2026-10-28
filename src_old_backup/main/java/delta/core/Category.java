package delta.core;


import lombok.Generated;

public enum Category {
    Combat("V"),
    Movement("I"),
    Render("t"),
    Player("L"),
    Misc("D"),
    Cosmetics("C");

    private final String f;

    @Generated
    public String a() {
        return this.f;
    }

    Category(String icon) {
        this.f = icon;
    }
}
