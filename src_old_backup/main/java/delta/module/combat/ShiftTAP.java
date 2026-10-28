package delta.module.combat;

import delta.core.Module;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.AttackEvent;
import delta.event.InputEvent;
import delta.event.TickEvent;

import net.minecraft.entity.player.PlayerEntity;

@ModuleRegister(a = "Shift TAP", b = "Автоматически приседает в момент удара по игроку", c = Category.Combat)
public class ShiftTAP extends Module {
    private int b;

    @EventTarget
    public void a(AttackEvent event) {
        if (event.b() instanceof PlayerEntity) {
            this.b = 2;
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.b > 0) {
            this.b--;
        }
    }

    @EventTarget
    public void a(InputEvent event) {
        if (this.b > 0) {
            event.c(true);
        }
    }
}
