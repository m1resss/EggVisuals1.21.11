package platform.inject.mixin;


import static delta.core.Interface.aM_;

import delta.ui.screen.AssistantScreen;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import delta.event.KeyEvent;
import delta.ui.screen.StationScreen;
import delta.ui.screen.SwapScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Keyboard.class})
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int action, net.minecraft.client.input.KeyInput input, CallbackInfo ci) {
        int key = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (action == 1 && key == 344) {
            if (Interface.aM_.currentScreen instanceof delta.ui.screen.ModernGUIScreen modernGui) {
                if (modernGui.getBindingTarget() == null) {
                    modernGui.close();
                    ci.cancel();
                    return;
                }
            } else if (Interface.aM_.currentScreen == null) {
                Interface.aM_.setScreen(new delta.ui.screen.ModernGUIScreen(net.minecraft.text.Text.literal("")));
                ci.cancel();
                return;
            }
        }
        if (Interface.aM_.currentScreen == null || (Interface.aM_.currentScreen instanceof SwapScreen) || (Interface.aM_.currentScreen instanceof AssistantScreen) || (Interface.aM_.currentScreen instanceof StationScreen) || (Interface.aM_.currentScreen instanceof HandledScreen)) {
            KeyEvent event = new KeyEvent(key, scanCode, action, modifiers);
            EventManager.a((IEvent) event);
            if (event.a()) {
                ci.cancel();
            }
        }
    }
}
