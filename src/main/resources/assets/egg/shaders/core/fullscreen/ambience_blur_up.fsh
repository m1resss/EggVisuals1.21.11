#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

// Dual-Kawase UP c сохранением альфы (в отличие от общего blur_up): tent-фильтр 8 тапов.
// Тот же шейдер работает и аддитивным подъёмом блума — режим решает бленд pipeline.
void main() {
    vec2 halfpixel = 0.5 / vec2(textureSize(Sampler0, 0));

    vec4 sum = texture(Sampler0, texCoord0 + vec2(-halfpixel.x * 2.0, 0.0) * blurOffset);
    sum += texture(Sampler0, texCoord0 + vec2(-halfpixel.x, halfpixel.y) * blurOffset) * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(0.0, halfpixel.y * 2.0) * blurOffset);
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x, halfpixel.y) * blurOffset) * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x * 2.0, 0.0) * blurOffset);
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x, -halfpixel.y) * blurOffset) * 2.0;
    sum += texture(Sampler0, texCoord0 + vec2(0.0, -halfpixel.y * 2.0) * blurOffset);
    sum += texture(Sampler0, texCoord0 + vec2(-halfpixel.x, -halfpixel.y) * blurOffset) * 2.0;

    fragColor = sum / 12.0;
}
