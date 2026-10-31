package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.util.AudioPlayer;
import egg.client.util.ColorUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
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

    private static final Identifier[] SNOWFLAKE_TEXTURES = new Identifier[]{
            Identifier.of("egg", "textures/world/snowflake_1.png"),
            Identifier.of("egg", "textures/world/snowflake_2.png"),
            Identifier.of("egg", "textures/world/snowflake_3.png"),
            Identifier.of("egg", "textures/world/snowflake_4.png"),
            Identifier.of("egg", "textures/world/snowflake_5.png"),
            Identifier.of("egg", "textures/world/snowflake_6.png"),
            Identifier.of("egg", "textures/world/snowflake_7.png"),
            Identifier.of("egg", "textures/world/snowflake_8.png"),
            Identifier.of("egg", "textures/world/snowflake_9.png"),
            Identifier.of("egg", "textures/world/snowflake_10.png")
    };

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
    private static final Identifier SNOW_TEXTURE = Identifier.of("egg", "textures/world/snow.png");
    private static final Identifier FOOTPRINT_TEXTURE = Identifier.of("egg", "textures/world/footprint.png");

    private final ModeSetting timeMode = new ModeSetting("Время суток", "Кастом", "Режим времени", "Кастом", "Синхрон", "Выкл");
    private final SliderSetting timeOfDay = new SliderSetting("Час дня", 6000.0, 0.0, 24000.0, 100.0, "Время игрового мира (0..24000)");

    private final ModeSetting season = new ModeSetting("Время года", "Весна", "Сезон мира", "Зима", "Весна", "Лето", "Осень", "Выкл");
    private final ModeSetting weather = new ModeSetting("Погода", "Ясно", "Кастомная погода", "Дождь", "Гроза", "Ясно", "Выкл");

    private final BooleanSetting particles = new BooleanSetting("Партиклы", true, "3D планирующие частицы в воздухе");
    private final SliderSetting particleDensity = new SliderSetting("Кол-во частиц", 80.0, 20.0, 180.0, 10.0, "Плотность частиц");
    private final SliderSetting snowSpeed = new SliderSetting("Скорость снега", 1.4, 0.5, 3.0, 0.1, "Скорость падения снежинок");

    private final BooleanSetting smoothSnow = new BooleanSetting("Рельефный снег", true, "Объемные сугробы и бугорки со скруглением");
    private final BooleanSetting snowFootprints = new BooleanSetting("Следы на снегу", true, "Оставлять следы шагов на снегу");
    private final BooleanSetting autumnLeavesGround = new BooleanSetting("Опавшие листья", true, "3D кучки и пары листьев на земле осенью");

    private final BooleanSetting customFog = new BooleanSetting("Кастомный туман", false, "Атмосферный цвет тумана горизонта");
    private final ModeSetting fogColorMode = new ModeSetting("Цвет тумана", "По сезону", "Режим цвета тумана", "По сезону", "Кастом", "Ванилла");
    private final ColorSetting fogCustomColor = new ColorSetting("Цвет", 0xFF9FC5E8, "Цвет кастомного тумана");

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

    private final List<FallingParticle> fallingParticles = new ArrayList<>();
    private final List<RainStreak> rainStreaks = new ArrayList<>();
    private final List<Footprint> footprints = new ArrayList<>();
    private final List<SnowQuad> cachedSnowMesh = new ArrayList<>();
    private final List<GroundLeaf> cachedGroundLeaves = new ArrayList<>();
    private final Random random = new Random();

    private double lastStepX = Double.NaN;
    private double lastStepY = Double.NaN;
    private double lastStepZ = Double.NaN;
    private boolean lastFootLeft = false;

    private int lastSnowCamX = Integer.MIN_VALUE;
    private int lastSnowCamY = Integer.MIN_VALUE;
    private int lastSnowCamZ = Integer.MIN_VALUE;
    private boolean snowMeshDirty = true;

    private int lastLeafCamX = Integer.MIN_VALUE;
    private int lastLeafCamY = Integer.MIN_VALUE;
    private int lastLeafCamZ = Integer.MIN_VALUE;
    private boolean groundLeavesDirty = true;

    private int thunderCooldown = 120;
    private long lastFrameNanos = 0L;
    private boolean wasAudioRaining = false;
    private String lastAppliedSeason = "";

    public WorldTweaks() {
        super("World Tweaks", "Кастомная погода, времена года и оптимизированные частицы", Category.MISC);
        addSettings(
                this.timeMode, this.timeOfDay, this.timeHour,
                this.season, this.weather,
                this.particles, this.particleDensity, this.snowSpeed,
                this.smoothSnow, this.snowFootprints, this.autumnLeavesGround,
                this.shaderQuality, this.reflections,
                this.bloom, this.bloomIntensity, this.bloomThreshold,
                this.colorGrade, this.colorGradeSaturation, this.colorGradeContrast, this.colorGradeBrightness, this.colorGradeTemperature,
                this.customFog, this.fogColorMode, this.fogCustomColor,
                this.customSound, this.soundVolume
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

    @Override
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
        this.footprints.clear();
        this.cachedSnowMesh.clear();
        this.cachedGroundLeaves.clear();
        this.snowMeshDirty = true;
        this.groundLeavesDirty = true;
        if (!"Выкл".equals(this.lastAppliedSeason)) {
            refreshWorldChunks();
        }
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

        syncAudioState();
        updateFootprints(mc);
    }

    public void syncAudioState() {
        boolean shouldRain = isEnabled() && this.customSound.get()
                && ("Дождь".equals(this.weather.get()) || "Гроза".equals(this.weather.get()));

        if (shouldRain) {
            if (!this.wasAudioRaining) {
                AudioPlayer.startRain((float) this.soundVolume.get());
                this.wasAudioRaining = true;
            } else {
                AudioPlayer.setRainVolume((float) this.soundVolume.get());
            }
        } else {
            AudioPlayer.stopAll();
            this.wasAudioRaining = false;
        }
    }

    private int getSurfaceY(MinecraftClient mc, double x, double z) {
        if (mc == null || mc.world == null) return -64;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return -64;
        try {
            int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
            BlockPos.Mutable mut = new BlockPos.Mutable(bx, topY, bz);
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

        if ("Зима".equals(curSeason)) {
            p.texIdx = this.random.nextInt(SNOWFLAKE_TEXTURES.length);
            float speedMult = (float) this.snowSpeed.get();
            p.vy = (-0.036f - this.random.nextFloat() * 0.034f) * speedMult;
            p.vx = (this.random.nextFloat() - 0.5f) * 0.014f;
            p.vz = (this.random.nextFloat() - 0.5f) * 0.014f;
            p.swayRadius = 0.018f + this.random.nextFloat() * 0.018f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 2.2f;
            p.pitchSpeed = (this.random.nextFloat() - 0.5f) * 1.8f;
            p.scale = 0.22f + this.random.nextFloat() * 0.18f;
        } else if ("Весна".equals(curSeason)) {
            p.texIdx = this.random.nextInt(SAKURA_TEXTURES.length);
            p.vy = -0.012f - this.random.nextFloat() * 0.014f;
            p.vx = 0.014f + this.random.nextFloat() * 0.016f;
            p.vz = 0.006f + this.random.nextFloat() * 0.012f;
            p.swayRadius = 0.024f + this.random.nextFloat() * 0.024f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 2.5f;
            p.pitchSpeed = 1.4f + this.random.nextFloat() * 2.0f;
            p.scale = 0.26f + this.random.nextFloat() * 0.16f;
        } else if ("Осень".equals(curSeason)) {
            p.texIdx = this.random.nextInt(LEAF_TEXTURES.length);
            p.vy = -0.018f - this.random.nextFloat() * 0.020f;
            p.vx = 0.012f + this.random.nextFloat() * 0.014f;
            p.vz = (this.random.nextFloat() - 0.5f) * 0.010f;
            p.swayRadius = 0.030f + this.random.nextFloat() * 0.030f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 3.0f;
            p.pitchSpeed = 1.8f + this.random.nextFloat() * 2.5f;
            p.scale = 0.26f + this.random.nextFloat() * 0.18f;
        } else {
            p.texIdx = 0;
            p.vy = -0.006f - this.random.nextFloat() * 0.006f;
            p.vx = (this.random.nextFloat() - 0.5f) * 0.008f;
            p.vz = (this.random.nextFloat() - 0.5f) * 0.008f;
            p.swayRadius = 0.014f;
            p.rollSpeed = 0.5f;
            p.pitchSpeed = 0.5f;
            p.scale = 0.14f + this.random.nextFloat() * 0.08f;
        }
    }

    private void updateRainSmooth(Vec3d center, float dt, MinecraftClient mc) {
        int streakCount = 140;
        while (this.rainStreaks.size() < streakCount) {
            RainStreak s = new RainStreak();
            resetRainStreak(s, center, true, mc);
            this.rainStreaks.add(s);
        }
        while (this.rainStreaks.size() > streakCount) {
            this.rainStreaks.remove(this.rainStreaks.size() - 1);
        }

        float speedFactor = dt * 20.0f;
        for (int i = 0; i < this.rainStreaks.size(); i++) {
            RainStreak s = this.rainStreaks.get(i);
            s.x += s.vx * speedFactor;
            s.y += s.vy * speedFactor;
            s.z += s.vz * speedFactor;

            if (s.y <= s.groundY || s.y < center.y - 5.0) {
                resetRainStreak(s, center, false, mc);
            }
        }
    }

    private void resetRainStreak(RainStreak s, Vec3d center, boolean randomHeight, MinecraftClient mc) {
        double r = 1.0 + Math.sqrt(this.random.nextDouble()) * 15.0;
        double a = this.random.nextDouble() * Math.PI * 2.0;
        s.x = center.x + Math.cos(a) * r;
        s.z = center.z + Math.sin(a) * r;
        s.groundY = getSurfaceY(mc, s.x, s.z);

        double spawnBase = Math.max(center.y, (double) s.groundY);
        s.y = randomHeight
                ? (spawnBase + this.random.nextDouble() * 14.0)
                : (spawnBase + 8.0 + this.random.nextDouble() * 6.0);

        s.vy = -1.40 - this.random.nextDouble() * 0.30;
        s.vx = 0.16;
        s.vz = 0.08;
    }

    public Vector4f getCustomFogColor(Vector4f vanilla) {
        if (!isCustomFogColorEnabled()) return vanilla;

        String mode = this.fogColorMode.get();
        if ("Кастом".equals(mode)) {
            int c = this.fogCustomColor.get();
            return new Vector4f(ColorUtil.red(c) / 255.0f, ColorUtil.green(c) / 255.0f, ColorUtil.blue(c) / 255.0f, 1.0f);
        }

        if ("Гроза".equals(this.weather.get())) {
            return new Vector4f(0.28f, 0.34f, 0.45f, 1.0f);
        }
        if ("Дождь".equals(this.weather.get())) {
            return new Vector4f(0.48f, 0.55f, 0.65f, 1.0f);
        }

        String s = this.season.get();
        if ("Зима".equals(s)) {
            return new Vector4f(0.84f, 0.90f, 0.97f, 1.0f);
        } else if ("Весна".equals(s)) {
            return new Vector4f(0.96f, 0.86f, 0.90f, 1.0f);
        } else if ("Лето".equals(s)) {
            return new Vector4f(0.96f, 0.93f, 0.80f, 1.0f);
        } else if ("Осень".equals(s)) {
            return new Vector4f(0.92f, 0.78f, 0.62f, 1.0f);
        }
        return vanilla;
    }

    public boolean renderWeatherPass(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return false;

        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) this.lastFrameNanos = now;
        float dt = (float) ((now - this.lastFrameNanos) * 1e-9);
        this.lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        boolean isRain = "Дождь".equals(this.weather.get()) || "Гроза".equals(this.weather.get()) || (mc.world.isRaining() && !"Ясно".equals(this.weather.get()));

        syncAudioState();

        if (this.particles.get() && !"Выкл".equals(this.season.get())) {
            updateParticlesSmooth(camPos, dt, mc);
            renderParticles3D(consumers, camPos, mc);
        }

        if (isRain) {
            updateRainSmooth(camPos, dt, mc);
            renderCinemaRain3D(consumers, camPos, mc);
        }

        return isRain;
    }

    private void renderParticles3D(VertexConsumerProvider consumers, Vec3d camPos, MinecraftClient mc) {
        String curSeason = this.season.get();
        Identifier[] texArray;
        if ("Зима".equals(curSeason)) {
            texArray = SNOWFLAKE_TEXTURES;
        } else if ("Весна".equals(curSeason)) {
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

                float fx = (float) dx;
                float fy = (float) dy;
                float fz = (float) dz;
                int alpha = Math.round(fade * 255.0f);

                float x0 = fx - right.x - up.x;
                float y0 = fy - right.y - up.y;
                float z0 = fz - right.z - up.z;

                float x1 = fx + right.x - up.x;
                float y1 = fy + right.y - up.y;
                float z1 = fz + right.z - up.z;

                float x2 = fx + right.x + up.x;
                float y2 = fy + right.y + up.y;
                float z2 = fz + right.z + up.z;

                float x3 = fx - right.x + up.x;
                float y3 = fy - right.y + up.y;
                float z3 = fz - right.z + up.z;

                buffer.vertex(x0, y0, z0).texture(0.0f, 1.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                buffer.vertex(x1, y1, z1).texture(1.0f, 1.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                buffer.vertex(x2, y2, z2).texture(1.0f, 0.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                buffer.vertex(x3, y3, z3).texture(0.0f, 0.0f).color(255, 255, 255, alpha).light(0x00F000F0);
            }
        }
    }

    private void renderCinemaRain3D(VertexConsumerProvider consumers, Vec3d camPos, MinecraftClient mc) {
        if (this.rainStreaks.isEmpty()) return;

        VertexConsumer buffer = consumers.getBuffer(RenderLayers.weather(RAIN_DROP_TEXTURE, true));

        for (int i = 0; i < this.rainStreaks.size(); i++) {
            RainStreak s = this.rainStreaks.get(i);
            double dx = s.x - camPos.x;
            double dy = s.y - camPos.y;
            double dz = s.z - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < 0.6 || distSq > 30.0 * 30.0) continue;

            float dist = (float) Math.sqrt(distSq);
            float alpha = MathHelper.clamp(1.0f - (dist / 28.0f), 0.30f, 0.95f);
            int col = Math.round(alpha * 250.0f);

            double horizDist = Math.sqrt(dx * dx + dz * dz);
            if (horizDist < 0.001) horizDist = 0.001;
            float nx = (float) (-dz / horizDist);
            float nz = (float) (dx / horizDist);

            float halfW = 0.038f;
            float len = 1.75f;

            float x1 = (float) dx - nx * halfW;
            float z1 = (float) dz - nz * halfW;
            float x2 = (float) dx + nx * halfW;
            float z2 = (float) dz + nz * halfW;
            float yTop = (float) dy;
            float yBottom = (float) (dy - len);

            buffer.vertex(x1, yTop, z1).texture(0.0f, 0.0f).color(225, 245, 255, col).light(0x00F000F0);
            buffer.vertex(x2, yTop, z2).texture(1.0f, 0.0f).color(225, 245, 255, col).light(0x00F000F0);
            buffer.vertex(x2, yBottom, z2).texture(1.0f, 1.0f).color(225, 245, 255, col).light(0x00F000F0);
            buffer.vertex(x1, yBottom, z1).texture(0.0f, 1.0f).color(225, 245, 255, col).light(0x00F000F0);
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
        } else if ("Зима".equals(s)) {
            return 0xFFFFFFFF;
        } else if ("Лето".equals(s)) {
            return 0xFF48C946;
        }

        return null;
    }

    public Integer getCustomGrassColor(BlockPos pos) {
        if (!isEnabled() || !isCustomSeason()) return null;

        String s = this.season.get();
        if ("Зима".equals(s)) {
            return 0xFFFFFFFF;
        } else if ("Весна".equals(s)) {
            return 0xFF82D955;
        } else if ("Осень".equals(s)) {
            return 0xFFD6A548;
        } else if ("Лето".equals(s)) {
            return 0xFF4CD137;
        }

        return null;
    }

    public boolean isCustomTime() {
        return isEnabled() && !"Выкл".equals(this.timeMode.get()) && !"Синхрон".equals(this.timeMode.get());
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
        return ("Дождь".equals(this.weather.get()) || "Гроза".equals(this.weather.get())) ? 1.0f : 0.0f;
    }

    public float getThunderGradient() {
        if (!isCustomWeather()) return 0.0f;
        return "Гроза".equals(this.weather.get()) ? 1.0f : 0.0f;
    }

    public boolean isRaining() {
        return getRainGradient() > 0.01f;
    }

    public boolean isThundering() {
        return getThunderGradient() > 0.01f;
    }

    public boolean shouldOverrideVanillaSound() {
        return isEnabled() && isCustomWeather() && this.customSound.get();
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
        this.snowMeshDirty = true;
        this.groundLeavesDirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
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

    private void updateFootprints(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        this.footprints.removeIf(fp -> now - fp.spawnTime >= fp.lifetime);

        if (!this.snowFootprints.get() || !"Зима".equals(this.season.get())) {
            return;
        }

        if (mc.player != null && mc.player.isOnGround()) {
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();

            if (Double.isNaN(this.lastStepX)) {
                this.lastStepX = px;
                this.lastStepY = py;
                this.lastStepZ = pz;
            } else {
                double dx = px - this.lastStepX;
                double dy = py - this.lastStepY;
                double dz = pz - this.lastStepZ;
                double distSq = dx * dx + dz * dz;

                if (distSq >= 0.65 * 0.65) {
                    this.lastFootLeft = !this.lastFootLeft;
                    float yaw = mc.player.getYaw();
                    double yawRad = Math.toRadians(yaw);
                    double sideSign = this.lastFootLeft ? -1.0 : 1.0;
                    double sideX = -Math.cos(yawRad) * 0.17 * sideSign;
                    double sideZ = -Math.sin(yawRad) * 0.17 * sideSign;

                    double stepX = px + sideX;
                    double stepZ = pz + sideZ;
                    double surfaceY = getSnowSurfaceY(mc, stepX, stepZ);

                    this.footprints.add(new Footprint(stepX, surfaceY + 0.015, stepZ, yaw, this.lastFootLeft, now, 25000L));

                    this.lastStepX = px;
                    this.lastStepY = py;
                    this.lastStepZ = pz;

                    if (this.footprints.size() > 200) {
                        this.footprints.remove(0);
                    }
                }
            }
        }
    }

    public double getSnowSurfaceY(MinecraftClient mc, double x, double z) {
        if (mc == null || mc.world == null) return 64.0;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return 64.0;
        try {
            int camBy = mc.player != null ? MathHelper.floor(mc.player.getY()) : 64;
            int groundY = findGroundY(mc, bx, bz, camBy);
            if (groundY == Integer.MIN_VALUE) {
                return 64.0;
            }
            if (!this.smoothSnow.get() || !"Зима".equals(this.season.get())) {
                return groundY + 1.002;
            }
            return groundY + 1.002 + getSnowDepthAt(x, z);
        } catch (Throwable ignored) {
            return 64.0;
        }
    }

    public float getSnowDepthAt(double worldX, double worldZ) {

        double n1 = Math.sin(worldX * 0.075 + 1.4) * Math.cos(worldZ * 0.075 + 0.8);
        double n2 = Math.sin((worldX * 0.045 + worldZ * 0.055) + 2.8) * 0.45 + Math.cos((worldX * 0.055 - worldZ * 0.045) + 1.2) * 0.35;
        double n3 = Math.sin(worldX * 0.14 + 0.3) * Math.cos(worldZ * 0.14 + 1.1) * 0.20;

        double combined = (n1 * 0.52 + n2 * 0.34 + n3 * 0.14 + 1.0) * 0.5;
        combined = Math.max(0.0, Math.min(1.0, combined));

        return (float) (Math.pow(combined, 1.35) * 0.24 + 0.03);
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

            if (isTerrainGroundBlock(s)) {
                return y;
            }
            break;
        }

        return Integer.MIN_VALUE;
    }

    private int getElevationDiff(MinecraftClient mc, int x, int z, int baseTopY) {
        if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) return 0;

        BlockPos wallPos = new BlockPos(x, baseTopY + 1, z);
        BlockState wallState = mc.world.getBlockState(wallPos);
        if (wallState.blocksMovement() && !isBarrierOrIgnored(wallState) && !(wallState.getBlock() instanceof PlantBlock) && !(wallState.getBlock() instanceof LeavesBlock)) {
            return 1;
        }

        BlockState atBase = mc.world.getBlockState(new BlockPos(x, baseTopY, z));
        if (atBase.blocksMovement() && !(atBase.getBlock() instanceof LeavesBlock)) {
            return 0;
        }

        int nTopY = findGroundY(mc, x, z, baseTopY);
        if (nTopY == Integer.MIN_VALUE) {
            return -999;
        }
        return nTopY - baseTopY;
    }

    private void buildSnowBlock(MinecraftClient mc, int bx, int topY, int bz, int distSq, int lod0RadSq) {
        BlockPos pos = new BlockPos(bx, topY, bz);
        BlockPos posAbove = pos.up();
        BlockState aboveState = mc.world.getBlockState(posAbove);
        if (aboveState.blocksMovement() && !(aboveState.getBlock() instanceof PlantBlock)) {
            return;
        }

        int light = WorldRenderer.getLightmapCoordinates(mc.world, posAbove);
        float baseY = topY + 1.002f;
        boolean isLod0 = distSq <= lod0RadSq;

        int diffN = getElevationDiff(mc, bx, bz - 1, topY);
        int diffS = getElevationDiff(mc, bx, bz + 1, topY);
        int diffW = getElevationDiff(mc, bx - 1, bz, topY);
        int diffE = getElevationDiff(mc, bx + 1, bz, topY);

        if (isLod0) {

            float dCenter = getSnowDepthAt(bx + 0.5, bz + 0.5);
            float dN      = getSnowDepthAt(bx + 0.5, bz);
            float dS      = getSnowDepthAt(bx + 0.5, bz + 1.0);
            float dW      = getSnowDepthAt(bx, bz + 0.5);
            float dE      = getSnowDepthAt(bx + 1.0, bz + 0.5);

            float d00     = getSnowDepthAt(bx, bz);
            float d10     = getSnowDepthAt(bx + 1.0, bz);
            float d11     = getSnowDepthAt(bx + 1.0, bz + 1.0);
            float d01     = getSnowDepthAt(bx, bz + 1.0);

            float mNy = (diffN < 0) ? (baseY + dN * 0.45f) : (baseY + dN);
            float mSy = (diffS < 0) ? (baseY + dS * 0.45f) : (baseY + dS);
            float mWy = (diffW < 0) ? (baseY + dW * 0.45f) : (baseY + dW);
            float mEy = (diffE < 0) ? (baseY + dE * 0.45f) : (baseY + dE);

            float cy = baseY + dCenter;

            float c00 = (diffN < 0 || diffW < 0) ? (baseY + d00 * 0.45f) : (baseY + d00);
            float c10 = (diffN < 0 || diffE < 0) ? (baseY + d10 * 0.45f) : (baseY + d10);
            float c11 = (diffS < 0 || diffE < 0) ? (baseY + d11 * 0.45f) : (baseY + d11);
            float c01 = (diffS < 0 || diffW < 0) ? (baseY + d01 * 0.45f) : (baseY + d01);

            int cr = Math.min(255, (int) (240 + dCenter * 70));
            int cg = Math.min(255, (int) (246 + dCenter * 40));
            int cb = 255;

            int mrN = Math.min(255, (int) (232 + dN * 70));
            int mgN = Math.min(255, (int) (240 + dN * 50));

            int mrS = Math.min(255, (int) (232 + dS * 70));
            int mgS = Math.min(255, (int) (240 + dS * 50));

            int mrW = Math.min(255, (int) (232 + dW * 70));
            int mgW = Math.min(255, (int) (240 + dW * 50));

            int mrE = Math.min(255, (int) (232 + dE * 70));
            int mgE = Math.min(255, (int) (240 + dE * 50));

            SnowVertex pCenter = new SnowVertex(bx + 0.5f, cy, bz + 0.5f, 0.5f, 0.5f, cr, cg, cb, 255, light);
            SnowVertex p00     = new SnowVertex(bx, c00, bz, 0.0f, 0.0f, 226, 235, 246, 255, light);
            SnowVertex p10     = new SnowVertex(bx + 1.0f, c10, bz, 1.0f, 0.0f, 226, 235, 246, 255, light);
            SnowVertex p11     = new SnowVertex(bx + 1.0f, c11, bz + 1.0f, 1.0f, 1.0f, 226, 235, 246, 255, light);
            SnowVertex p01     = new SnowVertex(bx, c01, bz + 1.0f, 0.0f, 1.0f, 226, 235, 246, 255, light);

            SnowVertex mN = new SnowVertex(bx + 0.5f, mNy, bz, 0.5f, 0.0f, mrN, mgN, 250, 255, light);
            SnowVertex mS = new SnowVertex(bx + 0.5f, mSy, bz + 1.0f, 0.5f, 1.0f, mrS, mgS, 250, 255, light);
            SnowVertex mW = new SnowVertex(bx, mWy, bz + 0.5f, 0.0f, 0.5f, mrW, mgW, 250, 255, light);
            SnowVertex mE = new SnowVertex(bx + 1.0f, mEy, bz + 0.5f, 1.0f, 0.5f, mrE, mgE, 250, 255, light);

            this.cachedSnowMesh.add(new SnowQuad(p00, mN, pCenter, mW));
            this.cachedSnowMesh.add(new SnowQuad(mN, p10, mE, pCenter));
            this.cachedSnowMesh.add(new SnowQuad(pCenter, mE, p11, mS));
            this.cachedSnowMesh.add(new SnowQuad(mW, pCenter, mS, p01));
        } else {

            float flatY = baseY + Math.min(0.04f, getSnowDepthAt(bx + 0.5, bz + 0.5) * 0.4f);
            SnowVertex v0 = new SnowVertex(bx, flatY, bz, 0.0f, 0.0f, 246, 250, 255, 255, light);
            SnowVertex v1 = new SnowVertex(bx + 1.0f, flatY, bz, 1.0f, 0.0f, 246, 250, 255, 255, light);
            SnowVertex v2 = new SnowVertex(bx + 1.0f, flatY, bz + 1.0f, 1.0f, 1.0f, 246, 250, 255, 255, light);
            SnowVertex v3 = new SnowVertex(bx, flatY, bz + 1.0f, 0.0f, 1.0f, 246, 250, 255, 255, light);
            this.cachedSnowMesh.add(new SnowQuad(v0, v1, v2, v3));
        }
    }

    private void buildLeavesSnowBlock(MinecraftClient mc, int bx, int topY, int bz) {
        BlockPos pos = new BlockPos(bx, topY, bz);
        if (!mc.world.getBlockState(pos.up()).isAir()) return;

        int light = WorldRenderer.getLightmapCoordinates(mc.world, pos.up());
        float baseY = topY + 1.002f;

        float depth = getSnowDepthAt(bx + 0.5, bz + 0.5) * 0.35f + 0.015f;
        float cy = baseY + depth;

        SnowVertex p00 = new SnowVertex(bx, baseY + 0.008f, bz, 0.0f, 0.0f, 242, 248, 255, 255, light);
        SnowVertex p10 = new SnowVertex(bx + 1.0f, baseY + 0.008f, bz, 1.0f, 0.0f, 242, 248, 255, 255, light);
        SnowVertex p11 = new SnowVertex(bx + 1.0f, baseY + 0.008f, bz + 1.0f, 1.0f, 1.0f, 242, 248, 255, 255, light);
        SnowVertex p01 = new SnowVertex(bx, baseY + 0.008f, bz + 1.0f, 0.0f, 1.0f, 242, 248, 255, 255, light);
        SnowVertex pCenter = new SnowVertex(bx + 0.5f, cy, bz + 0.5f, 0.5f, 0.5f, 255, 255, 255, 255, light);

        SnowVertex mN = new SnowVertex(bx + 0.5f, baseY + depth * 0.5f, bz, 0.5f, 0.0f, 246, 250, 255, 255, light);
        SnowVertex mS = new SnowVertex(bx + 0.5f, baseY + depth * 0.5f, bz + 1.0f, 0.5f, 1.0f, 246, 250, 255, 255, light);
        SnowVertex mW = new SnowVertex(bx, baseY + depth * 0.5f, bz + 0.5f, 0.0f, 0.5f, 246, 250, 255, 255, light);
        SnowVertex mE = new SnowVertex(bx + 1.0f, baseY + depth * 0.5f, bz + 0.5f, 1.0f, 0.5f, 246, 250, 255, 255, light);

        this.cachedSnowMesh.add(new SnowQuad(p00, mN, pCenter, mW));
        this.cachedSnowMesh.add(new SnowQuad(mN, p10, mE, pCenter));
        this.cachedSnowMesh.add(new SnowQuad(pCenter, mE, p11, mS));
        this.cachedSnowMesh.add(new SnowQuad(mW, pCenter, mS, p01));
    }

    private void rebuildSnowMesh(MinecraftClient mc, int camBx, int camBy, int camBz) {
        this.cachedSnowMesh.clear();
        if (mc.world == null) return;

        int maxRadius = 38;
        int maxRadSq = maxRadius * maxRadius;
        int lod0RadSq = 22 * 22;

        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > maxRadSq) continue;
                int bx = camBx + dx;
                int bz = camBz + dz;
                if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                int groundY = findGroundY(mc, bx, bz, camBy);
                if (groundY != Integer.MIN_VALUE) {
                    buildSnowBlock(mc, bx, groundY, bz, distSq, lod0RadSq);
                }

                int searchLeafY = Math.min(mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1, groundY + 36);
                for (int y = searchLeafY; y > groundY; y--) {
                    BlockPos lp = new BlockPos(bx, y, bz);
                    BlockState ls = mc.world.getBlockState(lp);
                    if (isBarrierOrIgnored(ls)) continue;
                    if (ls.getBlock() instanceof LeavesBlock && (mc.world.getBlockState(lp.up()).isAir() || isBarrierOrIgnored(mc.world.getBlockState(lp.up())))) {
                        buildLeavesSnowBlock(mc, bx, y, bz);
                    }
                    break;
                }
            }
        }
    }
    private void updateGroundLeaves(MinecraftClient mc, int camBx, int camBy, int camBz) {
        if (!this.groundLeavesDirty &&
                Math.abs(camBx - this.lastLeafCamX) < 2 &&
                Math.abs(camBz - this.lastLeafCamZ) < 2 &&
                Math.abs(camBy - this.lastLeafCamY) < 2) {
            return;
        }

        this.cachedGroundLeaves.clear();
        this.lastLeafCamX = camBx;
        this.lastLeafCamY = camBy;
        this.lastLeafCamZ = camBz;
        this.groundLeavesDirty = false;

        if (mc.world == null) return;

        int radius = 18;
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

                if (rand.nextInt(100) > 35) continue;

                int count = 2 + rand.nextInt(3);
                double clusterCenterX = bx + 0.25 + rand.nextDouble() * 0.50;
                double clusterCenterZ = bz + 0.25 + rand.nextDouble() * 0.50;

                for (int i = 0; i < count; i++) {
                    double lx = clusterCenterX + (rand.nextDouble() - 0.5) * 0.38;
                    double lz = clusterCenterZ + (rand.nextDouble() - 0.5) * 0.38;
                    double ly = topY + 1.004 + i * 0.005;

                    float yaw = rand.nextFloat() * 360.0f;
                    float pitch = (rand.nextFloat() - 0.5f) * 18.0f;
                    float roll = (rand.nextFloat() - 0.5f) * 14.0f;
                    float scale = 0.24f + rand.nextFloat() * 0.12f;
                    int texIdx = rand.nextInt(LEAF_TEXTURES.length);

                    int tintRoll = rand.nextInt(4);
                    int r, g, b;
                    if (tintRoll == 0) {
                        r = 255; g = 205; b = 55;
                    } else if (tintRoll == 1) {
                        r = 235; g = 110; b = 35;
                    } else if (tintRoll == 2) {
                        r = 215; g = 60; b = 38;
                    } else {
                        r = 200; g = 145; b = 45;
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

        if ("Зима".equals(curSeason)) {
            if (this.smoothSnow.get()) {
                if (this.snowMeshDirty ||
                        Math.abs(camBx - this.lastSnowCamX) >= 2 ||
                        Math.abs(camBz - this.lastSnowCamZ) >= 2 ||
                        Math.abs(camBy - this.lastSnowCamY) >= 2) {
                    rebuildSnowMesh(mc, camBx, camBy, camBz);
                    this.lastSnowCamX = camBx;
                    this.lastSnowCamY = camBy;
                    this.lastSnowCamZ = camBz;
                    this.snowMeshDirty = false;
                }

                if (!this.cachedSnowMesh.isEmpty()) {
                    VertexConsumer snowBuf = immediate.getBuffer(RenderLayers.weather(SNOW_TEXTURE, true));
                    for (int i = 0; i < this.cachedSnowMesh.size(); i++) {
                        SnowQuad q = this.cachedSnowMesh.get(i);
                        snowBuf.vertex((float) (q.v0.x - camPos.x), (float) (q.v0.y - camPos.y), (float) (q.v0.z - camPos.z)).texture(q.v0.u, q.v0.v).color(q.v0.r, q.v0.g, q.v0.b, q.v0.a).light(q.v0.light);
                        snowBuf.vertex((float) (q.v1.x - camPos.x), (float) (q.v1.y - camPos.y), (float) (q.v1.z - camPos.z)).texture(q.v1.u, q.v1.v).color(q.v1.r, q.v1.g, q.v1.b, q.v1.a).light(q.v1.light);
                        snowBuf.vertex((float) (q.v2.x - camPos.x), (float) (q.v2.y - camPos.y), (float) (q.v2.z - camPos.z)).texture(q.v2.u, q.v2.v).color(q.v2.r, q.v2.g, q.v2.b, q.v2.a).light(q.v2.light);
                        snowBuf.vertex((float) (q.v3.x - camPos.x), (float) (q.v3.y - camPos.y), (float) (q.v3.z - camPos.z)).texture(q.v3.u, q.v3.v).color(q.v3.r, q.v3.g, q.v3.b, q.v3.a).light(q.v3.light);
                    }
                }
            }

            if (this.snowFootprints.get() && !this.footprints.isEmpty()) {
                VertexConsumer fpBuffer = immediate.getBuffer(RenderLayers.weather(FOOTPRINT_TEXTURE, true));
                long curTime = System.currentTimeMillis();

                for (int i = 0; i < this.footprints.size(); i++) {
                    Footprint fp = this.footprints.get(i);
                    long age = curTime - fp.spawnTime;
                    if (age >= fp.lifetime) continue;

                    double dx = fp.x - camPos.x;
                    double dy = fp.y - camPos.y;
                    double dz = fp.z - camPos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > 36.0 * 36.0) continue;

                    float fade = 1.0f - (float) age / (float) fp.lifetime;
                    if (fade <= 0.02f) continue;

                    int alpha = Math.round(fade * 245.0f);

                    double rad = Math.toRadians(fp.yaw);
                    float fwdX = (float) -Math.sin(rad);
                    float fwdZ = (float) Math.cos(rad);
                    float rgtX = (float) Math.cos(rad);
                    float rgtZ = (float) Math.sin(rad);

                    float halfW = 0.15f;
                    float halfL = 0.28f;

                    float x0 = (float) dx - rgtX * halfW - fwdX * halfL;
                    float z0 = (float) dz - rgtZ * halfW - fwdZ * halfL;
                    float x1 = (float) dx + rgtX * halfW - fwdX * halfL;
                    float z1 = (float) dz + rgtZ * halfW - fwdZ * halfL;
                    float x2 = (float) dx + rgtX * halfW + fwdX * halfL;
                    float z2 = (float) dz + rgtZ * halfW + fwdZ * halfL;
                    float x3 = (float) dx - rgtX * halfW + fwdX * halfL;
                    float z3 = (float) dz - rgtZ * halfW + fwdZ * halfL;

                    float fy = (float) dy;
                    float u0 = fp.isLeft ? 0.0f : 1.0f;
                    float u1 = fp.isLeft ? 1.0f : 0.0f;

                    int fpLight = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(fp.x, fp.y, fp.z));

                    fpBuffer.vertex(x0, fy, z0).texture(u0, 1.0f).color(255, 255, 255, alpha).light(fpLight);
                    fpBuffer.vertex(x1, fy, z1).texture(u1, 1.0f).color(255, 255, 255, alpha).light(fpLight);
                    fpBuffer.vertex(x2, fy, z2).texture(u1, 0.0f).color(255, 255, 255, alpha).light(fpLight);
                    fpBuffer.vertex(x3, fy, z3).texture(u0, 0.0f).color(255, 255, 255, alpha).light(fpLight);
                }
            }
        }

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
    }

    private void renderWaterReflections(VertexConsumerProvider immediate, Vec3d camPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        BlockPos camBlock = BlockPos.ofFloored(camPos.x, camPos.y, camPos.z);
        int radius = 16;
        int minY = Math.max(mc.world.getBottomY(), camBlock.getY() - 6);
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight(), camBlock.getY() + 4);

        long timeMs = System.currentTimeMillis();
        float waveTime = (float) ((timeMs % 100000L) / 1000.0);

        Box searchBox = new Box(camBlock).expand(radius, 6, radius);
        List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, searchBox, LivingEntity::isAlive);

        VertexConsumer buf = immediate.getBuffer(RenderLayers.lightning());
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        BlockPos.Mutable upPos = new BlockPos.Mutable();

        for (int x = -radius; x <= radius; x++) {
            int bx = camBlock.getX() + x;
            for (int z = -radius; z <= radius; z++) {
                int bz = camBlock.getZ() + z;
                if (x * x + z * z > radius * radius) continue;

                for (int by = maxY; by >= minY; by--) {
                    mpos.set(bx, by, bz);
                    BlockState state = mc.world.getBlockState(mpos);
                    if (!state.isOf(Blocks.WATER)) continue;

                    upPos.set(bx, by + 1, bz);
                    BlockState upState = mc.world.getBlockState(upPos);
                    if (upState.isOf(Blocks.WATER)) continue;

                    float waterLevel = 0.88F;
                    float waterY = (float) (by + waterLevel - camPos.y);
                    float x0 = (float) (bx - camPos.x);
                    float x1 = x0 + 1.0F;
                    float z0 = (float) (bz - camPos.z);
                    float z1 = z0 + 1.0F;

                    float cx = (x0 + x1) * 0.5F;
                    float cz = (z0 + z1) * 0.5F;
                    float distSq = cx * cx + waterY * waterY + cz * cz;
                    float dist = (float) Math.sqrt(distSq);
                    if (dist < 0.1F) continue;

                    float cosTheta = Math.abs(waterY) / dist;
                    float fresnel = MathHelper.clamp(0.20F + 0.80F * (float) Math.pow(1.0F - cosTheta, 3.0), 0.0F, 1.0F);

                    float w0 = 0.015F * (float) Math.sin(bx * 0.7F + waveTime * 1.8F + bz * 0.5F);
                    float w1 = 0.015F * (float) Math.sin((bx + 1) * 0.7F + waveTime * 1.8F + bz * 0.5F);
                    float w2 = 0.015F * (float) Math.sin((bx + 1) * 0.7F + waveTime * 1.8F + (bz + 1) * 0.5F);
                    float w3 = 0.015F * (float) Math.sin(bx * 0.7F + waveTime * 1.8F + (bz + 1) * 0.5F);

                    int skyR = 120;
                    int skyG = 180;
                    int skyB = 240;
                    int sheenAlpha = Math.max(1, (int) (fresnel * 75.0F));

                    buf.vertex(x0, waterY + w0, z0).color(skyR, skyG, skyB, sheenAlpha);
                    buf.vertex(x1, waterY + w1, z0).color(skyR, skyG, skyB, sheenAlpha);
                    buf.vertex(x1, waterY + w2, z1).color(skyR, skyG, skyB, sheenAlpha);
                    buf.vertex(x0, waterY + w3, z1).color(skyR, skyG, skyB, sheenAlpha);

                    float sunDot = (cx * 0.4F + waterY * 0.8F + cz * 0.3F) / dist;
                    if (sunDot > 0.85F) {
                        float glint = (float) Math.pow((sunDot - 0.85F) / 0.15F, 16.0);
                        int glintAlpha = (int) (glint * 160.0F);
                        if (glintAlpha > 1) {
                            buf.vertex(x0, waterY + w0 + 0.002F, z0).color(255, 255, 230, glintAlpha);
                            buf.vertex(x1, waterY + w1 + 0.002F, z0).color(255, 255, 230, glintAlpha);
                            buf.vertex(x1, waterY + w2 + 0.002F, z1).color(255, 255, 230, glintAlpha);
                            buf.vertex(x0, waterY + w3 + 0.002F, z1).color(255, 255, 230, glintAlpha);
                        }
                    }

                    for (LivingEntity e : entities) {
                        double ex = e.getX() - camPos.x;
                        double ez = e.getZ() - camPos.z;
                        float ew = e.getWidth();

                        if (ex >= x0 - ew * 0.6 && ex <= x1 + ew * 0.6 && ez >= z0 - ew * 0.6 && ez <= z1 + ew * 0.6) {
                            double entityFeetY = e.getY();
                            double waterBlockFeetY = by + waterLevel;
                            double distAboveWater = entityFeetY - waterBlockFeetY;

                            if (distAboveWater >= -0.3 && distAboveWater <= 4.0) {
                                int reflAlpha = Math.max(1, (int) (fresnel * 160.0F * (1.0F - (float) distAboveWater / 5.0F)));

                                int er = 220, eg = 235, eb = 255;
                                if (e == mc.player) {
                                    er = 180; eg = 210; eb = 255;
                                }

                                float rw = ew * 0.45F;
                                float rx0 = (float) ex - rw;
                                float rx1 = (float) ex + rw;
                                float rz0 = (float) ez - rw;
                                float rz1 = (float) ez + rw;

                                buf.vertex(rx0, waterY + 0.005F, rz0).color(er, eg, eb, reflAlpha);
                                buf.vertex(rx1, waterY + 0.005F, rz0).color(er, eg, eb, reflAlpha);
                                buf.vertex(rx1, waterY + 0.005F, rz1).color(er, eg, eb, reflAlpha);
                                buf.vertex(rx0, waterY + 0.005F, rz1).color(er, eg, eb, reflAlpha);
                            }
                        }
                    }

                    break;
                }
            }
        }
    }

    private static class Footprint {
        final double x, y, z;
        final float yaw;
        final boolean isLeft;
        final long spawnTime;
        final long lifetime;

        Footprint(double x, double y, double z, float yaw, boolean isLeft, long spawnTime, long lifetime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.isLeft = isLeft;
            this.spawnTime = spawnTime;
            this.lifetime = lifetime;
        }
    }

    private static class SnowVertex {
        final float x, y, z;
        final float u, v;
        final int r, g, b, a;
        final int light;

        SnowVertex(float x, float y, float z, float u, float v, int r, int g, int b, int a, int light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.light = light;
        }
    }

    private static class SnowQuad {
        final SnowVertex v0, v1, v2, v3;

        SnowQuad(SnowVertex v0, SnowVertex v1, SnowVertex v2, SnowVertex v3) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }
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
