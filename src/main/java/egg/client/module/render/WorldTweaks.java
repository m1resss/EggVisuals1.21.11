package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.world.WeatherProfile;
import egg.client.render.world.WorldWeatherParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

public final class WorldTweaks extends Module {
    private static final String WEATHER = "Погода";
    private static final String WORLD = "Мир";
    private static WorldTweaks instance;

    private final ModeSetting section = new ModeSetting("Раздел", WEATHER, "", WEATHER, WORLD);
    private final BooleanSetting weatherEnabled = new BooleanSetting("Кастомная погода", true, "");
    private final ModeSetting weather = new ModeSetting("Режим погоды", "Дождь", "", "Дождь", "Туман", "Снег");
    private final ModeSetting sky = new ModeSetting("Небо", WEATHER, "", WEATHER, "Ясное", "Штормовое", "Звёздное");
    private final SliderSetting weatherTime = new SliderSetting("Время погоды (ч)", 12, 0, 24, 0.25, "");
    private final ModeSetting water = new ModeSetting("Вода на земле", "Лужи", "", "Лужи", "Плёнка");
    private final SliderSetting drops = new SliderSetting("Плотность осадков (%)", 100, 10, 200, 5, "");
    private final SliderSetting wind = new SliderSetting("Ветер (%)", 100, 0, 250, 10, "");
    private final BooleanSetting storm = new BooleanSetting("Гроза", true, "");
    private final SliderSetting snowDepth = new SliderSetting("Глубина снега (%)", 100, 20, 250, 5, "");
    private final SliderSetting fogRange = new SliderSetting("Видимость в тумане", 40, 12, 160, 4, "");
    private final SliderSetting mist = new SliderSetting("Дымка у земли (%)", 100, 0, 200, 5, "");
    private final SliderSetting haze = new SliderSetting("Мгла у горизонта (%)", 100, 0, 200, 5, "");
    private final SliderSetting reflection = new SliderSetting("Блики осадков (%)", 100, 0, 200, 5, "");

    private final BooleanSetting light = new BooleanSetting("Яркость мира", true, "");
    private final SliderSetting brightness = new SliderSetting("Уровень яркости", 1, 0, 1, 0.05, "");
    private final BooleanSetting grade = new BooleanSetting("Насыщенность мира", true, "");
    private final SliderSetting saturation = new SliderSetting("Уровень насыщенности", 1, 0, 3, 0.05, "");
    private final BooleanSetting fixedTime = new BooleanSetting("Кастомное время", false, "");
    private final SliderSetting worldTime = new SliderSetting("Время мира (ч)", 12, 0, 24, 1, "");
    private final BooleanSetting customFog = new BooleanSetting("Ручной туман", false, "");
    private final ColorSetting fogColor = new ColorSetting("Оттенок тумана", 0xFF8E96FF, "");
    private final SliderSetting fogDistance = new SliderSetting("Дистанция тумана", 80, 10, 255, 5, "");

    private final WorldWeatherParticles particles = new WorldWeatherParticles();
    private ClientWorld lastWorld;
    private volatile boolean frozenFoliage;
    private boolean foliageAtReload;
    private boolean foliageRefreshQueued;
    private WeatherProfile lastProfile;
    private boolean lastWeatherActive;
    private Frame from = Frame.CLEAR;
    private Frame target = Frame.CLEAR;
    private long transitionStart;
    private long wetnessTick;
    private float wetness;
    private final long started = System.currentTimeMillis();
    private long nextFlash;
    private long flashTime;

    public WorldTweaks() {
        super("World Tweaks", "Погода, небо и цветокоррекция мира", Category.MISC);
        instance = this;
        addSettings(section, weatherEnabled.setVisible(() -> section.is(WEATHER)), weather.setVisible(this::weatherPage),
                sky.setVisible(this::weatherPage), weatherTime.setVisible(this::weatherPage),
                water.setVisible(() -> weatherPage() && weather.is("Дождь")),
                drops.setVisible(() -> weatherPage() && !weather.is("Туман")),
                wind.setVisible(() -> weatherPage() && !weather.is("Туман")),
                storm.setVisible(() -> weatherPage() && weather.is("Дождь")),
                snowDepth.setVisible(() -> weatherPage() && weather.is("Снег")),
                fogRange.setVisible(this::fogPage), mist.setVisible(this::fogPage), haze.setVisible(this::fogPage),
                reflection.setVisible(this::weatherPage), light.setVisible(this::worldPage),
                brightness.setVisible(() -> worldPage() && light.get()), grade.setVisible(this::worldPage),
                saturation.setVisible(() -> worldPage() && grade.get()), fixedTime.setVisible(this::worldPage),
                worldTime.setVisible(() -> worldPage() && fixedTime.get()), customFog.setVisible(this::worldPage),
                fogColor.setVisible(() -> worldPage() && customFog.get()),
                fogDistance.setVisible(() -> worldPage() && customFog.get()));
    }

    public static WorldTweaks getInstance() { return instance; }
    private boolean weatherPage() { return section.is(WEATHER) && weatherEnabled.get(); }
    private boolean fogPage() { return weatherPage() && weather.is("Туман"); }
    private boolean worldPage() { return section.is(WORLD); }

