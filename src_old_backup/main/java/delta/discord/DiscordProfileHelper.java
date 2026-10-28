package delta.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import delta.core.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class DiscordProfileHelper implements Interface {
    private static final DiscordProfileHelper INSTANCE = new DiscordProfileHelper();

    private String username = "Player";
    private String tag = "Offline";
    private Identifier avatarTexture = null;
    private boolean connected = false;
    private final File cacheFile = new File("egg_discord.json");

    public static DiscordProfileHelper getInstance() {
        return INSTANCE;
    }

    private DiscordProfileHelper() {
        loadCache();
        startWorker();
    }

    public String getUsername() {
        if (!connected && (username == null || username.equals("Player"))) {
            if (aM_.getSession() != null && aM_.getSession().getUsername() != null) {
                return aM_.getSession().getUsername();
            }
        }
        return this.username;
    }

    public String getTag() {
        return this.tag;
    }

    public Identifier getAvatarTexture() {
        return this.avatarTexture;
    }

    public boolean isConnected() {
        return this.connected;
    }

    private void loadCache() {
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("username")) {
                    this.username = json.get("username").getAsString();
                }
                if (json.has("tag")) {
                    this.tag = json.get("tag").getAsString();
                }
                if (json.has("avatar_data")) {
                    String base64 = json.get("avatar_data").getAsString();
                    byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                    loadAvatarFromBytes(bytes);
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveCache(byte[] avatarBytes) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("username", this.username);
            json.addProperty("tag", this.tag);
            if (avatarBytes != null && avatarBytes.length > 0) {
                json.addProperty("avatar_data", java.util.Base64.getEncoder().encodeToString(avatarBytes));
            }
            try (FileWriter writer = new FileWriter(cacheFile, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {}
    }

    private void startWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                if (!connected) {
                    tryConnectIpc();
                }
                try {
                    Thread.sleep(connected ? 30000 : 5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Egg-DiscordProfileService");
        worker.setDaemon(true);
        worker.start();
    }

    public void updateFromUserData(JsonObject userObj) {
        if (userObj == null) return;
        try {
            String userId = userObj.has("id") ? userObj.get("id").getAsString() : null;
            String uname = userObj.has("username") ? userObj.get("username").getAsString() : "User";
            String globalName = userObj.has("global_name") && !userObj.get("global_name").isJsonNull()
                    ? userObj.get("global_name").getAsString() : uname;
            String avatarHash = userObj.has("avatar") && !userObj.get("avatar").isJsonNull()
                    ? userObj.get("avatar").getAsString() : null;

            this.username = globalName != null && !globalName.isEmpty() ? globalName : uname;
            this.tag = "@" + uname;
            this.connected = true;

            if (userId != null && avatarHash != null) {
                String avatarUrl = "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png?size=128";
                downloadAvatar(avatarUrl);
            } else {
                saveCache(null);
            }
        } catch (Exception ignored) {}
    }

    private void tryConnectIpc() {
        for (int i = 0; i < 10; i++) {
            File pipeFile = new File("\\\\.\\pipe\\discord-ipc-" + i);
            if (!pipeFile.exists()) {
                continue;
            }

            try (RandomAccessFile pipe = new RandomAccessFile(pipeFile, "rw")) {
                // Opcode 0 = HANDSHAKE
                String handshakeJson = "{\"v\":1,\"client_id\":\"1559901833848299602\"}";
                byte[] data = handshakeJson.getBytes(StandardCharsets.UTF_8);

                ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(0); // Opcode 0
                buf.putInt(data.length);
                buf.put(data);
                pipe.write(buf.array());

                // Read response header (8 bytes)
                byte[] header = new byte[8];
                pipe.readFully(header);
                ByteBuffer resBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int opcode = resBuf.getInt();
                int length = resBuf.getInt();

                if (opcode == 1 && length > 0 && length < 1024 * 1024) { // FRAME
                    byte[] payload = new byte[length];
                    pipe.readFully(payload);
                    String response = new String(payload, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(response).getAsJsonObject();

                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject resData = root.getAsJsonObject("data");
                        if (resData.has("user") && resData.get("user").isJsonObject()) {
                            JsonObject userObj = resData.getAsJsonObject("user");
                            updateFromUserData(userObj);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Pipe busy or error, check next
            }
        }
    }

    private void downloadAvatar(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "EggClient/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                byte[] bytes = out.toByteArray();
                loadAvatarFromBytes(bytes);
                saveCache(bytes);
            }
        } catch (Exception ignored) {}
    }

    private void loadAvatarFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        MinecraftClient.getInstance().execute(() -> {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                Identifier id = Identifier.of("delta", "discord/avatar_" + System.currentTimeMillis());
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "discord_avatar", image));
                this.avatarTexture = id;
            } catch (Exception ignored) {}
        });
    }
}
