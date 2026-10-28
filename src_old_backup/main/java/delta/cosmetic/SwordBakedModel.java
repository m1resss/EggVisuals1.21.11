package delta.cosmetic;

import net.minecraft.client.render.model.json.ModelTransformation;

public class SwordBakedModel {
    private final ModelTransformation transformation;

    public SwordBakedModel(ModelTransformation transformation) {
        this.transformation = transformation != null ? transformation : ModelTransformation.NONE;
    }

    public ModelTransformation getTransformation() {
        return this.transformation;
    }
}

