package egg.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import egg.client.module.misc.DiscordRPC;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EggUpdateChecker {
    private static final String VERSION_URL = "https://raw.githubusercontent.com/m1resss/EggVisuals1.21.11/main/version.json";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static volatile UpdateInfo availableUpdate;

    private EggUpdateChecker() {
    }

    public static void checkAsync() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                String current = getCurrentVersion();
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(4))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_URL))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "EggVisuals/" + current)
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                    return;
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("version")) return;
                String remote = json.get("version").getAsString().trim();
                if (remote.isEmpty() || !isNewer(remote, current)) return;
                String url = json.has("url") && !json.get("url").getAsString().isBlank()
                        ? json.get("url").getAsString().trim()
                        : DiscordRPC.TELEGRAM_URL;
                availableUpdate = new UpdateInfo(remote, url);
            } catch (Exception ignored) {
            }
        }, "egg-update-checker");
        worker.setDaemon(true);
        worker.start();
    }

    public static UpdateInfo getAvailableUpdate() {
        return availableUpdate;
    }

    public static String getCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer("egg")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("1.0.0");
    }

    public static boolean isNewer(String remote, String current) {
        int[] r = parseParts(remote);
        int[] c = parseParts(current);
        int len = Math.max(r.length, c.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (rv != cv) {
                return rv > cv;
            }
        }
        return false;
    }

    private static int[] parseParts(String version) {
        String cleaned = version.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        String[] tokens = cleaned.split("\\.");
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                String digits = tokens[i].replaceAll("[^0-9]", "");
                parts[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    public record UpdateInfo(String version, String url) {
    }
}
