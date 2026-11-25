package egg.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;

public final class EggStartupAnimation {
    private static final Identifier GLYPHS = Identifier.of("egg", "startup_glyphs");
    private static final int COLUMNS = 59;
    private static final int ROWS = 45;
    private static final int CELL_WIDTH = 6;
    private static final int CELL_HEIGHT = 8;
    private static final double CELL_DEPTH_Y = 0.057;
    private static final double CELL_DEPTH_X = CELL_DEPTH_Y * CELL_WIDTH / CELL_HEIGHT;
    private static final int[] GLYPH_BITS = {
            0b00000_00000_00000_00000_00100,
            0b00000_00100_00000_00100_00000,
            0b00000_00000_01110_00000_00000,
            0b00000_01110_00000_01110_00000,
            0b00000_00100_01110_00100_00000,
            0b10001_01010_00100_01010_10001,
            0b10101_01110_11111_01110_10101,
            0b11001_11010_00100_01011_10011,
            0b01010_11111_01010_11111_01010,
            0b01110_10001_10111_10101_01110
    };
    private static final ShellPoint[] SHELL = buildShell();

    private final double[] depth = new double[COLUMNS * ROWS];
    private final float[] light = new float[depth.length];
    private final float[] cracks = new float[depth.length];
    private boolean atlasReady;
    private long lastFrame;
    private float progress;

