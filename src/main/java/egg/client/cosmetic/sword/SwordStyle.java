package egg.client.cosmetic.sword;

import net.minecraft.util.Identifier;

public enum SwordStyle {
    NONE("None", null, Archetype.LONGSWORD, 0, 0, 0, 0, 0, 1.0f, 1.0f, false),
    WANDERER_KATANA("Wanderer Katana", "textures/swords/wanderer_katana.png", Archetype.KATANA, 0xCFD6E0, 0xF5FAFF, 0xDAAF4E, 0x20222A, 0, 1.12f, 0.90f, false),
    KNIGHT_LONGSWORD("Knight Longsword", "textures/swords/knight_longsword.png", Archetype.LONGSWORD, 0xC3CAD6, 0xEEF4FC, 0xD7B052, 0x603420, 0, 1.06f, 1.00f, false),
    CYBER_BLADE("Cyber Blade", "textures/swords/cyber_blade.png", Archetype.KATANA, 0x1C202A, 0x00EEFF, 0x2D303E, 0x161820, 0x00E5FF, 1.10f, 1.05f, false),
    PIRATE_CUTLASS("Pirate Cutlass", "textures/swords/pirate_cutlass.png", Archetype.CURVED, 0xB9C0CA, 0xEBF0F8, 0xD4A544, 0x582E18, 0, 0.96f, 1.18f, false),
    FROST_BRAND("Frost Brand", "textures/swords/frost_brand.png", Archetype.GREATSWORD, 0x78D7FF, 0xDCF8FF, 0x489BD7, 0x264162, 0x6EE1FF, 1.14f, 1.18f, true),
    CROSSGUARD_SABER("Crossguard Saber", "textures/swords/crossguard_saber.png", Archetype.SABER, 0xEB202A, 0xFFBEBE, 0x4B505C, 0x22242A, 0xFF232D, 1.10f, 0.92f, true),
    OBSIDIAN_GREATSWORD("Obsidian Greatsword", "textures/swords/obsidian_greatsword.png", Archetype.GREATSWORD, 0x1C1824, 0xFF6918, 0x342C40, 0x24181C, 0xFF5F14, 1.20f, 1.30f, false),
    SAKURA_WAKIZASHI("Sakura Wakizashi", "textures/swords/sakura_wakizashi.png", Archetype.KATANA, 0xEEE1E8, 0xFFA5CD, 0xE1A594, 0xC34E7D, 0xFF9BC8, 0.92f, 0.88f, false),
    GLADIUS("Gladius", "textures/swords/gladius.png", Archetype.LONGSWORD, 0xCAD0DA, 0xF0F5FC, 0xD7A848, 0x803E22, 0, 0.86f, 1.18f, false),
    ZWEIHANDER("Zweihander", "textures/swords/zweihander.png", Archetype.LONGSWORD, 0xBCC2CE, 0xECF2FA, 0x8E94A2, 0x482618, 0, 1.28f, 1.06f, false),
    SULTAN_SCIMITAR("Sultan Scimitar", "textures/swords/sultan_scimitar.png", Archetype.CURVED, 0xD2D8E2, 0xF8FAFF, 0xE8BC4E, 0x1894A8, 0, 1.06f, 1.15f, false),
    MUSKETEER_RAPIER("Musketeer Rapier", "textures/swords/musketeer_rapier.png", Archetype.RAPIER, 0xD7DEE8, 0xF8FCFF, 0xCDD2DE, 0x304491, 0, 1.16f, 0.65f, false),
    HIGHLAND_CLAYMORE("Highland Claymore", "textures/swords/highland_claymore.png", Archetype.LONGSWORD, 0xBAC0CC, 0xEBF0F8, 0x9CA2AF, 0x56301C, 0, 1.22f, 1.12f, false),
    KHOPESH("Khopesh", "textures/swords/khopesh.png", Archetype.KHOPESH, 0xD4AE54, 0xF8DE8E, 0x265FB9, 0x693A20, 0, 0.98f, 1.10f, false),
    FLAMBERGE("Flamberge", "textures/swords/flamberge.png", Archetype.WAVY, 0xCDC0B6, 0xFF8424, 0xAC763A, 0x402018, 0xFF6E1C, 1.18f, 1.10f, false),
    VOID_RUNEBLADE("Void Runeblade", "textures/swords/void_runeblade.png", Archetype.GREATSWORD, 0x24163A, 0xBC5CFF, 0x442A6C, 0x1A1226, 0xAF48FF, 1.18f, 1.26f, false),
    BUSTER_SWORD("Buster Sword", "textures/swords/buster_sword.png", Archetype.BUSTER, 0x848A96, 0xDEE4EE, 0x5C606C, 0x762424, 0, 1.22f, 1.55f, false),
    LEAF_BLADE("Leaf Blade", "textures/swords/leaf_blade.png", Archetype.CURVED, 0x2EA852, 0x8AF594, 0xD4B24E, 0x5C3A20, 0x5AEB78, 1.02f, 1.14f, false),
    BONE_SWORD("Bone Sword", "textures/swords/bone_sword.png", Archetype.WAVY, 0xE4DCC8, 0xF8F4E8, 0xC3B8A2, 0x765C48, 0, 1.04f, 1.14f, false),
    JADE_DAO("Jade Dao", "textures/swords/jade_dao.png", Archetype.CURVED, 0x26AF76, 0x8AF5C0, 0xE1B94E, 0x9E2226, 0x4BE19B, 1.04f, 1.22f, true),
    HOLY_AVENGER("Holy Avenger", "textures/swords/holy_avenger.png", Archetype.LONGSWORD, 0xF5F2E2, 0xFFE884, 0xEEC34E, 0xF2F0EA, 0xFFE16E, 1.14f, 1.14f, false),
    PRISM_SWORD("Prism Sword", "textures/swords/prism_sword.png", Archetype.LONGSWORD, 0xA8D7FF, 0xFF8EF5, 0xDCE8F8, 0x91A5E1, 0xA0D2FF, 1.10f, 1.10f, true),
    SHOTEL("Shotel", "textures/swords/shotel.png", Archetype.KHOPESH, 0xB8BECA, 0xEBF0F8, 0xC39444, 0x981C22, 0, 1.02f, 1.04f, false),
    GUNBLADE("Gunblade", "textures/swords/gunblade.png", Archetype.GUNBLADE, 0xB2B9C6, 0xEEF4FC, 0x4C525E, 0x2A2C32, 0, 1.08f, 1.24f, false),
    CANDY_CANE_SWORD("Candy Cane Sword", "textures/swords/candy_cane_sword.png", Archetype.CANDY_CANE, 0xF5F5F8, 0xE42028, 0x24A544, 0xE42028, 0, 1.04f, 1.00f, false),
    CORAL_BLADE("Coral Blade", "textures/swords/coral_blade.png", Archetype.CURVED, 0x2AC0C6, 0xFF766C, 0xEB605C, 0x1C7684, 0x41E1E1, 1.02f, 1.15f, false),
    THUNDER_NODACHI("Thunder Nodachi", "textures/swords/thunder_nodachi.png", Archetype.KATANA, 0xC6D0E1, 0xFFE448, 0x384058, 0x202A4C, 0xFFDA3A, 1.26f, 0.96f, false),
    BOKKEN("Bokken", "textures/swords/bokken.png", Archetype.KATANA, 0x8E5C34, 0xB27C4A, 0x6E4222, 0xB22226, 0, 1.06f, 0.94f, false),
    SERPENT_KRIS("Serpent Kris", "textures/swords/serpent_kris.png", Archetype.WAVY, 0x9EA6B2, 0xE0E6F0, 0xE1B644, 0x442A1C, 0, 1.04f, 1.04f, false),
    MOONLIGHT_GREATSWORD("Moonlight Greatsword", "textures/swords/moonlight_greatsword.png", Archetype.GREATSWORD, 0x3EDAD7, 0xB6FFFA, 0x94C0CD, 0x2C5260, 0x48F5EB, 1.20f, 1.28f, true),
    BLOODTHIRSTER("Bloodthirster", "textures/swords/bloodthirster.png", Archetype.GREATSWORD, 0x87101A, 0xFF3440, 0x30161C, 0x1C1014, 0xEB1C2A, 1.18f, 1.26f, false),
    PIXEL_SWORD("Pixel Sword", "textures/swords/pixel_sword.png", Archetype.PIXEL, 0x2DDAF5, 0xC3FAFF, 0xA848F5, 0x382A5C, 0x2DDCFF, 1.02f, 1.10f, false),
    CLOCKWORK_BLADE("Clockwork Blade", "textures/swords/clockwork_blade.png", Archetype.CLOCKWORK, 0xC6944A, 0xF2CC7A, 0x9C602E, 0x4E2C18, 0, 1.10f, 1.16f, false);

