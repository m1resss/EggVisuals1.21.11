package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class CausticUniformStorage {
    private static final int CAPACITY = 64;
    private static DynamicUniformStorage<CausticUniforms> storage;

    private CausticUniformStorage() {
    }

    public static void init() {
        if (storage == null) {
            String string = "CausticUniforms";
            storage = new DynamicUniformStorage<>(string, CausticUniforms.BLOCK_SIZE, CAPACITY);
        }
    }

    public static GpuBufferSlice write(CausticUniforms uniforms) {
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
