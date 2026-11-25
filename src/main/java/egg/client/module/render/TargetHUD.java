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
import egg.client.util.EntityUtil;
import egg.client.util.ProjectUtil;
import egg.client.util.RollingNumber;
import egg.client.util.animation.SpringAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
    private static final Identifier HEART_OUTLINE_ICON = Identifier.of("egg", "textures/icons/hud_heart_outline.png");
    private static final Identifier CROSS_ICON = Identifier.of("egg", "textures/icons/gui_cross.png");
    private static final String STYLE_CLASSIC = "Классик";
    private static final String STYLE_SPLIT = "Раздельный";

    private final ModeSetting style = new ModeSetting("Стиль", STYLE_CLASSIC, "Внешний вид Target HUD", STYLE_CLASSIC, STYLE_SPLIT);
    private final ModeSetting mode = new ModeSetting("Режим", "В мире", "Расположение Target HUD", "В мире", "Экран");
    private final SliderSetting hudScale = new SliderSetting("Масштаб", 1.0, 0.5, 2.0, 0.05, "Масштаб Target HUD");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 65.0, 10.0, 100.0, 5.0, "Прозрачность Target HUD (%)");
    private final SliderSetting targetTimeout = new SliderSetting("Таймаут цели", 3.0, 0.5, 8.0, 0.5, "Сколько секунд держать цель после отвода прицела");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true, "Отображать карточку только для игроков");
    private final BooleanSetting hitParticles = new BooleanSetting("Партиклы при ударе", true, "Отображать вылетающие частицы на полоске здоровья при получении урона");
    private final BooleanSetting hudThemeGradient = new BooleanSetting("Градиент HUD", true, "Мягкий градиент по бокам карточки");
    private final SliderSetting hudGradientStrength = new SliderSetting("Сила градиента", 60.0, 5.0, 100.0, 5.0, "Интенсивность градиента");
    private final BooleanSetting eatIndicator = new BooleanSetting("Индикатор еды", true, "Отображать процесс еды и зелий поверх головы");
    private final BooleanSetting showDurabilityPercent = new BooleanSetting("Прочность в %", false, "Показывать процент прочности текстом поверх брони");
    private final BooleanSetting ghostBar = new BooleanSetting("Ghost Bar", true, "Призрачный след на полоске здоровья при получении урона");
    private final SliderSetting posX = new SliderSetting("X", 0.52, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.68, 0.0, 1.0, 0.001, "Позиция Y");

    private LivingEntity currentTarget = null;
    private float targetTimer = 0.0f;
    private float animAlpha = 0.0f;
    private float visualHp = 20.0f;
    private float ghostHp = 20.0f;
    private float ghostFreezeTimer = 0.0f;
    private float visualAbsorption = 0.0f;
    private long lastFrameNanos = 0L;
    private final SpringAnimation popSpring = new SpringAnimation(0.0f, 240.0f, 18.0f);

    private float sideAnim = -1.0f;

    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private final HudContextMenu contextMenu = new HudContextMenu("Target HUD");
    private HudSnapHelper.SnapResult currentSnap = null;

    private float lastRenderX = 0.0f;
    private float lastRenderY = 0.0f;
    private float lastRenderW = 134.0f;
    private float lastRenderH = 44.0f;
    private float animatedScreenY = -1.0f;

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
    private final Matrix3x2f cardMat = new Matrix3x2f();
    private final Vector2f scratchScreenCenter = new Vector2f();
    private final Vector2f scratchScreenLeft = new Vector2f();
    private final Vector2f scratchScreenRight = new Vector2f();

    private LivingEntity lastEatTarget = null;
    private float smoothEatProgress = 0.0f;
    private ItemStack lastEatStack = ItemStack.EMPTY;
    private float eatTimerSeconds = 0.0f;

    public TargetHUD() {
        super("Target HUD", "Показывает здоровье, броню и имя противника в прицеле", Category.ESP);
        instance = this;

        posX.setVisible(() -> false);
        posY.setVisible(() -> false);
        ghostBar.setVisible(() -> style.is(STYLE_CLASSIC));

        contextMenu.addMode("Стиль", () -> style.get(), () -> style.cycle());
        contextMenu.addMode("Режим", () -> mode.get(), () -> mode.cycle());
        contextMenu.addToggle("Только игроки", () -> onlyPlayers.isEnabled(), () -> onlyPlayers.toggle());
        contextMenu.addToggle("Частицы удара", () -> hitParticles.isEnabled(), () -> hitParticles.toggle());
        contextMenu.addToggle("Градиент HUD", () -> hudThemeGradient.isEnabled(), () -> hudThemeGradient.toggle());
        contextMenu.addToggle("Индикатор еды", () -> eatIndicator.isEnabled(), () -> eatIndicator.toggle());
        contextMenu.addToggle("Прочность в %", () -> showDurabilityPercent.isEnabled(), () -> showDurabilityPercent.toggle());
        contextMenu.addToggle("Ghost Bar", () -> ghostBar.isEnabled(), () -> ghostBar.toggle());

        addSettings(style, mode, hudScale, opacity, targetTimeout, onlyPlayers, hitParticles, hudThemeGradient, hudGradientStrength, eatIndicator, showDurabilityPercent, ghostBar, posX, posY);
    }

    public static TargetHUD getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.animatedScreenY = -1.0f;
        this.particles.clear();
        this.lastDamageTarget = null;
        this.lastKnownHp = -1.0f;
        this.lastHpTarget = null;
        this.hpRolling.reset(-1);
        this.popSpring.setValue(0.0f);
        this.lastEatTarget = null;
        this.smoothEatProgress = 0.0f;
        this.lastEatStack = ItemStack.EMPTY;
        this.eatTimerSeconds = 0.0f;
        this.sideAnim = -1.0f;
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
            popSpring.setTarget(1.0f);
        } else {
            animAlpha = Math.max(0.0f, animAlpha - dt * 6.0f);
            popSpring.setTarget(0.0f);
        }

        popSpring.update(dt);
        float springProgress = popSpring.get();

        if ((animAlpha <= 0.01f && springProgress <= 0.01f) || renderTarget == null) {
            return;
        }

        float maxHealth = Math.max(1.0f, renderTarget.getMaxHealth());
        float actualHealth = Math.clamp(renderTarget.getHealth(), 0.0f, maxHealth);
        float actualAbsorption = Math.max(0.0f, renderTarget.getAbsorptionAmount());

        if (lastHpTarget != renderTarget) {
            lastHpTarget = renderTarget;
            visualHp = actualHealth;
            ghostHp = actualHealth;
            ghostFreezeTimer = 0.0f;
            hpRolling.reset((int) Math.ceil(actualHealth));
            sideAnim = -1.0f;
        } else {
            if (actualHealth < visualHp - 0.05f) {
                ghostFreezeTimer = 0.35f;
            } else if (actualHealth > visualHp + 0.05f) {
                ghostHp = actualHealth;
                ghostFreezeTimer = 0.0f;
            }

            visualHp += (actualHealth - visualHp) * Math.min(1.0f, dt * 16.0f);

            if (ghostFreezeTimer > 0.0f) {
                ghostFreezeTimer -= dt;
            } else {
                ghostHp += (visualHp - ghostHp) * Math.min(1.0f, dt * 4.5f);
            }
            if (Math.abs(ghostHp - visualHp) < 0.05f) {
                ghostHp = visualHp;
            }
        }
        visualAbsorption += (actualAbsorption - visualAbsorption) * Math.min(1.0f, dt * 10.0f);

        boolean splitStyle = style.is(STYLE_SPLIT);
        float cardW = splitStyle ? 153.0f : 134.0f;
        float cardH = splitStyle ? 34.0f : 44.0f;
        float cardRadius = splitStyle ? 7.5f : 12.0f;
        float scale = hudScale.getValueFloat();
        float springPopScale = 0.90f + 0.10f * springProgress;
        scale *= springPopScale;

        float cardX;
        float cardY;

        if (isWorldMode && renderTarget != mc.player && !inGui) {
            double tx = MathHelper.lerp(tickDelta, renderTarget.lastRenderX, renderTarget.getX());
            double ty = MathHelper.lerp(tickDelta, renderTarget.lastRenderY, renderTarget.getY());
            double tz = MathHelper.lerp(tickDelta, renderTarget.lastRenderZ, renderTarget.getZ());
            float halfWidth = renderTarget.getWidth() * 0.5f;
            float entityHeight = renderTarget.getHeight();
            double centerY = ty + entityHeight * 0.52;

            Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
            Vec3d camPos = (camera != null) ? camera.getCameraPos() : mc.player.getEyePos();

            double toX = tx - camPos.x;
            double toY = centerY - camPos.y;
            double toZ = tz - camPos.z;
            double toLenSq = toX * toX + toY * toY + toZ * toZ;
            double invLen = toLenSq > 1e-6 ? 1.0 / Math.sqrt(toLenSq) : 0.0;
            double dirX = toX * invLen;
            double dirZ = toZ * invLen;

            double rx = -dirZ;
            double rz = dirX;
            double rLen = Math.sqrt(rx * rx + rz * rz);
            if (rLen < 1e-5) {
                rx = 1.0;
                rz = 0.0;
            } else {
                rx /= rLen;
                rz /= rLen;
            }

            double sideDist = halfWidth + 0.15;
            double leftX = tx - rx * sideDist;
            double leftZ = tz - rz * sideDist;
            double rightX = tx + rx * sideDist;
            double rightZ = tz + rz * sideDist;

            boolean hasCenter = ProjectUtil.toScreen(tx, centerY, tz, tickDelta, scratchScreenCenter);
            if (!hasCenter || !ProjectUtil.isOnScreen(scratchScreenCenter)) return;

            ProjectUtil.toScreen(leftX, centerY, leftZ, tickDelta, scratchScreenLeft);
            ProjectUtil.toScreen(rightX, centerY, rightZ, tickDelta, scratchScreenRight);

            double dist = Math.sqrt(toLenSq);
            float distScale = 1.0f;
            if (dist > 4.0) {
                distScale = (float) Math.clamp(Math.pow(4.0 / dist, 0.60), 0.38, 1.0);
            }
            scale *= distScale;

            float leftEdgeX = (scratchScreenLeft.x != Float.MAX_VALUE) ? scratchScreenLeft.x : (scratchScreenCenter.x - 25.0f);
            float rightEdgeX = (scratchScreenRight.x != Float.MAX_VALUE) ? scratchScreenRight.x : (scratchScreenCenter.x + 25.0f);

            float margin = Math.max(3.0f, 7.0f * distScale);
            float targetLeftX = leftEdgeX - margin - (cardW * scale);
            float targetRightX = rightEdgeX + margin;

            double wallCheckDist = 0.90;
            Vec3d leftCheckPos = new Vec3d(tx - rx * wallCheckDist, centerY, tz - rz * wallCheckDist);
            Vec3d rightCheckPos = new Vec3d(tx + rx * wallCheckDist, centerY, tz + rz * wallCheckDist);
            Vec3d targetCenter = new Vec3d(tx, centerY, tz);

            BlockHitResult hitLeft = mc.world.raycast(new RaycastContext(
                    targetCenter, leftCheckPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, renderTarget));
            boolean leftWallHit = (hitLeft.getType() != HitResult.Type.MISS);
            BlockPos bposLeft = BlockPos.ofFloored(leftCheckPos);
            boolean leftSolid = !mc.world.getBlockState(bposLeft).getCollisionShape(mc.world, bposLeft).isEmpty();
            BlockHitResult camToLeftHit = mc.world.raycast(new RaycastContext(
                    camPos, leftCheckPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            boolean camLeftBlocked = (camToLeftHit.getType() != HitResult.Type.MISS);
            boolean isLeftBlocked = leftWallHit || leftSolid || camLeftBlocked || (targetLeftX < 4.0f);

            BlockHitResult hitRight = mc.world.raycast(new RaycastContext(
                    targetCenter, rightCheckPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, renderTarget));
            boolean rightWallHit = (hitRight.getType() != HitResult.Type.MISS);
            BlockPos bposRight = BlockPos.ofFloored(rightCheckPos);
            boolean rightSolid = !mc.world.getBlockState(bposRight).getCollisionShape(mc.world, bposRight).isEmpty();
            BlockHitResult camToRightHit = mc.world.raycast(new RaycastContext(
                    camPos, rightCheckPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            boolean camRightBlocked = (camToRightHit.getType() != HitResult.Type.MISS);
            float screenW = mc.getWindow().getScaledWidth();
            boolean isRightBlocked = rightWallHit || rightSolid || camRightBlocked || (targetRightX + cardW * scale > screenW - 4.0f);

            float desiredSide = -1.0f;
            if (isLeftBlocked && !isRightBlocked) {
                desiredSide = 1.0f;
            } else if (isLeftBlocked && isRightBlocked) {
                desiredSide = (targetLeftX < 4.0f) ? 1.0f : -1.0f;
            }

            sideAnim = MathHelper.lerp(Math.min(1.0f, dt * 14.0f), sideAnim, desiredSide);
            float t = Math.clamp((sideAnim + 1.0f) * 0.5f, 0.0f, 1.0f);

            float rawTargetX = MathHelper.lerp(t, targetLeftX, targetRightX);
            float rawTargetY = scratchScreenCenter.y - (cardH * scale) * 0.5f;

            float screenH = mc.getWindow().getScaledHeight();
            rawTargetX = Math.clamp(rawTargetX, 4.0f, screenW - cardW * scale - 4.0f);
            rawTargetY = Math.clamp(rawTargetY, 4.0f, screenH - cardH * scale - 4.0f);

            cardX = rawTargetX;
            cardY = rawTargetY;
        } else {

            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float rawX = posX.getValueFloat() * (screenW - cardW * scale);
            float rawY = posY.getValueFloat() * (screenH - cardH * scale);
            cardX = Math.clamp(rawX, 4.0f, screenW - cardW * scale - 4.0f);
            cardY = Math.clamp(rawY, 4.0f, screenH - cardH * scale - 4.0f);

            Interface iface = Interface.getInstance();
            float targetY = iface != null ? iface.getAdjustedY(cardX, cardY, cardW * scale, cardH * scale) : cardY;
            if (dragging || animatedScreenY < 0.0f) {
                animatedScreenY = dragging ? cardY : targetY;
            } else if (Math.abs(animatedScreenY - targetY) > 0.1f) {
                animatedScreenY += (targetY - animatedScreenY) * 0.25f;
            } else {
                animatedScreenY = targetY;
            }
            cardY = animatedScreenY;
        }

        this.lastRenderX = cardX;
        this.lastRenderY = cardY;
        this.lastRenderW = cardW * scale;
        this.lastRenderH = cardH * scale;

        if (splitStyle) {
            renderSplitCard(context, renderTarget, cardX, cardY, cardW, cardH, scale, animAlpha, maxHealth, actualAbsorption, dt);
        } else {
            renderCard(context, renderTarget, cardX, cardY, cardW, cardH, cardRadius, scale, animAlpha, maxHealth, actualAbsorption, dt);
        }
    }

    private void renderCard(DrawContext context, LivingEntity target, float x, float y, float w, float h,
                            float radius, float scale, float alpha, float maxHp, float absorption, float dt) {
        Matrix3x2f mat = null;
        boolean customScale = Math.abs(scale - 1.0f) > 0.001f;
        if (customScale) {
            mat = this.cardMat.identity();
            mat.translate(x, y);
            mat.scale(scale, scale);
            mat.translate(-x, -y);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-x, -y);
        }

        float op = opacity.getValueFloat() / 100.0f;
        int bgAlpha = (int) (200.0f * alpha * op);
        int borderAlpha = (int) (Math.max(16.0f, 32.0f * op) * alpha);
        int shadowAlpha = (int) (65 * alpha * op);

        int bgColor = ColorUtil.rgba(18, 18, 22, bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, borderAlpha);
        int shadowColor = ColorUtil.rgba(0, 0, 0, shadowAlpha);

        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, w, h, radius, shadowColor, 6.0f);
        Draw2DProcessor.INSTANCE.drawPanelBlur(mat, x, y, w, h, radius, alpha);
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, w, h, radius, bgColor);
        Draw2DProcessor.INSTANCE.drawOutline(mat, x, y, w, h, radius, 0.8f, borderColor);

        if (hudThemeGradient.isEnabled()) {
            Interface iface = Interface.getInstance();
            int gradC = iface != null ? iface.computeHudGradientColor(hudGradientStrength.getValueFloat(), alpha * op)
                    : ColorUtil.rgba(168, 85, 247, (int) ((hudGradientStrength.getValueFloat() / 100.0f) * 45.0f * alpha * op));
            Draw2DProcessor.INSTANCE.drawGradient(mat, x, y, w, h, radius, gradC, 0x00000000, 0x00000000, gradC);
        }
        DrawBatcher.flushPending();
        if (hudThemeGradient.isEnabled() && alpha > 0.15f) {
            Render2D.drawDiagonalSheen(context, x, y, w, h, radius, 0L, alpha);
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
        float contentW = 84.0f;
        float rightX = textX + contentW;
        float topY = y + 5.5f;

        int curHp = (int) Math.ceil(target.getHealth());
        if (lastHpTarget != target) {
            lastHpTarget = target;
            hpRolling.reset(curHp);
        } else {
            hpRolling.update(curHp);
        }

        String hpValueStr = String.valueOf(curHp);
        float hpValW = Fonts.b.a(hpValueStr, 9.5f);
        float hpSuffixW = Fonts.b.a("hp", 6.5f);
        String absStr = absorption > 0.0f ? " +" + String.format("%.0f", Math.ceil(absorption)) : "";
        float absTextW = absorption > 0.0f ? Fonts.b.a(absStr, 8.5f) : 0.0f;
        float hpTotalW = hpValW + hpSuffixW + (absorption > 0.0f ? 2.0f + absTextW : 0.0f);
        float hpStartX = rightX - hpTotalW;

        int hpTextColor = ColorUtil.rgba(255, 255, 255, (int) (240 * alpha));
        hpRolling.render(mat, hpStartX, topY, 9.5f, hpTextColor, 1.0f);

        int hpSuffixColor = ColorUtil.rgba(156, 163, 175, (int) (210 * alpha));
        Fonts.b.a(mat, "hp", hpStartX + hpValW + 0.5f, topY + 2.5f, 6.5f, hpSuffixColor);

        if (absorption > 0.0f) {
            int absTextColor = ColorUtil.rgba(255, 215, 0, (int) (255 * alpha));
            Fonts.b.a(mat, absStr, hpStartX + hpValW + hpSuffixW + 2.0f, topY + 1.0f, 8.5f, absTextColor);
        }

        String name = target.getName().getString();
        if (egg.client.module.misc.Globals.isEggUser(target)) {
            name = "🥚 " + name;
        }
        float availNameW = hpStartX - textX - 4.0f;
        String displayName = name;
        if (Fonts.b.a(displayName, 9.5f) > availNameW) {
            while (displayName.length() > 3 && Fonts.b.a(displayName + "...", 9.5f) > availNameW) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "...";
        }
        int nameColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Fonts.b.a(mat, displayName, textX, topY, 9.5f, nameColor);

        ItemStack[] equipStacks = new ItemStack[] {
            target.getEquippedStack(EquipmentSlot.HEAD),
            target.getEquippedStack(EquipmentSlot.CHEST),
            target.getEquippedStack(EquipmentSlot.LEGS),
            target.getEquippedStack(EquipmentSlot.FEET),
            target.getMainHandStack()
        };

        float equipY = y + 16.0f;
        float slotW = 14.0f;
        float slotH = 14.0f;
        float slotGap = 3.0f;
        float slotSep = 5.0f;

        for (int i = 0; i < 5; i++) {
            ItemStack stack = equipStacks[i];
            float slotX = textX + i * (slotW + slotGap) + (i == 4 ? (slotSep - slotGap) : 0.0f);
            boolean empty = stack == null || stack.isEmpty();
            int slotBg = ColorUtil.rgba(255, 255, 255, (int) ((empty ? 6 : 13) * alpha));
            int slotBorder = ColorUtil.rgba(255, 255, 255, (int) ((empty ? 12 : 24) * alpha));
            Draw2DProcessor.INSTANCE.drawRect(mat, slotX, equipY, slotW, slotH, 2.5f, slotBg);
            Draw2DProcessor.INSTANCE.drawOutline(mat, slotX, equipY, slotW, slotH, 2.5f, 0.6f, slotBorder);
        }

        for (int i = 0; i < 5; i++) {
            ItemStack stack = equipStacks[i];
            if (stack == null || stack.isEmpty()) continue;
            float slotX = textX + i * (slotW + slotGap) + (i == 4 ? (slotSep - slotGap) : 0.0f);

            if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                int maxDmg = stack.getMaxDamage();
                int curDmg = stack.getDamage();
                int curDurability = Math.max(0, maxDmg - curDmg);
                float durRatio = Math.clamp((float) curDurability / (float) maxDmg, 0.0f, 1.0f);

                if (durRatio < 1.0f) {
                    float barX = slotX + 1.2f;
                    float barY = equipY + slotH - 1.6f;
                    float barWidth = slotW - 2.4f;
                    float barHeight = 1.0f;

                    int durColor;
                    if (durRatio > 0.5f) {
                        durColor = ColorUtil.interpolate(0xFFFFD700, 0xFF33FF55, (durRatio - 0.5f) * 2.0f);
                    } else {
                        durColor = ColorUtil.interpolate(0xFFFF3333, 0xFFFFD700, durRatio * 2.0f);
                    }

                    Draw2DProcessor.INSTANCE.drawRect(mat, barX, barY, barWidth, barHeight, 0.5f, ColorUtil.rgba(0, 0, 0, (int) (180 * alpha)));
                    Draw2DProcessor.INSTANCE.drawRect(mat, barX, barY, Math.max(1.0f, barWidth * durRatio), barHeight, 0.5f, ColorUtil.withAlpha(durColor, (int) (240 * alpha)));
                }
            }
        }

        DrawBatcher.flushPending();

        for (int i = 0; i < 5; i++) {
            ItemStack stack = equipStacks[i];
            if (stack == null || stack.isEmpty()) continue;
            float slotX = textX + i * (slotW + slotGap) + (i == 4 ? (slotSep - slotGap) : 0.0f);
            float itemCx = slotX + slotW * 0.5f;
            float itemCy = equipY + 6.0f;
            float itemScale = 0.60f;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(itemCx, itemCy);
            context.getMatrices().scale(itemScale, itemScale);
            context.getMatrices().translate(-8.0f, -8.0f);
            context.drawItem(stack, 0, 0);
            context.getMatrices().popMatrix();
        }

        for (int i = 0; i < 5; i++) {
            ItemStack stack = equipStacks[i];
            if (stack == null || stack.isEmpty()) continue;
            float slotX = textX + i * (slotW + slotGap) + (i == 4 ? (slotSep - slotGap) : 0.0f);

            if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                int maxDmg = stack.getMaxDamage();
                int curDmg = stack.getDamage();
                int curDurability = Math.max(0, maxDmg - curDmg);
                float durRatio = Math.clamp((float) curDurability / (float) maxDmg, 0.0f, 1.0f);

                if (showDurabilityPercent.isEnabled() && durRatio < 1.0f) {
                    int pct = (int) Math.round(durRatio * 100.0f);
                    String pctStr = pct + "%";
                    float textScale = 0.55f;
                    int textW = mc.textRenderer.getWidth(pctStr);
                    int textH = mc.textRenderer.fontHeight;

                    int durColor;
                    if (durRatio > 0.5f) {
                        durColor = ColorUtil.interpolate(0xFFFFD700, 0xFF33FF55, (durRatio - 0.5f) * 2.0f);
                    } else {
                        durColor = ColorUtil.interpolate(0xFFFF3333, 0xFFFFD700, durRatio * 2.0f);
                    }
                    int pctColor = ColorUtil.withAlpha(durColor, (int) (255 * alpha));

                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(slotX + slotW * 0.5f, equipY + 6.0f);
                    context.getMatrices().scale(textScale, textScale);
                    context.drawTextWithShadow(mc.textRenderer, pctStr, -textW / 2, -textH / 2, pctColor);
                    context.getMatrices().popMatrix();
                }
            } else if (stack.getCount() > 1) {
                String countStr = String.valueOf(stack.getCount());
                float countScale = 0.55f;
                int cw = mc.textRenderer.getWidth(countStr);
                int ch = mc.textRenderer.fontHeight;
                int countColor = ColorUtil.rgba(255, 255, 255, (int) (245 * alpha));

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(slotX + slotW - 1.0f - (cw * countScale), equipY + slotH - 1.5f - (ch * countScale));
                context.getMatrices().scale(countScale, countScale);
                context.drawTextWithShadow(mc.textRenderer, countStr, 0, 0, countColor);
                context.getMatrices().popMatrix();
            }
        }

        float barX = textX;
        float barY = y + 33.0f;
        float barW = contentW;
        float barH = 4.0f;
        float barRadius = 2.0f;

        int trackColor = ColorUtil.rgba(255, 255, 255, (int) (38 * alpha));
        Draw2DProcessor.INSTANCE.drawRect(mat, barX, barY, barW, barH, barRadius, trackColor);

        int themeCol1 = Theme.getGradient1();
        int themeCol2 = Theme.getGradient2();
        int themeFill1 = ColorUtil.withAlpha(themeCol1, (int) (255 * alpha));
        int themeFill2 = ColorUtil.withAlpha(themeCol2, (int) (255 * alpha));

        float totalMax = maxHp + Math.max(0.0f, visualAbsorption);
        float baseRatio = Math.clamp(visualHp / totalMax, 0.0f, 1.0f);
        float baseW = barW * baseRatio;

        float ghostRatio = Math.clamp(ghostHp / totalMax, 0.0f, 1.0f);
        float ghostW = barW * ghostRatio;

        if (ghostBar.isEnabled() && ghostW > 1.0f) {
            int darkCol1 = ColorUtil.lerp(themeCol1, 0xFF000000, 0.65f);
            int darkCol2 = ColorUtil.lerp(themeCol2, 0xFF000000, 0.65f);
            int ghostFill1 = ColorUtil.withAlpha(darkCol1, (int) (230 * alpha));
            int ghostFill2 = ColorUtil.withAlpha(darkCol2, (int) (230 * alpha));
            Draw2DProcessor.INSTANCE.drawGradient(mat, barX, barY, ghostW, barH, barRadius, ghostFill1, ghostFill2, ghostFill1, ghostFill2);
        }

        if (baseW > 1.0f) {
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
            final float deltaSec = dt;
            particles.removeIf(p -> !p.update(deltaSec));
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

    private void renderSplitCard(DrawContext context, LivingEntity target, float x, float y, float w, float h,
                                 float scale, float alpha, float maxHp, float absorption, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Matrix3x2f mat = null;
        boolean customScale = Math.abs(scale - 1.0f) > 0.001f;
        if (customScale) {
            mat = this.cardMat.identity();
            mat.translate(x, y);
            mat.scale(scale, scale);
            mat.translate(-x, -y);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-x, -y);
        }

        updateEatState(target, dt, mc);

        float op = opacity.getValueFloat() / 100.0f;
        int bgAlpha = (int) (208.0f * alpha * op);
        int borderAlpha = (int) (Math.max(18.0f, 36.0f * op) * alpha);
        int shadowAlpha = (int) (72.0f * alpha * op);

        int bgColor = ColorUtil.rgba(14, 15, 19, bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, borderAlpha);
        int shadowColor = ColorUtil.rgba(0, 0, 0, shadowAlpha);

        float headBoxSize = h;
        float headBoxR = 7.0f;
        float gap = 5.0f;
        float infoX = x + headBoxSize + gap;
        float infoW = w - headBoxSize - gap;
        float infoR = 7.5f;

        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, headBoxSize, headBoxSize, headBoxR, shadowColor, 5.5f);
        Draw2DProcessor.INSTANCE.drawPanelBlur(mat, x, y, headBoxSize, headBoxSize, headBoxR, alpha);
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, headBoxSize, headBoxSize, headBoxR, bgColor);
        Draw2DProcessor.INSTANCE.drawOutline(mat, x, y, headBoxSize, headBoxSize, headBoxR, 0.8f, borderColor);

        Draw2DProcessor.INSTANCE.drawGlow(mat, infoX, y, infoW, h, infoR, shadowColor, 5.5f);
        Draw2DProcessor.INSTANCE.drawPanelBlur(mat, infoX, y, infoW, h, infoR, alpha);
        Draw2DProcessor.INSTANCE.drawRect(mat, infoX, y, infoW, h, infoR, bgColor);
        Draw2DProcessor.INSTANCE.drawOutline(mat, infoX, y, infoW, h, infoR, 0.8f, borderColor);

        if (hudThemeGradient.isEnabled()) {
            Interface iface = Interface.getInstance();
            int gradC = iface != null ? iface.computeHudGradientColor(hudGradientStrength.getValueFloat(), alpha * op)
                    : ColorUtil.rgba(168, 85, 247, (int) ((hudGradientStrength.getValueFloat() / 100.0f) * 42.0f * alpha * op));
            Draw2DProcessor.INSTANCE.drawGradient(mat, infoX, y, infoW, h, infoR, gradC, 0x00000000, 0x00000000, gradC);
        }

        float headPad = 2.5f;
        float headX = x + headPad;
        float headY = y + headPad;
        float headSize = headBoxSize - headPad * 2.0f;
        float headRadius = 5.0f;

        int headBgColor = ColorUtil.rgba(24, 24, 30, (int) (235 * alpha));
        if (target.hurtTime > 0) {
            float hurtFactor = target.hurtTime / 10.0f;
            headBgColor = ColorUtil.interpolate(headBgColor, ColorUtil.rgba(220, 34, 34, (int) (240 * alpha)), hurtFactor);
        }
        Draw2DProcessor.INSTANCE.drawRect(mat, headX, headY, headSize, headSize, headRadius, headBgColor);

        Identifier skinTex = getTargetSkin(target);
        if (skinTex != null) {
            if (smoothEatProgress > 0.01f) {
                renderBlurredHead(mat, skinTex, target, headX, headY, headSize, headRadius, alpha, smoothEatProgress);
            } else {
                Draw2DProcessor.INSTANCE.a(mat, skinTex, target, headX, headY, headSize, headSize, headRadius, alpha);
            }
        }
        if (smoothEatProgress > 0.01f && !lastEatStack.isEmpty()) {
            DrawBatcher.flushPending();
            float itemScale = 0.78f * (0.85f + 0.15f * smoothEatProgress);
            float itemCx = headX + headSize * 0.5f;
            float itemCy = headY + 8.5f;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(itemCx, itemCy);
            context.getMatrices().scale(itemScale, itemScale);
            context.getMatrices().translate(-8.0f, -8.0f);
            context.drawItem(lastEatStack, 0, 0);
            context.getMatrices().popMatrix();

            String timerStr = String.format(Locale.US, "%.2f", Math.max(0.0f, eatTimerSeconds));
            float tw = Fonts.b.a(timerStr, 6.8f);
            float pillW = tw + 4.0f;
            float pillH = 7.0f;
            float pillX = headX + (headSize - pillW) * 0.5f;
            float pillY = headY + headSize - 8.5f;
            Draw2DProcessor.INSTANCE.drawRect(mat, pillX, pillY, pillW, pillH, 2.5f,
                    ColorUtil.rgba(8, 8, 12, (int) (190 * smoothEatProgress * alpha)));
            Fonts.b.a(mat, timerStr, headX + (headSize - tw) * 0.5f, pillY + 0.4f, 6.8f,
                    ColorUtil.rgba(255, 255, 255, (int) (245 * smoothEatProgress * alpha)));
        }

        float padX = 7.5f;
        float topY = y + 4.5f;
        float pillH = 11.0f;

        int curHp = (int) Math.ceil(target.getHealth());
        if (lastHpTarget != target) {
            lastHpTarget = target;
            hpRolling.reset(curHp);
        } else {
            hpRolling.update(curHp);
        }

        float hpRatio = MathHelper.clamp(visualHp / Math.max(1.0f, maxHp), 0.0f, 1.0f);
        int pillBaseRgb;
        int pillEdgeRgb;
        if (hpRatio >= 0.55f) {
            float k = (hpRatio - 0.55f) / 0.45f;
            pillBaseRgb = ColorUtil.interpolate(0xFF9A7B22, 0xFF288C46, k);
            pillEdgeRgb = ColorUtil.interpolate(0xFFD1AE3D, 0xFF44BD67, k);
        } else {
            float k = hpRatio / 0.55f;
            pillBaseRgb = ColorUtil.interpolate(0xFFA82B36, 0xFF9A7B22, k);
            pillEdgeRgb = ColorUtil.interpolate(0xFFE0525E, 0xFFD1AE3D, k);
        }

        String hpStr = String.valueOf(curHp);
        String absStr = absorption > 0.0f ? "+" + (int) Math.ceil(absorption) : "";
        float hpFont = 7.9f;
        float hpNumW = Fonts.b.a(hpStr, hpFont);
        float absW = absorption > 0.0f ? Fonts.b.a(absStr, 6.8f) + 1.5f : 0.0f;
        float heartSize = 7.4f;
        float hpPillW = 5.0f + hpNumW + absW + 2.6f + heartSize + 4.2f;
        float hpPillX = infoX + infoW - padX - hpPillW;

        int pillBg = ColorUtil.withAlpha(pillBaseRgb, (int) (178 * alpha));
        int pillBorder = ColorUtil.withAlpha(pillEdgeRgb, (int) (220 * alpha));
        Draw2DProcessor.INSTANCE.drawRect(mat, hpPillX, topY, hpPillW, pillH, 3.6f, pillBg);
        Draw2DProcessor.INSTANCE.drawOutline(mat, hpPillX, topY, hpPillW, pillH, 3.6f, 0.7f, pillBorder);

        float hpTextX = hpPillX + 4.8f;
        float hpTextY = Fonts.b.a(hpFont, topY + pillH * 0.5f);
        int hpTextColor = ColorUtil.rgba(255, 255, 255, (int) (252 * alpha));
        hpRolling.render(mat, hpTextX, hpTextY, hpFont, hpTextColor, 1.0f);

        if (absorption > 0.0f) {
            Fonts.b.a(mat, absStr, hpTextX + hpNumW + 1.5f, Fonts.b.a(6.8f, topY + pillH * 0.5f),
                    6.8f, ColorUtil.rgba(255, 224, 96, (int) (250 * alpha)));
        }

        float heartX = hpPillX + hpPillW - 4.0f - heartSize;
        float heartY = topY + (pillH - heartSize) * 0.5f + 0.1f;
        Draw2DProcessor.INSTANCE.a(mat, HEART_OUTLINE_ICON, heartX, heartY, heartSize, heartSize, 0.0f,
                ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));

        String name = target.getName().getString();
        if (egg.client.module.misc.Globals.isEggUser(target)) {
            name = "🥚 " + name;
        }
        float nameX = infoX + padX;
        float availNameW = Math.max(20.0f, hpPillX - nameX - 5.0f);
        String displayName = name;
        float nameFont = 8.9f;
        if (Fonts.b.a(displayName, nameFont) > availNameW) {
            while (displayName.length() > 3 && Fonts.b.a(displayName + "...", nameFont) > availNameW) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "...";
        }
        Fonts.b.a(mat, displayName, nameX, Fonts.b.a(nameFont, topY + pillH * 0.5f),
                nameFont, ColorUtil.rgba(246, 248, 252, (int) (255 * alpha)));

        ItemStack[] equipStacks = new ItemStack[]{
                target.getEquippedStack(EquipmentSlot.HEAD),
                target.getEquippedStack(EquipmentSlot.CHEST),
                target.getEquippedStack(EquipmentSlot.LEGS),
                target.getEquippedStack(EquipmentSlot.FEET),
                target.getMainHandStack(),
                target.getOffHandStack()
        };

        float rowY = y + 18.8f;
        float slotSize = 12.0f;
        float armorGap = 2.6f;
        float groupGap = 8.0f;

        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipStacks[i];
            float slotX = computeSplitSlotX(infoX + padX, i, slotSize, armorGap, groupGap);
            boolean empty = stack == null || stack.isEmpty();
            if (empty) {
                float crossSize = 6.8f;
                float cx = slotX + (slotSize - crossSize) * 0.5f;
                float cy = rowY + (slotSize - crossSize) * 0.5f;
                Draw2DProcessor.INSTANCE.a(mat, CROSS_ICON, cx, cy, crossSize, crossSize, 0.0f,
                        ColorUtil.rgba(255, 255, 255, (int) (58 * alpha)));
            } else if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                int maxDmg = stack.getMaxDamage();
                int curDmg = stack.getDamage();
                float durRatio = Math.clamp((float) Math.max(0, maxDmg - curDmg) / (float) maxDmg, 0.0f, 1.0f);
                if (durRatio < 1.0f) {
                    int durColor = durRatio > 0.5f
                            ? ColorUtil.interpolate(0xFFFFD700, 0xFF33FF55, (durRatio - 0.5f) * 2.0f)
                            : ColorUtil.interpolate(0xFFFF3333, 0xFFFFD700, durRatio * 2.0f);
                    float barW = slotSize - 2.0f;
                    Draw2DProcessor.INSTANCE.drawRect(mat, slotX + 1.0f, rowY + slotSize - 1.2f, barW, 1.0f, 0.4f,
                            ColorUtil.rgba(0, 0, 0, (int) (175 * alpha)));
                    Draw2DProcessor.INSTANCE.drawRect(mat, slotX + 1.0f, rowY + slotSize - 1.2f,
                            Math.max(1.0f, barW * durRatio), 1.0f, 0.4f, ColorUtil.withAlpha(durColor, (int) (235 * alpha)));
                }
            }
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
                spawnDamageParticles(hpPillX + hpPillW * 0.5f, topY + pillH * 0.5f, Math.max(diff, 1.0f));
            }
            lastKnownHp = curActualHp;
            lastHurtTime = curHurt;
        }

        if (!particles.isEmpty()) {
            final float deltaSec = dt;
            particles.removeIf(p -> !p.update(deltaSec));
            if (hitParticles.get()) {
                for (HitParticle p : particles) {
                    p.render(mat, alpha);
                }
            } else {
                particles.clear();
            }
        }

        DrawBatcher.flushPending();

        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipStacks[i];
            if (stack == null || stack.isEmpty()) continue;
            float slotX = computeSplitSlotX(infoX + padX, i, slotSize, armorGap, groupGap);
            float itemCx = slotX + slotSize * 0.5f;
            float itemCy = rowY + slotSize * 0.5f - 0.3f;
            float itemScale = 0.62f;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(itemCx, itemCy);
            context.getMatrices().scale(itemScale, itemScale);
            context.getMatrices().translate(-8.0f, -8.0f);
            context.drawItem(stack, 0, 0);
            context.getMatrices().popMatrix();

            if (stack.isDamageable() && stack.getMaxDamage() > 0 && showDurabilityPercent.isEnabled()) {
                float durRatio = Math.clamp((float) Math.max(0, stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage(), 0.0f, 1.0f);
                if (durRatio < 1.0f && mc != null && mc.textRenderer != null) {
                    String pctStr = Math.round(durRatio * 100.0f) + "%";
                    int durColor = durRatio > 0.5f
                            ? ColorUtil.interpolate(0xFFFFD700, 0xFF33FF55, (durRatio - 0.5f) * 2.0f)
                            : ColorUtil.interpolate(0xFFFF3333, 0xFFFFD700, durRatio * 2.0f);
                    float textScale = 0.52f;
                    int textW = mc.textRenderer.getWidth(pctStr);
                    int textH = mc.textRenderer.fontHeight;
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(itemCx, itemCy);
                    context.getMatrices().scale(textScale, textScale);
                    context.drawTextWithShadow(mc.textRenderer, pctStr, -textW / 2, -textH / 2, ColorUtil.withAlpha(durColor, (int) (255 * alpha)));
                    context.getMatrices().popMatrix();
                }
            } else if (stack.getCount() > 1 && mc != null && mc.textRenderer != null) {
                String countStr = String.valueOf(stack.getCount());
                float countScale = 0.52f;
                int cw = mc.textRenderer.getWidth(countStr);
                int ch = mc.textRenderer.fontHeight;
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(slotX + slotSize - 0.5f - (cw * countScale), rowY + slotSize - 1.0f - (ch * countScale));
                context.getMatrices().scale(countScale, countScale);
                context.drawTextWithShadow(mc.textRenderer, countStr, 0, 0, ColorUtil.rgba(255, 255, 255, (int) (245 * alpha)));
                context.getMatrices().popMatrix();
            }
        }

        if (customScale) {
            context.getMatrices().popMatrix();
        }
    }

    private static float computeSplitSlotX(float startX, int index, float slotSize, float armorGap, float groupGap) {
        if (index < 4) {
            return startX + index * (slotSize + armorGap);
        }
        return startX + 4 * slotSize + 3 * armorGap + groupGap + (index - 4) * (slotSize + armorGap);
    }

    private void updateEatState(LivingEntity target, float dt, MinecraftClient mc) {
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
            } else if (eatTimerSeconds <= 0.0f) {
                int maxTime = lastEatStack.getMaxUseTime(target);
                eatTimerSeconds = (maxTime > 0 ? maxTime : 32) * 0.05f;
            } else {
                eatTimerSeconds = Math.max(0.0f, eatTimerSeconds - dt);
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
        if (this.currentTarget != null && (EntityUtil.isNakedInvisible(this.currentTarget) || (onlyPlayers.get() && !(this.currentTarget instanceof PlayerEntity)))) {
            this.currentTarget = null;
            this.lastDamageTarget = null;
            this.lastKnownHp = -1.0f;
            this.targetTimer = 0.0f;
        }
    }

    private LivingEntity findHoveredTarget(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (living != mc.player && living.isAlive() && !EntityUtil.isNakedInvisible(living) && (!onlyPlayers.get() || living instanceof PlayerEntity)) {
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
            if (e instanceof LivingEntity living && e != mc.player && living.isAlive() && !EntityUtil.isNakedInvisible(living)) {
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

        var bounds = new HudSnapHelper.HudElementBounds("target", lastRenderX, lastRenderY, lastRenderW, lastRenderH);
        int scalePercent = Math.round(hudScale.getValueFloat() * 100.0f);
        if (dragging && !mode.is("Экран")) dragging = false;
        if (dragging) {
            mouseDragged(mouseX, mouseY);
            HudSnapHelper.renderDragOverlay(bounds, currentSnap, screenW, screenH, scalePercent);
        } else {
            HudSnapHelper.renderHoverOverlay(bounds, screenW, screenH, mouseX, mouseY, scalePercent);
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
                contextMenu.openSmart("target", lastRenderX, lastRenderY, lastRenderW, lastRenderH, (float) mx, (float) my, hudScale.getValueFloat());
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
