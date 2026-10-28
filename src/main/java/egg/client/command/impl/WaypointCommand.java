package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.command.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;

public class WaypointCommand extends Command {
    public WaypointCommand() {
        super("waypoint", "Управление метками на карте", ".waypoint <add/del/clear/list> [name] [x y z]", "way", "wp");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendClientMessage("Использование: " + getSyntax(), false);
            return;
        }

        String sub = args[0].toLowerCase();
        WaypointManager wm = WaypointManager.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя метки: .waypoint add <name> [x y z]", false);
                    return;
                }
                String name = args[1];
                double x, y, z;
                if (args.length >= 5) {
                    try {
                        x = Double.parseDouble(args[2]);
                        y = Double.parseDouble(args[3]);
                        z = Double.parseDouble(args[4]);
                    } catch (NumberFormatException e) {
                        CommandManager.sendClientMessage("Координаты должны быть числами!", false);
                        return;
                    }
                } else {
                    if (mc.player == null) {
                        CommandManager.sendClientMessage("Вы не в игре!", false);
                        return;
                    }
                    x = Math.round(mc.player.getX());
                    y = Math.round(mc.player.getY());
                    z = Math.round(mc.player.getZ());
                }

                wm.add(name, x, y, z);
                CommandManager.sendClientMessage("Метка §d" + name + " §fдобавлена на координаты §7(" + (int) x + ", " + (int) y + ", " + (int) z + ")");
            }
            case "del", "remove" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("Укажите имя метки: .waypoint del <name>", false);
                    return;
                }
                String name = args[1];
                if (wm.remove(name)) {
                    CommandManager.sendClientMessage("Метка §d" + name + " §fудалена.");
                } else {
                    CommandManager.sendClientMessage("Метка §d" + name + " §fне найдена.", false);
                }
            }
            case "clear" -> {
                wm.clear();
                CommandManager.sendClientMessage("Все метки успешно очищены.");
            }
            case "list" -> {
                if (wm.getWaypoints().isEmpty()) {
                    CommandManager.sendClientMessage("Список меток пуст.");
                    return;
                }
                CommandManager.sendClientMessage("Список активных меток:");
                for (WaypointManager.Waypoint wp : wm.getWaypoints()) {
                    CommandManager.sendClientMessage(" - §d" + wp.name + "§7: (" + (int) wp.x + ", " + (int) wp.y + ", " + (int) wp.z + ")");
                }
            }
            default -> CommandManager.sendClientMessage("Неизвестное поддействие. Используйте: add, del, clear, list", false);
        }
    }
}
