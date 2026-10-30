package egg.client.util;

import egg.client.module.misc.ClientSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GuiSoundManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuiSound");
    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "GuiSound-Worker");
        t.setDaemon(true);
        return t;
    });

    public static final String OPEN_PATH = "C:\\Users\\admin\\Downloads\\AyuGram Desktop\\screen_open.wav";
    public static final String CLOSE_PATH = "C:\\Users\\admin\\Downloads\\AyuGram Desktop\\screen_close.wav";
    public static final String SLIDER_PATH = "C:\\Users\\admin\\Downloads\\AyuGram Desktop\\slider.wav";

    private static Clip openClip;
    private static Clip closeClip;
    private static final Clip[] sliderClips = new Clip[4];
    private static int sliderClipIndex = 0;
    private static long lastSliderPlayTime = 0L;

    static {
        ASYNC.submit(GuiSoundManager::initClips);
    }

    private static synchronized void initClips() {
        try {
            openClip = loadClip(OPEN_PATH, "/assets/egg/sounds/gui/screen_open.wav");
            closeClip = loadClip(CLOSE_PATH, "/assets/egg/sounds/gui/screen_close.wav");
            for (int i = 0; i < sliderClips.length; i++) {
                sliderClips[i] = loadClip(SLIDER_PATH, "/assets/egg/sounds/gui/slider.wav");
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize GUI audio clips", t);
        }
    }

    private static Clip loadClip(String filePath, String resourcePath) {
        try {
            InputStream in = null;
            File f = new File(filePath);
            if (f.exists()) {
                in = new BufferedInputStream(new FileInputStream(f));
            } else {
                in = GuiSoundManager.class.getResourceAsStream(resourcePath);
            }
            if (in == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            in.close();

            byte[] audioBytes = baos.toByteArray();
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Throwable t) {
            LOGGER.warn("Could not load clip: " + filePath + " (" + t.getMessage() + ")");
            return null;
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                if (volume <= 0.001f) {
                    gainControl.setValue(gainControl.getMinimum());
                } else {
                    float clamped = Math.max(0.0001f, Math.min(1.0f, volume));
                    float dB = (float) (Math.log10(clamped) * 20.0);
                    dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                    gainControl.setValue(dB);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void playScreenOpen() {
        float vol = ClientSettings.getOpenSoundVolume();
        if (vol <= 0.001f) return;
        ASYNC.submit(() -> {
            try {
                if (openClip == null) {
                    openClip = loadClip(OPEN_PATH, "/assets/egg/sounds/gui/screen_open.wav");
                }
                if (openClip != null) {
                    applyVolume(openClip, vol);
                    if (openClip.isRunning()) openClip.stop();
                    openClip.setFramePosition(0);
                    openClip.start();
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void playScreenClose() {
        float vol = ClientSettings.getOpenSoundVolume();
        if (vol <= 0.001f) return;
        ASYNC.submit(() -> {
            try {
                if (closeClip == null) {
                    closeClip = loadClip(CLOSE_PATH, "/assets/egg/sounds/gui/screen_close.wav");
                }
                if (closeClip != null) {
                    applyVolume(closeClip, vol);
                    if (closeClip.isRunning()) closeClip.stop();
                    closeClip.setFramePosition(0);
                    closeClip.start();
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void playSlider() {
        float vol = ClientSettings.getOpenSoundVolume();
        if (vol <= 0.001f) return;
        long now = System.currentTimeMillis();
        if (now - lastSliderPlayTime < 38L) return;
        lastSliderPlayTime = now;

        ASYNC.submit(() -> {
            try {
                Clip clip = sliderClips[sliderClipIndex];
                sliderClipIndex = (sliderClipIndex + 1) % sliderClips.length;

                if (clip == null) {
                    clip = loadClip(SLIDER_PATH, "/assets/egg/sounds/gui/slider.wav");
                    sliderClips[sliderClipIndex] = clip;
                }
                if (clip != null) {
                    applyVolume(clip, vol);
                    if (clip.isRunning()) clip.stop();
                    clip.setFramePosition(0);
                    clip.start();
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void playClick() {
        playSlider();
    }

    public static void playSelect() {
        playSlider();
    }

    public static void playToggle(boolean state) {
        playSlider();
    }
}
