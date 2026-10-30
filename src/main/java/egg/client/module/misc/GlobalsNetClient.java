package egg.client.module.misc;

import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalsNetClient implements Runnable {

    private static final String BROKER_HOST = "broker.hivemq.com";
    private static final int BROKER_PORT = 1883;
    private static final String TOPIC_PREFIX = "egg/globals/v1/";
    private static final int UDP_PORT = 38244;

    private final Globals module;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private DatagramSocket udpSocket;

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

        // Start UDP listener for LAN auto-discovery with SO_REUSEADDR
        try {
            MulticastSocket mcast = new MulticastSocket(null);
            mcast.setReuseAddress(true);
            mcast.bind(new InetSocketAddress(UDP_PORT));
            mcast.setBroadcast(true);
            this.udpSocket = mcast;
            startUdpListener();
        } catch (Exception e1) {
            try {
                this.udpSocket = new DatagramSocket();
                this.udpSocket.setBroadcast(true);
                startUdpListener();
            } catch (Exception ignored) {
            }
        }

        this.workerThread = new Thread(this, "Egg-Globals-NetClient");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public synchronized void stop() {
        this.running.set(false);
        closeSocket();
        if (this.udpSocket != null) {
            try {
                this.udpSocket.close();
            } catch (Exception ignored) {
            }
            this.udpSocket = null;
        }
        if (this.workerThread != null) {
            this.workerThread.interrupt();
            this.workerThread = null;
        }
    }

    private void startUdpListener() {
        Thread udpThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    module.onMessageReceived("udp/lan", msg);
                } catch (Exception ignored) {
                }
            }
        }, "Egg-Globals-UDP-Listener");
        udpThread.setDaemon(true);
        udpThread.start();
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
                closeSocket();
            }

            if (!this.running.get()) break;

            try {
                Thread.sleep(3000L);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        closeSocket();
    }

    private void connectAndListen() throws Exception {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(BROKER_HOST, BROKER_PORT), 5000);
        this.socket.setSoTimeout(30000);
        this.out = this.socket.getOutputStream();
        this.in = this.socket.getInputStream();

        sendConnect();

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

        subscribeToTopics();

        this.lastHeartbeatTime = 0L;
        this.lastPingTime = System.currentTimeMillis();

        while (this.running.get() && this.socket != null && !this.socket.isClosed()) {
            long now = System.currentTimeMillis();

            if (now - this.lastHeartbeatTime > 2500L) {
                this.lastHeartbeatTime = now;
                sendHeartbeat();
            }

            if (this.topicChanged) {
                this.topicChanged = false;
                subscribeToTopics();
            }

            if (now - this.lastPingTime > 18000L) {
                this.lastPingTime = now;
                sendPing();
            }

            if (this.in.available() > 0) {
                int header = this.in.read();
                if (header == -1) break;
                int packetType = (header >> 4) & 0x0F;
                int packetRemLen = readRemainingLength(this.in);
                if (packetRemLen < 0) break;

                byte[] packetData = new byte[packetRemLen];
                readFully(this.in, packetData);

                if (packetType == 3) {
                    handlePublish(packetData);
                } else if (packetType == 13) {
                    this.lastPingTime = System.currentTimeMillis();
                }
            } else {
                Thread.sleep(80L);
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
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            String name = mc.player.getNameForScoreboard();
            String uuid = mc.player.getUuidAsString();

            String cosmeticsData = "";
            CosmeticsModule mod = CosmeticsModule.getInstance();
            if (mod != null && mod.isEnabled() && this.module.isCosmeticsEnabled()) {
                cosmeticsData = mod.getSelectedWingId() + ";" +
                        mod.getSelectedBackpackId() + ";" +
                        mod.getSelectedHatId() + ";" +
                        mod.getSelectedMaskId() + ";" +
                        mod.getSelectedPetId() + ";" +
                        mod.selectedSword();
            }

            String payload = name + ":" + uuid + ":EggVisual:" + System.currentTimeMillis() + ":" + cosmeticsData;

            sendPublish(TOPIC_PREFIX + this.currentServer, payload);
            sendPublish(TOPIC_PREFIX + "all", payload);

            // Also broadcast over UDP LAN (both subnet and local loopback)
            sendUdpBroadcast(payload);
        } catch (Exception ignored) {
        }
    }

    private void sendUdpBroadcast(String payload) {
        try {
            if (this.udpSocket != null && !this.udpSocket.isClosed()) {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                try {
                    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                    this.udpSocket.send(packet);
                } catch (Exception ignored) {
                }
                try {
                    DatagramPacket localPacket = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), UDP_PORT);
                    this.udpSocket.send(localPacket);
                } catch (Exception ignored) {
                }
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

            int payloadOffset = 2 + topicLen;
            int payloadLen = data.length - payloadOffset;
            if (payloadLen <= 0) return;

            String payload = new String(data, payloadOffset, payloadLen, StandardCharsets.UTF_8);
            this.module.onMessageReceived(topic, payload);
        } catch (Exception ignored) {
        }
    }

    private void sendConnect() throws Exception {
        ByteArrayOutputStream variableHeaderAndPayload = new ByteArrayOutputStream();

        // Protocol Name
        variableHeaderAndPayload.write(0x00);
        variableHeaderAndPayload.write(0x04);
        variableHeaderAndPayload.write("MQTT".getBytes(StandardCharsets.UTF_8));

        // Protocol Level (4 = 3.1.1)
        variableHeaderAndPayload.write(0x04);

        // Connect Flags (Clean Session = 0x02)
        variableHeaderAndPayload.write(0x02);

        // Keep Alive (30 seconds)
        variableHeaderAndPayload.write(0x00);
        variableHeaderAndPayload.write(0x1E);

        // Payload: Client ID
        String clientId = "EggVisual_" + UUID.randomUUID().toString().substring(0, 12);
        byte[] clientBytes = clientId.getBytes(StandardCharsets.UTF_8);
        variableHeaderAndPayload.write((clientBytes.length >> 8) & 0xFF);
        variableHeaderAndPayload.write(clientBytes.length & 0xFF);
        variableHeaderAndPayload.write(clientBytes);

        byte[] varBytes = variableHeaderAndPayload.toByteArray();

        this.out.write(0x10); // CONNECT packet type
        writeRemainingLength(this.out, varBytes.length);
        this.out.write(varBytes);
        this.out.flush();
    }

    private void sendSubscribe(String topic) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Packet ID (1)
        buf.write(0x00);
        buf.write(0x01);

        // Topic string
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        buf.write((topicBytes.length >> 8) & 0xFF);
        buf.write(topicBytes.length & 0xFF);
        buf.write(topicBytes);

        // QoS (0)
        buf.write(0x00);

        byte[] body = buf.toByteArray();
        this.out.write(0x82); // SUBSCRIBE with QoS 1 header
        writeRemainingLength(this.out, body.length);
        this.out.write(body);
        this.out.flush();
    }

    private void sendPublish(String topic, String payload) throws Exception {
        if (this.out == null) return;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        buf.write((topicBytes.length >> 8) & 0xFF);
        buf.write(topicBytes.length & 0xFF);
        buf.write(topicBytes);

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        buf.write(payloadBytes);

        byte[] body = buf.toByteArray();
        this.out.write(0x30); // PUBLISH (QoS 0)
        writeRemainingLength(this.out, body.length);
        this.out.write(body);
        this.out.flush();
    }

    private void sendPing() throws Exception {
        if (this.out == null) return;
        this.out.write(0xC0); // PINGREQ
        this.out.write(0x00);
        this.out.flush();
    }

    private void closeSocket() {
        try {
            if (this.socket != null) {
                this.socket.close();
            }
        } catch (Exception ignored) {
        }
        this.socket = null;
        this.out = null;
        this.in = null;
    }

    private static void writeRemainingLength(OutputStream out, int length) throws Exception {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.write(digit);
        } while (length > 0);
    }

    private static int readRemainingLength(InputStream in) throws Exception {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.read();
            if (digit == -1) return -1;
            value += (digit & 0x7F) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new Exception("Malformed remaining length in MQTT packet");
            }
        } while ((digit & 0x80) != 0);
        return value;
    }

    private static void readFully(InputStream in, byte[] b) throws Exception {
        int offset = 0;
        while (offset < b.length) {
            int count = in.read(b, offset, b.length - offset);
            if (count < 0) throw new Exception("Unexpected EOF while reading MQTT packet");
            offset += count;
        }
    }
}
