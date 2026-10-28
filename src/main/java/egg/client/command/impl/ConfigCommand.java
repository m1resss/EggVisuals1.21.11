package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.config.ConfigManager;

import java.io.File;
import java.util.List;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", "Управление конфигурацией клиента", ".cfg <save/load/list/dir/delete> [name]", "cfg");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendClientMessage("Использование: " + getSyntax(), false);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "dir", "folder", "open" -> {
                File dir = ConfigManager.getConfigDir();
                ConfigManager.openConfigDir();
                CommandManager.sendClientMessage("Папка конфигураций открыта: §7" + dir.getAbsolutePath());
            }
            case "save" -> {
                String name = args.length > 1 ? args[1] : ConfigManager.getCurrentConfig();
                boolean ok = ConfigManager.save(name);
                if (ok) {
                    CommandManager.sendClientMessage("Конфигурация §d" + name + " §fуспешно сохранена.");
                } else {
                    CommandManager.sendClientMessage("Ошибка сохранения конфигурации §c" + name, false);
                }
            }
            case "load" -> {
                String name = args.length > 1 ? args[1] : ConfigManager.getCurrentConfig();
                boolean ok = ConfigManager.load(name);
                if (ok) {
                    CommandManager.sendClientMessage("Конфигурация §d" + name + " §fуспешно загружена.");
                } else {
                    CommandManager.sendClientMessage("Конфигурация §c" + name + " §fне найдена.", false);
                }
            }
            case "list" -> {
                List<String> configs = ConfigManager.getConfigs();
                if (configs.isEmpty()) {
                    CommandManager.sendClientMessage("Сохранённых конфигураций пока нет.");
                } else {
                    String active = ConfigManager.getCurrentConfig();
                    CommandManager.sendClientMessage("Конфигурации (" + configs.size() + "): §d" + String.join("§7, §d", configs) + " §7(активная: §a" + active + "§7)");
                }
            }
            case "delete", "del", "remove" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Использование: .cfg delete <name>", false);
                    return;
                }
                String name = args[1];
                boolean ok = ConfigManager.delete(name);
                if (ok) {
                    CommandManager.sendClientMessage("Конфигурация §d" + name + " §fудалена.");
                } else {
                    CommandManager.sendClientMessage("Конфигурация §c" + name + " §fне найдена.", false);
                }
            }
            default -> CommandManager.sendClientMessage("Использование: " + getSyntax(), false);
        }
    }
}
