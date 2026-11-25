package egg.client.module.movement;

import egg.client.mixin.KeyBindingAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Sprint extends Module {
    private final BooleanSetting keepSprint = new BooleanSetting(
            "Keep Sprint",
            true,
            "Удерживать спринт при атаке или получении урона"
    );
    private boolean holdingSprintKey;

    public Sprint() {
        super("Sprint", "Автоматически зажимает бег при движении", Category.MOVEMENT);
        addSettings(keepSprint);
    }

    public boolean shouldHoldSprint(MinecraftClient client) {
        return isEnabled()
                && client != null
                && client.player != null
                && client.options != null
                && client.currentScreen == null;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client == null || client.options == null) return;
        boolean hold = shouldHoldSprint(client) && !client.options.getSprintToggled().getValue();
        if (hold) {
            if (!client.options.sprintKey.isPressed()) {
                client.options.sprintKey.setPressed(true);
            }
            holdingSprintKey = true;
        } else {
            releaseSprintKey(client);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        releaseSprintKey(MinecraftClient.getInstance());
    }

    private void releaseSprintKey(MinecraftClient client) {
        if (!holdingSprintKey) return;
        holdingSprintKey = false;
        if (client == null || client.options == null || client.options.getSprintToggled().getValue()) {
            return;
        }
        if (!isPhysicalSprintKeyPressed(client)) {
            client.options.sprintKey.setPressed(false);
        }
    }

    private boolean isPhysicalSprintKeyPressed(MinecraftClient client) {
        if (client.getWindow() == null || client.options.sprintKey.isUnbound()) {
            return false;
        }
        InputUtil.Key key = ((KeyBindingAccessor) client.options.sprintKey).getBoundKey();
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
        }
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    public BooleanSetting getKeepSprint() {
        return keepSprint;
    }
}
