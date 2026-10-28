package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.combat.AutoSwap;
import egg.client.ui.ClickGuiScreen;
import egg.client.ui.screen.SwapScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKeyInject(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) {
            if (client.currentScreen instanceof SwapScreen swapScreen) {
                AutoSwap as = ModuleManager.getInstance().getModule(AutoSwap.class);
                if (as != null && keyInput.key() == as.getKey()) {
                    swapScreen.confirmAndClose();
                    ci.cancel();
                    return;
                }
            }
        }

        if (action == GLFW.GLFW_PRESS) {
            int key = keyInput.key();

            // Right Shift opens/closes ClickGUI
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                if (client.currentScreen == null) {
                    client.setScreen(ClickGuiScreen.getInstance());
                    ci.cancel();
                    return;
                } else if (client.currentScreen instanceof ClickGuiScreen gui) {
                    gui.closeWithAnimation();
                    ci.cancel();
                    return;
                }
            }

            /*
            // Global module hotkeys
            if (client.currentScreen == null) {
                if (egg.client.module.cosmetic.MeleeSkins.INSTANCE != null) {
                    egg.client.module.cosmetic.MeleeSkins.INSTANCE.onKeyPressed(key);
                }
                ModuleManager.getInstance().onKey(key, action);
            }
            */
            if (client.currentScreen == null) {
                ModuleManager.getInstance().onKey(key, action);
            }
        }
    }
}
