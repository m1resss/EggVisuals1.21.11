package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class AccumBlurUniformStorage {
    private static DynamicUniformStorage<AccumBlurUniforms> storage;

    private AccumBlurUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            storage = new DynamicUniformStorage<>("EggAccumBlur", 16, 16);
        }
    }

    public static GpuBufferSlice write(AccumBlurUniforms uniforms) {
        if (storage == null) {
            init();
        }
        return storage.write(uniforms);
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
