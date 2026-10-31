package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.combat.AutoSwap;
import egg.client.ui.widget.SwapWheelWidget;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButtonInject(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        int button = mouseInput.button();

        if (client != null && client.currentScreen instanceof InventoryScreen invScreen) {
            float mouseX = (float) client.mouse.getX() * (float) client.getWindow().getScaledWidth() / (float) client.getWindow().getWidth();
            float mouseY = (float) client.mouse.getY() * (float) client.getWindow().getScaledHeight() / (float) client.getWindow().getHeight();
            int guiX = ((HandledScreenAccessor) invScreen).getX();
            int guiY = ((HandledScreenAccessor) invScreen).getY();
            float miniRadius = 36.0f;
            float miniCx = Math.max(miniRadius + 8.0f, guiX - miniRadius - 16.0f);
            float miniCy = guiY + 83.0f;

            int sector = SwapWheelWidget.getSectorAt(mouseX, mouseY, miniCx, miniCy, miniRadius);
            if (sector != -1) {
                AutoSwap as = ModuleManager.getInstance().getModule(AutoSwap.class);
                if (as != null) {
                    if (action == GLFW.GLFW_PRESS) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {

                            as.setWheelSlot(sector, ItemStack.EMPTY);
                            GuiSoundManager.playSlider();
                            ci.cancel();
                            return;
                        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            ItemStack cursorStack = invScreen.getScreenHandler().getCursorStack();
                            if (!cursorStack.isEmpty()) {
                                as.setWheelSlot(sector, cursorStack.copy());
                                GuiSoundManager.playSlider();
                                ci.cancel();
                                return;
                            } else {
                                Slot focused = ((HandledScreenAccessor) invScreen).getFocusedSlot();
                                if (focused != null && focused.hasStack()) {
                                    as.setWheelSlot(sector, focused.getStack().copy());
                                    GuiSoundManager.playSlider();
                                    ci.cancel();
                                    return;
                                }
                            }
                        }
                    } else if (action == GLFW.GLFW_RELEASE) {
                        ItemStack cursorStack = invScreen.getScreenHandler().getCursorStack();
                        if (!cursorStack.isEmpty()) {
                            as.setWheelSlot(sector, cursorStack.copy());
                            GuiSoundManager.playSlider();
                            ci.cancel();
                            return;
                        }
                    }
                }
            }

            if (action == GLFW.GLFW_PRESS) {
                if (egg.client.ui.widget.InventoryScreenOverlay.mouseClicked(invScreen, mouseX, mouseY, button)) {
                    ci.cancel();
                    return;
                }
            }
        }

        if (client != null && client.currentScreen instanceof ChatScreen) {
            float mouseX = (float) client.mouse.getX() * (float) client.getWindow().getScaledWidth() / (float) client.getWindow().getWidth();
            float mouseY = (float) client.mouse.getY() * (float) client.getWindow().getScaledHeight() / (float) client.getWindow().getHeight();

            if (action == GLFW.GLFW_RELEASE) {
                egg.client.module.render.TargetHUD targetHUD = ModuleManager.getInstance().getModule(egg.client.module.render.TargetHUD.class);
                if (targetHUD != null && targetHUD.isEnabled()) {
                    targetHUD.onChatMouseReleased(mouseX, mouseY, button);
                }

                egg.client.module.render.Cooldowns cooldowns = ModuleManager.getInstance().getModule(egg.client.module.render.Cooldowns.class);
                if (cooldowns != null && cooldowns.isEnabled()) {
                    cooldowns.onChatMouseReleased(mouseX, mouseY, button);
                }

                egg.client.module.render.PotionHUD potionHUD = ModuleManager.getInstance().getModule(egg.client.module.render.PotionHUD.class);
                if (potionHUD != null && potionHUD.isEnabled()) {
                    potionHUD.onChatMouseReleased(mouseX, mouseY, button);
                }

                egg.client.module.render.ArmorHUD armorHUD = ModuleManager.getInstance().getModule(egg.client.module.render.ArmorHUD.class);
                if (armorHUD != null && armorHUD.isEnabled()) {
                    armorHUD.onChatMouseReleased(mouseX, mouseY, button);
                }

                egg.client.module.render.Interface iface = ModuleManager.getInstance().getModule(egg.client.module.render.Interface.class);
                if (iface != null && iface.isEnabled()) {
                    iface.onChatMouseReleased(mouseX, mouseY, button);
                }
            }
        }

        egg.client.module.render.ViewModel vm = ModuleManager.getInstance().getModule(egg.client.module.render.ViewModel.class);
        if (vm != null && vm.isEnabled()) {
            if (vm.onClick(button, action)) {
                return;
            }
        }

        if (action == GLFW.GLFW_PRESS && client != null && client.currentScreen == null) {
            ModuleManager.getInstance().onKey(1000 + button, action);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScrollInject(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client != null && client.currentScreen != null && client.getWindow() != null) {
            float mouseX = (float) client.mouse.getX() * (float) client.getWindow().getScaledWidth() / (float) client.getWindow().getWidth();
            float mouseY = (float) client.mouse.getY() * (float) client.getWindow().getScaledHeight() / (float) client.getWindow().getHeight();

            egg.client.module.render.Interface iface = ModuleManager.getInstance().getModule(egg.client.module.render.Interface.class);
            if (iface != null && iface.isEnabled()) {
                if (iface.onMouseScrolled(mouseX, mouseY, vertical)) {
                    ci.cancel();
                    return;
                }
            }

            egg.client.module.render.Cooldowns cooldowns = ModuleManager.getInstance().getModule(egg.client.module.render.Cooldowns.class);
            if (cooldowns != null && cooldowns.isEnabled()) {
                if (cooldowns.onMouseScrolled(mouseX, mouseY, vertical)) {
                    ci.cancel();
                    return;
                }
            }

            egg.client.module.render.PotionHUD potionHUD = ModuleManager.getInstance().getModule(egg.client.module.render.PotionHUD.class);
            if (potionHUD != null && potionHUD.isEnabled()) {
                if (potionHUD.onMouseScrolled(mouseX, mouseY, vertical)) {
                    ci.cancel();
                    return;
                }
            }

            egg.client.module.render.ArmorHUD armorHUD = ModuleManager.getInstance().getModule(egg.client.module.render.ArmorHUD.class);
            if (armorHUD != null && armorHUD.isEnabled()) {
                if (armorHUD.onMouseScrolled(mouseX, mouseY, vertical)) {
                    ci.cancel();
                    return;
                }
            }
        }

        egg.client.module.render.ViewModel vm = ModuleManager.getInstance().getModule(egg.client.module.render.ViewModel.class);
        if (vm != null && vm.isEnabled()) {
            if (vm.onScroll(vertical)) {
                ci.cancel();
            }
        }
    }
}
