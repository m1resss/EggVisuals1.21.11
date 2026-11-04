package egg.client.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.font.GlyphAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlyphAtlasTexture.class)
public class GlyphAtlasTextureMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/SamplerCache;getRepeated(Lcom/mojang/blaze3d/textures/FilterMode;)Lnet/minecraft/client/gl/GpuSampler;"
            )
    )
    private static FilterMode egg$linearFilter(FilterMode original) {
        return original;
    }
}
