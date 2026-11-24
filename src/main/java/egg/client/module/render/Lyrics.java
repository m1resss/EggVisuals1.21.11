package egg.client.module.render;

import egg.client.command.CommandManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.render.hud.LyricCaption;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import egg.client.util.LyricLine;
import egg.client.util.LyricsFetcher;
import egg.client.util.MediaTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Lyrics extends Module {
    private static final String WORLD = "В мире";
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

    private final ModeSetting placement = new ModeSetting("Расположение", "Над хотбаром", "Положение субтитров", "Над хотбаром", WORLD);
    private final SliderSetting size = new SliderSetting("Масштаб", 1.0, 0.4, 2.5, 0.1, "Размер субтитров");
    private final SliderSetting opacity = new SliderSetting("Непрозрачность фона", 78, 0, 100, 1, "Непрозрачность подложки в процентах");
    private final SliderSetting distance = new SliderSetting("Дистанция", 4.0, 2.0, 12.0, 0.5, "Расстояние до новой строки в блоках");
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 6.0, 2.0, 14.0, 0.5, "Сколько секунд держать строку во время паузы в тексте");
    private final ModeSetting colorStyle = new ModeSetting("Цвет текста", "Белый", "Цвет субтитров", "Белый", "Лавандовый", "Коралловый", "Тема");
    private final BooleanSetting showProgress = new BooleanSetting("Прогресс строки", true, "Полоска времени показа строки");
    private final BooleanSetting demoMode = new BooleanSetting("Демо режим", false, "Показать тестовые строки");
    private final LyricCaption caption = new LyricCaption();

    private List<LyricLine> activeLyrics = List.of();
    private String trackKey = "";
    private long loadGeneration;
    private boolean manualPlayback;
    private boolean demoPlaying;
    private long playbackStart;
    private long playbackOffset;

    public Lyrics() {
        super("Lyrics", "Показывает синхронизированный текст песни", Category.ESP);
        distance.setVisible(() -> placement.is(WORLD));
        addSettings(placement, size, opacity, distance, colorStyle, showProgress, lifetime, demoMode);
    }

    @Override
    public void onDisable() {
        clearTrack();
        manualPlayback = false;
        demoPlaying = false;
        caption.reset();
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (demoMode.isEnabled() && !demoPlaying) {
            startDemo();
        } else if (!demoMode.isEnabled() && demoPlaying) {
            stopPlayback();
        }

        long position = currentPosition();
        int index = findLine(position);
        LyricLine line = null;
        float progress = 0f;
        if (index >= 0) {
            LyricLine current = activeLyrics.get(index);
            long duration = (long) (lifetime.get() * 1000);
            if (index + 1 < activeLyrics.size()) {
                duration = Math.min(duration, activeLyrics.get(index + 1).timeMs() - current.timeMs());
            }
            long elapsed = position - current.timeMs();
            if (elapsed < duration && !current.text().isBlank()) {
                line = current;
                progress = (float) elapsed / duration;
            }
        }

        int textColor = switch (colorStyle.get()) {
            case "Лавандовый" -> 0xFFDDD7FF;
            case "Коралловый" -> 0xFFFFD5CF;
            case "Тема" -> ColorUtil.interpolate(0xFFF5F6FA, Theme.getAccent() | 0xFF000000, 0.45f);
            default -> 0xFFF5F6FA;
        };
        caption.render(context, tickCounter, line, progress, placement.is(WORLD), distance.getValueFloat(),
                size.getValueFloat(), opacity.getValueFloat() / 100f, textColor, showProgress.isEnabled());
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
