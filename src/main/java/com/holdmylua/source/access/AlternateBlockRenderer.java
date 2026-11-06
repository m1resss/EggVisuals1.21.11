package com.holdmylua.source.access;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

public interface AlternateBlockRenderer {
    public void renderSingleBlockWithEmission(BlockState var1, MatrixStack var2, OrderedRenderCommandQueue var3, int var4, ClientWorld var5, AbstractClientPlayerEntity var6);
}
