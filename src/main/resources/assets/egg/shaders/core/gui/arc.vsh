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

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
out vec2 quadCoord;
out vec2 arcAngles;    // (начало, раствор) в радианах
out vec2 arcShimmer;   // (фаза бегущего блика 0..1, его сила 0..1)
out float thicknessPx;

const float ANGLE_SCALE = 16.0;       // градусы упакованы ×16
const float SHIMMER_SCALE = 10000.0;  // доли упакованы ×10000
const float DEG_TO_RAD = 0.017453292;

vec3 srgbToLinear(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = vec4(srgbToLinear(Color.rgb), Color.a);
    quadCoord = UV0;
    arcAngles = vec2(UV1) / ANGLE_SCALE * DEG_TO_RAD;
    arcShimmer = vec2(UV2) / SHIMMER_SCALE;
    thicknessPx = LineWidth;
}
