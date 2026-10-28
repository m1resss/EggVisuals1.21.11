package platform.inject.mixin;


import delta.core.Delta;
import delta.module.misc.StreamerMode;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SkinTextures.class})
public class SkinTexturesMixin {
    @Inject(method = "body", at = @At("HEAD"), cancellable = true)
    public void body(CallbackInfoReturnable<net.minecraft.util.AssetInfo.TextureAsset> cir) {
        StreamerMode streamerMode = Delta.h().d().t().aE();
        if (streamerMode.m() && streamerMode.q().c().booleanValue()) {
            cir.setReturnValue(new net.minecraft.util.AssetInfo.TextureAssetInfo(Identifier.of("delta", "pictures/skin.png")));
        }
    }
}
