package egg.client.util.animation;

@FunctionalInterface
public interface Easing {
    float ease(float x);

    Easing LINEAR = x -> x;

    Easing EASE_OUT_CUBIC = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f) - 1.0f;
        return t * t * t + 1.0f;
    };

    Easing EASE_OUT_EXPO = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f);
        return (t == 1.0f) ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    };

    Easing EASE_IN_OUT_QUART = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f);
        return t < 0.5f ? 8.0f * t * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 4) / 2.0f;
    };

    Easing EASE_OUT_BACK = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f) - 1.0f;
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * t * t * t + c1 * t * t;
    };
}
