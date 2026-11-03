package egg.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import egg.client.command.CommandManager;

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
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Egg-LyricsFetcher");
        t.setDaemon(true);
        return t;
    });

    private LyricsFetcher() {}

    public static String makeKey(String artist, String title) {
        String a = artist != null ? artist.trim().toLowerCase(Locale.ROOT) : "";
        String t = title != null ? title.trim().toLowerCase(Locale.ROOT) : "";
        return a + " - " + t;
    }

    public List<LyricLine> getCachedLyrics(String artist, String title) {
        if (title == null || title.isBlank()) return null;
        String key = makeKey(artist, title);
        List<LyricLine> lines = cache.get(key);
        if (lines != null) return lines;
        return cache.get(title.trim().toLowerCase(Locale.ROOT));
    }

    public CompletableFuture<List<LyricLine>> fetchLyricsAsync(String artist, String title) {
        String[] parsed = parseArtistAndTitle(artist, title);
        String finalArtist = parsed[0];
        String finalTitle = parsed[1];

        if (finalTitle.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String key = makeKey(finalArtist, finalTitle);
        List<LyricLine> cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (!inFlight.add(key)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        CompletableFuture<List<LyricLine>> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                safeSendClientMessage("§7[Lyrics] Поиск текста: §f" + (finalArtist.isEmpty() ? "" : finalArtist + " - ") + finalTitle + "...");
                List<LyricLine> lines = fetchFromApiCascade(finalArtist, finalTitle);
                if (lines != null && !lines.isEmpty()) {
                    cache.put(key, lines);
                    cache.put(finalTitle.toLowerCase(Locale.ROOT), lines);
                    safeSendClientMessage("§d[Lyrics] §aНайден текст: §f" + (finalArtist.isEmpty() ? "" : finalArtist + " - ") + finalTitle + " §7(" + lines.size() + " строк)");
                    future.complete(lines);
                } else {
                    safeSendClientMessage("§d[Lyrics] §cТекст не найден: §f" + (finalArtist.isEmpty() ? "" : finalArtist + " - ") + finalTitle);
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

    private static void safeSendClientMessage(String msg) {
        try {
            CommandManager.sendClientMessage(msg, false);
        } catch (Throwable ignored) {}
    }

    private static String[] parseArtistAndTitle(String artist, String title) {
        String a = artist != null ? artist.trim() : "";
        String t = title != null ? title.trim() : "";
        if (a.isEmpty()) {
            for (String sep : new String[]{" - ", " — ", " – ", " / ", " | "}) {
                int idx = t.indexOf(sep);
                if (idx > 0 && idx + sep.length() < t.length()) {
                    a = t.substring(0, idx).trim();
                    t = t.substring(idx + sep.length()).trim();
                    break;
                }
            }
        }
        return new String[]{a, t};
    }

    private List<LyricLine> fetchFromApiCascade(String artist, String title) {
        String cleanA = cleanArtist(artist);
        String cleanT = cleanTitle(title);

        // 1. Primary: NetEase Cloud Music (fast, reliable, millisecond timestamps)
        List<LyricLine> neteaseRes = searchNetEase(artist, cleanA, title, cleanT);
        if (neteaseRes != null && !neteaseRes.isEmpty()) {
            return neteaseRes;
        }

        // 2. Secondary: LRCLIB (LRC format)
        List<LyricLine> lrclibRes = searchLrclib(artist, cleanA, title, cleanT);
        if (lrclibRes != null && !lrclibRes.isEmpty()) {
            return lrclibRes;
        }

        // 3. Fallback: Genius API
        List<LyricLine> geniusRes = searchGenius(artist, cleanT);
        if (geniusRes != null && !geniusRes.isEmpty()) {
            return geniusRes;
        }

        return Collections.emptyList();
    }

    // --- NetEase Cloud Music ---
    private List<LyricLine> searchNetEase(String artist, String cleanArtist, String title, String cleanTitle) {
        Map<String, String> headers = Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Referer", "https://music.163.com"
        );

        List<String> queries = new ArrayList<>();
        if (!artist.isEmpty()) {
            queries.add((artist + " " + title).trim());
            if (!cleanArtist.isEmpty() && !cleanArtist.equalsIgnoreCase(artist.trim())) {
                queries.add((cleanArtist + " " + cleanTitle).trim());
            }
        }
        queries.add(title.trim());
        if (!cleanTitle.isEmpty() && !cleanTitle.equalsIgnoreCase(title.trim())) {
            queries.add(cleanTitle.trim());
        }

        long bestSongId = -1L;
        double bestScore = Double.NEGATIVE_INFINITY;
        long firstSongIdFallback = -1L;

        for (String q : queries) {
            String url = NETEASE_BASE + "/search/get?type=1&limit=10&s=" + urlEncode(q);
            String json = httpGet(url, headers);
            if (json == null || json.isBlank()) continue;
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (!root.has("result") || !root.get("result").isJsonObject()) continue;
                JsonObject resObj = root.getAsJsonObject("result");
                if (!resObj.has("songs") || !resObj.get("songs").isJsonArray()) continue;
                JsonArray songs = resObj.getAsJsonArray("songs");
                if (songs.size() == 0) continue;

                if (firstSongIdFallback < 0 && songs.get(0).isJsonObject()) {
                    JsonObject first = songs.get(0).getAsJsonObject();
                    if (first.has("id") && !first.get("id").isJsonNull()) {
                        firstSongIdFallback = first.get("id").getAsLong();
                    }
                }

                for (JsonElement item : songs) {
                    if (!item.isJsonObject()) continue;
                    JsonObject sobj = item.getAsJsonObject();
                    if (!sobj.has("id") || sobj.get("id").isJsonNull()) continue;
                    long sid = sobj.get("id").getAsLong();
                    String sname = sobj.has("name") && !sobj.get("name").isJsonNull() ? sobj.get("name").getAsString() : "";
                    StringBuilder arts = new StringBuilder();
                    if (sobj.has("artists") && sobj.get("artists").isJsonArray()) {
                        for (JsonElement ae : sobj.getAsJsonArray("artists")) {
                            if (ae.isJsonObject()) {
                                JsonObject aobj = ae.getAsJsonObject();
                                if (aobj.has("name") && !aobj.get("name").isJsonNull()) {
                                    if (arts.length() > 0) arts.append(", ");
                                    arts.append(aobj.get("name").getAsString());
                                }
                            }
                        }
                    }
                    double dur = sobj.has("duration") && !sobj.get("duration").isJsonNull() ? sobj.get("duration").getAsDouble() / 1000.0 : 0.0;
                    double score = scoreCandidate(sname, arts.toString(), dur, artist, title, 0.0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSongId = sid;
                    }
                }
            } catch (Throwable ignored) {}
            if (bestSongId >= 0 && bestScore > 500.0) break;
        }

        if (bestSongId < 0 && firstSongIdFallback >= 0) {
            bestSongId = firstSongIdFallback;
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

    // --- LRCLIB ---
    private List<LyricLine> searchLrclib(String artist, String cleanArtist, String title, String cleanTitle) {
        Map<String, String> headers = Map.of(
                "User-Agent", "Rockstar/2.0 (https://github.com/rockstar)"
        );
        List<String> queryList = new ArrayList<>();
        // 1. Broad combined search first (most reliable on LRCLIB)
        String mainQuery = (cleanArtist.isEmpty() ? artist : cleanArtist) + " " + (cleanTitle.isEmpty() ? title : cleanTitle);
        queryList.add("q=" + urlEncode(mainQuery.trim()));
        if (!artist.isEmpty()) {
            queryList.add("track_name=" + urlEncode(title) + "&artist_name=" + urlEncode(artist));
            if (!cleanArtist.isEmpty() && !cleanArtist.equalsIgnoreCase(artist.trim())) {
                queryList.add("track_name=" + urlEncode(title) + "&artist_name=" + urlEncode(cleanArtist));
            }
        }
        queryList.add("track_name=" + urlEncode(cleanTitle.isEmpty() ? title : cleanTitle));
        queryList.add("q=" + urlEncode(title));

        String bestLrc = null;
        String bestPlain = null;
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
                        String candTrack = obj.has("trackName") && !obj.get("trackName").isJsonNull() ? obj.get("trackName").getAsString() : "";
                        String candArtist = obj.has("artistName") && !obj.get("artistName").isJsonNull() ? obj.get("artistName").getAsString() : "";
                        double candDur = obj.has("duration") && !obj.get("duration").isJsonNull() ? obj.get("duration").getAsDouble() : 0.0;

                        double score = scoreCandidate(candTrack, candArtist, candDur, artist, title, 0.0);
                        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                            String synced = obj.get("syncedLyrics").getAsString();
                            if (synced != null && !synced.isBlank() && score > bestScore) {
                                bestScore = score;
                                bestLrc = synced;
                            }
                        } else if (bestLrc == null && obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()) {
                            String plain = obj.get("plainLyrics").getAsString();
                            if (plain != null && !plain.isBlank()) {
                                bestPlain = plain;
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
        if (bestPlain != null) {
            List<LyricLine> parsed = parsePlainLyrics(bestPlain);
            if (!parsed.isEmpty()) return parsed;
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
        return parsePlainLyrics(text);
    }

    private static List<LyricLine> parsePlainLyrics(String text) {
        List<LyricLine> list = new ArrayList<>();
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

    public static double scoreCandidate(String candTrack, String candArtist, double candDur,
                                        String targetArtist, String targetTitle, double targetDur) {
        String tTrack = normalizeText(targetTitle);
        String cTrack = normalizeText(candTrack);
        if (tTrack.isEmpty() || cTrack.isEmpty()) return 0.0;

        String cleanCandTrack = candTrack;
        int hyphen = candTrack.lastIndexOf(" - ");
        if (hyphen >= 0 && hyphen + 3 < candTrack.length()) {
            cleanCandTrack = candTrack.substring(hyphen + 3).trim();
        }
        String cleanCTrack = normalizeText(cleanCandTrack);

        double trackSim = Math.max(levenshteinSim(tTrack, cTrack), levenshteinSim(tTrack, cleanCTrack));
        boolean isMatch = cTrack.equals(tTrack)
                || cleanCTrack.equals(tTrack)
                || cTrack.contains(tTrack)
                || tTrack.contains(cTrack)
                || cleanCTrack.contains(tTrack)
                || tTrack.contains(cleanCTrack)
                || cTrack.startsWith(tTrack)
                || cTrack.endsWith(tTrack);

        if (isMatch) {
            trackSim = Math.max(trackSim, 0.90);
        }

        if (!isMatch && trackSim < 0.35) {
            return Double.NEGATIVE_INFINITY;
        }

        double artScore = targetArtist.isBlank() ? 1.0 : artistSim(targetArtist, candArtist);
        if (artScore < 0.25 && !isMatch) {
            return Double.NEGATIVE_INFINITY;
        }

        return trackSim * 1000.0 + artScore * 900.0;
    }

    public static String cleanArtist(String artist) {
        if (artist == null || artist.isBlank()) return "";
        String[] parts = SPLIT_FEAT.split(artist.trim());
        return parts.length == 0 ? artist.trim() : parts[0].trim();
    }

    public static String cleanTitle(String title) {
        if (title == null || title.isBlank()) return "";
        String s = title.replaceAll("(?i)\\s*[\\[(](?:feat\\.?|ft\\.?|featuring|prod\\.?|with|official\\s+(?:audio|video|music\\s+video)|remaster(?:ed)?|live|radio\\s+edit)\\b.*?[\\]\\)]", "");
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
        int l1 = s1.length(), l2 = s2.length();
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
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3500);
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
        List<LyricLine> list = new ArrayList<>();
        String[] lines = lrcContent.split("\n");

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher m = LRC_PATTERN.matcher(line);
            if (m.matches()) {
                long minutes = Long.parseLong(m.group(1));
                double seconds = Double.parseDouble(m.group(2));
                long timeMs = minutes * 60000L + (long) (seconds * 1000.0);
                String text = m.group(3).replaceAll("<[^>]+>", "").trim();

                if (!text.isEmpty() && !text.startsWith("作词") && !text.startsWith("作曲") && !text.startsWith("编曲")) {
                    list.add(new LyricLine(timeMs, text));
                }
            }
        }

        list.sort(Comparator.comparingLong(LyricLine::timeMs));
        return list;
    }
}
