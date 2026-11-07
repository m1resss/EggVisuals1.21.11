package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
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
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionImpl;
import org.joml.Vector2f;

import java.util.*;

public class LethalZone extends Module {

    private static LethalZone instance;

    private final BooleanSetting showCrystals = new BooleanSetting("Кристаллы", true, "Опасные зоны энд-кристаллов");
    private final BooleanSetting showAnchors = new BooleanSetting("Якоря", true, "Опасные зоны заряженных якорей");
    private final BooleanSetting showTnt = new BooleanSetting("ТНТ", true, "Опасные зоны активированного ТНТ");
    private final BooleanSetting damageBadge = new BooleanSetting("Таблички урона", true, "Метки урона над объектами");
    private final ModeSetting renderMode = new ModeSetting("Режим", "Поверхность", "Отображение на блоках", "Поверхность", "Полный блок");
    private final SliderSetting searchRange = new SliderSetting("Дистанция", 20.0, 8.0, 36.0, 2.0, "Дальность обнаружения угроз");
    private final SliderSetting fillOpacity = new SliderSetting("Прозрачность", 45.0, 10.0, 100.0, 5.0, "Прозрачность подсветки блоков");

    public static class HazardSource {
        public final String name;
        public final Vec3d pos;
        public final float power;
        public final float maxRadius;
        public final float currentDamage;
        public final boolean isLethal;
        public final float badgeOffsetY;
        public final String extraInfo;

        public HazardSource(String name, Vec3d pos, float power, float maxRadius,
                            float currentDamage, boolean isLethal, float badgeOffsetY, String extraInfo) {
            this.name = name;
            this.pos = pos;
            this.power = power;
            this.maxRadius = maxRadius;
            this.currentDamage = currentDamage;
            this.isLethal = isLethal;
            this.badgeOffsetY = badgeOffsetY;
            this.extraInfo = extraInfo;
        }
    }

    public static class DangerousBlock {
        public final BlockPos pos;
        public final float damage;
        public final boolean isLethal;

        public DangerousBlock(BlockPos pos, float damage, boolean isLethal) {
            this.pos = pos;
            this.damage = damage;
            this.isLethal = isLethal;
        }
    }

    private final List<HazardSource> activeHazards = new ArrayList<>();
    private final Map<BlockPos, DangerousBlock> dangerBlocks = new LinkedHashMap<>();

    public LethalZone() {
        super("LethalZone", "Выделяет цветом опасные блоки в зоне поражения кристаллов, якорей и ТНТ", Category.ESP);
        instance = this;
        addSettings(showCrystals, showAnchors, showTnt, damageBadge, renderMode, searchRange, fillOpacity);
    }

    public static LethalZone getInstance() {
        return instance;
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        updateHazards(mc.player);

        if (dangerBlocks.isEmpty()) return;

        VertexConsumer buf = consumers.getBuffer(RenderLayers.debugQuads());
        float opacity = (float) (fillOpacity.get() / 100.0);
        boolean fullBox = renderMode.is("Полный блок");

        for (DangerousBlock db : dangerBlocks.values()) {
            renderBlockHighlight(buf, db, camPos, opacity, fullBox);
        }
    }

