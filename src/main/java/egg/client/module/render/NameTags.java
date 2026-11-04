package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.misc.Globals;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.pipeline.DrawBatcher;
import net.minecraft.util.Identifier;
import egg.client.render.font.ClientFont;
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
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NameTags extends Module {
    private static NameTags instance;
    private static final Identifier EGG_LOGO = Identifier.of("egg", "textures/egg_logo.png");

    private final BooleanSetting onlyVisible = new BooleanSetting("Только видимые", true, "Отображать только игроков в прямой видимости (без сквозь стены)");
    private final ModeSetting fontMode = new ModeSetting("Шрифт", "SF Semibold", "SF Semibold", "SF Medium", "SF Bold", "SF Regular");
    private final BooleanSetting showHealth = new BooleanSetting("Здоровье", true, "Шкала и числовое значение HP");
    private final BooleanSetting showPing = new BooleanSetting("Пинг", true, "Индикатор задержки игрока");
    private final BooleanSetting showDistance = new BooleanSetting("Дистанция", true, "Расстояние в метрах");
    private final BooleanSetting showArmor = new BooleanSetting("Броня и руки", true, "Экипированные предметы над ником");
    private final BooleanSetting showSelf = new BooleanSetting("Себя от 3-го лица", false, "Отображать бейдж над своим персонажем");
    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0, 0.5, 2.0, 0.05, "Базовый размер бейджа");
    private final SliderSetting bgAlpha = new SliderSetting("Непрозрачность", 88.0, 20.0, 100.0, 5.0, "Плотность подложки (%)");
    private final BooleanSetting borderGlow = new BooleanSetting("Неоновая обводка", true, "Светящаяся окантовка в стиле темы");
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
        super("NameTags", "Минималистичные современные бейджи игроков в стиле macOS/iOS", Category.ESP);
        instance = this;
        addSettings(onlyVisible, fontMode, showHealth, showPing, showDistance, showArmor, showSelf, scale, bgAlpha, borderGlow, colorMode);
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

    public boolean isOnlyVisible() {
        return this.onlyVisible.get();
    }

    private StyleSpriteSource.Font getSelectedFont() {
        return switch (this.fontMode.get()) {
            case "SF Medium" -> ClientFont.SF_MEDIUM;
            case "SF Bold" -> ClientFont.SF_BOLD;
            case "SF Regular" -> ClientFont.SF_REGULAR;
            default -> ClientFont.SF_SEMIBOLD;
        };
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

        // 3. Ноги / Низ модели
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

            if (this.onlyVisible.get() && player != mc.player && !isPlayerVisible(mc, player)) {
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

        // Адаптивный масштаб по дистанции
        float distScale = (float) MathHelper.clamp(1.0 - (dist - 6.0) * 0.016, 0.68, 1.22);
        float tagScale = this.scale.getValueFloat() * distScale;

        StyleSpriteSource.Font font = getSelectedFont();

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
        boolean isEgg = Globals.isEggUser(player);

        float nameW = ClientFont.getWidth(rawName, font);
        int nameColor = isFriend ? 0xFF38BDF8 : 0xFFFFFFFF;

        float eggIconSize = 10.0f;
        float eggIconW = isEgg ? (eggIconSize + 4.0f) : 0.0f;

        // 2. Пинг
        int latency = -1;
        PlayerListEntry entry = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) : null;
        if (entry != null) {
            latency = entry.getLatency();
        }
        String pingText = (this.showPing.get() && latency >= 0) ? (latency + "ms") : null;
        float pingW = pingText != null ? (ClientFont.getWidth(pingText, font) + 8.0f) : 0.0f; // с точкой
        int pingDotColor;
        if (latency < 80) pingDotColor = 0xFF4ADE80;
        else if (latency < 160) pingDotColor = 0xFFFACC15;
        else pingDotColor = 0xFFF87171;

        // 3. Дистанция
        String distText = this.showDistance.get() ? ((int) Math.round(dist) + "m") : null;
        float distW = distText != null ? ClientFont.getWidth(distText, font) : 0.0f;

        // 4. Здоровье
        String hpText = null;
        float hpW = 0.0f;
        int hpColor = 0xFF4ADE80;
        if (this.showHealth.get()) {
            float totalHp = currentHp + absorption;
            hpText = (int) Math.ceil(totalHp) + " HP";
            hpW = ClientFont.getWidth(hpText, font);

            float hpRatio = currentHp / maxHp;
            if (absorption > 0.0f) {
                hpColor = 0xFFF59E0B;
            } else if (hpRatio >= 0.70f) {
                hpColor = 0xFF4ADE80;
            } else if (hpRatio >= 0.35f) {
                hpColor = 0xFFFACC15;
            } else {
                hpColor = 0xFFEF4444;
            }
        }

        // Расчет общей ширины капсулы
        float padX = 7.0f;
        float gap = 5.0f;
        float contentW = nameW;
        if (isEgg) contentW += eggIconW;
        if (pingText != null) contentW += pingW + gap;
        if (distText != null) contentW += distW + gap;
        if (hpText != null) contentW += hpW + gap;

        float cardW = Math.max(48.0f, contentW + padX * 2.0f);
        float cardH = this.showHealth.get() ? 19.0f : 15.0f;
        float cardRadius = 5.0f;

        float alphaFactor = this.bgAlpha.getValueFloat() / 100.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(state.smoothX, state.smoothY);
        context.getMatrices().scale(tagScale, tagScale);

        float cardX = -cardW / 2.0f;
        float cardY = -cardH;

        // Отрисовка экипировки над ником
        if (this.showArmor.get()) {
            renderArmorItems(context, mc, player, 0.0f, cardY - 2.0f);
        }

        // Мягкая тень карточки
        int shadowColor = ColorUtil.rgba(0, 0, 0, (int) (140 * alphaFactor));
        Render2D.drawShadow(context, cardX, cardY, cardW, cardH, cardRadius, 8.0f, shadowColor);

        // Темная стеклянная подложка
        int bgColor = ColorUtil.rgba(13, 15, 21, (int) (225 * alphaFactor));
        Render2D.drawRoundedRect(context, cardX, cardY, cardW, cardH, cardRadius, bgColor);

        // Блик стекла
        Render2D.drawDiagonalSheen(context, cardX, cardY, cardW, cardH, cardRadius, 0L);

        // Неоновый или белый контур
        int borderColor = this.borderGlow.get()
                ? ColorUtil.withAlpha(Theme.getAccent(), (int) (125 * alphaFactor))
                : ColorUtil.rgba(255, 255, 255, (int) (26 * alphaFactor));
        Render2D.drawRoundedOutline(context, cardX, cardY, cardW, cardH, cardRadius, 1.0f, borderColor);

        // Отрисовка элементов в строке
        float curX = cardX + padX;
        float textY = cardY + 3.0f;

        // Пинг с точкой
        if (pingText != null) {
            Render2D.drawCircle(context, curX + 2.0f, textY + 4.5f, 1.8f, pingDotColor);
            ClientFont.draw(context, pingText, curX + 6.0f, textY, font, ColorUtil.rgba(203, 213, 225, 220));
            curX += pingW + gap;
        }

        // Иконка яйца для пользователей клиента
        if (isEgg) {
            float eggY = cardY + (cardH - (this.showHealth.get() ? 3.0f : 0.0f) - eggIconSize) / 2.0f;
            Draw2DProcessor.INSTANCE.a(context.getMatrices(), EGG_LOGO, curX, eggY, eggIconSize, eggIconSize, 2.0f, 0xFFFFFFFF);
            curX += eggIconW;
        }

        // Имя
        ClientFont.draw(context, rawName, curX, textY, font, nameColor);
        curX += nameW + gap;

        // Дистанция
        if (distText != null) {
            ClientFont.draw(context, distText, curX, textY, font, ColorUtil.rgba(148, 163, 184, 215));
            curX += distW + gap;
        }

        // Числовое значение HP
        if (hpText != null) {
            ClientFont.draw(context, hpText, curX, textY, font, hpColor);
        }

        // Плавная шкала здоровья внизу плашки
        if (this.showHealth.get()) {
            float barX = cardX + 5.0f;
            float barY = cardY + cardH - 3.2f;
            float barW = cardW - 10.0f;
            float barH = 1.8f;

            // Фоновая дорожка
            Render2D.drawRoundedRect(context, barX, barY, barW, barH, 0.9f, ColorUtil.rgba(255, 255, 255, 20));

            float visualRatio = MathHelper.clamp(state.visualHp / maxHp, 0.0f, 1.0f);
            float trailingRatio = MathHelper.clamp(state.trailingHp / maxHp, 0.0f, 1.0f);

            // Трейл урона (плавно догоняющий хвост)
            if (trailingRatio > visualRatio) {
                float trailW = Math.max(1.5f, barW * trailingRatio);
                Render2D.drawRoundedRect(context, barX, barY, trailW, barH, 0.9f, ColorUtil.rgba(251, 146, 60, 210));
            }

            // Основная полоса
            float fillW = Math.max(1.0f, barW * visualRatio);
            int barCol;
            String colorSetting = this.colorMode.get();
            if ("Тема".equals(colorSetting)) {
                barCol = Theme.getAccent();
            } else if ("Градиент".equals(colorSetting)) {
                if (visualRatio > 0.5f) {
                    float f = (visualRatio - 0.5f) * 2.0f;
                    barCol = ColorUtil.interpolate(0xFFFACC15, 0xFF4ADE80, f);
                } else {
                    float f = visualRatio * 2.0f;
                    barCol = ColorUtil.interpolate(0xFFEF4444, 0xFFFACC15, f);
                }
            } else { // "Здоровье"
                if (visualRatio > 0.6f) barCol = 0xFF4ADE80;
                else if (visualRatio > 0.25f) barCol = 0xFFFACC15;
                else barCol = 0xFFEF4444;
            }
            Render2D.drawRoundedRect(context, barX, barY, fillW, barH, 0.9f, barCol);

            // Золотые сердца (поглощение)
            if (absorption > 0.0f) {
                float absRatio = MathHelper.clamp(absorption / maxHp, 0.0f, 1.0f);
                float absW = Math.max(1.0f, barW * absRatio);
                Render2D.drawRoundedRect(context, barX, barY, absW, barH, 0.9f, ColorUtil.rgba(252, 211, 77, 230));
            }
        }

        context.getMatrices().popMatrix();
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
