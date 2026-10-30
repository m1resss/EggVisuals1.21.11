package egg.client.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
    @Accessor("displayContext")
    ItemDisplayContext getDisplayContext();

    @Accessor("displayContext")
    void setDisplayContext(ItemDisplayContext displayContext);
}
