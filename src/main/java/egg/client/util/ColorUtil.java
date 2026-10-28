package egg.client.util;

public final class ColorUtil {
    private ColorUtil() {}

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int withAlphaFloat(int color, float alpha) {
        int origAlpha = getAlpha(color);
        int a = Math.clamp((int) (origAlpha * alpha), 0, 255);
        return withAlpha(color, a);
    }

    public static int lerp(int from, int to, float factor) {
        factor = Math.clamp(factor, 0.0f, 1.0f);
        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return rgba(r, g, b, a);
    }

    public static int interpolate(int from, int to, float factor) {
        return lerp(from, to, factor);
    }

    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }

    public static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int getBlue(int color) {
        return color & 0xFF;
    }

    public static int red(int color) {
        return getRed(color);
    }

    public static int green(int color) {
        return getGreen(color);
    }

    public static int blue(int color) {
        return getBlue(color);
    }
}
