package egg.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.yggdrasil.ProfileResult;
import egg.client.EggClient;
import egg.client.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.util.Uuids;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class AccountManager {
    private static final AccountManager INSTANCE = new AccountManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static final String[] ADJECTIVES = {
            "Shadow", "Frost", "Night", "Dark", "Ghost", "Nova", "Cryo", "Void",
            "Storm", "Cyber", "Blaze", "Neon", "Phantom", "Iron", "Silent", "Lunar"
    };
    private static final String[] NOUNS = {
            "Fox", "Wolf", "Blade", "Ray", "Hawk", "Knight", "Spark", "Drift",
            "Pulse", "Vex", "Crow", "Viper", "Star", "Edge", "Echo", "Lynx"
    };

    private final List<String> accounts = new ArrayList<>();
    private final File file;
    private final Random random = new Random();

    private Session originalSession;
    private CompletableFuture<ProfileResult> originalProfileFuture;

    private AccountManager() {
        File dir = FabricLoader.getInstance().getConfigDir().resolve("egg").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.file = new File(dir, "accounts.json");
        load();
    }

    public static AccountManager getInstance() {
        return INSTANCE;
    }

    public synchronized void captureOriginal(MinecraftClient mc) {
        if (mc == null) return;
        if (originalSession == null) {
            originalSession = mc.getSession();
            originalProfileFuture = ((MinecraftClientAccessor) mc).getGameProfileFuture();
        }
    }

    public static boolean isValidUsername(String username) {
        return username != null && VALID_NAME.matcher(username).matches();
    }

    public synchronized String loginOffline(MinecraftClient mc, String username) {
        if (mc == null) return "Клиент не инициализирован";
        String clean = username == null ? "" : username.trim();
        if (!isValidUsername(clean)) {
            return "Ник: 3–16 символов (A-Z, 0-9, _)";
        }

        captureOriginal(mc);

        if (originalSession != null && originalSession.getUsername().equalsIgnoreCase(clean)) {
            restoreOriginal(mc);
            addOrPromote(clean);
            return null;
        }

        UUID offlineUuid = Uuids.getOfflinePlayerUuid(clean);
        Session offlineSession = new Session(clean, offlineUuid, "0", Optional.empty(), Optional.empty());
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        accessor.setSession(offlineSession);
        accessor.setGameProfileFuture(CompletableFuture.completedFuture(null));

        addOrPromote(clean);
        EggClient.LOGGER.info("[AccountManager] Switched to offline account: {}", clean);
        return null;
    }

    public synchronized void restoreOriginal(MinecraftClient mc) {
        if (mc == null || originalSession == null) return;
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        accessor.setSession(originalSession);
        accessor.setGameProfileFuture(originalProfileFuture != null ? originalProfileFuture : CompletableFuture.completedFuture(null));
        EggClient.LOGGER.info("[AccountManager] Restored original session: {}", originalSession.getUsername());
    }

    public synchronized void addOrPromote(String username) {
        if (!isValidUsername(username)) return;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).equalsIgnoreCase(username)) {
                accounts.set(i, username);
                save();
                return;
            }
        }
        accounts.add(0, username);
        save();
    }

    public synchronized void removeAccount(String username) {
        if (username == null) return;
        if (accounts.removeIf(name -> name.equalsIgnoreCase(username))) {
            save();
        }
    }

    public synchronized List<String> getAccounts() {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    public synchronized String getCurrentUsername(MinecraftClient mc) {
        if (mc == null || mc.getSession() == null) return "Player";
        return mc.getSession().getUsername();
    }

    public synchronized String getOriginalUsername() {
        return originalSession != null ? originalSession.getUsername() : null;
    }

    public synchronized boolean isOriginalActive(MinecraftClient mc) {
        if (mc == null || originalSession == null) return true;
        return mc.getSession() == originalSession;
    }

    public String generateRandomName() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String adj = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
            String noun = NOUNS[random.nextInt(NOUNS.length)];
            int num = 10 + random.nextInt(90);
            String candidate = adj + noun + num;
            if (candidate.length() > 16) {
                candidate = candidate.substring(0, 16);
            }
            boolean exists = false;
            synchronized (this) {
                for (String acc : accounts) {
                    if (acc.equalsIgnoreCase(candidate)) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) return candidate;
        }
        return "Player" + (1000 + random.nextInt(9000));
    }

    public synchronized void load() {
        accounts.clear();
        if (!file.exists() || file.length() == 0) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("accounts") && root.get("accounts").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("accounts");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) {
                        String name = el.getAsString();
                        if (isValidUsername(name) && accounts.stream().noneMatch(a -> a.equalsIgnoreCase(name))) {
                            accounts.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EggClient.LOGGER.warn("[AccountManager] Failed to load accounts.json", e);
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String acc : accounts) {
                arr.add(acc);
            }
            root.add("accounts", arr);
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            EggClient.LOGGER.warn("[AccountManager] Failed to save accounts.json", e);
        }
    }
}
