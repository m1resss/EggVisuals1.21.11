package egg.client.module.setting;

import org.lwjgl.glfw.GLFW;
import java.util.Locale;

public class BindSetting extends Setting {
    private int key;
    private final Runnable action;
    private Runnable onRelease;

    public BindSetting(String name, int defaultKey, Runnable action, String description) {
        this(name, defaultKey, action, null, description);
    }

    public BindSetting(String name, int defaultKey, Runnable action, Runnable onRelease, String description) {
        super(name, description);
        this.key = defaultKey;
        this.action = action;
        this.onRelease = onRelease;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public void setOnRelease(Runnable onRelease) {
        this.onRelease = onRelease;
    }

    public void trigger() {
        if (action != null) {
            action.run();
        }
    }

    public void release() {
        if (onRelease != null) {
            onRelease.run();
        }
    }

    public String getKeyName() {
        if (key <= 0) return "Нет";
        if (key >= 1000) {
            int btn = key - 1000;
            return switch (btn) {
                case 0 -> "ЛКМ";
                case 1 -> "ПКМ";
                case 2 -> "СКМ";
                default -> "M" + (btn + 1);
            };
        }
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase(Locale.ROOT);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_SPACE -> "Пробел";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "K" + key;
            }
        };
    }
}
