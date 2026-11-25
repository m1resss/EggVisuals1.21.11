package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.movement.Sprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.playerInput.sprint()) {
            return;
        }
        Sprint sprint = ModuleManager.getInstance().getModule(Sprint.class);
        if (sprint != null && sprint.shouldHoldSprint(MinecraftClient.getInstance())) {
            ((InputAccessor) this).setPlayerInput(new PlayerInput(
                    this.playerInput.forward(),
                    this.playerInput.backward(),
                    this.playerInput.left(),
                    this.playerInput.right(),
                    this.playerInput.jump(),
                    this.playerInput.sneak(),
                    true
            ));
        }
    }
}
