package egg.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class ItemRenderHelper {
    private ItemRenderHelper() {}

    public static void renderItem(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        if (stack == null || stack.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        World world = entity != null ? entity.getEntityWorld() : client.world;

        ItemRenderState state = new ItemRenderState();
        client.getItemModelManager().clearAndUpdate(
                state, stack, displayContext, world, entity, 0
        );

        OrderedRenderCommandQueueImpl queue = new OrderedRenderCommandQueueImpl();
        VertexConsumerProvider.Immediate immediate = vertexConsumers instanceof VertexConsumerProvider.Immediate imm
                ? imm
                : client.getBufferBuilders().getEntityVertexConsumers();

        RenderDispatcher dispatcher = new RenderDispatcher(
                queue,
                client.getBlockRenderManager(),
                immediate,
                client.getAtlasManager(),
                new OutlineVertexConsumerProvider(),
                immediate,
                client.textRenderer
        );

        try {
            state.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
            dispatcher.render();
            dispatcher.endLayeredCustoms();
        } catch (Throwable ignored) {
        } finally {
            try {
                dispatcher.close();
            } catch (Throwable ignored) {}
        }
    }
}
