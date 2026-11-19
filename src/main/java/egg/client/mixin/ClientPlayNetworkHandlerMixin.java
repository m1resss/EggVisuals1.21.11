package egg.client.mixin;

import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.misc.TickSync;
import egg.client.module.render.DamageNumbers;
import egg.client.module.render.Hitmarker;
import egg.client.module.render.KillEffect;
import egg.client.module.render.TotemPop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateTickRateS2CPacket;
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

    @Inject(method = "onEntity", at = @At("HEAD"))
    private void onEntity(EntityS2CPacket packet, CallbackInfo ci) {
        TickSync mod = ModuleManager.getInstance().getModule(TickSync.class);
        if (mod != null && mod.isEnabled()) {
            mod.onEntityPacket();
        }
    }

    @Inject(method = "onUpdateTickRate", at = @At("HEAD"))
    private void onUpdateTickRate(UpdateTickRateS2CPacket packet, CallbackInfo ci) {
        TickSync mod = ModuleManager.getInstance().getModule(TickSync.class);
        if (mod != null && mod.isEnabled()) {
            mod.onUpdateTickRate(packet.tickRate());
        }
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), cancellable = true)
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.isOnThread()) {
            return;
        }
        if (mc.world != null) {
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) {
                if (packet.getStatus() == 35) {
                    TotemPop totemPop = ModuleManager.getInstance().getModule(TotemPop.class);
                    if (totemPop != null && totemPop.isEnabled()) {
                        totemPop.onTotemPop(entity);
                        if (totemPop.shouldCancelVanilla()) {
                            mc.world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(),
                                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS,
                                    1.0f, 1.0f, false);
                            ci.cancel();
                        }
                    }
                } else if (packet.getStatus() == 2) {
                    Hitmarker hitmarker = ModuleManager.getInstance().getModule(Hitmarker.class);
                    if (hitmarker != null) {
                        hitmarker.onPacket(packet);
                    }
                    egg.client.module.combat.HitSounds hitSounds = ModuleManager.getInstance().getModule(egg.client.module.combat.HitSounds.class);
                    if (hitSounds != null && hitSounds.isEnabled()) {
                        hitSounds.onDamagePacket(entity);
                    }
                    DamageNumbers damageNumbers = ModuleManager.getInstance().getModule(DamageNumbers.class);
                    if (damageNumbers != null && damageNumbers.isEnabled()) {
                        damageNumbers.onEntityDamage(entity);
                    }
                } else if (packet.getStatus() == 3) {
                    KillEffect killEffect = ModuleManager.getInstance().getModule(KillEffect.class);
                    if (killEffect != null && killEffect.isEnabled()) {
                        killEffect.onEntityDeath(entity);
                    }
                }
            }
        }
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"))
    private void onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.isOnThread()) {
            return;
        }
        KillEffect killEffect = ModuleManager.getInstance().getModule(KillEffect.class);
        if (killEffect != null && killEffect.isEnabled()) {
            killEffect.onEntitiesDestroy(packet.getEntityIds());
        }
    }
}
