package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.misc.Globals;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.EntityUtil;
import egg.client.util.FriendManager;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NameTags extends Module {
    private static NameTags instance;

    private static final Identifier EGG_LOGO = Identifier.of("egg", "textures/egg_logo.png");

    private final BooleanSetting showHealth = new BooleanSetting("Здоровье", true, "Шкала и числовое значение HP");
    private final BooleanSetting showPing = new BooleanSetting("Пинг", true, "Индикатор задержки игрока");
    private final BooleanSetting showDistance = new BooleanSetting("Дистанция", true, "Расстояние в метрах");
    private final BooleanSetting showArmor = new BooleanSetting("Броня и руки", true, "Экипированные предметы над ником");
    private final BooleanSetting showSelf = new BooleanSetting("Себя от 3-го лица", false, "Отображать бейдж над своим персонажем");
    private final SliderSetting bgAlpha = new SliderSetting("Непрозрачность", 85.0, 20.0, 100.0, 5.0, "Плотность подложки (%)");
    private final ModeSetting colorMode = new ModeSetting("Цвет шкалы", "Градиент", "Градиент", "Тема", "Здоровье");

    private final BooleanSetting showItems = new BooleanSetting("Предметы", true, "Отображать названия и количество выброшенных предметов");
    private final BooleanSetting lootBeam = new BooleanSetting("Лучи над ценным лутом", true, "Вертикальные лучи света над ценными предметами");
    private final BooleanSetting onlyValuable = new BooleanSetting("Только ценные", false, "Отображать неймтеги только для ценного лута");
    private final BooleanSetting itemIcons = new BooleanSetting("Иконка предмета", true, "Мини-иконка предмета в плашке");

    private static class PlayerTagState {
        float smoothX = 0.0f;
        float smoothY = 0.0f;
        boolean smoothInit = false;
        float visualHp = 20.0f;
        float trailingHp = 20.0f;
        float lastHp = 20.0f;
        long lastDamageTime = 0L;
        long lastSeenTime = 0L;
    }

    private final Map<UUID, PlayerTagState> stateMap = new ConcurrentHashMap<>();
    private final Matrix3x2f tagMat = new Matrix3x2f();
    private final Vector2f scratchScreenPos = new Vector2f();
    private final Vector2f scratchItemPos = new Vector2f();
    private final List<ItemStack> scratchArmorList = new ArrayList<>(6);
    private long lastFrameNanos = 0L;

    public NameTags() {
        super("NameTags", "Отображает имя, здоровье и экипировку над игроками и предметами", Category.ESP);
        instance = this;

        this.lootBeam.setVisible(this.showItems::get);
        this.onlyValuable.setVisible(this.showItems::get);
        this.itemIcons.setVisible(this.showItems::get);

        addSettings(
                showHealth, showPing, showDistance, showArmor, showSelf, bgAlpha, colorMode,
                showItems, lootBeam, onlyValuable, itemIcons
        );
    }

    public static NameTags getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.stateMap.clear();
        this.lastFrameNanos = 0L;
    }

    public static boolean isPlayerVisible(MinecraftClient mc, PlayerEntity player) {
        if (mc == null || mc.world == null || mc.player == null || player == null) return false;
        if (EntityUtil.isInvisible(player)) {
            return false;
        }

        Vec3d eye = mc.player.getEyePos();

        Vec3d targetEye = player.getEyePos();
        double rayDistSq1 = eye.squaredDistanceTo(targetEye);
        BlockHitResult hit1 = mc.world.raycast(new RaycastContext(
                eye, targetEye, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit1.getType() == HitResult.Type.MISS || hit1.getPos().squaredDistanceTo(eye) >= rayDistSq1 - 0.15) {
            return true;
        }

        Vec3d targetCenter = player.getBoundingBox().getCenter();
        double rayDistSq2 = eye.squaredDistanceTo(targetCenter);
        BlockHitResult hit2 = mc.world.raycast(new RaycastContext(
                eye, targetCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit2.getType() == HitResult.Type.MISS || hit2.getPos().squaredDistanceTo(eye) >= rayDistSq2 - 0.15) {
            return true;
        }

        Vec3d targetFeet = new Vec3d(player.getX(), player.getY() + 0.25, player.getZ());
        double rayDistSq3 = eye.squaredDistanceTo(targetFeet);
        BlockHitResult hit3 = mc.world.raycast(new RaycastContext(
                eye, targetFeet, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        return hit3.getType() == HitResult.Type.MISS || hit3.getPos().squaredDistanceTo(eye) >= rayDistSq3 - 0.15;
    }

    public static boolean isItemVisible(MinecraftClient mc, ItemEntity item) {
        if (mc == null || mc.world == null || mc.player == null || item == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Vec3d itemTop = new Vec3d(item.getX(), item.getY() + item.getHeight() * 0.9 + 0.05, item.getZ());
        double distSqTop = eye.squaredDistanceTo(itemTop);
        BlockHitResult hitTop = mc.world.raycast(new RaycastContext(
                eye, itemTop, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hitTop.getType() == HitResult.Type.MISS || hitTop.getPos().squaredDistanceTo(eye) >= distSqTop - 0.20) {
            return true;
        }

        Vec3d itemCenter = item.getBoundingBox().getCenter();
        double distSqCenter = eye.squaredDistanceTo(itemCenter);
        BlockHitResult hitCenter = mc.world.raycast(new RaycastContext(
                eye, itemCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        return hitCenter.getType() == HitResult.Type.MISS || hitCenter.getPos().squaredDistanceTo(eye) >= distSqCenter - 0.20;
    }

    public static boolean isValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.TOTEM_OF_UNDYING
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.GOLDEN_APPLE
                || item == Items.ELYTRA
                || item == Items.MACE
                || item == Items.HEAVY_CORE
                || item == Items.END_CRYSTAL
                || item == Items.RESPAWN_ANCHOR
                || item == Items.ENDER_PEARL
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.GOLDEN_CARROT
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || item == Items.DRAGON_EGG
                || item == Items.DRAGON_HEAD
                || item == Items.PLAYER_HEAD) {
            return true;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id != null) {
            String path = id.getPath();
            if (path.startsWith("netherite_") || path.startsWith("diamond_") || path.contains("shulker_box")) {
                return true;
            }
        }
        return stack.hasGlint() || stack.getRarity() == Rarity.EPIC || stack.getRarity() == Rarity.RARE;
    }

    public static int getLootColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0xFFE2E8F0;
        Item item = stack.getItem();
        if (item == Items.TOTEM_OF_UNDYING
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.ELYTRA
                || item == Items.MACE
                || item == Items.HEAVY_CORE) {
            return 0xFFFFD700;
        }
        Identifier id = Registries.ITEM.getId(item);
        String path = id != null ? id.getPath() : "";
        if (path.startsWith("netherite_")) {
            return 0xFFFFD700;
        }
        if (item == Items.GOLDEN_APPLE
                || item == Items.END_CRYSTAL
                || item == Items.RESPAWN_ANCHOR
                || item == Items.DRAGON_EGG
                || item == Items.DRAGON_HEAD
                || item == Items.PLAYER_HEAD
                || path.contains("shulker_box")
                || stack.getRarity() == Rarity.EPIC) {
            return 0xFFE040FB;
        }
        if (item == Items.ENDER_PEARL
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || path.startsWith("diamond_")
                || stack.getRarity() == Rarity.RARE) {
            return 0xFF00E5FF;
        }
        if (item == Items.GOLDEN_CARROT
                || path.startsWith("emerald_")
                || stack.hasGlint()
                || stack.getRarity() == Rarity.UNCOMMON) {
            return 0xFF4ADE80;
        }
        return 0xFFE2E8F0;
    }

    public void render2D(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        float tickDelta = tickCounter != null ? tickCounter.getTickProgress(false) : 1.0f;
        long nowNanos = System.nanoTime();
        float dt = (lastFrameNanos == 0L) ? 0.016f : MathHelper.clamp((nowNanos - lastFrameNanos) / 1_000_000_000.0f, 0.001f, 0.05f);
        lastFrameNanos = nowNanos;
        long nowMs = System.currentTimeMillis();

        boolean inThirdPerson = !mc.options.getPerspective().isFirstPerson();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive() || player.isSpectator()) continue;
            if (EntityUtil.isInvisible(player)) continue;

            if (player == mc.player) {
                if (!this.showSelf.get() || !inThirdPerson) continue;
            }

            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq > 64.0 * 64.0) continue;
            double dist = Math.sqrt(distSq);

            if (player != mc.player && !isPlayerVisible(mc, player)) {
                continue;
            }

            double wx = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double wy = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + player.getHeight() + 0.40;
            double wz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

            boolean onScreen = ProjectUtil.toScreen(wx, wy, wz, tickDelta, scratchScreenPos);
            if (!onScreen || !ProjectUtil.isOnScreen(scratchScreenPos)) continue;

            PlayerTagState state = stateMap.computeIfAbsent(player.getUuid(), u -> {
                PlayerTagState s = new PlayerTagState();
                s.visualHp = player.getHealth();
                s.trailingHp = player.getHealth();
                s.lastHp = player.getHealth();
                return s;
            });
            state.lastSeenTime = nowMs;

            if (!state.smoothInit || Math.abs(state.smoothX - scratchScreenPos.x) > 120.0f || Math.abs(state.smoothY - scratchScreenPos.y) > 120.0f) {
                state.smoothX = scratchScreenPos.x;
                state.smoothY = scratchScreenPos.y;
                state.smoothInit = true;
            } else {
                float smoothSpeed = Math.min(1.0f, dt * 45.0f);
                state.smoothX = MathHelper.lerp(smoothSpeed, state.smoothX, scratchScreenPos.x);
                state.smoothY = MathHelper.lerp(smoothSpeed, state.smoothY, scratchScreenPos.y);
            }

            renderSingleTag(context, mc, player, state, dist, dt);
        }

        this.stateMap.entrySet().removeIf(e -> nowMs - e.getValue().lastSeenTime > 4000L);
        DrawBatcher.flushPending();

        if (this.showItems.get()) {
            renderItemTags(context, mc, tickDelta, dt, nowMs);
        }
    }

    private void renderSingleTag(DrawContext context, MinecraftClient mc, PlayerEntity player, PlayerTagState state, double dist, float dt) {
        float maxHp = Math.max(1.0f, player.getMaxHealth());
        float currentHp = MathHelper.clamp(player.getHealth(), 0.0f, maxHp);
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());

        if (currentHp < state.lastHp) {
            state.lastDamageTime = System.currentTimeMillis();
        }
        state.lastHp = currentHp;
        state.visualHp += (currentHp - state.visualHp) * Math.min(1.0f, dt * 14.0f);

        long timeSinceDamage = System.currentTimeMillis() - state.lastDamageTime;
        if (timeSinceDamage > 250L) {
            state.trailingHp += (currentHp - state.trailingHp) * Math.min(1.0f, dt * 6.5f);
        }

        float distScale = (float) MathHelper.clamp(1.0 - (dist - 6.0) * 0.016, 0.68, 1.22);
        float tagScale = 0.75f * distScale;

        Font font = Fonts.ROCKSTAR_ROUND_BOLD;
        float fontSize = 11.5f;

        String rawName = player.getNameForScoreboard();
        if (rawName == null || rawName.isEmpty()) {
            rawName = player.getName().getString();
        }
        if (rawName != null) {
            if (rawName.indexOf('🍳') >= 0 || rawName.indexOf('🥚') >= 0) {
                rawName = rawName.replace("🍳", "").replace("🥚", "").trim();
            }
        } else {
            rawName = "Player";
        }
        boolean isFriend = FriendManager.getInstance().isFriend(rawName);

        float nameW = font.a(rawName, fontSize);
        int nameColor = isFriend ? 0xFF38BDF8 : 0xFFFFFFFF;

        Globals globals = Globals.getInstance();
        boolean isEgg = globals != null && globals.isEnabled() && globals.isInNametag() && Globals.isEggUser(player);
        float eggSize = 10.0f;
        float eggGap = 3.5f;
        float eggW = isEgg ? (eggSize + eggGap) : 0.0f;

        int latency = -1;
        PlayerListEntry entry = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) : null;
        if (entry != null) {
            latency = entry.getLatency();
        }
        String pingText = (this.showPing.get() && latency >= 0) ? (latency + "ms") : null;
        float pingW = pingText != null ? (font.a(pingText, fontSize) + 8.0f) : 0.0f;
        int pingDotColor;
        if (latency < 80) pingDotColor = 0xFF4ADE80;
        else if (latency < 160) pingDotColor = 0xFFFACC15;
        else pingDotColor = 0xFFF87171;

        String distText = this.showDistance.get() ? ((int) Math.round(dist) + "m") : null;
        float distW = distText != null ? font.a(distText, fontSize) : 0.0f;

        String hpText = null;
        float hpW = 0.0f;
        int hpColor = 0xFF22C55E;
        if (this.showHealth.get()) {
            float totalHp = currentHp + absorption;
            hpText = (int) Math.ceil(totalHp) + " HP";
            hpW = font.a(hpText, fontSize);

            float hpRatio = currentHp / maxHp;
            if (absorption > 0.0f) {
                hpColor = 0xFFF59E0B;
            } else if (hpRatio >= 0.70f) {
                hpColor = 0xFF22C55E;
            } else if (hpRatio >= 0.35f) {
                hpColor = 0xFFFACC15;
            } else {
                hpColor = 0xFFEF4444;
            }
        }

        float padX = 7.0f;
        float gap = 5.0f;
        float contentW = nameW + eggW;
        if (pingText != null) contentW += pingW + gap;
        if (distText != null) contentW += distW + gap;
        if (hpText != null) contentW += hpW + gap;

        float cardW = Math.max(44.0f, contentW + padX * 2.0f);
        float cardH = this.showHealth.get() ? 19.0f : 15.0f;
        float cardRadius = 4.0f;

        float alphaFactor = this.bgAlpha.getValueFloat() / 100.0f;
        float cardX = -cardW / 2.0f;
        float cardY = -cardH;

        if (this.showArmor.get()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(state.smoothX, state.smoothY);
            context.getMatrices().scale(tagScale, tagScale);
            renderArmorItems(context, mc, player, 0.0f, cardY - 2.0f);
            context.getMatrices().popMatrix();
        }

        Matrix3x2f tagMat = this.tagMat.identity().translate(state.smoothX, state.smoothY).scale(tagScale, tagScale);

        int bgColor = ColorUtil.rgba(14, 16, 22, (int) (220 * alphaFactor));
        Draw2DProcessor.INSTANCE.drawRect(tagMat, cardX, cardY, cardW, cardH, cardRadius, bgColor);

        int sheenCol = ColorUtil.rgba(255, 255, 255, (int) (14 * alphaFactor));
        Draw2DProcessor.INSTANCE.drawGradient(tagMat, cardX, cardY, cardW, cardH * 0.5f, cardRadius, sheenCol, sheenCol, 0, 0);

        if (this.showHealth.get()) {
            float barX = cardX + 5.0f;
            float barY = cardY + cardH - 3.2f;
            float barW = cardW - 10.0f;
            float barH = 1.8f;

            Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, barW, barH, 0.9f, ColorUtil.rgba(255, 255, 255, 25));

            float visualRatio = MathHelper.clamp(state.visualHp / maxHp, 0.0f, 1.0f);
            float trailingRatio = MathHelper.clamp(state.trailingHp / maxHp, 0.0f, 1.0f);

            if (trailingRatio > visualRatio) {
                float trailW = Math.max(1.5f, barW * trailingRatio);
                Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, trailW, barH, 0.9f, ColorUtil.rgba(251, 146, 60, 240));
            }

            float fillW = Math.max(1.0f, barW * visualRatio);
            int barCol;
            String colorSetting = this.colorMode.get();
            if ("Тема".equals(colorSetting)) {
                barCol = Theme.getAccent();
            } else if ("Градиент".equals(colorSetting)) {
                if (visualRatio > 0.5f) {
                    float f = (visualRatio - 0.5f) * 2.0f;
                    barCol = ColorUtil.interpolate(0xFFFACC15, 0xFF22C55E, f);
                } else {
                    float f = visualRatio * 2.0f;
                    barCol = ColorUtil.interpolate(0xFFEF4444, 0xFFFACC15, f);
                }
            } else {
                if (visualRatio > 0.6f) barCol = 0xFF22C55E;
                else if (visualRatio > 0.25f) barCol = 0xFFFACC15;
                else barCol = 0xFFEF4444;
            }
            Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, fillW, barH, 0.9f, barCol);

            if (absorption > 0.0f) {
                float absRatio = MathHelper.clamp(absorption / maxHp, 0.0f, 1.0f);
                float absW = Math.max(1.0f, barW * absRatio);
                Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, absW, barH, 0.9f, ColorUtil.rgba(252, 211, 77, 255));
            }
        }

        float curX = cardX + padX;
        float textY = cardY + 2.0f;

        if (pingText != null) {
            float dotR = 1.8f;
            float dotX = curX + 2.0f;
            float dotY = textY + 5.5f;
            Draw2DProcessor.INSTANCE.drawRect(tagMat, dotX - dotR, dotY - dotR, dotR * 2.0f, dotR * 2.0f, dotR, pingDotColor);
            font.a(tagMat, pingText, curX + 6.0f, textY, fontSize, ColorUtil.rgba(203, 213, 225, 235));
            curX += pingW + gap;
        }

        if (isEgg) {
            float eggY = textY + (font.a(fontSize) - eggSize) / 2.0f;
            Draw2DProcessor.INSTANCE.a(tagMat, EGG_LOGO, curX, eggY, eggSize, eggSize, 0.0f, 0xFFFFFFFF);
            curX += eggW;
        }

        font.a(tagMat, rawName, curX, textY, fontSize, nameColor);
        curX += nameW + gap;

        if (distText != null) {
            font.a(tagMat, distText, curX, textY, fontSize, ColorUtil.rgba(203, 213, 225, 215));
            curX += distW + gap;
        }

        if (hpText != null) {
            font.a(tagMat, hpText, curX, textY, fontSize, hpColor);
        }

        DrawBatcher.flushPending();
    }

    private void renderItemTags(DrawContext context, MinecraftClient mc, float tickDelta, float dt, long nowMs) {
        Box searchBox = mc.player.getBoundingBox().expand(48.0);
        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, searchBox, e -> e.isAlive() && !e.getStack().isEmpty());
        if (items.isEmpty()) return;

        Font font = Fonts.ROCKSTAR_ROUND_BOLD;
        float fontSize = 10.5f;
        float alphaFactor = this.bgAlpha.getValueFloat() / 100.0f;
        int bgColor = ColorUtil.rgba(14, 16, 22, (int) (210 * alphaFactor));

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            boolean valuable = isValuable(stack);
            if (this.onlyValuable.get() && !valuable) continue;

            double distSq = mc.player.squaredDistanceTo(item);
            if (distSq > 48.0 * 48.0) continue;
            double dist = Math.sqrt(distSq);

            if (!isItemVisible(mc, item)) continue;

            double wx = MathHelper.lerp(tickDelta, item.lastRenderX, item.getX());
            double wy = MathHelper.lerp(tickDelta, item.lastRenderY, item.getY()) + item.getHeight() + 0.35;
            double wz = MathHelper.lerp(tickDelta, item.lastRenderZ, item.getZ());

            boolean onScreen = ProjectUtil.toScreen(wx, wy, wz, tickDelta, scratchItemPos);
            if (!onScreen || !ProjectUtil.isOnScreen(scratchItemPos)) continue;

            String name = stack.getName().getString();
            int count = stack.getCount();
            if (count > 1) {
                name = name + " x" + count;
            }

            int rarityColor = getLootColor(stack);

            float distScale = (float) MathHelper.clamp(1.0 - (dist - 4.0) * 0.02, 0.65, 1.15);
            float tagScale = 0.70f * distScale;

            boolean drawIcon = this.itemIcons.get();
            float iconSize = 11.0f;
            float iconGap = 3.5f;

            float nameW = font.a(name, fontSize);
            String distText = (int) Math.round(dist) + "m";
            float distW = font.a(distText, fontSize);

            float padX = 6.0f;
            float contentW = nameW + 5.0f + distW + (drawIcon ? iconSize + iconGap : 0.0f);
            float cardW = Math.max(36.0f, contentW + padX * 2.0f);
            float cardH = 15.0f;
            float cardX = -cardW / 2.0f;
            float cardY = -cardH;

            Matrix3x2f tagMat = this.tagMat.identity().translate(scratchItemPos.x, scratchItemPos.y).scale(tagScale, tagScale);

            Draw2DProcessor.INSTANCE.drawRect(tagMat, cardX, cardY, cardW, cardH, 3.5f, bgColor);
            Draw2DProcessor.INSTANCE.drawRect(tagMat, cardX, cardY, 2.2f, cardH, 1.0f, rarityColor);

            if (valuable) {
                int sheenCol = ColorUtil.rgba((rarityColor >> 16) & 0xFF, (rarityColor >> 8) & 0xFF, rarityColor & 0xFF, 30);
                Draw2DProcessor.INSTANCE.drawGradient(tagMat, cardX, cardY, cardW, cardH * 0.5f, 3.5f, sheenCol, sheenCol, 0, 0);
            }

            float curX = cardX + padX + (drawIcon ? iconSize + iconGap : 0.0f);
            float textY = cardY + 2.0f;

            font.a(tagMat, name, curX, textY, fontSize, rarityColor);
            font.a(tagMat, distText, curX + nameW + 5.0f, textY, fontSize, 0xFF94A3B8);

            DrawBatcher.flushPending();

            if (drawIcon) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(scratchItemPos.x, scratchItemPos.y);
                context.getMatrices().scale(tagScale, tagScale);
                context.getMatrices().translate(cardX + padX, cardY + (cardH - iconSize) / 2.0f);
                context.getMatrices().scale(iconSize / 16.0f, iconSize / 16.0f);
                context.drawItem(stack, 0, 0);
                context.getMatrices().popMatrix();
            }
        }
    }

    public void renderWorld3D(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider.Immediate immediate, Vec3d camPos) {
        if (!isEnabled() || !this.showItems.get() || !this.lootBeam.get()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        double maxDistSq = 56.0 * 56.0;
        Box searchBox = mc.player.getBoundingBox().expand(56.0);
        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, searchBox, e -> e.isAlive() && !e.getStack().isEmpty());
        if (items.isEmpty()) return;

        VertexConsumer lightBuf = immediate.getBuffer(RenderLayers.lightning());
        long nowMs = System.currentTimeMillis();
        float spin = (nowMs % 4000L) / 4000.0f * (float) (2.0 * Math.PI);
        float pulse = 0.80f + 0.20f * (float) Math.sin((nowMs % 1600L) / 1600.0 * Math.PI * 2.0);

        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            if (!isValuable(stack)) continue;

            double distSq = mc.player.squaredDistanceTo(item);
            if (distSq > maxDistSq) continue;

            double ix = MathHelper.lerp(tickDelta, item.lastRenderX, item.getX());
            double iy = MathHelper.lerp(tickDelta, item.lastRenderY, item.getY()) + 0.05;
            double iz = MathHelper.lerp(tickDelta, item.lastRenderZ, item.getZ());

            int color = getLootColor(stack);
            int cr = (color >> 16) & 0xFF;
            int cg = (color >> 8) & 0xFF;
            int cb = color & 0xFF;

            double maxY = iy + 18.0;
            int checkX = MathHelper.floor(ix);
            int checkZ = MathHelper.floor(iz);
            int startY = MathHelper.floor(iy);
            int endY = MathHelper.floor(iy + 18.0);
            for (int cy = startY + 1; cy <= endY; cy++) {
                mut.set(checkX, cy, checkZ);
                if (mc.world.getBlockState(mut).blocksMovement()) {
                    maxY = cy;
                    break;
                }
            }
            float beamHeight = (float) Math.max(1.8, maxY - iy);

            renderLootBeam(lightBuf, ix, iy, iz, camPos, beamHeight, cr, cg, cb, spin, pulse);
        }
    }

    private void renderLootBeam(VertexConsumer buf, double bx, double by, double bz, Vec3d camPos,
                                float height, int r, int g, int b, float spin, float pulse) {
        int segs = 8;
        float rCore = 0.08f;
        float rGlow = 0.26f;
        float rRing = 0.50f;

        int aCoreBase = Math.min(255, (int) (220 * pulse));
        int aGlowBase = Math.min(255, (int) (90 * pulse));

        float yBase = (float) (by - camPos.y);
        float yTop = yBase + height;

        for (int i = 0; i < segs; i++) {
            double a0 = spin + (i * 2.0 * Math.PI / segs);
            double a1 = spin + ((i + 1) * 2.0 * Math.PI / segs);

            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            float x0_c = (float) (bx + c0 * rCore - camPos.x);
            float z0_c = (float) (bz + s0 * rCore - camPos.z);
            float x1_c = (float) (bx + c1 * rCore - camPos.x);
            float z1_c = (float) (bz + s1 * rCore - camPos.z);

            buf.vertex(x0_c, yBase, z0_c).color(255, 255, 255, aCoreBase);
            buf.vertex(x1_c, yBase, z1_c).color(255, 255, 255, aCoreBase);
            buf.vertex(x1_c, yTop, z1_c).color(r, g, b, 0);
            buf.vertex(x0_c, yTop, z0_c).color(r, g, b, 0);

            float x0_g = (float) (bx + c0 * rGlow - camPos.x);
            float z0_g = (float) (bz + s0 * rGlow - camPos.z);
            float x1_g = (float) (bx + c1 * rGlow - camPos.x);
            float z1_g = (float) (bz + s1 * rGlow - camPos.z);

            buf.vertex(x0_g, yBase, z0_g).color(r, g, b, aGlowBase);
            buf.vertex(x1_g, yBase, z1_g).color(r, g, b, aGlowBase);
            buf.vertex(x1_g, yTop, z1_g).color(r, g, b, 0);
            buf.vertex(x0_g, yTop, z0_g).color(r, g, b, 0);

            float rx0_in = (float) (bx + c0 * (rRing * 0.35f) - camPos.x);
            float rz0_in = (float) (bz + s0 * (rRing * 0.35f) - camPos.z);
            float rx1_in = (float) (bx + c1 * (rRing * 0.35f) - camPos.x);
            float rz1_in = (float) (bz + s1 * (rRing * 0.35f) - camPos.z);
            float rx0_out = (float) (bx + c0 * rRing - camPos.x);
            float rz0_out = (float) (bz + s0 * rRing - camPos.z);
            float rx1_out = (float) (bx + c1 * rRing - camPos.x);
            float rz1_out = (float) (bz + s1 * rRing - camPos.z);
            float ringY = yBase + 0.02f;

            buf.vertex(rx0_in, ringY, rz0_in).color(r, g, b, (int) (160 * pulse));
            buf.vertex(rx1_in, ringY, rz1_in).color(r, g, b, (int) (160 * pulse));
            buf.vertex(rx1_out, ringY, rz1_out).color(r, g, b, 0);
            buf.vertex(rx0_out, ringY, rz0_out).color(r, g, b, 0);
        }
    }

    private void renderArmorItems(DrawContext context, MinecraftClient mc, PlayerEntity player, float centerX, float bottomY) {
        scratchArmorList.clear();

        ItemStack main = player.getMainHandStack();
        if (!main.isEmpty()) scratchArmorList.add(main);

        ItemStack helm = player.getEquippedStack(EquipmentSlot.HEAD);
        if (!helm.isEmpty()) scratchArmorList.add(helm);

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) scratchArmorList.add(chest);

        ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
        if (!legs.isEmpty()) scratchArmorList.add(legs);

        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        if (!feet.isEmpty()) scratchArmorList.add(feet);

        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty()) scratchArmorList.add(off);

        if (scratchArmorList.isEmpty()) return;

        float itemSize = 14.0f;
        float spacing = 2.0f;
        float totalW = scratchArmorList.size() * itemSize + (scratchArmorList.size() - 1) * spacing;
        float startX = centerX - totalW / 2.0f;
        float startY = bottomY - itemSize;

        for (int i = 0; i < scratchArmorList.size(); i++) {
            ItemStack stack = scratchArmorList.get(i);
            int ix = Math.round(startX + i * (itemSize + spacing));
            int iy = Math.round(startY);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(ix, iy);
            context.getMatrices().scale(itemSize / 16.0f, itemSize / 16.0f);
            context.drawItem(stack, 0, 0);
            if (mc.textRenderer != null) {
                context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
            }
            context.getMatrices().popMatrix();
        }
    }
}
