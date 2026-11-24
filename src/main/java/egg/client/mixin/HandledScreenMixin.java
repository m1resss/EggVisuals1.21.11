package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.misc.ServerAssistant;
import egg.client.module.player.ContainerSearch;
import egg.client.module.player.ItemScroller;
import egg.client.ui.widget.ContainerSearchBar;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Unique
    private Slot egg$lastScrolledSlot = null;

    @Unique
    private ContainerSearchBar egg$search;

    @Inject(method = "init", at = @At("RETURN"))
    private void egg$initSearch(CallbackInfo ci) {
        ContainerSearch module = ModuleManager.getInstance().getModule(ContainerSearch.class);
        if (!module.isEnabled()) {
            egg$search = null;
            return;
        }
        Screen screen = this;
        int slots;
        if (screen instanceof GenericContainerScreen chest) {
            slots = chest.getScreenHandler().getRows() * 9;
        } else if (screen instanceof ShulkerBoxScreen) {
            slots = 27;
        } else {
            return;
        }
        var bounds = (HandledScreenAccessor) this;
        String query = egg$search == null ? "" : egg$search.field().getText();
        egg$search = new ContainerSearchBar(textRenderer, slots, width, bounds.getX(), bounds.getY(), bounds.getBackgroundWidth(), query);
        addSelectableChild(egg$search.field());
    }

    @Inject(method = "drawSlots", at = @At("HEAD"))
    private void egg$updateSearch(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (egg$search != null) {
            HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
            egg$search.update(screen.getScreenHandler());
        }
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void egg$highlightSearchResult(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (egg$search != null) egg$search.drawSlot(context, slot);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void egg$searchKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (egg$search == null) return;
        var field = egg$search.field();
        if (input.key() == GLFW.GLFW_KEY_F && input.hasCtrlOrCmd()) {
            setFocused(field);
            cir.setReturnValue(true);
        } else if (field.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_TAB) {
                setFocused(null);
            } else {
                field.keyPressed(input);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void egg$pinComparisonItem(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (egg$search != null) {
            var field = egg$search.field();
            if (egg$search.contains(click.x(), click.y())) {
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    setFocused(field);
                    field.mouseClicked(click, doubleClick);
                    setDragging(true);
                }
                cir.setReturnValue(true);
                return;
            }
            if (field.isFocused()) setFocused(null);
        }
        ServerAssistant assistant = ServerAssistant.getInstance();
        if (click.button() == 2 && assistant != null && assistant.pinComparisonItem((HandledScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void egg$renderAuctionOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ServerAssistant sa = ServerAssistant.getInstance();
        if (sa != null) {
            sa.renderAuctionOverlay(context, (HandledScreen<?>)(Object) this, mouseX, mouseY);
        }

        if (egg$search != null) {
            egg$search.render(context, textRenderer, mouseX, mouseY, delta);
            if (egg$search.field().isFocused() || egg$search.contains(mouseX, mouseY)) return;
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
        if (egg$search != null && (egg$search.field().isFocused() || egg$search.contains(mouseX, mouseY))) return;
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
