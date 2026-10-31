package egg.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsFetcher {
    private static final LyricsFetcher INSTANCE = new LyricsFetcher();
    public static LyricsFetcher getInstance() {
        return INSTANCE;
    }

    private static final String API_BASE = "https://lrclib.net/api";
    private static final Pattern LRC_PATTERN = Pattern.compile("^\\[(\\d{1,2}):(\\d{1,2}(?:\\.\\d+)?)\\](.*)$");

    private final Map<String, List<LyricLine>> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Egg-LyricsFetcher");
        t.setDaemon(true);
        return t;
    });

    private final List<LyricLine> demoLyrics = List.of(
            new LyricLine(500, "Is this the real life?"),
            new LyricLine(4100, "Is this just fantasy?"),
            new LyricLine(7900, "Caught in a landslide, no escape from reality"),
            new LyricLine(14800, "Open your eyes, look up to the skies and see"),
            new LyricLine(21500, "I'm just a poor boy, I need no sympathy"),
            new LyricLine(26200, "Because I'm easy come, easy go, little high, little low"),
            new LyricLine(35400, "Any way the wind blows doesn't really matter to me, to me"),
            new LyricLine(46800, "Mama, just killed a man"),
            new LyricLine(52400, "Put a gun against his head, pulled my trigger, now he's dead"),
            new LyricLine(59500, "Mama, life had just begun"),
            new LyricLine(65800, "But now I've gone and thrown it all away"),
            new LyricLine(72300, "Mama, ooh, didn't mean to make you cry"),
            new LyricLine(79200, "If I'm not back again this time tomorrow"),
            new LyricLine(85400, "Carry on, carry on as if nothing really matters")
    );

    private LyricsFetcher() {
        cache.put("queen - bohemian rhapsody", demoLyrics);
        cache.put(" - bohemian rhapsody", demoLyrics);
    }

    public List<LyricLine> getDemoLyrics() {
        return demoLyrics;
    }

    public List<LyricLine> getCachedLyrics(String artist, String title) {
        String key = makeKey(artist, title);
        return cache.get(key);
    }

    public CompletableFuture<List<LyricLine>> fetchLyricsAsync(String artist, String title) {
        String key = makeKey(artist, title);
        List<LyricLine> cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (!inFlight.add(key)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        CompletableFuture<List<LyricLine>> future = new CompletableFuture<>();
        pool.submit(() -> {
            try {
                List<LyricLine> result = fetchFromApi(artist, title);
                if (result != null && !result.isEmpty()) {
                    cache.put(key, result);
                    future.complete(result);
                } else {
                    future.complete(Collections.emptyList());
                }
            } catch (Throwable t) {
                future.complete(Collections.emptyList());
            } finally {
                inFlight.remove(key);
            }
        });
        return future;
    }

    public void setCustomLyrics(String key, List<LyricLine> lines) {
        if (key != null && lines != null) {
            cache.put(key.toLowerCase().trim(), lines);
        }
    }

    private List<LyricLine> fetchFromApi(String artist, String title) {
        String cleanTitle = cleanTitle(title);
        String cleanArtist = artist != null ? artist.trim() : "";

        String synced = tryGetEndpoint(cleanArtist, title);
        if (synced == null && !cleanTitle.equalsIgnoreCase(title)) {
            synced = tryGetEndpoint(cleanArtist, cleanTitle);
        }

        if (synced == null) {
            synced = trySearchEndpoint(cleanArtist + " " + cleanTitle);
        }

        if (synced != null && !synced.isBlank()) {
            return parseLrc(synced);
        }
        return Collections.emptyList();
    }

    private String tryGetEndpoint(String artist, String track) {
        if (track == null || track.isBlank()) return null;
        try {
            String url = API_BASE + "/get?artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                    + "&track_name=" + URLEncoder.encode(track, StandardCharsets.UTF_8);
            String json = httpGet(url);
            if (json != null && !json.isBlank()) {
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                    return obj.get("syncedLyrics").getAsString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String trySearchEndpoint(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String url = API_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String json = httpGet(url);
            if (json != null && !json.isBlank()) {
                JsonElement elem = JsonParser.parseString(json);
                if (elem.isJsonArray()) {
                    JsonArray arr = elem.getAsJsonArray();
                    for (JsonElement item : arr) {
                        if (item.isJsonObject()) {
                            JsonObject obj = item.getAsJsonObject();
                            if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                                return obj.get("syncedLyrics").getAsString();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) EggVisual/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    return sb.toString();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    public static List<LyricLine> parseLrc(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) return Collections.emptyList();
        List<LyricLine> list = new ArrayList<>();
        String[] rawLines = lrcContent.split("\\r?\\n");
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = LRC_PATTERN.matcher(line);
            if (m.matches()) {
                try {
                    int minutes = Integer.parseInt(m.group(1));
                    float seconds = Float.parseFloat(m.group(2));
                    long timeMs = (long) ((minutes * 60 + seconds) * 1000);
                    String text = m.group(3).trim();
                    if (!text.isEmpty()) {
                        list.add(new LyricLine(timeMs, text));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(list);
        return list;
    }

    public static String cleanTitle(String title) {
        if (title == null) return "";
        String t = title;
        t = t.replaceAll("(?i)\\(feat\\.[^)]+\\)", "");
        t = t.replaceAll("(?i)\\[feat\\.[^\\]]+\\]", "");
        t = t.replaceAll("(?i)\\(ft\\.[^)]+\\)", "");
        t = t.replaceAll("(?i)\\[ft\\.[^\\]]+\\]", "");
        t = t.replaceAll("(?i)\\(remaster(ed)?(\\s+\\d{4})?\\)", "");
        t = t.replaceAll("(?i)-\\s+remaster(ed)?(\\s+\\d{4})?", "");
        t = t.replaceAll("(?i)\\(official(\\s+music)?\\s+video\\)", "");
        t = t.replaceAll("(?i)\\(audio\\)", "");
        t = t.replaceAll("(?i)\\(visualizer\\)", "");
        t = t.replaceAll("(?i)\\[official video\\]", "");
        return t.trim();
    }

    public static String makeKey(String artist, String title) {
        String a = artist != null ? artist.trim().toLowerCase() : "";
        String t = title != null ? cleanTitle(title).toLowerCase() : "";
        return a + " - " + t;
    }
}
