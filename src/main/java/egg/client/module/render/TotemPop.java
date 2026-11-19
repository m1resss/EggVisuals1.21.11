package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TotemPop extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Shockwave", "Выбор эффекта при срабатывании тотема", "Shockwave", "Конфетти");

    private final BooleanSetting groundWave = new BooleanSetting("Круглая волна", true, "Отображение расходящихся колец волны на земле");
    private final SliderSetting shockwaveRings = new SliderSetting("Кольца волны", 2.0, 1.0, 3.0, 1.0, "Количество расходящихся колец волны");

    private final BooleanSetting blockImpulse = new BooleanSetting("Импульс блоков", true, "Анимированный подброс блоков земли при взрыве волны");
    private final SliderSetting blockLift = new SliderSetting("Высота импульса", 0.40, 0.15, 0.90, 0.05, "Высота волны подброшенных блоков");

    private final ModeSetting shockwaveColorMode = new ModeSetting("Цвет волны", "Тотем", "Режим цвета ударной волны", "Тотем", "Тема", "Кастом");
    private final ColorSetting shockwaveCustomColor = new ColorSetting("Цвет", 0xFFFFD700, "Кастомный цвет ударной волны");
    private final SliderSetting shockwaveRadius = new SliderSetting("Радиус волны", 3.8, 1.5, 7.0, 0.1, "Максимальный радиус расхождения ударной волны");
    private final SliderSetting shockwaveSpeed = new SliderSetting("Скорость волны", 1.2, 0.5, 3.0, 0.1, "Скорость расширения ударной волны");
    private final BooleanSetting shockwaveGlow = new BooleanSetting("Свечение волны", true, "Неоновое свечение гребня ударной волны");

    private final SliderSetting count = new SliderSetting("Количество", 100, 30, 250, 10, "Число 3D частиц конфетти при выстреле");
    private final SliderSetting speed = new SliderSetting("Сила выстрела", 1.2f, 0.4f, 2.5f, 0.1f, "Начальная скорость разлета конфетти");
    private final SliderSetting gravity = new SliderSetting("Гравитация", 1.0f, 0.2f, 2.0f, 0.1f, "Сила тяжести при падении");
    private final SliderSetting flutter = new SliderSetting("Парение (флаттер)", 1.2f, 0.0f, 2.0f, 0.1f, "Аэродинамическое покачивание и скольжение бумаги");
    private final SliderSetting lifetime = new SliderSetting("Время жизни (сек)", 4.5f, 1.5f, 8.0f, 0.5f, "Сколько секунд конфетти кружится до исчезновения");
    private final BooleanSetting glow = new BooleanSetting("Свечение кружков", true, "Неоновое свечение желтых круглых конфетти");

    private final BooleanSetting cancelVanilla = new BooleanSetting("Скрыть дефолт частицы", true, "Отключает стандартные 2D частицы тотема");

    private final List<ConfettiParticle> particles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    private static final int[] GREEN_PALETTE = {
            0x2ECC71,
            0x10B981,
            0x22C55E,
            0x4ADE80,
            0x16A34A,
            0x84CC16,
            0x059669
    };

    private static final int[] YELLOW_PALETTE = {
            0xFFD700,
            0xFFFF33,
            0xFFE033,
            0xFFF566
    };

    private static final int SHOCKWAVE_SEGS = 48;
    private static final float[] SW_COS = new float[SHOCKWAVE_SEGS + 1];
    private static final float[] SW_SIN = new float[SHOCKWAVE_SEGS + 1];

    private static final int CIRCLE_SEGS = 12;
    private static final float[] COS_TABLE = new float[CIRCLE_SEGS + 1];
    private static final float[] SIN_TABLE = new float[CIRCLE_SEGS + 1];

    static {
        for (int i = 0; i <= SHOCKWAVE_SEGS; i++) {
            double ang = (i * 2.0 * Math.PI) / SHOCKWAVE_SEGS;
            SW_COS[i] = (float) Math.cos(ang);
            SW_SIN[i] = (float) Math.sin(ang);
        }
        for (int i = 0; i <= CIRCLE_SEGS; i++) {
            double ang = (i * 2.0 * Math.PI) / CIRCLE_SEGS;
            COS_TABLE[i] = (float) Math.cos(ang);
            SIN_TABLE[i] = (float) Math.sin(ang);
        }
    }

    public TotemPop() {
        super("TotemPop", "Кастомные эффекты при срабатывании тотема (Shockwave и конфетти)", Category.ESP);

        this.groundWave.setVisible(() -> "Shockwave".equals(this.mode.get()));
        this.shockwaveRings.setVisible(() -> "Shockwave".equals(this.mode.get()) && this.groundWave.isEnabled());
        this.blockImpulse.setVisible(() -> "Shockwave".equals(this.mode.get()));
        this.blockLift.setVisible(() -> "Shockwave".equals(this.mode.get()) && this.blockImpulse.isEnabled());
        this.shockwaveColorMode.setVisible(() -> "Shockwave".equals(this.mode.get()) && (this.groundWave.isEnabled() || this.blockImpulse.isEnabled()));
        this.shockwaveCustomColor.setVisible(() -> "Shockwave".equals(this.mode.get()) && (this.groundWave.isEnabled() || this.blockImpulse.isEnabled()) && "Кастом".equals(this.shockwaveColorMode.get()));
        this.shockwaveRadius.setVisible(() -> "Shockwave".equals(this.mode.get()) && (this.groundWave.isEnabled() || this.blockImpulse.isEnabled()));
        this.shockwaveSpeed.setVisible(() -> "Shockwave".equals(this.mode.get()) && (this.groundWave.isEnabled() || this.blockImpulse.isEnabled()));
        this.shockwaveGlow.setVisible(() -> "Shockwave".equals(this.mode.get()) && (this.groundWave.isEnabled() || this.blockImpulse.isEnabled()));

        this.count.setVisible(() -> "Конфетти".equals(this.mode.get()));
        this.speed.setVisible(() -> "Конфетти".equals(this.mode.get()));
        this.gravity.setVisible(() -> "Конфетти".equals(this.mode.get()));
        this.flutter.setVisible(() -> "Конфетти".equals(this.mode.get()));
        this.lifetime.setVisible(() -> "Конфетти".equals(this.mode.get()));
        this.glow.setVisible(() -> "Конфетти".equals(this.mode.get()));

        addSettings(
                this.mode,
                this.groundWave, this.shockwaveRings,
                this.blockImpulse, this.blockLift,
                this.shockwaveColorMode, this.shockwaveCustomColor, this.shockwaveRadius, this.shockwaveSpeed, this.shockwaveGlow,
                this.count, this.speed, this.gravity, this.flutter, this.lifetime, this.glow,
                this.cancelVanilla
        );
    }

    public boolean shouldCancelVanilla() {
        return isEnabled() && cancelVanilla.isEnabled();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        synchronized (particles) {
            particles.clear();
        }
        synchronized (shockwaves) {
            shockwaves.clear();
        }
        this.lastFrameNanos = 0L;
    }

    public void onTotemPop(Entity entity) {
        if (!isEnabled() || entity == null) return;
        Vec3d origin = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        spawnBurst(origin);
    }

    public void spawnBurst(Vec3d origin) {
        String m = this.mode.get();

        if ("Shockwave".equals(m)) {
            if (!this.groundWave.isEnabled() && !this.blockImpulse.isEnabled()) return;

            int waveCol;
            String cm = this.shockwaveColorMode.get();
            if ("Тема".equals(cm)) {
                waveCol = Theme.getAccent();
            } else if ("Кастом".equals(cm)) {
                waveCol = this.shockwaveCustomColor.get();
            } else {
                waveCol = 0xFFFFD700;
            }

            float rad = (float) this.shockwaveRadius.get();
            float spd = (float) this.shockwaveSpeed.get();
            float life = Math.max(0.45f, 0.95f / spd);
            int rings = this.groundWave.isEnabled() ? (int) this.shockwaveRings.get() : 0;

            List<BlockPos> blocks = new ArrayList<>();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (this.blockImpulse.isEnabled() && mc.world != null) {
                int centerBx = MathHelper.floor(origin.x);
                int centerBy = MathHelper.floor(origin.y);
                int centerBz = MathHelper.floor(origin.z);
                int r = (int) Math.ceil(rad) + 1;
                BlockPos.Mutable mut = new BlockPos.Mutable();

                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double distSq = dx * dx + dz * dz;
                        if (distSq > (rad + 0.6) * (rad + 0.6)) continue;
                        int bx = centerBx + dx;
                        int bz = centerBz + dz;
                        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                        for (int dy = 2; dy >= -3; dy--) {
                            mut.set(bx, centerBy + dy, bz);
                            BlockState s = mc.world.getBlockState(mut);
                            if (s.isAir() || !s.blocksMovement() || !s.getFluidState().isEmpty()) continue;
                            BlockPos above = mut.up();
                            BlockState sAbove = mc.world.getBlockState(above);
                            if (!sAbove.blocksMovement() && sAbove.getFluidState().isEmpty()) {
                                blocks.add(mut.toImmutable());
                                break;
                            }
                        }
                    }
                }
            }

            synchronized (shockwaves) {
                shockwaves.add(new Shockwave(origin.x, origin.y + 0.015, origin.z, life, rad, waveCol, rings, blocks));
            }
        } else {
            spawnConfetti(new Vec3d(origin.x, origin.y + 1.1, origin.z));
        }
    }

    private void spawnConfetti(Vec3d origin) {
        int num = count.getValueInt();
        float speedMul = speed.getValueFloat();
        float lifeSec = lifetime.getValueFloat();

        for (int i = 0; i < num; i++) {
            int typeIndex = i % 4;
            Shape shape = Shape.values()[typeIndex];

            ConfettiParticle p = new ConfettiParticle();
            p.shape = shape;
            p.x = origin.x + (random.nextDouble() - 0.5) * 0.2;
            p.y = origin.y + (random.nextDouble() - 0.5) * 0.2;
            p.z = origin.z + (random.nextDouble() - 0.5) * 0.2;

            double azimuth = random.nextDouble() * Math.PI * 2.0;
            double elevation = 0.25 + random.nextDouble() * 1.15;
            double vMag = (0.16 + random.nextDouble() * 0.30) * speedMul;

            p.vx = Math.cos(azimuth) * Math.cos(elevation) * vMag;
            p.vz = Math.sin(azimuth) * Math.cos(elevation) * vMag;
            p.vy = Math.sin(elevation) * vMag + (0.10 + random.nextDouble() * 0.16) * speedMul;

            p.terminalVelocity = 0.038f + random.nextFloat() * 0.035f;

            p.pitch = random.nextFloat() * 360.0f;
            p.yaw = random.nextFloat() * 360.0f;
            p.roll = random.nextFloat() * 360.0f;

            p.rotSpeedPitch = (random.nextFloat() - 0.5f) * 450.0f;
            p.rotSpeedYaw = (random.nextFloat() - 0.5f) * 450.0f;
            p.rotSpeedRoll = (random.nextFloat() - 0.5f) * 450.0f;

            p.flutterPhaseX = random.nextFloat() * 6.28f;
            p.flutterPhaseZ = random.nextFloat() * 6.28f;
            p.flutterFreqX = 3.5f + random.nextFloat() * 3.5f;
            p.flutterFreqZ = 3.0f + random.nextFloat() * 3.5f;
            p.flutterAmp = 0.015f + random.nextFloat() * 0.020f;

            p.age = 0.0f;
            p.maxAge = lifeSec * (0.85f + random.nextFloat() * 0.35f);

            if (shape == Shape.GLOWING_CIRCLE) {
                p.radius = 0.045f + random.nextFloat() * 0.035f;
                int baseCol = YELLOW_PALETTE[random.nextInt(YELLOW_PALETTE.length)];
                p.r = (baseCol >> 16) & 0xFF;
                p.g = (baseCol >> 8) & 0xFF;
                p.b = baseCol & 0xFF;
            } else {
                int baseCol = GREEN_PALETTE[random.nextInt(GREEN_PALETTE.length)];
                p.r = (baseCol >> 16) & 0xFF;
                p.g = (baseCol >> 8) & 0xFF;
                p.b = baseCol & 0xFF;

                if (shape == Shape.PARALLELOGRAM) {
                    p.width = 0.08f + random.nextFloat() * 0.05f;
                    p.height = 0.05f + random.nextFloat() * 0.035f;
                    p.shear = p.width * (0.25f + random.nextFloat() * 0.25f);
                } else if (shape == Shape.TRIANGLE) {
                    p.width = 0.09f + random.nextFloat() * 0.05f;
                    p.height = 0.07f + random.nextFloat() * 0.04f;
                    p.shear = (random.nextFloat() - 0.5f) * p.width * 0.4f;
                } else {
                    p.width = 0.045f + random.nextFloat() * 0.025f;
                    p.height = 0.11f + random.nextFloat() * 0.06f;
                }
            }

            synchronized (particles) {
                particles.add(p);
            }
        }
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            synchronized (particles) {
                particles.clear();
            }
            synchronized (shockwaves) {
                shockwaves.clear();
            }
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        renderShockwaves(consumers, camPos, dt);
        renderConfetti(consumers, camPos, dt, mc);
    }

    private void renderShockwaves(VertexConsumerProvider consumers, Vec3d camPos, float dt) {
        synchronized (shockwaves) {
            if (shockwaves.isEmpty()) return;

            Iterator<Shockwave> it = shockwaves.iterator();
            while (it.hasNext()) {
                Shockwave sw = it.next();
                if (!sw.update(dt)) {
                    it.remove();
                }
            }

            if (shockwaves.isEmpty()) return;
            if (!this.groundWave.isEnabled() && !this.blockImpulse.isEnabled()) return;

            VertexConsumer quadBuf = consumers.getBuffer(RenderLayers.debugQuads());
            VertexConsumer glowBuf = shockwaveGlow.isEnabled() ? consumers.getBuffer(RenderLayers.lightning()) : null;

            for (Shockwave sw : shockwaves) {
                float baseProg = sw.age / sw.maxLife;
                float easedMain = 1.0f - (float) Math.pow(1.0f - baseProg, 2.7);
                float currentRMain = Math.max(0.08f, easedMain * sw.maxRadius);

                int cr = (sw.color >> 16) & 0xFF;
                int cg = (sw.color >> 8) & 0xFF;
                int cb = sw.color & 0xFF;

                if (blockImpulse.isEnabled() && !sw.blocks.isEmpty()) {
                    float waveWidth = 1.25f;
                    float maxLift = (float) blockLift.get();
                    float globalBlockAlpha = (1.0f - baseProg);

                    for (int bi = 0; bi < sw.blocks.size(); bi++) {
                        BlockPos bpos = sw.blocks.get(bi);
                        double bCenterX = bpos.getX() + 0.5;
                        double bCenterZ = bpos.getZ() + 0.5;
                        double dx = bCenterX - sw.x;
                        double dz = bCenterZ - sw.z;
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        double diff = currentRMain - dist;
                        if (diff < -0.25 || diff > waveWidth + 0.45) continue;

                        float p = (float) ((diff + 0.25) / (waveWidth + 0.70));
                        float liftSin = (float) Math.sin(p * Math.PI);
                        if (liftSin <= 0.005f) continue;

                        float falloff = (float) Math.max(0.0, 1.0 - (dist / (sw.maxRadius + 0.6)));
                        float lift = liftSin * maxLift * falloff;
                        if (lift <= 0.005f) continue;

                        float x1 = (float) (bpos.getX() - camPos.x);
                        float x2 = x1 + 1.0f;
                        float z1 = (float) (bpos.getZ() - camPos.z);
                        float z2 = z1 + 1.0f;
                        float baseY = (float) (bpos.getY() + 1.001 - camPos.y);
                        float topY = baseY + lift;

                        int fillA = MathHelper.clamp(Math.round(liftSin * globalBlockAlpha * 105.0f), 0, 255);
                        int glowA = MathHelper.clamp(Math.round(liftSin * globalBlockAlpha * 190.0f), 0, 255);

                        quadBuf.vertex(x1, topY, z1).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x1, topY, z2).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x2, topY, z2).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x2, topY, z1).color(cr, cg, cb, fillA);

                        quadBuf.vertex(x1, baseY, z1).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, baseY, z1).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, topY, z1).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x1, topY, z1).color(cr, cg, cb, fillA);

                        quadBuf.vertex(x1, baseY, z2).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, baseY, z2).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, topY, z2).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x1, topY, z2).color(cr, cg, cb, fillA);

                        quadBuf.vertex(x1, baseY, z1).color(cr, cg, cb, 0);
                        quadBuf.vertex(x1, baseY, z2).color(cr, cg, cb, 0);
                        quadBuf.vertex(x1, topY, z2).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x1, topY, z1).color(cr, cg, cb, fillA);

                        quadBuf.vertex(x2, baseY, z1).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, baseY, z2).color(cr, cg, cb, 0);
                        quadBuf.vertex(x2, topY, z2).color(cr, cg, cb, fillA);
                        quadBuf.vertex(x2, topY, z1).color(cr, cg, cb, fillA);

                        if (glowBuf != null) {
                            float edgeInset = 0.035f;
                            glowBuf.vertex(x1 + edgeInset, topY + 0.001f, z1 + edgeInset).color(cr, cg, cb, glowA);
                            glowBuf.vertex(x1 + edgeInset, topY + 0.001f, z2 - edgeInset).color(cr, cg, cb, glowA);
                            glowBuf.vertex(x2 - edgeInset, topY + 0.001f, z2 - edgeInset).color(cr, cg, cb, glowA);
                            glowBuf.vertex(x2 - edgeInset, topY + 0.001f, z1 + edgeInset).color(cr, cg, cb, glowA);
                        }
                    }
                }

                if (this.groundWave.isEnabled()) {
                    for (int i = 0; i < sw.rings; i++) {
                        float delay = i * 0.16f;
                        if (baseProg < delay) continue;
                        float ringProg = (baseProg - delay) / (1.0f - delay);
                        if (ringProg >= 1.0f) continue;

                        float eased = 1.0f - (float) Math.pow(1.0f - ringProg, 2.7);
                        float r = Math.max(0.08f, eased * sw.maxRadius);
                        float ringAlpha = (1.0f - ringProg) * (1.0f - i * 0.22f);
                        if (ringProg < 0.08f) ringAlpha *= (ringProg / 0.08f);

                        float t = 0.38f * (0.85f + 0.45f * eased);
                        float rInner = Math.max(0.01f, r - t);
                        float rCrest = r - t * 0.32f;
                        float rOuter = r;

                        float rx = (float) (sw.x - camPos.x);
                        float ry = (float) (sw.y - camPos.y + i * 0.003);
                        float rz = (float) (sw.z - camPos.z);

                        int innerA = MathHelper.clamp(Math.round(ringAlpha * 70.0f), 0, 255);
                        int crestA = MathHelper.clamp(Math.round(ringAlpha * 220.0f), 0, 255);

                        for (int s = 0; s < SHOCKWAVE_SEGS; s++) {
                            float c0 = SW_COS[s];
                            float s0 = SW_SIN[s];
                            float c1 = SW_COS[s + 1];
                            float s1 = SW_SIN[s + 1];

                            float inX0 = rx + c0 * rInner;
                            float inZ0 = rz + s0 * rInner;
                            float inX1 = rx + c1 * rInner;
                            float inZ1 = rz + s1 * rInner;

                            float crX0 = rx + c0 * rCrest;
                            float crZ0 = rz + s0 * rCrest;
                            float crX1 = rx + c1 * rCrest;
                            float crZ1 = rz + s1 * rCrest;

                            float outX0 = rx + c0 * rOuter;
                            float outZ0 = rz + s0 * rOuter;
                            float outX1 = rx + c1 * rOuter;
                            float outZ1 = rz + s1 * rOuter;

                            quadBuf.vertex(inX0, ry, inZ0).color(cr, cg, cb, innerA);
                            quadBuf.vertex(inX1, ry, inZ1).color(cr, cg, cb, innerA);
                            quadBuf.vertex(crX1, ry, crZ1).color(cr, cg, cb, crestA);
                            quadBuf.vertex(crX0, ry, crZ0).color(cr, cg, cb, crestA);

                            quadBuf.vertex(crX0, ry, crZ0).color(cr, cg, cb, crestA);
                            quadBuf.vertex(crX1, ry, crZ1).color(cr, cg, cb, crestA);
                            quadBuf.vertex(outX1, ry, outZ1).color(cr, cg, cb, 0);
                            quadBuf.vertex(outX0, ry, outZ0).color(cr, cg, cb, 0);

                            if (glowBuf != null) {
                                int glowA = MathHelper.clamp(Math.round(ringAlpha * 140.0f), 0, 255);
                                glowBuf.vertex(inX0, ry + 0.002f, inZ0).color(cr, cg, cb, 0);
                                glowBuf.vertex(inX1, ry + 0.002f, inZ1).color(cr, cg, cb, 0);
                                glowBuf.vertex(crX1, ry + 0.002f, crZ1).color(cr, cg, cb, glowA);
                                glowBuf.vertex(crX0, ry + 0.002f, crZ0).color(cr, cg, cb, glowA);

                                glowBuf.vertex(crX0, ry + 0.002f, crZ0).color(cr, cg, cb, glowA);
                                glowBuf.vertex(crX1, ry + 0.002f, crZ1).color(cr, cg, cb, glowA);
                                glowBuf.vertex(outX1, ry + 0.002f, outZ1).color(cr, cg, cb, 0);
                                glowBuf.vertex(outX0, ry + 0.002f, outZ0).color(cr, cg, cb, 0);
                            }
                        }

                        float domeHeight = (0.35f + ringProg * 0.45f);
                        int domeA = MathHelper.clamp(Math.round(ringAlpha * 55.0f), 0, 255);
                        for (int s = 0; s < SHOCKWAVE_SEGS; s++) {
                            float c0 = SW_COS[s];
                            float s0 = SW_SIN[s];
                            float c1 = SW_COS[s + 1];
                            float s1 = SW_SIN[s + 1];

                            float bX0 = rx + c0 * rCrest;
                            float bZ0 = rz + s0 * rCrest;
                            float bX1 = rx + c1 * rCrest;
                            float bZ1 = rz + s1 * rCrest;

                            float tX0 = rx + c0 * (rCrest * 0.88f);
                            float tZ0 = rz + s0 * (rCrest * 0.88f);
                            float tX1 = rx + c1 * (rCrest * 0.88f);
                            float tZ1 = rz + s1 * (rCrest * 0.88f);

                            quadBuf.vertex(bX0, ry, bZ0).color(cr, cg, cb, domeA);
                            quadBuf.vertex(bX1, ry, bZ1).color(cr, cg, cb, domeA);
                            quadBuf.vertex(tX1, ry + domeHeight, tZ1).color(cr, cg, cb, 0);
                            quadBuf.vertex(tX0, ry + domeHeight, tZ0).color(cr, cg, cb, 0);
                        }
                    }
                }
            }
        }
    }

    private void renderConfetti(VertexConsumerProvider consumers, Vec3d camPos, float dt, MinecraftClient mc) {
        synchronized (particles) {
            if (particles.isEmpty()) return;

            float gravMul = gravity.getValueFloat();
            float flutterMul = flutter.getValueFloat();

            Iterator<ConfettiParticle> it = particles.iterator();
            while (it.hasNext()) {
                ConfettiParticle p = it.next();
                p.age += dt;
                if (p.age >= p.maxAge) {
                    it.remove();
                    continue;
                }

                if (!p.onGround) {
                    double drag = Math.pow(0.92, dt * 20.0);
                    p.vx *= drag;
                    p.vz *= drag;

                    p.vy -= 0.007 * gravMul * (dt * 20.0);

                    if (p.vy < -p.terminalVelocity) {
                        p.vy = MathHelper.lerp(0.08f * (dt * 20.0f), (float) p.vy, -p.terminalVelocity);
                    }

                    float swayX = (float) Math.sin(p.age * p.flutterFreqX + p.flutterPhaseX) * p.flutterAmp * flutterMul;
                    float swayZ = (float) Math.cos(p.age * p.flutterFreqZ + p.flutterPhaseZ) * p.flutterAmp * flutterMul;

                    double nextX = p.x + (p.vx + swayX) * (dt * 20.0);
                    double nextY = p.y + p.vy * (dt * 20.0);
                    double nextZ = p.z + (p.vz + swayZ) * (dt * 20.0);

                    BlockPos bpos = BlockPos.ofFloored(nextX, nextY, nextZ);
                    if (mc.world.isChunkLoaded(bpos.getX() >> 4, bpos.getZ() >> 4)) {
                        BlockState state = mc.world.getBlockState(bpos);
                        if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
                            p.onGround = true;
                            p.vx = 0;
                            p.vy = 0;
                            p.vz = 0;
                            p.y = bpos.getY() + 1.002;
                        } else {
                            p.x = nextX;
                            p.y = nextY;
                            p.z = nextZ;
                        }
                    } else {
                        p.x = nextX;
                        p.y = nextY;
                        p.z = nextZ;
                    }

                    p.pitch += p.rotSpeedPitch * dt;
                    p.yaw += p.rotSpeedYaw * dt;
                    p.roll += p.rotSpeedRoll * dt;

                    float rotDamp = (float) Math.pow(0.985, dt * 20.0);
                    p.rotSpeedPitch *= rotDamp;
                    p.rotSpeedYaw *= rotDamp;
                    p.rotSpeedRoll *= rotDamp;
                } else {
                    p.pitch = MathHelper.lerp(dt * 8.0f, p.pitch, 0.0f);
                    p.roll = MathHelper.lerp(dt * 8.0f, p.roll, 0.0f);
                }
            }

            if (particles.isEmpty()) return;

            VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());
            Vector3f lightDir = new Vector3f(0.25f, 0.95f, 0.18f).normalize();

            for (ConfettiParticle p : particles) {
                float fadeProgress = (p.maxAge - p.age) / 1.2f;
                float alpha = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);
                if (alpha <= 0.005f) continue;

                int alphaInt = Math.round(alpha * 255.0f);

                Quaternionf rot = new Quaternionf()
                        .rotationYXZ((float) Math.toRadians(p.yaw),
                                     (float) Math.toRadians(p.pitch),
                                     (float) Math.toRadians(p.roll));

                Vector3f normal = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
                float dotLight = Math.abs(normal.dot(lightDir));
                float brightness = 0.65f + 0.35f * dotLight;

                float dx = (float) (p.x - camPos.x);
                float dy = (float) (p.y - camPos.y);
                float dz = (float) (p.z - camPos.z);
                Vector3f viewDir = new Vector3f(dx, dy, dz).normalize();
                boolean isFrontFace = normal.dot(viewDir) < 0;
                float faceMul = isFrontFace ? 1.0f : 0.82f;

                int cr = MathHelper.clamp(Math.round(p.r * brightness * faceMul), 0, 255);
                int cg = MathHelper.clamp(Math.round(p.g * brightness * faceMul), 0, 255);
                int cb = MathHelper.clamp(Math.round(p.b * brightness * faceMul), 0, 255);

                if (p.shape == Shape.PARALLELOGRAM) {
                    renderParallelogram(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
                } else if (p.shape == Shape.TRIANGLE) {
                    renderTriangle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
                } else if (p.shape == Shape.RECTANGLE) {
                    renderRectangle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
                } else if (p.shape == Shape.GLOWING_CIRCLE) {
                    renderCircle(quadBuffer, p, rot, camPos, cr, cg, cb, alphaInt);
                }
            }

            if (glow.isEnabled()) {
                VertexConsumer glowBuffer = consumers.getBuffer(RenderLayers.lightning());
                for (ConfettiParticle p : particles) {
                    if (p.shape != Shape.GLOWING_CIRCLE) continue;

                    float fadeProgress = (p.maxAge - p.age) / 1.2f;
                    float alpha = MathHelper.clamp(fadeProgress, 0.0f, 1.0f);
                    if (alpha <= 0.01f) continue;

                    Quaternionf rot = new Quaternionf()
                            .rotationYXZ((float) Math.toRadians(p.yaw),
                                         (float) Math.toRadians(p.pitch),
                                         (float) Math.toRadians(p.roll));

                    int glowAlpha = Math.round(alpha * 140.0f);
                    renderCircleGlow(glowBuffer, p, rot, camPos, glowAlpha);
                }
            }
        }
    }

    private void renderRectangle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;

        emitQuad(buf, p, rot, camPos,
                -hw, -hh,
                 hw, -hh,
                 hw,  hh,
                -hw,  hh,
                r, g, b, a);
    }

    private void renderParallelogram(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;
        float s = p.shear;

        emitQuad(buf, p, rot, camPos,
                -hw,     -hh,
                 hw,     -hh,
                 hw + s,  hh,
                -hw + s,  hh,
                r, g, b, a);
    }

    private void renderTriangle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float hw = p.width * 0.5f;
        float hh = p.height * 0.5f;
        float apex = p.shear;

        emitQuad(buf, p, rot, camPos,
                -hw,  -hh,
                 hw,  -hh,
                 apex, hh,
                 apex, hh,
                r, g, b, a);
    }

    private void renderCircle(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int r, int g, int b, int a) {
        float rad = p.radius;

        for (int i = 0; i < CIRCLE_SEGS; i++) {
            float x1 = COS_TABLE[i] * rad;
            float y1 = SIN_TABLE[i] * rad;
            float x2 = COS_TABLE[i + 1] * rad;
            float y2 = SIN_TABLE[i + 1] * rad;

            emitQuad(buf, p, rot, camPos,
                    0.0f, 0.0f,
                    x1, y1,
                    x2, y2,
                    x2, y2,
                    r, g, b, a);
        }
    }

    private void renderCircleGlow(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos, int a) {
        float innerRad = p.radius * 0.55f;
        float outerRad = p.radius * 2.1f;

        for (int i = 0; i < CIRCLE_SEGS; i++) {
            float inX0 = COS_TABLE[i] * innerRad;
            float inY0 = SIN_TABLE[i] * innerRad;
            float inX1 = COS_TABLE[i + 1] * innerRad;
            float inY1 = SIN_TABLE[i + 1] * innerRad;

            float outX0 = COS_TABLE[i] * outerRad;
            float outY0 = SIN_TABLE[i] * outerRad;
            float outX1 = COS_TABLE[i + 1] * outerRad;
            float outY1 = SIN_TABLE[i + 1] * outerRad;

            emitColoredQuad(buf, p, rot, camPos,
                    inX0, inY0, 255, 235, 75, a,
                    inX1, inY1, 255, 235, 75, a,
                    outX1, outY1, 255, 235, 75, 0,
                    outX0, outY0, 255, 235, 75, 0);
        }
    }

    private void emitQuad(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos,
                          float x0, float y0,
                          float x1, float y1,
                          float x2, float y2,
                          float x3, float y3,
                          int r, int g, int b, int a) {
        Vector3f v0 = new Vector3f(x0, y0, 0.0f).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, 0.0f).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, 0.0f).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, 0.0f).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r, g, b, a);
    }

    private void emitColoredQuad(VertexConsumer buf, ConfettiParticle p, Quaternionf rot, Vec3d camPos,
                                 float x0, float y0, int r0, int g0, int b0, int a0,
                                 float x1, float y1, int r1, int g1, int b1, int a1,
                                 float x2, float y2, int r2, int g2, int b2, int a2,
                                 float x3, float y3, int r3, int g3, int b3, int a3) {
        Vector3f v0 = new Vector3f(x0, y0, 0.0f).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, 0.0f).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, 0.0f).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, 0.0f).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r0, g0, b0, a0);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r1, g1, b1, a1);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r2, g2, b2, a2);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r3, g3, b3, a3);
    }

    public enum Shape {
        PARALLELOGRAM,
        TRIANGLE,
        RECTANGLE,
        GLOWING_CIRCLE
    }

    private static class ConfettiParticle {
        Shape shape;
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotSpeedPitch, rotSpeedYaw, rotSpeedRoll;
        float terminalVelocity;

        float flutterPhaseX, flutterPhaseZ;
        float flutterFreqX, flutterFreqZ;
        float flutterAmp;

        float width, height, shear;
        float radius;
        int r, g, b;

        float age;
        float maxAge;
        boolean onGround = false;
    }

    private static class Shockwave {
        final double x, y, z;
        float age = 0.0f;
        final float maxLife;
        final float maxRadius;
        final int color;
        final int rings;
        final List<BlockPos> blocks;

        Shockwave(double x, double y, double z, float maxLife, float maxRadius, int color, int rings, List<BlockPos> blocks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxLife = maxLife;
            this.maxRadius = maxRadius;
            this.color = color;
            this.rings = rings;
            this.blocks = blocks;
        }

        boolean update(float dt) {
            age += dt;
            return age < maxLife;
        }
    }
}