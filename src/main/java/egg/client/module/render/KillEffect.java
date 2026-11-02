package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

    private final ModeSetting mode = new ModeSetting("Эффект", "Стекло", "Тип эффекта при смерти противника", "Стекло", "Лотос");
    private final BooleanSetting onlyTarget = new BooleanSetting("Только цель", true, "Создавать эффект только для атакованных вами врагов");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false, "Создавать эффект только при смерти игроков");
    private final BooleanSetting sound = new BooleanSetting("Звуки", true, "Звуковые эффекты при убийстве");
    private final BooleanSetting syncColor = new BooleanSetting("Синхр. цвета", true, "Использовать цвет темы клиента");
    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFF2555, "Пользовательский цвет эффекта");

    // Настройки для режима Стекло
    private final SliderSetting amount = new SliderSetting("Осколки стекла", 85, 20, 250, 5, "Количество 3D осколков стекла");
    private final SliderSetting size = new SliderSetting("Размер стекла", 0.22, 0.05, 0.60, 0.01, "Размер осколков стекла");
    private final SliderSetting speed = new SliderSetting("Скорость стекла", 6.5, 2.0, 15.0, 0.5, "Скорость разлета осколков во все стороны");
    private final SliderSetting lifetime = new SliderSetting("Жизнь стекла", 2.0, 0.5, 5.0, 0.1, "Длительность существования осколков (сек)");
    private final BooleanSetting bounce = new BooleanSetting("Отскок стекла", true, "Отскок осколков стекла от блоков и пола");
    private final BooleanSetting sparkle = new BooleanSetting("Блики стекла", true, "Яркие вспышки и отблески на гранях стекла");

    // Настройки для режима Лотос
    private final SliderSetting vineCount = new SliderSetting("Ветви лотоса", 6, 4, 8, 1, "Количество обвивающих ветвей лотоса");
    private final SliderSetting lotusScale = new SliderSetting("Масштаб лотоса", 1.0, 0.5, 2.0, 0.1, "Общий масштаб цветка и ветвей");

    private final List<DeathGlassShard> particles = new ArrayList<>();
    private final List<LotusKillInstance> lotusInstances = new ArrayList<>();
    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();
    private final Set<Integer> deadEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    private long lastFrameNanos = 0L;

    public KillEffect() {
        super("KillEffect", "Кастомные 3D килл-эффекты при смерти противника (Стекло, Лотос)", Category.ESP);
        instance = this;
        addSettings(mode, onlyTarget, onlyPlayers, sound, syncColor, color, vineCount, lotusScale,
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
            if ("Лотос".equals(mode.get())) {
                spawnLotusEffect(entity);
            } else {
                spawnGlassBurst(entity);
            }
        }
    }

    // =========================================================================
    // Режим: Лотос (Spider Lily & Entangling Vines)
    // =========================================================================
    public void spawnLotusEffect(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double bx = entity.getX();
        double by = entity.getY();
        double bz = entity.getZ();
        double h = Math.max(1.2, entity.getHeight());
        int tint = syncColor.isEnabled() ? Theme.getAccent() : color.get();
        int vines = (int) vineCount.get();
        float scale = (float) lotusScale.get();

        if (sound.isEnabled() && mc.world != null) {
            mc.world.playSoundClient(bx, by, bz,
                    SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS,
                    1.4f, 0.85f + random.nextFloat() * 0.15f, false);
            mc.world.playSoundClient(bx, by, bz,
                    SoundEvents.BLOCK_VINE_PLACE, SoundCategory.PLAYERS,
                    1.2f, 0.8f, false);
        }

        lotusInstances.add(new LotusKillInstance(bx, by, bz, h, tint, vines, scale));
        while (lotusInstances.size() > 10) {
            lotusInstances.remove(0);
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
            if (!particles.isEmpty()) particles.clear();
            if (!lotusInstances.isEmpty()) lotusInstances.clear();
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

        // Отрисовка осколков стекла
        if (!particles.isEmpty()) {
            renderGlassParticles(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }

        // Отрисовка эффекта Лотоса
        if (!lotusInstances.isEmpty()) {
            renderLotusInstances(quadBuffer, lightBuffer, camPos, lightDir, dt, mc);
        }
    }

    // =========================================================================
    // Отрисовка и обновление Лотоса
    // =========================================================================
    private void renderLotusInstances(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                      Vec3d camPos, Vector3f lightDir, float dt, MinecraftClient mc) {
        Iterator<LotusKillInstance> it = lotusInstances.iterator();
        while (it.hasNext()) {
            LotusKillInstance inst = it.next();
            inst.age += dt;
            if (inst.age >= inst.duration) {
                it.remove();
                continue;
            }

            // Фазовые звуки
            if (inst.age >= 0.45f && !inst.soundPlayedPhase2) {
                inst.soundPlayedPhase2 = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y + inst.entityHeight * 0.5, inst.z,
                            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS,
                            1.3f, 0.70f, false);
                    mc.world.playSoundClient(inst.x, inst.y + inst.entityHeight * 0.5, inst.z,
                            SoundEvents.BLOCK_VINE_PLACE, SoundCategory.PLAYERS,
                            1.1f, 0.75f, false);
                }
            }

            if (inst.age >= 0.85f && !inst.soundPlayedPhase3) {
                inst.soundPlayedPhase3 = true;
                if (sound.isEnabled() && mc.world != null) {
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.PLAYERS,
                            1.2f, 1.35f, false);
                    mc.world.playSoundClient(inst.x, inst.y, inst.z,
                            SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS,
                            1.1f, 0.55f, false);
                }
                // При начале затягивания под землю с цветка срываются лепестки
                inst.spawnBurstPetals(random);
            }

            // Вычисление смещения утягивания под землю
            float dragDown = 0.0f;
            if (inst.age >= 0.85f) {
                float dragProg = MathHelper.clamp((inst.age - 0.85f) / 0.85f, 0.0f, 1.0f);
                // Плавное кубическое ускорение рывка под землю
                dragDown = (float) (Math.pow(dragProg, 2.4) * (inst.entityHeight + 1.6) * inst.scale);
            }
            inst.currentDrag = dragDown;

            // 1. Отрисовка земляного разлома и корней на земле
            renderAbyssalRift(quadBuf, lightBuf, inst, camPos);

            // 2. Отрисовка обвивающих 3D ветвей
            renderEntanglingVines(quadBuf, inst, camPos, lightDir);

            // 3. Отрисовка распускающегося цветка Spider Lily / Lotus
            renderLotusFlower(quadBuf, lightBuf, inst, camPos, lightDir);

            // 4. Обновление и отрисовка сорванных лепестков и пыльцы
            inst.updateAndRenderParticles(quadBuf, lightBuf, camPos, lightDir, dt);
        }
    }

    private void renderAbyssalRift(VertexConsumer quadBuf, VertexConsumer lightBuf, LotusKillInstance inst, Vec3d camPos) {
        float age = inst.age;
        float openFactor = MathHelper.clamp(age / 0.40f, 0.0f, 1.0f);
        float fade = 1.0f;
        if (age > 1.8f) {
            fade = MathHelper.clamp(1.0f - (age - 1.8f) / 0.6f, 0.0f, 1.0f);
        }
        if (fade <= 0.01f || openFactor <= 0.01f) return;

        float radius = 0.95f * inst.scale * openFactor;
        float groundY = (float) (inst.y + 0.02 - camPos.y);
        float cx = (float) (inst.x - camPos.x);
        float cz = (float) (inst.z - camPos.z);

        int tint = inst.tintColor;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;

        int segments = 18;
        float pulse = 0.85f + 0.15f * (float) Math.sin(age * 12.0);
        int alphaVoid = (int) (220 * fade * pulse);
        int alphaEdge = (int) (255 * fade * pulse);

        // Внутренний темный разлом под землей
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;

            float x0 = cx + (float) Math.cos(a0) * radius;
            float z0 = cz + (float) Math.sin(a0) * radius;
            float x1 = cx + (float) Math.cos(a1) * radius;
            float z1 = cz + (float) Math.sin(a1) * radius;

            // Центральный треугольник разлома
            quadBuf.vertex(cx, groundY, cz).color(20, 4, 8, alphaVoid);
            quadBuf.vertex(x0, groundY, z0).color(tr / 3, tg / 3, tb / 3, alphaEdge);
            quadBuf.vertex(x1, groundY, z1).color(tr / 3, tg / 3, tb / 3, alphaEdge);
            quadBuf.vertex(cx, groundY, cz).color(20, 4, 8, alphaVoid);

            // Светящаяся руническая граница
            float edgeR = radius * 1.14f;
            float ex0 = cx + (float) Math.cos(a0) * edgeR;
            float ez0 = cz + (float) Math.sin(a0) * edgeR;
            float ex1 = cx + (float) Math.cos(a1) * edgeR;
            float ez1 = cz + (float) Math.sin(a1) * edgeR;

            lightBuf.vertex(x0, groundY + 0.005f, z0).color(tr, tg, tb, alphaEdge);
            lightBuf.vertex(ex0, groundY + 0.005f, ez0).color(tr, tg, tb, 0);
            lightBuf.vertex(ex1, groundY + 0.005f, ez1).color(tr, tg, tb, 0);
            lightBuf.vertex(x1, groundY + 0.005f, z1).color(tr, tg, tb, alphaEdge);
        }
    }

    private void renderEntanglingVines(VertexConsumer quadBuf, LotusKillInstance inst, Vec3d camPos, Vector3f lightDir) {
        float age = inst.age;
        float grow = MathHelper.clamp(age / 0.45f, 0.0f, 1.0f);
        grow = (float) Math.sin(grow * Math.PI * 0.5); // Smooth easeOut

        float fade = 1.0f;
        if (age > 1.8f) {
            fade = MathHelper.clamp(1.0f - (age - 1.8f) / 0.6f, 0.0f, 1.0f);
        }
        if (fade <= 0.01f || grow <= 0.01f) return;

        int numVines = inst.vineCount;
        int segCount = 26;
        int tint = inst.tintColor;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;

        float targetH = (float) inst.entityHeight * inst.scale;

        for (int v = 0; v < numVines; v++) {
            float baseAngle = (float) ((v / (double) numVines) * Math.PI * 2.0);
            float dir = (v % 2 == 0) ? 1.0f : -1.0f; // Чередование направления для перекрестного связывания как веревкой
            float maxU = grow;

            Vector3f prevPos = null;
            Vector3f prevSide1 = null;
            Vector3f prevSide2 = null;

            int visibleSegs = Math.max(2, (int) (segCount * maxU));

            for (int i = 0; i <= visibleSegs; i++) {
                float u = (i / (float) segCount);
                if (u > maxU) u = maxU;

                // Спиральная траектория, сжимающаяся вокруг тела цели
                float theta = baseAngle + u * (2.8f * (float) Math.PI) * dir;
                
                // Радиус: широкий у корней на земле (0.85м) -> плотно облегающий тело (0.33м) -> легкое расширение к шипам наверху
                float r;
                if (u < 0.18f) {
                    float t0 = u / 0.18f;
                    r = MathHelper.lerp(t0, 0.85f * inst.scale, 0.33f * inst.scale);
                } else if (u < 0.82f) {
                    float cinch = 0.33f * inst.scale + 0.03f * (float) Math.sin(u * 14.0f + v * 2.0f);
                    r = cinch;
                } else {
                    float t1 = (u - 0.82f) / 0.18f;
                    r = MathHelper.lerp(t1, 0.33f * inst.scale, 0.44f * inst.scale);
                }

                float currY = (float) (inst.y + (u * 0.92f) * targetH - inst.currentDrag);
                float currX = (float) (inst.x + Math.cos(theta) * r);
                float currZ = (float) (inst.z + Math.sin(theta) * r);

                Vector3f currPos = new Vector3f(currX - (float) camPos.x, currY - (float) camPos.y, currZ - (float) camPos.z);

                // Толщина ветви: сужается от корней к верхушке
                float thick = MathHelper.lerp(u, 0.065f, 0.022f) * inst.scale;

                if (prevPos != null) {
                    Vector3f forward = new Vector3f(currPos).sub(prevPos);
                    if (forward.lengthSquared() > 0.00001f) {
                        forward.normalize();
                    } else {
                        forward = new Vector3f(0, 1, 0);
                    }

                    Vector3f side1 = new Vector3f(forward).cross(new Vector3f(0, 1, 0));
                    if (side1.lengthSquared() < 0.0001f) {
                        side1 = new Vector3f(forward).cross(new Vector3f(1, 0, 0));
                    }
                    side1.normalize().mul(thick);

                    Vector3f side2 = new Vector3f(forward).cross(side1).normalize().mul(thick);

                    if (prevSide1 == null) {
                        prevSide1 = side1;
                        prevSide2 = side2;
                    }

                    // Освещение и цвет коры/шипов
                    float dotLight = Math.abs(side1.dot(lightDir));
                    float diff = 0.60f + 0.40f * dotLight;

                    // Градиент: темное терновое дерево у основания -> яркие светящиеся прожилки лотоса
                    int barkR = (int) (MathHelper.lerp(u, 35, tr) * diff);
                    int barkG = (int) (MathHelper.lerp(u, 8, tg) * diff);
                    int barkB = (int) (MathHelper.lerp(u, 14, tb) * diff);
                    int alpha = (int) (245 * fade);

                    // Плоскость 1 (крестообразное объемное сечение)
                    quadBuf.vertex(prevPos.x - prevSide1.x, prevPos.y - prevSide1.y, prevPos.z - prevSide1.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(currPos.x - side1.x, currPos.y - side1.y, currPos.z - side1.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(currPos.x + side1.x, currPos.y + side1.y, currPos.z + side1.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(prevPos.x + prevSide1.x, prevPos.y + prevSide1.y, prevPos.z + prevSide1.z).color(barkR, barkG, barkB, alpha);

                    // Плоскость 2
                    quadBuf.vertex(prevPos.x - prevSide2.x, prevPos.y - prevSide2.y, prevPos.z - prevSide2.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(currPos.x - side2.x, currPos.y - side2.y, currPos.z - side2.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(currPos.x + side2.x, currPos.y + side2.y, currPos.z + side2.z).color(barkR, barkG, barkB, alpha);
                    quadBuf.vertex(prevPos.x + prevSide2.x, prevPos.y + prevSide2.y, prevPos.z + prevSide2.z).color(barkR, barkG, barkB, alpha);

                    // 3D шипы на ветвях через каждые 5 сегментов
                    if (i % 5 == 0 && i < visibleSegs - 2) {
                        Vector3f thornOut = new Vector3f((float) Math.cos(theta), 0.15f, (float) Math.sin(theta)).normalize().mul(thick * 2.8f);
                        Vector3f thornTip = new Vector3f(currPos).add(thornOut);

                        quadBuf.vertex(currPos.x - side1.x, currPos.y - side1.y, currPos.z - side1.z).color(tr, tg, tb, alpha);
                        quadBuf.vertex(currPos.x + side1.x, currPos.y + side1.y, currPos.z + side1.z).color(tr, tg, tb, alpha);
                        quadBuf.vertex(thornTip.x, thornTip.y, thornTip.z).color(255, 230, 240, alpha);
                        quadBuf.vertex(thornTip.x, thornTip.y, thornTip.z).color(255, 230, 240, alpha);
                    }

                    prevSide1 = side1;
                    prevSide2 = side2;
                }

                prevPos = currPos;
            }
        }
    }

    private void renderLotusFlower(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                  LotusKillInstance inst, Vec3d camPos, Vector3f lightDir) {
        float age = inst.age;
        if (age < 0.35f) return;

        float bloomFactor = MathHelper.clamp((age - 0.35f) / 0.40f, 0.0f, 1.0f);
        // Эластичное раскрытие лепестков
        float easeBloom = (float) (Math.sin(bloomFactor * Math.PI * 0.5) * (1.0 + 0.12 * Math.sin(bloomFactor * Math.PI)));

        float fade = 1.0f;
        if (age > 1.8f) {
            fade = MathHelper.clamp(1.0f - (age - 1.8f) / 0.6f, 0.0f, 1.0f);
        }
        if (fade <= 0.01f || easeBloom <= 0.01f) return;

        // Положение центра бутона (на уровне груди / сердца жертвы с учетом утягивания вниз)
        float flowerY = (float) (inst.y + inst.entityHeight * 0.55 * inst.scale - inst.currentDrag);
        float fcx = (float) (inst.x - camPos.x);
        float fcy = (float) (flowerY - camPos.y);
        float fcz = (float) (inst.z - camPos.z);

        int tint = inst.tintColor;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;
        int alpha = (int) (245 * fade);

        int petalCount = 12;
        float petalMaxLen = 0.52f * inst.scale * easeBloom;

        // 12 элегантных изогнутых лепестков Spider Lily (Ликориса)
        for (int p = 0; p < petalCount; p++) {
            float pAngle = (float) ((p / (double) petalCount) * Math.PI * 2.0);
            float cos = (float) Math.cos(pAngle);
            float sin = (float) Math.sin(pAngle);

            Vector3f p0 = new Vector3f(fcx, fcy, fcz);
            // 4 сегмента изгиба лепестка (выгиб наружу и красивый дугообразный спуск кончика)
            Vector3f p1 = new Vector3f(fcx + cos * (petalMaxLen * 0.30f), fcy + 0.08f * inst.scale, fcz + sin * (petalMaxLen * 0.30f));
            Vector3f p2 = new Vector3f(fcx + cos * (petalMaxLen * 0.70f), fcy + 0.16f * inst.scale, fcz + sin * (petalMaxLen * 0.70f));
            Vector3f p3 = new Vector3f(fcx + cos * petalMaxLen, fcy - 0.05f * inst.scale, fcz + sin * petalMaxLen);

            float w1 = 0.06f * inst.scale;
            float w2 = 0.09f * inst.scale;
            Vector3f side = new Vector3f(-sin, 0, cos);

            // Нижняя часть лепестка (глубокий бархатный алый)
            quadBuf.vertex(p0.x, p0.y, p0.z).color(tr / 2, tg / 2, tb / 2, alpha);
            quadBuf.vertex(p1.x - side.x * w1, p1.y, p1.z - side.z * w1).color(tr, tg, tb, alpha);
            quadBuf.vertex(p1.x + side.x * w1, p1.y, p1.z + side.z * w1).color(tr, tg, tb, alpha);
            quadBuf.vertex(p0.x, p0.y, p0.z).color(tr / 2, tg / 2, tb / 2, alpha);

            // Средняя часть
            quadBuf.vertex(p1.x - side.x * w1, p1.y, p1.z - side.z * w1).color(tr, tg, tb, alpha);
            quadBuf.vertex(p2.x - side.x * w2, p2.y, p2.z - side.z * w2).color(255, Math.min(255, tg + 60), Math.min(255, tb + 60), alpha);
            quadBuf.vertex(p2.x + side.x * w2, p2.y, p2.z + side.z * w2).color(255, Math.min(255, tg + 60), Math.min(255, tb + 60), alpha);
            quadBuf.vertex(p1.x + side.x * w1, p1.y, p1.z + side.z * w1).color(tr, tg, tb, alpha);

            // Кончик лепестка (яркое градиентное свечение)
            quadBuf.vertex(p2.x - side.x * w2, p2.y, p2.z - side.z * w2).color(255, Math.min(255, tg + 60), Math.min(255, tb + 60), alpha);
            quadBuf.vertex(p3.x, p3.y, p3.z).color(255, 240, 245, alpha);
            quadBuf.vertex(p3.x, p3.y, p3.z).color(255, 240, 245, alpha);
            quadBuf.vertex(p2.x + side.x * w2, p2.y, p2.z + side.z * w2).color(255, Math.min(255, tg + 60), Math.min(255, tb + 60), alpha);
        }

        // Длинные дугообразные тычинки Ликориса со светящейся пыльцой
        int stamenCount = 8;
        float stamenLen = 0.38f * inst.scale * easeBloom;
        for (int s = 0; s < stamenCount; s++) {
            float sAngle = (float) ((s / (double) stamenCount) * Math.PI * 2.0 + 0.35);
            float scos = (float) Math.cos(sAngle);
            float ssin = (float) Math.sin(sAngle);

            float tipX = fcx + scos * (stamenLen * 0.85f);
            float tipY = fcy + (0.24f + 0.08f * (float) Math.sin(s * 2.0)) * inst.scale;
            float tipZ = fcz + ssin * (stamenLen * 0.85f);

            // Светящаяся пыльца на кончике тычинки (аддитивный блеск)
            float ps = 0.025f * inst.scale;
            lightBuf.vertex(tipX - ps, tipY - ps, tipZ).color(255, 255, 200, 0);
            lightBuf.vertex(tipX + ps, tipY - ps, tipZ).color(255, 255, 200, 0);
            lightBuf.vertex(tipX + ps, tipY + ps, tipZ).color(255, 255, 200, (int) (255 * fade));
            lightBuf.vertex(tipX - ps, tipY + ps, tipZ).color(255, 255, 200, (int) (255 * fade));
        }
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
        particles.clear();
        lotusInstances.clear();
        recentAttacks.clear();
        deadEntities.clear();
    }

    // =========================================================================
    // Вспомогательные классы
    // =========================================================================

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

    private static class LotusKillInstance {
        final double x, y, z;
        final double entityHeight;
        final int tintColor;
        final int vineCount;
        final float scale;
        final float duration = 2.4f;
        float age = 0.0f;
        float currentDrag = 0.0f;

        boolean soundPlayedPhase2 = false;
        boolean soundPlayedPhase3 = false;

        final List<LotusPetalParticle> detachedPetals = new ArrayList<>();
        final List<LotusSporeParticle> spores = new ArrayList<>();

        LotusKillInstance(double x, double y, double z, double entityHeight, int tintColor, int vineCount, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityHeight = entityHeight;
            this.tintColor = tintColor;
            this.vineCount = vineCount;
            this.scale = scale;
        }

        void spawnBurstPetals(Random rnd) {
            float flowerY = (float) (y + entityHeight * 0.55 * scale - currentDrag);
            for (int i = 0; i < 14; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double spd = (1.2 + rnd.nextDouble() * 2.4) * scale;
                double vx = Math.cos(a) * spd;
                double vy = (0.9 + rnd.nextDouble() * 2.0) * scale;
                double vz = Math.sin(a) * spd;
                detachedPetals.add(new LotusPetalParticle(
                        x + Math.cos(a) * 0.25,
                        flowerY + (rnd.nextDouble() - 0.5) * 0.15,
                        z + Math.sin(a) * 0.25,
                        vx, vy, vz,
                        (0.12f + rnd.nextFloat() * 0.08f) * scale,
                        1.4f + rnd.nextFloat() * 0.5f,
                        tintColor, rnd
                ));
            }

            for (int i = 0; i < 35; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double r = rnd.nextDouble() * 0.45 * scale;
                spores.add(new LotusSporeParticle(
                        x + Math.cos(a) * r,
                        flowerY + (rnd.nextDouble() - 0.5) * 0.3,
                        z + Math.sin(a) * r,
                        (rnd.nextDouble() - 0.5) * 0.8 * scale,
                        (0.6 + rnd.nextDouble() * 1.5) * scale,
                        (rnd.nextDouble() - 0.5) * 0.8 * scale,
                        (0.035f + rnd.nextFloat() * 0.03f) * scale,
                        1.5f + rnd.nextFloat() * 0.6f,
                        tintColor
                ));
            }
        }

        void updateAndRenderParticles(VertexConsumer quadBuf, VertexConsumer lightBuf,
                                      Vec3d camPos, Vector3f lightDir, float dt) {
            // Лепестки
            Iterator<LotusPetalParticle> itP = detachedPetals.iterator();
            while (itP.hasNext()) {
                LotusPetalParticle p = itP.next();
                p.age += dt;
                if (p.age >= p.life) {
                    itP.remove();
                    continue;
                }
                p.vy -= 2.6f * dt; // Невесомое порхание
                float drag = (float) Math.exp(-1.4f * dt);
                p.vx *= drag;
                p.vy *= drag;
                p.vz *= drag;

                p.pitch += p.rotPitch * dt;
                p.yaw += p.rotYaw * dt;
                p.roll += p.rotRoll * dt;

                p.x += p.vx * dt + Math.sin(p.age * 6.0f) * 0.02f;
                p.y += p.vy * dt;
                p.z += p.vz * dt + Math.cos(p.age * 6.0f) * 0.02f;

                float alphaP = MathHelper.clamp((1.0f - p.age / p.life) * 1.6f, 0.0f, 1.0f);
                if (alphaP <= 0.01f) continue;

                p.render(quadBuf, camPos, lightDir, alphaP);
            }

            // Пыльца
            Iterator<LotusSporeParticle> itS = spores.iterator();
            while (itS.hasNext()) {
                LotusSporeParticle s = itS.next();
                s.age += dt;
                if (s.age >= s.life) {
                    itS.remove();
                    continue;
                }
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                s.z += s.vz * dt;
                s.vy += 0.2f * dt; // Легкий подъем вверх

                float alphaS = MathHelper.clamp((1.0f - s.age / s.life) * 1.5f, 0.0f, 1.0f);
                if (alphaS <= 0.01f) continue;

                s.render(lightBuf, camPos, alphaS);
            }
        }
    }

    private static class LotusPetalParticle {
        double x, y, z;
        double vx, vy, vz;
        float pitch, yaw, roll;
        final float rotPitch, rotYaw, rotRoll;
        final float size;
        final float life;
        final int tintColor;
        float age = 0.0f;

        LotusPetalParticle(double x, double y, double z, double vx, double vy, double vz,
                           float size, float life, int tintColor, Random rnd) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.life = life;
            this.tintColor = tintColor;
            this.pitch = rnd.nextFloat() * 360.0f;
            this.yaw = rnd.nextFloat() * 360.0f;
            this.roll = rnd.nextFloat() * 360.0f;
            this.rotPitch = (rnd.nextFloat() - 0.5f) * 500.0f;
            this.rotYaw = (rnd.nextFloat() - 0.5f) * 500.0f;
            this.rotRoll = (rnd.nextFloat() - 0.5f) * 500.0f;
        }

        void render(VertexConsumer buf, Vec3d camPos, Vector3f lightDir, float alpha) {
            Quaternionf rot = new Quaternionf()
                    .rotationYXZ((float) Math.toRadians(yaw),
                                 (float) Math.toRadians(pitch),
                                 (float) Math.toRadians(roll));

            float s = size;
            float px = (float) (x - camPos.x);
            float py = (float) (y - camPos.y);
            float pz = (float) (z - camPos.z);

            Vector3f v0 = new Vector3f(-0.35f * s, -0.6f * s, 0).rotate(rot);
            Vector3f v1 = new Vector3f( 0.35f * s, -0.6f * s, 0).rotate(rot);
            Vector3f v2 = new Vector3f( 0.45f * s,  0.7f * s, 0).rotate(rot);
            Vector3f v3 = new Vector3f(-0.45f * s,  0.7f * s, 0).rotate(rot);

            int tr = (tintColor >> 16) & 0xFF;
            int tg = (tintColor >> 8) & 0xFF;
            int tb = tintColor & 0xFF;
            int a = (int) (230 * alpha);

            buf.vertex(px + v0.x, py + v0.y, pz + v0.z).color(tr, tg, tb, a);
            buf.vertex(px + v1.x, py + v1.y, pz + v1.z).color(tr, tg, tb, a);
            buf.vertex(px + v2.x, py + v2.y, pz + v2.z).color(255, 235, 240, a);
            buf.vertex(px + v3.x, py + v3.y, pz + v3.z).color(255, 235, 240, a);
        }
    }

    private static class LotusSporeParticle {
        double x, y, z;
        double vx, vy, vz;
        final float size;
        final float life;
        final int tintColor;
        float age = 0.0f;

        LotusSporeParticle(double x, double y, double z, double vx, double vy, double vz,
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

        void render(VertexConsumer buf, Vec3d camPos, float alpha) {
            float px = (float) (x - camPos.x);
            float py = (float) (y - camPos.y);
            float pz = (float) (z - camPos.z);
            float s = size;

            int tr = (tintColor >> 16) & 0xFF;
            int tg = (tintColor >> 8) & 0xFF;
            int tb = tintColor & 0xFF;
            int a = (int) (240 * alpha);

            buf.vertex(px - s, py - s, pz).color(tr, tg, tb, 0);
            buf.vertex(px + s, py - s, pz).color(tr, tg, tb, 0);
            buf.vertex(px + s, py + s, pz).color(255, 245, 230, a);
            buf.vertex(px - s, py + s, pz).color(255, 245, 230, a);
        }
    }
}