    public void render2D(DrawContext context) {
        if (!isEnabled() || !damageBadge.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (activeHazards.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        Font font = Fonts.ROCKSTAR_ROUND_BOLD;

        for (HazardSource h : activeHazards) {
            Vector2f screen = ProjectUtil.toScreen(h.pos.x, h.pos.y + h.badgeOffsetY, h.pos.z);
            if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) continue;
            if (screen.x < -120.0f || screen.x > screenW + 120.0f || screen.y < -120.0f || screen.y > screenH + 120.0f) {
                continue;
            }

            double dist = mc.player.getEntityPos().distanceTo(h.pos);
            String distStr = String.format(Locale.US, "(%.1fm)", dist);

            String titleStr = h.extraInfo != null && !h.extraInfo.isEmpty() ? h.name + " " + h.extraInfo : h.name;

            String dmgStr;
            int statusCol;
            if (h.isLethal) {
                dmgStr = String.format(Locale.US, "-%.1f HP", h.currentDamage);
                statusCol = 0xFFFF385C;
            } else if (h.currentDamage > 10.0f) {
                dmgStr = String.format(Locale.US, "-%.1f HP", h.currentDamage);
                statusCol = 0xFFFFA800;
            } else if (h.currentDamage > 0.5f) {
                dmgStr = String.format(Locale.US, "-%.1f HP", h.currentDamage);
                statusCol = 0xFFFFD166;
            } else {
                dmgStr = "SAFE";
                statusCol = 0xFF34D399;
            }

            float scale = (float) MathHelper.clamp(1.0 - (dist - 5.0) / 36.0 * 0.32, 0.72, 1.02);
            float fontSize = 10.0f * scale;
            float padX = 7.0f * scale;
            float badgeH = 16.5f * scale;
            float gap = 5.0f * scale;
            float dotR = 2.4f * scale;

            float wTitle = font.a(titleStr, fontSize);
            float wDmg = font.a(dmgStr, fontSize);
            float wDist = font.a(distStr, fontSize);
            float badgeW = padX + (dotR * 2.0f) + gap + wTitle + gap + wDmg + gap + wDist + padX;

            float bx = screen.x - badgeW / 2.0f;
            float by = screen.y - badgeH / 2.0f;

            float cardRadius = 4.5f * scale;

            Draw2DProcessor.INSTANCE.drawGlow(null, bx, by + 1.0f * scale, badgeW, badgeH, cardRadius, ColorUtil.rgba(0, 0, 0, 110), 4.0f * scale);
            Draw2DProcessor.INSTANCE.drawRect(null, bx, by, badgeW, badgeH, cardRadius, ColorUtil.rgba(14, 16, 22, 220));
            Draw2DProcessor.INSTANCE.drawGradient(null, bx, by, badgeW, badgeH * 0.45f, cardRadius, ColorUtil.rgba(255, 255, 255, 14), ColorUtil.rgba(255, 255, 255, 14), 0, 0);

            int outlineCol = h.isLethal ? ColorUtil.rgba(255, 56, 92, 120) : (h.currentDamage > 10.0f ? ColorUtil.rgba(255, 168, 0, 100) : ColorUtil.rgba(255, 255, 255, 28));
            Draw2DProcessor.INSTANCE.drawOutline(null, bx, by, badgeW, badgeH, cardRadius, 0.8f, outlineCol);

            float dotCx = bx + padX + dotR;
            float dotCy = by + badgeH / 2.0f;
            Draw2DProcessor.INSTANCE.drawRect(null, dotCx - dotR, dotCy - dotR, dotR * 2.0f, dotR * 2.0f, dotR, statusCol);

            float ty = by + (badgeH - font.a(fontSize)) / 2.0f - 0.5f;

            float txTitle = dotCx + dotR + gap;
            font.a(null, titleStr, txTitle, ty, fontSize, 0xFFFFFFFF);

            float txDmg = txTitle + wTitle + gap;
            font.a(null, dmgStr, txDmg, ty, fontSize, statusCol);

            float txDist = txDmg + wDmg + gap;
            font.a(null, distStr, txDist, ty, fontSize, ColorUtil.rgba(165, 175, 195, 220));
        }

        DrawBatcher.flushPending();
    }

    private void updateHazards(PlayerEntity player) {
        activeHazards.clear();
        dangerBlocks.clear();
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
                activeHazards.add(new HazardSource("Crystal", cPos, power, power * 2.0f, currentDmg, lethal, 1.4f, null));
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
                int fuseTicks = tnt.getFuse();
                String fuseStr = String.format(Locale.US, "%.1fs", fuseTicks / 20.0f);
                activeHazards.add(new HazardSource("TNT", tPos, power, power * 2.0f, currentDmg, lethal, 1.2f, fuseStr));
            }
        }

