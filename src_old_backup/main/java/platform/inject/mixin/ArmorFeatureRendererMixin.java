package platform.inject.mixin;

import delta.core.Delta;
import delta.render.TargetScanRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import platform.inject.accessors.EquipmentRendererAccessor;
import platform.interfaces.TargetScanRenderState;

import java.util.List;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<
        S extends BipedEntityRenderState,
        M extends BipedEntityModel<S>,
        A extends BipedEntityModel<S>> {

    @Shadow
    @Final
    private EquipmentRenderer equipmentRenderer;

    @Unique
    private S delta$currentRenderState;

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void delta$beforeRender(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            S state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci) {
        this.delta$currentRenderState = state;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void delta$afterRender(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            S state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci) {
        this.delta$currentRenderState = null;
    }

    @Inject(
            method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
            at = @At("TAIL")
    )
    private void delta$submitTargetScanOnArmor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            ItemStack itemStack,
            EquipmentSlot slot,
            int light,
            A armorModel,
            CallbackInfo ci) {
        if (this.delta$currentRenderState == null
                || !((TargetScanRenderState) this.delta$currentRenderState).delta$isTargetScanTarget()) {
            return;
        }

        EquippableComponent equippable = itemStack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }

        EquipmentModel.LayerType layerType = slot == EquipmentSlot.LEGS
                ? EquipmentModel.LayerType.HUMANOID_LEGGINGS
                : EquipmentModel.LayerType.HUMANOID;

        EquipmentModelLoader modelLoader = ((EquipmentRendererAccessor) this.equipmentRenderer).delta$getEquipmentModelLoader();
        EquipmentModel model = modelLoader.get(equippable.assetId().orElseThrow());
        List<EquipmentModel.Layer> layers = model.getLayers(layerType);
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Identifier armorTexture = layers.get(0).getFullTextureId(layerType);

        TargetScanRenderer.ScanParams params = TargetScanRenderer.getParams();
        if (params == null) {
            return;
        }

        TargetScanRenderer.submitArmor(
                armorModel,
                this.delta$currentRenderState,
                matrices,
                vertexConsumers,
                armorTexture,
                slot,
                params
        );
    }
}
