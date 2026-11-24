package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.world.GroundFormRenderer;
import egg.client.render.world.IceFreezeKillRenderer;
import egg.client.ui.theme.Theme;
import egg.client.util.EntityUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import it.unimi.dsi.fastutil.ints.IntList;

public class KillEffect extends Module {

    private static KillEffect instance;
    private EntityRenderState renderingAscension;

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

    private static final String ASCENSION = "Вознесение";
    private static final String GROUND_FORM = "Врос в мир";
    private static final String ICE1 = "Лед1";
    private static final long ASCENSION_DURATION = 1_650_000_000L;

    private final ModeSetting mode = new ModeSetting("Эффект", "Бабочки", "Тип эффекта при смерти противника", "Бабочки", "Стекло", ASCENSION, GROUND_FORM, ICE1);
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

    private final SliderSetting amount = new SliderSetting("Осколки стекла", 85, 20, 250, 5, "Количество 3D осколков стекла");
    private final SliderSetting size = new SliderSetting("Размер стекла", 0.22, 0.05, 0.60, 0.01, "Размер осколков стекла");
    private final SliderSetting speed = new SliderSetting("Скорость стекла", 6.5, 2.0, 15.0, 0.5, "Скорость разлета осколков во все стороны");
    private final SliderSetting lifetime = new SliderSetting("Жизнь стекла", 2.0, 0.5, 5.0, 0.1, "Длительность существования осколков (сек)");
    private final BooleanSetting bounce = new BooleanSetting("Отскок стекла", true, "Отскок осколков стекла от блоков и пола");
    private final BooleanSetting sparkle = new BooleanSetting("Блики стекла", true, "Яркие вспышки и отблески на гранях стекла");

    private final List<Ascension> ascensions = new ArrayList<>();
    private final Map<Entity, Long> hiddenOriginals = new IdentityHashMap<>();
    private final GroundFormRenderer groundForm = new GroundFormRenderer();
    private final IceFreezeKillRenderer iceFreeze = new IceFreezeKillRenderer();
    private final List<ButterflySwarmInstance> butterflySwarms = new ArrayList<>();
    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Map<Integer, TrackedVictim> victimsById = new ConcurrentHashMap<>();

    private final Map<String, TrackedVictim> victimsByName = new ConcurrentHashMap<>();
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

        amount.setVisible(() -> "Стекло".equals(mode.get()));
        size.setVisible(() -> "Стекло".equals(mode.get()));
        speed.setVisible(() -> "Стекло".equals(mode.get()));
        lifetime.setVisible(() -> "Стекло".equals(mode.get()));
        bounce.setVisible(() -> "Стекло".equals(mode.get()));
        sparkle.setVisible(() -> "Стекло".equals(mode.get()));

        syncColor.setVisible(() -> !mode.is(GROUND_FORM) && !mode.is(ICE1));
        color.setVisible(() -> !mode.is(GROUND_FORM) && !mode.is(ICE1) && !syncColor.isEnabled());

        addSettings(mode, onlyTarget, onlyPlayers, sound, syncColor, color,
                butterflyAmount, butterflySize, butterflySpeed, butterflyLifetime, butterflyTrails, butterflyRainbow,
                amount, size, speed, lifetime, bounce, sparkle);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    private static final class TrackedVictim {
        private final int id;
        private final String name;
        private Vec3d lastPos;
        private double height;
        private double width;
        private long lastAttackTime;
        private EntityRenderState ascensionModel;
        private GroundFormRenderer.Shape groundShape;

        private TrackedVictim(Entity entity) {
            id = entity.getId();
            name = entity.getName().getString();
            update(entity);
        }

        private void update(Entity entity) {
            lastPos = entity.getEntityPos();
            height = entity.getHeight();
            width = entity.getWidth();
        }
    }

