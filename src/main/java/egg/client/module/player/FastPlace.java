package egg.client.module.player;

import egg.client.mixin.MinecraftClientAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;

public class FastPlace extends Module {
    public FastPlace() {
        super("Fast Place", "Убирает задержку при установке блоков", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.getMainHandStack().getItem() instanceof BlockItem) {
            ((MinecraftClientAccessor) client).setItemUseCooldown(0);
        }
    }
}
