package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.WorldTweaks;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class BiomeColorsMixin {

    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private static void onGetGrassColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && wt.isEnabled() && wt.isCustomSeason()) {
            Integer customColor = wt.getCustomGrassColor(pos);
            if (customColor != null) {
                cir.setReturnValue(customColor);
            }
        }
    }
}
