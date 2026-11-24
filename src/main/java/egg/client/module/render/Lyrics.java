package egg.client.module.render;

import egg.client.command.CommandManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.world.WorldLyricsRenderer;
import egg.client.ui.theme.Theme;
import egg.client.util.LyricLine;
import egg.client.util.LyricsFetcher;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Lyrics extends Module {
    private static final long DEMO_LENGTH_MS = 24_000L;
    private static final List<LyricLine> DEMO = List.of(
            new LyricLine(0L, "whats wrong im sick"),
            new LyricLine(2800L, "with and twisted"),
            new LyricLine(5800L, "i confess"),
            new LyricLine(8800L, "fuck the opps"),
            new LyricLine(12000L, "don't ever fuck with world"),
            new LyricLine(15500L, "we Haunte"),
            new LyricLine(19000L, "whats wrong im sick")
    );

    private final SliderSetting words = new SliderSetting("Слов во фразе", 2.0, 1.0, 4.0, 1.0, "Длина фраз в мире");
    private final SliderSetting size = new SliderSetting("Масштаб", 1.0, 0.4, 2.5, 0.1, "Размер текста в мире");
    private final SliderSetting distance = new SliderSetting("Дистанция", 4.0, 2.0, 12.0, 0.5, "Дальность появления фраз в блоках");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 6.0, 2.0, 14.0, 0.5, "Сколько секунд держать строку во время паузы в тексте");
    private final ModeSetting colorStyle = new ModeSetting("Цвет текста", "Лавандовый", "Оттенок букв и свечения", "Белый", "Лавандовый", "Коралловый", "Тема");
    private final SliderSetting glow = new SliderSetting("Свечение", 75.0, 0.0, 100.0, 1.0, "Яркость свечения букв в процентах");
    private final BooleanSetting demoMode = new BooleanSetting("Демо режим", false, "Показать тестовые строки");
    private final WorldLyricsRenderer renderer = new WorldLyricsRenderer();

    private List<LyricLine> activeLyrics = List.of();
    private String trackKey = "";
    private long loadGeneration;
    private boolean manualPlayback;
    private boolean demoPlaying;
    private long playbackStart;
    private long playbackOffset;

    public Lyrics() {
        super("Lyrics", "Показывает фразы из песни в мире", Category.ESP);
        addSettings(words, size, distance, colorStyle, glow, lifetime, demoMode);
    }

    @Override
    public void onDisable() {
        clearTrack();
        manualPlayback = false;
        demoPlaying = false;
    }

    public void renderWorld(Camera camera) {
        if (demoMode.isEnabled() && !demoPlaying) {
            startDemo();
        } else if (!demoMode.isEnabled() && demoPlaying) {
            stopPlayback();
        }

        long position = currentPosition();
        int index = findLine(position);
        LyricLine line = null;
        long duration = 0L;
        long elapsed = 0L;
        if (index >= 0) {
            LyricLine current = activeLyrics.get(index);
            duration = (long) (lifetime.get() * 1000);
            if (index + 1 < activeLyrics.size()) {
                duration = Math.min(duration, activeLyrics.get(index + 1).timeMs() - current.timeMs());
            }
            elapsed = position - current.timeMs();
            if (elapsed < duration && !current.text().isBlank()) {
                line = current;
            }
        }

        int textColor = switch (colorStyle.get()) {
            case "Коралловый" -> 0xFFFF736F;
            case "Тема" -> Theme.getAccent() | 0xFF000000;
            case "Белый" -> 0xFFCAD6E7;
            default -> 0xFFB178FF;
        };
        renderer.render(camera, line, elapsed, duration, new WorldLyricsRenderer.Style(
                (int) words.get(), distance.getValueFloat(), size.getValueFloat(), textColor, glow.getValueFloat() / 100f));
    }

    private long currentPosition() {
        if (manualPlayback) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - playbackStart);
            long position = elapsed + playbackOffset;
            return demoPlaying ? position % DEMO_LENGTH_MS : position;
        }
        MediaTracker media = MediaTracker.getInstance();
        String artist = media.getTrackArtist();
        String title = media.getTrackTitle();
        if (title.isBlank()) {
            if (!trackKey.isEmpty()) clearTrack();
            return -1;
        }

        if (!LyricsFetcher.makeKey(artist, title).equals(trackKey)) {
            loadTrack(artist, title);
        }
        return media.isPlaying() ? media.getTrackElapsedMs() : -1;
    }

    private int findLine(long position) {
        if (position < 0) return -1;
        int low = 0;
        int high = activeLyrics.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (activeLyrics.get(middle).timeMs() <= position) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low - 1;
    }

    private void loadTrack(String artist, String title) {
        renderer.reset();
        long generation = ++loadGeneration;
        trackKey = LyricsFetcher.makeKey(artist, title);
        LyricsFetcher fetcher = LyricsFetcher.getInstance();
        activeLyrics = Objects.requireNonNullElse(fetcher.getCachedLyrics(artist, title), List.of());
        if (!activeLyrics.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        fetcher.fetchLyricsAsync(artist, title).thenAccept(lines -> client.execute(() -> {
            if (loadGeneration == generation && isEnabled()) {
                activeLyrics = lines;
            }
        }));
    }

    private void clearTrack() {
        renderer.reset();
        loadGeneration++;
        trackKey = "";
        activeLyrics = List.of();
    }

    private void startDemo() {
        clearTrack();
        manualPlayback = true;
        demoPlaying = true;
        playbackStart = System.nanoTime();
        playbackOffset = 0L;
        activeLyrics = DEMO;
    }

    public void playDemo() {
        demoMode.setEnabled(true);
        startDemo();
        CommandManager.sendClientMessage("§d[Lyrics] §fДемонстрация субтитров запущена", false);
    }

    public void playManual(String artist, String title) {
        demoMode.setEnabled(false);
        manualPlayback = true;
        demoPlaying = false;
        playbackStart = System.nanoTime();
        playbackOffset = 0L;
        String artistName = Objects.requireNonNullElse(artist, "");
        String trackTitle = Objects.requireNonNullElse(title, "");
        loadTrack(artistName, trackTitle);
        CommandManager.sendClientMessage("§d[Lyrics] §fПоиск текста для §e"
                + (artistName.isEmpty() ? "" : artistName + " — ") + trackTitle, false);
    }

    private void stopPlayback() {
        clearTrack();
        demoPlaying = false;
        manualPlayback = false;
    }

    public void stopManual() {
        demoMode.setEnabled(false);
        stopPlayback();
        CommandManager.sendClientMessage("§d[Lyrics] §7Автоопределение плеера активно", false);
    }

    public void seekManual(long seconds) {
        renderer.reset();
        playbackOffset = Math.clamp(TimeUnit.SECONDS.toMillis(seconds), 0L, Long.MAX_VALUE / 2);
        playbackStart = System.nanoTime();
        CommandManager.sendClientMessage("§d[Lyrics] §7Перемотка на §f" + seconds + " §7сек", false);
    }

    public boolean isManualPlayback() {
        return manualPlayback;
    }

    public List<LyricLine> getActiveLyrics() {
        return activeLyrics;
    }
}
