package egg.client.discord;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DiscordIPC implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Egg/DiscordIPC");

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final int MAX_PIPES = 10;
    private static final int MAX_FRAME_LENGTH = 65536;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private volatile Transport transport;
    private volatile boolean connected;

    public boolean isConnected() {
        return connected && transport != null;
    }

    public synchronized boolean connect(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        close();

        for (int i = 0; i < MAX_PIPES; i++) {
            Transport t = null;
            try {
                if (IS_WINDOWS) {
                    File pipeFile = new File("\\\\.\\pipe\\discord-ipc-" + i);
                    if (pipeFile.exists()) {
                        t = new WindowsPipeTransport(pipeFile);
                    }
                } else {
                    Path path = getUnixTempDir().resolve("discord-ipc-" + i);
                    if (Files.exists(path)) {
                        t = new UnixSocketTransport(path);
                    }
                }
            } catch (Throwable ignored) {
                t = null;
            }

            if (t != null) {
                this.transport = t;
                try {
                    JsonObject hs = new JsonObject();
                    hs.addProperty("v", 1);
                    hs.addProperty("client_id", clientId);
                    writeFrame(OP_HANDSHAKE, hs.toString());

                    Frame res = readFrame();
                    if (res != null && res.opcode() == OP_FRAME) {
                        this.connected = true;
                        LOGGER.info("Connected to Discord IPC on index {}", i);
                        return true;
                    } else {
                        close();
                    }
                } catch (IOException e) {
                    close();
                }
            }
        }
        return false;
    }

    public synchronized void setActivity(JsonObject activity) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to Discord IPC");
        }
        JsonObject args = new JsonObject();
        long pid = 1L;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Throwable ignored) {}
        args.addProperty("pid", pid);
        if (activity != null) {
            args.add("activity", activity);
        } else {
            args.add("activity", null);
        }

        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", UUID.randomUUID().toString());

        writeFrame(OP_FRAME, cmd.toString());
        Frame response = readFrame();
        if (response != null && response.opcode() == OP_CLOSE) {
            close();
            throw new IOException("Discord closed IPC connection");
        }
    }

    public synchronized void clearActivity() {
        try {
            if (isConnected()) {
                setActivity(null);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public synchronized void close() {
        connected = false;
        Transport t = this.transport;
        this.transport = null;
        if (t != null) {
            try {
                t.close();
            } catch (IOException ignored) {}
        }
    }

    private void writeFrame(int opcode, String payload) throws IOException {
        Transport t = this.transport;
        if (t == null) {
            throw new IOException("Transport is closed");
        }

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(bytes.length);
        buf.put(bytes);
        t.write(buf.array());
    }

    private Frame readFrame() throws IOException {
        Transport t = this.transport;
        if (t == null) {
            throw new IOException("Transport is closed");
        }

        byte[] header = new byte[8];
        t.readFully(header);
        ByteBuffer hBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = hBuf.getInt();
        int length = hBuf.getInt();

        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new IOException("Invalid frame length: " + length);
        }

        byte[] payload = new byte[length];
        if (length > 0) {
            t.readFully(payload);
        }
        return new Frame(opcode, new String(payload, StandardCharsets.UTF_8));
    }

    private static Path getUnixTempDir() {
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String val = System.getenv(env);
            if (val != null && !val.isEmpty()) {
                return Path.of(val);
            }
        }
        return Path.of("/tmp");
    }

    private record Frame(int opcode, String payload) {}

    interface Transport extends Closeable {
        void write(byte[] bytes) throws IOException;
        void readFully(byte[] bytes) throws IOException;
    }

    static final class WindowsPipeTransport implements Transport {
        private final RandomAccessFile pipe;

        WindowsPipeTransport(File file) throws IOException {
            this.pipe = new RandomAccessFile(file, "rw");
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            this.pipe.write(bytes);
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            this.pipe.readFully(bytes);
        }

        @Override
        public void close() throws IOException {
            this.pipe.close();
        }
    }

    static final class UnixSocketTransport implements Transport {
        private final SocketChannel channel;

        UnixSocketTransport(Path path) throws IOException {
            this.channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            this.channel.connect(UnixDomainSocketAddress.of(path));
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) {
                this.channel.write(buf);
            }
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) {
                if (this.channel.read(buf) < 0) {
                    throw new IOException("Unix socket closed");
                }
            }
        }

        @Override
        public void close() throws IOException {
            this.channel.close();
        }
    }
}
