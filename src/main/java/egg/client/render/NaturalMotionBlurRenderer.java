package egg.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import egg.client.mixin.PostEffectPassAccessor;
import egg.client.mixin.PostEffectProcessorAccessor;
import egg.client.module.ModuleManager;
import egg.client.module.render.MotionBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.util.List;
import java.util.Map;

public enum NaturalMotionBlurRenderer {
    INSTANCE;

    private static final int UBO_SIZE = 304;
    private static final long REFRESH_CHECK_INTERVAL_NS = 2_000_000_000L;
    private static final Identifier PRE_ID = Identifier.of("egg", "natural_velocity_pre");
    private static final Identifier F5_ID = Identifier.of("egg", "natural_velocity_f5");
    private static final Identifier POST_ID = Identifier.of("egg", "natural_velocity_post");

    private final Matrix4f prevModelView = new Matrix4f();
    private final Matrix4f prevProjection = new Matrix4f();
    private final Matrix4f scratchModelView = new Matrix4f();
    private final Matrix4f scratchProjection = new Matrix4f();
    private final Matrix4f mvInverse = new Matrix4f();
    private final Matrix4f projInverse = new Matrix4f();
    private final Matrix4f framePrevModelView = new Matrix4f();
    private final Matrix4f framePrevProjection = new Matrix4f();

    private final ManagedUbo preEntityUbo = new ManagedUbo("EggNaturalPreEntityUBO", UBO_SIZE);
    private final ManagedUbo f5EntityUbo = new ManagedUbo("EggNaturalF5EntityUBO", UBO_SIZE);
    private final ManagedUbo postRenderUbo = new ManagedUbo("EggNaturalPostRenderUBO", UBO_SIZE);

    private ObjectAllocator frameAllocator;
    private ClientWorld lastWorld;
    private double prevCamX;
    private double prevCamY;
    private double prevCamZ;
    private float camDx;
    private float camDy;
    private float camDz;
    private boolean previousFrameReady;

    private long lastFrameNano;
    private float currentFps;
    private long lastMonitorHandle;
    private int lastRefreshRate = 60;
    private long lastRefreshCheckNano;

    public void onWorldRenderHead(ObjectAllocator allocator, Camera camera, Matrix4f modelView, Matrix4f projection) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d pos = camera.getCameraPos();
        double cx = pos.x;
        double cy = pos.y;
        double cz = pos.z;

        if (mc == null || mc.world != this.lastWorld) {
            this.previousFrameReady = false;
            this.lastWorld = mc != null ? mc.world : null;
        }

        if (!isActive(mc)) {
            this.frameAllocator = null;
            rememberFrameState(modelView, projection, cx, cy, cz);
            return;
        }

        this.frameAllocator = allocator;
        beginFrameTiming(mc);

        this.scratchModelView.set(modelView);
        this.scratchProjection.set(projection);

        if (!this.previousFrameReady) {
            setCameraFrame(this.scratchModelView, this.scratchModelView, this.scratchProjection, this.scratchProjection, 0.0f, 0.0f, 0.0f);
            rememberFrameState(this.scratchModelView, this.scratchProjection, cx, cy, cz);
            return;
        }

        float dx = (float) (cx - this.prevCamX);
        float dy = (float) (cy - this.prevCamY);
        float dz = (float) (cz - this.prevCamZ);
        if (dx * dx + dy * dy + dz * dz > 100.0f) {
            setCameraFrame(this.scratchModelView, this.scratchModelView, this.scratchProjection, this.scratchProjection, 0.0f, 0.0f, 0.0f);
            rememberFrameState(this.scratchModelView, this.scratchProjection, cx, cy, cz);
            return;
        }

