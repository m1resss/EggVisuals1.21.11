package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;

/**
 * 1:1 AutoAuth port from egg 1.21.4 (delta.module.player.AutoAuth).
 */
public class AutoAuth extends Module {
    private final StringSetting authPassword = new StringSetting("Пароль авторизации", "", "Пароль для автоматического /login");
    private final StringSetting regPassword = new StringSetting("Пароль регистрации", "", "Пароль для автоматического /register");

    public AutoAuth() {
        super("Auto Auth", "Автоматически вводит пароль при авторизации и регистрации", Category.PLAYER);
        addSettings(authPassword, regPassword);
    }

    public void onChatMessage(String message) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        String lower = message.toLowerCase();
        if ((lower.contains("зарегистрируйтесь") || lower.contains("/reg") || lower.contains("/register")) && !this.regPassword.get().isEmpty()) {
            String p = this.regPassword.get();
            mc.player.networkHandler.sendChatMessage("/register " + p + " " + p);
        } else if ((lower.contains("авторизуйтесь") || lower.contains("войдите") || lower.contains("/login") || lower.contains("/l ")) && !this.authPassword.get().isEmpty()) {
            String p = this.authPassword.get();
            mc.player.networkHandler.sendChatMessage("/login " + p);
        }
    }
}
