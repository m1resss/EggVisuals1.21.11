package egg.client.render.texture;

import com.mojang.blaze3d.textures.GpuTextureView;
import egg.client.EggClient;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

public final class CloudNoiseTexture {
    private static final int SIZE = 512;
    private static NativeImageBackedTexture noiseTexture;
    private static NativeImageBackedTexture levelsTexture;

    private CloudNoiseTexture() {
    }

    public static GpuTextureView getNoiseTexture() {
        if (noiseTexture == null) {
            generate();
        }
        return getView(noiseTexture);
    }

    public static GpuTextureView getLevelsTexture() {
        if (levelsTexture == null) {
            generate();
        }
        return getView(levelsTexture);
    }

    private static GpuTextureView getView(NativeImageBackedTexture texture) {
        if (texture == null) {
            return null;
        }
        try {
            GpuTextureView view = texture.getGlTextureView();
            return (view != null && !view.isClosed()) ? view : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static NativeImageBackedTexture createTexture(String string, int[] ints) {
        NativeImageBackedTexture nativeImageBackedTexture = new NativeImageBackedTexture(() -> EggClient.MOD_ID + "_" + string, SIZE, SIZE, false);
        NativeImage nativeImage = nativeImageBackedTexture.getImage();

        for (int i = 0; i < SIZE; i++) {
            for (int i2 = 0; i2 < SIZE; i2++) {
                nativeImage.setColorArgb(i2, i, ints[i2 + i * SIZE]);
            }
        }

        nativeImageBackedTexture.upload();
        return nativeImageBackedTexture;
    }

    private static void generate() {
        int[] ints = new int[SIZE * SIZE];
        int[] ints2 = new int[SIZE * SIZE];
        CellLayer cellLayer = new CellLayer(3, 307, 0.9F, 1, null);
        CellLayer cellLayer2 = new CellLayer(10, 101, 0.8F, 3, null);
        CellLayer cellLayer3 = new CellLayer(
            26, 211, 0.85F, 3, bakeLevelMap(cellLayer2, 26, 211, (i, i2) -> Math.min((int)(random(i, i2, 217) * 3.0F), 2))
        );
        CellLayer cellLayer4 = new CellLayer(64, 503, 0.9F, 3, bakeLevelMap(cellLayer2, 64, 503, (i3, i4) -> -1));
        int[] ints3 = new int[3];

        for (int i5 = 0; i5 < SIZE; i5++) {
            for (int i6 = 0; i6 < SIZE; i6++) {
                float f = i6 / (float) SIZE;
                float f2 = i5 / (float) SIZE;
                float f3 = 0.3F + 0.7F * smoothstep(0.0F, 0.55F, cellLayer.sample(f, f2));
                cellLayer2.sample(f, f2);
                cellLayer3.sample(f, f2);
                cellLayer4.sample(f, f2);
                float f4 = 0.0F;

                for (int i7 = 0; i7 < 3; i7++) {
                    float f5 = cellLayer2.levelValues[i7] * f3;
                    float f6 = Math.min(Math.max(f5, f5 * 0.8F + cellLayer3.levelValues[i7] * 0.3F + cellLayer4.levelValues[i7] * 0.2F), 1.0F);
                    ints3[i7] = Math.round(f6 * 255.0F);
                    f4 = Math.max(f4, f6);
                }

                int i8 = Math.min(Math.round(f4 * 65535.0F), 65535);
                int i9 = hash(i6, i5, 7) & 0xFF;
                int i10 = hash(Math.floorMod(i6 + 37, SIZE), Math.floorMod(i5 + 17, SIZE), 7) & 0xFF;
                ints[i6 + i5 * SIZE] = packArgb(i10, i8 >> 8, i8 & 0xFF, i9);
                ints2[i6 + i5 * SIZE] = packArgb(255, ints3[0], ints3[1], ints3[2]);
            }
        }

        noiseTexture = createTexture("cloud_noise_lut", ints);
        levelsTexture = createTexture("cloud_levels_lut", ints2);
    }

    private static IntBinaryOperator bakeLevelMap(CellLayer cellLayer, int i, int i2, IntBinaryOperator intBinaryOperator) {
        int[] ints = new int[i * i];

        for (int i3 = 0; i3 < i; i3++) {
            for (int i4 = 0; i4 < i; i4++) {
                float f = (i4 + random(i4, i3, i2)) / i;
                float f2 = (i3 + random(i4, i3, i2 + 1)) / i;
                ints[i4 + i3 * i] = cellLayer.sample(f, f2) > 0.05F ? cellLayer.dominantLevel : intBinaryOperator.applyAsInt(i4, i3);
            }
        }

        return (i5, i6) -> ints[i5 + i6 * i];
    }

    private static int packArgb(int i, int i2, int i3, int i4) {
        return i << 24 | i2 << 16 | i3 << 8 | i4;
    }

    private static float smoothstep(float f, float f2, float f3) {
        float f4 = Math.min(Math.max((f3 - f) / (f2 - f), 0.0F), 1.0F);
        return f4 * f4 * (3.0F - 2.0F * f4);
    }

    private static int hash(int i, int i2, int i3) {
        int i4 = i * 374761393 + i2 * 668265263 + i3 * 1274126177;
        int i5 = (i4 ^ i4 >>> 13) * 1274126177;
        return i5 ^ i5 >>> 16;
    }

    static float random(int i, int i2, int i3) {
        return (hash(i, i2, i3) & 16777215) / 1.6777216E7F;
    }

    public static void close() {
        if (noiseTexture != null) {
            noiseTexture.close();
            noiseTexture = null;
        }
        if (levelsTexture != null) {
            levelsTexture.close();
            levelsTexture = null;
        }
    }

    static final class CellLayer {
        private final int cells;
        private final int seed;
        private final float density;
        private final IntBinaryOperator levelMapper;
        final float[] levelValues;
        int dominantLevel;

        CellLayer(int i, int i2, float f, int i3, IntBinaryOperator intBinaryOperator) {
            this.cells = i;
            this.seed = i2;
            this.density = f;
            this.levelMapper = intBinaryOperator != null ? intBinaryOperator : (i4, i5) -> Math.min((int)(CloudNoiseTexture.random(i4, i5, i2 + 6) * i3), i3 - 1);
            this.levelValues = new float[i3];
        }

        float sample(float f, float f2) {
            float f3 = f * this.cells;
            float f4 = f2 * this.cells;
            int i = (int)Math.floor(f3);
            int i2 = (int)Math.floor(f4);
            Arrays.fill(this.levelValues, 0.0F);
            float f5 = 0.0F;
            this.dominantLevel = 0;

            for (int i3 = -1; i3 <= 1; i3++) {
                for (int i4 = -1; i4 <= 1; i4++) {
                    int i5 = i + i4;
                    int i6 = i2 + i3;
                    int i7 = Math.floorMod(i5, this.cells);
                    int i8 = Math.floorMod(i6, this.cells);
                    if (!(CloudNoiseTexture.random(i7, i8, this.seed + 4) > this.density)) {
                        float f6 = 0.5F + 0.5F * CloudNoiseTexture.random(i7, i8, this.seed + 2);
                        float f7 = (f3 - (i5 + CloudNoiseTexture.random(i7, i8, this.seed))) / f6;
                        float f8 = (f4 - (i6 + CloudNoiseTexture.random(i7, i8, this.seed + 1))) / f6;
                        float f9 = f7 * f7 + f8 * f8;
                        if (!(f9 >= 1.0F)) {
                            float f10 = (0.55F + 0.45F * CloudNoiseTexture.random(i7, i8, this.seed + 3)) * (float)Math.sqrt(1.0F - f9);
                            int i9 = this.levelMapper.applyAsInt(i7, i8);
                            if (i9 >= 0) {
                                this.levelValues[i9] = Math.max(this.levelValues[i9], f10);
                                if (f10 > f5) {
                                    f5 = f10;
                                    this.dominantLevel = i9;
                                }
                            }
                        }
                    }
                }
            }

            return f5;
        }
    }
}
