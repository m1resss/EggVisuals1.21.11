package egg.client.module.combat;

import egg.client.module.Category;
import egg.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class ShiftTAP extends Module {
    private int sneakTicks = 0;

    public ShiftTAP() {
        super("ShiftTAP", "Автоматически приседает в момент удара по игроку для сброса отдачи", Category.COMBAT);
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        if (target instanceof PlayerEntity) {
            sneakTicks = 2;
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (sneakTicks > 0) {
            sneakTicks--;
            if (client.options != null) {
                client.options.sneakKey.setPressed(true);
            }
        } else if (sneakTicks == 0) {
            sneakTicks = -1;
            if (client.options != null && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                boolean physicalShift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (!physicalShift) {
                    client.options.sneakKey.setPressed(false);
                }
            }
        }
    }
}
