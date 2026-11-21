package egg.client.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractTexture.class)
public class AbstractTextureMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/SamplerCache;get(Lcom/mojang/blaze3d/textures/AddressMode;Lcom/mojang/blaze3d/textures/AddressMode;Lcom/mojang/blaze3d/textures/FilterMode;Lcom/mojang/blaze3d/textures/FilterMode;Z)Lnet/minecraft/client/gl/GpuSampler;",
                    ordinal = 0
            ),
            index = 2
    )
    private static FilterMode egg$minLinear(FilterMode original) {
        return FilterMode.LINEAR;
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/SamplerCache;get(Lcom/mojang/blaze3d/textures/AddressMode;Lcom/mojang/blaze3d/textures/AddressMode;Lcom/mojang/blaze3d/textures/FilterMode;Lcom/mojang/blaze3d/textures/FilterMode;Z)Lnet/minecraft/client/gl/GpuSampler;",
                    ordinal = 0
            ),
            index = 3
    )
    private static FilterMode egg$magLinear(FilterMode original) {
        return FilterMode.LINEAR;
    }
}
