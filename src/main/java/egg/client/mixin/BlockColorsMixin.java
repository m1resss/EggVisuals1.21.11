package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.WorldTweaks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockColors.class)
public class BlockColorsMixin {

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void onGetColor(BlockState state, BlockRenderView world, BlockPos pos, int tintIndex, CallbackInfoReturnable<Integer> cir) {
        WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
        if (wt != null && wt.isEnabled() && wt.isCustomSeason()) {
            Block block = state.getBlock();
            if (block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES) {
                Integer customColor = wt.getCustomTreeLeavesColor(block, pos);
                if (customColor != null) {
                    cir.setReturnValue(customColor);
                }
            } else if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.FERN || block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) {
                Integer customGrass = wt.getCustomGrassColor(pos);
                if (customGrass != null) {
                    cir.setReturnValue(customGrass);
                }
            }
        }
    }
}