        setCameraFrame(this.scratchModelView, this.prevModelView, this.scratchProjection, this.prevProjection, dx, dy, dz);
        rememberFrameState(this.scratchModelView, this.scratchProjection, cx, cy, cz);
    }

    public void onBeforeEntities() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isActive(mc) || this.frameAllocator == null) {
            return;
        }
        if (shouldUseSpecialSingleBlur(mc)) {
            applyPass(mc, F5_ID, "PreEntityBlurUniforms", this.f5EntityUbo);
        } else {
            applyPass(mc, PRE_ID, "PreEntityBlurUniforms", this.preEntityUbo);
        }
    }

    public void onWorldRenderTail() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isActive(mc) || this.frameAllocator == null) {
            return;
        }
        if (!shouldUseSpecialSingleBlur(mc)) {
            applyPass(mc, POST_ID, "PostRenderBlurUniforms", this.postRenderUbo);
        }
    }

    public void clearFrameAllocator() {
        this.frameAllocator = null;
    }

    public void reset() {
        this.frameAllocator = null;
        this.previousFrameReady = false;
        this.lastWorld = null;
        this.lastFrameNano = 0L;
        this.currentFps = 0.0f;
        this.preEntityUbo.reset();
        this.f5EntityUbo.reset();
        this.postRenderUbo.reset();
    }

    private boolean isActive(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return false;
        }
        MotionBlur module = ModuleManager.getInstance().getModule(MotionBlur.class);
        if (module == null || !module.isEnabled() || !module.getMode().is("Natural")) {
            return false;
        }
        if (module.getDisableInGui().isEnabled() && mc.currentScreen != null) {
            return false;
        }
        return module.getStrength().getValue() > 0.0;
    }

    private boolean shouldUseSpecialSingleBlur(MinecraftClient mc) {
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            return true;
        }
        return mc.player != null && mc.player.hasVehicle();
    }

    private void rememberFrameState(Matrix4fc modelView, Matrix4fc projection, double cx, double cy, double cz) {
        this.prevModelView.set(modelView);
        this.prevProjection.set(projection);
        this.prevCamX = cx;
        this.prevCamY = cy;
        this.prevCamZ = cz;
        this.previousFrameReady = true;
    }

    private void setCameraFrame(
            Matrix4f currentModelView,
            Matrix4f previousModelView,
            Matrix4f currentProjection,
            Matrix4f previousProjection,
            float dx,
            float dy,
            float dz
    ) {
        currentModelView.invert(this.mvInverse);
        currentProjection.invert(this.projInverse);
        this.framePrevModelView.set(previousModelView);
        this.framePrevProjection.set(previousProjection);
        this.camDx = dx;
        this.camDy = dy;
        this.camDz = dz;
    }

    private void applyPass(MinecraftClient mc, Identifier effectId, String uniformBlockName, ManagedUbo ubo) {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) {
            return;
        }

        MotionBlur module = ModuleManager.getInstance().getModule(MotionBlur.class);
        if (module == null) {
            return;
        }

        PostEffectProcessor processor;
        try {
            processor = mc.getShaderLoader().loadPostEffect(effectId, DefaultFramebufferSet.MAIN_ONLY);
        } catch (Throwable t) {
            return;
        }
        if (processor == null) {
            return;
        }

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).egg$getPasses();
        if (passes == null || passes.isEmpty()) {
            return;
        }

        Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) passes.getFirst()).egg$getUniformBuffers();
        if (uniformBuffers == null || !uniformBuffers.containsKey(uniformBlockName)) {
            return;
        }

        float baseStrength = (float) (module.getStrength().getValue() / 5.0);
        boolean scaleHz = module.getRefreshRateScaling().isEnabled();
        float effectiveStrength;
        int sampleCount;
        if (!scaleHz) {
            effectiveStrength = baseStrength;
            sampleCount = 100;
        } else {
            float ratio = this.lastRefreshRate > 0 ? this.currentFps / (float) this.lastRefreshRate : 1.0f;
            if (ratio < 1.0f) {
                ratio = 1.0f;
            }
            effectiveStrength = baseStrength * ratio;
            sampleCount = ratio > 1.0f ? (int) (100.0f * ratio) : 100;
        }
        int blurProfile = module.getNaturalProfile().is("Weighted") ? 1 : 0;

        try {
            GpuBuffer buffer = ubo.put(processor, uniformBuffers, uniformBlockName);
            try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, false, true)) {
                Std140Builder builder = Std140Builder.intoBuffer(view.data());
                builder.putMat4f(this.mvInverse);
                builder.putMat4f(this.projInverse);
                builder.putMat4f(this.framePrevModelView);
                builder.putMat4f(this.framePrevProjection);
                builder.putVec3(this.camDx, this.camDy, this.camDz);
                builder.putVec2((float) fb.textureWidth, (float) fb.textureHeight);
                builder.putFloat(effectiveStrength);
                builder.putInt(sampleCount);
                builder.putInt(blurProfile);
                builder.putInt(1);
            }
            processor.render(fb, this.frameAllocator);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("closed")) {
                ubo.reset();
                return;
            }
            throw ex;
        }
    }

    private void beginFrameTiming(MinecraftClient mc) {
        long now = System.nanoTime();
        if (this.lastFrameNano != 0L) {
            float dt = (float) (now - this.lastFrameNano) / 1.0E9f;
            this.currentFps = (dt > 0.0f && dt < 1.0f) ? (1.0f / dt) : 0.0f;
        } else {
            this.currentFps = 0.0f;
        }
        this.lastFrameNano = now;

        if (now - this.lastRefreshCheckNano >= REFRESH_CHECK_INTERVAL_NS) {
            this.lastRefreshCheckNano = now;
            updateRefreshRate(mc);
        }
    }

    private void updateRefreshRate(MinecraftClient mc) {
        try {
            long windowHandle = mc.getWindow().getHandle();
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();
            long monitor = getMonitorFromWindowPosition(windowHandle, winW, winH);
            if (monitor != this.lastMonitorHandle && monitor != 0L) {
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                this.lastRefreshRate = mode != null && mode.refreshRate() > 0 ? mode.refreshRate() : 60;
                this.lastMonitorHandle = monitor;
            }
        } catch (Throwable ignored) {
            this.lastRefreshRate = 60;
        }
    }

    private static long getMonitorFromWindowPosition(long windowHandle, int winW, int winH) {
        int[] wx = new int[1];
        int[] wy = new int[1];
        GLFW.glfwGetWindowPos(windowHandle, wx, wy);
        int cx = wx[0] + winW / 2;
        int cy = wy[0] + winH / 2;
        long best = GLFW.glfwGetPrimaryMonitor();
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null) {
            for (int i = 0; i < monitors.limit(); i++) {
                long monitor = monitors.get(i);
                int[] mx = new int[1];
                int[] my = new int[1];
                GLFW.glfwGetMonitorPos(monitor, mx, my);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode == null) {
                    continue;
                }
                if (cx >= mx[0] && cx < mx[0] + mode.width() && cy >= my[0] && cy < my[0] + mode.height()) {
                    best = monitor;
                    break;
                }
            }
        }
        return best;
    }

    private static final class ManagedUbo {
        private final String debugName;
        private final int sizeBytes;
        private PostEffectProcessor owner;
        private GpuBuffer buffer;

        private ManagedUbo(String debugName, int sizeBytes) {
            this.debugName = debugName;
            this.sizeBytes = sizeBytes;
        }

        private GpuBuffer put(PostEffectProcessor processor, Map<String, GpuBuffer> uniformBuffers, String key) {
            GpuBuffer target = get(processor);
            GpuBuffer existing = uniformBuffers.get(key);
            if (existing == target) {
                return target;
            }
            GpuBuffer previous = uniformBuffers.put(key, target);
            if (previous != null && previous != target) {
                closeQuietly(previous);
            }
            return target;
        }

        private GpuBuffer get(PostEffectProcessor processor) {
            if (processor != this.owner || (this.buffer != null && this.buffer.isClosed())) {
                reset();
                this.owner = processor;
            }
            if (this.buffer == null) {
                this.buffer = RenderSystem.getDevice().createBuffer(
                        () -> this.debugName,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                        this.sizeBytes
                );
            }
            return this.buffer;
        }

        private void reset() {
            closeQuietly(this.buffer);
            this.owner = null;
            this.buffer = null;
        }

        private static void closeQuietly(GpuBuffer buf) {
            if (buf == null) {
                return;
            }
            try {
                if (!buf.isClosed()) {
                    buf.close();
                }
            } catch (RuntimeException ignored) {
            }
        }
    }
}
