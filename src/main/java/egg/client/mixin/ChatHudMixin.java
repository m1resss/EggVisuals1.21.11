package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.player.AutoAccept;
import egg.client.module.player.NoChatBackground;
import egg.client.render.animation.HudAnimations;
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

    @Inject(
        method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud$Backend;updatePose(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER)
    )
    private void egg$offsetChatLines(ChatHud.Backend backend, int currentTick, int mouseX, boolean focused, CallbackInfo ci) {
        float f = HudAnimations.INSTANCE.getChatMessageOffset();
        if (f != 0.0F) {
            backend.updatePose(matrix3x2f -> matrix3x2f.translate(0.0F, f));
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        if (message == null) return;
        String text = message.getString();

        egg.client.module.minigames.chess.Chess chess = egg.client.module.minigames.chess.Chess.getInstance();
        if (chess != null && text.contains(egg.client.module.minigames.chess.ChessProtocol.PREFIX)) {
            boolean handled = chess.handleIncomingPayload(text);
            if (handled) {
                ci.cancel();
                return;
            }
        }

        HudAnimations.INSTANCE.onChatMessage();

        AutoAccept accept = ModuleManager.getInstance().getModule(AutoAccept.class);
        if (accept != null && accept.isEnabled()) {
            accept.onChatMessage(text);
        }

        egg.client.module.player.AutoAuth auth = ModuleManager.getInstance().getModule(egg.client.module.player.AutoAuth.class);
        if (auth != null && auth.isEnabled()) {
            auth.onChatMessage(text);
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onAddSignedMessage(Text message, net.minecraft.network.message.MessageSignatureData signatureData, net.minecraft.client.gui.hud.MessageIndicator indicator, CallbackInfo ci) {
        if (message == null) return;
        String text = message.getString();

        egg.client.module.minigames.chess.Chess chess = egg.client.module.minigames.chess.Chess.getInstance();
        if (chess != null && text.contains(egg.client.module.minigames.chess.ChessProtocol.PREFIX)) {
            boolean handled = chess.handleIncomingPayload(text);
            if (handled) {
                ci.cancel();
            }
        }
    }
}
