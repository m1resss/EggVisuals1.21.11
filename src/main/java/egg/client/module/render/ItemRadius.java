package egg.client.module.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.shader.MeshDrawUtil;
import egg.client.render.shader.Pipelines;
import egg.client.render.world.TrajectoryLines;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import egg.client.util.FriendManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class ItemRadius extends Module {

    private static final String SRV_FT = "FunTime";
    private static final String SRV_HW = "HolyWorld";
    private static final String SRV_FC = "FunCubes";

    private static final int HIT_COLOR = 0xFF22C55E;
    private static final int FRIEND_COLOR = 0xFF2BFF88;

    private final ModeSetting server = new ModeSetting("Сервер", SRV_FT, "Выбор сервера", SRV_FT, SRV_HW, SRV_FC);

    private final BooleanSetting showDez = new BooleanSetting("Дезориентация", true, "Радиус дезориентации (10м)");
    private final BooleanSetting showYavka = new BooleanSetting("Явная пыль", true, "Радиус явной пыли (10м)");
    private final BooleanSetting showFireCharge = new BooleanSetting("Огненный смерч", true, "Радиус огненного смерча (10м)");
    private final BooleanSetting showGodAura = new BooleanSetting("Божья Аура", true, "Радиус божьей ауры (2м)");
    private final BooleanSetting showSnowball = new BooleanSetting("Снежок заморозка", true, "Траектория и радиус снежка заморозки (5м)");
    private final BooleanSetting showTrap = new BooleanSetting("Трапка", true, "Зона трапки (5x4x5 или 7x6x7)");
    private final BooleanSetting dragonTrap = new BooleanSetting("Драконья трапка", false, "Увеличенная зона драконьей трапки и пласта (7x6x7)");
    private final BooleanSetting showPlast = new BooleanSetting("Пласт", true, "Зона установки пласта (по взгляду)");
    private final BooleanSetting showDrill = new BooleanSetting("Бур / Бульдозер", true, "Зона копания кирки с чарами Бур или Бульдозер");

    private final BooleanSetting hwStun = new BooleanSetting("Стан (Звезда)", true, "Зона стана 30x30x30 (15м)");
    private final BooleanSetting hwExplosiveTrap = new BooleanSetting("Взрывная трапка", true, "Зона взрывной трапки (осколок призмарина)");
    private final BooleanSetting hwTrap = new BooleanSetting("Трапка HW", true, "Зона обычной трапки (плод хоруса)");
    private final BooleanSetting hwJake = new BooleanSetting("Светильник Джейка", true, "Радиус светильника Джейка (15м)");

    private final BooleanSetting fcLightning = new BooleanSetting("Шаровая молния", true, "Зона сбития элитр (10м)");
    private final BooleanSetting fcSlime = new BooleanSetting("Ком слизи", true, "Зона кома слизи (5м)");
    private final BooleanSetting fcScute = new BooleanSetting("Черепаший захват", true, "Зона черепашьего захвата (7x6x7)");
    private final BooleanSetting fcWeb = new BooleanSetting("Паутина судьбы", true, "Стена паутины 3x3 перед игроком");
    private final BooleanSetting fcStun = new BooleanSetting("Стан FC", true, "Зона стана 60x60x60 (30м)");
    private final BooleanSetting fcMagnet = new BooleanSetting("Магнитный шар", true, "Зона магнитного шара (7м)");

    private final BooleanSetting onlyDonate = new BooleanSetting("Только донат-NBT", false, "Проверять NBT и лор сервера (выкл — любые предметы)");
    private final SliderSetting fillOpacity = new SliderSetting("Прозрачность", 30.0, 0.0, 100.0, 5.0, "Прозрачность заливки");

    private float threatTimer = 0.0f;
    private long lastFrameNanos = 0L;
    private BufferAllocator lineAllocator;

    public ItemRadius() {
        super("ItemRadius", "Показывает зоны и радиусы донат-предметов (FunTime, HolyWorld, FunCubes)", Category.ESP);

        showDez.setVisible(() -> server.is(SRV_FT));
        showYavka.setVisible(() -> server.is(SRV_FT));
        showFireCharge.setVisible(() -> server.is(SRV_FT));
        showGodAura.setVisible(() -> server.is(SRV_FT));
        showSnowball.setVisible(() -> server.is(SRV_FT));
        showTrap.setVisible(() -> server.is(SRV_FT));
        dragonTrap.setVisible(() -> server.is(SRV_FT) && (showTrap.isEnabled() || showPlast.isEnabled()));
        showPlast.setVisible(() -> server.is(SRV_FT));
        showDrill.setVisible(() -> server.is(SRV_FT) || server.is(SRV_HW));

        hwStun.setVisible(() -> server.is(SRV_HW));
        hwExplosiveTrap.setVisible(() -> server.is(SRV_HW));
        hwTrap.setVisible(() -> server.is(SRV_HW));
        hwJake.setVisible(() -> server.is(SRV_HW));

        fcLightning.setVisible(() -> server.is(SRV_FC));
        fcSlime.setVisible(() -> server.is(SRV_FC));
        fcScute.setVisible(() -> server.is(SRV_FC));
        fcWeb.setVisible(() -> server.is(SRV_FC));
        fcStun.setVisible(() -> server.is(SRV_FC));
        fcMagnet.setVisible(() -> server.is(SRV_FC));

        addSettings(
                server,
                showDez, showYavka, showFireCharge, showGodAura, showSnowball, showTrap, dragonTrap, showPlast,
                hwStun, hwExplosiveTrap, hwTrap, hwJake,
                fcLightning, fcSlime, fcScute, fcWeb, fcStun, fcMagnet,
                showDrill, onlyDonate, fillOpacity
        );
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (lineAllocator != null) {
            lineAllocator.close();
            lineAllocator = null;
        }
        threatTimer = 0.0f;
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = Math.clamp((float) ((now - lastFrameNanos) * 1e-9), 0.001f, 0.05f);
        lastFrameNanos = now;

        float tickDelta = mc.getRenderTickCounter() != null ? mc.getRenderTickCounter().getTickProgress(false) : 1.0f;
        Vec3d playerPos = new Vec3d(
                MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX()),
                MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY()),
                MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ())
        );

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        List<String> activeTypes = new ArrayList<>(2);
        String mainType = resolveMatchingType(main);
        if (mainType != null) activeTypes.add(mainType);
        String offType = resolveMatchingType(off);
        if (offType != null && !offType.equals(mainType)) activeTypes.add(offType);

        if (activeTypes.isEmpty()) {
            threatTimer = Math.max(0.0f, threatTimer - dt * 4.0f);
            return;
        }

        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());
        int accentColor = Theme.getAccent();
        boolean anyEnemy = false;

        for (String type : activeTypes) {
            boolean hit = renderFeature(mc, type, type.equals(mainType) ? main : off, quadBuffer, playerPos, camPos, tickDelta, accentColor);
            if (hit) anyEnemy = true;
        }

        float targetThreat = anyEnemy ? 1.0f : 0.0f;
        threatTimer += (targetThreat - threatTimer) * Math.min(1.0f, dt * 8.0f);
    }

    private String resolveMatchingType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        boolean strict = onlyDonate.isEnabled();

        if (server.is(SRV_FT)) {
            if (showTrap.isEnabled() && item == Items.NETHERITE_SCRAP && (!strict || isFunTimeTrap(stack))) {
                return "ft_trap";
            }
            if (showPlast.isEnabled() && item == Items.DRIED_KELP && (!strict || isFunTimePlast(stack))) {
                return "ft_plast";
            }
            if (showDez.isEnabled() && item == Items.ENDER_EYE && (!strict || matchesKeywordOrBukkit(stack, "disorient", "dez", "дез", "ослеп"))) {
                return "ft_dez";
            }
            if (showYavka.isEnabled() && item == Items.SUGAR && (!strict || matchesKeywordOrBukkit(stack, "dust", "yavka", "явн", "пыль", "световая вспышка"))) {
                return "ft_yavka";
            }
            if (showFireCharge.isEnabled() && item == Items.FIRE_CHARGE && (!strict || matchesKeywordOrBukkit(stack, "fire", "smerch", "смерч", "огненная волна"))) {
                return "ft_fire";
            }
            if (showGodAura.isEnabled() && item == Items.PHANTOM_MEMBRANE && (!strict || matchesKeywordOrBukkit(stack, "aura", "аур", "бож", "божественная аура"))) {
                return "ft_aura";
            }
            if (showSnowball.isEnabled() && item == Items.SNOWBALL && (!strict || matchesKeywordOrBukkit(stack, "freeze", "snow", "снеж", "замороз", "ледяная сфера"))) {
                return "ft_snowball";
            }
            if (showDrill.isEnabled() && isPickaxe(item) && resolveDrillSpec(stack, true) != null) {
                return "drill_ft";
            }
            return null;
        }

        if (server.is(SRV_HW)) {
            if (hwStun.isEnabled() && item == Items.NETHER_STAR && (!strict || matchesKeywordOrKringe(stack, "stun", "стан"))) {
                return "hw_stun";
            }
            if (hwExplosiveTrap.isEnabled() && item == Items.PRISMARINE_SHARD && (!strict || matchesKeywordOrKringe(stack, "explos", "взрыв", "трап"))) {
                return "hw_explosive_trap";
            }
            if (hwTrap.isEnabled() && item == Items.POPPED_CHORUS_FRUIT && (!strict || matchesKeywordOrKringe(stack, "trap", "трап"))) {
                return "hw_trap";
            }
            if (hwJake.isEnabled() && item == Items.JACK_O_LANTERN && (!strict || isHolyWorldJake(stack))) {
                return "hw_jake";
            }
            if (showDrill.isEnabled() && isPickaxe(item) && resolveDrillSpec(stack, false) != null) {
                return "drill_hw";
            }
            return null;
        }

        if (server.is(SRV_FC)) {
            if (fcLightning.isEnabled() && item == Items.NETHER_STAR && (!strict || matchesKeywordOrBukkit(stack, "lightning", "молни", "шаров"))) {
                return "fc_lightning";
            }
            if (fcSlime.isEnabled() && item == Items.SLIME_BALL && (!strict || matchesKeywordOrBukkit(stack, "slime", "слиз", "ком"))) {
                return "fc_slime";
            }
            if (fcScute.isEnabled() && item == Items.TURTLE_SCUTE && (!strict || matchesKeywordOrBukkit(stack, "turtle", "scute", "черепа"))) {
                return "fc_scute";
            }
            if (fcWeb.isEnabled() && item == Items.COBWEB && (!strict || matchesKeywordOrBukkit(stack, "web", "паутин", "судьб"))) {
                return "fc_web";
            }
            if (fcStun.isEnabled() && item == Items.ENDER_EYE && (!strict || matchesKeywordOrBukkit(stack, "stun", "стан"))) {
                return "fc_stun";
            }
            if (fcMagnet.isEnabled() && item == Items.FIREWORK_STAR && (!strict || matchesKeywordOrBukkit(stack, "magnet", "магнит"))) {
                return "fc_magnet";
            }
        }

        return null;
    }

    private boolean renderFeature(MinecraftClient mc, String type, ItemStack stack, VertexConsumer buf,
                                  Vec3d playerPos, Vec3d camPos, float tickDelta, int accentColor) {
        switch (type) {
            case "ft_dez":
            case "ft_yavka":
            case "ft_fire": {
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                Vec3d circleCenter = new Vec3d(playerPos.x, groundY, playerPos.z);
                boolean hit = hasEnemyInRadius(mc, playerPos, 10.0);
                renderCyberCircle(buf, circleCenter, camPos, 10.0f, accentColor, HIT_COLOR, hit ? Math.max(threatTimer, 0.35f) : threatTimer);
                return hit;
            }
            case "ft_aura": {
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                Vec3d circleCenter = new Vec3d(playerPos.x, groundY, playerPos.z);
                boolean friendIn = hasFriendInRadius(mc, playerPos, 2.0);
                boolean enemyIn = hasEnemyInRadius(mc, playerPos, 2.0);
                int baseCol = friendIn ? FRIEND_COLOR : accentColor;
                renderCyberCircle(buf, circleCenter, camPos, 2.0f, baseCol, HIT_COLOR, enemyIn ? Math.max(threatTimer, 0.35f) : threatTimer);
                return enemyIn;
            }
            case "ft_snowball": {
                return renderSnowballZone(mc, buf, camPos, tickDelta, accentColor);
            }
            case "ft_trap": {
                BlockPos pos = mc.player.getBlockPos();
                boolean dragon = dragonTrap.isEnabled();
                Box zone = dragon
                        ? new Box(pos.getX() - 3, pos.getY(), pos.getZ() - 3, pos.getX() + 4, pos.getY() + 6, pos.getZ() + 4)
                        : new Box(pos.getX() - 2, pos.getY(), pos.getZ() - 2, pos.getX() + 3, pos.getY() + 4, pos.getZ() + 3);
                boolean hit = hasEnemyInBox(mc, zone);
                renderZoneBox(buf, zone.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
                return hit;
            }
            case "ft_plast": {
                return renderFunTimePlast(mc, buf, camPos, accentColor);
            }
            case "drill_ft": {
                int[] spec = resolveDrillSpec(stack, true);
                if (spec != null) {
                    Box box = computeDrillBox(mc, spec[0], spec[1]);
                    if (box != null) {
                        renderZoneBox(buf, box.contract(0.005), camPos, accentColor, HIT_COLOR, 0.0f, false);
                    }
                }
                return false;
            }
            case "hw_stun": {
                Box box = new Box(playerPos.x - 15.0, playerPos.y - 15.0, playerPos.z - 15.0,
                        playerPos.x + 15.0, playerPos.y + 15.0, playerPos.z + 15.0);
                boolean hit = hasEnemyInBox(mc, box);
                renderZoneBox(buf, box, camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, false);
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                renderCyberCircle(buf, new Vec3d(playerPos.x, groundY, playerPos.z), camPos, 15.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            case "hw_explosive_trap": {
                BlockPos pos = mc.player.getBlockPos();
                Box visual = new Box(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3, pos.getX() + 4, pos.getY(), pos.getZ() + 4);
                Box detect = new Box(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
                boolean hit = hasEnemyInBox(mc, detect);
                renderZoneBox(buf, visual.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
                return hit;
            }
            case "hw_trap": {
                BlockPos pos = mc.player.getBlockPos();
                Box zone = new Box(pos.getX() - 2, pos.getY(), pos.getZ() - 2, pos.getX() + 3, pos.getY() + 4, pos.getZ() + 3);
                boolean hit = hasEnemyInBox(mc, zone);
                renderZoneBox(buf, zone.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
                return hit;
            }
            case "hw_jake": {
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                Vec3d circleCenter = new Vec3d(playerPos.x, groundY, playerPos.z);
                boolean hit = hasEnemyInRadius(mc, playerPos, 15.0);
                renderCyberCircle(buf, circleCenter, camPos, 15.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            case "drill_hw": {
                int[] spec = resolveDrillSpec(stack, false);
                if (spec != null) {
                    Box box = computeDrillBox(mc, spec[0], spec[1]);
                    if (box != null) {
                        renderZoneBox(buf, box.contract(0.005), camPos, accentColor, HIT_COLOR, 0.0f, false);
                    }
                }
                return false;
            }
            case "fc_lightning": {
                Box box = new Box(playerPos.x - 10.0, playerPos.y - 10.0, playerPos.z - 10.0,
                        playerPos.x + 10.0, playerPos.y + 10.0, playerPos.z + 10.0);
                boolean hit = hasElytraEnemyInBox(mc, box);
                renderZoneBox(buf, box, camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, false);
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                renderCyberCircle(buf, new Vec3d(playerPos.x, groundY, playerPos.z), camPos, 10.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            case "fc_slime": {
                double y = playerPos.y + 1.0;
                Box detect = new Box(playerPos.x - 5.0, y - 5.0, playerPos.z - 5.0, playerPos.x + 5.0, y + 5.0, playerPos.z + 5.0);
                boolean hit = hasEnemyInBox(mc, detect);
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                renderCyberCircle(buf, new Vec3d(playerPos.x, groundY, playerPos.z), camPos, 5.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            case "fc_scute": {
                BlockPos pos = mc.player.getBlockPos();
                Box zone = new Box(pos.getX() - 3, pos.getY() - 1, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 5, pos.getZ() + 4);
                boolean hit = hasEnemyInBox(mc, zone);
                renderZoneBox(buf, zone.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
                return hit;
            }
            case "fc_web": {
                BlockPos pos = mc.player.getBlockPos();
                float yaw = (mc.player.getYaw() % 360.0f + 360.0f) % 360.0f;
                BlockPos base;
                boolean xWide;
                if (yaw >= 315.0f || yaw < 45.0f) {
                    base = pos.add(0, 0, 3);
                    xWide = true;
                } else if (yaw >= 45.0f && yaw < 135.0f) {
                    base = pos.add(-3, 0, 0);
                    xWide = false;
                } else if (yaw >= 135.0f && yaw < 225.0f) {
                    base = pos.add(0, 0, -3);
                    xWide = true;
                } else {
                    base = pos.add(3, 0, 0);
                    xWide = false;
                }
                Box wall = xWide
                        ? new Box(base.getX() - 1, base.getY(), base.getZ(), base.getX() + 2, base.getY() + 3, base.getZ() + 1)
                        : new Box(base.getX(), base.getY(), base.getZ() - 1, base.getX() + 1, base.getY() + 3, base.getZ() + 2);
                boolean hit = hasEnemyInBox(mc, wall);
                renderZoneBox(buf, wall.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
                return hit;
            }
            case "fc_stun": {
                Box box = new Box(playerPos.x - 30.0, playerPos.y - 30.0, playerPos.z - 30.0,
                        playerPos.x + 30.0, playerPos.y + 30.0, playerPos.z + 30.0);
                boolean hit = hasEnemyInBox(mc, box);
                renderZoneBox(buf, box, camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, false);
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                renderCyberCircle(buf, new Vec3d(playerPos.x, groundY, playerPos.z), camPos, 30.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            case "fc_magnet": {
                Box detect = new Box(playerPos.x - 7.0, playerPos.y - 6.0, playerPos.z - 7.0,
                        playerPos.x + 7.0, playerPos.y + 8.0, playerPos.z + 7.0);
                boolean hit = hasEnemyInBox(mc, detect);
                double groundY = findGroundY(mc, playerPos.x, playerPos.y, playerPos.z);
                renderCyberCircle(buf, new Vec3d(playerPos.x, groundY, playerPos.z), camPos, 7.0f, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
                return hit;
            }
            default:
                return false;
        }
    }

    private boolean renderFunTimePlast(MinecraftClient mc, VertexConsumer buf, Vec3d camPos, int accentColor) {
        BlockPos pos = mc.player.getBlockPos();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        boolean dragon = dragonTrap.isEnabled();

        if (Math.abs(pitch) > 45.0f) {
            Box detect;
            if (pitch < -45.0f) {
                detect = dragon
                        ? new Box(pos.getX() - 3, pos.getY() + 3, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 7, pos.getZ() + 4)
                        : new Box(pos.getX() - 2, pos.getY() + 3, pos.getZ() - 2, pos.getX() + 3, pos.getY() + 5, pos.getZ() + 3);
            } else {
                detect = dragon
                        ? new Box(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3, pos.getX() + 4, pos.getY() - 1, pos.getZ() + 4)
                        : new Box(pos.getX() - 2, pos.getY() - 3, pos.getZ() - 2, pos.getX() + 3, pos.getY() - 1, pos.getZ() + 3);
            }
            boolean hit = hasEnemyInBox(mc, detect);
            renderZoneBox(buf, detect.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
            return hit;
        }

        float normYaw = (yaw % 360.0f + 360.0f) % 360.0f;
        float diagThreshold = 22.0f;
        boolean diagonal = Math.abs(normYaw - 45.0f) < diagThreshold
                || Math.abs(normYaw - 135.0f) < diagThreshold
                || Math.abs(normYaw - 225.0f) < diagThreshold
                || Math.abs(normYaw - 315.0f) < diagThreshold;

        if (diagonal) {
            float d45 = Math.abs(normYaw - 45.0f);
            float d135 = Math.abs(normYaw - 135.0f);
            float d225 = Math.abs(normYaw - 225.0f);
            float d315 = Math.abs(normYaw - 315.0f);
            float minD = Math.min(Math.min(d45, d135), Math.min(d225, d315));

            int snapped;
            int ox;
            int oz;
            if (minD == d45) {
                snapped = 45;
                ox = -3;
                oz = 2;
            } else if (minD == d135) {
                snapped = 135;
                ox = -3;
                oz = -2;
            } else if (minD == d225) {
                snapped = 225;
                ox = 2;
                oz = -2;
            } else {
                snapped = 315;
                ox = 2;
                oz = 2;
            }

            BlockPos origin = pos.add(ox, -1, oz);
            Set<BlockPos> blocks = new HashSet<>();
            int rangeMin = dragon ? -3 : -2;
            int rangeMax = dragon ? 3 : 2;
            int height = dragon ? 7 : 5;

            for (int d = rangeMin; d <= rangeMax; d++) {
                int dz = (snapped == 45 || snapped == 225) ? d : -d;
                for (int y = 0; y < height; y++) {
                    blocks.add(origin.add(d, y, dz));
                    blocks.add(origin.add(d + 1, y, dz));
                }
            }

            boolean hit = hasEnemyInBlockCluster(mc, blocks);
            renderBlockCluster(buf, blocks, camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer);
            return hit;
        }

        int forwardDist = 3;
        BlockPos anchor;
        boolean alongZ;
        if (normYaw >= 315.0f || normYaw < 45.0f) {
            anchor = pos.add(0, -1, forwardDist);
            alongZ = true;
        } else if (normYaw >= 45.0f && normYaw < 135.0f) {
            anchor = pos.add(-forwardDist, -1, 0);
            alongZ = false;
        } else if (normYaw >= 135.0f && normYaw < 225.0f) {
            anchor = pos.add(0, -1, -forwardDist);
            alongZ = true;
        } else {
            anchor = pos.add(forwardDist, -1, 0);
            alongZ = false;
        }

        Box detect;
        if (!alongZ) {
            detect = dragon
                    ? new Box(anchor.getX() - 1, anchor.getY(), anchor.getZ() - 3, anchor.getX() + 1, anchor.getY() + 7, anchor.getZ() + 4)
                    : new Box(anchor.getX() - 1, anchor.getY(), anchor.getZ() - 2, anchor.getX() + 1, anchor.getY() + 5, anchor.getZ() + 3);
        } else {
            detect = dragon
                    ? new Box(anchor.getX() - 3, anchor.getY(), anchor.getZ() - 1, anchor.getX() + 4, anchor.getY() + 7, anchor.getZ() + 1)
                    : new Box(anchor.getX() - 2, anchor.getY(), anchor.getZ() - 1, anchor.getX() + 3, anchor.getY() + 5, anchor.getZ() + 1);
        }

        boolean hit = hasEnemyInBox(mc, detect);
        renderZoneBox(buf, detect.contract(0.01), camPos, accentColor, HIT_COLOR, hit ? 1.0f : threatTimer, true);
        return hit;
    }

    private boolean renderSnowballZone(MinecraftClient mc, VertexConsumer quadBuf, Vec3d camPos, float tickDelta, int accentColor) {
        Vec3d start = mc.player.getCameraPosVec(tickDelta);
        float yaw = mc.player.getYaw(tickDelta);
        float pitch = mc.player.getPitch(tickDelta);

        float cosP = MathHelper.cos(pitch * (float) (Math.PI / 180.0));
        float sinP = MathHelper.sin(pitch * (float) (Math.PI / 180.0));
        float cosY = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
        float sinY = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
        Vec3d dir = new Vec3d(-sinY * cosP, -sinP, cosY * cosP).normalize();

        Vec3d vel = dir.multiply(1.5).add(0.0, mc.player.getVelocity().y * 0.5, 0.0);
        Vec3d pos = start;
        List<Vec3d> path = new ArrayList<>(64);
        path.add(pos);

        Vec3d impact = null;
        for (int step = 0; step < 160; step++) {
            Vec3d next = pos.add(vel);
            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
            ));
            Entity hitEntity = findEntityAlongSegment(mc, pos, blockHit.getType() != HitResult.Type.MISS ? blockHit.getPos() : next);
            if (hitEntity != null) {
                impact = hitEntity.getBoundingBox().expand(0.3).raycast(pos, next).orElse(hitEntity.getEntityPos());
                path.add(impact);
                break;
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                impact = blockHit.getPos();
                path.add(impact);
                break;
            }
            if (next.y < mc.world.getBottomY()) {
                impact = next;
                path.add(impact);
                break;
            }
            pos = next;
            path.add(pos);
            vel = vel.multiply(0.99).subtract(0.0, 0.03, 0.0);
        }

        if (impact == null) return false;

        float radius = 5.0f;
        boolean enemyInZone = hasEnemyInRadius(mc, impact, radius);
        int drawColor = ColorUtil.interpolate(accentColor, HIT_COLOR, enemyInZone ? 1.0f : threatTimer);

        if (path.size() >= 2) {
            if (lineAllocator == null) {
                lineAllocator = new BufferAllocator(16 * 1024);
            }
            try {
                BufferBuilder mesh = new BufferBuilder(lineAllocator, DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
                Vector3f forward = new Vector3f(0, 0, -1).rotate(mc.gameRenderer.getCamera().getRotation());
                float pixelScale = MathHelper.clamp(mc.getFramebuffer().textureHeight / 1080.0f, 0.78f, 1.75f);
                TrajectoryLines lines = new TrajectoryLines(mesh, camPos, new Vec3d(forward.x, forward.y, forward.z), 1.6f, pixelScale, false);
                lines.path(path, Vec3d.ZERO, drawColor);
                var built = mesh.endNullable();
                if (built != null) {
                    MeshDrawUtil.drawLabeled(Pipelines.TRAJECTORY_LINE, built, "ItemRadius snowball");
                }
            } finally {
                lineAllocator.reset();
            }
        }

        double groundY = findGroundY(mc, impact.x, impact.y + 0.15, impact.z);
        Vec3d ringCenter = new Vec3d(impact.x, Math.max(groundY, impact.y + 0.025), impact.z);
        renderCyberCircle(quadBuf, ringCenter, camPos, radius, accentColor, HIT_COLOR, enemyInZone ? 1.0f : threatTimer);
        return enemyInZone;
    }

    private Entity findEntityAlongSegment(MinecraftClient mc, Vec3d start, Vec3d end) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Box search = new Box(start, end).expand(1.0);
        for (Entity candidate : mc.world.getOtherEntities(mc.player, search)) {
            if (!candidate.isAlive() || candidate.isSpectator() || !(candidate instanceof LivingEntity) || EntityUtil.isNakedInvisible(candidate)) {
                continue;
            }
            Optional<Vec3d> hit = candidate.getBoundingBox().expand(0.3).raycast(start, end);
            if (hit.isPresent()) {
                double distSq = start.squaredDistanceTo(hit.get());
                if (distSq < closestDist) {
                    closestDist = distSq;
                    closest = candidate;
                }
            }
        }
        return closest;
    }

    private Box computeDrillBox(MinecraftClient mc, int radius, int depth) {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getSide();
        Direction.Axis axis = side.getAxis();
        Direction opposite = side.getOpposite();

        BlockPos p1 = offsetInPlane(pos, axis, -radius, -radius);
        BlockPos p2 = offsetInPlane(pos.offset(opposite, depth - 1), axis, radius, radius);

        return new Box(
                Math.min(p1.getX(), p2.getX()),
                Math.min(p1.getY(), p2.getY()),
                Math.min(p1.getZ(), p2.getZ()),
                Math.max(p1.getX(), p2.getX()) + 1,
                Math.max(p1.getY(), p2.getY()) + 1,
                Math.max(p1.getZ(), p2.getZ()) + 1
        );
    }

    private static BlockPos offsetInPlane(BlockPos pos, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> pos.add(0, a, b);
            case Y -> pos.add(a, 0, b);
            case Z -> pos.add(a, b, 0);
        };
    }

    private boolean isFunTimeTrap(ItemStack stack) {
        String ftTag = readBukkitTag(stack);
        if ("trap".equals(ftTag) || readBukkitInt(stack, "minecraft:trap") == 1) return true;
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("трап") || name.contains("trap")) return true;
        return hasLoreContaining(stack, "нерушимая клетка");
    }

    private boolean isFunTimePlast(ItemStack stack) {
        String ftTag = readBukkitTag(stack);
        if ("stratum".equals(ftTag) || readBukkitInt(stack, "minecraft:stratum") == 1) return true;
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("пласт") || name.contains("stratum")) return true;
        return hasLoreContaining(stack, "нерушимая стена");
    }

    private boolean isHolyWorldJake(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            NbtCompound root = data.copyNbt();
            NbtCompound kringe = root.getCompound("kringeItems").orElse(null);
            if (kringe != null && "JakeLuminaire".equals(kringe.getString("type").orElse(""))) {
                return true;
            }
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        return name.contains("джейк") || name.contains("jake");
    }

    private boolean matchesKeywordOrBukkit(ItemStack stack, String... keywords) {
        String ftTag = readBukkitTag(stack);
        String name = stack.getName().getString().toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (String kw : keywords) {
            if (ftTag != null && ftTag.contains(kw)) return true;
            if (name.contains(kw)) return true;
            if (hasLoreContaining(stack, kw)) return true;
        }
        return false;
    }

    private boolean matchesKeywordOrKringe(ItemStack stack, String... keywords) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            NbtCompound root = data.copyNbt();
            if (root.contains("kringeItems")) return true;
        }
        return matchesKeywordOrBukkit(stack, keywords);
    }

    private static String readBukkitTag(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        NbtCompound pbv = data.copyNbt().getCompound("PublicBukkitValues").orElse(null);
        if (pbv == null || pbv.isEmpty()) return null;
        String ftid = pbv.getString("minecraft:ftid").orElse("");
        if (!ftid.isEmpty()) return ftid.toLowerCase(Locale.ROOT);
        String don = pbv.getString("minecraft:don-item").orElse("");
        return don.isEmpty() ? null : don.toLowerCase(Locale.ROOT);
    }

    private static int readBukkitInt(ItemStack stack, String key) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return 0;
        NbtCompound pbv = data.copyNbt().getCompound("PublicBukkitValues").orElse(null);
        if (pbv == null) return 0;
        return pbv.getInt(key).orElse(0);
    }

    private static boolean hasLoreContaining(ItemStack stack, String substr) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        for (Text line : lore.lines()) {
            String s = line.getString().toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (s.contains(substr)) return true;
        }
        return false;
    }

    private static boolean isPickaxe(Item item) {
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE
                || item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE
                || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    private static int[] resolveDrillSpec(ItemStack stack, boolean funTime) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            NbtCompound root = data.copyNbt();
            NbtCompound pbv = root.getCompound("PublicBukkitValues").orElse(null);
            if (pbv != null) {
                NbtList enchants = pbv.getList("minecraft:custom-enchantments").orElse(null);
                if (enchants != null) {
                    for (int i = 0; i < enchants.size(); i++) {
                        NbtCompound entry = enchants.getCompound(i).orElse(null);
                        if (entry == null) continue;
                        String type = entry.getString("minecraft:type").orElse("").toLowerCase(Locale.ROOT);
                        if (type.isEmpty()) continue;
                        if (funTime) {
                            if (type.contains("mega")) return new int[]{4, 5};
                            if (type.contains("bulldozer")) {
                                int lvl = entry.getInt("minecraft:level").orElse(1);
                                return new int[]{1, lvl >= 2 ? 3 : 1};
                            }
                        } else if (type.contains("drill")) {
                            int lvl = entry.getInt("minecraft:level").orElse(1);
                            return new int[]{1, lvl >= 2 ? 2 : 1};
                        }
                    }
                }
            }
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text text : lore.lines()) {
                String line = text.getString();
                if (line == null) continue;
                String lower = line.toLowerCase(Locale.ROOT);
                if (funTime) {
                    if (lower.contains("9x9x5") || lower.contains("мега-бульдозер")) return new int[]{4, 5};
                    if (line.contains("Бульдозер II")) return new int[]{1, 3};
                    if (line.contains("Бульдозер I")) return new int[]{1, 1};
                } else {
                    if (line.contains("Бур II")) return new int[]{1, 2};
                    if (line.contains("Бур I")) return new int[]{1, 1};
                }
            }
        }
        return null;
    }

    private boolean hasEnemyInRadius(MinecraftClient mc, Vec3d center, double radius) {
        if (mc.world == null || mc.player == null) return false;
        double rSq = radius * radius;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!isValidTarget(mc.player, p, true)) continue;
            if (p.squaredDistanceTo(center) <= rSq) return true;
        }
        return false;
    }

    private boolean hasFriendInRadius(MinecraftClient mc, Vec3d center, double radius) {
        if (mc.world == null || mc.player == null) return false;
        double rSq = radius * radius;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!isValidTarget(mc.player, p, false)) continue;
            if (p.squaredDistanceTo(center) <= rSq) return true;
        }
        return false;
    }

    private boolean hasEnemyInBox(MinecraftClient mc, Box box) {
        if (mc.world == null || mc.player == null) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!isValidTarget(mc.player, p, true)) continue;
            if (box.contains(p.getEntityPos()) || box.intersects(p.getBoundingBox())) return true;
        }
        return false;
    }

    private boolean hasElytraEnemyInBox(MinecraftClient mc, Box box) {
        if (mc.world == null || mc.player == null) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!isValidTarget(mc.player, p, true)) continue;
            if (p.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) continue;
            if (box.contains(p.getEntityPos()) || box.intersects(p.getBoundingBox())) return true;
        }
        return false;
    }

    private boolean hasEnemyInBlockCluster(MinecraftClient mc, Set<BlockPos> blocks) {
        if (mc.world == null || mc.player == null) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!isValidTarget(mc.player, p, true)) continue;
            BlockPos bp = p.getBlockPos();
            if (blocks.contains(bp) || blocks.contains(bp.up())) return true;
        }
        return false;
    }

    private static boolean isValidTarget(PlayerEntity self, PlayerEntity p, boolean enemyOnly) {
        if (p == self || p.isSpectator() || !p.isAlive()) return false;
        if (EntityUtil.isNakedInvisible(p)) return false;
        boolean isFriend = FriendManager.getInstance().isFriend(p.getName().getString());
        return enemyOnly != isFriend;
    }

    private static double findGroundY(MinecraftClient mc, double x, double y, double z) {
        Vec3d start = new Vec3d(x, y + 0.35, z);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                start, new Vec3d(x, y - 3.0, z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos().y + 0.025;
        }
        return y + 0.025;
    }

    private void renderCyberCircle(VertexConsumer buf, Vec3d center, Vec3d camPos, float radius,
                                   int primaryCol, int hitCol, float threat) {
        int color = ColorUtil.interpolate(primaryCol, hitCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y - camPos.y);
        float cz = (float) (center.z - camPos.z);

        int segments = 72;
        float angleStep = (float) (2.0 * Math.PI / segments);
        float baseFillAlpha = (float) fillOpacity.get();

        if (baseFillAlpha > 0.5f) {
            int alphaInner = Math.round(baseFillAlpha * 0.45f);
            int alphaOuter = Math.round(baseFillAlpha * 0.85f);
            float fillRadius = Math.max(0.2f, radius - 0.05f);

            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;

                float x1 = cx + MathHelper.sin(a1) * fillRadius;
                float z1 = cz + MathHelper.cos(a1) * fillRadius;
                float x2 = cx + MathHelper.sin(a2) * fillRadius;
                float z2 = cz + MathHelper.cos(a2) * fillRadius;

                drawTriDoubleSided(buf, cx, cy, cz, x1, cy, z1, x2, cy, z2, r, g, b, alphaInner, alphaOuter);
            }
        }

        float coreHalfW = 0.045f;
        float haloWidth = 0.14f;
        float rIn = Math.max(0.05f, radius - coreHalfW);
        float rOut = radius + coreHalfW;
        float rHalo = radius + haloWidth;

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;
            float s1 = MathHelper.sin(a1), c1 = MathHelper.cos(a1);
            float s2 = MathHelper.sin(a2), c2 = MathHelper.cos(a2);

            drawQuadDoubleSided(buf,
                    cx + s1 * rIn, cy + 0.01f, cz + c1 * rIn,
                    cx + s1 * rOut, cy + 0.01f, cz + c1 * rOut,
                    cx + s2 * rOut, cy + 0.01f, cz + c2 * rOut,
                    cx + s2 * rIn, cy + 0.01f, cz + c2 * rIn,
                    r, g, b, 235);

            drawQuadDoubleSidedGrad(buf,
                    cx + s1 * rOut, cy + 0.01f, cz + c1 * rOut,
                    cx + s1 * rHalo, cy + 0.01f, cz + c1 * rHalo,
                    cx + s2 * rHalo, cy + 0.01f, cz + c2 * rHalo,
                    cx + s2 * rOut, cy + 0.01f, cz + c2 * rOut,
                    r, g, b, 90, 0);
        }
    }

    private void renderZoneBox(VertexConsumer buf, Box box, Vec3d camPos,
                               int primaryCol, int hitCol, float threat, boolean showCorners) {
        int color = ColorUtil.interpolate(primaryCol, hitCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float x1 = (float) (box.minX - camPos.x);
        float y1 = (float) (box.minY - camPos.y);
        float z1 = (float) (box.minZ - camPos.z);
        float x2 = (float) (box.maxX - camPos.x);
        float y2 = (float) (box.maxY - camPos.y);
        float z2 = (float) (box.maxZ - camPos.z);

        int faceAlpha = Math.round(MathHelper.clamp((float) fillOpacity.get(), 0.0f, 100.0f) * 0.65f);
        if (faceAlpha > 0) {
            drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, faceAlpha);
            drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, faceAlpha);
            drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, faceAlpha);
            drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, faceAlpha);
            drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, faceAlpha);
            drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, faceAlpha);
        }

        float edgeT = 0.018f;
        int edgeA = 235;
        drawSolidBox(buf, x1 - edgeT, y1 - edgeT, z1 - edgeT, x2 + edgeT, y1 + edgeT, z1 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x1 - edgeT, y1 - edgeT, z2 - edgeT, x2 + edgeT, y1 + edgeT, z2 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x1 - edgeT, y2 - edgeT, z1 - edgeT, x2 + edgeT, y2 + edgeT, z1 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x1 - edgeT, y2 - edgeT, z2 - edgeT, x2 + edgeT, y2 + edgeT, z2 + edgeT, r, g, b, edgeA);

        drawSolidBox(buf, x1 - edgeT, y1, z1 - edgeT, x1 + edgeT, y2, z1 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x2 - edgeT, y1, z1 - edgeT, x2 + edgeT, y2, z1 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x1 - edgeT, y1, z2 - edgeT, x1 + edgeT, y2, z2 + edgeT, r, g, b, edgeA);
        drawSolidBox(buf, x2 - edgeT, y1, z2 - edgeT, x2 + edgeT, y2, z2 + edgeT, r, g, b, edgeA);

        drawSolidBox(buf, x1 - edgeT, y1 - edgeT, z1, x1 + edgeT, y1 + edgeT, z2, r, g, b, edgeA);
        drawSolidBox(buf, x2 - edgeT, y1 - edgeT, z1, x2 + edgeT, y1 + edgeT, z2, r, g, b, edgeA);
        drawSolidBox(buf, x1 - edgeT, y2 - edgeT, z1, x1 + edgeT, y2 + edgeT, z2, r, g, b, edgeA);
        drawSolidBox(buf, x2 - edgeT, y2 - edgeT, z1, x2 + edgeT, y2 + edgeT, z2, r, g, b, edgeA);

        if (showCorners) {
            float bLen = Math.min(0.60f, Math.min(x2 - x1, Math.min(y2 - y1, z2 - z1)) * 0.25f);
            float cT = 0.035f;
            float[] xs = {x1, x2};
            float[] ys = {y1, y2};
            float[] zs = {z1, z2};
            for (float kx : xs) {
                float sx = kx == x1 ? bLen : -bLen;
                for (float ky : ys) {
                    float sy = ky == y1 ? bLen : -bLen;
                    for (float kz : zs) {
                        float sz = kz == z1 ? bLen : -bLen;
                        drawSolidBox(buf, Math.min(kx, kx + sx) - cT, ky - cT, kz - cT, Math.max(kx, kx + sx) + cT, ky + cT, kz + cT, r, g, b, 255);
                        drawSolidBox(buf, kx - cT, Math.min(ky, ky + sy) - cT, kz - cT, kx + cT, Math.max(ky, ky + sy) + cT, kz + cT, r, g, b, 255);
                        drawSolidBox(buf, kx - cT, ky - cT, Math.min(kz, kz + sz) - cT, kx + cT, ky + cT, Math.max(kz, kz + sz) + cT, r, g, b, 255);
                    }
                }
            }
        }
    }

    private void renderBlockCluster(VertexConsumer buf, Set<BlockPos> blocks, Vec3d camPos,
                                    int primaryCol, int hitCol, float threat) {
        if (blocks.isEmpty()) return;
        int color = ColorUtil.interpolate(primaryCol, hitCol, threat);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos bp : blocks) {
            minX = Math.min(minX, bp.getX());
            minY = Math.min(minY, bp.getY());
            minZ = Math.min(minZ, bp.getZ());
            maxX = Math.max(maxX, bp.getX());
            maxY = Math.max(maxY, bp.getY());
            maxZ = Math.max(maxZ, bp.getZ());
        }

        int faceAlpha = Math.round(MathHelper.clamp((float) fillOpacity.get(), 10.0f, 100.0f) * 0.72f);
        float edgeT = 0.015f;

        for (BlockPos bp : blocks) {
            float iMinX = bp.getX() == minX ? 0.01f : 0.0f;
            float iMinY = bp.getY() == minY ? 0.01f : 0.0f;
            float iMinZ = bp.getZ() == minZ ? 0.01f : 0.0f;
            float iMaxX = bp.getX() == maxX ? 0.01f : 0.0f;
            float iMaxY = bp.getY() == maxY ? 0.01f : 0.0f;
            float iMaxZ = bp.getZ() == maxZ ? 0.01f : 0.0f;

            float x1 = (float) (bp.getX() - camPos.x + iMinX);
            float y1 = (float) (bp.getY() - camPos.y + iMinY);
            float z1 = (float) (bp.getZ() - camPos.z + iMinZ);
            float x2 = (float) (bp.getX() + 1 - camPos.x - iMaxX);
            float y2 = (float) (bp.getY() + 1 - camPos.y - iMaxY);
            float z2 = (float) (bp.getZ() + 1 - camPos.z - iMaxZ);

            boolean down = blocks.contains(bp.down());
            boolean up = blocks.contains(bp.up());
            boolean north = blocks.contains(bp.north());
            boolean south = blocks.contains(bp.south());
            boolean west = blocks.contains(bp.west());
            boolean east = blocks.contains(bp.east());

            if (!down) drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, faceAlpha);
            if (!up) drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, faceAlpha);
            if (!north) drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, faceAlpha);
            if (!south) drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, faceAlpha);
            if (!west) drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, faceAlpha);
            if (!east) drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, faceAlpha);

            if (!down) {
                if (!west) drawSolidBox(buf, x1 - edgeT, y1 - edgeT, z1, x1 + edgeT, y1 + edgeT, z2, r, g, b, 235);
                if (!east) drawSolidBox(buf, x2 - edgeT, y1 - edgeT, z1, x2 + edgeT, y1 + edgeT, z2, r, g, b, 235);
                if (!north) drawSolidBox(buf, x1, y1 - edgeT, z1 - edgeT, x2, y1 + edgeT, z1 + edgeT, r, g, b, 235);
                if (!south) drawSolidBox(buf, x1, y1 - edgeT, z2 - edgeT, x2, y1 + edgeT, z2 + edgeT, r, g, b, 235);
            }
            if (!up) {
                if (!west) drawSolidBox(buf, x1 - edgeT, y2 - edgeT, z1, x1 + edgeT, y2 + edgeT, z2, r, g, b, 235);
                if (!east) drawSolidBox(buf, x2 - edgeT, y2 - edgeT, z1, x2 + edgeT, y2 + edgeT, z2, r, g, b, 235);
                if (!north) drawSolidBox(buf, x1, y2 - edgeT, z1 - edgeT, x2, y2 + edgeT, z1 + edgeT, r, g, b, 235);
                if (!south) drawSolidBox(buf, x1, y2 - edgeT, z2 - edgeT, x2, y2 + edgeT, z2 + edgeT, r, g, b, 235);
            }
            if (!west && !north) drawSolidBox(buf, x1 - edgeT, y1, z1 - edgeT, x1 + edgeT, y2, z1 + edgeT, r, g, b, 235);
            if (!east && !north) drawSolidBox(buf, x2 - edgeT, y1, z1 - edgeT, x2 + edgeT, y2, z1 + edgeT, r, g, b, 235);
            if (!east && !south) drawSolidBox(buf, x2 - edgeT, y1, z2 - edgeT, x2 + edgeT, y2, z2 + edgeT, r, g, b, 235);
            if (!west && !south) drawSolidBox(buf, x1 - edgeT, y1, z2 - edgeT, x1 + edgeT, y2, z2 + edgeT, r, g, b, 235);
        }
    }

    private static void drawSolidBox(VertexConsumer buf,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     int r, int g, int b, int a) {
        if (x1 > x2) { float t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { float t = y1; y1 = y2; y2 = t; }
        if (z1 > z2) { float t = z1; z1 = z2; z2 = t; }
        drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
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

    private static void drawQuadDoubleSidedGrad(VertexConsumer buf,
                                                float x1, float y1, float z1,
                                                float x2, float y2, float z2,
                                                float x3, float y3, float z3,
                                                float x4, float y4, float z4,
                                                int r, int g, int b, int aIn, int aOut) {
        buf.vertex(x1, y1, z1).color(r, g, b, aIn);
        buf.vertex(x2, y2, z2).color(r, g, b, aOut);
        buf.vertex(x3, y3, z3).color(r, g, b, aOut);
        buf.vertex(x4, y4, z4).color(r, g, b, aIn);

        buf.vertex(x4, y4, z4).color(r, g, b, aIn);
        buf.vertex(x3, y3, z3).color(r, g, b, aOut);
        buf.vertex(x2, y2, z2).color(r, g, b, aOut);
        buf.vertex(x1, y1, z1).color(r, g, b, aIn);
    }

    private static void drawTriDoubleSided(VertexConsumer buf,
                                           float cx, float cy, float cz,
                                           float x1, float y1, float z1,
                                           float x2, float y2, float z2,
                                           int r, int g, int b, int aCenter, int aEdge) {
        buf.vertex(cx, cy, cz).color(r, g, b, aCenter);
        buf.vertex(x1, y1, z1).color(r, g, b, aEdge);
        buf.vertex(x2, y2, z2).color(r, g, b, aEdge);
        buf.vertex(cx, cy, cz).color(r, g, b, aCenter);

        buf.vertex(cx, cy, cz).color(r, g, b, aCenter);
        buf.vertex(x2, y2, z2).color(r, g, b, aEdge);
        buf.vertex(x1, y1, z1).color(r, g, b, aEdge);
        buf.vertex(cx, cy, cz).color(r, g, b, aCenter);
    }
}
