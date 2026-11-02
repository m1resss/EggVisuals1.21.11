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
import java.text.Normalizer;
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
    private static final String NETEASE_BASE = "https://music.163.com/api";
    private static final Pattern LRC_PATTERN = Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2}(?:\\.\\d+)?)\\](.*)$");
    private static final Pattern SPLIT_FEAT = Pattern.compile("(?iu)\\s*(?:[,;/&×+]|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bvs\\.?\\b|\\bwith\\b|\\bx\\b|\\bи\\b)\\s*");

    private final Map<String, List<LyricLine>> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Egg-LyricsFetcher");
        t.setDaemon(true);
        return t;
    });

    // Demo lyrics matching the visual style in the reference screenshots
    private final List<LyricLine> demoLyrics = List.of(
            new LyricLine(0, "whats wrong im sick"),
            new LyricLine(2200, "with and twisted"),
            new LyricLine(4600, "i confess"),
            new LyricLine(7200, "fuck the"),
            new LyricLine(9400, "don't ever opps"),
            new LyricLine(11800, "Haunte fuck with"),
            new LyricLine(14200, "world, we"),
            new LyricLine(16800, "whats wrong im sick"),
            new LyricLine(19200, "and twisted i confess"),
            new LyricLine(22000, "don't ever fuck with world, we opps")
    );

    private LyricsFetcher() {
        cache.put("demo", demoLyrics);
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
                List<LyricLine> result = fetchFromApiCascade(artist, title);
                if (result != null && !result.isEmpty()) {
                    String clean = cleanTitle(title);
                    cache.put(key, result);
                    cache.put(makeKey(artist, clean), result);
                    cache.put(clean.toLowerCase().trim(), result);
                    if (cache.size() > 64) {
                        cache.clear();
                    }
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

    private List<LyricLine> fetchFromApiCascade(String artist, String title) {
        String cleanA = cleanArtist(artist);
        String cleanT = cleanTitle(title);

        // 1. LRCLIB Search (Tier 1)
        List<LyricLine> lrclibRes = searchLrclib(artist, cleanA, title, cleanT);
        if (lrclibRes != null && !lrclibRes.isEmpty()) {
            return lrclibRes;
        }

        // 2. NetEase Cloud Music Search (Tier 2)
        List<LyricLine> neteaseRes = searchNetEase(artist, cleanA, title, cleanT);
        if (neteaseRes != null && !neteaseRes.isEmpty()) {
            return neteaseRes;
        }

        // 3. Genius Fallback (Tier 3)
        List<LyricLine> geniusRes = searchGenius(artist, cleanT);
        if (geniusRes != null && !geniusRes.isEmpty()) {
            return geniusRes;
        }

        return Collections.emptyList();
    }

    // --- LRCLIB ---
    private List<LyricLine> searchLrclib(String artist, String cleanArtist, String title, String cleanTitle) {
        Map<String, String> headers = Map.of("User-Agent", "Rockstar/2.0 (https://github.com/rockstar)");
        List<String> queryList = new ArrayList<>();
        queryList.add("track_name=" + urlEncode(title) + "&artist_name=" + urlEncode(artist));
        if (!cleanArtist.isEmpty() && !cleanArtist.equalsIgnoreCase(artist.trim())) {
            queryList.add("track_name=" + urlEncode(title) + "&artist_name=" + urlEncode(cleanArtist));
        }
        queryList.add("track_name=" + urlEncode(title));
        queryList.add("q=" + urlEncode((cleanArtist.isEmpty() ? artist : cleanArtist) + " " + title));

        String bestLrc = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (String q : queryList) {
            String url = LRCLIB_BASE + "/search?" + q;
            String json = httpGet(url, headers);
            if (json == null || json.isBlank()) continue;
            try {
                JsonElement elem = JsonParser.parseString(json);
                if (elem.isJsonArray()) {
                    for (JsonElement item : elem.getAsJsonArray()) {
                        if (!item.isJsonObject()) continue;
                        JsonObject obj = item.getAsJsonObject();
                        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                            String synced = obj.get("syncedLyrics").getAsString();
                            if (synced != null && !synced.isBlank()) {
                                String candTrack = obj.has("trackName") && !obj.get("trackName").isJsonNull() ? obj.get("trackName").getAsString() : "";
                                String candArtist = obj.has("artistName") && !obj.get("artistName").isJsonNull() ? obj.get("artistName").getAsString() : "";
                                double candDur = obj.has("duration") && !obj.get("duration").isJsonNull() ? obj.get("duration").getAsDouble() : 0.0;

                                double score = scoreCandidate(candTrack, candArtist, candDur, artist, title, 0.0);
                                if (score > bestScore) {
                                    bestScore = score;
                                    bestLrc = synced;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            if (bestLrc != null) break;
        }

        if (bestLrc != null) {
            List<LyricLine> parsed = parseLrc(bestLrc);
            if (!parsed.isEmpty()) return parsed;
        }
        return null;
    }

    // --- NetEase Cloud Music ---
    private List<LyricLine> searchNetEase(String artist, String cleanArtist, String title, String cleanTitle) {
        Map<String, String> headers = Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Referer", "https://music.163.com"
        );

        List<String> queries = new ArrayList<>();
        queries.add(artist + " " + title);
        if (!cleanArtist.isEmpty() && !cleanArtist.equalsIgnoreCase(artist.trim())) {
            queries.add(cleanArtist + " " + title);
        }

        long bestSongId = -1L;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (String q : queries) {
            String url = NETEASE_BASE + "/search/get?type=1&limit=10&s=" + urlEncode(q);
            String json = httpGet(url, headers);
            if (json == null || json.isBlank()) continue;
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("result") && root.get("result").isJsonObject()) {
                    JsonObject res = root.getAsJsonObject("result");
                    if (res.has("songs") && res.get("songs").isJsonArray()) {
                        for (JsonElement se : res.getAsJsonArray("songs")) {
                            if (!se.isJsonObject()) continue;
                            JsonObject s = se.getAsJsonObject();
                            long id = s.has("id") && !s.get("id").isJsonNull() ? s.get("id").getAsLong() : -1L;
                            if (id < 0) continue;

                            String candTrack = s.has("name") && !s.get("name").isJsonNull() ? s.get("name").getAsString() : "";
                            StringBuilder sb = new StringBuilder();
                            if (s.has("artists") && s.get("artists").isJsonArray()) {
                                for (JsonElement ae : s.getAsJsonArray("artists")) {
                                    if (ae.isJsonObject()) {
                                        String an = ae.getAsJsonObject().has("name") ? ae.getAsJsonObject().get("name").getAsString() : "";
                                        if (!an.isBlank()) {
                                            if (sb.length() > 0) sb.append(", ");
                                            sb.append(an);
                                        }
                                    }
                                }
                            }
                            double candDur = s.has("duration") && !s.get("duration").isJsonNull() ? s.get("duration").getAsLong() / 1000.0 : 0.0;
                            double score = scoreCandidate(candTrack, sb.toString(), candDur, artist, title, 0.0);
                            if (score > bestScore) {
                                bestScore = score;
                                bestSongId = id;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            if (bestSongId >= 0) break;
        }

        if (bestSongId >= 0) {
            String lrcUrl = NETEASE_BASE + "/song/lyric?lv=1&kv=1&tv=-1&id=" + bestSongId;
            String lrcJson = httpGet(lrcUrl, headers);
            if (lrcJson != null && !lrcJson.isBlank()) {
                try {
                    JsonObject lroot = JsonParser.parseString(lrcJson).getAsJsonObject();
                    if (lroot.has("lrc") && lroot.get("lrc").isJsonObject()) {
                        JsonObject lrcObj = lroot.getAsJsonObject("lrc");
                        if (lrcObj.has("lyric") && !lrcObj.get("lyric").isJsonNull()) {
                            String lrc = lrcObj.get("lyric").getAsString();
                            List<LyricLine> parsed = parseLrc(lrc);
                            if (!parsed.isEmpty()) return parsed;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    // --- Genius Fallback ---
    private List<LyricLine> searchGenius(String artist, String cleanTitle) {
        Map<String, String> headers = Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Referer", "https://genius.com/"
        );
        String gQuery = (artist + " " + cleanTitle).trim();
        String url = "https://genius.com/api/search/song?per_page=5&q=" + urlEncode(gQuery);
        String json = httpGet(url, headers);
        if (json == null || json.isBlank()) return null;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("response") && root.get("response").isJsonObject()) {
                JsonObject resp = root.getAsJsonObject("response");
                if (resp.has("sections") && resp.get("sections").isJsonArray()) {
                    JsonArray sections = resp.getAsJsonArray("sections");
                    if (sections.size() > 0 && sections.get(0).isJsonObject()) {
                        JsonObject sec0 = sections.get(0).getAsJsonObject();
                        if (sec0.has("hits") && sec0.get("hits").isJsonArray()) {
                            for (JsonElement hit : sec0.getAsJsonArray("hits")) {
                                if (hit.isJsonObject()) {
                                    JsonObject res = hit.getAsJsonObject().getAsJsonObject("result");
                                    if (res != null && res.has("url") && !res.get("url").isJsonNull()) {
                                        String songUrl = res.get("url").getAsString();
                                        String html = httpGet(songUrl, headers);
                                        if (html != null && !html.isBlank()) {
                                            List<LyricLine> lines = parseGeniusHtml(html);
                                            if (!lines.isEmpty()) return lines;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private List<LyricLine> parseGeniusHtml(String html) {
        List<LyricLine> list = new ArrayList<>();
        Pattern containerPat = Pattern.compile("<div[^>]*class=\"[^\"]*Lyrics__Container[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
        Matcher m = containerPat.matcher(html);
        StringBuilder full = new StringBuilder();
        while (m.find()) {
            full.append(m.group(1)).append("\n");
        }
        if (full.length() == 0) return list;
        String text = full.toString().replaceAll("<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        long t = 0L;
        for (String s : text.split("\n")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("[")) {
                list.add(new LyricLine(t, trimmed));
                t += 3000L;
            }
        }
        return list;
    }

    // --- Scoring & Normalization (from ConfigInternal037) ---
    public static double scoreCandidate(String candTrack, String candArtist, double candDur,
                                        String targetArtist, String targetTitle, double targetDur) {
        String tTrack = normalizeText(targetTitle);
        String cTrack = normalizeText(candTrack);
        double trackSim = levenshteinSim(tTrack, cTrack);
        boolean isPrefix = cTrack.equals(tTrack) || cTrack.startsWith(tTrack + " ") || tTrack.startsWith(cTrack + " ");
        if (!isPrefix && trackSim < 0.55) {
            return Double.NEGATIVE_INFINITY;
        }
        if (cTrack.equals(tTrack)) {
            trackSim += 1.0;
        } else if (isPrefix) {
            trackSim += 0.85;
        }

        double durDiff = -1.0;
        double durScore = 0.0;
        if (targetDur > 0 && candDur > 0) {
            durDiff = Math.abs(candDur - targetDur);
            durScore = durDiff <= 2.0 ? 2.0 : Math.max(-1.0, 1.0 - durDiff / 15.0);
        }

        double artScore = artistSim(targetArtist, candArtist);
        if (artScore < 0.62 && !(durDiff >= 0.0 && durDiff <= 2.0)) {
            return Double.NEGATIVE_INFINITY;
        }

        return trackSim * 1000.0 + artScore * 900.0 + durScore * 1200.0;
    }

    public static String cleanArtist(String artist) {
        if (artist == null || artist.isBlank()) return "";
        String[] parts = SPLIT_FEAT.split(artist.trim());
        return parts.length == 0 ? artist.trim() : parts[0].trim();
    }

    public static String cleanTitle(String title) {
        if (title == null || title.isBlank()) return "";
        String s = title.replaceAll("(?i)\\s*[\\[(](?:feat\\.?|ft\\.?|featuring|prod\\.?|with|official\\s+(?:audio|video|music\\s+video)|remaster(?:ed)?|live|radio\\s+edit)\\b.*?[\\Item\\])]", "");
        s = s.replaceAll("(?i)\\s*[-–—]\\s*(?:remaster(?:ed)?|live|radio edit|single version|official audio).*$", "");
        return s.trim();
    }

    public static String normalizeText(String s) {
        if (s == null || s.isBlank()) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        String stripped = nfd.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ё', 'е');
        return stripped.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    public static double levenshteinSim(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        int l1 = s1.length();
        int l2 = s2.length();
        int[] dp = new int[l2 + 1];
        for (int j = 0; j <= l2; j++) dp[j] = j;
        for (int i = 1; i <= l1; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= l2; j++) {
                int temp = dp[j];
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = temp;
            }
        }
        return 1.0 - (double) dp[l2] / (double) Math.max(l1, l2);
    }

    public static double artistSim(String targetArtist, String candArtist) {
        String tNorm = normalizeText(targetArtist);
        String cNorm = normalizeText(candArtist);
        if (tNorm.isEmpty() || cNorm.isEmpty()) return 1.0;
        if (tNorm.equals(cNorm) || tNorm.contains(cNorm) || cNorm.contains(tNorm)) return 1.0;

        Set<String> tTokens = new HashSet<>();
        for (String p : SPLIT_FEAT.split(targetArtist)) {
            String n = normalizeText(p);
            if (!n.isEmpty()) tTokens.add(n);
        }
        for (String p : SPLIT_FEAT.split(candArtist)) {
            String n = normalizeText(p);
            if (!n.isEmpty() && tTokens.contains(n)) return 1.0;
        }
        return levenshteinSim(tNorm, cNorm);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }

    private static String httpGet(String urlStr, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(4000);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
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
                    // Strip inline karaoke tags <mm:ss.xx> for clean text
                    text = text.replaceAll("<\\d{1,3}:\\d{2}[.,]\\d{2,3}>", "").trim();
                    if (!text.isEmpty()) {
                        list.add(new LyricLine(timeMs, text));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(list);
        return list;
    }

    public static String makeKey(String artist, String title) {
        String a = artist != null ? artist.trim().toLowerCase() : "";
        String t = title != null ? cleanTitle(title).toLowerCase() : "";
        return a + " - " + t;
    }
}
