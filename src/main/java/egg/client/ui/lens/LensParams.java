package egg.client.ui.lens;

public final class LensParams {
    private LensParams() {}

    public static final long OPEN_DURATION_MS = 800L;
    public static final long CLOSE_DURATION_MS = 600L;

    public static final float MIN_RADIUS = 30.0f;
    public static final float RIPPLE_FREQ = 28.0f;
    public static final float RIPPLE_SPEED = 9.0f;
    public static final float RIPPLE_AMP = 0.012f;

    public static final float WAVY_EDGE_FREQ = 7.0f;
    public static final float WAVY_EDGE_SPEED = 4.0f;
    public static final float WAVY_EDGE_AMP = 0.03f;

    public static final float SWIRL_STRENGTH = 0.6f;
    public static final float GLASS_SHEEN_ALPHA = 0.15f;

    public static float easeOutExpo(float x) {
        return (x >= 1.0f) ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * x);
    }

    public static float easeInCubic(float x) {
        return x * x * x;
    }

    public static float easeInOutQuart(float x) {
        return x < 0.5f ? 8.0f * x * x * x * x : 1.0f - (float) Math.pow(-2.0 * x + 2.0, 4.0) / 2.0f;
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
