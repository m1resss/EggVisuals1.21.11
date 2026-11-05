package egg.client.module.minigames.chess;

import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChessRelay {
    private static final String ENDPOINT = "https://ntfy.sh/eggchess_";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Chess-Relay-Worker");
        t.setDaemon(true);
        return t;
    });

    private static Thread listenerThread;
    private static volatile boolean active = false;
    private static String currentRoom = "";
    private static volatile boolean connected = false;

    private ChessRelay() {}

    public static synchronized void connect(String roomCode) {
        disconnect();
        if (roomCode == null || roomCode.trim().isEmpty()) return;

        currentRoom = roomCode.trim().toLowerCase();
        active = true;

        listenerThread = new Thread(() -> {
            while (active) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(ENDPOINT + currentRoom + "/raw"))
                            .timeout(Duration.ofMinutes(15))
                            .GET()
                            .build();

                    HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() == 200) {
                        connected = true;
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while (active && (line = reader.readLine()) != null) {
                                String msg = line.trim();
                                if (!msg.isEmpty()) {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc != null) {
                                        mc.execute(() -> Chess.getInstance().handleIncomingPayload(msg));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                if (active) {
                    connected = false;
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            connected = false;
        }, "Chess-Relay-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public static synchronized void disconnect() {
        active = false;
        connected = false;
        currentRoom = "";
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    public static void send(String payload) {
        if (!active || currentRoom.isEmpty() || payload == null || payload.isEmpty()) return;

        executor.submit(() -> {
            try {
                HttpRequest post = HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT + currentRoom))
                        .timeout(Duration.ofSeconds(4))
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                client.sendAsync(post, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }

    public static boolean isConnected() {
        return active && connected;
    }

    public static String getCurrentRoom() {
        return currentRoom;
    }
}
