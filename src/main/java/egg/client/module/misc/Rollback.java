package egg.client.module.misc;

import com.mojang.blaze3d.textures.GpuTexture;
import egg.client.EggClient;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BindSetting;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.msdf.Draw2DProcessor;
import egg.client.render.msdf.Font;
import egg.client.render.msdf.Fonts;
import egg.client.render.msdf.pipeline.DrawBatcher;
import egg.client.render.shader.GlStateSnapshot;
import egg.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class Rollback extends Module {

    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int TS_PACKET_SIZE = 188;
    private static Rollback instance;

    private final SliderSetting durationSec = new SliderSetting("Длительность (сек)", 30.0, 10.0, 90.0, 5.0,
            "Сколько последних секунд сохранять в откат (до 1.5 минут)");
    private final BindSetting saveBind = new BindSetting("Сохранить откат", GLFW.GLFW_KEY_F9, this::saveRollback,
            "Клавиша для мгновенного сохранения отката");
    private final ModeSetting fpsMode = new ModeSetting("FPS", "60", "Частота кадров записи", "30", "60");
    private final ModeSetting resolutionMode = new ModeSetting("Разрешение", "720p", "Разрешение видео", "720p", "1080p", "Родное");
    private final ModeSetting qualityMode = new ModeSetting("Качество", "Высокое", "Качество сжатия видео", "Среднее", "Высокое", "Ультра");
    private final ModeSetting encoderMode = new ModeSetting("Кодировщик", "Авто (GPU/CPU)", "Кодек FFmpeg", "Авто (GPU/CPU)", "CPU (x264)", "NVIDIA (NVENC)");
    private final BooleanSetting showToast = new BooleanSetting("Тост в HUD", true, "Уведомление при сохранении как в GeForce Experience");
    private final BooleanSetting showIndicator = new BooleanSetting("Индикатор записи", true, "Компактный значок активного буфера отката");
    private final ButtonSetting saveNowButton = new ButtonSetting("Сохранить сейчас", "Сохранить текущий буфер отката в MP4", this::saveRollback);
    private final ButtonSetting openFolderButton = new ButtonSetting("Открыть папку", "Открыть папку с сохранёнными откатами", this::openOutputFolder);

    private static final int PBO_COUNT = 3;
    private static final int MAILBOX_SLOTS = 3;

    private int readSrcFbo;
    private int attachedSrcTexId;
    private int attachedSrcW;
    private int attachedSrcH;
    private int downscaleFbo;
    private int downscaleTex;
    private final int[] pboIds = new int[PBO_COUNT];
    private final long[] pboSyncs = new long[PBO_COUNT];
    private final ByteBuffer[] pboMapped = new ByteBuffer[PBO_COUNT];
    private final boolean[] pboPrimed = new boolean[PBO_COUNT];
    private boolean persistentPboMode;
    private int pboWriteIdx;
    private int glWidth;
    private int glHeight;

    private final Object mailboxLock = new Object();
    private byte[][] mailboxFrames;
    private int mailboxWidth;
    private int mailboxHeight;
    private int latestReadySlot = -1;
    private int feederInUseSlot = -1;
    private long nextCaptureNanos;

    private final Object ringLock = new Object();
    private final ArrayDeque<GopChunk> gopRing = new ArrayDeque<>();
    private final ByteArrayOutputStream currentGop = new ByteArrayOutputStream(256 * 1024);
    private byte[] lastPatPacket;
    private byte[] lastPmtPacket;
    private int pmtPid = 0x1000;
    private boolean currentGopHasKey;
    private long currentGopStartNanos;
    private volatile long ringFirstStartNanos;

    private final Object sessionLock = new Object();
    private volatile boolean sessionRunning;
    private Process encoderProcess;
    private OutputStream encoderStdin;
    private Thread feederThread;
    private Thread readerThread;
    private int activeWidth;
    private int activeHeight;
    private int activeFps;
    private String activeQuality;
    private String activeEncoderChoice;

    private volatile String cachedFfmpegPath;
    private final Map<String, Boolean> encoderSupportCache = new ConcurrentHashMap<>();
    private volatile boolean startingSession;
    private volatile boolean savingInProgress;
    private volatile long shutdownHookEpoch;

    private volatile String toastTitle = "";
    private volatile String toastSub = "";
    private volatile boolean toastError = false;
    private volatile long toastStartNanos = 0L;
    private volatile long toastUntilNanos = 0L;
    private float toastAnim = 0.0f;
    private long lastOverlayNanos = 0L;

    public Rollback() {
        super("Rollback", "Фоновая запись последних до 1.5 минут геймплея с сохранением по бинду", Category.MISC);
        instance = this;
        addSettings(durationSec, saveBind, fpsMode, resolutionMode, qualityMode, encoderMode,
                showToast, showIndicator, saveNowButton, openFolderButton);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownHookEpoch++;
            stopEncoderSession();
        }, "Rollback-Shutdown"));
    }

    public static Rollback getInstance() {
        return instance;
    }

    public boolean handleGlobalKey(int key) {
        if (isEnabled() && saveBind.getKey() != 0 && saveBind.getKey() == key) {
            saveRollback();
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        nextCaptureNanos = 0L;
        synchronized (mailboxLock) {
            latestReadySlot = -1;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null) {
            triggerToast("Откат активен", "Буфер " + Math.round(durationSec.get()) + "с • " + saveBind.getKeyName(), false);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stopEncoderSession();
        cleanupGlPending();
        synchronized (mailboxLock) {
            latestReadySlot = -1;
            feederInUseSlot = -1;
            mailboxFrames = null;
            mailboxWidth = 0;
            mailboxHeight = 0;
        }
        ringFirstStartNanos = 0L;
        synchronized (ringLock) {
            gopRing.clear();
            currentGop.reset();
            currentGopHasKey = false;
        }
    }

    public void captureFrame(MinecraftClient mc) {
        if (!isEnabled() || mc == null || mc.getWindow() == null) return;
        if (!GlStateSnapshot.isOpenGl()) return;
        if (mc.getOverlay() != null) return;

        Framebuffer fb = mc.getFramebuffer();
        if (fb == null) return;
        GpuTexture colorAttachment = fb.getColorAttachment();
        if (!(colorAttachment instanceof GlTexture glTex)) return;
        int srcTexId = glTex.getGlId();
        int srcW = fb.textureWidth;
        int srcH = fb.textureHeight;
        if (srcTexId <= 0 || srcW < 16 || srcH < 16) return;

        int targetFps = getTargetFps();
        long frameIntervalNanos = 1_000_000_000L / targetFps;
        long now = System.nanoTime();
        if (nextCaptureNanos != 0L && now + 1_500_000L < nextCaptureNanos) {
            return;
        }
        if (nextCaptureNanos == 0L || now - nextCaptureNanos > frameIntervalNanos * 2L) {
            nextCaptureNanos = now + frameIntervalNanos;
        } else {
            nextCaptureNanos += frameIntervalNanos;
        }

        int[] targetDims = computeRecordingResolution(srcW, srcH);
        int recW = targetDims[0];
        int recH = targetDims[1];

        ensureEncoderSession(recW, recH, targetFps);
        if (!sessionRunning) return;

        if (!ensureGlResources(recW, recH)) return;

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevPackPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevPackAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);

        try {
            for (int step = 1; step < PBO_COUNT; step++) {
                int readIdx = (pboWriteIdx + step) % PBO_COUNT;
                if (!pboPrimed[readIdx]) continue;
                if (isSyncReady(readIdx)) {
                    copyPboToMailbox(readIdx, recW, recH);
                    pboPrimed[readIdx] = false;
                }
            }

            if (pboPrimed[pboWriteIdx]) {
                if (!isSyncReady(pboWriteIdx)) {
                    return;
                }
                copyPboToMailbox(pboWriteIdx, recW, recH);
                pboPrimed[pboWriteIdx] = false;
            }

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readSrcFbo);
            if (attachedSrcTexId != srcTexId || attachedSrcW != srcW || attachedSrcH != srcH) {
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcTexId, 0);
                attachedSrcTexId = srcTexId;
                attachedSrcW = srcW;
                attachedSrcH = srcH;
            }

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downscaleFbo);
            int filter = (srcW == recW && srcH == recH) ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL30.glBlitFramebuffer(0, srcH, srcW, 0, 0, 0, recW, recH, GL11.GL_COLOR_BUFFER_BIT, filter);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, downscaleFbo);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[pboWriteIdx]);
            GL11.glReadPixels(0, 0, recW, recH, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);

            pboSyncs[pboWriteIdx] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            pboPrimed[pboWriteIdx] = true;
            pboWriteIdx = (pboWriteIdx + 1) % PBO_COUNT;
        } catch (Throwable t) {
            EggClient.LOGGER.warn("[Rollback] GL capture error", t);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPackPbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevPackAlign);
        }
    }

    private boolean isSyncReady(int idx) {
        long sync = pboSyncs[idx];
        if (sync == 0L) {
            return true;
        }
        int status = GL32.glClientWaitSync(sync, 0, 0L);
        if (status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED) {
            GL32.glDeleteSync(sync);
            pboSyncs[idx] = 0L;
            return true;
        }
        return false;
    }

    private void copyPboToMailbox(int slotIdx, int w, int h) {
        int byteSize = w * h * 4;
        byte[][] frames;
        int writeSlot = -1;
        synchronized (mailboxLock) {
            if (mailboxFrames == null || mailboxWidth != w || mailboxHeight != h) {
                mailboxFrames = new byte[MAILBOX_SLOTS][byteSize];
                mailboxWidth = w;
                mailboxHeight = h;
                latestReadySlot = -1;
                feederInUseSlot = -1;
            }
            frames = mailboxFrames;
            for (int i = 0; i < MAILBOX_SLOTS; i++) {
                if (i != latestReadySlot && i != feederInUseSlot) {
                    writeSlot = i;
                    break;
                }
            }
        }
        if (writeSlot < 0) {
            return;
        }
        byte[] dst = frames[writeSlot];
        boolean copied = false;
        if (persistentPboMode) {
            ByteBuffer mapped = pboMapped[slotIdx];
            if (mapped != null && mapped.capacity() >= byteSize) {
                mapped.position(0);
                mapped.get(dst, 0, byteSize);
                copied = true;
            }
        } else {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[slotIdx]);
            ByteBuffer mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, byteSize, GL30.GL_MAP_READ_BIT);
            if (mapped != null) {
                mapped.get(dst, 0, byteSize);
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                copied = true;
            }
        }
        if (copied) {
            synchronized (mailboxLock) {
                if (mailboxFrames == frames && mailboxWidth == w && mailboxHeight == h) {
                    latestReadySlot = writeSlot;
                }
            }
        }
    }

    public void renderOverlay(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) return;
        long now = System.nanoTime();
        float dt = lastOverlayNanos == 0L ? 0.016f : Math.clamp((now - lastOverlayNanos) / 1_000_000_000.0f, 0.001f, 0.05f);
        lastOverlayNanos = now;

        boolean toastVisible = showToast.isEnabled() && now < toastUntilNanos && toastTitle != null && !toastTitle.isEmpty();
        toastAnim = MathHelper.lerp(1.0f - (float) Math.exp(-dt * 14.0f), toastAnim, toastVisible ? 1.0f : 0.0f);
        if (!toastVisible && toastAnim < 0.005f) {
            toastAnim = 0.0f;
        }

        boolean drawRec = isEnabled() && showIndicator.isEnabled() && sessionRunning && mc.world != null;
        if (toastAnim <= 0.005f && !drawRec) return;

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        boolean prevBatch = DrawBatcher.isEnabled();
        DrawBatcher.setEnabledSilent(true);
        try {
            if (drawRec) {
                renderIndicator(sw, sh, now);
            }
            if (toastAnim > 0.005f) {
                renderToastCard(sw);
            }
            DrawBatcher.flushPending();
        } finally {
            DrawBatcher.setEnabledSilent(prevBatch);
        }
    }

    private void renderIndicator(float sw, float sh, long now) {
        double bufferedSec = getBufferedSeconds(now);
        String label = savingInProgress
                ? "сохранение..."
                : String.format(Locale.US, "откат · %.0f/%dс", Math.min(bufferedSec, durationSec.get()), Math.round(durationSec.get()));
        float tw = Fonts.sfRegular.a(label, 7.3f);
        float w = 15.0f + tw + 7.0f;
        float h = 15.0f;
        float x = sw - w - 10.0f;
        float y = sh - h - 10.0f;

        Draw2DProcessor.INSTANCE.drawRect(null, x, y, w, h, h * 0.5f, ColorUtil.rgba(14, 16, 22, 185));
        Draw2DProcessor.INSTANCE.drawOutline(null, x, y, w, h, h * 0.5f, 0.8f, ColorUtil.rgba(46, 52, 68, 220));

        float pulse = 0.7f + 0.3f * (float) Math.sin((now / 1_000_000_000.0) * 3.5);
        int dotCol = savingInProgress
                ? 0xFFFBBF24
                : (bufferedSec >= 2.0 ? ColorUtil.withAlphaFloat(0xFF4ADE80, pulse) : 0xFF94A3B8);
        Draw2DProcessor.INSTANCE.drawRect(null, x + 6.0f, y + (h - 4.5f) * 0.5f, 4.5f, 4.5f, 2.25f, dotCol);

        float ty = Fonts.sfRegular.a(7.3f, y + h * 0.5f);
        Fonts.sfRegular.a(null, label, x + 13.5f, ty, 7.3f, 0xFFD5DCE9);
    }

    private void renderToastCard(float sw) {
        float t = MathHelper.clamp(toastAnim, 0.0f, 1.0f);
        float ease = t * t * (3.0f - 2.0f * t);

        float titleW = Fonts.b.a(toastTitle, 8.6f);
        float subW = toastSub != null && !toastSub.isEmpty() ? Fonts.sfRegular.a(toastSub, 7.4f) : 0.0f;
        float w = Math.max(146.0f, Math.max(titleW, subW) + 28.0f);
        float h = subW > 0.0f ? 32.0f : 22.0f;
        float x = sw - 12.0f - w * ease;
        float y = 12.0f;

        Draw2DProcessor.INSTANCE.drawRoundedBlur(x, y, w, h, 7.0f, ease);
        Draw2DProcessor.INSTANCE.drawRect(null, x, y, w, h, 7.0f,
                ColorUtil.rgba(14, 16, 22, Math.round(224 * ease)));
        int brdCol = toastError
                ? ColorUtil.rgba(145, 58, 68, Math.round(240 * ease))
                : ColorUtil.rgba(52, 60, 78, Math.round(240 * ease));
        Draw2DProcessor.INSTANCE.drawOutline(null, x, y, w, h, 7.0f, 0.9f, brdCol);

        int accentDot = toastError ? 0xFFFF6670 : 0xFF4ADE80;
        Draw2DProcessor.INSTANCE.drawRect(null, x + 9.0f, y + 8.5f, 5.0f, 5.0f, 2.5f,
                ColorUtil.withAlphaFloat(accentDot, ease));

        Font titleFont = Fonts.b;
        titleFont.a(null, toastTitle, x + 19.0f, y + 6.5f, 8.6f, ColorUtil.withAlphaFloat(0xFFF2F5FA, ease));
        if (subW > 0.0f) {
            Fonts.sfRegular.a(null, toastSub, x + 19.0f, y + 18.0f, 7.4f, ColorUtil.withAlphaFloat(0xFF9AA6BE, ease));
        }
    }

    public void saveRollback() {
        if (!isEnabled()) {
            triggerToast("Модуль выключен", "Включите Rollback для записи", true);
            return;
        }
        String ff = resolveFfmpegPath();
        if (ff == null) {
            triggerToast("FFmpeg не найден", "Установите FFmpeg для сохранения", true);
            return;
        }
        if (savingInProgress) {
            triggerToast("Уже сохраняется", "Подождите завершения экспорта", false);
            return;
        }

        List<byte[]> segments = new ArrayList<>();
        long now = System.nanoTime();
        long wantedNanos = Math.round(durationSec.get() * 1_000_000_000.0);
        long cutoffNanos = now - wantedNanos;
        long earliestChunkTime = Long.MAX_VALUE;
        long totalBytes = 0L;

        synchronized (ringLock) {
            for (GopChunk chunk : gopRing) {
                if (chunk.endNanos >= cutoffNanos) {
                    segments.add(chunk.data);
                    totalBytes += chunk.data.length;
                    if (chunk.startNanos < earliestChunkTime) {
                        earliestChunkTime = chunk.startNanos;
                    }
                }
            }
            if (segments.isEmpty() && !gopRing.isEmpty()) {
                GopChunk last = gopRing.peekLast();
                segments.add(last.data);
                totalBytes += last.data.length;
                earliestChunkTime = last.startNanos;
            }
            if (currentGopHasKey && currentGop.size() >= TS_PACKET_SIZE) {
                byte[] tail = currentGop.toByteArray();
                segments.add(tail);
                totalBytes += tail.length;
                if (currentGopStartNanos < earliestChunkTime) {
                    earliestChunkTime = currentGopStartNanos;
                }
            }
        }

        if (segments.isEmpty() || totalBytes < TS_PACKET_SIZE) {
            triggerToast("Буфер пуст", "Подождите пару секунд после включения", true);
            return;
        }

        double actualSec = earliestChunkTime == Long.MAX_VALUE
                ? 1.0
                : Math.max(1.0, (now - earliestChunkTime) / 1_000_000_000.0);

        savingInProgress = true;
        triggerToast("Сохранение отката", String.format(Locale.US, "Подготовка %.0fс клипа...", actualSec), false);

        Thread saveWorker = new Thread(() -> {
            Path outDir = getOutputDirectory();
            String fileName = "rollback_" + FILE_TIME_FMT.format(LocalDateTime.now()) + ".mp4";
            Path outFile = outDir.resolve(fileName);
            try {
                Files.createDirectories(outDir);
                ProcessBuilder pb = new ProcessBuilder(
                        ff, "-y", "-hide_banner", "-loglevel", "error",
                        "-f", "mpegts", "-i", "-",
                        "-c", "copy", "-movflags", "+faststart",
                        outFile.toString()
                );
                pb.redirectErrorStream(true);
                Process mux = pb.start();
                try (OutputStream os = mux.getOutputStream()) {
                    for (byte[] seg : segments) {
                        os.write(seg);
                    }
                    os.flush();
                }
                StringBuilder muxErr = new StringBuilder();
                try (InputStream err = mux.getInputStream()) {
                    byte[] tmp = new byte[512];
                    int n;
                    while ((n = err.read(tmp)) != -1) {
                        muxErr.append(new String(tmp, 0, n));
                    }
                }
                boolean finished = mux.waitFor(20, TimeUnit.SECONDS);
                if (!finished) {
                    mux.destroyForcibly();
                    throw new IOException("FFmpeg mux timeout");
                }
                if (mux.exitValue() != 0 || !Files.exists(outFile) || Files.size(outFile) == 0L) {
                    throw new IOException("FFmpeg mux failed (" + mux.exitValue() + "): " + muxErr);
                }

                double mb = Files.size(outFile) / (1024.0 * 1024.0);
                String sub = String.format(Locale.US, "%s • %.0fс • %.1f МБ", fileName, actualSec, mb);
                triggerToast("Откат сохранён", sub, false);
                sendChatMessage("Откат сохранён: " + fileName + String.format(Locale.US, " (%.0fс, %.1f МБ)", actualSec, mb), Formatting.GREEN);
            } catch (Exception e) {
                EggClient.LOGGER.warn("[Rollback] Save failed", e);
                triggerToast("Ошибка сохранения", e.getMessage() != null ? e.getMessage() : "Сбой FFmpeg", true);
                sendChatMessage("Ошибка сохранения отката: " + e.getMessage(), Formatting.RED);
            } finally {
                savingInProgress = false;
            }
        }, "Rollback-Saver");
        saveWorker.setDaemon(true);
        saveWorker.start();
    }

    private void openOutputFolder() {
        try {
            Path dir = getOutputDirectory();
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toFile());
        } catch (Exception e) {
            EggClient.LOGGER.warn("[Rollback] Failed to open folder", e);
        }
    }

    private Path getOutputDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("rollbacks");
    }

    private void triggerToast(String title, String sub, boolean error) {
        long now = System.nanoTime();
        this.toastTitle = title;
        this.toastSub = sub;
        this.toastError = error;
        this.toastStartNanos = now;
        this.toastUntilNanos = now + 3_400_000_000L;
    }

    private void sendChatMessage(String msg, Formatting color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        mc.execute(() -> {
            if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
                mc.inGameHud.getChatHud().addMessage(
                        Text.literal("[Rollback] ").formatted(Formatting.AQUA).append(Text.literal(msg).formatted(color))
                );
            }
        });
    }

    private double getBufferedSeconds(long now) {
        long firstStart = ringFirstStartNanos;
        if (firstStart <= 0L || firstStart == Long.MAX_VALUE) {
            return 0.0;
        }
        return Math.max(0.0, (now - firstStart) / 1_000_000_000.0);
    }

    private int getTargetFps() {
        return fpsMode.is("30") ? 30 : 60;
    }

    private int[] computeRecordingResolution(int srcW, int srcH) {
        int dstH;
        int dstW;
        if (resolutionMode.is("720p")) {
            dstH = Math.min(720, srcH);
            dstW = (int) Math.round(srcW * (dstH / (double) srcH));
        } else if (resolutionMode.is("1080p")) {
            dstH = Math.min(1080, srcH);
            dstW = (int) Math.round(srcW * (dstH / (double) srcH));
        } else {
            dstW = srcW;
            dstH = srcH;
        }
        dstW = Math.max(64, dstW & ~1);
        dstH = Math.max(64, dstH & ~1);
        return new int[]{dstW, dstH};
    }

    private boolean ensureGlResources(int w, int h) {
        if (readSrcFbo != 0 && downscaleFbo != 0 && downscaleTex != 0 && pboIds[0] != 0 && glWidth == w && glHeight == h) {
            return true;
        }
        cleanupGlResources();

        readSrcFbo = GL30.glGenFramebuffers();
        attachedSrcTexId = 0;
        attachedSrcW = 0;
        attachedSrcH = 0;
        downscaleFbo = GL30.glGenFramebuffers();
        downscaleTex = GL11.glGenTextures();

        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, downscaleTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downscaleFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, downscaleTex, 0);

        int byteSize = w * h * 4;
        boolean canPersistent = GL.getCapabilities().OpenGL44;
        boolean persistentOk = canPersistent;
        if (canPersistent) {
            int mapFlags = GL30.GL_MAP_READ_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
            int storageFlags = mapFlags | GL44.GL_CLIENT_STORAGE_BIT;
            for (int i = 0; i < PBO_COUNT; i++) {
                pboIds[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
                GL44.glBufferStorage(GL21.GL_PIXEL_PACK_BUFFER, byteSize, storageFlags);
                pboMapped[i] = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, byteSize, mapFlags);
                pboSyncs[i] = 0L;
                pboPrimed[i] = false;
                if (pboMapped[i] == null) {
                    persistentOk = false;
                    break;
                }
            }
            if (!persistentOk) {
                for (int i = 0; i < PBO_COUNT; i++) {
                    if (pboMapped[i] != null && pboIds[i] != 0) {
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
                        GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                        pboMapped[i] = null;
                    }
                    if (pboIds[i] != 0) {
                        GL15.glDeleteBuffers(pboIds[i]);
                        pboIds[i] = 0;
                    }
                }
            }
        }

        persistentPboMode = persistentOk;
        if (!persistentOk) {
            for (int i = 0; i < PBO_COUNT; i++) {
                pboIds[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, byteSize, GL15.GL_STREAM_READ);
                pboMapped[i] = null;
                pboSyncs[i] = 0L;
                pboPrimed[i] = false;
            }
        }

        synchronized (mailboxLock) {
            if (mailboxWidth != w || mailboxHeight != h || mailboxFrames == null) {
                mailboxFrames = new byte[MAILBOX_SLOTS][byteSize];
                mailboxWidth = w;
                mailboxHeight = h;
                latestReadySlot = -1;
                feederInUseSlot = -1;
            }
        }

        pboWriteIdx = 0;
        glWidth = w;
        glHeight = h;

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        return true;
    }

    private void cleanupGlPending() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.isOnThread()) {
            cleanupGlResources();
        } else if (mc != null) {
            mc.execute(this::cleanupGlResources);
        }
    }

    private void cleanupGlResources() {
        int prevPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        if (readSrcFbo != 0) {
            GL30.glDeleteFramebuffers(readSrcFbo);
            readSrcFbo = 0;
        }
        attachedSrcTexId = 0;
        attachedSrcW = 0;
        attachedSrcH = 0;
        if (downscaleFbo != 0) {
            GL30.glDeleteFramebuffers(downscaleFbo);
            downscaleFbo = 0;
        }
        if (downscaleTex != 0) {
            GL11.glDeleteTextures(downscaleTex);
            downscaleTex = 0;
        }
        for (int i = 0; i < PBO_COUNT; i++) {
            if (pboSyncs[i] != 0L) {
                GL32.glDeleteSync(pboSyncs[i]);
                pboSyncs[i] = 0L;
            }
            if (pboMapped[i] != null && pboIds[i] != 0) {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                pboMapped[i] = null;
            }
            if (pboIds[i] != 0) {
                GL15.glDeleteBuffers(pboIds[i]);
                pboIds[i] = 0;
            }
            pboPrimed[i] = false;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
        persistentPboMode = false;
        glWidth = 0;
        glHeight = 0;
    }

    private void ensureEncoderSession(int w, int h, int fps) {
        String qual = qualityMode.get();
        String enc = encoderMode.get();
        if (sessionRunning && activeWidth == w && activeHeight == h && activeFps == fps
                && qual.equals(activeQuality) && enc.equals(activeEncoderChoice)
                && encoderProcess != null && encoderProcess.isAlive()) {
            return;
        }
        if (startingSession) {
            return;
        }
        startingSession = true;
        Thread starter = new Thread(() -> {
            try {
                synchronized (sessionLock) {
                    if (!isEnabled()) return;
                    if (sessionRunning && activeWidth == w && activeHeight == h && activeFps == fps
                            && qual.equals(activeQuality) && enc.equals(activeEncoderChoice)
                            && encoderProcess != null && encoderProcess.isAlive()) {
                        return;
                    }
                    stopEncoderSession();
                    String ff = resolveFfmpegPath();
                    if (ff == null) {
                        triggerToast("FFmpeg не найден", "Установите FFmpeg (winget install Gyan.FFmpeg)", true);
                        sendChatMessage("FFmpeg не найден. Установите FFmpeg (winget install Gyan.FFmpeg).", Formatting.RED);
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc != null) {
                            mc.execute(() -> setEnabled(false));
                        }
                        return;
                    }
                    startEncoderSession(ff, w, h, fps, qual, enc);
                }
            } finally {
                startingSession = false;
            }
        }, "Rollback-Starter");
        starter.setDaemon(true);
        starter.start();
    }

    private String resolveCodec(String ff, String encChoice) {
        if (encChoice.contains("CPU")) {
            return "libx264";
        }
        if (encChoice.contains("NVENC")) {
            return isEncoderAvailable(ff, "h264_nvenc") ? "h264_nvenc" : "libx264";
        }
        if (isEncoderAvailable(ff, "h264_nvenc")) {
            return "h264_nvenc";
        }
        if (isEncoderAvailable(ff, "h264_amf")) {
            return "h264_amf";
        }
        if (isEncoderAvailable(ff, "h264_qsv")) {
            return "h264_qsv";
        }
        return "libx264";
    }

    private void startEncoderSession(String ff, int w, int h, int fps, String qual, String encChoice) {
        String codec = resolveCodec(ff, encChoice);
        List<String> cmd = buildEncodeCommand(ff, w, h, fps, qual, codec);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();

            ringFirstStartNanos = 0L;
            synchronized (ringLock) {
                gopRing.clear();
                currentGop.reset();
                currentGopHasKey = false;
                lastPatPacket = null;
                lastPmtPacket = null;
            }

            this.encoderProcess = proc;
            this.encoderStdin = proc.getOutputStream();
            this.activeWidth = w;
            this.activeHeight = h;
            this.activeFps = fps;
            this.activeQuality = qual;
            this.activeEncoderChoice = encChoice;
            this.sessionRunning = true;

            long epoch = this.shutdownHookEpoch;
            Thread feed = new Thread(() -> runFeederLoop(proc, w, h, fps, epoch), "Rollback-Feeder");
            feed.setDaemon(true);
            this.feederThread = feed;

            Thread read = new Thread(() -> runReaderLoop(proc), "Rollback-Reader");
            read.setDaemon(true);
            this.readerThread = read;

            read.start();
            feed.start();
        } catch (Exception e) {
            EggClient.LOGGER.warn("[Rollback] Failed to start FFmpeg encoder", e);
            stopEncoderSession();
        }
    }

    private List<String> buildEncodeCommand(String ff, int w, int h, int fps, String qual, String codec) {
        int crf = switch (qual) {
            case "Ультра" -> 16;
            case "Высокое" -> 20;
            default -> 24;
        };
        String maxRate = switch (qual) {
            case "Ультра" -> "18M";
            case "Высокое" -> "10M";
            default -> "5M";
        };
        String targetRate = switch (qual) {
            case "Ультра" -> "14M";
            case "Высокое" -> "8M";
            default -> "4M";
        };
        String bufSize = switch (qual) {
            case "Ультра" -> "36M";
            case "Высокое" -> "20M";
            default -> "10M";
        };

        List<String> cmd = new ArrayList<>();
        cmd.add(ff);
        cmd.add("-y");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("bgra");
        cmd.add("-s");
        cmd.add(w + "x" + h);
        cmd.add("-r");
        cmd.add(String.valueOf(fps));
        cmd.add("-i");
        cmd.add("-");

        switch (codec) {
            case "h264_nvenc" -> {
                cmd.add("-c:v");
                cmd.add("h264_nvenc");
                cmd.add("-preset");
                cmd.add("p1");
                cmd.add("-tune");
                cmd.add("ull");
                cmd.add("-rc");
                cmd.add("vbr");
                cmd.add("-cq");
                cmd.add(String.valueOf(crf));
                cmd.add("-b:v");
                cmd.add("0");
                cmd.add("-maxrate");
                cmd.add(maxRate);
                cmd.add("-bufsize");
                cmd.add(bufSize);
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
            }
            case "h264_amf" -> {
                cmd.add("-c:v");
                cmd.add("h264_amf");
                cmd.add("-usage");
                cmd.add("ultralowlatency");
                cmd.add("-quality");
                cmd.add("speed");
                cmd.add("-rc");
                cmd.add("vbr_latency");
                cmd.add("-b:v");
                cmd.add(targetRate);
                cmd.add("-maxrate");
                cmd.add(maxRate);
                cmd.add("-bufsize");
                cmd.add(bufSize);
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
            }
            case "h264_qsv" -> {
                cmd.add("-c:v");
                cmd.add("h264_qsv");
                cmd.add("-preset");
                cmd.add("veryfast");
                cmd.add("-global_quality");
                cmd.add(String.valueOf(crf));
                cmd.add("-maxrate");
                cmd.add(maxRate);
                cmd.add("-bufsize");
                cmd.add(bufSize);
                cmd.add("-pix_fmt");
                cmd.add("nv12");
            }
            default -> {
                int x264Threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 3));
                cmd.add("-c:v");
                cmd.add("libx264");
                cmd.add("-preset");
                cmd.add("ultrafast");
                cmd.add("-tune");
                cmd.add("zerolatency");
                cmd.add("-threads");
                cmd.add(String.valueOf(x264Threads));
                cmd.add("-crf");
                cmd.add(String.valueOf(crf));
                cmd.add("-maxrate");
                cmd.add(maxRate);
                cmd.add("-bufsize");
                cmd.add(bufSize);
                cmd.add("-sc_threshold");
                cmd.add("0");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
            }
        }

        cmd.add("-g");
        cmd.add(String.valueOf(fps));
        cmd.add("-keyint_min");
        cmd.add(String.valueOf(fps));
        cmd.add("-f");
        cmd.add("mpegts");
        cmd.add("-mpegts_flags");
        cmd.add("+resend_headers");
        cmd.add("pipe:1");
        return cmd;
    }

    private void runFeederLoop(Process proc, int w, int h, int fps, long epoch) {
        long intervalNanos = 1_000_000_000L / fps;
        long nextTick = System.nanoTime();
        int expectedBytes = w * h * 4;
        OutputStream out = encoderStdin;

        while (sessionRunning && proc.isAlive() && shutdownHookEpoch == epoch) {
            boolean hasFrame;
            synchronized (mailboxLock) {
                hasFrame = mailboxFrames != null && mailboxWidth == w && mailboxHeight == h && latestReadySlot >= 0;
            }
            if (!hasFrame) {
                LockSupport.parkNanos(5_000_000L);
                nextTick = System.nanoTime();
                continue;
            }

            long now = System.nanoTime();
            if (now < nextTick) {
                LockSupport.parkNanos(Math.min(intervalNanos, nextTick - now));
                continue;
            }
            if (now - nextTick > intervalNanos * 4L) {
                nextTick = now;
            }
            nextTick += intervalNanos;

            byte[] frame = null;
            synchronized (mailboxLock) {
                if (mailboxFrames != null && mailboxWidth == w && mailboxHeight == h && latestReadySlot >= 0) {
                    feederInUseSlot = latestReadySlot;
                    frame = mailboxFrames[feederInUseSlot];
                }
            }
            if (frame == null || frame.length != expectedBytes) {
                synchronized (mailboxLock) {
                    if (mailboxWidth == w && mailboxHeight == h) {
                        feederInUseSlot = -1;
                    }
                }
                continue;
            }

            try {
                out.write(frame);
            } catch (IOException e) {
                break;
            } finally {
                synchronized (mailboxLock) {
                    if (mailboxWidth == w && mailboxHeight == h) {
                        feederInUseSlot = -1;
                    }
                }
            }
        }
    }

    private void runReaderLoop(Process proc) {
        byte[] packet = new byte[TS_PACKET_SIZE];
        try (InputStream is = new BufferedInputStream(proc.getInputStream(), 65536)) {
            while (sessionRunning) {
                int first = is.read();
                if (first == -1) break;
                if (first != 0x47) continue;

                packet[0] = 0x47;
                int offset = 1;
                while (offset < TS_PACKET_SIZE) {
                    int n = is.read(packet, offset, TS_PACKET_SIZE - offset);
                    if (n < 0) return;
                    offset += n;
                }
                consumeTsPacket(packet);
            }
        } catch (IOException ignored) {
        }
    }

    private void consumeTsPacket(byte[] pkt) {
        int pid = ((pkt[1] & 0x1F) << 8) | (pkt[2] & 0xFF);
        boolean pusi = (pkt[1] & 0x40) != 0;
        int afc = (pkt[3] >> 4) & 0x03;

        if (pid == 0 && pusi) {
            byte[] patCopy = Arrays.copyOf(pkt, TS_PACKET_SIZE);
            synchronized (ringLock) {
                lastPatPacket = patCopy;
            }
            int parsedPmt = parsePmtPidFromPat(pkt);
            if (parsedPmt > 0) {
                pmtPid = parsedPmt;
            }
            return;
        }

        if (pid == pmtPid && pusi) {
            byte[] pmtCopy = Arrays.copyOf(pkt, TS_PACKET_SIZE);
            synchronized (ringLock) {
                lastPmtPacket = pmtCopy;
            }
            return;
        }

        boolean isRai = false;
        if ((afc & 0x02) != 0) {
            int adaptLen = pkt[4] & 0xFF;
            if (adaptLen >= 1 && (pkt[5] & 0x40) != 0) {
                isRai = true;
            }
        }

        long now = System.nanoTime();
        synchronized (ringLock) {
            if (isRai) {
                if (currentGopHasKey && currentGop.size() >= TS_PACKET_SIZE) {
                    gopRing.addLast(new GopChunk(currentGop.toByteArray(), currentGopStartNanos, now));
                }
                currentGop.reset();
                currentGopStartNanos = now;
                currentGopHasKey = true;

                if (lastPatPacket != null) {
                    currentGop.write(lastPatPacket, 0, TS_PACKET_SIZE);
                }
                if (lastPmtPacket != null) {
                    currentGop.write(lastPmtPacket, 0, TS_PACKET_SIZE);
                }
                trimRingLocked(now);
                ringFirstStartNanos = !gopRing.isEmpty() ? gopRing.peekFirst().startNanos : currentGopStartNanos;
            }

            if (currentGopHasKey) {
                currentGop.write(pkt, 0, TS_PACKET_SIZE);
            }
        }
    }

    private void trimRingLocked(long now) {
        long keepNanos = Math.round((durationSec.get() + 4.0) * 1_000_000_000.0);
        while (!gopRing.isEmpty() && now - gopRing.peekFirst().endNanos > keepNanos) {
            gopRing.removeFirst();
        }
    }

    private static int parsePmtPidFromPat(byte[] pkt) {
        int afc = (pkt[3] >> 4) & 0x03;
        int pos = 4;
        if ((afc & 0x02) != 0) {
            pos += 1 + (pkt[pos] & 0xFF);
        }
        if (pos >= TS_PACKET_SIZE) return -1;
        int pointer = pkt[pos] & 0xFF;
        pos += 1 + pointer;
        if (pos + 12 >= TS_PACKET_SIZE) return -1;
        if ((pkt[pos] & 0xFF) != 0x00) return -1;
        int secLen = ((pkt[pos + 1] & 0x0F) << 8) | (pkt[pos + 2] & 0xFF);
        int end = Math.min(TS_PACKET_SIZE - 4, pos + 3 + secLen - 4);
        pos += 8;
        while (pos + 3 < end) {
            int progNum = ((pkt[pos] & 0xFF) << 8) | (pkt[pos + 1] & 0xFF);
            int progPid = ((pkt[pos + 2] & 0x1F) << 8) | (pkt[pos + 3] & 0xFF);
            if (progNum != 0) {
                return progPid;
            }
            pos += 4;
        }
        return -1;
    }

    private void stopEncoderSession() {
        synchronized (sessionLock) {
            sessionRunning = false;
            if (encoderStdin != null) {
                try {
                    encoderStdin.close();
                } catch (IOException ignored) {
                }
                encoderStdin = null;
            }
            if (encoderProcess != null) {
                try {
                    if (!encoderProcess.waitFor(400, TimeUnit.MILLISECONDS)) {
                        encoderProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    encoderProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
                encoderProcess = null;
            }
            feederThread = null;
            readerThread = null;
        }
    }

    private String resolveFfmpegPath() {
        if (cachedFfmpegPath != null) {
            return cachedFfmpegPath;
        }
        if (testExecutable("ffmpeg")) {
            cachedFfmpegPath = "ffmpeg";
            return cachedFfmpegPath;
        }
        String localApp = System.getenv("LOCALAPPDATA");
        String[] candidates = {
                localApp != null ? localApp + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe" : null,
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                FabricLoader.getInstance().getGameDir().resolve("ffmpeg.exe").toString()
        };
        for (String c : candidates) {
            if (c != null && new File(c).isFile() && testExecutable(c)) {
                cachedFfmpegPath = c;
                return cachedFfmpegPath;
            }
        }
        return null;
    }

    private boolean testExecutable(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEncoderAvailable(String ff, String codec) {
        Boolean cached = encoderSupportCache.get(codec);
        if (cached != null) {
            return cached;
        }
        try {
            String pixFmt = "h264_qsv".equals(codec) ? "nv12" : "yuv420p";
            Process p = new ProcessBuilder(
                    ff, "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "color=c=black:s=256x256:d=0.04",
                    "-pix_fmt", pixFmt,
                    "-c:v", codec, "-f", "null", "-"
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            encoderSupportCache.put(codec, ok);
            return ok;
        } catch (Exception e) {
            encoderSupportCache.put(codec, false);
            return false;
        }
    }

    private record GopChunk(byte[] data, long startNanos, long endNanos) {}
}
