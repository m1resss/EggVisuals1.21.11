package egg.client.render.msdf;

public class MathUtil {
    private MathUtil() {}

    public static float a(float start, float end, float delta) {
        return start + ((end - start) * delta);
    }

    public static float b(float num, float min, float max) {
        return Math.min(Math.max(num, min), max);
    }

    public static float a(float value) {
        float clamped = b(value, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    public static float a(float min, float max) {
        return (float) ((Math.random() * ((double) (max - min))) + ((double) min));
    }

    public static boolean a(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= ((double) x) && mouseX <= ((double) (x + width)) && mouseY >= ((double) y) && mouseY <= ((double) (y + height));
    }

    public static float c(float current, float target, float speed) {
        float delta = 0.05f;
        return current + ((target - current) * (1.0f - ((float) Math.exp((-speed) * delta))));
    }
}
