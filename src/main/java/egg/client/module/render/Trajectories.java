package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.*;

public class Trajectories extends Module {

    private final SliderSetting circleRadius = new SliderSetting("Радиус круга", 0.45, 0.20, 1.00, 0.05, "Радиус светящегося круга приземления");
    private final SliderSetting smoothSpeed = new SliderSetting("Плавность", 18.0, 5.0, 35.0, 1.0, "Скорость плавного следования круга за камерой");
    private final BooleanSetting showLine = new BooleanSetting("Линия полета", true, "Отображать траекторию полета снаряда");
    private final SliderSetting pullThreshold = new SliderSetting("При натяжении", 0.0, 0.0, 1.0, 0.05, "Порог силы натяжения для отображения (0 - всегда)");
    private final BooleanSetting glowEffect = new BooleanSetting("Неоновое свечение", true, "Мягкий неоновый ореол и лучи света вокруг круга");
    private final BooleanSetting inFlight = new BooleanSetting("Летящие снаряды", true, "Просчитывать уже выпущенные в мире стрелы и перлы");

    private final Map<String, Vec3d> smoothedPositions = new HashMap<>();
    private final Map<String, Direction> smoothedSides = new HashMap<>();
    private long lastFrameNanos = 0L;

    public Trajectories() {
        super("Trajectories", "Прогнозирует траекторию и точку падения стрел, зелий, жемчуга и снарядов", Category.ESP);
        pullThreshold.setVisible(() -> showLine.get());
        addSettings(circleRadius, smoothSpeed, showLine, pullThreshold, glowEffect, inFlight);
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        float smoothFactor = (float) Math.min(1.0, dt * smoothSpeed.get());
        int themeColor = Theme.getAccent();
        int r = (themeColor >> 16) & 0xFF;
        int g = (themeColor >> 8) & 0xFF;
        int b = themeColor & 0xFF;

        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());
        Set<String> activeKeys = new HashSet<>();

        Box viewRange = mc.player.getBoundingBox().expand(80.0);
        for (Entity entity : mc.world.getOtherEntities(mc.player, viewRange)) {
            if (!entity.isAlive() || entity.getVelocity().lengthSquared() < 0.0001) continue;
            if (entity instanceof ProjectileEntity || entity instanceof ArrowEntity || entity instanceof EnderPearlEntity || entity instanceof PotionEntity || entity instanceof TridentEntity) {
                renderInFlightPrediction(mc, entity, quadBuffer, camPos, r, g, b, smoothFactor, activeKeys);
            }
        }

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        if (isThrowableOrWeapon(main.getItem())) {
            renderItemPrediction(mc, main, Hand.MAIN_HAND, quadBuffer, camPos, r, g, b, smoothFactor, activeKeys);
        }
        if (isThrowableOrWeapon(off.getItem())) {
            renderItemPrediction(mc, off, Hand.OFF_HAND, quadBuffer, camPos, r, g, b, smoothFactor, activeKeys);
        }

