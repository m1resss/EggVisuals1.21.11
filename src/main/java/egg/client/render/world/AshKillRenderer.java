package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AshKillRenderer {
    private static final float BURN_TIME = 1.12f;
    private static final float MODEL_END = 1.92f;
    private static final float PILE_READY = 2.32f;
    private static final float HOLD_TIME = 3.0f;
    private static final float FADE_TIME = 0.64f;
    private static final float END_TIME = PILE_READY + HOLD_TIME + FADE_TIME;
    private static final int MAX_EFFECTS = 8;

    private final KillModelPass modelPass;
    private final List<Burn> effects = new ArrayList<>();
    private final Random random = new Random();
    private final Vector3f cameraRight = new Vector3f();
    private final Vector3f cameraUp = new Vector3f();
    private BufferAllocator allocator;
    private ClientWorld world;

    public AshKillRenderer(KillModelPass modelPass) {
        this.modelPass = modelPass;
    }

    public boolean spawn(MinecraftClient client, Vec3d origin, double height, double width, EntityRenderState model, boolean sound) {
        if (client.world == null || client.player == null) return false;
        if (world != client.world) {
            clear();
            world = client.world;
        }

        float h = (float) Math.clamp(height, 0.75, 3);
        float w = (float) Math.clamp(width, 0.40, 1.65);
        Vec3d floor = findFloor(client, origin.x, origin.y + 0.25, origin.z);
        Vec3d pile = floor == null ? null : floor.add(0, 0.013, 0);
        float pileRadius = pile == null ? 0 : supportedRadius(client, floor, w * 1.4f);
        List<Coal> coals = new ArrayList<>(86);
        for (int i = 0; i < 86; i++) coals.add(makeCoal(client, origin, h, w, i >= 58));

        Flame[] flames = new Flame[16];
        for (int i = 0; i < flames.length; i++) {
            float y = i < 5 ? 0.07f * random.nextFloat() : 0.14f + 0.77f * (i - 5) / (flames.length - 6f);
            double angle = i * 2.399963 + random.nextDouble() * 0.42;
            float radius = w * (0.39f + random.nextFloat() * 0.37f);
            flames[i] = new Flame(Math.cos(angle) * radius, y, Math.sin(angle) * radius,
                    0.34f + random.nextFloat() * 0.27f, 0.10f + random.nextFloat() * 0.10f, random.nextFloat() * 9);
        }

        if (effects.size() >= MAX_EFFECTS) effects.removeFirst();
        effects.add(new Burn(origin, pile, h, pileRadius, model, flames, coals, System.nanoTime()));
        if (sound) {
            world.playSoundClient(origin.x, origin.y, origin.z, SoundEvents.ITEM_FIRECHARGE_USE,
                    SoundCategory.PLAYERS, 0.45f, 1.08f, false);
        }
        return true;
    }

    private Coal makeCoal(MinecraftClient client, Vec3d origin, float h, float w, boolean fine) {
        float heightFraction = random.nextFloat();
        double angle = random.nextDouble() * Math.TAU;
        double reach = w * (0.17 + random.nextDouble() * (fine ? 1.78 : 1.12));
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double x = origin.x + dx * reach;
        double z = origin.z + dz * reach;
        Vec3d from = origin.add(dx * w * 0.35, h * heightFraction, dz * w * 0.35);
        Vec3d floor = findFloor(client, x, origin.y + 0.52, z);
        double groundY = floor == null ? origin.y - 2.1 : floor.y + 0.014;
        Vec3d landing = new Vec3d(x, groundY, z);
        float birth = 1.14f + (1 - heightFraction) * 0.59f + random.nextFloat() * 0.10f;
        float landAt = 1.99f + random.nextFloat() * (PILE_READY - 1.99f);
        float size = fine ? 0.008f + random.nextFloat() * 0.028f : 0.025f + random.nextFloat() * 0.057f;
        int tone = random.nextInt(30);
        return new Coal(from, landing, floor != null, birth, landAt, size, angle, 0.12f + random.nextFloat() * 0.36f,
                tone, random.nextFloat() * 6.28f, random.nextFloat() < (fine ? 0.80f : 0.61f));
    }

    private static Vec3d findFloor(MinecraftClient client, double x, double y, double z) {
        Vec3d start = new Vec3d(x, y, z);
        var hit = client.world.raycast(new RaycastContext(start, start.add(0, -24, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : null;
    }

    private static float supportedRadius(MinecraftClient client, Vec3d floor, float radius) {
        for (; radius >= 0.16f; radius *= 0.65f) {
            boolean supported = true;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                Vec3d sample = findFloor(client, floor.x + radius * Math.cos(angle),
                        floor.y + 0.24, floor.z + radius * Math.sin(angle));
                if (sample == null || Math.abs(sample.y - floor.y) > 0.04) {
                    supported = false;
                    break;
                }
            }
            if (supported) return radius;
        }
        return 0;
    }

    public void render(MinecraftClient client, MatrixStack matrices, Camera camera, Vec3d camPos, boolean sound) {
        if (world != client.world) {
            clear();
            return;
        }
        long now = System.nanoTime();
        effects.removeIf(effect -> effect.age(now) >= END_TIME);
        if (effects.isEmpty()) return;

        for (Burn effect : effects) {
            float age = effect.age(now);
            if (age < MODEL_END) {
                float front = MathHelper.lerp(smooth(age / BURN_TIME), -0.10f, 1.35f);
                float erosion = 1.40f * smooth((age - BURN_TIME) / (MODEL_END - BURN_TIME));
                modelPass.render(client, effect.model, effect.origin, effect.height, matrices, camera, camPos,
                        Pipelines.ASH_MODEL, front, erosion, age, smooth(age / 0.06f));
            }
            if (age >= BURN_TIME && !effect.cracked) {
                effect.cracked = true;
                if (sound) world.playSoundClient(effect.origin.x, effect.origin.y + effect.height * 0.45, effect.origin.z,
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.38f, 1.33f, false);
            }
        }

        cameraRight.set(1, 0, 0).rotate(camera.getRotation());
        cameraUp.set(0, 1, 0).rotate(camera.getRotation());
        if (allocator == null) allocator = new BufferAllocator(65536);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (Burn effect : effects) {
                float age = effect.age(now);
                float fade = 1 - smooth((age - PILE_READY - HOLD_TIME) / FADE_TIME);
                drawCoal(mesh, effect, age, fade, camPos);
                if (age < 2.10f) drawFire(mesh, effect, age, camPos);
            }
            var built = mesh.endNullable();
            if (built != null) MeshDrawUtil.drawLabeled(Pipelines.ASH_EFFECT, built, "Egg ash effect");
        } finally {
            allocator.reset();
        }
    }

    private void drawCoal(BufferBuilder mesh, Burn effect, float age, float fade, Vec3d camera) {
        float deposit = smooth((age - 1.98f) / (PILE_READY - 1.98f));
        if (effect.pile != null && effect.pileRadius > 0 && deposit > 0) {
            float radius = effect.pileRadius;
            Vec3d p = effect.pile.subtract(camera);
            quad(mesh, p.add(-radius, 0, -radius), p.add(radius, 0, -radius),
                    p.add(radius, 0, radius), p.add(-radius, 0, radius),
                    4, color(25, 22, 19, 0.63f * deposit * fade));
        }

        for (Coal coal : effect.coals) {
            if (age <= coal.birth) continue;
            float travel = Math.clamp((age - coal.birth) / (coal.landAt - coal.birth), 0, 1);
            boolean landed = travel >= 1;
            if (landed && !coal.ground) continue;
            float hot = coal.ember ? (1 - smooth((age - coal.birth - 0.065f) / 0.45f)) : 0;
            double lift = 4 * coal.lift * travel * (1 - travel);
            Vec3d p = coal.from.lerp(coal.landing, travel).add(0, lift, 0).subtract(camera);
            float alpha = landed ? fade : smooth((age - coal.birth) / 0.052f);
            float size = coal.size * (1 - 0.17f * smooth((travel - 0.80f) / 0.20f));
            drawChip(mesh, p, size, coal.angle + travel * 7.0, coal.phase, coal.tone, hot, alpha, landed);
            if (!landed && hot > 0.05f) {
                billboard(mesh, p, cameraRight, cameraUp, size * 2.8f, size * 2.8f, 4,
                        color(255, 118, 27, hot * 0.61f * alpha));
            }
        }
    }

    private void drawFire(BufferBuilder mesh, Burn effect, float age, Vec3d camera) {
        Vector3f uprightRight = new Vector3f(cameraRight.x, 0, cameraRight.z).normalize();
        if (!uprightRight.isFinite()) uprightRight.set(1, 0, 0);
        for (Flame flame : effect.flames) {
            float ignite = 0.08f + flame.y * 0.87f;
            float extinguish = 1.92f - flame.y * 0.38f;
            float strength = smooth((age - ignite) / 0.18f) * (1 - smooth((age - extinguish + 0.23f) / 0.31f));
            if (strength <= 0.009f) continue;
            float flutter = 0.83f + 0.15f * MathHelper.sin(age * 24 + flame.phase)
                    + 0.07f * MathHelper.sin(age * 39 - flame.phase * 0.61f);
            double y = effect.height * flame.y + 0.023 * MathHelper.sin(age * 17 + flame.phase);
            Vec3d p = effect.origin.add(flame.x, y, flame.z).subtract(camera);
            float width = flame.width * (0.75f + 0.25f * strength);
            float height = flame.length * flutter * (0.60f + 0.40f * strength);
            Vec3d side = new Vec3d(uprightRight.x * width, 0, uprightRight.z * width);
            Vec3d top = p.add(0.06 * MathHelper.sin(age * 12 + flame.phase), height, 0.026 * Math.cos(age * 13 + flame.phase));
            int bend = Math.round(128 + 105 * MathHelper.sin(age * 9 + flame.phase));
            int split = Math.round(128 + 120 * MathHelper.sin(age * 5.5f + flame.phase * 2));
            quad(mesh, p.subtract(side), p.add(side), top.add(side), top.subtract(side), 2,
                    color(bend, split, 255, strength));

            float sparkLife = age * 1.9f + flame.phase;
            float sparkAge = sparkLife - (float) Math.floor(sparkLife);
            float spark = smooth(sparkAge / 0.13f) * (1 - smooth(sparkAge / 0.90f)) * strength;
            if (spark > 0.022f) {
                double drift = 0.075 * MathHelper.sin(age * 6 + flame.phase);
                Vec3d point = p.add(drift, height * 0.45 + sparkAge * 0.73, -drift * 0.6);
                float size = 0.015f + 0.010f * flutter;
                billboard(mesh, point, cameraRight, cameraUp, size * 2.4f, size * 2.4f, 4,
                        color(255, 153, 39, spark * 0.60f));
                billboard(mesh, point, cameraRight, cameraUp, size * 0.63f, size, 4,
                        color(255, 233, 151, spark));
            }
            if (flame.phase > 6) {
                billboard(mesh, p.add(0, height * 0.34, 0), cameraRight, cameraUp, width * 2.25f,
                        height * 0.77f, 4, color(255, 98, 18, strength * 0.20f));
            }
        }
    }

    private static void drawChip(BufferBuilder mesh, Vec3d p, float size, double angle, float phase, int tone,
                                 float heat, float alpha, boolean landed) {
        Vec3d side = new Vec3d(Math.cos(angle) * size, 0, Math.sin(angle) * size);
        Vec3d across = new Vec3d(-side.z * 0.76, landed ? 0.003 : Math.sin(phase + angle) * size * 0.8, side.x * 0.76);
        Vec3d a = p.subtract(side).subtract(across);
        Vec3d b = p.add(side).subtract(across.multiply(0.73));
        Vec3d c = p.add(side.multiply(0.76)).add(across);
        Vec3d d = p.subtract(side.multiply(0.68)).add(across.multiply(0.85));
        Vec3d top = p.add(0, size * (landed ? 0.37 : 0.70), 0);
        int r = 25 + tone;
        int g = 24 + tone;
        int bl = 23 + tone;
        int bright = color(Math.round(MathHelper.lerp(heat, r + 14, 255)), Math.round(MathHelper.lerp(heat, g + 12, 199)),
                Math.round(MathHelper.lerp(heat, bl + 9, 72)), alpha);
        int shaded = color(Math.round(MathHelper.lerp(heat, r, 236)), Math.round(MathHelper.lerp(heat, g, 107)),
                Math.round(MathHelper.lerp(heat, bl, 29)), alpha);
        quad(mesh, a, b, top, top, 0, shaded);
        quad(mesh, b, c, top, top, 0, bright);
        quad(mesh, c, d, top, top, 0, shaded);
        quad(mesh, d, a, top, top, 0, bright);
        if (!landed) quad(mesh, d, c, b, a, 0, shaded);
    }

    private static void billboard(BufferBuilder mesh, Vec3d p, Vector3f right, Vector3f up, float width, float height,
                                  int material, int color) {
        Vec3d a = new Vec3d(right.x * width, right.y * width, right.z * width);
        Vec3d b = new Vec3d(up.x * height, up.y * height, up.z * height);
        quad(mesh, p.subtract(a).subtract(b), p.add(a).subtract(b), p.add(a).add(b), p.subtract(a).add(b), material, color);
    }

    private static void quad(BufferBuilder mesh, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int material, int color) {
        vertex(mesh, a, material, 0, color);
        vertex(mesh, b, material + 1, 0, color);
        vertex(mesh, c, material + 1, 1, color);
        vertex(mesh, d, material, 1, color);
    }

    private static void vertex(BufferBuilder mesh, Vec3d v, float u, float t, int color) {
        mesh.vertex((float) v.x, (float) v.y, (float) v.z).texture(u, t).color(color);
    }

    private static int color(int r, int g, int b, float alpha) {
        return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public void clear() {
        effects.clear();
        world = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private record Flame(double x, float y, double z, float length, float width, float phase) {}
    private record Coal(Vec3d from, Vec3d landing, boolean ground, float birth, float landAt, float size,
                        double angle, float lift, int tone, float phase, boolean ember) {}

    private static final class Burn {
        final Vec3d origin;
        final Vec3d pile;
        final float height;
        final float pileRadius;
        final EntityRenderState model;
        final Flame[] flames;
        final List<Coal> coals;
        final long started;
        boolean cracked;

        Burn(Vec3d origin, Vec3d pile, float height, float pileRadius, EntityRenderState model,
             Flame[] flames, List<Coal> coals, long started) {
            this.origin = origin;
            this.pile = pile;
            this.height = height;
            this.pileRadius = pileRadius;
            this.model = model;
            this.flames = flames;
            this.coals = coals;
            this.started = started;
        }

        float age(long now) {
            return (now - started) / 1_000_000_000f;
        }
    }
}
