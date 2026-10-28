package delta.util.animation;

public final class AnimationMath {
    private AnimationMath() {}

    public static float lerp(float start, float end, float t) {
        return start + (end - start) * clamp(t, 0.0f, 1.0f);
    }

    public static double lerp(double start, double end, double t) {
        return start + (end - start) * Math.max(0.0, Math.min(1.0, t));
    }

    public static float clamp(float val, float min, float max) {
        return val < min ? min : (val > max ? max : val);
    }

    public static int clamp(int val, int min, int max) {
        return val < min ? min : (val > max ? max : val);
    }

    public static int lerpColor(int startColor, int endColor, float t) {
        float factor = clamp(t, 0.0f, 1.0f);

        int a1 = (startColor >> 24) & 0xFF;
        int r1 = (startColor >> 16) & 0xFF;
        int g1 = (startColor >> 8) & 0xFF;
        int b1 = startColor & 0xFF;

        int a2 = (endColor >> 24) & 0xFF;
        int r2 = (endColor >> 16) & 0xFF;
        int g2 = (endColor >> 8) & 0xFF;
        int b2 = endColor & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * clamp(alpha, 0.0f, 1.0f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static float smoothDamp(float current, float target, float[] currentVelocity, float smoothTime, float maxSpeed, float deltaTime) {
        smoothTime = Math.max(0.0001f, smoothTime);
        float omega = 2.0f / smoothTime;

        float x = omega * deltaTime;
        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = current - target;
        float originalTo = target;

        float maxChange = maxSpeed * smoothTime;
        change = clamp(change, -maxChange, maxChange);
        target = current - change;

        float temp = (currentVelocity[0] + omega * change) * deltaTime;
        currentVelocity[0] = (currentVelocity[0] - omega * temp) * exp;
        float output = target + (change + temp) * exp;

        if (originalTo - current > 0.0f == output > originalTo) {
            output = originalTo;
            currentVelocity[0] = (output - originalTo) / deltaTime;
        }

        return output;
    }
}
