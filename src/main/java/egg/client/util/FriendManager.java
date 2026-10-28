package egg.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FriendManager {
    private static final FriendManager INSTANCE = new FriendManager();
    private final Set<String> friends = Collections.synchronizedSet(new HashSet<>());
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private FriendManager() {
        File dir = FabricLoader.getInstance().getConfigDir().resolve("egg").toFile();
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "friends.json");
        load();
    }

    public static FriendManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isFriend(String name) {
        if (name == null) return false;
        return friends.contains(name.toLowerCase());
    }

    public synchronized void addFriend(String name) {
        if (name != null) {
            friends.add(name.toLowerCase());
            save();
        }
    }

    public synchronized void removeFriend(String name) {
        if (name != null) {
            friends.remove(name.toLowerCase());
            save();
        }
    }

    public synchronized Set<String> getFriends() {
        return new HashSet<>(friends);
    }

    public synchronized void clear() {
        friends.clear();
        save();
    }

    public synchronized void load() {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Set<String> loaded = gson.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
            if (loaded != null) {
                friends.clear();
                for (String s : loaded) friends.add(s.toLowerCase());
            }
        } catch (Exception ignored) {}
    }

    public synchronized void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(friends, writer);
        } catch (Exception ignored) {}
    }
}
