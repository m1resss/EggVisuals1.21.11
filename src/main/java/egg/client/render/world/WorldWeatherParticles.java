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
import net.minecraft.client.sound.PositionedSoundInstance;
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
    private static final SpriteUv FULL_SPRITE = new SpriteUv(0, 0, 1, 1);
    private static final SpriteUv[] FLAKE_SPRITES = {flakeSprite(0, 0), flakeSprite(1, 0), flakeSprite(0, 1), flakeSprite(1, 1)};
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
    private SoundInstance rainSound;
    private long nextRainSound;

    public void reset() {
        snow.close();
        rain.clear();
        flakes.clear();
        ripples.clear();
        prints.clear();
        lastFrame = 0;
        lastSteps.clear();
        stopRain(MinecraftClient.getInstance());
    }

    private void stopRain(MinecraftClient client) {
        if (rainSound != null) client.getSoundManager().stop(rainSound);
        rainSound = null;
        nextRainSound = 0;
    }

    public void tickRainAudio(MinecraftClient client, float rainfall, long now) {
        Camera camera = client.gameRenderer.getCamera();
        if (rainfall < 0.05f || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            stopRain(client);
            return;
        }
        if (client.isPaused() || now < nextRainSound
                || (rainSound != null && client.getSoundManager().isPlaying(rainSound))) return;

        Vec3d eye = camera.getCameraPos();
        boolean outside = client.world.isSkyVisible(BlockPos.ofFloored(eye));
        SoundEvent event = outside ? SoundEvents.WEATHER_RAIN : SoundEvents.WEATHER_RAIN_ABOVE;
        float volume = Math.min(1, rainfall) * (outside ? 0.58f : 0.27f);
        float pitch = 0.9f + random.nextFloat() * 0.2f;
        rainSound = new PositionedSoundInstance(event, SoundCategory.WEATHER, volume, pitch, SoundInstance.createRandom(),
                eye.x + random.nextDouble(-2, 2), eye.y + 1, eye.z + random.nextDouble(-2, 2));
        client.getSoundManager().play(rainSound);
        nextRainSound = now + 850 + random.nextInt(901);
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
        renderDecals(client, buffers, eye, ripples, RIPPLE, 0xD4E3F2, dt, 1, 0);
        renderDecals(client, buffers, eye, prints, PRINT, 0x8498AA, dt, groundSnow, tweaks.snowDepth());
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
            SpriteUv sprite = rainDrop ? FULL_SPRITE : FLAKE_SPRITES[particle.sprite];
            quad(vertices, x, y, z, horizontal, vertical, tint, opacity * (rainDrop ? 0.64f : 0.9f), light, false, sprite);
        }
    }

    private Particle spawn(Vec3d eye, int radius, boolean anywhere) {
        double x = eye.x + (random.nextDouble() * 2 - 1) * radius;
        double z = eye.z + (random.nextDouble() * 2 - 1) * radius;
        int top = surface.height(MathHelper.floor(x), MathHelper.floor(z));
        double minY = Math.max(eye.y - radius * 0.45, top + 0.2);
        double maxY = Math.max(minY + 0.3, eye.y + radius * 0.68);
        double y = anywhere ? MathHelper.lerp(random.nextDouble(), minY, maxY) : maxY;
        return new Particle(x, y, z, random.nextFloat(), random.nextInt(FLAKE_SPRITES.length));
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
            prints.add(new Decal(player.getX() + MathHelper.cos(yaw) * side, foot.getY(),
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
                              Identifier texture, int tint, float dt, float amount, float snowDepth) {
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
            int light = light(client, decal.x, decal.y, decal.z);
            if (print) {
                footprint(vertices, eye, decal, tint, alpha, light, WeatherSnowMesh.thickness(amount, snowDepth));
            } else {
                quad(vertices, (float) (decal.x - eye.x), (float) (decal.y - eye.y), (float) (decal.z - eye.z),
                        horizontal, vertical, tint, alpha, light, decal.mirror, FULL_SPRITE);
            }
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

    private void footprint(VertexConsumer vertices, Vec3d eye, Decal decal, int tint, float opacity, int light, float thickness) {
        double x0 = decal.x - horizontal.x - vertical.x;
        double z0 = decal.z - horizontal.z - vertical.z;
        double x1 = decal.x + horizontal.x - vertical.x;
        double z1 = decal.z + horizontal.z - vertical.z;
        double x2 = decal.x + horizontal.x + vertical.x;
        double z2 = decal.z + horizontal.z + vertical.z;
        double x3 = decal.x - horizontal.x + vertical.x;
        double z3 = decal.z - horizontal.z + vertical.z;
        int baseY = (int) decal.y;
        if (!surface.supportsSnow(x0, z0, baseY) || !surface.supportsSnow(x1, z1, baseY)
                || !surface.supportsSnow(x2, z2, baseY) || !surface.supportsSnow(x3, z3, baseY)) return;

        int color = withOpacity(tint, opacity);
        float left = decal.mirror ? 1 : 0;
        float right = 1 - left;
        footprintVertex(vertices, eye, x0, baseY, z0, thickness, left, 1, color, light);
        footprintVertex(vertices, eye, x1, baseY, z1, thickness, right, 1, color, light);
        footprintVertex(vertices, eye, x2, baseY, z2, thickness, right, 0, color, light);
        footprintVertex(vertices, eye, x3, baseY, z3, thickness, left, 0, color, light);
    }

    private static void footprintVertex(VertexConsumer vertices, Vec3d eye, double x, int y, double z, float thickness,
                                        float u, float v, int color, int light) {
        float relativeY = (float) (y - eye.y) + WeatherSnowMesh.surfaceOffset(x, z, thickness) + 0.006f;
        vertices.vertex((float) (x - eye.x), relativeY, (float) (z - eye.z)).texture(u, v).color(color).light(light);
    }

    private static void quad(VertexConsumer vertices, float x, float y, float z, Vector3f right, Vector3f up,
                             int tint, float opacity, int light, boolean mirror, SpriteUv sprite) {
        int color = withOpacity(tint, opacity);
        float left = mirror ? sprite.maxU : sprite.minU;
        float end = mirror ? sprite.minU : sprite.maxU;
        vertices.vertex(x - right.x - up.x, y - right.y - up.y, z - right.z - up.z).texture(left, sprite.maxV).color(color).light(light);
        vertices.vertex(x + right.x - up.x, y + right.y - up.y, z + right.z - up.z).texture(end, sprite.maxV).color(color).light(light);
        vertices.vertex(x + right.x + up.x, y + right.y + up.y, z + right.z + up.z).texture(end, sprite.minV).color(color).light(light);
        vertices.vertex(x - right.x + up.x, y - right.y + up.y, z - right.z + up.z).texture(left, sprite.minV).color(color).light(light);
    }

    private static int withOpacity(int tint, float opacity) {
        return MathHelper.clamp(Math.round(opacity * 255), 0, 255) << 24 | tint & 0xFFFFFF;
    }

    private static SpriteUv flakeSprite(int column, int row) {
        float halfTexel = 0.5f / 64;
        float u = column * 0.5f;
        float v = row * 0.5f;
        return new SpriteUv(u + halfTexel, v + halfTexel, u + 0.5f - halfTexel, v + 0.5f - halfTexel);
    }

    private record SpriteUv(float minU, float minV, float maxU, float maxV) {
    }

    private static Identifier texture(String name) {
        return Identifier.of("egg", "textures/world/weather/" + name + ".png");
    }

    private static final class Particle {
        double x;
        double y;
        double z;
        float phase;
        int sprite;

        Particle(double x, double y, double z, float phase, int sprite) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.phase = phase;
            this.sprite = sprite;
        }

        void set(Particle particle) {
            x = particle.x;
            y = particle.y;
            z = particle.z;
            phase = particle.phase;
            sprite = particle.sprite;
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
}
