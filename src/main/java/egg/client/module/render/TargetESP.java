package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * 3D-projected Target ESP featuring Astral Souls and Cubes.
 * Wraps entities organically in 3D without raw OpenGL state pollution.
 */
public class TargetESP extends Module {
    private static TargetESP instance;

    private static final Identifier GLOWING_TEXTURE = Identifier.of("egg", "textures/glowing.png");
    private static final Identifier BLOOM_TEXTURE = Identifier.of("egg", "textures/target/bloom.png");

    private final ModeSetting mode = new ModeSetting("Визуализация цели", "Души", "Стиль Target ESP",
            "Души", "Кубы");

    private final SliderSetting radius = new SliderSetting("Радиус", 0.85, 0.4, 2.5, 0.05, "Радиус охвата цели");
    private final SliderSetting speed = new SliderSetting("Скорость", 3.0, 0.5, 8.0, 0.1, "Скорость движения частиц");
    private final SliderSetting particleSize = new SliderSetting("Размер частиц", 1.0, 0.3, 2.5, 0.05, "Масштаб частиц");
    private final SliderSetting tailLength = new SliderSetting("Длина хвоста", 16.0, 4.0, 30.0, 1.0, "Длина следа частиц");
    private final BooleanSetting coreGlow = new BooleanSetting("Ядро свечения", true, "Яркое белое ядро частиц");
    private final BooleanSetting hurtColor = new BooleanSetting("Окрас при уроне", true, "Окрашивание в красный цвет при ударе цели");
    private final SliderSetting maxDistance = new SliderSetting("Дистанция наводки", 30.0, 5.0, 64.0, 1.0, "Максимальная дистанция захвата цели");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true, "Захватывать только игроков");

    private LivingEntity target;
    private int loseTargetTicks = 0;
    private float animProgress = 0.0f;

    public TargetESP() {
        super("Target ESP", "Астральные души и кубы вокруг захваченной цели", Category.ESP);
        instance = this;
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
        this.loseTargetTicks = 0;
        this.animProgress = 0.0f;
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
            this.animProgress = Math.min(1.0f, this.animProgress + 0.15f);
        } else {
            this.animProgress = Math.max(0.0f, this.animProgress - 0.15f);
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
                Optional<Vec3d> hit = box.raycast(eyePos, endPos);
                if (hit.isPresent()) {
                    double dSq = eyePos.squaredDistanceTo(hit.get());
                    if (dSq < closestDistSq) {
                        closestDistSq = dSq;
                        closest = living;
                    }
                }
            }
        }
        return closest;
    }

    private boolean isValidTarget(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isInvisible() || entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) {
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
        if (!isEnabled() || this.target == null || this.animProgress <= 0.01f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || !mc.player.canSee(this.target)) return;

        float delta = tickCounter != null ? tickCounter.getTickProgress(true) : 0.0f;
        Vec3d center = this.target.getLerpedPos(delta);
        double entityHeight = this.target.getHeight();
        double centerY = center.y + entityHeight * 0.5;

        int themeColor = Theme.getAccent();
        int hurtCol = 0xFFFF2525;
        float hurtTimer = Math.max(0.0F, this.target.hurtTime - delta);
        float hurtFactor = this.hurtColor.get() ? (float) Math.sin(hurtTimer * (Math.PI / 10.0)) * 0.85F : 0.0f;
        int activeColor = hurtFactor > 0.01f ? ColorUtil.interpolate(themeColor, hurtCol, MathHelper.clamp(hurtFactor, 0.0f, 1.0f)) : themeColor;

        float baseRadius = (float) this.radius.get();
        float spd = (float) this.speed.get();
        float sizeMult = (float) this.particleSize.get();
        int steps = Math.max(4, (int) this.tailLength.get());
        long now = System.currentTimeMillis();
        float timeSec = (float) (now % 10000000L) / 1000.0f;

        String curMode = this.mode.get();
        if ("Кубы".equals(curMode)) {
            renderCubes(context, center.x, centerY, center.z, entityHeight, timeSec, baseRadius, spd, sizeMult, activeColor);
        } else {
            renderSouls(context, center.x, centerY, center.z, entityHeight, timeSec, baseRadius, spd, sizeMult, steps, activeColor);
        }
    }

    /**
     * 3 orbital souls swirling organically in tilted 3D space, wrapping the target like spirits.
     * Features intense luminous glow and radiant particle halos.
     */
    private void renderSouls(DrawContext context, double cx, double cy, double cz, double height,
                             float timeSec, float baseR, float spd, float sizeMult, int steps, int color) {
        double timeMs = timeSec * 1000.0;
        float hAmp = (float) (height * 0.45);

        for (int soul = 0; soul < 3; soul++) {
            for (int i = 0; i < steps; i++) {
                float trailFactor = 1.0f - ((float) i / (float) steps);
                double angle = 0.15 * (timeMs - (i * 12.0 / Math.max(0.5, spd))) / 25.0 * spd;
                double s = Math.sin(angle) * baseR;
                double c = Math.cos(angle) * baseR;
                double yWave = Math.sin(angle * 1.5 + soul * 2.0) * hAmp;

                double wx, wy, wz;
                if (soul == 0) {
                    wx = cx + s;
                    wy = cy + c * 0.65 + yWave * 0.4;
                    wz = cz - c;
                } else if (soul == 1) {
                    wx = cx - s;
                    wy = cy + s * 0.65 - yWave * 0.4;
                    wz = cz - c;
                } else {
                    wx = cx - s * 0.7 + c * 0.7;
                    wy = cy - yWave;
                    wz = cz + c * 0.7 + s * 0.7;
                }

                if (i == 0) {
                    // Head: bright glowing orb with clean vibrant bloom and white-hot spark
                    float headSz = 0.35f * sizeMult;
                    float alpha = this.animProgress * 0.95f;

                    // 1. Radiant bloom aura (vibrant, luminous halo)
                    drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, headSz * 1.9f, ColorUtil.withAlphaFloat(color, alpha * 0.70f));

                    // 2. Glowing body
                    drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, headSz, ColorUtil.withAlphaFloat(color, alpha * 0.95f));

                    // 3. Bright white luminous spark in center (makes it shine and glow brightly)
                    if (this.coreGlow.get()) {
                        drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, headSz * 0.45f, ColorUtil.withAlphaFloat(0xFFFFFFFF, this.animProgress * 0.98f));
                    }
                } else {
                    // Sleek fading particle trail
                    float tailSz = 0.22f * sizeMult * (0.25f + 0.75f * trailFactor);
                    float alpha = this.animProgress * (0.08f + 0.65f * trailFactor * trailFactor);

                    // Soft glow on upper trail only
                    if (i <= 2) {
                        drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, tailSz * 1.4f, ColorUtil.withAlphaFloat(color, alpha * 0.45f));
                    }
                    drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, tailSz, ColorUtil.withAlphaFloat(color, alpha));

                    // Delicate spark along upper trail
                    if (i < steps / 3) {
                        drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, tailSz * 0.35f, ColorUtil.withAlphaFloat(0xFFFFFFFF, alpha * 0.60f));
                    }
                }
            }
        }
    }

    /**
     * 3D floating and orbiting cube wisps.
     */
    private void renderCubes(DrawContext context, double cx, double cy, double cz, double height,
                            float timeSec, float baseR, float spd, float sizeMult, int color) {
        int cubeCount = 8;
        float hAmp = (float) (height * 0.45);

        for (int i = 0; i < cubeCount; i++) {
            float phase = (float) (i * (Math.PI * 2.0 / cubeCount));
            float t = timeSec * 0.85f * spd + phase;
            double wx = cx + Math.cos(t) * baseR * (1.0f + 0.2f * (float) Math.sin(t * 2.0f));
            double wz = cz + Math.sin(t) * baseR * (1.0f + 0.2f * (float) Math.cos(t * 2.0f));
            double wy = cy + Math.sin(t * 1.4f) * hAmp;

            float sz = 0.28f * sizeMult;
            drawParticle(context, BLOOM_TEXTURE, wx, wy, wz, sz * 1.5f, ColorUtil.withAlphaFloat(color, this.animProgress * 0.45f));
            drawParticle(context, GLOWING_TEXTURE, wx, wy, wz, sz * 0.7f, ColorUtil.withAlphaFloat(0xFFFFFFFF, this.animProgress * 0.9f));
        }
    }

    /**
     * Billboard particle projection with depth scaling and screen-boundary clipping.
     */
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
}
