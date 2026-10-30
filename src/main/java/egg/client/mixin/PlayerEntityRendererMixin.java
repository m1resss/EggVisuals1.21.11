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
import net.minecraft.text.Text;
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
            at = @At("HEAD")
    )
    private void onRenderLabelIfPresent(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        Globals globals = ModuleManager.getInstance().getModule(Globals.class);
        if (globals != null && globals.isEnabled() && Globals.isEggUser(state)) {
            Text eggPrefix = Text.literal("§f🥚 ");
            if (state.playerName != null && !state.playerName.getString().contains("🥚")) {
                state.playerName = Text.empty().append(eggPrefix).append(state.playerName);
            } else if (state.playerName == null && state.displayName != null && !state.displayName.getString().contains("🥚")) {
                state.playerName = Text.empty().append(eggPrefix).append(state.displayName);
            }
            if (state.displayName != null && !state.displayName.getString().contains("🥚")) {
                state.displayName = Text.empty().append(eggPrefix).append(state.displayName);
            }
        }
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        // Globals: White egg icon before Egg Visuals users' nameplate
        Globals globals = ModuleManager.getInstance().getModule(Globals.class);
        if (globals != null && globals.isEnabled() && ((entity instanceof Entity ent && Globals.isEggUser(ent)) || Globals.isEggUser(state))) {
            Text eggPrefix = Text.literal("§f🥚 ");
            if (state.playerName != null && !state.playerName.getString().contains("🥚")) {
                state.playerName = Text.empty().append(eggPrefix).append(state.playerName);
            } else if (state.playerName == null && state.displayName != null && !state.displayName.getString().contains("🥚")) {
                state.playerName = Text.empty().append(eggPrefix).append(state.displayName);
            }
            if (state.displayName != null && !state.displayName.getString().contains("🥚")) {
                state.displayName = Text.empty().append(eggPrefix).append(state.displayName);
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
        }
        if (!isLocal) return;

        state.rightArmPose = BipedEntityModel.ArmPose.ITEM;
        if (state.rightHandItem.isEmpty() || !CosmeticsManager.isSword(state.rightHandItem)) {
            state.rightHandItem = new ItemStack(Items.DIAMOND_SWORD);
        }
        CosmeticsManager.getInstance().applySwordRenderState(state.rightHandItemState, state.rightHandItem, entity instanceof LivingEntity le ? le : null, net.minecraft.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
