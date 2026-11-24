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

    public static final RenderPipeline ARMOR_HURT = armorHurt("armor_hurt", DepthTestFunction.LEQUAL_DEPTH_TEST);
    public static final RenderPipeline ARMOR_HURT_DECAL = armorHurt("armor_hurt_decal", DepthTestFunction.EQUAL_DEPTH_TEST);

    private static RenderPipeline armorHurt(String name, DepthTestFunction depthTest) {
        return register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/" + name))
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withSampler("Sampler1")
                .withCull(false)
                .withDepthTestFunction(depthTest)
                .build());
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

    public static final RenderPipeline TRAJECTORY_LINE = register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/trajectory_line"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/line"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/line"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    public static final RenderPipeline JUMP_CAUSTIC = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/jump_caustic"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("CausticUniforms", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AURA_BLOOM = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/aura_bloom"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AURA_BLOOM_DEPTH = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/aura_bloom_depth"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline TOTEM_SOUL = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/totem_soul"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/glow/composite"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/totem_soul"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AURA_TEXTURE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/aura_texture"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline AURA_TEXTURE_DEPTH = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/aura_texture_depth"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline TARGET_WATER = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/target_water"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/target_water"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline TARGET_IRON_DUST = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/target_iron_dust"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/target_iron_dust"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline COLOR_COPY = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/color_copy"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/copy"))
            .withSampler("Sampler0")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline TARGET_FOCUS = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/target_focus"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/target_focus"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/target_focus"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withSampler("Sampler3")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline ICE_FRACTURE_MODEL = register(
        RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ice_fracture_model"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("NO_OVERLAY")
            .withCull(false)
            .build()
    );

    public static final RenderPipeline ICE_FREEZE = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ice_freeze"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/ice_freeze"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/ice_freeze"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withUniform("IceFreezeUniforms", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline ICE_CRYSTAL = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/ice_crystal"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/ice_crystal"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline WORLD_LYRICS = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/world_lyrics"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/world/jump_caustic"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/world/lyrics_paint"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("LyricPaint", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline DISCHARGE_SHELL = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/discharge_shell"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/glow/composite"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/glow/discharge"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("EggFlame", UniformType.UNIFORM_BUFFER)
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
            .withUniform("EggBlurRect", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );
    public static final RenderPipeline WEATHER_SNOW = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/weather_snow"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/snow/vertex"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/snow/fragment"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("EggSnowData", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline MOTION_BLUR = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/motion_blur"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/motion_blur"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("EggMotionBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );

    public static final RenderPipeline MOTION_BLUR_ACCUM = register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(EggClient.MOD_ID, "pipeline/motion_blur_accum"))
            .withVertexShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/pass"))
            .withFragmentShader(Identifier.of(EggClient.MOD_ID, "core/fullscreen/motion_blur_accum"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("EggAccumBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(CustomVertexFormats.POSITION_TEX_LINE_WIDTH, DrawMode.QUADS)
            .build()
    );
}
