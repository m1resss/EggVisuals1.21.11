package egg.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import egg.client.module.ModuleManager;
import egg.client.module.combat.HitColor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EquipmentRenderer.class)
public class EquipmentRendererMixin {

    @ModifyArg(
        method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
        ),
        index = 5
    )
    private int modifyArmorOverlay(int overlay, @Local(argsOnly = true) Object state) {
        HitColor hitColor = ModuleManager.getInstance().getModule(HitColor.class);
        if (hitColor != null && hitColor.isEnabled() && hitColor.isColorArmor()) {
            if (state instanceof LivingEntityRenderState livingState && livingState.hurt) {
                return OverlayTexture.getUv(0.0f, true);
            }
        }
        return overlay;
    }
}
