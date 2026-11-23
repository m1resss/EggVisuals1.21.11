package egg.client.mixin;

import egg.client.module.render.WorldTweaks;
import egg.client.render.world.FrostFoliage;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class WeatherFoliageMixin {
    @Inject(method = "<init>(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/SpriteDimensions;Lnet/minecraft/client/texture/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
    private void egg$frostSprite(Identifier id, SpriteDimensions size, NativeImage image, Optional<?> animation,
                                  List<?> metadata, Optional<?> texture, CallbackInfo ci) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks != null && tweaks.frozenFoliage()) FrostFoliage.paint(id, image);
    }
}
