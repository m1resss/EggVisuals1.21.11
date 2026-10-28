package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.config.ConfigManager;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind", "Привязка клавиши к модулю", ".bind <module> <key/none>", "b");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            CommandManager.sendClientMessage("Использование: .bind <module> <key/none>", false);
            return;
        }

        String modName = args[0];
        String keyName = args[1].toUpperCase();

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

        if (keyName.equalsIgnoreCase("NONE") || keyName.equalsIgnoreCase("0") || keyName.equalsIgnoreCase("CLEAR")) {
            target.setKey(0);
            ConfigManager.save();
            CommandManager.sendClientMessage("Байнд с модуля §d" + target.getName() + " §fуспешно снят.");
            return;
        }

        int keyCode = resolveKeyCode(keyName);
        if (keyCode == 0) {
            CommandManager.sendClientMessage("Неизвестная клавиша: §c" + keyName, false);
            return;
        }

        target.setKey(keyCode);
        ConfigManager.save();
        CommandManager.sendClientMessage("Модуль §d" + target.getName() + " §fпривязан к клавише §d" + keyName + "§f.");
    }

    private int resolveKeyCode(String name) {
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        return switch (name) {
            case "RSHIFT", "RIGHT_SHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LSHIFT", "LEFT_SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RCTRL", "RIGHT_CONTROL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LCTRL", "LEFT_CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RALT", "RIGHT_ALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "LALT", "LEFT_ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "CAPS", "CAPSLOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "ENTER" -> GLFW.GLFW_KEY_ENTER;
            case "ESCAPE", "ESC" -> GLFW.GLFW_KEY_ESCAPE;
            default -> {
                try {
                    Field f = GLFW.class.getField("GLFW_KEY_" + name);
                    yield f.getInt(null);
                } catch (Exception ignored) {
                    yield 0;
                }
            }
        };
    }
}
