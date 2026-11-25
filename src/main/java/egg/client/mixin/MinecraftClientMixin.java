package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.misc.TickSync;
import egg.client.render.animation.HudAnimations;
import egg.client.ui.screen.EggTitleScreen;
import egg.client.util.AudioPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow public ClientWorld world;
    @Shadow public ClientPlayerEntity player;
    @Shadow public ClientPlayerInteractionManager interactionManager;
    @Shadow protected int attackCooldown;
    @Shadow public HitResult crosshairTarget;
    @Shadow public net.minecraft.entity.Entity targetedEntity;
    @Shadow public Screen currentScreen;
    @Shadow private boolean disconnecting;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen egg$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new EggTitleScreen();
        }
        if (screen == null && this.world == null && !this.disconnecting) {
            return new EggTitleScreen();
        }
        return screen;
    }

    @Inject(method = "getTargetMillisPerTick", at = @At("HEAD"), cancellable = true)
    private void onGetTargetMillisPerTick(float millis, CallbackInfoReturnable<Float> cir) {
        TickSync mod = ModuleManager.getInstance().getModule(TickSync.class);
        if (mod != null && mod.isEnabled() && mod.getClientTPS() > 20.0f && this.world != null && this.world.getTickManager().shouldTick()) {
            cir.setReturnValue(1000.0f / mod.getClientTPS());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        HudAnimations.INSTANCE.onScreenChange(this.currentScreen, screen);
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void onSetWorld(ClientWorld world, CallbackInfo ci) {
        if (world == null) {
            AudioPlayer.stopAll();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        AudioPlayer.stopAll();
    }
}
