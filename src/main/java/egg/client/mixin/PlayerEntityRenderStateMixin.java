package egg.client.mixin;

import egg.client.render.BedrockRunState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements BedrockRunState {
    @Unique
    private boolean egg$bedrockRunning;

    @Override
    public boolean egg$isBedrockRunning() {
        return egg$bedrockRunning;
    }

    @Override
    public void egg$setBedrockRunning(boolean running) {
        egg$bedrockRunning = running;
    }
}
