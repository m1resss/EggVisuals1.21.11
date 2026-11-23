package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import egg.client.module.render.WorldTweaks;
import egg.client.render.world.FrostFoliage;
import net.minecraft.client.color.world.BiomeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @ModifyReturnValue(method = "getGrassColor", at = @At("RETURN"))
    private static int egg$frozenGrass(int color) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        return tweaks != null && tweaks.frozenFoliage() ? FrostFoliage.tint(color, 14215388, 0.85f) : color;
    }

    @ModifyReturnValue(method = {"getFoliageColor", "getDryFoliageColor"}, at = @At("RETURN"))
    private static int egg$frozenFoliage(int color) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        return tweaks != null && tweaks.frozenFoliage() ? FrostFoliage.tint(color, 13821144, 0.85f) : color;
    }
}
