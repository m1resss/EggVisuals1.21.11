package egg.client.mixin;

import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.render.Hitmarker;
import egg.client.module.render.TotemPopConfetti;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String content, CallbackInfo ci) {
        if (content != null && content.startsWith(".")) {
            boolean handled = CommandManager.getInstance().execute(content);
            if (handled) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendChatCommand(String command, CallbackInfo ci) {
        if (command != null && command.startsWith(".")) {
            boolean handled = CommandManager.getInstance().execute(command);
            if (handled) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), cancellable = true)
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) {
                if (packet.getStatus() == 35) { // USE_TOTEM_OF_UNDYING
                    TotemPopConfetti confetti = ModuleManager.getInstance().getModule(TotemPopConfetti.class);
                    if (confetti != null && confetti.isEnabled()) {
                        confetti.onTotemPop(entity);
                        if (confetti.shouldCancelVanilla()) {
                            mc.world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(),
                                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS,
                                    1.0f, 1.0f, false);
                            ci.cancel();
                        }
                    }
                } else if (packet.getStatus() == 2) { // DAMAGE_TILT
                    Hitmarker hitmarker = ModuleManager.getInstance().getModule(Hitmarker.class);
                    if (hitmarker != null) {
                        hitmarker.onPacket(packet);
                    }
                }
            }
        }
    }
}