package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class FlameUniformStorage {
    private static final int CAPACITY = 64;
    private static DynamicUniformStorage<FlameUniforms> storage;

    private FlameUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            String string = "SadnesFlame";
            storage = new DynamicUniformStorage<>(string, 48, 64);
        }
    }

    public static GpuBufferSlice write(FlameUniforms flameUniforms) {
        if (storage == null) {
            init();
        }

        return storage.write(flameUniforms);
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
