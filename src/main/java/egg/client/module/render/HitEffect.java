package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class HitEffect extends Module {

    private static final String MODE_SLASH = "Разрез";
    private static final String MODE_IMPACT = "Линза";
    private static final String MODE_ECHO = "Отголосок";

    private static final int CRIT_GOLD = 0xFFFFD15C;

    private static HitEffect instance;

    private final ModeSetting mode = new ModeSetting("Эффект", MODE_SLASH, "Стиль визуального эффекта при ударе",
            MODE_SLASH, MODE_IMPACT, MODE_ECHO);
    private final BooleanSetting onlyCrits = new BooleanSetting("Только криты", false, "Показывать эффект только при критическом ударе");
    private final BooleanSetting critBoost = new BooleanSetting("Усиление критов", true, "Двойной эффект и золотистый оттенок при крите");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Источник цвета эффекта", "Тема", "Радуга", "Кастом");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", 0xFF70B8FF, "Пользовательский цвет эффекта");
    private final SliderSetting scale = new SliderSetting("Размер", 1.0, 0.5, 2.0, 0.1, "Масштаб эффекта удара");
    private final SliderSetting duration = new SliderSetting("Длительность", 0.32, 0.15, 0.70, 0.05, "Длительность эффекта в секундах");

    private final List<SlashPulse> slashes = new ArrayList<>();
    private final List<ImpactWave> impacts = new ArrayList<>();
    private final List<EchoGhost> echoes = new ArrayList<>();
    private final Random random = new Random();
    private BufferAllocator quadAllocator;
    private boolean slashFlip = false;

    public HitEffect() {
        super("HitEffect", "Отображает 3D-разрез, ударную линзу или фантомный отголосок при ударе", Category.ESP);
        instance = this;
        customColor.setVisible(() -> colorMode.is("Кастом"));
        addSettings(mode, onlyCrits, critBoost, colorMode, customColor, scale, duration);
    }

    public static HitEffect getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        slashes.clear();
        impacts.clear();
        echoes.clear();
        if (quadAllocator != null) {
            quadAllocator.close();
            quadAllocator = null;
        }
    }

    public void onAttack(PlayerEntity player, Entity target) {
        if (!isEnabled() || player == null || !(target instanceof LivingEntity) || EntityUtil.isNakedInvisible(target)) {
            return;
        }

        boolean isCrit = HitParticles.isCriticalHit(player, target);
        if (onlyCrits.isEnabled() && !isCrit) {
            return;
        }

        Vec3d hitPos = HitParticles.getHitPosition(player, target);
        Vec3d normal = player.getEyePos().subtract(hitPos);
        if (normal.lengthSquared() < 1e-6) {
            normal = player.getRotationVec(1.0f).multiply(-1.0);
        } else {
            normal = normal.normalize();
        }

        Vec3d shiftedHit = hitPos.add(normal.multiply(0.14));
        Vec3d entityPos = target.getEntityPos();
        Vec3d relOffset = shiftedHit.subtract(entityPos);
        int baseColor = resolveColor(isCrit);
        long now = System.nanoTime();
        long durNanos = Math.round(duration.get() * 1_000_000_000.0);
        float s = (float) scale.get();

        if (mode.is(MODE_SLASH)) {
            float baseRoll = (slashFlip ? -0.66f : 0.66f) + (random.nextFloat() - 0.5f) * 0.24f;
            slashFlip = !slashFlip;
            float size = (isCrit && critBoost.isEnabled() ? 1.18f : 0.96f) * s;
            slashes.add(new SlashPulse(target.getId(), shiftedHit, relOffset, normal, baseRoll, size, baseColor, now, durNanos));
            if (isCrit && critBoost.isEnabled()) {
                slashes.add(new SlashPulse(target.getId(), shiftedHit, relOffset, normal,
                        baseRoll + 1.48f, size * 0.94f, baseColor, now + 26_000_000L, durNanos));
            }
            if (slashes.size() > 32) {
                slashes.removeFirst();
            }
        } else if (mode.is(MODE_IMPACT)) {
            float size = (isCrit && critBoost.isEnabled() ? 1.08f : 0.85f) * s;
            impacts.add(new ImpactWave(target.getId(), shiftedHit, relOffset, normal, size, baseColor, now, durNanos));
            if (isCrit && critBoost.isEnabled()) {
                impacts.add(new ImpactWave(target.getId(), shiftedHit, relOffset, normal,
                        size * 1.18f, baseColor, now + 45_000_000L, durNanos));
            }
            if (impacts.size() > 32) {
                impacts.removeFirst();
            }
        } else {
            Vec3d kbDir = new Vec3d(target.getX() - player.getX(), 0.14, target.getZ() - player.getZ());
            if (kbDir.lengthSquared() < 1e-5) {
                kbDir = new Vec3d(0.0, 0.18, 0.0);
            } else {
                kbDir = kbDir.normalize();
            }
            Box box = target.getBoundingBox();
            float dist = (isCrit && critBoost.isEnabled() ? 0.56f : 0.38f) * s;
            echoes.add(new EchoGhost(box, kbDir, dist, baseColor, now, durNanos));
            if (isCrit && critBoost.isEnabled()) {
                echoes.add(new EchoGhost(box, kbDir, dist * 1.42f, baseColor, now + 35_000_000L, durNanos));
            }
            if (echoes.size() > 24) {
                echoes.removeFirst();
            }
        }
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos, float tickDelta) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (slashes.isEmpty() && impacts.isEmpty() && echoes.isEmpty()) return;

        long now = System.nanoTime();

        if (!slashes.isEmpty()) {
            renderSlashes(mc, camPos, tickDelta, now);
        }
        if (!impacts.isEmpty()) {
            renderImpacts(mc, camPos, tickDelta, now);
        }
        if (!echoes.isEmpty()) {
            renderEchoes(consumers, camPos, now);
        }
    }

    private void renderSlashes(MinecraftClient mc, Vec3d camPos, float tickDelta, long now) {
        if (quadAllocator == null) {
            quadAllocator = new BufferAllocator(32 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }
        try {
            BufferBuilder mesh = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int emitted = 0;

            Iterator<SlashPulse> it = slashes.iterator();
            while (it.hasNext()) {
                SlashPulse pulse = it.next();
                if (now < pulse.startNanos) continue;
                float t = (float) (now - pulse.startNanos) / (float) pulse.durNanos;
                if (t >= 1.0f) {
                    it.remove();
                    continue;
                }

                Vec3d center = resolveAnchor(mc, pulse.entityId, pulse.fallbackPos, pulse.relOffset, tickDelta);
                Vector3f n = new Vector3f((float) pulse.normal.x, (float) pulse.normal.y, (float) pulse.normal.z).normalize();
                Vector3f refUp = Math.abs(n.y) > 0.92f ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
                Vector3f right = new Vector3f(refUp).cross(n).normalize();
                Vector3f up = new Vector3f(n).cross(right).normalize();

                float cosR = MathHelper.cos(pulse.roll);
                float sinR = MathHelper.sin(pulse.roll);
                Vector3f rotU = new Vector3f(right).mul(cosR).add(new Vector3f(up).mul(sinR));
                Vector3f rotV = new Vector3f(right).mul(-sinR).add(new Vector3f(up).mul(cosR));

                emitEncodedQuad(mesh, center, camPos, rotU, rotV, pulse.size, t, pulse.color);
                emitted++;
            }

            if (emitted > 0) {
                var built = mesh.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeled(Pipelines.HIT_SLASH, built, "HitEffect slash");
                }
            }
        } finally {
            quadAllocator.reset();
        }
    }

    private void renderImpacts(MinecraftClient mc, Vec3d camPos, float tickDelta, long now) {
        if (quadAllocator == null) {
            quadAllocator = new BufferAllocator(32 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }
        try {
            BufferBuilder mesh = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int emitted = 0;

            Iterator<ImpactWave> it = impacts.iterator();
            while (it.hasNext()) {
                ImpactWave wave = it.next();
                if (now < wave.startNanos) continue;
                float t = (float) (now - wave.startNanos) / (float) wave.durNanos;
                if (t >= 1.0f) {
                    it.remove();
                    continue;
                }

                Vec3d center = resolveAnchor(mc, wave.entityId, wave.fallbackPos, wave.relOffset, tickDelta);
                Vector3f n = new Vector3f((float) wave.normal.x, (float) wave.normal.y, (float) wave.normal.z).normalize();
                Vector3f refUp = Math.abs(n.y) > 0.92f ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
                Vector3f right = new Vector3f(refUp).cross(n).normalize();
                Vector3f up = new Vector3f(n).cross(right).normalize();

                emitEncodedQuad(mesh, center, camPos, right, up, wave.size, t, wave.color);
                emitted++;
            }

            if (emitted > 0) {
                var built = mesh.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeled(Pipelines.HIT_IMPACT, built, "HitEffect impact");
                }
            }
        } finally {
            quadAllocator.reset();
        }
    }

    private void renderEchoes(VertexConsumerProvider consumers, Vec3d camPos, long now) {
        VertexConsumer quadBuf = consumers.getBuffer(RenderLayers.debugQuads());
        VertexConsumer glowBuf = consumers.getBuffer(RenderLayers.lightning());

        Iterator<EchoGhost> it = echoes.iterator();
        while (it.hasNext()) {
            EchoGhost echo = it.next();
            if (now < echo.startNanos) continue;
            float t = (float) (now - echo.startNanos) / (float) echo.durNanos;
            if (t >= 1.0f) {
                it.remove();
                continue;
            }

            float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            float fade = 1.0f - t * t;
            double shift = echo.distance * ease;
            double expand = 0.04 + ease * 0.10;
            Box shifted = echo.box
                    .offset(echo.dir.x * shift, echo.dir.y * shift, echo.dir.z * shift)
                    .expand(expand);

            int r = (echo.color >> 16) & 0xFF;
            int g = (echo.color >> 8) & 0xFF;
            int b = echo.color & 0xFF;

            int fillA = Math.round(38.0f * fade);
            int edgeA = Math.round(230.0f * fade);

            renderGhostBox(quadBuf, glowBuf, shifted, camPos, r, g, b, fillA, edgeA);
        }
    }

    private static Vec3d resolveAnchor(MinecraftClient mc, int entityId, Vec3d fallbackPos, Vec3d relOffset, float tickDelta) {
        Entity e = mc.world.getEntityById(entityId);
        if (e != null && e.isAlive()) {
            Vec3d lerped = e.getLerpedPos(tickDelta);
            return lerped.add(relOffset);
        }
        return fallbackPos;
    }

    private static void emitEncodedQuad(BufferBuilder mesh, Vec3d center, Vec3d camPos,
                                        Vector3f u, Vector3f v, float halfExtent, float progress, int color) {
        int bucket = Math.round(MathHelper.clamp(progress, 0.0f, 1.0f) * 200.0f);
        float uShift = bucket * 10.0f;
        float uv = 1.15f;

        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y - camPos.y);
        float cz = (float) (center.z - camPos.z);

        float ux = u.x * halfExtent;
        float uy = u.y * halfExtent;
        float uz = u.z * halfExtent;
        float vx = v.x * halfExtent;
        float vy = v.y * halfExtent;
        float vz = v.z * halfExtent;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 255;

        mesh.vertex(cx - ux - vx, cy - uy - vy, cz - uz - vz).texture(uShift - uv, -uv).color(r, g, b, a);
        mesh.vertex(cx + ux - vx, cy + uy - vy, cz + uz - vz).texture(uShift + uv, -uv).color(r, g, b, a);
        mesh.vertex(cx + ux + vx, cy + uy + vy, cz + uz + vz).texture(uShift + uv, uv).color(r, g, b, a);
        mesh.vertex(cx - ux + vx, cy - uy + vy, cz - uz + vz).texture(uShift - uv, uv).color(r, g, b, a);
    }

    private static void renderGhostBox(VertexConsumer fillBuf, VertexConsumer edgeBuf, Box box, Vec3d camPos,
                                       int r, int g, int b, int fillA, int edgeA) {
        float x1 = (float) (box.minX - camPos.x);
        float y1 = (float) (box.minY - camPos.y);
        float z1 = (float) (box.minZ - camPos.z);
        float x2 = (float) (box.maxX - camPos.x);
        float y2 = (float) (box.maxY - camPos.y);
        float z2 = (float) (box.maxZ - camPos.z);

        if (fillA > 1) {
            drawQuadDoubleSided(fillBuf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, fillA);
            drawQuadDoubleSided(fillBuf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, fillA);
            drawQuadDoubleSided(fillBuf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, fillA);
            drawQuadDoubleSided(fillBuf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, fillA);
            drawQuadDoubleSided(fillBuf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, fillA);
            drawQuadDoubleSided(fillBuf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, fillA);
        }

        if (edgeA > 1) {
            float bLen = Math.min(0.28f, Math.min(x2 - x1, Math.min(y2 - y1, z2 - z1)) * 0.34f);
            float t = 0.014f;
            float[] xs = {x1, x2};
            float[] ys = {y1, y2};
            float[] zs = {z1, z2};
            for (float kx : xs) {
                float sx = kx == x1 ? bLen : -bLen;
                for (float ky : ys) {
                    float sy = ky == y1 ? bLen : -bLen;
                    for (float kz : zs) {
                        float sz = kz == z1 ? bLen : -bLen;
                        drawSolidBox(edgeBuf, Math.min(kx, kx + sx) - t, ky - t, kz - t, Math.max(kx, kx + sx) + t, ky + t, kz + t, r, g, b, edgeA);
                        drawSolidBox(edgeBuf, kx - t, Math.min(ky, ky + sy) - t, kz - t, kx + t, Math.max(ky, ky + sy) + t, kz + t, r, g, b, edgeA);
                        drawSolidBox(edgeBuf, kx - t, ky - t, Math.min(kz, kz + sz) - t, kx + t, ky + t, Math.max(kz, kz + sz) + t, r, g, b, edgeA);
                    }
                }
            }
        }
    }

    private int resolveColor(boolean isCrit) {
        int col;
        if (colorMode.is("Тема")) {
            col = Theme.getAccent();
        } else if (colorMode.is("Радуга")) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
            col = Color.HSBtoRGB(hue, 0.82f, 1.0f) | 0xFF000000;
        } else {
            col = customColor.get();
        }
        if (isCrit && critBoost.isEnabled()) {
            col = ColorUtil.interpolate(col, CRIT_GOLD, 0.55f);
        }
        return col;
    }

    private static void drawSolidBox(VertexConsumer buf,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     int r, int g, int b, int a) {
        if (x1 > x2) { float t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { float t = y1; y1 = y2; y2 = t; }
        if (z1 > z2) { float t = z1; z1 = z2; z2 = t; }
        drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void drawQuadDoubleSided(VertexConsumer buf,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int r, int g, int b, int a) {
        buf.vertex(x1, y1, z1).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x4, y4, z4).color(r, g, b, a);

        buf.vertex(x4, y4, z4).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x1, y1, z1).color(r, g, b, a);
    }

    private record SlashPulse(int entityId, Vec3d fallbackPos, Vec3d relOffset, Vec3d normal,
                              float roll, float size, int color, long startNanos, long durNanos) {}

    private record ImpactWave(int entityId, Vec3d fallbackPos, Vec3d relOffset, Vec3d normal,
                              float size, int color, long startNanos, long durNanos) {}

    private record EchoGhost(Box box, Vec3d dir, float distance, int color, long startNanos, long durNanos) {}
}
