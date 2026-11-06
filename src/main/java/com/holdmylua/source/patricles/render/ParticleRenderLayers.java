package com.holdmylua.source.patricles.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import java.util.function.Function;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class ParticleRenderLayers {
    private static final Function<Identifier, RenderLayer> ADDITIVE_PARTICLE_LAYER;
    static BlendFunction ADDITIVE;
    static RenderPipeline ADDITIVE_PARTICLE;

    public static RenderLayer additiveParticle(Identifier texture) {
        return ADDITIVE_PARTICLE_LAYER.apply(texture);
    }

    static {
        ADDITIVE = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE);
        ADDITIVE_PARTICLE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET}).withLocation("pipeline/additive_particle_effect").withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withBlend(ADDITIVE).build());
        ADDITIVE_PARTICLE_LAYER = Util.memoize(texture -> RenderLayer.of((String)"fire_screen_effect", (RenderSetup)RenderSetup.builder((RenderPipeline)ADDITIVE_PARTICLE).texture("Sampler0", texture).build()));
    }
}
