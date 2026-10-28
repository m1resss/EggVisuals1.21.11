package delta.module.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import delta.core.Category;
import delta.core.Delta;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.discord.DiscordProfileHelper;
import delta.setting.BooleanSetting;
import net.minecraft.client.network.ServerInfo;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@ModuleRegister(a = "Discord RPC", b = "Отображает статус игры в профиле Discord", c = Category.Misc)
public class DiscordRPC extends Module {

    private static final String DEFAULT_CLIENT_ID = "1559901833848299602";
    private static final String DEFAULT_LOGO_ASSET = "egg_logo";

    private final BooleanSetting showServer = new BooleanSetting("Сервер", true);
    private final BooleanSetting showTimer = new BooleanSetting("Время", true);

    private volatile boolean running = false;
    private Thread workerThread = null;
    private final long sessionStartTime = System.currentTimeMillis() / 1000L;

    public DiscordRPC() {
        a(this.showServer, this.showTimer);
        a(true); // Enabled by default
    }

    @Override
    public void b() {
        super.b();
        startWorker();
    }

    @Override
    public void c() {
        super.c();
        stopWorker();
    }

    private synchronized void startWorker() {
        if (running && workerThread != null && workerThread.isAlive()) {
            return;
        }
        running = true;
        workerThread = new Thread(this::runRpcLoop, "Egg-DiscordRPC");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private synchronized void stopWorker() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private void runRpcLoop() {
        while (running && isEnabled()) {
            RandomAccessFile pipe = null;
            try {
                pipe = findAndOpenPipe();
                if (pipe == null) {
                    Thread.sleep(5000);
                    continue;
                }

                // 1. Handshake (Opcode 0)
                String handshakeJson = "{\"v\":1,\"client_id\":\"" + DEFAULT_CLIENT_ID + "\"}";
                byte[] hsBytes = handshakeJson.getBytes(StandardCharsets.UTF_8);

                ByteBuffer hsBuf = ByteBuffer.allocate(8 + hsBytes.length).order(ByteOrder.LITTLE_ENDIAN);
                hsBuf.putInt(0); // Opcode 0 = Handshake
                hsBuf.putInt(hsBytes.length);
                hsBuf.put(hsBytes);
                pipe.write(hsBuf.array());

                // Read Handshake response (Opcode 1 = READY or Opcode 2 = CLOSE)
                byte[] header = new byte[8];
                pipe.readFully(header);
                ByteBuffer resBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int opcode = resBuf.getInt();
                int length = resBuf.getInt();

                if (opcode != 1 || length <= 0) {
                    pipe.close();
                    Thread.sleep(10000);
                    continue;
                }

                byte[] readyPayload = new byte[length];
                pipe.readFully(readyPayload);
                try {
                    String readyStr = new String(readyPayload, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(readyStr).getAsJsonObject();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject resData = root.getAsJsonObject("data");
                        if (resData.has("user") && resData.get("user").isJsonObject()) {
                            DiscordProfileHelper.getInstance().updateFromUserData(resData.getAsJsonObject("user"));
                        }
                    }
                } catch (Exception ignored) {}

                // 2. Activity Loop
                int nonce = 0;
                while (running && isEnabled()) {
                    JsonObject activity = buildActivityJson();

                    JsonObject command = new JsonObject();
                    command.addProperty("cmd", "SET_ACTIVITY");
                    JsonObject args = new JsonObject();
                    args.addProperty("pid", ProcessHandle.current().pid());
                    args.add("activity", activity);
                    command.add("args", args);
                    command.addProperty("nonce", String.valueOf(nonce++));

                    byte[] actBytes = command.toString().getBytes(StandardCharsets.UTF_8);
                    ByteBuffer actBuf = ByteBuffer.allocate(8 + actBytes.length).order(ByteOrder.LITTLE_ENDIAN);
                    actBuf.putInt(1); // Opcode 1 = FRAME
                    actBuf.putInt(actBytes.length);
                    actBuf.put(actBytes);
                    pipe.write(actBuf.array());

                    // Read immediate response from Discord
                    pipe.readFully(header);
                    resBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                    int resOp = resBuf.getInt();
                    int resLen = resBuf.getInt();
                    if (resLen > 0 && resLen < 1024 * 1024) {
                        byte[] resData = new byte[resLen];
                        pipe.readFully(resData);
                    }

                    if (resOp == 2) {
                        break;
                    }

                    Thread.sleep(3500);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception ignored) {
                // Connection lost or Discord not running
            } finally {
                if (pipe != null) {
                    try {
                        pipe.close();
                    } catch (Exception ignored) {}
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private JsonObject buildActivityJson() {
        JsonObject activity = new JsonObject();

        // State (Singleplayer / Server address / Main Menu)
        String state;
        if (aM_.world == null) {
            state = "Main Menu";
        } else if (aM_.isInSingleplayer()) {
            state = "Singleplayer";
        } else {
            ServerInfo serverEntry = aM_.getCurrentServerEntry();
            boolean isStreamer = false;
            try {
                StreamerMode streamerMode = Delta.h().d().t().aE();
                if (streamerMode != null && streamerMode.isEnabled()) {
                    isStreamer = true;
                }
            } catch (Exception ignored) {}

            if (Boolean.TRUE.equals(this.showServer.h()) && !isStreamer && serverEntry != null && serverEntry.address != null && !serverEntry.address.isBlank()) {
                state = serverEntry.address;
            } else {
                state = "Multiplayer";
            }
        }
        activity.addProperty("state", state);

        // Timestamps (Start elapsed timer)
        if (Boolean.TRUE.equals(this.showTimer.h())) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", this.sessionStartTime);
            activity.add("timestamps", timestamps);
        }

        // Assets (Large Egg Logo)
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", DEFAULT_LOGO_ASSET);
        assets.addProperty("large_text", "Egg 1.21.4");
        activity.add("assets", assets);

        return activity;
    }

    private RandomAccessFile findAndOpenPipe() {
        for (int i = 0; i < 10; i++) {
            File pipeFile = new File("\\\\.\\pipe\\discord-ipc-" + i);
            if (!pipeFile.exists()) {
                continue;
            }
            try {
                return new RandomAccessFile(pipeFile, "rw");
            } catch (Exception ignored) {}
        }
        return null;
    }
}
