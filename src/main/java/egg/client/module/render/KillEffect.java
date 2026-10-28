package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * KillEffect - Мультяшный эффект поражения врага молнией при смерти:
 * Замирание, удар настоящей молнии со звуком грома, и бешеное рентгеновское мигание
 * силуэта (угольно-чёрный силуэт <-> светящийся скелет с костями), после чего враг осыпается в пепел.
 */
public class KillEffect extends Module {

    private static KillEffect instance;

    private static final Identifier SKELETON_TEXTURE = Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png");

    private final BooleanSetting lightning = new BooleanSetting("Удар молнии", true, "Призывать настоящую косметическую молнию с небес");
    private final BooleanSetting thunderSound = new BooleanSetting("Звук грома", true, "Оглушительный раскат грома и электрический разряд");
    private final BooleanSetting cartoonFlash = new BooleanSetting("Мигание костей", true, "Мультяшное чередование черного силуэта и скелета");
    private final SliderSetting duration = new SliderSetting("Длительность (сек)", 2.2, 1.0, 4.0, 0.2, "Время показа эффекта замирания и скелета");
    private final SliderSetting flashSpeed = new SliderSetting("Скорость строба", 18.0, 8.0, 30.0, 1.0, "Частота мультяшного мигания костей в секунду");

    private final java.util.Map<Integer, Long> attackTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<ActiveKillTarget> activeTargets = new ArrayList<>();
    private final Random random = new Random();
    private SkeletonEntityModel skeletonModel;
    private SkeletonEntityRenderState dummyState;

    public KillEffect() {
        super("KillEffect", "Мультяшный эффект смерти: замирание врага, удар молнии и мигание скелета", Category.ESP);
        instance = this;
        addSettings(lightning, thunderSound, cartoonFlash, duration, flashSpeed);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    public void recordAttack(Entity target) {
        if (target != null) {
            attackTimes.put(target.getId(), System.currentTimeMillis());
        }
    }

    private void ensureSkeletonModel() {
        if (skeletonModel == null) {
            try {
                ModelPart root = SkeletonEntityModel.getTexturedModelData().createModel();
                skeletonModel = new SkeletonEntityModel(root);
                dummyState = new SkeletonEntityRenderState();
            } catch (Exception e) {
                // fallback
            }
        }
    }

    public void onEntityDeath(Entity entity) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || entity == null || entity == mc.player) return;

