package egg.client.mixin;

import egg.client.util.AudioPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow public ClientPlayerEntity player;
    @Shadow public ClientPlayerInteractionManager interactionManager;
    @Shadow protected int attackCooldown;
    @Shadow public HitResult crosshairTarget;
    @Shadow public net.minecraft.entity.Entity targetedEntity;

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
