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
import egg.client.ui.theme.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KillEffect extends Module {

    private static KillEffect instance;

    private static final int[] BUTTERFLY_PALETTE = {
            0xFF00E5FF,
            0xFFBF00FF,
            0xFFFF4081,
            0xFFFFD700,
            0xFF00E676,
            0xFF448AFF,
            0xFFFF9100,
            0xFFE040FB
    };

    private final ModeSetting mode = new ModeSetting("Эффект", "Бабочки", "Тип эффекта при смерти противника", "Бабочки", "Обелиск", "Стекло");
    private final BooleanSetting onlyTarget = new BooleanSetting("Только цель", true, "Создавать эффект только для атакованных вами врагов");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false, "Создавать эффект только при смерти игроков");
    private final BooleanSetting sound = new BooleanSetting("Звуки", true, "Звуковые эффекты при убийстве");
    private final BooleanSetting syncColor = new BooleanSetting("Синхр. цвета", false, "Использовать цвет темы клиента для эффекта");
    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFFD700, "Пользовательский цвет эффекта (по умолч. золото)");

    private final SliderSetting butterflyAmount = new SliderSetting("Бабочки", 48, 15, 120, 1, "Количество вылетающих бабочек");
    private final SliderSetting butterflySize = new SliderSetting("Размер бабочек", 0.22, 0.08, 0.55, 0.01, "Размер крыльев бабочек");
    private final SliderSetting butterflySpeed = new SliderSetting("Скорость взлёта", 3.8, 1.0, 9.0, 0.2, "Начальная скорость разлёта бабочек");
    private final SliderSetting butterflyLifetime = new SliderSetting("Жизнь бабочек", 4.2, 1.5, 8.0, 0.1, "Длительность полёта бабочек (сек)");
    private final BooleanSetting butterflyTrails = new BooleanSetting("Пыльца", true, "Искрящийся шлейф пыльцы за крыльями бабочек");
    private final BooleanSetting butterflyRainbow = new BooleanSetting("Разноцветные", false, "Радужные переливы крыльев каждой бабочки");

    private final SliderSetting monolithHeight = new SliderSetting("Высота портала", 6.0, 3.5, 12.0, 0.5, "Высота появления небесного обелиска");
    private final SliderSetting monolithScale = new SliderSetting("Размер обелиска", 1.0, 0.6, 2.0, 0.1, "Масштаб 3D модели обелиска");
    private final BooleanSetting monolithRunes = new BooleanSetting("Руны", true, "Светящиеся выбитые руны на гранях обелиска");

    private final SliderSetting amount = new SliderSetting("Осколки стекла", 85, 20, 250, 5, "Количество 3D осколков стекла");
    private final SliderSetting size = new SliderSetting("Размер стекла", 0.22, 0.05, 0.60, 0.01, "Размер осколков стекла");
    private final SliderSetting speed = new SliderSetting("Скорость стекла", 6.5, 2.0, 15.0, 0.5, "Скорость разлета осколков во все стороны");
    private final SliderSetting lifetime = new SliderSetting("Жизнь стекла", 2.0, 0.5, 5.0, 0.1, "Длительность существования осколков (сек)");
    private final BooleanSetting bounce = new BooleanSetting("Отскок стекла", true, "Отскок осколков стекла от блоков и пола");
    private final BooleanSetting sparkle = new BooleanSetting("Блики стекла", true, "Яркие вспышки и отблески на гранях стекла");

    private final List<ButterflySwarmInstance> butterflySwarms = new ArrayList<>();
    private final List<MonolithInstance> monolithInstances = new ArrayList<>();
    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Set<Integer> deadEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public KillEffect() {
        super("KillEffect", "Воспроизводит визуальный эффект на месте гибели игрока", Category.ESP);
        instance = this;

        butterflyAmount.setVisible(() -> "Бабочки".equals(mode.get()));
        butterflySize.setVisible(() -> "Бабочки".equals(mode.get()));
        butterflySpeed.setVisible(() -> "Бабочки".equals(mode.get()));
        butterflyLifetime.setVisible(() -> "Бабочки".equals(mode.get()));
        butterflyTrails.setVisible(() -> "Бабочки".equals(mode.get()));
        butterflyRainbow.setVisible(() -> "Бабочки".equals(mode.get()));

        monolithHeight.setVisible(() -> "Обелиск".equals(mode.get()));
        monolithScale.setVisible(() -> "Обелиск".equals(mode.get()));
        monolithRunes.setVisible(() -> "Обелиск".equals(mode.get()));

        amount.setVisible(() -> "Стекло".equals(mode.get()));
        size.setVisible(() -> "Стекло".equals(mode.get()));
        speed.setVisible(() -> "Стекло".equals(mode.get()));
        lifetime.setVisible(() -> "Стекло".equals(mode.get()));
        bounce.setVisible(() -> "Стекло".equals(mode.get()));
        sparkle.setVisible(() -> "Стекло".equals(mode.get()));

        color.setVisible(() -> !syncColor.isEnabled());

        addSettings(mode, onlyTarget, onlyPlayers, sound, syncColor, color,
                butterflyAmount, butterflySize, butterflySpeed, butterflyLifetime, butterflyTrails, butterflyRainbow,
                monolithHeight, monolithScale, monolithRunes,
                amount, size, speed, lifetime, bounce, sparkle);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    public void onAttack(Entity target) {
        if (!isEnabled() || target == null) return;
        recentAttacks.put(target.getId(), System.currentTimeMillis());
    }

    public void onEntityDeath(Entity entity) {
        if (!isEnabled() || entity == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || entity == mc.player) return;

        if (onlyPlayers.isEnabled() && !(entity instanceof PlayerEntity)) {
            return;
        }

        int entityId = entity.getId();
        if (onlyTarget.isEnabled()) {
            Long lastAttack = recentAttacks.get(entityId);
            if (lastAttack == null || (System.currentTimeMillis() - lastAttack) > 6000L) {
                return;
            }
        }

        if (deadEntities.add(entityId)) {
            recentAttacks.remove(entityId);
            String curMode = mode.get();
            if ("Бабочки".equals(curMode)) {
                spawnButterflySwarm(entity);
            } else if ("Обелиск".equals(curMode)) {
                spawnMonolithEffect(entity);
            } else {
                spawnGlassBurst(entity);
            }
        }
    }

    public void spawnButterflySwarm(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double bx = entity.getX();
        double by = entity.getY();
        double bz = entity.getZ();
        double height = entity.getHeight();

        int count = Math.clamp((int) butterflyAmount.get(), 10, 150);
        float baseSpeed = (float) butterflySpeed.get();
        float baseSize = (float) butterflySize.get();
        float baseLife = (float) butterflyLifetime.get();
        int baseColor = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        boolean rainbow = butterflyRainbow.isEnabled();
        boolean trails = butterflyTrails.isEnabled();

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by + height * 0.5, bz,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS,
                    1.4f, 1.25f, false);
            mc.world.playSoundClient(bx, by + height * 0.5, bz,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS,
                    1.5f, 1.55f, false);
        }

        butterflySwarms.add(new ButterflySwarmInstance(
                bx, by, bz, height,
                count, baseSpeed, baseSize, baseLife,
                baseColor, rainbow, trails, random
        ));

        while (butterflySwarms.size() > 8) {
            butterflySwarms.remove(0);
        }
    }

    public void spawnMonolithEffect(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double bx = entity.getX();
        double by = entity.getY();
        double bz = entity.getZ();
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float headYaw = entity.getHeadYaw();
        boolean isSneaking = entity.isSneaking();

        float limbProgress = 0.0f;
        float limbSpeed = 0.0f;
        float swingProgress = 0.0f;
        if (entity instanceof LivingEntity living) {
            limbProgress = living.limbAnimator.getAnimationProgress();
            limbSpeed = living.limbAnimator.getSpeed();
            swingProgress = living.getHandSwingProgress(1.0f);
        }

        float rLegPitch = (float) Math.cos(limbProgress * 0.6662f) * 1.4f * limbSpeed;
        float lLegPitch = (float) Math.cos(limbProgress * 0.6662f + (float) Math.PI) * 1.4f * limbSpeed;
        float rArmPitch = (float) Math.cos(limbProgress * 0.6662f + (float) Math.PI) * 1.8f * limbSpeed;
        float lArmPitch = (float) Math.cos(limbProgress * 0.6662f) * 1.8f * limbSpeed;
        float rArmRoll = 0.0f;
        if (swingProgress > 0.01f) {
            rArmPitch -= swingProgress * 1.4f;
            rArmRoll += swingProgress * 0.35f;
        }

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by + 3.0, bz,
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS,
                    1.4f, 0.72f, false);
            mc.world.playSoundClient(bx, by + 3.0, bz,
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS,
                    1.2f, 1.45f, false);
            mc.world.playSoundClient(bx, by, bz,
                    SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS,
                    1.5f, 0.60f, false);
        }

        int runeCol = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        float pHeight = (float) monolithHeight.get();
        float scale = (float) monolithScale.get();

        monolithInstances.add(new MonolithInstance(
                bx, by, bz, yaw, pitch, headYaw, isSneaking,
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll,
                pHeight, scale, runeCol
        ));

        while (monolithInstances.size() > 8) {
            monolithInstances.remove(0);
        }
    }

    public void spawnGlassBurst(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d basePos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        double height = entity.getHeight();

        int count = Math.clamp((int) amount.get(), 10, 300);
        float baseSpeed = (float) speed.get();
        float baseSize = (float) size.get();
        float baseLife = (float) lifetime.get();
        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(basePos.x, basePos.y + height * 0.5, basePos.z,
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS,
                    1.2f, 0.95f + random.nextFloat() * 0.25f, false);
        }

        for (int i = 0; i < count; i++) {
            double spawnY = basePos.y + random.nextDouble() * height;
            double px = basePos.x + (random.nextDouble() - 0.5) * 0.35;
            double py = spawnY;
            double pz = basePos.z + (random.nextDouble() - 0.5) * 0.35;

            double azimuth = random.nextDouble() * Math.PI * 2.0;

            int tier = i % 3;
            double elevation;
            float speedTier;
            if (tier == 0) {
                elevation = -0.05 + random.nextDouble() * 0.25;
                speedTier = 1.15f;
            } else if (tier == 1) {
                elevation = 0.30 + random.nextDouble() * 0.45;
                speedTier = 1.00f;
            } else {
                elevation = 0.80 + random.nextDouble() * 0.55;
                speedTier = 1.25f;
            }

            float currentSpeed = baseSpeed * (0.65f + 0.65f * random.nextFloat()) * speedTier;
            double vHoriz = Math.cos(elevation) * currentSpeed;
            double vx = Math.cos(azimuth) * vHoriz;
            double vy = Math.sin(elevation) * currentSpeed;
            double vz = Math.sin(azimuth) * vHoriz;

            int shapeType = random.nextInt(4);
            float shardSize = baseSize * (0.75f + 0.55f * random.nextFloat());
            float shardLife = baseLife * (0.70f + 0.60f * random.nextFloat());

            float pitch = random.nextFloat() * 360.0f;
            float yaw = random.nextFloat() * 360.0f;
            float roll = random.nextFloat() * 360.0f;

            float rotPitch = (random.nextFloat() - 0.5f) * 750.0f;
            float rotYaw = (random.nextFloat() - 0.5f) * 750.0f;
            float rotRoll = (random.nextFloat() - 0.5f) * 750.0f;

            particles.add(new DeathGlassShard(
                    px, py, pz,
                    vx, vy, vz,
                    pitch, yaw, roll,
                    rotPitch, rotYaw, rotRoll,
                    shardSize, shardLife, shapeType, tint
            ));
        }

        while (particles.size() > 500) {
            particles.remove(0);
        }
    }

    public void render(VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) {
            if (!monolithInstances.isEmpty()) monolithInstances.clear();
            if (!particles.isEmpty()) particles.clear();
            if (!butterflySwarms.isEmpty()) butterflySwarms.clear();
            recentAttacks.clear();
            deadEntities.clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && !recentAttacks.isEmpty()) {
            long nowMs = System.currentTimeMillis();
            recentAttacks.entrySet().removeIf(entry -> {
                if (nowMs - entry.getValue() > 6000L) {
                    return true;
                }
                Entity target = mc.world.getEntityById(entry.getKey());
                if (target instanceof LivingEntity living && (!living.isAlive() || living.getHealth() <= 0.0f)) {
                    onEntityDeath(living);
                    return true;
                }
                return false;
            });
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
        VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
        Vector3f lightDir = new Vector3f(0.35f, 0.90f, 0.25f).normalize();

        if (!butterflySwarms.isEmpty()) {
            renderButterflySwarms(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!monolithInstances.isEmpty()) {
            renderMonolithInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!particles.isEmpty()) {
            renderGlassParticles(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }
    }

    private void renderMonolithInstances(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                         Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<MonolithInstance> it = monolithInstances.iterator();
        while (it.hasNext()) {
            MonolithInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.totalLife) {
                it.remove();
                continue;
            }

            float age = inst.age;

            if (age >= 0.22f && !inst.soundPlayedDrop) {
                inst.soundPlayedDrop = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y + inst.portalHeight, inst.z,
                            SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS,
                            1.4f, 0.65f, false);
                    mc.world.playSoundClient(inst.x, inst.y + inst.portalHeight * 0.5, inst.z,
                            SoundEvents.ITEM_TRIDENT_RIPTIDE_2.value(), SoundCategory.PLAYERS,
                            1.3f, 0.55f, false);
                }
            }

            if (age >= 0.46f && !inst.soundPlayedImpact) {
                inst.soundPlayedImpact = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
                            1.4f, 0.75f, false);
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS,
                            1.2f, 0.52f, false);
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS,
                            1.6f, 0.85f, false);
                }

                inst.spawnShatterDebris(random);
            }

            double curY;
            double shakeX = 0;
            double shakeZ = 0;

            if (age < 0.22f) {

                curY = inst.y + inst.portalHeight + 1.2 * inst.scale;
            } else if (age < 0.46f) {

                float dropProg = (age - 0.22f) / 0.24f;
                float easeDrop = dropProg * dropProg;
                curY = (inst.y + inst.portalHeight + 1.2 * inst.scale) * (1.0f - easeDrop) + inst.y * easeDrop;
            } else if (age < 1.15f) {

                curY = inst.y;
                float tremorTime = age - 0.46f;
                float tremorAmp = (float) (Math.exp(-tremorTime * 5.0) * 0.065 * inst.scale);
                shakeX = Math.sin(tremorTime * 48.0) * tremorAmp;
                shakeZ = Math.cos(tremorTime * 42.0) * tremorAmp;
            } else {

                float sinkProg = (age - 1.15f) / 1.85f;
                float easeSink = (float) Math.pow(sinkProg, 1.4);
                curY = inst.y - easeSink * (4.2 * inst.scale);
            }

            if (age < 0.65f) {
                renderCelestialPortal(quadBuf, lightBuf, inst, camPos, age);
            }

            if (age < 0.46f) {
                renderPetrifiedModel(quadBuf, inst, camPos, lightDir);
            }

            if (age >= 0.46f) {
                renderImpactShockwaveAndCracks(quadBuf, lightBuf, inst, camPos, age);
            }

            if (curY + 4.5 * inst.scale > inst.y - 2.0) {
                renderMonolithModel(quadBuf, lightBuf, inst, camPos, lightDir,
                        inst.x + shakeX, curY, inst.z + shakeZ);
            }

            inst.updateAndRenderDebris(quadBuf, camPos, lightDir, dt, mc);
        }
    }

    private void renderPetrifiedModel(VertexConsumer quadBuf, MonolithInstance inst, Vec3d camPos, Vector3f lightDir) {
        Matrix4f bodyMat = new Matrix4f()
                .rotateY((float) Math.toRadians(-inst.yaw));

        Matrix4f torsoMat = new Matrix4f(bodyMat);
        if (inst.isSneaking) {
            torsoMat.rotateX((float) Math.toRadians(25.0f));
        }

        float torsoBaseY = 0.70f;
        float torsoTopY = torsoBaseY + 0.75f;
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, torsoMat,
                -0.25f, torsoBaseY, -0.125f,
                0.25f, torsoTopY, 0.125f, lightDir, 255);

        Matrix4f headMat = new Matrix4f(bodyMat);
        headMat.translate(0, torsoTopY, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));

        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, headMat,
                -0.25f, 0, -0.25f,
                0.25f, 0.50f, 0.25f, lightDir, 255);

        Matrix4f rArmMat = new Matrix4f(bodyMat);
        rArmMat.translate(-0.375f, torsoTopY - 0.08f, 0);
        rArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rArmPitch)));
        rArmMat.rotateZ((float) Math.toRadians(-Math.toDegrees(inst.rArmRoll)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, rArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f, lightDir, 255);

        Matrix4f lArmMat = new Matrix4f(bodyMat);
        lArmMat.translate(0.375f, torsoTopY - 0.08f, 0);
        lArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lArmPitch)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, lArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f, lightDir, 255);

        Matrix4f rLegMat = new Matrix4f(bodyMat);
        rLegMat.translate(-0.125f, torsoBaseY, 0);
        rLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rLegPitch)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, rLegMat,
                -0.125f, -0.70f, -0.125f,
                0.125f, 0, 0.125f, lightDir, 255);

        Matrix4f lLegMat = new Matrix4f(bodyMat);
        lLegMat.translate(0.125f, torsoBaseY, 0);
        lLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lLegPitch)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, lLegMat,
                -0.125f, -0.70f, -0.125f,
                0.125f, 0, 0.125f, lightDir, 255);
    }

    private void renderCelestialPortal(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                       MonolithInstance inst, Vec3d camPos, float age) {
        float portalAlphaProg = 1.0f;
        if (age < 0.15f) {
            portalAlphaProg = age / 0.15f;
        } else if (age > 0.40f) {
            portalAlphaProg = MathHelper.clamp(1.0f - (age - 0.40f) / 0.25f, 0.0f, 1.0f);
        }
        if (portalAlphaProg <= 0.01f) return;

        float px = (float) (inst.x - camPos.x);
        float py = (float) (inst.y + inst.portalHeight - camPos.y);
        float pz = (float) (inst.z - camPos.z);

        int tr = (inst.runeColor >> 16) & 0xFF;
        int tg = (inst.runeColor >> 8) & 0xFF;
        int tb = inst.runeColor & 0xFF;

        int alpha = (int) (230 * portalAlphaProg);
        int innerAlpha = (int) (160 * portalAlphaProg);

        int segs = 16;
        float rOuter = 1.55f * inst.scale;
        float rInner = 1.25f * inst.scale;
        float rot1 = age * 2.8f;

        for (int i = 0; i < segs; i++) {
            double a0 = (i / (double) segs) * Math.PI * 2.0 + rot1;
            double a1 = ((i + 1) / (double) segs) * Math.PI * 2.0 + rot1;

            float x0_out = px + (float) Math.cos(a0) * rOuter;
            float z0_out = pz + (float) Math.sin(a0) * rOuter;
            float x1_out = px + (float) Math.cos(a1) * rOuter;
            float z1_out = pz + (float) Math.sin(a1) * rOuter;

            float x0_in = px + (float) Math.cos(a0) * rInner;
            float z0_in = pz + (float) Math.sin(a0) * rInner;
            float x1_in = px + (float) Math.cos(a1) * rInner;
            float z1_in = pz + (float) Math.sin(a1) * rInner;

            lightBuf.vertex(x0_in, py, z0_in).color(tr, tg, tb, alpha);
            lightBuf.vertex(x1_in, py, z1_in).color(tr, tg, tb, alpha);
            lightBuf.vertex(x1_out, py, z1_out).color(tr, tg, tb, 0);
            lightBuf.vertex(x0_out, py, z0_out).color(tr, tg, tb, 0);
        }

        float r2Outer = 0.95f * inst.scale;
        float r2Inner = 0.65f * inst.scale;
        float rot2 = -age * 3.8f;

        for (int i = 0; i < 8; i++) {
            double a0 = (i / 8.0) * Math.PI * 2.0 + rot2;
            double a1 = ((i + 0.65) / 8.0) * Math.PI * 2.0 + rot2;

            float x0_out = px + (float) Math.cos(a0) * r2Outer;
            float z0_out = pz + (float) Math.sin(a0) * r2Outer;
            float x1_out = px + (float) Math.cos(a1) * r2Outer;
            float z1_out = pz + (float) Math.sin(a1) * r2Outer;

            float x0_in = px + (float) Math.cos(a0) * r2Inner;
            float z0_in = pz + (float) Math.sin(a0) * r2Inner;
            float x1_in = px + (float) Math.cos(a1) * r2Inner;
            float z1_in = pz + (float) Math.sin(a1) * r2Inner;

            quadBuf.vertex(x0_in, py, z0_in).color(255, 255, 255, innerAlpha);
            quadBuf.vertex(x1_in, py, z1_in).color(255, 255, 255, innerAlpha);
            quadBuf.vertex(x1_out, py, z1_out).color(tr, tg, tb, innerAlpha);
            quadBuf.vertex(x0_out, py, z0_out).color(tr, tg, tb, innerAlpha);
        }

        float beamH = inst.portalHeight;
        float beamR = 0.55f * inst.scale;
        int beamAlpha = (int) (70 * portalAlphaProg);
        for (int i = 0; i < 6; i++) {
            double a0 = (i / 6.0) * Math.PI * 2.0;
            double a1 = ((i + 1) / 6.0) * Math.PI * 2.0;

            float bx0 = px + (float) Math.cos(a0) * beamR;
            float bz0 = pz + (float) Math.sin(a0) * beamR;
            float bx1 = px + (float) Math.cos(a1) * beamR;
            float bz1 = pz + (float) Math.sin(a1) * beamR;

            lightBuf.vertex(bx0, py, bz0).color(tr, tg, tb, beamAlpha);
            lightBuf.vertex(bx1, py, bz1).color(tr, tg, tb, beamAlpha);
            lightBuf.vertex(bx1, py - beamH, bz1).color(tr, tg, tb, 0);
            lightBuf.vertex(bx0, py - beamH, bz0).color(tr, tg, tb, 0);
        }
    }

    private void renderImpactShockwaveAndCracks(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                                MonolithInstance inst, Vec3d camPos, float age) {
        float timeSinceImpact = age - 0.46f;
        if (timeSinceImpact < 0.0f) return;

        float px = (float) (inst.x - camPos.x);
        float py = (float) (inst.y + 0.015 - camPos.y);
        float pz = (float) (inst.z - camPos.z);

        int tr = (inst.runeColor >> 16) & 0xFF;
        int tg = (inst.runeColor >> 8) & 0xFF;
        int tb = inst.runeColor & 0xFF;

        if (timeSinceImpact < 0.55f) {
            float waveProg = timeSinceImpact / 0.55f;
            float waveRadius = (0.25f + waveProg * 2.6f) * inst.scale;
            float ringThick = 0.35f * inst.scale * (1.0f - waveProg * 0.5f);
            int waveAlpha = (int) (220 * (1.0f - waveProg));

            int wSegs = 24;
            for (int i = 0; i < wSegs; i++) {
                double a0 = (i / (double) wSegs) * Math.PI * 2.0;
                double a1 = ((i + 1) / (double) wSegs) * Math.PI * 2.0;

                float x0_out = px + (float) Math.cos(a0) * (waveRadius + ringThick);
                float z0_out = pz + (float) Math.sin(a0) * (waveRadius + ringThick);
                float x1_out = px + (float) Math.cos(a1) * (waveRadius + ringThick);
                float z1_out = pz + (float) Math.sin(a1) * (waveRadius + ringThick);

                float x0_mid = px + (float) Math.cos(a0) * waveRadius;
                float z0_mid = pz + (float) Math.sin(a0) * waveRadius;
                float x1_mid = px + (float) Math.cos(a1) * waveRadius;
                float z1_mid = pz + (float) Math.sin(a1) * waveRadius;

                float x0_in = px + (float) Math.cos(a0) * Math.max(0.0f, waveRadius - ringThick);
                float z0_in = pz + (float) Math.sin(a0) * Math.max(0.0f, waveRadius - ringThick);
                float x1_in = px + (float) Math.cos(a1) * Math.max(0.0f, waveRadius - ringThick);
                float z1_in = pz + (float) Math.sin(a1) * Math.max(0.0f, waveRadius - ringThick);

                lightBuf.vertex(x0_mid, py, z0_mid).color(tr, tg, tb, waveAlpha);
                lightBuf.vertex(x1_mid, py, z1_mid).color(tr, tg, tb, waveAlpha);
                lightBuf.vertex(x1_out, py, z1_out).color(tr, tg, tb, 0);
                lightBuf.vertex(x0_out, py, z0_out).color(tr, tg, tb, 0);

                lightBuf.vertex(x0_in, py, z0_in).color(tr, tg, tb, 0);
                lightBuf.vertex(x1_in, py, z1_in).color(tr, tg, tb, 0);
                lightBuf.vertex(x1_mid, py, z1_mid).color(tr, tg, tb, waveAlpha);
                lightBuf.vertex(x0_mid, py, z0_mid).color(tr, tg, tb, waveAlpha);
            }
        }

        if (timeSinceImpact < 2.0f) {
            float crackProg = MathHelper.clamp(1.0f - timeSinceImpact / 2.0f, 0.0f, 1.0f);
            int crackAlpha = (int) (200 * crackProg);
            int darkAlpha = (int) (255 * crackProg);

            for (int c = 0; c < 6; c++) {
                double baseAng = (c / 6.0) * Math.PI * 2.0 + 0.25;
                float segLen = 0.45f * inst.scale;

                float cx0 = px;
                float cz0 = pz;

                for (int seg = 0; seg < 4; seg++) {
                    double ang = baseAng + ((seg % 2 == 0) ? 0.28 : -0.25);
                    float cx1 = cx0 + (float) Math.cos(ang) * segLen;
                    float cz1 = cz0 + (float) Math.sin(ang) * segLen;

                    float width = 0.035f * inst.scale * (1.0f - seg * 0.22f);
                    float nx = -(cz1 - cz0);
                    float nz = (cx1 - cx0);
                    float nLen = (float) (1.0 / Math.sqrt(nx * nx + nz * nz));
                    nx *= nLen * width;
                    nz *= nLen * width;

                    quadBuf.vertex(cx0 - nx, py, cz0 - nz).color(20, 22, 25, darkAlpha);
                    quadBuf.vertex(cx1 - nx, py, cz1 - nz).color(20, 22, 25, darkAlpha);
                    quadBuf.vertex(cx1 + nx, py, cz1 + nz).color(20, 22, 25, darkAlpha);
                    quadBuf.vertex(cx0 + nx, py, cz0 + nz).color(20, 22, 25, darkAlpha);

                    lightBuf.vertex(cx0, py + 0.003f, cz0).color(tr, tg, tb, crackAlpha);
                    lightBuf.vertex(cx1, py + 0.003f, cz1).color(tr, tg, tb, crackAlpha);
                    lightBuf.vertex(cx1 + nx * 0.5f, py + 0.003f, cz1 + nz * 0.5f).color(tr, tg, tb, 0);
                    lightBuf.vertex(cx0 + nx * 0.5f, py + 0.003f, cz0 + nz * 0.5f).color(tr, tg, tb, 0);

                    cx0 = cx1;
                    cz0 = cz1;
                }
            }
        }
    }

    private void renderMonolithModel(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                     MonolithInstance inst, Vec3d camPos, Vector3f lightDir,
                                     double mx, double my, double mz) {
        float scale = inst.scale;
        float rBase = 0.44f * scale;
        float rTop = 0.28f * scale;
        float hBody = 3.6f * scale;
        float hApex = 0.70f * scale;
        float hWedge = 0.50f * scale;

        float[] xb = new float[8];
        float[] zb = new float[8];
        float[] xt = new float[8];
        float[] zt = new float[8];

        for (int i = 0; i < 8; i++) {
            double ang = (i / 8.0) * Math.PI * 2.0 + (Math.PI / 8.0);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            xb[i] = cos * rBase;
            zb[i] = sin * rBase;
            xt[i] = cos * rTop;
            zt[i] = sin * rTop;
        }

        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;

            float lx0 = xb[i], ly0 = 0, lz0 = zb[i];
            float lx1 = xb[next], ly1 = 0, lz1 = zb[next];
            float lx2 = xt[next], ly2 = hBody, lz2 = zt[next];
            float lx3 = xt[i], ly3 = hBody, lz3 = zt[i];

            float ex = lx1 - lx0;
            float ey = ly1 - ly0;
            float ez = lz1 - lz0;
            float fx = lx3 - lx0;
            float fy = ly3 - ly0;
            float fz = lz3 - lz0;
            float nx = ey * fz - ez * fy;
            float ny = ez * fx - ex * fz;
            float nz = ex * fy - ey * fx;
            float nLen = (float) (1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
            Vector3f normal = new Vector3f(nx * nLen, ny * nLen, nz * nLen);

            float vx0 = (float) (mx + lx0 - camPos.x);
            float vy0 = (float) (my + ly0 - camPos.y);
            float vz0 = (float) (mz + lz0 - camPos.z);

            float vx1 = (float) (mx + lx1 - camPos.x);
            float vy1 = (float) (my + ly1 - camPos.y);
            float vz1 = (float) (mz + lz1 - camPos.z);

            float vx2 = (float) (mx + lx2 - camPos.x);
            float vy2 = (float) (my + ly2 - camPos.y);
            float vz2 = (float) (mz + lz2 - camPos.z);

            float vx3 = (float) (mx + lx3 - camPos.x);
            float vy3 = (float) (my + ly3 - camPos.y);
            float vz3 = (float) (mz + lz3 - camPos.z);

            int col0 = computeStoneColor(mx + lx0, my + ly0, mz + lz0, camPos, normal, lightDir);
            int col1 = computeStoneColor(mx + lx1, my + ly1, mz + lz1, camPos, normal, lightDir);
            int col2 = computeStoneColor(mx + lx2, my + ly2, mz + lz2, camPos, normal, lightDir);
            int col3 = computeStoneColor(mx + lx3, my + ly3, mz + lz3, camPos, normal, lightDir);

            quadBuf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, 255);
            quadBuf.vertex(vx1, vy1, vz1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, 255);
            quadBuf.vertex(vx2, vy2, vz2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, 255);
            quadBuf.vertex(vx3, vy3, vz3).color((col3 >> 16) & 0xFF, (col3 >> 8) & 0xFF, col3 & 0xFF, 255);

            if (monolithRunes.isEnabled() && (i % 2 == 0)) {
                renderMonolithFaceRunes(lightBuf, inst, camPos, mx, my, mz,
                        lx0, lz0, lx1, lz1, lx2, lz2, lx3, lz3, normal, hBody);
            }
        }

        float apexY = hBody + hApex;
        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;

            float lx0 = xt[i], ly0 = hBody, lz0 = zt[i];
            float lx1 = xt[next], ly1 = hBody, lz1 = zt[next];
            float lx2 = 0, ly2 = apexY, lz2 = 0;

            float ex = lx1 - lx0;
            float ey = ly1 - ly0;
            float ez = lz1 - lz0;
            float fx = lx2 - lx0;
            float fy = ly2 - ly0;
            float fz = lz2 - lz0;
            float nx = ey * fz - ez * fy;
            float ny = ez * fx - ex * fz;
            float nz = ex * fy - ey * fx;
            float nLen = (float) (1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
            Vector3f normal = new Vector3f(nx * nLen, ny * nLen, nz * nLen);

            float vx0 = (float) (mx + lx0 - camPos.x);
            float vy0 = (float) (my + ly0 - camPos.y);
            float vz0 = (float) (mz + lz0 - camPos.z);

            float vx1 = (float) (mx + lx1 - camPos.x);
            float vy1 = (float) (my + ly1 - camPos.y);
            float vz1 = (float) (mz + lz1 - camPos.z);

            float vx2 = (float) (mx + lx2 - camPos.x);
            float vy2 = (float) (my + ly2 - camPos.y);
            float vz2 = (float) (mz + lz2 - camPos.z);

            int col0 = computeStoneColor(mx + lx0, my + ly0, mz + lz0, camPos, normal, lightDir);
            int col1 = computeStoneColor(mx + lx1, my + ly1, mz + lz1, camPos, normal, lightDir);
            int col2 = computeStoneColor(mx + lx2, my + ly2, mz + lz2, camPos, normal, lightDir);

            quadBuf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, 255);
            quadBuf.vertex(vx1, vy1, vz1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, 255);
            quadBuf.vertex(vx2, vy2, vz2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, 255);
            quadBuf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, 255);
        }

        float wedgeY = -hWedge;
        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;

            float lx0 = xb[next], ly0 = 0, lz0 = zb[next];
            float lx1 = xb[i], ly1 = 0, lz1 = zb[i];
            float lx2 = 0, ly2 = wedgeY, lz2 = 0;

            float ex = lx1 - lx0;
            float ey = ly1 - ly0;
            float ez = lz1 - lz0;
            float fx = lx2 - lx0;
            float fy = ly2 - ly0;
            float fz = lz2 - lz0;
            float nx = ey * fz - ez * fy;
            float ny = ez * fx - ex * fz;
            float nz = ex * fy - ey * fx;
            float nLen = (float) (1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
            Vector3f normal = new Vector3f(nx * nLen, ny * nLen, nz * nLen);

            float vx0 = (float) (mx + lx0 - camPos.x);
            float vy0 = (float) (my + ly0 - camPos.y);
            float vz0 = (float) (mz + lz0 - camPos.z);

            float vx1 = (float) (mx + lx1 - camPos.x);
            float vy1 = (float) (my + ly1 - camPos.y);
            float vz1 = (float) (mz + lz1 - camPos.z);

            float vx2 = (float) (mx + lx2 - camPos.x);
            float vy2 = (float) (my + ly2 - camPos.y);
            float vz2 = (float) (mz + lz2 - camPos.z);

            int col0 = computeStoneColor(mx + lx0, my + ly0, mz + lz0, camPos, normal, lightDir);
            int col1 = computeStoneColor(mx + lx1, my + ly1, mz + lz1, camPos, normal, lightDir);
            int col2 = computeStoneColor(mx + lx2, my + ly2, mz + lz2, camPos, normal, lightDir);

            quadBuf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, 255);
            quadBuf.vertex(vx1, vy1, vz1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, 255);
            quadBuf.vertex(vx2, vy2, vz2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, 255);
            quadBuf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, 255);
        }
    }

    private static void renderMonolithFaceRunes(VertexConsumer lightBuf, MonolithInstance inst,
                                                Vec3d camPos, double mx, double my, double mz,
                                                float lx0, float lz0, float lx1, float lz1,
                                                float lx2, float lz2, float lx3, float lz3,
                                                Vector3f normal, float hBody) {
        float offX = normal.x * 0.012f;
        float offY = normal.y * 0.012f;
        float offZ = normal.z * 0.012f;

        float bx = (lx0 + lx1) * 0.5f;
        float bz = (lz0 + lz1) * 0.5f;
        float tx = (lx3 + lx2) * 0.5f;
        float tz = (lz3 + lz2) * 0.5f;

        float tangX = lx1 - lx0;
        float tangZ = lz1 - lz0;
        float tLen = (float) (1.0 / Math.sqrt(tangX * tangX + tangZ * tangZ));
        tangX *= tLen;
        tangZ *= tLen;

        int tr = (inst.runeColor >> 16) & 0xFF;
        int tg = (inst.runeColor >> 8) & 0xFF;
        int tb = inst.runeColor & 0xFF;

        float pulse = 0.80f + 0.20f * (float) Math.sin(inst.age * 7.5);
        if (inst.age >= 0.46f && inst.age < 0.85f) {
            float flare = 1.0f - (inst.age - 0.46f) / 0.39f;
            pulse += flare * 1.5f;
        }
        if (inst.age > 1.15f) {
            pulse *= MathHelper.clamp(1.0f - (inst.age - 1.15f) / 1.7f, 0.0f, 1.0f);
        }
        int runeAlpha = MathHelper.clamp((int) (220 * pulse), 0, 255);
        if (runeAlpha <= 2) return;

        float width = 0.012f * inst.scale;

        float ax0 = (float) (mx + bx + offX - camPos.x);
        float ay0 = (float) (my + 0.12f * hBody + offY - camPos.y);
        float az0 = (float) (mz + bz + offZ - camPos.z);

        float ax1 = (float) (mx + tx + offX - camPos.x);
        float ay1 = (float) (my + 0.88f * hBody + offY - camPos.y);
        float az1 = (float) (mz + tz + offZ - camPos.z);

        emitRuneLine(lightBuf, ax0, ay0, az0, ax1, ay1, az1, tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);

        float sDia = 0.065f * inst.scale;
        float[] pTop = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.77f, 0, hBody);
        float[] pR = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.71f, sDia, hBody);
        float[] pBot = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.65f, 0, hBody);
        float[] pL = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.71f, -sDia, hBody);

        emitRuneLine(lightBuf, pTop[0], pTop[1], pTop[2], pR[0], pR[1], pR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, pR[0], pR[1], pR[2], pBot[0], pBot[1], pBot[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, pBot[0], pBot[1], pBot[2], pL[0], pL[1], pL[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, pL[0], pL[1], pL[2], pTop[0], pTop[1], pTop[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, pL[0], pL[1], pL[2], pR[0], pR[1], pR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);

        float sChev = 0.062f * inst.scale;
        float[] cTL = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.55f, -sChev, hBody);
        float[] cTR = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.55f, sChev, hBody);
        float[] cMid = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.49f, 0, hBody);
        float[] cBL = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.43f, -sChev, hBody);
        float[] cBR = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.43f, sChev, hBody);

        emitRuneLine(lightBuf, cTL[0], cTL[1], cTL[2], cMid[0], cMid[1], cMid[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, cTR[0], cTR[1], cTR[2], cMid[0], cMid[1], cMid[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, cMid[0], cMid[1], cMid[2], cBL[0], cBL[1], cBL[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, cMid[0], cMid[1], cMid[2], cBR[0], cBR[1], cBR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, cTL[0], cTL[1], cTL[2], cTR[0], cTR[1], cTR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, cBL[0], cBL[1], cBL[2], cBR[0], cBR[1], cBR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);

        float sSol = 0.055f * inst.scale;
        float[] sTop = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.32f, 0, hBody);
        float[] sR = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.27f, sSol, hBody);
        float[] sBot = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.22f, 0, hBody);
        float[] sL = getRunePt(mx, my, mz, bx, bz, tx, tz, tangX, tangZ, offX, offY, offZ, camPos, 0.27f, -sSol, hBody);

        emitRuneLine(lightBuf, sTop[0], sTop[1], sTop[2], sR[0], sR[1], sR[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, sR[0], sR[1], sR[2], sBot[0], sBot[1], sBot[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, sBot[0], sBot[1], sBot[2], sL[0], sL[1], sL[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
        emitRuneLine(lightBuf, sL[0], sL[1], sL[2], sTop[0], sTop[1], sTop[2], tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);
    }

    private static float[] getRunePt(double mx, double my, double mz,
                                     float bx, float bz, float tx, float tz,
                                     float tangX, float tangZ,
                                     float offX, float offY, float offZ,
                                     Vec3d camPos, float t, float s, float hBody) {
        float cx = bx + (tx - bx) * t;
        float cz = bz + (tz - bz) * t;
        float px = (float) (mx + cx + tangX * s + offX - camPos.x);
        float py = (float) (my + t * hBody + offY - camPos.y);
        float pz = (float) (mz + cz + tangZ * s + offZ - camPos.z);
        return new float[]{px, py, pz};
    }

    private static void emitRuneLine(VertexConsumer lightBuf,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     float perpX, float perpY, float perpZ,
                                     float width, int r, int g, int b, int alpha) {
        float hx = perpX * width;
        float hy = perpY * width;
        float hz = perpZ * width;

        lightBuf.vertex(x0 - hx, y0 - hy, z0 - hz).color(r, g, b, alpha);
        lightBuf.vertex(x1 - hx, y1 - hy, z1 - hz).color(r, g, b, alpha);
        lightBuf.vertex(x1 + hx, y1 + hy, z1 + hz).color(r, g, b, alpha);
        lightBuf.vertex(x0 + hx, y0 + hy, z0 + hz).color(r, g, b, alpha);
    }

    private static void emitStoneBox(VertexConsumer buf, Vec3d camPos,
                                     double wx, double wy, double wz, Matrix4f mat,
                                     float x0, float y0, float z0, float x1, float y1, float z1,
                                     Vector3f lightDir, int alpha) {

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0);

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);

        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
    }

    private static void emitStoneFace(VertexConsumer buf, Vec3d camPos,
                                      double wx, double wy, double wz, Matrix4f mat,
                                      Vector3f lightDir, int alpha,
                                      float lx0, float ly0, float lz0,
                                      float lx1, float ly1, float lz1,
                                      float lx2, float ly2, float lz2,
                                      float lx3, float ly3, float lz3,
                                      float nx, float ny, float nz) {
        Vector3f p0 = new Vector3f(lx0, ly0, lz0).mulPosition(mat);
        Vector3f p1 = new Vector3f(lx1, ly1, lz1).mulPosition(mat);
        Vector3f p2 = new Vector3f(lx2, ly2, lz2).mulPosition(mat);
        Vector3f p3 = new Vector3f(lx3, ly3, lz3).mulPosition(mat);

        Vector3f normal = new Vector3f(nx, ny, nz).mulDirection(mat).normalize();

        float vx0 = (float) (wx + p0.x - camPos.x);
        float vy0 = (float) (wy + p0.y - camPos.y);
        float vz0 = (float) (wz + p0.z - camPos.z);

        float vx1 = (float) (wx + p1.x - camPos.x);
        float vy1 = (float) (wy + p1.y - camPos.y);
        float vz1 = (float) (wz + p1.z - camPos.z);

        float vx2 = (float) (wx + p2.x - camPos.x);
        float vy2 = (float) (wy + p2.y - camPos.y);
        float vz2 = (float) (wz + p2.z - camPos.z);

        float vx3 = (float) (wx + p3.x - camPos.x);
        float vy3 = (float) (wy + p3.y - camPos.y);
        float vz3 = (float) (wz + p3.z - camPos.z);

        int col0 = computeStoneColor(wx + p0.x, wy + p0.y, wz + p0.z, camPos, normal, lightDir);
        int col1 = computeStoneColor(wx + p1.x, wy + p1.y, wz + p1.z, camPos, normal, lightDir);
        int col2 = computeStoneColor(wx + p2.x, wy + p2.y, wz + p2.z, camPos, normal, lightDir);
        int col3 = computeStoneColor(wx + p3.x, wy + p3.y, wz + p3.z, camPos, normal, lightDir);

        buf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, alpha);
        buf.vertex(vx1, vy1, vz1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, alpha);
        buf.vertex(vx2, vy2, vz2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, alpha);
        buf.vertex(vx3, vy3, vz3).color((col3 >> 16) & 0xFF, (col3 >> 8) & 0xFF, col3 & 0xFF, alpha);
    }

    private static void emitStoneShard(VertexConsumer buf, Vec3d camPos,
                                       double sx, double sy, double sz,
                                       float pitch, float yaw, float roll,
                                       float size, Vector3f lightDir, int alpha) {
        Matrix4f mat = new Matrix4f()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));

        float s = size;
        emitStoneBox(buf, camPos, sx, sy, sz, mat,
                -s, -s, -s, s, s, s, lightDir, alpha);
    }

    private static int computeStoneColor(double worldX, double worldY, double worldZ,
                                         Vec3d camPos, Vector3f normal,
                                         Vector3f lightDir) {

        float camDx = (float) (camPos.x - worldX);
        float camDy = (float) (camPos.y - worldY);
        float camDz = (float) (camPos.z - worldZ);
        float camDistSq = camDx * camDx + camDy * camDy + camDz * camDz;
        float invDist = camDistSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(camDistSq)) : 1.0f;
        float toCamX = camDx * invDist;
        float toCamY = camDy * invDist;
        float toCamZ = camDz * invDist;

        double slant = worldY * 4.2 + worldX * 1.7 - worldZ * 1.5;
        float strata1 = (float) Math.sin(slant);
        float strata2 = (float) Math.sin(slant * 2.4 + 1.3);
        float crossVein = (float) Math.cos(worldX * 5.0 + worldZ * 5.0 + worldY * 1.8);
        float microNoise = (float) Math.sin(worldX * 15.0 + worldY * 19.0 + worldZ * 15.0);

        float mineralFactor = MathHelper.clamp(
                (strata1 * 0.42f + strata2 * 0.32f + crossVein * 0.16f + microNoise * 0.10f + 1.0f) * 0.5f,
                0.0f, 1.0f
        );

        float rBase, gBase, bBase;
        if (mineralFactor < 0.55f) {
            float t = mineralFactor / 0.55f;
            rBase = MathHelper.lerp(t, 34.0f, 64.0f);
            gBase = MathHelper.lerp(t, 36.0f, 68.0f);
            bBase = MathHelper.lerp(t, 40.0f, 75.0f);
        } else {
            float t = (mineralFactor - 0.55f) / 0.45f;
            rBase = MathHelper.lerp(t, 64.0f, 115.0f);
            gBase = MathHelper.lerp(t, 68.0f, 122.0f);
            bBase = MathHelper.lerp(t, 75.0f, 134.0f);
        }

        float NdotL = Math.max(0.0f, normal.x * lightDir.x + normal.y * lightDir.y + normal.z * lightDir.z);
        float diff = 0.32f + 0.68f * NdotL;

        float halfX = lightDir.x + toCamX;
        float halfY = lightDir.y + toCamY;
        float halfZ = lightDir.z + toCamZ;
        float hLenSq = halfX * halfX + halfY * halfY + halfZ * halfZ;
        float hLen = hLenSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(hLenSq)) : 1.0f;
        halfX *= hLen; halfY *= hLen; halfZ *= hLen;

        float NdotH = Math.max(0.0f, normal.x * halfX + normal.y * halfY + normal.z * halfZ);
        float stoneSpec = (float) Math.pow(NdotH, 12.0) * 0.28f;

        float NdotV = Math.max(0.0f, normal.x * toCamX + normal.y * toCamY + normal.z * toCamZ);
        float fresnel = (float) Math.pow(1.0f - NdotV, 3.2f) * 0.22f;

        float finalR = rBase * diff + 130.0f * stoneSpec + 120.0f * fresnel;
        float finalG = gBase * diff + 138.0f * stoneSpec + 128.0f * fresnel;
        float finalB = bBase * diff + 150.0f * stoneSpec + 140.0f * fresnel;

        int ir = MathHelper.clamp((int) finalR, 0, 255);
        int ig = MathHelper.clamp((int) finalG, 0, 255);
        int ib = MathHelper.clamp((int) finalB, 0, 255);

        return (ir << 16) | (ig << 8) | ib;
    }

    private void renderGlassParticles(VertexConsumer quadBuffer, VertexConsumer glintBuffer,
                                      Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        boolean doBounce = bounce.isEnabled() && mc.world != null;

        Iterator<DeathGlassShard> it = particles.iterator();
        while (it.hasNext()) {
            DeathGlassShard p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }

            p.vy -= 9.8f * dt;
            float drag = (float) Math.exp(-1.3f * dt);
            p.vx *= drag;
            p.vy *= drag;
            p.vz *= drag;

            p.pitch += p.rotPitch * dt;
            p.yaw += p.rotYaw * dt;
            p.roll += p.rotRoll * dt;

            double nextX = p.x + p.vx * dt;
            double nextY = p.y + p.vy * dt;
            double nextZ = p.z + p.vz * dt;

            if (doBounce) {
                BlockPos bp = BlockPos.ofFloored(nextX, nextY, nextZ);
                BlockState bs = mc.world.getBlockState(bp);
                if (!bs.isAir() && bs.isSolidBlock(mc.world, bp)) {
                    p.vy = -p.vy * 0.42;
                    p.vx *= 0.65;
                    p.vz *= 0.65;
                    p.rotPitch *= 0.6f;
                    p.rotYaw *= 0.6f;
                    p.rotRoll *= 0.6f;
                    nextY = p.y;
                }
            }

            p.x = nextX;
            p.y = nextY;
            p.z = nextZ;

            float fadeProgress = 1.0f - (p.age / p.life);
            float alpha = MathHelper.clamp(fadeProgress * 1.5f, 0.0f, 1.0f);
            if (alpha <= 0.01f) continue;

            Quaternionf rot = new Quaternionf()
                    .rotationYXZ((float) Math.toRadians(p.yaw),
                                 (float) Math.toRadians(p.pitch),
                                 (float) Math.toRadians(p.roll));

            renderShard3D(quadBuffer, p, rot, camPos, lightDir, alpha);

            if (sparkle.isEnabled() && alpha > 0.05f) {
                Vector3f toCam = new Vector3f((float) (camPos.x - p.x), (float) (camPos.y - p.y), (float) (camPos.z - p.z)).normalize();
                Vector3f frontNormal = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);

                float alignment = Math.abs(frontNormal.dot(toCam));
                if (alignment > 0.88f) {
                    float glintPower = (alignment - 0.88f) / 0.12f;
                    int glintAlpha = (int) (220.0f * alpha * glintPower);
                    renderGlintSparkle(glintBuffer, p, camPos, glintAlpha);
                }
            }
        }
    }

    private void renderShard3D(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos, Vector3f lightDir, float alphaFactor) {
        float s = p.size;
        float t = s * 0.26f;
        Vector3f toCam = new Vector3f((float) (camPos.x - p.x), (float) (camPos.y - p.y), (float) (camPos.z - p.z)).normalize();

        if (p.shapeType == 0) {
            float x0 = -0.45f * s, y0 = -0.40f * s;
            float x1 =  0.55f * s, y1 = -0.25f * s;
            float x2 = -0.15f * s, y2 =  0.65f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x2, y2, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x2, y2, -t, x1, y1, -t, x0, y0, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x0, y0, t);
        } else if (p.shapeType == 1) {
            float x0 =  0.00f * s, y0 = -0.60f * s;
            float x1 =  0.45f * s, y1 = -0.05f * s;
            float x2 = -0.05f * s, y2 =  0.60f * s;
            float x3 = -0.50f * s, y3 =  0.05f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x3, y3, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x3, y3, -t, x2, y2, -t, x1, y1, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x3, y3, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x3, y3, x0, y0, t);
        } else if (p.shapeType == 2) {
            float x0 = -0.50f * s, y0 = -0.35f * s;
            float x1 =  0.40f * s, y1 = -0.50f * s;
            float x2 =  0.55f * s, y2 =  0.30f * s;
            float x3 = -0.25f * s, y3 =  0.55f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x3, y3, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x3, y3, -t, x2, y2, -t, x1, y1, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x3, y3, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x3, y3, x0, y0, t);
        } else {
            float x0 = -0.12f * s, y0 = -0.75f * s;
            float x1 =  0.14f * s, y1 = -0.60f * s;
            float x2 =  0.05f * s, y2 =  0.80f * s;

            Vector3f fn = new Vector3f(0.0f, 0.0f, 1.0f).rotate(rot);
            int[] fCol = calculateFaceColor(p.tintColor, fn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x0, y0, t, x1, y1, t, x2, y2, t, x2, y2, t, fCol[0], fCol[1], fCol[2], fCol[3]);

            Vector3f bn = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
            int[] bCol = calculateFaceColor(p.tintColor, bn, lightDir, toCam, alphaFactor, false);
            emitQuad3D(buf, p, rot, camPos, x2, y2, -t, x1, y1, -t, x0, y0, -t, x0, y0, -t, bCol[0], bCol[1], bCol[2], bCol[3]);

            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x0, y0, x1, y1, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x1, y1, x2, y2, t);
            emitSideQuad(buf, p, rot, camPos, lightDir, toCam, alphaFactor, x2, y2, x0, y0, t);
        }
    }

    private void emitSideQuad(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos, Vector3f lightDir, Vector3f toCam, float alphaFactor,
                              float ax, float ay, float bx, float by, float t) {
        float dx = bx - ax;
        float dy = by - ay;
        Vector3f sideNorm = new Vector3f(dy, -dx, 0.0f).normalize().rotate(rot);

        int[] col = calculateFaceColor(p.tintColor, sideNorm, lightDir, toCam, alphaFactor, true);
        emitQuad3D(buf, p, rot, camPos,
                ax, ay,  t,
                bx, by,  t,
                bx, by, -t,
                ax, ay, -t,
                col[0], col[1], col[2], col[3]);
    }

    private int[] calculateFaceColor(int baseColor, Vector3f normal, Vector3f lightDir, Vector3f toCam, float alphaFactor, boolean isSide) {
        float dotLight = Math.abs(normal.dot(lightDir));
        float diff = 0.55f + 0.45f * dotLight;

        float dotCam = Math.abs(normal.dot(toCam));
        float fresnel = (float) Math.pow(1.0f - dotCam, 2.0);

        int br = (baseColor >> 16) & 0xFF;
        int bg = (baseColor >> 8) & 0xFF;
        int bb = baseColor & 0xFF;

        float faceMultiplier = isSide ? 1.25f : 1.0f;
        int r = MathHelper.clamp(Math.round(br * diff * faceMultiplier + 100.0f * fresnel), 0, 255);
        int g = MathHelper.clamp(Math.round(bg * diff * faceMultiplier + 100.0f * fresnel), 0, 255);
        int b = MathHelper.clamp(Math.round(bb * diff * faceMultiplier + 100.0f * fresnel), 0, 255);

        float baseAlpha = isSide ? 220.0f : 160.0f;
        int a = MathHelper.clamp(Math.round(baseAlpha * alphaFactor), 0, 255);

        return new int[]{r, g, b, a};
    }

    private void emitQuad3D(VertexConsumer buf, DeathGlassShard p, Quaternionf rot, Vec3d camPos,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            int r, int g, int b, int a) {
        Vector3f v0 = new Vector3f(x0, y0, z0).rotate(rot);
        Vector3f v1 = new Vector3f(x1, y1, z1).rotate(rot);
        Vector3f v2 = new Vector3f(x2, y2, z2).rotate(rot);
        Vector3f v3 = new Vector3f(x3, y3, z3).rotate(rot);

        buf.vertex((float) (p.x + v0.x - camPos.x), (float) (p.y + v0.y - camPos.y), (float) (p.z + v0.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v1.x - camPos.x), (float) (p.y + v1.y - camPos.y), (float) (p.z + v1.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v2.x - camPos.x), (float) (p.y + v2.y - camPos.y), (float) (p.z + v2.z - camPos.z)).color(r, g, b, a);
        buf.vertex((float) (p.x + v3.x - camPos.x), (float) (p.y + v3.y - camPos.y), (float) (p.z + v3.z - camPos.z)).color(r, g, b, a);
    }

    private void renderGlintSparkle(VertexConsumer buf, DeathGlassShard p, Vec3d camPos, int alpha) {
        float gs = p.size * 0.85f;
        float gw = gs * 0.18f;

        float px = (float) (p.x - camPos.x);
        float py = (float) (p.y - camPos.y);
        float pz = (float) (p.z - camPos.z);

        buf.vertex(px - gw, py - gs, pz).color(255, 255, 255, 0);
        buf.vertex(px + gw, py - gs, pz).color(255, 255, 255, 0);
        buf.vertex(px + gw, py + gs, pz).color(255, 255, 255, alpha);
        buf.vertex(px - gw, py + gs, pz).color(255, 255, 255, alpha);

        buf.vertex(px - gs, py - gw, pz).color(255, 255, 255, 0);
        buf.vertex(px - gs, py + gw, pz).color(255, 255, 255, 0);
        buf.vertex(px + gs, py + gw, pz).color(255, 255, 255, alpha);
        buf.vertex(px + gs, py - gw, pz).color(255, 255, 255, alpha);
    }

    @Override
    public void onDisable() {
        particles.clear();
        butterflySwarms.clear();
        monolithInstances.clear();
        recentAttacks.clear();
        deadEntities.clear();
    }

    private static class DeathGlassShard {
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        float rotPitch, rotYaw, rotRoll;
        final float size;
        final float life;
        final int shapeType;
        final int tintColor;
        float age = 0.0f;

        DeathGlassShard(double x, double y, double z,
                        double vx, double vy, double vz,
                        float pitch, float yaw, float roll,
                        float rotPitch, float rotYaw, float rotRoll,
                        float size, float life, int shapeType, int tintColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.rotPitch = rotPitch;
            this.rotYaw = rotYaw;
            this.rotRoll = rotRoll;
            this.size = size;
            this.life = life;
            this.shapeType = shapeType;
            this.tintColor = tintColor;
        }
    }

    private static class MonolithInstance {
        final double x, y, z;
        final float yaw, pitch, headYaw;
        final boolean isSneaking;
        final float rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll;

        final float portalHeight;
        final float scale;
        final int runeColor;

        float age = 0.0f;
        final float totalLife = 3.0f;

        boolean soundPlayedDrop = false;
        boolean soundPlayedImpact = false;

        final List<MonolithDebrisShard> shards = new ArrayList<>();

        MonolithInstance(double x, double y, double z,
                         float yaw, float pitch, float headYaw, boolean isSneaking,
                         float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll,
                         float portalHeight, float scale, int runeColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.headYaw = headYaw;
            this.isSneaking = isSneaking;
            this.rLegPitch = rLegPitch;
            this.lLegPitch = lLegPitch;
            this.rArmPitch = rArmPitch;
            this.lArmPitch = lArmPitch;
            this.rArmRoll = rArmRoll;
            this.portalHeight = portalHeight;
            this.scale = scale;
            this.runeColor = runeColor;
        }

        void spawnShatterDebris(Random rnd) {
            for (int i = 0; i < 32; i++) {
                double px = x + (rnd.nextDouble() - 0.5) * 0.45;
                double py = y + rnd.nextDouble() * 1.5;
                double pz = z + (rnd.nextDouble() - 0.5) * 0.45;

                double angle = rnd.nextDouble() * Math.PI * 2.0;
                double hSpeed = 2.5 + rnd.nextDouble() * 5.0;
                double vx = Math.cos(angle) * hSpeed;
                double vz = Math.sin(angle) * hSpeed;
                double vy = 2.2 + rnd.nextDouble() * 6.5;

                float s = 0.07f + rnd.nextFloat() * 0.11f;
                float l = 1.4f + rnd.nextFloat() * 0.8f;

                float rotP = (rnd.nextFloat() - 0.5f) * 800.0f;
                float rotY = (rnd.nextFloat() - 0.5f) * 800.0f;
                float rotR = (rnd.nextFloat() - 0.5f) * 800.0f;

                shards.add(new MonolithDebrisShard(px, py, pz, vx, vy, vz, rotP, rotY, rotR, s, l));
            }
        }

        void updateAndRenderDebris(VertexConsumer quadBuf, Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
            Iterator<MonolithDebrisShard> it = shards.iterator();
            while (it.hasNext()) {
                MonolithDebrisShard s = it.next();
                s.age += dt;
                if (s.age >= s.life) {
                    it.remove();
                    continue;
                }

                s.vy -= 18.0 * dt;
                double nextX = s.x + s.vx * dt;
                double nextY = s.y + s.vy * dt;
                double nextZ = s.z + s.vz * dt;

                if (nextY <= y + s.size) {
                    nextY = y + s.size;
                    s.vy = -s.vy * 0.35;
                    s.vx *= 0.65;
                    s.vz *= 0.65;
                }

                s.x = nextX;
                s.y = nextY;
                s.z = nextZ;

                s.pitch += s.rotP * dt;
                s.yaw += s.rotY * dt;
                s.roll += s.rotR * dt;

                float fade = MathHelper.clamp(1.0f - (s.age / s.life), 0.0f, 1.0f);
                if (fade <= 0.01f) continue;
                int alpha = (int) (255 * fade);

                emitStoneShard(quadBuf, camPos, s.x, s.y, s.z, s.pitch, s.yaw, s.roll, s.size, lightDir, alpha);
            }
        }
    }

    private static class MonolithDebrisShard {
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        final float rotP, rotY, rotR;
        final float size;
        final float life;
        float age = 0.0f;

        MonolithDebrisShard(double x, double y, double z,
                            double vx, double vy, double vz,
                            float rotP, float rotY, float rotR,
                            float size, float life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.rotP = rotP;
            this.rotY = rotY;
            this.rotR = rotR;
            this.size = size;
            this.life = life;
        }
    }

    private void renderButterflySwarms(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                       Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        butterflySwarms.removeIf(swarm -> !swarm.updateAndRender(quadBuf, lightBuf, camPos, lightDir, dt, mc));
    }

    private static void emitButterflyBody(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                          float cx, float cy, float cz, Matrix4f mat,
                                          float scale, int color, float fade) {
        int bodyAlpha = (int) (240 * fade);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int darkR = Math.max(15, (int) (r * 0.25f));
        int darkG = Math.max(15, (int) (g * 0.25f));
        int darkB = Math.max(20, (int) (b * 0.25f));

        Vector3f pFront = new Vector3f(0.0f, 0.0f, 0.28f * scale);
        Vector3f pBack = new Vector3f(0.0f, 0.0f, -0.38f * scale);
        Vector3f pLeft = new Vector3f(-0.045f * scale, 0.0f, -0.05f * scale);
        Vector3f pRight = new Vector3f(0.045f * scale, 0.0f, -0.05f * scale);

        mat.transformPosition(pFront);
        mat.transformPosition(pBack);
        mat.transformPosition(pLeft);
        mat.transformPosition(pRight);

        quadBuf.vertex(cx + pFront.x, cy + pFront.y, cz + pFront.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pLeft.x, cy + pLeft.y, cz + pLeft.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pBack.x, cy + pBack.y, cz + pBack.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pRight.x, cy + pRight.y, cz + pRight.z).color(darkR, darkG, darkB, bodyAlpha);

        quadBuf.vertex(cx + pFront.x, cy + pFront.y, cz + pFront.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pRight.x, cy + pRight.y, cz + pRight.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pBack.x, cy + pBack.y, cz + pBack.z).color(darkR, darkG, darkB, bodyAlpha);
        quadBuf.vertex(cx + pLeft.x, cy + pLeft.y, cz + pLeft.z).color(darkR, darkG, darkB, bodyAlpha);

        int coreAlpha = (int) (180 * fade);
        lightBuf.vertex(cx + pFront.x, cy + pFront.y, cz + pFront.z).color(r, g, b, coreAlpha);
        lightBuf.vertex(cx + pLeft.x, cy + pLeft.y, cz + pLeft.z).color(r, g, b, coreAlpha);
        lightBuf.vertex(cx + pBack.x, cy + pBack.y, cz + pBack.z).color(r, g, b, coreAlpha);
        lightBuf.vertex(cx + pRight.x, cy + pRight.y, cz + pRight.z).color(r, g, b, coreAlpha);
    }

    private static void emitButterflyWings(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                           float cx, float cy, float cz, Matrix4f mat,
                                           float scale, float wingAngle, int color, float fade,
                                           Vector3f lightDir) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int innerR = Math.min(255, (int) (r * 0.75f + 85));
        int innerG = Math.min(255, (int) (g * 0.75f + 85));
        int innerB = Math.min(255, (int) (b * 0.75f + 85));

        int tipR = r;
        int tipG = g;
        int tipB = b;

        int edgeR = (int) (r * 0.85f);
        int edgeG = (int) (g * 0.85f);
        int edgeB = (int) (b * 0.85f);

        int qAlpha = (int) (215 * fade);
        int lAlpha = (int) (135 * fade);

        float cosW = (float) Math.cos(wingAngle);
        float sinW = (float) Math.sin(wingAngle);

        emitSingleWing(quadBuf, lightBuf, cx, cy, cz, mat,
                0.0f, 0.22f * scale,
                0.0f, -0.05f * scale,
                -0.78f * scale, -0.05f * scale,
                -1.12f * scale, 0.42f * scale,
                cosW, sinW, true,
                innerR, innerG, innerB, tipR, tipG, tipB, edgeR, edgeG, edgeB, qAlpha, lAlpha);

        emitSingleWing(quadBuf, lightBuf, cx, cy, cz, mat,
                0.0f, -0.05f * scale,
                0.0f, -0.32f * scale,
                -0.62f * scale, -0.45f * scale,
                -0.78f * scale, -0.05f * scale,
                cosW, sinW, true,
                innerR, innerG, innerB, tipR, tipG, tipB, edgeR, edgeG, edgeB, qAlpha, lAlpha);

        emitSingleWing(quadBuf, lightBuf, cx, cy, cz, mat,
                0.0f, 0.22f * scale,
                1.12f * scale, 0.42f * scale,
                0.78f * scale, -0.05f * scale,
                0.0f, -0.05f * scale,
                cosW, sinW, false,
                innerR, innerG, innerB, tipR, tipG, tipB, edgeR, edgeG, edgeB, qAlpha, lAlpha);

        emitSingleWing(quadBuf, lightBuf, cx, cy, cz, mat,
                0.0f, -0.05f * scale,
                0.78f * scale, -0.05f * scale,
                0.62f * scale, -0.45f * scale,
                0.0f, -0.32f * scale,
                cosW, sinW, false,
                innerR, innerG, innerB, tipR, tipG, tipB, edgeR, edgeG, edgeB, qAlpha, lAlpha);
    }

    private static void emitSingleWing(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                       float cx, float cy, float cz, Matrix4f mat,
                                       float x0, float z0,
                                       float x1, float z1,
                                       float x2, float z2,
                                       float x3, float z3,
                                       float cosW, float sinW, boolean isLeft,
                                       int inR, int inG, int inB,
                                       int tipR, int tipG, int tipB,
                                       int edR, int edG, int edB,
                                       int qAlpha, int lAlpha) {
        Vector3f v0 = rotateWingVertex(x0, z0, cosW, sinW, isLeft);
        Vector3f v1 = rotateWingVertex(x1, z1, cosW, sinW, isLeft);
        Vector3f v2 = rotateWingVertex(x2, z2, cosW, sinW, isLeft);
        Vector3f v3 = rotateWingVertex(x3, z3, cosW, sinW, isLeft);

        mat.transformPosition(v0);
        mat.transformPosition(v1);
        mat.transformPosition(v2);
        mat.transformPosition(v3);

        quadBuf.vertex(cx + v0.x, cy + v0.y, cz + v0.z).color(inR, inG, inB, qAlpha);
        quadBuf.vertex(cx + v1.x, cy + v1.y, cz + v1.z).color(tipR, tipG, tipB, qAlpha);
        quadBuf.vertex(cx + v2.x, cy + v2.y, cz + v2.z).color(edR, edG, edB, qAlpha);
        quadBuf.vertex(cx + v3.x, cy + v3.y, cz + v3.z).color(inR, inG, inB, qAlpha);

        quadBuf.vertex(cx + v3.x, cy + v3.y, cz + v3.z).color(inR, inG, inB, qAlpha);
        quadBuf.vertex(cx + v2.x, cy + v2.y, cz + v2.z).color(edR, edG, edB, qAlpha);
        quadBuf.vertex(cx + v1.x, cy + v1.y, cz + v1.z).color(tipR, tipG, tipB, qAlpha);
        quadBuf.vertex(cx + v0.x, cy + v0.y, cz + v0.z).color(inR, inG, inB, qAlpha);

        lightBuf.vertex(cx + v0.x, cy + v0.y, cz + v0.z).color(inR, inG, inB, lAlpha);
        lightBuf.vertex(cx + v1.x, cy + v1.y, cz + v1.z).color(tipR, tipG, tipB, lAlpha);
        lightBuf.vertex(cx + v2.x, cy + v2.y, cz + v2.z).color(edR, edG, edB, lAlpha);
        lightBuf.vertex(cx + v3.x, cy + v3.y, cz + v3.z).color(inR, inG, inB, lAlpha);

        lightBuf.vertex(cx + v3.x, cy + v3.y, cz + v3.z).color(inR, inG, inB, lAlpha);
        lightBuf.vertex(cx + v2.x, cy + v2.y, cz + v2.z).color(edR, edG, edB, lAlpha);
        lightBuf.vertex(cx + v1.x, cy + v1.y, cz + v1.z).color(tipR, tipG, tipB, lAlpha);
        lightBuf.vertex(cx + v0.x, cy + v0.y, cz + v0.z).color(inR, inG, inB, lAlpha);
    }

    private static Vector3f rotateWingVertex(float lx, float lz, float cosW, float sinW, boolean isLeft) {
        float rx = lx * cosW;
        float ry = isLeft ? (-lx * sinW) : (lx * sinW);
        return new Vector3f(rx, ry, lz);
    }

    private static class ButterflySwarmInstance {
        final double originX, originY, originZ;
        final List<Butterfly> butterflies = new ArrayList<>();
        final List<ButterflyTrailParticle> trails = new ArrayList<>();
        final boolean enableTrails;

        ButterflySwarmInstance(double bx, double by, double bz, double height,
                               int count, float baseSpeed, float baseSize, float baseLife,
                               int baseColor, boolean rainbow, boolean enableTrails, Random rnd) {
            this.originX = bx;
            this.originY = by + height * 0.5;
            this.originZ = bz;
            this.enableTrails = enableTrails;

            for (int i = 0; i < count; i++) {
                double sx = bx + (rnd.nextDouble() - 0.5) * 0.45;
                double sy = by + rnd.nextDouble() * height;
                double sz = bz + (rnd.nextDouble() - 0.5) * 0.45;

                double angle = rnd.nextDouble() * Math.PI * 2.0;
                double hSpd = baseSpeed * (0.35 + 0.65 * rnd.nextDouble());
                double vx = Math.cos(angle) * hSpd;
                double vz = Math.sin(angle) * hSpd;
                double vy = (1.5 + rnd.nextDouble() * 2.8) * (baseSpeed / 3.8);

                float bSize = baseSize * (0.80f + 0.45f * rnd.nextFloat());
                float bLife = baseLife * (0.80f + 0.45f * rnd.nextFloat());
                float flapSpd = 16.0f + rnd.nextFloat() * 10.0f;
                float flapPhase = rnd.nextFloat() * 6.28f;
                float wanderOff = rnd.nextFloat() * 100.0f;
                float spiralDir = rnd.nextBoolean() ? 1.0f : -1.0f;

                int bColor;
                if (rainbow) {
                    bColor = BUTTERFLY_PALETTE[rnd.nextInt(BUTTERFLY_PALETTE.length)];
                } else {
                    float factor = 0.88f + 0.24f * rnd.nextFloat();
                    int r = Math.clamp((int) (((baseColor >> 16) & 0xFF) * factor), 0, 255);
                    int g = Math.clamp((int) (((baseColor >> 8) & 0xFF) * factor), 0, 255);
                    int b = Math.clamp((int) ((baseColor & 0xFF) * factor), 0, 255);
                    bColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }

                butterflies.add(new Butterfly(
                        sx, sy, sz, vx, vy, vz,
                        bSize, bLife, flapSpd, flapPhase,
                        wanderOff, spiralDir, bColor
                ));
            }

            if (enableTrails) {
                for (int i = 0; i < 24; i++) {
                    double px = bx + (rnd.nextDouble() - 0.5) * 0.6;
                    double py = by + rnd.nextDouble() * height;
                    double pz = bz + (rnd.nextDouble() - 0.5) * 0.6;
                    double vx = (rnd.nextDouble() - 0.5) * 2.5;
                    double vy = 1.0 + rnd.nextDouble() * 2.5;
                    double vz = (rnd.nextDouble() - 0.5) * 2.5;
                    int sparkCol = rainbow ? BUTTERFLY_PALETTE[rnd.nextInt(BUTTERFLY_PALETTE.length)] : baseColor;
                    trails.add(new ButterflyTrailParticle(px, py, pz, vx, vy, vz, 0.05f + rnd.nextFloat() * 0.06f, 1.2f + rnd.nextFloat() * 0.8f, sparkCol));
                }
            }
        }

        boolean updateAndRender(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
            Iterator<Butterfly> itB = butterflies.iterator();
            while (itB.hasNext()) {
                Butterfly b = itB.next();
                b.age += dt;
                if (b.age >= b.life) {
                    itB.remove();
                    continue;
                }

                b.update(dt, originX, originZ);

                if (enableTrails) {
                    b.trailTimer += dt;
                    if (b.trailTimer >= 0.045f) {
                        b.trailTimer = 0.0f;
                        if (trails.size() < 300) {
                            float sSize = b.size * 0.28f;
                            trails.add(new ButterflyTrailParticle(
                                    b.x, b.y, b.z,
                                    b.vx * 0.15 + (Math.random() - 0.5) * 0.3,
                                    b.vy * 0.15 - 0.15,
                                    b.vz * 0.15 + (Math.random() - 0.5) * 0.3,
                                    sSize, 0.75f, b.color
                            ));
                        }
                    }
                }

                b.render(quadBuf, lightBuf, camPos, lightDir);
            }

            Iterator<ButterflyTrailParticle> itT = trails.iterator();
            while (itT.hasNext()) {
                ButterflyTrailParticle t = itT.next();
                t.age += dt;
                if (t.age >= t.life) {
                    itT.remove();
                    continue;
                }
                t.vy -= 0.65 * dt;
                t.x += t.vx * dt;
                t.y += t.vy * dt;
                t.z += t.vz * dt;
                t.render(lightBuf, camPos);
            }

            return !butterflies.isEmpty() || !trails.isEmpty();
        }
    }

    private static class Butterfly {
        double x, y, z;
        double vx, vy, vz;
        float yaw, pitch, roll;
        final float size;
        final float life;
        final float flapSpeed;
        final float flapPhase;
        final float wanderOffset;
        final float spiralDir;
        final int color;
        float age = 0.0f;
        float trailTimer = 0.0f;

        Butterfly(double x, double y, double z,
                  double vx, double vy, double vz,
                  float size, float life,
                  float flapSpeed, float flapPhase,
                  float wanderOffset, float spiralDir, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.flapSpeed = flapSpeed;
            this.flapPhase = flapPhase;
            this.wanderOffset = wanderOffset;
            this.spiralDir = spiralDir;
            this.color = color;
            this.yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
            this.pitch = (float) Math.toDegrees(-Math.atan2(vy, Math.max(0.1, Math.sqrt(vx * vx + vz * vz))));
            this.roll = 0.0f;
        }

        void update(float dt, double originX, double originZ) {
            float flap = (float) Math.sin((age + flapPhase) * flapSpeed);
            float lift = flap > 0.0f ? 2.4f : 0.8f;
            vy += (lift - 1.15f) * dt;

            double dx = x - originX;
            double dz = z - originZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.01) {
                double swirl = 1.4 * spiralDir;
                vx += (-dz / dist) * swirl * dt;
                vz += (dx / dist) * swirl * dt;
            }

            float wanderTime = age * 2.2f + wanderOffset;
            vx += Math.cos(wanderTime) * 1.6f * dt;
            vz += Math.sin(wanderTime * 1.25f) * 1.6f * dt;

            float drag = (float) Math.exp(-0.75f * dt);
            vx *= drag;
            vz *= drag;

            x += vx * dt;
            y += vy * dt;
            z += vz * dt;

            double hSpeed = Math.sqrt(vx * vx + vz * vz);
            float targetYaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
            float targetPitch = (float) Math.toDegrees(-Math.atan2(vy, Math.max(0.1, hSpeed)));

            yaw = MathHelper.lerpAngleDegrees(Math.min(1.0f, dt * 8.0f), yaw, targetYaw);
            pitch = MathHelper.lerpAngleDegrees(Math.min(1.0f, dt * 8.0f), pitch, targetPitch);
            roll = flap * 12.0f;
        }

        void render(VertexConsumer quadBuf, VertexConsumer lightBuf, Vec3d camPos, Vector3f lightDir) {
            float progress = age / life;
            float fade = 1.0f;
            if (progress < 0.12f) {
                fade = progress / 0.12f;
            } else if (progress > 0.65f) {
                fade = (1.0f - progress) / 0.35f;
            }
            if (fade <= 0.01f) return;

            float currentScale = size * (0.85f + 0.15f * fade);
            float flap = (float) Math.sin((age + flapPhase) * flapSpeed);
            float wingAngle = flap * 0.88f;

            Matrix4f mat = new Matrix4f()
                    .rotateY((float) Math.toRadians(yaw))
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateZ((float) Math.toRadians(roll));

            float cx = (float) (x - camPos.x);
            float cy = (float) (y - camPos.y);
            float cz = (float) (z - camPos.z);

            emitButterflyBody(quadBuf, lightBuf, cx, cy, cz, mat, currentScale, color, fade);
            emitButterflyWings(quadBuf, lightBuf, cx, cy, cz, mat, currentScale, wingAngle, color, fade, lightDir);
        }
    }

    private static class ButterflyTrailParticle {
        double x, y, z;
        double vx, vy, vz;
        final float size;
        final float life;
        final int color;
        float age = 0.0f;

        ButterflyTrailParticle(double x, double y, double z,
                               double vx, double vy, double vz,
                               float size, float life, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.color = color;
        }

        void render(VertexConsumer lightBuf, Vec3d camPos) {
            float progress = age / life;
            float sparkle = (1.0f - progress) * (0.6f + 0.4f * (float) Math.sin(age * 18.0f));
            int alpha = (int) (235 * sparkle);
            if (alpha <= 2) return;

            float px = (float) (x - camPos.x);
            float py = (float) (y - camPos.y);
            float pz = (float) (z - camPos.z);
            float s = size * (0.8f + 0.4f * (1.0f - progress));

            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            lightBuf.vertex(px - s, py, pz).color(r, g, b, 0);
            lightBuf.vertex(px, py + s, pz).color(255, 255, 255, alpha);
            lightBuf.vertex(px + s, py, pz).color(r, g, b, 0);
            lightBuf.vertex(px, py - s, pz).color(255, 255, 255, alpha);
        }
    }
}
