package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
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
    private static final int ORBIT_FLAKES = 42;
    private static final int ORBIT_CRYSTALS = 5;
    private static final float GRAVITY = 2.6F;
    private static final float DRAG = 1.4F;
    private static final float TERMINAL_FALL = 1.15F;
    private static final float GROUND_LIFE = 2.6F;
    private static final float FREE_LIFE = 7.0F;

    private final ModeSetting mode = new ModeSetting("Визуализация цели", "Снежинки", "Стиль Target ESP",
            "Снежинки", "Души", "Кубы");

    private final SliderSetting radius = new SliderSetting("Радиус", 0.95, 0.4, 2.5, 0.05, "Радиус охвата цели");
    private final SliderSetting speed = new SliderSetting("Скорость", 1.8, 0.5, 8.0, 0.1, "Скорость движения частиц");
    private final SliderSetting particleSize = new SliderSetting("Размер частиц", 1.1, 0.3, 2.5, 0.05, "Масштаб частиц");
    private final SliderSetting tailLength = new SliderSetting("Длина хвоста", 16.0, 4.0, 30.0, 1.0, "Длина следа частиц (режим Души)");
    private final BooleanSetting coreGlow = new BooleanSetting("Ядро свечения", true, "Яркое белое ядро частиц");
    private final BooleanSetting hurtColor = new BooleanSetting("Окрас при уроне", true, "Окрашивание в красный цвет при ударе");
    private final SliderSetting maxDistance = new SliderSetting("Дистанция наводки", 30.0, 5.0, 64.0, 1.0, "Максимальная дистанция захвата цели");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true, "Захватывать только игроков");

    private LivingEntity target;
    private LivingEntity attachedTarget;
    private int loseTargetTicks = 0;
    private float animProgress = 0.0f;
    private Vec3d lastTargetPos = null;
    private double lastTargetHeight = 1.8;

    // Snowflake vortex particles state
    private final List<SnowflakeParticle> snowflakeParticles = new ArrayList<>();
    private final Random random = new Random();
    private long lastFrameTime = 0L;
    private float snowSimTime = 0.0f;

    public TargetESP() {
        super("Target ESP", "Снежный вихрь и астральные проекции вокруг цели", Category.ESP);
        instance = this;

        tailLength.setVisible(() -> "Души".equals(mode.get()));
        coreGlow.setVisible(() -> "Души".equals(mode.get()));
        radius.setVisible(() -> true);

        addSettings(mode, radius, speed, particleSize, tailLength, coreGlow, hurtColor, maxDistance, onlyPlayers);
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
        this.loseTargetTicks = 0;
        this.animProgress = 0.0f;
        this.snowflakeParticles.clear();
    }

    public void onTick() {
        if (!isEnabled()) {
            this.target = null;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.target = null;
            return;
        }

        LivingEntity found = findCrosshairTarget();
        if (found != null) {
            this.target = found;
            this.loseTargetTicks = 0;
        } else {
            this.loseTargetTicks++;
            if (this.loseTargetTicks > 6 || (this.target != null && !mc.player.canSee(this.target))) {
                this.target = null;
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
        p.fade = 1.0F - MathHelper.clamp((p.age - FREE_LIFE) / 1.0F, 0.0F, 1.0F);
        if (p.fade <= 0.0F) p.dead = true;
    }

    private void releaseSnow() {
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
            p.vx = p.vx * 0.55 + (rnd.nextDouble() - 0.5) * 0.6;
            p.vz = p.vz * 0.55 + (rnd.nextDouble() - 0.5) * 0.6;
            p.vy = 0.3 + rnd.nextDouble() * 0.6;
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