    public enum Archetype {
        KATANA,
        LONGSWORD,
        GREATSWORD,
        CURVED,
        WAVY,
        RAPIER,
        SABER,
        BUSTER,
        KHOPESH,
        GUNBLADE,
        CLOCKWORK,
        CANDY_CANE,
        PIXEL
    }

    private final String name;
    private final Identifier icon;
    private final Archetype archetype;
    private final int bladeColor;
    private final int edgeColor;
    private final int guardColor;
    private final int gripColor;
    private final int glowColor;
    private final float lengthScale;
    private final float widthScale;
    private final boolean translucentBlade;

    SwordStyle(
            String name,
            String iconPath,
            Archetype archetype,
            int bladeColor,
            int edgeColor,
            int guardColor,
            int gripColor,
            int glowColor,
            float lengthScale,
            float widthScale,
            boolean translucentBlade
    ) {
        this.name = name;
        this.icon = iconPath != null ? Identifier.of("egg", iconPath) : null;
        this.archetype = archetype;
        this.bladeColor = bladeColor;
        this.edgeColor = edgeColor;
        this.guardColor = guardColor;
        this.gripColor = gripColor;
        this.glowColor = glowColor;
        this.lengthScale = lengthScale;
        this.widthScale = widthScale;
        this.translucentBlade = translucentBlade;
    }

    public String getName() {
        return name;
    }

    public Identifier getIcon() {
        return icon;
    }

    public Archetype getArchetype() {
        return archetype;
    }

    public int getBladeColor() {
        return bladeColor;
    }

    public int getEdgeColor() {
        return edgeColor;
    }

    public int getGuardColor() {
        return guardColor;
    }

    public int getGripColor() {
        return gripColor;
    }

    public int getGlowColor() {
        return glowColor;
    }

    public float getLengthScale() {
        return lengthScale;
    }

    public float getWidthScale() {
        return widthScale;
    }

    public boolean isTranslucentBlade() {
        return translucentBlade;
    }

    public static SwordStyle fromName(String name) {
        if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name)) {
            return NONE;
        }
        for (SwordStyle style : values()) {
            if (style.name.equalsIgnoreCase(name) || style.name().equalsIgnoreCase(name)) {
                return style;
            }
        }
        return NONE;
    }
}
