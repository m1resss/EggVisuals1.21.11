package egg.client.cosmetic.watch;

import net.minecraft.util.Identifier;

public enum WatchStyle {
    NONE("None", null, CaseShape.CIRCLE),
    SUBMARINER("Submariner", "textures/watches/submariner.png", CaseShape.CIRCLE),
    HULK("Hulk", "textures/watches/hulk.png", CaseShape.CIRCLE),
    PEPSI("GMT Pepsi", "textures/watches/pepsi.png", CaseShape.CIRCLE),
    DAYTONA("Daytona", "textures/watches/daytona.png", CaseShape.CIRCLE),
    DAY_DATE("Day-Date", "textures/watches/day_date.png", CaseShape.CIRCLE),
    DATEJUST("Datejust", "textures/watches/datejust.png", CaseShape.CIRCLE),
    NAUTILUS("Nautilus", "textures/watches/nautilus.png", CaseShape.CIRCLE),
    AQUANAUT("Aquanaut", "textures/watches/aquanaut.png", CaseShape.OCTAGON),
    CALATRAVA("Calatrava", "textures/watches/calatrava.png", CaseShape.CIRCLE),
    ROYAL_OAK("Royal Oak", "textures/watches/royal_oak.png", CaseShape.OCTAGON),
    AP_OFFSHORE("AP Offshore", "textures/watches/ap_offshore.png", CaseShape.OCTAGON),
    RICHARD_MILLE("Richard Mille", "textures/watches/richard_mille.png", CaseShape.TONNEAU),
    PIAGET_POLO("Piaget Polo", "textures/watches/piaget_polo.png", CaseShape.CIRCLE),
    CARTIER_SANTOS("Cartier Santos", "textures/watches/cartier_santos.png", CaseShape.SQUIRCLE_GSHOCK),
    HUBLOT_BIG_BANG("Big Bang", "textures/watches/hublot_big_bang.png", CaseShape.CIRCLE),
    OMEGA_SPEEDMASTER("Speedmaster", "textures/watches/omega_speedmaster.png", CaseShape.CIRCLE),
    G_SHOCK("G-Shock", "textures/watches/g_shock.png", CaseShape.SQUIRCLE_GSHOCK),
    APPLE_ULTRA("Apple Ultra", "textures/watches/apple_ultra.png", CaseShape.SQUIRCLE_ULTRA),
    TAG_MONACO("TAG Monaco", "textures/watches/tag_monaco.png", CaseShape.SQUIRCLE_GSHOCK),
    BREITLING_NAVITIMER("Navitimer", "textures/watches/breitling_navitimer.png", CaseShape.CIRCLE),
    JACOB_ASTRONOMIA("Astronomia", "textures/watches/jacob_astronomia.png", CaseShape.CIRCLE),
    JACOB_GODFATHER("Godfather", "textures/watches/jacob_godfather.png", CaseShape.CIRCLE);

    public enum CaseShape {
        CIRCLE,
        OCTAGON,
        TONNEAU,
        SQUIRCLE_ULTRA,
        SQUIRCLE_GSHOCK
    }

    private final String name;
    private final Identifier texture;
    private final CaseShape caseShape;

    WatchStyle(String name, String texturePath, CaseShape caseShape) {
        this.name = name;
        this.texture = texturePath != null ? Identifier.of("egg", texturePath) : null;
        this.caseShape = caseShape;
    }

    public String getName() {
        return name;
    }

    public Identifier getTexture() {
        return texture;
    }

    public CaseShape getCaseShape() {
        return caseShape;
    }

    public static WatchStyle fromName(String name) {
        if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name)) {
            return NONE;
        }
        if ("Pepsi".equalsIgnoreCase(name)) {
            return PEPSI;
        }
        for (WatchStyle style : values()) {
            if (style.name.equalsIgnoreCase(name) || style.name().equalsIgnoreCase(name)) {
                return style;
            }
        }
        return NONE;
    }
}
