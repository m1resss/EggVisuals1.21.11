package egg.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import egg.client.module.ModuleManager;
import egg.client.module.combat.HitColor;
import egg.client.module.render.KillEffect;
import egg.client.render.shader.Pipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(EquipmentRenderer.class)
public class EquipmentRendererMixin {
    @Unique
    private static final String egg$RENDER = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V";
    @Unique
    private static final Function<Identifier, RenderLayer> egg$hurtArmor = Util.memoize(texture ->
            RenderLayer.of("egg_armor_hurt", RenderSetup.builder(Pipelines.ARMOR_HURT)
                    .texture("Sampler0", texture).useLightmap().useOverlay()
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .crumbling().outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE).build()));

    @Unique
    private static final Function<Identifier, RenderLayer> egg$iceFractureArmor = Util.memoize(texture ->
            RenderLayer.of("egg_ice_fracture_armor", RenderSetup.builder(Pipelines.ICE_FRACTURE_MODEL)
                    .texture("Sampler0", texture).useLightmap()
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).build()));

    @WrapOperation(method = egg$RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/RenderLayers;armorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer egg$armorLayer(Identifier texture, Operation<RenderLayer> original, @Local(argsOnly = true) Object state) {
        if (state instanceof LivingEntityRenderState living && KillEffect.isRenderingIceFracture(living)) {
            return egg$iceFractureArmor.apply(texture);
        }
        return egg$hasHurtArmor(state) ? egg$hurtArmor.apply(texture) : original.call(texture);
    }

    @WrapOperation(method = egg$RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/TexturedRenderLayers;getArmorTrims(Z)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer egg$trimLayer(boolean decal, Operation<RenderLayer> original, @Local(argsOnly = true) Object state) {
        if (!egg$hasHurtArmor(state)) return original.call(decal);
        return decal ? HurtTrimLayer.DECAL : egg$hurtArmor.apply(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
    }

    @ModifyArg(
        method = egg$RENDER,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
        ),
        index = 5
    )
    private int modifyArmorOverlay(int overlay, @Local(argsOnly = true) Object state) {
        return egg$hasHurtArmor(state) ? OverlayTexture.getUv(0.0f, true) : overlay;
    }

    @Unique
    private static boolean egg$hasHurtArmor(Object state) {
        if (!(state instanceof LivingEntityRenderState livingState) || !livingState.hurt) return false;
        HitColor hitColor = ModuleManager.getInstance().getModule(HitColor.class);
        return hitColor != null && hitColor.isEnabled() && hitColor.isColorArmor();
    }

    private static final class HurtTrimLayer {
        private static final RenderLayer DECAL = RenderLayer.of("egg_armor_hurt_decal",
                RenderSetup.builder(Pipelines.ARMOR_HURT_DECAL)
                        .texture("Sampler0", TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE)
                        .useLightmap().useOverlay().layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .crumbling().outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE).build());
    }
}
