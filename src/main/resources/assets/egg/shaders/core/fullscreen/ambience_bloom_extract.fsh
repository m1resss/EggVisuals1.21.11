#version 330

uniform sampler2D Sampler0; // текущий кадр мира (full-res)

layout(std140) uniform SadnesAmbiencePost {
    float Proj00;
    float Proj11;
    float Near;
    float Far;
    float BlurStart;
    float BlurEnd;
    float BloomThreshold;
    float BloomIntensity;
    float UpViewX;
    float UpViewY;
    float UpViewZ;
    float ReflStrength;
    float ReflDistance;
    float SkyZenithR;
    float SkyZenithG;
    float SkyZenithB;
    float SkyHorizonR;
    float SkyHorizonG;
    float SkyHorizonB;
    float SunDirX;
    float SunDirY;
    float SunDirZ;
    float AxisXx;
    float AxisXy;
    float AxisXz;
    float AxisZx;
    float AxisZy;
    float AxisZz;
    float CamX;
    float CamZ;
    float WaveTime;
    float Pad0;
};

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// Bright-pass блума. Префильтр box4 с весом Кариса (1/(1+luma)): одиночные яркие
// пиксели не стробят при движении камеры. Затем мягкий порог (колено 0.25) и
// нормировка 0.25 — аддитивный подъём суммирует 4 октавы (BLOOM_CHAIN_DEPTH),
// заранее делим, чтобы сумма не клипалась в RGBA8; интенсивность — в композите.
void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

    vec3 c0 = texture(Sampler0, texCoord0 + texel * vec2(-1.0, -1.0)).rgb;
    vec3 c1 = texture(Sampler0, texCoord0 + texel * vec2(1.0, -1.0)).rgb;
    vec3 c2 = texture(Sampler0, texCoord0 + texel * vec2(-1.0, 1.0)).rgb;
    vec3 c3 = texture(Sampler0, texCoord0 + texel * vec2(1.0, 1.0)).rgb;

    float w0 = 1.0 / (1.0 + luminance(c0));
    float w1 = 1.0 / (1.0 + luminance(c1));
    float w2 = 1.0 / (1.0 + luminance(c2));
    float w3 = 1.0 / (1.0 + luminance(c3));
    vec3 color = (c0 * w0 + c1 * w1 + c2 * w2 + c3 * w3) / (w0 + w1 + w2 + w3);

    float weight = smoothstep(BloomThreshold, BloomThreshold + 0.25, luminance(color));
    fragColor = vec4(color * weight * 0.25, 1.0);
}
