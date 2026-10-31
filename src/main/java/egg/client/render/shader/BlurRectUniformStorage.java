package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class BlurRectUniformStorage {
    private static final int CAPACITY = 16;
    private static DynamicUniformStorage<BlurRectUniforms> storage;

    private BlurRectUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            String string = "SadnesBlurRect";
            storage = new DynamicUniformStorage<>(string, 32, 16);
        }
    }

    public static GpuBufferSlice write(BlurRectUniforms blurRectUniforms) {
        if (storage == null) {
            init();
        }

        return storage.write(blurRectUniforms);
    }

    public static void clear() {
        if (storage != null) {
            storage.clear();
        }
    }

    public static void close() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }
}
