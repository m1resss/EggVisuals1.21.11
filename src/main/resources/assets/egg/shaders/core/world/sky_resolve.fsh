#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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

in vec3 skyDir;
out vec4 fragColor;

void main() {
    ivec2 jitter = ivec2(JitterX, JitterY);
    ivec2 local = ivec2(gl_FragCoord.xy) - jitter;
    bool isFreshPixel = ((local.x | local.y) & 1) == 0;
    if (isFreshPixel) {
        fragColor = texelFetch(Sampler0, local >> 1, 0);
        return;
    }
    vec2 freshUv = (gl_FragCoord.xy + 0.5 - vec2(jitter)) * 0.5 / vec2(textureSize(Sampler0, 0));
    fragColor = texture(Sampler0, freshUv);
    if (HistoryValid < 0.5) return;
    vec4 previous = PrevViewProj * vec4(skyDir, 1.0);
    if (previous.w <= 0.0001) return;
    vec2 ndc = previous.xy / previous.w;
    vec2 uv = vec2(ndc.x * 0.5 + 0.5, mix(ndc.y * 0.5 + 0.5, 0.5 - ndc.y * 0.5, OriginTop));
    bool isInsidePrevious = all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
    if (isInsidePrevious) fragColor = texture(Sampler1, uv);
}
