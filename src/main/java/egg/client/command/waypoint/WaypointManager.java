package egg.client.command.waypoint;

import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.*;

public final class WaypointManager {
    private static final WaypointManager INSTANCE = new WaypointManager();
    private static final Identifier WAYPOINT_PIN_TEXTURE = Identifier.of("egg", "textures/waypoint_pin.png");

    public static WaypointManager getInstance() {
        return INSTANCE;
    }

    public static class Waypoint {
        public final String name;
        public final double x, y, z;
        public final long createdAt;

        public Waypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = System.currentTimeMillis();
        }

        public Vec3d getPos() {
            return new Vec3d(x, y, z);
        }
    }

    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    private WaypointManager() {}

    public synchronized void add(String name, double x, double y, double z) {
        waypoints.put(name.toLowerCase(), new Waypoint(name, x, y, z));
    }

    public synchronized boolean remove(String name) {
        return waypoints.remove(name.toLowerCase()) != null;
    }

    public synchronized void clear() {
        waypoints.clear();
    }

    public synchronized Collection<Waypoint> getWaypoints() {
        return new ArrayList<>(waypoints.values());
    }

    public synchronized boolean contains(String name) {
        return waypoints.containsKey(name.toLowerCase());
    }

    public void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || waypoints.isEmpty()) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        List<Waypoint> list = new ArrayList<>(getWaypoints());
        list.sort((a, b) -> Double.compare(playerPos.squaredDistanceTo(b.getPos()), playerPos.squaredDistanceTo(a.getPos())));

        for (Waypoint wp : list) {
            double targetX = wp.x + (wp.x == Math.floor(wp.x) ? 0.5 : 0.0);
            double targetY = wp.y;
            double targetZ = wp.z + (wp.z == Math.floor(wp.z) ? 0.5 : 0.0);

            Vector2f screen = ProjectUtil.toScreen(targetX, targetY, targetZ);
            if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) continue;
            if (screen.x < -100.0f || screen.x > screenW + 100.0f || screen.y < -100.0f || screen.y > screenH + 100.0f) {
                continue;
            }

            double dist = playerPos.distanceTo(wp.getPos());
            String distText = dist >= 1000.0 ? String.format(Locale.US, "%.1fkm", dist / 1000.0) : String.format(Locale.US, "%.1fm", dist);

            float scaleFactor = (float) Math.clamp(1.0 - (dist - 10.0) / 200.0 * 0.28, 0.72, 1.05);

            float pinSize = 30.0f * scaleFactor;
            float fontSizeName = 10.5f * scaleFactor;
            float fontSizeDist = 9.0f * scaleFactor;
            float padX = 6.0f * scaleFactor;
            float badgeH = 15.0f * scaleFactor;
            float spacing = 4.5f * scaleFactor;

            float nameW = Fonts.d.a(wp.name, fontSizeName);
            float distW = Fonts.b.a(distText, fontSizeDist);
            float badgeW = padX + nameW + spacing + distW + padX;

            float pinX = screen.x - pinSize / 2.0f;
            float pinY = screen.y - pinSize;

            float badgeX = screen.x - badgeW / 2.0f;
            float badgeY = pinY - badgeH - (3.0f * scaleFactor);

            Draw2DProcessor.INSTANCE.a(null, WAYPOINT_PIN_TEXTURE, pinX, pinY, pinSize, pinSize, 0.0f, 0xFFFFFFFF);

            Draw2DProcessor.INSTANCE.drawGlow(null, badgeX, badgeY, badgeW, badgeH, 4.5f * scaleFactor, ColorUtil.rgba(0, 0, 0, 140), 6.0f * scaleFactor);
            Draw2DProcessor.INSTANCE.drawRect(null, badgeX, badgeY, badgeW, badgeH, 4.5f * scaleFactor, ColorUtil.rgba(14, 15, 22, 225));
            Draw2DProcessor.INSTANCE.drawOutline(null, badgeX, badgeY, badgeW, badgeH, 4.5f * scaleFactor, 0.8f, ColorUtil.withAlphaFloat(Theme.getAccent(), 0.55f));

            float textY = badgeY + (badgeH - Fonts.d.a(fontSizeName)) / 2.0f - 0.5f;
            Fonts.d.a(null, wp.name, badgeX + padX, textY, fontSizeName, 0xFFFFFFFF);
            Fonts.b.a(null, distText, badgeX + padX + nameW + spacing, textY + 0.5f, fontSizeDist, Theme.getAccentHover());
        }

        DrawBatcher.flushPending();
    }
}
