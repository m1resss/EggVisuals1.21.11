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
import delta.setting.BooleanSetting;
import delta.setting.ColorSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleRegister(a = "Trail", b = "Мультяшный шлейф и эффекты позади игрока при движении", c = Category.Render)
public class Trail extends Module implements Interface {

    private final ModeSetting style = new ModeSetting("Стиль", "Мультяшный", "Мультяшный", "Штрихи", "Пузыри", "Звездочки", "Линия");
    private final BooleanSetting segmented = new BooleanSetting("Прерывистый", true);
    private final SliderSetting dashLength = (SliderSetting) new SliderSetting("Длина штриха", 0.65f, 0.15f, 2.0f, 0.05f).a(() -> Boolean.valueOf(this.segmented.h()));
    private final SliderSetting gapLength = (SliderSetting) new SliderSetting("Промежуток", 0.45f, 0.1f, 1.5f, 0.05f).a(() -> Boolean.valueOf(this.segmented.h()));
    private final BooleanSetting cartoonOutline = new BooleanSetting("Мультяшная обводка", true);
    private final BooleanSetting spawnParticles = new BooleanSetting("Частицы", true);

    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Кастом", "Радуга");
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFFFF70A6).a(() -> Boolean.valueOf(this.colorMode.l("Кастом")));
    private final SliderSetting lifetime = new SliderSetting("Длина шлейфа", 1.4f, 0.3f, 4.0f, 0.1f);
    private final SliderSetting thickness = new SliderSetting("Толщина", 3.5f, 1.0f, 8.0f, 0.5f);
    private final SliderSetting heightOffset = new SliderSetting("Высота", 0.35f, 0.0f, 1.8f, 0.05f);
    private final BooleanSetting firstPerson = new BooleanSetting("От 1-го лица", true);

    private final List<TrailPoint> points = new ArrayList<>();
    private final List<CartoonParticle> toonParticles = new ArrayList<>();
    private long lastParticleSpawn = 0L;

    public Trail() {
        a(this.style, this.segmented, this.dashLength, this.gapLength, this.cartoonOutline,
          this.spawnParticles, this.colorMode, this.customColor, this.lifetime, this.thickness,
          this.heightOffset, this.firstPerson);
    }

    @Override
    public void c() {
        super.c();
        this.points.clear();
        this.toonParticles.clear();
    }

    private static class TrailPoint {
        final Vec3d pos;
        final long time;

        TrailPoint(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }

    private enum ParticleType {
        STAR,
        BUBBLE,
        PUFF
    }

    private static class CartoonParticle {
        Vec3d pos;
        final Vec3d vel;
        final long birthTime;
        final long maxLifeMs;
        final float baseSize;
        float rot;
        final float rotSpeed;
        final ParticleType type;

        CartoonParticle(Vec3d pos, Vec3d vel, long birthTime, long maxLifeMs, float baseSize, float rot, float rotSpeed, ParticleType type) {
            this.pos = pos;
            this.vel = vel;
            this.birthTime = birthTime;
            this.maxLifeMs = maxLifeMs;
            this.baseSize = baseSize;
            this.rot = rot;
            this.rotSpeed = rotSpeed;
            this.type = type;
        }

        boolean tick(long now) {
            if (now - this.birthTime > this.maxLifeMs) {
                return false;
            }
            this.pos = this.pos.add(this.vel);
            this.rot += this.rotSpeed;
            return true;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null) {
            this.points.clear();
            this.toonParticles.clear();
            return;
        }

        updatePoints();
        updateToonParticles();
    }

    private void updatePoints() {
        long now = System.currentTimeMillis();
        long maxLifetimeMs = (long) (this.lifetime.h().floatValue() * 1000.0f);

        Iterator<TrailPoint> it = this.points.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > maxLifetimeMs) {
                it.remove();
            }
        }

        double curX = aM_.player.getX();
        double curY = aM_.player.getY() + this.heightOffset.h().floatValue();
        double curZ = aM_.player.getZ();
        Vec3d curPos = new Vec3d(curX, curY, curZ);

        if (this.points.isEmpty()) {
            this.points.add(new TrailPoint(curPos, now));
        } else {
            TrailPoint last = this.points.get(this.points.size() - 1);
            double distSq = curPos.squaredDistanceTo(last.pos);
            if (distSq >= 0.001) {
                this.points.add(new TrailPoint(curPos, now));

                // Spawn cartoon particles when moving
                if (Boolean.TRUE.equals(this.spawnParticles.h()) && now - this.lastParticleSpawn > 140L) {
                    spawnToonParticle(curPos, now);
                    this.lastParticleSpawn = now;
                }
            }
        }
    }

    private void spawnToonParticle(Vec3d basePos, long now) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vec3d pos = basePos.add(
                rnd.nextDouble(-0.25, 0.25),
                rnd.nextDouble(-0.1, 0.25),
                rnd.nextDouble(-0.25, 0.25)
        );
        Vec3d vel = new Vec3d(
                rnd.nextDouble(-0.015, 0.015),
                rnd.nextDouble(0.015, 0.04), // Upward float
                rnd.nextDouble(-0.015, 0.015)
        );
        long life = (long) rnd.nextDouble(600, 1000);
        float size = (float) rnd.nextDouble(0.12, 0.22);
        float rSpeed = (float) rnd.nextDouble(-0.15, 0.15);

        ParticleType pType;
        String curStyle = this.style.h();
        if ("Звездочки".equals(curStyle)) {
            pType = ParticleType.STAR;
        } else if ("Пузыри".equals(curStyle)) {
            pType = ParticleType.BUBBLE;
        } else {
            int pick = rnd.nextInt(3);
            pType = pick == 0 ? ParticleType.STAR : (pick == 1 ? ParticleType.BUBBLE : ParticleType.PUFF);
        }

        this.toonParticles.add(new CartoonParticle(pos, vel, now, life, size, (float) rnd.nextDouble(0, Math.PI * 2), rSpeed, pType));
        if (this.toonParticles.size() > 80) {
            this.toonParticles.remove(0);
        }
    }

    private void updateToonParticles() {
        long now = System.currentTimeMillis();
        this.toonParticles.removeIf(p -> !p.tick(now));
    }

    @EventTarget
    public void onRender(DrawEvent event) {
        if (!m() || !event.c() || aM_.player == null || aM_.world == null) {
            return;
        }

        if (aM_.options.getPerspective().isFirstPerson() && !Boolean.TRUE.equals(this.firstPerson.h())) {
            return;
        }

        updatePoints();
        updateToonParticles();

        long now = System.currentTimeMillis();
        long maxLifetimeMs = (long) (this.lifetime.h().floatValue() * 1000.0f);
        float lineThick = this.thickness.h().floatValue();
        boolean outline = Boolean.TRUE.equals(this.cartoonOutline.h());

        int baseColor;
        if (this.colorMode.l("Кастом")) {
            baseColor = this.customColor.h().intValue();
        } else {
            baseColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        }

        // 1. Render Trail Dashes / Ribbons
        String curStyle = this.style.h();
        boolean onlyParticles = "Пузыри".equals(curStyle) || "Звездочки".equals(curStyle);

        if (!onlyParticles && this.points.size() >= 2) {
            boolean isSegmented = Boolean.TRUE.equals(this.segmented.h()) && !"Линия".equals(curStyle);
            float dashLen = Math.max(0.1f, this.dashLength.h().floatValue());
            float gapLen = Math.max(0.05f, this.gapLength.h().floatValue());
            float cycleLen = dashLen + gapLen;

            // Pre-calculate cumulative distance for clean dash alignment
            int count = this.points.size();
            double[] cumDist = new double[count];
            cumDist[0] = 0.0;
            for (int i = 1; i < count; i++) {
                cumDist[i] = cumDist[i - 1] + this.points.get(i - 1).pos.distanceTo(this.points.get(i).pos);
            }

            for (int i = 0; i < count - 1; i++) {
                TrailPoint p1 = this.points.get(i);
                TrailPoint p2 = this.points.get(i + 1);

                double segLen = cumDist[i + 1] - cumDist[i];
                if (segLen < 1e-4) continue;

                float age = MathHelper.clamp((float) (now - p1.time) / (float) maxLifetimeMs, 0.0f, 1.0f);
                float alphaFactor = (1.0f - age) * (1.0f - age);
                int alpha = (int) (alphaFactor * 255.0f);
                if (alpha <= 4) continue;

                int col;
                if (this.colorMode.l("Радуга")) {
                    float hue = ((p1.time % 2000L) / 2000.0f);
                    int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
                    col = ColorUtil.a(rgb, alpha);
                } else {
                    col = ColorUtil.a(baseColor, alpha);
                }

                if (!isSegmented) {
                    // Continuous cartoon stroke
                    drawCartoonSegment(event, p1.pos, p2.pos, col, alpha, lineThick, outline);
                } else {
                    // Subdivided dashed cartoon stroke
                    double s0 = cumDist[i];
                    double s1 = cumDist[i + 1];

                    // Step across this segment with 0.08m resolution
                    int steps = Math.max(1, (int) Math.ceil(segLen / 0.08));
                    Vec3d subStart = null;

                    for (int s = 0; s <= steps; s++) {
                        double t = (double) s / (double) steps;
                        double dist = s0 + t * segLen;
                        double mod = dist % cycleLen;
                        boolean inDash = mod < dashLen;

                        Vec3d curPoint = p1.pos.lerp(p2.pos, t);

                        if (inDash) {
                            if (subStart == null) {
                                subStart = curPoint;
                            }
                        } else {
                            if (subStart != null) {
                                drawCartoonSegment(event, subStart, curPoint, col, alpha, lineThick, outline);
                                subStart = null;
                            }
                        }
                    }

                    if (subStart != null) {
                        drawCartoonSegment(event, subStart, p2.pos, col, alpha, lineThick, outline);
                    }
                }
            }
        }

        // 2. Render Cartoon Particles (Stars, Bubbles, Puffs)
        if (Boolean.TRUE.equals(this.spawnParticles.h()) || onlyParticles) {
            for (CartoonParticle p : this.toonParticles) {
                float lifeRatio = MathHelper.clamp((float) (now - p.birthTime) / (float) p.maxLifeMs, 0.0f, 1.0f);
                // Cartoon bounce easing: pop in, stay, pop out
                float popScale = (float) Math.sin(lifeRatio * Math.PI);
                float curSize = p.baseSize * popScale;
                if (curSize <= 0.01f) continue;

                float alphaFactor = (1.0f - lifeRatio * lifeRatio);
                int alpha = (int) (alphaFactor * 255.0f);
                if (alpha <= 4) continue;

                int pCol;
                if (this.colorMode.l("Радуга")) {
                    float hue = (((now + (long) (p.pos.x * 50)) % 2000L) / 2000.0f);
                    int rgb = Color.HSBtoRGB(hue, 0.9f, 1.0f);
                    pCol = ColorUtil.a(rgb, alpha);
                } else {
                    pCol = ColorUtil.a(baseColor, alpha);
                }

                drawCartoonShape(event, p.pos, curSize, p.rot, p.type, pCol, alpha, outline);
            }
        }
    }

    private void drawCartoonSegment(DrawEvent event, Vec3d start, Vec3d end, int color, int alpha, float width, boolean outline) {
        if (outline) {
            // Dark cel-shaded outline pass (thicker line behind)
            int outlineColor = ColorUtil.a(0x0A0A0A, (int) (alpha * 0.95f));
            event.e().a(event.h(), start, end, null, outlineColor, width + 3.0f);
        }

        // Vibrant core line
        event.e().a(event.h(), start, end, null, color, width);

        // Glossy cartoon specular top highlight
        int highlightColor = ColorUtil.a(0xFFFFFF, (int) (alpha * 0.6f));
        Vec3d hlStart = start.add(0.0, 0.035, 0.0);
        Vec3d hlEnd = end.add(0.0, 0.035, 0.0);
        event.e().a(event.h(), hlStart, hlEnd, null, highlightColor, Math.max(1.0f, width * 0.45f));
    }

    private void drawCartoonShape(DrawEvent event, Vec3d pos, float size, float rot, ParticleType type, int color, int alpha, boolean outline) {
        float cos = MathHelper.cos(rot);
        float sin = MathHelper.sin(rot);

        if (type == ParticleType.STAR) {
            // 4-pointed 3D cartoon star
            float armX = cos * size;
            float armZ = sin * size;

            Vec3d h1 = pos.add(armX, 0.0, armZ);
            Vec3d h2 = pos.add(-armX, 0.0, -armZ);
            Vec3d v1 = pos.add(0.0, size * 1.15f, 0.0);
            Vec3d v2 = pos.add(0.0, -size * 1.15f, 0.0);

            // Diagonal mini sparkles
            float dSize = size * 0.45f;
            float dX = -sin * dSize;
            float dZ = cos * dSize;
            Vec3d d1 = pos.add(dX, dSize, dZ);
            Vec3d d2 = pos.add(-dX, -dSize, -dZ);

            if (outline) {
                int outCol = ColorUtil.a(0x050505, (int) (alpha * 0.9f));
                event.e().a(event.h(), h1, h2, null, outCol, 4.5f);
                event.e().a(event.h(), v1, v2, null, outCol, 4.5f);
                event.e().a(event.h(), d1, d2, null, outCol, 3.5f);
            }

            event.e().a(event.h(), h1, h2, null, color, 2.5f);
            event.e().a(event.h(), v1, v2, null, color, 2.5f);
            event.e().a(event.h(), d1, d2, null, color, 1.8f);

            // Center spark
            int whiteSpark = ColorUtil.a(0xFFFFFF, (int) (alpha * 0.85f));
            event.e().a(event.h(), pos.add(0, -0.04, 0), pos.add(0, 0.04, 0), null, whiteSpark, 3.0f);

        } else if (type == ParticleType.BUBBLE) {
            // Cute 3D cartoon bubble (octagonal / diamond rings)
            float r = size * 0.85f;
            Vec3d t = pos.add(0, r, 0);
            Vec3d b = pos.add(0, -r, 0);
            Vec3d r1 = pos.add(cos * r, 0, sin * r);
            Vec3d r2 = pos.add(-sin * r, 0, cos * r);
            Vec3d r3 = pos.add(-cos * r, 0, -sin * r);
            Vec3d r4 = pos.add(sin * r, 0, -cos * r);

            // Outline loop
            if (outline) {
                int outCol = ColorUtil.a(0x050505, (int) (alpha * 0.9f));
                event.e().a(event.h(), r1, r2, null, outCol, 3.5f);
                event.e().a(event.h(), r2, r3, null, outCol, 3.5f);
                event.e().a(event.h(), r3, r4, null, outCol, 3.5f);
                event.e().a(event.h(), r4, r1, null, outCol, 3.5f);
                event.e().a(event.h(), t, b, null, outCol, 3.0f);
            }

            // Core bubble lines
            event.e().a(event.h(), r1, r2, null, color, 2.0f);
            event.e().a(event.h(), r2, r3, null, color, 2.0f);
            event.e().a(event.h(), r3, r4, null, color, 2.0f);
            event.e().a(event.h(), r4, r1, null, color, 2.0f);
            event.e().a(event.h(), t, b, null, color, 1.5f);

            // Specular reflection glint (top right)
            int glintCol = ColorUtil.a(0xFFFFFF, (int) (alpha * 0.9f));
            Vec3d glint1 = pos.add(r * 0.35, r * 0.45, r * 0.35);
            Vec3d glint2 = pos.add(r * 0.55, r * 0.65, r * 0.55);
            event.e().a(event.h(), glint1, glint2, null, glintCol, 2.5f);

        } else { // PUFF
            // Soft cartoon smoke puff
            float r = size * 0.75f;
            Vec3d pTop = pos.add(0, r, 0);
            Vec3d pBot = pos.add(0, -r, 0);
            Vec3d pLeft = pos.add(cos * r, 0, sin * r);
            Vec3d pRight = pos.add(-cos * r, 0, -sin * r);

            if (outline) {
                int outCol = ColorUtil.a(0x101010, (int) (alpha * 0.8f));
                event.e().a(event.h(), pLeft, pTop, null, outCol, 3.5f);
                event.e().a(event.h(), pTop, pRight, null, outCol, 3.5f);
                event.e().a(event.h(), pRight, pBot, null, outCol, 3.5f);
                event.e().a(event.h(), pBot, pLeft, null, outCol, 3.5f);
            }

            event.e().a(event.h(), pLeft, pTop, null, color, 2.0f);
            event.e().a(event.h(), pTop, pRight, null, color, 2.0f);
            event.e().a(event.h(), pRight, pBot, null, color, 2.0f);
            event.e().a(event.h(), pBot, pLeft, null, color, 2.0f);
        }
    }
}
