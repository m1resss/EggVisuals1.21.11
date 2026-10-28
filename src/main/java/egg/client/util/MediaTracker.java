package egg.client.util;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import net.minecraft.util.Identifier;

public final class MediaTracker {
    private static final MediaTracker INSTANCE = new MediaTracker();
    public static MediaTracker getInstance() {
        return INSTANCE;
    }

    private static final Identifier DEFAULT_COVER = Identifier.of("egg", "textures/avatar.png");

    private volatile boolean playing = false;
    private volatile String trackTitle = "";
    private volatile String trackArtist = "";
    private volatile Identifier coverTexture = DEFAULT_COVER;

    private MediaTracker() {
        Thread thread = new Thread(this::pollLoop, "Egg-MediaTracker");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isPlaying() {
        return playing;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public String getTrackArtist() {
        return trackArtist;
    }

    public Identifier getCoverTexture() {
        return coverTexture != null ? coverTexture : DEFAULT_COVER;
    }

    public void setSimulatedTrack(boolean active, String title, String artist) {
        if (active) {
            this.playing = true;
            this.trackTitle = title != null ? title : "Midnight City";
            this.trackArtist = artist != null ? artist : "M83";
        }
    }

    private void pollLoop() {
        char[] titleBuffer = new char[512];
        char[] classBuffer = new char[128];

        while (!Thread.currentThread().isInterrupted()) {
            try {
                final boolean[] found = new boolean[]{false};
                final String[] foundTitle = new String[]{""};
                final String[] foundArtist = new String[]{""};

                User32.INSTANCE.EnumWindows((hwnd, data) -> {
                    if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                        return true;
                    }

                    int titleLen = User32.INSTANCE.GetWindowText(hwnd, titleBuffer, titleBuffer.length);
                    if (titleLen <= 0) return true;

                    String winTitle = new String(titleBuffer, 0, titleLen).trim();
                    if (winTitle.isEmpty()) return true;

                    int classLen = User32.INSTANCE.GetClassName(hwnd, classBuffer, classBuffer.length);
                    String className = classLen > 0 ? new String(classBuffer, 0, classLen) : "";

                    if (className.equals("Chrome_WidgetWin_0")) {

                        if (winTitle.equals("Spotify") || winTitle.equals("Spotify Free") || winTitle.equals("Spotify Premium")) {

                            return true;
                        }
                        if (winTitle.contains(" - ")) {
                            String[] parts = winTitle.split(" - ", 2);
                            foundArtist[0] = parts[0].trim();
                            foundTitle[0] = parts[1].trim();
                            found[0] = true;
                            return false;
                        }
                    }

                    if (winTitle.contains(" — Яндекс Музыка") || winTitle.contains(" - Яндекс Музыка")) {
                        String clean = winTitle.replace(" — Яндекс Музыка", "").replace(" - Яндекс Музыка", "");
                        if (clean.contains(" — ")) {
                            String[] parts = clean.split(" — ", 2);
                            foundTitle[0] = parts[0].trim();
                            foundArtist[0] = parts[1].trim();
                            found[0] = true;
                            return false;
                        } else if (clean.contains(" - ")) {
                            String[] parts = clean.split(" - ", 2);
                            foundTitle[0] = parts[0].trim();
                            foundArtist[0] = parts[1].trim();
                            found[0] = true;
                            return false;
                        }
                    }

                    if (winTitle.endsWith(" - YouTube") || winTitle.endsWith(" - YouTube Music")) {
                        String clean = winTitle.replace(" - YouTube Music", "").replace(" - YouTube", "");
                        if (clean.contains(" - ")) {
                            String[] parts = clean.split(" - ", 2);
                            foundTitle[0] = parts[0].trim();
                            foundArtist[0] = parts[1].trim();
                            found[0] = true;
                            return false;
                        }
                    }

                    return true;
                }, null);

                if (found[0]) {
                    this.playing = true;
                    this.trackTitle = foundTitle[0];
                    this.trackArtist = foundArtist[0];
                } else {
                    this.playing = false;
                }

                Thread.sleep(1200);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
