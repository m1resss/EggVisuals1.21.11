package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.player.AutoAccept;
import egg.client.module.player.NoChatBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Redirect(method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 1))
    private Object redirectChatBackgroundOpacity(net.minecraft.client.option.SimpleOption<?> option) {
        NoChatBackground mod = ModuleManager.getInstance().getModule(NoChatBackground.class);
        if (mod != null && mod.isEnabled()) {
            return 0.0;
        }
        return option.getValue();
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        if (message == null) return;
        String text = message.getString();

        AutoAccept accept = ModuleManager.getInstance().getModule(AutoAccept.class);
        if (accept != null && accept.isEnabled()) {
            accept.onChatMessage(text);
        }

        egg.client.module.player.AutoAuth auth = ModuleManager.getInstance().getModule(egg.client.module.player.AutoAuth.class);
        if (auth != null && auth.isEnabled()) {
            auth.onChatMessage(text);
        }

// egg.client.cases.CaseManager.getInstance().checkChatKill(text);
    }
}
