package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.FriendManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.List;

/**
 * ItemRadius - Отображение радиуса действия особых предметов на серверах Funtime и HolyWorld.
 * Имеет уникальную кибернетическую визуализацию с радарной волной, неоновыми градиентными контурами,
 * объёмной голографической ловушкой и энергетическим щитом пласта.
 */
public class ItemRadius extends Module {

    private final ModeSetting server = new ModeSetting("Сервер", "Funtime", "Выбор сервера", "Funtime", "HolyWorld");

    private final BooleanSetting showDez = new BooleanSetting("Дезка", true, "Радиус дезориентации (10м)");
    private final BooleanSetting showYavka = new BooleanSetting("Явка", true, "Радиус явной пыли (10м)");
    private final BooleanSetting showFireCharge = new BooleanSetting("Огненный заряд", true, "Радиус огненного заряда / смерча (10м)");
    private final BooleanSetting showGodAura = new BooleanSetting("Божья Аура", true, "Радиус божьей ауры (2-3м)");
    private final BooleanSetting showTrap = new BooleanSetting("Трапка", true, "Куб трапки / ловушки (4х4х4м)");
    private final BooleanSetting showPlast = new BooleanSetting("Пласт", true, "Стена пласта перед игроком (4х4х1.5м)");

    private final BooleanSetting radarWave = new BooleanSetting("Радарная волна", true, "Анимированный сканирующий луч");
    private final BooleanSetting pulseRing = new BooleanSetting("Пульсация", true, "Расширяющееся эхо-кольцо");
    private final SliderSetting fillOpacity = new SliderSetting("Прозрачность", 40.0, 5.0, 100.0, 5.0, "Прозрачность заливки");

    // Dynamic smoothing and transitions
    private float threatTimer = 0.0f;
    private float animTime = 0.0f;
    private long lastFrameNanos = 0L;

    // Smoothed pose for Plast
    private Vec3d plastSmoothedCenter = Vec3d.ZERO;
    private float plastSmoothedYawDeg = 0.0f;
    private float plastSmoothedPitchDeg = 0.0f;
    private boolean plastHasSmoothedPose = false;

    public ItemRadius() {
        super("ItemRadius", "Отображение радиуса особых предметов на серверах", Category.ESP);
        addSettings(server, showDez, showYavka, showFireCharge, showGodAura, showTrap, showPlast, radarWave, pulseRing, fillOpacity);
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

        animTime += dt;

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        boolean isFT = server.is("Funtime");
        String activeType = null;
        ItemStack heldStack = null;

        String[] types = {"dez", "yavka", "fire_charge", "god_aura", "trap", "plast"};
        for (String type : types) {
            if (!isSettingAllowed(type)) continue;

            if (isItemMatching(main, isFT, type)) {
                activeType = type;
                heldStack = main;
                break;
            } else if (isItemMatching(off, isFT, type)) {
                activeType = type;
                heldStack = off;
                break;
            }
        }

        if (activeType == null) {
            plastHasSmoothedPose = false;
            threatTimer = Math.max(0.0f, threatTimer - dt * 4.0f);
            return;
        }

        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());
        Vec3d playerPos = mc.player.getEntityPos();

        int accentColor = Theme.getAccent();
        int alertColor = 0xFFFF2A4B; // Vivid Crimson Red
        int friendColor = 0xFF2BFF88; // Holy Emerald Green

