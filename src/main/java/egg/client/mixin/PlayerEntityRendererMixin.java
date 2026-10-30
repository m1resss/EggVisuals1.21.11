package egg.client.mixin;

import egg.client.cosmetic.CosmeticsManager;
import egg.client.cosmetic.CosmeticsRenderer;
import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
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
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
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
        }
        if (!isLocal) return;

        state.rightArmPose = BipedEntityModel.ArmPose.ITEM;
        if (state.rightHandItem.isEmpty() || !CosmeticsManager.isSword(state.rightHandItem)) {
            state.rightHandItem = new ItemStack(Items.DIAMOND_SWORD);
        }
        CosmeticsManager.getInstance().applySwordRenderState(state.rightHandItemState, state.rightHandItem, entity instanceof LivingEntity le ? le : null, net.minecraft.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
