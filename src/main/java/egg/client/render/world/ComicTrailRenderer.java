package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ComicTrailRenderer {
    private static final double SPACING = 0.48;
    private static final int MAX_PUFFS = 25;
    private static final Comparator<Sprite> BACK_TO_FRONT = Comparator.comparingDouble(Sprite::depth).reversed();

    private final Map<UUID, Emitter> emitters = new HashMap<>();
    private final List<Sprite> sprites = new ArrayList<>();
    private BufferAllocator allocator;

    public void update(UUID player, Vec3d position, long now, int color, float duration) {
        Emitter emitter = emitters.computeIfAbsent(player, ignored -> new Emitter(position));
        emitter.lastFrame = now;
        emitter.puffs.removeIf(puff -> age(puff, now) >= 1);

        Vec3d movement = position.subtract(emitter.position);
        double travel = movement.length();
        if (travel < 0.001) return;

        Vec3d previous = emitter.position;
        emitter.position = position;
        if (travel > 3) {
            emitter.remaining = SPACING;
            return;
        }

        Vec3d forward = movement.multiply(1 / travel);
        Vec3d horizontal = new Vec3d(forward.x, 0, forward.z).normalize();
        Vec3d side = new Vec3d(-horizontal.z, 0, horizontal.x);
        double distance = emitter.remaining;
        while (distance <= travel) {
            Vec3d point = previous.add(forward.multiply(distance)).subtract(horizontal.multiply(0.43));
            addPuff(emitter, point, side, now, color, duration);
            distance += SPACING;
        }
        emitter.remaining = distance - travel;
    }

    private static void addPuff(Emitter emitter, Vec3d point, Vec3d side, long now, int color, float duration) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float lateral = (float) random.nextDouble(-0.115, 0.115);
        float lift = (float) random.nextDouble(0.19, 0.36);
        float scale = (float) random.nextDouble(0.87, 1.23);
        float angle = (float) random.nextDouble(-0.25, 0.25);
        point = point.add(side.multiply(lateral)).add(0, lift, 0);
        if (emitter.puffs.size() >= MAX_PUFFS) emitter.puffs.removeFirst();
        emitter.puffs.add(new Puff(point, side, now, duration * scale, scale, angle, lateral, color, emitter.nextShape));
        emitter.nextShape = (emitter.nextShape + 1) % 3;
    }

    public void render(Camera camera, Vec3d camPos, long now, float scale, float drift) {
        sprites.clear();
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera.getRotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera.getRotation());
        Vector3f forward = new Vector3f(0, 0, -1).rotate(camera.getRotation());
        for (Emitter emitter : emitters.values()) {
            if (emitter.lastFrame != now) continue;
            for (Puff puff : emitter.puffs) {
                float age = age(puff, now);
                float alpha = smooth(age / 0.12f) * (1 - smooth((age - 0.57f) / 0.43f));
                if (alpha <= 0.015f) continue;
                float rise = 0.32f * age;
                Vec3d position = puff.position.subtract(camPos).add(0, rise, 0)
                        .add(puff.side.multiply(puff.lateral * age * drift));
                double depth = position.x * forward.x + position.y * forward.y + position.z * forward.z;
                if (depth <= 0.05) continue;
                float size = puff.scale * scale * (0.29f + 0.29f * smooth(age));
                int color = ColorUtil.withAlphaFloat(puff.color, alpha);
                sprites.add(new Sprite(position, depth, size, puff.angle + puff.lateral * age, color, puff.variant));
            }
        }
        if (sprites.isEmpty()) return;
        sprites.sort(BACK_TO_FRONT);

        if (allocator == null) allocator = new BufferAllocator(16384);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (Sprite sprite : sprites) emitSprite(mesh, sprite, right, up);
            MeshDrawUtil.drawLabeled(Pipelines.TRAIL_COMIC, mesh.end(), "egg comic smoke");
        } finally {
            allocator.reset();
            sprites.clear();
        }
    }

    private static void emitSprite(BufferBuilder mesh, Sprite sprite, Vector3f right, Vector3f up) {
        float sin = (float) Math.sin(sprite.angle);
        float cos = (float) Math.cos(sprite.angle);
        Vec3d x = new Vec3d(right.x * cos + up.x * sin, right.y * cos + up.y * sin, right.z * cos + up.z * sin)
                .multiply(sprite.size * 1.23);
        Vec3d y = new Vec3d(up.x * cos - right.x * sin, up.y * cos - right.y * sin, up.z * cos - right.z * sin)
                .multiply(sprite.size);
        Vec3d center = sprite.position;
        int u = sprite.variant * 2;
        vertex(mesh, center.subtract(x).subtract(y), u, 0, sprite.color);
        vertex(mesh, center.add(x).subtract(y), u + 1, 0, sprite.color);
        vertex(mesh, center.add(x).add(y), u + 1, 1, sprite.color);
        vertex(mesh, center.subtract(x).add(y), u, 1, sprite.color);
    }

    private static void vertex(BufferBuilder mesh, Vec3d world, float u, float v, int color) {
        mesh.vertex((float) world.x, (float) world.y, (float) world.z).texture(u, v).color(color);
    }

    private static float age(Puff puff, long now) {
        return (now - puff.started) * 1.0e-9f / puff.duration;
    }

    private static float smooth(float input) {
        float t = Math.clamp(input, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public void retainPlayers(Set<UUID> players) {
        emitters.keySet().retainAll(players);
    }

    public void clear() {
        emitters.clear();
        sprites.clear();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static final class Emitter {
        final List<Puff> puffs = new ArrayList<>();
        Vec3d position;
        double remaining = SPACING;
        long lastFrame;
        int nextShape;

        Emitter(Vec3d position) {
            this.position = position;
        }
    }

    private record Puff(Vec3d position, Vec3d side, long started, float duration, float scale, float angle,
                        float lateral, int color, int variant) {}

    private record Sprite(Vec3d position, double depth, float size, float angle, int color, int variant) {}
}
