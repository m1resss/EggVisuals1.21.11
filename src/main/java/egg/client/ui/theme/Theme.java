package egg.client.ui.theme;

import egg.client.module.misc.ClientSettings;
import egg.client.util.ColorUtil;

public final class Theme {
    private Theme() {}

    public static ClientTheme getActive() {
        return ClientSettings.getActiveTheme();
    }

    // Window
    public static int getWindowBg() { return getActive().windowBg; }
    public static final int WINDOW_BG = ColorUtil.rgba(26, 26, 31, 235);
    public static final int WINDOW_BORDER = ColorUtil.rgba(255, 255, 255, 20);
    public static final float WINDOW_RADIUS = 14.0f;

    // Sidebar
    public static final int SIDEBAR_ACTIVE_PILL = ColorUtil.rgb(46, 46, 53);
    public static final int SIDEBAR_HOVER_PILL = ColorUtil.rgba(40, 40, 46, 180);
    public static final int SIDEBAR_DIVIDER = ColorUtil.rgba(255, 255, 255, 18);

    // Accent
    public static int getAccent() {
        return ClientSettings.getCustomAccent();
    }

    public static int getAccentHover() {
        int acc = getAccent();
        int r = Math.min(255, (int) (ColorUtil.red(acc) * 1.2f));
        int g = Math.min(255, (int) (ColorUtil.green(acc) * 1.2f));
        int b = Math.min(255, (int) (ColorUtil.blue(acc) * 1.2f));
        return ColorUtil.rgb(r, g, b);
    }

    public static int getAccentMuted() {
        int acc = getAccent();
        return ColorUtil.rgba(ColorUtil.red(acc), ColorUtil.green(acc), ColorUtil.blue(acc), 55);
    }

    public static final int ACCENT = ColorUtil.rgb(111, 95, 176);
    public static final int ACCENT_HOVER = ColorUtil.rgb(135, 118, 204);
    public static final int ACCENT_MUTED = ColorUtil.rgba(111, 95, 176, 55);

    // Cards
    public static int getCardBg() { return getActive().cardBg; }
    public static int getCardBgHover() { return getActive().cardBgHover; }

    public static final int CARD_BG = ColorUtil.rgba(35, 35, 41, 255);
    public static final int CARD_BG_HOVER = ColorUtil.rgba(42, 42, 50, 255);
    public static final int CARD_BORDER = ColorUtil.rgba(255, 255, 255, 18);
    public static final int CARD_BORDER_HOVER = ColorUtil.rgba(255, 255, 255, 30);
    public static final float CARD_RADIUS = 10.0f;

    // Toggles
    public static final int TOGGLE_OFF = ColorUtil.rgb(36, 38, 48);
    public static int getToggleOn() { return getAccent(); }
    public static final int TOGGLE_ON = ACCENT;
    public static final int TOGGLE_KNOB = ColorUtil.rgb(255, 255, 255);

    // Typography
    public static final int TEXT_TITLE = ColorUtil.rgb(255, 255, 255);
    public static final int TEXT_MUTED = ColorUtil.rgb(215, 218, 230);
    public static final int TEXT_SUB = TEXT_MUTED;
    public static final int TEXT_DIM = ColorUtil.rgb(175, 180, 195);

    // Icons
    public static final int STAR_INACTIVE = ColorUtil.rgba(255, 255, 255, 75);
    public static final int STAR_ACTIVE = ColorUtil.rgb(245, 158, 11);
    public static final int DOTS_INACTIVE = ColorUtil.rgba(255, 255, 255, 90);
    public static final int DOTS_ACTIVE = ColorUtil.rgb(255, 255, 255);
}
