package platform.inject.mixin;

import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import platform.interfaces.TargetScanRenderState;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements TargetScanRenderState {
    @Unique
    private boolean delta$targetScanTarget;

    @Override
    public boolean delta$isTargetScanTarget() {
        return this.delta$targetScanTarget;
    }

    @Override
    public void delta$setTargetScanTarget(boolean target) {
        this.delta$targetScanTarget = target;
    }
}
