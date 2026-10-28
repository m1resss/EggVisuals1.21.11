package egg.client;

import egg.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggClient implements ClientModInitializer {
    public static final String MOD_ID = "egg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static EggClient instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[Egg Visual] Initializing client...");

        // Initialize Module Manager
        ModuleManager.getInstance();
        egg.client.config.ConfigManager.load();

        // Register client tick listener for module updates
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleManager.getInstance().onTick(client);
        });

        LOGGER.info("[Egg Visual] Successfully loaded! Press Right Shift to open ClickGUI.");
    }

    public static EggClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return ModuleManager.getInstance();
    }
}
