package delta.module.render;

import static delta.core.Interface.aM_;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalsNetClient implements Runnable {

    private static final String BROKER_HOST = "broker.hivemq.com";
    private static final int BROKER_PORT = 1883;
    private static final String TOPIC_PREFIX = "egg/globals/v1/";

    private final Globals module;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private Socket socket;
    private OutputStream out;
    private InputStream in;

    private volatile String currentServer = "singleplayer";
    private volatile boolean topicChanged = false;
    private long lastHeartbeatTime = 0L;
    private long lastPingTime = 0L;

    public GlobalsNetClient(Globals module) {
        this.module = module;
    }

    public synchronized void start() {
        if (this.running.get()) return;
        this.running.set(true);
        this.workerThread = new Thread(this, "Egg-Globals-NetClient");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public synchronized void stop() {
        this.running.set(false);
        closeSocket();
        if (this.workerThread != null) {
            this.workerThread.interrupt();
            this.workerThread = null;
        }
    }

    public void updateServer(String serverAddress) {
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            serverAddress = "singleplayer";
        }
        String sanitized = sanitizeTopic(serverAddress);
        if (!this.currentServer.equals(sanitized)) {
            this.currentServer = sanitized;
            this.topicChanged = true;
        }
    }

    private static String sanitizeTopic(String input) {
        return input.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
    }

    @Override
    public void run() {
        while (this.running.get()) {
            try {
                connectAndListen();
            } catch (Throwable t) {
                // Connection lost or error; close and wait before reconnecting
                closeSocket();
            }

            if (!this.running.get()) break;

            try {
                Thread.sleep(4000L);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        closeSocket();
    }

    private void connectAndListen() throws Exception {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(BROKER_HOST, BROKER_PORT), 6000);
        this.socket.setSoTimeout(30000);
        this.out = this.socket.getOutputStream();
        this.in = this.socket.getInputStream();

        sendConnect();

        // Read CONNACK
        int connackHeader = this.in.read();
        if (connackHeader != 0x20) {
            throw new Exception("Expected CONNACK (0x20), got: " + connackHeader);
        }
        int remLen = readRemainingLength(this.in);
        byte[] connackBody = new byte[remLen];
        readFully(this.in, connackBody);
        if (connackBody.length >= 2 && connackBody[1] != 0) {
            throw new Exception("Broker rejected connection, code: " + connackBody[1]);
        }

        // Subscriptions
        subscribeToTopics();

        this.lastHeartbeatTime = 0L;
        this.lastPingTime = System.currentTimeMillis();

        // Packet processing loop
        while (this.running.get() && this.socket != null && !this.socket.isClosed()) {
            long now = System.currentTimeMillis();

            // Heartbeat check
            if (now - this.lastHeartbeatTime > 4000L) {
                this.lastHeartbeatTime = now;
                sendHeartbeat();
            }

            // Topic change check
            if (this.topicChanged) {
                this.topicChanged = false;
                subscribeToTopics();
            }

            // Keepalive ping
            if (now - this.lastPingTime > 18000L) {
                this.lastPingTime = now;
                sendPing();
            }

            // Check if there is data to read without blocking indefinitely
            if (this.in.available() > 0) {
                int header = this.in.read();
                if (header == -1) break;
                int packetType = (header >> 4) & 0x0F;
                int packetRemLen = readRemainingLength(this.in);
                if (packetRemLen < 0) break;

                byte[] packetData = new byte[packetRemLen];
                readFully(this.in, packetData);

                if (packetType == 3) { // PUBLISH
                    handlePublish(packetData);
                } else if (packetType == 13) { // PINGRESP (0xD0)
                    this.lastPingTime = System.currentTimeMillis();
                }
            } else {
                Thread.sleep(100L);
            }
        }
    }

    private void subscribeToTopics() {
        try {
            sendSubscribe(TOPIC_PREFIX + this.currentServer);
            sendSubscribe(TOPIC_PREFIX + "all");
        } catch (Exception ignored) {
        }
    }

    private void sendHeartbeat() {
        try {
            if (aM_.player == null) return;
            String name = aM_.player.getName().getString();
            String uuid = aM_.player.getUuidAsString();

            String cosmeticsData = "";
            delta.module.cosmetics.CosmeticsModule mod = delta.module.cosmetics.CosmeticsModule.getInstance();
            if (mod != null && mod.isEnabled() && this.module.isSyncCosmetics()) {
                cosmeticsData = mod.getSelectedWingId() + ";" +
                                mod.getSelectedBackpackId() + ";" +
                                mod.getSelectedHatId() + ";" +
                                mod.getSelectedMaskId() + ";" +
                                mod.getSelectedPetId() + ";" +
                                mod.selectedSword();
            }

            String payload = name + ":" + uuid + ":EggVisual:" + System.currentTimeMillis() + ":" + cosmeticsData;

            sendPublish(TOPIC_PREFIX + this.currentServer, payload);

            if (this.module.isGlobalSync()) {
                sendPublish(TOPIC_PREFIX + "all", payload);
            }
        } catch (Exception ignored) {
        }
    }

    private void handlePublish(byte[] data) {
        try {
            if (data.length < 2) return;
            int topicLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            if (data.length < 2 + topicLen) return;

            String topic = new String(data, 2, topicLen, StandardCharsets.UTF_8);
            int payloadLen = data.length - 2 - topicLen;
            String payload = new String(data, 2 + topicLen, payloadLen, StandardCharsets.UTF_8);

            this.module.onMessageReceived(topic, payload);
        } catch (Exception ignored) {
        }
    }

    private void sendConnect() throws Exception {
        String clientId = "egg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        byte[] cidBytes = clientId.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((cidBytes.length >> 8) & 0xFF);
        payload.write(cidBytes.length & 0xFF);
        payload.write(cidBytes);

        byte[] varHeader = new byte[]{
                0x00, 0x04, 'M', 'Q', 'T', 'T',
                0x04, // Protocol level 4 (MQTT 3.1.1)
                0x02, // Clean session
                0x00, 0x1E // Keep alive 30s
        };

        byte[] payloadBytes = payload.toByteArray();
        int remainingLength = varHeader.length + payloadBytes.length;

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x10); // CONNECT
        writeRemainingLength(packet, remainingLength);
        packet.write(varHeader);
        packet.write(payloadBytes);

        this.out.write(packet.toByteArray());
        this.out.flush();
    }

    private void sendSubscribe(String topic) throws Exception {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0x00); // Packet ID MSB
        payload.write(0x01); // Packet ID LSB
        payload.write((topicBytes.length >> 8) & 0xFF);
        payload.write(topicBytes.length & 0xFF);
        payload.write(topicBytes);
        payload.write(0x00); // QoS 0

        byte[] body = payload.toByteArray();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x82); // SUBSCRIBE
        writeRemainingLength(packet, body.length);
        packet.write(body);

        this.out.write(packet.toByteArray());
        this.out.flush();
    }

    public synchronized void sendPublish(String topic, String message) throws Exception {
        if (this.out == null) return;
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream varHeader = new ByteArrayOutputStream();
        varHeader.write((topicBytes.length >> 8) & 0xFF);
        varHeader.write(topicBytes.length & 0xFF);
        varHeader.write(topicBytes);

        int remainingLength = varHeader.size() + msgBytes.length;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x30); // PUBLISH QoS 0
        writeRemainingLength(packet, remainingLength);
        packet.write(varHeader.toByteArray());
        packet.write(msgBytes);

        this.out.write(packet.toByteArray());
        this.out.flush();
    }

    private void sendPing() throws Exception {
        if (this.out != null) {
            this.out.write(new byte[]{(byte) 0xC0, 0x00});
            this.out.flush();
        }
    }

    private static void writeRemainingLength(OutputStream stream, int length) throws Exception {
        do {
            byte encodedByte = (byte) (length % 128);
            length = length / 128;
            if (length > 0) {
                encodedByte = (byte) (encodedByte | 0x80);
            }
            stream.write(encodedByte);
        } while (length > 0);
    }

    private static int readRemainingLength(InputStream stream) throws Exception {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = stream.read();
            if (digit == -1) return -1;
            value += (digit & 127) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new Exception("Malformed remaining length");
            }
        } while ((digit & 128) != 0);
        return value;
    }

    private static void readFully(InputStream stream, byte[] buffer) throws Exception {
        int read = 0;
        while (read < buffer.length) {
            int r = stream.read(buffer, read, buffer.length - read);
            if (r == -1) throw new Exception("Unexpected EOF while reading packet");
            read += r;
        }
    }

    private synchronized void closeSocket() {
        try {
            if (this.in != null) this.in.close();
        } catch (Exception ignored) {
        }
        try {
            if (this.out != null) this.out.close();
        } catch (Exception ignored) {
        }
        try {
            if (this.socket != null && !this.socket.isClosed()) this.socket.close();
        } catch (Exception ignored) {
        }
        this.socket = null;
        this.out = null;
        this.in = null;
    }
}
