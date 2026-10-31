package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.Interface;
import egg.client.module.render.PotionHUD;
import egg.client.module.render.ArmorHUD;
import egg.client.module.render.Cooldowns;
import egg.client.module.render.TargetHUD;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderChatScreenTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        if (iface != null && iface.isEnabled()) {
            iface.renderChatOverlay(context, mouseX, mouseY);
        }
        PotionHUD potionHUD = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potionHUD != null && potionHUD.isEnabled()) {
            potionHUD.renderChatOverlay(context, mouseX, mouseY);
        }
        Cooldowns cooldowns = ModuleManager.getInstance().getModule(Cooldowns.class);
        if (cooldowns != null && cooldowns.isEnabled()) {
            cooldowns.renderChatOverlay(context, mouseX, mouseY);
        }
        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null && targetHUD.isEnabled()) {
            targetHUD.renderChatOverlay(context, mouseX, mouseY);
        }
        ArmorHUD armorHUD = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armorHUD != null && armorHUD.isEnabled()) {
            armorHUD.renderChatOverlay(context, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onChatMouseClicked(Click click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        ArmorHUD armorHUD = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armorHUD != null && armorHUD.isEnabled()) {
            if (armorHUD.onChatMouseClicked(click.x(), click.y(), click.button())) {
                cir.setReturnValue(true);
                return;
            }
        }
        TargetHUD targetHUD = ModuleManager.getInstance().getModule(TargetHUD.class);
        if (targetHUD != null && targetHUD.isEnabled()) {
            if (targetHUD.onChatMouseClicked(click.x(), click.y(), click.button())) {
                cir.setReturnValue(true);
                return;
            }
        }
        Cooldowns cooldowns = ModuleManager.getInstance().getModule(Cooldowns.class);
        if (cooldowns != null && cooldowns.isEnabled()) {
            if (cooldowns.onChatMouseClicked(click.x(), click.y(), click.button())) {
                cir.setReturnValue(true);
                return;
            }
        }
        PotionHUD potionHUD = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potionHUD != null && potionHUD.isEnabled()) {
            if (potionHUD.onChatMouseClicked(click.x(), click.y(), click.button())) {
                cir.setReturnValue(true);
                return;
            }
        }
        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        if (iface != null && iface.isEnabled()) {
            if (iface.onChatMouseClicked(click.x(), click.y(), click.button())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onChatMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        Interface iface = ModuleManager.getInstance().getModule(Interface.class);
        if (iface != null && iface.isEnabled()) {
            if (iface.onMouseScrolled(mouseX, mouseY, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }
        Cooldowns cooldowns = ModuleManager.getInstance().getModule(Cooldowns.class);
        if (cooldowns != null && cooldowns.isEnabled()) {
            if (cooldowns.onMouseScrolled(mouseX, mouseY, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }
        PotionHUD potionHUD = ModuleManager.getInstance().getModule(PotionHUD.class);
        if (potionHUD != null && potionHUD.isEnabled()) {
            if (potionHUD.onMouseScrolled(mouseX, mouseY, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }
        ArmorHUD armorHUD = ModuleManager.getInstance().getModule(ArmorHUD.class);
        if (armorHUD != null && armorHUD.isEnabled()) {
            if (armorHUD.onMouseScrolled(mouseX, mouseY, verticalAmount)) {
                cir.setReturnValue(true);
            }
        }
    }
}
