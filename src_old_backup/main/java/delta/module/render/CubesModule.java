package delta.module.render;

import static delta.core.Interface.aM_;
import delta.config.ThemeInfo;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.render.ColorUtil;
import delta.render.Draw3DProcessor;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "Cubes", b = "Парящие неоновые каркасные кубы вокруг игрока", c = Category.Render)
public class CubesModule extends Module implements Interface {
    private static final long FADE_MS = 500L;
    private static final int MAX_PARTICLES = 100;

    private static final int[][] EDGES = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] DIAGONALS = new int[][]{
            {0, 6}, {1, 7}, {3, 5}, {2, 4}
    };
    private static final float[][] CORNERS = new float[][]{
            {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, 0.5f},
            {-0.5f, 0.5f, -0.5f},  {0.5f, 0.5f, -0.5f},  {0.5f, 0.5f, 0.5f},  {-0.5f, 0.5f, 0.5f}
    };

    private final SliderSetting amount = new SliderSetting("Количество", 60.0f, 5.0f, 100.0f, 1.0f);
    private final SliderSetting size = new SliderSetting("Размер", 1.0f, 0.25f, 3.0f, 0.05f);
    private final SliderSetting speed = new SliderSetting("Скорость", 1.0f, 0.2f, 3.0f, 0.1f);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Кастом", "Радуга");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF8B5CF6).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));

    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    private Object lastLevel;

    public CubesModule() {
        a(this.amount, this.size, this.speed, this.colorMode, this.customColor);
    }

    @Override
    public void c() {
        super.c();
        this.particles.clear();
        this.lastLevel = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null) {
            this.particles.clear();
            this.lastLevel = null;
            return;
        }

        if (aM_.world != this.lastLevel) {
            this.particles.clear();
            this.lastLevel = aM_.world;
        }

        long nowNanos = System.nanoTime();
        this.particles.removeIf(p -> p.expired(nowNanos));

        int targetCount = Math.min(MAX_PARTICLES, (int) this.amount.c().floatValue());
        while (this.particles.size() > targetCount) {
            this.particles.remove(0);
        }

        for (Particle p : this.particles) {
            p.tick();
        }

        if (this.particles.size() < targetCount) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            float spd = this.speed.c().floatValue();
            Vec3d pos = aM_.player.getEntityPos().add(
                    rnd.nextDouble(-18.0, 18.0),
                    rnd.nextDouble(0.2, 5.0),
                    rnd.nextDouble(-18.0, 18.0)
            );
            Vec3d vel = new Vec3d(
                    rnd.nextDouble(-1.0, 1.0) * 0.035 * spd,
                    rnd.nextDouble(0.0, 2.0) * 0.035 * spd,
                    rnd.nextDouble(-1.0, 1.0) * 0.035 * spd
            );
            Vec3d spin = new Vec3d(
                    rnd.nextDouble(-1.0, 1.0) * 0.05 * spd,
                    rnd.nextDouble(-1.0, 1.0) * 0.05 * spd,
                    rnd.nextDouble(-1.0, 1.0) * 0.05 * spd
            );
            long lifetime = rnd.nextLong(2000L, 5000L);
            float pSize = (float) rnd.nextDouble(0.12, 0.32);
            this.particles.add(new Particle(pos, vel, spin, pSize, lifetime, nowNanos));
        }
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!event.c() || aM_.player == null || aM_.world == null || this.particles.isEmpty()) {
            return;
        }

        Draw3DProcessor d3d = event.e();
        if (d3d == null) return;

        float delta = event.g();
        long nowNanos = System.nanoTime();
        float sizeMult = this.size.c().floatValue();

        int baseColor;
        if (this.colorMode.l("Кастом")) {
            baseColor = this.customColor.c().intValue();
        } else {
            baseColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        }

        for (Particle p : this.particles) {
            float alphaFactor = p.alpha(nowNanos);
            if (alphaFactor <= 0.01f) continue;

            Vec3d renderPos = p.previous.lerp(p.position, delta);
            Vec3d rot = p.previousRotation.lerp(p.rotation, delta);
            float finalSize = p.size * sizeMult;

            Quaternionf q = new Quaternionf().rotationXYZ((float) rot.x, (float) rot.y, (float) rot.z);
            Vec3d[] transformedCorners = new Vec3d[CORNERS.length];
            for (int i = 0; i < CORNERS.length; i++) {
                float[] c = CORNERS[i];
                Vector3f v = new Vector3f(c[0], c[1], c[2]).rotate(q).mul(finalSize);
                transformedCorners[i] = renderPos.add(v.x, v.y, v.z);
            }

            int col;
            if (this.colorMode.l("Радуга")) {
                float hue = ((nowNanos / 1000000L + (long) (renderPos.x * 100)) % 3000L) / 3000.0f;
                col = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f);
            } else {
                col = baseColor;
            }

            int edgeCol = ColorUtil.a(col, (int) (220 * alphaFactor));
            int diagCol = ColorUtil.a(col, (int) (80 * alphaFactor));

            // Outer edges
            for (int[] edge : EDGES) {
                d3d.a(event.h(), transformedCorners[edge[0]], transformedCorners[edge[1]], null, edgeCol, 1.6f);
            }

            // Inner holographic diagonals
            for (int[] diag : DIAGONALS) {
                d3d.a(event.h(), transformedCorners[diag[0]], transformedCorners[diag[1]], null, diagCol, 1.0f);
            }
        }
    }

    private static final class Particle {
        private Vec3d position;
        private Vec3d previous;
        private Vec3d velocity;
        private Vec3d rotation = Vec3d.ZERO;
        private Vec3d previousRotation = Vec3d.ZERO;
        private Vec3d spin;
        private final float size;
        private final long lifetimeMs;
        private final long birthNanos;

        Particle(Vec3d pos, Vec3d vel, Vec3d spin, float size, long lifetimeMs, long birthNanos) {
            this.position = pos;
            this.previous = pos;
            this.velocity = vel;
            this.spin = spin;
            this.size = size;
            this.lifetimeMs = lifetimeMs;
            this.birthNanos = birthNanos;
        }

        boolean expired(long nowNanos) {
            return elapsed(nowNanos) >= this.lifetimeMs;
        }

        long elapsed(long nowNanos) {
            return Math.max(0L, (nowNanos - this.birthNanos) / 1000000L);
        }

        float alpha(long nowNanos) {
            long el = elapsed(nowNanos);
            if (el >= this.lifetimeMs) return 0.0f;
            float in = (float) el / (float) FADE_MS;
            float out = (float) (this.lifetimeMs - el) / (float) FADE_MS;
            return MathHelper.clamp(Math.min(in, out), 0.0f, 1.0f);
        }

        void tick() {
            this.previous = this.position;
            this.previousRotation = this.rotation;
            this.position = this.position.add(this.velocity);
            this.rotation = this.rotation.add(this.spin);
            this.velocity = this.velocity.multiply(0.98);
            this.spin = this.spin.multiply(0.98);
        }
    }
}
