package egg.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import egg.client.module.render.WorldTweaks;
import egg.client.render.world.FrostFoliage;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {
    @ModifyReturnValue(method = "getColor", at = @At("RETURN"))
    private int egg$frostFixedTint(int color, BlockState state, BlockRenderView world, BlockPos pos, int tintIndex) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || !tweaks.frozenFoliage() || tintIndex != 0) return color;
        if (state.isOf(Blocks.BIRCH_LEAVES)) return FrostFoliage.tint(color, 13821144, 0.85f);
        if (state.isOf(Blocks.SPRUCE_LEAVES)) return FrostFoliage.tint(color, 7310458, 0.6f);
        return color;
    }
}
