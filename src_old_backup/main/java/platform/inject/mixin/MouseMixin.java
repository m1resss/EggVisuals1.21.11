package platform.inject.mixin;


import static delta.core.Interface.aM_;

import delta.ui.screen.AssistantScreen;
import delta.event.ClickEvent;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.core.Interface;
import delta.event.KeyEvent;
import delta.event.LookEvent;
import delta.event.ScrollEvent;
import delta.ui.screen.SwapScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Mouse.class})
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    public void onMouseButton(long window, net.minecraft.client.input.MouseInput input, int action, CallbackInfo ci) {
        int button = input.button();
        int modifiers = input.modifiers();
        if (Interface.aM_.currentScreen == null || (Interface.aM_.currentScreen instanceof SwapScreen) || (Interface.aM_.currentScreen instanceof AssistantScreen)) {
            EventManager.a((IEvent) new KeyEvent((button < 0 || button > 7) ? button : (-100) + button, 0, action, modifiers));
        }
        if (action == 1) {
            ClickEvent event = new ClickEvent(Interface.aM_.mouse.getX() / 2.0d, Interface.aM_.mouse.getY() / 2.0d, button, ClickEvent.a.PRESS);
            EventManager.a((IEvent) event);
            if (event.a()) {
                ci.cancel();
                return;
            }
            return;
        }
        if (action == 0) {
            ClickEvent event2 = new ClickEvent(Interface.aM_.mouse.getX() / 2.0d, Interface.aM_.mouse.getY() / 2.0d, button, ClickEvent.a.RELEASE);
            EventManager.a((IEvent) event2);
            if (event2.a()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = {"onCursorPos"}, at = {@At("HEAD")}, cancellable = true)
    public void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        ClickEvent event = new ClickEvent(Interface.aM_.mouse.getX() / 2.0d, Interface.aM_.mouse.getY() / 2.0d, 0, ClickEvent.a.DRAG);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.cancel();
        }
    }

    @Inject(method = {"onMouseScroll"}, at = {@At("HEAD")}, cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        ScrollEvent event = new ScrollEvent(horizontal, vertical);
        EventManager.a((IEvent) event);
        if (event.a()) {
            ci.cancel();
        }
    }

    @Redirect(method = {"updateMouse"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void redirectChangeLookDirection(ClientPlayerEntity player, double yaw, double pitch) {
        LookEvent event = new LookEvent((float) yaw, (float) pitch);
        EventManager.a((IEvent) event);
        if (!event.a()) {
            player.changeLookDirection(yaw, pitch);
        }
    }
}
