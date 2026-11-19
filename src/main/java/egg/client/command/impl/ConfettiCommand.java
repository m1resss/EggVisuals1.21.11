package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.render.TotemPop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ConfettiCommand extends Command {
    public ConfettiCommand() {
        super("confetti", "Запускает эффект тотема", ".confetti", "totem", "totempop");
    }

    @Override
    public void execute(String[] args) {
        TotemPop module = ModuleManager.getInstance().getModule(TotemPop.class);
        if (module == null) return;

        if (!module.isEnabled()) {
            module.setEnabled(true);
            CommandManager.sendClientMessage("Модуль §aTotemPop §fбыл выключен и теперь §aвключен§f.");
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Vec3d origin = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            module.spawnBurst(origin);
            CommandManager.sendClientMessage("§aЭффект тотема запущен!");
        }
    }
}
