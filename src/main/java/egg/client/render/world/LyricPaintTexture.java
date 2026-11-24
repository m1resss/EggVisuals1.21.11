package egg.client.render.world;

import egg.client.EggClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;

record LyricPaintTexture(NativeImageBackedTexture texture, int width, int height) implements AutoCloseable {
    private static final int PADDING = 21;
    private static final float FONT_SIZE = 52f;
    private static final Font SYSTEM_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, (int) FONT_SIZE);
    private static final Font LYRIC_FONT = loadFont();
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);

    static LyricPaintTexture create(String text) {
        Font font = LYRIC_FONT.canDisplayUpTo(text) == -1 ? LYRIC_FONT : SYSTEM_FONT;
        TextLayout glyphs = new TextLayout(text, font, FONT_CONTEXT);
        var bounds = glyphs.getBounds();
        int width = Math.max(1, (int) Math.ceil(bounds.getWidth())) + PADDING * 2;
        int height = Math.max(1, (int) Math.ceil(bounds.getHeight())) + PADDING * 2;
        BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var canvas = bitmap.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            canvas.setColor(Color.WHITE);
            glyphs.draw(canvas, (float) (PADDING - bounds.getX()), (float) (PADDING - bounds.getY()));
        } finally {
            canvas.dispose();
        }

        int[] ink = ((DataBufferInt) bitmap.getRaster().getDataBuffer()).getData();
        int[] mask = new int[ink.length];
        int[] workspace = new int[ink.length];
        int[] near = new int[ink.length];
        int[] far = new int[ink.length];
        for (int i = 0; i < ink.length; i++) mask[i] = ink[i] >>> 24;
        blur(mask, workspace, near, width, height, 4);
        blur(near, workspace, far, width, height, 8);

        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                image.setColorArgb(x, y, mask[index] << 24 | near[index] << 16 | far[index] << 8);
            }
        }
        var texture = new NativeImageBackedTexture(() -> "Egg world lyric", image);
        try {
            texture.upload();
            return new LyricPaintTexture(texture, width, height);
        } catch (RuntimeException failure) {
            texture.close();
            throw failure;
        }
    }

    private static void blur(int[] input, int[] work, int[] output, int width, int height, int radius) {
        boxBlur(input, work, height, width, width, 1, radius);
        boxBlur(work, output, width, height, 1, width, radius);
    }

    private static void boxBlur(int[] input, int[] output, int lines, int length, int lineStep, int step, int radius) {
        int kernelSize = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            int start = line * lineStep;
            int sum = 0;
            for (int i = 0; i < Math.min(radius, length); i++) {
                sum += input[start + i * step];
            }
            for (int i = 0; i < length; i++) {
                if (i + radius < length) sum += input[start + (i + radius) * step];
                output[start + i * step] = sum / kernelSize;
                if (i >= radius) sum -= input[start + (i - radius) * step];
            }
        }
    }

    private static Font loadFont() {
        try (var stream = LyricPaintTexture.class.getResourceAsStream("/assets/egg/font/sf_medium.ttf")) {
            if (stream != null) return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(FONT_SIZE);
        } catch (IOException | FontFormatException exception) {
            EggClient.LOGGER.warn("Could not load the Lyrics font", exception);
        }
        return SYSTEM_FONT;
    }

    @Override
    public void close() {
        texture.close();
    }
}
