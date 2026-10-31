package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetESP extends Module {
    private static TargetESP instance;

    private static final Identifier GLOWING_TEXTURE = Identifier.of("egg", "textures/glowing.png");
    private static final Identifier BLOOM_TEXTURE = Identifier.of("egg", "textures/target/bloom.png");

    private static final Identifier[] SNOWFLAKES = new Identifier[10];
    static {
        for (int i = 0; i < 10; i++) {
            SNOWFLAKES[i] = Identifier.of("egg", "textures/world/snowflake_" + (i + 1) + ".png");
        }
    }

    // Snowflake vortex physics constants from sfamelov.txt
    private static final int ORBIT_FLAKES = 28;
    private static final int ORBIT_CRYSTALS = 4;
    private static final float GRAVITY = 2.6F;
    private static final float DRAG = 1.4F;
    private static final float TERMINAL_FALL = 1.15F;
    private static final float GROUND_LIFE = 0.8F;
    private static final float FREE_LIFE = 1.1F;

    private final ModeSetting mode = new ModeSetting("Визуализация цели", "Снежинки", "Стиль Target ESP",
            "Снежинки", "Души", "Кубы", "Молнии", "Разряд");

    private final SliderSetting radius = new SliderSetting("Радиус", 0.95, 0.4, 2.5, 0.05, "Радиус охвата цели");
    private final SliderSetting speed = new SliderSetting("Скорость", 1.8, 0.5, 8.0, 0.1, "Скорость движения частиц");
    private final SliderSetting particleSize = new SliderSetting("Размер частиц", 1.1, 0.3, 2.5, 0.05, "Масштаб частиц");
    private final SliderSetting tailLength = new SliderSetting("Длина хвоста", 16.0, 4.0, 30.0, 1.0, "Длина следа частиц (режим Души)");
    private final BooleanSetting coreGlow = new BooleanSetting("Ядро свечения", true, "Яркое белое ядро частиц");
    private final BooleanSetting hurtColor = new BooleanSetting("Окрас при уроне", true, "Окрашивание в красный цвет при ударе");
    private final SliderSetting maxDistance = new SliderSetting("Дистанция наводки", 30.0, 5.0, 64.0, 1.0, "Максимальная дистанция захвата цели");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true, "Захватывать только игроков");
    private final SliderSetting retainTime = new SliderSetting("Удержание цели", 1.8, 0.2, 5.0, 0.1, "Сколько секунд цель держится при отводе прицела");
    private final BooleanSetting focusSystem = new BooleanSetting("Фокус система", true, "Не переключать цель при случайном сдвиге прицела");

    private LivingEntity target;
    private LivingEntity attachedTarget;
    private float targetRetainTimer = 0.0f;
    private int newTargetHoverTicks = 0;
    private float animProgress = 0.0f;
    private Vec3d lastTargetPos = null;
    private double lastTargetHeight = 1.8;

    // Snowflake vortex particles state
    private final List<SnowflakeParticle> snowflakeParticles = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameTime = 0L;
    private float snowSimTime = 0.0f;

    // Lightning & Discharge state
    private float lightningTime = 0.0f;
    private long lightningLastNanos = 0L;
    private float dischargeRotation = 0.0f;
    private float dischargePulse = 0.0f;
    private int lastTargetHurtTime = 0;
    private int trackedTargetId = -1;

    public TargetESP() {
        super("Target ESP", "Снежный вихрь и астральные проекции вокруг цели", Category.ESP);
        instance = this;

        tailLength.setVisible(() -> "Души".equals(mode.get()));
        coreGlow.setVisible(() -> "Души".equals(mode.get()));
        radius.setVisible(() -> true);

        addSettings(mode, radius, speed, particleSize, tailLength, coreGlow, hurtColor, maxDistance, onlyPlayers, retainTime, focusSystem);
    }

    public static TargetESP getInstance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.target = null;
        this.attachedTarget = null;
        this.targetRetainTimer = 0.0f;
        this.newTargetHoverTicks = 0;
        this.animProgress = 0.0f;
        this.snowflakeParticles.clear();
        this.lightningTime = 0.0f;
        this.lightningLastNanos = 0L;
        this.dischargeRotation = 0.0f;
        this.dischargePulse = 0.0f;
        this.lastTargetHurtTime = 0;
        this.trackedTargetId = -1;
    }

    public void onTick() {
        if (!isEnabled()) {
            this.target = null;
            this.targetRetainTimer = 0.0f;
            this.newTargetHoverTicks = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.target = null;
            this.targetRetainTimer = 0.0f;
            this.newTargetHoverTicks = 0;
            return;
        }

        LivingEntity hovered = findCrosshairTarget();

        if (this.target != null) {
            boolean targetValid = this.target.isAlive() && !this.target.isRemoved()
                    && mc.player.distanceTo(this.target) <= (float) this.maxDistance.get();

            if (!targetValid) {
                this.target = hovered;
                this.targetRetainTimer = (hovered != null) ? (float) this.retainTime.get() : 0.0f;
                this.newTargetHoverTicks = 0;
            } else if (hovered == this.target) {
                this.targetRetainTimer = (float) this.retainTime.get();
                this.newTargetHoverTicks = 0;
            } else if (hovered != null && hovered != this.target) {
                if (!this.focusSystem.get()) {
                    this.target = hovered;
                    this.targetRetainTimer = (float) this.retainTime.get();
                    this.newTargetHoverTicks = 0;
                } else {
                    this.newTargetHoverTicks++;
                    if (this.newTargetHoverTicks >= 6 || this.targetRetainTimer <= 0.0f) {
                        this.target = hovered;
                        this.targetRetainTimer = (float) this.retainTime.get();
                        this.newTargetHoverTicks = 0;
                    }
                }
            } else {
                this.newTargetHoverTicks = 0;
                if (this.targetRetainTimer > 0.0f) {
                    this.targetRetainTimer -= 0.05f;
                }
                if (this.targetRetainTimer <= 0.0f) {
                    this.target = null;
                }
            }
        } else {
            if (hovered != null) {
                this.target = hovered;
                this.targetRetainTimer = (float) this.retainTime.get();
                this.newTargetHoverTicks = 0;
            }
        }

        if (this.target != null) {
            this.lastTargetPos = this.target.getEntityPos();
            this.lastTargetHeight = this.target.getHeight();
            this.animProgress = Math.min(1.0f, this.animProgress + 0.12f);
        } else {
            this.animProgress = Math.max(0.0f, this.animProgress - 0.04f);
        }
    }

    private LivingEntity findCrosshairTarget() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;

        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (isValidTarget(living)) {
                return living;
            }
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        double reach = this.maxDistance.get();
        Vec3d endPos = eyePos.add(lookVec.multiply(reach));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        double maxRayDistSq = reach * reach;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxRayDistSq = eyePos.squaredDistanceTo(blockHit.getPos());
            endPos = blockHit.getPos();
        }

        Box searchBox = mc.player.getBoundingBox().stretch(lookVec.multiply(reach)).expand(1.0);
        LivingEntity closest = null;
        double closestDistSq = maxRayDistSq;

        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBox)) {
            if (entity instanceof LivingEntity living && isValidTarget(living)) {
                Box box = living.getBoundingBox().expand(0.35);
                var opt = box.raycast(eyePos, endPos);
                if (opt.isPresent()) {
                    double distSq = eyePos.squaredDistanceTo(opt.get());
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = living;
                    }
                }
            }
        }

        return closest;
    }

    private boolean isValidTarget(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || entity == mc.player || !entity.isAlive()) {
            return false;
        }
        if (this.onlyPlayers.get() && !(entity instanceof PlayerEntity)) {
            return false;
        }
        if (!mc.player.canSee(entity)) {
            return false;
        }
        double distLimit = this.maxDistance.get();
        return mc.player.getEyePos().squaredDistanceTo(entity.getEntityPos()) <= distLimit * distLimit;
    }

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        float delta = tickCounter != null ? tickCounter.getTickProgress(true) : 0.0f;
        String curMode = this.mode.get();

        int themeColor = Theme.getAccent();
        int hurtCol = 0xFFFF2525;
        float hurtFactor = 0.0f;
        if (this.target != null) {
            float hurtTimer = Math.max(0.0F, this.target.hurtTime - delta);
            hurtFactor = this.hurtColor.get() ? (float) Math.sin(hurtTimer * (Math.PI / 10.0)) * 0.95F : 0.0f;
            hurtFactor = MathHelper.clamp(hurtFactor, 0.0f, 1.0f);
        }
        int activeColor = hurtFactor > 0.01f ? ColorUtil.interpolate(themeColor, hurtCol, hurtFactor) : themeColor;

        float baseRadius = (float) this.radius.get();
        float spd = (float) this.speed.get();
        float sizeMult = (float) this.particleSize.get();
        int steps = Math.max(4, (int) this.tailLength.get());
        long now = System.currentTimeMillis();
        float timeSec = (float) (now % 10000000L) / 1000.0f;

        if ("Снежинки".equals(curMode)) {
            renderSnowflakesVortex(context, delta, spd, baseRadius, sizeMult, activeColor);
            return;
        }

        if (this.animProgress <= 0.01f) return;

        Vec3d center = (this.target != null) ? this.target.getLerpedPos(delta) : this.lastTargetPos;
        if (center == null) return;
        double entityHeight = (this.target != null) ? this.target.getHeight() : this.lastTargetHeight;
        double centerY = center.y + entityHeight * 0.5;

        if ("Кубы".equals(curMode)) {
            renderCubes(context, center.x, centerY, center.z, entityHeight, timeSec, baseRadius, spd, sizeMult, activeColor);
        } else if ("Молнии".equals(curMode)) {
            renderLightning(context, center, entityHeight, delta, spd, baseRadius, sizeMult, activeColor);
        } else if ("Разряд".equals(curMode)) {
            renderDischarge(context, center, entityHeight, delta, spd, baseRadius, sizeMult, activeColor);
        } else {
            renderSouls(context, center.x, centerY, center.z, entityHeight, timeSec, baseRadius, spd, sizeMult, steps, activeColor);
        }
    }

    // -----------------------------------------------------------------------------------------
    // SFAMELOV SAKURA/SNOWFLAKE VORTEX ENGINE
    // -----------------------------------------------------------------------------------------

    private void renderSnowflakesVortex(DrawContext context, float tickDelta, float spd, float baseR, float sizeMult, int activeColor) {
        long nowNanos = System.nanoTime();
        float dt = lastFrameTime == 0L ? 0.016F : MathHelper.clamp((nowNanos - lastFrameTime) / 1.0E9F, 0.001F, 0.05F);
        lastFrameTime = nowNanos;
        snowSimTime += dt;

        LivingEntity currentTarget = (this.target != null && this.target.isAlive()) ? this.target : null;
        if (currentTarget != this.attachedTarget) {
            if (this.attachedTarget != null) {
                releaseSnow();
            }
            this.attachedTarget = currentTarget;
            if (currentTarget != null) {
                spawnSnowOrbit(currentTarget);
            }
        }

        Vec3d center = null;
        float height = 1.8F;
        float width = 0.6F;
        if (this.attachedTarget != null) {
            center = new Vec3d(
                    MathHelper.lerp(tickDelta, this.attachedTarget.lastRenderX, this.attachedTarget.getX()),
                    MathHelper.lerp(tickDelta, this.attachedTarget.lastRenderY, this.attachedTarget.getY()),
                    MathHelper.lerp(tickDelta, this.attachedTarget.lastRenderZ, this.attachedTarget.getZ())
            );
            height = this.attachedTarget.getHeight();
            width = this.attachedTarget.getWidth();
        }

        for (int i = this.snowflakeParticles.size() - 1; i >= 0; i--) {
            SnowflakeParticle p = this.snowflakeParticles.get(i);
            if (p.orbiting && center != null) {
                orbitSnow(p, center, height, width, dt, spd, baseR);
            } else {
                p.orbiting = false;
                fallSnow(p, dt);
            }
            if (p.dead) {
                this.snowflakeParticles.remove(i);
            }
        }

        if (this.snowflakeParticles.isEmpty()) return;

        // Render snowflake particles
        for (SnowflakeParticle p : this.snowflakeParticles) {
            float alpha = p.alpha * p.fade;
            if (alpha <= 0.01F) continue;

            // 1. Soft glowing bloom backdrop
            float glowAlpha = alpha * (p.flower ? 0.38F : 0.18F);
            float glowSize = p.size * 2.8F * sizeMult;
            drawParticle(context, BLOOM_TEXTURE, p.x, p.y, p.z, glowSize, ColorUtil.withAlphaFloat(activeColor, glowAlpha));

            // 2. Crisp snowflake crystal (all transparent RGBA particle textures, no opaque white blocks)
            Identifier flakeTex = SNOWFLAKES[p.textureIdx];
            float flakeSize = (p.flower ? p.size * 1.45F : p.size * 1.05F) * sizeMult;
            int flakeColor = p.flower ? 0xFFFFFFFF : ColorUtil.interpolate(0xFFFFFFFF, activeColor, 0.30F);
            drawParticle(context, flakeTex, p.x, p.y, p.z, flakeSize, ColorUtil.withAlphaFloat(flakeColor, alpha * 0.95F));
        }
    }

    private void spawnSnowOrbit(LivingEntity target) {
        this.snowflakeParticles.removeIf(p -> p.orbiting);
        Random rnd = this.random;
        int total = ORBIT_FLAKES + ORBIT_CRYSTALS;
        for (int i = 0; i < total; i++) {
            SnowflakeParticle p = new SnowflakeParticle();
            p.flower = (i >= ORBIT_FLAKES);
            p.textureIdx = rnd.nextInt(10);
            p.orbiting = true;
            p.angle = rnd.nextFloat() * MathHelper.TAU;
            p.level = i / (float) total + rnd.nextFloat() * 0.05F;
            p.angularSpeed = (1.1F + rnd.nextFloat() * 0.9F) * (p.flower ? 0.65F : 1.0F);
            p.sink = 0.07F + rnd.nextFloat() * 0.06F;
            p.radiusScale = p.flower ? 1.25F + rnd.nextFloat() * 0.15F : 0.95F + rnd.nextFloat() * 0.3F;
            p.phase = rnd.nextFloat() * MathHelper.TAU;
            p.size = p.flower ? 0.22F + rnd.nextFloat() * 0.06F : 0.11F + rnd.nextFloat() * 0.05F;
            p.spinA = rnd.nextFloat() * 360.0F;
            p.spinB = rnd.nextFloat() * 360.0F;
            p.spinSpeedA = (rnd.nextFloat() - 0.5F) * (p.flower ? 90.0F : 420.0F);
            p.spinSpeedB = (rnd.nextFloat() - 0.5F) * (p.flower ? 60.0F : 360.0F);
            p.tint = rnd.nextFloat();
            p.alpha = 0.0F;
            this.snowflakeParticles.add(p);
        }
    }

    private void orbitSnow(SnowflakeParticle p, Vec3d center, float height, float width, float dt, float spd, float baseR) {
        p.angle += p.angularSpeed * dt * spd;
        p.level -= p.sink * dt * spd;
        p.fade = 1.0F;
        if (p.level < 0.0F) {
            p.level += 1.0F;
            p.alpha = 0.0F;
        }
        p.alpha = Math.min(1.0F, p.alpha + dt * 2.2F);
        float edge = Math.min(1.0F, Math.min(p.level / 0.08F, (1.0F - p.level) / 0.08F));
        p.fade = MathHelper.clamp(edge, 0.0F, 1.0F);
        double radius = width * 0.85F * baseR * p.radiusScale * (1.0F + 0.1F * MathHelper.sin(snowSimTime * 1.7F + p.phase));
        double prevX = p.x;
        double prevY = p.y;
        double prevZ = p.z;
        p.x = center.x + Math.cos(p.angle) * radius;
        p.z = center.z + Math.sin(p.angle) * radius;
        p.y = center.y + 0.08 + p.level * height * 1.05 + MathHelper.sin(snowSimTime * 2.3F + p.phase) * 0.05;
        if (p.placed) {
            p.vx = (p.x - prevX) / Math.max(0.001f, dt);
            p.vy = (p.y - prevY) / Math.max(0.001f, dt);
            p.vz = (p.z - prevZ) / Math.max(0.001f, dt);
        }
        p.placed = true;
        p.spinA += p.spinSpeedA * dt;
        p.spinB += p.spinSpeedB * dt;
    }

    private void fallSnow(SnowflakeParticle p, float dt) {
        p.age += dt;
        if (p.grounded) {
            p.groundTime += dt;
            p.fade = 1.0F - MathHelper.clamp((p.groundTime - GROUND_LIFE) / 0.8F, 0.0F, 1.0F);
            if (p.fade <= 0.0F) p.dead = true;
            return;
        }
        float drag = (float) Math.exp(-DRAG * dt);
        p.vx *= drag;
        p.vz *= drag;
        p.vy = Math.max(-TERMINAL_FALL, p.vy * drag - GRAVITY * dt);
        double sway = MathHelper.sin(snowSimTime * 3.1F + p.phase) * 0.55;
        p.x += (p.vx + Math.cos(p.phase) * sway) * dt;
        p.y += p.vy * dt;
        p.z += (p.vz + Math.sin(p.phase) * sway) * dt;
        p.spinA += p.spinSpeedA * dt;
        p.spinB += p.spinSpeedB * dt;
        p.alpha = Math.min(1.0F, p.alpha + dt * 3.0F);
        if (p.y <= p.groundY) {
            p.y = p.groundY;
            p.grounded = true;
            p.spinB = 0.0F;
        }
        p.fade = 1.0F - MathHelper.clamp((p.age - FREE_LIFE) / 0.6F, 0.0F, 1.0F);
        if (p.fade <= 0.0F) p.dead = true;
    }

    private void releaseSnow() {
        // Accelerate existing free flakes so switching targets does not create particle bloat
        for (SnowflakeParticle p : this.snowflakeParticles) {
            if (!p.orbiting) {
                p.fade *= 0.35F;
                p.age = Math.max(p.age, FREE_LIFE * 0.8F);
            }
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        double minY = Double.MAX_VALUE;
        int count = 0;
        for (SnowflakeParticle p : this.snowflakeParticles) {
            if (!p.orbiting) continue;
            sumX += p.x;
            sumZ += p.z;
            minY = Math.min(minY, p.y);
            count++;
        }
        if (count == 0) return;
        double ground = findGround(sumX / count, minY + 0.5, sumZ / count);
        Random rnd = this.random;
        for (SnowflakeParticle p : this.snowflakeParticles) {
            if (!p.orbiting) continue;
            p.orbiting = false;
            p.vx = p.vx * 0.45 + (rnd.nextDouble() - 0.5) * 0.4;
            p.vz = p.vz * 0.45 + (rnd.nextDouble() - 0.5) * 0.4;
            p.vy = 0.18 + rnd.nextDouble() * 0.35;
            p.groundY = ground;
            p.age = 0.0F;
            p.alpha = Math.max(p.alpha * p.fade, 0.4F);
            p.fade = 1.0F;
        }
    }

    private double findGround(double gx, double fromY, double gz) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return fromY - 1.0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int startY = MathHelper.floor(fromY);
        for (int by = startY; by > startY - 10; by--) {
            pos.set(MathHelper.floor(gx), by, MathHelper.floor(gz));
            BlockState state = mc.world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(mc.world, pos);
            if (!shape.isEmpty()) {
                double top = by + shape.getMax(Direction.Axis.Y);
                if (top <= fromY + 0.05) return top + 0.015;
            }
        }
        return fromY - 1.0;
    }

    // -----------------------------------------------------------------------------------------
    // SOULS & CUBES MODES
    // -----------------------------------------------------------------------------------------

    private void renderSouls(DrawContext context, double cx, double cy, double cz, double height,
                             float timeSec, float baseR, float spd, float sizeMult, int steps, int color) {
        double timeMs = timeSec * 1000.0;
        float fade = this.animProgress;
        float expand = (1.0f - fade);
        float currentR = baseR * (1.0f + expand * 1.6f);
        double currentCy = cy + expand * 1.35;
        float hAmp = (float) (height * 0.28) * (1.0f + expand * 0.6f);

        for (int soul = 0; soul < 3; soul++) {
            for (int i = 0; i < steps; i++) {
                float trailFactor = 1.0f - ((float) i / (float) steps);
                double angle = 0.15 * (timeMs - (i * 12.0 / Math.max(0.5, spd))) / 25.0 * spd;
                double s = Math.sin(angle) * currentR;
                double c = Math.cos(angle) * currentR;
                double yWave = Math.sin(angle * 1.5 + soul * 2.0) * hAmp;

                double wx, wy, wz;
                if (soul == 0) {
                    wx = cx + s;
                    wy = currentCy + (c * 0.25 + yWave * 0.35);
                    wz = cz - c;
                } else if (soul == 1) {
                    wx = cx - s;
                    wy = currentCy + (s * 0.25 - yWave * 0.35);
                    wz = cz - c;
                } else {
                    wx = cx - s * 0.7 + c * 0.7;
                    wy = currentCy - yWave * 0.4;
                    wz = cz + c * 0.7 + s * 0.7;
                }

                if (i == 0) {
                    float headSz = 0.35f * sizeMult;
                    float alpha = this.animProgress * 0.95f;

                    drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, headSz * 3.4f, ColorUtil.withAlphaFloat(color, alpha * 0.60f));
                    drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, headSz * 2.2f, ColorUtil.withAlphaFloat(color, alpha * 0.85f));

                    if (this.coreGlow.get()) {
                        drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, headSz * 1.4f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.95f));
                        drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, headSz * 0.8f, ColorUtil.withAlphaFloat(0xFFFFFFFF, 1.0f));
                    }
                } else {
                    float tailSz = 0.22f * sizeMult * (0.25f + 0.75f * trailFactor);
                    float alpha = this.animProgress * (0.08f + 0.65f * trailFactor * trailFactor);

                    drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, tailSz * 2.2f, ColorUtil.withAlphaFloat(color, alpha * 0.60f * trailFactor));

                    if (this.coreGlow.get() && i < steps / 2) {
                        float coreFactor = 1.0f - ((float) i / (steps * 0.5f));
                        drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, tailSz * 1.1f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.85f * coreFactor));
                    }
                }
            }
        }
    }

    private void renderCubes(DrawContext context, double cx, double cy, double cz, double height,
                             float timeSec, float baseR, float spd, float sizeMult, int color) {
        int cubeCount = 8;
        float fade = this.animProgress;
        float expand = (1.0f - fade);
        float currentR = baseR * (1.0f + expand * 2.2f);
        double currentCy = cy + expand * 0.75;
        float hAmp = (float) (height * 0.45);
        float spinMult = spd * (1.0f + expand * 2.2f);
        float sz = (0.28f * sizeMult) * (0.25f + 0.75f * fade);
        float alpha = fade * fade;

        for (int i = 0; i < cubeCount; i++) {
            float phase = (float) (i * (Math.PI * 2.0 / cubeCount));
            float t = timeSec * 0.85f * spinMult + phase;
            double wx = cx + Math.cos(t) * currentR * (1.0f + 0.2f * (float) Math.sin(t * 2.0f));
            double wz = cz + Math.sin(t) * currentR * (1.0f + 0.2f * (float) Math.cos(t * 2.0f));
            double wy = currentCy + Math.sin(t * 1.4f) * hAmp;

            drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, sz * 1.8f, ColorUtil.withAlphaFloat(color, alpha * 0.55f));
            drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, sz * 0.8f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.95f));
        }
    }

    private void drawParticle(DrawContext context, Identifier texture, double worldX, double worldY, double worldZ,
                              float worldSize, int color) {
        if ((color & 0xFF000000) == 0) return;
        Vector2f screen = ProjectUtil.toScreen(worldX, worldY, worldZ);
        if (!ProjectUtil.isOnScreen(screen)) return;

        float scale = ProjectUtil.getLastScale();
        float pSize = Math.max(2.0f, worldSize * scale);
        float half = pSize / 2.0f;
        int isize = Math.max(1, Math.round(pSize));

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                Math.round(screen.x - half),
                Math.round(screen.y - half),
                0.0f, 0.0f,
                isize, isize,
                isize, isize,
                isize, isize,
                color
        );
    }

    // -----------------------------------------------------------------------------------------
    // LIGHTNING & DISCHARGE MODES (From sadnes TargetEspRenderer)
    // -----------------------------------------------------------------------------------------

    private void renderLightning(DrawContext context, Vec3d center, double height, float delta,
                                float spd, float baseR, float sizeMult, int activeColor) {
        if (this.animProgress <= 0.01f) return;
        long nowNanos = System.nanoTime();
        float dt = (this.lightningLastNanos == 0L) ? 0.016F : MathHelper.clamp((nowNanos - this.lightningLastNanos) / 1.0E9F, 0.001F, 0.05F);
        this.lightningLastNanos = nowNanos;
        this.lightningTime += dt * spd * 1.1F;

        LivingEntity curTarget = (this.target != null && this.target.isAlive()) ? this.target : null;
        double width = (curTarget != null) ? curTarget.getWidth() : 0.6;
        float fRadius = (float) (width * 0.95 * baseR);
        float fHeight = (float) height;

        float f9 = this.lightningTime * 6.0F;
        int i2 = (int) f9;
        float f10 = f9 - i2;

        for (int i3 = 0; i3 < 3; i3++) {
            float f11 = this.lightningTime * 1.3F + (float) i3 / 3.0F;
            int i4 = (int) Math.floor(f11);
            float f12 = f11 - i4;
            float f13 = MathHelper.clamp(f12 / 0.08F, 0.0F, 1.0F);
            float f14 = 1.0F - MathHelper.clamp((f12 - 0.42F) / 0.3F, 0.0F, 1.0F);
            float f15 = MathHelper.clamp(0.55F + 0.45F * smoothNoise(i3, 900, 0, i2, f10), 0.0F, 1.0F);
            float f16 = f13 * f14 * f15 * this.animProgress;
            if (f16 <= 0.01F) continue;

            float alpha = f16;
            float f18 = this.lightningTime * 0.9F;
            float f19 = fHeight * 0.16F * (float) Math.sin(this.lightningTime * 1.7F + i3 * 2.1F);
            float f20 = hashNoise(i3, 11, i4) * (float) Math.PI;
            float f21 = 0.6F + 0.5F * Math.abs(hashNoise(i3, 12, i4));
            float f22 = hashNoise(i3, 13, i4) >= 0.0F ? 1.0F : -1.0F;
            float f23 = (float) center.y + fHeight * (0.85F + 0.05F * hashNoise(i3, 14, i4));
            float f24 = fHeight * 0.67F;
            float f25 = fRadius * (1.0F + 0.12F * hashNoise(i3, 15, i4));

            float prevX = 0, prevY = 0, prevZ = 0;
            for (int i5 = 0; i5 <= 3; i5++) {
                float f29 = (float) i5 / 3.0F;
                float f30 = f20 + f18 + f22 * f21 * (float) (Math.PI * 2) * f29;
                float curX = (float) center.x + (float) Math.cos(f30) * f25;
                float curZ = (float) center.z + (float) Math.sin(f30) * f25;
                float curY = f23 - f24 * f29 + f19;

                if (i5 > 0) {
                    int seed = (i3 * 131 + i5) * 17 + i4 * 101 | 1;
                    buildBoltSegment(context, prevX, prevY, prevZ, curX, curY, curZ, 3,
                            sizeMult, alpha, activeColor, seed, 0, i2, f10);
                }
                prevX = curX;
                prevY = curY;
                prevZ = curZ;
            }
        }
    }

    private void buildBoltSegment(DrawContext context, float x1, float y1, float z1,
                                  float x2, float y2, float z2, int depth,
                                  float sizeMult, float alpha, int color,
                                  int seed, int branchCount, int i5, float f10) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (depth > 0 && len >= 0.01F) {
            float dirX = dx / len;
            float dirY = dy / len;
            float dirZ = dz / len;

            float upX = Math.abs(dirY) < 0.9F ? 0.0F : 1.0F;
            float upY = Math.abs(dirY) < 0.9F ? 1.0F : 0.0F;
            float perpX = -dirZ * upY;
            float perpY = dirZ * upX;
            float perpZ = dirX * upY - dirY * upX;
            float pLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
            if (pLen < 1.0E-5F) pLen = 1.0F;
            perpX /= pLen; perpY /= pLen; perpZ /= pLen;

            float normX = dirY * perpZ - dirZ * perpY;
            float normY = dirZ * perpX - dirX * perpZ;
            float normZ = dirX * perpY - dirY * perpX;

            float n1 = smoothNoise(seed, 0, 0, i5, f10);
            float n2 = smoothNoise(seed, 1, 0, i5, f10);
            float disp = len * 0.32F;
            float midX = (x1 + x2) * 0.5F + (perpX * n1 + normX * n2) * disp;
            float midY = (y1 + y2) * 0.5F + (perpY * n1 + normY * n2) * disp;
            float midZ = (z1 + z2) * 0.5F + (perpZ * n1 + normZ * n2) * disp;

            buildBoltSegment(context, x1, y1, z1, midX, midY, midZ, depth - 1, sizeMult, alpha, color, seed * 2 + 7, branchCount, i5, f10);
            buildBoltSegment(context, midX, midY, midZ, x2, y2, z2, depth - 1, sizeMult, alpha, color, seed * 2 + 9, branchCount, i5, f10);

            if (branchCount < 2 && depth >= 2 && smoothNoise(seed, 2, 0, i5, f10) > 0.45F) {
                float bn1 = smoothNoise(seed, 3, 0, i5, f10);
                float bn2 = smoothNoise(seed, 4, 0, i5, f10);
                float bDirX = dirX * 0.4F + perpX * bn1 + normX * bn2;
                float bDirY = dirY * 0.4F + perpY * bn1 + normY * bn2;
                float bDirZ = dirZ * 0.4F + perpZ * bn1 + normZ * bn2;
                float bLen = (float) Math.sqrt(bDirX * bDirX + bDirY * bDirY + bDirZ * bDirZ);
                if (bLen < 1.0E-5F) bLen = 1.0F;
                float bDist = len * 0.65F;
                float bEndX = midX + (bDirX / bLen) * bDist;
                float bEndY = midY + (bDirY / bLen) * bDist;
                float bEndZ = midZ + (bDirZ / bLen) * bDist;

                buildBoltSegment(context, midX, midY, midZ, bEndX, bEndY, bEndZ, depth - 1,
                        sizeMult * 0.7F, alpha * 0.7F, color, seed * 3 + 13, branchCount + 1, i5, f10);
            }
        } else {
            drawLightningSegment(context, x1, y1, z1, x2, y2, z2, sizeMult, alpha, color);
        }
    }

    private void drawLightningSegment(DrawContext context, float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float sizeMult, float alpha, int color) {
        Vector2f s1 = ProjectUtil.toScreen(x1, y1, z1);
        Vector2f s2 = ProjectUtil.toScreen(x2, y2, z2);
        if (!ProjectUtil.isOnScreen(s1) && !ProjectUtil.isOnScreen(s2)) return;

        float dx = s2.x - s1.x;
        float dy = s2.y - s1.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return;
        float angle = (float) Math.atan2(dy, dx);

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(s1.x, s1.y);
        matrices.rotate(angle);

        float haloThick = Math.max(3.0f, 6.0f * sizeMult);
        int haloCol = ColorUtil.withAlphaFloat(color, alpha * 0.35f);
        Render2D.drawRect(context, 0, -haloThick * 0.5f, len, haloThick, haloCol);

        float coreThick = Math.max(1.5f, 2.4f * sizeMult);
        int coreCol = ColorUtil.withAlphaFloat(color, alpha * 0.95f);
        Render2D.drawRect(context, 0, -coreThick * 0.5f, len, coreThick, coreCol);

        float centerThick = Math.max(0.8f, 1.0f * sizeMult);
        int whiteCol = ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.95f);
        Render2D.drawRect(context, 0, -centerThick * 0.5f, len, centerThick, whiteCol);

        matrices.popMatrix();
    }

    private void renderDischarge(DrawContext context, Vec3d center, double height, float delta,
                                float spd, float baseR, float sizeMult, int activeColor) {
        long nowNanos = System.nanoTime();
        float dt = (this.lastFrameTime == 0L) ? 0.016F : MathHelper.clamp((nowNanos - this.lastFrameTime) / 1.0E9F, 0.001F, 0.05F);
        this.lastFrameTime = nowNanos;

        LivingEntity curTarget = (this.target != null && this.target.isAlive()) ? this.target : null;
        if (curTarget != null) {
            int hurt = curTarget.hurtTime;
            if (this.trackedTargetId != curTarget.getId()) {
                this.trackedTargetId = curTarget.getId();
                this.lastTargetHurtTime = hurt;
            }
            if (hurt > this.lastTargetHurtTime) {
                this.dischargePulse = 1.0F;
            }
            this.lastTargetHurtTime = hurt;
            if (curTarget.isDead()) {
                this.dischargePulse = 1.0F;
            }
        }
        this.dischargePulse = Math.max(0.0F, this.dischargePulse - dt * 3.0F);
        this.dischargeRotation = (this.dischargeRotation + dt * 2.5F * spd * (1.0F + 2.0F * this.dischargePulse)) % MathHelper.TAU;

        float fade = this.animProgress;
        float pulse = this.dischargePulse;
        float rad = (float) (baseR * (0.85 + 0.15 * pulse));

        // 1. Scanning energy rings traversing the target body
        float scanPhase = (this.dischargeRotation * 1.8F) % MathHelper.TAU;
        float scanY1 = (float) (center.y + (0.15 + 0.7 * (0.5 + 0.5 * Math.sin(scanPhase))) * height);
        float scanY2 = (float) (center.y + (0.15 + 0.7 * (0.5 + 0.5 * Math.sin(scanPhase + Math.PI))) * height);

        drawDischargeRing(context, center.x, scanY1, center.z, rad, 16, activeColor, fade * 0.9f, sizeMult, pulse);
        drawDischargeRing(context, center.x, scanY2, center.z, rad * 0.92f, 16, activeColor, fade * 0.75f, sizeMult, pulse);

        // 2. Vertical crawling lightning arcs along 4 quadrants of the cylinder
        int arcs = 4;
        for (int a = 0; a < arcs; a++) {
            float baseAngle = this.dischargeRotation + (float) (a * Math.PI / 2.0);
            int segs = 7;
            float prevX = 0, prevY = 0, prevZ = 0;
            for (int s = 0; s <= segs; s++) {
                float relY = (float) s / (float) segs;
                float angle = baseAngle + 0.45F * (float) Math.sin(this.snowSimTime * 4.0F + s * 1.2F + a);
                float r = rad * (1.0F + 0.12F * (float) Math.sin(this.snowSimTime * 7.0F + s * 2.0F));
                float curX = (float) (center.x + Math.cos(angle) * r);
                float curZ = (float) (center.z + Math.sin(angle) * r);
                float curY = (float) (center.y + relY * height * 1.05);

                if (s > 0) {
                    drawLightningSegment(context, prevX, prevY, prevZ, curX, curY, curZ, sizeMult * 0.9F, fade * 0.95F, activeColor);
                    if (s % 2 == 1) {
                        drawParticle(context, BLOOM_TEXTURE, curX, curY, curZ, 0.22F * sizeMult, activeColor);
                    }
                }
                prevX = curX; prevY = curY; prevZ = curZ;
            }
        }

        // 3. Discharge bursts on hurt pulse
        if (pulse > 0.05F) {
            int sparks = Math.round(pulse * 10);
            for (int i = 0; i < sparks; i++) {
                float sa = (float) (i * MathHelper.TAU / sparks + this.snowSimTime * 3.0);
                float sr = rad * (1.1F + 0.3F * pulse);
                float sx = (float) (center.x + Math.cos(sa) * sr);
                float sz = (float) (center.z + Math.sin(sa) * sr);
                float sy = (float) (center.y + (0.2 + 0.6 * ((i * 17) % 10) / 10.0) * height);
                drawParticle(context, BLOOM_TEXTURE, sx, sy, sz, 0.35F * sizeMult * (1.0F + pulse), 0xFFFFFFFF);
                drawParticle(context, BLOOM_TEXTURE, sx, sy, sz, 0.55F * sizeMult * (1.0F + pulse), activeColor);
            }
        }
    }

    private void drawDischargeRing(DrawContext context, double cx, double cy, double cz,
                                  float radius, int points, int color, float alpha, float sizeMult, float pulse) {
        float prevX = 0, prevY = 0, prevZ = 0;
        for (int i = 0; i <= points; i++) {
            float angle = (float) (i * MathHelper.TAU / points);
            float jitter = 0.06F * (float) Math.sin(this.snowSimTime * 9.0F + i * 2.3F) * (1.0F + pulse * 2.0F);
            float r = radius * (1.0F + jitter);
            float curX = (float) (cx + Math.cos(angle) * r);
            float curZ = (float) (cz + Math.sin(angle) * r);
            float curY = (float) (cy + 0.04 * Math.sin(this.snowSimTime * 6.0F + angle * 3.0F));

            if (i > 0) {
                drawLightningSegment(context, prevX, prevY, prevZ, curX, curY, curZ, sizeMult * 0.75F, alpha, color);
            }
            prevX = curX; prevY = curY; prevZ = curZ;
        }
    }

    private static float smoothNoise(int i, int i2, int i3, int i4, float f) {
        float f2 = hashNoise(i, i2, i4 * 4 + i3);
        float f3 = hashNoise(i, i2, (i4 + 1) * 4 + i3);
        float f4 = f * f * (3.0F - 2.0F * f);
        return f2 + (f3 - f2) * f4;
    }

    private static float hashNoise(int i, int i2, int i3) {
        int i4 = i * 374761393 + i2 * 668265263 + i3 * 1274126177;
        int i5 = (i4 ^ i4 >>> 13) * 1274126177;
        int i6 = i5 ^ i5 >>> 16;
        return (i6 & 65535) / 32768.0F - 1.0F;
    }

    private static final class SnowflakeParticle {
        double x, y, z;
        double vx, vy, vz;
        double groundY;
        float angle, level, angularSpeed, sink, radiusScale, phase;
        float size, spinA, spinB, spinSpeedA, spinSpeedB, tint;
        float alpha, fade = 1.0F, age, groundTime;
        boolean flower, orbiting, grounded, dead, placed;
        int textureIdx;
    }
}
