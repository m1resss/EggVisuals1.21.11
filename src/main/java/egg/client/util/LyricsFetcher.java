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

    private static final String LRCLIB_BASE = "https://lrclib.net/api";
    private static final Pattern LRC_PATTERN = Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2}(?:\\.\\d+)?)\\](.*)$");

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
        cache.put("bohemian rhapsody", demoLyrics);
    }

    public List<LyricLine> getDemoLyrics() {
        return demoLyrics;
    }

    public List<LyricLine> getCachedLyrics(String artist, String title) {
        if (title == null || title.isBlank()) return null;
        String rawKey = makeKey(artist, title);
        List<LyricLine> found = cache.get(rawKey);
        if (found != null) return found;

        String clean = cleanTitle(title);
        String cleanKey = makeKey(artist, clean);
        found = cache.get(cleanKey);
        if (found != null) return found;

        String bareTitleKey = clean.toLowerCase().trim();
        return cache.get(bareTitleKey);
    }

    public CompletableFuture<List<LyricLine>> fetchLyricsAsync(String artist, String title) {
        String key = makeKey(artist, title);
        List<LyricLine> cached = getCachedLyrics(artist, title);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (!inFlight.add(key)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<List<LyricLine>> future = new CompletableFuture<>();
        pool.submit(() -> {
            try {
                List<LyricLine> result = fetchFromApi(artist, title);
                if (result != null && !result.isEmpty()) {
                    String clean = cleanTitle(title);
                    cache.put(key, result);
                    cache.put(makeKey(artist, clean), result);
                    cache.put(clean.toLowerCase().trim(), result);
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
        String cleanT = cleanTitle(title);
        String cleanA = artist != null ? artist.trim() : "";

        // 1. Try artist + clean title on Kugou
        String synced = null;
        if (!cleanA.isEmpty() && !cleanT.isEmpty()) {
            synced = fetchFromKugou(cleanA + " " + cleanT);
        }

        // 2. Try clean title alone on Kugou (crucial for remixes/covers like Epizoon - 505)
        if (synced == null && !cleanT.isEmpty()) {
            synced = fetchFromKugou(cleanT);
        }

        // 3. Try artist + raw title if different
        if (synced == null && !title.equalsIgnoreCase(cleanT) && !cleanA.isEmpty()) {
            synced = fetchFromKugou(cleanA + " " + title);
        }

        // 4. Try raw title alone if different
        if (synced == null && !title.equalsIgnoreCase(cleanT)) {
            synced = fetchFromKugou(title);
        }

        // 5. Fallback to LrcLib API
        if (synced == null) {
            synced = tryGetLrclib(cleanA, cleanT);
        }
        if (synced == null && !cleanA.isEmpty()) {
            synced = trySearchLrclib(cleanA + " " + cleanT);
        }
        if (synced == null && !cleanT.isEmpty()) {
            synced = trySearchLrclib(cleanT);
        }

        if (synced != null && !synced.isBlank()) {
            return parseLrc(synced);
        }
        return Collections.emptyList();
    }

    private static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    private static boolean isLrcCjkDominant(String text) {
        if (text == null) return false;
        int cjk = 0;
        int alpha = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                cjk++;
                alpha++;
            } else if (Character.isLetter(c)) {
                alpha++;
            }
        }
        return alpha > 0 && ((float) cjk / (float) alpha) > 0.35f;
    }

    private static boolean isValidLrc(String lrc, boolean queryHasCjk) {
        if (lrc == null || lrc.trim().length() < 20) return false;
        if (!queryHasCjk && isLrcCjkDominant(lrc)) return false;

        String[] rawLines = lrc.split("\\r?\\n");
        int realCount = 0;
        for (String raw : rawLines) {
            String l = raw.trim();
            if (l.startsWith("[0") || l.startsWith("[1") || l.startsWith("[2")) {
                if (!l.contains("纯音乐") && !l.contains("暂无歌词") && !l.contains("作词") && !l.contains("作曲")) {
                    realCount++;
                }
            }
        }
        return realCount >= 3;
    }

    private String fetchFromKugou(String query) {
        if (query == null || query.isBlank()) return null;
        boolean queryHasCjk = hasCjk(query);
        try {
            String searchUrl = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                    + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8) + "&page=1&pagesize=5";
            String searchJson = httpGet(searchUrl);
            if (searchJson == null || searchJson.isBlank()) return null;

            JsonObject sObj = JsonParser.parseString(searchJson).getAsJsonObject();
            if (!sObj.has("data") || !sObj.get("data").isJsonObject()) return null;
            JsonObject dObj = sObj.getAsJsonObject("data");
            if (!dObj.has("info") || !dObj.get("info").isJsonArray()) return null;
            JsonArray infoArr = dObj.getAsJsonArray("info");
            if (infoArr.isEmpty()) return null;

            for (JsonElement elem : infoArr) {
                if (!elem.isJsonObject()) continue;
                JsonObject song = elem.getAsJsonObject();
                if (!song.has("hash")) continue;
                String hash = song.get("hash").getAsString();
                if (hash == null || hash.isBlank()) continue;

                String candUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword="
                        + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8) + "&hash=" + hash;
                String candJson = httpGet(candUrl);
                if (candJson == null || candJson.isBlank()) continue;

                JsonObject cObj = JsonParser.parseString(candJson).getAsJsonObject();
                if (!cObj.has("candidates") || !cObj.get("candidates").isJsonArray()) continue;
                JsonArray cands = cObj.getAsJsonArray("candidates");
                if (cands.isEmpty()) continue;

                for (JsonElement candElem : cands) {
                    if (!candElem.isJsonObject()) continue;
                    JsonObject cand = candElem.getAsJsonObject();
                    if (!cand.has("id") || !cand.has("accesskey")) continue;
                    String candId = cand.get("id").getAsString();
                    String accessKey = cand.get("accesskey").getAsString();

                    String dlUrl = "http://krcs.kugou.com/download?ver=1&client=mobi&id="
                            + candId + "&accesskey=" + accessKey + "&fmt=lrc&charset=utf8";
                    String dlJson = httpGet(dlUrl);
                    if (dlJson == null || dlJson.isBlank()) continue;

                    JsonObject dlObj = JsonParser.parseString(dlJson).getAsJsonObject();
                    if (dlObj.has("content")) {
                        String base64Content = dlObj.get("content").getAsString();
                        if (base64Content != null && !base64Content.isBlank()) {
                            byte[] decoded = Base64.getDecoder().decode(base64Content);
                            String lrc = new String(decoded, StandardCharsets.UTF_8);
                            if (isValidLrc(lrc, queryHasCjk)) {
                                return lrc;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String tryGetLrclib(String artist, String track) {
        if (track == null || track.isBlank()) return null;
        try {
            String url = LRCLIB_BASE + "/get?artist_name=" + URLEncoder.encode(artist != null ? artist : "", StandardCharsets.UTF_8)
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

    private String trySearchLrclib(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String url = LRCLIB_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String json = httpGet(url);
            if (json != null && !json.isBlank()) {
                JsonElement elem = JsonParser.parseString(json);
                if (elem.isJsonArray()) {
                    JsonArray arr = elem.getAsJsonArray();
                    for (JsonElement item : arr) {
                        if (item.isJsonObject()) {
                            JsonObject obj = item.getAsJsonObject();
                            if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                                String s = obj.get("syncedLyrics").getAsString();
                                if (s != null && !s.isBlank()) {
                                    return s;
                                }
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
            conn.setRequestProperty("Accept", "*/*");

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
        lrcContent = lrcContent.replace("\uFEFF", "").replace("\u200B", "");
        List<LyricLine> list = new ArrayList<>();
        String[] rawLines = lrcContent.split("\\r?\\n");
        for (String raw : rawLines) {
            String line = raw.trim().replace("\uFEFF", "");
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
        // Strip parenthesized remixes and modifiers
        t = t.replaceAll("(?i)\\((?:hoodtrap|remix|sped up|slowed|edit|tiktok version|tik tok version|drill|phonk|acoustic|cover|instrumental|bass boosted|nightcore|reverb|radio edit|extended|vip|prod\\.[^)]*)\\)", "");
        t = t.replaceAll("(?i)\\[(?:hoodtrap|remix|sped up|slowed|edit|tiktok version|tik tok version|drill|phonk|acoustic|cover|instrumental|bass boosted|nightcore|reverb|radio edit|extended|vip|prod\\.[^\\]]*)\\]", "");
        // Strip hyphens with modifiers
        t = t.replaceAll("(?i)-\\s*(?:hoodtrap|remix|sped up|slowed|edit|drill|phonk|acoustic|bass boosted|nightcore|reverb).*$", "");
        // Strip feat / ft / with / prod
        t = t.replaceAll("(?i)\\((?:feat|ft|with|prod)\\.[^)]+\\)", "");
        t = t.replaceAll("(?i)\\[(?:feat|ft|with|prod)\\.[^\\]]+\\]", "");
        // Strip remasters and video tags
        t = t.replaceAll("(?i)\\((?:remaster|remastered|official|audio|visualizer|deluxe|bonus track)[^)]*\\)", "");
        t = t.replaceAll("(?i)\\[(?:remaster|remastered|official|audio|visualizer|deluxe|bonus track)[^\\]]*\\]", "");
        t = t.replaceAll("(?i)-\\s*(?:remaster|remastered|deluxe).*$", "");
        t = t.replaceAll("\\s+", " ");
        return t.trim();
    }

    public static String makeKey(String artist, String title) {
        String a = artist != null ? artist.trim().toLowerCase() : "";
        String t = title != null ? cleanTitle(title).toLowerCase() : "";
        return a + " - " + t;
    }
}
