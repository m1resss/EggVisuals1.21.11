package egg.client.ui.theme;

import egg.client.module.misc.ClientSettings;
import egg.client.util.ColorUtil;

public final class Theme {
    private Theme() {}

    public static ClientTheme getActive() {
        return ClientSettings.getActiveTheme();
    }

    public static int getWindowBg() {
        int acc = getAccent();
        int r = Math.min(22, 7 + (int) (ColorUtil.red(acc) * 0.035f));
        int g = Math.min(22, 7 + (int) (ColorUtil.green(acc) * 0.035f));
        int b = Math.min(26, 9 + (int) (ColorUtil.blue(acc) * 0.045f));
        float opacity = ClientSettings.getGuiBgOpacity();
        int alpha = Math.clamp(Math.round(255 * opacity), 40, 255);
        return ColorUtil.rgba(r, g, b, alpha);
    }
    public static final int WINDOW_BG = ColorUtil.rgba(11, 12, 16, 215);
    public static final int WINDOW_BORDER = 0;
    public static final float WINDOW_RADIUS = 16.0f;

    public static int getSidebarBg() {
        int acc = getAccent();
        int r = Math.min(18, 5 + (int) (ColorUtil.red(acc) * 0.025f));
        int g = Math.min(18, 5 + (int) (ColorUtil.green(acc) * 0.025f));
        int b = Math.min(22, 7 + (int) (ColorUtil.blue(acc) * 0.035f));
        float opacity = Math.min(1.0f, ClientSettings.getGuiBgOpacity() * 1.05f);
        int alpha = Math.clamp(Math.round(255 * opacity), 40, 255);
        return ColorUtil.rgba(r, g, b, alpha);
    }
    public static final int SIDEBAR_BG = ColorUtil.rgba(9, 9, 12, 225);
    public static final int SIDEBAR_ACTIVE_PILL = ColorUtil.rgba(36, 36, 44, 190);
    public static final int SIDEBAR_HOVER_PILL = ColorUtil.rgba(255, 255, 255, 14);
    public static final int SIDEBAR_DIVIDER = ColorUtil.rgba(255, 255, 255, 12);

    public static int getAccent() {
        return ClientSettings.getCustomAccent();
    }

    public static int getAccentHover() {
        int acc = getAccent();
        int r = Math.min(255, (int) (ColorUtil.red(acc) * 1.15f));
        int g = Math.min(255, (int) (ColorUtil.green(acc) * 1.15f));
        int b = Math.min(255, (int) (ColorUtil.blue(acc) * 1.15f));
        return ColorUtil.rgb(r, g, b);
    }

    public static int getAccentMuted() {
        int acc = getAccent();
        return ColorUtil.rgba(ColorUtil.red(acc), ColorUtil.green(acc), ColorUtil.blue(acc), 45);
    }

    public static int getGradient1() {
        return ColorUtil.interpolate(0xFFFFFFFF, getAccent(), 0.15f);
    }

    public static int getGradient2() {
        return getAccent();
    }

    public static final int ACCENT = ColorUtil.rgb(111, 95, 176);
    public static final int ACCENT_HOVER = ColorUtil.rgb(130, 115, 195);
    public static final int ACCENT_MUTED = ColorUtil.rgba(111, 95, 176, 45);

    public static int getCardBg() {
        int acc = getAccent();
        int r = Math.min(34, 18 + (int) (ColorUtil.red(acc) * 0.045f));
        int g = Math.min(34, 18 + (int) (ColorUtil.green(acc) * 0.045f));
        int b = Math.min(42, 24 + (int) (ColorUtil.blue(acc) * 0.055f));
        return ColorUtil.rgba(r, g, b, 210);
    }
    public static int getCardBgHover() {
        int acc = getAccent();
        int r = Math.min(44, 25 + (int) (ColorUtil.red(acc) * 0.065f));
        int g = Math.min(44, 25 + (int) (ColorUtil.green(acc) * 0.065f));
        int b = Math.min(52, 32 + (int) (ColorUtil.blue(acc) * 0.075f));
        return ColorUtil.rgba(r, g, b, 230);
    }

    public static final int CARD_BG = ColorUtil.rgba(19, 21, 29, 210);
    public static final int CARD_BG_HOVER = ColorUtil.rgba(25, 27, 38, 230);
    public static final int CARD_BORDER = ColorUtil.rgba(255, 255, 255, 16);
    public static final int CARD_BORDER_HOVER = ColorUtil.rgba(255, 255, 255, 30);
    public static final float CARD_RADIUS = 12.0f;

    public static final int TOGGLE_OFF = ColorUtil.rgb(24, 24, 28);
    public static int getToggleOn() { return getAccent(); }
    public static final int TOGGLE_ON = ACCENT;
    public static final int TOGGLE_KNOB = ColorUtil.rgb(230, 232, 236);

    public static final int TEXT_TITLE = 0xFFFFFFFF;
    public static final int TEXT_MUTED = ColorUtil.rgb(130, 134, 145);
    public static final int TEXT_SUB = ColorUtil.rgb(180, 185, 196);
    public static final int TEXT_DIM = ColorUtil.rgb(105, 108, 118);

    public static final int STAR_INACTIVE = ColorUtil.rgba(255, 255, 255, 45);
    public static final int STAR_ACTIVE = ColorUtil.rgb(245, 158, 11);
    public static final int DOTS_INACTIVE = ColorUtil.rgba(255, 255, 255, 45);
    public static final int DOTS_ACTIVE = ColorUtil.rgb(237, 237, 237);
}
