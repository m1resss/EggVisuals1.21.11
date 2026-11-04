package egg.client.module.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import egg.client.discord.DiscordIPC;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordRPC extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("Egg/DiscordRPC");

    public static final String DEFAULT_CLIENT_ID = "1559901833848299602";
    public static final String TELEGRAM_URL = "https://t.me/ClientEgg";
    public static final String DEFAULT_AVATAR_URL = "https://raw.githubusercontent.com/m1resss/EggVisuals1.21.11/main/rpc_logo_v2.jpg";
    public static final String CLIENT_VERSION = "1.21.11";

    private final BooleanSetting showServer = new BooleanSetting("Показывать сервер", true, "Отображать адрес сервера, на котором вы играете");
    private final BooleanSetting showTimer = new BooleanSetting("Показывать время", true, "Отображать время с момента запуска игры");
    private final StringSetting clientId = new StringSetting("Client ID", DEFAULT_CLIENT_ID, "ID Discord приложения");
    private final StringSetting avatarUrl = new StringSetting("URL логотипа", DEFAULT_AVATAR_URL, "Ссылка на логотип для Discord RPC");

    private final DiscordIPC ipc = new DiscordIPC();
    private volatile boolean running = false;
    private Thread workerThread = null;
    private final long sessionStartTime = System.currentTimeMillis() / 1000L;
    private String lastClientId = "";

    public DiscordRPC() {
        super("DiscordRPC", "Показывает игровую активность в профиле Discord", Category.MISC);
        addSettings(showServer, showTimer, clientId, avatarUrl);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        startWorker();
    }

    @Override
    public void onDisable() {
        stopWorker();
    }

    private synchronized void startWorker() {
        if (running && workerThread != null && workerThread.isAlive()) {
            return;
        }
        running = true;
        workerThread = new Thread(this::runPresenceLoop, "Egg-Discord-RPC");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private synchronized void stopWorker() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        try {
            ipc.clearActivity();
        } catch (Throwable ignored) {}
        ipc.close();
    }

    private void runPresenceLoop() {
        String lastSignature = null;
        while (running && isEnabled()) {
            try {
                String currentClientId = clientId.get();
                if (currentClientId == null || currentClientId.isBlank()) {
                    currentClientId = DEFAULT_CLIENT_ID;
                }

                if (!currentClientId.equals(lastClientId) || !ipc.isConnected()) {
                    if (!ipc.connect(currentClientId)) {
                        sleepSafe(4000L);
                        continue;
                    }
                    lastClientId = currentClientId;
                    lastSignature = null;
                }

                JsonObject activity = buildActivityJson();
                String signature = activity.toString();
                if (!signature.equals(lastSignature)) {
                    ipc.setActivity(activity);
                    lastSignature = signature;
                }

                sleepSafe(2500L);
            } catch (Exception e) {
                ipc.close();
                lastSignature = null;
                sleepSafe(5000L);
            }
        }
        try {
            ipc.clearActivity();
        } catch (Throwable ignored) {}
        ipc.close();
    }

    private JsonObject buildActivityJson() {
        JsonObject activity = new JsonObject();

        activity.addProperty("details", "Version: " + CLIENT_VERSION);

        String stateText = getServerState();
        if (stateText != null && !stateText.isBlank()) {
            activity.addProperty("state", stateText);
        }

        if (showTimer.get()) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", sessionStartTime);
            activity.add("timestamps", timestamps);
        }

        JsonObject assets = new JsonObject();
        String img = avatarUrl.get();
        if (img == null || img.isBlank()) {
            img = DEFAULT_AVATAR_URL;
        }
        assets.addProperty("large_image", img);
        assets.addProperty("large_text", "Egg Visuals");
        activity.add("assets", assets);

        JsonArray buttons = new JsonArray();
        JsonObject tgButton = new JsonObject();
        tgButton.addProperty("label", "Telegram");
        tgButton.addProperty("url", TELEGRAM_URL);
        buttons.add(tgButton);
        activity.add("buttons", buttons);

        return activity;
    }

    private String getServerState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return "В главном меню";
        }
        if (client.isInSingleplayer()) {
            return "В одиночной игре";
        }

        ServerInfo serverEntry = client.getCurrentServerEntry();
        if (!showServer.get()) {
            return "В сетевой игре";
        }

        boolean streamerProtected = false;
        try {
            StreamerMode sm = ModuleManager.getInstance().getModule(StreamerMode.class);
            if (sm != null && sm.isEnabled()) {
                streamerProtected = true;
            }
        } catch (Throwable ignored) {}

        if (streamerProtected) {
            return "Сервер: Protected";
        }

        if (serverEntry != null && serverEntry.address != null && !serverEntry.address.isBlank()) {
            return "Сервер: " + serverEntry.address;
        }

        return "В сетевой игре";
    }

    private void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
