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

    // Textures
    private static final Identifier[] SNOWFLAKE_TEXTURES = new Identifier[]{
            Identifier.of("egg", "textures/world/snowflake_1.png"),
            Identifier.of("egg", "textures/world/snowflake_2.png"),
            Identifier.of("egg", "textures/world/snowflake_3.png"),
            Identifier.of("egg", "textures/world/snowflake_4.png"),
            Identifier.of("egg", "textures/world/snowflake_5.png")
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

    // Settings
    private final ModeSetting timeMode = new ModeSetting("Время суток", "Кастом", "Режим времени", "Кастом", "Синхрон", "Выкл");
    private final SliderSetting timeOfDay = new SliderSetting("Час дня", 6000.0, 0.0, 24000.0, 100.0, "Время игрового мира (0..24000)");

    private final ModeSetting season = new ModeSetting("Время года", "Весна", "Сезон мира", "Зима", "Весна", "Лето", "Осень", "Выкл");
    private final ModeSetting weather = new ModeSetting("Погода", "Ясно", "Кастомная погода", "Дождь", "Гроза", "Ясно", "Выкл");

    private final BooleanSetting particles = new BooleanSetting("Партиклы", true, "3D планирующие частицы в воздухе");
    private final SliderSetting particleDensity = new SliderSetting("Кол-во частиц", 80.0, 20.0, 180.0, 10.0, "Плотность частиц");

    // Native Vanilla Fog Color (Lightweight, zero-FPS cost)
    private final BooleanSetting customFog = new BooleanSetting("Кастомный туман", false, "Атмосферный цвет тумана горизонта");
    private final ModeSetting fogColorMode = new ModeSetting("Цвет тумана", "По сезону", "Режим цвета тумана", "По сезону", "Кастом", "Ванилла");
    private final ColorSetting fogCustomColor = new ColorSetting("Цвет", 0xFF9FC5E8, "Цвет кастомного тумана");

    private final BooleanSetting customSound = new BooleanSetting("Звуки дождя/грозы", true, "Кастомный шум дождя и раскаты грома");
    private final SliderSetting soundVolume = new SliderSetting("Громкость звука", 0.65, 0.0, 1.0, 0.05, "Громкость MP3 звуков");

    // Dynamic State
    private final List<FallingParticle> fallingParticles = new ArrayList<>();
    private final List<RainStreak> rainStreaks = new ArrayList<>();
    private final Random random = new Random();

    private int thunderCooldown = 120;
    private long lastFrameNanos = 0L;
    private boolean wasAudioRaining = false;
    private String lastAppliedSeason = "";

    public WorldTweaks() {
        super("World Tweaks", "Кастомная погода, времена года и оптимизированные частицы", Category.MISC);
        addSettings(
                this.timeMode, this.timeOfDay,
                this.season, this.weather,
                this.particles, this.particleDensity,
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

        // Thunderstorm sound triggers
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

            // Fast ground collision check near player feet (zero VoxelShape allocations)
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
        p.maxAge = 240.0f + this.random.nextFloat() * 160.0f; // 12 - 20 seconds
        p.swayPhase = this.random.nextFloat() * 6.28f;
        p.roll = this.random.nextFloat() * 360.0f;
        p.pitch = this.random.nextFloat() * 360.0f;

        if ("Зима".equals(curSeason)) {
            p.texIdx = this.random.nextInt(SNOWFLAKE_TEXTURES.length);
            p.vy = -0.016f - this.random.nextFloat() * 0.016f;
            p.vx = (this.random.nextFloat() - 0.5f) * 0.008f;
            p.vz = (this.random.nextFloat() - 0.5f) * 0.008f;
            p.swayRadius = 0.014f + this.random.nextFloat() * 0.014f;
            p.rollSpeed = (this.random.nextFloat() - 0.5f) * 1.5f;
            p.pitchSpeed = (this.random.nextFloat() - 0.5f) * 1.2f;
            p.scale = 0.20f + this.random.nextFloat() * 0.14f;
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
        int streakCount = 140; // Lightweight, sleek cinematic rain
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

        // 1. Falling 3D Seasonal Particles (Batched per texture)
        if (this.particles.get() && !"Выкл".equals(this.season.get())) {
            updateParticlesSmooth(camPos, dt, mc);
            renderParticles3D(consumers, camPos, mc);
        }

        // 2. Cinema Rain in 3D (Single draw call)
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

        // Batch rendering by texture index: exactly texArray.length draw calls total!
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    // Helper classes
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
}
