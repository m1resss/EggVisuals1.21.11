package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.liteapi.LiteApiFeatureControl;
import net.minecraft.client.MinecraftClient;

import java.util.Set;

public class HolyWorldCommand extends Command {
    public HolyWorldCommand() {
        super("holyworld", "Показывает статус подключения к HolyWorld LiteAPI", ".holyworld [check]", "hw", "liteapi");
    }

    @Override
    public void execute(String[] args) {
        LiteApiFeatureControl api = LiteApiFeatureControl.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            boolean sent = api.sendCheckFeatures(mc);
            if (sent) {
                CommandManager.sendClientMessage("§7[§6HolyWorld§7] §aЗапрос checkFeatures отправлен на сервер.", false);
            } else {
                CommandManager.sendClientMessage("§7[§6HolyWorld§7] §cНе удалось отправить запрос (rate limit 10с или оффлайн).", false);
            }
            return;
        }

        CommandManager.sendClientMessage("§7--- §6HolyWorld In-Game API §7---", false);
        CommandManager.sendClientMessage("§7Статус: " + (api.isConnected() ? "§aПодключено к серверу" : "§eОжидание ответа сервера"), false);
        CommandManager.sendClientMessage("§7Client ID: §f" + LiteApiFeatureControl.CLIENT_ID, false);
        CommandManager.sendClientMessage("§7Канал: §fliteapi:feature-control", false);
        CommandManager.sendClientMessage("§7Всего функций в API: §f" + api.getFeaturesCount(), false);
        Set<String> blocklist = api.getActiveBlocklist();
        CommandManager.sendClientMessage("§7Активный блок-лист: §f" + (blocklist.isEmpty() ? "[] (все разрешены)" : blocklist.toString()), false);
        CommandManager.sendClientMessage("§7Для ручной отправки: §f.hw check", false);
    }
}
