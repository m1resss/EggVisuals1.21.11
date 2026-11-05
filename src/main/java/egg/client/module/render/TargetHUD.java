package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.Render2D;
import egg.client.render.hud.HudContextMenu;
import egg.client.render.hud.HudSnapHelper;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.ClickGuiScreen;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class TargetHUD extends Module {

    private static TargetHUD instance;

    private static final Identifier HEART_ICON = Identifier.of("egg", "textures/hud/heart.png");

    private final ModeSetting mode = new ModeSetting("Режим", "Экран", "Расположение Target HUD", "Экран", "В мире");
    private final SliderSetting hudScale = new SliderSetting("Масштаб", 1.0, 0.5, 2.0, 0.05, "Масштаб Target HUD");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 100.0, 10.0, 100.0, 5.0, "Прозрачность Target HUD (%)");
    private final SliderSetting targetTimeout = new SliderSetting("Таймаут цели", 3.0, 0.5, 8.0, 0.5, "Сколько секунд держать цель после отвода прицела");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true, "Отображать карточку только для игроков");
    private final BooleanSetting hitParticles = new BooleanSetting("Партиклы при ударе", true, "Отображать вылетающие частицы на полоске здоровья при получении урона");
    private final BooleanSetting hudThemeGradient = new BooleanSetting("Градиент HUD", true, "Мягкий градиент по бокам карточки");
    private final SliderSetting hudGradientStrength = new SliderSetting("Сила градиента", 60.0, 5.0, 100.0, 5.0, "Интенсивность градиента");
    private final BooleanSetting showFps = new BooleanSetting("Показывать FPS", true, "Отображать метку FPS на карточке");
    private final BooleanSetting showDistance = new BooleanSetting("Дистанция", true, "Отображать дистанцию до цели");
    private final BooleanSetting eatIndicator = new BooleanSetting("Индикатор еды", true, "Отображать процесс еды и зелий поверх головы");
    private final SliderSetting posX = new SliderSetting("X", 0.52, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.68, 0.0, 1.0, 0.001, "Позиция Y");

    private LivingEntity currentTarget = null;
    private float targetTimer = 0.0f;
    private float animAlpha = 0.0f;
    private float visualHp = 20.0f;
    private float visualAbsorption = 0.0f;
    private long lastFrameNanos = 0L;

    private float smoothScreenX = 0.0f;
    private float smoothScreenY = 0.0f;
    private boolean smoothInit = false;

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private final HudContextMenu contextMenu = new HudContextMenu("Target HUD");
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastRenderX = 0.0f;
    private float lastRenderY = 0.0f;
    private float lastRenderW = 152.0f;
    private float lastRenderH = 44.0f;

    public ModeSetting getMode() { return mode; }
    public float getLastX() { return lastRenderX; }
    public float getLastY() { return lastRenderY; }
    public float getLastW() { return lastRenderW; }
    public float getLastH() { return lastRenderH; }

    private final List<HitParticle> particles = new ArrayList<>();
    private LivingEntity lastDamageTarget = null;
    private float lastKnownHp = -1.0f;
    private int lastHurtTime = 0;

    private final RollingNumber hpRolling = new RollingNumber();
    private LivingEntity lastHpTarget = null;

    private LivingEntity lastEatTarget = null;
    private float smoothEatProgress = 0.0f;
    private ItemStack lastEatStack = ItemStack.EMPTY;
    private float eatTimerSeconds = 0.0f;

    public TargetHUD() {
        super("Target HUD", "Показывает здоровье, броню и имя противника в прицеле", Category.ESP);
        instance = this;

        posX.setVisible(() -> false);
        posY.setVisible(() -> false);

        contextMenu.addMode("Режим", () -> mode.get(), () -> mode.cycle());
        contextMenu.addToggle("Только игроки", () -> onlyPlayers.isEnabled(), () -> onlyPlayers.toggle());
        contextMenu.addToggle("Частицы удара", () -> hitParticles.isEnabled(), () -> hitParticles.toggle());
        contextMenu.addToggle("Градиент HUD", () -> hudThemeGradient.isEnabled(), () -> hudThemeGradient.toggle());
        contextMenu.addToggle("Дистанция", () -> showDistance.isEnabled(), () -> showDistance.toggle());
        contextMenu.addToggle("Показывать FPS", () -> showFps.isEnabled(), () -> showFps.toggle());
        contextMenu.addToggle("Индикатор еды", () -> eatIndicator.isEnabled(), () -> eatIndicator.toggle());

        addSettings(mode, hudScale, opacity, targetTimeout, onlyPlayers, hitParticles, hudThemeGradient, hudGradientStrength, showFps, showDistance, eatIndicator, posX, posY);
    }

    public static TargetHUD getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.particles.clear();
        this.lastDamageTarget = null;
        this.lastKnownHp = -1.0f;
        this.lastHpTarget = null;
        this.hpRolling.reset(-1);
        this.lastEatTarget = null;
        this.smoothEatProgress = 0.0f;
        this.lastEatStack = ItemStack.EMPTY;
        this.eatTimerSeconds = 0.0f;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) * 1e-9);
        lastFrameNanos = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

        float tickDelta = tickCounter != null ? tickCounter.getTickProgress(true) : 0.0f;

        updateTargetAcquisition(mc, dt);

        boolean inGui = mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof ChatScreen;
        LivingEntity renderTarget = currentTarget;

        if (renderTarget == null || !renderTarget.isAlive()) {
            if (inGui) {
                renderTarget = mc.player;
            }
        }

        boolean hasTarget = renderTarget != null && renderTarget.isAlive();

        boolean isWorldMode = mode.is("В мире");
        if (hasTarget && isWorldMode && renderTarget != mc.player) {
            if (isOccludedByWall(mc, renderTarget)) {
                hasTarget = false;
            }
        }

        if (hasTarget) {
            animAlpha = Math.min(1.0f, animAlpha + dt * 7.0f);
        } else {
            animAlpha = Math.max(0.0f, animAlpha - dt * 6.0f);
            smoothInit = false;
        }

        if (animAlpha <= 0.01f || renderTarget == null) {
            smoothInit = false;
            return;
        }

        float maxHealth = Math.max(1.0f, renderTarget.getMaxHealth());
        float actualHealth = Math.clamp(renderTarget.getHealth(), 0.0f, maxHealth);
        float actualAbsorption = Math.max(0.0f, renderTarget.getAbsorptionAmount());

        visualHp += (actualHealth - visualHp) * Math.min(1.0f, dt * 10.0f);
        visualAbsorption += (actualAbsorption - visualAbsorption) * Math.min(1.0f, dt * 10.0f);

        float cardW = 152.0f;
        float cardH = 44.0f;
        float cardRadius = 12.0f;
        float scale = hudScale.getValueFloat();

        float cardX;
        float cardY;

        if (isWorldMode && renderTarget != mc.player && !inGui) {

            double wx = MathHelper.lerp(tickDelta, renderTarget.lastRenderX, renderTarget.getX());
            double wy = MathHelper.lerp(tickDelta, renderTarget.lastRenderY, renderTarget.getY()) + renderTarget.getHeight() * 0.5;
            double wz = MathHelper.lerp(tickDelta, renderTarget.lastRenderZ, renderTarget.getZ());

            Vector2f screenPos = ProjectUtil.toScreen(wx, wy, wz, tickDelta);
            if (!ProjectUtil.isOnScreen(screenPos)) return;

            double dist = mc.player.distanceTo(renderTarget);
            float distScale = (float) Math.clamp(1.0 - (dist - 4.0) * 0.015, 0.72, 1.25);
            scale *= distScale;

            if (!smoothInit || Math.abs(smoothScreenX - screenPos.x) > 120.0f || Math.abs(smoothScreenY - screenPos.y) > 120.0f) {
                smoothScreenX = screenPos.x;
                smoothScreenY = screenPos.y;
                smoothInit = true;
            } else {
                float smoothSpeed = Math.min(1.0f, dt * 45.0f);
                smoothScreenX = MathHelper.lerp(smoothSpeed, smoothScreenX, screenPos.x);
                smoothScreenY = MathHelper.lerp(smoothSpeed, smoothScreenY, screenPos.y);
            }

            cardX = smoothScreenX - (cardW * scale) / 2.0f;
            cardY = smoothScreenY - (cardH * scale) / 2.0f;
        } else {

            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float rawX = posX.getValueFloat() * (screenW - cardW * scale);
            float rawY = posY.getValueFloat() * (screenH - cardH * scale);
            cardX = Math.clamp(rawX, 4.0f, screenW - cardW * scale - 4.0f);
            cardY = Math.clamp(rawY, 4.0f, screenH - cardH * scale - 4.0f);
        }

        this.lastRenderX = cardX;
        this.lastRenderY = cardY;
        this.lastRenderW = cardW * scale;
        this.lastRenderH = cardH * scale;

        renderCard(context, renderTarget, cardX, cardY, cardW, cardH, cardRadius, scale, animAlpha, maxHealth, actualAbsorption, dt);
    }

    private void renderCard(DrawContext context, LivingEntity target, float x, float y, float w, float h,
                            float radius, float scale, float alpha, float maxHp, float absorption, float dt) {
        Matrix3x2f mat = null;
        boolean customScale = Math.abs(scale - 1.0f) > 0.001f;
        if (customScale) {
            mat = new Matrix3x2f();
            mat.translate(x, y);
            mat.scale(scale, scale);
            mat.translate(-x, -y);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-x, -y);
        }

        float op = opacity.getValueFloat() / 100.0f;
        int bgAlpha = (int) (225 * alpha * op);
        int borderAlpha = (int) (30 * alpha * op);
        int shadowAlpha = (int) (65 * alpha * op);

        int bgColor = ColorUtil.rgba(18, 18, 22, bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, borderAlpha);
        int shadowColor = ColorUtil.rgba(0, 0, 0, shadowAlpha);

        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, w, h, radius, shadowColor, 6.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, w, h, radius, bgColor);

        if (hudThemeGradient.isEnabled()) {
            float str = hudGradientStrength.getValueFloat() / 100.0f;
            int gradC = ColorUtil.rgba(168, 85, 247, (int) (str * 45.0f * alpha * op));
            Draw2DProcessor.INSTANCE.drawGradient(mat, x, y, w, h, radius, gradC, 0x00000000, 0x00000000, gradC);
        }
        DrawBatcher.flushPending();
        if (hudThemeGradient.isEnabled() && alpha > 0.15f) {
            Render2D.drawDiagonalSheen(context, x, y, w, h, radius, 0L);
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (lastEatTarget != target) {
            lastEatTarget = target;
            smoothEatProgress = 0.0f;
            lastEatStack = ItemStack.EMPTY;
            eatTimerSeconds = 0.0f;
        }

        boolean eating = false;
        ItemStack activeStack = target.isUsingItem() ? target.getActiveItem() : ItemStack.EMPTY;
        if (activeStack.isEmpty() && target.isUsingItem()) {
            if (isConsumable(target.getMainHandStack())) {
                activeStack = target.getMainHandStack();
            } else if (isConsumable(target.getOffHandStack())) {
                activeStack = target.getOffHandStack();
            }
        }
        if (target.isUsingItem() && isConsumable(activeStack)) {
            eating = true;
            lastEatStack = activeStack.copy();
            int timeLeft = target.getItemUseTimeLeft();
            if (timeLeft > 0) {
                float tickDelta = (mc != null && mc.getRenderTickCounter() != null) ? mc.getRenderTickCounter().getTickProgress(false) : 0.0f;
                eatTimerSeconds = Math.max(0.0f, (float) timeLeft - tickDelta) * 0.05f;
            } else {
                if (eatTimerSeconds <= 0.0f) {
                    int maxTime = lastEatStack.getMaxUseTime(target);
                    eatTimerSeconds = (maxTime > 0 ? maxTime : 32) * 0.05f;
                } else {
                    eatTimerSeconds = Math.max(0.0f, eatTimerSeconds - dt);
                }
            }
        } else {
            eatTimerSeconds = Math.max(0.0f, eatTimerSeconds - dt);
        }

        float targetEatProgress = (eating && eatIndicator.get()) ? 1.0f : 0.0f;
        if (smoothEatProgress < targetEatProgress) {
            smoothEatProgress = Math.min(targetEatProgress, smoothEatProgress + dt * 10.0f);
        } else if (smoothEatProgress > targetEatProgress) {
            smoothEatProgress = Math.max(targetEatProgress, smoothEatProgress - dt * 8.0f);
        }
        if (smoothEatProgress <= 0.001f) {
            smoothEatProgress = 0.0f;
            if (!eating) {
                lastEatStack = ItemStack.EMPTY;
                eatTimerSeconds = 0.0f;
            }
        }

        float headSize = 30.0f;
        float headX = x + 7.0f;
        float headY = y + 7.0f;
        float headRadius = 7.0f;

        int headBgColor = ColorUtil.rgba(30, 30, 36, (int) (240 * alpha));
        if (target.hurtTime > 0) {
            float hurtFactor = target.hurtTime / 10.0f;
            headBgColor = ColorUtil.interpolate(headBgColor, ColorUtil.rgba(220, 30, 30, (int) (240 * alpha)), hurtFactor);
        }
        Draw2DProcessor.INSTANCE.drawRect(mat, headX, headY, headSize, headSize, headRadius, headBgColor);

        Identifier skinTex = getTargetSkin(target);
        if (skinTex != null) {
            if (smoothEatProgress > 0.01f) {
                renderBlurredHead(mat, skinTex, target, headX, headY, headSize, headRadius, alpha, smoothEatProgress);
            } else {
                Draw2DProcessor.INSTANCE.a(mat, skinTex, target, headX, headY, headSize, headSize, headRadius, alpha);
            }
        } else if (smoothEatProgress > 0.01f) {
            int frostDark = ColorUtil.rgba(12, 12, 18, (int) (185 * smoothEatProgress * alpha));
            Draw2DProcessor.INSTANCE.drawRect(mat, headX, headY, headSize, headSize, headRadius, frostDark);
            int accentBorder = ColorUtil.withAlpha(Theme.getAccent(), (int) (160 * smoothEatProgress * alpha));
            Draw2DProcessor.INSTANCE.drawOutline(mat, headX, headY, headSize, headSize, headRadius, 1.0f, accentBorder);
        }

        if (smoothEatProgress > 0.01f && !lastEatStack.isEmpty()) {
            DrawBatcher.flushPending();

            float itemScale = 0.82f * (0.85f + 0.15f * smoothEatProgress);
            float itemCx = headX + headSize * 0.5f;
            float itemCy = headY + 8.5f;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(itemCx, itemCy);
            context.getMatrices().scale(itemScale, itemScale);
            context.getMatrices().translate(-8.0f, -8.0f);
            context.drawItem(lastEatStack, 0, 0);
            context.getMatrices().popMatrix();

            String timerStr = String.format(Locale.US, "%.2fсек", Math.max(0.0f, eatTimerSeconds));
            float tw = Fonts.b.a(timerStr, 7.0f);
            float pillW = tw + 4.0f;
            float pillH = 7.5f;
            float pillX = headX + (headSize - pillW) * 0.5f;
            float pillY = headY + 19.5f;

            int pillBg = ColorUtil.rgba(8, 8, 12, (int) (185 * smoothEatProgress * alpha));
            Draw2DProcessor.INSTANCE.drawRect(mat, pillX, pillY, pillW, pillH, 2.5f, pillBg);

            int textCol = ColorUtil.rgba(255, 255, 255, (int) (245 * smoothEatProgress * alpha));
            Fonts.b.a(mat, timerStr, headX + (headSize - tw) * 0.5f, pillY + 0.5f, 7.0f, textCol);
        }

        float textX = x + 43.0f;
        float nameY = y + 5.5f;
        String name = target.getName().getString();
        if (egg.client.module.misc.Globals.isEggUser(target)) {
            name = "🥚 " + name;
        }
        int maxNameLen = 15;
        if (name.length() > maxNameLen) {
            name = name.substring(0, maxNameLen) + "...";
        }

        int nameColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Fonts.b.a(mat, name, textX, nameY, 11.0f, nameColor);

        float hpY = y + 20.0f;
        float heartSize = 10.0f;
        int heartGlowColor = ColorUtil.rgba(255, 30, 80, (int) (160 * alpha));
        Draw2DProcessor.INSTANCE.drawGlow(mat, textX - 2.0f, hpY - 1.5f, heartSize + 4.0f, heartSize + 4.0f, (heartSize + 4.0f) / 2.0f, heartGlowColor, 4.0f);
        int heartColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Draw2DProcessor.INSTANCE.a(mat, HEART_ICON, textX, hpY + 0.5f, heartSize, heartSize, 0.0f, heartColor);

        float hpTextX = textX + heartSize + 4.0f;
        int curHp = (int) Math.ceil(target.getHealth());
        if (lastHpTarget != target) {
            lastHpTarget = target;
            hpRolling.reset(curHp);
        } else {
            hpRolling.update(curHp);
        }

        int hpTextColor = ColorUtil.rgba(255, 255, 255, (int) (230 * alpha));
        hpRolling.render(mat, hpTextX, hpY, 9.5f, hpTextColor, 1.0f);

        String hpValueStr = String.valueOf(curHp);
        float nextTextX = hpTextX + Fonts.b.a(hpValueStr, 9.5f);
        if (absorption > 0.0f) {
            String absStr = " +" + String.format("%.0f", Math.ceil(absorption));
            int absTextColor = ColorUtil.rgba(255, 215, 0, (int) (255 * alpha));
            Fonts.b.a(mat, absStr, nextTextX, hpY, 9.5f, absTextColor);
        }

        float barX = textX;
        float barY = y + 32.0f;
        float barW = w - 43.0f - 10.0f;
        float barH = 5.0f;
        float barRadius = 2.5f;

        int trackColor = ColorUtil.rgba(255, 255, 255, (int) (38 * alpha));
        Draw2DProcessor.INSTANCE.drawRect(mat, barX, barY, barW, barH, barRadius, trackColor);

        int themeCol1 = Theme.getAccent();
        int themeCol2 = Theme.getAccentHover();
        int themeFill1 = ColorUtil.withAlpha(themeCol1, (int) (255 * alpha));
        int themeFill2 = ColorUtil.withAlpha(themeCol2, (int) (255 * alpha));

        float totalMax = maxHp + Math.max(0.0f, visualAbsorption);
        float baseRatio = Math.clamp(visualHp / totalMax, 0.0f, 1.0f);
        float baseW = barW * baseRatio;

        if (baseW > 1.5f) {
            Draw2DProcessor.INSTANCE.drawGradient(mat, barX, barY, baseW, barH, barRadius, themeFill1, themeFill2, themeFill1, themeFill2);
        }

        float tipX = barX + baseW;
        float tipY = barY + barH / 2.0f;

        if (visualAbsorption > 0.01f) {
            float absRatio = Math.clamp(visualAbsorption / totalMax, 0.0f, 1.0f - baseRatio);
            float absW = Math.max(3.0f, barW * absRatio);
            float absX = barX + baseW;
            tipX = absX + absW;

            int goldCol1 = ColorUtil.rgba(255, 215, 0, (int) (255 * alpha));
            int goldCol2 = ColorUtil.rgba(255, 179, 0, (int) (255 * alpha));
            Draw2DProcessor.INSTANCE.drawGradient(mat, absX, barY, absW, barH, barRadius, goldCol1, goldCol2, goldCol1, goldCol2);
        }

        float curActualHp = target.getHealth();
        int curHurt = target.hurtTime;
        if (lastDamageTarget != target || lastKnownHp < 0.0f) {
            lastDamageTarget = target;
            lastKnownHp = curActualHp;
            lastHurtTime = curHurt;
        } else {
            float diff = lastKnownHp - curActualHp;
            boolean damaged = diff > 0.01f || (curHurt > 0 && lastHurtTime == 0);
            if (damaged && hitParticles.get()) {
                spawnDamageParticles(tipX, tipY, Math.max(diff, 1.0f));
            }
            lastKnownHp = curActualHp;
            lastHurtTime = curHurt;
        }

        if (!particles.isEmpty()) {
            particles.removeIf(p -> !p.update(dt));
            if (hitParticles.get()) {
                for (HitParticle p : particles) {
                    p.render(mat, alpha);
                }
            } else {
                particles.clear();
            }
        }

        DrawBatcher.flushPending();
        if (customScale) {
            context.getMatrices().popMatrix();
        }
    }

    private void spawnDamageParticles(float tipX, float tipY, float damage) {
        int count = Math.clamp(8 + (int) (damage * 3.0f), 10, 24);
        int themeCol = Theme.getAccent();
        int themeHover = Theme.getAccentHover();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble(-Math.PI * 0.85, Math.PI * 0.35);
            float speed = (float) rng.nextDouble(28.0, 85.0);
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;

            float whiteFactor = (float) rng.nextDouble(0.1, 0.6);
            int baseC = rng.nextBoolean() ? themeCol : themeHover;
            int pColor = ColorUtil.interpolate(baseC, 0xFFFFFFFF, whiteFactor);

            float size = (float) rng.nextDouble(1.8, 3.4);
            float lifetime = (float) rng.nextDouble(0.45, 0.85);

            particles.add(new HitParticle(
                tipX + (float) rng.nextDouble(-2.0, 2.0),
                tipY + (float) rng.nextDouble(-1.5, 1.5),
                vx, vy, size, pColor, lifetime
            ));
        }
    }

    private static class HitParticle {
        private float x, y;
        private float vx, vy;
        private final float size;
        private final int color;
        private float life;
        private final float maxLife;

        HitParticle(float x, float y, float vx, float vy, float size, int color, float maxLife) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.life = maxLife;
            this.maxLife = maxLife;
        }

        boolean update(float dt) {
            life -= dt;
            if (life <= 0.0f) return false;
            vy += 50.0f * dt;
            vx *= Math.max(0.0f, 1.0f - 2.8f * dt);
            vy *= Math.max(0.0f, 1.0f - 2.8f * dt);
            x += vx * dt;
            y += vy * dt;
            return true;
        }

        void render(Matrix3x2f mat, float cardAlpha) {
            float progress = life / maxLife;
            float pAlpha = progress * cardAlpha;
            if (pAlpha <= 0.01f) return;

            int c = ColorUtil.withAlpha(color, (int) (255 * pAlpha));
            int glowC = ColorUtil.withAlpha(color, (int) (120 * pAlpha));
            float r = size * (0.6f + 0.4f * progress);

            Draw2DProcessor.INSTANCE.drawGlow(mat, x - r, y - r, r * 2.0f, r * 2.0f, r, glowC, 3.5f);
            Draw2DProcessor.INSTANCE.drawRect(mat, x - r, y - r, r * 2.0f, r * 2.0f, r, c);
        }
    }

    public static class RollingNumber {
        private int currentVal = -1;
        private int prevVal = -1;
        private long changeTime = 0L;
        private static final long DURATION_MS = 180L;

        public void reset(int val) {
            this.currentVal = val;
            this.prevVal = val;
            this.changeTime = 0L;
        }

        public void update(int newVal) {
            if (currentVal == -1) {
                currentVal = newVal;
                prevVal = newVal;
                return;
            }
            if (newVal != currentVal) {
                prevVal = currentVal;
                currentVal = newVal;
                changeTime = System.currentTimeMillis();
            }
        }

        public void render(Matrix3x2f mat, float x, float y, float size, int color, float alphaFactor) {
            long elapsed = System.currentTimeMillis() - changeTime;
            int baseA = (int) (((color >> 24) & 0xFF) * alphaFactor);
            int rgb = color & 0x00FFFFFF;
            if (elapsed >= DURATION_MS || prevVal == currentVal || prevVal < 0) {
                Fonts.b.a(mat, String.valueOf(currentVal), x, y, size, (baseA << 24) | rgb);
                return;
            }

            float t = (float) elapsed / DURATION_MS;
            float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

            String oldText = String.valueOf(prevVal);
            String newText = String.valueOf(currentVal);

            float oldY = y - ease * 5.0f;
            int oldA = Math.round(baseA * (1.0f - ease));
            if (oldA > 5) {
                Fonts.b.a(mat, oldText, x, oldY, size, (oldA << 24) | rgb);
            }

            float newY = y + (1.0f - ease) * 5.0f;
            int newA = Math.round(baseA * ease);
            if (newA > 5) {
                Fonts.b.a(mat, newText, x, newY, size, (newA << 24) | rgb);
            }
        }
    }

    private Identifier getTargetSkin(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && entity instanceof PlayerEntity player) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null && entry.getSkinTextures() != null && entry.getSkinTextures().body() != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        }
        if (mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null && entry.getSkinTextures() != null && entry.getSkinTextures().body() != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        }
        return Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    }

    private void updateTargetAcquisition(MinecraftClient mc, float dt) {
        LivingEntity found = findHoveredTarget(mc);
        if (found != null) {
            this.currentTarget = found;
            this.targetTimer = (float) targetTimeout.get();
        } else {
            if (this.targetTimer > 0.0f) {
                this.targetTimer -= dt;
                if (this.targetTimer <= 0.0f) {
                    this.currentTarget = null;
                    this.lastDamageTarget = null;
                    this.lastKnownHp = -1.0f;
                }
            } else {
                this.currentTarget = null;
                this.lastDamageTarget = null;
                this.lastKnownHp = -1.0f;
            }
        }
        if (this.currentTarget != null && onlyPlayers.get() && !(this.currentTarget instanceof PlayerEntity)) {
            this.currentTarget = null;
            this.lastDamageTarget = null;
            this.lastKnownHp = -1.0f;
            this.targetTimer = 0.0f;
        }
    }

    private LivingEntity findHoveredTarget(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (living != mc.player && living.isAlive() && (!onlyPlayers.get() || living instanceof PlayerEntity)) {
                return living;
            }
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double reach = 32.0;
        Vec3d end = eye.add(look.multiply(reach));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
        ));
        double maxDistSq = reach * reach;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistSq = eye.squaredDistanceTo(blockHit.getPos());
            end = blockHit.getPos();
        }

        Box box = mc.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        LivingEntity closest = null;
        double closestDistSq = maxDistSq;

        for (Entity e : mc.world.getOtherEntities(mc.player, box)) {
            if (e instanceof LivingEntity living && e != mc.player && living.isAlive()) {
                if (onlyPlayers.get() && !(living instanceof PlayerEntity)) {
                    continue;
                }
                Box eb = living.getBoundingBox().expand(0.3);
                Optional<Vec3d> hit = eb.raycast(eye, end);
                if (hit.isPresent()) {
                    double dSq = eye.squaredDistanceTo(hit.get());
                    if (dSq < closestDistSq) {
                        closestDistSq = dSq;
                        closest = living;
                    }
                }
            }
        }
        return closest;
    }

    private boolean isOccludedByWall(MinecraftClient mc, LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetEye = target.getEyePos();

        BlockHitResult hitEye = mc.world.raycast(new RaycastContext(
                eye, targetEye, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hitEye.getType() != HitResult.Type.MISS) {
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            BlockHitResult hitCenter = mc.world.raycast(new RaycastContext(
                    eye, targetCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
            ));
            return hitCenter.getType() != HitResult.Type.MISS;
        }
        return false;
    }

    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float rad = 12.0f * hudScale.getValueFloat();
        boolean isScreenMode = mode.is("Экран");

        if (dragging && isScreenMode) {
            mouseDragged(mouseX, mouseY);
            HudSnapHelper.renderGridAndGuides(context, currentSnap, screenW, screenH,
                    new HudSnapHelper.HudElementBounds("target", "Target HUD", lastRenderX, lastRenderY, lastRenderW, lastRenderH),
                    "Target HUD", Math.round(hudScale.getValueFloat() * 100.0f), rad);
        } else {
            if (dragging && !isScreenMode) {
                dragging = false;
            }
            boolean inside = mouseX >= lastRenderX - 2.0f && mouseX <= lastRenderX + lastRenderW + 2.0f
                    && mouseY >= lastRenderY - 2.0f && mouseY <= lastRenderY + lastRenderH + 2.0f;
            if (inside) {
                String modeTag = isScreenMode ? "" : " [В мире]";
                String scaleBadge = "Target HUD" + modeTag + ": " + Math.round(hudScale.getValueFloat() * 100.0f) + "%";
                HudSnapHelper.renderHoverBadge(context, lastRenderX, lastRenderY, lastRenderW, lastRenderH, rad, scaleBadge);
            }
        }

        if (contextMenu.isOpen()) {
            contextMenu.setScale(hudScale.getValueFloat());
            contextMenu.render(context, mouseX, mouseY);
        }
        DrawBatcher.flushPending();
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isEnabled()) return false;
        boolean inside = mouseX >= lastRenderX - 4.0f && mouseX <= lastRenderX + lastRenderW + 4.0f
                && mouseY >= lastRenderY - 4.0f && mouseY <= lastRenderY + lastRenderH + 4.0f;
        if (inside) {
            double cur = hudScale.getValue();
            double step = amount > 0 ? 0.05 : -0.05;
            hudScale.setValue(Math.clamp(Math.round((cur + step) * 100.0) / 100.0, 0.5, 2.0));
            ConfigManager.save();
            return true;
        }
        return false;
    }

    public boolean onChatMouseClicked(double mx, double my, int button) {
        if (!isEnabled()) return false;

        if (contextMenu.isOpen()) {
            contextMenu.setScale(hudScale.getValueFloat());
            if (contextMenu.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        boolean inside = mx >= lastRenderX - 3.0f && mx <= lastRenderX + lastRenderW + 3.0f
                && my >= lastRenderY - 3.0f && my <= lastRenderY + lastRenderH + 3.0f;
        if (inside) {
            if (button == 1) {
                contextMenu.setScale(hudScale.getValueFloat());
                contextMenu.open((float) mx, (float) my);
                return true;
            } else if (button == 0 && mode.is("Экран")) {
                this.dragging = true;
                this.dragOffsetX = (float) mx - lastRenderX;
                this.dragOffsetY = (float) my - lastRenderY;
                return true;
            }
        }
        return false;
    }

    public boolean onChatMouseReleased(double mx, double my, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            this.currentSnap = null;
            ConfigManager.save();
            return true;
        }
        return false;
    }

    public boolean onChatMouseDragged(double mx, double my, int button) {
        if (this.dragging && button == 0) {
            mouseDragged(mx, my);
            return true;
        }
        return false;
    }

    public void mouseDragged(double mx, double my) {
        if (this.dragging && mode.is("Экран")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float proposedX = (float) mx - dragOffsetX;
            float proposedY = (float) my - dragOffsetY;

            HudSnapHelper.SnapResult snap = HudSnapHelper.calculateSnap("target", proposedX, proposedY, lastRenderW, lastRenderH, screenW, screenH);
            this.currentSnap = snap;

            float maxAvailX = Math.max(1.0f, screenW - lastRenderW);
            float maxAvailY = Math.max(1.0f, screenH - lastRenderH);

            posX.setValue(Math.clamp(snap.snappedX / maxAvailX, 0.0, 1.0));
            posY.setValue(Math.clamp(snap.snappedY / maxAvailY, 0.0, 1.0));
        }
    }

    private void renderBlurredHead(Matrix3x2f mat, Identifier skin, LivingEntity target,
                                   float x, float y, float size, float radius, float alpha, float blurFactor) {
        if (skin == null) return;
        float blurSpread = blurFactor * 1.8f;
        float baseAlpha = alpha * (1.0f - blurFactor * 0.35f);

        float[][] samples = {
            {0.0f, 0.0f, 0.28f},
            {-blurSpread, 0.0f, 0.12f},
            {blurSpread, 0.0f, 0.12f},
            {0.0f, -blurSpread, 0.12f},
            {0.0f, blurSpread, 0.12f},
            {-blurSpread * 0.7f, -blurSpread * 0.7f, 0.06f},
            {-blurSpread * 0.7f, blurSpread * 0.7f, 0.06f},
            {blurSpread * 0.7f, -blurSpread * 0.7f, 0.06f},
            {blurSpread * 0.7f, blurSpread * 0.7f, 0.06f}
        };

        for (float[] s : samples) {
            Draw2DProcessor.INSTANCE.a(mat, skin, target, x + s[0], y + s[1], size, size, radius, baseAlpha * (s[2] / 0.28f));
        }

        int frostDark = ColorUtil.rgba(12, 12, 18, (int) (185 * blurFactor * alpha));
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, size, size, radius, frostDark);

        int accentBorder = ColorUtil.withAlpha(Theme.getAccent(), (int) (160 * blurFactor * alpha));
        Draw2DProcessor.INSTANCE.drawOutline(mat, x, y, size, size, radius, 1.0f, accentBorder);

        int accentGlow = ColorUtil.withAlpha(Theme.getAccent(), (int) (50 * blurFactor * alpha));
        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, size, size, radius, accentGlow, 3.5f);
    }

    private static boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        UseAction action = stack.getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }
}
