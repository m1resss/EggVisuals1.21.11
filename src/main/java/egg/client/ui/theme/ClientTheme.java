package egg.client.ui.theme;

import egg.client.util.ColorUtil;

public enum ClientTheme {
    GRAPHITE("Graphite",
            ColorUtil.rgb(111, 95, 176),
            ColorUtil.rgb(135, 118, 204),
            ColorUtil.rgba(111, 95, 176, 55),
            ColorUtil.rgba(26, 26, 31, 235),
            ColorUtil.rgba(35, 35, 41, 255),
            ColorUtil.rgba(42, 42, 50, 255)),

    PURPLE("Midnight Purple",
            ColorUtil.rgb(168, 85, 247),
            ColorUtil.rgb(192, 132, 252),
            ColorUtil.rgba(168, 85, 247, 55),
            ColorUtil.rgba(24, 21, 36, 235),
            ColorUtil.rgba(33, 29, 48, 255),
            ColorUtil.rgba(41, 36, 60, 255)),

    OCEAN("Ocean Blue",
            ColorUtil.rgb(59, 130, 246),
            ColorUtil.rgb(96, 165, 250),
            ColorUtil.rgba(59, 130, 246, 55),
            ColorUtil.rgba(18, 27, 42, 235),
            ColorUtil.rgba(25, 38, 58, 255),
            ColorUtil.rgba(32, 48, 74, 255)),

    EMERALD("Emerald Green",
            ColorUtil.rgb(16, 185, 129),
            ColorUtil.rgb(52, 211, 153),
            ColorUtil.rgba(16, 185, 129, 55),
            ColorUtil.rgba(17, 34, 27, 235),
            ColorUtil.rgba(24, 46, 37, 255),
            ColorUtil.rgba(30, 58, 47, 255)),

    CRIMSON("Crimson Ruby",
            ColorUtil.rgb(244, 63, 94),
            ColorUtil.rgb(251, 113, 133),
            ColorUtil.rgba(244, 63, 94, 55),
            ColorUtil.rgba(36, 20, 24, 235),
            ColorUtil.rgba(48, 27, 33, 255),
            ColorUtil.rgba(60, 34, 42, 255)),

    SUNSET("Sunset Amber",
            ColorUtil.rgb(245, 158, 11),
            ColorUtil.rgb(251, 191, 36),
            ColorUtil.rgba(245, 158, 11, 55),
            ColorUtil.rgba(36, 28, 20, 235),
            ColorUtil.rgba(48, 38, 28, 255),
            ColorUtil.rgba(60, 48, 36, 255));

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
