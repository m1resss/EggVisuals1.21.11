package egg.client.util;

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

    public static void playScreenOpen() {
        ASYNC.submit(() -> {
            try {
                if (openClip == null) {
                    openClip = loadClip(OPEN_PATH, "/assets/egg/sounds/gui/screen_open.wav");
                }
                if (openClip != null) {
                    if (openClip.isRunning()) openClip.stop();
                    openClip.setFramePosition(0);
                    openClip.start();
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void playScreenClose() {
        ASYNC.submit(() -> {
            try {
                if (closeClip == null) {
                    closeClip = loadClip(CLOSE_PATH, "/assets/egg/sounds/gui/screen_close.wav");
                }
                if (closeClip != null) {
                    if (closeClip.isRunning()) closeClip.stop();
                    closeClip.setFramePosition(0);
                    closeClip.start();
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void playSlider() {
        long now = System.currentTimeMillis();
        if (now - lastSliderPlayTime < 38L) return; // Crisp tactile rate limit
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
