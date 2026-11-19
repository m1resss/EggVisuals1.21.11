package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.util.AudioPlayer;
import egg.client.util.ColorUtil;
import egg.client.render.world.AmbienceLightningRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldTweaks extends Module {
    private static WorldTweaks instance;

    private static final Identifier[] SAKURA_TEXTURES = new Identifier[]{
            Identifier.of("egg", "textures/world/sakura_1.png"),
            Identifier.of("egg", "textures/world/sakura_2.png"),
            Identifier.of("egg", "textures/world/sakura_3.png"),
            Identifier.of("egg", "textures/world/sakura_4.png"),
            Identifier.of("egg", "textures/world/sakura_5.png")
    };

    private static final Identifier[] LEAF_TEXTURES = new Identifier[]{
            Identifier.of("egg", "textures/world/leaf_1.png"),
            Identifier.of("egg", "textures/world/leaf_2.png"),
            Identifier.of("egg", "textures/world/leaf_3.png"),
            Identifier.of("egg", "textures/world/leaf_4.png")
    };

    private static final Identifier RAIN_DROP_TEXTURE = Identifier.of("egg", "textures/world/rain_drop.png");

    private final ModeSetting timeMode = new ModeSetting("Режим времени", "Выкл", "Режим времени", "День", "Ночь", "Закат", "Рассвет", "Кастом", "Выкл");
    private final SliderSetting timeOfDay = new SliderSetting("Время суток", 6000.0, 0.0, 24000.0, 100.0, "Время суток в тиках (0..24000)");

    private final ModeSetting season = new ModeSetting("Сезон мира", "Выкл", "Сезон мира", "Весна", "Лето", "Осень", "Выкл");
    private final ModeSetting weather = new ModeSetting("Погода", "Выкл", "Режим погоды", "Ясно", "Дождь", "Гроза", "Выкл");

    private final BooleanSetting particles = new BooleanSetting("Частицы", true, "3D частицы листьев и лепестков вокруг игрока");
    private final SliderSetting particleDensity = new SliderSetting("Кол-во частиц", 80.0, 20.0, 180.0, 10.0, "Плотность частиц");
    private final SliderSetting particleSpeed = new SliderSetting("Скорость частиц", 1.5, 0.5, 3.5, 0.1, "Скорость падения частиц листьев и лепестков");

    private final BooleanSetting autumnLeavesGround = new BooleanSetting("Опавшие листья", true, "3D ковер из опавших листьев на земле осенью");
    private final SliderSetting groundLeavesDensity = new SliderSetting("Плотность листьев", 75.0, 20.0, 100.0, 5.0, "Плотность ковра листьев на земле (%)");

    private final BooleanSetting customFog = new BooleanSetting("Кастомный туман", false, "Атмосферный цвет тумана горизонта");
    private final ModeSetting fogColorMode = new ModeSetting("Цвет тумана", "По сезону", "Режим цвета тумана", "По сезону", "Кастом", "Ванилла");
    private final ColorSetting fogCustomColor = new ColorSetting("Цвет", 0xFF9FC5E8, "Цвет кастомного тумана");
    private final BooleanSetting customFogDistance = new BooleanSetting("Дальность тумана", false, "Ручная настройка дистанции и плотности тумана");
    private final SliderSetting fogDistanceMultiplier = new SliderSetting("Множитель дальности", 1.0, 0.1, 5.0, 0.05, "Множитель дистанции тумана (1.0 = ванилла, больше = дальше туман)");
    private final SliderSetting fogStartRatio = new SliderSetting("Начало тумана", 0.75, 0.0, 1.0, 0.05, "Точка старта тумана (0.0 = прямо перед игроком, 1.0 = у края горизонта)");
    private final BooleanSetting fogTransparency = new BooleanSetting("Прозрачность тумана", false, "Включает прозрачность тумана горизонта");
    private final SliderSetting fogOpacity = new SliderSetting("Непрозрачность тумана", 0.5, 0.0, 1.0, 0.05, "Степень видимости тумана (0 = полностью прозрачный, 1 = стандартный плотный)");

    private final BooleanSetting reflections = new BooleanSetting("Отражения", false, "SSR отражения в реальном времени на воде и блоках");
    private final SliderSetting timeHour = new SliderSetting("Время (ч)", 12.0, 0.0, 24.0, 0.1, "Время суток в часах");
    private final ModeSetting shaderQuality = new ModeSetting("Качество шейдеров", "x2", "Качество эффектов", "x1", "x2", "x3", "x4");
    private final BooleanSetting bloom = new BooleanSetting("Блум", false, "Свечение ярких элементов и неба");
    private final SliderSetting bloomIntensity = new SliderSetting("Сила блума", 0.8, 0.1, 2.5, 0.05, "Интенсивность свечения");
    private final SliderSetting bloomThreshold = new SliderSetting("Порог блума", 0.8, 0.2, 1.5, 0.05, "Порог яркости для свечения");
    private final BooleanSetting colorGrade = new BooleanSetting("Цветокоррекция", false, "Пост-обработка цвета мира");
    private final SliderSetting colorGradeSaturation = new SliderSetting("Насыщенность", 100.0, 0.0, 200.0, 5.0, "Насыщенность цвета (%)");
    private final SliderSetting colorGradeContrast = new SliderSetting("Контраст", 100.0, 50.0, 150.0, 5.0, "Контраст изображения (%)");
    private final SliderSetting colorGradeBrightness = new SliderSetting("Яркость", 100.0, 50.0, 150.0, 5.0, "Яркость мира (%)");
    private final SliderSetting colorGradeTemperature = new SliderSetting("Температура", 0.0, -100.0, 100.0, 5.0, "Цветовая температура");

    private final BooleanSetting customSound = new BooleanSetting("Звуки дождя/грозы", true, "Кастомный шум дождя и раскаты грома");
    private final SliderSetting soundVolume = new SliderSetting("Громкость звука", 0.65, 0.0, 1.0, 0.05, "Громкость MP3 звуков");

    private final BooleanSetting customClouds = new BooleanSetting("Облака", true, "Кастомные облака");
    private final ModeSetting cloudMode = new ModeSetting("Режим облаков", "Шейдерные", "Режим отображения облаков", "Шейдерные", "Ванильные", "Цветные", "Выкл");
    private final SliderSetting cloudCoverage = new SliderSetting("Плотность облаков", 55.0, 5.0, 95.0, 5.0, "Покрытие неба облаками (%)");
    private final SliderSetting cloudDistance = new SliderSetting("Дальность облаков", 2048.0, 512.0, 4096.0, 128.0, "Дистанция рендера облаков");
    private final SliderSetting cloudHeight = new SliderSetting("Высота облаков", 192.0, 64.0, 384.0, 8.0, "Высота облачного слоя");
    private final SliderSetting cloudSpeed = new SliderSetting("Скорость облаков", 1.0, 0.0, 5.0, 0.1, "Скорость движения облаков");
    private final ColorSetting cloudColor = new ColorSetting("Цвет облаков", 0xFFFFFFFF, "Цвет кастомных облаков");

    private final BooleanSetting customThunder = new BooleanSetting("Эффекты грозы", true, "Атмосферные 3D молнии в небе во время грозы");
    private final SliderSetting lightningFrequency = new SliderSetting("Частота молний", 50.0, 5.0, 100.0, 5.0, "Частота появления атмосферных молний (%)");

    private final List<FallingParticle> fallingParticles = new ArrayList<>();
    private final List<RainStreak> rainStreaks = new ArrayList<>();
    private final List<GroundLeaf> cachedGroundLeaves = new ArrayList<>();
    private final Random random = new Random();

    private int lastLeafCamX = Integer.MIN_VALUE;
    private int lastLeafCamY = Integer.MIN_VALUE;
    private int lastLeafCamZ = Integer.MIN_VALUE;
    private double lastGroundLeavesDensity = -1.0;
    private boolean groundLeavesDirty = true;

    private int thunderCooldown = 120;
    private long lastFrameNanos = 0L;
    private boolean wasAudioRaining = false;
    private String lastAppliedSeason = "";

    public WorldTweaks() {
        super("World Tweaks", "Настройка погоды, времени и сезонов", Category.MISC);
        this.fogDistanceMultiplier.setVisible(() -> this.customFogDistance.isEnabled());
        this.fogStartRatio.setVisible(() -> this.customFogDistance.isEnabled());
        this.fogOpacity.setVisible(() -> this.fogTransparency.isEnabled());
        addSettings(
                this.timeMode, this.timeOfDay, this.timeHour,
                this.season, this.weather,
                this.particles, this.particleDensity, this.particleSpeed,
                this.autumnLeavesGround, this.groundLeavesDensity,
                this.shaderQuality, this.reflections,
                this.bloom, this.bloomIntensity, this.bloomThreshold,
                this.colorGrade, this.colorGradeSaturation, this.colorGradeContrast, this.colorGradeBrightness, this.colorGradeTemperature,
                this.customFog, this.fogColorMode, this.fogCustomColor,
                this.customFogDistance, this.fogDistanceMultiplier, this.fogStartRatio,
                this.fogTransparency, this.fogOpacity,
                this.customSound, this.soundVolume,
                this.customClouds, this.cloudMode, this.cloudCoverage, this.cloudDistance, this.cloudHeight, this.cloudSpeed, this.cloudColor,
                this.customThunder, this.lightningFrequency
        );
        instance = this;
    }

    public static WorldTweaks getInstance() {
        return instance;
    }

    public BooleanSetting getReflections() {
        return this.reflections;
    }

    public boolean isReflectionsEnabled() {
        return isEnabled() && this.reflections.get();
    }

    public boolean isBloomEnabled() {
        return isEnabled() && this.bloom.get();
    }

    public boolean isColorGradeEnabled() {
        return isEnabled() && this.colorGrade.get();
    }

    public double getBloomIntensity() {
        return this.bloomIntensity.get();
    }

    public double getBloomThreshold() {
        return this.bloomThreshold.get();
    }

    public double getColorGradeSaturation() {
        return this.colorGradeSaturation.get() / 100.0;
    }

    public double getColorGradeContrast() {
        return this.colorGradeContrast.get() / 100.0;
    }

    public double getColorGradeBrightness() {
        return this.colorGradeBrightness.get() / 100.0;
    }

    public double getColorGradeTemperature() {
        return this.colorGradeTemperature.get() / 100.0;
    }

    public int getShaderQualityScale() {
        String mode = this.shaderQuality.get();
        if ("x1".equals(mode)) return 1;
        if ("x3".equals(mode)) return 3;
        if ("x4".equals(mode)) return 4;
        return 2;
    }

    public boolean isCustomFogColorEnabled() {
        return isEnabled() && this.customFog.get() && !"Ванилла".equals(this.fogColorMode.get());
    }

    public boolean isCustomFogDistanceEnabled() {
        return isEnabled() && this.customFogDistance.get();
    }

    public boolean isFogTransparencyEnabled() {
        return isEnabled() && this.fogTransparency.get();
    }

    public float getFogAlpha() {
        return (float) this.fogOpacity.get();
    }

    public void applyFogDistance(net.minecraft.client.render.fog.FogData fogData, float baseViewBlocks) {
        if (!isCustomFogDistanceEnabled()) return;
        float mult = (float) this.fogDistanceMultiplier.get();
        float end = Math.max(8.0f, baseViewBlocks * mult);
        float start = end * MathHelper.clamp((float) this.fogStartRatio.get(), 0.0f, 0.99f);
        fogData.renderDistanceEnd = end;
        fogData.renderDistanceStart = start;
        fogData.environmentalEnd = Math.max(fogData.environmentalEnd, end);
        fogData.environmentalStart = Math.min(fogData.environmentalStart, start);
    }

    public Vector4f getCustomFogColor(Vector4f vanilla) {
        if (!isEnabled()) return vanilla;

        float alpha = vanilla.w;
        if (isFogTransparencyEnabled()) {
            alpha = (float) this.fogOpacity.get();
        }

        String mode = this.fogColorMode.get();
        if ("Кастом".equals(mode)) {
            int c = this.fogCustomColor.get();
            return new Vector4f(ColorUtil.red(c) / 255.0f, ColorUtil.green(c) / 255.0f, ColorUtil.blue(c) / 255.0f, alpha);
        }

        if ("Гроза".equals(this.weather.get())) {
            return new Vector4f(0.28f, 0.34f, 0.45f, alpha);
        }
        if ("Дождь".equals(this.weather.get())) {
            return new Vector4f(0.48f, 0.55f, 0.65f, alpha);
        }

        String s = this.season.get();
        if ("Весна".equals(s)) {
            return new Vector4f(0.96f, 0.86f, 0.90f, alpha);
        } else if ("Лето".equals(s)) {
            return new Vector4f(0.96f, 0.93f, 0.80f, alpha);
        } else if ("Осень".equals(s)) {
            return new Vector4f(0.92f, 0.78f, 0.62f, alpha);
        }
        return new Vector4f(vanilla.x, vanilla.y, vanilla.z, alpha);
    }

    public void onEnable() {
        if (isCustomSeason()) {
            refreshWorldChunks();
        }
        syncAudioState();
        this.lastFrameNanos = System.nanoTime();
    }

    @Override
    public void onDisable() {
        AudioPlayer.stopAll();
        this.wasAudioRaining = false;
        this.fallingParticles.clear();
        this.rainStreaks.clear();
        this.cachedGroundLeaves.clear();
        this.groundLeavesDirty = true;
        if (!"Выкл".equals(this.lastAppliedSeason)) {
            refreshWorldChunks();
        }
        egg.client.render.shader.AmbienceRenderer.INSTANCE.reset();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isEnabled() || mc.world == null || mc.player == null) {
            if (this.wasAudioRaining) {
                AudioPlayer.stopAll();
                this.wasAudioRaining = false;
            }
            return;
        }

        if ("Гроза".equals(this.weather.get())) {
            this.thunderCooldown--;
            if (this.thunderCooldown <= 0) {
                this.thunderCooldown = 160 + this.random.nextInt(180);
                if (this.customSound.get()) {
                    AudioPlayer.playThunder((float) this.soundVolume.get());
                }
            }
        }
        if (isThundering() && isThunderLightningEnabled()) {
            AmbienceLightningRenderer.INSTANCE.tick(mc.world, getLightningFrequency());
        } else {
            AmbienceLightningRenderer.INSTANCE.tick(mc.world, 0.0F);
        }

        syncAudioState();
    }

    public void syncAudioState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isEnabled() || mc.world == null || mc.player == null) {
            if (this.wasAudioRaining) {
                AudioPlayer.stopAll();
                this.wasAudioRaining = false;
            }
            return;
        }

        boolean rainActive = isRaining();
        if (rainActive && this.customSound.get()) {
            if (!this.wasAudioRaining) {
                AudioPlayer.startRain((float) this.soundVolume.get());
                this.wasAudioRaining = true;
            } else {
                AudioPlayer.setRainVolume((float) this.soundVolume.get());
            }
        } else {
            if (this.wasAudioRaining) {
                AudioPlayer.stopRain();
                this.wasAudioRaining = false;
            }
        }
    }

    private int getSurfaceY(MinecraftClient mc, double x, double z) {
        if (mc.world == null) return -64;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return -64;
        try {
            int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
            BlockPos.Mutable mut = new BlockPos.Mutable();
            for (int y = topY; y >= mc.world.getBottomY(); y--) {
                mut.set(bx, y, bz);
                BlockState s = mc.world.getBlockState(mut);
                if (isBarrierOrIgnored(s)) continue;
                return y + 1;
            }
            return topY;
        } catch (Throwable ignored) {
            return -64;
        }
    }

    private void updateParticlesSmooth(Vec3d center, float dt, MinecraftClient mc) {
        int targetCount = (int) this.particleDensity.get();
        String curSeason = this.season.get();

        while (this.fallingParticles.size() < targetCount) {
            this.fallingParticles.add(createParticle(center, true, curSeason));
        }
        while (this.fallingParticles.size() > targetCount) {
            this.fallingParticles.remove(this.fallingParticles.size() - 1);
        }

        float speedFactor = dt * 20.0f;

        for (int i = 0; i < this.fallingParticles.size(); i++) {
            FallingParticle p = this.fallingParticles.get(i);
            p.age += speedFactor;
            p.roll += p.rollSpeed * speedFactor;
            p.pitch += p.pitchSpeed * speedFactor;

            float sway = (float) Math.sin((p.age * 0.04) + p.swayPhase);
            p.x += (p.vx + sway * p.swayRadius) * speedFactor;
            p.y += p.vy * speedFactor;
            p.z += (p.vz + (float) Math.cos((p.age * 0.04) + p.swayPhase) * (p.swayRadius * 0.5f)) * speedFactor;

            double dx = p.x - center.x;
            double dz = p.z - center.z;
            double distSq = dx * dx + dz * dz;

            boolean shouldReset = p.age >= p.maxAge || distSq > 24.0 * 24.0 || p.y < center.y - 5.5;

            if (!shouldReset && p.y <= center.y + 0.8) {
                int bx = MathHelper.floor(p.x);
                int by = MathHelper.floor(p.y);
                int bz = MathHelper.floor(p.z);
                if (mc.world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
                        shouldReset = true;
                    }
                }
            }

            if (shouldReset) {
                resetParticle(p, center, false, curSeason);
            }
        }
    }

    private FallingParticle createParticle(Vec3d center, boolean randomizeHeight, String curSeason) {
        FallingParticle p = new FallingParticle();
        resetParticle(p, center, randomizeHeight, curSeason);
        return p;
    }

    private void resetParticle(FallingParticle p, Vec3d center, boolean randomizeHeight, String curSeason) {
        double r = 1.5 + Math.sqrt(this.random.nextDouble()) * 17.0;
        double a = this.random.nextDouble() * Math.PI * 2.0;
        p.x = center.x + Math.cos(a) * r;
        p.z = center.z + Math.sin(a) * r;
        p.y = randomizeHeight
                ? (center.y - 2.0 + this.random.nextDouble() * 14.0)
                : (center.y + 6.0 + this.random.nextDouble() * 6.0);

        p.age = 0.0f;
        p.maxAge = 240.0f + this.random.nextFloat() * 160.0f;
        p.swayPhase = this.random.nextFloat() * 6.28f;
        p.roll = this.random.nextFloat() * 360.0f;
        p.pitch = this.random.nextFloat() * 360.0f;

        if ("Весна".equals(curSeason)) {
            p.texIdx = this.random.nextInt(SAKURA_TEXTURES.length);
            float speedMult = (float) this.particleSpeed.get();
            p.vy = (-0.022f - this.random.nextFloat() * 0.020f) * speedMult;
            p.vx = 0.014f + this.random.nextFloat() * 0.016f;
            p.vz = 0.006f + this.random.nextFloat() * 0.012f;
            p.swayRadius = 0.024f + this.random.nextFloat() * 0.024f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 2.5f;
            p.pitchSpeed = 1.4f + this.random.nextFloat() * 2.0f;
            p.scale = 0.26f + this.random.nextFloat() * 0.16f;
        } else if ("Осень".equals(curSeason)) {
            p.texIdx = this.random.nextInt(LEAF_TEXTURES.length);
            float speedMult = (float) this.particleSpeed.get();
            p.vy = (-0.028f - this.random.nextFloat() * 0.024f) * speedMult;
            p.vx = 0.018f + this.random.nextFloat() * 0.020f;
            p.vz = 0.008f + this.random.nextFloat() * 0.016f;
            p.swayRadius = 0.030f + this.random.nextFloat() * 0.028f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 3.2f;
            p.pitchSpeed = 1.8f + this.random.nextFloat() * 2.4f;
            p.scale = 0.28f + this.random.nextFloat() * 0.18f;
        } else {
            p.texIdx = 0;
            p.vy = -0.03f;
            p.vx = 0.0f;
            p.vz = 0.0f;
            p.swayRadius = 0.0f;
            p.rollSpeed = 0.0f;
            p.pitchSpeed = 0.0f;
            p.scale = 0.2f;
        }
    }

    private void updateRainSmooth(Vec3d center, float dt, MinecraftClient mc) {
        int targetCount = isThundering() ? 400 : 250;
        while (this.rainStreaks.size() < targetCount) {
            RainStreak s = new RainStreak();
            resetRainStreak(s, center, true, mc);
            this.rainStreaks.add(s);
        }
        while (this.rainStreaks.size() > targetCount) {
            this.rainStreaks.remove(this.rainStreaks.size() - 1);
        }

        float speedFactor = dt * 20.0f;

        for (int i = 0; i < this.rainStreaks.size(); i++) {
            RainStreak s = this.rainStreaks.get(i);
            s.x += s.vx * speedFactor;
            s.y += s.vy * speedFactor;
            s.z += s.vz * speedFactor;

            double dx = s.x - center.x;
            double dz = s.z - center.z;
            double distSq = dx * dx + dz * dz;

            if (distSq > 24.0 * 24.0 || s.y < s.groundY || s.y < center.y - 12.0) {
                resetRainStreak(s, center, false, mc);
            }
        }
    }

    private void resetRainStreak(RainStreak s, Vec3d center, boolean randomHeight, MinecraftClient mc) {
        double r = 1.0 + Math.sqrt(this.random.nextDouble()) * 18.0;
        double a = this.random.nextDouble() * Math.PI * 2.0;
        s.x = center.x + Math.cos(a) * r;
        s.z = center.z + Math.sin(a) * r;
        s.y = randomHeight
                ? (center.y - 4.0 + this.random.nextDouble() * 20.0)
                : (center.y + 12.0 + this.random.nextDouble() * 6.0);

        s.vx = -0.10 + (this.random.nextDouble() - 0.5) * 0.04;
        s.vz = 0.06 + (this.random.nextDouble() - 0.5) * 0.04;
        s.vy = -1.55 - this.random.nextDouble() * 0.45;

        s.groundY = getSurfaceY(mc, s.x, s.z);
    }

    public boolean renderWeatherPass(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return false;

        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
        }
        float dt = (now - this.lastFrameNanos) / 1_000_000_000.0f;
        this.lastFrameNanos = now;
        if (dt < 0.0f || dt > 0.1f) dt = 0.05f;

        boolean seasonPart = isCustomSeason() && this.particles.get();
        boolean rainPart = isRaining();

        if (seasonPart) {
            updateParticlesSmooth(camPos, dt, mc);
            renderParticles3D(consumers, camPos, mc);
        } else {
            this.fallingParticles.clear();
        }

        if (rainPart) {
            updateRainSmooth(camPos, dt, mc);
            renderCinemaRain3D(consumers, camPos, mc);
        } else {
            this.rainStreaks.clear();
        }

        return seasonPart || rainPart;
    }

    private void renderParticles3D(VertexConsumerProvider consumers, Vec3d camPos, MinecraftClient mc) {
        String curSeason = this.season.get();
        Identifier[] texArray;
        if ("Весна".equals(curSeason)) {
            texArray = SAKURA_TEXTURES;
        } else if ("Осень".equals(curSeason)) {
            texArray = LEAF_TEXTURES;
        } else {
            texArray = null;
        }

        if (texArray == null || texArray.length == 0 || this.fallingParticles.isEmpty()) return;

        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        Quaternionf camRot = camera != null ? camera.getRotation() : new Quaternionf();

        for (int t = 0; t < texArray.length; t++) {
            Identifier tex = texArray[t];
            VertexConsumer buffer = null;

            for (int i = 0; i < this.fallingParticles.size(); i++) {
                FallingParticle p = this.fallingParticles.get(i);
                if ((p.texIdx % texArray.length) != t) continue;

                double dx = p.x - camPos.x;
                double dy = p.y - camPos.y;
                double dz = p.z - camPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < 0.6 || distSq > 32.0 * 32.0) continue;

                double dist = Math.sqrt(distSq);
                float fade = (float) MathHelper.clamp((dist - 0.8) / 1.5, 0.0, 1.0);
                if (fade <= 0.05f) continue;

                if (buffer == null) {
                    buffer = consumers.getBuffer(RenderLayers.weather(tex, true));
                }

                Quaternionf rot = new Quaternionf(camRot).rotateZ((float) Math.toRadians(p.roll));
                float size = p.scale * 0.5f;
                Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(rot).mul(size);
                Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(rot).mul(size);

                float x0 = (float) dx - right.x - up.x;
                float y0 = (float) dy - right.y - up.y;
                float z0 = (float) dz - right.z - up.z;

                float x1 = (float) dx + right.x - up.x;
                float y1 = (float) dy + right.y - up.y;
                float z1 = (float) dz + right.z - up.z;

                float x2 = (float) dx + right.x + up.x;
                float y2 = (float) dy + right.y + up.y;
                float z2 = (float) dz + right.z + up.z;

                float x3 = (float) dx - right.x + up.x;
                float y3 = (float) dy - right.y + up.y;
                float z3 = (float) dz - right.z + up.z;

                int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(p.x, p.y, p.z));
                int alpha = (int) (fade * 240.0f);

                buffer.vertex(x0, y0, z0).texture(0.0f, 1.0f).color(255, 255, 255, alpha).light(light);
                buffer.vertex(x1, y1, z1).texture(1.0f, 1.0f).color(255, 255, 255, alpha).light(light);
                buffer.vertex(x2, y2, z2).texture(1.0f, 0.0f).color(255, 255, 255, alpha).light(light);
                buffer.vertex(x3, y3, z3).texture(0.0f, 0.0f).color(255, 255, 255, alpha).light(light);
            }
        }
    }

    private void renderCinemaRain3D(VertexConsumerProvider consumers, Vec3d camPos, MinecraftClient mc) {
        if (this.rainStreaks.isEmpty()) return;

        VertexConsumer buffer = consumers.getBuffer(RenderLayers.weather(RAIN_DROP_TEXTURE, true));
        float streakLen = 0.55f;
        float halfW = 0.024f;

        for (int i = 0; i < this.rainStreaks.size(); i++) {
            RainStreak s = this.rainStreaks.get(i);
            double dx = s.x - camPos.x;
            double dy = s.y - camPos.y;
            double dz = s.z - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < 0.4 || distSq > 24.0 * 24.0) continue;

            double dist = Math.sqrt(distSq);
            float fade = (float) MathHelper.clamp((dist - 0.6) / 1.5, 0.0, 1.0);
            if (fade <= 0.05f) continue;

            float x0 = (float) (dx - halfW);
            float x1 = (float) (dx + halfW);
            float y0 = (float) dy;
            float y1 = (float) (dy + streakLen);
            float z = (float) dz;

            int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(s.x, s.y, s.z));
            int alpha = (int) (fade * 190.0f);

            buffer.vertex(x0, y0, z).texture(0.0f, 1.0f).color(210, 230, 255, alpha).light(light);
            buffer.vertex(x1, y0, z).texture(1.0f, 1.0f).color(210, 230, 255, alpha).light(light);
            buffer.vertex(x1, y1, z).texture(1.0f, 0.0f).color(210, 230, 255, alpha).light(light);
            buffer.vertex(x0, y1, z).texture(0.0f, 0.0f).color(210, 230, 255, alpha).light(light);
        }
    }

    public Integer getCustomTreeLeavesColor(Block block, BlockPos pos) {
        if (!isEnabled() || !isCustomSeason()) return null;
        if (block != Blocks.OAK_LEAVES && block != Blocks.BIRCH_LEAVES) return null;

        String s = this.season.get();
        if ("Весна".equals(s)) {
            if (block == Blocks.BIRCH_LEAVES) {
                return 0xFFFFCAD4;
            } else {
                return 0xFFFFB2C2;
            }
        } else if ("Осень".equals(s)) {
            if (block == Blocks.BIRCH_LEAVES) {
                return 0xFFFFCE38;
            } else {
                int hash = pos != null ? Math.abs((pos.getX() * 31) ^ pos.getZ()) % 3 : 0;
                if (hash == 0) return 0xFFE05A28;
                if (hash == 1) return 0xFFDE7024;
                return 0xFFEB9C34;
            }
        } else if ("Лето".equals(s)) {
            return 0xFF48C946;
        }

        return null;
    }

    public Integer getCustomGrassColor(BlockPos pos) {
        if (!isEnabled() || !isCustomSeason()) return null;

        String s = this.season.get();
        if ("Весна".equals(s)) {
            return 0xFF82D955;
        } else if ("Осень".equals(s)) {
            return 0xFFD6A548;
        } else if ("Лето".equals(s)) {
            return 0xFF4CD137;
        }

        return null;
    }

    public boolean isCustomTime() {
        return isEnabled() && !"Выкл".equals(this.timeMode.get());
    }

    public long getTimeOfDay() {
        return Math.round(this.timeOfDay.get());
    }

    public void setTimeOfDay(double time) {
        this.timeOfDay.set(MathHelper.clamp(time, 0.0, 24000.0));
    }

    public boolean isCustomSeason() {
        return isEnabled() && !"Выкл".equals(this.season.get());
    }

    public boolean isCustomWeather() {
        return isEnabled() && !"Выкл".equals(this.weather.get());
    }

    public float getRainGradient() {
        if (!isCustomWeather()) return 0.0f;
        String w = this.weather.get();
        return ("Дождь".equals(w) || "Гроза".equals(w)) ? 1.0f : 0.0f;
    }

    public float getThunderGradient() {
        if (!isCustomWeather()) return 0.0f;
        return "Гроза".equals(this.weather.get()) ? 1.0f : 0.0f;
    }

    public boolean isRaining() {
        if (!isCustomWeather()) return false;
        String w = this.weather.get();
        return "Дождь".equals(w) || "Гроза".equals(w);
    }

    public boolean isThundering() {
        if (!isCustomWeather()) return false;
        return "Гроза".equals(this.weather.get());
    }

    public boolean shouldOverrideVanillaSound() {
        return isEnabled() && this.customSound.get() && isCustomWeather();
    }

    public boolean isCustomCloudsEnabled() {
        return isEnabled() && this.customClouds.get();
    }

    public boolean isVolumetricClouds() {
        return isCustomCloudsEnabled() && "Шейдерные".equals(this.cloudMode.get());
    }

    public String getCloudMode() {
        return this.cloudMode.get();
    }

    public double getCloudCoverage() {
        return this.cloudCoverage.get();
    }

    public double getCloudDistance() {
        return this.cloudDistance.get();
    }

    public double getCloudHeight() {
        return this.cloudHeight.get();
    }

    public double getCloudSpeed() {
        return this.cloudSpeed.get();
    }

    public int getCloudColor() {
        return this.cloudColor.get();
    }

    public boolean isThunderLightningEnabled() {
        return this.customThunder.get();
    }

    public float getLightningFrequency() {
        return (float) (this.lightningFrequency.get() / 100.0);
    }

    public ModeSetting getSeasonSetting() {
        return this.season;
    }

    public String getSeason() {
        return this.season.get();
    }

    public ModeSetting getWeatherSetting() {
        return this.weather;
    }

    public SliderSetting getTimeOfDaySetting() {
        return this.timeOfDay;
    }

    public void refreshWorldChunks() {
        this.lastAppliedSeason = this.season.get();
        this.groundLeavesDirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    private boolean isBarrierOrIgnored(BlockState s) {
        if (s == null || s.isAir()) return true;
        Block b = s.getBlock();
        return b instanceof net.minecraft.block.BarrierBlock
                || s.isOf(Blocks.BARRIER)
                || s.isOf(Blocks.STRUCTURE_VOID)
                || s.isOf(Blocks.LIGHT);
    }

    private boolean isTerrainGroundBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (isBarrierOrIgnored(state)) return false;
        Block b = state.getBlock();
        if (b instanceof net.minecraft.block.PillarBlock) return false;
        if (b instanceof PlantBlock) return false;
        if (b instanceof LeavesBlock) return false;
        if (b instanceof net.minecraft.block.FenceBlock || b instanceof net.minecraft.block.WallBlock) return false;
        if (b instanceof net.minecraft.block.StairsBlock || b instanceof net.minecraft.block.SlabBlock) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return state.blocksMovement();
    }

    private int findGroundY(MinecraftClient mc, int bx, int bz, int camBy) {
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return Integer.MIN_VALUE;

        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
        if (topY < mc.world.getBottomY()) return Integer.MIN_VALUE;

        int minY = Math.max(mc.world.getBottomY(), camBy - 36);
        BlockPos.Mutable mut = new BlockPos.Mutable(bx, topY, bz);

        for (int y = topY; y >= minY; y--) {
            mut.set(bx, y, bz);
            BlockState s = mc.world.getBlockState(mut);

            if (isBarrierOrIgnored(s)) {
                continue;
            }
            if (s.getBlock() instanceof LeavesBlock || s.getBlock() instanceof net.minecraft.block.PillarBlock || s.getBlock() instanceof PlantBlock || s.getBlock() == Blocks.SNOW) {
                continue;
            }

            if (!s.blocksMovement()) {
                continue;
            }

            if (isTerrainGroundBlock(s)) {
                return y;
            }
            break;
        }

        return Integer.MIN_VALUE;
    }

    private void updateGroundLeaves(MinecraftClient mc, int camBx, int camBy, int camBz) {
        double densityVal = this.groundLeavesDensity.get();
        if (!this.groundLeavesDirty &&
                Math.abs(camBx - this.lastLeafCamX) < 2 &&
                Math.abs(camBz - this.lastLeafCamZ) < 2 &&
                Math.abs(camBy - this.lastLeafCamY) < 2 &&
                Math.abs(densityVal - this.lastGroundLeavesDensity) < 0.1) {
            return;
        }

        this.cachedGroundLeaves.clear();
        this.lastLeafCamX = camBx;
        this.lastLeafCamY = camBy;
        this.lastLeafCamZ = camBz;
        this.lastGroundLeavesDensity = densityVal;
        this.groundLeavesDirty = false;

        if (mc.world == null) return;

        int radius = 26;
        int radSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radSq) continue;
                int bx = camBx + dx;
                int bz = camBz + dz;
                if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                int topY = findGroundY(mc, bx, bz, camBy);
                if (topY == Integer.MIN_VALUE) continue;

                long seed = ((long) bx * 3129871L) ^ ((long) bz * 6128657L) ^ ((long) topY * 19349663L);
                Random rand = new Random(seed);

                if (rand.nextInt(100) >= (int) densityVal) continue;

                int clusterRoll = rand.nextInt(100);
                boolean isHeavy = clusterRoll < 50;
                int count = isHeavy ? (4 + rand.nextInt(5)) : (2 + rand.nextInt(3));
                double clusterCenterX = bx + 0.25 + rand.nextDouble() * 0.50;
                double clusterCenterZ = bz + 0.25 + rand.nextDouble() * 0.50;

                for (int i = 0; i < count; i++) {
                    double lx = (isHeavy && i < 3) ? (clusterCenterX + (rand.nextDouble() - 0.5) * 0.44) : (bx + 0.12 + rand.nextDouble() * 0.76);
                    double lz = (isHeavy && i < 3) ? (clusterCenterZ + (rand.nextDouble() - 0.5) * 0.44) : (bz + 0.12 + rand.nextDouble() * 0.76);
                    double ly = topY + 1.003 + (i % 6) * 0.004;

                    float yaw = rand.nextFloat() * 360.0f;
                    float pitch = (rand.nextFloat() - 0.5f) * 18.0f;
                    float roll = (rand.nextFloat() - 0.5f) * 14.0f;
                    float scale = 0.24f + rand.nextFloat() * 0.12f;
                    int texIdx = rand.nextInt(LEAF_TEXTURES.length);

                    int tintRoll = rand.nextInt(5);
                    int r, g, b;
                    if (tintRoll == 0) {
                        r = 255; g = 205; b = 55;
                    } else if (tintRoll == 1) {
                        r = 235; g = 110; b = 35;
                    } else if (tintRoll == 2) {
                        r = 215; g = 60; b = 38;
                    } else if (tintRoll == 3) {
                        r = 200; g = 145; b = 45;
                    } else {
                        r = 195; g = 85; b = 32;
                    }

                    this.cachedGroundLeaves.add(new GroundLeaf(lx, ly, lz, yaw, pitch, roll, scale, texIdx, r, g, b));
                }
            }
        }
    }

    public void renderWorld3D(VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        String curSeason = this.season.get();
        int camBx = MathHelper.floor(camPos.x);
        int camBy = MathHelper.floor(camPos.y);
        int camBz = MathHelper.floor(camPos.z);

        if ("Осень".equals(curSeason) && this.autumnLeavesGround.get()) {
            updateGroundLeaves(mc, camBx, camBy, camBz);

            for (int t = 0; t < LEAF_TEXTURES.length; t++) {
                Identifier leafTex = LEAF_TEXTURES[t];
                VertexConsumer leafBuffer = null;

                for (int i = 0; i < this.cachedGroundLeaves.size(); i++) {
                    GroundLeaf leaf = this.cachedGroundLeaves.get(i);
                    if (leaf.texIdx != t) continue;

                    double dx = leaf.x - camPos.x;
                    double dy = leaf.y - camPos.y;
                    double dz = leaf.z - camPos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > 32.0 * 32.0) continue;

                    if (leafBuffer == null) {
                        leafBuffer = immediate.getBuffer(RenderLayers.weather(leafTex, true));
                    }

                    Quaternionf rot = new Quaternionf()
                            .rotateY((float) Math.toRadians(leaf.yaw))
                            .rotateX((float) Math.toRadians(leaf.pitch))
                            .rotateZ((float) Math.toRadians(leaf.roll));

                    float s = leaf.scale * 0.5f;
                    Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(rot).mul(s);
                    Vector3f fwd = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot).mul(s);

                    float x0 = (float) dx - right.x - fwd.x;
                    float y0 = (float) dy - right.y - fwd.y;
                    float z0 = (float) dz - right.z - fwd.z;

                    float x1 = (float) dx + right.x - fwd.x;
                    float y1 = (float) dy + right.y - fwd.y;
                    float z1 = (float) dz + right.z - fwd.z;

                    float x2 = (float) dx + right.x + fwd.x;
                    float y2 = (float) dy + right.y + fwd.y;
                    float z2 = (float) dz + right.z + fwd.z;

                    float x3 = (float) dx - right.x + fwd.x;
                    float y3 = (float) dy - right.y + fwd.y;
                    float z3 = (float) dz - right.z + fwd.z;

                    int r = leaf.r;
                    int g = leaf.g;
                    int b = leaf.b;

                    int leafLight = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(leaf.x, leaf.y, leaf.z));

                    leafBuffer.vertex(x0, y0, z0).texture(0.0f, 1.0f).color(r, g, b, 245).light(leafLight);
                    leafBuffer.vertex(x1, y1, z1).texture(1.0f, 1.0f).color(r, g, b, 245).light(leafLight);
                    leafBuffer.vertex(x2, y2, z2).texture(1.0f, 0.0f).color(r, g, b, 245).light(leafLight);
                    leafBuffer.vertex(x3, y3, z3).texture(0.0f, 0.0f).color(r, g, b, 245).light(leafLight);
                }
            }
        }

        if (isThundering() && isThunderLightningEnabled()) {
            AmbienceLightningRenderer.INSTANCE.render(camPos, 0.0F, 0.005F);
        }
    }

    private static class FallingParticle {
        double x, y, z;
        float vx, vy, vz;
        float swayRadius, swayPhase;
        float roll, rollSpeed;
        float pitch, pitchSpeed;
        float scale;
        float age;
        float maxAge;
        int texIdx;
    }

    private static class RainStreak {
        double x, y, z;
        double vx, vy, vz;
        int groundY;
    }

    private static class GroundLeaf {
        final double x, y, z;
        final float yaw, pitch, roll;
        final float scale;
        final int texIdx;
        final int r, g, b;

        GroundLeaf(double x, double y, double z, float yaw, float pitch, float roll, float scale, int texIdx, int r, int g, int b) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.scale = scale;
            this.texIdx = texIdx;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }
}
