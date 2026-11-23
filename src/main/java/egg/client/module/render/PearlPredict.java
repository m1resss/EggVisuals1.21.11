package egg.client.module.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.EggClient;
import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.hud.HudContextMenu;
import egg.client.render.hud.HudSnapHelper;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.world.PearlCameraRenderer;
import egg.client.render.world.PearlTrajectory;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class PearlPredict extends Module {
    private static final String ID = "pearl_predict";
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final Identifier PEARL_ICON = Identifier.ofVanilla("textures/item/ender_pearl.png");
    private static final float RADIUS = 7.0f;
    private static final float WIDTH = 146.0f;
    private static final float INSET = 4.0f;
    private static final float HEADER_HEIGHT = 16.0f;
    private static final float CAMERA_WIDTH = WIDTH - 2 * INSET;
    private static final float CAMERA_HEIGHT = CAMERA_WIDTH * PearlCameraRenderer.HEIGHT / PearlCameraRenderer.WIDTH;
    private static final float HEIGHT = HEADER_HEIGHT + CAMERA_HEIGHT + INSET;
    private static final double SCALE_STEP = 0.05;

    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0, 0.65, 1.5, SCALE_STEP, "Размер окна камеры");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность", 65, 10, 100, 5, "Непрозрачность фона окна (%)");
    private final BooleanSetting hudBlur = new BooleanSetting("Размытие фона", true, "Размывать фон окна при включённом размытии HUD");
    private final BooleanSetting hudGradient = new BooleanSetting("Градиент HUD", true, "Использовать градиент темы HUD на фоне окна");
    private final SliderSetting gradientStrength = new SliderSetting("Сила градиента", 60, 5, 100, 5, "Интенсивность градиента");
    private final BooleanSetting showMarker = new BooleanSetting("Метка попадания", true, "Показывать предполагаемую точку попадания");
    private final SliderSetting posX = new SliderSetting("X", 0.97, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.18, 0.0, 1.0, 0.001, "Позиция Y");
    private final PearlCameraRenderer camera = new PearlCameraRenderer();
    private final HudContextMenu contextMenu = new HudContextMenu("PearlPredict");

    private ClientWorld world;
    private EnderPearlEntity pearl;
    private int lastTrackedTick = -1;
    private Vector2f pearlMarker;
    private Vector2f marker;
    private boolean flight;
    private boolean available;
    private boolean failed;
    private boolean hasImpact;
    private HudSnapHelper.HudElementBounds bounds;
    private boolean dragging;
    private double dragX;
    private double dragY;

    public PearlPredict() {
        super("PearlPredict", "Показывает место попадания и полёт жемчуга края в окне камеры", Category.ESP);
        posX.setVisible(() -> false);
        posY.setVisible(() -> false);
        gradientStrength.setVisible(hudGradient::isEnabled);
        contextMenu.addToggle("Размытие фона", hudBlur::isEnabled, hudBlur::toggle);
        contextMenu.addToggle("Градиент HUD", hudGradient::isEnabled, hudGradient::toggle);
        contextMenu.addToggle("Метка попадания", showMarker::isEnabled, showMarker::toggle);
        addSettings(scale, opacity, hudBlur, hudGradient, gradientStrength, showMarker, posX, posY);
    }

    @Override
    public void onDisable() {
        camera.close();
        contextMenu.close();
        world = null;
        pearl = null;
        lastTrackedTick = -1;
        bounds = null;
        dragging = false;
        failed = false;
    }

    public void capture(RenderTickCounter ticks, GpuBufferSlice fog, Vector4f fogColor, PearlCameraRenderer.TerrainPass terrain) {
        available = false;
        hasImpact = false;
        marker = null;
        pearlMarker = null;
        flight = false;
        if (mc.world != world) {
            pearl = null;
            lastTrackedTick = -1;
            world = mc.world;
        }
        boolean editing = mc.currentScreen instanceof ChatScreen;
        if (!editing) {
            if (contextMenu.isOpen()) contextMenu.close();
            finishDrag(0);
        }
        if (world == null || mc.player == null || mc.options.hudHidden || mc.getOverlay() != null
                || (mc.currentScreen != null && !editing)) {
            bounds = null;
            dragging = false;
            return;
        }

        boolean holding = isHoldingPearl();
        if (pearl != null && (!pearl.isAlive() || pearl.getEntityWorld() != world)) pearl = null;
        float tickDelta = ticks.getTickProgress(false);
        if (mc.player.age != lastTrackedTick) {
            lastTrackedTick = mc.player.age;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof EnderPearlEntity candidate && candidate.isAlive()
                        && candidate.getOwner() == mc.player && (pearl == null || candidate.age < pearl.age)) {
                    pearl = candidate;
                }
            }
        }

        flight = pearl != null;
        if (!flight && !holding) {
            if (!editing) bounds = null;
            return;
        }

        Entity source = flight ? pearl : mc.player;
        Vec3d start = flight ? pearl.getLerpedPos(tickDelta) : mc.player.getCameraPosVec(tickDelta).add(0, -0.1, 0);
        Vec3d velocity = flight ? pearl.getVelocity() : mc.player.getRotationVec(tickDelta).multiply(1.5);
        if (!flight) {
            Vec3d movement = mc.player.getVelocity();
            velocity = velocity.add(movement.x, mc.player.isOnGround() ? 0 : movement.y, movement.z);
        }
        Vec3d simulationStart = flight ? pearl.getEntityPos() : start;
        PearlTrajectory trajectory = PearlTrajectory.predict(world, source, mc.player, simulationStart, velocity);
        Vec3d impact = trajectory.impact();
        hasImpact = impact != null;
        if (failed || (!flight && impact == null)) return;

        Vec3d target = flight ? start.add(0, 0.13, 0) : impact;
        Vec3d anchor = flight ? target : trajectory.pointBeforeImpact(0.22);
        Vec3d backwards = velocity.normalize().multiply(-1);
        Vec3d cameraPosition = flight ? target.add(backwards.multiply(4.0)).add(0, 1.7, 0)
                : trajectory.pointBeforeImpact(6.0).add(0, 2.4, 0);
        HitResult obstacle = world.raycast(new RaycastContext(anchor, cameraPosition,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, source));
        if (obstacle.getType() != HitResult.Type.MISS) {
            Vec3d segment = obstacle.getPos().subtract(anchor);
            cameraPosition = anchor.add(segment.multiply(Math.max(0, 1 - 0.23 / Math.max(0.001, segment.length()))));
        }
        if (cameraPosition.squaredDistanceTo(target) < 0.04) {
            cameraPosition = anchor.add(backwards.multiply(0.3));
        }
        Vec3d focus = target;
        if (flight && impact != null) {
            Vec3d lead = impact.subtract(target);
            if (lead.lengthSquared() > 0.01) focus = target.add(lead.normalize().multiply(Math.min(2.0, lead.length() * 0.45)));
        }

        try {
            available = camera.render(new PearlCameraRenderer.View(cameraPosition, focus), ticks, fog, fogColor, terrain);
            if (available && hasImpact && showMarker.isEnabled()) marker = camera.project(impact);
            if (available && flight) pearlMarker = camera.project(target);
        } catch (RuntimeException exception) {
            EggClient.LOGGER.warn("PearlPredict camera capture failed", exception);
            failed = true;
        }
    }

    public HudSnapHelper.HudElementBounds getBounds() {
        return bounds;
    }

    public void render(DrawContext context, boolean editing, int mouseX, int mouseY) {
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
        if (!editing && !flight && !isHoldingPearl()) {
            bounds = null;
            return;
        }

        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();
        float s = Math.min(scale.getValueFloat(), Math.min(screenWidth / (WIDTH + 12), screenHeight / (HEIGHT + 12)));
        float width = WIDTH * s;
        float height = HEIGHT * s;
        float maxX = Math.max(1, screenWidth - width);
        float maxY = Math.max(1, screenHeight - height);
        HudSnapHelper.SnapResult snap = null;
        if (editing && dragging) {
            snap = HudSnapHelper.calculateSnap(ID, (float) (mouseX - dragX), (float) (mouseY - dragY), width, height, screenWidth, screenHeight);
            posX.setValue(Math.clamp(snap.snappedX / maxX, 0, 1));
            posY.setValue(Math.clamp(snap.snappedY / maxY, 0, 1));
        }
        float x = maxX * posX.getValueFloat();
        float y = maxY * posY.getValueFloat();
        bounds = new HudSnapHelper.HudElementBounds(ID, getName(), x, y, width, height);

        Draw2DProcessor draw = Draw2DProcessor.INSTANCE;
        float radius = RADIUS * s;
        int accent = Theme.getAccent();
        float alpha = opacity.getValueFloat() / 100;
        draw.drawGlow(null, x, y, width, height, radius, ColorUtil.withAlpha(0xFF000000, Math.round(65 * alpha)), 6 * s);
        if (hudBlur.isEnabled() && Interface.isHudBlurEnabled()) {
            draw.drawPanelBlur(null, x, y, width, height, radius, alpha);
        }
        draw.drawRect(null, x, y, width, height, radius, ColorUtil.withAlpha(Theme.getCardBg(), Math.round(200 * alpha)));
        if (hudGradient.isEnabled()) {
            Interface iface = Interface.getInstance();
            float strength = gradientStrength.getValueFloat();
            int color = iface != null ? iface.computeHudGradientColor(strength, alpha)
                    : ColorUtil.withAlpha(accent, Math.round(strength * 0.45f * alpha));
            draw.drawGradient(null, x, y, width, height, radius, color, 0, 0, color);
        }
        draw.drawOutline(null, x, y, width, height, radius, 0.8f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(32 * alpha)));
        Fonts.ROCKSTAR_SEMIBOLD.a(null, getName(), x + 7 * s, y + 4 * s, 7.5f * s, Theme.TEXT_TITLE);

        float vx = x + INSET * s;
        float vy = y + HEADER_HEIGHT * s;
        float vw = CAMERA_WIDTH * s;
        float viewHeight = CAMERA_HEIGHT * s;
        float viewRadius = 4 * s;
        if (!available) draw.drawRect(null, vx, vy, vw, viewHeight, viewRadius, 0xFF141519);
        DrawBatcher.flushPending();
        if (available && camera.getTexture() != null) {
            draw.getTexturePipeline().drawTexture(camera.getTexture(), vx, vy, vw, viewHeight, viewRadius);
            DrawBatcher.flushPending();
            if (marker != null) {
                float mx = vx + vw * marker.x;
                float my = vy + viewHeight * marker.y;
                draw.drawOutline(null, mx - 4 * s, my - 4 * s, 8 * s, 8 * s, 4 * s, 1 * s, accent);
                draw.drawRect(null, mx - s, my - s, 2 * s, 2 * s, s, 0xFFFFFFFF);
            }
            if (pearlMarker != null) {
                draw.getTexturePipeline().drawTexture(PEARL_ICON, vx + vw * pearlMarker.x - 4 * s,
                        vy + viewHeight * pearlMarker.y - 4 * s, 8 * s, 8 * s, 0, 0, 1, 1, 0xFFFFFFFF, 0, 1);
            }
        } else {
            String message = SODIUM_LOADED ? "Камера недоступна с Sodium"
                    : failed || hasImpact || flight ? "Нет изображения"
                    : editing && !isHoldingPearl() ? "Возьмите жемчуг" : "Нет точки попадания";
            float size = 7.6f * s;
            Fonts.sfRegular.a(null, message, vx + (vw - Fonts.sfRegular.a(message, size)) / 2,
                    vy + viewHeight / 2 - 3 * s, size, Theme.TEXT_SUB);
        }

        if (snap != null) {
            HudSnapHelper.renderGridAndGuides(context, snap, screenWidth, screenHeight, bounds,
                    getName(), Math.round(scale.getValueFloat() * 100), radius);
        } else if (editing && contains(mouseX, mouseY)) {
            HudSnapHelper.renderHoverBadge(context, x, y, width, height, radius, Math.round(scale.getValueFloat() * 100) + "%");
        }
        if (editing && contextMenu.isOpen()) contextMenu.render(context, mouseX, mouseY);
        DrawBatcher.flushPending();
    }

    public boolean onChatMouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.isOpen() && contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
        if (!contains(mouseX, mouseY)) return false;
        if (button == 1) {
            contextMenu.openSmart(ID, bounds.x(), bounds.y(), bounds.w(), bounds.h(), (float) mouseX, (float) mouseY, scale.getValueFloat());
            return true;
        }
        if (button != 0) return false;
        dragging = true;
        dragX = mouseX - bounds.x();
        dragY = mouseY - bounds.y();
        return true;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (dragging || contextMenu.isOpen() || amount == 0 || !contains(mouseX, mouseY)) return false;
        scale.setValue(scale.getValue() + Math.copySign(SCALE_STEP, amount));
        ConfigManager.save();
        return true;
    }

    public void finishDrag(int button) {
        if (button != 0 || !dragging) return;
        dragging = false;
        ConfigManager.save();
    }

    private boolean isHoldingPearl() {
        return mc.player.getMainHandStack().isOf(Items.ENDER_PEARL) || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);
    }

    private boolean contains(double x, double y) {
        return bounds != null && x >= bounds.x() && x <= bounds.x() + bounds.w()
                && y >= bounds.y() && y <= bounds.y() + bounds.h();
    }
}