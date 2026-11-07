package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.ExplosionImpl;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LethalZone extends Module {

    private static LethalZone instance;

    private final BooleanSetting showCrystals = new BooleanSetting("Кристаллы", true, "Опасные зоны энд-кристаллов");
    private final BooleanSetting showAnchors = new BooleanSetting("Якоря", true, "Опасные зоны заряженных якорей возрождения");
    private final BooleanSetting showTnt = new BooleanSetting("ТНТ", true, "Опасные зоны активированного ТНТ");
    private final BooleanSetting damageBadge = new BooleanSetting("Таблички урона", true, "3D-метки урона и статуса над объектами");
    private final BooleanSetting radarScan = new BooleanSetting("Радар-сканер", true, "Вращающийся сканирующий луч");
    private final BooleanSetting pulse = new BooleanSetting("Пульсация", true, "Анимация пульсации летальной зоны");
    private final SliderSetting searchRange = new SliderSetting("Дистанция", 24.0, 8.0, 48.0, 2.0, "Дальность обнаружения угроз");
    private final SliderSetting fillOpacity = new SliderSetting("Прозрачность", 45.0, 10.0, 100.0, 5.0, "Прозрачность проекций на полу");

    private final List<HazardSource> activeHazards = new ArrayList<>();
    private float animTime = 0.0f;
    private long lastFrameNanos = 0L;

    public static class HazardSource {
        public final String name;
        public final Vec3d pos;
        public final double groundY;
        public final float power;
        public final float maxRadius;
        public final float lethalRadius;
        public final float currentDamage;
        public final boolean isLethal;
        public final float badgeOffsetY;
        public final String extraInfo;

        public HazardSource(String name, Vec3d pos, double groundY, float power, float maxRadius,
                            float lethalRadius, float currentDamage, boolean isLethal, float badgeOffsetY, String extraInfo) {
            this.name = name;
            this.pos = pos;
            this.groundY = groundY;
            this.power = power;
            this.maxRadius = maxRadius;
            this.lethalRadius = lethalRadius;
            this.currentDamage = currentDamage;
            this.isLethal = isLethal;
            this.badgeOffsetY = badgeOffsetY;
            this.extraInfo = extraInfo;
        }
    }

    public LethalZone() {
        super("LethalZone", "Визуализирует зоны летального урона от кристаллов, якорей и ТНТ", Category.ESP);
        instance = this;
        addSettings(showCrystals, showAnchors, showTnt, damageBadge, radarScan, pulse, searchRange, fillOpacity);
    }

    public static LethalZone getInstance() {
        return instance;
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        animTime += dt;

        updateHazards(mc.player);

        if (activeHazards.isEmpty()) return;

        VertexConsumer buf = consumers.getBuffer(RenderLayers.debugQuads());
        float opacity = (float) (fillOpacity.get() / 100.0);

        for (HazardSource h : activeHazards) {
            renderHazardField(buf, h, camPos, opacity, animTime);
        }
    }

    public void render2D(DrawContext context) {
        if (!isEnabled() || !damageBadge.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (activeHazards.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        for (HazardSource h : activeHazards) {
            Vector2f screen = ProjectUtil.toScreen(h.pos.x, h.pos.y + h.badgeOffsetY, h.pos.z);
            if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) continue;
            if (screen.x < -80.0f || screen.x > screenW + 80.0f || screen.y < -80.0f || screen.y > screenH + 80.0f) {
                continue;
            }

            double dist = mc.player.getEntityPos().distanceTo(h.pos);
            String distStr = String.format(Locale.US, "%.1fm", dist);

            String statusStr;
            int statusCol;
            if (h.isLethal) {
                statusStr = String.format(Locale.US, "LETHAL -%.1f HP", h.currentDamage);
                statusCol = 0xFFFF2E4C;
            } else if (h.currentDamage > 10.0f) {
                statusStr = String.format(Locale.US, "DANGER -%.1f HP", h.currentDamage);
                statusCol = 0xFFFFAA00;
            } else if (h.currentDamage > 0.5f) {
                statusStr = String.format(Locale.US, "-%.1f HP", h.currentDamage);
                statusCol = 0xFF44FF88;
            } else {
                statusStr = "SAFE";
                statusCol = 0xFF38EF7D;
            }

            String titleStr = h.extraInfo != null && !h.extraInfo.isEmpty() ? h.name + " [" + h.extraInfo + "]" : h.name;

            float scale = (float) MathHelper.clamp(1.0 - (dist - 6.0) / 40.0 * 0.35, 0.75, 1.05);
            float fontTitle = 10.0f * scale;
            float fontStatus = 9.0f * scale;
            float padX = 6.0f * scale;
            float badgeH = 15.0f * scale;
            float gap = 5.0f * scale;

            float wTitle = Fonts.d.a(titleStr, fontTitle);
            float wStatus = Fonts.d.a(statusStr, fontStatus);
            float wDist = Fonts.b.a(distStr, fontStatus);
            float badgeW = padX + wTitle + gap + wStatus + gap + wDist + padX;

            float bx = screen.x - badgeW / 2.0f;
            float by = screen.y - badgeH / 2.0f;

            int outlineCol = h.isLethal ? 0xFFFF2E4C : (h.currentDamage > 10.0f ? 0xFFFFAA00 : ColorUtil.withAlphaFloat(Theme.getAccent(), 0.65f));
            Draw2DProcessor.INSTANCE.drawGlow(null, bx, by, badgeW, badgeH, 4.0f * scale, outlineCol, 6.0f * scale);
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, badgeW, badgeH, 4.0f * scale, ColorUtil.rgba(14, 15, 22, 230));
            Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, badgeW, badgeH, 4.0f * scale, 0.8f, outlineCol);

            float ty = by + (badgeH - Fonts.d.a(fontTitle)) / 2.0f - 0.5f;
            Fonts.d.a(null, titleStr, bx + padX, ty, fontTitle, 0xFFFFFFFF);
            Fonts.d.a(null, statusStr, bx + padX + wTitle + gap, ty, fontStatus, statusCol);
            Fonts.b.a(null, distStr, bx + padX + wTitle + gap + wStatus + gap, ty + 0.5f, fontStatus, ColorUtil.rgba(180, 185, 200, 240));
        }

        DrawBatcher.flushPending();
    }

    private void updateHazards(PlayerEntity player) {
        activeHazards.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        double maxDist = searchRange.get();
        double maxDistSq = maxDist * maxDist;
        Vec3d pPos = player.getEntityPos();

        if (showCrystals.isEnabled()) {
            for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class, player.getBoundingBox().expand(maxDist), c -> c.isAlive())) {
                Vec3d cPos = crystal.getEntityPos().add(0.0, 0.5, 0.0);
                if (cPos.squaredDistanceTo(pPos) > maxDistSq) continue;
                float power = 6.0f;
                float currentDmg = calculateExplosionDamage(player, cPos, power);
                float playerHp = player.getHealth() + player.getAbsorptionAmount();
                boolean lethal = currentDmg >= playerHp;
                float lethalR = calculateLethalRadius(player, cPos, power);
                double groundY = findGroundY(cPos);
                activeHazards.add(new HazardSource("Crystal", cPos, groundY, power, power * 2.0f, lethalR, currentDmg, lethal, 1.4f, null));
            }
        }

        if (showTnt.isEnabled()) {
            for (TntEntity tnt : mc.world.getEntitiesByClass(TntEntity.class, player.getBoundingBox().expand(maxDist), t -> t.isAlive())) {
                Vec3d tPos = tnt.getEntityPos().add(0.0, 0.49, 0.0);
                if (tPos.squaredDistanceTo(pPos) > maxDistSq) continue;
                float power = 4.0f;
                float currentDmg = calculateExplosionDamage(player, tPos, power);
                float playerHp = player.getHealth() + player.getAbsorptionAmount();
                boolean lethal = currentDmg >= playerHp;
                float lethalR = calculateLethalRadius(player, tPos, power);
                double groundY = findGroundY(tPos);
                int fuseTicks = tnt.getFuse();
                String fuseStr = String.format(Locale.US, "%.1fs", fuseTicks / 20.0f);
                activeHazards.add(new HazardSource("TNT", tPos, groundY, power, power * 2.0f, lethalR, currentDmg, lethal, 1.2f, fuseStr));
            }
        }

        if (showAnchors.isEnabled() && mc.world.getRegistryKey() != net.minecraft.world.World.NETHER) {
            BlockPos centerBp = BlockPos.ofFloored(pPos);
            int bRadius = (int) Math.min(18.0, maxDist);
            BlockPos.Mutable mut = new BlockPos.Mutable();

            for (int dx = -bRadius; dx <= bRadius; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -bRadius; dz <= bRadius; dz++) {
                        mut.set(centerBp.getX() + dx, centerBp.getY() + dy, centerBp.getZ() + dz);
                        BlockState state = mc.world.getBlockState(mut);
                        if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
                            int charges = state.get(RespawnAnchorBlock.CHARGES);
                            if (charges > 0) {
                                Vec3d aPos = new Vec3d(mut.getX() + 0.5, mut.getY() + 0.5, mut.getZ() + 0.5);
                                if (aPos.squaredDistanceTo(pPos) > maxDistSq) continue;
                                float power = 5.0f;
                                float currentDmg = calculateExplosionDamage(player, aPos, power);
                                float playerHp = player.getHealth() + player.getAbsorptionAmount();
                                boolean lethal = currentDmg >= playerHp;
                                float lethalR = calculateLethalRadius(player, aPos, power);
                                double groundY = mut.getY() + 1.01;
                                String chargeStr = charges + "★";
                                activeHazards.add(new HazardSource("Anchor", aPos, groundY, power, power * 2.0f, lethalR, currentDmg, lethal, 1.1f, chargeStr));
                            }
                        }
                    }
                }
            }
        }
    }

    private void renderHazardField(VertexConsumer buf, HazardSource h, Vec3d camPos, float opacity, float time) {
        float cx = (float) (h.pos.x - camPos.x);
        float cy = (float) (h.groundY - camPos.y);
        float cz = (float) (h.pos.z - camPos.z);

        int segments = 40;
        float angleStep = (float) (2.0 * Math.PI / segments);

        float pulseFactor = pulse.isEnabled() ? (0.85f + 0.15f * (float) Math.sin(time * (h.isLethal ? 6.5f : 3.5f))) : 1.0f;
        float effLethalR = h.lethalRadius * pulseFactor;

        int lRed = 255;
        int lGreen = h.isLethal ? 25 : 45;
        int lBlue = 55;
        int lFillA = (int) (opacity * (h.isLethal ? 80.0f : 55.0f));
        int lBorderA = (int) (opacity * 240.0f);

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float x1 = cx + (float) Math.sin(a1) * effLethalR;
            float z1 = cz + (float) Math.cos(a1) * effLethalR;
            float x2 = cx + (float) Math.sin(a2) * effLethalR;
            float z2 = cz + (float) Math.cos(a2) * effLethalR;

            buf.vertex(cx, cy + 0.005f, cz).color(lRed, lGreen, lBlue, lFillA);
            buf.vertex(x1, cy + 0.005f, z1).color(lRed, lGreen, lBlue, lFillA / 2);
            buf.vertex(x2, cy + 0.005f, z2).color(lRed, lGreen, lBlue, lFillA / 2);
            buf.vertex(cx, cy + 0.005f, cz).color(lRed, lGreen, lBlue, lFillA);

            float bInX1 = cx + (float) Math.sin(a1) * (effLethalR - 0.06f);
            float bInZ1 = cz + (float) Math.cos(a1) * (effLethalR - 0.06f);
            float bInX2 = cx + (float) Math.sin(a2) * (effLethalR - 0.06f);
            float bInZ2 = cz + (float) Math.cos(a2) * (effLethalR - 0.06f);

            buf.vertex(bInX1, cy + 0.008f, bInZ1).color(lRed, lGreen, lBlue, lBorderA);
            buf.vertex(x1, cy + 0.008f, z1).color(lRed, lGreen, lBlue, lBorderA);
            buf.vertex(x2, cy + 0.008f, z2).color(lRed, lGreen, lBlue, lBorderA);
            buf.vertex(bInX2, cy + 0.008f, bInZ2).color(lRed, lGreen, lBlue, lBorderA);
        }

        int dRed = 255;
        int dGreen = 160;
        int dBlue = 30;
        int dFillA = (int) (opacity * 28.0f);
        int dBorderA = (int) (opacity * 140.0f);

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float sin1 = (float) Math.sin(a1);
            float cos1 = (float) Math.cos(a1);
            float sin2 = (float) Math.sin(a2);
            float cos2 = (float) Math.cos(a2);

            float inX1 = cx + sin1 * effLethalR;
            float inZ1 = cz + cos1 * effLethalR;
            float inX2 = cx + sin2 * effLethalR;
            float inZ2 = cz + cos2 * effLethalR;

            float outX1 = cx + sin1 * h.maxRadius;
            float outZ1 = cz + cos1 * h.maxRadius;
            float outX2 = cx + sin2 * h.maxRadius;
            float outZ2 = cz + cos2 * h.maxRadius;

            buf.vertex(inX1, cy + 0.003f, inZ1).color(dRed, dGreen, dBlue, dFillA);
            buf.vertex(outX1, cy + 0.003f, outZ1).color(dRed, dGreen, dBlue, 0);
            buf.vertex(outX2, cy + 0.003f, outZ2).color(dRed, dGreen, dBlue, 0);
            buf.vertex(inX2, cy + 0.003f, inZ2).color(dRed, dGreen, dBlue, dFillA);

            float bInX1 = cx + sin1 * (h.maxRadius - 0.05f);
            float bInZ1 = cz + cos1 * (h.maxRadius - 0.05f);
            float bInX2 = cx + sin2 * (h.maxRadius - 0.05f);
            float bInZ2 = cz + cos2 * (h.maxRadius - 0.05f);

            buf.vertex(bInX1, cy + 0.006f, bInZ1).color(dRed, dGreen, dBlue, dBorderA);
            buf.vertex(outX1, cy + 0.006f, outZ1).color(dRed, dGreen, dBlue, dBorderA);
            buf.vertex(outX2, cy + 0.006f, outZ2).color(dRed, dGreen, dBlue, dBorderA);
            buf.vertex(bInX2, cy + 0.006f, bInZ2).color(dRed, dGreen, dBlue, dBorderA);
        }

        if (radarScan.isEnabled()) {
            float sweepAngle = (float) ((time * 2.2f) % (2.0 * Math.PI));
            float sweepArc = (float) Math.toRadians(60.0);

            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;

                float diff = (a1 - sweepAngle + (float) (4.0 * Math.PI)) % (float) (2.0 * Math.PI);
                if (diff < sweepArc) {
                    float beamFactor = 1.0f - (diff / sweepArc);
                    int beamA = (int) (beamFactor * opacity * 160.0f);

                    float x1 = cx + (float) Math.sin(a1) * h.maxRadius;
                    float z1 = cz + (float) Math.cos(a1) * h.maxRadius;
                    float x2 = cx + (float) Math.sin(a2) * h.maxRadius;
                    float z2 = cz + (float) Math.cos(a2) * h.maxRadius;

                    buf.vertex(cx, cy + 0.007f, cz).color(255, 60, 60, beamA / 3);
                    buf.vertex(x1, cy + 0.007f, z1).color(255, 60, 60, beamA);
                    buf.vertex(x2, cy + 0.007f, z2).color(255, 60, 60, beamA);
                    buf.vertex(cx, cy + 0.007f, cz).color(255, 60, 60, beamA / 3);
                }
            }
        }
    }

    private double findGroundY(Vec3d pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return pos.y;
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                pos,
                pos.add(0.0, -5.0, 0.0),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos().y + 0.02;
        }
        return pos.y - 0.5;
    }

    public static float calculateExplosionDamage(PlayerEntity player, Vec3d explosionPos, float power) {
        if (player == null || player.getEntityWorld() == null) return 0.0f;
        double maxDist = power * 2.0;
        double dist = Math.sqrt(player.squaredDistanceTo(explosionPos));
        if (dist >= maxDist) return 0.0f;

        double q = dist / maxDist;
        double exposure = 1.0;
        try {
            exposure = ExplosionImpl.calculateReceivedDamage(explosionPos, player);
        } catch (Throwable ignored) {
            exposure = 0.80;
        }

        double impact = (1.0 - q) * exposure;
        double rawDamage = ((impact * impact + impact) / 2.0) * 7.0 * maxDist + 1.0;

        float armor = (float) player.getArmor();
        float toughness = 0.0f;
        try {
            toughness = (float) player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        } catch (Throwable ignored) {}

        float protPoints = 0.0f;
        net.minecraft.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET
        };
        for (net.minecraft.entity.EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack != null && !stack.isEmpty()) {
                protPoints += 3.5f;
            }
        }

        float f = 2.0f + toughness / 4.0f;
        float g = MathHelper.clamp(armor - (float) rawDamage / f, armor * 0.2f, 20.0f);
        float damageAfterArmor = (float) rawDamage * (1.0f - g / 25.0f);

        float protClamp = MathHelper.clamp(protPoints, 0.0f, 20.0f);
        float finalDamage = damageAfterArmor * (1.0f - protClamp / 25.0f);

        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            finalDamage = finalDamage * Math.max(0.0f, 1.0f - amp * 0.2f);
        }

        return Math.max(0.0f, finalDamage);
    }

    public static float calculateLethalRadius(PlayerEntity player, Vec3d explosionPos, float power) {
        float playerHp = player.getHealth() + player.getAbsorptionAmount();
        float maxDist = power * 2.0f;
        float low = 0.1f;
        float high = maxDist;
        float lethalR = 0.0f;

        for (int i = 0; i < 10; i++) {
            float mid = (low + high) * 0.5f;
            Vec3d testPos = explosionPos.add(mid, 0.0, 0.0);
            float dmg = calculateExplosionDamage(player, testPos, power);
            if (dmg >= playerHp) {
                lethalR = mid;
                low = mid;
            } else {
                high = mid;
            }
        }
        return Math.max(0.6f, lethalR);
    }
}
