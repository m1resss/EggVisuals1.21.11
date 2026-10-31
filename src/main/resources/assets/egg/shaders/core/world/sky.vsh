#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform EggSky {
    float Time;
    int Variant;
    float Brightness;
    float Opacity;
    int Quality;
    float SkyR;
    float SkyG;
    float SkyB;
    mat4 PrevViewProj;
    float ScaleX;
    float ScaleY;
    float OffsetX;
    float OffsetY;
    int JitterX;
    int JitterY;
    float OriginTop;
    float HistoryValid;
    float PixelScale;
    float SkyPad0;
    float SkyPad1;
    float SkyPad2;
};

in vec3 Position;

out vec3 skyDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position.xy = gl_Position.xy * vec2(ScaleX, ScaleY) + vec2(OffsetX, OffsetY) * gl_Position.w;
    // Небо рисуется ПОСЛЕ непрозрачного мира (шов перед translucent, SectionRenderStateMixin):
    // z=w прижимает куб к дальней плоскости, и LEQUAL-тест пропускает фрагмент только там,
    // где глубина осталась 1.0 — процедурка считается лишь на реально видимых пикселях неба.
    gl_Position.z = gl_Position.w * 0.999999;
    skyDir = Position;
}