    public void drawProgress(DrawContext context, long now, float loadProgress) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float elapsed = Math.min(100, now - lastFrame);
        progress = MathHelper.lerp(1f - (float) Math.exp(-elapsed / 125f), progress, MathHelper.clamp(loadProgress, 0f, 1f));
        lastFrame = now;
        int half = Math.max(10, Math.round(Math.min(width, height) * 0.105f));
        int left = width / 2 - half;
        int y = Math.round(height * 0.89f);
        context.fill(left, y, left + half * 2, y + 1, color(0xAAB1BD, 0.26f));
        int filled = Math.round(progress * half * 2);
        if (filled > 0) context.fill(left, y, left + filled, y + 1, color(0xE8EAEE, 0.72f));
    }

    public void draw(MinecraftClient client, DrawContext context, long age, float scatter, float opacity) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        if (!atlasReady) {
            registerAtlas(client);
            atlasReady = true;
        }

        double seconds = age * 0.001;
        float intro = smooth(age / 1910f);
        project(seconds, intro, smooth((age - 2300) / 1050f));
        float scale = Math.min(height * 0.84f / (ROWS * CELL_HEIGHT), width * 0.80f / (COLUMNS * CELL_WIDTH));

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(width * 0.5f, height * 0.47f);
            matrices.scale(scale);
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int cell = row * COLUMNS + col;
                    if (depth[cell] != Double.NEGATIVE_INFINITY) {
                        drawCell(context, col, row, cell, intro, scatter, opacity);
                    }
                }
            }
        } finally {
            matrices.popMatrix();
        }

    }

    private void project(double seconds, float intro, float fracture) {
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        double yaw = seconds * 0.92;
        double lean = -0.39 + Math.sin(seconds * 1.15) * 0.055;
        double pitch = Math.sin(seconds * 0.94) * 0.15;
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosLean = Math.cos(lean);
        double sinLean = Math.sin(lean);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double size = 0.49 + 0.51 * intro;
        for (ShellPoint p : SHELL) {
            double spinX = p.x * cosYaw + p.z * sinYaw;
            double spinZ = p.z * cosYaw - p.x * sinYaw;
            double tiltY = p.y * cosPitch - spinZ * sinPitch;
            double z = spinZ * cosPitch + p.y * sinPitch;
            double perspective = size / (1.0 - z * 0.17);
            int col = (int) Math.round((COLUMNS - 1) * 0.5 + (spinX * cosLean - tiltY * sinLean) * perspective / CELL_DEPTH_X);
            int row = (int) Math.round((ROWS - 1) * 0.5 - (tiltY * cosLean + spinX * sinLean) * perspective / CELL_DEPTH_Y);
            if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) continue;
            int cell = row * COLUMNS + col;
            if (z <= depth[cell]) continue;
            depth[cell] = z;

            double normalX = p.nx * cosYaw + p.nz * sinYaw;
            double normalZ = p.nz * cosYaw - p.nx * sinYaw;
            double normalY = p.ny * cosPitch - normalZ * sinPitch;
            double nz = normalZ * cosPitch + p.ny * sinPitch;
            double nx = normalX * cosLean - normalY * sinLean;
            double ny = normalY * cosLean + normalX * sinLean;
            double diffuse = Math.max(0.0, -nx * 0.48 + ny * 0.58 + nz * 0.65);
            double gloss = Math.pow(Math.max(0.0, -nx * 0.30 + ny * 0.39 + nz * 0.87), 13);
            double rim = Math.pow(1 - Math.max(0.0, nz), 3) * Math.max(0.0, (ny - nx * 0.5) * 0.55 + 0.18);
            double bounce = Math.max(0.0, nx * 0.62 - ny * 0.38 + nz * 0.18);
            light[cell] = (float) Math.min(1, 0.12 + diffuse * 0.57 + gloss * 0.34 + rim * 0.42 + bounce * 0.23 + p.grain * 0.10);
            float split = smooth((fracture - (col - 11f) / (COLUMNS - 20f)) * 6f);
            cracks[cell] = (1f - smooth((float) (Math.abs(p.y - p.fault) / 0.053))) * split;
        }
    }

    private void drawCell(DrawContext context, int col, int row, int cell, float intro, float scatter, float visibility) {
        float x = col - (COLUMNS - 1) * 0.5f;
        float y = row - (ROWS - 1) * 0.5f;
        float seed = noise(cell);
        float order = 0.40f + x * 0.009f + y * 0.006f + seed * 0.30f;
        float gather = 1f - smooth((intro - order + 0.48f) / 0.52f);
        float leave = smooth((scatter - seed * 0.34f - (x + 19) * 0.005f) / 0.46f);
        float opacity = (1f - smooth(gather * 1.3f)) * (1f - smooth(leave)) * visibility;
        if (opacity < 0.025f) return;

        float spread = leave * (0.35f + seed * 0.85f);
        float sweep = gather * (0.3f + seed * 0.6f);
        int drawX = Math.round((x * (1f + spread) - y * sweep * 0.76f + spread * 8f) * CELL_WIDTH) - 2;
        int drawY = Math.round((y * (1f + spread * 0.65f) + x * sweep * 0.56f - spread * 8f) * CELL_HEIGHT) - 2;
        float brightness = MathHelper.clamp(light[cell] + (seed - 0.5f) * 0.19f, 0f, 1f);
        int glyph = Math.min(GLYPH_BITS.length - 1, (int) (brightness * GLYPH_BITS.length));
        if (leave > 0.08f || gather > 0.3f) glyph = Math.min(glyph, 4);

        int rgb = blendRgb(0x8D949E, 0xFFFFFF, smooth(brightness * 1.35f));
        float ink = (0.66f + brightness * 0.34f) * (1f - cracks[cell] * 0.73f) * opacity;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GLYPHS, drawX, drawY,
                glyph * CELL_WIDTH, 0, 5, 5, 5, 5, GLYPH_BITS.length * CELL_WIDTH, 6, color(rgb, ink));
    }

    private static void registerAtlas(MinecraftClient client) {
        NativeImage image = new NativeImage(GLYPH_BITS.length * CELL_WIDTH, 6, true);
        for (int glyph = 0; glyph < GLYPH_BITS.length; glyph++) {
            for (int pixel = 0; pixel < 25; pixel++) {
                if ((GLYPH_BITS[glyph] & (1 << (24 - pixel))) != 0) {
                    image.setColorArgb(glyph * CELL_WIDTH + pixel % 5, pixel / 5, 0xFFFFFFFF);
                }
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> GLYPHS.toString(), image);
        texture.upload();
        client.getTextureManager().registerTexture(GLYPHS, texture);
    }

    private static ShellPoint[] buildShell() {
        int rings = 76;
        int segments = 128;
        ShellPoint[] shell = new ShellPoint[(rings + 1) * segments];
        for (int ring = 0; ring <= rings; ring++) {
            double angle = ring * Math.PI / rings;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            double radius = sin * (0.74 - cos * 0.16);
            double radialSlope = cos * (0.74 - cos * 0.16) + sin * sin * 0.16;
            double verticalSlope = 1.04 * sin;
            double normalLength = Math.hypot(verticalSlope, radialSlope);
            for (int segment = 0; segment < segments; segment++) {
                double longitude = (segment + (ring % 2) * 0.5) * Math.TAU / segments;
                double u = Math.cos(longitude);
                double v = Math.sin(longitude);
                double x = radius * u;
                double y = cos * 1.04;
                double z = radius * v;
                double grain = Math.sin(x * 29 + Math.sin(y * 18) * 2.1) * Math.sin(z * 24 - y * 14);
                double saw = Math.asin(Math.sin(longitude * 8 + 0.6 * Math.sin(longitude * 3)));
                double fault = 0.42 + 0.05 * Math.sin(longitude * 2) + saw * 0.035;
                shell[ring * segments + segment] = new ShellPoint(x, y, z,
                        verticalSlope * u / normalLength, radialSlope / normalLength, verticalSlope * v / normalLength, grain, fault);
            }
        }
        return shell;
    }

    private static float noise(int cell) {
        int hash = (cell + 1) * 0x9E3779B9;
        hash = (hash ^ (hash >>> 16)) * 0x85EBCA6B;
        return ((hash ^ (hash >>> 13)) & 0xFFFF) / 65535f;
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int color(int rgb, float alpha) {
        return rgb | (Math.round(alpha * 255) << 24);
    }

    private static int blendRgb(int from, int to, float amount) {
        int red = MathHelper.lerp(amount, from >> 16, to >> 16);
        int green = MathHelper.lerp(amount, (from >> 8) & 255, (to >> 8) & 255);
        int blue = MathHelper.lerp(amount, from & 255, to & 255);
        return red << 16 | green << 8 | blue;
    }

    private record ShellPoint(double x, double y, double z, double nx, double ny, double nz, double grain, double fault) {}
}
