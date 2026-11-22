package egg.client.module.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import egg.client.EggClient;
import egg.client.config.ConfigManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.SliderSetting;
import egg.client.render.hud.HudSnapHelper;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.world.PearlCameraRenderer;
import egg.client.render.world.PearlTrajectory;
import egg.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.Locale;

public final class PearlPredict extends Module {
    private static final String ID = "pearl_predict";
    private static final Identifier PEARL_ICON = Identifier.ofVanilla("textures/item/ender_pearl.png");
    private static final float RADIUS = 9.0f;

    private final SliderSetting scale = new SliderSetting("Масштаб", 1.0, 0.65, 1.5, 0.05, "Размер окна камеры");
    private final SliderSetting posX = new SliderSetting("X", 0.97, 0.0, 1.0, 0.001, "Позиция X");
    private final SliderSetting posY = new SliderSetting("Y", 0.18, 0.0, 1.0, 0.001, "Позиция Y");
    private final PearlCameraRenderer camera = new PearlCameraRenderer();

    private ClientWorld world;
    private EnderPearlEntity pearl;
    private int lastTrackedTick = -1;
    private Vec3d impact;
    private Vector2f pearlMarker;
    private Vector2f marker;
    private boolean flight;
    private boolean available;
    private boolean failed;
    private double distance;
    private int remainingTicks;
    private HudSnapHelper.HudElementBounds bounds;
    private boolean dragging;
    private double dragX;
    private double dragY;

    public PearlPredict() {
        super("PearlPredict", "Показывает место попадания и полёт жемчуга края в окне камеры", Category.ESP);
        posX.setVisible(() -> false);
        posY.setVisible(() -> false);
        addSettings(scale, posX, posY);
    }

    @Override
    public void onDisable() {
        camera.close();
        world = null;
        pearl = null;
        lastTrackedTick = -1;
        bounds = null;
        dragging = false;
        failed = false;
    }

    public void capture(RenderTickCounter ticks, GpuBufferSlice fog, Vector4f fogColor, PearlCameraRenderer.TerrainPass terrain) {
        available = false;
        impact = null;
        marker = null;
        pearlMarker = null;
        flight = false;
        if (mc.world != world) {
            pearl = null;
            lastTrackedTick = -1;
            world = mc.world;
        }
        if (world == null || mc.player == null || mc.options.hudHidden || mc.getOverlay() != null
                || (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen))) {
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
            if (!(mc.currentScreen instanceof ChatScreen)) bounds = null;
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
        impact = trajectory.impact();
        distance = impact == null ? 0.0 : mc.player.getEntityPos().distanceTo(impact);
        remainingTicks = trajectory.points().size() - 1;
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
            if (available && impact != null) marker = camera.project(impact);
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
        float s = Math.min(scale.getValueFloat(), Math.min(screenWidth / 184.0f, screenHeight / 145.0f));
        float width = 172 * s;
        float viewHeight = 90 * s;
        float height = 129 * s;
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
        draw.drawGlow(null, x, y, width, height, radius, 0x6E000000, 8 * s);
        if (Interface.isHudBlurEnabled()) draw.drawPanelBlur(null, x, y, width, height, radius, 1);
        draw.drawRect(null, x, y, width, height, radius, (Theme.getCardBg() & 0xFFFFFF) | 0xDB000000);
        draw.drawOutline(null, x, y, width, height, radius, 0.8f, 0x18FFFFFF);
        draw.drawRect(null, x + 8 * s, y + 9 * s, 4 * s, 4 * s, 2 * s, accent);
        Fonts.ROCKSTAR_SEMIBOLD.a(null, getName(), x + 17 * s, y + 7 * s, 8 * s, Theme.TEXT_TITLE);
        String state = flight ? "В полёте" : "Прогноз";
        float stateSize = 6.8f * s;
        Fonts.sfRegular.a(null, state, x + width - 8 * s - Fonts.sfRegular.a(state, stateSize), y + 8 * s, stateSize, Theme.TEXT_SUB);

        float vx = x + 6 * s;
        float vy = y + 20 * s;
        float vw = width - 12 * s;
        draw.drawRect(null, vx, vy, vw, viewHeight, 6 * s, 0xFF141519);
        DrawBatcher.flushPending();
        if (available && camera.getTexture() != null) {
            draw.getTexturePipeline().drawTexture(camera.getTexture(), vx, vy, vw, viewHeight, 6 * s);
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
            String message = failed || impact != null || flight ? "Камера недоступна"
                    : editing ? "Возьмите жемчуг края" : "Нет точки попадания";
            float size = 7.6f * s;
            Fonts.sfRegular.a(null, message, vx + (vw - Fonts.sfRegular.a(message, size)) / 2,
                    vy + viewHeight / 2 - 3 * s, size, Theme.TEXT_SUB);
        }

        if (impact != null) {
            BlockPos block = BlockPos.ofFloored(impact);
            String coords = block.getX() + " / " + block.getY() + " / " + block.getZ();
            String time = String.format(Locale.ROOT, "%.1f м  ·  %.1f с", distance, remainingTicks / 20.0);
            float size = 7.0f * s;
            Fonts.sfRegular.a(null, coords, x + 8 * s, y + 116 * s, size, Theme.TEXT_TITLE);
            Fonts.sfRegular.a(null, time, x + width - 8 * s - Fonts.sfRegular.a(time, size), y + 116 * s, size, Theme.TEXT_SUB);
        } else {
            Fonts.sfRegular.a(null, "Точка попадания неизвестна", x + 8 * s, y + 116 * s, 7 * s, Theme.TEXT_SUB);
        }

        if (snap != null) {
            HudSnapHelper.renderGridAndGuides(context, snap, screenWidth, screenHeight, bounds,
                    getName(), Math.round(scale.getValueFloat() * 100), radius);
        } else if (editing && contains(mouseX, mouseY)) {
            HudSnapHelper.renderHoverBadge(context, x, y, width, height, radius, Math.round(scale.getValueFloat() * 100) + "%");
        }
        DrawBatcher.flushPending();
    }

    public boolean startDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        dragging = true;
        dragX = mouseX - bounds.x();
        dragY = mouseY - bounds.y();
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