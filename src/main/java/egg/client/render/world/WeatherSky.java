package egg.client.render.world;

import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class WeatherSky {
    private static final SkyKey[] CLEAR = {
            new SkyKey(0.0f, rgb(0.018f, 0.03f, 0.075f), rgb(0.055f, 0.08f, 0.15f),
                    rgb(0.02f, 0.028f, 0.05f), rgb(0.13f, 0.16f, 0.235f), rgb(0.04f, 0.052f, 0.085f), 0.22f, 0.35f, 1.05f, 0.018f, 0.16f, rgb(0.07f, 0.1f, 0.18f), 0.8f, 0.42f, 0.0f, 0.22f, 0.02f, 0.3f, 0.72f, 0.0f),
            new SkyKey(4.8f, rgb(0.055f, 0.105f, 0.235f), rgb(0.28f, 0.26f, 0.33f),
                    rgb(0.055f, 0.07f, 0.105f), rgb(0.43f, 0.43f, 0.52f), rgb(0.15f, 0.165f, 0.23f), 0.24f, 0.28f, 1.05f, 0.019f, 0.3f, rgb(0.34f, 0.31f, 0.38f), 0.34f, 0.16f, 0.0f, 0.08f, 0.022f, 0.35f, 0.8f, 0.0f),
            new SkyKey(6.3f, rgb(0.14f, 0.265f, 0.545f), rgb(0.98f, 0.64f, 0.43f),
                    rgb(0.12f, 0.115f, 0.145f), rgb(1.0f, 0.815f, 0.67f), rgb(0.4f, 0.39f, 0.5f), 0.28f, 0.18f, 1.05f, 0.02f, 0.42f, rgb(0.98f, 0.68f, 0.48f), 0.05f, 0.02f, 0.0f, 0.02f, 0.032f, 0.95f, 0.92f, 0.0f),
            new SkyKey(8.0f, rgb(0.15f, 0.35f, 0.74f), rgb(0.76f, 0.83f, 0.93f),
                    rgb(0.23f, 0.29f, 0.38f), rgb(1.0f, 0.985f, 0.955f), rgb(0.62f, 0.685f, 0.805f), 0.3f, 0.1f, 1.05f, 0.02f, 0.26f, rgb(0.72f, 0.8f, 0.92f), 0.0f, 0.0f, 0.0f, 0.0f, 0.03f, 0.85f, 1.02f, 0.0f),
            new SkyKey(12.0f, rgb(0.095f, 0.315f, 0.8f), rgb(0.73f, 0.86f, 0.985f),
                    rgb(0.25f, 0.32f, 0.42f), rgb(1.0f, 1.0f, 1.0f), rgb(0.66f, 0.74f, 0.865f), 0.32f, 0.07f, 1.0f, 0.021f, 0.2f, rgb(0.76f, 0.865f, 0.965f), 0.0f, 0.0f, 0.0f, 0.0f, 0.03f, 0.88f, 1.08f, 0.0f),
            new SkyKey(16.8f, rgb(0.135f, 0.335f, 0.735f), rgb(0.8f, 0.83f, 0.91f),
                    rgb(0.235f, 0.29f, 0.375f), rgb(1.0f, 0.975f, 0.93f), rgb(0.64f, 0.69f, 0.8f), 0.3f, 0.11f, 1.05f, 0.02f, 0.28f, rgb(0.82f, 0.82f, 0.9f), 0.0f, 0.0f, 0.0f, 0.0f, 0.03f, 0.88f, 1.0f, 0.0f),
            new SkyKey(18.3f, rgb(0.13f, 0.23f, 0.5f), rgb(1.0f, 0.58f, 0.36f),
                    rgb(0.115f, 0.105f, 0.135f), rgb(1.0f, 0.76f, 0.6f), rgb(0.395f, 0.36f, 0.46f), 0.28f, 0.2f, 1.05f, 0.02f, 0.46f, rgb(1.0f, 0.62f, 0.41f), 0.06f, 0.03f, 0.0f, 0.03f, 0.032f, 0.95f, 0.9f, 0.0f),
            new SkyKey(20.5f, rgb(0.045f, 0.08f, 0.195f), rgb(0.2f, 0.18f, 0.275f),
                    rgb(0.045f, 0.058f, 0.09f), rgb(0.33f, 0.34f, 0.44f), rgb(0.11f, 0.125f, 0.185f), 0.24f, 0.3f, 1.05f, 0.019f, 0.26f, rgb(0.25f, 0.24f, 0.33f), 0.48f, 0.22f, 0.0f, 0.14f, 0.022f, 0.34f, 0.78f, 0.0f),
    };

    private static final SkyKey[] STORM = {
            new SkyKey(0.0f, rgb(0.022f, 0.028f, 0.036f), rgb(0.052f, 0.06f, 0.07f),
                    rgb(0.016f, 0.02f, 0.026f), rgb(0.115f, 0.13f, 0.15f), rgb(0.026f, 0.032f, 0.04f), 0.97f, 0.9f, 1.55f, 0.036f, 0.44f, rgb(0.06f, 0.068f, 0.08f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.05f, 0.66f, 0.26f),
            new SkyKey(4.8f, rgb(0.048f, 0.055f, 0.066f), rgb(0.105f, 0.11f, 0.125f),
                    rgb(0.03f, 0.035f, 0.042f), rgb(0.2f, 0.215f, 0.24f), rgb(0.05f, 0.058f, 0.07f), 0.96f, 0.88f, 1.55f, 0.036f, 0.48f, rgb(0.115f, 0.12f, 0.135f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.06f, 0.72f, 0.24f),
            new SkyKey(6.3f, rgb(0.085f, 0.092f, 0.105f), rgb(0.25f, 0.195f, 0.175f),
                    rgb(0.05f, 0.055f, 0.065f), rgb(0.34f, 0.315f, 0.315f), rgb(0.08f, 0.085f, 0.098f), 0.97f, 0.86f, 1.55f, 0.038f, 0.56f, rgb(0.245f, 0.205f, 0.19f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.08f, 0.8f, 0.28f),
            new SkyKey(8.0f, rgb(0.135f, 0.155f, 0.175f), rgb(0.265f, 0.29f, 0.31f),
                    rgb(0.07f, 0.08f, 0.092f), rgb(0.45f, 0.48f, 0.51f), rgb(0.092f, 0.105f, 0.122f), 0.97f, 0.85f, 1.55f, 0.038f, 0.52f, rgb(0.265f, 0.29f, 0.312f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 0.88f, 0.3f),
            new SkyKey(12.0f, rgb(0.16f, 0.185f, 0.205f), rgb(0.31f, 0.34f, 0.36f),
                    rgb(0.082f, 0.094f, 0.108f), rgb(0.51f, 0.545f, 0.575f), rgb(0.1f, 0.115f, 0.135f), 0.98f, 0.85f, 1.6f, 0.04f, 0.55f, rgb(0.305f, 0.335f, 0.358f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 0.92f, 0.34f),
            new SkyKey(16.8f, rgb(0.14f, 0.158f, 0.176f), rgb(0.28f, 0.3f, 0.318f),
                    rgb(0.072f, 0.082f, 0.095f), rgb(0.47f, 0.495f, 0.522f), rgb(0.094f, 0.107f, 0.125f), 0.97f, 0.86f, 1.55f, 0.039f, 0.54f, rgb(0.278f, 0.298f, 0.318f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.09f, 0.88f, 0.32f),
            new SkyKey(18.3f, rgb(0.082f, 0.088f, 0.1f), rgb(0.255f, 0.19f, 0.165f),
                    rgb(0.048f, 0.053f, 0.062f), rgb(0.33f, 0.3f, 0.298f), rgb(0.078f, 0.082f, 0.094f), 0.97f, 0.87f, 1.55f, 0.038f, 0.58f, rgb(0.25f, 0.2f, 0.18f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.08f, 0.8f, 0.3f),
            new SkyKey(20.5f, rgb(0.04f, 0.046f, 0.056f), rgb(0.09f, 0.095f, 0.108f),
                    rgb(0.026f, 0.03f, 0.037f), rgb(0.175f, 0.188f, 0.21f), rgb(0.042f, 0.049f, 0.06f), 0.97f, 0.89f, 1.55f, 0.037f, 0.46f, rgb(0.098f, 0.104f, 0.118f), 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.06f, 0.7f, 0.28f),
    };

    private static final SkyKey[] STARS = {
            new SkyKey(0.0f, rgb(0.012f, 0.02f, 0.058f), rgb(0.05f, 0.078f, 0.15f),
                    rgb(0.014f, 0.02f, 0.04f), rgb(0.3f, 0.36f, 0.5f), rgb(0.055f, 0.075f, 0.13f), 0.34f, 0.38f, 1.2f, 0.014f, 0.18f, rgb(0.07f, 0.105f, 0.195f), 1.15f, 1.0f, 0.0f, 0.55f, 0.024f, 0.6f, 0.7f, 0.0f),
            new SkyKey(4.8f, rgb(0.02f, 0.032f, 0.082f), rgb(0.09f, 0.115f, 0.195f),
                    rgb(0.02f, 0.028f, 0.05f), rgb(0.34f, 0.395f, 0.53f), rgb(0.065f, 0.085f, 0.145f), 0.34f, 0.36f, 1.2f, 0.014f, 0.22f, rgb(0.105f, 0.13f, 0.21f), 1.05f, 0.92f, 0.0f, 0.48f, 0.024f, 0.58f, 0.74f, 0.0f),
            new SkyKey(6.3f, rgb(0.03f, 0.048f, 0.11f), rgb(0.195f, 0.165f, 0.225f),
                    rgb(0.026f, 0.034f, 0.058f), rgb(0.43f, 0.43f, 0.52f), rgb(0.08f, 0.095f, 0.155f), 0.34f, 0.34f, 1.2f, 0.015f, 0.28f, rgb(0.185f, 0.165f, 0.23f), 0.98f, 0.86f, 0.0f, 0.44f, 0.026f, 0.66f, 0.78f, 0.0f),
            new SkyKey(8.0f, rgb(0.04f, 0.062f, 0.145f), rgb(0.15f, 0.185f, 0.3f),
                    rgb(0.032f, 0.042f, 0.07f), rgb(0.48f, 0.52f, 0.64f), rgb(0.09f, 0.11f, 0.175f), 0.34f, 0.34f, 1.2f, 0.015f, 0.26f, rgb(0.15f, 0.185f, 0.295f), 0.95f, 0.84f, 0.0f, 0.44f, 0.026f, 0.7f, 0.82f, 0.0f),
            new SkyKey(12.0f, rgb(0.048f, 0.075f, 0.175f), rgb(0.175f, 0.215f, 0.345f),
                    rgb(0.036f, 0.048f, 0.078f), rgb(0.545f, 0.59f, 0.715f), rgb(0.1f, 0.122f, 0.19f), 0.34f, 0.32f, 1.2f, 0.016f, 0.24f, rgb(0.175f, 0.215f, 0.335f), 0.9f, 0.8f, 0.0f, 0.42f, 0.028f, 0.74f, 0.86f, 0.0f),
            new SkyKey(16.8f, rgb(0.042f, 0.066f, 0.152f), rgb(0.155f, 0.19f, 0.308f),
                    rgb(0.033f, 0.044f, 0.072f), rgb(0.5f, 0.54f, 0.66f), rgb(0.092f, 0.112f, 0.178f), 0.34f, 0.33f, 1.2f, 0.015f, 0.26f, rgb(0.155f, 0.19f, 0.3f), 0.95f, 0.84f, 0.0f, 0.44f, 0.026f, 0.7f, 0.82f, 0.0f),
            new SkyKey(18.3f, rgb(0.03f, 0.048f, 0.112f), rgb(0.2f, 0.16f, 0.22f),
                    rgb(0.026f, 0.034f, 0.058f), rgb(0.43f, 0.42f, 0.51f), rgb(0.08f, 0.095f, 0.155f), 0.34f, 0.34f, 1.2f, 0.015f, 0.3f, rgb(0.19f, 0.16f, 0.225f), 1.0f, 0.88f, 0.0f, 0.48f, 0.026f, 0.66f, 0.78f, 0.0f),
            new SkyKey(20.5f, rgb(0.018f, 0.028f, 0.075f), rgb(0.07f, 0.098f, 0.175f),
                    rgb(0.018f, 0.025f, 0.046f), rgb(0.33f, 0.385f, 0.52f), rgb(0.062f, 0.082f, 0.14f), 0.34f, 0.37f, 1.2f, 0.014f, 0.2f, rgb(0.09f, 0.118f, 0.2f), 1.1f, 0.96f, 0.0f, 0.52f, 0.024f, 0.6f, 0.72f, 0.0f),
    };

    private static final LightKey[] LIGHT = {
            new LightKey(0.0f, 0.15f, 1.0f, 0.8f, 0.86f, 1.14f, 0.6f, 0.68f, 0.92f, 0.38f, 0.92f, 0.5f, 0.82f, 0.68f, 1.12f),
            new LightKey(3.0f, 0.14f, 1.0f, 0.79f, 0.85f, 1.15f, 0.58f, 0.66f, 0.92f, 0.36f, 0.95f, 0.52f, 0.8f, 0.66f, 1.12f),
            new LightKey(4.4f, 0.2f, 1.0f, 0.82f, 0.86f, 1.12f, 0.66f, 0.7f, 0.9f, 0.42f, 0.78f, 0.42f, 0.88f, 0.7f, 1.14f),
            new LightKey(5.4f, 0.34f, 0.9f, 1.0f, 0.9f, 1.02f, 0.78f, 0.78f, 0.9f, 0.6f, 0.34f, 0.14f, 1.2f, 0.78f, 1.16f),
            new LightKey(6.2f, 0.58f, 0.82f, 1.16f, 0.94f, 0.88f, 1.26f, 0.66f, 0.42f, 1.5f, 0.08f, 0.03f, 1.42f, 0.85f, 1.18f),
            new LightKey(7.4f, 0.82f, 0.9f, 1.1f, 1.0f, 0.9f, 1.18f, 0.84f, 0.58f, 1.35f, 0.01f, 0.0f, 1.32f, 0.93f, 1.1f),
            new LightKey(9.6f, 0.93f, 0.97f, 1.04f, 1.0f, 0.95f, 1.08f, 0.92f, 0.74f, 1.14f, 0.0f, 0.0f, 1.14f, 1.0f, 1.03f),
            new LightKey(12.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.97f, 0.86f, 1.0f, 0.0f, 0.0f, 1.0f, 1.05f, 1.0f),
            new LightKey(14.4f, 0.98f, 1.0f, 1.02f, 1.0f, 0.98f, 1.02f, 0.95f, 0.82f, 1.05f, 0.0f, 0.0f, 1.06f, 1.04f, 1.0f),
            new LightKey(16.4f, 0.9f, 0.96f, 1.07f, 0.99f, 0.92f, 1.14f, 0.87f, 0.64f, 1.22f, 0.0f, 0.0f, 1.2f, 0.98f, 1.05f),
            new LightKey(17.4f, 0.8f, 0.86f, 1.18f, 0.96f, 0.86f, 1.3f, 0.74f, 0.46f, 1.55f, 0.02f, 0.01f, 1.4f, 0.92f, 1.14f),
            new LightKey(18.2f, 0.55f, 0.82f, 1.16f, 0.9f, 0.92f, 0.84f, 0.78f, 0.88f, 0.7f, 0.16f, 0.06f, 1.36f, 0.84f, 1.18f),
            new LightKey(19.4f, 0.34f, 0.9f, 0.98f, 0.88f, 1.04f, 0.72f, 0.74f, 0.92f, 0.58f, 0.46f, 0.22f, 1.12f, 0.78f, 1.16f),
            new LightKey(20.6f, 0.22f, 0.98f, 0.86f, 0.87f, 1.1f, 0.66f, 0.7f, 0.92f, 0.48f, 0.72f, 0.38f, 0.94f, 0.72f, 1.14f),
            new LightKey(22.0f, 0.17f, 1.0f, 0.82f, 0.87f, 1.12f, 0.64f, 0.7f, 0.92f, 0.44f, 0.84f, 0.44f, 0.88f, 0.7f, 1.12f),
    };

    private static final SkyKey RAIN = new SkyKey(12, rgb(0.098f, 0.116f, 0.142f), rgb(0.178f, 0.202f, 0.232f),
            rgb(0.062f, 0.07f, 0.084f), rgb(0.38f, 0.41f, 0.47f), rgb(0.19f, 0.21f, 0.26f),
            0.93f, 0.58f, 1.45f, 0.032f, 0.45f, rgb(0.2f, 0.23f, 0.27f), 0, 0, 0, 0, 0, 0.2f, 0.84f, 0.22f);
    private static final SkyKey SNOW = new SkyKey(12, rgb(0.105f, 0.3f, 0.7f), rgb(0.7f, 0.82f, 0.94f),
            rgb(0.27f, 0.35f, 0.46f), rgb(1, 0.995f, 0.98f), rgb(0.64f, 0.72f, 0.84f),
            0.26f, 0.1f, 1.05f, 0.02f, 0.26f, rgb(0.66f, 0.78f, 0.92f), 0.55f, 0.32f, 0, 0.24f,
            0.03f, 0.85f, 1.06f, 0);
    private static final SkyKey FOG = new SkyKey(12, rgb(0.28f, 0.3f, 0.33f), rgb(0.4f, 0.42f, 0.45f),
            rgb(0.24f, 0.25f, 0.28f), rgb(0.52f, 0.54f, 0.56f), rgb(0.36f, 0.37f, 0.4f),
            0.75f, 0.3f, 1.5f, 0.02f, 1, rgb(0.44f, 0.46f, 0.49f), 0, 0, 0, 0,
            0.008f, 0.35f, 0.96f, 0);

    private WeatherSky() {
    }

    static State sample(WeatherProfile profile, int style, float hour, float hazeStrength) {
        LightKey light = lightAt(hour);
        float sunAngle = (hour - 6) / 24 * MathHelper.TAU;
        float sine = MathHelper.sin(sunAngle);
        Vector3f solar = rgb(MathHelper.cos(sunAngle), sine * MathHelper.cos(0.55f), sine * MathHelper.sin(0.55f));
        boolean night = solar.y < 0;
        Vector3f sun = night ? new Vector3f(solar).negate() : new Vector3f(solar);
        float disk = WeatherProfile.smooth(sun.y / 0.06f);
        float focus = WeatherProfile.smooth((solar.y + 0.38f) / 0.32f);
        Vector3f sunColor = rgb(light.sunRed, light.sunGreen, light.sunBlue);
        Vector3f lightTint = rgb(light.red, light.green, light.blue);
        if (style != 0) {
            SkyKey sky = skyAt(switch (style) {
                case 2 -> STORM;
                case 3 -> STARS;
                default -> CLEAR;
            }, hour);
            return new State(sky.zenith, sky.horizon, sky.nadir, sun, sunColor, sky.sunSize * disk, sky.sunGlare * disk,
                    sky.cloudCover, sky.cloudDark, sky.cloudSpeed, sky.cloudScale, sky.cloudLit, sky.cloudShadow,
                    sky.stars, sky.band, sky.aurora, sky.meteors, Math.min(1.4f, sky.haze * hazeStrength),
                    sky.hazeColor, solar, focus, rgb(0.8f, 0.86f, 1.05f), sky.brightness, sky.flashRate, lightTint, light.reflection);
        }

        SkyKey sky = switch (profile) {
            case SNOW -> SNOW;
            case FOG -> FOG;
            default -> RAIN;
        };
        float nightWeight = 1 - light.level;
        Vector3f zenith = tint(sky.zenith, lightTint, light.level * light.zenith, 0.45f);
        Vector3f horizon = tint(sky.horizon, lightTint, light.level, 1).lerp(zenith, nightWeight * 0.72f);
        return new State(zenith, horizon, tint(sky.nadir, lightTint, light.level * light.zenith, 0.7f), sun, sunColor,
                (night ? Math.max(sky.sunSize, 0.018f) : sky.sunSize) * disk, sky.sunGlare * light.glare * disk,
                sky.cloudCover, sky.cloudDark, sky.cloudSpeed, sky.cloudScale, tint(sky.cloudLit, lightTint, light.level, 1),
                tint(sky.cloudShadow, lightTint, light.level, 1), Math.min(1.2f, sky.stars + light.stars),
                Math.min(1.2f, sky.band + light.band), sky.aurora * (0.2f + 0.8f * nightWeight),
                sky.meteors * (0.12f + 0.88f * nightWeight),
                Math.min(1.2f, sky.haze * light.haze * hazeStrength * (1 - nightWeight * 0.55f)),
                tint(sky.hazeColor, lightTint, light.level, 1), solar, focus, rgb(0.8f, 0.86f, 1.05f),
                sky.brightness * light.brightness, sky.flashRate, lightTint, light.reflection);
    }

    private static SkyKey skyAt(SkyKey[] keys, float hour) {
        int upper = 0;
        while (upper < keys.length && keys[upper].hour <= hour) upper++;
        SkyKey a = keys[(upper + keys.length - 1) % keys.length];
        SkyKey b = keys[upper % keys.length];
        float t = fraction(a.hour, b.hour, hour);
        return new SkyKey(hour, blend(a.zenith, b.zenith, t), blend(a.horizon, b.horizon, t), blend(a.nadir, b.nadir, t),
                blend(a.cloudLit, b.cloudLit, t), blend(a.cloudShadow, b.cloudShadow, t), lerp(t, a.cloudCover, b.cloudCover),
                lerp(t, a.cloudDark, b.cloudDark), lerp(t, a.cloudScale, b.cloudScale), lerp(t, a.cloudSpeed, b.cloudSpeed),
                lerp(t, a.haze, b.haze), blend(a.hazeColor, b.hazeColor, t), lerp(t, a.stars, b.stars), lerp(t, a.band, b.band),
                lerp(t, a.aurora, b.aurora), lerp(t, a.meteors, b.meteors), lerp(t, a.sunSize, b.sunSize),
                lerp(t, a.sunGlare, b.sunGlare), lerp(t, a.brightness, b.brightness), lerp(t, a.flashRate, b.flashRate));
    }

    private static LightKey lightAt(float hour) {
        int upper = 0;
        while (upper < LIGHT.length && LIGHT[upper].hour <= hour) upper++;
        LightKey a = LIGHT[(upper + LIGHT.length - 1) % LIGHT.length];
        LightKey b = LIGHT[upper % LIGHT.length];
        float t = fraction(a.hour, b.hour, hour);
        return new LightKey(hour, lerp(t, a.level, b.level), lerp(t, a.zenith, b.zenith), lerp(t, a.red, b.red),
                lerp(t, a.green, b.green), lerp(t, a.blue, b.blue), lerp(t, a.sunRed, b.sunRed),
                lerp(t, a.sunGreen, b.sunGreen), lerp(t, a.sunBlue, b.sunBlue), lerp(t, a.glare, b.glare),
                lerp(t, a.stars, b.stars), lerp(t, a.band, b.band), lerp(t, a.haze, b.haze),
                lerp(t, a.brightness, b.brightness), lerp(t, a.reflection, b.reflection));
    }

    private static float fraction(float from, float to, float hour) {
        float length = (to - from + 24) % 24;
        return WeatherProfile.smooth(((hour - from + 24) % 24) / length);
    }

    private static Vector3f tint(Vector3f base, Vector3f light, float factor, float strength) {
        return rgb(base.x * factor * lerp(strength, 1, light.x), base.y * factor * lerp(strength, 1, light.y),
                base.z * factor * lerp(strength, 1, light.z));
    }

    private static Vector3f rgb(float red, float green, float blue) {
        return new Vector3f(red, green, blue);
    }

    private static Vector3f blend(Vector3f from, Vector3f to, float factor) {
        return new Vector3f(from).lerp(to, factor);
    }

    private static float lerp(float factor, float from, float to) {
        return MathHelper.lerp(factor, from, to);
    }

    private record SkyKey(float hour, Vector3f zenith, Vector3f horizon, Vector3f nadir, Vector3f cloudLit, Vector3f cloudShadow,
                          float cloudCover, float cloudDark, float cloudScale, float cloudSpeed, float haze, Vector3f hazeColor,
                          float stars, float band, float aurora, float meteors, float sunSize, float sunGlare,
                          float brightness, float flashRate) {
    }

    private record LightKey(float hour, float level, float zenith, float red, float green, float blue,
                            float sunRed, float sunGreen, float sunBlue, float glare, float stars, float band, float haze,
                            float brightness, float reflection) {
    }

    record State(Vector3f zenith, Vector3f horizon, Vector3f nadir, Vector3f sun, Vector3f sunColor, float sunSize,
                 float sunGlare, float cloudCover, float cloudDark, float cloudSpeed, float cloudScale, Vector3f cloudLit,
                 Vector3f cloudShadow, float stars, float band, float aurora, float meteors, float haze, Vector3f hazeColor,
                 Vector3f glowDirection, float glowFocus, Vector3f flashColor, float brightness, float flashRate,
                 Vector3f lightTint, float reflections) {
        State mix(State next, float t) {
            return new State(blend(zenith, next.zenith, t), blend(horizon, next.horizon, t), blend(nadir, next.nadir, t),
                    blend(sun, next.sun, t), blend(sunColor, next.sunColor, t), lerp(t, sunSize, next.sunSize),
                    lerp(t, sunGlare, next.sunGlare), lerp(t, cloudCover, next.cloudCover), lerp(t, cloudDark, next.cloudDark),
                    lerp(t, cloudSpeed, next.cloudSpeed), lerp(t, cloudScale, next.cloudScale), blend(cloudLit, next.cloudLit, t),
                    blend(cloudShadow, next.cloudShadow, t), lerp(t, stars, next.stars), lerp(t, band, next.band),
                    lerp(t, aurora, next.aurora), lerp(t, meteors, next.meteors), lerp(t, haze, next.haze),
                    blend(hazeColor, next.hazeColor, t), blend(glowDirection, next.glowDirection, t), lerp(t, glowFocus, next.glowFocus),
                    blend(flashColor, next.flashColor, t), lerp(t, brightness, next.brightness), lerp(t, flashRate, next.flashRate),
                    blend(lightTint, next.lightTint, t), lerp(t, reflections, next.reflections));
        }

        Vector4f fogColor() {
            return new Vector4f(tone(horizon.x), tone(horizon.y), tone(horizon.z), 1);
        }

        private static float tone(float channel) {
            return 1 - (float) Math.exp(-Math.max(channel, 0) * 1.9f);
        }
    }
}
