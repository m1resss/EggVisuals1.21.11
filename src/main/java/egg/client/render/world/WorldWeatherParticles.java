package egg.client.render.world;

import egg.client.module.render.WorldTweaks;
import egg.client.module.render.WorldTweaks.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class WorldWeatherParticles {
    private static final Identifier RAIN = texture("drop");
    private static final Identifier FLAKE = texture("flake");
    private static final Identifier RIPPLE = texture("ripple");
    private static final Identifier PRINT = texture("print");
    private final WeatherSurface surface = new WeatherSurface();
    private final WeatherSnowMesh snow = new WeatherSnowMesh();

    private final Random random = new Random();
    private final List<Particle> rain = new ArrayList<>();
    private final List<Particle> flakes = new ArrayList<>();
    private final List<Decal> ripples = new ArrayList<>();
    private final List<Decal> prints = new ArrayList<>();
    private final BlockPos.Mutable lightPos = new BlockPos.Mutable();
    private final Vector3f right = new Vector3f();
    private final Vector3f facingUp = new Vector3f();
    private final Vector3f horizontal = new Vector3f();
    private final Vector3f vertical = new Vector3f();
    private long lastFrame;
    private double animationTime;
    private final Map<Integer, Step> lastSteps = new HashMap<>();
    private WeatherLoop rainSound;
    private WeatherLoop indoorSound;
    private WeatherLoop windSound;

    public void reset() {
        snow.close();
        rain.clear();
        flakes.clear();
        ripples.clear();
        prints.clear();
        lastFrame = 0;
        lastSteps.clear();
        stopSound();
    }

    public boolean hasSound() { return rainSound != null || indoorSound != null || windSound != null; }

    private void stopSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (rainSound != null) client.getSoundManager().stop(rainSound);
        if (indoorSound != null) client.getSoundManager().stop(indoorSound);
        if (windSound != null) client.getSoundManager().stop(windSound);
        rainSound = indoorSound = windSound = null;
    }

    public void tickSounds(MinecraftClient client, Frame frame) {
        Camera camera = client.gameRenderer.getCamera();
        BlockPos pos = BlockPos.ofFloored(camera.getCameraPos());
        boolean outside = client.world.isSkyVisible(pos) && camera.getSubmersionType() == CameraSubmersionType.NONE;
        float rainfall = Math.min(1, frame.rain());
        rainSound = sound(client, rainSound, SoundEvents.WEATHER_RAIN, outside ? rainfall * 0.65f : 0);
        indoorSound = sound(client, indoorSound, SoundEvents.WEATHER_RAIN_ABOVE, outside ? 0 : rainfall * 0.28f);
        float intensity = frame.snow() * 0.45f;
        windSound = sound(client, windSound, SoundEvents.ENTITY_BREEZE_IDLE_AIR, intensity * (outside ? 0.24f : 0.04f));
    }

    private static WeatherLoop sound(MinecraftClient client, WeatherLoop loop, SoundEvent event, float volume) {
        if (volume > 0.005f && (loop == null || !client.getSoundManager().isPlaying(loop))) {
            loop = new WeatherLoop(event, volume);
            client.getSoundManager().play(loop);
        }
        if (loop == null) return null;
        loop.target = volume;
        if (volume == 0 && loop.getVolume() < 0.006f) {
            client.getSoundManager().stop(loop);
            return null;
        }
        return loop;
    }

    public void thunder(MinecraftClient client, float strength) {
        Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
        client.world.playSoundClient(eye.x + 22, eye.y + 18, eye.z - 28, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER, strength * 0.65f, 0.9f, false);
    }

    public void render(MinecraftClient client, WorldTweaks tweaks, VertexConsumerProvider buffers, Camera camera) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Frame frame = tweaks.frame();
        float groundSnow = frame.frost();

        long now = System.nanoTime();
        float dt = lastFrame == 0 || client.isPaused() ? 0 : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        animationTime += dt;
        Vec3d eye = camera.getCameraPos();
        surface.update(client.world, eye);
        right.set(1, 0, 0).rotate(camera.getRotation());
        facingUp.set(0, 1, 0).rotate(camera.getRotation());
        float windX = frame.windX() * 14f;
        float windZ = frame.windZ() * 14f;
        if (groundSnow > 0.005f) {
            snow.draw(surface, eye, groundSnow, tweaks.snowDepth(), frame.reflection() / 0.14f, WorldWeatherEffects.sky(tweaks));
        }
        int tint = tint(frame.precipitationTint());

        drawParticles(client, buffers, eye, rain, RAIN, frame.rain(), 560, 28, 17, windX, windZ, tint, dt, true, frame.splash());
        drawParticles(client, buffers, eye, flakes, FLAKE, frame.snow(), 540, 28, 1.4f, windX, windZ, tint, dt, false, 0);
        if (groundSnow > 0.35f) addFootprints(client);
        else lastSteps.clear();
        renderDecals(client, buffers, eye, ripples, RIPPLE, 0xD4E3F2, dt, 1);
        renderDecals(client, buffers, eye, prints, PRINT, 0x8498AA, dt, groundSnow);
    }

    private void drawParticles(MinecraftClient client, VertexConsumerProvider buffers, Vec3d eye,
                               List<Particle> particles, Identifier texture, float amount, int density,
                               int radius, float fallSpeed, float windX, float windZ, int tint,
                               float dt, boolean rainDrop, float splash) {
        if (amount < 0.002f) {
            particles.clear();
            return;
        }
        int count = Math.min(1250, (int) Math.ceil(density * Math.max(0.12f, amount)));
        while (particles.size() < count) particles.add(spawn(eye, radius, true));
        if (particles.size() > count) particles.subList(count, particles.size()).clear();

        VertexConsumer vertices = buffers.getBuffer(RenderLayers.weather(texture, true));
        float windGust = 0.78f + 0.28f * MathHelper.sin((float) animationTime * 0.52f);
        for (Particle particle : particles) {
            float speed = fallSpeed * (0.62f + particle.phase * 0.65f);
            float sway = rainDrop ? 0 : MathHelper.sin((float) animationTime * 1.4f + particle.phase * 8);
            particle.x += (windX * windGust + sway * 0.42f) * dt;
            particle.y -= speed * dt;
            particle.z += (windZ * windGust + sway * 0.29f) * dt;
            float x = (float) (particle.x - eye.x);
            float y = (float) (particle.y - eye.y);
            float z = (float) (particle.z - eye.z);
            float squaredDistance = x * x + y * y + z * z;
            int top = surface.height(MathHelper.floor(particle.x), MathHelper.floor(particle.z));
            if (y < -radius * 0.6f || squaredDistance > radius * radius * 1.6f || particle.y <= top) {
                if (rainDrop && top != Short.MIN_VALUE && particle.y <= top && squaredDistance < 225
                        && surface.solid(MathHelper.floor(particle.x), MathHelper.floor(particle.z))
                        && random.nextFloat() < splash * 0.44f && ripples.size() < 100) {
                    ripples.add(new Decal(particle.x, top + 0.013, particle.z, 0.15f + random.nextFloat() * 0.18f,
                            0, 0.55f, random.nextBoolean()));
                }
                particle.set(spawn(eye, radius, false));
                continue;
            }

            float distance = MathHelper.sqrt(squaredDistance);
            float fade = WeatherProfile.smooth((distance - 0.3f) / 1.4f)
                    * (1 - WeatherProfile.smooth((distance - radius * 0.65f) / (radius * 0.35f)));
            float opacity = Math.min(1, amount * 2.2f) * fade;
            if (opacity < 0.008f) continue;

            float halfWidth = rainDrop ? 0.021f + particle.phase * 0.011f
                    : 0.042f + particle.phase * 0.052f;
            if (rainDrop) {
                horizontal.set(right).mul(halfWidth);
                vertical.set(-windX * 0.032f, 0.38f + particle.phase * 0.3f, -windZ * 0.032f);
            } else {
                float angle = (float) animationTime * (particle.phase - 0.5f) * 2 + particle.phase * MathHelper.TAU;
                float cosine = MathHelper.cos(angle) * halfWidth;
                float sine = MathHelper.sin(angle) * halfWidth;
                horizontal.set(right).mul(cosine).fma(sine, facingUp);
                vertical.set(facingUp).mul(cosine).fma(-sine, right);
            }
            int light = light(client, particle.x, particle.y, particle.z);
            quad(vertices, x, y, z, horizontal, vertical, tint, opacity * (rainDrop ? 0.64f : 0.9f), light, false);
        }
    }

    private Particle spawn(Vec3d eye, int radius, boolean anywhere) {
        double x = eye.x + (random.nextDouble() * 2 - 1) * radius;
        double z = eye.z + (random.nextDouble() * 2 - 1) * radius;
        int top = surface.height(MathHelper.floor(x), MathHelper.floor(z));
        double minY = Math.max(eye.y - radius * 0.45, top + 0.2);
        double maxY = Math.max(minY + 0.3, eye.y + radius * 0.68);
        double y = anywhere ? MathHelper.lerp(random.nextDouble(), minY, maxY) : maxY;
        return new Particle(x, y, z, random.nextFloat());
    }

    private void addFootprints(MinecraftClient client) {
        var players = client.world.getPlayers();
        if (prints.size() > 160) prints.subList(0, prints.size() - 160).clear();
        for (var player : players) {
            BlockPos foot = player.getBlockPos();
            int id = player.getId();
            Step previous = lastSteps.get(id);
            if (!player.isOnGround() || (previous != null && foot.equals(previous.pos()))) continue;
            boolean left = previous == null || !previous.left();
            lastSteps.put(id, new Step(foot, left));
            if (!surface.solid(foot.getX(), foot.getZ()) || surface.height(foot.getX(), foot.getZ()) != foot.getY()) continue;
            float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            float side = left ? -0.14f : 0.14f;
            prints.add(new Decal(player.getX() + MathHelper.cos(yaw) * side, foot.getY() + 0.057,
                    player.getZ() + MathHelper.sin(yaw) * side, 0.13f, yaw, 14, left));
        }
        lastSteps.keySet().removeIf(id -> client.world.getEntityById(id) == null);
    }

    private record Step(BlockPos pos, boolean left) {
    }

    private static int tint(Vector4f color) {
        return (int) (color.x * 255) << 16 | (int) (color.y * 255) << 8 | (int) (color.z * 255);
    }

    private void renderDecals(MinecraftClient client, VertexConsumerProvider buffers, Vec3d eye, List<Decal> decals,
                              Identifier texture, int tint, float dt, float amount) {
        if (decals.isEmpty()) return;
        VertexConsumer vertices = buffers.getBuffer(RenderLayers.weather(texture, true));
        for (Iterator<Decal> it = decals.iterator(); it.hasNext();) {
            Decal decal = it.next();
            decal.age += dt;
            if (decal.age >= decal.life) {
                it.remove();
                continue;
            }
            float progress = decal.age / decal.life;
            boolean print = texture == PRINT;
            float alpha = (1 - progress * progress) * amount * (print ? 0.52f : 0.7f);
            float size = decal.size * (print ? 1 : 0.3f + progress);
            floorAxes(decal.angle, size, print ? size * 1.5f : size);
            quad(vertices, (float) (decal.x - eye.x), (float) (decal.y - eye.y), (float) (decal.z - eye.z),
                    horizontal, vertical, tint, alpha, light(client, decal.x, decal.y, decal.z), decal.mirror);
        }
    }

    private void floorAxes(float angle, float width, float length) {
        float sine = MathHelper.sin(angle);
        float cosine = MathHelper.cos(angle);
        horizontal.set(cosine * width, 0, sine * width);
        vertical.set(sine * length, 0, -cosine * length);
    }

    private int light(MinecraftClient client, double x, double y, double z) {
        return WorldRenderer.getLightmapCoordinates(client.world, lightPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
    }

    private static void quad(VertexConsumer vertices, float x, float y, float z, Vector3f right, Vector3f up,
                             int tint, float opacity, int light, boolean mirror) {
        int color = MathHelper.clamp(Math.round(opacity * 255), 0, 255) << 24 | tint & 0xFFFFFF;
        float left = mirror ? 1 : 0;
        float end = 1 - left;
        vertices.vertex(x - right.x - up.x, y - right.y - up.y, z - right.z - up.z).texture(left, 1).color(color).light(light);
        vertices.vertex(x + right.x - up.x, y + right.y - up.y, z + right.z - up.z).texture(end, 1).color(color).light(light);
        vertices.vertex(x + right.x + up.x, y + right.y + up.y, z + right.z + up.z).texture(end, 0).color(color).light(light);
        vertices.vertex(x - right.x + up.x, y - right.y + up.y, z - right.z + up.z).texture(left, 0).color(color).light(light);
    }

    private static Identifier texture(String name) {
        return Identifier.of("egg", "textures/world/weather/" + name + ".png");
    }

    private static final class Particle {
        double x;
        double y;
        double z;
        float phase;

        Particle(double x, double y, double z, float phase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.phase = phase;
        }

        void set(Particle particle) {
            x = particle.x;
            y = particle.y;
            z = particle.z;
            phase = particle.phase;
        }
    }

    private static final class Decal {
        final double x;
        final double y;
        final double z;
        final float size;
        final float angle;
        final float life;
        final boolean mirror;
        float age;

        Decal(double x, double y, double z, float size, float angle, float life, boolean mirror) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.angle = angle;
            this.life = life;
            this.mirror = mirror;
        }
    }

    private static final class WeatherLoop extends MovingSoundInstance {
        float target;

        WeatherLoop(SoundEvent event, float volume) {
            super(event, SoundCategory.WEATHER, SoundInstance.createRandom());
            target = volume;
            this.volume = Math.max(0.01f, volume);
            repeat = true;
            repeatDelay = 0;
            relative = true;
            attenuationType = AttenuationType.NONE;
        }

        @Override
        public void tick() {
            volume = MathHelper.lerp(0.12f, volume, target);
        }
    }
}
