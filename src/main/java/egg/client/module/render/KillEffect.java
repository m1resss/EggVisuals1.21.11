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

    private final ModeSetting mode = new ModeSetting("Эффект", "Статуя", "Тип эффекта при смерти противника", "Статуя", "Обелиск", "Чернила", "Стекло");
    private final BooleanSetting onlyTarget = new BooleanSetting("Только цель", true, "Создавать эффект только для атакованных вами врагов");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false, "Создавать эффект только при смерти игроков");
    private final BooleanSetting sound = new BooleanSetting("Звуки", true, "Звуковые эффекты при убийстве");
    private final BooleanSetting syncColor = new BooleanSetting("Синхр. цвета", false, "Использовать цвет темы клиента для эффекта");
    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFFD700, "Пользовательский цвет эффекта (по умолч. золото)");

    // Настройки для режима Статуя
    private final SliderSetting statueSpeed = new SliderSetting("Скорость льда", 5.2, 1.0, 12.0, 0.5, "Скорость скольжения золотой статуи по земле");
    private final SliderSetting meltDuration = new SliderSetting("Время плавления", 2.6, 1.5, 5.0, 0.1, "Длительность плавления статуи в лужу (сек)");

    // Настройки для режима Обелиск (Monolith Crush)
    private final SliderSetting monolithHeight = new SliderSetting("Высота портала", 6.0, 3.5, 12.0, 0.5, "Высота появления небесного обелиска");
    private final SliderSetting monolithScale = new SliderSetting("Размер обелиска", 1.0, 0.6, 2.0, 0.1, "Масштаб 3D модели обелиска");
    private final BooleanSetting monolithRunes = new BooleanSetting("Руны", true, "Светящиеся выбитые руны на гранях обелиска");

    // Настройки для режима Чернила (Суми-э)
    private final SliderSetting slashScale = new SliderSetting("Масштаб среза", 1.0, 0.5, 2.0, 0.1, "Масштаб каллиграфического среза и модели");
    private final SliderSetting inkDrops = new SliderSetting("Брызги туши", 35, 10, 80, 5, "Количество вылетающих капель туши");

    // Настройки для режима Стекло
    private final SliderSetting amount = new SliderSetting("Осколки стекла", 85, 20, 250, 5, "Количество 3D осколков стекла");
    private final SliderSetting size = new SliderSetting("Размер стекла", 0.22, 0.05, 0.60, 0.01, "Размер осколков стекла");
    private final SliderSetting speed = new SliderSetting("Скорость стекла", 6.5, 2.0, 15.0, 0.5, "Скорость разлета осколков во все стороны");
    private final SliderSetting lifetime = new SliderSetting("Жизнь стекла", 2.0, 0.5, 5.0, 0.1, "Длительность существования осколков (сек)");
    private final BooleanSetting bounce = new BooleanSetting("Отскок стекла", true, "Отскок осколков стекла от блоков и пола");
    private final BooleanSetting sparkle = new BooleanSetting("Блики стекла", true, "Яркие вспышки и отблески на гранях стекла");

    private final List<GoldenStatueInstance> statueInstances = new ArrayList<>();
    private final List<MonolithInstance> monolithInstances = new ArrayList<>();
    private final List<InkSlashInstance> inkSlashInstances = new ArrayList<>();
    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Set<Integer> deadEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public KillEffect() {
        super("KillEffect", "Воспроизводит визуальный эффект на месте гибели игрока", Category.ESP);
        instance = this;

        // Фильтрация настроек: отображаются только параметры активного режима
        statueSpeed.setVisible(() -> "Статуя".equals(mode.get()));
        meltDuration.setVisible(() -> "Статуя".equals(mode.get()));

        monolithHeight.setVisible(() -> "Обелиск".equals(mode.get()));
        monolithScale.setVisible(() -> "Обелиск".equals(mode.get()));
        monolithRunes.setVisible(() -> "Обелиск".equals(mode.get()));

        slashScale.setVisible(() -> "Чернила".equals(mode.get()));
        inkDrops.setVisible(() -> "Чернила".equals(mode.get()));

        amount.setVisible(() -> "Стекло".equals(mode.get()));
        size.setVisible(() -> "Стекло".equals(mode.get()));
        speed.setVisible(() -> "Стекло".equals(mode.get()));
        lifetime.setVisible(() -> "Стекло".equals(mode.get()));
        bounce.setVisible(() -> "Стекло".equals(mode.get()));
        sparkle.setVisible(() -> "Стекло".equals(mode.get()));

        color.setVisible(() -> !syncColor.isEnabled());

        addSettings(mode, onlyTarget, onlyPlayers, sound, syncColor, color,
                statueSpeed, meltDuration,
                monolithHeight, monolithScale, monolithRunes,
                slashScale, inkDrops,
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
            if ("Статуя".equals(curMode)) {
                spawnStatueEffect(entity);
            } else if ("Обелиск".equals(curMode)) {
                spawnMonolithEffect(entity);
            } else if ("Чернила".equals(curMode)) {
                spawnInkSlashEffect(entity);
            } else {
                spawnGlassBurst(entity);
            }
        }
    }

    // =========================================================================
    // Режим: Золотая Статуя (Golden Statue Melt & Ice Slide)
    // =========================================================================
    public void spawnStatueEffect(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double bx = entity.getX();
        double by = entity.getY();
        double bz = entity.getZ();
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float headYaw = entity.getHeadYaw();
        boolean isSneaking = entity.isSneaking();
        Vec3d vel = entity.getVelocity();

        float limbProgress = 0.0f;
        float limbSpeed = 0.0f;
        float swingProgress = 0.0f;
        if (entity instanceof LivingEntity living) {
            limbProgress = living.limbAnimator.getAnimationProgress();
            limbSpeed = living.limbAnimator.getSpeed();
            swingProgress = living.getHandSwingProgress(1.0f);
        }

        // Вычисление точных углов суставов на момент смерти
        float rLegPitch = (float) Math.cos(limbProgress * 0.6662f) * 1.4f * limbSpeed;
        float lLegPitch = (float) Math.cos(limbProgress * 0.6662f + (float) Math.PI) * 1.4f * limbSpeed;
        float rArmPitch = (float) Math.cos(limbProgress * 0.6662f + (float) Math.PI) * 1.8f * limbSpeed;
        float lArmPitch = (float) Math.cos(limbProgress * 0.6662f) * 1.8f * limbSpeed;
        float rArmRoll = 0.0f;
        if (swingProgress > 0.01f) {
            rArmPitch -= swingProgress * 1.4f;
            rArmRoll += swingProgress * 0.35f;
        }

        // Вектор скорости, сила тяжести и инерция
        boolean onGround = entity.isOnGround();
        double vy = onGround ? 0.0 : vel.y;
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double slideVx;
        double slideVz;
        float speedSetting = (float) statueSpeed.get();

        if (hSpeed > 0.06) {
            slideVx = (vel.x / hSpeed) * speedSetting;
            slideVz = (vel.z / hSpeed) * speedSetting;
        } else {
            // Если стоял неподвижно — скользит назад от удара
            float rad = (float) Math.toRadians(yaw);
            slideVx = Math.sin(rad) * speedSetting * 0.8;
            slideVz = -Math.cos(rad) * speedSetting * 0.8;
        }

        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        float maxLife = (float) meltDuration.get();

        if (sound.isEnabled() && mc.world != null) {
            // Звон мгновенной кристаллизации в монолитное золото
            mc.world.playSoundClient(bx, by + 1.0, bz,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS,
                    1.4f, 0.82f, false);
            mc.world.playSoundClient(bx, by + 1.0, bz,
                    SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS,
                    0.85f, 1.75f, false);
            // Скольжение по льду (только если уже на твердой поверхности)
            if (onGround) {
                mc.world.playSoundClient(bx, by, bz,
                        SoundEvents.BLOCK_GLASS_STEP, SoundCategory.PLAYERS,
                        1.3f, 0.70f, false);
            }
        }

        statueInstances.add(new GoldenStatueInstance(
                bx, by, bz, yaw, pitch, headYaw, isSneaking,
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll,
                slideVx, slideVz, vy, onGround, tint, maxLife
        ));

        while (statueInstances.size() > 8) {
            statueInstances.remove(0);
        }
    }

    // =========================================================================
    // Режим: Небесный Обелиск (Monolith Crush)
    // =========================================================================
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

        // Звуки небесного разлома и нарастающего гравитационного гула
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

    // =========================================================================
    // Режим: Чернила Суми-э (Calligraphic Ink Slash + 1:1 Model Slice)
    // =========================================================================
    public void spawnInkSlashEffect(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double bx = entity.getX();
        double by = entity.getY();
        double bz = entity.getZ();
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float headYaw = entity.getHeadYaw();
        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        float scale = (float) slashScale.get();
        int dropCount = (int) inkDrops.get();

        Identifier skin = null;
        boolean isSlim = false;
        if (entity instanceof AbstractClientPlayerEntity player) {
            try {
                skin = player.getSkin().body().id();
                isSlim = player.getSkin().model() == PlayerSkinType.SLIM;
            } catch (Throwable ignored) {}
        }
        if (skin == null) {
            skin = DefaultSkinHelper.getTexture();
        }

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by + 1.1, bz,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
                    1.5f, 0.65f, false);
            mc.world.playSoundClient(bx, by + 1.1, bz,
                    SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS,
                    1.2f, 1.8f, false);
        }

        inkSlashInstances.add(new InkSlashInstance(bx, by, bz, yaw, pitch, headYaw, skin, isSlim, tint, scale, dropCount));
        while (inkSlashInstances.size() > 8) {
            inkSlashInstances.remove(0);
        }
    }

    // =========================================================================
    // Режим: Стекло (3D Glass Burst)
    // =========================================================================
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
            if (!statueInstances.isEmpty()) statueInstances.clear();
            if (!monolithInstances.isEmpty()) monolithInstances.clear();
            if (!inkSlashInstances.isEmpty()) inkSlashInstances.clear();
            if (!particles.isEmpty()) particles.clear();
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

        // 1. Отрисовка Золотой Статуи (скольжение + плавление + лужа)
        if (!statueInstances.isEmpty()) {
            renderGoldenStatues(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        // 2. Отрисовка Небесного Обелиска (Monolith Crush)
        if (!monolithInstances.isEmpty()) {
            renderMonolithInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        // 3. Отрисовка эффекта Чернила Суми-э
        if (!inkSlashInstances.isEmpty()) {
            renderInkSlashInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        // 4. Отрисовка осколков стекла
        if (!particles.isEmpty()) {
            renderGlassParticles(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }
    }

    // =========================================================================
    // Отрисовка и физика Золотой Статуи
    // =========================================================================
    private void renderGoldenStatues(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                     Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<GoldenStatueInstance> it = statueInstances.iterator();
        while (it.hasNext()) {
            GoldenStatueInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.maxLife) {
                it.remove();
                continue;
            }

            // Физика скольжения и гравитации: пока статуя не осела в лужу (первые 1.35 сек)
            if (inst.age < 1.35f) {
                if (mc.world != null) {
                    // 1. Сила тяжести
                    if (!inst.onGround) {
                        inst.vy -= 22.0 * dt;
                        if (inst.vy < -32.0) inst.vy = -32.0;
                    }

                    // 2. Горизонтальное скольжение с проверкой препятствий (стен)
                    double nextX = inst.x + inst.slideVx * dt;
                    double nextZ = inst.z + inst.slideVz * dt;

                    BlockPos wallX = BlockPos.ofFloored(nextX, inst.y + 0.6, inst.z);
                    if (mc.world.getBlockState(wallX).blocksMovement()) {
                        inst.slideVx = -inst.slideVx * 0.25;
                        nextX = inst.x;
                    }
                    BlockPos wallZ = BlockPos.ofFloored(inst.x, inst.y + 0.6, nextZ);
                    if (mc.world.getBlockState(wallZ).blocksMovement()) {
                        inst.slideVz = -inst.slideVz * 0.25;
                        nextZ = inst.z;
                    }

                    // 3. Вертикальное перемещение и коллизия с твердой поверхностью
                    double nextY = inst.y + inst.vy * dt;

                    BlockPos feetPos = BlockPos.ofFloored(nextX, nextY, nextZ);
                    BlockState feetState = mc.world.getBlockState(feetPos);

                    boolean hitGround = false;
                    if (feetState.blocksMovement()) {
                        VoxelShape shape = feetState.getCollisionShape(mc.world, feetPos);
                        double blockTop = feetPos.getY() + (shape.isEmpty() ? 1.0 : shape.getMax(Direction.Axis.Y));
                        if (inst.y >= blockTop - 0.35) {
                            nextY = blockTop;
                            if (!inst.onGround && inst.vy < -2.0) {
                                if (sound.isEnabled()) {
                                    mc.world.playSoundClient(nextX, nextY, nextZ,
                                            SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS,
                                            0.65f, 1.45f + random.nextFloat() * 0.2f, false);
                                }
                            }
                            inst.vy = 0.0;
                            inst.onGround = true;
                            hitGround = true;
                        }
                    }

                    if (!hitGround) {
                        // Проверка блока непосредственно под подошвой при скольжении
                        BlockPos underPos = BlockPos.ofFloored(nextX, nextY - 0.08, nextZ);
                        BlockState underState = mc.world.getBlockState(underPos);
                        if (underState.blocksMovement() && inst.vy <= 0.0) {
                            VoxelShape shape = underState.getCollisionShape(mc.world, underPos);
                            double blockTop = underPos.getY() + (shape.isEmpty() ? 1.0 : shape.getMax(Direction.Axis.Y));
                            if (Math.abs(inst.y - blockTop) < 0.22) {
                                nextY = blockTop;
                                inst.vy = 0.0;
                                inst.onGround = true;
                                hitGround = true;
                            }
                        }
                    }

                    if (!hitGround) {
                        inst.onGround = false;
                    }

                    inst.x = nextX;
                    inst.y = nextY;
                    inst.z = nextZ;

                    // 4. Трение: на земле ледяное скольжение, в воздухе легкое аэродинамическое сопротивление
                    if (inst.onGround) {
                        float friction = (float) Math.exp(-1.45f * dt);
                        inst.slideVx *= friction;
                        inst.slideVz *= friction;
                    } else {
                        float airDrag = (float) Math.exp(-0.25f * dt);
                        inst.slideVx *= airDrag;
                        inst.slideVz *= airDrag;
                    }

                    // Лужа следует за текущей точкой опоры статуи
                    inst.puddleX = inst.x;
                    inst.puddleY = inst.y;
                    inst.puddleZ = inst.z;

                    // Срыв расплавленных золотых капель на ходу
                    if (random.nextFloat() < 0.65f) {
                        inst.spawnMeltingDroplet(random);
                    }
                }
            }

            // Провал в бездну
            if (inst.y < -70.0) {
                it.remove();
                continue;
            }

            // Звук шипения тающего золота
            if (inst.age >= 0.35f && !inst.soundPlayedMelt) {
                inst.soundPlayedMelt = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.PLAYERS,
                            1.1f, 1.4f, false);
                }
            }

            // Коэффициент плавления (от 0.0 до 1.0)
            float meltProg = MathHelper.clamp((inst.age - 0.20f) / 1.10f, 0.0f, 1.0f);
            float yScale = (float) (Math.pow(1.0f - meltProg, 1.45) * 0.96f + 0.04f);
            float xzSpread = 1.0f + meltProg * 0.42f;

            // 1. Отрисовка самой тающей 3D золотой статуи
            if (meltProg < 0.98f) {
                renderMeltingStatueModel(quadBuf, lightBuf, inst, camPos, lightDir, yScale, xzSpread, meltProg);
            }

            // 2. Отрисовка неподвижной зеркальной золотой лужи на земле
            renderGoldenPuddle(quadBuf, lightBuf, inst, camPos, lightDir, meltProg);

            // 3. Обновление и отрисовка капель жидкого золота
            inst.updateAndRenderDroplets(quadBuf, lightBuf, camPos, dt);
        }
    }

    private void renderMeltingStatueModel(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                         GoldenStatueInstance inst, Vec3d camPos, Vector3f lightDir,
                                         float yScale, float xzSpread, float meltProg) {
        // Положение и поворот тела
        Matrix4f bodyMat = new Matrix4f()
                .rotateY((float) Math.toRadians(-inst.yaw));

        // Матрица трансформации торса
        Matrix4f torsoMat = new Matrix4f(bodyMat);
        if (inst.isSneaking) {
            torsoMat.rotateX((float) Math.toRadians(25.0f));
        }

        // Цвета золота: 24K золотой отлив с глубоким амбровым затемнением
        int baseCol = inst.tintColor;
        int alpha = (int) (255 * MathHelper.clamp((1.0f - meltProg) / 0.15f, 0.0f, 1.0f));

        // Торс (ширина 0.5, высота 0.75, глубина 0.25)
        float torsoBaseY = 0.70f * yScale;
        float torsoTopY = torsoBaseY + 0.75f * yScale;
        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, torsoMat,
                -0.25f * xzSpread, torsoBaseY, -0.125f * xzSpread,
                0.25f * xzSpread, torsoTopY, 0.125f * xzSpread,
                baseCol, lightDir, alpha);

        // Голова (0.5 x 0.5 x 0.5), поворачивается по headYaw и pitch
        Matrix4f headMat = new Matrix4f(bodyMat);
        headMat.translate(0, torsoTopY, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));

        float headH = 0.50f * yScale;
        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, headMat,
                -0.25f * xzSpread, 0, -0.25f * xzSpread,
                0.25f * xzSpread, headH, 0.25f * xzSpread,
                baseCol, lightDir, alpha);

        // Правая рука (сохраняет зафиксированный угол атаки/бега)
        Matrix4f rArmMat = new Matrix4f(bodyMat);
        rArmMat.translate(-0.375f * xzSpread, torsoTopY - 0.08f * yScale, 0);
        rArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rArmPitch)));
        rArmMat.rotateZ((float) Math.toRadians(-Math.toDegrees(inst.rArmRoll)));

        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, rArmMat,
                -0.125f * xzSpread, -0.72f * yScale, -0.125f * xzSpread,
                0.125f * xzSpread, 0.03f * yScale, 0.125f * xzSpread,
                baseCol, lightDir, alpha);

        // Левая рука
        Matrix4f lArmMat = new Matrix4f(bodyMat);
        lArmMat.translate(0.375f * xzSpread, torsoTopY - 0.08f * yScale, 0);
        lArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lArmPitch)));

        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, lArmMat,
                -0.125f * xzSpread, -0.72f * yScale, -0.125f * xzSpread,
                0.125f * xzSpread, 0.03f * yScale, 0.125f * xzSpread,
                baseCol, lightDir, alpha);

        // Правая нога (зафиксированная в шаге)
        Matrix4f rLegMat = new Matrix4f(bodyMat);
        rLegMat.translate(-0.125f * xzSpread, torsoBaseY, 0);
        rLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rLegPitch)));

        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, rLegMat,
                -0.125f * xzSpread, -0.70f * yScale, -0.125f * xzSpread,
                0.125f * xzSpread, 0, 0.125f * xzSpread,
                baseCol, lightDir, alpha);

        // Левая нога
        Matrix4f lLegMat = new Matrix4f(bodyMat);
        lLegMat.translate(0.125f * xzSpread, torsoBaseY, 0);
        lLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lLegPitch)));

        emitGoldBox(quadBuf, camPos, inst.x, inst.y, inst.z, lLegMat,
                -0.125f * xzSpread, -0.70f * yScale, -0.125f * xzSpread,
                0.125f * xzSpread, 0, 0.125f * xzSpread,
                baseCol, lightDir, alpha);
    }

    private void renderGoldenPuddle(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                    GoldenStatueInstance inst, Vec3d camPos, Vector3f lightDir, float meltProg) {
        if (meltProg < 0.15f) return;

        // Плавное появление лужи по мере таяния статуи
        float puddleAppear = MathHelper.clamp((meltProg - 0.15f) / 0.85f, 0.0f, 1.0f);

        // Плавное исчезновение лужи после завершения таяния (после 1.45 сек)
        float fade = 1.0f;
        if (inst.age > 1.45f) {
            fade = MathHelper.clamp(1.0f - (inst.age - 1.45f) / (inst.maxLife - 1.45f), 0.0f, 1.0f);
        }
        if (fade <= 0.005f) return;

        // Лужа НЕ ДВИГАЕТСЯ: зафиксирована на поверхности блока в inst.puddleX, inst.puddleY, inst.puddleZ
        float px = (float) (inst.puddleX - camPos.x);
        float py = (float) (inst.puddleY + 0.015 - camPos.y);
        float pz = (float) (inst.puddleZ - camPos.z);

        float radius = (0.55f + puddleAppear * 0.42f);
        int segments = 24;

        int centerAlpha = (int) (245 * fade);
        int rimAlpha = (int) (225 * fade);

        // Отражение неба и солнца на зеркальной поверхности жидкого золота
        int centerCol = computeGoldColor(inst.puddleX, inst.puddleY, inst.puddleZ,
                camPos, new Vector3f(0, 1, 0), null, lightDir, inst.tintColor);
        int cr = (centerCol >> 16) & 0xFF;
        int cg = (centerCol >> 8) & 0xFF;
        int cb = centerCol & 0xFF;

        // Отрисовка жидкого зеркального золота на земле
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;

            // Волнообразная кромка лужи с поверхностным натяжением
            float r0 = radius * (1.0f + 0.07f * (float) Math.sin(a0 * 5.0 + inst.age * 3.0));
            float r1 = radius * (1.0f + 0.07f * (float) Math.sin(a1 * 5.0 + inst.age * 3.0));

            float x0 = px + (float) Math.cos(a0) * r0;
            float z0 = pz + (float) Math.sin(a0) * r0;
            float x1 = px + (float) Math.cos(a1) * r1;
            float z1 = pz + (float) Math.sin(a1) * r1;

            // Нормали на кромке изогнуты наружу (мениск жидкого металла)
            Vector3f norm0 = new Vector3f((float) Math.cos(a0) * 0.35f, 0.93f, (float) Math.sin(a0) * 0.35f).normalize();
            Vector3f norm1 = new Vector3f((float) Math.cos(a1) * 0.35f, 0.93f, (float) Math.sin(a1) * 0.35f).normalize();

            int col0 = computeGoldColor(inst.puddleX + Math.cos(a0) * r0, inst.puddleY, inst.puddleZ + Math.sin(a0) * r0,
                    camPos, norm0, null, lightDir, inst.tintColor);
            int col1 = computeGoldColor(inst.puddleX + Math.cos(a1) * r1, inst.puddleY, inst.puddleZ + Math.sin(a1) * r1,
                    camPos, norm1, null, lightDir, inst.tintColor);

            // Зеркальная золотая поверхность лужи
            quadBuf.vertex(px, py, pz).color(cr, cg, cb, centerAlpha);
            quadBuf.vertex(x0, py, z0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, rimAlpha);
            quadBuf.vertex(x1, py, z1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, rimAlpha);
            quadBuf.vertex(px, py, pz).color(cr, cg, cb, centerAlpha);

            // Светящаяся кромка расплавленного золота
            float glowR0 = r0 * 1.12f;
            float glowR1 = r1 * 1.12f;
            float gx0 = px + (float) Math.cos(a0) * glowR0;
            float gz0 = pz + (float) Math.sin(a0) * glowR0;
            float gx1 = px + (float) Math.cos(a1) * glowR1;
            float gz1 = pz + (float) Math.sin(a1) * glowR1;

            int auraAlpha = (int) (150 * fade);
            lightBuf.vertex(x0, py + 0.005f, z0).color(cr, cg, cb, auraAlpha);
            lightBuf.vertex(gx0, py + 0.005f, gz0).color(cr, cg, cb, 0);
            lightBuf.vertex(gx1, py + 0.005f, gz1).color(cr, cg, cb, 0);
            lightBuf.vertex(x1, py + 0.005f, z1).color(cr, cg, cb, auraAlpha);
        }
    }

    private static void emitGoldBox(VertexConsumer buf, Vec3d camPos,
                                    double wx, double wy, double wz, Matrix4f mat,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    int tintColor, Vector3f lightDir, int alpha) {
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;
        float cz = (z0 + z1) * 0.5f;

        // Front (+Z)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                x0 - cx, y0 - cy, z1 - cz,
                x1 - cx, y0 - cy, z1 - cz,
                x1 - cx, y1 - cy, z1 - cz,
                x0 - cx, y1 - cy, z1 - cz,
                0, 0, 1);
        // Back (-Z)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                x1 - cx, y0 - cy, z0 - cz,
                x0 - cx, y0 - cy, z0 - cz,
                x0 - cx, y1 - cy, z0 - cz,
                x1 - cx, y1 - cy, z0 - cz,
                0, 0, -1);
        // Top (+Y)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                x0 - cx, y1 - cy, z0 - cz,
                x0 - cx, y1 - cy, z1 - cz,
                x1 - cx, y1 - cy, z1 - cz,
                x1 - cx, y1 - cy, z0 - cz,
                0, 1, 0);
        // Bottom (-Y)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,
                x0 - cx, y0 - cy, z1 - cz,
                x0 - cx, y0 - cy, z0 - cz,
                x1 - cx, y0 - cy, z0 - cz,
                x1 - cx, y0 - cy, z1 - cz,
                0, -1, 0);
        // Left (-X)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                x0 - cx, y0 - cy, z0 - cz,
                x0 - cx, y0 - cy, z1 - cz,
                x0 - cx, y1 - cy, z1 - cz,
                x0 - cx, y1 - cy, z0 - cz,
                -1, 0, 0);
        // Right (+X)
        emitGoldFace(buf, camPos, wx, wy, wz, mat, lightDir, tintColor, alpha,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                x1 - cx, y0 - cy, z1 - cz,
                x1 - cx, y0 - cy, z0 - cz,
                x1 - cx, y1 - cy, z0 - cz,
                x1 - cx, y1 - cy, z1 - cz,
                1, 0, 0);
    }

    private static void emitGoldFace(VertexConsumer buf, Vec3d camPos,
                                     double wx, double wy, double wz, Matrix4f mat,
                                     Vector3f lightDir, int tintColor, int alpha,
                                     float lx0, float ly0, float lz0,
                                     float lx1, float ly1, float lz1,
                                     float lx2, float ly2, float lz2,
                                     float lx3, float ly3, float lz3,
                                     float c0x, float c0y, float c0z,
                                     float c1x, float c1y, float c1z,
                                     float c2x, float c2y, float c2z,
                                     float c3x, float c3y, float c3z,
                                     float nx, float ny, float nz) {
        Vector3f p0 = new Vector3f(lx0, ly0, lz0).mulPosition(mat);
        Vector3f p1 = new Vector3f(lx1, ly1, lz1).mulPosition(mat);
        Vector3f p2 = new Vector3f(lx2, ly2, lz2).mulPosition(mat);
        Vector3f p3 = new Vector3f(lx3, ly3, lz3).mulPosition(mat);

        Vector3f normal = new Vector3f(nx, ny, nz).mulDirection(mat).normalize();

        Vector3f cd0 = new Vector3f(c0x, c0y, c0z).mulDirection(mat);
        Vector3f cd1 = new Vector3f(c1x, c1y, c1z).mulDirection(mat);
        Vector3f cd2 = new Vector3f(c2x, c2y, c2z).mulDirection(mat);
        Vector3f cd3 = new Vector3f(c3x, c3y, c3z).mulDirection(mat);

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

        int col0 = computeGoldColor(wx + p0.x, wy + p0.y, wz + p0.z, camPos, normal, cd0, lightDir, tintColor);
        int col1 = computeGoldColor(wx + p1.x, wy + p1.y, wz + p1.z, camPos, normal, cd1, lightDir, tintColor);
        int col2 = computeGoldColor(wx + p2.x, wy + p2.y, wz + p2.z, camPos, normal, cd2, lightDir, tintColor);
        int col3 = computeGoldColor(wx + p3.x, wy + p3.y, wz + p3.z, camPos, normal, cd3, lightDir, tintColor);

        buf.vertex(vx0, vy0, vz0).color((col0 >> 16) & 0xFF, (col0 >> 8) & 0xFF, col0 & 0xFF, alpha);
        buf.vertex(vx1, vy1, vz1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, alpha);
        buf.vertex(vx2, vy2, vz2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, alpha);
        buf.vertex(vx3, vy3, vz3).color((col3 >> 16) & 0xFF, (col3 >> 8) & 0xFF, col3 & 0xFF, alpha);
    }

    private static int computeGoldColor(double worldX, double worldY, double worldZ,
                                        Vec3d camPos, Vector3f faceNormal,
                                        Vector3f cornerDir,
                                        Vector3f lightDir, int tintColor) {
        float camDx = (float) (camPos.x - worldX);
        float camDy = (float) (camPos.y - worldY);
        float camDz = (float) (camPos.z - worldZ);
        float camDistSq = camDx * camDx + camDy * camDy + camDz * camDz;
        float invDist = camDistSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(camDistSq)) : 1.0f;
        float toCamX = camDx * invDist;
        float toCamY = camDy * invDist;
        float toCamZ = camDz * invDist;

        float nx = faceNormal.x;
        float ny = faceNormal.y;
        float nz = faceNormal.z;
        if (cornerDir != null) {
            float cLenSq = cornerDir.x * cornerDir.x + cornerDir.y * cornerDir.y + cornerDir.z * cornerDir.z;
            if (cLenSq > 1.0e-5f) {
                float invCLen = (float) (1.0 / Math.sqrt(cLenSq));
                nx += cornerDir.x * invCLen * 0.18f;
                ny += cornerDir.y * invCLen * 0.18f;
                nz += cornerDir.z * invCLen * 0.18f;
                float nLen = (float) (1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
                nx *= nLen;
                ny *= nLen;
                nz *= nLen;
            }
        }

        // Вектор отражения R = 2*(N.V)*N - V
        float NdotV = nx * toCamX + ny * toCamY + nz * toCamZ;
        float rx = 2.0f * NdotV * nx - toCamX;
        float ry = 2.0f * NdotV * ny - toCamY;
        float rz = 2.0f * NdotV * nz - toCamZ;
        float rLenSq = rx * rx + ry * ry + rz * rz;
        float invRLen = rLenSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(rLenSq)) : 1.0f;
        rx *= invRLen;
        ry *= invRLen;
        rz *= invRLen;

        // Диффузная составляющая (у металлов слабая, формирует теплое тело)
        float NdotL = Math.max(0.0f, nx * lightDir.x + ny * lightDir.y + nz * lightDir.z);
        float diff = 0.30f + 0.42f * NdotL;

        // Прямой зеркальный блик солнца (ослепительное полированное золото)
        float RdotL = Math.max(0.0f, rx * lightDir.x + ry * lightDir.y + rz * lightDir.z);
        float sharpSun = (float) Math.pow(RdotL, 28.0);
        float broadSun = (float) Math.pow(RdotL, 7.0) * 0.35f;

        // Отражение купола неба (верхнее полупространство дает зеркальный золотой глянец)
        float skyFactor = MathHelper.clamp((ry + 0.15f) / 1.15f, 0.0f, 1.0f);

        // Отражение земли (нижнее полупространство отражает теплый коньячно-бронзовый оттенок)
        float groundFactor = MathHelper.clamp(-ry, 0.0f, 1.0f);

        // Горизонтный металлический отблеск
        float horizonGlint = (float) Math.exp(-ry * ry * 16.0f) * 0.28f;

        // Френель проводника (на краях золото отражает почти 100% света)
        float fresnel = (float) Math.pow(1.0f - Math.max(0.0f, Math.abs(NdotV)), 2.6f);

        // Физический спектр чистого 24-каратного золота:
        // Базовые амбровые тени: (125, 75, 12)
        // Основной тон слитка 24K: (255, 215, 30)
        // Зеркальное отражение неба: (255, 238, 130)
        // Блик солнца: (255, 255, 220)
        // Ореол Френеля: (255, 245, 155)
        float r = 125.0f * (1.0f - diff) + 255.0f * diff;
        float g = 75.0f * (1.0f - diff) + 215.0f * diff;
        float b = 12.0f * (1.0f - diff) + 30.0f * diff;

        // Отражение окружения (небо)
        r += 255.0f * skyFactor * 0.45f;
        g += 238.0f * skyFactor * 0.45f;
        b += 130.0f * skyFactor * 0.45f;

        // Отражение земли
        r += 42.0f * groundFactor;
        g += 22.0f * groundFactor;
        b += 6.0f * groundFactor;

        // Полоса горизонта
        r += 135.0f * horizonGlint;
        g += 115.0f * horizonGlint;
        b += 48.0f * horizonGlint;

        // Металлический солнечный блик
        float totalSpec = sharpSun + broadSun;
        r += 255.0f * totalSpec;
        g += 255.0f * totalSpec;
        b += 220.0f * totalSpec;

        // Френелевское контурное свечение металла
        r += 255.0f * fresnel * 0.65f;
        g += 245.0f * fresnel * 0.65f;
        b += 155.0f * fresnel * 0.65f;

        if (tintColor != 0xFFFFD700 && tintColor != 0) {
            float tr = ((tintColor >> 16) & 0xFF) / 255.0f;
            float tg = ((tintColor >> 8) & 0xFF) / 215.0f;
            float tb = (tintColor & 0xFF) / 30.0f;
            r = r * 0.4f + r * tr * 0.6f;
            g = g * 0.4f + g * tg * 0.6f;
            b = b * 0.4f + b * tb * 0.6f;
        }

        int ir = MathHelper.clamp((int) r, 0, 255);
        int ig = MathHelper.clamp((int) g, 0, 255);
        int ib = MathHelper.clamp((int) b, 0, 255);

        return (ir << 16) | (ig << 8) | ib;
    }

    // =========================================================================
    // Отрисовка и физика Небесного Обелиска (Monolith Crush)
    // =========================================================================
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

            // 1. Звук падения со свистом воздуха
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

            // 2. Сейсмический удар о землю (Impact)
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
                // Окаменевшая фигура разбивается на 3D каменные осколки
                inst.spawnShatterDebris(random);
            }

            // 3. Вычисление текущей высоты и положения обелиска
            double curY;
            double shakeX = 0;
            double shakeZ = 0;

            if (age < 0.22f) {
                // До начала падения обелиск находится в портале наверху
                curY = inst.y + inst.portalHeight + 1.2 * inst.scale;
            } else if (age < 0.46f) {
                // Сверхзвуковое падение вертикально вниз с ускорением
                float dropProg = (age - 0.22f) / 0.24f;
                float easeDrop = dropProg * dropProg;
                curY = (inst.y + inst.portalHeight + 1.2 * inst.scale) * (1.0f - easeDrop) + inst.y * easeDrop;
            } else if (age < 1.15f) {
                // Обелиск вонзился в землю: затухающая сейсмическая вибрация
                curY = inst.y;
                float tremorTime = age - 0.46f;
                float tremorAmp = (float) (Math.exp(-tremorTime * 5.0) * 0.065 * inst.scale);
                shakeX = Math.sin(tremorTime * 48.0) * tremorAmp;
                shakeZ = Math.cos(tremorTime * 42.0) * tremorAmp;
            } else {
                // Обелиск медленно погружается в глубины земли
                float sinkProg = (age - 1.15f) / 1.85f;
                float easeSink = (float) Math.pow(sinkProg, 1.4);
                curY = inst.y - easeSink * (4.2 * inst.scale);
            }

            // 4. Отрисовка небесного рунического портала (вверху)
            if (age < 0.65f) {
                renderCelestialPortal(quadBuf, lightBuf, inst, camPos, age);
            }

            // 5. Отрисовка окаменевшей модельки врага (до момента удара на 0.46 сек)
            if (age < 0.46f) {
                renderPetrifiedModel(quadBuf, inst, camPos, lightDir);
            }

            // 6. Отрисовка сейсмической ударной волны и трещин в земле
            if (age >= 0.46f) {
                renderImpactShockwaveAndCracks(quadBuf, lightBuf, inst, camPos, age);
            }

            // 7. Отрисовка самого 3D обелиска из натурального камня
            if (curY + 4.5 * inst.scale > inst.y - 2.0) {
                renderMonolithModel(quadBuf, lightBuf, inst, camPos, lightDir,
                        inst.x + shakeX, curY, inst.z + shakeZ);
            }

            // 8. Обновление и отрисовка разлетающихся 3D каменных осколков
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

        // Торс
        float torsoBaseY = 0.70f;
        float torsoTopY = torsoBaseY + 0.75f;
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, torsoMat,
                -0.25f, torsoBaseY, -0.125f,
                0.25f, torsoTopY, 0.125f, lightDir, 255);

        // Голова
        Matrix4f headMat = new Matrix4f(bodyMat);
        headMat.translate(0, torsoTopY, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));

        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, headMat,
                -0.25f, 0, -0.25f,
                0.25f, 0.50f, 0.25f, lightDir, 255);

        // Руки
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

        // Ноги
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

        // 1. Внешнее магическое кольцо (16 сегментов)
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

        // 2. Внутреннее руническое кольцо (противоположное вращение)
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

        // 3. Вертикальный нисходящий луч призыва
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

        // 1. Быстро расширяющаяся сейсмическая ударная волна (до 0.55 сек после удара)
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

        // 2. Радиальные трещины в земле от точки падения (до 2.0 сек)
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

        // 1. Отрисовка 8 вертикальных граней тела обелиска (Shaft)
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

            // 2. Светящиеся выбитые руны на 4 главных широких гранях (i = 0, 2, 4, 6)
            if (monolithRunes.isEnabled() && (i % 2 == 0)) {
                renderMonolithFaceRunes(lightBuf, inst, camPos, mx, my, mz,
                        lx0, lz0, lx1, lz1, lx2, lz2, lx3, lz3, normal, hBody);
            }
        }

        // 3. Отрисовка 8 граней вершины (Пирамидион / Apex)
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

        // 4. Отрисовка нижнего клина (Wedge), вонзающегося в грунт
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

        // Центральная вертикальная ось рунического канала
        float ax0 = (float) (mx + bx + offX - camPos.x);
        float ay0 = (float) (my + 0.12f * hBody + offY - camPos.y);
        float az0 = (float) (mz + bz + offZ - camPos.z);

        float ax1 = (float) (mx + tx + offX - camPos.x);
        float ay1 = (float) (my + 0.88f * hBody + offY - camPos.y);
        float az1 = (float) (mz + tz + offZ - camPos.z);

        emitRuneLine(lightBuf, ax0, ay0, az0, ax1, ay1, az1, tangX, 0, tangZ, width, tr, tg, tb, runeAlpha);

        // Глиф 1: Верхний кристаллический ромб (Diamond Glyph, t ~ 0.72)
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

        // Глиф 2: Средний шевронный узел (Chevron Sigil, t ~ 0.49)
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

        // Глиф 3: Нижний солярный знак (Solar Glyph, t ~ 0.27)
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
        // Front (+Z)
        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        // Back (-Z)
        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        // Top (+Y)
        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);
        // Bottom (-Y)
        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0);
        // Left (-X)
        emitStoneFace(buf, camPos, wx, wy, wz, mat, lightDir, alpha,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        // Right (+X)
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
        // Вектор взгляда к камере
        float camDx = (float) (camPos.x - worldX);
        float camDy = (float) (camPos.y - worldY);
        float camDz = (float) (camPos.z - worldZ);
        float camDistSq = camDx * camDx + camDy * camDy + camDz * camDz;
        float invDist = camDistSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(camDistSq)) : 1.0f;
        float toCamX = camDx * invDist;
        float toCamY = camDy * invDist;
        float toCamZ = camDz * invDist;

        // Натуральные геологические слои породы и минеральные прожилки
        double slant = worldY * 4.2 + worldX * 1.7 - worldZ * 1.5;
        float strata1 = (float) Math.sin(slant);
        float strata2 = (float) Math.sin(slant * 2.4 + 1.3);
        float crossVein = (float) Math.cos(worldX * 5.0 + worldZ * 5.0 + worldY * 1.8);
        float microNoise = (float) Math.sin(worldX * 15.0 + worldY * 19.0 + worldZ * 15.0);

        float mineralFactor = MathHelper.clamp(
                (strata1 * 0.42f + strata2 * 0.32f + crossVein * 0.16f + microNoise * 0.10f + 1.0f) * 0.5f,
                0.0f, 1.0f
        );

        // Палитра натурального базальта, сланца и гранитных прожилок
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

        // Освещение шероховатого камня
        float NdotL = Math.max(0.0f, normal.x * lightDir.x + normal.y * lightDir.y + normal.z * lightDir.z);
        float diff = 0.32f + 0.68f * NdotL;

        // Вектор полупути для матового минерального блика
        float halfX = lightDir.x + toCamX;
        float halfY = lightDir.y + toCamY;
        float halfZ = lightDir.z + toCamZ;
        float hLenSq = halfX * halfX + halfY * halfY + halfZ * halfZ;
        float hLen = hLenSq > 1.0e-6f ? (float) (1.0 / Math.sqrt(hLenSq)) : 1.0f;
        halfX *= hLen; halfY *= hLen; halfZ *= hLen;

        float NdotH = Math.max(0.0f, normal.x * halfX + normal.y * halfY + normal.z * halfZ);
        float stoneSpec = (float) Math.pow(NdotH, 12.0) * 0.28f;

        // Френель минерала
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

    // =========================================================================
    // Отрисовка и симуляция Чернил Суми-э
    // =========================================================================
    private void renderInkSlashInstances(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                        Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<InkSlashInstance> it = inkSlashInstances.iterator();
        while (it.hasNext()) {
            InkSlashInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.duration) {
                it.remove();
                continue;
            }

            if (inst.age >= 0.12f && !inst.soundPlayedImpact) {
                inst.soundPlayedImpact = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y + 1.1, inst.z,
                            SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS,
                            1.2f, 0.75f, false);
                    mc.world.playSoundClient(inst.x, inst.y + 1.1, inst.z,
                            SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS,
                            1.3f, 0.85f, false);
                }
                inst.spawnInkSplatter(random);
            }

            render1to1PlayerModel(inst, camPos);
            renderSeveredWoundSurfaces(quadBuf, lightBuf, inst, camPos);
            renderCalligraphicBrushSlash(quadBuf, lightBuf, inst, camPos);
            inst.updateAndRenderInkParticles(quadBuf, lightBuf, camPos, dt);
        }
    }

    private void render1to1PlayerModel(InkSlashInstance inst, Vec3d camPos) {
        float age = inst.age;
        float fade = 1.0f;
        float dissolve = 0.0f;
        if (age > 0.65f) {
            dissolve = MathHelper.clamp((age - 0.65f) / 1.15f, 0.0f, 1.0f);
            fade = 1.0f - dissolve;
        }
        if (fade <= 0.01f) return;

        int baseR = (int) MathHelper.lerp(dissolve, 255.0f, 25.0f);
        int baseG = (int) MathHelper.lerp(dissolve, 255.0f, 15.0f);
        int baseB = (int) MathHelper.lerp(dissolve, 255.0f, 20.0f);
        int alpha = (int) (255 * fade);

        float slideProgress = MathHelper.clamp((age - 0.12f) / 0.80f, 0.0f, 1.0f);
        float slideEase = (float) Math.pow(slideProgress, 1.6);
        float upperSlideX = slideEase * 0.42f * inst.scale;
        float upperSlideY = -slideEase * 0.28f * inst.scale;
        float upperSlideZ = slideEase * 0.12f * inst.scale;
        float upperTilt = slideEase * 18.0f;
        float lowerDropY = -slideEase * 0.08f * inst.scale;

        try (BufferAllocator alloc = new BufferAllocator(16384)) {
            BufferBuilder buf = new BufferBuilder(alloc, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            Matrix4f upperMat = new Matrix4f()
                    .translate(upperSlideX, upperSlideY, upperSlideZ)
                    .rotateZ((float) Math.toRadians(-upperTilt))
                    .rotateY((float) Math.toRadians(-inst.yaw));

            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, upperMat,
                    -0.25f, 1.42f, -0.25f, 0.25f, 1.92f, 0.25f,
                    0, 0, 8, 8, 8, baseR, baseG, baseB, alpha);

            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, upperMat,
                    -0.25f, 1.05f, -0.125f, 0.25f, 1.42f, 0.125f,
                    16, 16, 8, 6, 4, baseR, baseG, baseB, alpha);

            float armW = inst.isSlim ? 0.1875f : 0.25f;
            float rArmMinX = -0.25f - armW;
            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, upperMat,
                    rArmMinX, 0.70f, -0.125f, -0.25f, 1.42f, 0.125f,
                    40, 16, 4, 12, 4, baseR, baseG, baseB, alpha);

            float lArmMaxX = 0.25f + armW;
            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, upperMat,
                    0.25f, 0.70f, -0.125f, lArmMaxX, 1.42f, 0.125f,
                    32, 48, 4, 12, 4, baseR, baseG, baseB, alpha);

            Matrix4f lowerMat = new Matrix4f()
                    .translate(0, lowerDropY, 0)
                    .rotateX((float) Math.toRadians(slideEase * 4.0f))
                    .rotateY((float) Math.toRadians(-inst.yaw));

            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, lowerMat,
                    -0.25f, 0.70f, -0.125f, 0.25f, 1.05f, 0.125f,
                    16, 22, 8, 6, 4, baseR, baseG, baseB, alpha);

            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, lowerMat,
                    -0.25f, 0.00f, -0.125f, 0.00f, 0.70f, 0.125f,
                    0, 16, 4, 12, 4, baseR, baseG, baseB, alpha);

            emitStandardBox(buf, camPos, inst.x, inst.y, inst.z, lowerMat,
                    0.00f, 0.00f, -0.125f, 0.25f, 0.70f, 0.125f,
                    16, 48, 4, 12, 4, baseR, baseG, baseB, alpha);

            BuiltBuffer built = buf.endNullable();
            if (built != null) {
                MeshDrawUtil.drawLabeledTextured(Pipelines.AURA_TEXTURE_DEPTH, built, "egg sumie victim", inst.skinTexture);
            }
        }
    }

    private void renderSeveredWoundSurfaces(VertexConsumer quadBuf, VertexConsumer lightBuf, InkSlashInstance inst, Vec3d camPos) {
        if (inst.age < 0.12f) return;
        float fade = 1.0f;
        if (inst.age > 0.65f) {
            fade = MathHelper.clamp(1.0f - (inst.age - 0.65f) / 1.15f, 0.0f, 1.0f);
        }
        if (fade <= 0.01f) return;

        float slideProgress = MathHelper.clamp((inst.age - 0.12f) / 0.80f, 0.0f, 1.0f);
        float slideEase = (float) Math.pow(slideProgress, 1.6);
        float upperSlideX = slideEase * 0.42f * inst.scale;
        float upperSlideY = -slideEase * 0.28f * inst.scale;
        float upperSlideZ = slideEase * 0.12f * inst.scale;
        float upperTilt = slideEase * 18.0f;

        int tint = inst.tintColor;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;
        int alpha = (int) (255 * fade);

        Matrix4f upperMat = new Matrix4f()
                .translate(upperSlideX, upperSlideY, upperSlideZ)
                .rotateZ((float) Math.toRadians(-upperTilt))
                .rotateY((float) Math.toRadians(-inst.yaw));

        emitCutCapQuad(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, upperMat,
                -0.25f, 1.05f, -0.125f, 0.25f, 1.05f, 0.125f,
                tr, tg, tb, alpha);

        Matrix4f lowerMat = new Matrix4f()
                .translate(0, -slideEase * 0.08f * inst.scale, 0)
                .rotateX((float) Math.toRadians(slideEase * 4.0f))
                .rotateY((float) Math.toRadians(-inst.yaw));

        emitCutCapQuad(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, lowerMat,
                -0.25f, 1.05f, -0.125f, 0.25f, 1.05f, 0.125f,
                tr, tg, tb, alpha);
    }

    private void emitCutCapQuad(VertexConsumer quadBuf, VertexConsumer lightBuf, Vec3d camPos,
                                double wx, double wy, double wz, Matrix4f mat,
                                float x0, float y, float z0, float x1, float dummy, float z1,
                                int tr, int tg, int tb, int a) {
        Vector3f p0 = new Vector3f(x0, y, z0).mulPosition(mat);
        Vector3f p1 = new Vector3f(x1, y, z0).mulPosition(mat);
        Vector3f p2 = new Vector3f(x1, y, z1).mulPosition(mat);
        Vector3f p3 = new Vector3f(x0, y, z1).mulPosition(mat);

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

        quadBuf.vertex(vx0, vy0, vz0).color(12, 4, 8, a);
        quadBuf.vertex(vx1, vy1, vz1).color(12, 4, 8, a);
        quadBuf.vertex(vx2, vy2, vz2).color(12, 4, 8, a);
        quadBuf.vertex(vx3, vy3, vz3).color(12, 4, 8, a);

        lightBuf.vertex(vx0, vy0 + 0.005f, vz0).color(tr, tg, tb, a);
        lightBuf.vertex(vx1, vy1 + 0.005f, vz1).color(tr, tg, tb, a);
        lightBuf.vertex(vx2, vy2 + 0.005f, vz2).color(tr, tg, tb, a);
        lightBuf.vertex(vx3, vy3 + 0.005f, vz3).color(tr, tg, tb, a);
    }

    private void renderCalligraphicBrushSlash(VertexConsumer quadBuf, VertexConsumer lightBuf, InkSlashInstance inst, Vec3d camPos) {
        float age = inst.age;
        if (age > 0.90f) return;

        float anim = MathHelper.clamp(age / 0.15f, 0.0f, 1.0f);
        float fade = 1.0f;
        if (age > 0.35f) {
            fade = MathHelper.clamp(1.0f - (age - 0.35f) / 0.55f, 0.0f, 1.0f);
        }
        if (fade <= 0.01f) return;

        int segs = 20;
        float totalLen = 2.8f * inst.scale;
        float maxU = anim;

        int tint = inst.tintColor;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;

        float slashAngleRad = (float) Math.toRadians(-inst.yaw);
        float cosYaw = (float) Math.cos(slashAngleRad);
        float sinYaw = (float) Math.sin(slashAngleRad);

        Vector3f prevCoreL = null;
        Vector3f prevCoreR = null;
        Vector3f prevGlintL = null;
        Vector3f prevGlintR = null;

        int count = Math.max(2, (int) (segs * maxU));

        for (int i = 0; i <= count; i++) {
            float u = (i / (float) segs) * 2.0f - 1.0f;
            float s = u * (totalLen * 0.5f);

            float lx = s;
            float ly = 1.05f * inst.scale - s * 0.32f + (1.0f - u * u) * 0.14f * inst.scale;
            float lz = (float) Math.sin(u * 2.5) * 0.18f * inst.scale;

            float rx = lx * cosYaw - lz * sinYaw;
            float rz = lx * sinYaw + lz * cosYaw;

            float wx = (float) (inst.x + rx - camPos.x);
            float wy = (float) (inst.y + ly - camPos.y);
            float wz = (float) (inst.z + rz - camPos.z);

            float brushW = (float) (0.22f * Math.pow(Math.max(0.0, 1.0 - u * u), 0.65) * inst.scale);
            float glintW = brushW * 0.18f;

            Vector3f coreL = new Vector3f(wx, wy + brushW, wz);
            Vector3f coreR = new Vector3f(wx, wy - brushW, wz);

            Vector3f glintL = new Vector3f(wx, wy + glintW, wz);
            Vector3f glintR = new Vector3f(wx, wy - glintW, wz);

            if (prevCoreL != null) {
                int inkAlpha = (int) (240 * fade);

                quadBuf.vertex(prevCoreL.x, prevCoreL.y, prevCoreL.z).color(10, 4, 8, inkAlpha);
                quadBuf.vertex(coreL.x, coreL.y, coreL.z).color(10, 4, 8, inkAlpha);
                quadBuf.vertex(coreR.x, coreR.y, coreR.z).color(10, 4, 8, inkAlpha);
                quadBuf.vertex(prevCoreR.x, prevCoreR.y, prevCoreR.z).color(10, 4, 8, inkAlpha);

                int bladeAlpha = (int) (255 * fade);
                lightBuf.vertex(prevGlintL.x, prevGlintL.y, prevGlintL.z).color(tr, tg, tb, bladeAlpha);
                lightBuf.vertex(glintL.x, glintL.y, glintL.z).color(tr, tg, tb, bladeAlpha);
                lightBuf.vertex(glintR.x, glintR.y, glintR.z).color(255, 240, 245, bladeAlpha);
                lightBuf.vertex(prevGlintR.x, prevGlintR.y, prevGlintR.z).color(255, 240, 245, bladeAlpha);
            }

            prevCoreL = coreL;
            prevCoreR = coreR;
            prevGlintL = glintL;
            prevGlintR = glintR;
        }

        if (age < 0.25f) {
            float flashAlpha = (1.0f - age / 0.25f);
            float fx = (float) (inst.x - camPos.x);
            float fy = (float) (inst.y + 1.05f * inst.scale - camPos.y);
            float fz = (float) (inst.z - camPos.z);
            float fs = 0.35f * inst.scale;

            lightBuf.vertex(fx - fs, fy, fz).color(255, 255, 255, (int) (255 * flashAlpha));
            lightBuf.vertex(fx + fs, fy, fz).color(255, 255, 255, (int) (255 * flashAlpha));
            lightBuf.vertex(fx, fy + fs * 1.5f, fz).color(tr, tg, tb, (int) (255 * flashAlpha));
            lightBuf.vertex(fx, fy - fs * 1.5f, fz).color(tr, tg, tb, (int) (255 * flashAlpha));
        }
    }

    private static void emitStandardBox(BufferBuilder buf, Vec3d camPos,
                                        double wx, double wy, double wz, Matrix4f mat,
                                        float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                        int u, int v, int w, int h, int d,
                                        int cr, int cg, int cb, int ca) {
        float f = 64.0f;
        float uRight0 = u / f;
        float uRight1 = (u + d) / f;
        float uFront0 = (u + d) / f;
        float uFront1 = (u + d + w) / f;
        float uLeft0  = (u + d + w) / f;
        float uLeft1  = (u + d + w + d) / f;
        float uBack0  = (u + d + w + d) / f;
        float uBack1  = (u + d + w + d + w) / f;

        float vSide0 = (v + d) / f;
        float vSide1 = (v + d + h) / f;

        float uTop0 = (u + d) / f;
        float uTop1 = (u + d + w) / f;
        float vTop0 = v / f;
        float vTop1 = (v + d) / f;

        float uBot0 = (u + d + w) / f;
        float uBot1 = (u + d + w + w) / f;

        // Front (+Z)
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, maxZ, uFront0, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, maxZ, uFront1, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, maxZ, uFront1, vSide0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, maxZ, uFront0, vSide0, cr, cg, cb, ca);

        // Back (-Z)
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, minZ, uBack0, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, minZ, uBack1, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, minZ, uBack1, vSide0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, minZ, uBack0, vSide0, cr, cg, cb, ca);

        // Top (+Y)
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, minZ, uTop0, vTop0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, maxZ, uTop0, vTop1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, maxZ, uTop1, vTop1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, minZ, uTop1, vTop0, cr, cg, cb, ca);

        // Bottom (-Y)
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, maxZ, uBot0, vTop0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, minZ, uBot0, vTop1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, minZ, uBot1, vTop1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, maxZ, uBot1, vTop0, cr, cg, cb, ca);

        // Left (+X)
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, maxZ, uLeft0, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, minY, minZ, uLeft1, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, minZ, uLeft1, vSide0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, maxX, maxY, maxZ, uLeft0, vSide0, cr, cg, cb, ca);

        // Right (-X)
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, minZ, uRight0, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, minY, maxZ, uRight1, vSide1, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, maxZ, uRight1, vSide0, cr, cg, cb, ca);
        emitVertex(buf, camPos, wx, wy, wz, mat, minX, maxY, minZ, uRight0, vSide0, cr, cg, cb, ca);
    }

    private static void emitVertex(BufferBuilder buf, Vec3d camPos,
                                   double wx, double wy, double wz, Matrix4f mat,
                                   float lx, float ly, float lz, float u, float v,
                                   int r, int g, int b, int a) {
        Vector3f p = new Vector3f(lx, ly, lz).mulPosition(mat);
        float x = (float) (wx + p.x - camPos.x);
        float y = (float) (wy + p.y - camPos.y);
        float z = (float) (wz + p.z - camPos.z);
        buf.vertex(x, y, z).texture(u, v).color(r, g, b, a);
    }

    // =========================================================================
    // Отрисовка и обновление Осколков стекла
    // =========================================================================
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
        statueInstances.clear();
        inkSlashInstances.clear();
        particles.clear();
        recentAttacks.clear();
        deadEntities.clear();
    }

    // =========================================================================
    // Вспомогательные классы
    // =========================================================================

    private static class GoldenStatueInstance {
        double x, y, z;
        final float yaw;
        final float pitch;
        final float headYaw;
        final boolean isSneaking;

        final float rLegPitch;
        final float lLegPitch;
        final float rArmPitch;
        final float lArmPitch;
        final float rArmRoll;

        double slideVx;
        double slideVz;
        double vy;
        boolean onGround;
        final int tintColor;
        final float maxLife;

        double puddleX;
        double puddleY;
        double puddleZ;

        float age = 0.0f;
        boolean soundPlayedMelt = false;

        final List<GoldDropParticle> drops = new ArrayList<>();

        GoldenStatueInstance(double x, double y, double z,
                             float yaw, float pitch, float headYaw, boolean isSneaking,
                             float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll,
                             double slideVx, double slideVz, double vy, boolean onGround,
                             int tintColor, float maxLife) {
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
            this.slideVx = slideVx;
            this.slideVz = slideVz;
            this.vy = vy;
            this.onGround = onGround;
            this.tintColor = tintColor;
            this.maxLife = maxLife;
            this.puddleX = x;
            this.puddleY = y;
            this.puddleZ = z;
        }

        void spawnMeltingDroplet(Random rnd) {
            double dx = (rnd.nextDouble() - 0.5) * 0.45;
            double dy = rnd.nextDouble() * 0.85;
            double dz = (rnd.nextDouble() - 0.5) * 0.45;
            drops.add(new GoldDropParticle(
                    x + dx, y + dy, z + dz,
                    (rnd.nextDouble() - 0.5) * 0.6,
                    -rnd.nextDouble() * 1.8,
                    (rnd.nextDouble() - 0.5) * 0.6,
                    0.035f + rnd.nextFloat() * 0.035f,
                    0.75f + rnd.nextFloat() * 0.4f,
                    tintColor
            ));
        }

        void updateAndRenderDroplets(VertexConsumer quadBuf, VertexConsumer lightBuf, Vec3d camPos, float dt) {
            Iterator<GoldDropParticle> it = drops.iterator();
            while (it.hasNext()) {
                GoldDropParticle d = it.next();
                d.age += dt;
                if (d.age >= d.life) {
                    it.remove();
                    continue;
                }
                d.vy -= 16.0 * dt;
                double nextX = d.x + d.vx * dt;
                double nextY = d.y + d.vy * dt;
                double nextZ = d.z + d.vz * dt;

                if (nextY <= y + 0.015) {
                    nextY = y + 0.015;
                    d.vy = 0.0;
                    d.vx *= 0.35;
                    d.vz *= 0.35;
                }

                d.x = nextX;
                d.y = nextY;
                d.z = nextZ;

                float alpha = MathHelper.clamp((1.0f - d.age / d.life) * 1.8f, 0.0f, 1.0f);
                if (alpha <= 0.01f) continue;

                float px = (float) (d.x - camPos.x);
                float py = (float) (d.y - camPos.y);
                float pz = (float) (d.z - camPos.z);
                float s = d.size;

                quadBuf.vertex(px - s, py - s, pz).color(255, 235, 120, (int) (240 * alpha));
                quadBuf.vertex(px + s, py - s, pz).color(255, 235, 120, (int) (240 * alpha));
                quadBuf.vertex(px + s, py + s, pz).color(255, 255, 210, (int) (255 * alpha));
                quadBuf.vertex(px - s, py + s, pz).color(255, 255, 210, (int) (255 * alpha));

                float gs = s * 1.8f;
                lightBuf.vertex(px - gs, py - gs, pz).color(255, 200, 30, (int) (120 * alpha));
                lightBuf.vertex(px + gs, py - gs, pz).color(255, 200, 30, (int) (120 * alpha));
                lightBuf.vertex(px + gs, py + gs, pz).color(255, 245, 140, (int) (160 * alpha));
                lightBuf.vertex(px - gs, py + gs, pz).color(255, 245, 140, (int) (160 * alpha));
            }
        }
    }

    private static class GoldDropParticle {
        double x, y, z;
        double vx, vy, vz;
        final float size;
        final float life;
        final int color;
        float age = 0.0f;

        GoldDropParticle(double x, double y, double z, double vx, double vy, double vz,
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
    }

    private static class InkSlashInstance {
        final double x, y, z;
        final float yaw;
        final float pitch;
        final float headYaw;
        final Identifier skinTexture;
        final boolean isSlim;
        final int tintColor;
        final float scale;
        final int dropCount;
        final float duration = 2.0f;
        float age = 0.0f;
        boolean soundPlayedImpact = false;

        final List<InkDropParticle> drops = new ArrayList<>();
        final List<InkSmokeParticle> smoke = new ArrayList<>();

        InkSlashInstance(double x, double y, double z, float yaw, float pitch, float headYaw,
                         Identifier skinTexture, boolean isSlim, int tintColor, float scale, int dropCount) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.headYaw = headYaw;
            this.skinTexture = skinTexture;
            this.isSlim = isSlim;
            this.tintColor = tintColor;
            this.scale = scale;
            this.dropCount = dropCount;
        }

        void spawnInkSplatter(Random rnd) {
            float slashAngleRad = (float) Math.toRadians(-yaw);
            float cosYaw = (float) Math.cos(slashAngleRad);
            float sinYaw = (float) Math.sin(slashAngleRad);

            for (int i = 0; i < dropCount; i++) {
                double s = (rnd.nextDouble() - 0.5) * 0.6 * scale;
                double spdX = (2.2 + rnd.nextDouble() * 3.8) * scale;
                double spdY = (0.6 + rnd.nextDouble() * 2.8) * scale;
                double spdZ = (rnd.nextDouble() - 0.5) * 2.5 * scale;

                double rvx = spdX * cosYaw - spdZ * sinYaw;
                double rvz = spdX * sinYaw + spdZ * cosYaw;

                boolean isAccent = rnd.nextFloat() < 0.28f;
                int dropCol = isAccent ? tintColor : 0xFF0D060C;

                drops.add(new InkDropParticle(
                        x + s * cosYaw,
                        y + 1.05 * scale + (rnd.nextDouble() - 0.5) * 0.15,
                        z + s * sinYaw,
                        rvx, spdY, rvz,
                        (0.045f + rnd.nextFloat() * 0.05f) * scale,
                        1.2f + rnd.nextFloat() * 0.6f,
                        dropCol
                ));
            }

            for (int i = 0; i < 20; i++) {
                smoke.add(new InkSmokeParticle(
                        x + (rnd.nextDouble() - 0.5) * 0.4 * scale,
                        y + 1.05 * scale + (rnd.nextDouble() - 0.5) * 0.2,
                        z + (rnd.nextDouble() - 0.5) * 0.4 * scale,
                        (rnd.nextDouble() - 0.5) * 0.4 * scale,
                        (0.3 + rnd.nextDouble() * 0.8) * scale,
                        (rnd.nextDouble() - 0.5) * 0.4 * scale,
                        (0.12f + rnd.nextFloat() * 0.14f) * scale,
                        1.4f + rnd.nextFloat() * 0.5f,
                        tintColor
                ));
            }
        }

        void updateAndRenderInkParticles(VertexConsumer quadBuf, VertexConsumer lightBuf, Vec3d camPos, float dt) {
            Iterator<InkDropParticle> itD = drops.iterator();
            while (itD.hasNext()) {
                InkDropParticle d = itD.next();
                d.age += dt;
                if (d.age >= d.life) {
                    itD.remove();
                    continue;
                }
                d.vy -= 9.2f * dt;
                float drag = (float) Math.exp(-1.8f * dt);
                d.vx *= drag;
                d.vy *= drag;
                d.vz *= drag;

                d.x += d.vx * dt;
                d.y += d.vy * dt;
                d.z += d.vz * dt;

                float alpha = MathHelper.clamp((1.0f - d.age / d.life) * 1.8f, 0.0f, 1.0f);
                if (alpha <= 0.01f) continue;

                d.render(quadBuf, camPos, alpha);
            }

            Iterator<InkSmokeParticle> itS = smoke.iterator();
            while (itS.hasNext()) {
                InkSmokeParticle s = itS.next();
                s.age += dt;
                if (s.age >= s.life) {
                    itS.remove();
                    continue;
                }
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                s.z += s.vz * dt;
                s.size += 0.08f * dt;

                float alpha = MathHelper.clamp((1.0f - s.age / s.life) * 1.4f, 0.0f, 1.0f);
                if (alpha <= 0.01f) continue;

                s.render(quadBuf, lightBuf, camPos, alpha);
            }
        }
    }

    private static class InkDropParticle {
        double x, y, z;
        double vx, vy, vz;
        final float size;
        final float life;
        final int color;
        float age = 0.0f;

        InkDropParticle(double x, double y, double z, double vx, double vy, double vz,
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

        void render(VertexConsumer buf, Vec3d camPos, float alpha) {
            float px = (float) (x - camPos.x);
            float py = (float) (y - camPos.y);
            float pz = (float) (z - camPos.z);

            float stretch = (float) Math.min(2.5, 1.0 + Math.sqrt(vx * vx + vy * vy + vz * vz) * 0.18);
            float sx = size;
            float sy = size * stretch;

            int cr = (color >> 16) & 0xFF;
            int cg = (color >> 8) & 0xFF;
            int cb = color & 0xFF;
            int ca = (int) (240 * alpha);

            buf.vertex(px - sx, py - sy, pz).color(cr, cg, cb, ca);
            buf.vertex(px + sx, py - sy, pz).color(cr, cg, cb, ca);
            buf.vertex(px + sx, py + sy, pz).color(cr, cg, cb, ca);
            buf.vertex(px - sx, py + sy, pz).color(cr, cg, cb, ca);
        }
    }

    private static class InkSmokeParticle {
        double x, y, z;
        double vx, vy, vz;
        float size;
        final float life;
        final int tintColor;
        float age = 0.0f;

        InkSmokeParticle(double x, double y, double z, double vx, double vy, double vz,
                         float size, float life, int tintColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.tintColor = tintColor;
        }

        void render(VertexConsumer quadBuf, VertexConsumer lightBuf, Vec3d camPos, float alpha) {
            float px = (float) (x - camPos.x);
            float py = (float) (y - camPos.y);
            float pz = (float) (z - camPos.z);
            float s = size;

            int ca = (int) (160 * alpha);
            quadBuf.vertex(px - s, py - s, pz).color(14, 5, 10, ca);
            quadBuf.vertex(px + s, py - s, pz).color(14, 5, 10, ca);
            quadBuf.vertex(px + s, py + s, pz).color(14, 5, 10, ca);
            quadBuf.vertex(px - s, py + s, pz).color(14, 5, 10, ca);
        }
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
}
