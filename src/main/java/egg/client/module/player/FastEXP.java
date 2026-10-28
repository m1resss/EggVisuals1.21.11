package egg.client.module.player;

import egg.client.mixin.MinecraftClientAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class FastEXP extends Module {

    public FastEXP() {
        super("FastEXP", "Позволяет очень быстро бросать пузырьки опыта без задержки", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) || client.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            ((MinecraftClientAccessor) client).setItemUseCooldown(0);
        }
    }
}
