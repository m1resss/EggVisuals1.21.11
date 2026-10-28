package delta.module.player;

import platform.inject.accessors.MinecraftClientAccessor;
import static delta.core.Interface.aM_;
import delta.core.Module;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.ModuleRegister;
import delta.event.TickEvent;

import net.minecraft.item.Items;

@ModuleRegister(a = "Fast EXP", b = "Позволяет очень быстро бросать опыт", c = Category.Player)
public class FastEXP extends Module implements Interface {
    @EventTarget
    public void a(TickEvent event) {
        if (aM_.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            ((platform.inject.accessors.MinecraftClientAccessor) (Object) aM_).setItemUseCooldown(0);
        }
    }
}
