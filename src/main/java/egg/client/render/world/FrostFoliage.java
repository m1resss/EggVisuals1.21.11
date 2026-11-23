package egg.client.render.world;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class FrostFoliage {
    private static final int SNOW = 14213872;
    private static final String[] PLANTS = {
            "leaves", "grass", "fern", "bush", "sapling", "vine", "flower", "tulip", "orchid", "dandelion",
            "poppy", "allium", "bluet", "daisy", "cornflower", "lily", "rose", "peony", "lilac", "sunflower",
            "azalea", "petals", "mushroom", "wheat", "carrots", "potatoes", "beetroots", "sugar_cane", "bamboo",
            "moss_carpet", "seagrass", "kelp"
    };
    private static final String[] EXCLUDED = {"grass_block", "grass_path", "short_grass_top", "seagrass_", "kelp_plant"};

    private FrostFoliage() {
    }

    public static void paint(Identifier id, NativeImage image) {
        String path = id.getPath();
        if (!"minecraft".equals(id.getNamespace()) || !path.startsWith("block/")
                || matches(path, EXCLUDED) || !matches(path, PLANTS)) return;
        int width = image.getWidth();
        int height = image.getHeight();
        long seed = id.hashCode() * -7046029254386353131L;
        if (path.contains("leaves")) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = image.getColorArgb(x, y);
                    if (pixel >>> 24 > 16) image.setColorArgb(x, y, tint(pixel, snow(seed, x, y), 0.8f));
                }
            }
        }
        for (int x = 0; x < width; x++) {
            int first = 0;
            while (first < height && image.getColorArgb(x, first) >>> 24 <= 128) first++;
            float variation = random(seed, x, 0) + 0.5f * random(seed, x, 7);
            int depth = Math.max(1, Math.round(3.2f * (width / 16f) * (0.55f + 0.75f * variation)));
            for (int y = first; y < height && y < first + depth; y++) {
                int pixel = image.getColorArgb(x, y);
                if (pixel >>> 24 <= 128) break;
                image.setColorArgb(x, y, tint(pixel, snow(seed, x, y + 3), y == first + depth - 1 ? 0.45f : 1));
            }
        }
    }

    private static boolean matches(String path, String[] names) {
        for (String name : names) {
            if (path.contains(name)) return true;
        }
        return false;
    }

    private static int snow(long seed, int x, int y) {
        return tint(SNOW, 0xFFFFFF, random(seed, x, y));
    }

    public static int tint(int color, int add, float amount) {
        return color & 0xFF000000 | channel(color >>> 16, add >>> 16, amount) << 16
                | channel(color >>> 8, add >>> 8, amount) << 8 | channel(color, add, amount);
    }

    private static int channel(int color, int add, float amount) {
        return Math.round(MathHelper.lerp(amount, color & 255, add & 255));
    }

    private static float random(long seed, int x, int y) {
        long value = seed ^ x * -6752110988234923001L ^ y * -6939452855193903323L;
        value = (value ^ value >>> 29) * -4658895280553007687L;
        return ((value ^ value >>> 32) >>> 40) / 16777216f;
    }
}