        if (showAnchors.isEnabled() && mc.world.getRegistryKey() != net.minecraft.world.World.NETHER) {
            BlockPos centerBp = BlockPos.ofFloored(pPos);
            int bRadius = (int) Math.min(16.0, maxDist);
            BlockPos.Mutable mut = new BlockPos.Mutable();

            for (int dx = -bRadius; dx <= bRadius; dx++) {
                for (int dy = -5; dy <= 5; dy++) {
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
                                String chargeStr = charges + "★";
                                activeHazards.add(new HazardSource("Anchor", aPos, power, power * 2.0f, currentDmg, lethal, 1.1f, chargeStr));
                            }
                        }
                    }
                }
            }
        }

        if (activeHazards.isEmpty()) return;

        float playerHp = player.getHealth() + player.getAbsorptionAmount();

        for (HazardSource h : activeHazards) {
            int blockRadius = (int) Math.min(10, Math.ceil(h.maxRadius));
            BlockPos centerBp = BlockPos.ofFloored(h.pos);
            double maxRSq = h.maxRadius * h.maxRadius;

            for (int dx = -blockRadius; dx <= blockRadius; dx++) {
                for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                    double horizSq = dx * dx + dz * dz;
                    if (horizSq > maxRSq) continue;

                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos checkPos = new BlockPos(centerBp.getX() + dx, centerBp.getY() + dy, centerBp.getZ() + dz);
                        BlockState state = mc.world.getBlockState(checkPos);
                        if (state.isAir() || state.getCollisionShape(mc.world, checkPos).isEmpty()) continue;

                        BlockState above = mc.world.getBlockState(checkPos.up());
                        if (!above.getCollisionShape(mc.world, checkPos.up()).isEmpty()) continue;

                        Vec3d feetPos = new Vec3d(checkPos.getX() + 0.5, checkPos.getY() + 1.0, checkPos.getZ() + 0.5);
                        double dist = feetPos.distanceTo(h.pos);
                        if (dist >= h.maxRadius) continue;

                        double exposure = calculatePointExposure(mc.world, h.pos, feetPos, player);
                        if (exposure <= 0.05) continue;

                        double q = dist / h.maxRadius;
                        double impact = (1.0 - q) * exposure;
                        double rawDmg = ((impact * impact + impact) / 2.0) * 7.0 * h.maxRadius + 1.0;
                        float finalDmg = applyArmorAndEnchants(player, (float) rawDmg);

                        if (finalDmg < 0.5f) continue;

                        boolean lethal = finalDmg >= playerHp;
                        DangerousBlock existing = dangerBlocks.get(checkPos);
                        if (existing == null || finalDmg > existing.damage) {
                            dangerBlocks.put(checkPos, new DangerousBlock(checkPos, finalDmg, lethal));
                        }
                    }
                }
            }
        }
    }

    private double calculatePointExposure(World world, Vec3d source, Vec3d targetFeet, PlayerEntity player) {
        Vec3d p1 = new Vec3d(targetFeet.x, targetFeet.y + 0.15, targetFeet.z);
        Vec3d p2 = new Vec3d(targetFeet.x, targetFeet.y + 0.9, targetFeet.z);
        Vec3d p3 = new Vec3d(targetFeet.x, targetFeet.y + 1.7, targetFeet.z);

        double hits = 0.0;
        if (canRayReach(world, source, p1, player)) hits += 1.0;
        if (canRayReach(world, source, p2, player)) hits += 1.0;
        if (canRayReach(world, source, p3, player)) hits += 1.0;

        return hits / 3.0;
    }

    private boolean canRayReach(World world, Vec3d from, Vec3d to, PlayerEntity player) {
        BlockHitResult hit = world.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (hit.getType() == HitResult.Type.MISS) return true;
        double rayDistSq = from.squaredDistanceTo(to);
        return hit.getPos().squaredDistanceTo(from) >= rayDistSq - 0.15;
    }

    private void renderBlockHighlight(VertexConsumer buf, DangerousBlock db, Vec3d camPos, float opacity, boolean fullBox) {
        float x1 = (float) (db.pos.getX() - camPos.x);
        float x2 = x1 + 1.0f;
        float z1 = (float) (db.pos.getZ() - camPos.z);
        float z2 = z1 + 1.0f;
        float yTop = (float) (db.pos.getY() + 1.002 - camPos.y);

        int r, g, b, fillA, borderA;
        if (db.isLethal) {
            r = 255;
            g = 46;
            b = 76;
            fillA = (int) (opacity * 95.0f);
            borderA = (int) (opacity * 235.0f);
        } else {
            r = 255;
            g = 168;
            b = 25;
            fillA = (int) (opacity * 60.0f);
            borderA = (int) (opacity * 190.0f);
        }

        buf.vertex(x1, yTop, z1).color(r, g, b, fillA);
        buf.vertex(x1, yTop, z2).color(r, g, b, fillA);
        buf.vertex(x2, yTop, z2).color(r, g, b, fillA);
        buf.vertex(x2, yTop, z1).color(r, g, b, fillA);

        float bw = 0.045f;
        buf.vertex(x1, yTop + 0.001f, z1).color(r, g, b, borderA);
        buf.vertex(x1, yTop + 0.001f, z1 + bw).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z1 + bw).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z1).color(r, g, b, borderA);

        buf.vertex(x1, yTop + 0.001f, z2 - bw).color(r, g, b, borderA);
        buf.vertex(x1, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z2 - bw).color(r, g, b, borderA);

        buf.vertex(x1, yTop + 0.001f, z1).color(r, g, b, borderA);
        buf.vertex(x1, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x1 + bw, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x1 + bw, yTop + 0.001f, z1).color(r, g, b, borderA);

        buf.vertex(x2 - bw, yTop + 0.001f, z1).color(r, g, b, borderA);
        buf.vertex(x2 - bw, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z2).color(r, g, b, borderA);
        buf.vertex(x2, yTop + 0.001f, z1).color(r, g, b, borderA);

        if (fullBox) {
            float yBottom = (float) (db.pos.getY() - camPos.y);
            int sideA = fillA / 2;

            buf.vertex(x1, yBottom, z1).color(r, g, b, sideA);
            buf.vertex(x1, yTop, z1).color(r, g, b, sideA);
            buf.vertex(x2, yTop, z1).color(r, g, b, sideA);
            buf.vertex(x2, yBottom, z1).color(r, g, b, sideA);

            buf.vertex(x1, yBottom, z2).color(r, g, b, sideA);
            buf.vertex(x2, yBottom, z2).color(r, g, b, sideA);
            buf.vertex(x2, yTop, z2).color(r, g, b, sideA);
            buf.vertex(x1, yTop, z2).color(r, g, b, sideA);

            buf.vertex(x1, yBottom, z1).color(r, g, b, sideA);
            buf.vertex(x1, yBottom, z2).color(r, g, b, sideA);
            buf.vertex(x1, yTop, z2).color(r, g, b, sideA);
            buf.vertex(x1, yTop, z1).color(r, g, b, sideA);

            buf.vertex(x2, yBottom, z1).color(r, g, b, sideA);
            buf.vertex(x2, yTop, z1).color(r, g, b, sideA);
            buf.vertex(x2, yTop, z2).color(r, g, b, sideA);
            buf.vertex(x2, yBottom, z2).color(r, g, b, sideA);
        }
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
        return applyArmorAndEnchants(player, (float) rawDamage);
    }

    private static float applyArmorAndEnchants(PlayerEntity player, float rawDamage) {
        float armor = (float) player.getArmor();
        float toughness = 0.0f;
        try {
            toughness = (float) player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        } catch (Throwable ignored) {}

        float protPoints = 0.0f;
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack != null && !stack.isEmpty()) {
                protPoints += 3.5f;
            }
        }

        float f = 2.0f + toughness / 4.0f;
        float g = MathHelper.clamp(armor - rawDamage / f, armor * 0.2f, 20.0f);
        float damageAfterArmor = rawDamage * (1.0f - g / 25.0f);

        float protClamp = MathHelper.clamp(protPoints, 0.0f, 20.0f);
        float finalDamage = damageAfterArmor * (1.0f - protClamp / 25.0f);

        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            finalDamage = finalDamage * Math.max(0.0f, 1.0f - amp * 0.2f);
        }

        return Math.max(0.0f, finalDamage);
    }
}
