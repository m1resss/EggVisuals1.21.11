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
import egg.client.util.EntityUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
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
    private static final long ASCENSION_DURATION = 1_650_000_000L;

    private final ModeSetting mode = new ModeSetting("Эффект", "Бабочки", "Тип эффекта при смерти противника", "Бабочки", "Обелиск", "Стекло", "Распад", "Черная дыра", ASCENSION);
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

    private final SliderSetting dissolveDuration = new SliderSetting("Время распада", 1.8, 0.8, 3.5, 0.1, "Длительность полного растворения сущности (сек)");
    private final SliderSetting dissolveDensity = new SliderSetting("Плотность частиц", 130, 40, 260, 10, "Количество восходящих вокселей пепла");
    private final BooleanSetting dissolveRing = new BooleanSetting("Рунический круг", true, "Светящийся рунический круг на земле под сущностью");

    private final SliderSetting singularityRadius = new SliderSetting("Радиус горизонта", 0.45, 0.20, 0.85, 0.05, "Радиус горизонта событий черной дыры");
    private final SliderSetting singularityInflow = new SliderSetting("Частицы материи", 54, 20, 110, 2, "Количество затягиваемых световых частиц материи");
    private final BooleanSetting singularityRays = new BooleanSetting("Лучи сверхновой", true, "Выброс радиальных лучей энергии при взрыве сверхновой");
    private final BooleanSetting singularityRift = new BooleanSetting("Разлом пространства", true, "Остаточный пространственный разлом и туманность после взрыва");
    private final BooleanSetting singularityDistortion = new BooleanSetting("Искажение пространства", true, "Гравитационные волны искажения и сотрясение камеры");

    private final List<Ascension> ascensions = new ArrayList<>();
    private final Map<Entity, Long> ascensionOriginals = new IdentityHashMap<>();
    private final List<ButterflySwarmInstance> butterflySwarms = new ArrayList<>();
    private final List<MonolithInstance> monolithInstances = new ArrayList<>();
    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final List<DissolveInstance> dissolveInstances = new ArrayList<>();
    private final List<SingularityInstance> singularityInstances = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Map<Integer, TrackedVictim> victimsById = new ConcurrentHashMap<>();

    private final Matrix4f bodyMat = new Matrix4f();
    private final Matrix4f torsoMat = new Matrix4f();
    private final Matrix4f headMat = new Matrix4f();
    private final Matrix4f rArmMat = new Matrix4f();
    private final Matrix4f lArmMat = new Matrix4f();
    private final Matrix4f rLegMat = new Matrix4f();
    private final Matrix4f lLegMat = new Matrix4f();
    private final Map<String, TrackedVictim> victimsByName = new ConcurrentHashMap<>();
    private final Set<Integer> deadEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    private long lastFrameNanos = 0L;
    private float cameraTrauma = 0.0f;
    private long lastShakeNanos = 0L;

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

        dissolveDuration.setVisible(() -> "Распад".equals(mode.get()));
        dissolveDensity.setVisible(() -> "Распад".equals(mode.get()));
        dissolveRing.setVisible(() -> "Распад".equals(mode.get()));

        singularityRadius.setVisible(() -> "Черная дыра".equals(mode.get()));
        singularityInflow.setVisible(() -> "Черная дыра".equals(mode.get()));
        singularityRays.setVisible(() -> "Черная дыра".equals(mode.get()));
        singularityRift.setVisible(() -> "Черная дыра".equals(mode.get()));
        singularityDistortion.setVisible(() -> "Черная дыра".equals(mode.get()));

        color.setVisible(() -> !syncColor.isEnabled());

        addSettings(mode, onlyTarget, onlyPlayers, sound, syncColor, color,
                butterflyAmount, butterflySize, butterflySpeed, butterflyLifetime, butterflyTrails, butterflyRainbow,
                monolithHeight, monolithScale, monolithRunes,
                amount, size, speed, lifetime, bounce, sparkle,
                dissolveDuration, dissolveDensity, dissolveRing,
                singularityRadius, singularityInflow, singularityRays, singularityRift, singularityDistortion);
    }

    public static KillEffect getInstance() {
        return instance;
    }

    public static class TrackedVictim {
        public final int id;
        public final String name;
        public final boolean isPlayer;
        public volatile Vec3d lastPos;
        public volatile double height;
        public volatile float yaw;
        public volatile float pitch;
        public volatile float headYaw;
        public volatile boolean isSneaking;
        public volatile long lastAttackTime;
        public volatile float lastHealth;
        public volatile Identifier skinTexture;
        public volatile boolean isSlim;
        public volatile Identifier armorLayer1;
        public volatile Identifier armorLayer2;
        public volatile boolean hasHelmet;
        public volatile boolean hasChestplate;
        public volatile boolean hasLeggings;
        public volatile boolean hasBoots;
        private EntityRenderState ascensionModel;

        public TrackedVictim(Entity e) {
            this.id = e.getId();
            this.name = e.getName() != null ? e.getName().getString() : "";
            this.isPlayer = e instanceof PlayerEntity;
            this.lastPos = new Vec3d(e.getX(), e.getY(), e.getZ());
            this.height = e.getHeight();
            this.yaw = e.getYaw();
            this.pitch = e.getPitch();
            this.headYaw = e.getHeadYaw();
            this.isSneaking = e.isSneaking();
            this.lastAttackTime = System.currentTimeMillis();
            this.lastHealth = (e instanceof LivingEntity living) ? living.getHealth() : 20.0f;
            if (e instanceof LivingEntity living) {
                captureAppearance(living);
            }
        }

        public void update(Entity e) {
            this.lastPos = new Vec3d(e.getX(), e.getY(), e.getZ());
            this.height = e.getHeight();
            this.yaw = e.getYaw();
            this.pitch = e.getPitch();
            this.headYaw = e.getHeadYaw();
            this.isSneaking = e.isSneaking();
            if (e instanceof LivingEntity living) {
                this.lastHealth = living.getHealth();
                captureAppearance(living);
            }
        }

        private void captureAppearance(LivingEntity living) {
            if (living instanceof AbstractClientPlayerEntity player) {
                SkinTextures st = player.getSkin();
                if (st != null && st.body() != null) {
                    this.skinTexture = st.body().id();
                    this.isSlim = (st.model() == PlayerSkinType.SLIM);
                }
            } else if (living instanceof PlayerEntity player) {
                SkinTextures st = DefaultSkinHelper.getSkinTextures(player.getGameProfile());
                if (st != null && st.body() != null) {
                    this.skinTexture = st.body().id();
                    this.isSlim = (st.model() == PlayerSkinType.SLIM);
                }
            }
            if (this.skinTexture == null) {
                this.skinTexture = resolveEntityTexture(living);
            }

            ItemStack head = living.getEquippedStack(EquipmentSlot.HEAD);
            ItemStack chest = living.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack legs = living.getEquippedStack(EquipmentSlot.LEGS);
            ItemStack feet = living.getEquippedStack(EquipmentSlot.FEET);

            this.hasHelmet = head != null && !head.isEmpty();
            this.hasChestplate = chest != null && !chest.isEmpty();
            this.hasLeggings = legs != null && !legs.isEmpty();
            this.hasBoots = feet != null && !feet.isEmpty();

            String mat1 = resolveArmorMaterial(head, chest, feet);
            if (mat1 != null) {
                this.armorLayer1 = Identifier.ofVanilla("textures/models/armor/" + mat1 + "_layer_1.png");
            } else {
                this.armorLayer1 = null;
            }

            String mat2 = resolveArmorMaterial(legs);
            if (mat2 != null) {
                this.armorLayer2 = Identifier.ofVanilla("textures/models/armor/" + mat2 + "_layer_2.png");
            } else {
                this.armorLayer2 = null;
            }
        }
    }

    private static String resolveArmorMaterial(ItemStack... stacks) {
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            String path = Registries.ITEM.getId(s.getItem()).getPath();
            if (path.contains("netherite")) return "netherite";
            if (path.contains("diamond")) return "diamond";
            if (path.contains("iron")) return "iron";
            if (path.contains("gold")) return "gold";
            if (path.contains("chain")) return "chainmail";
            if (path.contains("leather")) return "leather";
            if (path.contains("turtle")) return "turtle";
            if (path.contains("copper")) return "copper";
        }
        return null;
    }

    private static Identifier resolveEntityTexture(Entity entity) {
        if (entity == null) return DefaultSkinHelper.getTexture();
        String type = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
        if (type.contains("zombie")) return Identifier.ofVanilla("textures/entity/zombie/zombie.png");
        if (type.contains("skeleton")) return Identifier.ofVanilla("textures/entity/skeleton/skeleton.png");
        if (type.contains("creeper")) return Identifier.ofVanilla("textures/entity/creeper/creeper.png");
        if (type.contains("spider")) return Identifier.ofVanilla("textures/entity/spider/spider.png");
        if (type.contains("enderman")) return Identifier.ofVanilla("textures/entity/enderman/enderman.png");
        if (type.contains("piglin")) return Identifier.ofVanilla("textures/entity/piglin/piglin.png");
        if (type.contains("blaze")) return Identifier.ofVanilla("textures/entity/blaze.png");
        if (type.contains("cow")) return Identifier.ofVanilla("textures/entity/cow/cow.png");
        if (type.contains("pig")) return Identifier.ofVanilla("textures/entity/pig/pig.png");
        if (type.contains("sheep")) return Identifier.ofVanilla("textures/entity/sheep/sheep.png");
        return DefaultSkinHelper.getTexture();
    }

    public void triggerCameraShockwave(double x, double y, double z) {
        if (!singularityDistortion.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double distSq = mc.player.squaredDistanceTo(x, y, z);
        double maxDist = 28.0;
        if (distSq > maxDist * maxDist) return;
        double dist = Math.sqrt(distSq);
        float distFactor = (float) Math.max(0.0, 1.0 - (dist / maxDist));
        float traumaAdd = 0.85f * distFactor * distFactor;
        this.cameraTrauma = Math.min(1.0f, this.cameraTrauma + traumaAdd);
    }

    public void applyCameraShake(MatrixStack matrices, float tickDelta) {
        if (!isEnabled() || cameraTrauma <= 0.001f) return;
        long now = System.nanoTime();
        if (lastShakeNanos != 0L) {
            float dt = (now - lastShakeNanos) / 1_000_000_000.0f;
            cameraTrauma = Math.max(0.0f, cameraTrauma - dt * 1.35f);
        }
        lastShakeNanos = now;
        if (cameraTrauma <= 0.001f) return;

        float shake = cameraTrauma * cameraTrauma;
        float time = (System.currentTimeMillis() % 100000) * 0.035f;
        float pitch = (float) Math.sin(time * 1.3f) * 4.2f * shake;
        float roll = (float) Math.cos(time * 1.7f) * 3.6f * shake;
        float yaw = (float) Math.sin(time * 0.9f) * 2.1f * shake;

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    public void onAttack(Entity target) {
        if (!isEnabled() || target == null || EntityUtil.isNakedInvisible(target)) return;
        long now = System.currentTimeMillis();
        recentAttacks.put(target.getId(), now);

        TrackedVictim v = victimsById.computeIfAbsent(target.getId(), id -> new TrackedVictim(target));
        v.lastAttackTime = now;
        v.update(target);
        if (ASCENSION.equals(mode.get())) v.ascensionModel = captureAscensionModel(target);
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
            } else if ("Обелиск".equals(curMode)) {
                spawnMonolithEffect(entity);
            } else if ("Распад".equals(curMode)) {
                spawnDissolveEffect(entity);
            } else if ("Черная дыра".equals(curMode)) {
                spawnSingularityEffect(entity);
            } else if (ASCENSION.equals(curMode)) {
                EntityRenderState model = captureAscensionModel(entity);
                ascensionOriginals.put(entity, System.nanoTime());
                spawnAscension(entity.getX(), entity.getY(), entity.getZ(), model);
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
            float yaw = 0.0f, pitch = 0.0f, headYaw = 0.0f;
            boolean sneaking = false;

            if (entity != null) {
                deathPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                height = entity.getHeight();
                yaw = entity.getYaw();
                pitch = entity.getPitch();
                headYaw = entity.getHeadYaw();
                sneaking = entity.isSneaking();
            } else if (v != null) {
                deathPos = v.lastPos;
                height = v.height;
                yaw = v.yaw;
                pitch = v.pitch;
                headYaw = v.headYaw;
                sneaking = v.isSneaking;
            }

            if (deathPos != null) {
                double distSq = deathPos.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (distSq <= 256.0) {
                    if (deadEntities.add(id)) {
                        EntityRenderState model = ASCENSION.equals(mode.get()) && entity != null
                                ? captureAscensionModel(entity) : v != null ? v.ascensionModel : null;
                        triggerEffectAt(deathPos.x, deathPos.y, deathPos.z, height, yaw, pitch, headYaw, sneaking, model);
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
                            triggerEffectAt(v.lastPos.x, v.lastPos.y, v.lastPos.z, v.height, v.yaw, v.pitch, v.headYaw, v.isSneaking, v.ascensionModel);
                        }
                        recentAttacks.remove(v.id);
                        victimsById.remove(v.id);
                        break;
                    }
                }
            }
        }
    }

    private void triggerEffectAt(double bx, double by, double bz, double height, float yaw, float pitch, float headYaw,
                                 boolean isSneaking, EntityRenderState model) {
        String curMode = mode.get();
        if ("Бабочки".equals(curMode)) {
            spawnButterflySwarmAt(bx, by, bz, height);
        } else if ("Обелиск".equals(curMode)) {
            spawnMonolithEffectAt(bx, by, bz, yaw, pitch, headYaw, isSneaking, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        } else if ("Распад".equals(curMode)) {
            spawnDissolveEffectAt(bx, by, bz, yaw, pitch, headYaw, isSneaking, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        } else if ("Черная дыра".equals(curMode)) {
            spawnSingularityEffectAt(bx, by, bz, yaw, pitch, headYaw, isSneaking, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, height);
        } else if (ASCENSION.equals(curMode)) {
            spawnAscension(bx, by, bz, model);
        } else {
            spawnGlassBurstAt(bx, by, bz, height);
        }
    }

    public boolean shouldHideAscensionOriginal(Entity entity) {
        if (!isEnabled() || !ASCENSION.equals(mode.get())) return false;
        if (entity instanceof LivingEntity living && living.isDead() && !ascensionOriginals.containsKey(entity)) {
            onEntityDeath(entity);
        }
        return ascensionOriginals.containsKey(entity);
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

    private void renderAscensions(VertexConsumerProvider consumers, Vec3d camera, long now) {
        ascensions.removeIf(effect -> effect.world() != mc.world || now - effect.started() >= ASCENSION_DURATION);
        if (ascensions.isEmpty()) return;
        EntityRenderManager manager = mc.getEntityRenderDispatcher();
        RenderDispatcher dispatcher = mc.gameRenderer.getEntityRenderDispatcher();
        CameraRenderState cameraState = new CameraRenderState();
        cameraState.orientation = mc.gameRenderer.getCamera().getRotation();
        cameraState.pos = camera;
        cameraState.blockPos = BlockPos.ofFloored(camera);
        cameraState.entityPos = camera;
        cameraState.initialized = true;
        MatrixStack matrices = new MatrixStack();
        for (Ascension effect : ascensions) {
            float time = (now - effect.started()) / (float) ASCENSION_DURATION;
            Vec3d offset = effect.position().add(0, 0.12 + 6.5 * time * time, 0).subtract(camera);
            float end = Math.clamp((time - 0.78f) / 0.22f, 0, 1);
            float scale = 1 - end * end;
            matrices.push();
            matrices.translate(offset.x, offset.y, offset.z);
            matrices.scale(scale, scale, scale);
            manager.render(effect.model(), cameraState, 0, 0, 0, matrices, dispatcher.getQueue());
            matrices.pop();
        }
        dispatcher.render();
        mc.getBufferBuilders().getEntityVertexConsumers().draw();

        VertexConsumer haloBuffer = consumers.getBuffer(RenderLayers.lightning());
        for (Ascension effect : ascensions) {
            float time = (now - effect.started()) / (float) ASCENSION_DURATION;
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

    public void spawnMonolithEffect(Entity entity) {
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

        spawnMonolithEffectAt(entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), entity.isSneaking(),
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll);
    }

    public void spawnMonolithEffectAt(double bx, double by, double bz, float yaw, float pitch, float headYaw, boolean isSneaking,
                                      float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll) {
        MinecraftClient mc = MinecraftClient.getInstance();
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

    public void spawnDissolveEffect(Entity entity) {
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

        spawnDissolveEffectAt(entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), entity.isSneaking(),
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll);
    }

    public void spawnDissolveEffectAt(double bx, double by, double bz, float yaw, float pitch, float headYaw, boolean isSneaking,
                                      float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by + 1.0, bz,
                    SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.PLAYERS,
                    1.4f, 1.25f, false);
            mc.world.playSoundClient(bx, by + 1.0, bz,
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS,
                    1.1f, 1.6f, false);
            mc.world.playSoundClient(bx, by + 1.0, bz,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS,
                    1.2f, 1.45f, false);
        }

        int effectColor = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        float duration = (float) dissolveDuration.get();
        int density = (int) dissolveDensity.get();
        boolean ring = dissolveRing.isEnabled();

        dissolveInstances.add(new DissolveInstance(
                bx, by, bz, yaw, pitch, headYaw, isSneaking,
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll,
                duration, density, effectColor, ring
        ));

        while (dissolveInstances.size() > 6) {
            dissolveInstances.remove(0);
        }
    }

    public void spawnSingularityEffect(Entity entity) {
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

        Identifier skinTexture = null;
        boolean isSlim = false;
        Identifier armorLayer1 = null;
        Identifier armorLayer2 = null;
        boolean hasHelmet = false;
        boolean hasChestplate = false;
        boolean hasLeggings = false;
        boolean hasBoots = false;

        TrackedVictim v = victimsById.get(entity.getId());
        if (v != null && v.skinTexture != null) {
            skinTexture = v.skinTexture;
            isSlim = v.isSlim;
            armorLayer1 = v.armorLayer1;
            armorLayer2 = v.armorLayer2;
            hasHelmet = v.hasHelmet;
            hasChestplate = v.hasChestplate;
            hasLeggings = v.hasLeggings;
            hasBoots = v.hasBoots;
        } else if (entity instanceof LivingEntity living) {
            if (living instanceof AbstractClientPlayerEntity player) {
                SkinTextures st = player.getSkin();
                if (st != null && st.body() != null) {
                    skinTexture = st.body().id();
                    isSlim = (st.model() == PlayerSkinType.SLIM);
                }
            } else if (living instanceof PlayerEntity player) {
                SkinTextures st = DefaultSkinHelper.getSkinTextures(player.getGameProfile());
                if (st != null && st.body() != null) {
                    skinTexture = st.body().id();
                    isSlim = (st.model() == PlayerSkinType.SLIM);
                }
            }
            if (skinTexture == null) {
                skinTexture = resolveEntityTexture(living);
            }

            ItemStack head = living.getEquippedStack(EquipmentSlot.HEAD);
            ItemStack chest = living.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack legs = living.getEquippedStack(EquipmentSlot.LEGS);
            ItemStack feet = living.getEquippedStack(EquipmentSlot.FEET);

            hasHelmet = head != null && !head.isEmpty();
            hasChestplate = chest != null && !chest.isEmpty();
            hasLeggings = legs != null && !legs.isEmpty();
            hasBoots = feet != null && !feet.isEmpty();

            String mat1 = resolveArmorMaterial(head, chest, feet);
            if (mat1 != null) {
                armorLayer1 = Identifier.ofVanilla("textures/models/armor/" + mat1 + "_layer_1.png");
            }
            String mat2 = resolveArmorMaterial(legs);
            if (mat2 != null) {
                armorLayer2 = Identifier.ofVanilla("textures/models/armor/" + mat2 + "_layer_2.png");
            }
        }
        if (skinTexture == null) {
            skinTexture = DefaultSkinHelper.getTexture();
        }

        spawnSingularityEffectAt(entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), entity.isSneaking(),
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll, entity.getHeight(),
                skinTexture, isSlim, armorLayer1, armorLayer2, hasHelmet, hasChestplate, hasLeggings, hasBoots);
    }

    public void spawnSingularityEffectAt(double bx, double by, double bz, float yaw, float pitch, float headYaw, boolean isSneaking,
                                         float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll, double height) {
        spawnSingularityEffectAt(bx, by, bz, yaw, pitch, headYaw, isSneaking,
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll, height,
                DefaultSkinHelper.getTexture(), false, null, null, false, false, false, false);
    }

    public void spawnSingularityEffectAt(double bx, double by, double bz, float yaw, float pitch, float headYaw, boolean isSneaking,
                                         float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll, double height,
                                         Identifier skinTexture, boolean isSlim, Identifier armorLayer1, Identifier armorLayer2,
                                         boolean hasHelmet, boolean hasChestplate, boolean hasLeggings, boolean hasBoots) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by + height * 0.55, bz,
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS,
                    1.4f, 0.65f, false);
            mc.world.playSoundClient(bx, by + height * 0.55, bz,
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS,
                    1.1f, 1.45f, false);
        }

        int effectColor = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        float radius = (float) singularityRadius.get();
        int inflow = (int) singularityInflow.get();
        boolean rays = singularityRays.isEnabled();
        boolean rift = singularityRift.isEnabled();
        boolean distortion = singularityDistortion.isEnabled();

        singularityInstances.add(new SingularityInstance(
                bx, by, bz, (float) height, yaw, pitch, headYaw, isSneaking,
                rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll,
                radius, inflow, rays, rift, distortion, effectColor,
                skinTexture, isSlim, armorLayer1, armorLayer2, hasHelmet, hasChestplate, hasLeggings, hasBoots, random
        ));

        while (singularityInstances.size() > 8) {
            singularityInstances.remove(0);
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
            ascensionOriginals.clear();
            if (!monolithInstances.isEmpty()) monolithInstances.clear();
            if (!particles.isEmpty()) particles.clear();
            if (!butterflySwarms.isEmpty()) butterflySwarms.clear();
            if (!dissolveInstances.isEmpty()) dissolveInstances.clear();
            if (!singularityInstances.isEmpty()) singularityInstances.clear();
            cameraTrauma = 0.0f;
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
                        if (distSq > 400.0 && timeSinceAttack <= 3500L && distToPlayerSq <= 144.0) {
                            if (deadEntities.add(id)) {
                                triggerEffectAt(v.lastPos.x, v.lastPos.y, v.lastPos.z, v.height, v.yaw, v.pitch, v.headYaw, v.isSneaking, v.ascensionModel);
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

        ascensionOriginals.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || entity.getEntityWorld() != mc.world
                    || (entity.isAlive() && now - entry.getValue() >= ASCENSION_DURATION);
        });
        if (!ascensions.isEmpty()) renderAscensions(immediate, camPos, now);

        if (!butterflySwarms.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
            renderButterflySwarms(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!monolithInstances.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
            renderMonolithInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!dissolveInstances.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
            renderDissolveInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        if (!singularityInstances.isEmpty()) {
            renderSingularityInstances(immediate, camPos, lightDir, dt, mc);
        }

        if (!particles.isEmpty()) {
            VertexConsumer quadBuffer = immediate.getBuffer(RenderLayers.debugQuads());
            VertexConsumer lightBuffer = immediate.getBuffer(RenderLayers.lightning());
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
        bodyMat.identity().rotateY((float) Math.toRadians(-inst.yaw));

        torsoMat.set(bodyMat);
        if (inst.isSneaking) {
            torsoMat.rotateX((float) Math.toRadians(25.0f));
        }

        float torsoBaseY = 0.70f;
        float torsoTopY = torsoBaseY + 0.75f;
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, torsoMat,
                -0.25f, torsoBaseY, -0.125f,
                0.25f, torsoTopY, 0.125f, lightDir, 255);

        headMat.set(bodyMat);
        headMat.translate(0, torsoTopY, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));

        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, headMat,
                -0.25f, 0, -0.25f,
                0.25f, 0.50f, 0.25f, lightDir, 255);

        rArmMat.set(bodyMat);
        rArmMat.translate(-0.375f, torsoTopY - 0.08f, 0);
        rArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rArmPitch)));
        rArmMat.rotateZ((float) Math.toRadians(-Math.toDegrees(inst.rArmRoll)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, rArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f, lightDir, 255);

        lArmMat.set(bodyMat);
        lArmMat.translate(0.375f, torsoTopY - 0.08f, 0);
        lArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lArmPitch)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, lArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f, lightDir, 255);

        rLegMat.set(bodyMat);
        rLegMat.translate(-0.125f, torsoBaseY, 0);
        rLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rLegPitch)));
        emitStoneBox(quadBuf, camPos, inst.x, inst.y, inst.z, rLegMat,
                -0.125f, -0.70f, -0.125f,
                0.125f, 0, 0.125f, lightDir, 255);

        lLegMat.set(bodyMat);
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
        ascensions.clear();
        ascensionOriginals.clear();
        particles.clear();
        butterflySwarms.clear();
        monolithInstances.clear();
        dissolveInstances.clear();
        singularityInstances.clear();
        recentAttacks.clear();
        victimsById.clear();
        victimsByName.clear();
        deadEntities.clear();
        cameraTrauma = 0.0f;
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

    private void renderDissolveInstances(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                         Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<DissolveInstance> it = dissolveInstances.iterator();
        while (it.hasNext()) {
            DissolveInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.duration + 1.2f) {
                it.remove();
                continue;
            }
            renderSingleDissolve(quadBuf, lightBuf, inst, camPos, lightDir, dt);
        }
    }

    private void renderSingleDissolve(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                      DissolveInstance inst, Vec3d camPos, Vector3f lightDir, float dt) {
        float prog = MathHelper.clamp(inst.age / inst.duration, 0.0f, 1.0f);
        float cutHeight = prog * 2.05f;

        if (inst.ring) {
            renderDissolveGroundRune(lightBuf, inst, camPos, prog);
        }

        if (prog < 1.0f) {
            renderDissolveSilhouette(quadBuf, lightBuf, inst, camPos, lightDir, cutHeight);
        }

        inst.updateAndRenderVoxels(quadBuf, lightBuf, camPos, lightDir, dt);
    }

    private void renderDissolveGroundRune(VertexConsumer lightBuf, DissolveInstance inst,
                                         Vec3d camPos, float prog) {
        float ringAlphaProg = prog < 0.15f ? (prog / 0.15f) : (1.0f - (prog - 0.15f) / 0.85f);
        ringAlphaProg = MathHelper.clamp(ringAlphaProg, 0.0f, 1.0f);
        int alpha = (int) (180 * ringAlphaProg);
        if (alpha <= 2) return;

        int r = (inst.color >> 16) & 0xFF;
        int g = (inst.color >> 8) & 0xFF;
        int b = inst.color & 0xFF;

        float cx = (float) (inst.x - camPos.x);
        float cy = (float) (inst.y + 0.02 - camPos.y);
        float cz = (float) (inst.z - camPos.z);

        float outerR = 0.95f + 0.35f * (float) Math.sin(prog * Math.PI * 0.5f);
        float innerR = outerR * 0.72f;
        int segments = 28;
        float rot = inst.age * 0.65f;

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segments) + rot;
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segments) + rot;

            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float x0Out = cx + cos0 * outerR;
            float z0Out = cz + sin0 * outerR;
            float x1Out = cx + cos1 * outerR;
            float z1Out = cz + sin1 * outerR;

            float x0In = cx + cos0 * innerR;
            float z0In = cz + sin0 * innerR;
            float x1In = cx + cos1 * innerR;
            float z1In = cz + sin1 * innerR;

            lightBuf.vertex(x0In, cy, z0In).color(r, g, b, alpha);
            lightBuf.vertex(x1In, cy, z1In).color(r, g, b, alpha);
            lightBuf.vertex(x1Out, cy, z1Out).color(r, g, b, 0);
            lightBuf.vertex(x0Out, cy, z0Out).color(r, g, b, 0);

            if (i % 3 == 0) {
                float spokeR0 = innerR * 0.55f;
                float spokeR1 = outerR * 1.08f;
                float sx0 = cx + cos0 * spokeR0;
                float sz0 = cz + sin0 * spokeR0;
                float sx1 = cx + cos0 * spokeR1;
                float sz1 = cz + sin0 * spokeR1;

                float perpX = -sin0 * 0.02f;
                float perpZ = cos0 * 0.02f;

                lightBuf.vertex(sx0 - perpX, cy, sz0 - perpZ).color(255, 255, 255, alpha);
                lightBuf.vertex(sx1 - perpX, cy, sz1 - perpZ).color(r, g, b, 0);
                lightBuf.vertex(sx1 + perpX, cy, sz1 + perpZ).color(r, g, b, 0);
                lightBuf.vertex(sx0 + perpX, cy, sz0 + perpZ).color(255, 255, 255, alpha);
            }
        }
    }

    private void renderDissolveSilhouette(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                          DissolveInstance inst, Vec3d camPos, Vector3f lightDir, float cutHeight) {
        float prog = MathHelper.clamp(inst.age / inst.duration, 0.0f, 1.0f);
        float phantomAlpha = MathHelper.clamp(1.0f - prog * 0.35f, 0.35f, 1.0f);
        boolean canSpawn = inst.spawnedVoxels < inst.targetVoxelCount;

        bodyMat.identity().rotateY((float) Math.toRadians(-inst.yaw));

        torsoMat.set(bodyMat);
        if (inst.isSneaking) {
            torsoMat.rotateX((float) Math.toRadians(25.0f));
        }

        float torsoBaseY = 0.70f;
        float torsoTopY = torsoBaseY + 0.75f;
        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, torsoMat,
                -0.25f, torsoBaseY, -0.125f,
                0.25f, torsoTopY, 0.125f,
                torsoBaseY, torsoTopY, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);

        headMat.set(bodyMat);
        headMat.translate(0, torsoTopY, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));

        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, headMat,
                -0.25f, 0, -0.25f,
                0.25f, 0.50f, 0.25f,
                torsoTopY, torsoTopY + 0.50f, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);

        rArmMat.set(bodyMat);
        rArmMat.translate(-0.375f, torsoTopY - 0.08f, 0);
        rArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rArmPitch)));
        rArmMat.rotateZ((float) Math.toRadians(-Math.toDegrees(inst.rArmRoll)));
        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, rArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f,
                0.65f, 1.40f, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);

        lArmMat.set(bodyMat);
        lArmMat.translate(0.375f, torsoTopY - 0.08f, 0);
        lArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lArmPitch)));
        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, lArmMat,
                -0.125f, -0.72f, -0.125f,
                0.125f, 0.03f, 0.125f,
                0.65f, 1.40f, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);

        rLegMat.set(bodyMat);
        rLegMat.translate(-0.125f, torsoBaseY, 0);
        rLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rLegPitch)));
        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, rLegMat,
                -0.125f, -0.70f, -0.125f,
                0.125f, 0, 0.125f,
                0.0f, torsoBaseY, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);

        lLegMat.set(bodyMat);
        lLegMat.translate(0.125f, torsoBaseY, 0);
        lLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lLegPitch)));
        renderDissolveBox(quadBuf, lightBuf, camPos, inst.x, inst.y, inst.z, lLegMat,
                -0.125f, -0.70f, -0.125f,
                0.125f, 0, 0.125f,
                0.0f, torsoBaseY, cutHeight, lightDir, inst.color, phantomAlpha, inst, canSpawn);
    }

    private void renderDissolveBox(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                   Vec3d camPos, double wx, double wy, double wz, Matrix4f mat,
                                   float x0, float y0, float z0, float x1, float y1, float z1,
                                   float cutPartMinY, float cutPartMaxY, float cutHeight,
                                   Vector3f lightDir, int color, float phantomAlpha,
                                   DissolveInstance inst, boolean canSpawnVoxels) {
        if (cutHeight >= cutPartMaxY) {
            return;
        }

        float curY0 = y0;
        boolean hasCutSeam = false;

        if (cutHeight > cutPartMinY) {
            float frac = (cutHeight - cutPartMinY) / Math.max(1.0e-4f, cutPartMaxY - cutPartMinY);
            curY0 = y0 + frac * (y1 - y0);
            hasCutSeam = true;

            if (canSpawnVoxels) {
                int spawnCount = 1 + inst.random.nextInt(3);
                for (int s = 0; s < spawnCount && inst.spawnedVoxels < inst.targetVoxelCount; s++) {
                    float px, pz;
                    int side = inst.random.nextInt(4);
                    float r = inst.random.nextFloat();
                    if (side == 0) {
                        px = x0 + (x1 - x0) * r;
                        pz = z0;
                    } else if (side == 1) {
                        px = x0 + (x1 - x0) * r;
                        pz = z1;
                    } else if (side == 2) {
                        px = x0;
                        pz = z0 + (z1 - z0) * r;
                    } else {
                        px = x1;
                        pz = z0 + (z1 - z0) * r;
                    }

                    Vector3f wp = new Vector3f(px, curY0, pz).mulPosition(mat);
                    float vx = wp.x * 0.45f + (inst.random.nextFloat() - 0.5f) * 0.35f;
                    float vy = 0.45f + inst.random.nextFloat() * 0.65f;
                    float vz = wp.z * 0.45f + (inst.random.nextFloat() - 0.5f) * 0.35f;
                    float life = 0.75f + inst.random.nextFloat() * 0.75f;
                    float sz = 0.024f + inst.random.nextFloat() * 0.034f;

                    inst.voxels.add(new DissolveVoxel(
                            wx + wp.x, wy + wp.y, wz + wp.z,
                            vx, vy, vz,
                            inst.random.nextFloat() * 6.28f, inst.random.nextFloat() * 6.28f, inst.random.nextFloat() * 6.28f,
                            (inst.random.nextFloat() - 0.5f) * 8.0f, (inst.random.nextFloat() - 0.5f) * 8.0f, (inst.random.nextFloat() - 0.5f) * 8.0f,
                            sz, life, color
                    ));
                    inst.spawnedVoxels++;
                }
            }
        }

        int alpha = (int) (185 * phantomAlpha);
        if (alpha > 2) {
            emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                    x0, curY0, z1, x1, curY0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
            emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                    x1, curY0, z0, x0, curY0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
            emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                    x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);
            if (!hasCutSeam) {
                emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                        x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0);
            }
            emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                    x0, curY0, z0, x0, curY0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
            emitDissolveFace(quadBuf, camPos, wx, wy, wz, mat, lightDir, color, alpha,
                    x1, curY0, z1, x1, curY0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
        }

        if (hasCutSeam) {
            emitDissolveCap(lightBuf, camPos, wx, wy, wz, mat, x0, z0, x1, z1, curY0, color, inst.age);
        }
    }

    private static void emitDissolveFace(VertexConsumer buf, Vec3d camPos,
                                        double wx, double wy, double wz, Matrix4f mat,
                                        Vector3f lightDir, int baseColor, int alpha,
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
        float diffuse = MathHelper.clamp(normal.dot(lightDir) * 0.45f + 0.65f, 0.25f, 1.0f);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        int cr = MathHelper.clamp((int) (r * diffuse), 0, 255);
        int cg = MathHelper.clamp((int) (g * diffuse), 0, 255);
        int cb = MathHelper.clamp((int) (b * diffuse), 0, 255);

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

        buf.vertex(vx0, vy0, vz0).color(cr, cg, cb, alpha);
        buf.vertex(vx1, vy1, vz1).color(cr, cg, cb, alpha);
        buf.vertex(vx2, vy2, vz2).color(cr, cg, cb, alpha);
        buf.vertex(vx3, vy3, vz3).color(cr, cg, cb, alpha);
    }

    private static void emitDissolveCap(VertexConsumer buf, Vec3d camPos,
                                       double wx, double wy, double wz, Matrix4f mat,
                                       float x0, float z0, float x1, float z1, float capY,
                                       int color, float age) {
        float flare = 0.022f;
        Vector3f p0 = new Vector3f(x0 - flare, capY, z0 - flare).mulPosition(mat);
        Vector3f p1 = new Vector3f(x1 + flare, capY, z0 - flare).mulPosition(mat);
        Vector3f p2 = new Vector3f(x1 + flare, capY, z1 + flare).mulPosition(mat);
        Vector3f p3 = new Vector3f(x0 - flare, capY, z1 + flare).mulPosition(mat);

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

        int pulse = (int) (200 + 45 * Math.sin(age * 26.0f));
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buf.vertex(vx0, vy0, vz0).color(255, 255, 255, pulse);
        buf.vertex(vx1, vy1, vz1).color(r, g, b, pulse);
        buf.vertex(vx2, vy2, vz2).color(255, 255, 255, pulse);
        buf.vertex(vx3, vy3, vz3).color(r, g, b, pulse);

        buf.vertex(vx3, vy3, vz3).color(r, g, b, pulse);
        buf.vertex(vx2, vy2, vz2).color(255, 255, 255, pulse);
        buf.vertex(vx1, vy1, vz1).color(r, g, b, pulse);
        buf.vertex(vx0, vy0, vz0).color(255, 255, 255, pulse);
    }

    private static void emitMiniVoxel(VertexConsumer buf, Vec3d camPos,
                                      double vx, double vy, double vz,
                                      float pitch, float yaw, float roll,
                                      float size, int r, int g, int b, int a) {
        float cx = (float) (vx - camPos.x);
        float cy = (float) (vy - camPos.y);
        float cz = (float) (vz - camPos.z);

        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);

        float m00 = cyaw * cr + syaw * sp * sr;
        float m01 = -cyaw * sr + syaw * sp * cr;
        float m02 = syaw * cp;
        float m10 = cp * sr;
        float m11 = cp * cr;
        float m12 = -sp;
        float m20 = -syaw * cr + cyaw * sp * sr;
        float m21 = syaw * sr + cyaw * sp * cr;
        float m22 = cyaw * cp;

        float s = size;

        float c0x = cx + (-s) * m00 + (-s) * m01 + (-s) * m02;
        float c0y = cy + (-s) * m10 + (-s) * m11 + (-s) * m12;
        float c0z = cz + (-s) * m20 + (-s) * m21 + (-s) * m22;

        float c1x = cx + (s) * m00 + (-s) * m01 + (-s) * m02;
        float c1y = cy + (s) * m10 + (-s) * m11 + (-s) * m12;
        float c1z = cz + (s) * m20 + (-s) * m21 + (-s) * m22;

        float c2x = cx + (s) * m00 + (s) * m01 + (-s) * m02;
        float c2y = cy + (s) * m10 + (s) * m11 + (-s) * m12;
        float c2z = cz + (s) * m20 + (s) * m21 + (-s) * m22;

        float c3x = cx + (-s) * m00 + (s) * m01 + (-s) * m02;
        float c3y = cy + (-s) * m10 + (s) * m11 + (-s) * m12;
        float c3z = cz + (-s) * m20 + (s) * m21 + (-s) * m22;

        float c4x = cx + (-s) * m00 + (-s) * m01 + (s) * m02;
        float c4y = cy + (-s) * m10 + (-s) * m11 + (s) * m12;
        float c4z = cz + (-s) * m20 + (-s) * m21 + (s) * m22;

        float c5x = cx + (s) * m00 + (-s) * m01 + (s) * m02;
        float c5y = cy + (s) * m10 + (-s) * m11 + (s) * m12;
        float c5z = cz + (s) * m20 + (-s) * m21 + (s) * m22;

        float c6x = cx + (s) * m00 + (s) * m01 + (s) * m02;
        float c6y = cy + (s) * m10 + (s) * m11 + (s) * m12;
        float c6z = cz + (s) * m20 + (s) * m21 + (s) * m22;

        float c7x = cx + (-s) * m00 + (s) * m01 + (s) * m02;
        float c7y = cy + (-s) * m10 + (s) * m11 + (s) * m12;
        float c7z = cz + (-s) * m20 + (s) * m21 + (s) * m22;

        buf.vertex(c4x, c4y, c4z).color(r, g, b, a);
        buf.vertex(c5x, c5y, c5z).color(r, g, b, a);
        buf.vertex(c6x, c6y, c6z).color(r, g, b, a);
        buf.vertex(c7x, c7y, c7z).color(r, g, b, a);

        buf.vertex(c1x, c1y, c1z).color(r, g, b, a);
        buf.vertex(c0x, c0y, c0z).color(r, g, b, a);
        buf.vertex(c3x, c3y, c3z).color(r, g, b, a);
        buf.vertex(c2x, c2y, c2z).color(r, g, b, a);

        int tr = MathHelper.clamp((int) (r * 1.15f), 0, 255);
        int tg = MathHelper.clamp((int) (g * 1.15f), 0, 255);
        int tb = MathHelper.clamp((int) (b * 1.15f), 0, 255);

        buf.vertex(c3x, c3y, c3z).color(tr, tg, tb, a);
        buf.vertex(c7x, c7y, c7z).color(tr, tg, tb, a);
        buf.vertex(c6x, c6y, c6z).color(tr, tg, tb, a);
        buf.vertex(c2x, c2y, c2z).color(tr, tg, tb, a);

        int br = (int) (r * 0.70f);
        int bg = (int) (g * 0.70f);
        int bb = (int) (b * 0.70f);

        buf.vertex(c0x, c0y, c0z).color(br, bg, bb, a);
        buf.vertex(c1x, c1y, c1z).color(br, bg, bb, a);
        buf.vertex(c5x, c5y, c5z).color(br, bg, bb, a);
        buf.vertex(c4x, c4y, c4z).color(br, bg, bb, a);

        buf.vertex(c0x, c0y, c0z).color(r, g, b, a);
        buf.vertex(c4x, c4y, c4z).color(r, g, b, a);
        buf.vertex(c7x, c7y, c7z).color(r, g, b, a);
        buf.vertex(c3x, c3y, c3z).color(r, g, b, a);

        buf.vertex(c5x, c5y, c5z).color(r, g, b, a);
        buf.vertex(c1x, c1y, c1z).color(r, g, b, a);
        buf.vertex(c2x, c2y, c2z).color(r, g, b, a);
        buf.vertex(c6x, c6y, c6z).color(r, g, b, a);
    }

    private static class DissolveVoxel {
        double x, y, z;
        float vx, vy, vz;
        float pitch, yaw, roll;
        float vPitch, vYaw, vRoll;
        float size;
        float life;
        float age = 0.0f;
        int color;

        DissolveVoxel(double x, double y, double z,
                      float vx, float vy, float vz,
                      float pitch, float yaw, float roll,
                      float vPitch, float vYaw, float vRoll,
                      float size, float life, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.vPitch = vPitch;
            this.vYaw = vYaw;
            this.vRoll = vRoll;
            this.size = size;
            this.life = life;
            this.color = color;
        }
    }

    private static class DissolveInstance {
        final double x, y, z;
        final float yaw, pitch, headYaw;
        final boolean isSneaking;
        final float rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll;
        final float duration;
        final int targetVoxelCount;
        final int color;
        final boolean ring;
        float age = 0.0f;
        int spawnedVoxels = 0;
        final List<DissolveVoxel> voxels = new ArrayList<>();
        final Random random = new Random();

        DissolveInstance(double x, double y, double z, float yaw, float pitch, float headYaw, boolean isSneaking,
                         float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll,
                         float duration, int targetVoxelCount, int color, boolean ring) {
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
            this.duration = duration;
            this.targetVoxelCount = targetVoxelCount;
            this.color = color;
            this.ring = ring;
        }

        void updateAndRenderVoxels(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                   Vec3d camPos, Vector3f lightDir, float dt) {
            Iterator<DissolveVoxel> it = voxels.iterator();
            while (it.hasNext()) {
                DissolveVoxel v = it.next();
                v.age += dt;
                if (v.age >= v.life) {
                    it.remove();
                    continue;
                }

                v.vy += 0.28f * dt;

                float dx = (float) (v.x - x);
                float dz = (float) (v.z - z);
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01f) {
                    float nx = -dz / dist;
                    float nz = dx / dist;
                    v.vx += nx * 0.38f * dt;
                    v.vz += nz * 0.38f * dt;
                }

                v.vx *= (1.0f - 0.35f * dt);
                v.vz *= (1.0f - 0.35f * dt);

                v.x += v.vx * dt;
                v.y += v.vy * dt;
                v.z += v.vz * dt;

                v.pitch += v.vPitch * dt;
                v.yaw += v.vYaw * dt;
                v.roll += v.vRoll * dt;

                float prog = v.age / v.life;
                float alphaFactor = prog < 0.15f ? (prog / 0.15f) : (1.0f - (prog - 0.15f) / 0.85f);
                int a = (int) (245 * MathHelper.clamp(alphaFactor, 0.0f, 1.0f));
                if (a <= 2) continue;

                int vr, vg, vb;
                if (prog < 0.22f) {
                    vr = MathHelper.clamp((int) (((v.color >> 16) & 0xFF) * 0.45f + 255 * 0.55f), 0, 255);
                    vg = MathHelper.clamp((int) (((v.color >> 8) & 0xFF) * 0.45f + 255 * 0.55f), 0, 255);
                    vb = MathHelper.clamp((int) ((v.color & 0xFF) * 0.45f + 255 * 0.55f), 0, 255);
                } else if (prog < 0.68f) {
                    vr = (v.color >> 16) & 0xFF;
                    vg = (v.color >> 8) & 0xFF;
                    vb = v.color & 0xFF;
                } else {
                    float cinderProg = (prog - 0.68f) / 0.32f;
                    vr = (int) (((v.color >> 16) & 0xFF) * (1.0f - cinderProg * 0.72f) * 0.5f + 35 * cinderProg);
                    vg = (int) (((v.color >> 8) & 0xFF) * (1.0f - cinderProg * 0.82f) * 0.3f + 18 * cinderProg);
                    vb = (int) ((v.color & 0xFF) * (1.0f - cinderProg * 0.88f) * 0.2f + 14 * cinderProg);
                }

                float curSize = v.size * (1.0f - prog * 0.42f);
                emitMiniVoxel(quadBuf, camPos, v.x, v.y, v.z, v.pitch, v.yaw, v.roll, curSize, vr, vg, vb, a);

                if (prog < 0.55f) {
                    float sparkSize = curSize * 2.2f;
                    float px = (float) (v.x - camPos.x);
                    float py = (float) (v.y - camPos.y);
                    float pz = (float) (v.z - camPos.z);
                    int haloAlpha = (int) (a * 0.55f * (1.0f - prog / 0.55f));
                    lightBuf.vertex(px - sparkSize, py, pz).color(vr, vg, vb, 0);
                    lightBuf.vertex(px, py + sparkSize, pz).color(255, 255, 255, haloAlpha);
                    lightBuf.vertex(px + sparkSize, py, pz).color(vr, vg, vb, 0);
                    lightBuf.vertex(px, py - sparkSize, pz).color(255, 255, 255, haloAlpha);
                }
            }
        }
    }

    private void renderSingularityInstances(VertexConsumerProvider immediate,
                                            Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<SingularityInstance> it = singularityInstances.iterator();
        while (it.hasNext()) {
            SingularityInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.totalDuration) {
                it.remove();
            }
        }
        if (singularityInstances.isEmpty()) return;

        for (SingularityInstance inst : singularityInstances) {
            if (inst.age < 1.62f) {
                renderFlexibleCharacterSheet(immediate, inst, camPos, inst.age);
            }
        }

        VertexConsumer quadBuf = immediate.getBuffer(RenderLayers.debugQuads());
        for (SingularityInstance inst : singularityInstances) {
            if (inst.riftEnabled) {
                renderSpacetimeRiftQuads(quadBuf, inst, camPos, inst.cx, inst.cy, inst.cz, inst.age);
            }
        }

        VertexConsumer lightBuf = immediate.getBuffer(RenderLayers.lightning());
        for (SingularityInstance inst : singularityInstances) {
            float age = inst.age;
            double cx = inst.cx;
            double cy = inst.cy;
            double cz = inst.cz;

            if (age >= 1.55f && !inst.soundPlayedBoom) {
                inst.soundPlayedBoom = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(cx, cy, cz,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS,
                            1.8f, 0.80f, false);
                    mc.world.playSoundClient(cx, cy, cz,
                            SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS,
                            1.6f, 0.70f, false);
                    mc.world.playSoundClient(cx, cy, cz,
                            SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS,
                            1.4f, 1.30f, false);
                    mc.world.playSoundClient(cx, cy, cz,
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
                            1.3f, 1.25f, false);
                }
                inst.spawnSupernovaSparks(random, this, cx, cy, cz);
            }

            if (inst.riftEnabled) {
                renderSpacetimeRiftGlowAndArcs(lightBuf, inst, camPos, cx, cy, cz, age, inst.color);
            }

            if (age < 1.62f) {
                renderInflowParticles(lightBuf, inst, camPos, cx, cy, cz, 0.15f);
            }

            if (age >= 1.55f) {
                float shockAge = age - 0.85f;
                emitSupernovaShockwave(lightBuf, camPos, cx, cy, cz, shockAge, inst.color, inst.distortionEnabled);
                if (inst.distortionEnabled) {
                    emitGravitationalLensingBubble(lightBuf, camPos, cx, cy, cz, shockAge, inst.color);
                }
                if (inst.raysEnabled) {
                    emitNovaRays(lightBuf, inst, camPos, cx, cy, cz, shockAge, inst.color);
                }
                updateAndRenderStardust(lightBuf, inst, camPos, dt);
                if (inst.riftEnabled) {
                    updateAndRenderNebulaClouds(lightBuf, inst, camPos, cx, cy, cz, dt);
                    updateAndRenderQuantumEmbers(lightBuf, inst, camPos, cx, cy, cz, dt);
                }
            }
        }
    }

    private void renderCollapsingSilhouette(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                            SingularityInstance inst, Vec3d camPos, Vector3f lightDir,
                                            float prog, double cx, double cy, double cz) {
        float s = (float) Math.pow(1.0f - prog, 1.75);
        if (s <= 0.01f) return;

        float spin = (float) Math.toRadians(-inst.yaw) + inst.age * inst.age * 36.0f;
        int alpha = (int) (240 * (1.0f - prog));
        if (alpha <= 2) return;

        bodyMat.identity();
        bodyMat.translate((float) (cx - camPos.x), (float) (cy - camPos.y), (float) (cz - camPos.z));
        bodyMat.rotateY(spin);
        bodyMat.scale(s, s, s);

        torsoMat.set(bodyMat);
        if (inst.isSneaking) {
            torsoMat.rotateX((float) Math.toRadians(25.0f));
        }
        renderSwirlingBox(quadBuf, lightBuf, torsoMat, -0.25f, -0.38f, -0.125f, 0.25f, 0.38f, 0.125f, inst.color, alpha);

        headMat.set(bodyMat);
        headMat.translate(0, 0.42f, 0);
        headMat.rotateY((float) Math.toRadians(-(inst.headYaw - inst.yaw)));
        headMat.rotateX((float) Math.toRadians(-inst.pitch));
        renderSwirlingBox(quadBuf, lightBuf, headMat, -0.22f, 0.0f, -0.22f, 0.22f, 0.44f, 0.22f, inst.color, alpha);

        rArmMat.set(bodyMat);
        rArmMat.translate(-0.36f, 0.32f, 0);
        rArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rArmPitch)));
        rArmMat.rotateZ((float) Math.toRadians(-Math.toDegrees(inst.rArmRoll)));
        renderSwirlingBox(quadBuf, lightBuf, rArmMat, -0.11f, -0.65f, -0.11f, 0.11f, 0.03f, 0.11f, inst.color, alpha);

        lArmMat.set(bodyMat);
        lArmMat.translate(0.36f, 0.32f, 0);
        lArmMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lArmPitch)));
        renderSwirlingBox(quadBuf, lightBuf, lArmMat, -0.11f, -0.65f, -0.11f, 0.11f, 0.03f, 0.11f, inst.color, alpha);

        rLegMat.set(bodyMat);
        rLegMat.translate(-0.13f, -0.38f, 0);
        rLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.rLegPitch)));
        renderSwirlingBox(quadBuf, lightBuf, rLegMat, -0.11f, -0.70f, -0.11f, 0.11f, 0.0f, 0.11f, inst.color, alpha);

        lLegMat.set(bodyMat);
        lLegMat.translate(0.13f, -0.38f, 0);
        lLegMat.rotateX((float) Math.toRadians(-Math.toDegrees(inst.lLegPitch)));
        renderSwirlingBox(quadBuf, lightBuf, lLegMat, -0.11f, -0.70f, -0.11f, 0.11f, 0.0f, 0.11f, inst.color, alpha);
    }

    private void renderSwirlingBox(VertexConsumer quadBuf, VertexConsumer lightBuf, Matrix4f mat,
                                   float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                   int col, int a) {
        Vector3f c0 = new Vector3f(minX, minY, minZ).mulPosition(mat);
        Vector3f c1 = new Vector3f(maxX, minY, minZ).mulPosition(mat);
        Vector3f c2 = new Vector3f(maxX, maxY, minZ).mulPosition(mat);
        Vector3f c3 = new Vector3f(minX, maxY, minZ).mulPosition(mat);
        Vector3f c4 = new Vector3f(minX, minY, maxZ).mulPosition(mat);
        Vector3f c5 = new Vector3f(maxX, minY, maxZ).mulPosition(mat);
        Vector3f c6 = new Vector3f(maxX, maxY, maxZ).mulPosition(mat);
        Vector3f c7 = new Vector3f(minX, maxY, maxZ).mulPosition(mat);

        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;

        int qr = MathHelper.clamp((int) (r * 0.15f + 10), 0, 255);
        int qg = MathHelper.clamp((int) (g * 0.15f + 10), 0, 255);
        int qb = MathHelper.clamp((int) (b * 0.15f + 18), 0, 255);

        quadBuf.vertex(c4.x, c4.y, c4.z).color(qr, qg, qb, a);
        quadBuf.vertex(c5.x, c5.y, c5.z).color(qr, qg, qb, a);
        quadBuf.vertex(c6.x, c6.y, c6.z).color(qr, qg, qb, a);
        quadBuf.vertex(c7.x, c7.y, c7.z).color(qr, qg, qb, a);

        quadBuf.vertex(c1.x, c1.y, c1.z).color(qr, qg, qb, a);
        quadBuf.vertex(c0.x, c0.y, c0.z).color(qr, qg, qb, a);
        quadBuf.vertex(c3.x, c3.y, c3.z).color(qr, qg, qb, a);
        quadBuf.vertex(c2.x, c2.y, c2.z).color(qr, qg, qb, a);

        quadBuf.vertex(c3.x, c3.y, c3.z).color(qr, qg, qb, a);
        quadBuf.vertex(c7.x, c7.y, c7.z).color(qr, qg, qb, a);
        quadBuf.vertex(c6.x, c6.y, c6.z).color(qr, qg, qb, a);
        quadBuf.vertex(c2.x, c2.y, c2.z).color(qr, qg, qb, a);

        quadBuf.vertex(c0.x, c0.y, c0.z).color(qr, qg, qb, a);
        quadBuf.vertex(c1.x, c1.y, c1.z).color(qr, qg, qb, a);
        quadBuf.vertex(c5.x, c5.y, c5.z).color(qr, qg, qb, a);
        quadBuf.vertex(c4.x, c4.y, c4.z).color(qr, qg, qb, a);

        quadBuf.vertex(c0.x, c0.y, c0.z).color(qr, qg, qb, a);
        quadBuf.vertex(c4.x, c4.y, c4.z).color(qr, qg, qb, a);
        quadBuf.vertex(c7.x, c7.y, c7.z).color(qr, qg, qb, a);
        quadBuf.vertex(c3.x, c3.y, c3.z).color(qr, qg, qb, a);

        quadBuf.vertex(c5.x, c5.y, c5.z).color(qr, qg, qb, a);
        quadBuf.vertex(c1.x, c1.y, c1.z).color(qr, qg, qb, a);
        quadBuf.vertex(c2.x, c2.y, c2.z).color(qr, qg, qb, a);
        quadBuf.vertex(c6.x, c6.y, c6.z).color(qr, qg, qb, a);

        int edgeA = (int) (a * 0.75f);
        if (edgeA > 2) {
            emitLineQuad(lightBuf, c0, c1, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c1, c2, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c2, c3, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c3, c0, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c4, c5, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c5, c6, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c6, c7, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c7, c4, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c0, c4, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c1, c5, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c2, c6, 0.012f, r, g, b, edgeA);
            emitLineQuad(lightBuf, c3, c7, 0.012f, r, g, b, edgeA);
        }
    }

    private void emitLineQuad(VertexConsumer buf, Vector3f p0, Vector3f p1, float width, int r, int g, int b, int a) {
        float dx = p1.x - p0.x;
        float dy = p1.y - p0.y;
        float dz = p1.z - p0.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;

        float nx = -dz / len * width;
        float nz = dx / len * width;

        buf.vertex(p0.x - nx, p0.y, p0.z - nz).color(r, g, b, a);
        buf.vertex(p1.x - nx, p1.y, p1.z - nz).color(r, g, b, a);
        buf.vertex(p1.x + nx, p1.y, p1.z + nz).color(r, g, b, a);
        buf.vertex(p0.x + nx, p0.y, p0.z + nz).color(r, g, b, a);
    }

    private void emitAccretionRibbons(VertexConsumer lightBuf, Vec3d camPos, double cx, double cy, double cz, float age, int color) {
        float prog = MathHelper.clamp(age / 0.38f, 0.0f, 1.0f);
        int alpha = (int) (220 * (1.0f - prog));
        if (alpha <= 2) return;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        int ribbons = 12;
        int segs = 14;
        for (int i = 0; i < ribbons; i++) {
            float baseAngle = (float) (i * 2.0 * Math.PI / ribbons) + age * 18.0f;
            float heightOffset = ((i % 5) - 2) * 0.12f;

            for (int s = 0; s < segs; s++) {
                float t0 = (float) s / segs;
                float t1 = (float) (s + 1) / segs;

                float r0 = 0.08f + (1.0f - t0) * (1.4f * (1.0f - prog));
                float r1 = 0.08f + (1.0f - t1) * (1.4f * (1.0f - prog));

                float a0 = baseAngle + t0 * 3.8f;
                float a1 = baseAngle + t1 * 3.8f;

                float y0 = py + heightOffset * (1.0f - t0 * 0.8f);
                float y1 = py + heightOffset * (1.0f - t1 * 0.8f);

                float x0 = px + (float) Math.cos(a0) * r0;
                float z0 = pz + (float) Math.sin(a0) * r0;

                float x1 = px + (float) Math.cos(a1) * r1;
                float z1 = pz + (float) Math.sin(a1) * r1;

                float w0 = 0.025f * (1.0f - t0 * 0.5f);
                float w1 = 0.025f * (1.0f - t1 * 0.5f);

                int a0Val = (int) (alpha * (1.0f - t0 * 0.7f));
                int a1Val = (int) (alpha * (1.0f - t1 * 0.7f));

                lightBuf.vertex(x0, y0 - w0, z0).color(r, g, b, a0Val);
                lightBuf.vertex(x1, y1 - w1, z1).color(r, g, b, a1Val);
                lightBuf.vertex(x1, y1 + w1, z1).color(255, 255, 255, a1Val);
                lightBuf.vertex(x0, y0 + w0, z0).color(255, 255, 255, a0Val);
            }
        }
    }

    private void renderFlexibleCharacterSheet(VertexConsumerProvider immediate, SingularityInstance inst, Vec3d camPos, float age) {
        if (age < 0.02f || age > 1.62f) return;

        VertexConsumer skinBuf = immediate.getBuffer(RenderLayers.entityTranslucent(inst.skinTexture));
        renderSheetBodyMesh(skinBuf, inst, camPos, age, 0);

        if (inst.armorLayer1 != null && (inst.hasHelmet || inst.hasChestplate || inst.hasBoots)) {
            VertexConsumer armor1Buf = immediate.getBuffer(RenderLayers.armorTranslucent(inst.armorLayer1));
            renderSheetBodyMesh(armor1Buf, inst, camPos, age, 1);
        }

        if (inst.armorLayer2 != null && inst.hasLeggings) {
            VertexConsumer armor2Buf = immediate.getBuffer(RenderLayers.armorTranslucent(inst.armorLayer2));
            renderSheetBodyMesh(armor2Buf, inst, camPos, age, 2);
        }


    }

    private void renderSheetBodyMesh(VertexConsumer consumer, SingularityInstance inst, Vec3d camPos, float age, int passType) {
        boolean slim = inst.isSlim;
        float armW = slim ? 0.15f : 0.20f;

        if (passType == 0) {
            renderSheetPart(consumer, inst, camPos, age, -0.20f, 0.20f, 1.42f, 1.82f, 4, 4,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f,
                    24f / 64f, 8f / 64f, 32f / 64f, 16f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, -0.21f, 0.21f, 1.41f, 1.83f, 4, 4,
                    40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f,
                    56f / 64f, 8f / 64f, 64f / 64f, 16f / 64f, 0.012f);

            renderSheetPart(consumer, inst, camPos, age, -0.20f, 0.20f, 0.72f, 1.42f, 4, 6,
                    20f / 64f, 20f / 64f, 28f / 64f, 32f / 64f,
                    32f / 64f, 20f / 64f, 40f / 64f, 32f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, -0.208f, 0.208f, 0.71f, 1.43f, 4, 6,
                    20f / 64f, 36f / 64f, 28f / 64f, 48f / 64f,
                    32f / 64f, 36f / 64f, 40f / 64f, 48f / 64f, 0.010f);

            float rArmU0 = 44f / 64f;
            float rArmU1 = (slim ? 47f : 48f) / 64f;
            float rArmBU0 = 52f / 64f;
            float rArmBU1 = (slim ? 55f : 56f) / 64f;

            renderSheetPart(consumer, inst, camPos, age, -0.20f - armW, -0.20f, 0.72f, 1.38f, 2, 6,
                    rArmU0, 20f / 64f, rArmU1, 32f / 64f,
                    rArmBU0, 20f / 64f, rArmBU1, 32f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, -0.205f - armW, -0.195f, 0.71f, 1.39f, 2, 6,
                    rArmU0, 36f / 64f, rArmU1, 48f / 64f,
                    rArmBU0, 36f / 64f, rArmBU1, 48f / 64f, 0.010f);

            float lArmU0 = 36f / 64f;
            float lArmU1 = (slim ? 39f : 40f) / 64f;
            float lArmBU0 = 44f / 64f;
            float lArmBU1 = (slim ? 47f : 48f) / 64f;

            renderSheetPart(consumer, inst, camPos, age, 0.20f, 0.20f + armW, 0.72f, 1.38f, 2, 6,
                    lArmU0, 52f / 64f, lArmU1, 64f / 64f,
                    lArmBU0, 52f / 64f, lArmBU1, 64f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, 0.195f, 0.205f + armW, 0.71f, 1.39f, 2, 6,
                    52f / 64f, 52f / 64f, (slim ? 55f : 56f) / 64f, 64f / 64f,
                    60f / 64f, 52f / 64f, (slim ? 63f : 64f) / 64f, 64f / 64f, 0.010f);

            renderSheetPart(consumer, inst, camPos, age, -0.20f, 0.0f, 0.02f, 0.72f, 2, 6,
                    4f / 64f, 20f / 64f, 8f / 64f, 32f / 64f,
                    12f / 64f, 20f / 64f, 16f / 64f, 32f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, -0.205f, 0.005f, 0.01f, 0.73f, 2, 6,
                    4f / 64f, 36f / 64f, 8f / 64f, 48f / 64f,
                    12f / 64f, 36f / 64f, 16f / 64f, 48f / 64f, 0.010f);

            renderSheetPart(consumer, inst, camPos, age, 0.0f, 0.20f, 0.02f, 0.72f, 2, 6,
                    20f / 64f, 52f / 64f, 24f / 64f, 64f / 64f,
                    28f / 64f, 52f / 64f, 32f / 64f, 64f / 64f, 0.0f);

            renderSheetPart(consumer, inst, camPos, age, -0.005f, 0.205f, 0.01f, 0.73f, 2, 6,
                    4f / 64f, 52f / 64f, 8f / 64f, 64f / 64f,
                    12f / 64f, 52f / 64f, 16f / 64f, 64f / 64f, 0.010f);
        } else if (passType == 1) {
            if (inst.hasHelmet) {
                renderSheetPart(consumer, inst, camPos, age, -0.215f, 0.215f, 1.40f, 1.84f, 4, 4,
                        8f / 64f, 8f / 32f, 16f / 64f, 16f / 32f,
                        24f / 64f, 8f / 32f, 32f / 64f, 16f / 32f, 0.018f);
            }
            if (inst.hasChestplate) {
                renderSheetPart(consumer, inst, camPos, age, -0.21f, 0.21f, 0.71f, 1.43f, 4, 6,
                        20f / 64f, 20f / 32f, 28f / 64f, 32f / 32f,
                        32f / 64f, 20f / 32f, 40f / 64f, 32f / 32f, 0.018f);

                renderSheetPart(consumer, inst, camPos, age, -0.205f - armW, -0.195f, 0.71f, 1.39f, 2, 6,
                        44f / 64f, 20f / 32f, 48f / 64f, 32f / 32f,
                        52f / 64f, 20f / 32f, 56f / 64f, 32f / 32f, 0.018f);

                renderSheetPart(consumer, inst, camPos, age, 0.195f, 0.205f + armW, 0.71f, 1.39f, 2, 6,
                        44f / 64f, 20f / 32f, 48f / 64f, 32f / 32f,
                        52f / 64f, 20f / 32f, 56f / 64f, 32f / 32f, 0.018f);
            }
            if (inst.hasBoots) {
                renderSheetPart(consumer, inst, camPos, age, -0.21f, 0.01f, 0.01f, 0.32f, 2, 3,
                        4f / 64f, 26f / 32f, 8f / 64f, 32f / 32f,
                        12f / 64f, 26f / 32f, 16f / 64f, 32f / 32f, 0.018f);

                renderSheetPart(consumer, inst, camPos, age, -0.01f, 0.21f, 0.01f, 0.32f, 2, 3,
                        4f / 64f, 26f / 32f, 8f / 64f, 32f / 32f,
                        12f / 64f, 26f / 32f, 16f / 64f, 32f / 32f, 0.018f);
            }
        } else if (passType == 2) {
            if (inst.hasLeggings) {
                renderSheetPart(consumer, inst, camPos, age, -0.208f, 0.208f, 0.65f, 0.88f, 4, 3,
                        20f / 64f, 20f / 32f, 28f / 64f, 26f / 32f,
                        32f / 64f, 20f / 32f, 40f / 64f, 26f / 32f, 0.014f);

                renderSheetPart(consumer, inst, camPos, age, -0.208f, 0.008f, 0.22f, 0.72f, 2, 5,
                        4f / 64f, 20f / 32f, 8f / 64f, 30f / 32f,
                        12f / 64f, 20f / 32f, 16f / 64f, 30f / 32f, 0.014f);

                renderSheetPart(consumer, inst, camPos, age, -0.008f, 0.208f, 0.22f, 0.72f, 2, 5,
                        4f / 64f, 20f / 32f, 8f / 64f, 30f / 32f,
                        12f / 64f, 20f / 32f, 16f / 64f, 30f / 32f, 0.014f);
            }
        }
    }

    private void renderSheetPart(VertexConsumer consumer, SingularityInstance inst, Vec3d camPos, float age,
                                 float minLx, float maxLx, float minLy, float maxLy, int cols, int rows,
                                 float u0, float v0, float u1, float v1,
                                 float bu0, float bv0, float bu1, float bv1, float layerNudge) {
        for (int c = 0; c < cols; c++) {
            float lxA = minLx + (maxLx - minLx) * ((float) c / cols);
            float lxB = minLx + (maxLx - minLx) * ((float) (c + 1) / cols);

            float cuA = u0 + (u1 - u0) * ((float) c / cols);
            float cuB = u0 + (u1 - u0) * ((float) (c + 1) / cols);

            float bcuA = bu0 + (bu1 - bu0) * ((float) (cols - c) / cols);
            float bcuB = bu0 + (bu1 - bu0) * ((float) (cols - (c + 1)) / cols);

            for (int r = 0; r < rows; r++) {
                float lyA = minLy + (maxLy - minLy) * ((float) r / rows);
                float lyB = minLy + (maxLy - minLy) * ((float) (r + 1) / rows);

                float cvA = v0 + (v1 - v0) * ((float) (rows - r) / rows);
                float cvB = v0 + (v1 - v0) * ((float) (rows - (r + 1)) / rows);

                float bcvA = bv0 + (bv1 - bv0) * ((float) (rows - r) / rows);
                float bcvB = bv0 + (bv1 - bv0) * ((float) (rows - (r + 1)) / rows);

                int alpha = getSheetAlpha(lyA, inst, age);
                if (alpha <= 2) continue;

                Vector3f p00 = deformSheetPoint(lxA, lyA, layerNudge, inst, age, camPos);
                Vector3f p10 = deformSheetPoint(lxB, lyA, layerNudge, inst, age, camPos);
                Vector3f p11 = deformSheetPoint(lxB, lyB, layerNudge, inst, age, camPos);
                Vector3f p01 = deformSheetPoint(lxA, lyB, layerNudge, inst, age, camPos);

                float d1x = p10.x - p00.x;
                float d1y = p10.y - p00.y;
                float d1z = p10.z - p00.z;
                float d2x = p01.x - p00.x;
                float d2y = p01.y - p00.y;
                float d2z = p01.z - p00.z;
                float nx = d1y * d2z - d1z * d2y;
                float ny = d1z * d2x - d1x * d2z;
                float nz = d1x * d2y - d1y * d2x;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.001f) {
                    nx /= len; ny /= len; nz /= len;
                } else {
                    nx = 0f; ny = 1f; nz = 0f;
                }

                consumer.vertex(p00.x, p00.y, p00.z).color(255, 255, 255, alpha).texture(cuA, cvA).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx, ny, nz);
                consumer.vertex(p10.x, p10.y, p10.z).color(255, 255, 255, alpha).texture(cuB, cvA).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx, ny, nz);
                consumer.vertex(p11.x, p11.y, p11.z).color(255, 255, 255, alpha).texture(cuB, cvB).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx, ny, nz);
                consumer.vertex(p01.x, p01.y, p01.z).color(255, 255, 255, alpha).texture(cuA, cvB).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(nx, ny, nz);

                consumer.vertex(p01.x, p01.y, p01.z).color(255, 255, 255, alpha).texture(bcuA, bcvB).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx, -ny, -nz);
                consumer.vertex(p11.x, p11.y, p11.z).color(255, 255, 255, alpha).texture(bcuB, bcvB).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx, -ny, -nz);
                consumer.vertex(p10.x, p10.y, p10.z).color(255, 255, 255, alpha).texture(bcuB, bcvA).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx, -ny, -nz);
                consumer.vertex(p00.x, p00.y, p00.z).color(255, 255, 255, alpha).texture(bcuA, bcvA).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(-nx, -ny, -nz);
            }
        }
    }

    private int getSheetAlpha(float ly, SingularityInstance inst, float age) {
        float globalProg = MathHelper.clamp((age - 0.05f) / 1.50f, 0.0f, 1.0f);
        float normY = ly / 1.82f;
        float localProg = MathHelper.clamp(globalProg * 1.30f - (1.0f - normY) * 0.30f, 0.0f, 1.0f);
        float alphaProg = localProg < 0.05f ? (localProg / 0.05f) : (localProg > 0.88f ? (1.0f - (localProg - 0.88f) / 0.12f) : 1.0f);
        return (int) (255 * MathHelper.clamp(alphaProg, 0.0f, 1.0f));
    }

    private Vector3f deformSheetPoint(float lx, float ly, float layerNudge, SingularityInstance inst, float age, Vec3d camPos) {
        float globalProg = MathHelper.clamp((age - 0.05f) / 1.50f, 0.0f, 1.0f);
        float normY = ly / 1.82f;
        float localProg = MathHelper.clamp(globalProg * 1.30f - (1.0f - normY) * 0.30f, 0.0f, 1.0f);

        float t = (float) Math.pow(localProg, 1.6);
        float oneMinusT = 1.0f - t;

        float yawRad = (float) Math.toRadians(-inst.yaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double latX = fwdZ;
        double latZ = -fwdX;

        double startX = inst.x;
        double startY = inst.y + ly;
        double startZ = inst.z;

        double midX = (startX + inst.cx) * 0.5 + latX * 0.65 * (1.0f - localProg);
        double midY = Math.max(startY, inst.cy) + 0.45 * (1.0f - localProg);
        double midZ = (startZ + inst.cz) * 0.5 + latZ * 0.65 * (1.0f - localProg);

        double curX = oneMinusT * oneMinusT * startX + 2.0 * oneMinusT * t * midX + t * t * inst.cx;
        double curY = oneMinusT * oneMinusT * startY + 2.0 * oneMinusT * t * midY + t * t * inst.cy;
        double curZ = oneMinusT * oneMinusT * startZ + 2.0 * oneMinusT * t * midZ + t * t * inst.cz;

        double toRiftX = inst.cx - curX;
        double toRiftY = inst.cy - curY;
        double toRiftZ = inst.cz - curZ;
        double toRiftLen = Math.sqrt(toRiftX * toRiftX + toRiftY * toRiftY + toRiftZ * toRiftZ);
        if (toRiftLen > 0.001) {
            toRiftX /= toRiftLen; toRiftY /= toRiftLen; toRiftZ /= toRiftLen;
        } else {
            toRiftX = fwdX; toRiftY = 0; toRiftZ = fwdZ;
        }

        double align = Math.min(1.0, localProg * 1.5);
        double curFwdX = fwdX * (1.0 - align) + toRiftX * align;
        double curFwdY = toRiftY * align;
        double curFwdZ = fwdZ * (1.0 - align) + toRiftZ * align;
        double curFwdLen = Math.sqrt(curFwdX * curFwdX + curFwdY * curFwdY + curFwdZ * curFwdZ);
        if (curFwdLen > 0.001) {
            curFwdX /= curFwdLen; curFwdY /= curFwdLen; curFwdZ /= curFwdLen;
        }

        double curLatX = -curFwdZ;
        double curLatY = 0.0;
        double curLatZ = curFwdX;
        double curLatLen = Math.sqrt(curLatX * curLatX + curLatZ * curLatZ);
        if (curLatLen > 0.001) {
            curLatX /= curLatLen; curLatZ /= curLatLen;
        }

        double curNormX = curLatY * curFwdZ - curLatZ * curFwdY;
        double curNormY = curLatZ * curFwdX - curLatX * curFwdZ;
        double curNormZ = curLatX * curFwdY - curLatY * curFwdX;

        float waveTime = age * 16.0f;
        float flutter = (float) (Math.sin(waveTime - normY * 7.5f + (lx / 0.45f) * 3.2f) * 0.13f * (1.0f - localProg * 0.65f)
                + Math.cos(waveTime * 1.3f + normY * 4.8f - (lx / 0.45f) * 3.8f) * 0.06f * (1.0f - localProg * 0.65f));

        float curlAmount = MathHelper.clamp((localProg - 0.15f) / 0.75f, 0.0f, 1.0f);
        float curlAngle = (lx / 0.45f) * (float) Math.PI * 1.15f * curlAmount;
        float curledLx = (float) Math.sin(curlAngle) * 0.28f * (1.0f - localProg * 0.65f);
        float curlDepth = (1.0f - (float) Math.cos(curlAngle)) * 0.16f * curlAmount;

        float widthComp = MathHelper.lerp(localProg, 1.0f, 0.22f);
        curledLx = (curledLx * curlAmount + lx * (1.0f - curlAmount)) * widthComp;

        double finalX = curX + curLatX * curledLx + curNormX * (flutter + curlDepth + layerNudge);
        double finalY = curY + curLatY * curledLx + curNormY * (flutter + curlDepth + layerNudge);
        double finalZ = curZ + curLatZ * curledLx + curNormZ * (flutter + curlDepth + layerNudge);

        return new Vector3f((float) (finalX - camPos.x), (float) (finalY - camPos.y), (float) (finalZ - camPos.z));
    }

    private void emitBlackHoleSphere(VertexConsumer quadBuf, Vec3d camPos, double cx, double cy, double cz, float radius) {
        if (radius <= 0.01f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        int latRings = 10;
        int lonSegs = 20;

        for (int i = 0; i < latRings; i++) {
            float phi0 = (float) (-Math.PI * 0.5 + i * Math.PI / latRings);
            float phi1 = (float) (-Math.PI * 0.5 + (i + 1) * Math.PI / latRings);

            float y0 = py + (float) Math.sin(phi0) * radius;
            float y1 = py + (float) Math.sin(phi1) * radius;
            float r0 = (float) Math.cos(phi0) * radius;
            float r1 = (float) Math.cos(phi1) * radius;

            for (int j = 0; j < lonSegs; j++) {
                float th0 = (float) (j * 2.0 * Math.PI / lonSegs);
                float th1 = (float) ((j + 1) * 2.0 * Math.PI / lonSegs);

                float x00 = px + (float) Math.cos(th0) * r0;
                float z00 = pz + (float) Math.sin(th0) * r0;

                float x10 = px + (float) Math.cos(th1) * r0;
                float z10 = pz + (float) Math.sin(th1) * r0;

                float x11 = px + (float) Math.cos(th1) * r1;
                float z11 = pz + (float) Math.sin(th1) * r1;

                float x01 = px + (float) Math.cos(th0) * r1;
                float z01 = pz + (float) Math.sin(th0) * r1;

                quadBuf.vertex(x00, y0, z00).color(2, 2, 4, 255);
                quadBuf.vertex(x10, y0, z10).color(2, 2, 4, 255);
                quadBuf.vertex(x11, y1, z11).color(2, 2, 4, 255);
                quadBuf.vertex(x01, y1, z01).color(2, 2, 4, 255);
            }
        }
    }

    private void emitPhotonHaloRing(VertexConsumer lightBuf, Vec3d camPos, double cx, double cy, double cz,
                                    float radius, int color, float age) {
        if (radius <= 0.01f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist < 0.01f) return;
        toCamX /= dist; toCamY /= dist; toCamZ /= dist;

        float ux, uy, uz;
        if (Math.abs(toCamY) < 0.95f) {
            ux = -toCamZ; uy = 0; uz = toCamX;
            float uLen = (float) Math.sqrt(ux * ux + uz * uz);
            ux /= uLen; uz /= uLen;
        } else {
            ux = 0; uy = -toCamZ; uz = toCamY;
            float uLen = (float) Math.sqrt(uy * uy + uz * uz);
            uy /= uLen; uz /= uLen;
        }
        float vx = toCamY * uz - toCamZ * uy;
        float vy = toCamZ * ux - toCamX * uz;
        float vz = toCamX * uy - toCamY * ux;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segs = 28;
        float innerR = radius * 0.98f;
        float midR = radius * 1.35f;
        float outerR = radius * 1.95f;

        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs);

            float c0 = (float) Math.cos(a0); float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1); float s1 = (float) Math.sin(a1);

            float d0x = ux * c0 + vx * s0; float d0y = uy * c0 + vy * s0; float d0z = uz * c0 + vz * s0;
            float d1x = ux * c1 + vx * s1; float d1y = uy * c1 + vy * s1; float d1z = uz * c1 + vz * s1;

            lightBuf.vertex(px + d0x * innerR, py + d0y * innerR, pz + d0z * innerR).color(255, 255, 255, 255);
            lightBuf.vertex(px + d1x * innerR, py + d1y * innerR, pz + d1z * innerR).color(255, 255, 255, 255);
            lightBuf.vertex(px + d1x * midR, py + d1y * midR, pz + d1z * midR).color(r, g, b, 140);
            lightBuf.vertex(px + d0x * midR, py + d0y * midR, pz + d0z * midR).color(r, g, b, 140);

            lightBuf.vertex(px + d0x * midR, py + d0y * midR, pz + d0z * midR).color(r, g, b, 140);
            lightBuf.vertex(px + d1x * midR, py + d1y * midR, pz + d1z * midR).color(r, g, b, 140);
            lightBuf.vertex(px + d1x * outerR, py + d1y * outerR, pz + d1z * outerR).color(r, g, b, 0);
            lightBuf.vertex(px + d0x * outerR, py + d0y * outerR, pz + d0z * outerR).color(r, g, b, 0);
        }
    }

    private void emitAccretionDisk(VertexConsumer lightBuf, Vec3d camPos, double cx, double cy, double cz,
                                   float radius, int color, float age) {
        if (radius <= 0.01f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        Vector3f norm = new Vector3f(0.28f, 0.93f, 0.22f).normalize();
        Vector3f u = new Vector3f(-norm.z, 0, norm.x).normalize();
        Vector3f v = new Vector3f(norm).cross(u).normalize();

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist > 0.01f) {
            toCamX /= dist; toCamY /= dist; toCamZ /= dist;
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segs = 32;
        float innerR = radius * 1.15f;
        float outerR = radius * 2.85f;
        float rot = age * 16.0f;

        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs) + rot;
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs) + rot;

            float c0 = (float) Math.cos(a0); float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1); float s1 = (float) Math.sin(a1);

            float d0x = u.x * c0 + v.x * s0; float d0y = u.y * c0 + v.y * s0; float d0z = u.z * c0 + v.z * s0;
            float d1x = u.x * c1 + v.x * s1; float d1y = u.y * c1 + v.y * s1; float d1z = u.z * c1 + v.z * s1;

            float tan0x = -u.x * s0 + v.x * c0; float tan0y = -u.y * s0 + v.y * c0; float tan0z = -u.z * s0 + v.z * c0;
            float doppler = MathHelper.clamp(tan0x * toCamX + tan0y * toCamY + tan0z * toCamZ, -0.6f, 0.6f);
            float intensity = 1.0f + doppler * 0.45f;

            int ir = MathHelper.clamp((int) (r * intensity + 45 * Math.max(0.0f, doppler)), 0, 255);
            int ig = MathHelper.clamp((int) (g * intensity + 45 * Math.max(0.0f, doppler)), 0, 255);
            int ib = MathHelper.clamp((int) (b * intensity + 70 * Math.max(0.0f, doppler)), 0, 255);
            int ia = MathHelper.clamp((int) (225 * intensity), 0, 255);

            lightBuf.vertex(px + d0x * innerR, py + d0y * innerR, pz + d0z * innerR).color(255, 255, 255, ia);
            lightBuf.vertex(px + d1x * innerR, py + d1y * innerR, pz + d1z * innerR).color(255, 255, 255, ia);
            lightBuf.vertex(px + d1x * outerR, py + d1y * outerR, pz + d1z * outerR).color(ir, ig, ib, 0);
            lightBuf.vertex(px + d0x * outerR, py + d0y * outerR, pz + d0z * outerR).color(ir, ig, ib, 0);
        }
    }

    private void renderInflowParticles(VertexConsumer lightBuf, SingularityInstance inst, Vec3d camPos,
                                       double cx, double cy, double cz, float rHole) {
        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        for (SingularityInflowParticle p : inst.inflowParticles) {
            if (inst.age < p.spawnDelay || inst.age >= p.spawnDelay + p.duration) continue;

            float prog = (inst.age - p.spawnDelay) / p.duration;
            float progOld = Math.max(0.0f, prog - 0.045f);

            float r = p.startR * (float) Math.pow(1.0f - prog, 2.1);
            if (r <= rHole * 0.95f) continue;
            float rOld = p.startR * (float) Math.pow(1.0f - progOld, 2.1);

            float a = p.startAngle + prog * p.spiralSpeed;
            float aOld = p.startAngle + progOld * p.spiralSpeed;

            float y = p.startY * (1.0f - prog * 0.88f);
            float yOld = p.startY * (1.0f - progOld * 0.88f);

            float x0 = px + (float) Math.cos(a) * r;
            float z0 = pz + (float) Math.sin(a) * r;
            float y0 = py + y;

            float x1 = px + (float) Math.cos(aOld) * rOld;
            float z1 = pz + (float) Math.sin(aOld) * rOld;
            float y1 = py + yOld;

            float dx = x0 - x1;
            float dy = y0 - y1;
            float dz = z0 - z1;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.001f) continue;

            float nx = -dz / len * 0.022f;
            float nz = dx / len * 0.022f;

            int alpha = (int) (240 * MathHelper.clamp(prog < 0.15f ? (prog / 0.15f) : (1.0f - (prog - 0.70f) / 0.30f), 0.0f, 1.0f));
            if (alpha <= 2) continue;

            int pr = (p.pColor >> 16) & 0xFF;
            int pg = (p.pColor >> 8) & 0xFF;
            int pb = p.pColor & 0xFF;

            lightBuf.vertex(x0 - nx, y0, z0 - nz).color(255, 255, 255, alpha);
            lightBuf.vertex(x1 - nx, y1, z1 - nz).color(pr, pg, pb, 0);
            lightBuf.vertex(x1 + nx, y1, z1 + nz).color(pr, pg, pb, 0);
            lightBuf.vertex(x0 + nx, y0, z0 + nz).color(255, 255, 255, alpha);
        }
    }

    private void emitSupernovaShockwave(VertexConsumer lightBuf, Vec3d camPos, double cx, double cy, double cz,
                                        float age, int color, boolean distortion) {
        float prog = (age - 0.70f) / 0.65f;
        if (prog < 0.0f || prog > 1.0f) {
            if (!distortion) return;
        }

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        Vector3f norm = new Vector3f(0.28f, 0.93f, 0.22f).normalize();
        Vector3f u = new Vector3f(-norm.z, 0, norm.x).normalize();
        Vector3f v = new Vector3f(norm).cross(u).normalize();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int segs = 36;

        if (prog >= 0.0f && prog <= 1.0f) {
            float ease = 1.0f - (float) Math.pow(1.0f - prog, 3.2);
            float baseOuterR = 0.15f + ease * 4.40f;
            int mainAlpha = (int) (255 * (1.0f - (float) Math.pow(prog, 1.4)));

            if (mainAlpha > 2) {
                float innerR = baseOuterR * (0.78f + 0.18f * prog);
                emitFlatWaveRing(lightBuf, px, py, pz, u, v, innerR, baseOuterR, 255, 255, 255, mainAlpha, r, g, b, 0, segs);

                if (distortion) {
                    float redProg = MathHelper.clamp((age - 0.70f) / 0.58f, 0.0f, 1.0f);
                    float redEase = 1.0f - (float) Math.pow(1.0f - redProg, 3.0);
                    float redOuterR = 0.15f + redEase * 4.75f;
                    float redInnerR = redOuterR * 0.88f;
                    int redAlpha = (int) (175 * (1.0f - redProg));
                    if (redAlpha > 2) {
                        emitFlatWaveRing(lightBuf, px, py, pz, u, v, redInnerR, redOuterR, 255, 60, 95, redAlpha, 255, 40, 60, 0, segs);
                    }

                    float blueProg = MathHelper.clamp((age - 0.70f) / 0.74f, 0.0f, 1.0f);
                    float blueEase = 1.0f - (float) Math.pow(1.0f - blueProg, 3.4);
                    float blueOuterR = 0.15f + blueEase * 4.05f;
                    float blueInnerR = blueOuterR * 0.85f;
                    int blueAlpha = (int) (185 * (1.0f - blueProg));
                    if (blueAlpha > 2) {
                        emitFlatWaveRing(lightBuf, px, py, pz, u, v, blueInnerR, blueOuterR, 40, 200, 255, blueAlpha, 10, 80, 255, 0, segs);
                    }
                }
            }
        }

        if (distortion) {
            float echo1Prog = (age - 0.88f) / 0.62f;
            if (echo1Prog >= 0.0f && echo1Prog <= 1.0f) {
                float echoEase = 1.0f - (float) Math.pow(1.0f - echo1Prog, 2.8);
                float echoOuterR = 0.12f + echoEase * 3.8f;
                float echoInnerR = echoOuterR * 0.90f;
                int echoAlpha = (int) (140 * (1.0f - echo1Prog));
                if (echoAlpha > 2) {
                    emitFlatWaveRing(lightBuf, px, py, pz, u, v, echoInnerR, echoOuterR, 255, 255, 255, echoAlpha, r, g, b, 0, segs);
                }
            }

            float echo2Prog = (age - 1.15f) / 0.70f;
            if (echo2Prog >= 0.0f && echo2Prog <= 1.0f) {
                float echoEase = 1.0f - (float) Math.pow(1.0f - echo2Prog, 2.5);
                float echoOuterR = 0.10f + echoEase * 3.2f;
                float echoInnerR = echoOuterR * 0.92f;
                int echoAlpha = (int) (95 * (1.0f - echo2Prog));
                if (echoAlpha > 2) {
                    emitFlatWaveRing(lightBuf, px, py, pz, u, v, echoInnerR, echoOuterR, 120, 220, 255, echoAlpha, 40, 100, 220, 0, segs);
                }
            }
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

    private void emitGravitationalLensingBubble(VertexConsumer lightBuf, Vec3d camPos,
                                                double cx, double cy, double cz, float age, int color) {
        float prog = (age - 0.70f) / 0.95f;
        if (prog < 0.0f || prog > 1.0f) return;

        float ease = 1.0f - (float) Math.pow(1.0f - prog, 2.5);
        float radius = 0.25f + ease * 4.2f;
        int alpha = (int) (160 * (1.0f - prog) * (prog < 0.15f ? (prog / 0.15f) : 1.0f));
        if (alpha <= 2) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int latRings = 8;
        int lonSegs = 16;

        for (int i = 0; i < latRings; i++) {
            float phi0 = (float) (-Math.PI * 0.5 + i * Math.PI / latRings);
            float phi1 = (float) (-Math.PI * 0.5 + (i + 1) * Math.PI / latRings);

            float y0 = (float) Math.sin(phi0) * radius;
            float y1 = (float) Math.sin(phi1) * radius;
            float r0 = (float) Math.cos(phi0) * radius;
            float r1 = (float) Math.cos(phi1) * radius;

            float midPhi = (phi0 + phi1) * 0.5f;
            float midCos = (float) Math.cos(midPhi);

            for (int j = 0; j < lonSegs; j++) {
                float th0 = (float) (j * 2.0 * Math.PI / lonSegs);
                float th1 = (float) ((j + 1) * 2.0 * Math.PI / lonSegs);

                float cth0 = (float) Math.cos(th0); float sth0 = (float) Math.sin(th0);
                float cth1 = (float) Math.cos(th1); float sth1 = (float) Math.sin(th1);

                float midTh = (th0 + th1) * 0.5f;
                float nx = (float) Math.cos(midTh) * midCos;
                float ny = (float) Math.sin(midPhi);
                float nz = (float) Math.sin(midTh) * midCos;

                float vx = (px + nx * radius);
                float vy = (py + ny * radius);
                float vz = (pz + nz * radius);
                float vdist = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vdist > 0.001f) {
                    vx /= vdist; vy /= vdist; vz /= vdist;
                }

                float dot = Math.abs(nx * vx + ny * vy + nz * vz);
                float fresnel = (float) Math.pow(1.0f - dot, 2.2);
                int ringAlpha = (int) (alpha * fresnel);
                if (ringAlpha <= 1) continue;

                float x00 = px + cth0 * r0; float z00 = pz + sth0 * r0;
                float x10 = px + cth1 * r0; float z10 = pz + sth1 * r0;
                float x11 = px + cth1 * r1; float z11 = pz + sth1 * r1;
                float x01 = px + cth0 * r1; float z01 = pz + sth0 * r1;

                int fr = MathHelper.clamp((int) (r * 0.5f + 120), 0, 255);
                int fg = MathHelper.clamp((int) (g * 0.5f + 160), 0, 255);
                int fb = MathHelper.clamp((int) (b * 0.5f + 255), 0, 255);

                lightBuf.vertex(x00, py + y0, z00).color(fr, fg, fb, ringAlpha);
                lightBuf.vertex(x10, py + y0, z10).color(fr, fg, fb, ringAlpha);
                lightBuf.vertex(x11, py + y1, z11).color(fr, fg, fb, ringAlpha);
                lightBuf.vertex(x01, py + y1, z01).color(fr, fg, fb, ringAlpha);
            }
        }
    }

    private void renderSpacetimeRiftQuads(VertexConsumer quadBuf, SingularityInstance inst,
                                          Vec3d camPos, double cx, double cy, double cz, float age) {
        if (age < 0.01f || age > 1.88f) return;

        float progOpen = MathHelper.clamp(age / 0.20f, 0.0f, 1.0f);
        float closeProg = MathHelper.clamp((age - 1.60f) / 0.28f, 0.0f, 1.0f);
        float globalScale = progOpen * (1.0f - (float) Math.pow(closeProg, 2.5));
        if (globalScale <= 0.005f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist > 0.01f) {
            toCamX /= dist; toCamY /= dist; toCamZ /= dist;
        }

        float ux = -toCamZ;
        float uz = toCamX;
        float uLen = (float) Math.sqrt(ux * ux + uz * uz);
        if (uLen > 0.001f) {
            ux /= uLen; uz /= uLen;
        } else {
            ux = 1.0f; uz = 0.0f;
        }

        int n = inst.riftVertices.size();
        if (n < 2) return;

        float maxHalfW = 0.12f * globalScale;
        Vector3f[] leftPts = new Vector3f[n];
        Vector3f[] rightPts = new Vector3f[n];

        for (int i = 0; i < n; i++) {
            SingularityRiftVertex rv = inst.riftVertices.get(i);
            float normY = Math.abs(rv.yOffset) / 0.90f;
            float localHeal = Math.min(1.0f, closeProg * (1.0f + normY * 1.5f));
            float localW = maxHalfW * (1.0f - normY * 0.65f) * (1.0f - localHeal);

            float jitter = (float) Math.sin(age * 22.0f + rv.phase) * 0.025f * (1.0f - closeProg);
            float vx = px + (rv.ox + jitter * ux) * globalScale;
            float vy = py + rv.yOffset * globalScale;
            float vz = pz + (rv.oz + jitter * uz) * globalScale;

            leftPts[i] = new Vector3f(vx - ux * localW, vy, vz - uz * localW);
            rightPts[i] = new Vector3f(vx + ux * localW, vy, vz + uz * localW);
        }

        for (int i = 0; i < n - 1; i++) {
            Vector3f l0 = leftPts[i];
            Vector3f l1 = leftPts[i + 1];
            Vector3f r0 = rightPts[i];
            Vector3f r1 = rightPts[i + 1];

            quadBuf.vertex(l0.x, l0.y, l0.z).color(1, 1, 2, 255);
            quadBuf.vertex(r0.x, r0.y, r0.z).color(1, 1, 2, 255);
            quadBuf.vertex(r1.x, r1.y, r1.z).color(1, 1, 2, 255);
            quadBuf.vertex(l1.x, l1.y, l1.z).color(1, 1, 2, 255);
        }
    }

    private void renderSpacetimeRiftGlowAndArcs(VertexConsumer lightBuf, SingularityInstance inst,
                                                Vec3d camPos, double cx, double cy, double cz, float age, int color) {
        if (age < 0.01f || age > 1.88f) return;

        float progOpen = MathHelper.clamp(age / 0.20f, 0.0f, 1.0f);
        float closeProg = MathHelper.clamp((age - 1.60f) / 0.28f, 0.0f, 1.0f);
        float globalScale = progOpen * (1.0f - (float) Math.pow(closeProg, 2.5));
        if (globalScale <= 0.005f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist > 0.01f) {
            toCamX /= dist; toCamY /= dist; toCamZ /= dist;
        }

        float ux = -toCamZ;
        float uz = toCamX;
        float uLen = (float) Math.sqrt(ux * ux + uz * uz);
        if (uLen > 0.001f) {
            ux /= uLen; uz /= uLen;
        } else {
            ux = 1.0f; uz = 0.0f;
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int alpha = (int) (245 * (1.0f - closeProg * 0.85f));

        int n = inst.riftVertices.size();
        if (n < 2) return;

        float maxHalfW = 0.12f * globalScale;
        float glowMargin = 0.07f * globalScale;

        Vector3f[] leftPts = new Vector3f[n];
        Vector3f[] rightPts = new Vector3f[n];
        Vector3f[] leftGlowPts = new Vector3f[n];
        Vector3f[] rightGlowPts = new Vector3f[n];

        for (int i = 0; i < n; i++) {
            SingularityRiftVertex rv = inst.riftVertices.get(i);
            float normY = Math.abs(rv.yOffset) / 0.90f;
            float localHeal = Math.min(1.0f, closeProg * (1.0f + normY * 1.5f));
            float localW = maxHalfW * (1.0f - normY * 0.65f) * (1.0f - localHeal);

            float jitter = (float) Math.sin(age * 22.0f + rv.phase) * 0.025f * (1.0f - closeProg);
            float vx = px + (rv.ox + jitter * ux) * globalScale;
            float vy = py + rv.yOffset * globalScale;
            float vz = pz + (rv.oz + jitter * uz) * globalScale;

            leftPts[i] = new Vector3f(vx - ux * localW, vy, vz - uz * localW);
            rightPts[i] = new Vector3f(vx + ux * localW, vy, vz + uz * localW);
            leftGlowPts[i] = new Vector3f(vx - ux * (localW + glowMargin), vy, vz - uz * (localW + glowMargin));
            rightGlowPts[i] = new Vector3f(vx + ux * (localW + glowMargin), vy, vz + uz * (localW + glowMargin));
        }

        for (int i = 0; i < n - 1; i++) {
            Vector3f l0 = leftPts[i];
            Vector3f l1 = leftPts[i + 1];
            Vector3f r0 = rightPts[i];
            Vector3f r1 = rightPts[i + 1];

            Vector3f lg0 = leftGlowPts[i];
            Vector3f lg1 = leftGlowPts[i + 1];
            Vector3f rg0 = rightGlowPts[i];
            Vector3f rg1 = rightGlowPts[i + 1];

            lightBuf.vertex(l0.x, l0.y, l0.z).color(255, 255, 255, alpha);
            lightBuf.vertex(l1.x, l1.y, l1.z).color(255, 255, 255, alpha);
            lightBuf.vertex(lg1.x, lg1.y, lg1.z).color(r, g, b, 0);
            lightBuf.vertex(lg0.x, lg0.y, lg0.z).color(r, g, b, 0);

            lightBuf.vertex(r0.x, r0.y, r0.z).color(255, 255, 255, alpha);
            lightBuf.vertex(rg0.x, rg0.y, rg0.z).color(r, g, b, 0);
            lightBuf.vertex(rg1.x, rg1.y, rg1.z).color(r, g, b, 0);
            lightBuf.vertex(r1.x, r1.y, r1.z).color(255, 255, 255, alpha);
        }

        if (age < 1.60f && (random.nextFloat() < 0.35f)) {
            int i0 = random.nextInt(n);
            int i1 = Math.clamp(i0 + (random.nextBoolean() ? 1 : -1), 0, n - 1);
            Vector3f pStart = random.nextBoolean() ? leftPts[i0] : rightPts[i0];
            Vector3f pEnd = random.nextBoolean() ? leftPts[i1] : rightPts[i1];
            float midArcX = (pStart.x + pEnd.x) * 0.5f + (random.nextFloat() - 0.5f) * 0.12f;
            float midArcY = (pStart.y + pEnd.y) * 0.5f + (random.nextFloat() - 0.5f) * 0.08f;
            float midArcZ = (pStart.z + pEnd.z) * 0.5f + (random.nextFloat() - 0.5f) * 0.12f;
            emitLineQuad(lightBuf, pStart, new Vector3f(midArcX, midArcY, midArcZ), 0.015f, 255, 255, 255, alpha);
            emitLineQuad(lightBuf, new Vector3f(midArcX, midArcY, midArcZ), pEnd, 0.015f, r, g, b, alpha);
        }
    }

    private void updateAndRenderNebulaClouds(VertexConsumer lightBuf, SingularityInstance inst,
                                             Vec3d camPos, double cx, double cy, double cz, float dt) {
        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist > 0.01f) {
            toCamX /= dist; toCamY /= dist; toCamZ /= dist;
        }

        float ux, uy, uz;
        if (Math.abs(toCamY) < 0.95f) {
            ux = -toCamZ; uy = 0; uz = toCamX;
            float uLen = (float) Math.sqrt(ux * ux + uz * uz);
            ux /= uLen; uz /= uLen;
        } else {
            ux = 0; uy = -toCamZ; uz = toCamY;
            float uLen = (float) Math.sqrt(uy * uy + uz * uz);
            uy /= uLen; uz /= uLen;
        }
        float vx = toCamY * uz - toCamZ * uy;
        float vy = toCamZ * ux - toCamX * uz;
        float vz = toCamX * uy - toCamY * ux;

        for (SingularityNebulaCloud cloud : inst.nebulaClouds) {
            cloud.age += dt;
            if (cloud.age >= cloud.maxLife) continue;

            cloud.x += cloud.vx * dt;
            cloud.y += cloud.vy * dt;
            cloud.z += cloud.vz * dt;

            cloud.vx *= (1.0f - 1.1f * dt);
            cloud.vy = cloud.vy * (1.0f - 1.1f * dt) + 0.12f * dt;
            cloud.vz *= (1.0f - 1.1f * dt);

            float curDist = (float) Math.sqrt(cloud.x * cloud.x + cloud.z * cloud.z);
            if (curDist > 0.01f) {
                float angle = cloud.swirl * dt * 0.75f;
                float cosA = (float) Math.cos(angle);
                float sinA = (float) Math.sin(angle);
                float nx = cloud.x * cosA - cloud.z * sinA;
                float nz = cloud.x * sinA + cloud.z * cosA;
                cloud.x = nx;
                cloud.z = nz;
            }

            float prog = cloud.age / cloud.maxLife;
            float curR = cloud.baseSize + cloud.growth * (float) Math.pow(prog, 0.65);

            float alphaProg = prog < 0.18f ? (prog / 0.18f) : (1.0f - (prog - 0.18f) / 0.82f);
            int a = (int) (125 * MathHelper.clamp(alphaProg, 0.0f, 1.0f));
            if (a <= 2) continue;

            int cr = (cloud.color >> 16) & 0xFF;
            int cg = (cloud.color >> 8) & 0xFF;
            int cb = cloud.color & 0xFF;

            float cx0 = px + cloud.x;
            float cy0 = py + cloud.y;
            float cz0 = pz + cloud.z;

            int segs = 8;
            for (int s = 0; s < segs; s++) {
                float a0 = (float) (s * 2.0 * Math.PI / segs);
                float a1 = (float) ((s + 1) * 2.0 * Math.PI / segs);

                float c0 = (float) Math.cos(a0); float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1); float s1 = (float) Math.sin(a1);

                float d0x = (ux * c0 + vx * s0) * curR;
                float d0y = (uy * c0 + vy * s0) * curR;
                float d0z = (uz * c0 + vz * s0) * curR;

                float d1x = (ux * c1 + vx * s1) * curR;
                float d1y = (uy * c1 + vy * s1) * curR;
                float d1z = (uz * c1 + vz * s1) * curR;

                lightBuf.vertex(cx0, cy0, cz0).color(cr, cg, cb, a);
                lightBuf.vertex(cx0 + d0x, cy0 + d0y, cz0 + d0z).color(cr, cg, cb, 0);
                lightBuf.vertex(cx0 + d1x, cy0 + d1y, cz0 + d1z).color(cr, cg, cb, 0);
                lightBuf.vertex(cx0, cy0, cz0).color(cr, cg, cb, a);
            }
        }
    }

    private void updateAndRenderQuantumEmbers(VertexConsumer lightBuf, SingularityInstance inst,
                                              Vec3d camPos, double cx, double cy, double cz, float dt) {
        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        int r = (inst.color >> 16) & 0xFF;
        int g = (inst.color >> 8) & 0xFF;
        int b = inst.color & 0xFF;

        for (SingularityQuantumEmber ember : inst.quantumEmbers) {
            ember.age += dt;
            if (ember.age >= ember.life) continue;

            float prog = ember.age / ember.life;
            ember.y += ember.vy * dt;
            ember.x += ember.vx * dt + (float) Math.cos(ember.age * ember.freq + ember.phase) * 0.15f * dt;
            ember.z += ember.vz * dt + (float) Math.sin(ember.age * ember.freq + ember.phase) * 0.15f * dt;

            float pulse = 0.6f + 0.4f * (float) Math.sin(ember.age * 12.0f + ember.phase);
            float alphaProg = prog < 0.15f ? (prog / 0.15f) : (1.0f - (prog - 0.15f) / 0.85f);
            int a = (int) (235 * MathHelper.clamp(alphaProg, 0.0f, 1.0f) * pulse);
            if (a <= 2) continue;

            float sx = px + ember.x;
            float sy = py + ember.y;
            float sz = pz + ember.z;
            float szQuad = ember.size * (1.0f - prog * 0.4f);

            lightBuf.vertex(sx - szQuad, sy - szQuad, sz).color(r, g, b, 0);
            lightBuf.vertex(sx + szQuad, sy - szQuad, sz).color(255, 255, 255, a);
            lightBuf.vertex(sx + szQuad, sy + szQuad, sz).color(255, 255, 255, a);
            lightBuf.vertex(sx - szQuad, sy + szQuad, sz).color(r, g, b, 0);
        }
    }

    private void emitNovaRays(VertexConsumer lightBuf, SingularityInstance inst, Vec3d camPos,
                              double cx, double cy, double cz, float age, int color) {
        float prog = (age - 0.70f) / 0.52f;
        if (prog < 0.0f || prog > 1.0f) return;

        float px = (float) (cx - camPos.x);
        float py = (float) (cy - camPos.y);
        float pz = (float) (cz - camPos.z);

        float toCamX = -px;
        float toCamY = -py;
        float toCamZ = -pz;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        if (dist > 0.01f) {
            toCamX /= dist; toCamY /= dist; toCamZ /= dist;
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float lenProg = prog < 0.25f ? (prog / 0.25f) : 1.0f;
        float alphaProg = prog < 0.15f ? (prog / 0.15f) : (1.0f - (prog - 0.15f) / 0.85f);
        int alpha = (int) (255 * MathHelper.clamp(alphaProg, 0.0f, 1.0f));
        if (alpha <= 2) return;

        for (SingularityNovaRay ray : inst.novaRays) {
            float curLen = ray.maxLen * (0.15f + 0.85f * (float) Math.pow(lenProg, 0.5));
            float curWidth = ray.width * (1.0f - prog * 0.65f);

            float wx = ray.dy * toCamZ - ray.dz * toCamY;
            float wy = ray.dz * toCamX - ray.dx * toCamZ;
            float wz = ray.dx * toCamY - ray.dy * toCamX;
            float wLen = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wLen > 0.001f) {
                wx = (wx / wLen) * curWidth;
                wy = (wy / wLen) * curWidth;
                wz = (wz / wLen) * curWidth;
            }

            float midX = px + ray.dx * (curLen * 0.40f);
            float midY = py + ray.dy * (curLen * 0.40f);
            float midZ = pz + ray.dz * (curLen * 0.40f);

            float tipX = px + ray.dx * curLen;
            float tipY = py + ray.dy * curLen;
            float tipZ = pz + ray.dz * curLen;

            lightBuf.vertex(px, py, pz).color(255, 255, 255, alpha);
            lightBuf.vertex(midX - wx, midY - wy, midZ - wz).color(r, g, b, (int) (alpha * 0.65f));
            lightBuf.vertex(tipX, tipY, tipZ).color(r, g, b, 0);
            lightBuf.vertex(midX + wx, midY + wy, midZ + wz).color(r, g, b, (int) (alpha * 0.65f));
        }
    }

    private void updateAndRenderStardust(VertexConsumer lightBuf, SingularityInstance inst, Vec3d camPos, float dt) {
        float px = (float) (inst.x - camPos.x);
        float py = (float) (inst.y + inst.height * 0.55 - camPos.y);
        float pz = (float) (inst.z - camPos.z);

        int r = (inst.color >> 16) & 0xFF;
        int g = (inst.color >> 8) & 0xFF;
        int b = inst.color & 0xFF;

        for (SingularityStardust spark : inst.stardust) {
            spark.age += dt;
            if (spark.age >= spark.life) continue;

            spark.x += spark.vx * dt;
            spark.y += spark.vy * dt;
            spark.z += spark.vz * dt;

            spark.vx *= (1.0f - 1.8f * dt);
            spark.vy *= (1.0f - 1.8f * dt);
            spark.vz *= (1.0f - 1.8f * dt);

            float prog = spark.age / spark.life;
            int a = (int) (255 * (1.0f - prog));
            if (a <= 2) continue;

            float sx = (float) (px + spark.x);
            float sy = (float) (py + spark.y);
            float sz = (float) (pz + spark.z);
            float szQuad = spark.size * (1.0f - prog * 0.5f);

            lightBuf.vertex(sx - szQuad, sy - szQuad, sz).color(r, g, b, 0);
            lightBuf.vertex(sx + szQuad, sy - szQuad, sz).color(255, 255, 255, a);
            lightBuf.vertex(sx + szQuad, sy + szQuad, sz).color(255, 255, 255, a);
            lightBuf.vertex(sx - szQuad, sy + szQuad, sz).color(r, g, b, 0);
        }
    }

    private static class SingularityInflowParticle {
        final float startR;
        final float startAngle;
        final float startY;
        final float spawnDelay;
        final float duration;
        final float spiralSpeed;
        final int pColor;

        SingularityInflowParticle(float startR, float startAngle, float startY,
                                  float spawnDelay, float duration, float spiralSpeed, int pColor) {
            this.startR = startR;
            this.startAngle = startAngle;
            this.startY = startY;
            this.spawnDelay = spawnDelay;
            this.duration = duration;
            this.spiralSpeed = spiralSpeed;
            this.pColor = pColor;
        }
    }

    private static class SingularityNovaRay {
        final float dx, dy, dz;
        final float maxLen;
        final float width;

        SingularityNovaRay(float dx, float dy, float dz, float maxLen, float width) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.maxLen = maxLen;
            this.width = width;
        }
    }

    private static class SingularityStardust {
        double x, y, z;
        float vx, vy, vz;
        float size;
        float life;
        float age = 0.0f;

        SingularityStardust(double x, double y, double z, float vx, float vy, float vz, float size, float life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
        }
    }

    private static class SingularityRiftVertex {
        final float ox, yOffset, oz, phase;

        SingularityRiftVertex(float ox, float yOffset, float oz, float phase) {
            this.ox = ox;
            this.yOffset = yOffset;
            this.oz = oz;
            this.phase = phase;
        }
    }

    private static class SingularityNebulaCloud {
        float x, y, z;
        float vx, vy, vz;
        final float swirl;
        final float baseSize;
        final float growth;
        final float maxLife;
        final int color;
        float age = 0.0f;

        SingularityNebulaCloud(float x, float y, float z, float vx, float vy, float vz,
                               float swirl, float baseSize, float growth, float maxLife, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.swirl = swirl;
            this.baseSize = baseSize;
            this.growth = growth;
            this.maxLife = maxLife;
            this.color = color;
        }
    }

    private static class SingularityQuantumEmber {
        float x, y, z;
        final float vx, vy, vz;
        final float size;
        final float life;
        final float freq;
        final float phase;
        float age = 0.0f;

        SingularityQuantumEmber(float x, float y, float z, float vx, float vy, float vz,
                                float size, float life, float freq, float phase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.freq = freq;
            this.phase = phase;
        }
    }

    private static class SingularityInstance {
        final double x, y, z;
        final double cx, cy, cz;
        final float height;
        final float yaw, pitch, headYaw;
        final boolean isSneaking;
        final float rLegPitch, lLegPitch, rArmPitch, lArmPitch, rArmRoll;
        final float baseRadius;
        final int inflowCount;
        final boolean raysEnabled;
        final boolean riftEnabled;
        final boolean distortionEnabled;
        final int color;
        final Identifier skinTexture;
        final boolean isSlim;
        final Identifier armorLayer1;
        final Identifier armorLayer2;
        final boolean hasHelmet;
        final boolean hasChestplate;
        final boolean hasLeggings;
        final boolean hasBoots;
        float age = 0.0f;
        final float totalDuration = 2.85f;
        boolean soundPlayedBoom = false;
        final List<SingularityInflowParticle> inflowParticles = new ArrayList<>();
        final List<SingularityNovaRay> novaRays = new ArrayList<>();
        final List<SingularityStardust> stardust = new ArrayList<>();
        final List<SingularityNebulaCloud> nebulaClouds = new ArrayList<>();
        final List<SingularityRiftVertex> riftVertices = new ArrayList<>();
        final List<SingularityQuantumEmber> quantumEmbers = new ArrayList<>();

        SingularityInstance(double x, double y, double z, float height, float yaw, float pitch, float headYaw, boolean isSneaking,
                            float rLegPitch, float lLegPitch, float rArmPitch, float lArmPitch, float rArmRoll,
                            float baseRadius, int inflowCount, boolean raysEnabled, boolean riftEnabled, boolean distortionEnabled,
                            int color, Identifier skinTexture, boolean isSlim, Identifier armorLayer1, Identifier armorLayer2,
                            boolean hasHelmet, boolean hasChestplate, boolean hasLeggings, boolean hasBoots, Random random) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.height = height;
            this.yaw = yaw;
            this.pitch = pitch;
            this.headYaw = headYaw;
            this.isSneaking = isSneaking;
            this.rLegPitch = rLegPitch;
            this.lLegPitch = lLegPitch;
            this.rArmPitch = rArmPitch;
            this.lArmPitch = lArmPitch;
            this.rArmRoll = rArmRoll;
            this.baseRadius = baseRadius;
            this.inflowCount = inflowCount;
            this.raysEnabled = raysEnabled;
            this.riftEnabled = riftEnabled;
            this.distortionEnabled = distortionEnabled;
            this.color = color;
            this.skinTexture = skinTexture;
            this.isSlim = isSlim;
            this.armorLayer1 = armorLayer1;
            this.armorLayer2 = armorLayer2;
            this.hasHelmet = hasHelmet;
            this.hasChestplate = hasChestplate;
            this.hasLeggings = hasLeggings;
            this.hasBoots = hasBoots;

            float yawRad = (float) Math.toRadians(-yaw);
            double riftDist = 1.45;
            this.cx = x - Math.sin(yawRad) * riftDist;
            this.cy = y + height * 0.55;
            this.cz = z + Math.cos(yawRad) * riftDist;

            for (int i = 0; i < inflowCount; i++) {
                float startR = 1.8f + random.nextFloat() * 1.5f;
                float startAngle = random.nextFloat() * (float) Math.PI * 2.0f;
                float startY = (random.nextFloat() - 0.5f) * 1.6f;
                float spawnDelay = 0.04f + random.nextFloat() * 0.38f;
                float duration = 0.26f + random.nextFloat() * 0.20f;
                float spiralSpeed = 14.0f + random.nextFloat() * 12.0f;
                int pColor = (random.nextFloat() < 0.25f) ? 0xFFFFFFFF : color;
                inflowParticles.add(new SingularityInflowParticle(startR, startAngle, startY, spawnDelay, duration, spiralSpeed, pColor));
            }

            if (raysEnabled) {
                int rayCount = 22;
                for (int i = 0; i < rayCount; i++) {
                    float theta = random.nextFloat() * (float) Math.PI * 2.0f;
                    float zDir = (random.nextFloat() * 2.0f) - 1.0f;
                    float sinZ = (float) Math.sqrt(Math.max(0.0f, 1.0f - zDir * zDir));
                    float dx = (float) Math.cos(theta) * sinZ;
                    float dy = (float) Math.sin(theta) * sinZ;
                    float dz = zDir;
                    float maxLen = 2.4f + random.nextFloat() * 1.5f;
                    float width = 0.07f + random.nextFloat() * 0.06f;
                    novaRays.add(new SingularityNovaRay(dx, dy, dz, maxLen, width));
                }
            }

            if (riftEnabled) {
                int vCount = 16;
                for (int i = 0; i < vCount; i++) {
                    float yOffset = -0.90f + i * (1.80f / (vCount - 1));
                    float envelope = 1.0f - Math.abs(yOffset) / 0.90f;
                    float ox = (random.nextFloat() - 0.5f) * 0.16f * envelope;
                    float oz = (random.nextFloat() - 0.5f) * 0.16f * envelope;
                    float phase = random.nextFloat() * (float) Math.PI * 2.0f;
                    riftVertices.add(new SingularityRiftVertex(ox, yOffset, oz, phase));
                }
            }
        }

        void spawnSupernovaSparks(Random random, KillEffect parent, double cx, double cy, double cz) {
            if (distortionEnabled) {
                parent.triggerCameraShockwave(cx, cy, cz);
            }

            int sparkCount = 38;
            for (int i = 0; i < sparkCount; i++) {
                float theta = random.nextFloat() * (float) Math.PI * 2.0f;
                float zDir = (random.nextFloat() * 2.0f) - 1.0f;
                float sinZ = (float) Math.sqrt(Math.max(0.0f, 1.0f - zDir * zDir));
                float spd = 2.8f + random.nextFloat() * 4.8f;
                float vx = (float) Math.cos(theta) * sinZ * spd;
                float vy = (float) Math.sin(theta) * sinZ * spd;
                float vz = zDir * spd;
                float size = 0.035f + random.nextFloat() * 0.045f;
                float life = 0.40f + random.nextFloat() * 0.30f;
                stardust.add(new SingularityStardust(0, 0, 0, vx, vy, vz, size, life));
            }

            if (riftEnabled) {
                int cloudCount = 46;
                for (int i = 0; i < cloudCount; i++) {
                    float theta = random.nextFloat() * (float) Math.PI * 2.0f;
                    float dist = 0.15f + random.nextFloat() * 0.65f;
                    float startX = (float) Math.cos(theta) * dist;
                    float startY = (random.nextFloat() - 0.5f) * 0.85f;
                    float startZ = (float) Math.sin(theta) * dist;

                    float expSpeed = 0.6f + random.nextFloat() * 1.8f;
                    float vx = (startX / Math.max(0.01f, dist)) * expSpeed;
                    float vy = (random.nextFloat() - 0.4f) * 0.6f;
                    float vz = (startZ / Math.max(0.01f, dist)) * expSpeed;

                    float swirl = (random.nextFloat() > 0.5f ? 1.0f : -1.0f) * (0.8f + random.nextFloat() * 1.4f);
                    float baseSize = 0.22f + random.nextFloat() * 0.28f;
                    float growth = 0.35f + random.nextFloat() * 0.45f;
                    float maxLife = 1.4f + random.nextFloat() * 0.75f;

                    int cloudColor;
                    float cPick = random.nextFloat();
                    if (cPick < 0.35f) {
                        cloudColor = color;
                    } else if (cPick < 0.65f) {
                        cloudColor = 0xFF552288;
                    } else if (cPick < 0.85f) {
                        cloudColor = 0xFF114488;
                    } else {
                        cloudColor = 0xFF2277AA;
                    }

                    nebulaClouds.add(new SingularityNebulaCloud(
                            startX, startY, startZ, vx, vy, vz, swirl,
                            baseSize, growth, maxLife, cloudColor
                    ));
                }

                int emberCount = 30;
                for (int i = 0; i < emberCount; i++) {
                    float ox = (random.nextFloat() - 0.5f) * 0.5f;
                    float oy = (random.nextFloat() - 0.5f) * 0.4f;
                    float oz = (random.nextFloat() - 0.5f) * 0.5f;
                    float vy = 0.35f + random.nextFloat() * 0.75f;
                    float vx = (random.nextFloat() - 0.5f) * 0.25f;
                    float vz = (random.nextFloat() - 0.5f) * 0.25f;
                    float size = 0.025f + random.nextFloat() * 0.035f;
                    float life = 1.2f + random.nextFloat() * 0.85f;
                    float freq = 2.0f + random.nextFloat() * 4.0f;
                    float phase = random.nextFloat() * (float) Math.PI * 2.0f;
                    quantumEmbers.add(new SingularityQuantumEmber(ox, oy, oz, vx, vy, vz, size, life, freq, phase));
                }
            }
        }
    }
}
