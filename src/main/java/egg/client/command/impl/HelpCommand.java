package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Показывает список доступных команд", ".help", "h", "commands");
    }

    @Override
    public void execute(String[] args) {
        CommandManager.sendClientMessage("Список доступных команд:");
        for (Command cmd : CommandManager.getInstance().getCommands()) {
            CommandManager.sendClientMessage("§d." + cmd.getName() + " §7- " + cmd.getDescription() + " §8(" + cmd.getSyntax() + ")");
        }
    }
}
