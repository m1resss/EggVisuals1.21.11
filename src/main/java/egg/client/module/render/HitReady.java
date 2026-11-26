package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.combat.NoFriendDamage;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.Primitive2D;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public class HitReady extends Module {

    private static final String STYLE_BOTH = "Кольцо и плашка";
    private static final String STYLE_RING = "Только кольцо";
    private static final String STYLE_PILL = "Только плашка";

    private static final int COL_CAN = 0xFF22C55E;
    private static final int COL_CRIT = 0xFFFBBF24;
    private static final int COL_CANT = 0xFFEF4444;
    private static final int COL_FRIEND = 0xFF60A5FA;
    private static final int COL_IDLE = 0xFF94A3B8;

    private final ModeSetting style = new ModeSetting("Вид", STYLE_BOTH, "Способ отображения готовности удара",
            STYLE_BOTH, STYLE_RING, STYLE_PILL);
    private final BooleanSetting showWithoutTarget = new BooleanSetting("Без цели", true,
            "Показывать индикатор даже когда рядом нет врагов");
    private final BooleanSetting showReason = new BooleanSetting("Причина запрета", true,
            "Дописывать причину, почему нельзя ударить (КД, далеко, мимо, щит)");
    private final BooleanSetting critIndicator = new BooleanSetting("Индикатор крита", true,
            "Подсвечивать золотым готовность критического удара");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false,
            "Реагировать только на других игроков");
    private final SliderSetting ringRadius = new SliderSetting("Радиус кольца", 12.0, 7.0, 22.0, 0.5,
            "Радиус кольца вокруг прицела");
    private final SliderSetting pillOffsetY = new SliderSetting("Отступ плашки", 20.0, 12.0, 44.0, 1.0,
            "Смещение плашки ниже прицела");

    private float smoothAlpha = 0.0f;
    private float smoothRingFill = 1.0f;
    private int smoothColor = COL_IDLE;
    private float readyPop = 0.0f;
    private boolean lastCanHit = false;
    private long lastFrameNanos = 0L;

    public HitReady() {
        super("HitReady", "Чётко показывает у прицела, можно ли сейчас ударить цель или нет", Category.ESP);
        ringRadius.setVisible(() -> !style.is(STYLE_PILL));
        pillOffsetY.setVisible(() -> !style.is(STYLE_RING));
        showReason.setVisible(() -> !style.is(STYLE_RING));
        addSettings(style, showWithoutTarget, showReason, critIndicator, onlyPlayers, ringRadius, pillOffsetY);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled() || context == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.options.hudHidden || !mc.options.getPerspective().isFirstPerson()) {
            resetSmooth();
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016f : Math.clamp((now - lastFrameNanos) / 1_000_000_000.0f, 0.001f, 0.05f);
        lastFrameNanos = now;

        float tickProgress = tickCounter != null ? tickCounter.getTickProgress(true) : 1.0f;
        Probe probe = evaluateAttack(mc, tickProgress);

        float targetAlpha = (showWithoutTarget.isEnabled() || probe.hasTarget || probe.cooldown < 0.96f)
                ? probe.alphaScale : 0.0f;

        float rate = Math.clamp(dt * 18.0f, 0.0f, 1.0f);
        smoothAlpha = MathHelper.lerp(rate, smoothAlpha, targetAlpha);
        smoothRingFill = MathHelper.lerp(Math.clamp(dt * 24.0f, 0.0f, 1.0f), smoothRingFill, probe.ringFill);
        smoothColor = smoothAlpha < 0.02f ? probe.color : ColorUtil.interpolate(smoothColor, probe.color, Math.clamp(dt * 22.0f, 0.0f, 1.0f));

        if (probe.canHit && !lastCanHit) {
            readyPop = 1.0f;
        }
        readyPop = Math.max(0.0f, readyPop - dt * 6.0f);
        lastCanHit = probe.canHit;

        if (smoothAlpha <= 0.02f) {
            return;
        }

        float cx = mc.getWindow().getScaledWidth() * 0.5f;
        float cy = mc.getWindow().getScaledHeight() * 0.5f;
        boolean drawRing = !style.is(STYLE_PILL);
        boolean drawPill = !style.is(STYLE_RING);

        if (drawRing) {
            DrawBatcher.flushPending();
            float r = ringRadius.getValueFloat() + readyPop * 1.8f;
            float thickness = 2.3f + readyPop * 0.6f;
            float innerR = Math.max(4.0f, r - thickness * 0.5f);
            float outerR = innerR + thickness;

            int haloColor = ColorUtil.rgba(8, 10, 14, Math.round(145.0f * smoothAlpha));
            int trackColor = ColorUtil.rgba(255, 255, 255, Math.round(32.0f * smoothAlpha));
            int activeColor = ColorUtil.withAlphaFloat(smoothColor, smoothAlpha * 0.98f);

            Primitive2D.drawRingArc(cx, cy, innerR - 0.7f, outerR + 0.7f, 0.0f, 360.0f, haloColor);
            Primitive2D.drawProgressRing(cx, cy, innerR, outerR, Math.clamp(smoothRingFill, 0.0f, 1.0f), trackColor, activeColor);
        }

        if (drawPill && !probe.label.isEmpty()) {
            float fontSize = 7.6f;
            float textW = Fonts.b.a(probe.label, fontSize);
            float dotSize = 4.5f;
            float padX = 5.5f;
            float gap = 4.0f;
            float pillW = padX * 2.0f + dotSize + gap + textW;
            float pillH = 13.5f;
            float pillX = cx - pillW * 0.5f;
            float pillY = cy + pillOffsetY.getValueFloat();
            float pillR = 4.5f;

            int bgCol = ColorUtil.rgba(12, 14, 20, Math.round(212.0f * smoothAlpha));
            int borderCol = ColorUtil.withAlphaFloat(smoothColor, smoothAlpha * (probe.canHit ? 0.85f : 0.65f));
            int dotCol = ColorUtil.withAlphaFloat(smoothColor, smoothAlpha);
            int txtCol = probe.hasTarget
                    ? ColorUtil.interpolate(0xFFFFFFFF, smoothColor, probe.canHit ? 0.25f : 0.12f)
                    : ColorUtil.rgba(215, 222, 234, Math.round(230.0f * smoothAlpha));
            txtCol = ColorUtil.withAlphaFloat(txtCol, smoothAlpha);

            Draw2DProcessor.INSTANCE.drawPanelBlur(null, pillX, pillY, pillW, pillH, pillR, smoothAlpha);
            Draw2DProcessor.INSTANCE.drawRect(null, pillX, pillY, pillW, pillH, pillR, bgCol);
            Draw2DProcessor.INSTANCE.drawOutline(null, pillX, pillY, pillW, pillH, pillR, 0.85f, borderCol);
            Draw2DProcessor.INSTANCE.drawRect(null, pillX + padX, pillY + (pillH - dotSize) * 0.5f, dotSize, dotSize, dotSize * 0.5f, dotCol);
            Fonts.b.a(null, probe.label, pillX + padX + dotSize + gap, Fonts.b.a(fontSize, pillY + pillH * 0.5f), fontSize, txtCol);
        }

        DrawBatcher.flushPending();
    }

    private Probe evaluateAttack(MinecraftClient mc, float tickProgress) {
        double reach = Math.max(0.5, mc.player.getEntityInteractionRange());
        float cooldown = Math.clamp(mc.player.getAttackCooldownProgress(tickProgress), 0.0f, 1.0f);
        boolean usingItem = mc.player.isUsingItem();
        boolean playersOnly = onlyPlayers.isEnabled();
        boolean withReason = showReason.isEnabled();
        NoFriendDamage nfd = ModuleManager.getInstance().getModule(NoFriendDamage.class);

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(tickProgress).normalize();
        double scanRange = reach + 3.5;
        Vec3d rayEnd = eye.add(look.multiply(scanRange));

        var blockHit = mc.world.raycast(new RaycastContext(
                eye, rayEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(blockHit.getPos())
                : scanRange + 1.0;

        LivingEntity aimedTarget = null;
        double aimedDist = Double.MAX_VALUE;
        boolean occluded = false;

        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity living) {
            if (isValidTarget(living, playersOnly)) {
                aimedTarget = living;
                aimedDist = eye.distanceTo(ehr.getPos());
            }
        }

        LivingEntity nearestTarget = aimedTarget;
        double nearestBoxDist = aimedTarget != null ? Math.min(aimedDist, reach) : Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living, playersOnly)) continue;
            if (entity.squaredDistanceTo(mc.player) > (scanRange + 2.0) * (scanRange + 2.0)) continue;

            Box bb = living.getBoundingBox().expand(living.getTargetingMargin());
            double boxDist = distanceToBox(eye, bb);
            if (boxDist <= scanRange) {
                if (aimedTarget == null) {
                    Optional<Vec3d> hit = bb.raycast(eye, rayEnd);
                    if (hit.isPresent()) {
                        double d = eye.distanceTo(hit.get());
                        if (d < aimedDist) {
                            aimedDist = d;
                            aimedTarget = living;
                            occluded = d > blockDist + 0.05;
                        }
                    }
                }
                if (boxDist < nearestBoxDist && hasLineOfSight(mc, eye, bb)) {
                    nearestBoxDist = boxDist;
                    nearestTarget = living;
                }
            }
        }

        if (aimedTarget != null) {
            if (nfd != null && nfd.shouldCancelAttack(aimedTarget)) {
                return new Probe(true, false, COL_FRIEND, withReason ? "НЕЛЬЗЯ · ДРУГ" : "НЕЛЬЗЯ", 1.0f, 0.92f, cooldown);
            }
            if (occluded) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · СТЕНА" : "НЕЛЬЗЯ", 1.0f, 0.95f, cooldown);
            }
            if (aimedDist > reach + 0.01) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · ДАЛЕКО" : "НЕЛЬЗЯ", 1.0f, 0.95f, cooldown);
            }
            if (usingItem) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · ЗАНЯТ" : "НЕЛЬЗЯ", 1.0f, 0.96f, cooldown);
            }
            if (cooldown < 0.96f) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · КД" : "НЕЛЬЗЯ", cooldown, 0.98f, cooldown);
            }
            if (critIndicator.isEnabled() && isCritReady(mc)) {
                return new Probe(true, true, COL_CRIT, "КРИТ", 1.0f, 1.0f, cooldown);
            }
            return new Probe(true, true, COL_CAN, "МОЖНО", 1.0f, 1.0f, cooldown);
        }

        if (nearestTarget != null) {
            if (nfd != null && nfd.shouldCancelAttack(nearestTarget)) {
                return new Probe(true, false, COL_FRIEND, withReason ? "НЕЛЬЗЯ · ДРУГ" : "НЕЛЬЗЯ", 1.0f, 0.78f, cooldown);
            }
            if (usingItem) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · ЗАНЯТ" : "НЕЛЬЗЯ", 1.0f, 0.88f, cooldown);
            }
            if (nearestBoxDist <= reach + 0.02) {
                return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · МИМО" : "НЕЛЬЗЯ", Math.max(0.25f, cooldown), 0.92f, cooldown);
            }
            return new Probe(true, false, COL_CANT, withReason ? "НЕЛЬЗЯ · ДАЛЕКО" : "НЕЛЬЗЯ", 0.35f, 0.80f, cooldown);
        }

        if (cooldown < 0.96f) {
            return new Probe(false, false, COL_CANT, withReason ? "НЕЛЬЗЯ · КД" : "НЕЛЬЗЯ", cooldown, 0.75f, cooldown);
        }
        return new Probe(false, false, COL_IDLE, "НЕТ ЦЕЛИ", 1.0f, 0.52f, cooldown);
    }

    private static boolean isCritReady(MinecraftClient mc) {
        return !mc.player.isOnGround()
                && mc.player.fallDistance > 0.0f
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle()
                && !mc.player.isSprinting();
    }

    private static boolean isValidTarget(LivingEntity entity, boolean onlyPlayers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (onlyPlayers && !(entity instanceof PlayerEntity)) return false;
        return !EntityUtil.isNakedInvisible(entity);
    }

    private static double distanceToBox(Vec3d eye, Box bb) {
        double cx = Math.clamp(eye.x, bb.minX, bb.maxX);
        double cy = Math.clamp(eye.y, bb.minY, bb.maxY);
        double cz = Math.clamp(eye.z, bb.minZ, bb.maxZ);
        return eye.distanceTo(new Vec3d(cx, cy, cz));
    }

    private static boolean hasLineOfSight(MinecraftClient mc, Vec3d eye, Box bb) {
        Vec3d center = bb.getCenter();
        var hit = mc.world.raycast(new RaycastContext(
                eye, center, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() != HitResult.Type.BLOCK || eye.distanceTo(hit.getPos()) + 0.1 >= eye.distanceTo(center);
    }

    private void resetSmooth() {
        smoothAlpha = 0.0f;
        readyPop = 0.0f;
        lastCanHit = false;
        lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetSmooth();
    }

    private record Probe(boolean hasTarget, boolean canHit, int color, String label, float ringFill, float alphaScale, float cooldown) {}
}