        smoothedPositions.keySet().removeIf(k -> !activeKeys.contains(k));
    }

    private boolean isThrowableOrWeapon(Item item) {
        return item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                || item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG
                || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION
                || item == Items.EXPERIENCE_BOTTLE || item == Items.WIND_CHARGE;
    }

    private void renderItemPrediction(MinecraftClient mc, ItemStack stack, Hand hand,
                                      VertexConsumer quadBuffer, Vec3d camPos,
                                      int r, int g, int b, float smoothFactor, Set<String> activeKeys) {
        if (stack == null || stack.isEmpty()) return;
        Item item = stack.getItem();

        float speed = 0.0f;
        boolean isThrowable = false;
        float pitchOffset = 0.0f;
        double gravity = 0.03;

        if (item instanceof BowItem) {
            float pull = getBowPull(mc.player);
            if (pull <= 0.0f) {

                speed = 3.0f;
            } else {
                speed = pull * 3.0f;
            }
            gravity = 0.05;
        } else if (item instanceof CrossbowItem) {
            speed = 3.15f;
            gravity = 0.05;
        } else if (item instanceof TridentItem) {
            speed = 2.5f;
            gravity = 0.05;
        } else if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG) {
            speed = 1.5f;
            isThrowable = true;
            gravity = 0.03;
        } else if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            speed = 0.5f;
            isThrowable = true;
            pitchOffset = -20.0f;
            gravity = 0.05;
        } else if (item == Items.EXPERIENCE_BOTTLE) {
            speed = 0.7f;
            isThrowable = true;
            pitchOffset = -20.0f;
            gravity = 0.07;
        } else if (item == Items.WIND_CHARGE) {
            speed = 1.5f;
            isThrowable = true;
            gravity = 0.01;
        }

        if (speed <= 0.0f) return;

        boolean isMulti = (item instanceof CrossbowItem) && hasMultishot(stack);
        float[] yawSpreads = isMulti ? new float[]{-10.0f, 0.0f, 10.0f} : new float[]{0.0f};

        for (int i = 0; i < yawSpreads.length; i++) {
            float yawSpread = yawSpreads[i];
            String key = "held_" + hand.name() + "_" + i;
            activeKeys.add(key);

            TrajectoryResult res = simulateTrajectory(mc, speed, isThrowable, pitchOffset, gravity, yawSpread);
            if (res.path.size() >= 2) {
                Vec3d targetImpact = res.impactPoint;
                Vec3d smoothPos = smoothedPositions.compute(key, (k, old) -> {
                    if (old == null || old.squaredDistanceTo(targetImpact) > 100.0) return targetImpact;
                    return old.lerp(targetImpact, smoothFactor);
                });

                if (showLine.isEnabled()) {
                    boolean lineAllowed = true;
                    if (pullThreshold.get() > 0.0) {
                        if (item instanceof BowItem) {
                            lineAllowed = getBowPull(mc.player) >= pullThreshold.get();
                        } else if (item instanceof CrossbowItem) {
                            lineAllowed = CrossbowItem.isCharged(stack) || mc.player.isUsingItem();
                        } else if (item instanceof TridentItem) {
                            lineAllowed = mc.player.isUsingItem();
                        }
                    }
                    if (lineAllowed) {
                        renderPathRibbon(quadBuffer, res.path, camPos, r, g, b);
                    }
                }

                renderGlowingLandingCircle(quadBuffer, smoothPos, camPos, res.impactSide, r, g, b);
            }
        }
    }

    private void renderInFlightPrediction(MinecraftClient mc, Entity entity,
                                          VertexConsumer quadBuffer, Vec3d camPos,
                                          int r, int g, int b, float smoothFactor, Set<String> activeKeys) {
        String key = "flight_" + entity.getId();
        activeKeys.add(key);

        TrajectoryResult res = simulateInFlight(mc, entity);
        if (res.path.size() >= 2) {
            Vec3d targetImpact = res.impactPoint;
            Vec3d smoothPos = smoothedPositions.compute(key, (k, old) -> {
                if (old == null || old.squaredDistanceTo(targetImpact) > 100.0) return targetImpact;
                return old.lerp(targetImpact, smoothFactor);
            });

            if (showLine.isEnabled()) {
                renderPathRibbon(quadBuffer, res.path, camPos, r, g, b);
            }

            renderGlowingLandingCircle(quadBuffer, smoothPos, camPos, res.impactSide, r, g, b);
        }
    }

    private boolean hasMultishot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemEnchantmentsComponent enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (var entry : enchants.getEnchantments()) {
            if (entry.getIdAsString().contains("multishot")) return true;
        }
        return false;
    }

    private float getBowPull(LivingEntity player) {
        if (!player.isUsingItem()) return 0.0f;
        ItemStack active = player.getActiveItem();
        if (!(active.getItem() instanceof BowItem)) return 0.0f;
        int useTicks = active.getItem().getMaxUseTime(active, player) - player.getItemUseTimeLeft();
        float f = useTicks / 20.0f;
        return Math.min(((f * f) + (f * 2.0f)) / 3.0f, 1.0f);
    }

    private TrajectoryResult simulateTrajectory(MinecraftClient mc, float speed, boolean isThrowable,
                                                float pitchOffset, double gravity, float yawSpreadDegrees) {
        float playerPitch = mc.player.getPitch();
        float playerYaw = mc.player.getYaw() + yawSpreadDegrees;

        double pitchRad = Math.toRadians(playerPitch + pitchOffset);
        double yawRad = Math.toRadians(playerYaw);

        Vec3d look = new Vec3d(-Math.sin(yawRad) * Math.cos(pitchRad),
                               -Math.sin(pitchRad),
                                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();

        Vec3d vel = look.multiply(speed);
        Vec3d pos = mc.player.getEyePos();

        List<Vec3d> path = new ArrayList<>();
        path.add(pos);

        Vec3d impactPoint = pos;

        for (int i = 0; i < 140 && vel.lengthSquared() >= 1e-6; i++) {
            double drag = 0.99;
            BlockPos bpos = BlockPos.ofFloored(pos);
            if (mc.world.getFluidState(bpos).isIn(FluidTags.WATER)) {
                drag = isThrowable ? 0.80 : 0.60;
            }

            if (isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y - gravity) * drag, vel.z * drag);
            }

            Vec3d next = pos.add(vel);
            BlockHitResult hitBlock = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            Vec3d end = hitBlock.getType() != HitResult.Type.MISS ? hitBlock.getPos() : next;

            Entity hitEntity = raycastEntity(mc, pos, end);
            if (hitEntity != null) {
                impactPoint = hitEntity.getBoundingBox().expand(0.3).raycast(pos, end).orElse(end);
                path.add(impactPoint);
                return new TrajectoryResult(path, impactPoint, Direction.UP);
            }

            if (hitBlock.getType() != HitResult.Type.MISS) {
                impactPoint = hitBlock.getPos();
                path.add(impactPoint);
                return new TrajectoryResult(path, impactPoint, hitBlock.getSide());
            }

            pos = next;
            if (!isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y * drag) - gravity, vel.z * drag);
            }
            path.add(pos);
            impactPoint = pos;
        }

        return new TrajectoryResult(path, impactPoint, Direction.UP);
    }

    private TrajectoryResult simulateInFlight(MinecraftClient mc, Entity entity) {
        Vec3d vel = entity.getVelocity();
        Vec3d pos = entity.getEntityPos();
        boolean isThrowable = entity instanceof ThrownEntity;
        double gravity = entity instanceof PotionEntity ? 0.05 : 0.03;

        List<Vec3d> path = new ArrayList<>();
        path.add(pos);

        for (int i = 0; i < 120 && vel.lengthSquared() >= 1e-6; i++) {
            double drag = 0.99;
            BlockPos bpos = BlockPos.ofFloored(pos);
            if (mc.world.getFluidState(bpos).isIn(FluidTags.WATER)) {
                drag = isThrowable ? 0.80 : 0.60;
            }

            if (isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y - gravity) * drag, vel.z * drag);
            }

            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));

            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getPos());
                return new TrajectoryResult(path, hit.getPos(), hit.getSide());
            }

            pos = next;
            if (!isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y * drag) - 0.05, vel.z * drag);
            }
            path.add(pos);
        }
        return new TrajectoryResult(path, pos, Direction.UP);
    }

    private Entity raycastEntity(MinecraftClient mc, Vec3d start, Vec3d end) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Box range = new Box(start, end).expand(1.0);

        for (Entity candidate : mc.world.getOtherEntities(mc.player, range)) {
            if (candidate.isAlive() && !candidate.isSpectator() && (candidate instanceof LivingEntity)) {
                Optional<Vec3d> hit = candidate.getBoundingBox().expand(0.3).raycast(start, end);
                if (hit.isPresent()) {
                    double dist = start.squaredDistanceTo(hit.get());
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = candidate;
                    }
                }
            }
        }
        return closest;
    }

    private void renderGlowingLandingCircle(VertexConsumer buf, Vec3d center, Vec3d camPos, Direction side, int cr, int cg, int cb) {
        if (side == null) side = Direction.UP;

        Vec3d offset = new Vec3d(side.getOffsetX() * 0.02, side.getOffsetY() * 0.02, side.getOffsetZ() * 0.02);
        Vec3d drawPos = center.add(offset);

        float cx = (float) (drawPos.x - camPos.x);
        float cy = (float) (drawPos.y - camPos.y);
        float cz = (float) (drawPos.z - camPos.z);

        Vector3f normal = new Vector3f(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vector3f u;
        if (Math.abs(normal.y) > 0.9f) {
            u = new Vector3f(1.0f, 0.0f, 0.0f);
        } else {
            u = new Vector3f(0.0f, 1.0f, 0.0f).cross(normal).normalize();
        }
        Vector3f v = new Vector3f(normal).cross(u).normalize();

        float baseR = (float) circleRadius.get();

        long now = System.currentTimeMillis();
        float pulse = (float) Math.sin(now * 0.005) * 0.04f;
        float R = baseR * (1.0f + pulse);

        int segments = 24;

        if (glowEffect.isEnabled()) {
            float glowR = R * 1.55f;
            int haloCenterA = 120;
            for (int i = 0; i < segments; i++) {
                float theta1 = (float) (2.0 * Math.PI * i / segments);
                float theta2 = (float) (2.0 * Math.PI * (i + 1) / segments);

                float cos1 = (float) Math.cos(theta1);
                float sin1 = (float) Math.sin(theta1);
                float cos2 = (float) Math.cos(theta2);
                float sin2 = (float) Math.sin(theta2);

                Vector3f pOuter1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * glowR)).add(new Vector3f(v).mul(sin1 * glowR));
                Vector3f pOuter2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * glowR)).add(new Vector3f(v).mul(sin2 * glowR));

                buf.vertex(cx, cy, cz).color(cr, cg, cb, haloCenterA);
                buf.vertex(pOuter1.x, pOuter1.y, pOuter1.z).color(cr, cg, cb, 0);
                buf.vertex(pOuter2.x, pOuter2.y, pOuter2.z).color(cr, cg, cb, 0);
                buf.vertex(cx, cy, cz).color(cr, cg, cb, haloCenterA);
            }

            float pillarH = 0.45f;
            Vector3f pNorm = new Vector3f(normal).mul(pillarH);
            for (int i = 0; i < segments; i++) {
                float theta1 = (float) (2.0 * Math.PI * i / segments);
                float theta2 = (float) (2.0 * Math.PI * (i + 1) / segments);

                float cos1 = (float) Math.cos(theta1);
                float sin1 = (float) Math.sin(theta1);
                float cos2 = (float) Math.cos(theta2);
                float sin2 = (float) Math.sin(theta2);

                float botR = R * 0.85f;
                Vector3f b1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * botR)).add(new Vector3f(v).mul(sin1 * botR));
                Vector3f b2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * botR)).add(new Vector3f(v).mul(sin2 * botR));

                Vector3f t1 = new Vector3f(b1).add(pNorm);
                Vector3f t2 = new Vector3f(b2).add(pNorm);

                buf.vertex(b1.x, b1.y, b1.z).color(cr, cg, cb, 80);
                buf.vertex(b2.x, b2.y, b2.z).color(cr, cg, cb, 80);
                buf.vertex(t2.x, t2.y, t2.z).color(cr, cg, cb, 0);
                buf.vertex(t1.x, t1.y, t1.z).color(cr, cg, cb, 0);
            }
        }

        float rOut1 = R * 0.94f;
        float rOut2 = R * 1.04f;
        for (int i = 0; i < segments; i++) {
            float theta1 = (float) (2.0 * Math.PI * i / segments);
            float theta2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            Vector3f p11 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * rOut1)).add(new Vector3f(v).mul(sin1 * rOut1));
            Vector3f p12 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * rOut1)).add(new Vector3f(v).mul(sin2 * rOut1));
            Vector3f p22 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * rOut2)).add(new Vector3f(v).mul(sin2 * rOut2));
            Vector3f p21 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * rOut2)).add(new Vector3f(v).mul(sin1 * rOut2));

            buf.vertex(p11.x, p11.y, p11.z).color(cr, cg, cb, 230);
            buf.vertex(p12.x, p12.y, p12.z).color(cr, cg, cb, 230);
            buf.vertex(p22.x, p22.y, p22.z).color(cr, cg, cb, 230);
            buf.vertex(p21.x, p21.y, p21.z).color(cr, cg, cb, 230);
        }

        float rIn1 = R * 0.40f;
        float rIn2 = R * 0.48f;
        for (int i = 0; i < segments; i++) {
            float theta1 = (float) (2.0 * Math.PI * i / segments);
            float theta2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            Vector3f p11 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * rIn1)).add(new Vector3f(v).mul(sin1 * rIn1));
            Vector3f p12 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * rIn1)).add(new Vector3f(v).mul(sin2 * rIn1));
            Vector3f p22 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * rIn2)).add(new Vector3f(v).mul(sin2 * rIn2));
            Vector3f p21 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * rIn2)).add(new Vector3f(v).mul(sin1 * rIn2));

            buf.vertex(p11.x, p11.y, p11.z).color(cr, cg, cb, 190);
            buf.vertex(p12.x, p12.y, p12.z).color(cr, cg, cb, 190);
            buf.vertex(p22.x, p22.y, p22.z).color(cr, cg, cb, 190);
            buf.vertex(p21.x, p21.y, p21.z).color(cr, cg, cb, 190);
        }

        float dotR = R * 0.12f;
        int wr = Math.min(255, cr + 45);
        int wg = Math.min(255, cg + 45);
        int wb = Math.min(255, cb + 45);
        for (int i = 0; i < segments; i++) {
            float theta1 = (float) (2.0 * Math.PI * i / segments);
            float theta2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            Vector3f p1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos1 * dotR)).add(new Vector3f(v).mul(sin1 * dotR));
            Vector3f p2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos2 * dotR)).add(new Vector3f(v).mul(sin2 * dotR));

            buf.vertex(cx, cy, cz).color(wr, wg, wb, 255);
            buf.vertex(p1.x, p1.y, p1.z).color(wr, wg, wb, 255);
            buf.vertex(p2.x, p2.y, p2.z).color(wr, wg, wb, 255);
            buf.vertex(cx, cy, cz).color(wr, wg, wb, 255);
        }
    }

    private void renderPathRibbon(VertexConsumer buf, List<Vec3d> path, Vec3d camPos, int r, int g, int b) {
        int count = path.size();
        if (count < 2) return;

        float halfWidth = 0.015f;

        for (int i = 0; i < count - 1; i++) {
            Vec3d p0 = path.get(i);
            Vec3d p1 = path.get(i + 1);

            float prog0 = (float) i / (float) count;
            float prog1 = (float) (i + 1) / (float) count;

            int a0 = Math.round(40 + prog0 * 180);
            int a1 = Math.round(40 + prog1 * 180);

            Vec3d dir = p1.subtract(p0).normalize();
            Vec3d camDir = p0.subtract(camPos).normalize();
            Vec3d side = dir.crossProduct(camDir).normalize().multiply(halfWidth);

            float x0a = (float) (p0.x + side.x - camPos.x);
            float y0a = (float) (p0.y + side.y - camPos.y);
            float z0a = (float) (p0.z + side.z - camPos.z);

            float x0b = (float) (p0.x - side.x - camPos.x);
            float y0b = (float) (p0.y - side.y - camPos.y);
            float z0b = (float) (p0.z - side.z - camPos.z);

            float x1b = (float) (p1.x - side.x - camPos.x);
            float y1b = (float) (p1.y - side.y - camPos.y);
            float z1b = (float) (p1.z - side.z - camPos.z);

            float x1a = (float) (p1.x + side.x - camPos.x);
            float y1a = (float) (p1.y + side.y - camPos.y);
            float z1a = (float) (p1.z + side.z - camPos.z);

            buf.vertex(x0a, y0a, z0a).color(r, g, b, a0);
            buf.vertex(x0b, y0b, z0b).color(r, g, b, a0);
            buf.vertex(x1b, y1b, z1b).color(r, g, b, a1);
            buf.vertex(x1a, y1a, z1a).color(r, g, b, a1);
        }
    }

    private static class TrajectoryResult {
        final List<Vec3d> path;
        final Vec3d impactPoint;
        final Direction impactSide;

        TrajectoryResult(List<Vec3d> path, Vec3d impactPoint, Direction impactSide) {
            this.path = path;
            this.impactPoint = impactPoint;
            this.impactSide = impactSide != null ? impactSide : Direction.UP;
        }
    }
}