        switch (activeType) {
            case "dez":
            case "yavka":
            case "fire_charge": {
                plastHasSmoothedPose = false;
                float radius = 10.0f;
                Vec3d center = new Vec3d(playerPos.x, playerPos.y + 0.02, playerPos.z);
                boolean enemyInRadius = checkPlayersInRadius(mc.player, center, radius);

                float targetThreat = enemyInRadius ? 1.0f : 0.0f;
                threatTimer += (targetThreat - threatTimer) * Math.min(1.0f, dt * 6.0f);

                renderCyberCircle(quadBuffer, center, camPos, radius, accentColor, alertColor, threatTimer, animTime);
                break;
            }
            case "god_aura": {
                plastHasSmoothedPose = false;
                float radius = isFT ? 2.0f : 2.5f;
                Vec3d center = new Vec3d(playerPos.x, playerPos.y + 0.02, playerPos.z);
                boolean friendInRadius = checkFriendsInRadius(mc.player, center, radius);
                boolean enemyInRadius = checkPlayersInRadius(mc.player, center, radius);

                float targetThreat = enemyInRadius ? 1.0f : 0.0f;
                threatTimer += (targetThreat - threatTimer) * Math.min(1.0f, dt * 6.0f);

                int baseCol = friendInRadius ? friendColor : accentColor;
                renderCyberCircle(quadBuffer, center, camPos, radius, baseCol, alertColor, threatTimer, animTime);
                break;
            }
            case "trap": {
                plastHasSmoothedPose = false;
                Vec3d cubeCenter = new Vec3d(playerPos.x, playerPos.y + 2.0, playerPos.z);
                boolean enemyInTrap = checkPlayersInRadius(mc.player, cubeCenter, 2.5);

                float targetThreat = enemyInTrap ? 1.0f : 0.0f;
                threatTimer += (targetThreat - threatTimer) * Math.min(1.0f, dt * 6.0f);

                renderHolographicTrap(quadBuffer, cubeCenter, camPos, 4.0f, accentColor, alertColor, threatTimer, animTime);
                break;
            }
            case "plast": {
                PlanePose targetPose = computePlastPose(mc, dt);
                PlanePose smoothPose = smoothPlastPose(targetPose, dt);
                boolean enemyNearPlast = checkPlayersInRadius(mc.player, smoothPose.center, 2.5);

                float targetThreat = enemyNearPlast ? 1.0f : 0.0f;
                threatTimer += (targetThreat - threatTimer) * Math.min(1.0f, dt * 6.0f);

                renderHolographicPlast(quadBuffer, smoothPose, camPos, 4.0f, 4.0f, 1.5f, accentColor, alertColor, threatTimer, animTime);
                break;
            }
        }
    }

    private boolean isSettingAllowed(String type) {
        return switch (type) {
            case "dez" -> showDez.isEnabled();
            case "yavka" -> showYavka.isEnabled();
            case "fire_charge" -> showFireCharge.isEnabled();
            case "god_aura" -> showGodAura.isEnabled();
            case "trap" -> showTrap.isEnabled();
            case "plast" -> showPlast.isEnabled();
            default -> false;
        };
    }

    private boolean isItemMatching(ItemStack stack, boolean isFT, String type) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        String name = stack.getName().getString().toLowerCase();

        switch (type) {
            case "dez":
                if (item != Items.ENDER_EYE) return false;
                if (isFT) {
                    return containsCyrillicLetter(stack, 'Я') || name.contains("дез");
                } else {
                    return name.contains("ослеп") || name.contains("дез") || name.contains("сфер");
                }
            case "yavka":
                if (item != Items.SUGAR) return false;
                if (isFT) {
                    return containsCyrillicLetter(stack, 'Я') || name.contains("явк") || name.contains("пыль");
                } else {
                    return name.contains("пыль") || name.contains("явк") || name.contains("просвет");
                }
            case "fire_charge":
                if (isFT) {
                    if (item != Items.FIRE_CHARGE) return false;
                    return containsCyrillicLetter(stack, 'С') || name.contains("смерч") || name.contains("заряд");
                } else {
                    if (item != Items.FIRE_CHARGE && item != Items.FIREWORK_STAR) return false;
                    return name.contains("смерч") || name.contains("огн") || name.contains("буря");
                }
            case "god_aura":
                if (item != Items.PHANTOM_MEMBRANE) return false;
                if (isFT) {
                    return containsCyrillicLetter(stack, 'У') || name.contains("аур") || name.contains("бож");
                } else {
                    return name.contains("аур") || name.contains("бог") || name.contains("благ");
                }
            case "trap":
                if (isFT) {
                    if (item != Items.NETHERITE_SCRAP) return false;
                    return containsCyrillicLetter(stack, 'А') || name.contains("трап");
                } else {
                    if (item != Items.NETHERITE_SCRAP && item != Items.PRISMARINE_SHARD) return false;
                    return name.contains("трап") || name.contains("лов");
                }
            case "plast":
                if (item != Items.DRIED_KELP) return false;
                if (isFT) {
                    return containsCyrillicLetter(stack, 'П') || name.contains("пласт");
                } else {
                    return name.contains("пласт") || name.contains("стен") || name.contains("щит");
                }
            default:
                return false;
        }
    }

    private boolean containsCyrillicLetter(ItemStack stack, char letter) {
        if (stack == null || stack.isEmpty()) return false;
        String name = stack.getName().getString();
        if (name == null || name.isEmpty()) return false;
        String upper = name.toUpperCase();
        return upper.indexOf(Character.toUpperCase(letter)) >= 0;
    }

    private boolean checkPlayersInRadius(PlayerEntity self, Vec3d center, double radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        double rSq = radius * radius;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == self || p.isSpectator() || !p.isAlive()) continue;
            if (FriendManager.getInstance().isFriend(p.getName().getString())) continue;
            if (p.squaredDistanceTo(center) <= rSq) {
                return true;
            }
        }
        return false;
    }

    private boolean checkFriendsInRadius(PlayerEntity self, Vec3d center, double radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        double rSq = radius * radius;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == self || p.isSpectator() || !p.isAlive()) continue;
            if (FriendManager.getInstance().isFriend(p.getName().getString())) {
                if (p.squaredDistanceTo(center) <= rSq) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Unique Cybernetic Circle Rendering:
     * - Dual glowing neon boundary ribbon
     * - Subtle gradient floor fill with configurable opacity
     * - Animated 360° rotating radar sweep sector
     * - Expanding circular ripple pulse
     * - Cardinal cyber notches at North/East/South/West
     */
    private void renderCyberCircle(VertexConsumer buf, Vec3d center, Vec3d camPos, float radius,
                                   int primaryCol, int alertCol, float threat, float time) {
        int color = ColorUtil.interpolate(primaryCol, alertCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y - camPos.y);
        float cz = (float) (center.z - camPos.z);

        int segments = 72;
        float angleStep = (float) (2.0 * Math.PI / segments);
        float baseFillAlpha = (float) fillOpacity.get();
        if (threat > 0.05f) {
            float warningPulse = (float) Math.sin(time * 12.0f) * 0.5f + 0.5f;
            baseFillAlpha = MathHelper.lerp(warningPulse, baseFillAlpha, 110.0f);
        }

        // 1. Semi-transparent gradient floor disc (quad pairs forming complete circular disc)
        for (int i = 0; i < segments; i += 2) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;
            float a3 = (i + 2) * angleStep;

            float x1 = cx + (float) Math.sin(a1) * (radius - 0.08f);
            float z1 = cz + (float) Math.cos(a1) * (radius - 0.08f);

            float x2 = cx + (float) Math.sin(a2) * (radius - 0.08f);
            float z2 = cz + (float) Math.cos(a2) * (radius - 0.08f);

            float x3 = cx + (float) Math.sin(a3) * (radius - 0.08f);
            float z3 = cz + (float) Math.cos(a3) * (radius - 0.08f);

            int alphaInner = (int) (baseFillAlpha * 0.35f);
            int alphaOuter = (int) (baseFillAlpha * 0.85f);

            buf.vertex(cx, cy, cz).color(r, g, b, alphaInner);
            buf.vertex(x1, cy, z1).color(r, g, b, alphaOuter);
            buf.vertex(x2, cy, z2).color(r, g, b, alphaOuter);
            buf.vertex(x3, cy, z3).color(r, g, b, alphaOuter);
        }

        // 2. Animated Radar Sweep Beam
        if (radarWave.isEnabled()) {
            float sweepAngle = (float) ((time * 2.2f) % (2.0 * Math.PI));
            float sweepArc = (float) Math.toRadians(75.0);

            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;

                float diff = (a1 - sweepAngle + (float) (4.0 * Math.PI)) % (float) (2.0 * Math.PI);
                if (diff < sweepArc) {
                    float beamFactor = 1.0f - (diff / sweepArc);
                    int beamAlpha = (int) (beamFactor * 130.0f);

                    float x1 = cx + (float) Math.sin(a1) * (radius - 0.08f);
                    float z1 = cz + (float) Math.cos(a1) * (radius - 0.08f);
                    float x2 = cx + (float) Math.sin(a2) * (radius - 0.08f);
                    float z2 = cz + (float) Math.cos(a2) * (radius - 0.08f);

                    buf.vertex(cx, cy, cz).color(255, 255, 255, beamAlpha / 3);
                    buf.vertex(cx, cy, cz).color(r, g, b, beamAlpha / 3);
                    buf.vertex(x1, cy, z1).color(r, g, b, beamAlpha);
                    buf.vertex(x2, cy, z2).color(r, g, b, beamAlpha);
                }
            }
        }

        // 3. Expanding Ripple Pulse
        if (pulseRing.isEnabled()) {
            float pulsePhase = (time * 0.75f) % 1.0f;
            float pulseR = radius * pulsePhase;
            int pulseAlpha = (int) ((1.0f - pulsePhase) * 180.0f);
            float pWidth = 0.05f;

            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;

                float inR = Math.max(0.01f, pulseR - pWidth);
                float outR = pulseR + pWidth;

                float ix1 = cx + (float) Math.sin(a1) * inR;
                float iz1 = cz + (float) Math.cos(a1) * inR;
                float ix2 = cx + (float) Math.sin(a2) * inR;
                float iz2 = cz + (float) Math.cos(a2) * inR;

                float ox1 = cx + (float) Math.sin(a1) * outR;
                float oz1 = cz + (float) Math.cos(a1) * outR;
                float ox2 = cx + (float) Math.sin(a2) * outR;
                float oz2 = cz + (float) Math.cos(a2) * outR;

                buf.vertex(ix1, cy + 0.005f, iz1).color(r, g, b, pulseAlpha);
                buf.vertex(ox1, cy + 0.005f, oz1).color(r, g, b, pulseAlpha);
                buf.vertex(ox2, cy + 0.005f, oz2).color(r, g, b, pulseAlpha);
                buf.vertex(ix2, cy + 0.005f, iz2).color(r, g, b, pulseAlpha);
            }
        }

        // 4. Dual Glowing Boundary Ribbon (Core Ring + Soft Outer Halo)
        float coreHalfW = 0.06f;
        float haloWidth = 0.22f;

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float sin1 = (float) Math.sin(a1);
            float cos1 = (float) Math.cos(a1);
            float sin2 = (float) Math.sin(a2);
            float cos2 = (float) Math.cos(a2);

            // Core sharp neon ribbon
            float rIn = radius - coreHalfW;
            float rOut = radius + coreHalfW;

            float cInX1 = cx + sin1 * rIn;
            float cInZ1 = cz + cos1 * rIn;
            float cInX2 = cx + sin2 * rIn;
            float cInZ2 = cz + cos2 * rIn;

            float cOutX1 = cx + sin1 * rOut;
            float cOutZ1 = cz + cos1 * rOut;
            float cOutX2 = cx + sin2 * rOut;
            float cOutZ2 = cz + cos2 * rOut;

            buf.vertex(cInX1, cy + 0.01f, cInZ1).color(r, g, b, 230);
            buf.vertex(cOutX1, cy + 0.01f, cOutZ1).color(r, g, b, 255);
            buf.vertex(cOutX2, cy + 0.01f, cOutZ2).color(r, g, b, 255);
            buf.vertex(cInX2, cy + 0.01f, cInZ2).color(r, g, b, 230);

            // Soft outer halo ribbon (fades to 0)
            float hOutX1 = cx + sin1 * (radius + haloWidth);
            float hOutZ1 = cz + cos1 * (radius + haloWidth);
            float hOutX2 = cx + sin2 * (radius + haloWidth);
            float hOutZ2 = cz + cos2 * (radius + haloWidth);

            buf.vertex(cOutX1, cy + 0.01f, cOutZ1).color(r, g, b, 140);
            buf.vertex(hOutX1, cy + 0.01f, hOutZ1).color(r, g, b, 0);
            buf.vertex(hOutX2, cy + 0.01f, hOutZ2).color(r, g, b, 0);
            buf.vertex(cOutX2, cy + 0.01f, cOutZ2).color(r, g, b, 140);
        }

        // 5. Cardinal Cyber Notches (N, E, S, W pointer tick marks)
        float[] cardinalAngles = {0.0f, (float) (Math.PI * 0.5), (float) Math.PI, (float) (Math.PI * 1.5)};
        for (float ca : cardinalAngles) {
            float forwardX = (float) Math.sin(ca);
            float forwardZ = (float) Math.cos(ca);
            float perpX = -forwardZ;
            float perpZ = forwardX;

            float tickStartR = radius - 0.45f;
            float tickEndR = radius + 0.35f;
            float tickHalfThick = 0.05f;

            float tx1 = cx + forwardX * tickStartR - perpX * tickHalfThick;
            float tz1 = cz + forwardZ * tickStartR - perpZ * tickHalfThick;
            float tx2 = cx + forwardX * tickStartR + perpX * tickHalfThick;
            float tz2 = cz + forwardZ * tickStartR + perpZ * tickHalfThick;
            float tx3 = cx + forwardX * tickEndR + perpX * tickHalfThick;
            float tz3 = cz + forwardZ * tickEndR + perpZ * tickHalfThick;
            float tx4 = cx + forwardX * tickEndR - perpX * tickHalfThick;
            float tz4 = cz + forwardZ * tickEndR - perpZ * tickHalfThick;

            buf.vertex(tx1, cy + 0.015f, tz1).color(255, 255, 255, 200);
            buf.vertex(tx2, cy + 0.015f, tz2).color(255, 255, 255, 200);
            buf.vertex(tx3, cy + 0.015f, tz3).color(r, g, b, 240);
            buf.vertex(tx4, cy + 0.015f, tz4).color(r, g, b, 240);
        }
    }

    /**
     * Unique Holographic Trap Cage Rendering:
     * - 4x4x4 Holographic energy containment field
     * - Sci-Fi corner targeting brackets on all 8 vertices
     * - Soft translucent energy barrier faces
     * - Smooth animated vertical laser scanning plane
     */
    private void renderHolographicTrap(VertexConsumer buf, Vec3d center, Vec3d camPos, float size,
                                       int primaryCol, int alertCol, float threat, float time) {
        int color = ColorUtil.interpolate(primaryCol, alertCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y - camPos.y);
        float cz = (float) (center.z - camPos.z);

        float h = size * 0.5f; // 2.0m half-extent
        float x1 = cx - h, x2 = cx + h;
        float y1 = cy - h, y2 = cy + h;
        float z1 = cz - h, z2 = cz + h;

        int faceAlpha = threat > 0.05f ? 45 : 24;

        // 1. Translucent holographic box faces
        // Bottom & Top
        buf.vertex(x1, y1, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y1, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y1, z2).color(r, g, b, faceAlpha);
        buf.vertex(x1, y1, z2).color(r, g, b, faceAlpha);

        buf.vertex(x1, y2, z1).color(r, g, b, faceAlpha);
        buf.vertex(x1, y2, z2).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z2).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z1).color(r, g, b, faceAlpha);

        // Sides
        buf.vertex(x1, y1, z1).color(r, g, b, faceAlpha);
        buf.vertex(x1, y2, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y1, z1).color(r, g, b, faceAlpha);

        buf.vertex(x1, y1, z2).color(r, g, b, faceAlpha);
        buf.vertex(x2, y1, z2).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z2).color(r, g, b, faceAlpha);
        buf.vertex(x1, y2, z2).color(r, g, b, faceAlpha);

        buf.vertex(x1, y1, z1).color(r, g, b, faceAlpha);
        buf.vertex(x1, y1, z2).color(r, g, b, faceAlpha);
        buf.vertex(x1, y2, z2).color(r, g, b, faceAlpha);
        buf.vertex(x1, y2, z1).color(r, g, b, faceAlpha);

        buf.vertex(x2, y1, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z1).color(r, g, b, faceAlpha);
        buf.vertex(x2, y2, z2).color(r, g, b, faceAlpha);
        buf.vertex(x2, y1, z2).color(r, g, b, faceAlpha);

        // 2. Animated Vertical Laser Scanner Grid Plane
        float laserRelY = (float) Math.sin(time * 3.0f) * (h - 0.15f);
        float ly = cy + laserRelY;
        int laserAlpha = threat > 0.05f ? 110 : 65;

        buf.vertex(x1, ly, z1).color(r, g, b, laserAlpha);
        buf.vertex(x2, ly, z1).color(r, g, b, laserAlpha);
        buf.vertex(x2, ly, z2).color(r, g, b, laserAlpha);
        buf.vertex(x1, ly, z2).color(r, g, b, laserAlpha);

        // 3. Sci-Fi Neon Corner Brackets (8 corners)
        float bLen = 0.65f;
        float bThick = 0.045f;
        int bracketAlpha = 240;

        float[] xs = {x1, x2};
        float[] ys = {y1, y2};
        float[] zs = {z1, z2};

        for (float kx : xs) {
            float dx = (kx == x1) ? 1.0f : -1.0f;
            for (float ky : ys) {
                float dy = (ky == y1) ? 1.0f : -1.0f;
                for (float kz : zs) {
                    float dz = (kz == z1) ? 1.0f : -1.0f;

                    // Segment along X
                    renderThickLineSegment(buf, kx, ky, kz, kx + dx * bLen, ky, kz, bThick, r, g, b, bracketAlpha);
                    // Segment along Y
                    renderThickLineSegment(buf, kx, ky, kz, kx, ky + dy * bLen, kz, bThick, r, g, b, bracketAlpha);
                    // Segment along Z
                    renderThickLineSegment(buf, kx, ky, kz, kx, ky, kz + dz * bLen, bThick, r, g, b, bracketAlpha);
                }
            }
        }
    }

    /**
     * Unique Holographic Plast (Shield Wall) Rendering:
     * - Raycast-aligned 4x4x1.5m energy barrier
     * - Dual glowing neon boundary frames
     * - Translucent holographic energy shield
     * - Animated energetic scanwave traveling across the wall
     */
    private void renderHolographicPlast(VertexConsumer buf, PlanePose pose, Vec3d camPos,
                                        float width, float height, float thickness,
                                        int primaryCol, int alertCol, float threat, float time) {
        int color = ColorUtil.interpolate(primaryCol, alertCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float hw = width * 0.5f;
        float hh = height * 0.5f;
        float ht = thickness * 0.5f;

        // Transform 8 local box vertices to rotated world coordinates
        Vec3d[] localVertices = {
                new Vec3d(-hw, -hh, -ht), // 0: ---
                new Vec3d(hw, -hh, -ht),  // 1: +--
                new Vec3d(hw, hh, -ht),   // 2: ++-
                new Vec3d(-hw, hh, -ht),  // 3: -+-
                new Vec3d(-hw, -hh, ht),  // 4: --+
                new Vec3d(hw, -hh, ht),   // 5: +-+
                new Vec3d(hw, hh, ht),    // 6: +++
                new Vec3d(-hw, hh, ht)    // 7: -++
        };

        Vec3d[] worldVertices = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            Vec3d rotated = rotatePosePoint(localVertices[i], pose.yawDeg, pose.pitchDeg);
            worldVertices[i] = new Vec3d(
                    pose.center.x + rotated.x - camPos.x,
                    pose.center.y + rotated.y - camPos.y,
                    pose.center.z + rotated.z - camPos.z
            );
        }

        int barrierAlpha = threat > 0.05f ? 75 : 40;

        // 1. Draw 6 semi-transparent barrier shield faces
        // Front face (0, 1, 2, 3)
        renderQuad(buf, worldVertices[0], worldVertices[1], worldVertices[2], worldVertices[3], r, g, b, barrierAlpha);
        // Back face (5, 4, 7, 6)
        renderQuad(buf, worldVertices[5], worldVertices[4], worldVertices[7], worldVertices[6], r, g, b, barrierAlpha);
        // Top face (3, 2, 6, 7)
        renderQuad(buf, worldVertices[3], worldVertices[2], worldVertices[6], worldVertices[7], r, g, b, barrierAlpha);
        // Bottom face (4, 5, 1, 0)
        renderQuad(buf, worldVertices[4], worldVertices[5], worldVertices[1], worldVertices[0], r, g, b, barrierAlpha);
        // Left face (4, 0, 3, 7)
        renderQuad(buf, worldVertices[4], worldVertices[0], worldVertices[3], worldVertices[7], r, g, b, barrierAlpha);
        // Right face (1, 5, 6, 2)
        renderQuad(buf, worldVertices[1], worldVertices[5], worldVertices[6], worldVertices[2], r, g, b, barrierAlpha);

        // 2. Thick Sci-Fi Neon Edge Frame on Front & Back
        float frameThick = 0.06f;
        int frameAlpha = 245;

        // Front edges: 0-1, 1-2, 2-3, 3-0
        renderThickLineSegment(buf, worldVertices[0], worldVertices[1], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[1], worldVertices[2], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[2], worldVertices[3], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[3], worldVertices[0], frameThick, r, g, b, frameAlpha);

        // Back edges: 4-5, 5-6, 6-7, 7-4
        renderThickLineSegment(buf, worldVertices[4], worldVertices[5], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[5], worldVertices[6], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[6], worldVertices[7], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[7], worldVertices[4], frameThick, r, g, b, frameAlpha);

        // Connecting strut edges: 0-4, 1-5, 2-6, 3-7
        renderThickLineSegment(buf, worldVertices[0], worldVertices[4], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[1], worldVertices[5], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[2], worldVertices[6], frameThick, r, g, b, frameAlpha);
        renderThickLineSegment(buf, worldVertices[3], worldVertices[7], frameThick, r, g, b, frameAlpha);

        // 3. Animated Energy Pulse Wave through the barrier
        float waveProgress = (time * 1.5f) % 1.0f;
        float waveY = MathHelper.lerp(waveProgress, -hh, hh);

        Vec3d w1 = rotatePosePoint(new Vec3d(-hw, waveY, 0.0), pose.yawDeg, pose.pitchDeg);
        Vec3d w2 = rotatePosePoint(new Vec3d(hw, waveY, 0.0), pose.yawDeg, pose.pitchDeg);

        Vec3d wv1 = new Vec3d(pose.center.x + w1.x - camPos.x, pose.center.y + w1.y - camPos.y, pose.center.z + w1.z - camPos.z);
        Vec3d wv2 = new Vec3d(pose.center.x + w2.x - camPos.x, pose.center.y + w2.y - camPos.y, pose.center.z + w2.z - camPos.z);

        int pulseAlpha = (int) ((1.0f - Math.abs(waveProgress - 0.5f) * 2.0f) * 190.0f);
        renderThickLineSegment(buf, wv1, wv2, 0.12f, 255, 255, 255, pulseAlpha);
    }

    private void renderQuad(VertexConsumer buf, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, int r, int g, int b, int a) {
        buf.vertex((float) v1.x, (float) v1.y, (float) v1.z).color(r, g, b, a);
        buf.vertex((float) v2.x, (float) v2.y, (float) v2.z).color(r, g, b, a);
        buf.vertex((float) v3.x, (float) v3.y, (float) v3.z).color(r, g, b, a);
        buf.vertex((float) v4.x, (float) v4.y, (float) v4.z).color(r, g, b, a);
    }

    private void renderThickLineSegment(VertexConsumer buf, Vec3d p1, Vec3d p2, float thickness, int r, int g, int b, int a) {
        renderThickLineSegment(buf, (float) p1.x, (float) p1.y, (float) p1.z, (float) p2.x, (float) p2.y, (float) p2.z, thickness, r, g, b, a);
    }

    private void renderThickLineSegment(VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2,
                                       float thickness, int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;

        float ht = thickness * 0.5f;

        // Find perpendicular vector
        float px, py, pz;
        if (Math.abs(dy / len) > 0.9f) {
            px = ht; py = 0.0f; pz = 0.0f;
        } else {
            px = 0.0f; py = ht; pz = 0.0f;
        }

        buf.vertex(x1 - px, y1 - py, z1 - pz).color(r, g, b, a);
        buf.vertex(x2 - px, y2 - py, z2 - pz).color(r, g, b, a);
        buf.vertex(x2 + px, y2 + py, z2 + pz).color(r, g, b, a);
        buf.vertex(x1 + px, y1 + py, z1 + pz).color(r, g, b, a);
    }

    private Vec3d rotatePosePoint(Vec3d pt, float yawDeg, float pitchDeg) {
        // Rotate around local X axis (pitch)
        double radPitch = Math.toRadians(pitchDeg);
        double cosP = Math.cos(radPitch);
        double sinP = Math.sin(radPitch);
        double y1 = pt.y * cosP - pt.z * sinP;
        double z1 = pt.y * sinP + pt.z * cosP;
        double x1 = pt.x;

        // Rotate around local Y axis (yaw)
        double radYaw = Math.toRadians(yawDeg);
        double cosY = Math.cos(radYaw);
        double sinY = Math.sin(radYaw);
        double x2 = x1 * cosY + z1 * sinY;
        double z2 = -x1 * sinY + z1 * cosY;
        double y2 = y1;

        return new Vec3d(x2, y2, z2);
    }

    private PlanePose computePlastPose(MinecraftClient mc, float dt) {
        Vec3d start = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d end = start.add(lookVec.multiply(4.0));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));

        float pitch = mc.player.getPitch();
        boolean isLookingDown = pitch > 45.0f;
        boolean isLookingUp = pitch < -45.0f;
        boolean isLookingHorizontal = !isLookingDown && !isLookingUp;

        float thickness = 1.5f;
        float halfThickness = thickness / 2.0f;

        Vec3d center;
        float yawDeg;
        float pitchDeg;

        if (hit.getType() == HitResult.Type.BLOCK && hit.getPos().distanceTo(start) <= 4.0) {
            Vec3d hitPos = hit.getPos();
            Direction face = hit.getSide();

            if (isLookingDown) {
                center = new Vec3d(
                        Math.floor(hitPos.x) + 0.5,
                        Math.floor(hitPos.y + 1.0) - 1.8 + halfThickness,
                        Math.floor(hitPos.z) + 0.5
                );
                yawDeg = 0.0f;
                pitchDeg = 90.0f;
            } else if (isLookingUp) {
                center = new Vec3d(
                        Math.floor(hitPos.x) + 0.5,
                        Math.floor(hitPos.y) - halfThickness + 1.6,
                        Math.floor(hitPos.z) + 0.5
                );
                yawDeg = 0.0f;
                pitchDeg = -90.0f;
            } else {
                double offsetX = face.getOffsetX() != 0 ? face.getOffsetX() * halfThickness : 0.0;
                double offsetZ = face.getOffsetZ() != 0 ? face.getOffsetZ() * halfThickness : 0.0;
                center = new Vec3d(
                        Math.floor(hitPos.x) + 0.5 + offsetX,
                        Math.floor(hitPos.y) + 0.5 + 1.6,
                        Math.floor(hitPos.z) + 0.5 + offsetZ
                );

                yawDeg = switch (face) {
                    case NORTH -> 180.0f;
                    case SOUTH -> 0.0f;
                    case WEST -> 90.0f;
                    case EAST -> -90.0f;
                    default -> -mc.player.getYaw();
                };
                pitchDeg = 0.0f;
            }
        } else {
            Vec3d approx = start.add(lookVec.multiply(4.0));
            double y = Math.floor(approx.y) + (isLookingDown ? (-1.8 + halfThickness) : isLookingUp ? (-halfThickness + 1.6) : (0.5 + 1.6));
            center = new Vec3d(Math.floor(approx.x) + 0.5, y, Math.floor(approx.z) + 0.5);
            yawDeg = -mc.player.getYaw();
            pitchDeg = 0.0f;
        }

        if (!isLookingHorizontal) {
            yawDeg = -mc.player.getYaw();
        }

        return new PlanePose(center, yawDeg, pitchDeg);
    }

    private PlanePose smoothPlastPose(PlanePose target, float dt) {
        if (!plastHasSmoothedPose) {
            plastSmoothedCenter = target.center;
            plastSmoothedYawDeg = target.yawDeg;
            plastSmoothedPitchDeg = target.pitchDeg;
            plastHasSmoothedPose = true;
            return target;
        }

        float speed = Math.min(1.0f, dt * 16.0f);
        plastSmoothedCenter = new Vec3d(
                MathHelper.lerp(speed, plastSmoothedCenter.x, target.center.x),
                MathHelper.lerp(speed, plastSmoothedCenter.y, target.center.y),
                MathHelper.lerp(speed, plastSmoothedCenter.z, target.center.z)
        );
        plastSmoothedYawDeg = MathHelper.lerpAngleDegrees(speed, plastSmoothedYawDeg, target.yawDeg);
        plastSmoothedPitchDeg = MathHelper.lerpAngleDegrees(speed, plastSmoothedPitchDeg, target.pitchDeg);

        return new PlanePose(plastSmoothedCenter, plastSmoothedYawDeg, plastSmoothedPitchDeg);
    }

    public static class PlanePose {
        public final Vec3d center;
        public final float yawDeg;
        public final float pitchDeg;

        public PlanePose(Vec3d center, float yawDeg, float pitchDeg) {
            this.center = center;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
        }
    }
}
