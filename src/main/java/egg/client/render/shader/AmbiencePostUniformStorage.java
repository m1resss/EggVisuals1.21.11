package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class AmbiencePostUniformStorage {
    private static final int CAPACITY = 4;
    private static DynamicUniformStorage<AmbiencePostUniforms> storage;

    private AmbiencePostUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            String string = "SadnesAmbiencePost";
            storage = new DynamicUniformStorage<>(string, 272, 4);
        }
    }

    public static GpuBufferSlice write(AmbiencePostUniforms ambiencePostUniforms) {
        if (storage == null) {
            init();
        }

        return storage.write(ambiencePostUniforms);
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
