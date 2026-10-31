package egg.client.render.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

public final class FrameBufferPool {
    private static final Deque<GpuBuffer> currentFrame = new ArrayDeque<>();
    private static final Deque<GpuBuffer> previousFrame = new ArrayDeque<>();

    private FrameBufferPool() {
    }

    public static GpuBuffer createBuffer(Supplier<String> supplier, ByteBuffer byteBuffer) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(supplier, 40, byteBuffer);
        currentFrame.addLast(gpuBuffer);
        return gpuBuffer;
    }

    public static void endFrame() {
        RenderSystem.assertOnRenderThread();

        while (!previousFrame.isEmpty()) {
            GpuBuffer gpuBuffer = previousFrame.pollFirst();
            if (gpuBuffer != null && !gpuBuffer.isClosed()) {
                gpuBuffer.close();
            }
        }

        previousFrame.addAll(currentFrame);
        currentFrame.clear();
    }

    public static void closeAll() {
        RenderSystem.assertOnRenderThread();

        while (!currentFrame.isEmpty()) {
            GpuBuffer gpuBuffer = currentFrame.pollFirst();
            if (gpuBuffer != null && !gpuBuffer.isClosed()) {
                gpuBuffer.close();
            }
        }

        while (!previousFrame.isEmpty()) {
            GpuBuffer gpuBuffer2 = previousFrame.pollFirst();
            if (gpuBuffer2 != null && !gpuBuffer2.isClosed()) {
                gpuBuffer2.close();
            }
        }
    }
}
