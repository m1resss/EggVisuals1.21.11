package egg.client.render.world;

import net.minecraft.util.math.MathHelper;
import org.joml.Vector4f;

public enum WeatherProfile {
    RAIN(0.78f, 0, 96, -1766406440, 0.55f, 1, -1710880240, -4927770, 1, 0.6f, 1, 0.2f, -12959156, 0.84f, 0.72f),
    FILM(0.78f, 0, 96, -1766406440, 0.55f, 1, -1274672624, -4927770, 0.8f, 0.35f, 0, 0.2f, -12959156, 0.84f, 0.72f),
    FOG(0, 0, 40, 0, 0, 0.5f, 772937760, -5652790, 0.38f, 0.18f, 1, 1, -7564132, 0.96f, 0.65f),
    SNOW(0, 0.42f, 150, -924520710, 0, 1, 672143400, -2693388, 0.14f, 0.1f, 0, 0.18f, -4336924, 1.06f, 0.72f);

    public final float rain;
    public final float snow;
    public final float fogDistance;
    public final int precipitationColor;
    public final float splash;
    public final float wetness;
    public final float darkening;
    public final int wetColor;
    public final float reflection;
    public final float glint;
    public final float puddles;
    public final float mist;
    public final int mistColor;
    public final float brightness;
    public final float saturation;

    WeatherProfile(float rain, float snow, float fogDistance, int precipitationColor, float splash, float wetness,
                   int darkening, int wetColor, float reflection, float glint, float puddles, float mist, int mistColor,
                   float brightness, float saturation) {
        this.rain = rain;
        this.snow = snow;
        this.fogDistance = fogDistance;
        this.precipitationColor = precipitationColor;
        this.splash = splash;
        this.wetness = wetness;
        this.darkening = (darkening >>> 24) / 255f;
        this.wetColor = wetColor;
        this.reflection = reflection;
        this.glint = glint;
        this.puddles = puddles;
        this.mist = mist;
        this.mistColor = mistColor;
        this.brightness = brightness;
        this.saturation = saturation;
    }

    public static float noise(long seed, int index) {
        long value = seed * -7046029254386353131L + index * -3335678366873096957L;
        value = (value ^ value >>> 33) * -49064778989728563L;
        return ((value ^ value >>> 33) >>> 40) / 16777216f;
    }

    public static float smooth(float value) {
        float progress = MathHelper.clamp(value, 0, 1);
        return progress * progress * (3 - 2 * progress);
    }

    public static Vector4f color(int argb) {
        return new Vector4f((argb >>> 16 & 255) / 255f, (argb >>> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
