package platform.inject.mixin;
import net.minecraft.client.util.math.MatrixStack;


import static delta.core.Interface.aM_;


import delta.render.Animations;
import delta.event.ContainerEvent;
import delta.core.Delta;
import delta.core.EventManager;
import delta.core.IEvent;
import delta.mixin.ISlot;
import delta.core.Interface;
import delta.module.player.ItemScroller;
import delta.event.KeyEvent;
import delta.ui.screen.SwapScreen;
import delta.lib.javassist.TokenId;
import net.minecraft.util.Formatting;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import delta.module.misc.ContainerSearcher;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import platform.inject.accessors.HandledScreenAccessor;
import platform.inject.accessors.ScreenAccessor;

@Mixin({HandledScreen.class})
public abstract class HandledScreenMixin<T extends ScreenHandler> {

    @Unique
    private ButtonWidget buttonFold;

    @Unique
    private ButtonWidget buttonTake;

    @Unique
    private ButtonWidget buttonDrop;

    @Unique
    private TextFieldWidget searchField;

    @Shadow
    @Final
    protected T handler;

    @Unique
    private int chestSize;

    @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("HEAD"))
    private void onDrawSlotHead(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Animations animations = Delta.h().d().t().Q();
        if (animations.m() && animations.q().a("Предметы").c().booleanValue()) {
            boolean focused = slot == ((HandledScreenAccessor) this).getFocusedSlot() && slot.hasStack();
            float scale = ((ISlot) slot).getAnimation().a(focused ? 1.25f : 1.0f, focused ? 1.25f : 0.75f);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(slot.x + 8.0f, slot.y + 8.0f);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-(slot.x + 8.0f), -(slot.y + 8.0f));
        }
    }

    @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("RETURN"))
    private void onDrawSlotTail(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Animations animations = Delta.h().d().t().Q();
        if (animations.m() && animations.q().a("Предметы").c().booleanValue()) {
            context.getMatrices().popMatrix();
        }
    }

    @ModifyArg(method = {"drawForeground"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V", ordinal = 0), index = 1)
    private Text modifyTitle(Text title) {
        ContainerEvent event = new ContainerEvent((HandledScreen)(Object) this, title);
        EventManager.a((IEvent) event);
        return event.i();
    }

    @Inject(method = {"init"}, at = {@At("TAIL")})
    private void onInit(CallbackInfo ci) {
        int iMethod_17388;
        if ((this.handler instanceof GenericContainerScreenHandler) || (this.handler instanceof ShulkerBoxScreenHandler)) {
            HandledScreenAccessor screen = (HandledScreenAccessor) (HandledScreenAccessor) this;
            ScreenAccessor screenBase = (ScreenAccessor) this;
            GenericContainerScreenHandler class_1707Var = this.handler instanceof GenericContainerScreenHandler ? (GenericContainerScreenHandler) this.handler : null;
            if (class_1707Var instanceof GenericContainerScreenHandler) {
                GenericContainerScreenHandler genericContainerScreenHandler = class_1707Var;
                iMethod_17388 = genericContainerScreenHandler.getRows() * 9;
            } else {
                iMethod_17388 = this.handler instanceof ShulkerBoxScreenHandler ? 27 : this.chestSize;
            }
            this.chestSize = iMethod_17388;
            this.buttonFold = screenBase.invokeAddDrawableChild(ButtonWidget.builder(Text.literal("Сложить"), b -> {
                onFoldClick();
            }).dimensions(((screenBase.getWidth() + screen.getBackgroundWidth()) / 2) + 5, (screenBase.getHeight() - screen.getBackgroundHeight()) / 2, 100, 20).build());
            this.buttonTake = screenBase.invokeAddDrawableChild(ButtonWidget.builder(Text.literal("Забрать"), b2 -> {
                onTakeClick();
            }).dimensions(((screenBase.getWidth() + screen.getBackgroundWidth()) / 2) + 5, ((screenBase.getHeight() - screen.getBackgroundHeight()) / 2) + 24, 100, 20).build());
            this.buttonDrop = screenBase.invokeAddDrawableChild(ButtonWidget.builder(Text.literal("Выкинуть"), b3 -> {
                onDropClick();
            }).dimensions(((screenBase.getWidth() + screen.getBackgroundWidth()) / 2) + 5, ((screenBase.getHeight() - screen.getBackgroundHeight()) / 2) + 48, 100, 20).build());

            ContainerSearcher searcher = ContainerSearcher.getInstance();
            if (searcher != null && searcher.m()) {
                this.searchField = screenBase.invokeAddDrawableChild(new TextFieldWidget(Interface.aM_.textRenderer, ((screenBase.getWidth() + screen.getBackgroundWidth()) / 2) + 5, ((screenBase.getHeight() - screen.getBackgroundHeight()) / 2) + 72, 100, 18, Text.literal("Поиск...")));
                this.searchField.setPlaceholder(Text.literal("Поиск..."));
                if (searcher.getCurrentQuery() != null && !searcher.getCurrentQuery().isEmpty()) {
                    this.searchField.setText(searcher.getCurrentQuery());
                }
                this.searchField.setChangedListener(searcher::setCurrentQuery);
            }
        }
    }

    @Unique
    private boolean hasStack(Slot slot) {
        return slot.hasStack() && !slot.getStack().isEmpty();
    }

    @Unique
    private void onFoldClick() {
        for (int i = this.chestSize; i < ((ScreenHandler) this.handler).slots.size(); i++) {
            Slot slot = (Slot) ((ScreenHandler) this.handler).slots.get(i);
            if (hasStack(slot)) {
                Interface.aM_.interactionManager.clickSlot(((ScreenHandler) this.handler).syncId, slot.id, 0, SlotActionType.QUICK_MOVE, Interface.aM_.player);
            }
        }
    }

    @Unique
    private void onTakeClick() {
        for (int i = 0; i < this.chestSize; i++) {
            Slot slot = (Slot) ((ScreenHandler) this.handler).slots.get(i);
            if (hasStack(slot)) {
                Interface.aM_.interactionManager.clickSlot(((ScreenHandler) this.handler).syncId, slot.id, 0, SlotActionType.QUICK_MOVE, Interface.aM_.player);
            }
        }
    }

    @Unique
    private void onDropClick() {
        for (int i = 0; i < this.chestSize; i++) {
            Slot slot = (Slot) ((ScreenHandler) this.handler).slots.get(i);
            if (hasStack(slot)) {
                Interface.aM_.interactionManager.clickSlot(((ScreenHandler) this.handler).syncId, slot.id, 1, SlotActionType.THROW, Interface.aM_.player);
            }
        }
    }

    @Inject(method = {"keyPressed"}, at = {@At("HEAD")}, cancellable = true)
    private void onKeyPressed(net.minecraft.client.input.KeyInput input, CallbackInfoReturnable<Boolean> info) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchField.setFocused(false);
                info.setReturnValue(true);
                return;
            }
            if (this.searchField.keyPressed(input)) {
                info.setReturnValue(true);
                return;
            }
            info.setReturnValue(true);
            return;
        }
        Slot focusedSlot;
        KeyEvent event = new KeyEvent(keyCode, scanCode, 0, modifiers);
        EventManager.a((IEvent) event);
        if (event.a()) {
            info.setReturnValue(true);
            return;
        }
        if (Interface.aM_.options.dropKey == null || !Interface.aM_.options.dropKey.matchesKey(input) || (modifiers & 2) == 0 || (modifiers & 1) == 0 || (focusedSlot = ((HandledScreenAccessor) this).getFocusedSlot()) == null || !focusedSlot.hasStack()) {
            return;
        }
        handleDropItems(focusedSlot.getStack().getItem());
        info.setReturnValue(true);
    }

    @Unique
    private void handleDropItems(Item targetItem) {
        for (Slot slot : ((ScreenHandler) this.handler).slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                Interface.aM_.interactionManager.clickSlot(((ScreenHandler) this.handler).syncId, slot.id, 1, SlotActionType.THROW, Interface.aM_.player);
            }
        }
    }

    @Inject(method = {"onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V"}, at = {@At("HEAD")}, cancellable = true)
    private void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        SwapScreen swapScreen = Delta.h().d().t().L().q();
        if (swapScreen.b() && !slot.getStack().isEmpty()) {
            swapScreen.a(swapScreen.a(), slot.getStack());
            swapScreen.a(-1);
            swapScreen.a(false);
            Interface.aM_.setScreen(swapScreen);
            ci.cancel();
        }
    }

    @Inject(method = {"removed"}, at = {@At("HEAD")})
    private void onRemoved(CallbackInfo ci) {
        SwapScreen swapMenu = Delta.h().d().t().L().q();
        if (swapMenu.b()) {
            swapMenu.a(false);
        }
    }

    @Inject(method = {"render"}, at = {@At("HEAD")})
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.buttonFold != null) {
            this.buttonFold.active = ((ScreenHandler) this.handler).slots.subList(this.chestSize, ((ScreenHandler) this.handler).slots.size()).stream().anyMatch(this::hasStack);
        }
        if (this.buttonTake != null && this.buttonDrop != null) {
            this.buttonTake.active = ((ScreenHandler) this.handler).slots.subList(0, this.chestSize).stream().anyMatch(this::hasStack);
            this.buttonDrop.active = ((ScreenHandler) this.handler).slots.subList(0, this.chestSize).stream().anyMatch(this::hasStack);
        }
        EventManager.a((IEvent) new ContainerEvent((HandledScreen)(Object) this, context, mouseX, mouseY, ContainerEvent.Phase.PRE));
        ItemScroller itemScroller = Delta.h().d().t().w();
        if (itemScroller.m() && ((HandledScreenAccessor) this).getFocusedSlot() != null && ((HandledScreenAccessor) this).getFocusedSlot().hasStack() && GLFW.glfwGetMouseButton(Interface.aM_.getWindow().getHandle(), 0) == 1 && GLFW.glfwGetKey(Interface.aM_.getWindow().getHandle(), TokenId.O_) == 1 && itemScroller.r().a(itemScroller.q().c().intValue())) {
            Interface.aM_.interactionManager.clickSlot(((ScreenHandler) this.handler).syncId, ((HandledScreenAccessor) this).getFocusedSlot().id, 0, SlotActionType.QUICK_MOVE, Interface.aM_.player);
            itemScroller.r().b();
        }
    }

    @Inject(method = {"render"}, at = {@At("TAIL")})
    private void onRenderTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        EventManager.a((IEvent) new ContainerEvent((HandledScreen)(Object) this, context, mouseX, mouseY, ContainerEvent.Phase.POST));

        ContainerSearcher searcher = ContainerSearcher.getInstance();
        if (searcher != null && searcher.m() && this.searchField != null) {
            String query = searcher.getCurrentQuery();
            if (query != null && !query.trim().isEmpty()) {
                HandledScreenAccessor accessor = (HandledScreenAccessor) this;
                for (Slot slot : ((ScreenHandler) this.handler).slots) {
                    float slotX = accessor.getX() + slot.x;
                    float slotY = accessor.getY() + slot.y;
                    boolean matches = slot.hasStack() && searcher.matches(slot.getStack(), query);
                    if (matches) {
                        Delta.h().d().i().a(new MatrixStack(), slotX - 1.0f, slotY - 1.0f, 18.0f, 18.0f, 2.0f, 1.0f, 0xFF55FF55);
                    } else if (searcher.isDimNonMatching()) {
                        Delta.h().d().i().a(context, slotX, slotY, 16.0f, 16.0f, 0xAA000000);
                    }
                }
            }
        }
    }
}
