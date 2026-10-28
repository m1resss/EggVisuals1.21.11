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
    private static final long RATE_LIMIT_MS = 10_000L; // 1 request per 10 seconds per HolyWorld spec

    private LiteApiFeatureControl() {}

    public static LiteApiFeatureControl getInstance() {
        return INSTANCE;
    }

    public void init() {
        try {
            // Register custom payloads
            PayloadTypeRegistry.playS2C().register(LiteApiPayload.ID, LiteApiPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(LiteApiPayload.ID, LiteApiPayload.CODEC);

            // Register packet receiver
            ClientPlayNetworking.registerGlobalReceiver(LiteApiPayload.ID, (payload, context) -> {
                handleServerResponse(payload.data(), context.client());
            });

            // On Server Join
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                activeBlocklist.clear();
                // Send checkFeatures after a brief 500ms delay to let the connection stabilize
                new Timer("LiteAPI-Join-Timer", true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        client.execute(() -> sendCheckFeatures(client));
                    }
                }, 500L);
            });

            // On Server Disconnect
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                client.execute(this::unblockAll);
            });

            EggClient.LOGGER.info("[LiteAPI] Feature Control initialized (client id: " + CLIENT_ID + ")");
        } catch (Throwable t) {
            EggClient.LOGGER.error("[LiteAPI] Failed to initialize Feature Control", t);
        }
    }

    public boolean sendCheckFeatures(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - lastRequestTime < RATE_LIMIT_MS) {
            return false;
        }
        lastRequestTime = now;

        try {
            if (!ClientPlayNetworking.canSend(LiteApiPayload.ID)) {
                // Server doesn't support LiteAPI channel, don't block anything
                return false;
            }

            List<String> features = collectFeatureList();
            CheckFeaturesRequest request = new CheckFeaturesRequest(CLIENT_ID, features);
            String json = gson.toJson(request);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            ClientPlayNetworking.send(new LiteApiPayload(bytes));
            EggClient.LOGGER.info("[LiteAPI] Sent checkFeatures request with " + features.size() + " features");
            return true;
        } catch (Throwable t) {
            EggClient.LOGGER.error("[LiteAPI] Error sending checkFeatures request", t);
            return false;
        }
    }

    private void handleServerResponse(byte[] data, MinecraftClient client) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
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
        // Add common standard Minecraft feature names checked by HolyWorld
        allFeatures.addAll(List.of("xray", "fly", "speed", "freecam", "killaura", "fullbright", "gamma"));

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

        // Specific semantic aliases
        switch (compact) {
            case "fullbright" -> { list.add("gamma"); list.add("brightness"); }
            case "breakblock" -> { list.add("fastbreak"); list.add("fast_break"); }
            case "targetesp" -> { list.add("esp"); list.add("box_esp"); list.add("glow_esp"); }
            case "inventorysorter" -> { list.add("sorter"); list.add("invsorter"); list.add("cheststealer"); }
            case "tapemouse" -> { list.add("autoclicker"); list.add("clicker"); }
            case "shifttap" -> { list.add("wtap"); list.add("w_tap"); }
            case "worldtweaks" -> { list.add("timechanger"); list.add("weather"); }
            case "itemscroller" -> { list.add("quickcraft"); }
            case "thirdperson" -> { list.add("freelook"); }
        }
        return list;
    }
}