    public WeatherProfile profile() {
        return switch (weather.getIndex()) {
            case 1 -> WeatherProfile.FOG;
            case 2 -> WeatherProfile.SNOW;
            default -> water.is("Плёнка") ? WeatherProfile.FILM : WeatherProfile.RAIN;
        };
    }

    private boolean hasWeather() {
        return isEnabled() && weatherEnabled.get() && mc.world != null && mc.world.canHaveWeather();
    }

    public boolean hasWeatherEffects() {
        return mc.world != null && mc.world.canHaveWeather()
                && MathHelper.lerp(progress(System.currentTimeMillis()), from.amount(), target.amount()) > 0.002f;
    }

    public boolean frozenFoliage() { return frozenFoliage; }
    public int skyStyle() { return sky.getIndex(); }
    public float skyHour() { return (float) weatherTime.get() % 24; }
    public float clock() { return (System.currentTimeMillis() - started) / 1000f; }
    public float snowDepth() { return percent(snowDepth); }
    public float wetness() { return wetness; }
    public float gamma() { return isEnabled() && light.get() ? (float) brightness.get() * 10 : 0; }
    public float brightness() { return isEnabled() && light.get() ? 0.7f + (float) brightness.get() * 0.55f : 1; }
    public float saturation() { return isEnabled() && grade.get() ? (float) saturation.get() : 1; }

    public long transformTime(long vanillaTime) {
        if (mc.world == null) return vanillaTime;
        boolean worldClock = isEnabled() && fixedTime.get();
        if (!worldClock && frame().amount() < 0.15f) return vanillaTime;
        float hour = worldClock ? (float) worldTime.get() : skyHour();
        return Math.floorDiv(vanillaTime, 24_000) * 24_000 + Math.floorMod(Math.round((hour - 6) * 1000), 24_000);
    }

    @Override
    public void onEnable() {
        tick();
    }

    @Override
    public void onDisable() {
        tick();
    }

