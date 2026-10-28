package egg.client.command.waypoint;

import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.ProjectUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.*;

public final class WaypointManager {
    private static final WaypointManager INSTANCE = new WaypointManager();
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

        for (Waypoint wp : getWaypoints()) {
            Vector2f screen = ProjectUtil.toScreen(wp.x, wp.y + 1.2, wp.z);
            if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) continue;

            double dist = playerPos.distanceTo(wp.getPos());
            String distText = String.format(Locale.US, "%.1fm", dist);
            String label = wp.name + " (" + distText + ")";

            float textW = ClientFont.getWidth(label, ClientFont.SF_MEDIUM);
            float badgeW = textW + 16.0f;
            float badgeH = 16.0f;
            float bx = screen.x - badgeW / 2.0f;
            float by = screen.y - badgeH / 2.0f;

            // Background badge
            Render2D.drawShadow(context, bx, by, badgeW, badgeH, 4.0f, 8.0f, ColorUtil.rgba(0, 0, 0, 160));
            Render2D.drawRoundedRect(context, bx, by, badgeW, badgeH, 4.0f, ColorUtil.rgba(18, 19, 24, 215));
            Render2D.drawRoundedOutline(context, bx, by, badgeW, badgeH, 4.0f, 1.0f, ColorUtil.rgba(168, 85, 247, 180));

            // Accent dot
            Render2D.drawCircle(context, bx + 7.0f, by + badgeH / 2.0f, 2.0f, Theme.getAccent());

            // Text
            ClientFont.draw(context, label, bx + 13.0f, by + 3.5f, ClientFont.SF_MEDIUM, 0xFFFFFFFF);
        }
    }
}
