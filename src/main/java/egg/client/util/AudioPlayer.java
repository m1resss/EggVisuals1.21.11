package egg.client.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AudioPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("EggAudio");
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EggAudio-Worker");
        t.setDaemon(true);
        return t;
    });

    private interface WinMM extends Library {
        WinMM INSTANCE = Native.load("winmm", WinMM.class);
        int mciSendStringW(WString lpstrCommand, WString lpstrReturnString, int uReturnLength, int hwndCallback);
    }

    private static int sendMci(String command) {
        try {
            return WinMM.INSTANCE.mciSendStringW(new WString(command), null, 0, 0);
        } catch (Throwable t) {
            LOGGER.error("MCI error executing: " + command, t);
            return -1;
        }
    }

    private static final String RAIN_ALIAS = "egg_rain";
    private static final String THUNDER_ALIAS = "egg_thunder";
    private static volatile boolean rainPlaying = false;
    private static final Random RANDOM = new Random();

    public static final String RAIN_PATH = "C:\\Users\\admin\\Downloads\\rainsound.mp3";
    public static final String[] THUNDER_PATHS = new String[]{
            "C:\\Users\\admin\\Downloads\\thunder-lightning (mp3cut.net) (3).mp3",
            "C:\\Users\\admin\\Downloads\\thunder-lightning (mp3cut.net).mp3",
            "C:\\Users\\admin\\Downloads\\thunder-lightning (mp3cut.net) (1).mp3",
            "C:\\Users\\admin\\Downloads\\thunder-lightning (mp3cut.net) (2).mp3"
    };

    public static boolean isRainPlaying() {
        return rainPlaying;
    }

    public static synchronized void startRain(float volume) {
        if (rainPlaying) {
            setRainVolume(volume);
            return;
        }
        File file = new File(RAIN_PATH);
        if (!file.exists()) {
            LOGGER.warn("Rain audio file not found: " + RAIN_PATH);
            return;
        }

        rainPlaying = true;
        AUDIO_EXECUTOR.submit(() -> {
            if (!rainPlaying) return;
            sendMci("stop " + RAIN_ALIAS);
            sendMci("close " + RAIN_ALIAS);
            int res = sendMci("open \"" + RAIN_PATH + "\" type mpegvideo alias " + RAIN_ALIAS);
            if (res == 0 && rainPlaying) {
                int targetMci = Math.round(Math.max(0.0f, Math.min(1.0f, volume)) * 1000.0f);
                sendMci("setaudio " + RAIN_ALIAS + " volume to " + targetMci);
                sendMci("play " + RAIN_ALIAS + " repeat");
            } else {
                sendMci("stop " + RAIN_ALIAS);
                sendMci("close " + RAIN_ALIAS);
            }
        });
    }

    public static synchronized void setRainVolume(float volume) {
        if (!rainPlaying) return;
        AUDIO_EXECUTOR.submit(() -> {
            if (!rainPlaying) return;
            int targetMci = Math.round(Math.max(0.0f, Math.min(1.0f, volume)) * 1000.0f);
            sendMci("setaudio " + RAIN_ALIAS + " volume to " + targetMci);
        });
    }

    public static synchronized void stopRain() {
        stopAll();
    }

    public static synchronized void playThunder(float volume) {
        int idx = RANDOM.nextInt(THUNDER_PATHS.length);
        String path = THUNDER_PATHS[idx];
        File file = new File(path);
        if (!file.exists()) return;

        AUDIO_EXECUTOR.submit(() -> {
            sendMci("stop " + THUNDER_ALIAS);
            sendMci("close " + THUNDER_ALIAS);
            int res = sendMci("open \"" + path + "\" type mpegvideo alias " + THUNDER_ALIAS);
            if (res == 0) {
                int maxMci = Math.round(Math.max(0.0f, Math.min(1.0f, volume)) * 1000.0f);
                sendMci("setaudio " + THUNDER_ALIAS + " volume to " + maxMci);
                sendMci("play " + THUNDER_ALIAS);
            }
        });
    }

    public static synchronized void stopAll() {
        rainPlaying = false;

        sendMci("stop " + RAIN_ALIAS);
        sendMci("close " + RAIN_ALIAS);
        sendMci("stop " + THUNDER_ALIAS);
        sendMci("close " + THUNDER_ALIAS);
        sendMci("close all");

        try {
            AUDIO_EXECUTOR.submit(() -> {
                sendMci("stop " + RAIN_ALIAS);
                sendMci("close " + RAIN_ALIAS);
                sendMci("stop " + THUNDER_ALIAS);
                sendMci("close " + THUNDER_ALIAS);
                sendMci("close all");
            }).get(350, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }
}
