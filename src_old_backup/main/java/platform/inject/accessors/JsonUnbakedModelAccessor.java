package platform.inject.accessors;

import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(JsonUnbakedModel.class)
public interface JsonUnbakedModelAccessor {
    @Accessor("elements")
    List<ModelElement> getElements();
}
