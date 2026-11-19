package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class MotionBlurUniformStorage {
    private static final int CAPACITY = 16;
    private static DynamicUniformStorage<MotionBlurUniforms> storage;

    private MotionBlurUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            storage = new DynamicUniformStorage<>("EggMotionBlur", 304, 16);
        }
    }

    public static GpuBufferSlice write(MotionBlurUniforms motionBlurUniforms) {
        if (storage == null) {
            init();
        }
        return storage.write(motionBlurUniforms);
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
