package delta.module.render.killeffect;

public final class KillEffectEasing {
    private KillEffectEasing() {}

    public static float quintOut(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 5.0);
    }

    public static float sineInOut(float t) {
        return -(float) (Math.cos(Math.PI * (double) t) - 1.0) / 2.0f;
    }

    public static float sineOut(float t) {
        return (float) Math.sin(((double) t * Math.PI) / 2.0);
    }
}
