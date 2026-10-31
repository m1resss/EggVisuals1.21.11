package egg.client.render.shader;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import egg.client.EggClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class Pipelines {
    private Pipelines() {
    }

    private static RenderPipeline register(RenderPipeline pipeline) {
        return RenderPipelines.register(pipeline);
    }

    public static final RenderPipeline LIGHTNING = register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/lightning"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/line"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/lightning"))
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    );

    public static final RenderPipeline LIGHTNING_DEPTH = register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/lightning_depth"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/line"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/lightning"))
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    );

    public static final RenderPipeline DISCHARGE_SHELL = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/discharge_shell"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/glow/composite"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/glow/discharge"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("SadnesFlame", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline GLOW_MASK = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/glow_mask"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/glow/mask"))
            .withSampler("Sampler0")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline GLOW_BLUR = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/glow_blur"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/glow/blur"))
            .withSampler("Sampler0")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_REFLECTION = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_reflection"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_reflection"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withUniform("SadnesAmbiencePost", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_REFLECTION_COMPOSITE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_reflection_composite"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_reflection_composite"))
            .withSampler("Sampler0")
            .withUniform("SadnesAmbiencePost", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_BLOOM_EXTRACT = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_bloom_extract"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_bloom_extract"))
            .withSampler("Sampler0")
            .withUniform("SadnesAmbiencePost", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_BLOOM_COMPOSITE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_bloom_composite"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_bloom_composite"))
            .withSampler("Sampler0")
            .withUniform("SadnesAmbiencePost", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_COLOR_GRADE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_color_grade"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_color_grade"))
            .withSampler("Sampler0")
            .withUniform("SadnesAmbiencePost", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );



    public static final RenderPipeline AMBIENCE_BLUR_DOWN = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_blur_down"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_blur_down"))
            .withSampler("Sampler0")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_BLUR_UP = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_blur_up"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_blur_up"))
            .withSampler("Sampler0")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AMBIENCE_BLUR_UP_ADD = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ambience_blur_up_add"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/ambience_blur_up"))
            .withSampler("Sampler0")
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline PARTICLE_FLUFF_COMPOSITE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/fluff_composite"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/particle/fluff_composite"))
            .withSampler("Sampler0")
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline CLAMP_COPY = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/clamp_copy"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/clamp_copy"))
            .withSampler("Sampler0")
            .withUniform("SadnesBlurRect", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );
}
