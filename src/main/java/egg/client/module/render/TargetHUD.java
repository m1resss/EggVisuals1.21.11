package egg.client.module.render;

import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
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
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * TargetHUD - Элегантная информационная карточка цели.
 * Поддерживает 2 режима: классический HUD на экране и 3D привязку в центр противника в мире (скрыта через стены).
 */
public class TargetHUD extends Module {

    private static TargetHUD instance;

    private static final Identifier HEART_ICON = Identifier.of("egg", "textures/hud/heart.png");

    private final ModeSetting mode = new ModeSetting("Режим", "Экран", "Расположение Target HUD", "Экран", "В мире");
    private final SliderSetting hudScale = new SliderSetting("Масштаб", 1.0, 0.5, 2.0, 0.05, "Масштаб Target HUD");
    private final SliderSetting targetTimeout = new SliderSetting("Таймаут цели", 3.0, 0.5, 8.0, 0.5, "Сколько секунд держать цель после отвода прицела");
    private final BooleanSetting showFps = new BooleanSetting("Показывать FPS", true, "Отображать метку FPS на карточке");
    private final BooleanSetting showDistance = new BooleanSetting("Дистанция", true, "Отображать дистанцию до цели");

    // Internal coordinates for mouse dragging (no ugly sliders in GUI)
    private float posX = 0.52f;
    private float posY = 0.68f;

    private LivingEntity currentTarget = null;
    private float targetTimer = 0.0f;
    private float animAlpha = 0.0f;
    private float visualHp = 20.0f;
    private float visualAbsorption = 0.0f;
    private long lastFrameNanos = 0L;

    // Smooth world tracking to completely eliminate jitter
    private float smoothScreenX = 0.0f;
    private float smoothScreenY = 0.0f;
    private boolean smoothInit = false;

    // Mouse dragging state
    private boolean dragging = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;
    private float lastRenderX = 0.0f;
    private float lastRenderY = 0.0f;
    private float lastRenderW = 152.0f;
    private float lastRenderH = 44.0f;

    public TargetHUD() {
        super("Target HUD", "Информационная карточка цели на экране или в центре противника", Category.ESP);
        instance = this;
        // Clean settings: no coordinate sliders!
        addSettings(mode, hudScale, targetTimeout);
    }

    public static TargetHUD getInstance() {
        return instance;
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

        // 1. Target acquisition
        updateTargetAcquisition(mc, dt);

        boolean inGui = mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof ChatScreen;
        LivingEntity renderTarget = currentTarget;

        if (renderTarget == null || !renderTarget.isAlive()) {
            if (inGui) {
                renderTarget = mc.player; // Preview in GUI / Chat
            }
        }

        boolean hasTarget = renderTarget != null && renderTarget.isAlive();

        // 2. Strict line-of-sight check in World Mode: "через стены не видно ее"
        boolean isWorldMode = mode.is("В мире");
        if (hasTarget && isWorldMode && renderTarget != mc.player) {
            if (isOccludedByWall(mc, renderTarget)) {
                hasTarget = false;
            }
        }

        // Animate alpha
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

        // Health animations
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

        if (isWorldMode && renderTarget != mc.player) {
            // Position exactly in the CENTER of the enemy body with sub-frame interpolation
            double wx = MathHelper.lerp(tickDelta, renderTarget.lastRenderX, renderTarget.getX());
            double wy = MathHelper.lerp(tickDelta, renderTarget.lastRenderY, renderTarget.getY()) + renderTarget.getHeight() * 0.5;
            double wz = MathHelper.lerp(tickDelta, renderTarget.lastRenderZ, renderTarget.getZ());

            Vector2f screenPos = ProjectUtil.toScreen(wx, wy, wz, tickDelta);
            if (!ProjectUtil.isOnScreen(screenPos)) return;

            // Distance scaling
            double dist = mc.player.distanceTo(renderTarget);
            float distScale = (float) Math.clamp(1.0 - (dist - 4.0) * 0.015, 0.72, 1.25);
            scale *= distScale;

            // Smooth interpolation to completely eliminate camera/movement jitter
            if (!smoothInit || Math.abs(smoothScreenX - screenPos.x) > 120.0f || Math.abs(smoothScreenY - screenPos.y) > 120.0f) {
                smoothScreenX = screenPos.x;
                smoothScreenY = screenPos.y;
                smoothInit = true;
            } else {
                float smoothSpeed = Math.min(1.0f, dt * 45.0f);
                smoothScreenX = MathHelper.lerp(smoothSpeed, smoothScreenX, screenPos.x);
                smoothScreenY = MathHelper.lerp(smoothSpeed, smoothScreenY, screenPos.y);
            }

            // Center card horizontally and vertically on the enemy
            cardX = smoothScreenX - (cardW * scale) / 2.0f;
            cardY = smoothScreenY - (cardH * scale) / 2.0f;
        } else {
            // Screen HUD Mode
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float rawX = posX * (screenW - cardW * scale);
            float rawY = posY * (screenH - cardH * scale);
            cardX = Math.clamp(rawX, 4.0f, screenW - cardW * scale - 4.0f);
            cardY = Math.clamp(rawY, 4.0f, screenH - cardH * scale - 4.0f);
        }

        this.lastRenderX = cardX;
        this.lastRenderY = cardY;
        this.lastRenderW = cardW * scale;
        this.lastRenderH = cardH * scale;

        renderCard(context, renderTarget, cardX, cardY, cardW, cardH, cardRadius, scale, animAlpha, maxHealth, actualAbsorption);
    }