        Long last = attackTimes.remove(entity.getId());
        if (last != null && (System.currentTimeMillis() - last <= 5000)) {
            if (entity instanceof LivingEntity living) {
                spawnKillEffect(living.getEntityPos(), living.getYaw(), living.getPitch());
            }
        }
    }

    public void spawnKillEffect(Vec3d pos, float yaw, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        ActiveKillTarget target = new ActiveKillTarget(pos, yaw, pitch, (float) duration.get());
        activeTargets.add(target);

        // 1. Lightning bolt
        if (lightning.isEnabled()) {
            LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
            bolt.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
            bolt.setCosmetic(true);
            mc.world.addEntity(bolt);
        }

        // 2. Thunder sound
        if (thunderSound.isEnabled()) {
            mc.world.playSoundClient(pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.2f, 1.0f, false);
            mc.world.playSoundClient(pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 1.5f, 1.0f, false);
        }
    }

    public void render(VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled() || activeTargets.isEmpty()) return;
        ensureSkeletonModel();
        if (skeletonModel == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float dt = 0.016f;
        Iterator<ActiveKillTarget> it = activeTargets.iterator();
        while (it.hasNext()) {
            ActiveKillTarget target = it.next();
            target.age += dt;

            if (target.age >= target.maxAge) {
                // Spawn burst of smoke/ash particles at the end
                for (int i = 0; i < 25; i++) {
                    double ox = (random.nextDouble() - 0.5) * 0.8;
                    double oy = random.nextDouble() * 1.8;
                    double oz = (random.nextDouble() - 0.5) * 0.8;
                    mc.world.addParticleClient(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                            target.pos.x + ox, target.pos.y + oy, target.pos.z + oz,
                            ox * 0.1, 0.05, oz * 0.1);
                }
                it.remove();
                continue;
            }

            renderCartoonShock(immediate, target, camPos);
        }
    }

    private void renderCartoonShock(VertexConsumerProvider immediate, ActiveKillTarget target, Vec3d camPos) {
        MatrixStack matrices = new MatrixStack();
        matrices.push();

        // Translate to entity world position relative to camera
        double rx = target.pos.x - camPos.x;
        double ry = target.pos.y - camPos.y;
        double rz = target.pos.z - camPos.z;
        matrices.translate(rx, ry, rz);

        // Cartoon jitter/vibration when electrocuted!
        float jitterIntensity = Math.max(0.0f, 1.0f - (target.age / target.maxAge)) * 0.055f;
        float jx = (random.nextFloat() - 0.5f) * jitterIntensity;
        float jy = (random.nextFloat() - 0.5f) * jitterIntensity;
        float jz = (random.nextFloat() - 0.5f) * jitterIntensity;
        matrices.translate(jx, jy, jz);

        // Orient model
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - target.yaw));
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.translate(0.0, -1.501, 0.0);

        // Cartoon rapid strobe: alternate between pitch-black charred silhouette and glowing white skeleton bones!
        float strobeRate = (float) flashSpeed.get();
        boolean showBones = cartoonFlash.isEnabled() && ((int) (target.age * strobeRate) % 2 == 0);

        try {
            if (showBones) {
                // Glowing white skeleton bones layer
                VertexConsumer buffer = immediate.getBuffer(RenderLayers.entityTranslucent(SKELETON_TEXTURE));
                int boneCol = 0xFFFFFFFF; // Pure white bones
                skeletonModel.setAngles(dummyState);
                skeletonModel.render(matrices, buffer, 0x00F000F0, OverlayTexture.DEFAULT_UV, boneCol);
            } else {
                // Solid pitch black charred body silhouette (with red/yellow electricity tint)
                VertexConsumer buffer = immediate.getBuffer(RenderLayers.entityCutoutNoCull(SKELETON_TEXTURE));
                // Pitch black charred silhouette with electrical flash
                int charColor = 0xFF141416; // Deep charred black
                skeletonModel.setAngles(dummyState);
                skeletonModel.render(matrices, buffer, 0x00F000F0, OverlayTexture.DEFAULT_UV, charColor);
            }
        } catch (Throwable ignored) {}

        matrices.pop();

        // Render electrical neon spark arcs radiating around the shocked body
        renderElectricArcs(immediate, target, camPos);
    }

    private void renderElectricArcs(VertexConsumerProvider immediate, ActiveKillTarget target, Vec3d camPos) {
        VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
        Matrix4f posMat = new Matrix4f();

        double rx = target.pos.x - camPos.x;
        double ry = target.pos.y - camPos.y;
        double rz = target.pos.z - camPos.z;

        // Draw 3-5 electric lightning sparks surrounding the body
        int sparkCount = 4;
        for (int s = 0; s < sparkCount; s++) {
            float angle = (float) (s * (Math.PI * 2.0 / sparkCount) + target.age * 12.0f);
            float h = 0.3f + (s * 0.4f);
            float dist = 0.45f + (float) Math.sin(target.age * 20.0f + s) * 0.15f;

            float x1 = (float) (rx + Math.cos(angle) * dist);
            float y1 = (float) (ry + h);
            float z1 = (float) (rz + Math.sin(angle) * dist);

            float x2 = (float) (rx + Math.cos(angle + 0.5f) * (dist + 0.15f));
            float y2 = y1 + 0.25f;
            float z2 = (float) (rz + Math.sin(angle + 0.5f) * (dist + 0.15f));

            // Neon electric cyan/yellow color
            int arcColor = (s % 2 == 0) ? 0xFF00FFFF : 0xFFFFF200;
            drawSparkSegment(quadBuffer, posMat, x1, y1, z1, x2, y2, z2, 0.035f, arcColor);
        }
    }

    private void drawSparkSegment(VertexConsumer buf, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float w, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 230;

        buf.vertex(mat, x1 - w, y1, z1 - w).color(r, g, b, a);
        buf.vertex(mat, x1 + w, y1, z1 + w).color(r, g, b, a);
        buf.vertex(mat, x2 + w, y2, z2 + w).color(r, g, b, a);
        buf.vertex(mat, x2 - w, y2, z2 - w).color(r, g, b, a);

        buf.vertex(mat, x1, y1 - w, z1).color(r, g, b, a);
        buf.vertex(mat, x1, y1 + w, z1).color(r, g, b, a);
        buf.vertex(mat, x2, y2 + w, z2).color(r, g, b, a);
        buf.vertex(mat, x2, y2 - w, z2).color(r, g, b, a);
    }

    private static class ActiveKillTarget {
        final Vec3d pos;
        final float yaw;
        final float pitch;
        final float maxAge;
        float age = 0.0f;

        ActiveKillTarget(Vec3d pos, float yaw, float pitch, float maxAge) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.maxAge = maxAge;
        }
    }
}
