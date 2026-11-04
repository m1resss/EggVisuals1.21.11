package egg.client.module.misc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Globals extends Module {
    private static Globals instance;

    private final BooleanSetting showSelf = new BooleanSetting("Отображать себе", true, "Отображать яйцо перед своим ником");
    private final BooleanSetting inTab = new BooleanSetting("В табе", true, "Отображать яйцо перед никами в табе");
    private final BooleanSetting inNametag = new BooleanSetting("Над головой", true, "Отображать яйцо в неймтегах над головой");
    private final ModeSetting iconPosition = new ModeSetting("Позиция яйца", "Перед ником", "Где отображать значок яйца", "Перед ником", "В начале строки");
    private final BooleanSetting cosmetics = new BooleanSetting("Косметика", true, "Отображать косметику других пользователей Egg Visuals");

    private final GlobalsNetClient netClient;

    private final Map<String, Long> peersByName = new ConcurrentHashMap<>();
    private final Map<UUID, Long> peersByUuid = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> peerCosmeticsByName = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> peerCosmeticsByUuid = new ConcurrentHashMap<>();

    private String lastKnownServer = "";
    private long lastLocalShareTime = 0L;
    private static final Gson GSON = new Gson();

    public Globals() {
        super("Globals", "Показывает значок перед никами других пользователей и их украшения", Category.MISC);
        instance = this;
        this.netClient = new GlobalsNetClient(this);
        addSettings(showSelf, inTab, inNametag, iconPosition, cosmetics);
    }

    public static Globals getInstance() {
        return instance;
    }

    public boolean isShowSelf() {
        return showSelf.get();
    }

    public boolean isInTab() {
        return inTab.get();
    }

    public boolean isInNametag() {
        return inNametag.get();
    }

    public String getIconPosition() {
        return iconPosition.get();
    }

    public boolean isCosmeticsEnabled() {
        return cosmetics.get();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.netClient.start();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.netClient.stop();
        this.peersByName.clear();
        this.peersByUuid.clear();
        this.peerCosmeticsByName.clear();
        this.peerCosmeticsByUuid.clear();
    }

    @Override
    public void onTick(MinecraftClient client) {
        onTick();
    }

    public void onTick() {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String currentServer = "singleplayer";
        if (mc.world != null && !mc.isInSingleplayer() && mc.getCurrentServerEntry() != null) {
            currentServer = mc.getCurrentServerEntry().address;
        } else if (mc.getServer() != null) {
            currentServer = "lan";
        }

        if (!currentServer.equals(this.lastKnownServer)) {
            this.lastKnownServer = currentServer;
            this.peersByName.clear();
            this.peersByUuid.clear();
            this.peerCosmeticsByName.clear();
            this.peerCosmeticsByUuid.clear();
            this.netClient.updateServer(currentServer);
        }

        long now = System.currentTimeMillis();
        if (now - this.lastLocalShareTime > 1000L) {
            this.lastLocalShareTime = now;
            syncLocalShare(mc, now);
        }

        this.peersByName.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue() > 35000L);
            if (expired) {
                this.peerCosmeticsByName.remove(entry.getKey());
            }
            return expired;
        });
        this.peersByUuid.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue() > 35000L);
            if (expired) {
                this.peerCosmeticsByUuid.remove(entry.getKey());
            }
            return expired;
        });
    }

    private void syncLocalShare(MinecraftClient mc, long now) {
        try {
            List<File> shareFiles = new ArrayList<>();
            File homeFile = new File(System.getProperty("user.home"), ".egg_globals_share.json");
            shareFiles.add(homeFile);
            if (mc.runDirectory != null) {
                shareFiles.add(new File(mc.runDirectory, "egg_globals_share.json"));
            }
            File geminiDir = new File(System.getProperty("user.home"), ".gemini/antigravity");
            if (geminiDir.exists() || geminiDir.mkdirs()) {
                shareFiles.add(new File(geminiDir, "egg_globals_share.json"));
            }

            JsonObject mergedRoot = new JsonObject();
            for (File file : shareFiles) {
                if (file.exists()) {
                    try {
                        String content = Files.readString(file.toPath());
                        var elem = JsonParser.parseString(content);
                        if (elem.isJsonObject()) {
                            JsonObject obj = elem.getAsJsonObject();
                            for (var entry : obj.entrySet()) {
                                mergedRoot.add(entry.getKey(), entry.getValue());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            String myName = mc.player.getNameForScoreboard().toLowerCase(Locale.ROOT);
            JsonObject selfObj = new JsonObject();
            selfObj.addProperty("uuid", mc.player.getUuidAsString());
            selfObj.addProperty("time", now);

            CosmeticsModule mod = CosmeticsModule.getInstance();
            if (mod != null && mod.isEnabled()) {
                JsonObject cObj = new JsonObject();
                cObj.addProperty("wings", mod.getSelectedWingId());
                cObj.addProperty("backpacks", mod.getSelectedBackpackId());
                cObj.addProperty("hats", mod.getSelectedHatId());
                cObj.addProperty("masks", mod.getSelectedMaskId());
                cObj.addProperty("pets", mod.getSelectedPetId());
                cObj.addProperty("sword", mod.selectedSword());
                selfObj.add("cosmetics", cObj);
            }
            mergedRoot.add(myName, selfObj);

            for (var entry : mergedRoot.entrySet()) {
                String peerName = entry.getKey().toLowerCase(Locale.ROOT);
                if (peerName.equals(myName)) continue;

                if (entry.getValue().isJsonObject()) {
                    JsonObject pObj = entry.getValue().getAsJsonObject();
                    long pTime = pObj.has("time") ? pObj.get("time").getAsLong() : 0L;
                    if (now - pTime < 25000L) {
                        this.peersByName.put(peerName, now);
                        UUID pUuid = null;
                        if (pObj.has("uuid")) {
                            try {
                                pUuid = UUID.fromString(pObj.get("uuid").getAsString());
                                this.peersByUuid.put(pUuid, now);
                            } catch (Exception ignored) {
                            }
                        }
                        if (pObj.has("cosmetics") && pObj.get("cosmetics").isJsonObject()) {
                            JsonObject cos = pObj.getAsJsonObject("cosmetics");
                            Map<String, String> cMap = new HashMap<>();
                            for (var cEntry : cos.entrySet()) {
                                cMap.put(cEntry.getKey(), cEntry.getValue().getAsString());
                            }
                            this.peerCosmeticsByName.put(peerName, cMap);
                            if (pUuid != null) {
                                this.peerCosmeticsByUuid.put(pUuid, cMap);
                            }
                        }
                    }
                }
            }

            String jsonStr = GSON.toJson(mergedRoot);
            for (File file : shareFiles) {
                try {
                    Files.writeString(file.toPath(), jsonStr);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void onMessageReceived(String topic, String payload) {
        if (!isEnabled() || payload == null || payload.trim().isEmpty()) return;

        try {
            String[] parts = payload.split(":", -1);
            if (parts.length >= 2) {
                String name = parts[0].trim().toLowerCase(Locale.ROOT);
                String uuidStr = parts[1].trim();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null && name.equalsIgnoreCase(mc.player.getNameForScoreboard())) {
                    return;
                }

                long now = System.currentTimeMillis();
                this.peersByName.put(name, now);

                UUID peerUuid = null;
                try {
                    peerUuid = UUID.fromString(uuidStr);
                    this.peersByUuid.put(peerUuid, now);
                } catch (Exception ignored) {
                }

                if (parts.length >= 5) {
                    String cData = parts[4].trim();
                    if (!cData.isEmpty()) {
                        String[] cParts = cData.split(";", -1);
                        Map<String, String> map = new HashMap<>();
                        if (cParts.length > 0 && !cParts[0].isEmpty()) map.put("wings", cParts[0]);
                        if (cParts.length > 1 && !cParts[1].isEmpty()) map.put("backpacks", cParts[1]);
                        if (cParts.length > 2 && !cParts[2].isEmpty()) map.put("hats", cParts[2]);
                        if (cParts.length > 3 && !cParts[3].isEmpty()) map.put("masks", cParts[3]);
                        if (cParts.length > 4 && !cParts[4].isEmpty()) map.put("pets", cParts[4]);
                        if (cParts.length > 5 && !cParts[5].isEmpty()) map.put("sword", cParts[5]);
                        this.peerCosmeticsByName.put(name, map);
                        if (peerUuid != null) {
                            this.peerCosmeticsByUuid.put(peerUuid, map);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean containsUsername(String text, String username) {
        if (text == null || username == null || username.isEmpty()) return false;
        int idx = text.indexOf(username);
        while (idx >= 0) {
            boolean startOk = (idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1)));
            int endIdx = idx + username.length();
            boolean endOk = (endIdx == text.length() || !Character.isLetterOrDigit(text.charAt(endIdx)));
            if (startOk && endOk) {
                return true;
            }
            idx = text.indexOf(username, idx + 1);
        }
        return false;
    }

    public static boolean isEggUser(PlayerListEntry entry) {
        Globals g = getInstance();
        if (g == null || !g.isEnabled() || entry == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && entry.getProfile() != null) {
            if (mc.player.getUuid().equals(entry.getProfile().id()) ||
                    mc.player.getNameForScoreboard().equalsIgnoreCase(entry.getProfile().name())) {
                return g.showSelf.get();
            }
        }

        if (entry.getProfile() != null) {
            String name = entry.getProfile().name();
            if (name != null && g.peersByName.containsKey(name.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
            UUID uuid = entry.getProfile().id();
            if (uuid != null && g.peersByUuid.containsKey(uuid)) {
                return true;
            }
        }

        if (entry.getDisplayName() != null) {
            String text = entry.getDisplayName().getString().toLowerCase(Locale.ROOT);
            for (String peerName : g.peersByName.keySet()) {
                if (containsUsername(text, peerName)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isEggUser(Entity entity) {
        Globals g = getInstance();
        if (g == null || !g.isEnabled()) return false;
        if (!(entity instanceof PlayerEntity player)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (player == mc.player || player.getUuid().equals(mc.player.getUuid()))) {
            return g.showSelf.get();
        }

        String name = player.getNameForScoreboard();
        if (name != null && g.peersByName.containsKey(name.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (player.getName() != null && g.peersByName.containsKey(player.getName().getString().trim().toLowerCase(Locale.ROOT))) {
            return true;
        }

        if (player.getUuid() != null && g.peersByUuid.containsKey(player.getUuid())) {
            return true;
        }

        return false;
    }

    public static boolean isEggUser(PlayerEntityRenderState state) {
        Globals g = getInstance();
        if (g == null || !g.isEnabled() || state == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state.id == mc.player.getId()) {
            return g.showSelf.get();
        }

        if (mc.world != null) {
            Entity ent = mc.world.getEntityById(state.id);
            if (ent instanceof PlayerEntity player) {
                return isEggUser(player);
            }
        }

        if (state.playerName != null) {
            String raw = state.playerName.getString().trim().toLowerCase(Locale.ROOT);
            if (raw.startsWith("🥚")) raw = raw.substring(1).trim();
            if (mc.player != null && raw.equalsIgnoreCase(mc.player.getNameForScoreboard())) {
                return g.showSelf.get();
            }
            if (g.peersByName.containsKey(raw)) {
                return true;
            }
            for (String peerName : g.peersByName.keySet()) {
                if (containsUsername(raw, peerName)) {
                    return true;
                }
            }
        }
        if (state.displayName != null) {
            String raw = state.displayName.getString().trim().toLowerCase(Locale.ROOT);
            if (raw.startsWith("🥚")) raw = raw.substring(1).trim();
            if (mc.player != null && (raw.equalsIgnoreCase(mc.player.getNameForScoreboard()) || containsUsername(raw, mc.player.getNameForScoreboard().toLowerCase(Locale.ROOT)))) {
                return g.showSelf.get();
            }
            if (g.peersByName.containsKey(raw)) {
                return true;
            }
            for (String peerName : g.peersByName.keySet()) {
                if (containsUsername(raw, peerName)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String extractCleanName(PlayerEntityRenderState state) {
        if (state == null) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state.id == mc.player.getId()) {
            return mc.player.getNameForScoreboard();
        }
        if (mc.world != null) {
            Entity ent = mc.world.getEntityById(state.id);
            if (ent instanceof PlayerEntity player) {
                return player.getNameForScoreboard();
            }
        }
        if (state.playerName != null) {
            String raw = state.playerName.getString().trim();
            if (raw.startsWith("🥚")) raw = raw.substring(1).trim();
            return raw;
        }
        return null;
    }

    public Text formatPlayerName(Text original, String cleanName) {
        if (original == null) return null;
        if (original.getString().contains("🥚")) {
            return original;
        }

        String pos = iconPosition.get();
        if ("В начале строки".equalsIgnoreCase(pos) || cleanName == null || cleanName.trim().isEmpty()) {
            return Text.empty().append(Text.literal("§f🥚§r ")).append(original);
        }

        String fullStr = original.getString();
        String lowerFull = fullStr.toLowerCase(Locale.ROOT);
        String lowerTarget = cleanName.trim().toLowerCase(Locale.ROOT);
        int targetIdx = lowerFull.indexOf(lowerTarget);
        if (targetIdx < 0) {
            return Text.empty().append(Text.literal("§f🥚§r ")).append(original);
        }

        boolean needLeadingSpace = targetIdx > 0 && fullStr.charAt(targetIdx - 1) != ' ';
        String eggStr = needLeadingSpace ? " §f🥚§r " : "§f🥚§r ";

        boolean[] inserted = new boolean[]{false};
        Text formatted = insertEggBeforeTarget(original, lowerTarget, eggStr, inserted);
        if (inserted[0] && formatted != null && formatted.getString().contains("🥚")) {
            return formatted;
        }

        return Text.empty().append(Text.literal(eggStr)).append(original);
    }

    private Text insertEggBeforeTarget(Text node, String lowerTarget, String eggStr, boolean[] inserted) {
        if (inserted[0]) {
            return node;
        }

        TextContent content = node.getContent();
        if (content instanceof PlainTextContent pt) {
            String str = pt.string();
            String lower = str.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(lowerTarget);
            if (idx >= 0) {
                inserted[0] = true;
                String before = str.substring(0, idx);
                String matched = str.substring(idx, idx + lowerTarget.length());
                String after = str.substring(idx + lowerTarget.length());

                MutableText result = Text.empty();
                if (!before.isEmpty()) {
                    result.append(Text.literal(before).setStyle(node.getStyle()));
                }
                result.append(Text.literal(eggStr));
                result.append(Text.literal(matched).setStyle(node.getStyle()));
                if (!after.isEmpty()) {
                    result.append(Text.literal(after).setStyle(node.getStyle()));
                }
                for (Text sibling : node.getSiblings()) {
                    result.append(sibling);
                }
                return result;
            }
        }

        MutableText copy = node.copyContentOnly().setStyle(node.getStyle());
        for (Text sibling : node.getSiblings()) {
            copy.append(insertEggBeforeTarget(sibling, lowerTarget, eggStr, inserted));
        }
        return copy;
    }

    public static Map<String, String> getPlayerCosmetics(PlayerEntity player) {
        Globals g = getInstance();
        if (g == null || !g.isEnabled() || player == null) return Collections.emptyMap();

        if (player.getUuid() != null) {
            Map<String, String> byUuid = g.peerCosmeticsByUuid.get(player.getUuid());
            if (byUuid != null && !byUuid.isEmpty()) return byUuid;
        }

        String name = player.getNameForScoreboard();
        if (name != null) {
            Map<String, String> known = g.peerCosmeticsByName.get(name.trim().toLowerCase(Locale.ROOT));
            if (known != null && !known.isEmpty()) {
                return known;
            }
        }

        return Collections.emptyMap();
    }

    public static Map<String, String> getPlayerCosmetics(PlayerEntityRenderState state) {
        Globals g = getInstance();
        if (g == null || !g.isEnabled() || state == null) return Collections.emptyMap();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            Entity ent = mc.world.getEntityById(state.id);
            if (ent instanceof PlayerEntity player) {
                return getPlayerCosmetics(player);
            }
        }

        if (state.playerName != null) {
            String raw = state.playerName.getString().trim().toLowerCase(Locale.ROOT);
            if (raw.startsWith("🥚")) raw = raw.substring(1).trim();
            Map<String, String> known = g.peerCosmeticsByName.get(raw);
            if (known != null && !known.isEmpty()) return known;
        }

        return Collections.emptyMap();
    }

    public static void addEggUser(String name) {
        Globals g = getInstance();
        if (g != null && name != null && !name.trim().isEmpty()) {
            g.peersByName.put(name.trim().toLowerCase(Locale.ROOT), System.currentTimeMillis() + 86400000L);
        }
    }

    public static void removeEggUser(String name) {
        Globals g = getInstance();
        if (g != null && name != null) {
            g.peersByName.remove(name.trim().toLowerCase(Locale.ROOT));
        }
    }

    public static Set<String> getEggUsers() {
        Globals g = getInstance();
        if (g != null) {
            return new HashSet<>(g.peersByName.keySet());
        }
        return Collections.emptySet();
    }

    public static void clearEggUsers() {
        Globals g = getInstance();
        if (g != null) {
            g.peersByName.clear();
        }
    }

    public Text formatLabel(Text original) {
        return formatPlayerName(original, null);
    }
}
