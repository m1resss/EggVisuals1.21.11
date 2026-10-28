package egg.client.liteapi;

import com.google.gson.Gson;
import egg.client.EggClient;
import egg.client.liteapi.packets.CheckFeaturesRequest;
import egg.client.liteapi.packets.CheckFeaturesResponse;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.ui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LiteApiFeatureControl {
    private static final LiteApiFeatureControl INSTANCE = new LiteApiFeatureControl();
    public static final String CLIENT_ID = "egg";

    private final Gson gson = new Gson();
    private final Set<String> activeBlocklist = ConcurrentHashMap.newKeySet();
    private long lastRequestTime = 0L;
    private static final long RATE_LIMIT_MS = 10_000L;

    private LiteApiFeatureControl() {}

    public static LiteApiFeatureControl getInstance() {
        return INSTANCE;
    }

    public void init() {
        try {

            PayloadTypeRegistry.playS2C().register(LiteApiPayload.ID, LiteApiPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(LiteApiPayload.ID, LiteApiPayload.CODEC);

            ClientPlayNetworking.registerGlobalReceiver(LiteApiPayload.ID, (payload, context) -> {
                handleServerResponse(payload.data(), context.client());
            });

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                activeBlocklist.clear();
                lastRequestTime = 0L;
                EggClient.LOGGER.info("[LiteAPI] Connected to server, scheduling checkFeatures");
                new Timer("LiteAPI-Join-Timer", true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        client.execute(() -> sendCheckFeatures(client));
                    }
                }, 400L);
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                lastRequestTime = 0L;
                client.execute(this::unblockAll);
            });

            EggClient.LOGGER.info("[LiteAPI] Feature Control initialized (client id: " + CLIENT_ID + ")");
        } catch (Throwable t) {
            EggClient.LOGGER.error("[LiteAPI] Failed to initialize Feature Control", t);
        }
    }

    public boolean sendCheckFeatures(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < RATE_LIMIT_MS) {
            return false;
        }
        lastRequestTime = now;

        try {
            List<String> features = collectFeatureList();
            CheckFeaturesRequest request = new CheckFeaturesRequest(CLIENT_ID, features);
            String json = gson.toJson(request);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            ClientPlayNetworking.send(new LiteApiPayload(bytes));
            EggClient.LOGGER.info("[LiteAPI] Sent checkFeatures request with " + features.size() + " features (id: " + request.getId() + ")");
            return true;
        } catch (Throwable t) {
            EggClient.LOGGER.error("[LiteAPI] Error sending checkFeatures request", t);
            return false;
        }
    }

    private void handleServerResponse(byte[] data, MinecraftClient client) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            EggClient.LOGGER.info("[LiteAPI] Server response: " + json);
            CheckFeaturesResponse response = gson.fromJson(json, CheckFeaturesResponse.class);
            if (response == null) return;

            client.execute(() -> {
                if (response.isOk()) {
                    List<String> blocklist = response.getBlocklist();
                    activeBlocklist.clear();
                    if (blocklist != null) {
                        for (String f : blocklist) {
                            if (f != null && !f.trim().isEmpty()) {
                                activeBlocklist.add(f.toLowerCase(Locale.ROOT).trim());
                            }
                        }
                    }
                    applyBlocklist();
                    EggClient.LOGGER.info("[LiteAPI] Server blocklist updated: " + activeBlocklist);
                } else {
                    EggClient.LOGGER.warn("[LiteAPI] Server responded with error: " + response.getError() + (response.getMessage() != null ? " - " + response.getMessage() : ""));
                }
            });
        } catch (Throwable t) {
            EggClient.LOGGER.error("[LiteAPI] Failed to parse LiteAPI response", t);
        }
    }

    private void applyBlocklist() {
        for (Module m : ModuleManager.getInstance().getModules()) {
            boolean shouldBlock = false;
            for (String alias : getModuleAliases(m)) {
                if (activeBlocklist.contains(alias.toLowerCase(Locale.ROOT).trim())) {
                    shouldBlock = true;
                    break;
                }
            }
            m.setBlocked(shouldBlock);
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof ClickGuiScreen) {
            ClickGuiScreen.getInstance().markModulesDirty();
        }
    }

    public void unblockAll() {
        activeBlocklist.clear();
        for (Module m : ModuleManager.getInstance().getModules()) {
            m.setBlocked(false);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof ClickGuiScreen) {
            ClickGuiScreen.getInstance().markModulesDirty();
        }
        EggClient.LOGGER.info("[LiteAPI] Disconnected: all features unblocked");
    }

    public Set<String> getActiveBlocklist() {
        return Collections.unmodifiableSet(activeBlocklist);
    }

    private List<String> collectFeatureList() {
        Set<String> allFeatures = new LinkedHashSet<>();


        for (Module m : ModuleManager.getInstance().getModules()) {
            allFeatures.addAll(getModuleAliases(m));
        }
        return new ArrayList<>(allFeatures);
    }

    private List<String> getModuleAliases(Module m) {
        List<String> list = new ArrayList<>();
        String raw = m.getName().toLowerCase(Locale.ROOT).trim();
        String compact = raw.replaceAll("[^a-z0-9]", "");
        list.add(compact);
        list.add(raw.replace(" ", "_"));
        list.add(raw.replace(" ", "-"));

        switch (compact) {
            case "fullbright" -> { list.add("gamma"); list.add("brightness"); }
            case "targetesp" -> { list.add("esp"); }
            case "inventorysorter" -> { list.add("invsorter"); }
            case "thirdperson" -> { list.add("freelook"); }
        }
        return list;
    }
}
