package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.misc.Globals;

public class GlobalsCommand extends Command {
    public GlobalsCommand() {
        super("globals", "Управление списком пользователей Egg Visuals", ".globals <add/del/list/clear> [name]", "g", "egg");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendClientMessage("Использование: " + getSyntax(), false);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя игрока: .globals add <name>", false);
                    return;
                }
                String name = args[1];
                Globals.addEggUser(name);
                CommandManager.sendClientMessage("Игрок §e" + name + " §fдобавлен как пользователь Egg Visuals.");
            }
            case "del", "remove" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя игрока: .globals del <name>", false);
                    return;
                }
                String name = args[1];
                Globals.removeEggUser(name);
                CommandManager.sendClientMessage("Игрок §e" + name + " §fудален из списка пользователей Egg Visuals.");
            }
            case "list" -> {
                var users = Globals.getEggUsers();
                if (users.isEmpty()) {
                    CommandManager.sendClientMessage("Список пользователей Egg Visuals пуст (локальный игрок добавляется автоматически).");
                    return;
                }
                CommandManager.sendClientMessage("Пользователи Egg Visuals (" + users.size() + "):");
                for (String u : users) {
                    CommandManager.sendClientMessage(" - §e" + u);
                }
            }
            case "clear" -> {
                Globals.clearEggUsers();
                CommandManager.sendClientMessage("Список пользователей Egg Visuals очищен.");
            }
            default -> CommandManager.sendClientMessage("Неизвестная подкоманда. Использование: " + getSyntax(), false);
        }
    }
}
