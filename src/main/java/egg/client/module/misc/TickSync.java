package egg.client.module.misc;

import egg.client.module.Category;
import egg.client.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TickSync extends Module {
    private boolean isTickRateChangedLastTick;
    private boolean isPacketReceivedThisTick;
    private boolean isPacketRangeUpdatedThisTick;
    private int afterLazyPacketCooldown;
    private int packetRange;
    private int instantPacketRange;
    private int avgPacketDelay = 12;
    private long lastSyncTime = System.currentTimeMillis();
    private long lastServerPacketTime = System.currentTimeMillis();
    private long lastPacketRangeUpdatedTime = System.currentTimeMillis();
    private float clientTPS = 20.0f;
    private float serverTPS = 20.0f;

    private final List<Integer> packetDelayBuffer = new ArrayList<>(Collections.nCopies(1, 12));
    private final List<Integer> packetRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));
    private final List<Integer> fastPacketRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));

    public TickSync() {
        super("TickSync", "Синхронизация клиентских тиков с тиками сервера для плавной интерполяции и стабильного хитбокса", Category.MISC);

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (this.isEnabled()) {
                this.onClientTickStart();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetTickRate());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetTickRate());
    }

    @Override
    public void onEnable() {
        resetTickRate();
    }

    @Override
    public void onDisable() {
        resetTickRate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!isEnabled()) return;
        this.onClientTickEnd();
    }

    public void onEntityPacket() {
        long now = System.currentTimeMillis();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            this.lastServerPacketTime = now;
            this.isPacketReceivedThisTick = true;
        }
        if (!this.isPacketRangeUpdatedThisTick) {
            int delta = (int) (now - this.lastPacketRangeUpdatedTime);
            if (delta >= 0) {
                int margin = (int) applyRatio(25.0f);
                int duration = getTickDuration();
                int range = ((delta % duration + margin) % duration) - margin;
                addToPacketRangeBuffer(range);
            }
            if (delta >= getTickDuration() * 2) {
                this.afterLazyPacketCooldown = 20;
            }
            this.lastPacketRangeUpdatedTime = now;
            this.isPacketRangeUpdatedThisTick = true;
        }
    }

    public void onUpdateTickRate(float tickRate) {
        this.serverTPS = tickRate;
        this.clientTPS = Math.min(20.0f, tickRate);
    }

    public void onClientTickStart() {
        if (!isPlayingInGame()) return;
        long now = System.currentTimeMillis();
        int delay = (int) (now - this.lastServerPacketTime);
        if (delay >= 0 && (float) delay < applyRatio(55.0f)) {
            addToTickDeltaBuffer(delay);
        } else {
            addToTickDeltaBuffer(this.avgPacketDelay);
        }
        if (this.afterLazyPacketCooldown > 0) {
            this.afterLazyPacketCooldown--;
        }
        this.avgPacketDelay = calculateMean(this.packetDelayBuffer);
        this.packetRange = calculateRange(this.packetRangeBuffer, 40);
        this.instantPacketRange = calculateRange(this.fastPacketRangeBuffer, 10);
        this.isPacketRangeUpdatedThisTick = false;
        this.isPacketReceivedThisTick = false;
    }

    public void onClientTickEnd() {
        if (this.isTickRateChangedLastTick) {
            this.isTickRateChangedLastTick = false;
            setTickRate(Math.min(20.0f, this.serverTPS));
        }
        long now = System.currentTimeMillis();
        if (isPlayingInGame() && canSync() && now - this.lastSyncTime > 1000L) {
            if (isWeirdSyncOccurred()) {
                fixWeirdSync();
            }
            if (isTickSyncRequired()) {
                matchTickSync();
            }
        }
    }

    public boolean canSync() {
        return this.serverTPS <= 20.0f && getCurrentFPS() > 40 && (float) this.instantPacketRange < applyRatio(25.0f);
    }

    private boolean isTickSyncRequired() {
        return this.avgPacketDelay > getThreshold();
    }

    private int getThreshold() {
        int threshold = (int) applyRatio(getPacketMargin()) + 4;
        if ((float) threshold < getFrameDuration()) {
            return (int) (getFrameDuration() * 1.5f);
        }
        return threshold;
    }

    private int getPacketMargin() {
        int minMargin = (this.afterLazyPacketCooldown > 0) ? 8 : 6;
        return Math.clamp(this.packetRange, minMargin, 16);
    }

    private void matchTickSync() {
        this.lastSyncTime = System.currentTimeMillis();
        int shift = (getTickDuration() - this.avgPacketDelay) + quantizeToFrame(applyRatio((float) (getPacketMargin() + getMatchSyncOffset())));
        shiftNextTickDuration(shift);
    }

    private int getMatchSyncOffset() {
        int fps = getCurrentFPS();
        if (fps > 240) return 0;
        if (fps > 120) return 1;
        return 2;
    }

    private int quantizeToFrame(float val) {
        float frameDur = getFrameDuration();
        if (val < frameDur) return (int) frameDur;
        return (int) (Math.floor(val / frameDur) * frameDur);
    }

    private void shiftNextTickDuration(int shift) {
        int nextDuration = Math.max(5, shift + getTickDuration());
        float newRate = 1000.0f / nextDuration;
        setTickRate(newRate);
        this.packetDelayBuffer.clear();
        addToTickDeltaBuffer(getPacketMargin());
        this.avgPacketDelay = getPacketMargin();
        this.isTickRateChangedLastTick = true;
    }

    private boolean isWeirdSyncOccurred() {
        if (this.packetDelayBuffer.isEmpty()) return false;
        int range = calculateRange(this.packetDelayBuffer, 10);
        return this.packetDelayBuffer.size() >= 10 && (float) range > applyRatio(35.0f);
    }

    private void fixWeirdSync() {
        if (this.packetDelayBuffer.isEmpty()) return;
        this.lastSyncTime = System.currentTimeMillis();
        float minDelay = Math.min(getPacketMargin() / 2, Collections.min(this.packetDelayBuffer));
        int shift = quantizeToFrame(applyRatio((float) getPacketMargin() - minDelay));
        shiftNextTickDuration(shift);
    }

    private void setTickRate(float rate) {
        this.clientTPS = rate;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            client.world.getTickManager().setTickRate(rate);
        }
    }

    public void resetTickRate() {
        this.clientTPS = 20.0f;
        this.serverTPS = 20.0f;
        this.isTickRateChangedLastTick = false;
        this.afterLazyPacketCooldown = 0;
        this.lastServerPacketTime = System.currentTimeMillis();
        this.lastSyncTime = System.currentTimeMillis();
        this.lastPacketRangeUpdatedTime = System.currentTimeMillis();
        this.packetDelayBuffer.clear();
        this.packetRangeBuffer.clear();
        this.fastPacketRangeBuffer.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            client.world.getTickManager().setTickRate(20.0f);
        }
    }

    private int getTickDuration() {
        return (int) (1000.0f / Math.max(1.0f, this.serverTPS));
    }

    private float getFrameDuration() {
        return 1000.0f / (float) Math.max(1, getCurrentFPS());
    }

    private float applyRatio(float val) {
        return val * (20.0f / Math.max(1.0f, this.serverTPS));
    }

    private int getCurrentFPS() {
        return MinecraftClient.getInstance().getCurrentFps();
    }

    private boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused();
    }

    public float getClientTPS() {
        return this.clientTPS;
    }

    public float getServerTPS() {
        return this.serverTPS;
    }

    private void addToTickDeltaBuffer(int val) {
        this.packetDelayBuffer.add(val);
        if (this.packetDelayBuffer.size() > 10) {
            this.packetDelayBuffer.remove(0);
        }
    }

    private void addToPacketRangeBuffer(int val) {
        if (Math.abs(val) <= 8) {
            this.packetRangeBuffer.add(val);
        }
        if (this.packetRangeBuffer.size() > 40) {
            this.packetRangeBuffer.remove(0);
        }
        this.fastPacketRangeBuffer.add(val);
        if (this.fastPacketRangeBuffer.size() > 10) {
            this.fastPacketRangeBuffer.remove(0);
        }
    }

    private int calculateMean(List<Integer> list) {
        int size = list.size();
        if (size == 0) return 10;
        int sum = 0;
        for (int v : list) {
            sum += v;
        }
        return sum / size;
    }

    private int calculateRange(List<Integer> list, int minCount) {
        if (list == null || list.isEmpty() || list.size() < minCount) return 12;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Integer v : new ArrayList<>(list)) {
            if (v == null) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return max - min;
    }
}
