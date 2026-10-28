package egg.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural rasterizer for pixel-perfect anti-aliased geometry textures
 * mapped 1:1 to physical screen pixels at the user's GUI scale.
 */
public final class DynamicGuiTextures {
    private DynamicGuiTextures() {}

    private static int lastGuiScale = -1;
    private static final Map<Integer, Identifier> RECT_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> OUTLINE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> CIRCLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> SHADOW_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> SPEEDOMETER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> CLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> EGG_3D_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> FOLDER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> TPS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> PING_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> MUSIC_NOTE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> UPGRADE_GAUGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> UPGRADE_NEEDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> CASE_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> CARD_ARC_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> SECTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> WHEEL_BASE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Identifier> WHEEL_LINES_CACHE = new ConcurrentHashMap<>();

    private static int getScale() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null) {
            int s = (int) Math.round(mc.getWindow().getScaleFactor());
            if (s != lastGuiScale) {
                lastGuiScale = s;
                RECT_CACHE.clear();
                OUTLINE_CACHE.clear();
                CIRCLE_CACHE.clear();
                SHADOW_CACHE.clear();
                SPEEDOMETER_CACHE.clear();
                CLOCK_CACHE.clear();
                EGG_3D_CACHE.clear();
                FOLDER_CACHE.clear();
                TPS_CACHE.clear();
                PING_CACHE.clear();
                MUSIC_NOTE_CACHE.clear();
                UPGRADE_GAUGE_CACHE.clear();
                UPGRADE_NEEDLE_CACHE.clear();
                CARD_ARC_CACHE.clear();
                SECTOR_CACHE.clear();
                WHEEL_BASE_CACHE.clear();
                WHEEL_LINES_CACHE.clear();
            }
            return Math.max(1, s);
        }
        return 2;
    }

    public static Identifier getRoundedRect(int radius) {
        int scale = getScale();
        int key = (scale << 16) | (radius & 0xFFFF);
        return RECT_CACHE.computeIfAbsent(key, k -> createRoundedRect(radius, scale));
    }

    public static Identifier getRoundedOutline(int radius) {
        int scale = getScale();
        int key = (scale << 16) | (radius & 0xFFFF);
        return OUTLINE_CACHE.computeIfAbsent(key, k -> createRoundedOutline(radius, scale));
    }

    public static Identifier getCircle(int radius) {
        int scale = getScale();
        int key = (scale << 16) | (radius & 0xFFFF);
        return CIRCLE_CACHE.computeIfAbsent(key, k -> createCircle(radius, scale));
    }

    public static Identifier getRoundedShadow(int radius, int spread) {
        int scale = getScale();
        int key = (scale << 20) | ((radius & 0x3FF) << 10) | (spread & 0x3FF);
        return SHADOW_CACHE.computeIfAbsent(key, k -> createShadow(radius, spread, scale));
    }

    public static Identifier getSpeedometerIcon() {
        int scale = getScale();
        return SPEEDOMETER_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createSpeedometer);
    }

    public static Identifier getClockIcon() {
        int scale = getScale();
        return CLOCK_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createClock);
    }

    public static Identifier getEggLogo3D() {
        int scale = getScale();
        return EGG_3D_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createEggLogo3D);
    }

    public static Identifier getFolderIcon() {
        int scale = getScale();
        return FOLDER_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createFolder);
    }

    public static Identifier getTpsIcon() {
        int scale = getScale();
        return TPS_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createTps);
    }

    public static Identifier getPingIcon() {
        int scale = getScale();
        return PING_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createPing);
    }

    public static Identifier getMusicNoteIcon() {
        int scale = getScale();
        return MUSIC_NOTE_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createMusicNote);
    }

    private static Identifier createRoundedRect(int radius, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        NativeImage image = new NativeImage(size, size, false);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x < r) ? (r - 0.5f - x) : (x >= size - r ? (x - (size - r - 0.5f)) : 0.0f);
                float dy = (y < r) ? (r - 0.5f - y) : (y >= size - r ? (y - (size - r - 0.5f)) : 0.0f);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = clamp01(r - dist + 0.5f);
                int a = Math.round(alpha * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/rrect_" + radius + "_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createRoundedOutline(int radius, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        float stroke = Math.max(1.0f, 1.0f * scale);
        NativeImage image = new NativeImage(size, size, false);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x < r) ? (r - 0.5f - x) : (x >= size - r ? (x - (size - r - 0.5f)) : 0.0f);
                float dy = (y < r) ? (r - 0.5f - y) : (y >= size - r ? (y - (size - r - 0.5f)) : 0.0f);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float outerAlpha = clamp01(r - dist + 0.5f);
                float innerAlpha = clamp01((r - stroke) - dist + 0.5f);
                float alpha = Math.max(0.0f, outerAlpha - innerAlpha);
                int a = Math.round(alpha * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/routline_" + radius + "_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createCircle(int radius, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        NativeImage image = new NativeImage(size, size, false);

        float center = r - 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = clamp01(r - dist + 0.5f);
                int a = Math.round(alpha * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/circle_" + radius + "_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createShadow(int radius, int spread, int scale) {
        int r = Math.max(1, radius) * scale;
        int sp = Math.max(1, spread) * scale;
        int corner = r + sp;
        int size = corner * 2;
        NativeImage image = new NativeImage(size, size, false);

        float sigma = sp * 0.45f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x < corner) ? (corner - 0.5f - x) : (x >= size - corner ? (x - (size - corner - 0.5f)) : 0.0f);
                float dy = (y < corner) ? (corner - 0.5f - y) : (y >= size - corner ? (y - (size - corner - 0.5f)) : 0.0f);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float factor = (dist <= r) ? 1.0f : (float) Math.exp(-((dist - r) * (dist - r)) / (2.0f * sigma * sigma));
                int a = Math.round(clamp01(factor) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/shadow_" + radius + "_" + spread + "_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createSpeedometer(int scale) {
        int size = 24 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float cx = size / 2.0f - 0.5f;
        float cy = size / 2.0f - 0.5f;
        float outerR = 8.5f * scale;
        float innerR = 7.1f * scale;
        float pivotR = 1.6f * scale;

        // Needle vector: pointing up-right (~40 deg from horizontal)
        float ndx = 5.2f * scale;
        float ndy = -4.2f * scale;
        float nlen = (float) Math.hypot(ndx, ndy);
        float nnx = ndx / nlen;
        float nny = ndy / nlen;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180

                // 1. Gauge Arc (from -145 to 145 deg)
                float arcAlpha = 0.0f;
                boolean inArcRange = !(angle > 50.0f && angle < 130.0f); // open at bottom
                if (inArcRange) {
                    float ringDist = Math.abs(dist - (outerR + innerR) / 2.0f);
                    float halfThick = (outerR - innerR) / 2.0f;
                    arcAlpha = clamp01(halfThick - ringDist + 0.5f);

                    // Fade ends of arc
                    if (angle >= 40.0f && angle <= 50.0f) {
                        arcAlpha *= (50.0f - angle) / 10.0f;
                    } else if (angle >= 130.0f && angle <= 140.0f) {
                        arcAlpha *= (angle - 130.0f) / 10.0f;
                    }
                }

                // 2. Center Pivot Dot
                float pivotAlpha = clamp01(pivotR - dist + 0.5f);

                // 3. Needle (segment from center to needle tip)
                float needleAlpha = 0.0f;
                float proj = dx * nnx + dy * nny;
                if (proj >= 0.0f && proj <= nlen) {
                    float perp = (float) Math.abs(dx * (-nny) + dy * nnx);
                    needleAlpha = clamp01((0.9f * scale) - perp + 0.5f);
                }

                float totalAlpha = Math.max(arcAlpha, Math.max(pivotAlpha, needleAlpha));
                int a = Math.round(clamp01(totalAlpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_speedometer_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createClock(int scale) {
        int size = 24 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float cx = size / 2.0f - 0.5f;
        float cy = size / 2.0f - 0.5f;
        float outerR = 8.5f * scale;
        float innerR = 7.1f * scale;
        float pivotR = 1.3f * scale;

        // Hour hand: to 10:00 (dx = -2.8, dy = -2.8)
        float hx = -2.8f * scale;
        float hy = -2.8f * scale;
        float hlen = (float) Math.hypot(hx, hy);
        float hnx = hx / hlen;
        float hny = hy / hlen;

        // Minute hand: to 02:05 (dx = 4.2, dy = -2.2)
        float mx = 4.2f * scale;
        float my = -2.2f * scale;
        float mlen = (float) Math.hypot(mx, my);
        float mnx = mx / mlen;
        float mny = my / mlen;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                // 1. Clock Outer Ring
                float ringDist = Math.abs(dist - (outerR + innerR) / 2.0f);
                float ringAlpha = clamp01(((outerR - innerR) / 2.0f) - ringDist + 0.5f);

                // 2. Center Pivot
                float pivotAlpha = clamp01(pivotR - dist + 0.5f);

                // 3. Hour hand
                float hourAlpha = 0.0f;
                float hproj = dx * hnx + dy * hny;
                if (hproj >= 0.0f && hproj <= hlen) {
                    float hperp = (float) Math.abs(dx * (-hny) + dy * hnx);
                    hourAlpha = clamp01((0.85f * scale) - hperp + 0.5f);
                }

                // 4. Minute hand
                float minAlpha = 0.0f;
                float mproj = dx * mnx + dy * mny;
                if (mproj >= 0.0f && mproj <= mlen) {
                    float mperp = (float) Math.abs(dx * (-mny) + dy * mnx);
                    minAlpha = clamp01((0.75f * scale) - mperp + 0.5f);
                }

                float totalAlpha = Math.max(ringAlpha, Math.max(pivotAlpha, Math.max(hourAlpha, minAlpha)));
                int a = Math.round(clamp01(totalAlpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_clock_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createEggLogo3D(int scale) {
        int size = 32 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float cx = size / 2.0f - 0.5f;
        float cy = size / 2.0f - 0.5f;
        float rx = 7.8f * scale;
        float ryTop = 10.5f * scale;
        float ryBot = 8.8f * scale;

        // Light direction: from top-left-front
        float lx = -0.55f;
        float ly = -0.65f;
        float lz = 0.75f;
        float llen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= llen; ly /= llen; lz /= llen;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float ry = dy < 0.0f ? ryTop : ryBot;

                float nx = dx / rx;
                float ny = dy / ry;
                float dist = (float) Math.sqrt(nx * nx + ny * ny);

                if (dist <= 1.05f) {
                    float edgeAlpha = clamp01((1.0f - dist) * scale * 2.0f + 0.5f);
                    if (edgeAlpha <= 0.0f) continue;

                    float nz = (float) Math.sqrt(Math.max(0.0f, 1.0f - Math.min(1.0f, nx * nx + ny * ny)));
                    float dot = nx * lx + ny * ly + nz * lz;
                    float diffuse = Math.max(0.0f, dot);

                    // Specular highlight on top-left
                    float spec = (float) Math.pow(Math.max(0.0f, dot), 8.0) * 0.45f;
                    float ambient = 0.65f;
                    float brightness = clamp01(ambient + diffuse * 0.35f + spec);

                    int r = Math.round(brightness * 255.0f);
                    int g = Math.round(brightness * 255.0f);
                    int b = Math.round(Math.min(255.0f, brightness * 255.0f + 10.0f));
                    int a = Math.round(edgeAlpha * 255.0f);

                    image.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/egg_3d_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createFolder(int scale) {
        int size = 20 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float s = scale;

        // Clean folder outline with plus [+] inside
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float fx = x / s;
                float fy = y / s;

                float alpha = 0.0f;
                // Outer body rect: [2.5 .. 17.5], [4.5 .. 16.5]
                boolean inBody = (fx >= 2.5f && fx <= 17.5f && fy >= 5.5f && fy <= 16.5f);
                // Top tab: [2.5 .. 8.5], [3.5 .. 6.0]
                boolean inTab = (fx >= 2.5f && fx <= 8.5f && fy >= 3.5f && fy <= 6.0f);

                if (inBody || inTab) {
                    boolean border = (fx <= 3.5f || fx >= 16.5f || fy >= 15.5f || (inTab && fy <= 4.5f) || (fx >= 8.5f && fy <= 6.5f));
                    // Center plus: vertical [9.5..10.5], [8.0..14.0], horizontal [7.0..13.0], [10.5..11.5]
                    boolean plusH = (fx >= 7.0f && fx <= 13.0f && fy >= 10.3f && fy <= 11.7f);
                    boolean plusV = (fx >= 9.3f && fx <= 10.7f && fy >= 8.0f && fy <= 14.0f);
                    if (border || plusH || plusV) {
                        alpha = 1.0f;
                    }
                }

                int a = Math.round(clamp01(alpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_folder_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createTps(int scale) {
        int size = 20 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float cx = size / 2.0f - 0.5f;
        float cy = size / 2.0f - 0.5f;
        float r = 6.2f * scale;
        float stroke = 1.25f * scale;

        // Circular reload arrow ⟳
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx); // -PI to PI

                float alpha = 0.0f;
                // Ring segment from -0.8 PI to 0.7 PI (~270 degrees)
                float ringDist = Math.abs(dist - r);
                float ringAlpha = clamp01(stroke - ringDist + 0.5f);

                if (angle < -0.9f * Math.PI || angle > 0.65f * Math.PI) {
                    ringAlpha = 0.0f;
                }
                alpha = Math.max(alpha, ringAlpha);

                // Arrow head at end of arc (~0.65 PI)
                float ahX = cx + r * (float) Math.cos(0.65 * Math.PI);
                float ahY = cy + r * (float) Math.sin(0.65 * Math.PI);
                float adx = (x - ahX) / scale;
                float ady = (y - ahY) / scale;
                if (adx >= -2.5f && adx <= 2.5f && ady >= -2.5f && ady <= 2.5f) {
                    if (Math.abs(adx + ady) <= 2.2f && adx <= 1.0f) {
                        alpha = 1.0f;
                    }
                }

                int a = Math.round(clamp01(alpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_tps_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createPing(int scale) {
        int size = 20 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float s = scale;

        // Cellular signal bars (ascending 4 vertical bars)
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float fx = x / s;
                float fy = y / s;

                float alpha = 0.0f;
                // Bar 1 (height 3): fx in [3..5], fy in [13..16]
                if (fx >= 3.0f && fx <= 5.0f && fy >= 13.0f && fy <= 16.0f) alpha = 1.0f;
                // Bar 2 (height 6): fx in [7..9], fy in [10..16]
                if (fx >= 7.0f && fx <= 9.0f && fy >= 10.0f && fy <= 16.0f) alpha = 1.0f;
                // Bar 3 (height 9): fx in [11..13], fy in [7..16]
                if (fx >= 11.0f && fx <= 13.0f && fy >= 7.0f && fy <= 16.0f) alpha = 1.0f;
                // Bar 4 (height 12): fx in [15..17], fy in [4..16]
                if (fx >= 15.0f && fx <= 17.0f && fy >= 4.0f && fy <= 16.0f) alpha = 1.0f;

                int a = Math.round(clamp01(alpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_ping_s" + scale);
        register(id, image);
        return id;
    }

    private static Identifier createMusicNote(int scale) {
        int size = 24 * scale;
        NativeImage image = new NativeImage(size, size, false);
        float s = scale;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float fx = x / s;
                float fy = y / s;

                float alpha = 0.0f;
                // Note head 1: oval at [5..8.5], [14..18]
                float dx1 = fx - 7.0f;
                float dy1 = (fy - 16.0f) * 1.3f;
                if (dx1 * dx1 + dy1 * dy1 <= 4.0f) alpha = 1.0f;

                // Vertical stem 1: fx in [7.8..9.2], fy in [6..16]
                if (fx >= 7.8f && fx <= 9.2f && fy >= 6.0f && fy <= 16.0f) alpha = 1.0f;

                // Note head 2: oval at [13..16.5], [12..16]
                float dx2 = fx - 15.0f;
                float dy2 = (fy - 14.0f) * 1.3f;
                if (dx2 * dx2 + dy2 * dy2 <= 4.0f) alpha = 1.0f;

                // Vertical stem 2: fx in [15.8..17.2], fy in [4..14]
                if (fx >= 15.8f && fx <= 17.2f && fy >= 4.0f && fy <= 14.0f) alpha = 1.0f;

                // Beam connecting tops: fx in [8.0..17.0], slanted fy
                float beamY = 6.0f - (fx - 8.0f) * 0.22f;
                if (fx >= 8.0f && fx <= 17.0f && fy >= beamY - 1.2f && fy <= beamY + 1.2f) alpha = 1.0f;

                int a = Math.round(clamp01(alpha) * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/icon_music_note_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getUpgradeGauge(float chancePercent) {
        int scale = getScale();
        int step = Math.round(Math.clamp(chancePercent, 0.0f, 100.0f) * 10.0f);
        int key = (scale << 16) | (step & 0xFFFF);
        return UPGRADE_GAUGE_CACHE.computeIfAbsent(key, k -> createUpgradeGauge(step / 10.0f, scale));
    }

    private static Identifier createUpgradeGauge(float chancePercent, int scale) {
        int size = 128 * scale;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        float cx = size / 2.0f;
        float cy = size / 2.0f;
        float outerR = 48.0f * scale;
        float innerR = 36.0f * scale;
        float midR = (outerR + innerR) / 2.0f;
        float halfThick = (outerR - innerR) / 2.0f;
        float capR = halfThick;

        float winSweep = Math.clamp(chancePercent / 100.0f, 0.0f, 1.0f) * 360.0f;
        float startAngle = -90.0f;

        float startCapX = cx;
        float startCapY = cy - midR;

        float endRad = (float) Math.toRadians(startAngle + winSweep);
        float endCapX = cx + (float) Math.cos(endRad) * midR;
        float endCapY = cy + (float) Math.sin(endRad) * midR;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f) - cx;
                float dy = (y + 0.5f) - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float ringDist = Math.abs(dist - midR);
                float ringAlpha = clamp01(halfThick - ringDist + 0.5f);

                if (ringAlpha <= 0.001f) {
                    continue;
                }

                float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90.0f;
                if (angle < 0.0f) angle += 360.0f;

                boolean inWinArc = (winSweep > 0.5f) && (angle <= winSweep);

                if (!inWinArc && winSweep > 0.5f) {
                    float dStart = (float) Math.hypot((x + 0.5f) - startCapX, (y + 0.5f) - startCapY);
                    if (dStart <= capR) inWinArc = true;
                    float dEnd = (float) Math.hypot((x + 0.5f) - endCapX, (y + 0.5f) - endCapY);
                    if (dEnd <= capR) inWinArc = true;
                }

                int a = Math.round(ringAlpha * 255.0f);
                if (inWinArc) {
                    // Vibrant Emerald / Lime Green with light highlight
                    int rgb = (ringDist < halfThick * 0.4f) ? 0x004ADE80 : 0x0022C55E;
                    image.setColorArgb(x, y, (a << 24) | rgb);
                } else {
                    // Clearly visible dark slate gauge track with subtle metallic border
                    int rgb = (ringDist > halfThick * 0.7f) ? 0x003A455E : 0x00242B3C;
                    image.setColorArgb(x, y, (a << 24) | rgb);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/upg_gauge_" + Math.round(chancePercent * 10) + "_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getUpgraderNeedle() {
        int scale = getScale();
        return UPGRADE_NEEDLE_CACHE.computeIfAbsent(scale, DynamicGuiTextures::createUpgraderNeedle);
    }

    private static Identifier createUpgraderNeedle(int scale) {
        int size = 128 * scale;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        float cx = size / 2.0f;
        float cy = size / 2.0f;

        float tipY = cy - 53.0f * scale;
        float wingY = cy - 43.0f * scale;
        float baseY = cy - 24.0f * scale;
        float wingW = 7.0f * scale;
        float shaftW = 2.0f * scale;

        for (int y = 0; y < size; y++) {
            float py = y + 0.5f;
            if (py < tipY - 1.0f || py > baseY + 1.0f) continue;

            for (int x = 0; x < size; x++) {
                float px = x + 0.5f;
                float dx = Math.abs(px - cx);

                float allowedHalfW;
                if (py <= wingY) {
                    float t = (py - tipY) / Math.max(1.0f, wingY - tipY);
                    allowedHalfW = t * wingW;
                } else {
                    allowedHalfW = shaftW;
                }

                float alpha = clamp01(allowedHalfW - dx + 0.5f);
                if (alpha > 0.001f) {
                    int a = Math.round(alpha * 255.0f);
                    int rgb = (py <= wingY) ? 0x00FFFFFF : 0x00E2E8F0;
                    image.setColorArgb(x, y, (a << 24) | rgb);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/upg_needle_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getCaseTexture(String key) {
        if (key == null) return null;
        return CASE_TEXTURE_CACHE.computeIfAbsent(key, k -> {
            try {
                java.io.InputStream rawIn = DynamicGuiTextures.class.getResourceAsStream("/assets/egg/textures/cases/case_" + k + ".png");
                if (rawIn == null) {
                    rawIn = DynamicGuiTextures.class.getClassLoader().getResourceAsStream("assets/egg/textures/cases/case_" + k + ".png");
                }
                if (rawIn != null) {
                    try (java.io.InputStream in = rawIn) {
                        NativeImage img = NativeImage.read(in);
                        Identifier id = Identifier.of("egg", "dynamic/case_" + k);
                        register(id, img);
                        return id;
                    }
                }
            } catch (Throwable t) {
                // Ignore and fall back to procedural
            }
            return createProceduralCase(k);
        });
    }

    private static Identifier createProceduralCase(String k) {
        int s = getScale();
        int size = 128 * s;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        int acc;
        int accSec;
        if ("gradient".equalsIgnoreCase(k)) {
            acc = 0xEC4899;
            accSec = 0x3B82F6;
        } else if ("pearl".equalsIgnoreCase(k)) {
            acc = 0x22D3EE;
            accSec = 0x93C5FD;
        } else if ("blood".equalsIgnoreCase(k)) {
            acc = 0xEF4444;
            accSec = 0xB91C1C;
        } else {
            acc = 0xA855F7;
            accSec = 0xF59E0B;
        }

        float bx0 = 20.0f * s, by0 = 42.0f * s, bx1 = 108.0f * s, by1 = 102.0f * s;
        for (int y = (int) by0; y <= (int) by1 && y < size; y++) {
            for (int x = (int) bx0; x <= (int) bx1 && x < size; x++) {
                int body = 0xFF1C202C;
                if (x <= bx0 + 3 * s || x >= bx1 - 3 * s || y <= by0 + 3 * s || y >= by1 - 3 * s) {
                    body = 0xFF2E364A;
                }
                image.setColorArgb(x, y, body);
            }
        }

        // Lid
        float ly0 = 34.0f * s, ly1 = 44.0f * s;
        for (int y = (int) ly0; y <= (int) ly1 && y < size; y++) {
            for (int x = (int) (bx0 - 3 * s); x <= (int) (bx1 + 3 * s) && x < size; x++) {
                image.setColorArgb(x, y, 0xFF000000 | acc);
            }
        }

        // Emblem in center
        float emCX = 64.0f * s, emCY = 72.0f * s, emR = 12.0f * s;
        for (int y = (int) (emCY - emR); y <= (int) (emCY + emR) && y < size; y++) {
            for (int x = (int) (emCX - emR); x <= (int) (emCX + emR) && x < size; x++) {
                float dist = (float) Math.hypot(x - emCX, y - emCY);
                if (dist <= emR) {
                    image.setColorArgb(x, y, 0xFF000000 | accSec);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/case_proc_" + k + "_s" + s);
        register(id, image);
        return id;
    }


    public static Identifier getCardArc(int width, int height) {
        int scale = getScale();
        int key = (scale << 20) | ((width & 0x3FF) << 10) | (height & 0x3FF);
        return CARD_ARC_CACHE.computeIfAbsent(key, k -> createCardArc(width, height, scale));
    }

    private static Identifier createCardArc(int w, int h, int scale) {
        int sw = w * scale;
        int sh = h * scale;
        NativeImage image = new NativeImage(sw, sh, false);
        image.fillRect(0, 0, sw, sh, 0);

        float cx = sw / 2.0f;
        float d = (sw - 36.0f * scale) / 2.0f;
        float s = sh - 20.0f * scale;
        float R = (d * d + s * s) / (2.0f * s);
        float cy = 20.0f * scale + R;
        float halfThick = 2.4f * scale;
        float glowR = 14.0f * scale;

        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.hypot(dx, dy);
                float diff = dist - R;
                if (Math.abs(diff) < glowR && y < cy && Math.abs(dx) <= d + 4.0f * scale) {
                    float glowAlpha = (float) Math.exp(-Math.abs(diff) / (6.0f * scale)) * 0.45f;
                    float coreDist = Math.abs(diff);
                    float coreAlpha = clamp01(halfThick - coreDist + 0.5f);
                    float totalA = Math.min(1.0f, glowAlpha + coreAlpha);
                    if (totalA > 0.01f) {
                        int a = Math.round(totalA * 255.0f);
                        image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                    }
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/card_arc_" + w + "_" + h + "_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getWheelSector(int radius, int sectorIndex) {
        int scale = getScale();
        int key = (scale << 20) | ((radius & 0x7FFF) << 2) | (sectorIndex & 0x3);
        return SECTOR_CACHE.computeIfAbsent(key, k -> createWheelSector(radius, sectorIndex, scale));
    }

    private static Identifier createWheelSector(int radius, int sectorIndex, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        float cx = r - 0.5f;
        float cy = r - 0.5f;
        float R = (float) r;
        float sqrt3_2 = 0.8660254f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > R + 1.0f) continue;

                float edgeAlpha = clamp01(R - dist + 0.5f);
                if (edgeAlpha <= 0.0f) continue;

                float distBoundary;
                if (sectorIndex == 0) {
                    // Sector 0 (Top): bounded by ray -150 deg and ray -30 deg
                    float dA = dx * 0.5f - dy * sqrt3_2;
                    float dB = -dx * 0.5f - dy * sqrt3_2;
                    distBoundary = Math.min(dA, dB);
                } else if (sectorIndex == 1) {
                    // Sector 1 (Bottom-Left): bounded by ray 90 deg and ray 210 deg (-150 deg)
                    float dA = -dx;
                    float dB = -dx * 0.5f + dy * sqrt3_2;
                    distBoundary = Math.min(dA, dB);
                } else {
                    // Sector 2 (Bottom-Right): bounded by ray -30 deg and ray 90 deg
                    float dA = dx * 0.5f + dy * sqrt3_2;
                    float dB = dx;
                    distBoundary = Math.min(dA, dB);
                }

                float angleAlpha = clamp01(distBoundary + 0.5f);
                float alpha = edgeAlpha * angleAlpha;
                if (alpha > 0.001f) {
                    int a = Math.round(alpha * 255.0f);
                    image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/wheel_sec_" + radius + "_" + sectorIndex + "_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getWheelBase(int radius, int themeColor) {
        int scale = getScale();
        int key = (scale << 24) | ((themeColor & 0xFFFFFF) ^ ((radius & 0x7F) << 16));
        return WHEEL_BASE_CACHE.computeIfAbsent(key, k -> createWheelBase(radius, themeColor, scale));
    }

    private static Identifier createWheelBase(int radius, int themeColor, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        float cx = r - 0.5f;
        float cy = r - 0.5f;
        float R = (float) r;

        float tR = (float) ((themeColor >> 16) & 0xFF);
        float tG = (float) ((themeColor >> 8) & 0xFF);
        float tB = (float) (themeColor & 0xFF);

        float baseR = 18.0f;
        float baseG = 22.0f;
        float baseB = 32.0f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > R + 1.0f) continue;

                float edgeAlpha = clamp01(R - dist + 0.5f);
                if (edgeAlpha <= 0.0f) continue;

                // Gradient along the edges of the wheel (from 0.55 R to R)
                float t = clamp01((dist - 0.55f * R) / (0.45f * R));
                float glow = t * t; // smooth ease-in
                float rimWeight = glow * 0.60f; // up to 60% theme color at outer edge

                int cr = Math.round(baseR * (1.0f - rimWeight) + tR * rimWeight);
                int cg = Math.round(baseG * (1.0f - rimWeight) + tG * rimWeight);
                int cb = Math.round(baseB * (1.0f - rimWeight) + tB * rimWeight);
                int ca = Math.round(edgeAlpha * (225.0f + glow * 25.0f));

                image.setColorArgb(x, y, (ca << 24) | (cr << 16) | (cg << 8) | cb);
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/wheel_base_" + radius + "_" + Integer.toHexString(themeColor & 0xFFFFFF) + "_s" + scale);
        register(id, image);
        return id;
    }

    public static Identifier getWheelLines(int radius) {
        int scale = getScale();
        int key = (scale << 16) | (radius & 0xFFFF);
        return WHEEL_LINES_CACHE.computeIfAbsent(key, k -> createWheelLines(radius, scale));
    }

    private static Identifier createWheelLines(int radius, int scale) {
        int r = Math.max(1, radius) * scale;
        int size = r * 2;
        NativeImage image = new NativeImage(size, size, false);
        image.fillRect(0, 0, size, size, 0);

        float cx = r - 0.5f;
        float cy = r - 0.5f;
        float R = (float) r;
        float sqrt3_2 = 0.8660254f;

        float hw = Math.max(1.0f, 1.0f * scale);
        float hubR = 3.5f * scale;
        float stroke = Math.max(1.5f, 1.5f * scale);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > R + 1.0f) continue;

                float circleMask = clamp01(R - dist + 0.5f);
                if (circleMask <= 0.0f) continue;

                // 1. Ray 1: straight down (90 deg)
                float d1 = (dy >= -hw) ? Math.abs(dx) : 1000.0f;

                // 2. Ray 2: up-left (210 deg / -150 deg)
                float proj2 = -dx * sqrt3_2 - dy * 0.5f;
                float d2 = (proj2 >= -hw) ? Math.abs(-0.5f * dx + sqrt3_2 * dy) : 1000.0f;

                // 3. Ray 3: up-right (330 deg / -30 deg)
                float proj3 = dx * sqrt3_2 - dy * 0.5f;
                float d3 = (proj3 >= -hw) ? Math.abs(-0.5f * dx - sqrt3_2 * dy) : 1000.0f;

                float minDist = Math.min(d1, Math.min(d2, d3));
                float lineAlpha = clamp01(hw - minDist + 0.5f);

                // Center hub dot
                float hubAlpha = clamp01(hubR - dist + 0.5f);

                // Outer circle outline ring
                float ringDist = Math.abs(dist - (R - stroke * 0.5f));
                float ringAlpha = clamp01(stroke * 0.5f - ringDist + 0.5f);

                float totalAlpha = circleMask * Math.max(lineAlpha * 0.90f, Math.max(hubAlpha, ringAlpha * 0.95f));
                if (totalAlpha > 0.001f) {
                    int a = Math.round(clamp01(totalAlpha) * 255.0f);
                    image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
        }

        Identifier id = Identifier.of("egg", "dynamic/wheel_lines_" + radius + "_s" + scale);
        register(id, image);
        return id;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static void register(Identifier id, NativeImage image) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
            texture.upload();
            mc.getTextureManager().registerTexture(id, texture);
        }
    }
}
