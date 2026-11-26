package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.render.world.TrajectoryLines;
import egg.client.ui.theme.Theme;
import egg.client.util.EntityUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import egg.client.util.ColorUtil;
import net.minecraft.util.Arm;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Trajectories extends Module {

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Кастом", "Выбор источника цвета", "Кастом", "Акцент", "Радуга");
    private final ColorSetting customColor = new ColorSetting("Цвет", 0xFFFF6B81, "Цвет траектории и круга");
    private final SliderSetting circleRadius = new SliderSetting("Радиус круга", 0.45, 0.20, 1.20, 0.05, "Радиус светящегося круга приземления");
    private final SliderSetting lineWidth = new SliderSetting("Толщина линии", 1.55, 0.75, 2.50, 0.05, "Толщина линии в пикселях при 1080p") {
        @Override
        public void set(double value) {
            super.set(value > 0.0 && value <= 0.060
                    ? MathHelper.clampedMap(value, 0.010, 0.060, 1.10, 2.20) : value);
        }
    };
    private final BooleanSetting showLine = new BooleanSetting("Линия полета", true, "Отображать траекторию полета снаряда");
    private final BooleanSetting verticalRay = new BooleanSetting("Вертикальный луч", true, "Световой луч вверх от точки приземления");
    private final SliderSetting rayHeight = new SliderSetting("Высота луча", 1.0, 0.3, 3.0, 0.1, "Высота вертикального светового луча");
    private final BooleanSetting glowEffect = new BooleanSetting("Неоновое свечение", true, "Мягкое неоновое свечение линии и круга");
    private final BooleanSetting handOffset = new BooleanSetting("От руки (1-е лицо)", true, "Начало траектории из руки игрока");
    private final SliderSetting smoothSpeed = new SliderSetting("Плавность", 22.0, 5.0, 40.0, 1.0, "Скорость плавного следования круга");
    private final SliderSetting pullThreshold = new SliderSetting("При натяжении", 0.0, 0.0, 1.0, 0.05, "Порог натяжения лука (0 - всегда)");
    private final BooleanSetting inFlightPrediction = new BooleanSetting("Линия летящих снарядов", true, "Прогноз полёта уже запущенных снарядов");
    private final BooleanSetting potionRadius = new BooleanSetting("Радиус зелий", true, "Круглая зона на земле, где вы получите эффект от зелья");
    private final BooleanSetting potionEffectColor = new BooleanSetting("Цвет эффекта зелья", true, "Цвет зоны по эффекту зелья (зелёный — вы в зоне)");

    private BufferAllocator lineAllocator;
    private BufferAllocator potionAllocator;

    public Trajectories() {
        super("Trajectories", "Показывает линию полета стрел, зелий и жемчуга края", Category.ESP);
        customColor.setVisible(() -> "Кастом".equals(colorMode.get()));
        rayHeight.setVisible(() -> verticalRay.get());
        pullThreshold.setVisible(() -> showLine.get());
        potionEffectColor.setVisible(() -> potionRadius.isEnabled());
        addSettings(colorMode, customColor, circleRadius, lineWidth, showLine, verticalRay, rayHeight, glowEffect, handOffset, smoothSpeed, pullThreshold, inFlightPrediction, potionRadius, potionEffectColor);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (lineAllocator != null) {
            lineAllocator.close();
            lineAllocator = null;
        }
        if (potionAllocator != null) {
            potionAllocator.close();
            potionAllocator = null;
        }
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float tickDelta = mc.getRenderTickCounter() != null ? mc.getRenderTickCounter().getTickProgress(false) : 1.0f;
        render(consumers, camPos, tickDelta);
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos, float tickDelta) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (lineAllocator == null) lineAllocator = new BufferAllocator(48 * 1024);
        try {
            BufferBuilder mesh = new BufferBuilder(lineAllocator, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
            Vector3f forward = new Vector3f(0, 0, -1).rotate(mc.gameRenderer.getCamera().getRotation());
            float pixelScale = MathHelper.clamp(mc.getFramebuffer().textureHeight / 1080.0f, 0.78f, 1.75f);
            var lines = new TrajectoryLines(mesh, camPos, new Vec3d(forward.x, forward.y, forward.z),
                    lineWidth.getValueFloat(), pixelScale, glowEffect.isEnabled());
            renderEffects(mc, consumers, camPos, tickDelta, lines);
            var built = mesh.endNullable();
            if (built != null) MeshDrawUtil.drawLabeled(Pipelines.TRAJECTORY_LINE, built, "Egg trajectory line");
        } finally {
            lineAllocator.reset();
        }
    }

    private void renderEffects(MinecraftClient mc, VertexConsumerProvider consumers, Vec3d camPos, float tickDelta, TrajectoryLines lines) {
        int col = resolveColor();

        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());
        VertexConsumer glowBuffer = glowEffect.isEnabled() ? consumers.getBuffer(RenderLayers.lightning()) : null;
        List<PotionZone> potionZones = potionRadius.isEnabled() ? new ArrayList<>(4) : null;

        if (showLine.isEnabled() && inFlightPrediction.isEnabled()) {
            Box viewRange = mc.player.getBoundingBox().expand(80.0);
            for (Entity entity : mc.world.getOtherEntities(mc.player, viewRange)) {
                if (entity instanceof ProjectileEntity projectile && projectile.isAlive()
                        && projectile.getVelocity().lengthSquared() >= 0.0001) {
                    TrajectoryResult flyRes = simulateInFlight(mc, projectile, tickDelta);
                    lines.path(flyRes.path, Vec3d.ZERO, col);
                    if (potionZones != null && projectile instanceof PotionEntity potionEntity && flyRes.path.size() >= 2) {
                        ItemStack potStack = potionEntity.getStack();
                        if (isAreaPotion(potStack.getItem())) {
                            potionZones.add(new PotionZone(flyRes.impactPoint, potStack));
                        }
                    }
                }
            }
        }

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        if (isThrowableOrWeapon(main.getItem())) {
            renderItemPrediction(mc, main, Hand.MAIN_HAND, quadBuffer, glowBuffer, camPos, col, tickDelta, lines, potionZones);
        } else if (isThrowableOrWeapon(off.getItem())) {
            renderItemPrediction(mc, off, Hand.OFF_HAND, quadBuffer, glowBuffer, camPos, col, tickDelta, lines, potionZones);
        }

        if (potionZones != null && !potionZones.isEmpty()) {
            renderPotionZones(mc, potionZones, camPos, col);
        }
    }

    private int resolveColor() {
        String mode = colorMode.get();
        if ("Акцент".equals(mode)) {
            return Theme.getAccent();
        } else if ("Радуга".equals(mode)) {
            float hue = (System.currentTimeMillis() % 4500L) / 4500.0f;
            return java.awt.Color.HSBtoRGB(hue, 0.80f, 1.0f);
        }
        return customColor.get();
    }

    private boolean isThrowableOrWeapon(Item item) {
        return item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                || item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG
                || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION
                || item == Items.EXPERIENCE_BOTTLE || item == Items.WIND_CHARGE;
    }

    private void renderItemPrediction(MinecraftClient mc, ItemStack stack, Hand hand,
                                      VertexConsumer quadBuffer, VertexConsumer glowBuffer, Vec3d camPos,
                                      int color, float tickDelta, TrajectoryLines lines, List<PotionZone> potionZones) {
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
            gravity = 0.005;
        }

        if (speed <= 0.0f) return;

        if (pullThreshold.get() > 0.0) {
            if (item instanceof BowItem) {
                if (getBowPull(mc.player) < pullThreshold.get()) return;
            } else if (item instanceof CrossbowItem) {
                if (!CrossbowItem.isCharged(stack) && !mc.player.isUsingItem()) return;
            } else if (item instanceof TridentItem) {
                if (!mc.player.isUsingItem()) return;
            }
        }

        boolean isMulti = (item instanceof CrossbowItem) && hasMultishot(stack);
        float[] yawSpreads = isMulti ? new float[]{-10.0f, 0.0f, 10.0f} : new float[]{0.0f};

        Vec3d eyePos;
        if (mc.options.getPerspective().isFirstPerson()) {
            eyePos = camPos;
        } else {
            double px = MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX());
            double py = MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY()) + mc.player.getEyeHeight(mc.player.getPose());
            double pz = MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ());
            eyePos = new Vec3d(px, py, pz);
        }

        Vec3d offset = Vec3d.ZERO;
        if (handOffset.isEnabled() && mc.options.getPerspective().isFirstPerson()) {
            Arm arm = hand == Hand.MAIN_HAND ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
            float side = arm == Arm.RIGHT ? 1.0f : -1.0f;
            Vector3f local = new Vector3f(side * 0.25f, -0.19f, -0.34f).rotate(mc.gameRenderer.getCamera().getRotation());
            offset = new Vec3d(local.x, local.y, local.z);
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int i = 0; i < yawSpreads.length; i++) {
            float yawSpread = yawSpreads[i];
            TrajectoryResult res = simulateTrajectory(mc, eyePos, speed, isThrowable, pitchOffset, gravity, yawSpread, tickDelta);
            if (res.path.size() >= 2) {
                if (showLine.isEnabled()) {
                    lines.path(res.path, offset, color);
                }

                if (res.hitEntity instanceof LivingEntity target && target.isAlive() && target != mc.player) {
                    Box targetBox = CustomHitbox.getInterpolatedBoundingBox(target, tickDelta);
                    CustomHitbox.renderHitbox(quadBuffer, targetBox, camPos,
                            true, r, g, b, 45,
                            true, r, g, b, 240,
                            1.2f);
                } else {
                    renderLandingCircle(quadBuffer, glowBuffer, res.impactPoint, camPos, res.impactSide, r, g, b, lines);
                }
                if (potionZones != null && isAreaPotion(item)) {
                    potionZones.add(new PotionZone(res.impactPoint, stack));
                }
            }
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

    private TrajectoryResult simulateTrajectory(MinecraftClient mc, Vec3d startPos, float speed, boolean isThrowable,
                                                float pitchOffset, double gravity, float yawSpreadDegrees, float tickDelta) {
        float playerPitch = mc.player.getPitch(tickDelta) + pitchOffset;
        float playerYaw = mc.player.getYaw(tickDelta) + yawSpreadDegrees;

        float f = playerPitch * ((float) Math.PI / 180F);
        float g = -playerYaw * ((float) Math.PI / 180F);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        Vec3d look = new Vec3d((double) (i * j), (double) (-k), (double) (h * j)).normalize();

        Vec3d vel = look.multiply(speed);
        Vec3d pVel = mc.player.getVelocity();
        vel = vel.add(pVel.x, mc.player.isOnGround() ? 0.0 : pVel.y, pVel.z);

        Vec3d pos = startPos;
        List<Vec3d> path = new ArrayList<>();
        path.add(pos);
        Vec3d impactPoint = pos;

        for (int step = 0; step < 160 && vel.lengthSquared() >= 1e-6; step++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hitBlock = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            Vec3d end = hitBlock.getType() != HitResult.Type.MISS ? hitBlock.getPos() : next;

            Entity hitEntity = raycastEntity(mc, pos, end, mc.player);
            if (hitEntity != null) {
                impactPoint = hitEntity.getBoundingBox().expand(0.3).raycast(pos, end).orElse(end);
                path.add(impactPoint);
                return new TrajectoryResult(path, impactPoint, Direction.UP, hitEntity);
            }

            if (hitBlock.getType() != HitResult.Type.MISS) {
                impactPoint = hitBlock.getPos();
                path.add(impactPoint);
                return new TrajectoryResult(path, impactPoint, hitBlock.getSide(), null);
            }

            pos = next;
            double drag = 0.99;
            BlockPos bpos = BlockPos.ofFloored(pos);
            if (mc.world.getFluidState(bpos).isIn(FluidTags.WATER)) {
                drag = isThrowable ? 0.80 : 0.60;
            }

            if (isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y - gravity) * drag, vel.z * drag);
            } else {
                vel = new Vec3d(vel.x * drag, (vel.y * drag) - gravity, vel.z * drag);
            }
            path.add(pos);
            impactPoint = pos;
        }

        return new TrajectoryResult(path, impactPoint, Direction.UP, null);
    }

    private TrajectoryResult simulateInFlight(MinecraftClient mc, Entity entity, float tickDelta) {
        Vec3d vel = entity.getVelocity();
        double ix = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double iy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double iz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        Vec3d pos = new Vec3d(ix, iy, iz);

        boolean isThrowable = entity instanceof ThrownEntity;
        double gravity = entity instanceof PotionEntity ? 0.05 : 0.03;

        List<Vec3d> path = new ArrayList<>();
        path.add(pos);

        for (int step = 0; step < 140 && vel.lengthSquared() >= 1e-6; step++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));

            Vec3d end = hit.getType() != HitResult.Type.MISS ? hit.getPos() : next;
            Entity hitEntity = raycastEntity(mc, pos, end, entity);
            if (hitEntity != null) {
                Vec3d entImpact = hitEntity.getBoundingBox().expand(0.3).raycast(pos, end).orElse(end);
                path.add(entImpact);
                return new TrajectoryResult(path, entImpact, Direction.UP, hitEntity);
            }

            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getPos());
                return new TrajectoryResult(path, hit.getPos(), hit.getSide(), null);
            }

            pos = next;
            double drag = 0.99;
            BlockPos bpos = BlockPos.ofFloored(pos);
            if (mc.world.getFluidState(bpos).isIn(FluidTags.WATER)) {
                drag = isThrowable ? 0.80 : 0.60;
            }

            if (isThrowable) {
                vel = new Vec3d(vel.x * drag, (vel.y - gravity) * drag, vel.z * drag);
            } else {
                vel = new Vec3d(vel.x * drag, (vel.y * drag) - 0.05, vel.z * drag);
            }
            path.add(pos);
        }
        return new TrajectoryResult(path, pos, Direction.UP);
    }

    private Entity raycastEntity(MinecraftClient mc, Vec3d start, Vec3d end, Entity ignoreEntity) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Box range = new Box(start, end).expand(1.0);

        for (Entity candidate : mc.world.getOtherEntities(ignoreEntity, range)) {
            if (candidate.isAlive() && !candidate.isSpectator() && (candidate instanceof LivingEntity) && !EntityUtil.isNakedInvisible(candidate)) {
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

    private void renderLandingCircle(VertexConsumer quadBuf, VertexConsumer glowBuf, Vec3d center, Vec3d camPos,
                                     Direction side, int cr, int cg, int cb, TrajectoryLines lines) {
        if (side == null) side = Direction.UP;

        Vec3d normOffset = new Vec3d(side.getOffsetX() * 0.012, side.getOffsetY() * 0.012, side.getOffsetZ() * 0.012);
        Vec3d drawPos = center.add(normOffset);

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
        float pulse = (float) Math.sin(now * 0.005) * 0.02f;
        float R = baseR * (1.0f + pulse);

        int segments = 40;
        float[] cos = new float[segments + 1];
        float[] sin = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            cos[i] = (float) Math.cos(angle);
            sin[i] = (float) Math.sin(angle);
        }

        int fillCenterA = 75;
        int fillEdgeA = 40;
        for (int i = 0; i < segments; i++) {
            Vector3f p1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * R)).add(new Vector3f(v).mul(sin[i] * R));
            Vector3f p2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * R)).add(new Vector3f(v).mul(sin[i + 1] * R));

            quadBuf.vertex(cx, cy, cz).color(cr, cg, cb, fillCenterA);
            quadBuf.vertex(p1.x, p1.y, p1.z).color(cr, cg, cb, fillEdgeA);
            quadBuf.vertex(p2.x, p2.y, p2.z).color(cr, cg, cb, fillEdgeA);
            quadBuf.vertex(cx, cy, cz).color(cr, cg, cb, fillCenterA);

            quadBuf.vertex(cx, cy, cz).color(cr, cg, cb, fillCenterA);
            quadBuf.vertex(p2.x, p2.y, p2.z).color(cr, cg, cb, fillEdgeA);
            quadBuf.vertex(p1.x, p1.y, p1.z).color(cr, cg, cb, fillEdgeA);
            quadBuf.vertex(cx, cy, cz).color(cr, cg, cb, fillCenterA);
        }

        float rIn = R * 0.94f;
        float rOut = R * 1.04f;
        int ringA = 255;
        for (int i = 0; i < segments; i++) {
            Vector3f p11 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * rIn)).add(new Vector3f(v).mul(sin[i] * rIn));
            Vector3f p12 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * rIn)).add(new Vector3f(v).mul(sin[i + 1] * rIn));
            Vector3f p22 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * rOut)).add(new Vector3f(v).mul(sin[i + 1] * rOut));
            Vector3f p21 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * rOut)).add(new Vector3f(v).mul(sin[i] * rOut));

            drawQuadDoubleSided(quadBuf, p11.x, p11.y, p11.z, p12.x, p12.y, p12.z, p22.x, p22.y, p22.z, p21.x, p21.y, p21.z, cr, cg, cb, ringA);
        }

        if (glowBuf != null) {
            float gIn = R * 0.85f;
            float gOut = R * 1.15f;
            int gMidA = 150;
            for (int i = 0; i < segments; i++) {
                Vector3f piIn = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * gIn)).add(new Vector3f(v).mul(sin[i] * gIn));
                Vector3f piMid = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * R)).add(new Vector3f(v).mul(sin[i] * R));
                Vector3f piOut = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * gOut)).add(new Vector3f(v).mul(sin[i] * gOut));

                Vector3f pjIn = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * gIn)).add(new Vector3f(v).mul(sin[i + 1] * gIn));
                Vector3f pjMid = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * R)).add(new Vector3f(v).mul(sin[i + 1] * R));
                Vector3f pjOut = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * gOut)).add(new Vector3f(v).mul(sin[i + 1] * gOut));

                glowBuf.vertex(piIn.x, piIn.y, piIn.z).color(cr, cg, cb, 0);
                glowBuf.vertex(pjIn.x, pjIn.y, pjIn.z).color(cr, cg, cb, 0);
                glowBuf.vertex(pjMid.x, pjMid.y, pjMid.z).color(cr, cg, cb, gMidA);
                glowBuf.vertex(piMid.x, piMid.y, piMid.z).color(cr, cg, cb, gMidA);

                glowBuf.vertex(piMid.x, piMid.y, piMid.z).color(cr, cg, cb, gMidA);
                glowBuf.vertex(pjMid.x, pjMid.y, pjMid.z).color(cr, cg, cb, gMidA);
                glowBuf.vertex(pjOut.x, pjOut.y, pjOut.z).color(cr, cg, cb, 0);
                glowBuf.vertex(piOut.x, piOut.y, piOut.z).color(cr, cg, cb, 0);
            }
        }

        float dotR = R * 0.11f;
        int dotWr = Math.min(255, cr + 45);
        int dotWg = Math.min(255, cg + 45);
        int dotWb = Math.min(255, cb + 45);
        for (int i = 0; i < segments; i++) {
            Vector3f p1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * dotR)).add(new Vector3f(v).mul(sin[i] * dotR));
            Vector3f p2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * dotR)).add(new Vector3f(v).mul(sin[i + 1] * dotR));

            quadBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(p1.x, p1.y, p1.z).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(p2.x, p2.y, p2.z).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 255);

            quadBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(p2.x, p2.y, p2.z).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(p1.x, p1.y, p1.z).color(dotWr, dotWg, dotWb, 255);
            quadBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 255);
        }

        if (glowBuf != null) {
            float dotGlowR = R * 0.28f;
            for (int i = 0; i < segments; i++) {
                Vector3f p1 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i] * dotGlowR)).add(new Vector3f(v).mul(sin[i] * dotGlowR));
                Vector3f p2 = new Vector3f(cx, cy, cz).add(new Vector3f(u).mul(cos[i + 1] * dotGlowR)).add(new Vector3f(v).mul(sin[i + 1] * dotGlowR));

                glowBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 210);
                glowBuf.vertex(p1.x, p1.y, p1.z).color(cr, cg, cb, 0);
                glowBuf.vertex(p2.x, p2.y, p2.z).color(cr, cg, cb, 0);
                glowBuf.vertex(cx, cy, cz).color(dotWr, dotWg, dotWb, 210);
            }
        }

        if (verticalRay.isEnabled()) {
            Vec3d tip = drawPos.add(side.getOffsetX() * rayHeight.get(), side.getOffsetY() * rayHeight.get(),
                    side.getOffsetZ() * rayHeight.get());
            lines.impactRay(drawPos, tip, (cr << 16) | (cg << 8) | cb);
        }
    }

    private static void drawQuadDoubleSided(VertexConsumer buf,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int r, int g, int b, int a) {
        buf.vertex(x1, y1, z1).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x4, y4, z4).color(r, g, b, a);

        buf.vertex(x4, y4, z4).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x1, y1, z1).color(r, g, b, a);
    }

    private static boolean isAreaPotion(Item item) {
        return item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private void renderPotionZones(MinecraftClient mc, List<PotionZone> zones, Vec3d camPos, int fallbackCol) {
        if (potionAllocator == null) {
            potionAllocator = new BufferAllocator(16 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
        }
        try {
            BufferBuilder mesh = new BufferBuilder(potionAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (PotionZone zone : zones) {
                Vec3d impact = zone.impactPoint;
                boolean lingering = zone.stack.getItem() == Items.LINGERING_POTION;
                double groundY = findPotionFloorY(mc, impact);
                float dy = (float) Math.abs(impact.y - groundY);
                float maxR = lingering ? 3.0f : 4.0f;
                if (dy > (lingering ? 1.8f : 2.2f)) continue;

                float effectiveR = lingering
                        ? maxR
                        : Math.max(0.45f, (float) Math.sqrt(Math.max(0.0, 16.0 - dy * dy)));

                boolean inside = isPlayerReceivingPotion(mc.player, impact, lingering);

                int potCol = fallbackCol;
                if (potionEffectColor.isEnabled()) {
                    PotionContentsComponent contents = zone.stack.get(DataComponentTypes.POTION_CONTENTS);
                    if (contents != null) {
                        int c = contents.getColor();
                        if ((c & 0x00FFFFFF) != 0) {
                            potCol = 0xFF000000 | (c & 0xFFFFFF);
                        }
                    }
                }

                int drawCol = inside ? ColorUtil.interpolate(potCol, 0xFF22C55E, 0.72f) : potCol;
                float uOffset = inside ? 0.0f : 4.0f;
                float alpha = inside ? 0.94f : 0.78f;

                float uv = 1.04f;
                float halfQuad = (effectiveR / 0.90f) * uv;
                float cx = (float) (impact.x - camPos.x);
                float cy = (float) (groundY - camPos.y);
                float cz = (float) (impact.z - camPos.z);

                int cr = (drawCol >> 16) & 0xFF;
                int cg = (drawCol >> 8) & 0xFF;
                int cb = drawCol & 0xFF;
                int ca = Math.round(alpha * 255.0f);

                mesh.vertex(cx - halfQuad, cy, cz - halfQuad).texture(uOffset - uv, -uv).color(cr, cg, cb, ca);
                mesh.vertex(cx + halfQuad, cy, cz - halfQuad).texture(uOffset + uv, -uv).color(cr, cg, cb, ca);
                mesh.vertex(cx + halfQuad, cy, cz + halfQuad).texture(uOffset + uv, uv).color(cr, cg, cb, ca);
                mesh.vertex(cx - halfQuad, cy, cz + halfQuad).texture(uOffset - uv, uv).color(cr, cg, cb, ca);
            }
            var built = mesh.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeled(Pipelines.REACH_CIRCLE, built, "egg potion radius");
            }
        } finally {
            potionAllocator.reset();
        }
    }

    private static double findPotionFloorY(MinecraftClient mc, Vec3d impact) {
        Vec3d start = new Vec3d(impact.x, impact.y + 0.15, impact.z);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                start, new Vec3d(impact.x, impact.y - 2.3, impact.z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos().y + 0.025;
        }
        return impact.y + 0.025;
    }

    private static boolean isPlayerReceivingPotion(LivingEntity player, Vec3d impact, boolean lingering) {
        if (lingering) {
            double dx = player.getX() - impact.x;
            double dz = player.getZ() - impact.z;
            double dy = Math.abs(player.getY() - impact.y);
            double r = 3.0 + player.getWidth() * 0.5;
            return dx * dx + dz * dz <= r * r && dy <= 1.8;
        }
        Box impactBox = new Box(impact, impact).expand(4.0, 2.0, 4.0);
        return player.getBoundingBox().intersects(impactBox) && player.getEntityPos().squaredDistanceTo(impact) < 16.0;
    }

    private record PotionZone(Vec3d impactPoint, ItemStack stack) {}

    private static class TrajectoryResult {
        final List<Vec3d> path;
        final Vec3d impactPoint;
        final Direction impactSide;
        final Entity hitEntity;

        TrajectoryResult(List<Vec3d> path, Vec3d impactPoint, Direction impactSide, Entity hitEntity) {
            this.path = path;
            this.impactPoint = impactPoint;
            this.impactSide = impactSide != null ? impactSide : Direction.UP;
            this.hitEntity = hitEntity;
        }

        TrajectoryResult(List<Vec3d> path, Vec3d impactPoint, Direction impactSide) {
            this(path, impactPoint, impactSide, null);
        }
    }
}
