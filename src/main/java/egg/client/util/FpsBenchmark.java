package egg.client.util;

import egg.client.EggClient;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public class FpsBenchmark {
    private static final FpsBenchmark INSTANCE = new FpsBenchmark();
    public static FpsBenchmark getInstance() { return INSTANCE; }

    public enum Mode {
        IDLE,
        MEASURE_CLOSED,
        MEASURE_OPEN_BASELINE,
        MEASURE_OPEN_NO_BLUR,
        MEASURE_OPEN_NO_SHADOW,
        MEASURE_OPEN_NO_TEXT,
        MEASURE_OPEN_OPTIMIZED,
        DONE
    }

    private Mode currentMode = Mode.IDLE;
    private int sampleCount = 0;
    private static final int SAMPLES_PER_STAGE = 120;
    private long lastFrameNano = 0;
    private double accumulatedFrameTimeMs = 0;

    // Results in FPS
    public double fpsClosed = 0.0;
    public double fpsOpenBaseline = 0.0;
    public double fpsOpenNoBlur = 0.0;
    public double fpsOpenNoShadow = 0.0;
    public double fpsOpenNoText = 0.0;
    public double fpsOpenOptimized = 0.0;

    // Overrides for benchmarking
    public static boolean overrideNoBlur = false;
    public static boolean overrideNoShadow = false;
    public static boolean overrideNoText = false;

    private boolean automatedRunStarted = false;

    public void startAutoBenchmark() {
        currentMode = Mode.MEASURE_CLOSED;
        sampleCount = 0;
        accumulatedFrameTimeMs = 0;
        lastFrameNano = System.nanoTime();
        automatedRunStarted = true;
        EggClient.LOGGER.info("[EggProfiler] Starting automated GUI performance benchmark...");
    }

    public void onFrame(boolean guiOpen) {
        long now = System.nanoTime();
        if (lastFrameNano == 0) {
            lastFrameNano = now;
            return;
        }
        double frameTimeMs = (now - lastFrameNano) / 1_000_000.0;
        lastFrameNano = now;

        if (currentMode == Mode.IDLE || currentMode == Mode.DONE) {
            return;
        }

        // Skip outlier frames (e.g. world load spike > 100ms)
        if (frameTimeMs > 100.0) return;

        accumulatedFrameTimeMs += frameTimeMs;
        sampleCount++;

        if (sampleCount >= SAMPLES_PER_STAGE) {
            double avgFrameTime = accumulatedFrameTimeMs / sampleCount;
            double fps = 1000.0 / avgFrameTime;

            MinecraftClient client = MinecraftClient.getInstance();

            switch (currentMode) {
                case MEASURE_CLOSED -> {
                    fpsClosed = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 1/5: GUI Closed -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    // Switch to Open Baseline
                    currentMode = Mode.MEASURE_OPEN_BASELINE;
                    overrideNoBlur = false;
                    overrideNoShadow = false;
                    overrideNoText = false;
                    if (client.currentScreen == null) {
                        client.execute(() -> client.setScreen(new egg.client.ui.ClickGuiScreen()));
                    }
                }
                case MEASURE_OPEN_BASELINE -> {
                    fpsOpenBaseline = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 2/5: GUI Open (Baseline) -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    // Switch to No Blur
                    currentMode = Mode.MEASURE_OPEN_NO_BLUR;
                    overrideNoBlur = true;
                    overrideNoShadow = false;
                    overrideNoText = false;
                }
                case MEASURE_OPEN_NO_BLUR -> {
                    fpsOpenNoBlur = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 3/5: GUI Open (No Blur) -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    // Switch to No Shadow
                    currentMode = Mode.MEASURE_OPEN_NO_SHADOW;
                    overrideNoBlur = false;
                    overrideNoShadow = true;
                    overrideNoText = false;
                }
                case MEASURE_OPEN_NO_SHADOW -> {
                    fpsOpenNoShadow = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 4/5: GUI Open (No Shadow) -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    // Switch to No Text
                    currentMode = Mode.MEASURE_OPEN_NO_TEXT;
                    overrideNoBlur = false;
                    overrideNoShadow = false;
                    overrideNoText = true;
                }
                case MEASURE_OPEN_NO_TEXT -> {
                    fpsOpenNoText = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 5/6: GUI Open (No Text) -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    // Switch to Optimized
                    currentMode = Mode.MEASURE_OPEN_OPTIMIZED;
                    overrideNoBlur = false;
                    overrideNoShadow = false;
                    overrideNoText = false;
                }
                case MEASURE_OPEN_OPTIMIZED -> {
                    fpsOpenOptimized = fps;
                    EggClient.LOGGER.info(String.format(Locale.US, "[EggProfiler] Stage 6/6: GUI Open (Optimized) -> %.1f FPS (%.2f ms)", fps, avgFrameTime));
                    currentMode = Mode.DONE;
                    printFinalReport();
                }
                default -> {}
            }

            sampleCount = 0;
            accumulatedFrameTimeMs = 0;
        }
    }

    private void printFinalReport() {
        EggClient.LOGGER.info("================== [EGG GUI PROFILER REPORT] ==================");
        EggClient.LOGGER.info(String.format(Locale.US, "  1. GUI Closed:              %6.1f FPS (%5.2f ms)", fpsClosed, 1000.0 / Math.max(1, fpsClosed)));
        EggClient.LOGGER.info(String.format(Locale.US, "  2. GUI Open (Baseline):     %6.1f FPS (%5.2f ms)  [Δ: -%.1f FPS]", fpsOpenBaseline, 1000.0 / Math.max(1, fpsOpenBaseline), fpsClosed - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  3. GUI Open (No Blur):      %6.1f FPS (%5.2f ms)  [Gain: +%.1f FPS]", fpsOpenNoBlur, 1000.0 / Math.max(1, fpsOpenNoBlur), fpsOpenNoBlur - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  4. GUI Open (No Shadow):    %6.1f FPS (%5.2f ms)  [Gain: +%.1f FPS]", fpsOpenNoShadow, 1000.0 / Math.max(1, fpsOpenNoShadow), fpsOpenNoShadow - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  5. GUI Open (No Text):      %6.1f FPS (%5.2f ms)  [Gain: +%.1f FPS]", fpsOpenNoText, 1000.0 / Math.max(1, fpsOpenNoText), fpsOpenNoText - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  6. GUI Open (Optimized):    %6.1f FPS (%5.2f ms)  [+%.1f FPS vs Baseline!]", fpsOpenOptimized, 1000.0 / Math.max(1, fpsOpenOptimized), fpsOpenOptimized - fpsOpenBaseline));
        EggClient.LOGGER.info("---------------------------------------------------------------");
        EggClient.LOGGER.info("  Culprit Breakdown:");
        EggClient.LOGGER.info(String.format(Locale.US, "  - Fullscreen Blur:          ~%.1f FPS impact (separable Gaussian post-process)", fpsOpenNoBlur - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  - Text Allocations & Draw:  ~%.1f FPS impact (glyph quads & dynamic Text objects)", fpsOpenNoText - fpsOpenBaseline));
        EggClient.LOGGER.info(String.format(Locale.US, "  - Shadows & Slices:         ~%.1f FPS impact (overdrawn 9-slice quads)", fpsOpenNoShadow - fpsOpenBaseline));
        EggClient.LOGGER.info("===============================================================");
    }
}
