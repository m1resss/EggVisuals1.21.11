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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
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
                this.timeMode, this.timeOfDay,
                this.season, this.weather,
                this.particles, this.particleDensity, this.snowSpeed,
                this.smoothSnow, this.snowFootprints, this.autumnLeavesGround,
                this.customFog, this.fogColorMode, this.fogCustomColor,
                this.customSound, this.soundVolume
        );
        instance = this;
    }

    public static WorldTweaks getInstance() {
        return instance;
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
            return mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
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
                    double sideX = -Math.cos(yawRad) * 0.16 * sideSign;
                    double sideZ = -Math.sin(yawRad) * 0.16 * sideSign;

                    double stepX = px + sideX;
                    double stepZ = pz + sideZ;
                    double surfaceY = getSnowSurfaceY(mc, stepX, stepZ);

                    this.footprints.add(new Footprint(stepX, surfaceY + 0.003, stepZ, yaw, this.lastFootLeft, now, 25000L));

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
            int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
            if (!this.smoothSnow.get() || !"Зима".equals(this.season.get())) {
                return topY + 1.0;
            }
            double u = x - bx;
            double v = z - bz;
            float c00 = getSnowCornerHeight(mc, bx, bz, topY, 0, 0);
            float c10 = getSnowCornerHeight(mc, bx, bz, topY, 1, 0);
            float c11 = getSnowCornerHeight(mc, bx, bz, topY, 1, 1);
            float c01 = getSnowCornerHeight(mc, bx, bz, topY, 0, 1);
            double cornerH = (1.0 - u) * (1.0 - v) * c00 + u * (1.0 - v) * c10 + u * v * c11 + (1.0 - u) * v * c01;
            double dome = Math.sin(u * Math.PI) * Math.sin(v * Math.PI) * 0.08;
            return topY + 1.0 + cornerH + dome;
        } catch (Throwable ignored) {
            return 64.0;
        }
    }

    private float getSnowNoise(int x, int z) {
        double nx = x * 0.16;
        double nz = z * 0.16;
        double v1 = Math.sin(nx) * Math.cos(nz);
        double v2 = Math.sin((x + z) * 0.32) * 0.45;
        double v3 = Math.cos(x * 0.38 - z * 0.42) * 0.25;
        return (float) ((v1 + v2 + v3) * 0.5);
    }

    private float getSnowCornerHeight(MinecraftClient mc, int bx, int bz, int baseTopY, int cx, int cz) {
        int neighborX = bx + (cx == 1 ? 1 : -1);
        int neighborZ = bz + (cz == 1 ? 1 : -1);

        float sum = 0.0f;
        int count = 0;

        int[][] coords = new int[][]{
                {bx, bz},
                {neighborX, bz},
                {bx, neighborZ},
                {neighborX, neighborZ}
        };

        boolean hasCliffUp = false;
        boolean hasCliffDown = false;

        for (int[] c : coords) {
            int colX = c[0];
            int colZ = c[1];
            if (!mc.world.isChunkLoaded(colX >> 4, colZ >> 4)) continue;
            int colTopY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, colX, colZ) - 1;
            if (colTopY > baseTopY) {
                hasCliffUp = true;
            } else if (colTopY < baseTopY) {
                hasCliffDown = true;
            } else {
                sum += 0.18f + getSnowNoise(colX, colZ) * 0.10f;
                count++;
            }
        }

        if (hasCliffUp) {
            return 0.38f;
        }
        if (hasCliffDown && count == 1) {
            return 0.08f;
        }

        return count > 0 ? (sum / count) : 0.18f;
    }

    private boolean isDropOff(MinecraftClient mc, int x, int z, int baseY) {
        if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) return false;
        int nTopY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        return nTopY < baseY;
    }

    private void rebuildSnowMesh(MinecraftClient mc, int camBx, int camBy, int camBz) {
        this.cachedSnowMesh.clear();
        if (mc.world == null) return;

        int radius = 13;
        int radSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radSq) continue;
                int bx = camBx + dx;
                int bz = camBz + dz;
                if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
                if (topY < camBy - 10 || topY > camBy + 8) continue;

                BlockPos pos = new BlockPos(bx, topY, bz);
                BlockState state = mc.world.getBlockState(pos);
                if (!state.blocksMovement() || !state.getFluidState().isEmpty()) continue;

                BlockState above = mc.world.getBlockState(pos.up());
                if (above.blocksMovement() && above.getBlock() != Blocks.SNOW) continue;

                float centerThickness = 0.20f + getSnowNoise(bx, bz) * 0.12f;
                float cy = topY + 1.0f + centerThickness + 0.08f;

                float c00 = getSnowCornerHeight(mc, bx, bz, topY, 0, 0);
                float c10 = getSnowCornerHeight(mc, bx, bz, topY, 1, 0);
                float c11 = getSnowCornerHeight(mc, bx, bz, topY, 1, 1);
                float c01 = getSnowCornerHeight(mc, bx, bz, topY, 0, 1);

                float mNy = topY + 1.0f + (c00 + c10) * 0.5f + 0.04f;
                float mSy = topY + 1.0f + (c01 + c11) * 0.5f + 0.04f;
                float mWy = topY + 1.0f + (c00 + c01) * 0.5f + 0.04f;
                float mEy = topY + 1.0f + (c10 + c11) * 0.5f + 0.04f;

                SnowVertex pCenter = new SnowVertex(bx + 0.5f, cy, bz + 0.5f, 255, 255, 255, 255);
                SnowVertex p00     = new SnowVertex(bx, topY + 1.0f + c00, bz, 238, 244, 252, 255);
                SnowVertex p10     = new SnowVertex(bx + 1.0f, topY + 1.0f + c10, bz, 238, 244, 252, 255);
                SnowVertex p11     = new SnowVertex(bx + 1.0f, topY + 1.0f + c11, bz + 1.0f, 238, 244, 252, 255);
                SnowVertex p01     = new SnowVertex(bx, topY + 1.0f + c01, bz + 1.0f, 238, 244, 252, 255);

                SnowVertex mN = new SnowVertex(bx + 0.5f, mNy, bz, 246, 250, 255, 255);
                SnowVertex mS = new SnowVertex(bx + 0.5f, mSy, bz + 1.0f, 246, 250, 255, 255);
                SnowVertex mW = new SnowVertex(bx, mWy, bz + 0.5f, 246, 250, 255, 255);
                SnowVertex mE = new SnowVertex(bx + 1.0f, mEy, bz + 0.5f, 246, 250, 255, 255);

                this.cachedSnowMesh.add(new SnowQuad(p00, mN, pCenter, mW));
                this.cachedSnowMesh.add(new SnowQuad(mN, p10, mE, pCenter));
                this.cachedSnowMesh.add(new SnowQuad(pCenter, mE, p11, mS));
                this.cachedSnowMesh.add(new SnowQuad(mW, pCenter, mS, p01));

                float skirtDropY = topY + 0.94f;
                int sr = 205, sg = 218, sb = 234;

                if (isDropOff(mc, bx, bz - 1, topY)) {
                    SnowVertex b0 = new SnowVertex(bx, skirtDropY, bz, sr, sg, sb, 255);
                    SnowVertex b1 = new SnowVertex(bx + 0.5f, skirtDropY, bz, sr, sg, sb, 255);
                    SnowVertex b2 = new SnowVertex(bx + 1.0f, skirtDropY, bz, sr, sg, sb, 255);
                    this.cachedSnowMesh.add(new SnowQuad(p00, mN, b1, b0));
                    this.cachedSnowMesh.add(new SnowQuad(mN, p10, b2, b1));
                }
                if (isDropOff(mc, bx, bz + 1, topY)) {
                    SnowVertex b0 = new SnowVertex(bx, skirtDropY, bz + 1.0f, sr, sg, sb, 255);
                    SnowVertex b1 = new SnowVertex(bx + 0.5f, skirtDropY, bz + 1.0f, sr, sg, sb, 255);
                    SnowVertex b2 = new SnowVertex(bx + 1.0f, skirtDropY, bz + 1.0f, sr, sg, sb, 255);
                    this.cachedSnowMesh.add(new SnowQuad(p01, b0, b1, mS));
                    this.cachedSnowMesh.add(new SnowQuad(mS, b1, b2, p11));
                }
                if (isDropOff(mc, bx - 1, bz, topY)) {
                    SnowVertex b0 = new SnowVertex(bx, skirtDropY, bz, sr, sg, sb, 255);
                    SnowVertex b1 = new SnowVertex(bx, skirtDropY, bz + 0.5f, sr, sg, sb, 255);
                    SnowVertex b2 = new SnowVertex(bx, skirtDropY, bz + 1.0f, sr, sg, sb, 255);
                    this.cachedSnowMesh.add(new SnowQuad(p00, b0, b1, mW));
                    this.cachedSnowMesh.add(new SnowQuad(mW, b1, b2, p01));
                }
                if (isDropOff(mc, bx + 1, bz, topY)) {
                    SnowVertex b0 = new SnowVertex(bx + 1.0f, skirtDropY, bz, sr, sg, sb, 255);
                    SnowVertex b1 = new SnowVertex(bx + 1.0f, skirtDropY, bz + 0.5f, sr, sg, sb, 255);
                    SnowVertex b2 = new SnowVertex(bx + 1.0f, skirtDropY, bz + 1.0f, sr, sg, sb, 255);
                    this.cachedSnowMesh.add(new SnowQuad(p10, mE, b1, b0));
                    this.cachedSnowMesh.add(new SnowQuad(mE, p11, b2, b1));
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

        int radius = 14;
        int radSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radSq) continue;
                int bx = camBx + dx;
                int bz = camBz + dz;
                if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
                if (topY < camBy - 10 || topY > camBy + 8) continue;

                BlockPos pos = new BlockPos(bx, topY, bz);
                BlockState state = mc.world.getBlockState(pos);
                if (!state.blocksMovement() || !state.getFluidState().isEmpty()) continue;

                long seed = ((long) bx * 3129871L) ^ ((long) bz * 6128657L) ^ ((long) topY * 19349663L);
                Random rand = new Random(seed);

                if (rand.nextInt(100) > 35) continue;

                int count = 2 + rand.nextInt(3);
                double clusterCenterX = bx + 0.25 + rand.nextDouble() * 0.50;
                double clusterCenterZ = bz + 0.25 + rand.nextDouble() * 0.50;

                for (int i = 0; i < count; i++) {
                    double lx = clusterCenterX + (rand.nextDouble() - 0.5) * 0.38;
                    double lz = clusterCenterZ + (rand.nextDouble() - 0.5) * 0.38;
                    double ly = topY + 1.002 + i * 0.005;

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
                    VertexConsumer snowBuf = immediate.getBuffer(RenderLayers.debugQuads());
                    for (int i = 0; i < this.cachedSnowMesh.size(); i++) {
                        SnowQuad q = this.cachedSnowMesh.get(i);
                        snowBuf.vertex((float) (q.v0.x - camPos.x), (float) (q.v0.y - camPos.y), (float) (q.v0.z - camPos.z)).color(q.v0.r, q.v0.g, q.v0.b, q.v0.a);
                        snowBuf.vertex((float) (q.v1.x - camPos.x), (float) (q.v1.y - camPos.y), (float) (q.v1.z - camPos.z)).color(q.v1.r, q.v1.g, q.v1.b, q.v1.a);
                        snowBuf.vertex((float) (q.v2.x - camPos.x), (float) (q.v2.y - camPos.y), (float) (q.v2.z - camPos.z)).color(q.v2.r, q.v2.g, q.v2.b, q.v2.a);
                        snowBuf.vertex((float) (q.v3.x - camPos.x), (float) (q.v3.y - camPos.y), (float) (q.v3.z - camPos.z)).color(q.v3.r, q.v3.g, q.v3.b, q.v3.a);

                        snowBuf.vertex((float) (q.v3.x - camPos.x), (float) (q.v3.y - camPos.y), (float) (q.v3.z - camPos.z)).color(q.v3.r, q.v3.g, q.v3.b, q.v3.a);
                        snowBuf.vertex((float) (q.v2.x - camPos.x), (float) (q.v2.y - camPos.y), (float) (q.v2.z - camPos.z)).color(q.v2.r, q.v2.g, q.v2.b, q.v2.a);
                        snowBuf.vertex((float) (q.v1.x - camPos.x), (float) (q.v1.y - camPos.y), (float) (q.v1.z - camPos.z)).color(q.v1.r, q.v1.g, q.v1.b, q.v1.a);
                        snowBuf.vertex((float) (q.v0.x - camPos.x), (float) (q.v0.y - camPos.y), (float) (q.v0.z - camPos.z)).color(q.v0.r, q.v0.g, q.v0.b, q.v0.a);
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
                    if (distSq > 32.0 * 32.0) continue;

                    float fade = 1.0f - (float) age / (float) fp.lifetime;
                    if (fade <= 0.02f) continue;

                    int alpha = Math.round(fade * 230.0f);

                    double rad = Math.toRadians(fp.yaw);
                    float fwdX = (float) -Math.sin(rad);
                    float fwdZ = (float) Math.cos(rad);
                    float rgtX = (float) Math.cos(rad);
                    float rgtZ = (float) Math.sin(rad);

                    float halfW = 0.11f;
                    float halfL = 0.22f;

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

                    fpBuffer.vertex(x0, fy, z0).texture(u0, 1.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                    fpBuffer.vertex(x1, fy, z1).texture(u1, 1.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                    fpBuffer.vertex(x2, fy, z2).texture(u1, 0.0f).color(255, 255, 255, alpha).light(0x00F000F0);
                    fpBuffer.vertex(x3, fy, z3).texture(u0, 0.0f).color(255, 255, 255, alpha).light(0x00F000F0);
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
                    if (distSq > 28.0 * 28.0) continue;

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

                    leafBuffer.vertex(x0, y0, z0).texture(0.0f, 1.0f).color(r, g, b, 245).light(0x00F000F0);
                    leafBuffer.vertex(x1, y1, z1).texture(1.0f, 1.0f).color(r, g, b, 245).light(0x00F000F0);
                    leafBuffer.vertex(x2, y2, z2).texture(1.0f, 0.0f).color(r, g, b, 245).light(0x00F000F0);
                    leafBuffer.vertex(x3, y3, z3).texture(0.0f, 0.0f).color(r, g, b, 245).light(0x00F000F0);
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
        final int r, g, b, a;

        SnowVertex(float x, float y, float z, int r, int g, int b, int a) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
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
