package delta.module.movement;

import delta.core.Interface;

import platform.inject.accessors.LivingEntityAccessor;
import platform.inject.accessors.MinecraftClientAccessor;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.TickEvent;
import delta.setting.BooleanSetting;

import delta.setting.MultiModeSetting;
import net.minecraft.item.BlockItem;

@ModuleRegister(a = "No Delay", b = "Убирает задержку у выбранных действий", c = Category.Movement)
public class NoDelay extends Module {
    public final MultiModeSetting b = new MultiModeSetting("Отключить задержку на", new BooleanSetting("Поставку блоков", true), new BooleanSetting("Прыжки", true));

    public NoDelay() {
        a(this.b);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.b.a("Поставку блоков").c().booleanValue() && (aM_.player.getMainHandStack().getItem() instanceof BlockItem)) {
            ((platform.inject.accessors.MinecraftClientAccessor) (Object) aM_).setItemUseCooldown(0);
        }
        if (this.b.a("Прыжки").c().booleanValue()) {
            ((platform.inject.accessors.LivingEntityAccessor) (Object) aM_.player).setJumpingCooldown(0);
        }
    }
}
