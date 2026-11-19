package egg.client.util.animation;

@FunctionalInterface
public interface Easing {
    float ease(float x);

    Easing LINEAR = x -> x;

    Easing EASE_OUT_CUBIC = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f) - 1.0f;
        return t * t * t + 1.0f;
    };

    Easing EASE_OUT_QUART = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f) - 1.0f;
        return 1.0f - t * t * t * t;
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

    Easing EASE_OUT_SPRING = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t - 0.075f) * (2.0f * (float) Math.PI) / 0.3f) + 1.0f);
    };

    Easing EASE_OUT_ELASTIC = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f);
        if (t <= 0.0f) return 0.0f;
        if (t >= 1.0f) return 1.0f;
        float p = 0.3f;
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t - p / 4.0f) * (2.0f * (float) Math.PI) / p) + 1.0f);
    };

    Easing EASE_OUT_BOUNCE = x -> {
        float t = Math.clamp(x, 0.0f, 1.0f);
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    };
}