    public void tick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            lastProfile = null;
            lastWeatherActive = false;
            if (frozenFoliage) {
                frozenFoliage = false;
                scheduleFoliageRefresh();
            }
            from = target = Frame.CLEAR;
            wetness = 0;
            wetnessTick = nextFlash = flashTime = 0;
            particles.reset();
        }

        long now = System.currentTimeMillis();
        WeatherProfile profile = profile();
        boolean active = hasWeather();
        if (active != lastWeatherActive || active && profile != lastProfile) {
            from = frame(now);
            transitionStart = now;
            lastProfile = profile;
            lastWeatherActive = active;
        }
        target = active ? makeFrame(profile) : Frame.CLEAR;

        Frame frame = frame(now);
        float elapsed = wetnessTick == 0 ? 0 : Math.min(now - wetnessTick, 250);
        wetnessTick = now;
        boolean accumulating = frame.wetness() > wetness;
        float fade = elapsed / (accumulating ? 4500f : 14_000f);
        wetness = frame.wetness() + (wetness - frame.wetness()) * (float) Math.exp(-fade * 3);
        if (wetness < 0.001f && !active) wetness = 0;
        boolean frost = frame.frost() > 0.5f;
        if (frost != frozenFoliage) {
            frozenFoliage = frost;
            rebuildColors();
            scheduleFoliageRefresh();
        }
        if (mc.world != null && mc.player != null) {
            if (active || frame.amount() > 0) updateLightning(now, frame.thunder());
            particles.tickRainAudio(mc, active ? frame.rain() : 0, now);
        }
    }

    private Frame makeFrame(WeatherProfile profile) {
        float airflow = percent(wind);
        return new Frame(1, profile.rain * percent(drops), profile.snow * percent(drops),
                profile.rain > 0 && storm.get() ? 1 : 0,
                profile.rain > 0 ? 0.2f * airflow : 0.1f * airflow,
                profile.rain > 0 ? 0.07f * airflow : 0.05f * airflow,
                profile == WeatherProfile.FOG ? (float) fogRange.get() : profile.fogDistance,
                profile == WeatherProfile.SNOW ? 1 : 0, profile.wetness, profile.darkening, WeatherProfile.color(profile.wetColor),
                Math.min(1.4f, profile.reflection * percent(reflection)), profile.glint * percent(reflection), profile.puddles,
                profile.mist * (profile == WeatherProfile.FOG ? percent(mist) : 1),
                WeatherProfile.color(profile.mistColor), profile == WeatherProfile.FOG ? percent(haze) : 1,
                profile.brightness, profile.saturation, WeatherProfile.color(profile.precipitationColor), profile.splash);
    }

    public Frame frame() { return frame(System.currentTimeMillis()); }

    private Frame frame(long now) {
        float progress = progress(now);
        return progress >= 1 ? target : from.mix(target, progress);
    }

    private float progress(long now) {
        return WeatherProfile.smooth((now - transitionStart) / 900f);
    }

    private void updateLightning(long now, float thunder) {
        if (thunder < 0.05f) {
            nextFlash = 0;
            return;
        }
        if (nextFlash == 0) nextFlash = now + 3000;
        if (now >= nextFlash) {
            flashTime = now;
            nextFlash = now + 4500 + (long) (WeatherProfile.noise(now, 0) * 6000);
            particles.thunder(mc, thunder);
        }
    }

    public float flash() {
        float elapsed = (System.currentTimeMillis() - flashTime) / 1000f;
        if (elapsed > 1) return 0;
        return frame().thunder() * Math.min(1, flashPulse(elapsed, 0.09f) + flashPulse(elapsed - 0.13f, 0.17f) * 0.65f);
    }

    private static float flashPulse(float phase, float decay) {
        return phase < 0 ? 0 : (float) (Math.exp(-phase / decay) * (1 - Math.exp(-phase / 0.012)));
    }

    private void scheduleFoliageRefresh() {
        if (foliageRefreshQueued) return;
        foliageRefreshQueued = true;
        foliageAtReload = frozenFoliage;
        mc.reloadResourcesConcurrently().whenComplete((unused, error) -> mc.execute(() -> {
            foliageRefreshQueued = false;
            if (frozenFoliage != foliageAtReload) scheduleFoliageRefresh();
        }));
    }

    private void rebuildColors() {
        mc.world.reloadColor();
        Vec3d position = mc.gameRenderer.getCamera().getCameraPos();
        int x = ChunkSectionPos.getSectionCoord(position.x);
        int z = ChunkSectionPos.getSectionCoord(position.z);
        int range = mc.options.getClampedViewDistance() + 1;
        mc.worldRenderer.scheduleChunkRenders(x - range, mc.world.getBottomSectionCoord(), z - range,
                x + range, mc.world.getTopSectionCoord(), z + range);
    }

    public Vector4f fogColor(Vector4f vanilla, Vector4f weatherColor, Frame frame) {
        if (isEnabled() && customFog.get()) return WeatherProfile.color(fogColor.get());
        if (frame.amount() <= 0.002f) return vanilla;
        Vector4f result = new Vector4f(vanilla).lerp(weatherColor, frame.amount());
        result.w = vanilla.w * (1 - 0.1f * frame.amount());
        return result;
    }

    public void applyFog(FogData fog, Frame frame) {
        boolean override = isEnabled() && customFog.get();
        float amount = override ? 1 : frame.amount();
        if (amount <= 0.002f) return;
        float distance = override ? (float) fogDistance.get() : frame.fogDistance();
        fog.environmentalEnd = MathHelper.lerp(amount, fog.environmentalEnd, distance);
        fog.environmentalStart = Math.min(fog.environmentalStart, fog.environmentalEnd * 0.85f);
        fog.skyEnd = Math.min(fog.skyEnd, fog.environmentalEnd);
        fog.cloudEnd = Math.min(fog.cloudEnd, fog.environmentalEnd);
    }

    public void renderWeather(VertexConsumerProvider buffers, Camera camera) {
        if (hasWeatherEffects()) particles.render(mc, this, buffers, camera);
    }

    private static float percent(SliderSetting setting) { return (float) setting.get() / 100; }

    public record Frame(float amount, float rain, float snow, float thunder, float windX, float windZ,
                        float fogDistance, float frost, float wetness, float darkening, Vector4f wetTint,
                        float reflection, float glint, float puddles, float mist, Vector4f mistTint, float haze,
                        float brightness, float saturation, Vector4f precipitationTint, float splash) {
        private static final Frame CLEAR = new Frame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new Vector4f(), 0, 0, 0, 0,
                new Vector4f(), 1, 1, 1, new Vector4f(), 0);

        public float rainGradient() { return Math.min(1, Math.max(rain, snow)); }

        private Frame mix(Frame next, float progress) {
            Frame values = amount == 0 ? next : this;
            Frame end = next.amount == 0 ? this : next;
            return new Frame(MathHelper.lerp(progress, amount, next.amount), MathHelper.lerp(progress, rain, next.rain),
                    MathHelper.lerp(progress, snow, next.snow), MathHelper.lerp(progress, thunder, next.thunder),
                    MathHelper.lerp(progress, windX, next.windX), MathHelper.lerp(progress, windZ, next.windZ),
                    MathHelper.lerp(progress, values.fogDistance, end.fogDistance), MathHelper.lerp(progress, frost, next.frost),
                    MathHelper.lerp(progress, wetness, next.wetness), MathHelper.lerp(progress, values.darkening, end.darkening),
                    new Vector4f(values.wetTint).lerp(end.wetTint, progress), MathHelper.lerp(progress, reflection, next.reflection),
                    MathHelper.lerp(progress, glint, next.glint), MathHelper.lerp(progress, puddles, next.puddles),
                    MathHelper.lerp(progress, mist, next.mist), new Vector4f(values.mistTint).lerp(end.mistTint, progress),
                    MathHelper.lerp(progress, haze, next.haze), MathHelper.lerp(progress, brightness, next.brightness),
                    MathHelper.lerp(progress, saturation, next.saturation),
                    new Vector4f(values.precipitationTint).lerp(end.precipitationTint, progress),
                    MathHelper.lerp(progress, splash, next.splash));
        }
    }
}
