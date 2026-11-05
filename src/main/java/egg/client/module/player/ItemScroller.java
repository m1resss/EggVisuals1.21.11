package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class ItemScroller extends Module {
    private final ModeSetting activationMode = new ModeSetting("Режим", "Shift + Зажатие", "Условие перемещения проведением курсора", "Shift + Зажатие", "Только Shift", "Только ЛКМ");
    private final BooleanSetting mouseWheel = new BooleanSetting("Колёсико", true, "Перемещение предметов прокруткой мыши над слотом");
    private final SliderSetting delay = new SliderSetting("Задержка (мс)", 25.0, 0.0, 100.0, 5.0, "Задержка между слотами при перемещении");
    private long lastMoveTime = 0L;

    public ItemScroller() {
        super("ItemScroller", "Быстро перемещает предметы в сундуках проведением мыши с зажатой клавишей", Category.PLAYER);
        addSettings(activationMode, mouseWheel, delay);
    }

    public boolean isWheelEnabled() {
        return mouseWheel.isEnabled();
    }

    public boolean isKeyHeld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        boolean shiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean lmbDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        String m = activationMode.get();
        if ("Только Shift".equals(m)) {
            return shiftDown;
        } else if ("Только ЛКМ".equals(m)) {
            return lmbDown;
        } else {
            return shiftDown && lmbDown;
        }
    }

    public boolean canMove() {
        long now = System.currentTimeMillis();
        if (now - lastMoveTime >= (long) delay.get()) {
            lastMoveTime = now;
            return true;
        }
        return false;
    }
}
