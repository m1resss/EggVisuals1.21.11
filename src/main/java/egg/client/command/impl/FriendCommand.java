package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.util.FriendManager;

public class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", "Управление списком друзей", ".friend <add/del/list/clear> [name]", "f");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendClientMessage("Использование: " + getSyntax(), false);
            return;
        }

        String sub = args[0].toLowerCase();
        FriendManager fm = FriendManager.getInstance();

        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя игрока: .friend add <name>", false);
                    return;
                }
                String name = args[1];
                if (fm.isFriend(name)) {
                    CommandManager.sendClientMessage("Игрок §d" + name + " §fуже есть в друзьях.", false);
                } else {
                    fm.addFriend(name);
                    CommandManager.sendClientMessage("Игрок §d" + name + " §fдобавлен в друзья.");
                }
            }
            case "del", "remove" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя игрока: .friend del <name>", false);
                    return;
                }
                String name = args[1];
                if (fm.isFriend(name)) {
                    fm.removeFriend(name);
                    CommandManager.sendClientMessage("Игрок §d" + name + " §fудален из друзей.");
                } else {
                    CommandManager.sendClientMessage("Игрок §d" + name + " §fне найден в друзьях.", false);
                }
            }
            case "list" -> {
                if (fm.getFriends().isEmpty()) {
                    CommandManager.sendClientMessage("Список друзей пуст.");
                    return;
                }
                CommandManager.sendClientMessage("Список друзей (" + fm.getFriends().size() + "):");
                for (String fr : fm.getFriends()) {
                    CommandManager.sendClientMessage(" - §d" + fr);
                }
            }
            case "clear" -> {
                fm.clear();
                CommandManager.sendClientMessage("Список друзей очищен.");
            }
            default -> CommandManager.sendClientMessage("Используйте: add, del, list, clear", false);
        }
    }
}
