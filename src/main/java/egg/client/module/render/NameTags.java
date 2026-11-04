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
import egg.client.util.FriendManager;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.*;
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
    private long lastFrameNanos = 0L;

    public NameTags() {
        super("NameTags", "Отображает имя, здоровье и экипировку над головами игроков", Category.ESP);
        instance = this;
        addSettings(showHealth, showPing, showDistance, showArmor, showSelf, bgAlpha, colorMode);
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

    private static boolean hasArmorEquipped(PlayerEntity player) {
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    public static boolean isPlayerVisible(MinecraftClient mc, PlayerEntity player) {
        if (mc == null || mc.world == null || mc.player == null || player == null) return false;

        if (player.isInvisible() && !hasArmorEquipped(player) && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()) {
            return false;
        }

        Vec3d eye = mc.player.getEyePos();

        // 1. Глаза / Голова
        Vec3d targetEye = player.getEyePos();
        double rayDistSq1 = eye.squaredDistanceTo(targetEye);
        BlockHitResult hit1 = mc.world.raycast(new RaycastContext(
                eye, targetEye, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit1.getType() == HitResult.Type.MISS || hit1.getPos().squaredDistanceTo(eye) >= rayDistSq1 - 0.15) {
            return true;
        }

        // 2. Торс / Центр хитбокса
        Vec3d targetCenter = player.getBoundingBox().getCenter();
        double rayDistSq2 = eye.squaredDistanceTo(targetCenter);
        BlockHitResult hit2 = mc.world.raycast(new RaycastContext(
                eye, targetCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit2.getType() == HitResult.Type.MISS || hit2.getPos().squaredDistanceTo(eye) >= rayDistSq2 - 0.15) {
            return true;
        }

        // 3. Ноги / Чуть выше земли
        Vec3d targetFeet = new Vec3d(player.getX(), player.getY() + 0.25, player.getZ());
        double rayDistSq3 = eye.squaredDistanceTo(targetFeet);
        BlockHitResult hit3 = mc.world.raycast(new RaycastContext(
                eye, targetFeet, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        return hit3.getType() == HitResult.Type.MISS || hit3.getPos().squaredDistanceTo(eye) >= rayDistSq3 - 0.15;
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

            if (player == mc.player) {
                if (!this.showSelf.get() || !inThirdPerson) continue;
            }

            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq > 64.0 * 64.0) continue;
            double dist = Math.sqrt(distSq);

            // Визуал, не чит: всегда только прямая видимость
            if (player != mc.player && !isPlayerVisible(mc, player)) {
                continue;
            }

            double wx = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double wy = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + player.getHeight() + 0.40;
            double wz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

            Vector2f screenPos = ProjectUtil.toScreen(wx, wy, wz, tickDelta);
            if (!ProjectUtil.isOnScreen(screenPos)) continue;

            PlayerTagState state = stateMap.computeIfAbsent(player.getUuid(), u -> {
                PlayerTagState s = new PlayerTagState();
                s.visualHp = player.getHealth();
                s.trailingHp = player.getHealth();
                s.lastHp = player.getHealth();
                return s;
            });
            state.lastSeenTime = nowMs;

            if (!state.smoothInit || Math.abs(state.smoothX - screenPos.x) > 120.0f || Math.abs(state.smoothY - screenPos.y) > 120.0f) {
                state.smoothX = screenPos.x;
                state.smoothY = screenPos.y;
                state.smoothInit = true;
            } else {
                float smoothSpeed = Math.min(1.0f, dt * 45.0f);
                state.smoothX = MathHelper.lerp(smoothSpeed, state.smoothX, screenPos.x);
                state.smoothY = MathHelper.lerp(smoothSpeed, state.smoothY, screenPos.y);
            }

            renderSingleTag(context, mc, player, state, dist, dt);
        }

        // Очистка старых игроков
        this.stateMap.entrySet().removeIf(e -> nowMs - e.getValue().lastSeenTime > 4000L);
        DrawBatcher.flushPending();
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

        // Фиксированный масштаб 0.75 с плавной коррекцией по дистанции
        float distScale = (float) MathHelper.clamp(1.0 - (dist - 6.0) * 0.016, 0.68, 1.22);
        float tagScale = 0.75f * distScale;

        // Всегда Rockstar Round
        Font font = Fonts.ROCKSTAR_ROUND_BOLD;
        float fontSize = 11.5f;

        // 1. Имя
        String rawName = player.getNameForScoreboard();
        if (rawName == null || rawName.isEmpty()) {
            rawName = player.getName().getString();
        }
        if (rawName != null) {
            rawName = rawName.replace("🍳", "").replace("🥚", "").trim();
        } else {
            rawName = "Player";
        }
        boolean isFriend = FriendManager.getInstance().isFriend(rawName);

        float nameW = font.a(rawName, fontSize);
        int nameColor = isFriend ? 0xFF38BDF8 : 0xFFFFFFFF;

        // Проверка Globals на яйцо перед ником
        Globals globals = Globals.getInstance();
        boolean isEgg = globals != null && globals.isEnabled() && globals.isInNametag() && Globals.isEggUser(player);
        float eggSize = 10.0f;
        float eggGap = 3.5f;
        float eggW = isEgg ? (eggSize + eggGap) : 0.0f;

        // 2. Пинг
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

        // 3. Дистанция
        String distText = this.showDistance.get() ? ((int) Math.round(dist) + "m") : null;
        float distW = distText != null ? font.a(distText, fontSize) : 0.0f;

        // 4. Здоровье
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

        // Обсчёт ширины плашки
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

        // Экипировка над головой (через Minecraft DrawContext)
        if (this.showArmor.get()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(state.smoothX, state.smoothY);
            context.getMatrices().scale(tagScale, tagScale);
            renderArmorItems(context, mc, player, 0.0f, cardY - 2.0f);
            context.getMatrices().popMatrix();
        }

        // Рендерим плашку, полоску, иконки и текст В ОДНОМ MSDF-БАТЧЕ через Draw2DProcessor
        // Это гарантирует, что плашка ВСЕГДА под текстом, независимо от прозрачности
        Matrix3x2f tagMat = new Matrix3x2f().translate(state.smoothX, state.smoothY).scale(tagScale, tagScale);

        // 1. Фон плашки: матовый темный акрил
        int bgColor = ColorUtil.rgba(14, 16, 22, (int) (220 * alphaFactor));
        Draw2DProcessor.INSTANCE.drawRect(tagMat, cardX, cardY, cardW, cardH, cardRadius, bgColor);

        // 2. Мягкий верхний блик
        int sheenCol = ColorUtil.rgba(255, 255, 255, (int) (14 * alphaFactor));
        Draw2DProcessor.INSTANCE.drawGradient(tagMat, cardX, cardY, cardW, cardH * 0.5f, cardRadius, sheenCol, sheenCol, 0, 0);

        // 3. Полоска здоровья внизу плашки
        if (this.showHealth.get()) {
            float barX = cardX + 5.0f;
            float barY = cardY + cardH - 3.2f;
            float barW = cardW - 10.0f;
            float barH = 1.8f;

            // Фоновая канавка
            Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, barW, barH, 0.9f, ColorUtil.rgba(255, 255, 255, 25));

            float visualRatio = MathHelper.clamp(state.visualHp / maxHp, 0.0f, 1.0f);
            float trailingRatio = MathHelper.clamp(state.trailingHp / maxHp, 0.0f, 1.0f);

            // Трейл урона
            if (trailingRatio > visualRatio) {
                float trailW = Math.max(1.5f, barW * trailingRatio);
                Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, trailW, barH, 0.9f, ColorUtil.rgba(251, 146, 60, 240));
            }

            // Основная полоска
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
            } else { // "Здоровье"
                if (visualRatio > 0.6f) barCol = 0xFF22C55E;
                else if (visualRatio > 0.25f) barCol = 0xFFFACC15;
                else barCol = 0xFFEF4444;
            }
            Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, fillW, barH, 0.9f, barCol);

            // Абсорбция
            if (absorption > 0.0f) {
                float absRatio = MathHelper.clamp(absorption / maxHp, 0.0f, 1.0f);
                float absW = Math.max(1.0f, barW * absRatio);
                Draw2DProcessor.INSTANCE.drawRect(tagMat, barX, barY, absW, barH, 0.9f, ColorUtil.rgba(252, 211, 77, 255));
            }
        }

        float curX = cardX + padX;
        float textY = cardY + 2.0f;

        // 4. Пинг-точка
        if (pingText != null) {
            float dotR = 1.8f;
            float dotX = curX + 2.0f;
            float dotY = textY + 5.5f;
            Draw2DProcessor.INSTANCE.drawRect(tagMat, dotX - dotR, dotY - dotR, dotR * 2.0f, dotR * 2.0f, dotR, pingDotColor);
            font.a(tagMat, pingText, curX + 6.0f, textY, fontSize, ColorUtil.rgba(203, 213, 225, 235));
            curX += pingW + gap;
        }

        // 5. Яйцо Globals перед ником
        if (isEgg) {
            float eggY = textY + (font.a(fontSize) - eggSize) / 2.0f;
            Draw2DProcessor.INSTANCE.a(tagMat, EGG_LOGO, curX, eggY, eggSize, eggSize, 0.0f, 0xFFFFFFFF);
            curX += eggW;
        }

        // 6. Имя игрока (Rockstar Round MSDF)
        font.a(tagMat, rawName, curX, textY, fontSize, nameColor);
        curX += nameW + gap;

        // 7. Дистанция
        if (distText != null) {
            font.a(tagMat, distText, curX, textY, fontSize, ColorUtil.rgba(203, 213, 225, 215));
            curX += distW + gap;
        }

        // 8. Числовое HP
        if (hpText != null) {
            font.a(tagMat, hpText, curX, textY, fontSize, hpColor);
        }

        DrawBatcher.flushPending();
    }

    private void renderArmorItems(DrawContext context, MinecraftClient mc, PlayerEntity player, float centerX, float bottomY) {
        List<ItemStack> items = new ArrayList<>(6);

        ItemStack main = player.getMainHandStack();
        if (!main.isEmpty()) items.add(main);

        ItemStack helm = player.getEquippedStack(EquipmentSlot.HEAD);
        if (!helm.isEmpty()) items.add(helm);

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) items.add(chest);

        ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
        if (!legs.isEmpty()) items.add(legs);

        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        if (!feet.isEmpty()) items.add(feet);

        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty()) items.add(off);

        if (items.isEmpty()) return;

        float itemSize = 14.0f;
        float spacing = 2.0f;
        float totalW = items.size() * itemSize + (items.size() - 1) * spacing;
        float startX = centerX - totalW / 2.0f;
        float startY = bottomY - itemSize;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
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
