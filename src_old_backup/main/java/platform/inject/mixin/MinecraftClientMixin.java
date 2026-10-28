package platform.inject.mixin;


import static delta.core.Interface.aM_;

import delta.core.Delta;
import delta.core.EventManager;
import delta.ui.screen.ModernGUIScreen;
import delta.core.GlobalEvent;
import delta.event.HotbarEvent;
import delta.core.IEvent;
import delta.core.Interface;
import delta.core.InterfaceC0020Opcode;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({MinecraftClient.class})
public abstract class MinecraftClientMixin implements Interface {

    @Unique
    private static final String DELTA_WINDOW_TITLE = "Egg Client - 1.21.11";

    @Unique
    private Set<String> resourcePacks;

    @Inject(method = {"getWindowTitle"}, at = {@At("HEAD")}, cancellable = true)
    private void getWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(DELTA_WINDOW_TITLE);
    }

    @Inject(method = {"setScreen"}, at = {@At("HEAD")})
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof PackScreen) {
            this.resourcePacks = (Set) aM_.getResourcePackManager().getEnabledProfiles().stream().map((v0) -> {
                return v0.getId();
            }).collect(Collectors.toCollection(HashSet::new));
        } else if (this.resourcePacks != null && !(aM_.currentScreen instanceof PackScreen)) {
            this.resourcePacks = null;
        }
    }

    @Inject(method = {"reloadResources()Ljava/util/concurrent/CompletableFuture;"}, at = {@At("HEAD")}, cancellable = true)
    private void reloadResources(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (this.resourcePacks != null) {
            Set<String> current = (Set) aM_.getResourcePackManager().getEnabledProfiles().stream().map((v0) -> {
                return v0.getId();
            }).collect(Collectors.toSet());
            if (this.resourcePacks.equals(current)) {
                cir.setReturnValue(CompletableFuture.completedFuture(null));
            }
            this.resourcePacks = null;
        }
    }

    @Inject(method = {"tick"}, at = {@At("HEAD")})
    private void onGlobalTick(CallbackInfo ci) {
        EventManager.a((IEvent) new GlobalEvent());
    }

    @Redirect(method = {"handleInputEvents"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void handleInputEvents(ClientPlayerInteractionManager manager, PlayerEntity player) {
        if (Delta.h().d().v().k().a()) {
            return;
        }
        if (player.isUsingItem()) {
            ItemStack stack = player.getActiveItem();
            if ((stack.getItem() instanceof CrossbowItem) && stack.getItem().getMaxUseTime(stack, player) - player.getItemUseTimeLeft() <= CrossbowItem.getPullTime(stack, player)) {
                return;
            }
        }
        manager.stopUsingItem(player);
    }

    @Redirect(method = {"handleInputEvents"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"))
    private void onSetSelectedSlot(net.minecraft.entity.player.PlayerInventory inventory, int slot) {
        HotbarEvent event = new HotbarEvent(slot);
        EventManager.a((IEvent) event);
        if (!event.a()) {
            inventory.setSelectedSlot(slot);
        }
    }

    @Inject(method = {"doItemUse"}, at = {@At("HEAD")}, cancellable = true)
    private void doItemUse(CallbackInfo ci) {
        ItemStack stack = aM_.player.getStackInHand(Hand.MAIN_HAND);
        if ((stack.getItem() instanceof PotionItem) && aM_.player.getItemCooldownManager().isCoolingDown(stack)) {
            ci.cancel();
        }
        if ((stack.getItem() instanceof CrossbowItem) && CrossbowItem.isCharged(stack) && aM_.player.getItemCooldownManager().isCoolingDown(stack)) {
            ci.cancel();
        }
    }

    @Inject(method = {"hasOutline"}, at = {@At("HEAD")}, cancellable = true)
    private void onHasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (delta.module.render.ShaderESP.shouldOutline(entity)) {
            cir.setReturnValue(true);
        }
    }
}
