package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.render.TotemPopConfetti;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ConfettiCommand extends Command {
    public ConfettiCommand() {
        super("confetti", "Запускает тестовый взрыв 3D конфетти", ".confetti", "totem");
    }

    @Override
    public void execute(String[] args) {
        TotemPopConfetti module = ModuleManager.getInstance().getModule(TotemPopConfetti.class);
        if (module == null) return;

        if (!module.isEnabled()) {
            module.setEnabled(true);
            CommandManager.sendClientMessage("Модуль §aTotemPopConfetti §fбыл выключен и теперь §aвключен§f.");
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Vec3d origin = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight() * 0.7, mc.player.getZ());
            module.spawnBurst(origin);
            CommandManager.sendClientMessage("§aВзрыв 3D конфетти запущен!");
        }
    }
}
