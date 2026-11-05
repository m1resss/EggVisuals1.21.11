package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.misc.ServerAssistant;
import egg.client.module.player.ItemScroller;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Unique
    private Slot egg$lastScrolledSlot = null;

    @Inject(method = "render", at = @At("TAIL"))
    private void egg$renderAuctionOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ServerAssistant sa = ServerAssistant.getInstance();
        if (sa != null) {
            sa.renderAuctionOverlay(context, (HandledScreen<?>)(Object) this, mouseX, mouseY);
        }

        ItemScroller scroller = ModuleManager.getInstance().getModule(ItemScroller.class);
        if (scroller != null && scroller.isEnabled()) {
            if (scroller.isKeyHeld()) {
                Slot focused = ((HandledScreenAccessor) this).getFocusedSlot();
                if (focused != null && focused.hasStack()) {
                    if (focused != egg$lastScrolledSlot && scroller.canMove()) {
                        egg$lastScrolledSlot = focused;
                        this.onMouseClick(focused, focused.id, 0, SlotActionType.QUICK_MOVE);
                    }
                }
            } else {
                egg$lastScrolledSlot = null;
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void egg$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller scroller = ModuleManager.getInstance().getModule(ItemScroller.class);
        if (scroller != null && scroller.isEnabled() && scroller.isWheelEnabled()) {
            if (verticalAmount != 0) {
                Slot focused = ((HandledScreenAccessor) this).getFocusedSlot();
                if (focused != null && focused.hasStack() && scroller.canMove()) {
                    this.onMouseClick(focused, focused.id, 0, SlotActionType.QUICK_MOVE);
                    cir.setReturnValue(true);
                }
            }
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
