package delta.util.animation;

@FunctionalInterface
public interface Easing {
    float ease(float t);

    Easing LINEAR = t -> t;

    Easing EASE_OUT_CUBIC = t -> {
        float f = t - 1.0f;
        return f * f * f + 1.0f;
    };

    Easing EASE_IN_OUT_CUBIC = t -> {
        if (t < 0.5f) {
            return 4.0f * t * t * t;
        } else {
            float f = 2.0f * t - 2.0f;
            return 0.5f * f * f * f + 1.0f;
        }
    };

    Easing EASE_OUT_EXPO = t -> (t >= 1.0f) ? 1.0f : (1.0f - (float) Math.pow(2.0, -10.0 * t));

    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float f = t - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    };

    Easing EASE_IN_OUT_QUART = t -> {
        if (t < 0.5f) {
            return 8.0f * t * t * t * t;
        } else {
            float f = t - 1.0f;
            return 1.0f - 8.0f * f * f * f * f;
        }
    };
}
