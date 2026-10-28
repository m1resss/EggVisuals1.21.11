package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.player.AutoAccept;
import egg.client.module.player.NoChatBackground;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;toAlpha(F)I")
    )
    private float modifyChatBgAlpha(float alpha) {
        NoChatBackground mod = ModuleManager.getInstance().getModule(NoChatBackground.class);
        if (mod != null && mod.isEnabled()) {
            return 0.0f;
        }
        return alpha;
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
    }
}