    private void renderCard(DrawContext context, LivingEntity target, float x, float y, float w, float h,
                            float radius, float scale, float alpha, float maxHp, float absorption) {
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

        int bgAlpha = (int) (225 * alpha);
        int borderAlpha = (int) (30 * alpha);
        int shadowAlpha = (int) (65 * alpha);

        int bgColor = ColorUtil.rgba(18, 18, 22, bgAlpha);
        int borderColor = ColorUtil.rgba(255, 255, 255, borderAlpha);
        int shadowColor = ColorUtil.rgba(0, 0, 0, shadowAlpha);

        // 1. Shadow glow & pill card
        Draw2DProcessor.INSTANCE.drawGlow(mat, x, y, w, h, radius, shadowColor, 6.0f);
        Draw2DProcessor.INSTANCE.drawRect(mat, x, y, w, h, radius, bgColor);
        // Draw2DProcessor.INSTANCE.drawOutline(mat, x, y, w, h, radius, 0.8f, borderColor);

        // 2. Avatar / Face on Left with Damage Flash
        float headSize = 30.0f;
        float headX = x + 7.0f;
        float headY = y + 7.0f;
        float headRadius = 7.0f;

        // When receiving damage: head background and border flash red!
        int headBgColor = ColorUtil.rgba(30, 30, 36, (int) (240 * alpha));
        if (target.hurtTime > 0) {
            float hurtFactor = target.hurtTime / 10.0f;
            headBgColor = ColorUtil.interpolate(headBgColor, ColorUtil.rgba(220, 30, 30, (int) (240 * alpha)), hurtFactor);
        }
        Draw2DProcessor.INSTANCE.drawRect(mat, headX, headY, headSize, headSize, headRadius, headBgColor);

        Identifier skinTex = getTargetSkin(target);
        if (skinTex != null) {
            // Draw skin face (Draw2DProcessor tints it red automatically on damage)
            Draw2DProcessor.INSTANCE.a(mat, skinTex, target, headX, headY, headSize, headSize, headRadius, alpha);
        }

        // 3. Name & Info
        float textX = x + 43.0f;
        float nameY = y + 5.5f;
        String name = target.getName().getString();
        int maxNameLen = 13;
        if (name.length() > maxNameLen) {
            name = name.substring(0, maxNameLen) + "...";
        }

        int nameColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Fonts.b.a(mat, name, textX, nameY, 11.0f, nameColor);

        // 4. Glowing Heart Icon & HP Value
        float hpY = y + 20.0f;
        float heartSize = 10.0f;
        int heartGlowColor = ColorUtil.rgba(255, 30, 80, (int) (160 * alpha));
        Draw2DProcessor.INSTANCE.drawGlow(mat, textX - 2.0f, hpY - 1.5f, heartSize + 4.0f, heartSize + 4.0f, (heartSize + 4.0f) / 2.0f, heartGlowColor, 4.0f);
        int heartColor = ColorUtil.rgba(255, 255, 255, (int) (255 * alpha));
        Draw2DProcessor.INSTANCE.a(mat, HEART_ICON, textX, hpY + 0.5f, heartSize, heartSize, 0.0f, heartColor);

        // HP value text (e.g. "8" or "14 + 4" with golden hearts)
        float hpTextX = textX + heartSize + 4.0f;
        String hpValueStr = String.format("%.0f", Math.ceil(target.getHealth()));
        int hpTextColor = ColorUtil.rgba(255, 255, 255, (int) (230 * alpha));
        Fonts.b.a(mat, hpValueStr, hpTextX, hpY, 9.5f, hpTextColor);

        float nextTextX = hpTextX + Fonts.b.a(hpValueStr, 9.5f);
        if (absorption > 0.0f) {
            String absStr = " +" + String.format("%.0f", Math.ceil(absorption));
            int absTextColor = ColorUtil.rgba(255, 215, 0, (int) (255 * alpha)); // Gold
            Fonts.b.a(mat, absStr, nextTextX, hpY, 9.5f, absTextColor);
        }

        // 5. Health Bar (Theme Color) + Golden Absorption Bar
        float barX = textX;
        float barY = y + 32.0f;
        float barW = w - 43.0f - 10.0f; // 99.0f
        float barH = 5.0f;
        float barRadius = 2.5f;

        // Background track
        int trackColor = ColorUtil.rgba(255, 255, 255, (int) (38 * alpha));
        Draw2DProcessor.INSTANCE.drawRect(mat, barX, barY, barW, barH, barRadius, trackColor);

        // Health line depends on Theme Accent color!
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

        // Golden hearts: Yellow/gold health bar segment!
        if (visualAbsorption > 0.01f) {
            float absRatio = Math.clamp(visualAbsorption / totalMax, 0.0f, 1.0f - baseRatio);
            float absW = Math.max(3.0f, barW * absRatio);
            float absX = barX + baseW;

            int goldCol1 = ColorUtil.rgba(255, 215, 0, (int) (255 * alpha));
            int goldCol2 = ColorUtil.rgba(255, 179, 0, (int) (255 * alpha));
            Draw2DProcessor.INSTANCE.drawGradient(mat, absX, barY, absW, barH, barRadius, goldCol1, goldCol2, goldCol1, goldCol2);
        }

        if (customScale) {
            context.getMatrices().popMatrix();
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
                }
            } else {
                this.currentTarget = null;
            }
        }
    }

    private LivingEntity findHoveredTarget(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            if (living != mc.player && living.isAlive()) {
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

    // Chat Screen Overlay & Dragging
    public void renderChatOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled() || !mode.is("Экран")) return;
        if (dragging) {
            mouseDragged(mouseX, mouseY);
            Draw2DProcessor.INSTANCE.drawOutline(null, lastRenderX - 2.0f, lastRenderY - 2.0f, lastRenderW + 4.0f, lastRenderH + 4.0f, 13.0f, 1.2f, Theme.getAccent());
        }
    }

    public boolean onChatMouseClicked(double mx, double my, int button) {
        return mouseClicked(mx, my, button);
    }

    public boolean onChatMouseReleased(double mx, double my, int button) {
        mouseReleased(mx, my, button);
        return false;
    }

    public boolean onChatMouseDragged(double mx, double my, int button) {
        mouseDragged(mx, my);
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || !mode.is("Экран")) return false;
        if (button == 0 && mx >= lastRenderX && mx <= lastRenderX + lastRenderW
                && my >= lastRenderY && my <= lastRenderY + lastRenderH) {
            this.dragging = true;
            this.dragOffsetX = (float) mx - lastRenderX;
            this.dragOffsetY = (float) my - lastRenderY;
            return true;
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            ConfigManager.save();
        }
    }

    public void mouseDragged(double mx, double my) {
        if (this.dragging && mode.is("Экран")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getWindow() == null) return;
            float screenW = mc.getWindow().getScaledWidth();
            float screenH = mc.getWindow().getScaledHeight();

            float newX = ((float) mx - dragOffsetX) / Math.max(1.0f, screenW - lastRenderW);
            float newY = ((float) my - dragOffsetY) / Math.max(1.0f, screenH - lastRenderH);
            this.posX = Math.clamp(newX, 0.0f, 1.0f);
            this.posY = Math.clamp(newY, 0.0f, 1.0f);
        }
    }
}
