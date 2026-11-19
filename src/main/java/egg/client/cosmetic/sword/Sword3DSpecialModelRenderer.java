package egg.client.cosmetic.sword;

import egg.client.module.cosmetic.CosmeticsModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class Sword3DSpecialModelRenderer implements SpecialModelRenderer<SwordStyle> {
    public static final Sword3DSpecialModelRenderer INSTANCE = new Sword3DSpecialModelRenderer();

    private Sword3DSpecialModelRenderer() {
    }

    @Override
    public void render(@Nullable SwordStyle style, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int seed) {
        if (style == null || style == SwordStyle.NONE) {
            CosmeticsModule mod = CosmeticsModule.getInstance();
            if (mod != null && mod.isEnabled()) {
                style = mod.getSelectedSwordStyle();
            }
        }
        if (style == null || style == SwordStyle.NONE) return;
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        SwordRenderer.render(style, mode, matrices, queue, light);
        matrices.pop();
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer) {
    }

    @Override
    @Nullable
    public SwordStyle getData(ItemStack stack) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return null;
        SwordStyle style = mod.getSelectedSwordStyle();
        return style == SwordStyle.NONE ? null : style;
    }
}
