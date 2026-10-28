package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.Module;
import egg.client.module.ModuleManager;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Включает или выключает указанный модуль", ".toggle <module>", "t");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendClientMessage("Использование: .toggle <module>", false);
            return;
        }

        String modName = args[0];
        Module target = null;
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.getName().equalsIgnoreCase(modName)) {
                target = m;
                break;
            }
        }

        if (target == null) {
            CommandManager.sendClientMessage("Модуль §d" + modName + " §fне найден.", false);
            return;
        }

        target.toggle();
        egg.client.config.ConfigManager.save();
        String state = target.isEnabled() ? "§aвключен" : "§cвыключен";
        CommandManager.sendClientMessage("Модуль §d" + target.getName() + " §fтеперь " + state + "§f.");
    }
}
