package egg.client.render.world;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
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

public final class ShockKillRenderer {
    private static final float SHOCK_END = 0.62f;
    private static final float CRUMBLE_START = 0.66f;
    private static final float MODEL_END = 1.28f;
    private static final float PILE_READY = 1.62f;
    private static final float HOLD_TIME = 1.35f;
    private static final float FADE_TIME = 0.55f;
    private static final float END_TIME = PILE_READY + HOLD_TIME + FADE_TIME;
    private static final int MAX_EFFECTS = 8;

    private final KillModelPass modelPass;
    private final List<Shock> effects = new ArrayList<>();
    private final Random random = new Random();
    private final Vector3f cameraRight = new Vector3f();
    private final Vector3f cameraUp = new Vector3f();
    private BufferAllocator allocator;
    private ClientWorld world;

    public ShockKillRenderer(KillModelPass modelPass) {
        this.modelPass = modelPass;
    }

    public boolean spawn(MinecraftClient client, Vec3d origin, double height, double width, EntityRenderState model, boolean sound) {
        if (client == null || client.world == null || client.player == null) return false;
        if (world != client.world) {
            clear();
            world = client.world;
        }

        float h = (float) Math.clamp(height, 0.75, 3.0);
        float w = (float) Math.clamp(width, 0.40, 1.65);
        float bodyYaw = (model instanceof LivingEntityRenderState living) ? living.bodyYaw : 0.0f;

        Vec3d floor = findFloor(client, origin.x, origin.y + 0.25, origin.z);
        Vec3d pile = floor == null ? null : floor.add(0.0, 0.013, 0.0);
        float pileRadius = pile == null ? 0.0f : supportedRadius(client, floor, w * 1.25f);

        List<DustGrain> dust = new ArrayList<>(88);
        for (int i = 0; i < 88; i++) {
            dust.add(makeDust(client, origin, h, w, i >= 56));
        }

        BoltNode[] mainBolt = buildMainBolt(origin, h, w);
        BoltNode[][] branches = new BoltNode[3][];
        for (int b = 0; b < 3; b++) {
            int anchor = 2 + b * 2;
            branches[b] = buildBranch(mainBolt[Math.min(anchor, mainBolt.length - 1)], w);
        }

        if (effects.size() >= MAX_EFFECTS) effects.removeFirst();
        effects.add(new Shock(origin, pile, h, w, bodyYaw, pileRadius, model, mainBolt, branches, dust, System.nanoTime()));
        if (sound) {
            world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                    SoundCategory.PLAYERS, 0.42f, 1.38f, false);
            world.playSoundClient(origin.x, origin.y + h * 0.5, origin.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.26f, 1.45f, false);
        }
        return true;
    }

    private BoltNode[] buildMainBolt(Vec3d origin, float h, float w) {
        int steps = 10;
        BoltNode[] nodes = new BoltNode[steps + 1];
        double topX = origin.x + (random.nextDouble() - 0.5) * 1.4;
        double topZ = origin.z + (random.nextDouble() - 0.5) * 1.4;
        double topY = origin.y + h + 7.5;
        double endY = origin.y + h * 0.72;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float env = MathHelper.sin(t * MathHelper.PI);
            double x = MathHelper.lerp(t, topX, origin.x) + (random.nextDouble() - 0.5) * 0.85 * env;
            double z = MathHelper.lerp(t, topZ, origin.z) + (random.nextDouble() - 0.5) * 0.85 * env;
            double y = MathHelper.lerp(t, topY, endY);
            nodes[i] = new BoltNode(x, y, z, random.nextFloat() * 100.0f);
        }
        return nodes;
    }

    private BoltNode[] buildBranch(BoltNode start, float w) {
        int steps = 4;
        BoltNode[] nodes = new BoltNode[steps + 1];
        double dirX = (random.nextDouble() - 0.5) * 1.6;
        double dirZ = (random.nextDouble() - 0.5) * 1.6;
        double dropY = 1.1 + random.nextDouble() * 1.4;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            double spread = Math.max(0.35, w) * t;
            double x = start.x + dirX * spread + (random.nextDouble() - 0.5) * 0.28 * t;
            double y = start.y - dropY * t;
            double z = start.z + dirZ * spread + (random.nextDouble() - 0.5) * 0.28 * t;
            nodes[i] = new BoltNode(x, y, z, random.nextFloat() * 100.0f);
        }
        return nodes;
    }

    private DustGrain makeDust(MinecraftClient client, Vec3d origin, float h, float w, boolean fine) {
        float heightFraction = random.nextFloat();
        double angle = random.nextDouble() * MathHelper.TAU;
        double reach = w * (0.18 + random.nextDouble() * (fine ? 1.85 : 1.18));
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double x = origin.x + dx * reach;
        double z = origin.z + dz * reach;
        Vec3d from = origin.add(dx * w * 0.34, h * heightFraction, dz * w * 0.34);
        Vec3d floor = findFloor(client, x, origin.y + 0.52, z);
        double groundY = floor == null ? origin.y - 2.0 : floor.y + 0.014;
        Vec3d landing = new Vec3d(x, groundY, z);
        float birth = CRUMBLE_START + (1.0f - heightFraction) * (MODEL_END - CRUMBLE_START - 0.08f) + random.nextFloat() * 0.07f;
        float landAt = PILE_READY - 0.28f + random.nextFloat() * 0.28f;
        float size = fine ? 0.009f + random.nextFloat() * 0.025f : 0.022f + random.nextFloat() * 0.044f;
        int tone = random.nextInt(42);
        boolean spark = !fine && random.nextFloat() < 0.24f;
        return new DustGrain(from, landing, floor != null, birth, landAt, size, angle, 0.10f + random.nextFloat() * 0.32f,
                tone, random.nextFloat() * 6.28f, spark);
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
                double angle = i * Math.PI / 4.0;
                Vec3d sample = findFloor(client, floor.x + radius * Math.cos(angle),
                        floor.y + 0.24, floor.z + radius * Math.sin(angle));
                if (sample == null || Math.abs(sample.y - floor.y) > 0.04) {
                    supported = false;
                    break;
                }
            }
            if (supported) return radius;
        }
        return 0.0f;
    }

    public void render(MinecraftClient client, MatrixStack matrices, Camera camera, Vec3d camPos, boolean sound) {
        if (client == null || world != client.world) {
            clear();
            return;
        }
        long now = System.nanoTime();
        effects.removeIf(effect -> effect.age(now) >= END_TIME);
        if (effects.isEmpty()) return;

        for (Shock effect : effects) {
            float age = effect.age(now);
            if (age < MODEL_END) {
                float shockActive = age < SHOCK_END ? 1.0f - smooth((age - (SHOCK_END - 0.12f)) / 0.12f) : 0.0f;
                float erosion = age >= CRUMBLE_START
                        ? 1.42f * smooth((age - CRUMBLE_START) / (MODEL_END - CRUMBLE_START))
                        : 0.0f;
                float jitterAmp = shockActive * 0.028f;
                double jx = MathHelper.sin(age * 96.0f) * jitterAmp;
                double jz = MathHelper.cos(age * 118.0f) * jitterAmp;
                Vec3d renderOrigin = effect.origin.add(jx, 0.0, jz);
                float opacity = smooth(age / 0.04f);
                modelPass.render(client, effect.model, renderOrigin, effect.height, matrices, camera, camPos,
                        Pipelines.SHOCK_MODEL, shockActive, erosion, age, opacity);
            }
            if (age >= CRUMBLE_START && !effect.crumbled) {
                effect.crumbled = true;
                if (sound && world != null) {
                    world.playSoundClient(effect.origin.x, effect.origin.y + effect.height * 0.4, effect.origin.z,
                            SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.42f, 1.32f, false);
                }
            }
        }

        cameraRight.set(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        cameraUp.set(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());

        if (allocator == null) allocator = new BufferAllocator(131072);
        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (Shock effect : effects) {
                float age = effect.age(now);
                float fade = 1.0f - smooth((age - PILE_READY - HOLD_TIME) / FADE_TIME);

                if (age < MODEL_END) {
                    drawSkeleton(mesh, effect, age, camPos);
                }
                if (age < 0.56f) {
                    drawLightning(mesh, effect, age, camPos);
                }
                drawDust(mesh, effect, age, fade, camPos);
            }
            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.ASH_EFFECT, built, "Egg shock kill");
            }
        } finally {
            allocator.reset();
        }
    }

    private void drawSkeleton(BufferBuilder mesh, Shock effect, float age, Vec3d camPos) {
        float shockActive = age < SHOCK_END ? 1.0f - smooth((age - (SHOCK_END - 0.14f)) / 0.14f) : 0.0f;
        float crumble = age >= CRUMBLE_START ? smooth((age - CRUMBLE_START) / (MODEL_END - CRUMBLE_START)) : 0.0f;
        float cutoffFrac = MathHelper.lerp(crumble, 1.08f, -0.05f);

        float jitterAmp = shockActive * 0.028f;
        double jx = MathHelper.sin(age * 96.0f) * jitterAmp;
        double jz = MathHelper.cos(age * 118.0f) * jitterAmp;
        Vec3d origin = effect.origin.add(jx, 0.0, jz).subtract(camPos);

        float flicker = shockActive > 0.05f && (Math.floor(age * 16.0f) % 2.0 == 0.0) ? 1.0f : 0.0f;
        float boneAlpha = smooth(age / 0.04f) * (1.0f - crumble * 0.35f);
        if (boneAlpha <= 0.01f) return;

        float sx = effect.width / 0.6f;
        float sy = effect.height / 1.8f;
        float yawRad = -effect.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        float cosY = MathHelper.cos(yawRad);
        float sinY = MathHelper.sin(yawRad);

        int topR = Math.round(MathHelper.lerp(crumble, MathHelper.lerp(flicker * 0.35f, 250, 210), 105));
        int topG = Math.round(MathHelper.lerp(crumble, MathHelper.lerp(flicker * 0.35f, 248, 248), 106));
        int topB = Math.round(MathHelper.lerp(crumble, 255, 112));

        int sideR = Math.round(topR * 0.80f);
        int sideG = Math.round(topG * 0.83f);
        int sideB = Math.round(topB * 0.88f);

        int ivoryTop = color(topR, topG, topB, boneAlpha);
        int ivorySide = color(sideR, sideG, sideB, boneAlpha);
        int socketCol = color(14, 16, 24, boneAlpha);

        if (shockActive > 0.05f) {
            float glowA = shockActive * (0.28f + 0.16f * flicker);
            Vec3d torsoCenter = origin.add(0.0, effect.height * 0.56, 0.0);
            billboard(mesh, torsoCenter, cameraRight, cameraUp, effect.width * 1.1f, effect.height * 0.55f, 4,
                    color(115, 225, 255, glowA));
        }

        float cutoffY = cutoffFrac * 1.8f;
        float wobble = MathHelper.sin(age * 46.0f) * shockActive * 0.04f;

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.50f, 1.76f, 0.0f, 0.145f, 0.135f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.43f, 1.50f, 0.01f, 0.095f, 0.110f, ivoryTop, ivorySide);

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.062f, 1.57f, 1.65f, 0.133f, 0.036f, 0.010f, socketCol, socketCol);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.062f, 1.57f, 1.65f, 0.133f, 0.036f, 0.010f, socketCol, socketCol);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.515f, 1.555f, 0.133f, 0.018f, 0.010f, socketCol, socketCol);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.042f, 1.438f, 1.482f, 0.118f, 0.010f, 0.008f, socketCol, socketCol);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.438f, 1.482f, 0.118f, 0.010f, 0.008f, socketCol, socketCol);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.042f, 1.438f, 1.482f, 0.118f, 0.010f, 0.008f, socketCol, socketCol);

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 0.74f, 1.43f, -0.045f, 0.036f, 0.036f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.32f, 1.38f, 0.0f, 0.245f, 0.032f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 1.00f, 1.33f, 0.062f, 0.024f, 0.022f, ivoryTop, ivorySide);

        float[] ribY = {1.23f, 1.12f, 1.01f};
        for (float ry : ribY) {
            emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, ry, ry + 0.038f, 0.062f, 0.125f, 0.018f, ivoryTop, ivorySide);
            emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.118f, ry, ry + 0.038f, 0.008f, 0.020f, 0.064f, ivoryTop, ivorySide);
            emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.118f, ry, ry + 0.038f, 0.008f, 0.020f, 0.064f, ivoryTop, ivorySide);
            emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, ry, ry + 0.038f, -0.046f, 0.125f, 0.018f, ivoryTop, ivorySide);
        }

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.0f, 0.69f, 0.76f, 0.0f, 0.140f, 0.044f, ivoryTop, ivorySide);

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.235f, 1.04f, 1.35f, wobble, 0.032f, 0.032f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.235f, 1.04f, 1.35f, -wobble, 0.032f, 0.032f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.255f, 0.75f, 1.04f, wobble * 1.4f, 0.027f, 0.027f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.255f, 0.75f, 1.04f, -wobble * 1.4f, 0.027f, 0.027f, ivoryTop, ivorySide);

        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.095f, 0.36f, 0.70f, 0.0f, 0.038f, 0.038f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.095f, 0.36f, 0.70f, 0.0f, 0.038f, 0.038f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, -0.095f, 0.02f, 0.36f, 0.0f, 0.031f, 0.031f, ivoryTop, ivorySide);
        emitClippedBone(mesh, origin, sx, sy, cosY, sinY, cutoffY, 0.095f, 0.02f, 0.36f, 0.0f, 0.031f, 0.031f, ivoryTop, ivorySide);
    }

    private static void emitClippedBone(BufferBuilder mesh, Vec3d origin, float sx, float sy, float cosY, float sinY,
                                        float cutoffY, float lx, float y0, float y1, float lz, float hx, float hz,
                                        int topCol, int sideCol) {
        if (y0 >= cutoffY) return;
        float clippedY1 = Math.min(y1, cutoffY);
        if (clippedY1 - y0 <= 0.004f) return;

        float rx = lx * sx;
        float rz = lz * sx;
        float rhx = hx * sx;
        float rhz = hz * sx;
        float wy0 = y0 * sy;
        float wy1 = clippedY1 * sy;

        Vec3d p00 = rotateLocal(origin, rx - rhx, wy0, rz - rhz, cosY, sinY);
        Vec3d p10 = rotateLocal(origin, rx + rhx, wy0, rz - rhz, cosY, sinY);
        Vec3d p11 = rotateLocal(origin, rx + rhx, wy0, rz + rhz, cosY, sinY);
        Vec3d p01 = rotateLocal(origin, rx - rhx, wy0, rz + rhz, cosY, sinY);

        Vec3d q00 = rotateLocal(origin, rx - rhx, wy1, rz - rhz, cosY, sinY);
        Vec3d q10 = rotateLocal(origin, rx + rhx, wy1, rz - rhz, cosY, sinY);
        Vec3d q11 = rotateLocal(origin, rx + rhx, wy1, rz + rhz, cosY, sinY);
        Vec3d q01 = rotateLocal(origin, rx - rhx, wy1, rz + rhz, cosY, sinY);

        quad(mesh, q00, q01, q11, q10, 0, topCol);
        quad(mesh, p00, p10, p11, p01, 0, sideCol);
        quad(mesh, p01, p11, q11, q01, 0, topCol);
        quad(mesh, p10, p00, q00, q10, 0, sideCol);
        quad(mesh, p00, p01, q01, q00, 0, sideCol);
        quad(mesh, p11, p10, q10, q11, 0, sideCol);
    }

    private static Vec3d rotateLocal(Vec3d origin, float lx, float ly, float lz, float cosY, float sinY) {
        double wx = origin.x + lx * cosY + lz * sinY;
        double wy = origin.y + ly;
        double wz = origin.z - lx * sinY + lz * cosY;
        return new Vec3d(wx, wy, wz);
    }

    private void drawLightning(BufferBuilder mesh, Shock effect, float age, Vec3d camPos) {
        float boltAlpha = (1.0f - smooth((age - 0.14f) / 0.42f));
        if (boltAlpha <= 0.01f) return;

        int crackTick = (int) (age * 24.0f);
        drawBoltRibbon(mesh, effect.mainBolt, crackTick, 0.048f, 0.16f, boltAlpha, camPos);
        for (int b = 0; b < effect.branches.length; b++) {
            drawBoltRibbon(mesh, effect.branches[b], crackTick + b * 7, 0.026f, 0.09f, boltAlpha * 0.85f, camPos);
        }

        if (age < 0.30f) {
            float impactA = 1.0f - smooth(age / 0.30f);
            Vec3d hitPos = effect.origin.add(0.0, effect.height * 0.72, 0.0).subtract(camPos);
            billboard(mesh, hitPos, cameraRight, cameraUp, 0.85f, 0.85f, 4, color(120, 225, 255, impactA * 0.75f));
            billboard(mesh, hitPos, cameraRight, cameraUp, 0.38f, 0.38f, 4, color(255, 255, 255, impactA));
        }
    }

    private void drawBoltRibbon(BufferBuilder mesh, BoltNode[] nodes, int crackTick,
                                float coreWidth, float haloWidth, float alpha, Vec3d camPos) {
        int coreCol = color(242, 252, 255, alpha);
        int haloCol = color(72, 198, 255, alpha * 0.46f);
        for (int i = 0; i < nodes.length - 1; i++) {
            Vec3d p0 = jitterNode(nodes[i], i == nodes.length - 1 ? 0.0f : 0.09f, crackTick).subtract(camPos);
            Vec3d p1 = jitterNode(nodes[i + 1], i + 1 == nodes.length - 1 ? 0.0f : 0.09f, crackTick).subtract(camPos);
            Vec3d seg = p1.subtract(p0);
            Vec3d camDir = p0.add(p1).multiply(0.5).normalize();
            Vec3d side = seg.crossProduct(camDir);
            if (side.lengthSquared() < 1e-6) side = new Vec3d(cameraRight.x, cameraRight.y, cameraRight.z);
            side = side.normalize();

            Vec3d hOff = side.multiply(haloWidth);
            quad(mesh, p0.subtract(hOff), p0.add(hOff), p1.add(hOff), p1.subtract(hOff), 0, haloCol);

            Vec3d cOff = side.multiply(coreWidth);
            quad(mesh, p0.subtract(cOff), p0.add(cOff), p1.add(cOff), p1.subtract(cOff), 0, coreCol);
        }
    }

    private static Vec3d jitterNode(BoltNode node, float amp, int tick) {
        if (amp <= 0.0f) return new Vec3d(node.x, node.y, node.z);
        float ph = node.seed + tick * 3.7f;
        return new Vec3d(
                node.x + MathHelper.sin(ph) * amp,
                node.y,
                node.z + MathHelper.cos(ph * 1.31f) * amp
        );
    }

    private void drawDust(BufferBuilder mesh, Shock effect, float age, float fade, Vec3d camera) {
        float deposit = smooth((age - PILE_READY + 0.18f) / 0.24f);
        if (effect.pile != null && effect.pileRadius > 0.0f && deposit > 0.0f && fade > 0.01f) {
            float radius = effect.pileRadius;
            Vec3d p = effect.pile.subtract(camera);
            quad(mesh, p.add(-radius, 0.0, -radius), p.add(radius, 0.0, -radius),
                    p.add(radius, 0.0, radius), p.add(-radius, 0.0, radius),
                    4, color(34, 36, 42, 0.56f * deposit * fade));
        }

        for (DustGrain d : effect.dust) {
            if (age <= d.birth) continue;
            float travel = Math.clamp((age - d.birth) / (d.landAt - d.birth), 0.0f, 1.0f);
            boolean landed = travel >= 1.0f;
            if (landed && !d.ground) continue;

            double lift = 4.0 * d.lift * travel * (1.0f - travel);
            Vec3d p = d.from.lerp(d.landing, travel).add(0.0, lift, 0.0).subtract(camera);
            float alpha = landed ? fade : smooth((age - d.birth) / 0.05f) * (d.spark ? (1.0f - travel * 0.7f) : 1.0f);
            if (alpha <= 0.01f) continue;

            float size = d.size * (1.0f - 0.16f * smooth((travel - 0.78f) / 0.22f));
            if (d.spark && !landed) {
                billboard(mesh, p, cameraRight, cameraUp, size * 2.5f, size * 2.5f, 4,
                        color(90, 215, 255, 0.65f * alpha));
            }
            drawChip(mesh, p, size, d.angle + travel * 6.5, d.phase, d.tone, d.spark && !landed ? 1.0f - travel : 0.0f, alpha, landed);
        }
    }

    private static void drawChip(BufferBuilder mesh, Vec3d p, float size, double angle, float phase, int tone,
                                 float sparkMix, float alpha, boolean landed) {
        Vec3d side = new Vec3d(Math.cos(angle) * size, 0.0, Math.sin(angle) * size);
        Vec3d across = new Vec3d(-side.z * 0.76, landed ? 0.003 : Math.sin(phase + angle) * size * 0.8, side.x * 0.76);
        Vec3d a = p.subtract(side).subtract(across);
        Vec3d b = p.add(side).subtract(across.multiply(0.73));
        Vec3d c = p.add(side.multiply(0.76)).add(across);
        Vec3d d = p.subtract(side.multiply(0.68)).add(across.multiply(0.85));
        Vec3d top = p.add(0.0, size * (landed ? 0.36 : 0.68), 0.0);

        int base = 36 + tone;
        int bright = color(Math.round(MathHelper.lerp(sparkMix, base + 24, 210)),
                Math.round(MathHelper.lerp(sparkMix, base + 26, 245)),
                Math.round(MathHelper.lerp(sparkMix, base + 30, 255)), alpha);
        int shaded = color(Math.round(MathHelper.lerp(sparkMix, base, 90)),
                Math.round(MathHelper.lerp(sparkMix, base + 2, 200)),
                Math.round(MathHelper.lerp(sparkMix, base + 5, 255)), alpha);
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
        return (Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f) << 24) | (r << 16) | (g << 8) | b;
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public void clear() {
        effects.clear();
        world = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private record BoltNode(double x, double y, double z, float seed) {}

    private record DustGrain(Vec3d from, Vec3d landing, boolean ground, float birth, float landAt, float size,
                             double angle, float lift, int tone, float phase, boolean spark) {}

    private static final class Shock {
        final Vec3d origin;
        final Vec3d pile;
        final float height;
        final float width;
        final float bodyYaw;
        final float pileRadius;
        final EntityRenderState model;
        final BoltNode[] mainBolt;
        final BoltNode[][] branches;
        final List<DustGrain> dust;
        final long started;
        boolean crumbled;

        Shock(Vec3d origin, Vec3d pile, float height, float width, float bodyYaw, float pileRadius,
              EntityRenderState model, BoltNode[] mainBolt, BoltNode[][] branches, List<DustGrain> dust, long started) {
            this.origin = origin;
            this.pile = pile;
            this.height = height;
            this.width = width;
            this.bodyYaw = bodyYaw;
            this.pileRadius = pileRadius;
            this.model = model;
            this.mainBolt = mainBolt;
            this.branches = branches;
            this.dust = dust;
            this.started = started;
        }

        float age(long now) {
            return (now - started) / 1_000_000_000.0f;
        }
    }
}
