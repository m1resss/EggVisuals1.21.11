package delta.module.render;

import delta.core.Interface;

import platform.inject.accessors.HandledScreenAccessor;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;
import delta.render.ColorUtil;
import delta.util.ProjectUtil;

import delta.core.Category;
import delta.core.EventTarget;
import delta.core.ModuleRegister;
import delta.event.ContainerEvent;
import delta.event.DrawEvent;

import java.util.List;
import net.minecraft.entity.ItemEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector2f;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.DataComponentTypes;

@ModuleRegister(a = "Shulker Preview", b = "Показывает содержимое шалкеров в инвентаре зажав ALT (и на земле, без зажатия, если античит слабый)", c = Category.Render)
public class ShulkerPreview extends Module {
    private final Vector2f b = new Vector2f(175.0f, 70.0f);

    @EventTarget
    public void a(ContainerEvent event) {
        Slot hovered;
        if (event.h() == ContainerEvent.Phase.POST && (hovered = ((platform.inject.accessors.HandledScreenAccessor) event.b()).getFocusedSlot()) != null && hovered.getStack() != null && hovered.hasStack()) {
            ItemStack hoveredStack = hovered.getStack();
            if (hoveredStack.get(DataComponentTypes.CONTAINER) != null) {
                BlockItem class_1747VarMethod_7909 = (BlockItem) hoveredStack.getItem();
                if (class_1747VarMethod_7909 instanceof BlockItem) {
                    BlockItem blockItem = class_1747VarMethod_7909;
                    if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                        a(event.d(), hoveredStack, ((ContainerComponent) hoveredStack.get(DataComponentTypes.CONTAINER)).stream().toList(), event.f() + 8, (event.g() - this.b.y()) - 16.0f, 1.0f, true);
                    }
                }
            }
        }
    }

    @EventTarget
    public void a(DrawEvent event) {
        ItemStack stack;
        if (event.b()) {
            for (ItemEntity itemEntity : aM_.world.getEntitiesByClass(ItemEntity.class, aM_.player.getBoundingBox().expand(64), entity -> true)) {
                    BlockItem class_1747VarMethod_7909 = (BlockItem) itemEntity.getStack().getItem();
                    if (class_1747VarMethod_7909 instanceof BlockItem) {
                        BlockItem blockItem = class_1747VarMethod_7909;
                        if ((blockItem.getBlock() instanceof ShulkerBoxBlock) && (stack = itemEntity.getStack()) != null && !stack.isEmpty()) {
                            Vec3d lerped = itemEntity.getLerpedPos(aM_.getRenderTickCounter().getTickProgress(false));
                            Vector2f projected = ProjectUtil.a(lerped.x, lerped.y + 0.5d, lerped.z);
                            ContainerComponent container = (ContainerComponent) stack.get(DataComponentTypes.CONTAINER);
                            if (container != null && !container.stream().toList().isEmpty()) {
                                a(event.i(), stack, container.stream().toList(), projected.x() - ((this.b.x() * 0.5f) / 2.0f), projected.y() - (this.b.y() * 0.5f), 0.5f, false);
                            }
                        }
                    }
            }
        }
    }

    private void a(DrawContext context, ItemStack itemStack, List<ItemStack> stacks, float x, float y, float scale, boolean overlay) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("delta", "pictures/minecraft/3x9.png"), 0, 0, 0.0f, 0.0f, (int) this.b.x(), (int) this.b.y(), 256, 256, ColorUtil.a(((BlockItem) itemStack.getItem()).getBlock().getDefaultMapColor().color, 255));
        for (int i = 0; i < Math.min(stacks.size(), 27); i++) {
            ItemStack stack = stacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                int slotX = 9 + ((i % 9) * 18) + 1;
                int slotY = 9 + ((i / 9) * 18) + 1;
                Delta.h().d().j().a(context, stack, slotX, slotY, 0, 1.0f, 0.75f, overlay);
            }
        }
        matrices.popMatrix();
    }
}
