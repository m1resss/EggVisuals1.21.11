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
        long now = System.currentTimeMillis();
        long delta = playing ? (now - lastPositionUpdate) : 0L;
        if (trackPositionSec > 0L) {
            long posMs = trackPositionSec * 1000L + delta + manualOffsetMs;
            if (trackDurationSec > 0L) {
                posMs = Math.min(posMs, trackDurationSec * 1000L);
            }
            return Math.max(0L, posMs);
        }
        if (trackTitle.isEmpty()) return manualOffsetMs;
        return Math.max(0L, (playing ? delta : 0L) + manualOffsetMs);
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
                        IMediaSession bestSession = null;
                        // 1a. Priority 1: Dedicated music app actively playing
                        for (IMediaSession s : sessions) {
                            if (s == null) continue;
                            MediaInfo m = s.getMedia();
                            if (m == null || m.getTitle().isBlank()) continue;
                            String owner = s.getOwner() != null ? s.getOwner().toLowerCase(java.util.Locale.ROOT) : "";
                            boolean isMusicApp = owner.contains("spotify") || owner.contains("yandex") || owner.contains("aimp") || owner.contains("apple") || owner.contains("music") || owner.contains("foobar");
                            if (isMusicApp && m.isPlaying()) {
                                bestSession = s;
                                break;
                            }
                        }
                        // 1b. Priority 2: Any dedicated music app present (even if paused/transitioning)
                        if (bestSession == null) {
                            for (IMediaSession s : sessions) {
                                if (s == null) continue;
                                MediaInfo m = s.getMedia();
                                if (m == null || m.getTitle().isBlank()) continue;
                                String owner = s.getOwner() != null ? s.getOwner().toLowerCase(java.util.Locale.ROOT) : "";
                                boolean isMusicApp = owner.contains("spotify") || owner.contains("yandex") || owner.contains("aimp") || owner.contains("apple") || owner.contains("music") || owner.contains("foobar");
                                if (isMusicApp) {
                                    bestSession = s;
                                    break;
                                }
                            }
                        }
                        // 1c. Priority 3: Browser / other session that is actively playing
                        if (bestSession == null) {
                            for (IMediaSession s : sessions) {
                                if (s == null) continue;
                                MediaInfo m = s.getMedia();
                                if (m == null || m.getTitle().isBlank()) continue;
                                if (m.isPlaying()) {
                                    bestSession = s;
                                    break;
                                }
                            }
                        }
                        // 1d. Priority 4: Any non-empty session
                        if (bestSession == null) {
                            for (IMediaSession s : sessions) {
                                if (s == null) continue;
                                MediaInfo m = s.getMedia();
                                if (m != null && !m.getTitle().isBlank()) {
                                    bestSession = s;
                                    break;
                                }
                            }
                        }

                        if (bestSession != null) {
                            MediaInfo media = bestSession.getMedia();
                            String t = media.getTitle().trim();
                            String a = media.getArtist().trim();

                            if (a.isEmpty() && (t.contains(" - ") || t.contains(" — ") || t.contains(" – "))) {
                                String[] split = splitArtistAndTitle(t);
                                a = split[0];
                                t = split[1];
                            }

                            if (!t.isEmpty()) {
                                boolean wasPlaying = this.playing;
                                boolean nowPlaying = media.isPlaying();

                                if (!this.trackTitle.equals(t) || !this.trackArtist.equals(a)) {
                                    this.trackTitle = t;
                                    this.trackArtist = a;
                                    this.trackPositionSec = media.getPosition();
                                    this.lastPositionUpdate = System.currentTimeMillis();
                                    this.manualOffsetMs = 0L;
                                }

                                if (wasPlaying && !nowPlaying) {
                                    // Track just paused - freeze position
                                    long current = getTrackElapsedMs();
                                    this.trackPositionSec = current / 1000L;
                                    this.manualOffsetMs = current % 1000L;
                                    this.lastPositionUpdate = System.currentTimeMillis();
                                } else if (!wasPlaying && nowPlaying) {
                                    // Track unpaused - resume from current position
                                    this.lastPositionUpdate = System.currentTimeMillis();
                                }

                                this.playing = nowPlaying;
                                this.trackDurationSec = media.getDuration();
                                long pos = media.getPosition();
                                if (pos != this.trackPositionSec) {
                                    this.trackPositionSec = pos;
                                    this.lastPositionUpdate = System.currentTimeMillis();
                                }
                                foundMedia = true;
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

                            boolean isBrowserOrNonMedia = winTitle.endsWith(" - Google Chrome")
                                    || winTitle.endsWith(" - Chromium")
                                    || winTitle.endsWith(" - Brave")
                                    || winTitle.endsWith(" - Microsoft​ Edge")
                                    || winTitle.endsWith(" - Discord")
                                    || winTitle.endsWith(" - Visual Studio Code")
                                    || winTitle.contains("Visual Studio")
                                    || winTitle.endsWith(" - Notepad");

                            // 1. YouTube / YouTube Music in browsers
                            if (winTitle.contains(" - YouTube")) {
                                String clean = winTitle.replaceAll("(?i)\\s*[-—–|]\\s*(?:Google Chrome|Chromium|Brave|Microsoft​ Edge).*$", "").trim();
                                clean = clean.replaceAll("(?i)\\s*-\\s*YouTube(?:\\s+Music)?.*$", "").trim();
                                String[] split = splitArtistAndTitle(clean);
                                if (!split[1].isEmpty()) {
                                    foundArtist[0] = split[0];
                                    foundTitle[0] = split[1];
                                    winFound[0] = true;
                                    return false;
                                }
                            }

                            // 2. Yandex Music Desktop or Web / VK / SoundCloud / AIMP / VLC
                            if (winTitle.contains("Яндекс Музыка") || winTitle.contains("VK Музыка") || winTitle.contains("SoundCloud") || winTitle.contains("AIMP") || winTitle.contains("VLC")) {
                                String clean = winTitle.replaceAll("(?i)\\s*[-—–|]\\s*(?:Яндекс Музыка|VK Музыка|SoundCloud|AIMP|VLC media player|Google Chrome|Brave|Edge).*$", "").trim();
                                String[] split = splitArtistAndTitle(clean);
                                if (!split[1].isEmpty()) {
                                    foundArtist[0] = split[0];
                                    foundTitle[0] = split[1];
                                    winFound[0] = true;
                                    return false;
                                }
                            }

                            // 3. Spotify desktop client
                            if (!isBrowserOrNonMedia && (className.contains("Spotify") || className.startsWith("Chrome_WidgetWin"))) {
                                if (winTitle.equalsIgnoreCase("Spotify") || winTitle.equalsIgnoreCase("Spotify Free") || winTitle.equalsIgnoreCase("Spotify Premium")) {
                                    return true;
                                }
                                if (winTitle.contains(" - ") || winTitle.contains(" — ")) {
                                    String[] split = splitArtistAndTitle(winTitle);
                                    if (!split[1].isEmpty()) {
                                        foundArtist[0] = split[0];
                                        foundTitle[0] = split[1];
                                        winFound[0] = true;
                                        return false;
                                    }
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
