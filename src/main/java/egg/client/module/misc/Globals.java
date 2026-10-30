package egg.client.module.misc;

import egg.client.cosmetic.CosmeticsManager;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class Globals extends Module {
    private static Globals instance;

    private final BooleanSetting cosmetics = new BooleanSetting("Косметика", true, "Отображать косметику других пользователей Egg Visuals");

    private static final Set<UUID> KNOWN_USERS_UUID = new HashSet<>();
    private static final Set<String> KNOWN_USERS_NAME = new HashSet<>();
    private static final Map<String, Map<String, String>> CUSTOM_USER_COSMETICS = new HashMap<>();

    public Globals() {
        super("Globals", "Отображение пользователей Egg Visuals и их косметики", Category.MISC);
        instance = this;
        addSettings(cosmetics);
    }

    public static Globals getInstance() {
        return instance;
    }

    public boolean isCosmeticsEnabled() {
        return cosmetics.get();
    }

    public static boolean isEggUser(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (player == mc.player || player.getUuid().equals(mc.player.getUuid()))) {
            return true;
        }
        if (player.getUuid() != null && KNOWN_USERS_UUID.contains(player.getUuid())) {
            return true;
        }
        String name = player.getNameForScoreboard();
        if (name != null && KNOWN_USERS_NAME.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    public static void addEggUser(String name) {
        if (name == null || name.trim().isEmpty()) return;
        KNOWN_USERS_NAME.add(name.trim().toLowerCase(Locale.ROOT));
    }

    public static void removeEggUser(String name) {
        if (name == null || name.trim().isEmpty()) return;
        KNOWN_USERS_NAME.remove(name.trim().toLowerCase(Locale.ROOT));
        CUSTOM_USER_COSMETICS.remove(name.trim().toLowerCase(Locale.ROOT));
    }

    public static void clearEggUsers() {
        KNOWN_USERS_NAME.clear();
        KNOWN_USERS_UUID.clear();
        CUSTOM_USER_COSMETICS.clear();
    }

    public static Set<String> getEggUsers() {
        return Collections.unmodifiableSet(KNOWN_USERS_NAME);
    }

    public static void setPlayerCosmetic(String name, String category, String assetId) {
        if (name == null) return;
        String key = name.trim().toLowerCase(Locale.ROOT);
        addEggUser(key);
        CUSTOM_USER_COSMETICS.computeIfAbsent(key, k -> new HashMap<>()).put(category, assetId);
    }

    public static Map<String, String> getPlayerCosmetics(PlayerEntity player) {
        if (player == null) return Collections.emptyMap();
        String name = player.getNameForScoreboard();
        String key = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";

        Map<String, String> custom = CUSTOM_USER_COSMETICS.get(key);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }

        // Deterministic stylish default loadout based on player name seed
        CosmeticsManager mgr = CosmeticsManager.getInstance();
        mgr.ensureIndex();

        List<CosmeticsManager.Asset> hats = mgr.getCategoryAssets("hats", "");
        List<CosmeticsManager.Asset> wings = mgr.getCategoryAssets("wings", "");
        List<CosmeticsManager.Asset> bags = mgr.getCategoryAssets("backpacks", "");
        List<CosmeticsManager.Asset> masks = mgr.getCategoryAssets("masks", "");

        Map<String, String> loadout = new HashMap<>();
        int seed = Math.abs(key.hashCode());

        if (!hats.isEmpty()) {
            loadout.put("hats", hats.get(seed % hats.size()).id());
        }
        if (!wings.isEmpty()) {
            loadout.put("wings", wings.get((seed / 3) % wings.size()).id());
        }
        if (!bags.isEmpty()) {
            loadout.put("backpacks", bags.get((seed / 7) % bags.size()).id());
        }
        if (!masks.isEmpty() && (seed % 2 == 0)) {
            loadout.put("masks", masks.get((seed / 11) % masks.size()).id());
        }

        return loadout;
    }
}
