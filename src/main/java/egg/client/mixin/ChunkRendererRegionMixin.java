package egg.client.mixin;

import egg.client.module.ModuleManager;
import egg.client.module.render.WorldTweaks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowyBlock;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRendererRegion.class)
public class ChunkRendererRegionMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null && state.contains(SnowyBlock.SNOWY) && !state.get(SnowyBlock.SNOWY)) {
            WorldTweaks wt = ModuleManager.getInstance().getModule(WorldTweaks.class);
            if (wt != null && wt.isEnabled() && wt.isCustomSeason() && "Зима".equals(wt.getSeason())) {
                cir.setReturnValue(state.with(SnowyBlock.SNOWY, true));
            }
        }
    }
}
