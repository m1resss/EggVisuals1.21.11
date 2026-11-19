package egg.client.ui.theme;

import egg.client.util.ColorUtil;

public enum ClientTheme {
    GRAPHITE("Graphite",
            ColorUtil.rgb(111, 95, 176),
            ColorUtil.rgb(130, 115, 195),
            ColorUtil.rgba(111, 95, 176, 45),
            ColorUtil.rgba(14, 14, 18, 160),
            ColorUtil.rgba(20, 20, 26, 135),
            ColorUtil.rgba(28, 28, 36, 160)),

    PURPLE("Midnight Purple",
            ColorUtil.rgb(168, 85, 247),
            ColorUtil.rgb(192, 132, 252),
            ColorUtil.rgba(168, 85, 247, 55),
            ColorUtil.rgba(18, 14, 28, 160),
            ColorUtil.rgba(26, 20, 38, 135),
            ColorUtil.rgba(34, 26, 48, 160)),

    OCEAN("Ocean Blue",
            ColorUtil.rgb(59, 130, 246),
            ColorUtil.rgb(96, 165, 250),
            ColorUtil.rgba(59, 130, 246, 55),
            ColorUtil.rgba(12, 18, 28, 160),
            ColorUtil.rgba(18, 26, 40, 135),
            ColorUtil.rgba(24, 34, 52, 160)),

    EMERALD("Emerald Green",
            ColorUtil.rgb(16, 185, 129),
            ColorUtil.rgb(52, 211, 153),
            ColorUtil.rgba(16, 185, 129, 55),
            ColorUtil.rgba(11, 22, 18, 160),
            ColorUtil.rgba(16, 32, 26, 135),
            ColorUtil.rgba(22, 42, 34, 160)),

    CRIMSON("Crimson Ruby",
            ColorUtil.rgb(244, 63, 94),
            ColorUtil.rgb(251, 113, 133),
            ColorUtil.rgba(244, 63, 94, 55),
            ColorUtil.rgba(24, 14, 18, 160),
            ColorUtil.rgba(34, 18, 24, 135),
            ColorUtil.rgba(44, 24, 30, 160)),

    SUNSET("Sunset Amber",
            ColorUtil.rgb(245, 158, 11),
            ColorUtil.rgb(251, 191, 36),
            ColorUtil.rgba(245, 158, 11, 55),
            ColorUtil.rgba(24, 18, 14, 160),
            ColorUtil.rgba(34, 26, 18, 135),
            ColorUtil.rgba(44, 34, 24, 160));

    private final String displayName;
    public final int accent;
    public final int accentHover;
    public final int accentMuted;
    public final int windowBg;
    public final int cardBg;
    public final int cardBgHover;

    ClientTheme(String displayName, int accent, int accentHover, int accentMuted, int windowBg, int cardBg, int cardBgHover) {
        this.displayName = displayName;
        this.accent = accent;
        this.accentHover = accentHover;
        this.accentMuted = accentMuted;
        this.windowBg = windowBg;
        this.cardBg = cardBg;
        this.cardBgHover = cardBgHover;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ClientTheme fromName(String name) {
        for (ClientTheme t : values()) {
            if (t.displayName.equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return GRAPHITE;
    }
}