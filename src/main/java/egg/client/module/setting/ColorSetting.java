package egg.client.module.setting;

import egg.client.util.ColorUtil;

public class ColorSetting extends Setting {
    private int color;
    private final int defaultColor;
    private boolean opened = false;

    public ColorSetting(String name, int defaultColor, String description) {
        super(name, description);
        this.color = defaultColor;
        this.defaultColor = defaultColor;
    }

    public ColorSetting(String name, int defaultColor) {
        this(name, defaultColor, "");
    }

    public int get() {
        return color;
    }

    public void set(int color) {
        this.color = color;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public void reset() {
        this.color = defaultColor;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public void toggleOpened() {
        this.opened = !this.opened;
    }

    public float getRed() {
        return ColorUtil.red(color) / 255.0f;
    }

    public float getGreen() {
        return ColorUtil.green(color) / 255.0f;
    }

    public float getBlue() {
        return ColorUtil.blue(color) / 255.0f;
    }

    public float getAlpha() {
        return ((color >> 24) & 255) / 255.0f;
    }

    public float[] getHSB() {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        return java.awt.Color.RGBtoHSB(r, g, b, null);
    }

    public void setFromHSB(float hue, float saturation, float brightness) {
        int alpha = (color >> 24) & 255;
        if (alpha == 0) alpha = 255;
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        this.color = (alpha << 24) | rgb;
    }

    public void setFromHSBA(float hue, float saturation, float brightness, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        this.color = (a << 24) | rgb;
    }

    public String getHex() {
        int a = (color >> 24) & 255;
        if (a < 255) {
            return String.format("#%08X", color);
        }
        return String.format("#%06X", (color & 0xFFFFFF));
    }
}
