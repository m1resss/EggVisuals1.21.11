#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
layout(std140) uniform EggWeatherPost {
    float WorldSaturation;
    float NearPlane;
    float FarPlane;
    float TanHalfFov;
    float Aspect;
    float Pitch;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    vec4 WorldGrade;
    vec4 FogColor;
};

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

float linearDepth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * NearPlane * FarPlane / (FarPlane + NearPlane - ndc * (FarPlane - NearPlane));
}

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

vec3 grade(vec3 color) {
    float amount = max(WorldSaturation, 0.0);
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    float boost = max(amount - 1.0, 0.0);
    vec3 saturated = mix(vec3(luma), color, amount);
    saturated = clamp(saturated * (1.0 + boost * 0.35), 0.0, 1.0);
    return clamp(saturated * WorldGrade.rgb * WorldGrade.a, 0.0, 1.0);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 0.999999) {
        fragColor = source;
        return;
    }

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 view = vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0) * linearDepth(depth);
    float pitchCos = cos(Pitch);
    float pitchSin = sin(Pitch);
    vec3 offset = vec3(view.x, view.y * pitchCos + view.z * pitchSin, view.z * pitchCos - view.y * pitchSin);

    float spherical = length(offset);
    float cylindrical = max(length(offset.xz), abs(offset.y));
    float fog = max(linear_fog_value(spherical, FogEnvironmentalStart, FogEnvironmentalEnd),
            linear_fog_value(cylindrical, FogRenderDistanceStart, FogRenderDistanceEnd)) * FogColor.a;
    if (fog >= 0.999) {
        fragColor = source;
        return;
    }

    vec3 lit = clamp((source.rgb - FogColor.rgb * fog) / (1.0 - fog), 0.0, 1.0);
    fragColor = vec4(mix(grade(lit), FogColor.rgb, fog), source.a);
}
