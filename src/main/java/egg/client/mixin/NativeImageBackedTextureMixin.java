package egg.client.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NativeImageBackedTexture.class)
public class NativeImageBackedTextureMixin {

    @ModifyArg(
            method = "createTexture(Ljava/util/function/Supplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/SamplerCache;getRepeated(Lcom/mojang/blaze3d/textures/FilterMode;)Lnet/minecraft/client/gl/GpuSampler;"
            )
    )
    private static FilterMode egg$linearFilter(FilterMode original) {
        return FilterMode.LINEAR;
    }
}
