package delta.module.combat;

import delta.core.Delta;
import delta.core.Module;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.AttackEvent;

@ModuleRegister(a = "No Friend Damage", b = "Не позволяет наносить урон вашим друзьям", c = Category.Combat)
public class NoFriendDamage extends Module {
    @EventTarget
    public void a(AttackEvent event) {
        event.a(Delta.h().d().e().d(event.b().getName().getString()));
    }
}