    public void onAttack(Entity target) {
        if (!isEnabled() || target == null || EntityUtil.isNakedInvisible(target)) return;
        long now = System.currentTimeMillis();
        recentAttacks.put(target.getId(), now);

        TrackedVictim v = victimsById.computeIfAbsent(target.getId(), id -> new TrackedVictim(target));
        v.lastAttackTime = now;
        v.update(target);
        if (ASCENSION.equals(mode.get()) || mode.is(ICE1)) v.ascensionModel = captureAscensionModel(target);
        if (mode.is(GROUND_FORM)) v.groundShape = groundForm.capture(target);
        if (v.name != null && !v.name.isEmpty()) {
            victimsByName.put(v.name.toLowerCase(), v);
        }
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
            victimsById.remove(entityId);
            String curMode = mode.get();
            if ("Бабочки".equals(curMode)) {
                spawnButterflySwarm(entity);
            } else if (ASCENSION.equals(curMode)) {
                EntityRenderState model = captureAscensionModel(entity);
                hiddenOriginals.put(entity, System.nanoTime());
                spawnAscension(entity.getX(), entity.getY(), entity.getZ(), model);
            } else if (GROUND_FORM.equals(curMode)) {
                spawnGroundForm(entity, entity.getEntityPos(), groundForm.capture(entity));
            } else if (ICE1.equals(curMode)) {
                EntityRenderState model = captureAscensionModel(entity);
                spawnIce1(entity, entity.getEntityPos(), entity.getHeight(), entity.getWidth(), model);
            } else {
                spawnGlassBurst(entity);
            }
        }
    }

    public void onEntitiesDestroy(IntList entityIds) {
        if (!isEnabled() || entityIds == null || entityIds.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        for (int i = 0; i < entityIds.size(); i++) {
            int id = entityIds.getInt(i);
            if (id == mc.player.getId()) continue;

            Entity entity = mc.world.getEntityById(id);
            TrackedVictim v = victimsById.get(id);

            if (onlyTarget.isEnabled()) {
                if (v == null || (now - v.lastAttackTime) > 5500L) {
                    continue;
                }
            } else if (entity != null && onlyPlayers.isEnabled() && !(entity instanceof PlayerEntity)) {
                continue;
            }

            Vec3d deathPos = null;
            double height = 1.8;
            double width = 0.6;

            if (entity != null) {
                deathPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                height = entity.getHeight();
                width = entity.getWidth();
            } else if (v != null) {
                deathPos = v.lastPos;
                height = v.height;
                width = v.width;
            }

            if (deathPos != null) {
                double distSq = deathPos.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (distSq <= 256.0) {
                    if (deadEntities.add(id)) {
                        EntityRenderState model = (ASCENSION.equals(mode.get()) || mode.is(ICE1)) && entity != null
                                ? captureAscensionModel(entity) : v != null ? v.ascensionModel : null;
                        GroundFormRenderer.Shape shape = mode.is(GROUND_FORM) && entity != null
                                ? groundForm.capture(entity) : v != null ? v.groundShape : null;
                        triggerEffectAt(deathPos.x, deathPos.y, deathPos.z, height, width, model, shape);
                        recentAttacks.remove(id);
                        victimsById.remove(id);
                    }
                }
            }
        }
    }

    public void onChatMessage(String rawText) {
        if (!isEnabled() || rawText == null || rawText.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String lower = rawText.toLowerCase();
        boolean hasKillKeyword = lower.contains("убил")
                || lower.contains("убита")
                || lower.contains("убит")
                || lower.contains("погиб")
                || lower.contains("казнен")
                || lower.contains("зарубил")
                || lower.contains("пал")
                || lower.contains("смерть")
                || lower.contains("killed")
                || lower.contains("slain")
                || lower.contains("shot")
                || lower.contains("eliminated")
                || lower.contains("was killed by");

        if (!hasKillKeyword) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<String, TrackedVictim> entry : victimsByName.entrySet()) {
            String victimName = entry.getKey();
            if (victimName.length() >= 3 && lower.contains(victimName)) {
                TrackedVictim v = entry.getValue();
                if ((now - v.lastAttackTime) <= 8000L) {
                    if (deadEntities.add(v.id)) {
                        if (v.lastPos != null && v.lastPos.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()) <= 576.0) {
                            Entity victim = mc.world == null ? null : mc.world.getEntityById(v.id);
                            if (mode.is(GROUND_FORM)) {
                                GroundFormRenderer.Shape shape = v.groundShape;
                                if (shape == null && victim != null) shape = groundForm.capture(victim);
                                spawnGroundForm(victim, v.lastPos, shape);
                            } else if (mode.is(ICE1)) {
                                EntityRenderState model = v.ascensionModel;
                                if (model == null && victim != null) model = captureAscensionModel(victim);
                                spawnIce1(victim, v.lastPos, v.height, v.width, model);
                            } else {
                                triggerEffectAt(v.lastPos.x, v.lastPos.y, v.lastPos.z, v.height, v.width, v.ascensionModel, null);
                            }
                        }
                        recentAttacks.remove(v.id);
                        victimsById.remove(v.id);
                        break;
                    }
                }
            }
        }
    }

    private void triggerEffectAt(double bx, double by, double bz, double height, double width,
                                 EntityRenderState model, GroundFormRenderer.Shape shape) {
        String curMode = mode.get();
        if ("Бабочки".equals(curMode)) {
            spawnButterflySwarmAt(bx, by, bz, height);
        } else if (ASCENSION.equals(curMode)) {
            spawnAscension(bx, by, bz, model);
        } else if (GROUND_FORM.equals(curMode)) {
            spawnGroundForm(null, new Vec3d(bx, by, bz), shape);
        } else if (ICE1.equals(curMode)) {
            spawnIce1(null, new Vec3d(bx, by, bz), height, width, model);
        } else {
            spawnGlassBurstAt(bx, by, bz, height);
        }
    }

    private void spawnGroundForm(Entity entity, Vec3d position, GroundFormRenderer.Shape shape) {
        if (groundForm.spawn(mc, position, shape, sound.isEnabled()) && entity != null) {
            hiddenOriginals.put(entity, System.nanoTime());
        }
    }

    private void spawnIce1(Entity entity, Vec3d position, double height, double width, EntityRenderState model) {
        if (iceFreeze.spawn(mc, position, height, width, model, sound.isEnabled()) && entity != null) {
            hiddenOriginals.put(entity, System.nanoTime());
        }
    }

    public boolean shouldHideOriginal(Entity entity) {
        if (!isEnabled() || (!mode.is(ASCENSION) && !mode.is(GROUND_FORM) && !mode.is(ICE1))) return false;
        if (!(entity instanceof LivingEntity living) || !living.isDead()) return false;
        if (!hiddenOriginals.containsKey(entity)) {
            onEntityDeath(entity);
        }
        return hiddenOriginals.containsKey(entity);
    }

    private EntityRenderState captureAscensionModel(Entity entity) {
        EntityRenderState state = mc.getEntityRenderDispatcher().getAndUpdateRenderState(entity, 1.0f);
        state.displayName = null;
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.leashDatas = null;
        state.onFire = false;
        if (state instanceof LivingEntityRenderState living) {
            living.deathTime = 0;
            living.hurt = false;
            living.limbSwingAmplitude = 0;
            living.flipUpsideDown = false;
            living.usingRiptide = false;
            living.pose = EntityPose.STANDING;
            living.sleepingDirection = null;
            living.pitch = 0;
        }
        if (state instanceof BipedEntityRenderState biped) {
            biped.leaningPitch = 0;
            biped.isGliding = false;
            biped.isSwimming = false;
            biped.isInSneakingPose = false;
            biped.hasVehicle = false;
        }
        if (state instanceof PlayerEntityRenderState player) {
            player.glidingTicks = 0;
            player.applyFlyingRotation = false;
        }
        return state;
    }

    private void spawnAscension(double x, double y, double z, EntityRenderState model) {
        if (mc.world == null || model == null) return;
        if (ascensions.size() >= 8) ascensions.removeFirst();
        ascensions.add(new Ascension(mc.world, new Vec3d(x, y, z), System.nanoTime(),
                model,
                syncColor.isEnabled() ? Theme.getAccent() : color.get()));
        if (sound.isEnabled()) {
            mc.world.playSoundClient(x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.5f, 1.35f, false);
        }
    }

    public static boolean isRenderingAscension(EntityRenderState state) {
        return instance != null && instance.renderingAscension == state;
    }

    public static boolean isRenderingUpright(EntityRenderState state) {
        return instance != null && (instance.renderingAscension == state || instance.iceFreeze.isRendering(state));
    }

    public void queueModels(MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (!isEnabled()) return;
        groundForm.queue(mc, matrices, queue, camera);
        long now = System.nanoTime();
        ascensions.removeIf(effect -> effect.world() != mc.world || now - effect.started() >= ASCENSION_DURATION);
        if (ascensions.isEmpty()) return;

        EntityRenderManager manager = mc.getEntityRenderDispatcher();
        for (Ascension effect : ascensions) {
            float time = Math.clamp((now - effect.started()) / (float) ASCENSION_DURATION, 0, 1);
            Vec3d offset = effect.position().add(0, 0.12 + 6.5 * time * time, 0).subtract(camera.pos);
            float end = Math.clamp((time - 0.78f) / 0.22f, 0, 1);
            float scale = 1 - end * end;
            EntityRenderState model = effect.model();
            matrices.push();
            renderingAscension = model;
            try {
                matrices.translate(offset.x, offset.y, offset.z);
                matrices.scale(scale, scale, scale);
                manager.getRenderer(model).render(model, matrices, queue, camera);
            } finally {
                renderingAscension = null;
                matrices.pop();
            }
        }
    }

    private void renderAscensionHalos(VertexConsumerProvider consumers, Vec3d camera, long now) {
        VertexConsumer haloBuffer = consumers.getBuffer(RenderLayers.lightning());
        for (Ascension effect : ascensions) {
            float time = Math.clamp((now - effect.started()) / (float) ASCENSION_DURATION, 0, 1);
            float fade = Math.clamp(time / 0.18f, 0, 1) * Math.clamp((1 - time) / 0.42f, 0, 1);
            Vec3d center = effect.position().add(0, 0.12 + 6.5 * time * time + effect.model().height + 0.15, 0).subtract(camera);
            float radius = Math.clamp(effect.model().width * 0.55f, 0.18f, 0.6f);
            int tint = effect.color();
            int red = (tint >> 16) & 255;
            int green = (tint >> 8) & 255;
            int blue = tint & 255;
            int alpha = Math.round(155 * fade);
            emitFlatWaveRing(haloBuffer, (float) center.x, (float) center.y, (float) center.z,
                    new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), radius * 0.88f, radius,
                    red, green, blue, alpha, red, green, blue, alpha, 28);
        }
    }

    private record Ascension(ClientWorld world, Vec3d position, long started, EntityRenderState model, int color) {}

    public void spawnButterflySwarm(Entity entity) {
        spawnButterflySwarmAt(entity.getX(), entity.getY(), entity.getZ(), entity.getHeight());
    }

    public void spawnButterflySwarmAt(double bx, double by, double bz, double height) {
        MinecraftClient mc = MinecraftClient.getInstance();
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

    public void spawnGlassBurst(Entity entity) {
        spawnGlassBurstAt(entity.getX(), entity.getY(), entity.getZ(), entity.getHeight());
    }

    public void spawnGlassBurstAt(double bx, double by, double bz, double height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d basePos = new Vec3d(bx, by, bz);

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
            ascensions.clear();
            hiddenOriginals.clear();
            groundForm.clear();
            iceFreeze.clear();
            if (!particles.isEmpty()) particles.clear();
            if (!butterflySwarms.isEmpty()) butterflySwarms.clear();
            recentAttacks.clear();
            victimsById.clear();
            victimsByName.clear();
            deadEntities.clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && mc.player != null) {
            long nowMs = System.currentTimeMillis();

            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                if (onlyPlayers.isEnabled() && !(e instanceof PlayerEntity)) continue;
                if (e instanceof LivingEntity living) {
                    if (living.getHealth() <= 0.0f || living.isDead()) {
                        onEntityDeath(living);
                    }
                }
            }

            for (Iterator<Map.Entry<Integer, TrackedVictim>> it = victimsById.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, TrackedVictim> entry = it.next();
                int id = entry.getKey();
                TrackedVictim v = entry.getValue();

                if (nowMs - v.lastAttackTime > 10000L) {
                    it.remove();
                    continue;
                }

                Entity e = mc.world.getEntityById(id);
                if (e != null) {
                    Vec3d currentPos = new Vec3d(e.getX(), e.getY(), e.getZ());
                    if (v.lastPos != null && !deadEntities.contains(id)) {
                        double distSq = currentPos.squaredDistanceTo(v.lastPos);
                        long timeSinceAttack = nowMs - v.lastAttackTime;
                        double distToPlayerSq = v.lastPos.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        if (!mode.is(GROUND_FORM) && !mode.is(ICE1) && distSq > 400.0 && timeSinceAttack <= 3500L && distToPlayerSq <= 144.0) {
                            if (deadEntities.add(id)) {
                                triggerEffectAt(v.lastPos.x, v.lastPos.y, v.lastPos.z, v.height, v.width, v.ascensionModel, null);
                                recentAttacks.remove(id);
                                it.remove();
                                continue;
                            }
                        }
                    }
                    v.update(e);
                }
            }

            if (deadEntities.size() > 200) {
                deadEntities.clear();
            }
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        Vector3f lightDir = new Vector3f(0.35f, 0.90f, 0.25f).normalize();

        hiddenOriginals.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || entity.getEntityWorld() != mc.world
                    || (entity.isAlive() && now - entry.getValue() >= ASCENSION_DURATION);
        });
        if (!ascensions.isEmpty()) renderAscensionHalos(immediate, camPos, now);

        if (!butterflySwarms.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
            renderButterflySwarms(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!particles.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
            renderGlassParticles(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider immediate, Vec3d camPos) {
        if (!isEnabled()) return;
        iceFreeze.render(matrices, camera, tickDelta, immediate, camPos, sound.isEnabled());
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
        ascensions.clear();
        hiddenOriginals.clear();
        groundForm.clear();
        iceFreeze.clear();
        particles.clear();
        butterflySwarms.clear();
        recentAttacks.clear();
        victimsById.clear();
        victimsByName.clear();
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

    private void emitFlatWaveRing(VertexConsumer lightBuf, float px, float py, float pz,
                                  Vector3f u, Vector3f v, float innerR, float outerR,
                                  int inR, int inG, int inB, int inA,
                                  int outR, int outG, int outB, int outA, int segs) {
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs);

            float c0 = (float) Math.cos(a0); float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1); float s1 = (float) Math.sin(a1);

            float d0x = u.x * c0 + v.x * s0; float d0y = u.y * c0 + v.y * s0; float d0z = u.z * c0 + v.z * s0;
            float d1x = u.x * c1 + v.x * s1; float d1y = u.y * c1 + v.y * s1; float d1z = u.z * c1 + v.z * s1;

            lightBuf.vertex(px + d0x * innerR, py + d0y * innerR, pz + d0z * innerR).color(inR, inG, inB, inA);
            lightBuf.vertex(px + d1x * innerR, py + d1y * innerR, pz + d1z * innerR).color(inR, inG, inB, inA);
            lightBuf.vertex(px + d1x * outerR, py + d1y * outerR, pz + d1z * outerR).color(outR, outG, outB, outA);
            lightBuf.vertex(px + d0x * outerR, py + d0y * outerR, pz + d0z * outerR).color(outR, outG, outB, outA);
        }
    }

}
