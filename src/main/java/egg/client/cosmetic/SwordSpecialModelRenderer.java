package egg.client.cosmetic;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class SwordSpecialModelRenderer implements SpecialModelRenderer<SkycoreCosmeticsManager.SwordEntry> {
    public static final SwordSpecialModelRenderer INSTANCE = new SwordSpecialModelRenderer();

    private SwordSpecialModelRenderer() {}

    @Override
    public void render(@Nullable SkycoreCosmeticsManager.SwordEntry sword, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int seed) {
        if (sword == null) return;
        SkycoreCosmeticsManager.getInstance().renderSwordModel(sword, mode, matrices, queue, light, overlay, glint);
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer) {}

    @Override
    @Nullable
    public SkycoreCosmeticsManager.SwordEntry getData(ItemStack stack) {
        return SkycoreCosmeticsManager.getInstance().getSelectedSwordEntry();
    }
}
