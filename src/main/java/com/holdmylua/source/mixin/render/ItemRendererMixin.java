package com.holdmylua.source.mixin.render;

import com.holdmylua.source.global.DispatcherStorage;
import com.holdmylua.source.global.GlobalsStorage;
import com.holdmylua.source.global.item_model.ItemModelContext;
import com.holdmylua.source.global.item_model.ItemModelStorage;
import com.holdmylua.source.lua_runtime.ModelScriptCache;
import com.holdmylua.source.lua_runtime.ScriptHolder;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.command.ItemCommandRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MatrixUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={ItemCommandRenderer.class})
public abstract class ItemRendererMixin {
    @Unique
    private static int getTint(int[] tints, int index) {
        return index >= 0 && index < tints.length ? tints[index] : -1;
    }

    @Unique
    private static boolean useTranslucentGlint(RenderLayer renderLayer) {
        return MinecraftClient.usesImprovedTransparency() && renderLayer == TexturedRenderLayers.getItemTranslucentCull();
    }

    @Redirect(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"))
    private static void render(ItemDisplayContext displayContext, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint) {
        if (egg.client.module.render.HMI.getInstance() == null || !egg.client.module.render.HMI.getInstance().isEnabled()) {
            ItemRenderer.renderItem(displayContext, matrices, vertexConsumers, light, overlay, tints, quads, layer, glint);
            return;
        }
        VertexConsumer vertexConsumer;
        if (glint == ItemRenderState.Glint.SPECIAL) {
            MatrixStack.Entry entry = matrices.peek().copy();
            if (displayContext == ItemDisplayContext.GUI) {
                MatrixUtil.scale((Matrix4f)entry.getPositionMatrix(), (float)0.5f);
            } else if (displayContext.isFirstPerson()) {
                MatrixUtil.scale((Matrix4f)entry.getPositionMatrix(), (float)0.75f);
            }
            vertexConsumer = ItemRendererMixin.getSpecialItemGlintConsumer(vertexConsumers, layer, entry);
        } else {
            vertexConsumer = ItemRenderer.getItemGlintConsumer((VertexConsumerProvider)vertexConsumers, (RenderLayer)layer, (boolean)true, (glint != ItemRenderState.Glint.NONE ? 1 : 0) != 0);
        }
        ItemRendererMixin.customRenderBakedQuads(matrices, vertexConsumer, quads, tints, light, overlay, displayContext);
    }

    @Unique
    private static void customRenderBakedQuads(MatrixStack matrices, VertexConsumer vertexConsumer, List<BakedQuad> quads, int[] tints, int light, int overlay, ItemDisplayContext displayContext) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        int index = 0;
        if ((displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) && MinecraftClient.getInstance().getEntityRenderDispatcher().gameOptions.getPerspective().isFirstPerson() && player == MinecraftClient.getInstance().player) {
            ItemModelContext context = ItemModelStorage.get();
            ItemStack renderedItem = DispatcherStorage.getRenderedItem();
            ScriptHolder.itemModelCache.executeModel(context, renderedItem, (AbstractClientPlayerEntity)player, GlobalsStorage.modelPartAnimator);
            for (ModelScriptCache cache : ScriptHolder.itemModelAddonsCache) {
                cache.executeModel(context, renderedItem, (AbstractClientPlayerEntity)player, GlobalsStorage.modelPartAnimator);
            }
        }
        for (BakedQuad bakedQuad : quads) {
            float j;
            float h;
            float g;
            float f;
            if (bakedQuad.hasTint()) {
                int i = ItemRendererMixin.getTint(tints, bakedQuad.tintIndex());
                f = (float)ColorHelper.getAlpha((int)i) / 255.0f;
                g = (float)ColorHelper.getRed((int)i) / 255.0f;
                h = (float)ColorHelper.getGreen((int)i) / 255.0f;
                j = (float)ColorHelper.getBlue((int)i) / 255.0f;
            } else {
                f = 1.0f;
                g = 1.0f;
                h = 1.0f;
                j = 1.0f;
            }
            matrices.push();
            GlobalsStorage.modelPartAnimator.applyPoses(index, matrices);
            MatrixStack.Entry entry = matrices.peek();
            vertexConsumer.quad(entry, bakedQuad, g, h, j, f, light, overlay);
            matrices.pop();
            ++index;
        }
        GlobalsStorage.modelPartAnimator.clear();
    }

    @Unique
    private static VertexConsumer getSpecialItemGlintConsumer(VertexConsumerProvider consumers, RenderLayer layer, MatrixStack.Entry matrix) {
        return VertexConsumers.union((VertexConsumer)new OverlayVertexConsumer(consumers.getBuffer(ItemRendererMixin.useTranslucentGlint(layer) ? RenderLayers.glintTranslucent() : RenderLayers.glint()), matrix, 0.0078125f), (VertexConsumer)consumers.getBuffer(layer));
    }
}
