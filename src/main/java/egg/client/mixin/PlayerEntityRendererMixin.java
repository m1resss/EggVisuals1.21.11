package egg.client.mixin;

import egg.client.cosmetic.CosmeticsManager;
import egg.client.cosmetic.CosmeticsRenderer;
import egg.client.module.ModuleManager;
import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.module.misc.Globals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    public PlayerEntityRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        this.addFeature(new CosmeticsRenderer((PlayerEntityRenderer) (Object) this));
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderLabelIfPresent(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        egg.client.module.render.NameTags nameTags = ModuleManager.getInstance().getModule(egg.client.module.render.NameTags.class);
        if (nameTags != null && nameTags.isEnabled()) {
            ci.cancel();
            return;
        }
        Globals globals = ModuleManager.getInstance().getModule(Globals.class);
        if (globals != null && globals.isEnabled() && globals.isInNametag() && Globals.isEggUser(state)) {
            String cleanName = Globals.extractCleanName(state);
            if (state.displayName != null) {
                state.displayName = globals.formatPlayerName(state.displayName, cleanName);
            } else if (state.playerName != null) {
                state.playerName = globals.formatPlayerName(state.playerName, cleanName);
            }
        }
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        Globals globals = ModuleManager.getInstance().getModule(Globals.class);
        if (globals != null && globals.isEnabled() && globals.isInNametag() && ((entity instanceof Entity ent && Globals.isEggUser(ent)) || Globals.isEggUser(state))) {
            String cleanName = (entity != null) ? entity.getNameForScoreboard() : Globals.extractCleanName(state);
            if (state.displayName != null) {
                state.displayName = globals.formatPlayerName(state.displayName, cleanName);
            } else if (state.playerName != null) {
                state.playerName = globals.formatPlayerName(state.playerName, cleanName);
            }
        }

        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        CosmeticsManager.SwordEntry sword = CosmeticsManager.getInstance().getSelectedSwordEntry();
        if (sword == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isLocal = false;
        if (mc.player != null) {
            if (entity == mc.player || state.id == mc.player.getId() || state.id <= 0) {
                isLocal = true;
            }
            String myName = mc.player.getNameForScoreboard();
            if (myName != null && !myName.isEmpty()) {
                if (state.displayName != null && state.displayName.getString().contains(myName)) {
                    isLocal = true;
                }
                if (state.playerName != null && state.playerName.getString().contains(myName)) {
                    isLocal = true;
                }
            }
        }
        if (!isLocal) return;

        state.rightArmPose = BipedEntityModel.ArmPose.ITEM;
        if (state.rightHandItem.isEmpty() || !CosmeticsManager.isSword(state.rightHandItem)) {
            state.rightHandItem = new ItemStack(Items.DIAMOND_SWORD);
        }
        CosmeticsManager.getInstance().applySwordRenderState(state.rightHandItemState, state.rightHandItem, entity instanceof LivingEntity le ? le : null, net.minecraft.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
