package com.holdmylua.source.mixin.render;

import com.holdmylua.source.access.AlternateBlockRenderer;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={BlockRenderManager.class})
public abstract class BlockRenderManagerMixin
implements AlternateBlockRenderer {
    @Shadow
    @Final
    private BlockColors blockColors;

    @Shadow
    public abstract BlockStateModel getModel(BlockState var1);

    @Override
    @Unique
    public void renderSingleBlockWithEmission(BlockState blockState, MatrixStack poseStack, OrderedRenderCommandQueue queue, int combinedLight, ClientWorld world, AbstractClientPlayerEntity player) {
        BlockRenderType renderShape = blockState.getRenderType();
        if (renderShape == BlockRenderType.INVISIBLE) {
            return;
        }
        combinedLight = LightmapTextureManager.applyEmission((int)combinedLight, (int)blockState.getLuminance());
        BlockStateModel blockStateModel = this.getModel(blockState);
        int tint = this.blockColors.getColor(blockState, (BlockRenderView)world, player.getBlockPos(), 0);
        float r = (float)(tint >> 16 & 0xFF) / 255.0f;
        float g = (float)(tint >> 8 & 0xFF) / 255.0f;
        float b = (float)(tint & 0xFF) / 255.0f;
        for (BlockModelPart blockModelPart : blockStateModel.getParts(Random.create((long)42L))) {
            for (Direction direction : Direction.values()) {
                for (BakedQuad bakedQuad : blockModelPart.getQuads(direction)) {
                    this.hmi$renderBakedQuad(bakedQuad, poseStack, queue, r, g, b, combinedLight, blockState);
                }
            }
            for (BakedQuad bakedQuad : blockModelPart.getQuads(null)) {
                this.hmi$renderBakedQuad(bakedQuad, poseStack, queue, r, g, b, combinedLight, blockState);
            }
        }
        MinecraftClient.getInstance().getBakedModelManager().getBlockEntityModelsSupplier().render(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, queue, combinedLight, OverlayTexture.DEFAULT_UV, 0);
    }

    @Unique
    private void hmi$renderBakedQuad(BakedQuad bakedQuad, MatrixStack poseStack, OrderedRenderCommandQueue queue, float r, float g, float b, int combinedLight, BlockState blockState) {
        if (bakedQuad.hasTint()) {
            r = Math.clamp(r, 0.0f, 1.0f);
            g = Math.clamp(g, 0.0f, 1.0f);
            b = Math.clamp(b, 0.0f, 1.0f);
        } else {
            r = 1.0f;
            g = 1.0f;
            b = 1.0f;
        }
        RenderLayer usedLayer = bakedQuad.shade() && blockState.getLuminance() == 0 ? TexturedRenderLayers.getBlockTranslucentCull() : RenderLayers.cutout();
        float finalR = r;
        float finalG = g;
        float finalB = b;
        queue.submitCustom(poseStack, usedLayer, (matricesEntry, consumer) -> consumer.quad(matricesEntry, bakedQuad, new float[]{1.0f, 1.0f, 1.0f, 1.0f}, finalR, finalG, finalB, 1.0f, new int[]{combinedLight, combinedLight, combinedLight, combinedLight}, OverlayTexture.DEFAULT_UV));
    }
}
