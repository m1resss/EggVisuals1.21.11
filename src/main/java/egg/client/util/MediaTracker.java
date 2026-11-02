package egg.client.util;

import com.sun.jna.platform.win32.User32;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.util.Identifier;

import java.util.List;

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
    private volatile long trackDurationSec = 0L;
    private volatile long trackPositionSec = 0L;
    private volatile long lastPositionUpdate = System.currentTimeMillis();
    private volatile long manualOffsetMs = 0L;

    private MediaTracker() {
        Thread thread = new Thread(this::pollLoop, "Egg-MediaTracker");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
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

    public long getTrackDurationSec() {
        return trackDurationSec;
    }

    public long getTrackElapsedMs() {
        if (trackPositionSec > 0L) {
            long delta = System.currentTimeMillis() - lastPositionUpdate;
            return trackPositionSec * 1000L + Math.min(2500L, delta) + manualOffsetMs;
        }
        if (!playing) return manualOffsetMs;
        return Math.max(0L, (System.currentTimeMillis() - lastPositionUpdate) + manualOffsetMs);
    }

    public void setTrackElapsedMs(long ms) {
        this.manualOffsetMs = ms;
        this.lastPositionUpdate = System.currentTimeMillis();
    }

    public void setSimulatedTrack(boolean active, String title, String artist) {
        if (active) {
            this.playing = true;
            this.trackTitle = title != null ? title : "whats wrong im sick";
            this.trackArtist = artist != null ? artist : "Demo";
            this.lastPositionUpdate = System.currentTimeMillis();
            this.manualOffsetMs = 0L;
        } else {
            this.playing = false;
        }
    }

    private static String[] splitArtistAndTitle(String text) {
        if (text == null || text.isBlank()) return new String[]{"", ""};
        String trimmed = text.trim();
        for (String sep : new String[]{" - ", " — ", " – ", " / ", " | "}) {
            int idx = trimmed.indexOf(sep);
            if (idx > 0 && idx + sep.length() < trimmed.length()) {
                String a = trimmed.substring(0, idx).trim();
                String t = trimmed.substring(idx + sep.length()).trim();
                return new String[]{a, t};
            }
        }
        return new String[]{"", trimmed};
    }

    private void pollLoop() {
        char[] titleBuffer = new char[512];
        char[] classBuffer = new char[128];

        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean foundMedia = false;

                // 1. Primary: Windows Media Transport Controls (GSMTC via MediaPlayerInfo.dll)
                try {
                    List<IMediaSession> sessions = MediaPlayerInfo.INSTANCE.getMediaSessions();
                    if (sessions != null && !sessions.isEmpty()) {
                        for (IMediaSession session : sessions) {
                            if (session == null) continue;
                            MediaInfo media = session.getMedia();
                            if (media != null && !media.getTitle().isEmpty()) {
                                String t = media.getTitle().trim();
                                String a = media.getArtist().trim();

                                if (a.isEmpty() && (t.contains(" - ") || t.contains(" — ") || t.contains(" – "))) {
                                    String[] split = splitArtistAndTitle(t);
                                    a = split[0];
                                    t = split[1];
                                }

                                if (!t.isEmpty()) {
                                    if (!this.trackTitle.equals(t) || !this.trackArtist.equals(a)) {
                                        this.trackTitle = t;
                                        this.trackArtist = a;
                                        this.manualOffsetMs = 0L;
                                    }
                                    this.playing = media.isPlaying();
                                    this.trackDurationSec = media.getDuration();
                                    long pos = media.getPosition();
                                    if (pos != this.trackPositionSec) {
                                        this.trackPositionSec = pos;
                                        this.lastPositionUpdate = System.currentTimeMillis();
                                    }
                                    foundMedia = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // 2. Fallback: Window title enumeration (EnumWindows)
                if (!foundMedia) {
                    final boolean[] winFound = new boolean[]{false};
                    final String[] foundTitle = new String[]{""};
                    final String[] foundArtist = new String[]{""};

                    try {
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

                            // Spotify desktop client
                            if (className.startsWith("Chrome_WidgetWin") || className.contains("Spotify")) {
                                if (winTitle.equalsIgnoreCase("Spotify") || winTitle.equalsIgnoreCase("Spotify Free") || winTitle.equalsIgnoreCase("Spotify Premium")) {
                                    return true;
                                }
                                String[] split = splitArtistAndTitle(winTitle);
                                if (!split[1].isEmpty()) {
                                    foundArtist[0] = split[0];
                                    foundTitle[0] = split[1];
                                    winFound[0] = true;
                                    return false;
                                }
                            }

                            // Yandex Music / VK / Web players
                            if (winTitle.contains("Яндекс Музыка") || winTitle.contains("VK Музыка") || winTitle.contains("SoundCloud") || winTitle.contains("AIMP") || winTitle.contains("VLC")) {
                                String clean = winTitle.replaceAll("(?i)\\s*[-—–|]\\s*(?:Яндекс Музыка|VK Музыка|SoundCloud|AIMP|VLC media player).*$", "").trim();
                                String[] split = splitArtistAndTitle(clean);
                                if (!split[1].isEmpty()) {
                                    foundArtist[0] = split[0];
                                    foundTitle[0] = split[1];
                                    winFound[0] = true;
                                    return false;
                                }
                            }

                            // YouTube / YouTube Music in browsers
                            if (winTitle.endsWith(" - YouTube") || winTitle.endsWith(" - YouTube Music")) {
                                String clean = winTitle.replaceAll("(?i)\\s*-\\s*YouTube(?:\\s+Music)?.*$", "").trim();
                                String[] split = splitArtistAndTitle(clean);
                                if (!split[1].isEmpty()) {
                                    foundArtist[0] = split[0];
                                    foundTitle[0] = split[1];
                                    winFound[0] = true;
                                    return false;
                                }
                            }

                            return true;
                        }, null);
                    } catch (Throwable ignored) {}

                    if (winFound[0]) {
                        if (!this.trackTitle.equals(foundTitle[0]) || !this.trackArtist.equals(foundArtist[0])) {
                            this.trackTitle = foundTitle[0];
                            this.trackArtist = foundArtist[0];
                            this.trackPositionSec = 0L;
                            this.lastPositionUpdate = System.currentTimeMillis();
                            this.manualOffsetMs = 0L;
                        }
                        this.playing = true;
                        foundMedia = true;
                    }
                }

                if (!foundMedia) {
                    if (this.playing) {
                        this.playing = false;
                    }
                }

                Thread.sleep(foundMedia ? 250 : 800);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
