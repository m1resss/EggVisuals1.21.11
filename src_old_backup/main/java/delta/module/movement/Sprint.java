package delta.module.movement;

import delta.core.Interface;

import static delta.core.Interface.aM_;
import delta.core.Module;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.TickEvent;

import net.minecraft.entity.effect.StatusEffects;

@ModuleRegister(a = "Sprint", b = "Автоматически включает спринт при движении", c = Category.Movement)
public class Sprint extends Module {
    @EventTarget
    public void a(TickEvent event) {
        aM_.player.setSprinting(aM_.player.input.getMovementInput().y > 0.0f && !aM_.player.hasStatusEffect(StatusEffects.BLINDNESS) && (aM_.player.getAbilities().invulnerable || aM_.player.getHungerManager().getFoodLevel() > 6));
    }
}
