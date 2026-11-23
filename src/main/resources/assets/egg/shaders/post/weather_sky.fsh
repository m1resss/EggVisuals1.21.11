#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
layout(std140) uniform EggWeatherPost {
    float Intensity;
    float time;
    vec4 CameraPosition4;
    float SkyPitch;
    float SkyYaw;
    float TanHalfFov;
    float Aspect;
    vec4 SkyZenith4;
    vec4 SkyHorizon4;
    vec4 SkyNadir4;
    vec4 SunDir4;
    vec4 SunColor4;
    float SunSize;
    float SunGlare;
    float CloudCover;
    float CloudDark;
    float CloudSpeed;
    float CloudScale;
    vec4 CloudLit4;
    vec4 CloudShadow4;
    float StarAmount;
    float BandAmount;
    float AuroraAmount;
    float MeteorAmount;
    float HazeAmount;
    float FrostAmount;
    vec4 HazeColor4;
    vec4 GlowDir4;
    float GlowFocus;
    float Flash;
    vec4 FlashColor4;
    float Opacity;
};

#define CameraPosition CameraPosition4.xyz
#define SkyZenith SkyZenith4.xyz
#define SkyHorizon SkyHorizon4.xyz
#define SkyNadir SkyNadir4.xyz
#define SunDir SunDir4.xyz
#define SunColor SunColor4.xyz
#define CloudLit CloudLit4.xyz
#define CloudShadow CloudShadow4.xyz
#define HazeColor HazeColor4.xyz
#define GlowDir GlowDir4.xyz
#define FlashColor FlashColor4.xyz

#moj_import <egg:weather_sky_color.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 viewDirection = normalize(vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0));
    vec3 skyDirection = getSkyDirection(viewDirection);
    vec3 shaderColor = weatherSky(skyDirection);
    float amount = clamp(max(Intensity, 0.0), 0.0, 1.0);
    float blend = fullSkyAmount(skyDirection.y, amount);

    if (Opacity > 0.0) {
        fragColor = vec4(shaderColor, blend * Opacity);
    } else {
        vec4 source = texture(InSampler, texCoord);
        fragColor = vec4(mix(source.rgb, shaderColor, blend), source.a);
    }
}
