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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Locale;
import java.util.Optional;

public class HitReady extends Module {

    private static final String STYLE_BOTH = "Кольцо и статус";
    private static final String STYLE_RING = "Только прицел";
    private static final String STYLE_PILL = "Только плашка";

    private static final int COL_READY = 0xFF34D399;
    private static final int COL_CRIT = 0xFFFBBF24;
    private static final int COL_COOLDOWN = 0xFFF59E0B;
    private static final int COL_FAR = 0xFFF87171;
    private static final int COL_BLOCKED = 0xFFFB7185;
    private static final int COL_FRIEND = 0xFF60A5FA;
    private static final int COL_OFF_AIM = 0xFF94A3B8;

    private final ModeSetting style = new ModeSetting("Вид", STYLE_BOTH, "Отображение индикатора удара",
            STYLE_BOTH, STYLE_RING, STYLE_PILL);
    private final BooleanSetting showAlways = new BooleanSetting("Показывать всегда", false,
            "Показывать индикатор даже когда рядом нет врагов");
    private final BooleanSetting critIndicator = new BooleanSetting("Индикатор крита", true,
            "Подсвечивать золотым готовность критического удара");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true,
            "Реагировать только на других игроков");
    private final SliderSetting ringRadius = new SliderSetting("Радиус кольца", 11.5, 7.0, 22.0, 0.5,
            "Радиус индикатора вокруг прицела");
    private final SliderSetting pillOffsetY = new SliderSetting("Отступ плашки", 19.0, 12.0, 42.0, 1.0,
            "Смещение плашки статуса ниже прицела");

    private float smoothAlpha = 0.0f;
    private float smoothRingFill = 1.0f;
    private int smoothColor = COL_READY;
    private float readyPop = 0.0f;
    private AttackState lastState = AttackState.IDLE;
    private long lastFrameNanos = 0L;

    public HitReady() {
        super("HitReady", "Показывает по прицелу, дистанции и КД, можно ли сейчас ударить цель", Category.ESP);
        ringRadius.setVisible(() -> !style.is(STYLE_PILL));
        pillOffsetY.setVisible(() -> !style.is(STYLE_RING));
        addSettings(style, showAlways, critIndicator, onlyPlayers, ringRadius, pillOffsetY);
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

        boolean visible = showAlways.isEnabled()
                || probe.state != AttackState.IDLE
                || probe.cooldown < 0.99f;

        float targetAlpha = visible ? probe.alphaScale : 0.0f;
        float lerpRate = Math.clamp(dt * 16.0f, 0.0f, 1.0f);
        smoothAlpha = MathHelper.lerp(lerpRate, smoothAlpha, targetAlpha);
        smoothRingFill = MathHelper.lerp(Math.clamp(dt * 22.0f, 0.0f, 1.0f), smoothRingFill, probe.ringFill);
        smoothColor = smoothAlpha < 0.02f ? probe.color : ColorUtil.interpolate(smoothColor, probe.color, Math.clamp(dt * 18.0f, 0.0f, 1.0f));

        boolean becameReady = (probe.state == AttackState.HIT_READY || probe.state == AttackState.CRIT_READY)
                && lastState != AttackState.HIT_READY && lastState != AttackState.CRIT_READY;
        if (becameReady) {
            readyPop = 1.0f;
        }
        readyPop = Math.max(0.0f, readyPop - dt * 6.5f);
        lastState = probe.state;

        if (smoothAlpha <= 0.02f) {
            return;
        }

        float cx = mc.getWindow().getScaledWidth() * 0.5f;
        float cy = mc.getWindow().getScaledHeight() * 0.5f;
        boolean drawRing = !style.is(STYLE_PILL);
        boolean drawPill = !style.is(STYLE_RING) && !probe.label.isEmpty();

        if (drawRing) {
            DrawBatcher.flushPending();
            float r = ringRadius.getValueFloat() + readyPop * 1.6f;
            float thickness = 1.65f + readyPop * 0.45f;
            float innerR = Math.max(4.0f, r - thickness * 0.5f);
            float outerR = innerR + thickness;

            int haloColor = ColorUtil.rgba(10, 12, 16, Math.round(115.0f * smoothAlpha));
            int trackColor = ColorUtil.rgba(255, 255, 255, Math.round(28.0f * smoothAlpha));
            int activeColor = ColorUtil.withAlphaFloat(smoothColor, smoothAlpha * (0.94f));

            Primitive2D.drawRingArc(cx, cy, innerR - 0.6f, outerR + 0.6f, 0.0f, 360.0f, haloColor);
            Primitive2D.drawProgressRing(cx, cy, innerR, outerR, Math.clamp(smoothRingFill, 0.0f, 1.0f), trackColor, activeColor);
        }

        if (drawPill) {
            float fontSize = 6.7f;
            float textW = Fonts.b.a(probe.label, fontSize);
            float dotSize = 4.0f;
            float padX = 5.0f;
            float gap = 3.5f;
            float pillW = padX * 2.0f + dotSize + gap + textW;
            float pillH = 11.5f;
            float pillX = cx - pillW * 0.5f;
            float pillY = cy + pillOffsetY.getValueFloat();
            float pillR = 4.0f;

            int bgCol = ColorUtil.rgba(14, 16, 22, Math.round(196.0f * smoothAlpha));
            int borderCol = ColorUtil.interpolate(
                    ColorUtil.rgba(255, 255, 255, Math.round(36.0f * smoothAlpha)),
                    ColorUtil.withAlphaFloat(smoothColor, smoothAlpha * 0.72f),
                    0.55f
            );
            int dotCol = ColorUtil.withAlphaFloat(smoothColor, smoothAlpha);
            int txtCol = ColorUtil.rgba(244, 247, 252, Math.round(248.0f * smoothAlpha));

            Draw2DProcessor.INSTANCE.drawPanelBlur(null, pillX, pillY, pillW, pillH, pillR, smoothAlpha);
            Draw2DProcessor.INSTANCE.drawRect(null, pillX, pillY, pillW, pillH, pillR, bgCol);
            Draw2DProcessor.INSTANCE.drawOutline(null, pillX, pillY, pillW, pillH, pillR, 0.7f, borderCol);
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
        NoFriendDamage nfd = ModuleManager.getInstance().getModule(NoFriendDamage.class);

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(tickProgress).normalize();
        double scanRange = reach + 3.2;
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
                return new Probe(AttackState.FRIEND, COL_FRIEND, "ДРУГ", cooldown, 1.0f, cooldown);
            }
            if (occluded) {
                return new Probe(AttackState.BLOCKED_WALL, COL_FAR, "СТЕНА", 0.22f, 0.92f, cooldown);
            }
            if (aimedDist > reach + 0.01) {
                return new Probe(AttackState.TOO_FAR, COL_FAR,
                        String.format(Locale.US, "%.1fм", aimedDist),
                        0.32f, 0.96f, cooldown);
            }
            if (usingItem) {
                return new Probe(AttackState.BLOCKED_ITEM, COL_BLOCKED, "ЗАНЯТ", 0.25f, 0.96f, cooldown);
            }
            if (cooldown < 0.99f) {
                return new Probe(AttackState.COOLDOWN, COL_COOLDOWN,
                        "КД " + Math.round(cooldown * 100.0f) + "%",
                        cooldown, 1.0f, cooldown);
            }
            if (critIndicator.isEnabled() && isCritReady(mc)) {
                return new Probe(AttackState.CRIT_READY, COL_CRIT, "КРИТ", 1.0f, 1.0f, cooldown);
            }
            return new Probe(AttackState.HIT_READY, COL_READY, "УДАР", 1.0f, 1.0f, cooldown);
        }

        if (nearestTarget != null) {
            if (nfd != null && nfd.shouldCancelAttack(nearestTarget)) {
                return new Probe(AttackState.FRIEND, COL_FRIEND, "ДРУГ", cooldown, 0.65f, cooldown);
            }
            if (nearestBoxDist <= reach + 0.02) {
                if (usingItem) {
                    return new Probe(AttackState.BLOCKED_ITEM, COL_BLOCKED, "ЗАНЯТ", 0.22f, 0.85f, cooldown);
                }
                return new Probe(AttackState.OFF_AIM, COL_OFF_AIM, "НАВЕДИ", cooldown, 0.86f, cooldown);
            }
            float fade = MathHelper.clamp((float) ((scanRange - nearestBoxDist) / 2.6), 0.15f, 0.72f);
            return new Probe(AttackState.TOO_FAR, COL_OFF_AIM,
                    String.format(Locale.US, "%.1fм", nearestBoxDist),
                    cooldown * 0.6f, fade, cooldown);
        }

        String idleLabel = cooldown < 0.99f ? "КД " + Math.round(cooldown * 100.0f) + "%" : "ГОТОВ";
        int idleCol = cooldown < 0.99f ? COL_COOLDOWN : COL_OFF_AIM;
        return new Probe(AttackState.IDLE, idleCol, idleLabel, cooldown, showAlways.isEnabled() ? 0.78f : 0.0f, cooldown);
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
        lastState = AttackState.IDLE;
        lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetSmooth();
    }

    private enum AttackState {
        IDLE,
        CRIT_READY,
        HIT_READY,
        COOLDOWN,
        TOO_FAR,
        OFF_AIM,
        BLOCKED_WALL,
        BLOCKED_ITEM,
        FRIEND
    }

    private record Probe(AttackState state, int color, String label, float ringFill, float alphaScale, float cooldown) {}
}
