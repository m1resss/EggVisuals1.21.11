package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.combat.NoFriendDamage;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HitReach extends Module {

    private static final String MODE_TARGETS = "Вокруг врагов";
    private static final String MODE_SELF = "Вокруг себя";
    private static final String MODE_BOTH = "Оба";

    private static final String SHAPE_SOLID = "Сплошной";
    private static final String SHAPE_DASHED = "Пунктир";

    private static final int COL_CAN = 0xFF22C55E;
    private static final int COL_CRIT = 0xFFFBBF24;
    private static final int COL_COOLDOWN = 0xFFF59E0B;
    private static final int COL_CANT = 0xFFEF4444;
    private static final int COL_FRIEND = 0xFF60A5FA;

    private final ModeSetting circleMode = new ModeSetting("Режим кругов", MODE_TARGETS, "Где рисовать круги дистанции удара",
            MODE_TARGETS, MODE_SELF, MODE_BOTH);
    private final ModeSetting ringShape = new ModeSetting("Стиль круга", SHAPE_SOLID, "Вид линии круга на земле",
            SHAPE_SOLID, SHAPE_DASHED);
    private final BooleanSetting respectCooldown = new BooleanSetting("Учитывать КД", true,
            "Показывать оранжевым пока удар перезаряжается");
    private final BooleanSetting critIndicator = new BooleanSetting("Индикатор крита", true,
            "Подсвечивать золотым готовность критического удара");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false,
            "Рисовать круги только вокруг игроков");
    private final SliderSetting maxShowDist = new SliderSetting("Дальность показа", 12.0, 5.0, 24.0, 1.0,
            "Максимальная дистанция отображения кругов врагов");
    private final SliderSetting opacity = new SliderSetting("Прозрачность", 85.0, 20.0, 100.0, 5.0,
            "Яркость кругов дистанции на земле");

    private final Map<Integer, CircleState> circleStates = new HashMap<>();
    private final CircleState selfState = new CircleState();
    private BufferAllocator allocator;
    private long lastWorldNanos = 0L;

    public HitReach() {
        super("HitReach", "Показывает 3D-круги дистанции удара на земле: зелёный — достаёшь, красный — далеко", Category.ESP);
        maxShowDist.setVisible(() -> !circleMode.is(MODE_SELF));
        addSettings(circleMode, ringShape, respectCooldown, critIndicator, onlyPlayers, maxShowDist, opacity);
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        float dt = lastWorldNanos == 0L ? 0.016f : Math.clamp((now - lastWorldNanos) / 1_000_000_000.0f, 0.001f, 0.05f);
        lastWorldNanos = now;

        double reach = Math.max(0.5, mc.player.getEntityInteractionRange());
        float cooldown = Math.clamp(mc.player.getAttackCooldownProgress(tickDelta), 0.0f, 1.0f);
        boolean checkCd = respectCooldown.isEnabled();
        boolean checkCrit = critIndicator.isEnabled() && isCritReady(mc);
        boolean usingItem = mc.player.isUsingItem();
        boolean playersOnly = onlyPlayers.isEnabled();
        NoFriendDamage nfd = ModuleManager.getInstance().getModule(NoFriendDamage.class);

        Vec3d eye = mc.player.getEyePos();
        double maxDist = maxShowDist.get();
        double maxDistSq = (maxDist + 2.0) * (maxDist + 2.0);
        float baseAlpha = opacity.getValueFloat() / 100.0f;
        boolean dashed = ringShape.is(SHAPE_DASHED);
        float uOffset = dashed ? 4.0f : 0.0f;

        boolean drawTargets = !circleMode.is(MODE_SELF);
        boolean drawSelf = !circleMode.is(MODE_TARGETS);

        boolean anyTargetInReach = false;
        boolean anyTargetCanHit = false;
        boolean anyTargetCrit = false;

        if (allocator == null) {
            allocator = new BufferAllocator(48 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }

        try {
            BufferBuilder mesh = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            float smoothFactor = Math.clamp(dt * 16.0f, 0.0f, 1.0f);
            double fadeStart = Math.max(2.0, maxDist - 2.5);

            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof LivingEntity living) || !isValidTarget(living, playersOnly)) continue;
                double distSq = entity.squaredDistanceTo(mc.player);
                if (distSq > maxDistSq) continue;

                Box bb = living.getBoundingBox().expand(living.getTargetingMargin());
                double boxDist = distanceToBox(eye, bb);
                if (boxDist > maxDist) continue;

                boolean friend = nfd != null && nfd.shouldCancelAttack(living);
                boolean los = hasLineOfSight(mc, eye, bb);
                boolean inReach = boxDist <= reach + 0.01 && los && !friend;

                int targetCol;
                if (friend) {
                    targetCol = COL_FRIEND;
                } else if (!inReach) {
                    targetCol = COL_CANT;
                } else if (usingItem || (checkCd && cooldown < 0.96f)) {
                    targetCol = COL_COOLDOWN;
                    anyTargetInReach = true;
                } else {
                    anyTargetInReach = true;
                    anyTargetCanHit = true;
                    if (checkCrit) {
                        targetCol = COL_CRIT;
                        anyTargetCrit = true;
                    } else {
                        targetCol = COL_CAN;
                    }
                }

                if (!drawTargets) continue;

                CircleState st = circleStates.computeIfAbsent(living.getId(), id -> new CircleState());
                st.seenAt = now;
                st.color = st.initialized ? ColorUtil.interpolate(st.color, targetCol, smoothFactor) : targetCol;
                double clampedY = Math.clamp(eye.y, bb.minY, bb.maxY);
                double dy = eye.y - clampedY;
                double horizReach = Math.sqrt(Math.max(0.0, reach * reach - dy * dy));
                float targetRadius = Math.max(0.35f, (float) (horizReach + living.getWidth() * 0.5));
                st.radius = st.initialized ? MathHelper.lerp(smoothFactor, st.radius, targetRadius) : targetRadius;
                st.initialized = true;

                float distFade = boxDist > fadeStart
                        ? Math.clamp((float) ((maxDist - boxDist) / Math.max(0.5, maxDist - fadeStart)), 0.0f, 1.0f)
                        : 1.0f;
                float a = baseAlpha * distFade;
                if (a <= 0.01f) continue;

                Vec3d lerped = living.getLerpedPos(tickDelta);
                double groundY = findGroundY(mc, lerped.x, lerped.y, lerped.z);
                emitCircleQuad(mesh, (float) (lerped.x - camPos.x), (float) (groundY - camPos.y), (float) (lerped.z - camPos.z),
                        st.radius, uOffset, st.color, a);
            }

            for (Iterator<Map.Entry<Integer, CircleState>> it = circleStates.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue().seenAt > 250_000_000L) {
                    it.remove();
                }
            }

            if (drawSelf) {
                int selfTargetCol;
                if (anyTargetCanHit) {
                    selfTargetCol = anyTargetCrit ? COL_CRIT : COL_CAN;
                } else if (anyTargetInReach) {
                    selfTargetCol = COL_COOLDOWN;
                } else {
                    selfTargetCol = COL_CANT;
                }
                selfState.color = selfState.initialized
                        ? ColorUtil.interpolate(selfState.color, selfTargetCol, smoothFactor)
                        : selfTargetCol;
                float targetSelfR = (float) reach + 0.30f;
                selfState.radius = selfState.initialized
                        ? MathHelper.lerp(smoothFactor, selfState.radius, targetSelfR)
                        : targetSelfR;
                selfState.initialized = true;

                Vec3d selfPos = mc.player.getLerpedPos(tickDelta);
                double selfGroundY = findGroundY(mc, selfPos.x, selfPos.y, selfPos.z);
                emitCircleQuad(mesh, (float) (selfPos.x - camPos.x), (float) (selfGroundY - camPos.y), (float) (selfPos.z - camPos.z),
                        selfState.radius, uOffset, selfState.color, baseAlpha);
            }

            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.REACH_CIRCLE, built, "egg reach circles");
            }
        } finally {
            allocator.reset();
        }
    }

    private static void emitCircleQuad(BufferBuilder mesh, float cx, float cy, float cz, float worldRadius,
                                       float uOffset, int color, float alpha) {
        float uv = 1.04f;
        float halfQuad = (worldRadius / 0.90f) * uv;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f);

        mesh.vertex(cx - halfQuad, cy, cz - halfQuad).texture(uOffset - uv, -uv).color(r, g, b, a);
        mesh.vertex(cx + halfQuad, cy, cz - halfQuad).texture(uOffset + uv, -uv).color(r, g, b, a);
        mesh.vertex(cx + halfQuad, cy, cz + halfQuad).texture(uOffset + uv, uv).color(r, g, b, a);
        mesh.vertex(cx - halfQuad, cy, cz + halfQuad).texture(uOffset - uv, uv).color(r, g, b, a);
    }

    private static double findGroundY(MinecraftClient mc, double x, double y, double z) {
        Vec3d start = new Vec3d(x, y + 0.35, z);
        var hit = mc.world.raycast(new RaycastContext(
                start, new Vec3d(x, y - 2.8, z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos().y + 0.028;
        }
        return y + 0.028;
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

    @Override
    public void onDisable() {
        super.onDisable();
        circleStates.clear();
        selfState.initialized = false;
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
        lastWorldNanos = 0L;
    }

    private static final class CircleState {
        int color;
        float radius;
        boolean initialized;
        long seenAt;
    }
}
