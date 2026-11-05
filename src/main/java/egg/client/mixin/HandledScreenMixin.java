package egg.client.mixin;

import egg.client.module.misc.ServerAssistant;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void egg$renderAuctionOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ServerAssistant sa = ServerAssistant.getInstance();
        if (sa != null) {
            sa.renderAuctionOverlay(context, (HandledScreen<?>)(Object) this, mouseX, mouseY);
        }
    }

    @ModifyArg(
        method = "drawForeground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
            ordinal = 0
        ),
        index = 1
    )
    private Text egg$modifyContainerTitle(Text title) {
        ServerAssistant sa = ServerAssistant.getInstance();
        if (sa != null) {
            return sa.modifyTitle(title, (HandledScreen<?>)(Object) this);
        }
        return title;
    }
}
